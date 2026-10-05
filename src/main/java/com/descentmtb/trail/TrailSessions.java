package com.descentmtb.trail;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Keeps the per-player memory of the trail tools from growing: when a player logs out, their undo history,
 * clipboard, pending ramp link and trail-run rate limit are forgotten.
 */
public final class TrailSessions {
    /** Registered on the NeoForge event bus by {@link com.descentmtb.DescentMtb}. */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var id = event.getEntity().getUUID();
        TrailEdit.forget(id);
        TrailRecords.forget(id);
    }

    private TrailSessions() {}
}
