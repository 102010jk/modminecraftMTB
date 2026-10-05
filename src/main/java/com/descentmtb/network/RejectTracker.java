package com.descentmtb.network;

/**
 * Counts the invalid bike packets of one player: tells the caller when to write a (rate-limited) log line and
 * when the player is flooding the server badly enough to be kicked. Pure; the clock is passed in.
 */
public final class RejectTracker {
    /** This many rejected packets within {@link #KICK_WINDOW_MS} is a flood. */
    public static final int KICK_COUNT = 200;
    public static final long KICK_WINDOW_MS = 10_000;
    public static final long LOG_INTERVAL_MS = 5_000;

    public record Result(boolean kick, boolean log) {}

    private final long[] times = new long[KICK_COUNT];
    private int head, size;
    private long lastLogMs;
    private boolean logged;

    /** Records one rejected packet; {@code wantLog} says whether this one may be written to the log. */
    public Result record(long nowMs, boolean wantLog) {
        times[head] = nowMs;
        head = (head + 1) % KICK_COUNT;
        if (size < KICK_COUNT) size++;
        boolean kick = size == KICK_COUNT && nowMs - times[head] <= KICK_WINDOW_MS;   // times[head] is the oldest
        boolean log = false;
        if (wantLog && (!logged || nowMs - lastLogMs >= LOG_INTERVAL_MS)) {
            log = true;
            logged = true;
            lastLogMs = nowMs;
        }
        return new Result(kick, log);
    }
}
