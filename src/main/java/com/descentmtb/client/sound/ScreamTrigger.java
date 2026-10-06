package com.descentmtb.client.sound;

/**
 * Decides when the rider's scream starts and stops. The crash prediction must hold for {@link #CONFIRM_TICKS}
 * ticks in a row before the scream starts (one noisy prediction is not a crash), it starts at most once per jump,
 * and it is cut when the bike lands without bailing. When it does bail the scream is left to play into the bail.
 */
public final class ScreamTrigger {
    public enum Action { NONE, START, STOP }

    public static final int CONFIRM_TICKS = 3;
    private int streak;
    private boolean active;

    public boolean active() {
        return active;
    }

    /**
     * @param airborne   the bike is in the air
     * @param crashAhead {@link CrashPredictor} says the landing will crash
     * @param bailed     the rider has crashed (at the latest on this tick)
     */
    public Action update(boolean airborne, boolean crashAhead, boolean bailed) {
        if (!airborne) {
            streak = 0;
            if (active) {
                active = false;
                return bailed ? Action.NONE : Action.STOP;
            }
            return Action.NONE;
        }
        if (active) return Action.NONE;
        streak = crashAhead ? streak + 1 : 0;
        if (streak >= CONFIRM_TICKS) {
            active = true;
            return Action.START;
        }
        return Action.NONE;
    }

    public void reset() {
        streak = 0;
        active = false;
    }
}
