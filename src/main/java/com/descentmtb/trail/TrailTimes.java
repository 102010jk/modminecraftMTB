package com.descentmtb.trail;

import java.util.Locale;

/** Pure helpers for trail run times: validation, the personal-best comparison and the text shown to the rider. */
public final class TrailTimes {
    /** Shortest run the server accepts; anything quicker is a glitch or a cheat. */
    public static final int MIN_MS = 1000;
    /** Longest run the server accepts (six hours). */
    public static final int MAX_MS = 6 * 60 * 60 * 1000;

    /**
     * Outcome of comparing a finished run with the previous personal best.
     *
     * @param best     the personal best after this run
     * @param previous the personal best before this run, or -1 if there was none
     * @param record   true if this run is a new personal best
     */
    public record Result(int best, int previous, boolean record) {
        /** Difference to the previous best in centiseconds (positive = slower); 0 when there was no previous best. */
        public int deltaCentis(int time) {
            return TrailTimes.deltaCentis(time, previous);
        }
    }

    /** {@code time} minus {@code reference} in centiseconds, as shown on the clock; 0 if there is no reference (-1). */
    public static int deltaCentis(int time, int reference) {
        return reference < 0 ? 0 : time / 10 - reference / 10;
    }

    /** True if the time is plausible for a real run. */
    public static boolean isValid(long ms) {
        return ms >= MIN_MS && ms <= MAX_MS;
    }

    /** Compares a run with the previous best (-1 = none). The first run is always a record. */
    public static Result evaluate(int previousBest, int time) {
        boolean record = previousBest < 0 || time < previousBest;
        return new Result(record ? time : previousBest, previousBest, record);
    }

    /** Key under which a trail name is stored: names differ only by letters, not by case or outer spaces. */
    public static String key(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /** {@code m:ss.cc}, e.g. 83450 ms gives {@code 1:23.45}. Hundredths are truncated, like a stopwatch. */
    public static String format(long ms) {
        long clamped = Math.max(0, ms);
        long centis = clamped / 10;
        return String.format(Locale.ROOT, "%d:%02d.%02d", centis / 6000, centis / 100 % 60, centis % 100);
    }

    /** {@code +0.84 s} or {@code -0.84 s} for a difference given in centiseconds. */
    public static String formatDelta(int centis) {
        return String.format(Locale.ROOT, "%s%d.%02d s", centis < 0 ? "-" : "+", Math.abs(centis) / 100, Math.abs(centis) % 100);
    }

    private TrailTimes() {}
}
