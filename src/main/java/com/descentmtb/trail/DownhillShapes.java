package com.descentmtb.trail;

import com.descentmtb.trail.BermShapes.Steepness;
import com.descentmtb.trail.TrailMath.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.DoubleBinaryOperator;

/**
 * Pure (Minecraft-free) planner of the Trail Shaper's downhill line ({@link ShapeMode#DOWNHILL}): from a start and
 * a finish on a hillside it makes a rideable run of dirt, as a height field the same code builds the blocks from
 * and the ride tests ride.
 *
 * <ol>
 *   <li>{@link DownhillRoute} finds a smooth path that follows the terrain.</li>
 *   <li>Where the path turns by more than 35 degrees within about 8 m the turn becomes a banked berm
 *       ({@link BermShapes}, medium steepness).</li>
 *   <li>The height along the path is the terrain smoothed into an even grade (never climbing, steps of 1.5 m and
 *       more turned into a steep drop with a smooth landing).</li>
 *   <li>On the straighter stretches, every 12 to 20 m, a feature is added to that grade by the {@link Style}:
 *       rollers, or a kicker with a gap and a landing (a table top on flat ground). Jumps are sized for the speed
 *       a rider has on that grade, and the landing is at least as long as the gap and slopes down at least as
 *       steeply as the flight path comes in.</li>
 * </ol>
 * The track is flat across its width and fades into the terrain beside it.
 */
public final class DownhillShapes {
    /** What the features along the line are. */
    public enum Style {
        /** Rollers and berms. */
        FLOW,
        /** Kickers with a gap and a landing, tables on flatter ground. */
        JUMPS,
        /** Alternating jumps and rollers. */
        MIXED;

        /** Language key of the name. */
        public String key() {
            return "descentmtb.downhill.style." + name().toLowerCase(Locale.ROOT);
        }

        /** The next (or previous, with a negative step) style; it wraps around. */
        public Style cycled(int step) {
            return values()[Math.floorMod(ordinal() + step, values().length)];
        }
    }

    /** What a feature is. */
    public enum Kind { KICKER, TABLE, ROLLERS, DROP, BERM }

    /** A feature along the line between {@code from} and {@code to} (m from the start). */
    public record Feature(Kind kind, double from, double to) {}

    /** @param width width of the track (m), {@code maxLength} the longest line (m) accepted */
    public record Params(Style style, double width, double maxLength) {}

    /** The line is not possible; {@link #key} is a language key. */
    public static final class Rejected extends IllegalArgumentException {
        private final Object[] args;

        Rejected(String key, Object... args) {
            super(key);
            this.args = args;
        }

        public String key() {
            return getMessage();
        }

        public Object[] args() {
            return args;
        }
    }

    /** Fade (m) from the track into the surrounding terrain. */
    public static final double MARGIN = 2;
    /** The shortest line (m). */
    private static final double MIN_LENGTH = 14;
    private static final double STEP = DownhillRoute.STEP;
    private static final double GRAVITY = 9.81;
    /** A turn of this many radians within {@link #TURN_WINDOW} samples gets a berm. */
    private static final double TURN = Math.toRadians(35);
    private static final int TURN_WINDOW = 8;
    /** The ground may not climb faster than this (rise over run). */
    private static final double MAX_CLIMB = .06;
    /** Flat ground below this grade gets tables instead of gap jumps. */
    private static final double TABLE_GRADE = .08;
    /** Steepness of the descent of a landing's decay curve. */
    private static final double LANDING_POWER = 2.4;
    /** Length (m) of the fall behind the lip. */
    private static final double PIT = 1.5;

    /** A feature that is added to the grade of the line as a bump. */
    private interface Placed {
        /** Height (m) the feature adds to the grade at {@code s} metres from the start. */
        double at(double s);

        Feature feature();
    }

    private record Berm(BermShapes shape, double from, double to) {}

    private final DoubleBinaryOperator ground;
    private final double[][] centre;
    private final double[] distance, target, profile, heading;
    private final List<Feature> features = new ArrayList<>();
    private final List<Berm> berms = new ArrayList<>();
    private final List<Jump> jumpList = new ArrayList<>();
    private final double half;
    private final java.util.Map<Long, List<Integer>> buckets = new java.util.HashMap<>();

    private DownhillShapes(DoubleBinaryOperator ground, double[][] centre, Params params) {
        this.ground = ground;
        this.centre = centre;
        this.half = params.width() / 2;
        int n = centre.length;
        distance = new double[n];
        for (int i = 1; i < n; i++) {
            distance[i] = distance[i - 1] + Math.hypot(centre[i][0] - centre[i - 1][0], centre[i][1] - centre[i - 1][1]);
        }
        target = new double[n];
        profile = new double[n];
        heading = headings(centre);
        for (int i = 0; i + 1 < n; i++) {
            buckets.computeIfAbsent(bucket(centre[i][0], centre[i][1]), k -> new ArrayList<>()).add(i);
        }
    }

    /**
     * @param ground terrain height (absolute Y) at any (x, z)
     * @param seed   decides the spacing of the features, so the same click gives the same line
     * @throws Rejected when the points are too close together or the line too long
     */
    public static DownhillShapes plan(DoubleBinaryOperator ground, double startX, double startZ, double finishX, double finishZ,
                                      Params params, long seed) {
        if (Math.hypot(finishX - startX, finishZ - startZ) < MIN_LENGTH) {
            throw new Rejected("descentmtb.downhill.too_close");
        }
        if (Double.isNaN(ground.applyAsDouble(startX, startZ)) || Double.isNaN(ground.applyAsDouble(finishX, finishZ))) {
            throw new Rejected("descentmtb.downhill.no_ground");
        }
        double[][] route = DownhillRoute.find(ground, startX, startZ, finishX, finishZ);
        if (DownhillRoute.length(route) > params.maxLength()) {
            throw new Rejected("descentmtb.downhill.too_long", (int) params.maxLength());
        }
        List<double[][]> turns = new ArrayList<>();
        route = DownhillRoute.resample(withBermCurves(route, params.width(), turns), STEP);

        DownhillShapes line = new DownhillShapes(ground, route, params);
        line.shapeGrade(turns);
        line.addBerms(turns, params.width());
        line.addFeatures(params.style(), new Random(seed));
        return line;
    }

    // ---- the route -----------------------------------------------------------------------------------------------

    /** Heading of the route at each point (rad), continuous across the full turn. */
    private static double[] headings(double[][] route) {
        int n = route.length;
        double[] heading = new double[n];
        double previous = 0;
        for (int i = 0; i < n; i++) {
            double[] a = route[Math.max(0, i - 2)], b = route[Math.min(n - 1, i + 2)];
            double angle = Math.atan2(b[1] - a[1], b[0] - a[0]);
            if (i > 0) {
                while (angle - previous > Math.PI) {
                    angle -= 2 * Math.PI;
                }
                while (angle - previous < -Math.PI) {
                    angle += 2 * Math.PI;
                }
            }
            heading[i] = previous = angle;
        }
        return heading;
    }

    /**
     * Replaces the sharp turns of the route by the curves of berms. Every turn found is added to {@code turns} as
     * the three points {@code {entry, apex, exit}} (x, z) the berm is built through.
     */
    private static double[][] withBermCurves(double[][] route, double width, List<double[][]> turns) {
        int n = route.length;
        double[] heading = headings(route);
        List<int[]> runs = new ArrayList<>();   // {first sample, last sample, sign}
        for (int i = TURN_WINDOW; i < n - TURN_WINDOW; i++) {
            double change = heading[i + TURN_WINDOW] - heading[i - TURN_WINDOW];
            if (Math.abs(change) < TURN) {
                continue;
            }
            int sign = change > 0 ? 1 : -1;
            int[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (last != null && last[2] == sign && i - last[1] <= 12) {
                last[1] = i;
            } else {
                runs.add(new int[]{i, i, sign});
            }
        }
        List<double[]> out = new ArrayList<>();
        int copied = 0;
        for (int[] run : runs) {
            int a = Math.max(copied, run[0] - TURN_WINDOW / 2), c = Math.min(n - 1, run[1] + TURN_WINDOW / 2);
            if (a < copied || (c - a) * STEP < 6 || (c - a) * STEP > 70) {
                continue;
            }
            double[] entry = route[a], apex = route[(a + c) / 2], exit = route[c];
            BermShapes berm;
            try {
                berm = BermShapes.of(new Point(entry[0], 0, entry[1]), new Point(apex[0], 0, apex[1]), new Point(exit[0], 0, exit[1]),
                        new BermShapes.Params(width, Steepness.MEDIUM), 80);
            } catch (BermShapes.Rejected e) {
                continue;
            }
            for (int i = copied; i < a; i++) {
                out.add(route[i]);
            }
            for (int k = 0; k <= 64; k++) {
                Point p = berm.centre(k / 64.0);
                out.add(new double[]{p.x(), p.z()});
            }
            turns.add(new double[][]{entry, apex, exit});
            copied = c + 1;
        }
        for (int i = copied; i < n; i++) {
            out.add(route[i]);
        }
        return out.toArray(new double[0][]);
    }

    // ---- the grade -----------------------------------------------------------------------------------------------

    /** The even grade of the line: terrain smoothed, never climbing, steps turned into smooth drops. */
    private void shapeGrade(List<double[][]> turns) {
        int n = centre.length;
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double h = ground.applyAsDouble(centre[i][0], centre[i][1]);
            raw[i] = Double.isNaN(h) ? (i > 0 ? raw[i - 1] : 0) : h;
        }
        // steps of 1.5 m and more are taken out, smoothed over and then put back as a long steep drop
        double[] levelled = raw.clone();
        boolean[] inTurn = new boolean[n];
        for (double[][] turn : turns) {
            for (int i = nearestSample(turn[0][0], turn[0][1]) - 4; i <= nearestSample(turn[2][0], turn[2][1]) + 4; i++) {
                inTurn[Math.max(0, Math.min(n - 1, i))] = true;
            }
        }
        List<double[]> drops = findDrops(raw, inTurn);
        for (double[] drop : drops) {
            for (int j = (int) drop[0] + 1; j < n; j++) {
                levelled[j] += drop[1];
            }
        }
        double[] smooth = blur(blur(levelled, 6), 6);
        for (double[] drop : drops) {
            double length = 3 * drop[1], at = distance[(int) drop[0]];
            for (int j = 0; j < n; j++) {
                smooth[j] -= drop[1] * PumpMath.smooth((distance[j] - at) / length + .5);
            }
            features.add(new Feature(Kind.DROP, at - length / 2, at + length / 2));
        }
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                smooth[i] = Math.min(smooth[i], smooth[i - 1] + MAX_CLIMB * STEP);
            }
            target[i] = Math.max(raw[i] - 3, Math.min(raw[i] + 3, smooth[i]));
            profile[i] = target[i];
        }
    }

    /** Steps in the terrain (outside the turns, where the line cuts the corner): {@code {sample, height}} where it drops by 1.5 m or more within 3 m beyond the grade. */
    private static List<double[]> findDrops(double[] raw, boolean[] inTurn) {
        int n = raw.length;
        double[] step = new double[n];
        for (int i = 3; i < n - 3; i++) {
            int from = Math.max(0, i - 16), to = Math.min(n - 1, i + 16);
            double grade = (raw[from] - raw[to]) / Math.max(1, to - from);
            step[i] = (raw[i - 3] - raw[i + 3]) - 6 * grade;
        }
        List<double[]> drops = new ArrayList<>();
        for (int i = 3; i < n - 3; i++) {
            if (step[i] < 1.5 || inTurn[i]) {
                continue;
            }
            boolean peak = true;
            for (int j = Math.max(3, i - 8); j <= Math.min(n - 4, i + 8); j++) {
                peak &= step[j] <= step[i] && (step[j] < step[i] || j >= i);
            }
            if (peak && (drops.isEmpty() || i - drops.get(drops.size() - 1)[0] > 16)) {
                drops.add(new double[]{i, Math.min(4, step[i])});
            }
        }
        return drops;
    }

    /** A triangular moving average over {@code radius} samples each side. */
    private static double[] blur(double[] values, int radius) {
        int n = values.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0, weight = 0;
            for (int k = -radius; k <= radius; k++) {
                double w = radius + 1 - Math.abs(k);
                sum += w * values[Math.max(0, Math.min(n - 1, i + k))];
                weight += w;
            }
            out[i] = sum / weight;
        }
        return out;
    }

    // ---- berms ---------------------------------------------------------------------------------------------------

    private void addBerms(List<double[][]> turns, double width) {
        for (double[][] t : turns) {
            double from = distance[nearestSample(t[0][0], t[0][1])], to = distance[nearestSample(t[2][0], t[2][1])];
            try {
                BermShapes berm = BermShapes.of(point(t[0]), point(t[1]), point(t[2]),
                        new BermShapes.Params(width, Steepness.MEDIUM), 80);
                berms.add(new Berm(berm, from, to));
                features.add(new Feature(Kind.BERM, from, to));
            } catch (BermShapes.Rejected ignored) {
                // the curve stays as a plain flat turn
            }
        }
    }

    private Point point(double[] xz) {
        return new Point(xz[0], gradeAt(distance[nearestSample(xz[0], xz[1])]), xz[1]);
    }

    private int nearestSample(double x, double z) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < centre.length; i++) {
            double d = Math.hypot(centre[i][0] - x, centre[i][1] - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    // ---- features ------------------------------------------------------------------------------------------------

    /** Height (m) of the grade of the line at {@code s} metres from the start: the terrain smoothed, without the features. */
    public double gradeAt(double s) {
        return interpolate(target, s);
    }

    private double interpolate(double[] values, double s) {
        double at = Math.max(0, Math.min(values.length - 1.0001, s / STEP));
        int i = (int) at;
        return values[i] + (values[i + 1] - values[i]) * (at - i);
    }

    /** Slope (rise over run) of the grade at {@code s}. */
    private double slopeAt(double s) {
        return (gradeAt(s + 1.5) - gradeAt(s - 1.5)) / 3;
    }

    /** The speed (m/s) a rider is likely to have at {@code s}, from the drop in the last stretch. */
    private double speedAt(double s) {
        double from = Math.max(0, s - 15);
        double descent = Math.max(0, (gradeAt(from) - gradeAt(s)) / Math.max(1, s - from));
        return Math.max(7, Math.min(13, 6.5 + 18 * descent));
    }

    /** The speed (m/s) a rider is likely to have leaving a lip at {@code s}: what is left after the kicker. */
    private double lipSpeedAt(double s) {
        double from = Math.max(0, s - 15);
        double descent = Math.max(0, (gradeAt(from) - gradeAt(s)) / Math.max(1, s - from));
        return Math.max(6, Math.min(11, 5.5 + 14 * descent));
    }

    private void addFeatures(Style style, Random random) {
        double total = distance[distance.length - 1];
        List<Placed> placed = new ArrayList<>();
        double cursor = 10;
        int index = 0;
        while (cursor < total - 14) {
            boolean jump = style == Style.JUMPS || style == Style.MIXED && index % 2 == 0;
            Placed made = null;
            for (double s = cursor; s < cursor + 24 && made == null; s++) {
                made = jump ? jump(s, total) : rollers(s, total, random.nextInt(2) + 2);
            }
            if (made == null) {
                cursor += 10;
                continue;
            }
            placed.add(made);
            if (made instanceof Jump kicker) {
                jumpList.add(kicker);
            }
            features.add(made.feature());
            cursor = made.feature().to() + 12 + 8 * random.nextDouble();
            index++;
        }
        features.sort((a, b) -> Double.compare(a.from(), b.from()));
        for (int i = 0; i < profile.length; i++) {
            double lift = 0;
            for (Placed feature : placed) {
                lift += feature.at(distance[i]);
            }
            profile[i] = target[i] + lift;
        }
    }

    /** True when the stretch is clear of turns, drops and the ends of the line, so a feature fits there. */
    private boolean free(double from, double to, double total) {
        if (to > total - 8 || from < 8) {
            return false;
        }
        for (Feature other : features) {
            if (from < other.to() + 3 && to > other.from() - 3) {
                return false;
            }
        }
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (int i = (int) (from / STEP); i <= Math.min(heading.length - 1, (int) (to / STEP)); i++) {
            low = Math.min(low, heading[i]);
            high = Math.max(high, heading[i]);
        }
        return high - low < Math.toRadians(14);
    }

    /** Rollers: a few waves of the pumptrack profile ({@link PumpMath#wave}) laid on the grade. */
    private record Rollers(double from, double spacing, double height, int count) implements Placed {
        @Override
        public double at(double s) {
            double d = s - from;
            return d <= 0 || d >= spacing * count ? 0 : PumpMath.wave(d, spacing, height);
        }

        @Override
        public Feature feature() {
            return new Feature(Kind.ROLLERS, from, from + spacing * count);
        }
    }

    private Rollers rollers(double from, double total, int count) {
        double speed = speedAt(from), spacing = Math.max(5.5, Math.min(7, .6 * speed));
        double height = Math.max(.4, Math.min(.5, .25 + .02 * speed));
        return free(from, from + spacing * count, total) ? new Rollers(from, spacing, height, count) : null;
    }

    /**
     * A kicker with a gap and a landing, or (on flat ground) a table. Lengths are metres from {@code from}:
     * the kicker rises to {@code lip} over {@code kicker} and ends in the lip; then comes the gap (the table top),
     * the face of the landing, and the landing, which falls back to the grade with a decay curve.
     */
    record Jump(boolean table, double from, double height, double kicker, double gap, double face,
                        double landingHeight, double landing) implements Placed {
        double length() {
            return kicker + gap + (table ? 0 : face) + landing;
        }

        @Override
        public double at(double s) {
            double u = s - from;
            if (u <= 0) {
                return 0;
            }
            if (u <= kicker) {
                return height * Math.pow(u / kicker, 2);
            }
            double v = u - kicker;
            if (table) {
                return v <= gap ? height : decay(v - gap);
            }
            if (v < gap) {
                return v < PIT ? height * (1 - PumpMath.smooth(v / PIT)) : 0;
            }
            return v < gap + face ? landingHeight * PumpMath.smooth((v - gap) / face) : decay(v - gap - face);
        }

        private double decay(double w) {
            return w >= landing ? 0 : landingHeight * Math.pow(1 - w / landing, LANDING_POWER);
        }

        @Override
        public Feature feature() {
            return new Feature(table ? Kind.TABLE : Kind.KICKER, from, from + length());
        }
    }

    private Jump jump(double from, double total) {
        double expected = lipSpeedAt(from), speed = .88 * expected;   // plan for a little slower: falling long is better than falling short
        boolean table = -slopeAt(from + 6) < TABLE_GRADE;
        double lip = Math.max(1.0, Math.min(1.5, .35 + .09 * expected));
        double exit = Math.max(16, Math.min(25, 16 + 2 * (expected - 6)));   // slower riders get a gentler kick
        double kicker = lip * 2 / Math.tan(Math.toRadians(exit));
        double start = from + kicker;

        // the flight path from the lip: launch along the kicker, ballistic after that; where does it meet the grade?
        double launch = slopeAt(start) + 2 * lip / kicker;
        double vx = speed * Math.cos(Math.atan(launch));
        double reach = 14;
        for (double x = 1; x < 14; x += .1) {
            if (flightOver(start, lip, launch, vx, x) <= 0) {
                reach = x;
                break;
            }
        }

        // the landing begins under the flight path, a metre before it would touch the ground
        double crest = table ? Math.max(3.5, Math.min(5, reach - 1)) : Math.max(3.5, Math.min(6, reach - 1));
        double landingHeight = table ? lip : Math.max(.3, Math.min(.8, flightOver(start, lip, launch, vx, crest) - .3));
        double face = table ? 0 : Math.max(1.5, Math.min(2.2, 2.5 * landingHeight));
        double gap = crest - face;

        // the longest landing that still slopes down as steeply as the flight path comes in
        double touch = crest + 1;
        double pathDown = GRAVITY * touch / (vx * vx) - launch, ground = -slopeAt(start + touch);
        double landing = Math.max(gap, table ? 6 : 4.5);
        for (double candidate = 10; candidate >= landing; candidate -= .5) {
            double slope = ground + landingHeight * LANDING_POWER * Math.pow(1 - 1 / candidate, LANDING_POWER - 1) / candidate;
            if (slope >= pathDown) {
                landing = candidate;
                break;
            }
        }
        Jump jump = new Jump(table, from, lip, kicker, gap, face, landingHeight, landing);
        return free(from - 2, from + jump.length() + 2, total) ? jump : null;
    }

    /** Height (m) of the flight path above the grade {@code x} metres after the lip. */
    private double flightOver(double lipAt, double lip, double launch, double vx, double x) {
        double path = lip + launch * x - GRAVITY * x * x / (2 * vx * vx);
        return path - (gradeAt(lipAt + x) - gradeAt(lipAt));
    }

    // ---- the height field ----------------------------------------------------------------------------------------

    private static long bucket(double x, double z) {
        return ((long) Math.floor(x / 6) << 32) ^ ((long) Math.floor(z / 6) & 0xffffffffL);
    }

    /** {@code {position as a sample index, distance from the centre line}} of the nearest point; null when far away. */
    private double[] locate(double x, double z) {
        double best = Double.MAX_VALUE, at = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<Integer> segments = buckets.get(bucket(x + 6 * dx, z + 6 * dz));
                if (segments == null) {
                    continue;
                }
                for (int i : segments) {
                    double ax = centre[i][0], az = centre[i][1], bx = centre[i + 1][0] - ax, bz = centre[i + 1][1] - az;
                    double t = Math.max(0, Math.min(1, ((x - ax) * bx + (z - az) * bz) / Math.max(1e-9, bx * bx + bz * bz)));
                    double d = Math.hypot(x - ax - bx * t, z - az - bz * t);
                    if (d < best) {
                        best = d;
                        at = i + t;
                    }
                }
            }
        }
        return best > half + MARGIN ? null : new double[]{at, best};
    }

    /** Absolute height of the line at (x, z), feathered into the terrain. */
    public DoubleBinaryOperator heights() {
        return (x, z) -> {
            for (Berm berm : berms) {
                if (berm.shape().contains(x, z)) {
                    return berm.shape().heights(ground).applyAsDouble(x, z);
                }
            }
            double[] near = locate(x, z);
            double terrain = ground.applyAsDouble(x, z);
            if (near == null) {
                return terrain;
            }
            int i = Math.min(profile.length - 2, (int) near[0]);
            double y = profile[i] + (profile[i + 1] - profile[i]) * (near[0] - i);
            return Double.isNaN(terrain) ? y : terrain + (1 - PumpMath.smooth((near[1] - half) / MARGIN)) * (y - terrain);
        };
    }

    /** True within the width of the track itself (and half a metre beyond): the cells that become trail dirt. */
    public boolean onTrack(double x, double z) {
        double[] near = locate(x, z);
        if (near != null && near[1] <= half + .5) {
            return true;
        }
        for (Berm berm : berms) {
            if (berm.shape().contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** True for the ground columns the line may change (its footprint). */
    public boolean contains(double x, double z) {
        if (locate(x, z) != null) {
            return true;
        }
        for (Berm berm : berms) {
            if (berm.shape().contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** Block bounds {@code {minX, minZ, maxX, maxZ}} of the footprint. */
    public int[] bounds() {
        double reach = half + MARGIN + 1;
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : centre) {
            minX = Math.min(minX, p[0] - reach);
            maxX = Math.max(maxX, p[0] + reach);
            minZ = Math.min(minZ, p[1] - reach);
            maxZ = Math.max(maxZ, p[1] + reach);
        }
        int[] box = {(int) Math.floor(minX), (int) Math.floor(minZ), (int) Math.ceil(maxX), (int) Math.ceil(maxZ)};
        for (Berm berm : berms) {
            int[] b = berm.shape().bounds();
            box = new int[]{Math.min(box[0], b[0]), Math.min(box[1], b[1]), Math.max(box[2], b[2]), Math.max(box[3], b[3])};
        }
        return box;
    }

    // ---- what was made -------------------------------------------------------------------------------------------

    /** The centre line as {@code {x, z}} points half a metre apart; ride it. */
    public double[][] centre() {
        return centre;
    }

    /** Length of the line (m). */
    public double length() {
        return distance[distance.length - 1];
    }

    /** The features along the line, from the start. */
    public List<Feature> features() {
        return features;
    }

    /** Height of the centre line (m) at {@code s} metres from the start, features included. */
    public double centreHeight(double s) {
        return interpolate(profile, s);
    }

    /** The dimensions of the kickers and tables (for the tests). */
    List<Jump> jumpDetails() {
        return jumpList;
    }

    /** Kickers, tables and drops: everything with some air. */
    public int jumps() {
        return (int) features.stream().filter(f -> f.kind() == Kind.KICKER || f.kind() == Kind.TABLE || f.kind() == Kind.DROP).count();
    }

    public int berms() {
        return (int) features.stream().filter(f -> f.kind() == Kind.BERM).count();
    }
}
