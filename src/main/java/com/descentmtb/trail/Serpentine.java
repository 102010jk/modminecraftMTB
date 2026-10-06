package com.descentmtb.trail;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (Minecraft-free) geometry of the switchbacks of a downhill line with a limited grade ({@link
 * DownhillShapes.Grade}). When the hillside drops faster than the trail may, the way down has to be longer than the
 * straight way: it zigzags across the fall line, long traverses joined by {@code n} hairpins (the first and the last
 * traverse are half as long, so every traverse falls by about the same share). The traverses
 * run between the start and the finish at up to {@code A} metres to each side of the line that joins them, the hairpins
 * are circular turns of radius {@link #RADIUS} made of banked berm pieces that meet end to end.
 *
 * <p>The planner picks the fewest hairpins with which the trail can lose the whole drop at its grade limit: the
 * traverses count with the full grade, the platform of a hairpin with {@link #PLATFORM} of it (a turn is kept nearly
 * flat), and the half width {@code A} is then as small as that allows (at most {@link #MAX_HALF_WIDTH}).
 */
final class Serpentine {
    /** The widest the zigzag may swing to each side of the line from start to finish (m), the outer edge of its hairpins. */
    static final double MAX_HALF_WIDTH = 35;
    /** The narrowest swing worth making (m). */
    static final double MIN_HALF_WIDTH = 8;
    /** Radius (m) of a hairpin: a bike takes it at about 5 m/s. */
    static final double RADIUS = 7;
    /** The most hairpins of one line. */
    static final int MAX_HAIRPINS = 16;
    /** The share of the grade limit a hairpin's platform may fall: it is nearly flat so the bike can lean into the turn. */
    static final double PLATFORM = .35;
    /** One piece of a hairpin turns the track by at most this much (a quadratic berm curve is a fair arc up to there). */
    private static final double PIECE_TURN = Math.toRadians(80);
    /** The least distance (m) between neighbouring traverses along the line from start to finish: a track, its margins and a berm. */
    private static final double MIN_SPACING = 11;
    /** What the whole drop is multiplied by: the margin the planner keeps below the limit. */
    private static final double MARGIN = 1.1;

    /** A traverse: a straight between the end of one hairpin and the start of the next ({@code {x, z}} points). */
    record Leg(double[] from, double[] to) {}

    /**
     * A hairpin: berm pieces as the three points {@code {entry, apex, exit}} each (x, z), chained end to end, from
     * the tangent point where the track leaves the traverse to the one where it joins the next.
     */
    record Hairpin(List<double[][]> pieces, double arcLength) {
        double[] entry() {
            return pieces.get(0)[0];
        }

        double[] exit() {
            return pieces.get(pieces.size() - 1)[2];
        }
    }

    /** The planned zigzag: traverses and hairpins alternate, starting and ending with a traverse. */
    record Layout(List<Leg> legs, List<Hairpin> hairpins, double length, double halfWidth) {}

    /**
     * Plans the zigzag from the start to the finish.
     *
     * @param drop      how far the finish lies below the start (m)
     * @param slope     the steepest grade of the trail (rise over run)
     * @param maxLength the longest line (m) accepted
     * @throws DownhillShapes.Rejected when the way down cannot be made with that grade, or is longer than {@code maxLength}
     */
    static Layout plan(double sx, double sz, double fx, double fz, double drop, double slope, double maxLength) {
        double needed = drop * MARGIN;
        Layout fallback = null;
        for (int n = 1; n <= MAX_HAIRPINS; n++) {
            Layout widest = layout(sx, sz, fx, fz, n, MAX_HALF_WIDTH, slope, needed);
            if (widest == null) {
                break;   // more hairpins only make the traverses shorter
            }
            fallback = widest;
            if (capacity(widest, slope) < needed) {
                continue;
            }
            Layout best = widest;
            for (double a = MAX_HALF_WIDTH - 1; a >= MIN_HALF_WIDTH; a--) {
                Layout narrower = layout(sx, sz, fx, fz, n, a, slope, needed);
                if (narrower == null || capacity(narrower, slope) < needed) {
                    break;
                }
                best = narrower;
            }
            if (best.length() > maxLength) {
                throw new DownhillShapes.Rejected("descentmtb.downhill.too_long", (int) maxLength);
            }
            return best;
        }
        if (fallback != null && fallback.length() > maxLength) {
            throw new DownhillShapes.Rejected("descentmtb.downhill.too_long", (int) maxLength);
        }
        throw new DownhillShapes.Rejected("descentmtb.downhill.grade_impossible");
    }

    /** How much height (m) a trail with this grade limit can lose along the layout. */
    static double capacity(Layout layout, double slope) {
        double straight = 0, arcs = 0;
        for (Leg leg : layout.legs()) {
            straight += Math.hypot(leg.to()[0] - leg.from()[0], leg.to()[1] - leg.from()[1]);
        }
        for (Hairpin hairpin : layout.hairpins()) {
            arcs += hairpin.arcLength();
        }
        return slope * (straight + PLATFORM * arcs);
    }

    /**
     * The zigzag with {@code n} hairpins: circles of radius {@link #RADIUS} {@code a - radius} metres to each side of the
     * line from start to finish, spaced along it so the first and the last traverse are half as long as the others, and the straights between them (the tangent lines of
     * neighbouring circles, in turn left and right). Null when the circles are too close together or the straights too short.
     */
    static Layout layout(double sx, double sz, double fx, double fz, int n, double a, double slope, double needed) {
        double length = Math.hypot(fx - sx, fz - sz);
        double spacing = length / n;
        double radius = RADIUS;
        if (spacing < MIN_SPACING || a < radius + 2) {
            return null;
        }
        double ux = (fx - sx) / length, uz = (fz - sz) / length, lx = -uz, lz = ux;
        double[][] centre = new double[n][];
        double[] sense = new double[n];
        for (int k = 0; k < n; k++) {
            double along = spacing * (k + .5), side = k % 2 == 0 ? a - radius : -(a - radius);
            centre[k] = new double[]{sx + ux * along + lx * side, sz + uz * along + lz * side};
            sense[k] = side > 0 ? -1 : 1;   // heading across the line and then down it is a right turn (clockwise) on the right-hand circle
        }
        // headings of the straights: 0 = from the start, k = after circle k - 1, n = into the finish
        double[] heading = new double[n + 1];
        double[] straight = new double[n + 1];
        for (int k = 0; k <= n; k++) {
            double[] from = k == 0 ? new double[]{sx, sz} : centre[k - 1], to = k == n ? new double[]{fx, fz} : centre[k];
            double dx = to[0] - from[0], dz = to[1] - from[1], distance = Math.hypot(dx, dz), delta = Math.atan2(dz, dx);
            if (k == 0) {
                if (distance < radius * 1.05) {
                    return null;
                }
                heading[k] = delta - Math.asin(radius * sense[0] / distance);
                straight[k] = Math.sqrt(distance * distance - radius * radius);
            } else if (k == n) {
                if (distance < radius * 1.05) {
                    return null;
                }
                heading[k] = delta + Math.asin(radius * sense[n - 1] / distance);
                straight[k] = Math.sqrt(distance * distance - radius * radius);
            } else {
                if (distance < 2.1 * radius) {
                    return null;
                }
                heading[k] = delta + Math.asin(2 * radius * sense[k - 1] / distance);
                straight[k] = Math.sqrt(distance * distance - 4 * radius * radius);
            }
        }
        List<Leg> legs = new ArrayList<>();
        List<Hairpin> hairpins = new ArrayList<>();
        double total = 0;
        double[][] arrive = new double[n][], depart = new double[n][];
        for (int k = 0; k < n; k++) {
            arrive[k] = onCircle(centre[k], radius, sense[k], heading[k]);
            depart[k] = onCircle(centre[k], radius, sense[k], heading[k + 1]);
        }
        for (int k = 0; k <= n; k++) {
            double[] from = k == 0 ? new double[]{sx, sz} : depart[k - 1], to = k == n ? new double[]{fx, fz} : arrive[k];
            legs.add(new Leg(from, to));
            total += straight[k];
        }
        for (int k = 0; k < n; k++) {
            double turn = ((sense[k] * (heading[k + 1] - heading[k])) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            if (turn < Math.toRadians(30) || turn > Math.toRadians(300)) {
                return null;
            }
            Hairpin hairpin = arc(centre[k], radius, sense[k], arrive[k], turn);
            hairpins.add(hairpin);
            total += hairpin.arcLength();
        }
        return new Layout(legs, hairpins, total, a);
    }

    /** The point of the circle where a track of the given sense of turning has the heading {@code heading}. */
    private static double[] onCircle(double[] centre, double radius, double sense, double heading) {
        return new double[]{centre[0] + radius * sense * Math.sin(heading), centre[1] - radius * sense * Math.cos(heading)};
    }

    /** The turn of {@code angle} radians around {@code centre}, from the point {@code start} on the circle, as chained berm pieces. */
    private static Hairpin arc(double[] centre, double radius, double sense, double[] start, double angle) {
        double first = Math.atan2(start[1] - centre[1], start[0] - centre[0]);
        int pieces = Math.max(1, (int) Math.ceil(angle / PIECE_TURN));
        double each = angle / pieces;
        List<double[][]> list = new ArrayList<>();
        for (int j = 0; j < pieces; j++) {
            double a0 = first + sense * each * j, a1 = a0 + sense * each, mid = a0 + sense * each / 2;
            double[] p0 = {centre[0] + radius * Math.cos(a0), centre[1] + radius * Math.sin(a0)};
            double[] p1 = {centre[0] + radius * Math.cos(a1), centre[1] + radius * Math.sin(a1)};
            double pull = radius / Math.cos(each / 2);
            double[] control = {centre[0] + pull * Math.cos(mid), centre[1] + pull * Math.sin(mid)};
            double[] apex = {.25 * p0[0] + .5 * control[0] + .25 * p1[0], .25 * p0[1] + .5 * control[1] + .25 * p1[1]};
            list.add(new double[][]{p0, apex, p1});
        }
        return new Hairpin(list, radius * angle);
    }

    /** The point of a berm piece (entry, apex, exit) at {@code t} in 0..1 along its quadratic curve through the apex. */
    static double[] curve(double[][] piece, double t) {
        double[] a = piece[0], b = piece[1], c = piece[2];
        double cx = 2 * b[0] - (a[0] + c[0]) / 2, cz = 2 * b[1] - (a[1] + c[1]) / 2, u = 1 - t;
        return new double[]{u * u * a[0] + 2 * u * t * cx + t * t * c[0], u * u * a[1] + 2 * u * t * cz + t * t * c[1]};
    }

    private Serpentine() {}
}
