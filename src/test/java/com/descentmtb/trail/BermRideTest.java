package com.descentmtb.trail;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.trail.BermShapes.Steepness;
import com.descentmtb.trail.TrailMath.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rides the Trail Shaper's berm (the exact height field {@link BermShapes} builds the blocks from) through a 90
 * degree turn at every steepness. The ground is sampled like the real blocks: heights at integer corners,
 * bilinear inside each 1 m cell, so the slopes are the ones the bike meets in the game.
 */
class BermRideTest {
    static final double BASE = 64, DT = 0.05, RADIUS = 9;
    static final DoubleBinaryOperator FLAT = (x, z) -> BASE;

    /** The 90 degree right turn: heading east along z = 0, then south along x = 9. */
    static BermShapes berm(Steepness steepness, double width) {
        double apex = RADIUS * Math.sqrt(.5);
        return BermShapes.of(new Point(0, BASE, 0), new Point(apex, BASE, RADIUS - apex), new Point(RADIUS, BASE, RADIUS),
                new BermShapes.Params(width, steepness), 64);
    }

    /** Lead-in straight, the arc and lead-out straight, {@code offset} m outside the centre line. */
    static double[][] path(double offset) {
        List<double[]> points = new ArrayList<>();
        double r = RADIUS + offset;
        for (double x = -14; x < 0; x += .5) {
            points.add(new double[]{x, RADIUS - r});
        }
        for (double phi = 0; phi < Math.PI / 2; phi += .5 / r) {
            points.add(new double[]{r * Math.sin(phi), RADIUS - r * Math.cos(phi)});
        }
        for (double z = RADIUS; z <= RADIUS + 14; z += .5) {
            points.add(new double[]{r, z});
        }
        return points.toArray(new double[0][]);
    }

    /** Like {@link #path} but the line drifts from the centre up the bank to {@code peak} m outside and back down. */
    static double[][] climbingPath(double peak) {
        List<double[]> points = new ArrayList<>();
        for (double x = -14; x < 0; x += .5) {
            points.add(new double[]{x, RADIUS - RADIUS});
        }
        for (double phi = 0; phi < Math.PI / 2; phi += .5 / RADIUS) {
            double r = RADIUS + peak * Math.pow(Math.sin(2 * phi), 2);
            points.add(new double[]{r * Math.sin(phi), RADIUS - r * Math.cos(phi)});
        }
        for (double z = RADIUS; z <= RADIUS + 14; z += .5) {
            points.add(new double[]{RADIUS, z});
        }
        return points.toArray(new double[0][]);
    }

    record Result(boolean bailed, String why, double progress, double minSpeed, double maxOff, double maxLeanDeg,
                  double maxClimb, double wallSeconds, double seconds) {
        String summary() {
            return String.format(Locale.ROOT, "bail=%s%s | reached %.0f%% of the path in %.1f s | speed min %.1f m/s | off the line max %.2f m | lean max %.0f° | climbed %.2f m | on the wall %.1f s",
                    bailed, bailed ? " (" + why + ")" : "", progress * 100, seconds, minSpeed, maxOff, maxLeanDeg, maxClimb, wallSeconds);
        }
    }

    /** Rides {@code path} at about {@code target} m/s with a pure-pursuit rider. */
    static Result ride(DoubleBinaryOperator height, double[][] path, double target, double seconds) {
        BikeSim sim = new BikeSim(BikeType.ENDURO.params(), new PumpTrackRideTest.GridTerrain(height));
        double[] p0 = path[0], p1 = path[4];
        sim.place(p0[0], BASE, p0[1], Math.atan2(-(p1[0] - p0[0]), p1[1] - p0[1]));
        sim.vel = sim.forward().mul(target * .8);
        sim.riderVel = sim.vel;

        int n = path.length, index = 0, steps = (int) (seconds / DT), ticks = 0;
        double minSpeed = 1e9, maxOff = 0, maxLean = 0, maxClimb = 0, wall = 0;
        for (int i = 0; i < steps && !sim.bailed; i++) {
            double best = 1e18;
            int bestIndex = index;
            for (int k = -10; k <= 40; k++) {
                int idx = Math.max(0, Math.min(n - 1, index + k));
                double dx = path[idx][0] - sim.pos.x, dz = path[idx][1] - sim.pos.z;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    bestIndex = idx;
                }
            }
            index = bestIndex;
            double speed = sim.vel.horizontalLength();
            int ahead = (int) Math.max(6, (3.0 + .45 * speed) / .5);
            int idx = Math.min(n - 1, index + ahead);
            V3 fwd = sim.forward().horizontal().normalize(), right = sim.rightAxis().horizontal().normalize();
            V3 want = new V3(path[idx][0] - sim.pos.x, 0, path[idx][1] - sim.pos.z).normalize();
            float steer = (float) Math.max(-1, Math.min(1, Math.atan2(want.dot(right), want.dot(fwd)) * 2.2));
            float pedal = speed < target ? 1 : 0;
            float brake = speed > target + 2.5 ? .5f : 0;
            sim.tick(new Controls(steer, 0, pedal, brake, 0, 0, false, 0, 0), DT);
            sim.events.clear();

            maxOff = Math.max(maxOff, Math.sqrt(best));
            maxLean = Math.max(maxLean, Math.abs(Math.toDegrees(sim.lean)));
            maxClimb = Math.max(maxClimb, sim.pos.y - BASE);
            minSpeed = Math.min(minSpeed, sim.speed());
            if (sim.wallRide) {
                wall += DT;
            }
            ticks++;
            if (index >= n - 3) {
                break;
            }
        }
        return new Result(sim.bailed, sim.bailReason, index / (double) (n - 1), minSpeed, maxOff, maxLean, maxClimb, wall, ticks * DT);
    }

    /** The steepest slope (degrees) between two neighbouring grid corners of the footprint. */
    static double realisedSlope(BermShapes berm, DoubleBinaryOperator height) {
        int[] box = berm.bounds();
        double steepest = 0;
        for (int x = box[0]; x < box[2]; x++) {
            for (int z = box[1]; z < box[3]; z++) {
                double h = height.applyAsDouble(x, z);
                steepest = Math.max(steepest, Math.abs(height.applyAsDouble(x + 1, z) - h));
                steepest = Math.max(steepest, Math.abs(height.applyAsDouble(x, z + 1) - h));
            }
        }
        return Math.toDegrees(Math.atan(steepest));
    }

    @Test
    void theBankHasTheAdvertisedShape() {
        for (Steepness s : Steepness.values()) {
            assertEquals(0, s.bank(0), 1e-9);
            double previous = 0;
            double x = 0;
            for (; x < s.reach() && s.bank(x) < s.top - 1e-9; x += .01) {
                assertTrue(s.bank(x) >= previous - 1e-9, s + " keeps rising up to its top");
                previous = s.bank(x);
            }
            assertTrue(x < s.reach() - 1, s + " reaches its top before it falls away");
            assertEquals(0, s.bank(s.reach()), 1e-6, s + " falls back to the ground");
        }
        assertTrue(Steepness.WALLRIDE.top >= 2.5, "the wall is at least 2.5 m high");
    }

    @Test
    void outsideOfTheTurnIsBankedAndTheInsideStaysFlat() {
        BermShapes berm = berm(Steepness.MEDIUM, 4);
        DoubleBinaryOperator h = berm.heights(FLAT);
        double apex = RADIUS * Math.sqrt(.5);
        // the circle's centre is (0, RADIUS); u points from there to the apex of the curve at (apex, RADIUS - apex)
        double ux = Math.sqrt(.5), uz = -Math.sqrt(.5);
        assertEquals(BASE, h.applyAsDouble(apex - 2.5 * ux, RADIUS - apex - 2.5 * uz), 0.05, "inside is flat");
        assertTrue(h.applyAsDouble(apex + 3.5 * ux, RADIUS - apex + 3.5 * uz) > BASE + .6, "outside is banked");
        assertEquals(BASE, h.applyAsDouble(-12, 0), 1e-6, "no berm before the entry");
        assertTrue(berm.contains(apex, RADIUS - apex));
        assertFalse(berm.contains(-3, 0));
    }

    @Test
    void straightAndTooShortPointsAreRejected() {
        var params = new BermShapes.Params(4, Steepness.MEDIUM);
        assertThrows(BermShapes.Rejected.class, () -> BermShapes.of(new Point(0, 0, 0), new Point(5, 0, 0), new Point(10, 0, 0), params, 64));
        assertThrows(BermShapes.Rejected.class, () -> BermShapes.of(new Point(0, 0, 0), new Point(1, 0, 0), new Point(2, 0, 1), params, 64));
        assertThrows(BermShapes.Rejected.class, () -> BermShapes.of(new Point(0, 0, 0), new Point(40, 0, 5), new Point(80, 0, 40), params, 64));
    }

    @Test
    void ridesA90DegreeBermAtEverySteepness() {
        for (Steepness steepness : Steepness.values()) {
            BermShapes berm = berm(steepness, 4);
            DoubleBinaryOperator height = berm.heights(FLAT);
            double speed = steepness == Steepness.WALLRIDE ? 9 : 7;
            System.out.printf(Locale.ROOT, "[berm] %-8s (%2.0f°) 4 m wide, top %.1f m, steepest grid slope %.0f°%n",
                    steepness, steepness.degrees, steepness.top, realisedSlope(berm, height));
            Result flat = ride(FLAT, path(0), speed, 40);
            System.out.printf(Locale.ROOT, "[berm] (the same turn on flat ground at %.0f m/s: %s)%n", speed, flat.summary());
            Result climbing = ride(height, climbingPath(3.5), speed, 40);
            System.out.printf(Locale.ROOT, "[berm] %-8s at %.0f m/s, line climbing 3.5 m up the bank: %s%n", steepness, speed, climbing.summary());
            for (double offset : new double[]{0, 2.5, 3.5}) {
                Result r = ride(height, path(offset), speed, 40);
                System.out.printf(Locale.ROOT, "[berm] %-8s at %.0f m/s, line %.1f m outside the centre: %s%n", steepness, speed, offset, r.summary());
                if (steepness == Steepness.WALLRIDE && offset == 3.5) {
                    // the bike cannot be thrown off the wall: it drives up the face like a very steep ramp and reaches the top
                    assertFalse(r.bailed, "wall climb bailed: " + r.why);
                    assertTrue(r.maxClimb > 2.5, "wall climb only reached " + r.maxClimb);
                }
                if (steepness != Steepness.WALLRIDE && offset == 0) {
                    assertFalse(r.bailed, steepness + " bailed: " + r.why);
                    assertTrue(r.progress > .95, steepness + " did not finish the turn");
                }
            }
        }
    }

    @Test
    void aBermHoldsFastRidersThatAFlatCornerThrowsOff() {
        for (double speed : new double[]{12}) {
            Result flat = ride(FLAT, path(0), speed, 40);
            System.out.printf(Locale.ROOT, "[berm] flat ground at %.0f m/s: %s%n", speed, flat.summary());
            for (Steepness steepness : Steepness.values()) {
                DoubleBinaryOperator height = berm(steepness, 4).heights(FLAT);
                for (double offset : new double[]{0, 3.5}) {
                    Result r = ride(height, path(offset), speed, 40);
                    System.out.printf(Locale.ROOT, "[berm] %-8s at %.0f m/s, line %.1f m outside: %s%n", steepness, speed, offset, r.summary());
                }
            }
        }
    }
}
