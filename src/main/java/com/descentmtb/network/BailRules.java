package com.descentmtb.network;

/**
 * When the server believes a rider's "I crashed" message. A real crash is announced by a state packet with the
 * BAILED flag right before the bail message; failing that, the bike must genuinely have been going fast (a bail
 * is how a rider gets thrown off, so it cannot be used to get off a parked bike or to move the player around).
 */
public final class BailRules {
    /** The bail position may be at most this far from the server's bike (blocks). */
    public static final double MAX_DISTANCE = 4;
    /** Speed (m/s) at which a bail without a BAILED state packet is still believed. */
    public static final double FAST_SPEED = 6;
    /** The last accepted state must be this recent (ms) to say anything about the crash. */
    public static final long MAX_STATE_AGE_MS = 2000;

    public static boolean accept(boolean lastStateBailed, double lastSpeed, long stateAgeMs, double distance) {
        if (!(distance <= MAX_DISTANCE) || stateAgeMs > MAX_STATE_AGE_MS) return false;
        return lastStateBailed || lastSpeed >= FAST_SPEED;
    }

    private BailRules() {}
}
