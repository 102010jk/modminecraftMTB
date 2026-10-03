package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Descenders "feel spec" (PLAN.md §1) as numbers. Each scenario rides a
 * synthetic world headless and checks the outcome; telemetry CSVs land in
 * build/telemetry for tuning.
 */
class FeelSpecTest {
    static final double DT = 0.05;
    static final double KMH = 3.6;

    // ------------------------------------------------------------------ helpers

    static final class Ride {
        final BikeSim sim;
        final String name;
        final StringBuilder csv = new StringBuilder("t,x,y,z,kmh,compF,compR,pitchDeg,riderUp,riderFwd,clear,leanDeg,airborne\n");
        double t;
        double maxY = -1e9, maxClear = -1e9, maxKmh;
        int takeoffs;
        double longestAir;

        Ride(String name, Terrain terrain, BikeParams params) {
            this.name = name;
            this.sim = new BikeSim(params, terrain);
        }

        Ride(String name, Terrain terrain) {
            this(name, terrain, new BikeParams());
        }

        Ride place(double x, double groundY, double z, double yawDeg) {
            sim.place(x, groundY, z, Math.toRadians(yawDeg));
            return this;
        }

        Ride speed(double ms) {
            V3 f = sim.forward();
            sim.vel = f.mul(ms);
            sim.riderVel = f.mul(ms);
            return this;
        }

        Ride run(double seconds, Function<Ride, Controls> input) {
            int n = (int) Math.round(seconds / DT);
            for (int i = 0; i < n; i++) {
                sim.tick(input.apply(this), DT);
                t += DT;
                for (BikeSim.Event e : sim.events) {
                    if (e.type() == BikeSim.Event.Type.TAKEOFF) takeoffs++;
                    if (e.type() == BikeSim.Event.Type.LAND) longestAir = Math.max(longestAir, sim.airTime);
                }
                sim.events.clear();
                double clear = sim.wheelClearance();
                maxY = Math.max(maxY, sim.pos.y);
                maxClear = Math.max(maxClear, clear);
                maxKmh = Math.max(maxKmh, sim.speed() * KMH);
                assertFalse(Double.isNaN(sim.pos.y) || Double.isNaN(sim.vel.x), name + ": NaN at t=" + t);
                csv.append(String.format(java.util.Locale.ROOT, "%.2f,%.3f,%.3f,%.3f,%.2f,%.3f,%.3f,%.1f,%.3f,%.3f,%.3f,%.1f,%d%n",
                        t, sim.pos.x, sim.pos.y, sim.pos.z, sim.speed() * KMH,
                        sim.front.compression, sim.rear.compression, Math.toDegrees(sim.pitch),
                        sim.riderUp, sim.riderFwd, clear, Math.toDegrees(sim.lean), sim.airborne ? 1 : 0));
            }
            return this;
        }

        Ride run(double seconds, Controls c) {
            return run(seconds, r -> c);
        }

        double kmh() {
            return sim.speed() * KMH;
        }

        void save() {
            try {
                Path dir = Path.of("build", "telemetry");
                Files.createDirectories(dir);
                try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir.resolve(name + ".csv")))) {
                    w.print(csv);
                }
            } catch (IOException ignored) {
            }
        }
    }

    static Controls pedal(double v) {
        return new Controls(0, 0, (float) v, 0, 0, 0, false, 0, 0);
    }

    static Controls ctl(double steer, double lean, double pedal, double brake, double body) {
        return new Controls((float) steer, (float) lean, (float) pedal, (float) brake, (float) body, 0, false, 0, 0);
    }

    static void log(String fmt, Object... args) {
        System.out.println("[feel] " + String.format(java.util.Locale.ROOT, fmt, args));
    }

    // ------------------------------------------------------------------ F5 / basics

    @Test
    void restsOnFlatAtSag() {
        Ride r = new Ride("rest", TestTerrains.flat(64, Terrain.Surface.DIRT)).place(0, 64, 0, 0);
        r.run(4, Controls.NONE);
        r.save();
        BikeParams p = r.sim.p;
        double sagF = r.sim.front.compression / p.forkTravel, sagR = r.sim.rear.compression / p.shockTravel;
        log("rest: sag front %.0f%% rear %.0f%%, speed %.4f m/s, pitch %.2f°", sagF * 100, sagR * 100,
                r.sim.speed(), Math.toDegrees(r.sim.pitch));
        assertTrue(r.sim.speed() < 0.03, "bike should be still");
        assertTrue(sagF > 0.12 && sagF < 0.45, "front sag " + sagF);
        assertTrue(sagR > 0.12 && sagR < 0.45, "rear sag " + sagR);
        assertFalse(r.sim.bailed);
    }

    // ------------------------------------------------------------------ F1 speed

    @Test
    void pedalTopSpeedOnFlat() {
        Ride dirt = new Ride("pedal_dirt", TestTerrains.flat(64, Terrain.Surface.DIRT)).place(0, 64, 0, 0);
        dirt.run(30, pedal(1));
        Ride grass = new Ride("pedal_grass", TestTerrains.flat(64, Terrain.Surface.GRASS)).place(0, 64, 0, 0);
        grass.run(30, pedal(1));
        dirt.save();
        log("pedal flat: dirt %.1f km/h, grass %.1f km/h (5 s: %s)", dirt.kmh(), grass.kmh(), "");
        assertTrue(dirt.kmh() > 24 && dirt.kmh() < 37, "dirt top speed " + dirt.kmh());
        assertTrue(grass.kmh() < dirt.kmh(), "grass should be slower");
    }

    @Test
    void gravityDoesTheWorkDownhill() {
        double ang = Math.toRadians(15);
        Terrain slope = TestTerrains.fn((x, z) -> 64 - z * Math.tan(ang), Terrain.Surface.DIRT);
        Ride r = new Ride("slope15", slope).place(0, 64, 0, 0);
        r.run(5, Controls.NONE);
        double at5 = r.kmh();
        r.run(25, Controls.NONE);
        r.save();
        log("15° slope coast: %.1f km/h after 5 s, %.1f km/h after 30 s, takeoffs %d", at5, r.kmh(), r.takeoffs);
        assertTrue(at5 > 30 && at5 < 55, "5 s speed " + at5);
        assertTrue(r.kmh() < 90, "terminal " + r.kmh());
        assertEquals(0, r.takeoffs, "should stay planted on a smooth slope");
        assertFalse(r.sim.bailed);
    }

    @Test
    void brakesStopFromFifty() {
        Ride r = new Ride("brake", TestTerrains.flat(64, Terrain.Surface.DIRT)).place(0, 64, 0, 0).speed(50 / KMH);
        r.run(0.3, Controls.NONE);
        double z0 = r.sim.pos.z;
        r.run(6, ctl(0, 0, 0, 1, 0));
        double dist = r.sim.pos.z - z0;
        r.save();
        log("brake from 50: stopped in %.1f m, end speed %.2f km/h, bailed=%s", dist, r.kmh(), r.sim.bailed);
        assertTrue(r.kmh() < 1.0);
        assertTrue(dist > 7 && dist < 22, "stopping distance " + dist);
        assertFalse(r.sim.bailed);
    }

    // ------------------------------------------------------------------ F3 / F4 cornering

    @Test
    void carvesAndLeansIntoTurns() {
        Ride r = new Ride("carve", TestTerrains.flat(64, Terrain.Surface.DIRT)).place(0, 64, 0, 0).speed(10);
        double[] latG = {0};
        r.run(3, rr -> {
            double yawRate = -rr.sim.omega.dot(V3.Y);
            latG[0] = yawRate * rr.sim.vel.horizontalLength() / 9.81;
            return ctl(1, 0, rr.sim.speed() < 10 ? 1 : 0, 0, 0);
        });
        r.save();
        log("carve @36 km/h full lock: %.2f g, lean %.0f°, speed %.1f km/h, sliding F/R %s/%s",
                latG[0], Math.toDegrees(r.sim.lean), r.kmh(), r.sim.front.sliding, r.sim.rear.sliding);
        assertTrue(latG[0] > 0.6 && latG[0] < 1.3, "lateral g " + latG[0]);
        assertTrue(Math.toDegrees(r.sim.lean) > 28 && Math.toDegrees(r.sim.lean) < 55, "lean");
        assertFalse(r.sim.bailed);
        assertTrue(r.sim.grounded());

        Ride ice = new Ride("carve_ice", TestTerrains.flat(64, Terrain.Surface.ICE)).place(0, 64, 0, 0).speed(10);
        double[] iceG = {0};
        ice.run(2, rr -> {
            iceG[0] = -rr.sim.omega.dot(V3.Y) * rr.sim.vel.horizontalLength() / 9.81;
            return ctl(1, 0, 0, 0, 0);
        });
        log("ice carve: %.2f g", iceG[0]);
        assertTrue(Math.abs(iceG[0]) < 0.3, "ice must not grip like dirt");
    }

    // ------------------------------------------------------------------ F5 blocky terrain

    @Test
    void ridesDownBlockStaircaseSmoothly() {
        // 1 block down every 2 blocks (≈27°) for a long way, then flat
        Terrain stairs = TestTerrains.blocks((x, z) -> 80 - Math.min(Math.max(Math.floorDiv(z, 2), 0), 20),
                Terrain.Surface.DIRT);
        Ride r = new Ride("stairs", stairs).place(0.5, 80, 0.5, 0).speed(6);
        r.run(6, Controls.NONE);
        r.save();
        log("block staircase: %.1f km/h, takeoffs %d, longest air %.2f s, bailed=%s %s",
                r.kmh(), r.takeoffs, r.longestAir, r.sim.bailed, r.sim.bailReason);
        assertFalse(r.sim.bailed, r.sim.bailReason);
        assertTrue(r.longestAir < 0.36, "should not be launched by 1-block steps");
        assertTrue(r.sim.pos.z > 25, "should have made it down");
    }

    @Test
    void singleBlockStepUpIsRideable() {
        Terrain step = TestTerrains.blocks((x, z) -> z >= 10 ? 65 : 64, Terrain.Surface.DIRT);
        Ride r = new Ride("step_up", step).place(0.5, 64, 0.5, 0).speed(6);
        r.run(4, pedal(0.5));
        r.save();
        log("1-block step up @22 km/h: z=%.1f, %.1f km/h, bailed=%s %s", r.sim.pos.z, r.kmh(), r.sim.bailed, r.sim.bailReason);
        assertFalse(r.sim.bailed, r.sim.bailReason);
        assertTrue(r.sim.pos.z > 14, "got over the step");
    }

    @Test
    void twoBlockWallStopsYou() {
        Terrain wall = TestTerrains.blocks((x, z) -> z >= 10 ? 66 : 64, Terrain.Surface.DIRT);
        Ride r = new Ride("wall", wall).place(0.5, 64, 0.5, 0).speed(9);
        r.run(3, Controls.NONE);
        log("2-block wall @32 km/h: z=%.2f bailed=%s %s", r.sim.pos.z, r.sim.bailed, r.sim.bailReason);
        assertTrue(r.sim.pos.z < 10.5, "must not pass through the wall");
        assertTrue(r.sim.bailed, "hitting a wall that fast is a crash");
    }

    // ------------------------------------------------------------------ F6 / F7 pop & bunny hop

    double hop(boolean crouchFirst, double crouchTime) {
        Ride r = new Ride("hop_" + crouchFirst + "_" + crouchTime, TestTerrains.flat(64, Terrain.Surface.DIRT))
                .place(0, 64, 0, 0).speed(5);
        r.run(0.5, Controls.NONE);
        if (crouchFirst) r.run(crouchTime, ctl(0, 0, 0, 0, -1));
        r.run(0.3, ctl(0, 0, 0, 0, 1));
        r.maxClear = -1;
        r.run(1.5, Controls.NONE);
        r.save();
        assertFalse(r.sim.bailed, r.sim.bailReason);
        return r.maxClear;
    }

    @Test
    void bunnyHopHeightDependsOnTechnique() {
        double lazy = hop(false, 0);
        double good = hop(true, 0.35);
        double rushed = hop(true, 0.1);
        log("bunny hop clearance: lazy %.2f m, rushed %.2f m, full preload %.2f m", lazy, rushed, good);
        assertTrue(good > 0.45 && good < 1.2, "full hop " + good);
        assertTrue(good > lazy + 0.1, "preload must matter");
        assertTrue(good >= rushed, "longer preload should not be worse");
    }

    // ------------------------------------------------------------------ F6 / F9 jumps

    /** Flat run-in, 30° kicker (1.5 m), pit, 25° landing ramp. */
    static Terrain kickerLine() {
        double kick = Math.tan(Math.toRadians(30)), land = Math.tan(Math.toRadians(25));
        double rt = 3.0, zc = 10 + rt * Math.sin(Math.toRadians(30)), yc = 64 + rt * (1 - Math.cos(Math.toRadians(30)));
        double lip = zc + (65.5 - yc) / kick;
        return TestTerrains.fn((x, z) -> {
            if (z < 10) return 64;
            if (z < zc) return 64 + rt - Math.sqrt(rt * rt - (z - 10) * (z - 10)); // 3 m radius transition
            if (z < lip) return yc + (z - zc) * kick;            // 30° face up to 1.5 m
            if (z < lip + 3.6) return 61;                       // pit
            if (z < lip + 3.6 + 1.0 / land) return 65.0 - (z - lip - 3.6) * land; // landing, knuckle 0.5 m below the lip
            return 64;
        }, Terrain.Surface.DIRT);
    }

    double jump(boolean pop) {
        Ride r = new Ride("kicker_" + (pop ? "pop" : "passive"), kickerLine()).place(0, 64, 0, 0).speed(9.2);
        r.run(4, rr -> {
            double z = rr.sim.pos.z;
            if (!pop) return Controls.NONE;
            if (z > 8.0 && z < 11.6) return ctl(0, 0, 0, 0, -1);  // bend into the face
            if (z >= 11.6 && z < 13.5) return ctl(0, 0, 0, 0, 1); // extend at the lip
            return Controls.NONE;
        });
        r.save();
        log("kicker %s: apex y=%.2f, air %.2f s, landed z=%.1f, %.1f km/h, bailed=%s %s",
                pop ? "POP" : "passive", r.maxY, r.longestAir, r.sim.pos.z, r.kmh(), r.sim.bailed, r.sim.bailReason);
        assertFalse(r.sim.bailed, r.sim.bailReason);
        assertTrue(r.takeoffs >= 1, "should leave the lip");
        return r.maxY;
    }

    @Test
    void poppingTheLipGoesHigher() {
        double passive = jump(false);
        double pop = jump(true);
        assertTrue(pop > passive + 0.15, "pop " + pop + " vs passive " + passive);
    }

    @Test
    void dropsToFlat() {
        Function<Double, Terrain> drop = hgt -> TestTerrains.fn((x, z) -> z < 5 ? 64 + hgt : 64, Terrain.Surface.DIRT);
        Ride small = new Ride("drop2", drop.apply(2.0)).place(0, 66, 0, 0).speed(6);
        small.run(3, Controls.NONE);
        small.save();
        Ride huge = new Ride("drop14", drop.apply(14.0)).place(0, 78, 0, 0).speed(6);
        huge.run(4, Controls.NONE);
        log("drop 2 m: bailed=%s; drop 14 m: bailed=%s (%s)", small.sim.bailed, huge.sim.bailed, huge.sim.bailReason);
        assertFalse(small.sim.bailed, small.sim.bailReason);
        assertTrue(huge.sim.bailed, "14 m to flat must hurt");
    }

    // ------------------------------------------------------------------ F10 pump

    @Test
    void pumpingRollersGainsSpeed() {
        Terrain rollers = TestTerrains.fn((x, z) -> 64 + 0.55 * Math.sin(2 * Math.PI * z / 7.0), Terrain.Surface.DIRT);
        double crest = 7.0 / 4;
        Ride passive = new Ride("rollers_passive", rollers).place(0, 64.55, crest, 0).speed(6);
        passive.run(8, Controls.NONE);
        Ride pump = new Ride("rollers_pump", rollers).place(0, 64.55, crest, 0).speed(6);
        pump.run(8, rr -> {
            double z = rr.sim.pos.z;
            // smooth pumping with the right stick: soak up the face (bend as the
            // ground rises), push down the backside (stretch as it falls away)
            double look = z + 0.4;                         // a little anticipation
            return ctl(0, 0, 0, 0, -Math.sin(2 * Math.PI * look / 7.0));
        });
        passive.save();
        pump.save();
        log("rollers 8 s: passive %.1f km/h (z=%.0f), pumped %.1f km/h (z=%.0f)",
                passive.kmh(), passive.sim.pos.z, pump.kmh(), pump.sim.pos.z);
        assertFalse(pump.sim.bailed, pump.sim.bailReason);
        assertTrue(pump.sim.pos.z > passive.sim.pos.z + 2, "pumping should carry you further");
    }

    // ------------------------------------------------------------------ manual

    @Test
    void manualLiftsTheFront() {
        Ride r = new Ride("manual", TestTerrains.flat(64, Terrain.Surface.DIRT)).place(0, 64, 0, 0).speed(6);
        int[] frontUp = {0, 0};
        double[] maxPitch = {0};
        r.run(0.3, Controls.NONE);
        r.run(2.5, rr -> {
            frontUp[1]++;
            if (!rr.sim.front.contact && rr.sim.rear.contact) frontUp[0]++;
            maxPitch[0] = Math.max(maxPitch[0], rr.sim.pitch);
            return ctl(0, -1, 0, 0, 0);
        });
        r.save();
        double frac = frontUp[0] / (double) frontUp[1];
        log("manual: front up %.0f%% of the time, max pitch %.0f°, bailed=%s", frac * 100, Math.toDegrees(maxPitch[0]), r.sim.bailed);
        assertTrue(frac > 0.5, "front should be up");
        assertTrue(maxPitch[0] < Math.toRadians(60), "should not loop out");
        assertFalse(r.sim.bailed);
    }

    // ------------------------------------------------------------------ F8 air control

    @Test
    void backflipAndSpinRates() {
        Terrain far = TestTerrains.flat(0, Terrain.Surface.DIRT);
        Ride flip = new Ride("flip", far).place(0, 0, 0, 0);
        flip.sim.pos = new V3(0, 200, 0);
        flip.sim.riderPos = flip.sim.pos.add(new V3(0, flip.sim.p.riderHeight, 0));
        flip.run(0.2, Controls.NONE);
        flip.run(0.9, ctl(0, -1, 0, 0, 0));
        double flipTurns = flip.sim.airPitchTravel / (2 * Math.PI);

        Ride spin = new Ride("spin", far).place(0, 0, 0, 0);
        spin.sim.pos = new V3(0, 200, 0);
        spin.sim.riderPos = spin.sim.pos.add(new V3(0, spin.sim.p.riderHeight, 0));
        spin.run(0.2, Controls.NONE);
        spin.run(0.8, ctl(1, 0, 0, 0, 0));
        double spinTurns = spin.sim.airYawTravel / (2 * Math.PI);
        log("air: 0.9 s of backflip = %.2f turns, 0.8 s of spin = %.2f turns", flipTurns, spinTurns);
        assertTrue(flipTurns > 0.8 && flipTurns < 1.2, "flip " + flipTurns);
        assertTrue(spinTurns > 0.8 && spinTurns < 1.2, "spin " + spinTurns);
    }

    // ------------------------------------------------------------------ robustness

    @Test
    void survivesRandomBlockTerrain() {
        java.util.Random rnd = new java.util.Random(42);
        int[][] hgt = new int[64][256];
        for (int x = 0; x < 64; x++) for (int z = 0; z < 256; z++) hgt[x][z] = 100 - z / 3 + (rnd.nextInt(10) == 0 ? 1 : 0);
        Terrain t = TestTerrains.blocks((x, z) -> hgt[Math.floorMod(x, 64)][Math.max(0, Math.min(255, z))], Terrain.Surface.GRASS);
        Ride r = new Ride("random", t).place(32.5, 100, 2.5, 0).speed(8);
        r.run(12, rr -> ctl(Math.sin(rr.t * 1.3) * 0.6, 0, 0.3, 0, 0));
        r.save();
        log("random grass hill: z=%.0f, max %.0f km/h, takeoffs %d, bailed=%s %s",
                r.sim.pos.z, r.maxKmh, r.takeoffs, r.sim.bailed, r.sim.bailReason);
        assertTrue(r.maxKmh < 120, "no explosions");
    }
}
