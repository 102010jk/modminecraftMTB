package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A thrown-off rider leaves the bike tumbling on with its momentum, scraping to a stop in a few seconds. */
class CrashTumbleTest {
    /** A 15 degree descent towards +z. */
    static Terrain slope() {
        return TestTerrains.fn((x, z) -> 200 - 0.27 * z, Terrain.Surface.DIRT);
    }

    static BikeSim crashed(boolean crash, double speed) {
        BikeSim sim = new BikeSim(new BikeParams(), slope());
        sim.riderless = true;
        sim.place(0, 200, 0, 0);
        V3 v = new V3(0, -speed * 0.26, speed * 0.966);
        sim.vel = sim.riderVel = v;
        sim.pitch = -0.26;
        sim.omega = sim.rightAxis().mul(1.5);       // still pitching forward from the crash
        if (crash) sim.beginCrash();
        return sim;
    }

    @Test void crashedBikeKeepsMomentumThenStopsWithinAFewSeconds() {
        BikeSim sim = crashed(true, 14);
        double stopped = -1, startZ = sim.pos.z, speedAt1 = 0, maxZ = startZ;
        for (int i = 0; i < 160; i++) {
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
            assertTrue(sim.stateFinite());
            if (i == 19) speedAt1 = sim.speed();
            maxZ = Math.max(maxZ, sim.pos.z);
            if (stopped < 0 && sim.speed() < 0.6) stopped = (i + 1) * 0.05;
        }
        System.out.printf("crash: speed after 1 s %.1f m/s, stopped at %.2f s, slid %.1f m%n", speedAt1, stopped, maxZ - startZ);
        assertTrue(speedAt1 > 5, "it does not stop dead: " + speedAt1);
        assertTrue(stopped > 1.2 && stopped < 5.5, "stops after a few seconds, was " + stopped);
        assertTrue(maxZ - startZ > 8, "carries on down the slope");
    }

    @Test void aBikeThatWasNotCrashedKeepsRollingDownTheSlope() {
        BikeSim sim = crashed(false, 14);
        for (int i = 0; i < 60; i++) { sim.tick(Controls.NONE, 0.05); sim.events.clear(); }
        BikeSim crash = crashed(true, 14);
        for (int i = 0; i < 60; i++) { crash.tick(Controls.NONE, 0.05); crash.events.clear(); }
        assertTrue(sim.speed() > crash.speed() + 2, "crash friction slows it: " + sim.speed() + " vs " + crash.speed());
    }

    @Test void crashedBikeNeverEndsUpUnderTheGround() {
        BikeSim sim = crashed(true, 20);
        for (int i = 0; i < 160; i++) {
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
            double ground = 200 - 0.27 * sim.pos.z;
            assertTrue(sim.pos.y > ground - 0.05, "below the slope at tick " + i + ": " + (sim.pos.y - ground));
        }
    }
}
