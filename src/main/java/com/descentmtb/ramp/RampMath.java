package com.descentmtb.ramp;

/**
 * Pure ramp surface maths (no Minecraft classes, allocation-free, unit-tested).
 *
 * <p>Facing codes are {@code Direction.get2DDataValue()}: 0 = SOUTH (+z), 1 = WEST (-x),
 * 2 = NORTH (-z), 3 = EAST (+x). The ramp rises TOWARD its facing direction; the
 * along-facing coordinate {@code t} runs from 0 (back edge) to 1 (front edge).
 * Profile codes: 0 = LINEAR, 1 = CONCAVE (flat start, steepening), 2 = CONVEX (steep
 * start, flattening). Heights are {@code (start + (end - start) * p(t)) / 16} blocks.
 */
public final class RampMath {
    public static final int LINEAR = 0, CONCAVE = 1, CONVEX = 2;

    /** Circular arc: the concave curve spans the angle with sin(theta) = S (keeps the end slope finite). */
    private static final double S = 0.7;
    private static final double S2 = S * S;
    private static final double INV = 1.0 / (1.0 - Math.sqrt(1.0 - S2));

    private RampMath() {}

    /** Concave unit profile: 0 at t=0 (slope 0), 1 at t=1 (slope ~2.4). */
    private static double concave(double t) {
        return (1.0 - Math.sqrt(1.0 - S2 * t * t)) * INV;
    }

    private static double concaveD(double t) {
        return S2 * t / Math.sqrt(1.0 - S2 * t * t) * INV;
    }

    private static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    /** Unit profile p(t), p(0)=0, p(1)=1. */
    public static double profile(int profile, double t) {
        t = clamp01(t);
        switch (profile) {
            case CONCAVE: return concave(t);
            case CONVEX: return 1.0 - concave(1.0 - t);
            default: return t;
        }
    }

    /** dp/dt. */
    public static double profileD(int profile, double t) {
        t = clamp01(t);
        switch (profile) {
            case CONCAVE: return concaveD(t);
            case CONVEX: return concaveD(1.0 - t);
            default: return 1.0;
        }
    }

    /** Along-facing coordinate for in-block coords. */
    public static double alongT(int facing, double fx, double fz) {
        switch (facing & 3) {
            case 0: return clamp01(fz);
            case 1: return clamp01(1.0 - fx);
            case 2: return clamp01(1.0 - fz);
            default: return clamp01(fx);
        }
    }

    /** Surface height in blocks at along-coordinate t. */
    public static double heightAtT(int start, int end, int profile, double t) {
        return (start + (end - start) * profile(profile, t)) * (1.0 / 16.0);
    }

    public static double height(int start, int end, int profile, int facing, double fx, double fz) {
        return heightAtT(start, end, profile, alongT(facing, fx, fz));
    }

    /** dh/dt in blocks per block. */
    private static double dhdt(int start, int end, int profile, int facing, double fx, double fz) {
        return (end - start) * profileD(profile, alongT(facing, fx, fz)) * (1.0 / 16.0);
    }

    /** d(height)/dx in blocks per block. */
    public static double slopeX(int start, int end, int profile, int facing, double fx, double fz) {
        switch (facing & 3) {
            case 1: return -dhdt(start, end, profile, facing, fx, fz);
            case 3: return dhdt(start, end, profile, facing, fx, fz);
            default: return 0.0;
        }
    }

    /** d(height)/dz in blocks per block. */
    public static double slopeZ(int start, int end, int profile, int facing, double fx, double fz) {
        switch (facing & 3) {
            case 0: return dhdt(start, end, profile, facing, fx, fz);
            case 2: return -dhdt(start, end, profile, facing, fx, fz);
            default: return 0.0;
        }
    }
}
