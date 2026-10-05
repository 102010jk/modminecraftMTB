package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

/**
 * Where does the speed go? Coasts (no pedal, no brake, no body input) over shapes and compares the energy that is
 * left with what drag + rolling resistance alone would take. Anything beyond that is lost in the suspension, the
 * rider's legs or contact handling - the bike "doesn't keep its speed".
 */
class EnergyTest {
    static final double DT = 0.05;

    record Run(double v0, double v1, double h0, double h1, double lostJPerKg, double dragRollJPerKg, double distance) {
        double extra() {
            return lostJPerKg - dragRollJPerKg;
        }
    }

    static Run coast(Terrain terrain, double speed, double seconds, double stopAtZ) {
        return coast(new BikeParams(), terrain, speed, seconds, stopAtZ);
    }

    static Run coast(BikeParams p, Terrain terrain, double speed, double seconds, double stopAtZ) {
        BikeSim sim = new BikeSim(p, terrain);
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(speed);
        sim.riderVel = sim.vel;
        for (int i = 0; i < 10; i++) {                       // settle the suspension first
            sim.tick(Controls.NONE, DT);
            sim.events.clear();
        }
        double m = p.totalMass();
        double v0 = sim.speed(), h0 = com(sim, p), z0 = sim.pos.z, dragRoll = 0;
        int n = (int) (seconds / DT);
        for (int i = 0; i < n && sim.pos.z < stopAtZ; i++) {
            double v = sim.speed();
            double drag = .5 * p.airDensity * p.dragArea * v * v;
            double roll = sim.grounded() ? .02 * m * p.gravity : 0;
            dragRoll += (drag + roll) * v * DT / m;          // J/kg
            sim.tick(Controls.NONE, DT);
            sim.events.clear();
        }
        double v1 = sim.speed(), h1 = com(sim, p);
        double lost = (.5 * v0 * v0 + p.gravity * h0) - (.5 * v1 * v1 + p.gravity * h1);
        return new Run(v0, v1, h0, h1, lost, dragRoll, sim.pos.z - z0);
    }

    /** Height of the combined centre of mass. */
    static double com(BikeSim sim, BikeParams p) {
        return (sim.pos.y * p.bikeMass + sim.riderPos.y * p.riderMass) / p.totalMass();
    }

    static void print(String name, Run r) {
        System.out.printf(Locale.ROOT, "[energy] %-26s v %.2f -> %.2f m/s over %.1f m | lost %.1f J/kg, drag+rolling %.1f, EXTRA %.1f J/kg (%.0f%% of the entry KE)%n",
                name, r.v0, r.v1, r.distance, r.lostJPerKg, r.dragRollJPerKg, r.extra(), 100 * r.extra() / (.5 * r.v0 * r.v0));
    }

    static Terrain fn(DoubleBinaryOperator h) {
        return TestTerrains.fn(h, Terrain.Surface.DIRT);
    }

    @Test void audit() {
        print("flat", coast(TestTerrains.flat(64, Terrain.Surface.DIRT), 9, 3, 1e9));
        print("roller 0.5 m / 6 m", coast(fn((x, z) -> 64 + (z > 8 && z < 14 ? .5 * Math.pow(Math.sin(Math.PI * (z - 8) / 6), 2) : 0)), 9, 3, 1e9));
        print("roller 1.0 m / 8 m", coast(fn((x, z) -> 64 + (z > 8 && z < 16 ? 1.0 * Math.pow(Math.sin(Math.PI * (z - 8) / 8), 2) : 0)), 9, 3, 1e9));
        // concave kicker 1.5 m over 3 m (quarter circle-ish), measure at the lip
        print("kicker 1.5 m / 3 m", coast(fn((x, z) -> 64 + (z < 8 ? 0 : z < 11 ? 1.5 * Math.pow((z - 8) / 3, 2) : 1.5)), 9.2, 3, 11.0));
        print("kicker 1.5 m / 5 m", coast(fn((x, z) -> 64 + (z < 8 ? 0 : z < 13 ? 1.5 * Math.pow((z - 8) / 5, 2) : 1.5)), 9.2, 3, 13.0));
        print("10 deg slope", coast(fn((x, z) -> 64 - .176 * Math.max(0, z - 6)), 6, 3, 1e9));
        print("blocks: 0.5 m steps up", coast(TestTerrains.blocks((x, z) -> 64 + Math.max(0, Math.min(3, Math.floor((z - 6) / 3.0))) * .5, Terrain.Surface.DIRT), 9, 2.5, 1e9));
        print("blocks: 1 m steps down", coast(TestTerrains.blocks((x, z) -> 70 - Math.max(0, Math.min(5, Math.floor((z - 6) / 4.0))), Terrain.Surface.DIRT), 7, 2.5, 1e9));
    }

    @Test void channels() {
        Terrain kicker = fn((x, z) -> 64 + (z < 8 ? 0 : z < 11 ? 1.5 * Math.pow((z - 8) / 3, 2) : 1.5));
        Terrain roller = fn((x, z) -> 64 + (z > 8 && z < 14 ? .5 * Math.pow(Math.sin(Math.PI * (z - 8) / 6), 2) : 0));
        String[] names = {"default", "legs damping 300", "suspension damping /3", "both", "rider rigid (stiff legs)", "no rolling resistance", "24 substeps", "pitch inertia x4", "lateral stiffness 1"};
        for (int k = 0; k < names.length; k++) {
            BikeParams[] ps = {new BikeParams(), new BikeParams()};
            for (BikeParams p : ps) {
                if (k == 1 || k == 3) p.legDamping = 300;
                if (k == 2 || k == 3) { p.forkCompDamp /= 3; p.forkRebDamp /= 3; p.shockCompDamp /= 3; p.shockRebDamp /= 3; }
                if (k == 4) { p.legStiffness = 40000; p.legDamping = 3000; }
                if (k == 5) p.tyreRolling = 0;
                if (k == 6) p.substeps = 24;
                if (k == 7) p.inertiaPitch *= 4;
                if (k == 8) p.lateralStiffness = 1;
            }
            Run a = coast(ps[0], kicker, 9.2, 3, 11.0), b = coast(ps[1], roller, 9, 3, 1e9);
            System.out.printf(Locale.ROOT, "[energy-ch] %-26s kicker extra %.1f J/kg (lip %.2f m/s) | roller extra %.1f J/kg (exit %.2f)%n", names[k], a.extra(), a.v1, b.extra(), b.v1);
        }
    }
}
