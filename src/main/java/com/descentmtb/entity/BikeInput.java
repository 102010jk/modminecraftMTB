package com.descentmtb.entity;

/**
 * One snapshot of rider input, produced on the client (keyboard + controller)
 * and pushed to the controlling {@link MountainBikeEntity} each client tick.
 *
 * <p>Kept deliberately tiny and free of any client-only imports so it is safe
 * to reference from common entity code.
 */
public final class BikeInput {
    public static final BikeInput NONE = new BikeInput(0f, 0f, 0f, 0f, false);

    /** +1 = pedal forward, -1 = back-pedal / lean back (also preloads the hop). */
    public final float throttle;
    /** +1 = steer left, -1 = right. Turns the bars / changes heading. */
    public final float steer;
    /** 0..1 brake strength (both brakes). */
    public final float brake;
    /** +1 = lean body left, -1 = right. Separate "second stick" tilt. */
    public final float lean;
    /** Bunny-hop button held (hold to charge, pull back to preload, release to pop). */
    public final boolean hop;

    public BikeInput(float throttle, float steer, float brake, float lean, boolean hop) {
        this.throttle = throttle;
        this.steer = steer;
        this.brake = brake;
        this.lean = lean;
        this.hop = hop;
    }
}
