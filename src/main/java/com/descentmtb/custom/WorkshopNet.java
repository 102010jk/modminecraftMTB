package com.descentmtb.custom;

import net.minecraft.core.BlockPos;

/**
 * CONTRACT between the workshop screen (client) and the stand logic (server): the backend agent implements these
 * (C2S payloads, validated on the server); the screen only calls them.
 */
public final class WorkshopNet {
    /** Save {@code build} to the bike standing on the stand at {@code stand}. */
    public static void apply(BlockPos stand, BikeBuild build) {
        // implemented by the backend work
    }

    /** Take the bike off the stand into the player's inventory. */
    public static void takeBike(BlockPos stand) {
        // implemented by the backend work
    }

    private WorkshopNet() {}
}
