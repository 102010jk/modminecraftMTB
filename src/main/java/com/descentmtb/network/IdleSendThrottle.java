package com.descentmtb.network;

/**
 * Decides when the rider's client sends a state packet. A moving bike sends one every tick. A parked bike
 * changes nothing the server needs, so it only sends a few a second - unless the rider moves the bars or the
 * body, which other players see (steer angle, rider pose), in which case the change goes out at once.
 * Pure; one instance per rider.
 */
public final class IdleSendThrottle {
    /** A parked bike sends one packet every this many ticks. */
    public static final int IDLE_INTERVAL = 10;
    /** An input change smaller than this (stick units) is noise, not a visible change. */
    public static final float INPUT_EPSILON = 0.02f;

    private int idleTicks;
    private float sentSteer, sentLean, sentBody, sentTweak;
    private boolean sentAny;

    /**
     * Called once per tick; true if this tick's state should be sent.
     *
     * @param resting the bike is standing still on the ground
     */
    public boolean shouldSend(boolean resting, float steer, float lean, float body, float tweak) {
        boolean inputChanged = !sentAny
                || Math.abs(steer - sentSteer) > INPUT_EPSILON || Math.abs(lean - sentLean) > INPUT_EPSILON
                || Math.abs(body - sentBody) > INPUT_EPSILON || Math.abs(tweak - sentTweak) > INPUT_EPSILON;
        boolean send = !resting || inputChanged || idleTicks % IDLE_INTERVAL == 0;
        idleTicks = resting ? idleTicks + 1 : 0;
        if (send) {
            sentSteer = steer;
            sentLean = lean;
            sentBody = body;
            sentTweak = tweak;
            sentAny = true;
        }
        return send;
    }

    /** Starts over (new ride): the next call always sends. */
    public void reset() {
        idleTicks = 0;
        sentAny = false;
    }
}
