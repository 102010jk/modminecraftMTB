package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** An airbag is absolute safety: whatever the landing, it never bails, and it rights an upturned bike. */
class AirbagSafetyTest {
    static final double FLOOR = 64;

    /** Flat cushion floor, or plain dirt for the control runs. */
    static Terrain floor(Terrain.Surface s) {
        return TestTerrains.flat(FLOOR, s);
    }

    /** Drops a bike from {@code height} metres with a given attitude and speed, returns the sim after 4 s. */
    static BikeSim drop(Terrain t, double height, double yaw, double pitch, V3 v, double spinPitch) {
        BikeParams p = new BikeParams();
        p.riskReward = true;
        p.airAlignAssist = 0;     // no self-levelling: the attitude is the test
        BikeSim sim = new BikeSim(p, t);
        sim.place(0, FLOOR, 0, yaw);
        V3 lift = new V3(0, height, 0);
        sim.pos = sim.pos.add(lift);
        sim.pitch = pitch;
        sim.riderPos = sim.pos.addScaled(sim.upAxis(), p.riderHeight).addScaled(sim.forward(), p.riderForward);
        sim.vel = sim.riderVel = v;
        sim.omega = sim.rightAxis().mul(spinPitch);
        for (int i = 0; i < 80; i++) {
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
        }
        return sim;
    }

    @Test void veryHardDropOnAirbagDoesNotBail() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 16, 0, 0, new V3(0, 0, 4), 0);
        assertFalse(sim.bailed, sim.bailReason);
        BikeSim control = drop(floor(Terrain.Surface.DIRT), 16, 0, 0, new V3(0, 0, 4), 0);
        assertTrue(control.bailed, "the same drop on dirt must be a crash, or this test proves nothing");
    }

    @Test void noseDownLandingOnAirbagDoesNotBail() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 3, 0, -1.9, new V3(0, 0, 6), 0);
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(drop(floor(Terrain.Surface.DIRT), 3, 0, -1.9, new V3(0, 0, 6), 0).bailed);
    }

    @Test void sidewaysLandingOnAirbagDoesNotBail() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 2, Math.PI / 2, 0, new V3(0, 0, 7), 0);
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(drop(floor(Terrain.Surface.DIRT), 2, Math.PI / 2, 0, new V3(0, 0, 7), 0).bailed);
    }

    @Test void loopedOutOverTheBarsOnAirbagDoesNotBail() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 4, 0, 2.6, new V3(0, 0, 5), 3.5);
        assertFalse(sim.bailed, sim.bailReason);
    }

    @Test void headFirstOntoAirbagDoesNotBail() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 6, 0, -Math.PI / 2, new V3(0, -6, 3), -2);
        assertFalse(sim.bailed, sim.bailReason);
    }

    @Test void upsideDownOnAirbagIsRightedGently() {
        BikeSim sim = drop(floor(Terrain.Surface.AIRBAG), 1.5, 0, Math.PI, new V3(0, 0, 0.5), 0);
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(Math.abs(sim.pitch) < Math.toRadians(40), "the bike should be back on its wheels, pitch=" + Math.toDegrees(sim.pitch));
        assertTrue(sim.speed() < 3, "no trampoline");
    }

    @Test void noTrampolineRebound() {
        BikeParams p = new BikeParams();
        BikeSim sim = new BikeSim(p, floor(Terrain.Surface.AIRBAG));
        sim.place(0, FLOOR, 0, 0);
        sim.pos = sim.pos.add(new V3(0, 8, 0));
        sim.riderPos = sim.riderPos.add(new V3(0, 8, 0));
        sim.vel = sim.riderVel = new V3(0, 0, 2);
        double peakAfter = 0;
        boolean landed = false;
        for (int i = 0; i < 100; i++) {
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
            if (sim.grounded()) landed = true;
            else if (landed) peakAfter = Math.max(peakAfter, sim.pos.y - (FLOOR + 0.6));
        }
        assertTrue(landed);
        assertTrue(peakAfter < 0.25, "rebounded " + peakAfter + " m");
    }

    /** Wall of cushion at z >= 20 on a dirt floor. */
    static Terrain airbagWall() {
        Terrain flat = TestTerrains.flat(FLOOR, Terrain.Surface.DIRT);
        return new Terrain() {
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                return flat.ground(x, z, top, bottom, out);
            }
            public boolean solidAt(double x, double y, double z) {
                return y < FLOOR - 0.05 || (z >= 20 && y < FLOOR + 6);
            }
            @Override public Surface surfaceAt(double x, double y, double z) {
                return z >= 19.9 ? Surface.AIRBAG : Surface.DIRT;
            }
        };
    }

    @Test void wallCrashIntoAirbagDoesNotBail() {
        BikeSim sim = new BikeSim(new BikeParams(), airbagWall());
        sim.place(0, FLOOR, 12, 0);
        sim.vel = sim.riderVel = new V3(0, 0, 14);
        for (int i = 0; i < 80; i++) {
            sim.tick(new Controls(0, 0, 1, 0, 0, 0, false, 0, 0), 0.05);
            sim.events.clear();
        }
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(sim.pos.z < 20.5, "the cushion still stops the bike, z=" + sim.pos.z);
    }

    @Test void sameWallOfDirtIsACrash() {
        Terrain wall = new Terrain() {
            final Terrain flat = TestTerrains.flat(FLOOR, Terrain.Surface.DIRT);
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                return flat.ground(x, z, top, bottom, out);
            }
            public boolean solidAt(double x, double y, double z) {
                return y < FLOOR - 0.05 || (z >= 20 && y < FLOOR + 6);
            }
        };
        BikeSim sim = new BikeSim(new BikeParams(), wall);
        sim.place(0, FLOOR, 12, 0);
        sim.vel = sim.riderVel = new V3(0, 0, 14);
        for (int i = 0; i < 80 && !sim.bailed; i++) {
            sim.tick(new Controls(0, 0, 1, 0, 0, 0, false, 0, 0), 0.05);
            sim.events.clear();
        }
        assertTrue(sim.bailed, "a 50 km/h wall hit on dirt must bail (control run)");
    }
}
