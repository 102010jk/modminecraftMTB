package com.descentmtb.map;

/**
 * Bike-park difficulty of a recorded trail, the way trail signs grade it (green / blue / red / black), read from the
 * shape of the track alone: overall gradient, the steepest sustained pitch and the biggest drop. Pure, unit-tested.
 */
public enum TrailDifficulty {
    /** Flow: wide, gentle, no drops. */
    GREEN(0xff3f9b4f, "Easy"),
    /** Rollers, small berms, table-tops. */
    BLUE(0xff3a72c4, "Intermediate"),
    /** Steep chutes, rock gardens, gaps. */
    RED(0xffd0443a, "Advanced"),
    /** Big drops, road gaps, extreme pitch. */
    BLACK(0xff22262b, "Pro Line");

    public final int argb;
    public final String label;

    TrailDifficulty(int argb, String label) {
        this.argb = argb;
        this.label = label;
    }

    /** Horizontal window (blocks) over which a pitch counts as sustained rather than a single step. */
    static final double PITCH_WINDOW = 6;

    /** What the grading looked at. Grades are rise/run in percent; drop is in blocks. */
    public record Profile(double averageGrade, double steepestGrade, double biggestDrop) {}

    public static TrailDifficulty of(int[] pts) {
        return of(profile(pts));
    }

    public static TrailDifficulty of(Profile p) {
        if (p.biggestDrop >= 3 || p.steepestGrade >= 45 || p.averageGrade >= 25) return BLACK;
        if (p.biggestDrop >= 1.5 || p.steepestGrade >= 30 || p.averageGrade >= 15) return RED;
        if (p.biggestDrop >= 0.8 || p.steepestGrade >= 15 || p.averageGrade >= 7) return BLUE;
        return GREEN;
    }

    public static Profile profile(int[] pts) {
        int n = pts.length / 3;
        if (n < 2) return new Profile(0, 0, 0);
        double unit = TrackGeometry.UNIT;
        double[] run = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            y[i] = pts[i * 3 + 1] / unit;
            if (i > 0) run[i] = run[i - 1] + Math.hypot(pts[i * 3] - pts[i * 3 - 3], pts[i * 3 + 2] - pts[i * 3 - 1]) / unit;
        }
        double length = run[n - 1];
        double descent = 0, drop = 0, steepest = 0;
        for (int i = 1; i < n; i++) {
            double dy = y[i - 1] - y[i];
            if (dy > 0) descent += dy;
            // a fall over almost no ground is a drop (jumped off), not a slope
            double horizontal = run[i] - run[i - 1];
            if (dy > 0 && horizontal < Math.max(1.5, dy * 0.8)) drop = Math.max(drop, dy);
        }
        // steepest sustained descent: every window at least PITCH_WINDOW long (two-pointer sweep)
        int j = 0;
        for (int i = 1; i < n; i++) {
            while (j < i && run[i] - run[j + 1] >= PITCH_WINDOW) j++;
            double span = run[i] - run[j];
            if (span >= PITCH_WINDOW) steepest = Math.max(steepest, (y[j] - y[i]) / span * 100);
        }
        if (steepest == 0 && length > 0) steepest = Math.max(0, (y[0] - y[n - 1]) / length * 100);
        double average = length > 0 ? descent / length * 100 : 0;
        return new Profile(average, steepest, drop);
    }

    /**
     * Catmull-Rom sampling of a polyline (any number of dimensions packed per point, {@code dims} values each), so a
     * coarse recorded track draws as a smooth curve. Returns {@code segments * steps + 1} points through every input
     * point; with fewer than three points the input comes back as it is.
     */
    public static double[] smooth(double[] points, int dims, int steps) {
        int n = points.length / dims;
        if (n < 3 || steps < 2) return points.clone();
        double[] out = new double[((n - 1) * steps + 1) * dims];
        int o = 0;
        for (int seg = 0; seg < n - 1; seg++) {
            int p0 = Math.max(0, seg - 1), p1 = seg, p2 = seg + 1, p3 = Math.min(n - 1, seg + 2);
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps, t2 = t * t, t3 = t2 * t;
                for (int d = 0; d < dims; d++) {
                    double a = points[p0 * dims + d], b = points[p1 * dims + d], c = points[p2 * dims + d], e = points[p3 * dims + d];
                    out[o++] = 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - e) * t2 + (-a + 3 * b - 3 * c + e) * t3);
                }
            }
        }
        for (int d = 0; d < dims; d++) out[o++] = points[(n - 1) * dims + d];
        return out;
    }
}
