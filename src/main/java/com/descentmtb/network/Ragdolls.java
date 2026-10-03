package com.descentmtb.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of a bail: no fall damage while you tumble (crashing is punishment enough). */
public final class Ragdolls {
    private static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();
    public static final int DURATION = 60;

    static void start(ServerPlayer p) {
        ACTIVE.put(p.getUUID(), DURATION);
    }

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Integer left = ACTIVE.get(p.getUUID());
        if (left == null) return;
        p.resetFallDistance();
        if (left <= 1) ACTIVE.remove(p.getUUID());
        else ACTIVE.put(p.getUUID(), left - 1);
    }

    public static void onFall(LivingFallEvent e) {
        if (ACTIVE.containsKey(e.getEntity().getUUID())) e.setCanceled(true);
    }

    private Ragdolls() {}
}
