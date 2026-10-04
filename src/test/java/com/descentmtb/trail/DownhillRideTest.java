package com.descentmtb.trail;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.trail.DownhillShapes.Kind;
import com.descentmtb.trail.DownhillShapes.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plans the Trail Shaper's downhill line (the exact height field {@link DownhillShapes} builds the blocks from) on
 * synthetic hillsides and rides it with the bike physics and a path-following rider: it must reach the finish
 * without bailing, stay within 1.5 m of the line and land every jump. The hillsides are made of 1 m block columns
 * like the real world; the ground is sampled like the real blocks (heights at integer corners, bilinear inside).
 */
class DownhillRideTest {
    static final double DT = 0.05;
    static final int TRACE_EVERY = Integer.getInteger("trace.every", 1) == 1 && System.getenv("DOWNHILL_TRACE_EVERY") != null ? Integer.parseInt(System.getenv("DOWNHILL_TRACE_EVERY")) : 4;
    /** Debugging: DOWNHILL_TRACE=from:to prints the rider's state between these metres of the line. */
    static final double[] TRACE = System.getenv("DOWNHILL_TRACE") == null ? null
            : java.util.Arrays.stream(System.getenv("DOWNHILL_TRACE").split(":")).mapToDouble(Double::parseDouble).toArray();

    /** A hillside as block columns: heights floor(f) at the column centres, smooth in between like the planner sees it. */
    static DoubleBinaryOperator blocks(DoubleBinaryOperator f) {
        return (x, z) -> {
            double fx = x - .5, fz = z - .5;
            int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
            double tx = fx - i, tz = fz - j;
            double h00 = Math.floor(f.applyAsDouble(i + .5, j + .5)), h10 = Math.floor(f.applyAsDouble(i + 1.5, j + .5));
            double h01 = Math.floor(f.applyAsDouble(i + .5, j + 1.5)), h11 = Math.floor(f.applyAsDouble(i + 1.5, j + 1.5));
            return (h00 * (1 - tx) + h10 * tx) * (1 - tz) + (h01 * (1 - tx) + h11 * tx) * tz;
        };
    }

    static final DoubleBinaryOperator EVEN = blocks((x, z) -> 200 - .16 * z + 1.2 * Math.sin(x / 9) + .8 * Math.sin(z / 7));
    /** A mound right across the straight line: the way down has to go round it. */
    static final DoubleBinaryOperator MOUND = blocks((x, z) -> 200 - .17 * z + 7 * Math.exp(-(sq(x - 22) + sq(z - 70)) / 140));
    /** A steep part and a 2 m step across the whole hillside. */
    static final DoubleBinaryOperator STEPPED = blocks((x, z) -> 200 - (z < 40 ? .1 * z : z < 80 ? 4 + .3 * (z - 40) : 16 + .12 * (z - 80)) - (z > 105 ? 2 : 0));
    /** A valley that bends by 90 degrees: straight down for 60 m, then to the east. */
    static final DoubleBinaryOperator BEND = blocks((x, z) -> {
        // distance to the valley floor (a bent line) and how far down it the nearest point is
        double d1 = Math.hypot(x - 10, z - Math.max(0, Math.min(60, z))), along1 = Math.max(0, Math.min(60, z));
        double x2 = Math.max(10, Math.min(90, x)), d2 = Math.hypot(x - x2, z - 60), along2 = 60 + x2 - 10;
        boolean first = d1 <= d2;
        return 200 - .15 * (first ? along1 : along2) + .5 * Math.min(first ? d1 : d2, 40);
    });
    static final DoubleBinaryOperator FLAT = blocks((x, z) -> 200 - .04 * z + .6 * Math.sin(x / 6));

    static double sq(double v) {
        return v * v;
    }

    record Hill(String name, DoubleBinaryOperator ground, double finishX, double finishZ) {}

    static final List<Hill> HILLS = List.of(
            new Hill("even", EVEN, 30, 150), new Hill("mound", MOUND, 28, 140),
            new Hill("stepped", STEPPED, 20, 150), new Hill("flat", FLAT, 24, 130), new Hill("bend", BEND, 90, 60));

    record Result(boolean bailed, String why, double progress, double maxOff, double minSpeed, double minAt, double avgSpeed, List<Double> flights, double seconds, List<String> events) {
        String summary() {
            StringBuilder air = new StringBuilder();
            flights.forEach(a -> air.append(String.format(Locale.ROOT, " %.1f", a)));
            return String.format(Locale.ROOT, "bail=%s%s | reached %.0f%% in %.0f s | speed min %.1f (at %.0f m) avg %.1f m/s | off the line max %.2f m | %d flights (s):%s",
                    bailed, bailed ? " (" + why + ")" : "", progress * 100, seconds, minSpeed, minAt, avgSpeed, maxOff, flights.size(), air);
        }
    }

    /** Rides the centre line at about {@code target} m/s with a pure-pursuit rider. */
    static Result ride(DoubleBinaryOperator height, double[][] path, double target, double seconds) {
        BikeSim sim = new BikeSim(BikeType.ENDURO.params(), new PumpTrackRideTest.GridTerrain(height));
        double[] p0 = path[0], p1 = path[4];
        sim.place(p0[0], height.applyAsDouble(p0[0], p0[1]), p0[1], Math.atan2(-(p1[0] - p0[0]), p1[1] - p0[1]));
        sim.vel = sim.forward().mul(target * .8);
        sim.riderVel = sim.vel;

        double[] limit = DevRideSim.cornerSpeeds(path, target);
        int n = path.length, index = 0, steps = (int) (seconds / DT), ticks = 0;
        double minSpeed = 1e9, minAt = 0, sumSpeed = 0, maxOff = 0, airTime = 0;
        List<Double> flights = new ArrayList<>();
        boolean wasAir = false;
        List<String> events = new ArrayList<>();
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
            double wanted = limit[index];
            float pedal = speed < wanted ? 1 : 0;
            float brake = speed > wanted + 1.5 ? .8f : 0;
            sim.tick(new Controls(steer, 0, pedal, brake, 0, 0, false, 0, 0), DT);
            for (var e : sim.events) {
                events.add(String.format(Locale.ROOT, "%s at %.0f m: %.1f %s (speed %.1f)", e.type(), index * .5, e.value(), e.info(), sim.speed()));
            }
            if (TRACE != null && i % TRACE_EVERY == 0 && index * .5 >= TRACE[0] && index * .5 <= TRACE[1]) {
                events.add(String.format(Locale.ROOT, "TRACE %.1f m: pos %.2f %.2f y %.2f speed %.1f yaw %.0f steer %.2f air %s lean %.0f pitch %.0f contacts %s%s vy %.1f", index * .5, sim.pos.x, sim.pos.z, sim.pos.y, sim.speed(), Math.toDegrees(sim.yaw), steer, sim.airborne, Math.toDegrees(sim.lean), Math.toDegrees(sim.pitch), sim.front.contact ? "F" : "-", sim.rear.contact ? "R" : "-", sim.vel.y));
            }
            sim.events.clear();

            if (Math.sqrt(best) > 1.0 && Math.sqrt(best) > maxOff) {
                events.add(String.format(Locale.ROOT, "OFF %.2f m at %.1f m (pos %.1f %.1f y %.2f speed %.1f yaw %.0f)", Math.sqrt(best), index * .5, sim.pos.x, sim.pos.z, sim.pos.y, sim.speed(), Math.toDegrees(sim.yaw)));
            }
            maxOff = Math.max(maxOff, Math.sqrt(best));
            if (sim.speed() < minSpeed && i > 40) {
                minSpeed = sim.speed();
                minAt = index * .5;
            }
            sumSpeed += sim.speed();
            if (sim.airborne) {
                airTime += DT;
            } else if (wasAir) {
                if (airTime >= .3) {
                    flights.add(airTime);
                }
                airTime = 0;
            }
            wasAir = sim.airborne;
            ticks++;
            if (index >= n - 3) {
                break;
            }
        }
        return new Result(sim.bailed, sim.bailReason, index / (double) (n - 1), maxOff, minSpeed, minAt, sumSpeed / Math.max(1, ticks), flights, ticks * DT, events);
    }

    static DownhillShapes plan(Hill hill, Style style, double width) {
        return DownhillShapes.plan(hill.ground(), 10, 0, hill.finishX(), hill.finishZ(), new DownhillShapes.Params(style, width, 200), 7);
    }

    @Test
    void theLineFollowsTheHillAndNeverClimbs() {
        for (Hill hill : HILLS) {
            DownhillShapes line = plan(hill, Style.MIXED, 3);
            double[][] c = line.centre();
            double worstClimb = 0, minRadius = 1e9;
            for (int i = 1; i < c.length; i++) {
                worstClimb = Math.max(worstClimb, (line.gradeAt(i * .5) - line.gradeAt((i - 1) * .5)) / .5);
            }
            for (int i = 4; i < c.length - 4; i++) {
                double ax = c[i][0] - c[i - 4][0], az = c[i][1] - c[i - 4][1], bx = c[i + 4][0] - c[i][0], bz = c[i + 4][1] - c[i][1];
                double turn = Math.abs(Math.atan2(ax * bz - az * bx, ax * bx + az * bz));
                minRadius = Math.min(minRadius, Math.hypot(ax, az) / Math.max(1e-6, turn));
            }
            System.out.printf(Locale.ROOT, "[downhill] %-8s %.0f m (straight %.0f m), %d jumps, %d berms, steepest climb %.2f, tightest radius %.1f m, features %s%n",
                    hill.name(), line.length(), Math.hypot(hill.finishX() - 10, hill.finishZ()), line.jumps(), line.berms(), worstClimb, minRadius, line.features());
            assertTrue(line.length() <= 200);
            assertTrue(worstClimb < .3, hill.name() + " climbs too steeply (" + worstClimb + ") " + line.features());
            assertTrue(minRadius > 3.5, hill.name() + " has a turn tighter than a rider can take: " + minRadius);
        }
    }

    @Test
    void theMoundIsGoneAroundAndTheStepBecomesADrop() {
        DownhillShapes mound = plan(HILLS.get(1), Style.FLOW, 3);
        double nearest = 1e9;
        for (double[] p : mound.centre()) {
            nearest = Math.min(nearest, Math.hypot(p[0] - 22, p[1] - 70));
        }
        assertTrue(nearest > 6, "the line goes over the top of the mound: " + nearest);
        DownhillShapes bend = plan(HILLS.get(4), Style.FLOW, 3);
        assertTrue(bend.berms() >= 1, "the 90 degree bend of the valley needs a berm: " + bend.features());
        DownhillShapes stepped = plan(HILLS.get(2), Style.FLOW, 3);
        assertTrue(stepped.features().stream().anyMatch(f -> f.kind() == Kind.DROP), "the 2 m step is a drop: " + stepped.features());
    }

    @Test
    void jumpsAreSizedAndLandingsAreLongEnough() {
        for (Hill hill : HILLS) {
            DownhillShapes line = plan(hill, Style.JUMPS, 3);
            long jumps = line.features().stream().filter(f -> f.kind() == Kind.KICKER || f.kind() == Kind.TABLE).count();
            System.out.printf(Locale.ROOT, "[downhill] %-8s jumps style: %d kickers/tables %s%n", hill.name(), jumps, line.features());
            assertTrue(jumps >= (hill.name().equals("bend") ? 1 : 3), hill.name() + " should get several jumps, got " + jumps);
            for (var jump : line.jumpDetails()) {
                String what = hill.name() + " " + jump;
                assertTrue(jump.height() >= 1.0 && jump.height() <= 1.5, "lip 1 to 1.5 m: " + what);
                assertTrue(jump.landing() >= jump.gap(), "the landing is at least as long as the gap: " + what);
                assertTrue(jump.gap() + jump.face() >= 3 && jump.gap() + jump.face() <= 6.1, "gap of 3 to 5 m plus the landing face: " + what);
                assertTrue(jump.length() >= 9 && jump.length() <= 28, "length " + jump.length());
            }
        }
    }

    @Test
    void ridesEveryStyleOnEveryHillside() {
        for (Hill hill : HILLS) {
            for (Style style : Style.values()) {
                for (double width : new double[]{3, 5}) {
                    DownhillShapes line = plan(hill, style, width);
                    DoubleBinaryOperator height = line.heights();
                    double length = line.length();
                    Result r = ride(height, line.centre(), 9, length / 6 + 25);
                    System.out.printf(Locale.ROOT, "[downhill] %-8s %-5s %.0f m wide, %3.0f m, %d jumps %d berms: %s%n", hill.name(), style, width, length, line.jumps(), line.berms(), r.summary());
                    String what = hill.name() + " " + style + " " + width + " m wide";
                    assertFalse(r.bailed, what + " bailed: " + r.why);
                    assertTrue(r.progress > .97, what + " only reached " + r.progress);
                    assertTrue(r.maxOff < 1.5, what + " left the line by " + r.maxOff);
                }
            }
        }
    }

    @Test
    void jumpsAreLandedAtEverySpeedAWidthAndAFasterRider() {
        for (Hill hill : HILLS) {
            for (double speed : new double[]{8, 12}) {
                DownhillShapes line = plan(hill, Style.JUMPS, 4);
                Result r = ride(line.heights(), line.centre(), speed, line.length() / 4 + 25);
                System.out.printf(Locale.ROOT, "[downhill] %-8s JUMPS 4 m wide at %.0f m/s (slower and faster than the plan): %s%n", hill.name(), speed, r.summary());
                assertFalse(r.bailed, hill.name() + " at " + speed + " m/s bailed: " + r.why);
                assertTrue(r.progress > .97, hill.name() + " at " + speed + " m/s only reached " + r.progress);
            }
        }
    }

    /** Diagnostics: every take-off and landing of one ride (set DOWNHILL_DEBUG=even:JUMPS:8). */
    @Test
    void debugOneRide() {
        String spec = System.getenv("DOWNHILL_DEBUG");
        if (spec == null) {
            return;
        }
        String[] parts = spec.split(":");
        Hill hill = HILLS.stream().filter(h -> h.name().equals(parts[0])).findFirst().orElseThrow();
        DownhillShapes line = plan(hill, Style.valueOf(parts[1]), parts.length > 3 ? Double.parseDouble(parts[3]) : 3);
        Result r = ride(line.heights(), line.centre(), Double.parseDouble(parts[2]), 60);
        System.out.println("[debug] " + line.features());
        if (TRACE != null) {
            for (double m = TRACE[0]; m <= TRACE[1]; m += .5) {
                System.out.printf(Locale.ROOT, "[debug] profile %.1f m: grade %.2f centre %.2f slope %.2f%n", m, line.gradeAt(m), line.centreHeight(m),
                        (line.centreHeight(m + .5) - line.centreHeight(m - .5)));
            }
        }
        for (int i = 0; i < line.centre().length; i += (i > 100 && i < 140) || i > 230 ? 4 : 20) {
            System.out.printf(Locale.ROOT, "[debug] route %.0f m: %.1f %.1f ground %.1f grade %.1f profile %.1f%n", i * .5, line.centre()[i][0], line.centre()[i][1],
                    hill.ground().applyAsDouble(line.centre()[i][0], line.centre()[i][1]), line.gradeAt(i * .5), line.centreHeight(i * .5));
        }
        r.events().forEach(e -> System.out.println("[debug] " + e));
        System.out.println("[debug] " + r.summary());
    }

    @Test
    void theBlocksOfALineStayWithinTheEditLimit() {
        for (Hill hill : HILLS) {
            for (double width : new double[]{3, 5}) {
                DownhillShapes line = plan(hill, Style.MIXED, width);
                int[] box = line.bounds();
                DoubleBinaryOperator height = line.heights();
                int columns = 0, layers = 0;
                for (int x = box[0]; x <= box[2]; x++) {
                    for (int z = box[1]; z <= box[3]; z++) {
                        if (!line.contains(x + .5, z + .5)) {
                            continue;
                        }
                        double[] h = {height.applyAsDouble(x, z), height.applyAsDouble(x + 1, z), height.applyAsDouble(x, z + 1), height.applyAsDouble(x + 1, z + 1)};
                        for (double corner : h) {
                            assertFalse(Double.isNaN(corner));
                        }
                        columns++;
                        layers += ColumnShaper.layers(h, false).layers().size();
                    }
                }
                System.out.printf(Locale.ROOT, "[downhill] %-8s %.0f m wide: %d columns, %d surface layers, box %d x %d%n", hill.name(), width, columns, layers,
                        box[2] - box[0] + 1, box[3] - box[1] + 1);
                assertTrue(layers < 4500, hill.name() + " needs " + layers + " surface blocks");
            }
        }
    }

    @Test
    void theLineCanBeBuiltInTheSameShapeTwice() {
        DownhillShapes a = plan(HILLS.get(0), Style.MIXED, 3), b = plan(HILLS.get(0), Style.MIXED, 3);
        assertEquals(a.features(), b.features());
        assertEquals(a.length(), b.length(), 1e-9);
    }

    @Test
    void tooClosePointsAreRejected() {
        assertThrows(DownhillShapes.Rejected.class, () -> DownhillShapes.plan(EVEN, 10, 0, 14, 4, new DownhillShapes.Params(Style.FLOW, 3, 200), 1));
        assertThrows(DownhillShapes.Rejected.class, () -> DownhillShapes.plan(EVEN, 10, 0, 30, 150, new DownhillShapes.Params(Style.FLOW, 3, 100), 1));
    }
}
