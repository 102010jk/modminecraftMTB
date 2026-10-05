package com.descentmtb.network;

/**
 * Pure range limits for everything a rider's client reports about its bike. The rider's client is authoritative
 * over the bike, so the server never trusts a value: the visual floats that end up in synced entity data (and so
 * on every other player's screen) are clamped to what the simulation can actually produce.
 */
public final class BikeStateLimits {
    public static final float LEAN_MAX = 1.6f;
    public static final float STEER_MAX = 1f;
    public static final float COMPRESSION_MAX = 0.3f;
    public static final float RIDER_MAX = 1f;
    /** Bits of {@link BikeStatePayload}'s flags that are stored; TELEPORT is a request, never state. */
    public static final byte FLAG_MASK = BikeStatePayload.AIRBORNE | BikeStatePayload.BAILED | BikeStatePayload.WALL_RIDE;
    /** The crank angle only grows; it is folded back by a whole number of turns this large (invisible, see below). */
    public static final double CRANK_PERIOD = Math.PI * 2 * 1024;
    /** Largest speed (m/s) trusted when a bailed bike is handed to the server's riderless simulation. */
    public static final double MAX_HANDOFF_SPEED = 80;
    public static final double MAX_ANGULAR_RATE = 30;

    /** Clamps to [lo, hi]; NaN becomes 0 (every visual range contains 0). */
    public static float clamp(float v, float lo, float hi) {
        if (Float.isNaN(v)) return 0f;
        return Math.max(lo, Math.min(hi, v));
    }

    public static float lean(float v) { return clamp(v, -LEAN_MAX, LEAN_MAX); }
    public static float steer(float v) { return clamp(v, -STEER_MAX, STEER_MAX); }
    public static float compression(float v) { return clamp(v, 0f, COMPRESSION_MAX); }
    public static float rider(float v) { return clamp(v, -RIDER_MAX, RIDER_MAX); }
    public static float unit(float v) { return clamp(v, 0f, 1f); }

    public static byte maskFlags(byte flags) { return (byte) (flags & FLAG_MASK); }

    /** Trick ids outside the enum fall back to 0 (no trick). */
    public static int trickId(int id, int trickCount) { return id >= 0 && id < trickCount ? id : 0; }

    /** The trick side is -1 or +1. */
    public static int trickSide(int side) { return side < 0 ? -1 : 1; }

    /** Wraps an angle in radians to [-pi, pi). */
    public static double wrapAngle(double a) {
        if (!Double.isFinite(a)) return 0;
        return a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
    }

    public static float wrapAngle(float a) { return (float) wrapAngle((double) a); }

    /**
     * Folds a runaway crank angle back into range. Whole turns of {@link #CRANK_PERIOD} (1024 revolutions) are
     * removed, so the cranks look identical; the fold happens about once per 1000 pedal strokes.
     */
    public static float crank(float a) {
        if (!Float.isFinite(a)) return 0f;
        if (Math.abs(a) <= CRANK_PERIOD) return a;
        return (float) (a - CRANK_PERIOD * Math.rint(a / CRANK_PERIOD));
    }

    /** A tyre/fork pressure from untrusted data (item NBT, saved entity): non-finite values use the default. */
    public static float pressure(float v, float fallback, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : fallback;
    }

    public static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }

    /** Scale factor (at most 1) that brings a velocity with the given squared length down to {@code max}. */
    public static double speedScale(double lengthSq, double max) {
        return lengthSq > max * max ? max / Math.sqrt(lengthSq) : 1.0;
    }

    /**
     * Angular rate (rad/s) between two reports {@code ticks} game ticks apart, 0 when the reports are too far
     * apart (or in the same tick) to say anything about the current rotation.
     */
    public static double angularRate(double previous, double current, int ticks) {
        if (ticks < 1 || ticks > 3) return 0;
        double rate = wrapAngle(current - previous) / (ticks * 0.05);
        return Math.max(-MAX_ANGULAR_RATE, Math.min(MAX_ANGULAR_RATE, rate));
    }

    private BikeStateLimits() {}
}
