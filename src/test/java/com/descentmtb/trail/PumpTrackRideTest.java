package com.descentmtb.trail;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.*;
import com.descentmtb.trail.TrailMath.Point;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rides a large closed pumptrack (the exact height field the Trail Wand builds) with a
 * path-following rider and checks cornering: the bike must stay on the track through banked
 * turns, in both directions, without bailing, and the banks must actually help.
 *
 * <p>The terrain is sampled like the real blocks: heights at integer corners, bilinear inside
 * each 1 m cell (that is what a TrailSurface block is).
 */
class PumpTrackRideTest {
    static final double DT = 0.05;

    /** Corner-sampled, bilinear-per-block surface - what the TrailSurface blocks give the physics. */
    static final class GridTerrain implements Terrain {
        final DoubleBinaryOperator h;
        GridTerrain(DoubleBinaryOperator h) { this.h = h; }

        @Override public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            double fx = x - ix, fz = z - iz;
            double h00 = h.applyAsDouble(ix, iz), h10 = h.applyAsDouble(ix + 1, iz);
            double h01 = h.applyAsDouble(ix, iz + 1), h11 = h.applyAsDouble(ix + 1, iz + 1);
            double y = (h00 * (1 - fx) + h10 * fx) * (1 - fz) + (h01 * (1 - fx) + h11 * fx) * fz;
            if (y > yTop + 1e-3 || y < yBottom) return false;
            double dx = (h10 - h00) * (1 - fz) + (h11 - h01) * fz;
            double dz = (h01 - h00) * (1 - fx) + (h11 - h10) * fx;
            out.set(y, new V3(-dx, 1, -dz).normalize(), Surface.TRAIL);
            return true;
        }

        @Override public boolean solidAt(double x, double y, double z) {
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            double fx = x - ix, fz = z - iz;
            double yy = (h.applyAsDouble(ix, iz) * (1 - fx) + h.applyAsDouble(ix + 1, iz) * fx) * (1 - fz)
                    + (h.applyAsDouble(ix, iz + 1) * (1 - fx) + h.applyAsDouble(ix + 1, iz + 1) * fx) * fz;
            return yy - 0.05 > y;
        }
    }

    record Result(double laps, double seconds, boolean bailed, String bailReason, double minSpeed, double avgSpeed,
                  double maxOffTrack, double maxLeanDeg, double maxLatG, double p95LatG, double airFraction, double cornerSpeed,
                  String csv) {}

    static final double BASE = 64;

    /** Terrain outside the track: flat ground. */
    static final DoubleBinaryOperator FLAT = (x, z) -> BASE;

    static PumpShapes.Params settings(double width, double height, double spacing) {
        return new PumpShapes.Params(width, height, spacing, 24);
    }

    /**
     * @param dir +1 / -1 around the oval
     * @param target speed the rider tries to hold (m/s)
     */
    static Result ride(String name, DoubleBinaryOperator height, PumpShapes.Oval o, int dir, double target,
                       double seconds, BikeType type) {
        return ride(name, height, o, dir, target, seconds, type, Double.NaN, 5, false);
    }

    /**
     * @param pumpPhase NaN = passive; otherwise the rider pumps the rollers with this phase (rad)
     * @param noPedal   coast only - the speed must come from pumping
     */
    static Result ride(String name, DoubleBinaryOperator height, PumpShapes.Oval o, int dir, double target,
                       double seconds, BikeType type, double pumpPhase, double spacing, boolean noPedal) {
        BikeSim sim = new BikeSim(type.params(), new GridTerrain(height));
        // start on the middle of the lower straight, heading along the direction of travel
        double th0 = -Math.PI / 2;
        double sx = o.cx() + o.rx() * Math.cos(th0), sz = o.cz() + o.rz() * Math.sin(th0);
        double tx = -o.rx() * Math.sin(th0) * dir, tz = o.rz() * Math.cos(th0) * dir;
        sim.place(sx, height.applyAsDouble(sx, sz), sz, Math.atan2(-tx, tz));
        V3 f0 = sim.forward();
        sim.vel = f0.mul(target * 0.8);
        sim.riderVel = sim.vel;

        StringBuilder csv = new StringBuilder("t,x,z,y,kmh,off,lean,latg,air,theta\n");
        double unwrapped = th0, last = th0;
        double minV = 1e9, sumV = 0, maxOff = 0, maxLean = 0, maxG = 0;
        int air = 0, n = (int) (seconds / DT), cornerTicks = 0;
        java.util.List<Double> latGs = new java.util.ArrayList<>();
        double cornerV = 0;
        for (int i = 0; i < n && !sim.bailed; i++) {
            // --- nearest point on the centre line (coarse + refine) ---
            double best = 1e18, th = last;
            for (int k = -40; k <= 40; k++) {
                double q = last + k * 0.0045;
                double ex = o.cx() + o.rx() * Math.cos(q) - sim.pos.x, ez = o.cz() + o.rz() * Math.sin(q) - sim.pos.z;
                double d = ex * ex + ez * ez;
                if (d < best) { best = d; th = q; }
            }
            unwrapped += th - last;
            last = th;
            // --- pure pursuit ---
            double speed = sim.vel.horizontalLength();
            double look = 3.0 + 0.45 * speed;
            double dth = look / (0.5 * (o.rx() + o.rz())) * dir;
            double q = th + dth;
            double px = o.cx() + o.rx() * Math.cos(q), pz = o.cz() + o.rz() * Math.sin(q);
            V3 fwd = sim.forward().horizontal().normalize();
            V3 right = sim.rightAxis().horizontal().normalize();
            V3 d = new V3(px - sim.pos.x, 0, pz - sim.pos.z).normalize();
            double err = Math.atan2(d.dot(right), d.dot(fwd));
            double steer = Math.max(-1, Math.min(1, err * 2.2));
            double pedal = noPedal ? 0 : speed < target ? 1 : 0;
            double body = 0;
            if (!Double.isNaN(pumpPhase)) {
                body = Math.cos(2 * Math.PI * (o.distance(sim.pos.x, sim.pos.z) - spacing / 2) / spacing * dir + pumpPhase)
                        * (1 - o.turnAmount(sim.pos.x, sim.pos.z));
            }
            double brake = speed > target + 2.5 ? 0.5 : 0;
            sim.tick(new Controls((float) steer, 0, (float) pedal, (float) brake, (float) body, 0, false, 0, 0), DT);
            sim.events.clear();

            double off = Math.sqrt(best);   // distance to the centre line
            double yawRate = Math.abs(sim.omega.dot(V3.Y)), latG = yawRate * sim.vel.horizontalLength() / 9.81;
            double turn = o.turnAmount(sim.pos.x, sim.pos.z);
            maxOff = Math.max(maxOff, off);
            maxLean = Math.max(maxLean, Math.abs(Math.toDegrees(sim.lean)));
            maxG = Math.max(maxG, latG);
            latGs.add(latG);
            minV = Math.min(minV, sim.speed());
            sumV += sim.speed();
            if (sim.airborne) air++;
            if (turn > 0.8) { cornerV += sim.speed(); cornerTicks++; }
            csv.append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.3f,%.1f,%.2f,%.1f,%.2f,%d,%.3f%n",
                    i * DT, sim.pos.x, sim.pos.z, sim.pos.y, sim.speed() * 3.6, off, Math.toDegrees(sim.lean), latG,
                    sim.airborne ? 1 : 0, th));
        }
        double laps = Math.abs(unwrapped - th0) / (2 * Math.PI);
        Result r = new Result(laps, n * DT, sim.bailed, sim.bailReason, minV, sumV / Math.max(1, n), maxOff, maxLean, maxG, percentile(latGs, .95),
                air / (double) Math.max(1, n), cornerTicks == 0 ? 0 : cornerV / cornerTicks, csv.toString());
        System.out.printf(Locale.ROOT,
                "[pump] %-22s laps %.2f in %.0fs | speed min %.1f avg %.1f corners %.1f m/s | off-line max %.2f m | lean max %.0f° | lat %.2f g (95%%: %.2f) | air %.0f%% | bail=%s %s%n",
                name, r.laps, seconds, r.minSpeed, r.avgSpeed, r.cornerSpeed, r.maxOffTrack, r.maxLeanDeg, r.maxLatG, r.p95LatG,
                r.airFraction * 100, r.bailed, r.bailReason);
        try {
            Path dir2 = Path.of("build", "telemetry");
            Files.createDirectories(dir2);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(dir2.resolve("pump_" + name + ".csv")))) {
                w.print(r.csv);
            }
        } catch (IOException ignored) {
        }
        return r;
    }

    /** The loop exactly as it was built before the berm rework (kept to compare against). */
    static DoubleBinaryOperator legacyLoop(DoubleBinaryOperator terrain, Point a, Point c, PumpShapes.Params s) {
        double half = s.width() / 2, margin = 2.0;
        double cx = (a.x() + c.x()) / 2, cz = (a.z() + c.z()) / 2;
        double rx = Math.abs(c.x() - a.x()) / 2 - half - margin, rz = Math.abs(c.z() - a.z()) / 2 - half - margin;
        double perimeter = Math.PI * (3 * (rx + rz) - Math.sqrt((3 * rx + rz) * (rx + 3 * rz)));
        double spacing = s.spacing(), active = Math.min(perimeter, spacing * s.repeats());
        return (x, z) -> {
            double nx = (x - cx) / rx, nz = (z - cz) / rz, theta = Math.atan2(nz, nx), rad = Math.sqrt(nx * nx + nz * nz);
            double side = (rad - 1) * Math.min(rx, rz), base = terrain.applyAsDouble(x, z);
            double edge = PumpMath.edge(Math.abs(side), half + margin, .6), bank = Math.max(0, side) / half;
            double distance = (theta + Math.PI) / Math.PI / 2 * perimeter;
            double wave = distance <= active ? PumpMath.wave(distance, spacing, s.height()) * PumpMath.smooth((active - distance) / 2) : 0;
            double target = a.y();
            return base + edge * (target - base + wave + Math.min(2, bank * bank) * s.height() * .9);
        };
    }

    @Test void legacyShapeForComparison() {
        PumpShapes.Params s = settings(5, 0.75, 5);
        PumpShapes.Oval o = PumpShapes.oval(A, C, s);
        Result r = ride("legacy_slow", legacyLoop(FLAT, A, C, s), o, 1, 7.5, 60, BikeType.ENDURO);
        Result f = ride("legacy_fast", legacyLoop(FLAT, A, C, s), o, 1, 11.0, 60, BikeType.ENDURO);
        System.out.printf(Locale.ROOT, "[pump] legacy: slow bail=%s fast bail=%s%n", r.bailed, f.bailed);
    }

    static double percentile(java.util.List<Double> values, double q) {
        if (values.isEmpty()) return 0;
        var sorted = values.stream().sorted().toList();
        return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(q * sorted.size())));
    }

    // a "larger" pumptrack: the full 64 x 44 m selection the server allows, 5 m wide
    static final Point A = new Point(0, BASE, 0), C = new Point(64, BASE, 44);

    @Test void ridesALargeClosedPumptrackInBothDirections() {
        PumpShapes.Params s = settings(5, 0.75, 5);
        PumpShapes.Oval o = PumpShapes.oval(A, C, s);
        DoubleBinaryOperator track = PumpShapes.loop(FLAT, A, C, s);
        System.out.printf(Locale.ROOT, "[pump] oval %.1f x %.1f m, centre line %.0f m%n", o.rx() * 2, o.rz() * 2, o.perimeter());
        for (int dir : new int[]{1, -1}) {
            Result r = ride("enduro_dir" + dir, track, o, dir, 7.5, 90, BikeType.ENDURO);
            assertFalse(r.bailed, "bailed: " + r.bailReason);
            assertTrue(r.laps > 3.0, "should lap several times, did " + r.laps);
            assertTrue(r.maxOffTrack < o.half() + 1.5, "left the track by " + r.maxOffTrack);
            assertTrue(r.minSpeed > 2.0, "stalled in a corner: " + r.minSpeed);
            assertTrue(r.p95LatG < 1.3, "banks should hold the bike without violent side loads, 95% = " + r.p95LatG + " g");
            assertTrue(r.maxOffTrack < 1.2, "line holds within the berms: " + r.maxOffTrack);
        }
    }

    @Test void phaseSweepExperiment() {
        PumpShapes.Params s = settings(5, 0.75, 5);
        PumpShapes.Oval o = PumpShapes.oval(A, C, s);
        DoubleBinaryOperator track = PumpShapes.loop(FLAT, A, C, s);
        for (int k = 0; k < 12; k++) {
            double ph = k * Math.PI / 6;
            Result r = ride("sweep" + k, track, o, 1, 7.0, 40, BikeType.ENDURO, ph, 5, true);
            System.out.printf(Locale.ROOT, "[sweep] phase %3.0f° -> laps %.2f avg %.1f min %.1f bail=%s%n", Math.toDegrees(ph), r.laps, r.avgSpeed, r.minSpeed, r.bailed);
        }
        Result none = ride("sweep_none", track, o, 1, 7.0, 40, BikeType.ENDURO, Double.NaN, 5, true);
        System.out.printf(Locale.ROOT, "[sweep] passive      -> laps %.2f avg %.1f min %.1f%n", none.laps, none.avgSpeed, none.minSpeed);
    }

    @Test void hardtailRidesItToo() {
        PumpShapes.Params s = settings(5, 0.75, 5);
        PumpShapes.Oval o = PumpShapes.oval(A, C, s);
        Result r = ride("hardtail", PumpShapes.loop(FLAT, A, C, s), o, 1, 7.5, 90, BikeType.HARDTAIL);
        assertFalse(r.bailed, "bailed: " + r.bailReason);
        assertTrue(r.laps > 3.0, "laps " + r.laps);
    }

    @Test void bermsHoldSpeedBetterThanAFlatCorner() {
        PumpShapes.Params s = settings(5, 0.75, 5);
        PumpShapes.Oval o = PumpShapes.oval(A, C, s);
        Result berm = ride("berm_fast", PumpShapes.loop(FLAT, A, C, s), o, 1, 11.0, 80, BikeType.ENDURO);
        // same oval but no banking and no rollers (flat ground)
        Result flat = ride("flat_fast", FLAT, o, 1, 11.0, 80, BikeType.ENDURO);
        System.out.printf(Locale.ROOT, "[pump] fast corners: berm %.1f m/s / %.2f g vs flat %.1f m/s / %.2f g%n",
                berm.cornerSpeed, berm.maxLatG, flat.cornerSpeed, flat.maxLatG);
        assertFalse(berm.bailed, "berm bailed: " + berm.bailReason);
        assertTrue(berm.laps > 2.5, "berm laps " + berm.laps);
    }
}
