package com.descentmtb.physics;

import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RideSafetyTest {
    @Test void acceleratesToUsefulTrailSpeedFromRest() {
        for (BikeType type : BikeType.values()) {
            if (type.motor()) continue;   // pedal power only; the dirt bike has its own engine tests (DirtBikeTest)
            BikeSim s = new BikeSim(type.params(), TestTerrains.flat(64, Terrain.Surface.DIRT));
            s.place(0, 64, 0, 0);
            for (int i = 0; i < 100; i++) s.tick(new Controls(0, 0, 1, 0, 0, 0, false, 0, 0), .05);
            assertTrue(s.speed() * 3.6 >= 20 && s.speed() * 3.6 <= 34, type + " " + s.speed() * 3.6);
            assertFalse(s.bailed);
        }
    }
    @Test void loopedManualBailsEvenWithoutWheelContact() {
        BikeSim s = new BikeSim(new BikeParams(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        s.pitch = Math.toRadians(95);
        s.riderPos = s.pos.addScaled(s.upAxis(), s.p.riderHeight).addScaled(s.forward(), s.p.riderForward);
        s.vel = s.riderVel = new V3(0, 0, 2);
        for (int i = 0; i < 200 && !s.bailed; i++)
            s.tick(new Controls(0, -1, 1, 0, 0, 0, false, 0, 0), .05);
        assertTrue(s.bailed, "cannot freeze inverted without dismounting");
    }
    @Test void fastRiderlessFallCannotPassThroughThinFloor() {
        Terrain floor = new Terrain() {
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                if (top < 64 || bottom > 64) return false;
                out.set(64, V3.Y, Surface.ROCK); return true;
            }
            public boolean solidAt(double x, double y, double z) { return y > 63.9 && y < 64; }
        };
        for (BikeType type : BikeType.values()) for (int side : new int[]{-1, 1}) {
            BikeSim s = new BikeSim(type.params(), floor);
            s.riderless = true; s.place(0, 64, 0, 0);
            s.pos = s.pos.add(new V3(0, 5, 0)); s.lean = side;
            s.vel = new V3(8, -35, 0);
            for (int i = 0; i < 200; i++) {
                s.tick(Controls.NONE, .05);
                assertTrue(s.pos.y > 63.9, type + " fell through at " + i);
            }
        }
    }
}
