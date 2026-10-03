package com.descentmtb.physics;

import com.descentmtb.ramp.RampMath;
import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RampRideTest {
    /** Same two-block kicker as the client playtest, including its sharp lip. */
    static Terrain kicker() {
        return new BlockTerrain(new BlockTerrain.Columns() {
            boolean ramp(double z) { return z >= 24 && z < 26; }
            double height(double z) {
                return 64 + (z < 25
                        ? RampMath.heightAtT(0, 6, RampMath.CONCAVE, z - 24)
                        : RampMath.heightAtT(6, 16, RampMath.LINEAR, z - 25));
            }
            public double top(int x, int z, double top, double bottom) {
                return ramp(z) || top < 64 || bottom > 64 ? Double.NaN : 64;
            }
            public Terrain.Surface surface(int x, int z, double y) { return Terrain.Surface.GRASS; }
            public boolean exactSurface(double x, double z, double top, double bottom, double[] out) {
                if (!ramp(z)) return false;
                double y = height(z);
                if (y > top || y < bottom) return false;
                out[0] = y; out[1] = 0;
                out[2] = z < 25 ? 6.0 / 16 * RampMath.profileD(RampMath.CONCAVE, z - 24) : 10.0 / 16;
                return true;
            }
            public boolean solid(double x, double y, double z) { return y < (ramp(z) ? height(z) : 64); }
        });
    }

    @Test void pedalsOverCopycatKickerWithoutStalling() {
        BikeSim sim = new BikeSim(new BikeParams(), kicker());
        sim.place(0.5, 64, 2.5, 0);
        sim.vel = sim.riderVel = new V3(0, 0, 9.2);
        boolean flew = false;
        for (int t = 0; t < 500; t++) {
            double z = sim.pos.z;
            float body = z > 19.5 && z < 24.6 ? -1 : z >= 24.6 && z < 27 ? 1 : 0;
            sim.tick(new Controls(0, 0, 1, 0, body, 0, false, 0, 0), 0.05);
            if (sim.airborne && sim.pos.z > 25) flew = true;
            sim.events.clear();
        }
        System.out.printf("copycat: z=%.3f speed=%.2f pitch=%.1f bail=%s%n", sim.pos.z, sim.speed(), Math.toDegrees(sim.pitch), sim.bailReason);
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(sim.pos.z > 30, "must ride past the ramp");
        assertTrue(flew, "the lip should launch the bike");
    }

    @Test void treadStaysSupportedJustPastTheLip() {
        BikeSim sim = new BikeSim(new BikeParams(), kicker());
        sim.place(.5, 64, 25.47, 0);
        V3 shift = new V3(0, 65.35 - sim.pos.y, 0);
        sim.pos = sim.pos.add(shift);
        sim.riderPos = sim.riderPos.add(shift);
        sim.tick(Controls.NONE, .01);
        assertTrue(sim.front.contact, "round tyre should still overlap the ramp behind its axle");
        assertTrue(sim.front.normal.z < -.2, "contact must come from the ramp, not the floor below");
    }

    @Test void bothBikesRideRampsInEveryDirection() {
        for (BikeType type : BikeType.values()) for (int direction = 0; direction < 4; direction++) {
            double yaw = direction * Math.PI / 2;
            V3 f = new V3(-Math.sin(yaw), 0, Math.cos(yaw));
            Terrain base = kicker();
            Terrain rotated = new Terrain() {
                public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                    if (!base.ground(.5, x * f.x + z * f.z, top, bottom, out)) return false;
                    out.normal = new V3(f.x * out.normal.z, out.normal.y, f.z * out.normal.z);
                    return true;
                }
                public boolean solidAt(double x, double y, double z) { return base.solidAt(.5, y, x * f.x + z * f.z); }
            };
            BikeSim sim = new BikeSim(type.params(), rotated);
            sim.bikeType = type;
            sim.place(f.x * 2.5, 64, f.z * 2.5, yaw);
            sim.vel = sim.riderVel = f.mul(11);
            boolean airborne = false, landed = false;
            for (int t = 0; t < 180; t++) {
                double z = sim.pos.dot(f);
                float body = z > 19.5 && z < 24.6 ? -1 : z >= 24.6 && z < 27 ? 1 : 0;
                sim.tick(new Controls(0, 0, 1, 0, body, 0, false, 0, 0), .05);
                airborne |= sim.airborne && z > 25;
                landed |= airborne && !sim.airborne && z > 28;
                sim.events.clear();
            }
            assertFalse(sim.bailed, type + " facing " + direction + ": " + sim.bailReason);
            assertTrue(sim.pos.dot(f) > 38 && landed, type + " facing " + direction);
        }
    }

    @Test void gentleSidewaysLandingIsASkidRatherThanABail() {
        BikeParams p = new BikeParams();
        p.airAlignAssist = 0;
        BikeSim sim = new BikeSim(p, TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, Math.PI / 2);
        sim.pos = sim.pos.add(new V3(0, 2, 0));
        sim.riderPos = sim.riderPos.add(new V3(0, 2, 0));
        sim.vel = sim.riderVel = new V3(0, 0, 6);
        for (int i = 0; i < 60; i++) { sim.tick(Controls.NONE, .05); sim.events.clear(); }
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(sim.pos.y > 64, "bike should remain above the floor");
    }

    @Test void crashKeepsTheRidersLaunchVelocity() {
        Terrain wall = TestTerrains.blocks((x, z) -> z >= 10 ? 68 : 64, Terrain.Surface.DIRT);
        BikeSim sim = new BikeSim(new BikeParams(), wall);
        sim.place(0, 64, 2, 0);
        sim.vel = sim.riderVel = new V3(0, 0, 12);
        for (int i = 0; i < 40 && !sim.bailed; i++) sim.tick(Controls.NONE, .05);
        assertTrue(sim.bailed);
        assertTrue(sim.crashRiderVel.z > 8, "rider must be thrown with pre-collision momentum");
    }
}
