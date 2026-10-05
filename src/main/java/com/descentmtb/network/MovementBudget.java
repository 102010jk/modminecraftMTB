package com.descentmtb.network;

/**
 * How far a rider's bike may move between two accepted state packets. A token bucket measured in blocks: it
 * refills at {@link #RATE} blocks per second of real time (2.5 blocks per server tick, 50 m/s) and holds
 * {@link #CAPACITY} blocks, so a lag spike that delivers several ticks of legitimate movement at once is still
 * accepted while a sustained speed hack, or a single huge jump, is not.
 */
public final class MovementBudget {
    /** Blocks per second the bike may travel on average. Even a long freefall stays well below this. */
    public static final double RATE = 50;
    /** Headroom on top of one second of travel, so jitter and a late packet never trip the check. */
    public static final double SLACK = 8;
    public static final double CAPACITY = SLACK + RATE;

    private double tokens = CAPACITY;
    private long lastMs;
    private boolean started;

    /** Spends {@code blocks} of travel at time {@code nowMs}; false (and nothing spent) if it exceeds the budget. */
    public boolean tryConsume(double blocks, long nowMs) {
        refill(nowMs);
        if (!(blocks <= tokens)) return false;     // also false for NaN
        tokens -= blocks;
        return true;
    }

    /** Starts over with a full bucket (new ride, server-initiated teleport). */
    public void reset(long nowMs) {
        tokens = CAPACITY;
        lastMs = nowMs;
        started = true;
    }

    public double tokens() { return tokens; }

    private void refill(long nowMs) {
        if (started && nowMs > lastMs) tokens = Math.min(CAPACITY, tokens + RATE * (nowMs - lastMs) / 1000.0);
        lastMs = nowMs;
        started = true;
    }
}
