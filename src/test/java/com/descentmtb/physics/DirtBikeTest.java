package com.descentmtb.physics;

import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The dirt bike: engine torque on the rear wheel, gears, wheelspin, power wheelies, engine braking. */
class DirtBikeTest {
    private static final Controls GAS = new Controls(0, 0, 1, 0, 0, 0, false, 0, 0);
    private static final Controls GAS_LEAN_BACK = new Controls(0, -1, 1, 0, 0, 0, false, 0, 0);

    private static BikeSim moto(double speed) {
        BikeSim s = new BikeSim(BikeType.DIRT_BIKE.params(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        s.vel = s.riderVel = new V3(0, 0, speed);
        return s;
    }

    @Test void fullGasFromRestLightsUpTheRearAndPullsHard() {
        BikeSim s = moto(0);
        double spin = 0;
        for (int i = 0; i < 20; i++) { s.tick(GAS, .05); spin = Math.max(spin, s.wheelspin); }
        assertTrue(spin > 0.05, "rear tyre breaks loose at launch (roost): " + spin);
        for (int i = 0; i < 80; i++) s.tick(GAS, .05);
        assertFalse(s.bailed);
        assertTrue(s.speed() * 3.6 > 85, "0-85 km/h in 5 s: " + s.speed() * 3.6);
    }

    @Test void topSpeedIsFifthGearOnTheLimiter() {
        BikeSim s = moto(0);
        int lastGear = 0;
        for (int i = 0; i < 300; i++) {
            s.tick(GAS, .05);
            assertTrue(s.engine.gear >= lastGear, "gears only go up under full gas");
            lastGear = s.engine.gear;
        }
        assertEquals(4, s.engine.gear);
        assertTrue(s.speed() * 3.6 > 100 && s.speed() * 3.6 < 125, "top speed " + s.speed() * 3.6);
        assertTrue(s.engine.rpm > 10500, "on the limiter: " + s.engine.rpm);
    }

    @Test void gasAndLeanBackLiftsAPowerWheelie() {
        BikeSim s = moto(8);
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
        BikeSim s = moto(15);
        double pitch = 0;
        for (int i = 0; i < 60; i++) { s.tick(GAS, .05); pitch = Math.max(pitch, Math.toDegrees(s.pitch)); }
        assertTrue(pitch < 10, "no unwanted wheelie: " + pitch);
    }

    @Test void leanBackWithoutGasIsNoManualOnAHeavyBike() {
        BikeSim s = moto(8);
        double pitch = 0;
        for (int i = 0; i < 30; i++) { s.tick(new Controls(0, -1, 0, 0, 0, 0, false, 0, 0), .05); pitch = Math.max(pitch, Math.toDegrees(s.pitch)); }
        assertTrue(pitch < 12, "105 kg does not manual off the rider's weight alone: " + pitch);
    }

    @Test void closingTheThrottleBrakesWithTheEngine() {
        BikeSim coast = moto(20), roll = moto(20);
        roll.p.engineBrakeTorque = 0;
        for (int i = 0; i < 40; i++) { coast.tick(Controls.NONE, .05); roll.tick(Controls.NONE, .05); }
        assertTrue(coast.speed() < roll.speed() - 0.5, "engine braking: " + coast.speed() + " vs " + roll.speed());
        assertTrue(coast.speed() > 8, "but it is no brake: " + coast.speed());
    }

    @Test void snappingTheThrottleShutAtHighRevsBackfires() {
        Engine e = new Engine(BikeType.DIRT_BIKE.params());
        BikeParams p = BikeType.DIRT_BIKE.params();
        for (int i = 0; i < 40; i++) e.step(p, 1, 0, false, 0.01);   // rev it in the air
        assertTrue(e.rpm > p.backfireRpm);
        boolean popped = false;
        for (int i = 0; i < 20; i++) { e.step(p, 0, 0, false, 0.01); popped |= e.takeBackfire(); }
        assertTrue(popped);
        assertFalse(e.takeBackfire(), "one pop per snap");
    }

    @Test void engineStateSurvivesTheSyncPacking() {
        Engine e = new Engine(BikeType.DIRT_BIKE.params());
        e.rpm = 7300;
        e.throttle = 0.9;
        assertEquals(7300, Engine.decodeRpm(e.encode()), 1);
        assertTrue(Engine.decodeThrottle(e.encode()));
        e.throttle = 0;
        assertFalse(Engine.decodeThrottle(e.encode()));
    }

    @Test void bicyclesHaveNoEngine() {
        BikeSim s = new BikeSim(BikeType.ENDURO.params(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        s.tick(GAS, .05);
        assertNull(s.engine);
    }
}
