package com.descentmtb.map;

import java.util.Arrays;

/**
 * Pure helpers for recorded trails. A track is a packed {@code int[]} of x, y, z triples in tenths of a block
 * ({@link #UNIT} per block), so a track is compact, exact to 10 cm and cheap to compare, store and send. No
 * Minecraft types here: everything is unit-tested.
 */
public final class TrackGeometry {
    /** Packed units per block. */
    public static final int UNIT = 10;
    /** Most points a stored track may have. */
    public static final int MAX_POINTS = 2500;
    /** Fewest points a track needs to count. */
    public static final int MIN_POINTS = 2;
    /** A track shorter than this (blocks, horizontally) is thrown away. */
    public static final double MIN_LENGTH = 5;

    /** Coordinate limits (packed units) a track point may have: the world border, and a generous height range. */
    private static final int XZ_LIMIT = 31_000_000 * UNIT;
    private static final int Y_MIN = -512 * UNIT, Y_MAX = 1024 * UNIT;

    /**
     * What a track measures. Length is horizontal (the grade is a rise over a horizontal run), ascent and descent
     * are the sums of all upward and downward steps, grade is {@code descent / length} in percent.
     */
    public record Stats(double length, double ascent, double descent, double grade) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0);
    }

    public static int count(int[] pts) {
        return pts.length / 3;
    }

    /** Whether the array is a well-formed track: whole triples, within the size limit, inside the world. */
    public static boolean valid(int[] pts) {
        if (pts == null || pts.length % 3 != 0 || pts.length > MAX_POINTS * 3) {
            return false;
        }
        for (int i = 0; i < pts.length; i += 3) {
            if (Math.abs(pts[i]) > XZ_LIMIT || Math.abs(pts[i + 2]) > XZ_LIMIT || pts[i + 1] < Y_MIN || pts[i + 1] > Y_MAX) {
                return false;
            }
        }
        return true;
    }

    /** Packs a world coordinate into track units (rounded, clamped to a safe range). */
    public static int pack(double blocks) {
        return (int) Math.max(-2_000_000_000L, Math.min(2_000_000_000L, Math.round(blocks * UNIT)));
    }

    // ---- simplification ----

    /**
     * Douglas-Peucker: drops every point that lies within {@code toleranceBlocks} (3D distance) of the line
     * between its kept neighbours. The first and last point are always kept. Iterative, so a long track cannot
     * overflow the stack.
     */
    public static int[] simplify(int[] pts, double toleranceBlocks) {
        int n = count(pts);
        if (n <= 2) {
            return pts.clone();
        }
        double tol = toleranceBlocks * UNIT;
        boolean[] keep = new boolean[n];
        keep[0] = keep[n - 1] = true;
        int[] stack = new int[n * 2 + 4];
        int top = 0;
        stack[top++] = 0;
        stack[top++] = n - 1;
        while (top > 0) {
            int hi = stack[--top];
            int lo = stack[--top];
            if (hi - lo < 2) {
                continue;
            }
            double worst = -1;
            int at = -1;
            for (int i = lo + 1; i < hi; i++) {
                double d = distanceToSegment(pts, i, lo, hi);
                if (d > worst) {
                    worst = d;
                    at = i;
                }
            }
            if (worst > tol) {
                keep[at] = true;
                stack[top++] = lo;
                stack[top++] = at;
                stack[top++] = at;
                stack[top++] = hi;
            }
        }
        int kept = 0;
        for (boolean k : keep) {
            if (k) {
                kept++;
            }
        }
        int[] out = new int[kept * 3];
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out[w++] = pts[i * 3];
                out[w++] = pts[i * 3 + 1];
                out[w++] = pts[i * 3 + 2];
            }
        }
        return out;
    }

    /** Simplifies with growing tolerance (starting at {@code startTolerance}) until at most {@code maxPoints} remain. */
    public static int[] simplifyTo(int[] pts, double startTolerance, int maxPoints) {
        double tol = startTolerance;
        int[] out = simplify(pts, tol);
        int guard = 0;
        while (count(out) > maxPoints && guard++ < 40) {
            tol *= 1.6;
            out = simplify(pts, tol);
        }
        if (count(out) > maxPoints) {      // pathological input: thin it out evenly
            out = decimate(out, maxPoints);
        }
        return out;
    }

    /** Keeps {@code max} evenly spread points including both ends. */
    static int[] decimate(int[] pts, int max) {
        int n = count(pts);
        if (n <= max || max < 2) {
            return pts.clone();
        }
        int[] out = new int[max * 3];
        for (int k = 0; k < max; k++) {
            int i = (int) Math.round((double) k * (n - 1) / (max - 1));
            System.arraycopy(pts, i * 3, out, k * 3, 3);
        }
        return out;
    }

    private static double distanceToSegment(int[] p, int i, int a, int b) {
        double ax = p[a * 3], ay = p[a * 3 + 1], az = p[a * 3 + 2];
        double dx = p[b * 3] - ax, dy = p[b * 3 + 1] - ay, dz = p[b * 3 + 2] - az;
        double px = p[i * 3] - ax, py = p[i * 3 + 1] - ay, pz = p[i * 3 + 2] - az;
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy + pz * dz) / len2));
        double ex = px - dx * t, ey = py - dy * t, ez = pz - dz * t;
        return Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    // ---- measuring ----

    public static Stats stats(int[] pts) {
        int n = count(pts);
        if (n < 2) {
            return Stats.EMPTY;
        }
        double length = 0, up = 0, down = 0;
        for (int i = 1; i < n; i++) {
            double dx = pts[i * 3] - pts[(i - 1) * 3], dz = pts[i * 3 + 2] - pts[(i - 1) * 3 + 2];
            double dy = pts[i * 3 + 1] - pts[(i - 1) * 3 + 1];
            length += Math.sqrt(dx * dx + dz * dz);
            if (dy > 0) {
                up += dy;
            } else {
                down -= dy;
            }
        }
        length /= UNIT;
        up /= UNIT;
        down /= UNIT;
        return new Stats(length, up, down, length > 0 ? down / length * 100 : 0);
    }

    /** Horizontal distance from the start to every point, in blocks (same length as the point count). */
    public static double[] distances(int[] pts) {
        int n = count(pts);
        double[] out = new double[n];
        for (int i = 1; i < n; i++) {
            double dx = pts[i * 3] - pts[(i - 1) * 3], dz = pts[i * 3 + 2] - pts[(i - 1) * 3 + 2];
            out[i] = out[i - 1] + Math.sqrt(dx * dx + dz * dz) / UNIT;
        }
        return out;
    }

    // ---- compact encoding ----

    /** Zig-zag: small positive and negative numbers both become small positive ones. */
    public static int zigzag(int v) {
        return (v << 1) ^ (v >> 31);
    }

    public static int unzigzag(int v) {
        return (v >>> 1) ^ -(v & 1);
    }

    /** First point as is, every later value as the step from the previous point, zig-zagged. */
    public static int[] toDeltas(int[] pts) {
        int[] out = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            out[i] = zigzag(i < 3 ? pts[i] : pts[i] - pts[i - 3]);
        }
        return out;
    }

    public static int[] fromDeltas(int[] deltas) {
        int[] out = new int[deltas.length];
        for (int i = 0; i < deltas.length; i++) {
            int v = unzigzag(deltas[i]);
            out[i] = i < 3 ? v : out[i - 3] + v;
        }
        return out;
    }

    /** Bounding box of a track in blocks: minX, minZ, maxX, maxZ. */
    public static double[] bounds(int[] pts) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < pts.length; i += 3) {
            b[0] = Math.min(b[0], (double) pts[i] / UNIT);
            b[1] = Math.min(b[1], (double) pts[i + 2] / UNIT);
            b[2] = Math.max(b[2], (double) pts[i] / UNIT);
            b[3] = Math.max(b[3], (double) pts[i + 2] / UNIT);
        }
        return b;
    }

    public static boolean same(int[] a, int[] b) {
        return Arrays.equals(a, b);
    }

    private TrackGeometry() {}
}
