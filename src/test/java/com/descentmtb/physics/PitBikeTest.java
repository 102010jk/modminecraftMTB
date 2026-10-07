package com.descentmtb.physics;

import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pit bike: a light 125 cc four-speed. Quick off the line, ~70 km/h flat out, nimbler than the dirt bike. */
class PitBikeTest {
    private static final Controls GAS = new Controls(0, 0, 1, 0, 0, 0, false, 0, 0);
    private static final Controls GAS_LEAN_BACK = new Controls(0, -1, 1, 0, 0, 0, false, 0, 0);

    private static BikeSim pit(double speed) {
        BikeSim s = new BikeSim(BikeType.PIT_BIKE.params(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        s.vel = s.riderVel = new V3(0, 0, speed);
        return s;
    }

    @Test void isAMotorbikeWithItsOwnSavedOrdinal() {
        assertTrue(BikeType.PIT_BIKE.motor());
        assertEquals(BikeType.PIT_BIKE.ordinal() - 1, BikeType.DIRT_BIKE.ordinal(), "appended after the dirt bike: saved by ordinal");
        assertEquals(BikeType.PIT_BIKE, BikeType.byId(BikeType.PIT_BIKE.ordinal()));
        assertEquals(com.descentmtb.trick.Trick.TABLETOP, BikeType.PIT_BIKE.trickAt(1));
    }

    @Test void fullGasFromRestLaunchesHard() {
        BikeSim s = pit(0);
        for (int i = 0; i < 20; i++) s.tick(GAS, .05);
        assertTrue(s.speed() * 3.6 > 20, "light and torquey off the line, 20 km/h in a second: " + s.speed() * 3.6);
        assertTrue(Math.toDegrees(s.pitch) < 20, "but the front stays on the ground without leaning back");
        for (int i = 0; i < 100; i++) s.tick(GAS, .05);
        assertFalse(s.bailed);
        assertTrue(s.speed() * 3.6 > 55, "0-55 km/h in 6 s: " + s.speed() * 3.6);
    }

    @Test void topSpeedIsFourthGearOnTheLimiter() {
        BikeSim s = pit(0);
        int lastGear = 0;
        for (int i = 0; i < 400; i++) {
            s.tick(GAS, .05);
            assertTrue(s.engine.gear >= lastGear, "gears only go up under full gas");
            lastGear = s.engine.gear;
        }
        assertEquals(3, s.engine.gear);
        assertTrue(s.speed() * 3.6 > 60 && s.speed() * 3.6 < 85, "top speed " + s.speed() * 3.6);
        assertTrue(s.engine.rpm > 9000, "on the limiter: " + s.engine.rpm);
    }

    @Test void gasAndLeanBackLiftsAPowerWheelie() {
        BikeSim s = pit(6);
        double pitch = 0;
        for (int i = 0; i < 40; i++) {
            s.tick(GAS_LEAN_BACK, .05);
            pitch = Math.max(pitch, Math.toDegrees(s.pitch));
            assertFalse(s.bailed, "a held wheelie does not loop out by itself");
        }
        assertTrue(pitch > 20, "front wheel up: " + pitch);
        assertFalse(s.front.contact);
    }

    @Test void gasAloneAtSpeedKeepsTheFrontDown() {
        BikeSim s = pit(12);
        double pitch = 0;
        for (int i = 0; i < 60; i++) { s.tick(GAS, .05); pitch = Math.max(pitch, Math.toDegrees(s.pitch)); }
        assertTrue(pitch < 10, "no unwanted wheelie: " + pitch);
    }

    @Test void restingSagIsAboutAThirdOfTheTravel() {
        BikeSim s = pit(0);
        for (int i = 0; i < 60; i++) s.tick(Controls.NONE, .05);
        BikeParams p = s.p;
        assertEquals(0.30, s.front.compression / p.forkTravel, 0.12, "fork sag " + s.front.compression);
        assertEquals(0.30, s.rear.compression / p.shockTravel, 0.12, "shock sag " + s.rear.compression);
    }

    @Test void isLighterAndNimblerThanTheDirtBike() {
        BikeParams pit = BikeType.PIT_BIKE.params(), dirt = BikeType.DIRT_BIKE.params();
        assertTrue(pit.bikeMass < dirt.bikeMass && pit.inertiaPitch < dirt.inertiaPitch && pit.inertiaYaw < dirt.inertiaYaw);
        assertTrue(pit.flipRate > dirt.flipRate && pit.spinRate > dirt.spinRate);
        assertTrue(pit.peakTorque < dirt.peakTorque && pit.limitRpm < dirt.limitRpm);
    }

    @Test void everyMotorTrickInputKeepsTheStateFinite() {
        BikeSim s = pit(14);
        for (int i = 0; i < 200; i++) {
            s.tick(new Controls((float) Math.sin(i * .1), (float) Math.cos(i * .07), 1, i % 40 < 8 ? 1 : 0, 0, 0, false, 0, 0), .05);
            assertTrue(Double.isFinite(s.pos.x + s.pos.y + s.pos.z + s.pitch + s.lean + s.engine.rpm), "NaN at tick " + i);
        }
    }

    @Test void engineStateSurvivesTheSyncPacking() {
        BikeParams p = BikeType.PIT_BIKE.params();
        Engine e = new Engine(p);
        e.rpm = 6100;
        e.throttle = 0.9;
        assertEquals(6100, Engine.decodeRpm(e.encode()), 1);
        assertTrue(Engine.decodeThrottle(e.encode()));
        assertTrue(e.rpm < p.limitRpm);
    }
}
