package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Steering must feel planted: full lock on a keyboard (digital, ramped like the client does) holds the
 * line on dirt, a turn never asks for more than the surface can give, and sliding is a choice
 * (rear brake + lock), not what the bike does by itself.
 */
class SteeringTest {
    static final double DT = 0.05;

    record Turn(double slidingFront, double slidingRear, double maxRearSlipDeg, double latG, boolean bailed, String why, double speedEnd) {}

    /** Keyboard-like steer: 0 -> 1 over {@code ramp} seconds. */
    static IntToDoubleFunction keyboardRamp(double ramp) {
        return tick -> Math.min(1, tick * DT / ramp);
    }

    static Turn turn(Terrain.Surface surface, double speed, double seconds, IntToDoubleFunction steer, double brake) {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(64, surface));
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(speed);
        sim.riderVel = sim.vel;
        int n = (int) (seconds / DT), front = 0, rear = 0;
        double maxSlip = 0, lat = 0;
        for (int i = 0; i < n && !sim.bailed; i++) {
            sim.tick(new Controls((float) steer.applyAsDouble(i), 0, 0, (float) brake, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            if (sim.front.sliding) front++;
            if (sim.rear.sliding) rear++;
            V3 patchVel = sim.vel.add(sim.omega.cross(sim.rear.patch.sub(sim.pos)));
            double slip = Math.abs(Math.atan2(patchVel.dot(sim.rear.tL), Math.max(.1, Math.abs(patchVel.dot(sim.rear.tF)))));
            if (i > 6) maxSlip = Math.max(maxSlip, Math.toDegrees(slip));
            if (i >= n - 10) lat = Math.max(lat, Math.abs(sim.omega.dot(V3.Y)) * sim.vel.horizontalLength() / 9.81);
        }
        Turn t = new Turn(front / (double) n, rear / (double) n, maxSlip, lat, sim.bailed, sim.bailReason, sim.speed());
        System.out.printf(Locale.ROOT, "[steer] %-6s %4.1f m/s brake %.1f: sliding F %.0f%% R %.0f%%, rear slip max %.1f°, %.2f g, end %.1f m/s, bail=%s%n",
                surface, speed, brake, t.slidingFront * 100, t.slidingRear * 100, t.maxRearSlipDeg, t.latG, t.speedEnd, t.bailed);
        return t;
    }

    @Test void keyboardFullLockDoesNotDriftOnDirt() {
        for (double v : new double[]{6, 9, 12}) {
            Turn t = turn(Terrain.Surface.DIRT, v, 3, keyboardRamp(.22), 0);
            assertFalse(t.bailed, t.why);
            // (the rear may twitch briefly while the yaw spins up)
            assertTrue(t.slidingFront < .05 && t.slidingRear < .15, "no sliding at " + v + " m/s: F " + t.slidingFront + " R " + t.slidingRear);
            assertTrue(t.maxRearSlipDeg < 6, "rear slip angle " + t.maxRearSlipDeg + "° at " + v + " m/s");
        }
    }

    @Test void gripLimitedCarveStaysUnderMu() {
        for (var s : new Terrain.Surface[]{Terrain.Surface.DIRT, Terrain.Surface.GRASS, Terrain.Surface.GRAVEL}) {
            Turn t = turn(s, 9, 3, keyboardRamp(.22), 0);
            assertTrue(t.latG <= s.grip * 1.02, s + ": " + t.latG + " g exceeds the surface grip " + s.grip);
        }
    }

    @Test void brakeTurnDriftsOnPurpose() {
        Turn t = turn(Terrain.Surface.GRASS, 9, 2, keyboardRamp(.22), 1.0);
        assertFalse(t.bailed, t.why);
        assertTrue(t.slidingRear >= .2, "rear brake + full lock should slide the rear (" + t.slidingRear + ")");
    }

    @Test void looseSurfacesSlideMoreThanDirt() {
        Turn dirt = turn(Terrain.Surface.DIRT, 9, 2, keyboardRamp(.22), 1.0);
        Turn gravel = turn(Terrain.Surface.GRAVEL, 9, 2, keyboardRamp(.22), 1.0);
        assertTrue(gravel.slidingRear >= dirt.slidingRear, "gravel " + gravel.slidingRear + " vs dirt " + dirt.slidingRear);
    }

    @Test void iceLimitsTheTurnInsteadOfGrippingLikeDirt() {
        Turn ice = turn(Terrain.Surface.ICE, 9, 2, keyboardRamp(.22), 0);
        Turn dirt = turn(Terrain.Surface.DIRT, 9, 2, keyboardRamp(.22), 0);
        assertTrue(ice.latG < .3 && ice.latG < dirt.latG * .4, "ice " + ice.latG + " g vs dirt " + dirt.latG + " g");
    }

    @Test void leaningForwardWhileSteeringDriftsTheRear() {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(9);
        sim.riderVel = sim.vel;
        int rear = 0, n = 40;
        for (int i = 0; i < n && !sim.bailed; i++) {
            sim.tick(new Controls(1, 1, 0, 0, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            if (sim.rear.sliding) rear++;
        }
        System.out.printf(Locale.ROOT, "[steer] lean-forward drift: rear sliding %.0f%%, bail=%s%n", rear * 100.0 / n, sim.bailed);
        assertFalse(sim.bailed, sim.bailReason);
        assertTrue(rear >= n * .3, "lean forward + full lock should slide the rear (" + rear + "/" + n + ")");
    }

    @Test void visibleLeanStaysModest() {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(10);
        sim.riderVel = sim.vel;
        double max = 0;
        for (int i = 0; i < 60 && !sim.bailed; i++) {
            sim.tick(new Controls(1, 0, 0, 0, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            max = Math.max(max, Math.abs(sim.lean));
        }
        System.out.printf(Locale.ROOT, "[steer] full-lock visible lean max %.0f°%n", Math.toDegrees(max));
        assertTrue(max <= new BikeParams().leanMax + 1e-9, "lean " + Math.toDegrees(max));
    }
}
