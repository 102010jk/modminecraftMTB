package com.descentmtb.trick;

/**
 * Hook for the sound / effects code: register with {@link TrickAnimation#addListener}. Called on the thread that
 * ticks the bike simulation (the client thread, only for the bike the local player rides).
 */
public interface TrickListener {
    /** A trick began: a spin starts rotating, a hold trick starts blending in. {@code side} is -1 or +1. */
    void onTrickStart(Trick trick, int side);

    /**
     * A trick ended. {@code completed} is true when it was done properly (a spin played its full rotation, a hold
     * trick was held long enough and released) and false when it was cancelled by landing, a bail or a respawn
     * (or a hold trick released before it really got going).
     */
    void onTrickEnd(Trick trick, boolean completed);
}
