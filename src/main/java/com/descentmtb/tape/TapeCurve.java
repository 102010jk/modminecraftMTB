package com.descentmtb.tape;

/**
 * Geometry of a hanging tape, free of Minecraft classes so it can be unit tested: the tape sags like a chain
 * between its two fixing points, and the light along it is blended between the lights at its ends.
 */
public final class TapeCurve {
    /** Height of the tape above the base of a post, in blocks. */
    public static final double HEIGHT = 0.8;
    /** Sag in the middle as a fraction of the tape length. */
    public static final double SAG_PER_LENGTH = 0.04;
    /** Straight pieces the ribbon is drawn in. */
    public static final int SEGMENTS = 12;

    private TapeCurve() {}

    /** Distance the middle of a tape of this length hangs below the straight line. */
    public static double sag(double length) {
        return SAG_PER_LENGTH * length;
    }

    /**
     * The point at {@code t} (0 at a, 1 at b) of a tape between the two fixing points: the straight line lowered
     * by a parabola that reaches {@link #sag} in the middle and zero at both ends.
     */
    public static double[] point(double[] a, double[] b, double t) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double drop = 4 * sag(length) * t * (1 - t);
        return new double[]{a[0] + dx * t, a[1] + dy * t - drop, a[2] + dz * t};
    }

    /** Blends two packed lightmap values (sky in bits 20-23, block in bits 4-7) component by component. */
    public static int blendLight(int from, int to, double t) {
        int block = blend((from >> 4) & 0xF, (to >> 4) & 0xF, t);
        int sky = blend((from >> 20) & 0xF, (to >> 20) & 0xF, t);
        return sky << 20 | block << 4;
    }

    private static int blend(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }
}
