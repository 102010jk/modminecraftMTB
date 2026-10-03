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
    private static final Map<UUID, net.minecraft.world.entity.Pose> POSES = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> POSE_ACTIVE=ConcurrentHashMap.newKeySet();
    public static final int DURATION = 140;

    static void start(ServerPlayer p) {
        ACTIVE.put(p.getUUID(), DURATION);
        if(POSE_ACTIVE.add(p.getUUID()) && p.getForcedPose()!=null)POSES.put(p.getUUID(),p.getForcedPose());
        p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
    }

    public static void recover(ServerPlayer p) {
        Integer left=ACTIVE.get(p.getUUID());if(left==null||left>DURATION-28)return;
        restorePose(p);
    }
    private static void restorePose(ServerPlayer p){if(POSE_ACTIVE.remove(p.getUUID()))p.setForcedPose(POSES.remove(p.getUUID()));}
    public static void clearSession(){ACTIVE.clear();POSES.clear();POSE_ACTIVE.clear();}
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Integer left = ACTIVE.get(p.getUUID());
        if (left == null) return;
        if(p.isPassenger())restorePose(p);
        p.resetFallDistance();
        if (left <= 1) {
            ACTIVE.remove(p.getUUID());
            restorePose(p);
        }
        else ACTIVE.put(p.getUUID(), left - 1);
    }

    public static void onFall(LivingFallEvent e) {
        if (ACTIVE.containsKey(e.getEntity().getUUID())) e.setCanceled(true);
    }

    private Ragdolls() {}
}
