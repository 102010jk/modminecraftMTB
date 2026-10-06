package com.descentmtb.client.sound;

/**
 * Turns a click rate (clicks per second) into discrete click events on a 20 Hz tick: accumulates the clicks of
 * each tick and hands them out one at a time. At most one click per tick - faster than that is the buzz loop's job.
 */
public final class ClickPacer {
    private double owed;

    /** Advances one tick at the given rate; true if a click is due now. */
    public boolean tick(double clicksPerSecond) {
        if (clicksPerSecond <= 0) {
            owed = Math.min(owed, 0.5);
            return false;
        }
        owed = Math.min(owed + clicksPerSecond * 0.05, 1.6);
        if (owed >= 1) {
            owed -= 1;
            return true;
        }
        return false;
    }

    public void reset() {
        owed = 0;
    }
}
