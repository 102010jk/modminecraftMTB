package com.descentmtb.physics;

import com.descentmtb.custom.MotoBuild;
import com.descentmtb.custom.MotoBuild.Exhaust;
import com.descentmtb.custom.MotoBuild.Suspension;
import com.descentmtb.custom.MotoTuning;
import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** What sprocket, pipe and suspension do to the bike's physics. */
class MotoTuningTest {
    private static final Controls GAS = new Controls(0, 0, 1, 0, 0, 0, false, 0, 0);
    private static final BikeType[] MOTOS = {BikeType.DIRT_BIKE, BikeType.PIT_BIKE};

    private static BikeParams tuned(BikeType type, MotoBuild build) {
        BikeParams p = type.params();
        MotoTuning.apply(p, type.params(), build);
        return p;
    }

    private static BikeSim ride(BikeParams p, double seconds) {
        BikeSim s = new BikeSim(p, TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        for (int i = 0; i < (int) (seconds / .05); i++) s.tick(GAS, .05);
        return s;
    }

    /**
     * Drive force at the rear tyre (N) at full throttle in second gear with the clutch in, at the road speed where
     * the STOCK gearing turns the engine at {@code stockRpm}: what a sprocket change does at a given speed.
     */
    private static double driveForce(BikeType type, MotoBuild build, double stockRpm) {
        BikeParams p = tuned(type, build);
        Engine e = new Engine(p);
        e.gear = 1;
        e.rpm = stockRpm * Engine.ratio(p, 1) / Engine.ratio(type.params(), 1);     // already revving: no downshift
        double omega = stockRpm / Engine.ratio(type.params(), 1) * 2 * Math.PI / 60;
        double force = 0;
        for (int i = 0; i < 60; i++) force = e.step(p, 1, omega, true, .01);
        return force;
    }

    @Test void theStockBuildChangesNothing() {
        for (BikeType type : MOTOS) {
            BikeParams p = tuned(type, MotoBuild.DEFAULT), base = type.params();
            assertEquals(base.finalRatio, p.finalRatio, 1e-12);
            assertEquals(base.peakTorque, p.peakTorque, 1e-12);
            assertEquals(base.forkRate, p.forkRate, 1e-9);
            assertEquals(base.shockRate, p.shockRate, 1e-9);
            assertEquals(base.shockCompDamp, p.shockCompDamp, 1e-9);
            assertEquals(base.forkRebDamp, p.forkRebDamp, 1e-9);
        }
    }

    @Test void stockTeethMatchTheRealBikes() {
        assertEquals(51, BikeType.DIRT_BIKE.params().rearSprocket);
        assertEquals(37, BikeType.PIT_BIKE.params().rearSprocket);
    }

    @Test void aBiggerSprocketGearsDownByTheToothRatio() {
        BikeParams dirt = tuned(BikeType.DIRT_BIKE, MotoBuild.DEFAULT.withSprocket(3));
        assertEquals(BikeType.DIRT_BIKE.params().finalRatio * 54 / 51, dirt.finalRatio, 1e-9);
        BikeParams pit = tuned(BikeType.PIT_BIKE, MotoBuild.DEFAULT.withSprocket(-3));
        assertEquals(BikeType.PIT_BIKE.params().finalRatio * 34 / 37, pit.finalRatio, 1e-9);
    }

    @Test void sprocketTradesPullForTopSpeed() {
        for (BikeType type : MOTOS) {
            double shortPull = driveForce(type, MotoBuild.DEFAULT.withSprocket(3), 6000);
            double stockPull = driveForce(type, MotoBuild.DEFAULT, 6000);
            double longPull = driveForce(type, MotoBuild.DEFAULT.withSprocket(-3), 6000);
            assertTrue(shortPull > stockPull * 1.03, type + ": + teeth push harder at the same road speed: " + shortPull + " vs " + stockPull);
            assertTrue(stockPull > longPull * 1.03, type + ": - teeth push softer: " + stockPull + " vs " + longPull);
            BikeSim shorter = ride(tuned(type, MotoBuild.DEFAULT.withSprocket(3)), 25);
            BikeSim stock = ride(tuned(type, MotoBuild.DEFAULT), 25);
            BikeSim longer = ride(tuned(type, MotoBuild.DEFAULT.withSprocket(-3)), 25);
            assertTrue(shorter.speed() < stock.speed() - 0.5, type + ": + teeth top out lower " + shorter.speed() + " vs " + stock.speed());
            assertTrue(longer.speed() > stock.speed() + 0.5, type + ": - teeth top out higher " + longer.speed() + " vs " + stock.speed());
        }
    }

    @Test void theHintTopSpeedFollowsTheSprocket() {
        for (BikeType type : MOTOS) {
            double stock = MotoTuning.gearedTopSpeedKmh(tuned(type, MotoBuild.DEFAULT));
            assertTrue(MotoTuning.gearedTopSpeedKmh(tuned(type, MotoBuild.DEFAULT.withSprocket(3))) < stock);
            assertTrue(MotoTuning.gearedTopSpeedKmh(tuned(type, MotoBuild.DEFAULT.withSprocket(-3))) > stock);
        }
        double pit = MotoTuning.gearedTopSpeedKmh(BikeType.PIT_BIKE.params());
        assertTrue(pit > 70 && pit < 90, "pit bike geared for ~75-80 km/h: " + pit);
    }

    @Test void theRacePipeAddsEightPercentTorque() {
        for (BikeType type : MOTOS) {
            MotoBuild race = MotoBuild.DEFAULT.withExhaust(Exhaust.RACE);
            assertEquals(type.params().peakTorque * 1.08, tuned(type, race).peakTorque, 1e-9);
            assertEquals(1.08, driveForce(type, race, 6000) / driveForce(type, MotoBuild.DEFAULT, 6000), 1e-6,
                    type + ": the push at the tyre grows by the same 8%");
        }
    }

    @Test void suspensionScalesSpringsAndDampingBySquareRoot() {
        for (BikeType type : MOTOS) {
            BikeParams base = type.params();
            for (Suspension s : Suspension.values()) {
                BikeParams p = tuned(type, MotoBuild.DEFAULT.withSuspension(s));
                double k = s.spring;
                assertEquals(base.forkRate * k, p.forkRate, 1e-6);
                assertEquals(base.shockRate * k, p.shockRate, 1e-6);
                assertEquals(base.forkCompDamp * Math.sqrt(k), p.forkCompDamp, 1e-6);
                assertEquals(base.shockRebDamp * Math.sqrt(k), p.shockRebDamp, 1e-6);
            }
            assertEquals(0.85, Suspension.SOFT.spring);
            assertEquals(1.15, Suspension.STIFF.spring);
        }
    }

    @Test void softSagsMoreThanStiffOnTheRealSimulation() {
        for (BikeType type : MOTOS) {
            double soft = restSag(tuned(type, MotoBuild.DEFAULT.withSuspension(Suspension.SOFT)));
            double stiff = restSag(tuned(type, MotoBuild.DEFAULT.withSuspension(Suspension.STIFF)));
            assertTrue(soft > stiff + 0.002, type + ": soft " + soft + " vs stiff " + stiff);
        }
    }

    private static double restSag(BikeParams p) {
        BikeSim s = new BikeSim(p, TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, 0);
        for (int i = 0; i < 80; i++) s.tick(Controls.NONE, .05);
        return s.front.compression + s.rear.compression;
    }

    @Test void applyingAfterThePressureTuningNeverCompounds() {
        BikeType type = BikeType.PIT_BIKE;
        BikeParams p = type.params(), base = type.params();
        MotoBuild build = MotoBuild.DEFAULT.withSuspension(Suspension.STIFF).withSprocket(2).withExhaust(Exhaust.RACE);
        for (int tick = 0; tick < 5; tick++) {          // the client does both, every tick
            BikeTuning.apply(p, base, 26, 28, 80, 1);
            MotoTuning.apply(p, base, build);
        }
        BikeParams once = type.params();
        BikeTuning.apply(once, base, 26, 28, 80, 1);
        MotoTuning.apply(once, base, build);
        assertEquals(once.forkRate, p.forkRate, 1e-9);
        assertEquals(once.shockRate, p.shockRate, 1e-9);
        assertEquals(once.finalRatio, p.finalRatio, 1e-12);
        assertEquals(once.peakTorque, p.peakTorque, 1e-12);
    }
}
