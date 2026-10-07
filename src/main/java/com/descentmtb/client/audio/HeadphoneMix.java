package com.descentmtb.client.audio;

/** How much of the world gets through worn headphones that are playing music. Pure, unit-tested. */
public final class HeadphoneMix {
    private HeadphoneMix() {}

    /** Closed-back cans with music on: the world drops by ~85 %. */
    public static final double DEFAULT_WORLD = 0.15;

    /**
     * The rider's own bike and voice are felt through the body and the helmet, not only through the air: they keep
     * roughly half their level instead of following the world down to nothing.
     */
    public static float playerGain(float world) {
        float w = Math.max(0, Math.min(1, world));
        return 0.4f + 0.6f * w;
    }
}
