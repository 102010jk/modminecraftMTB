package com.descentmtb.network;

import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The server's {@link RiderSession}s, one per player who has ridden, and the lifecycle events that end them. */
public final class RiderSessions {
    private static final Map<UUID, RiderSession> SESSIONS = new ConcurrentHashMap<>();

    static RiderSession of(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), id -> new RiderSession());
    }

    /** The player just got on a bike (called by the bike entity, server side). */
    public static void onMount(ServerPlayer player) {
        of(player).beginRide(Util.getMillis());
    }

    /** The player just got off a bike. */
    public static void onDismount(ServerPlayer player) {
        RiderSession s = SESSIONS.get(player.getUUID());
        if (s != null) s.endRide();
    }

    /** Hooks the logout / death / respawn / tracking events; called once from {@link ModNetwork}. */
    static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            SESSIONS.remove(e.getEntity().getUUID());
            Ragdolls.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            onDismount(player);
            Ragdolls.forget(player.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> Ragdolls.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getEntity() instanceof ServerPlayer watcher && e.getTarget() instanceof ServerPlayer target) {
                Ragdolls.catchUp(watcher, target);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SESSIONS.clear());
    }

    private RiderSessions() {}
}
