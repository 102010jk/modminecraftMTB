package com.descentmtb.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server side of a bail: no fall damage while you tumble (crashing is punishment enough). */
public final class Ragdolls {
    /** A running ragdoll: ticks left and the throw it started with (to catch up players who start tracking later). */
    private record Active(int left, Vec3 throwSpeed) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, net.minecraft.world.entity.Pose> POSES = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> POSE_ACTIVE=ConcurrentHashMap.newKeySet();
    public static final int DURATION = 140;
    private static final int CATCH_UP_WINDOW = 100;

    static void start(ServerPlayer p, Vec3 throwSpeed) {
        ACTIVE.put(p.getUUID(), new Active(DURATION, throwSpeed));
        com.descentmtb.audio.BoomboxServer.dropHeadphones(p);
        if(POSE_ACTIVE.add(p.getUUID()) && p.getForcedPose()!=null)POSES.put(p.getUUID(),p.getForcedPose());
        p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
    }

    public static void recover(ServerPlayer p) {
        Active a=ACTIVE.get(p.getUUID());if(a==null||a.left>DURATION-28)return;
        restorePose(p);
    }
    private static void restorePose(ServerPlayer p){if(POSE_ACTIVE.remove(p.getUUID()))p.setForcedPose(POSES.remove(p.getUUID()));}
    public static void clearSession(){ACTIVE.clear();POSES.clear();POSE_ACTIVE.clear();}

    /** Drops everything about a player's ragdoll (logout, death, respawn): the entity it belonged to is gone. */
    static void forget(UUID id) {
        ACTIVE.remove(id);
        POSES.remove(id);
        POSE_ACTIVE.remove(id);
    }

    /** {@code watcher} just started tracking {@code target}: if the target is mid-tumble, show it to them too. */
    static void catchUp(ServerPlayer watcher, ServerPlayer target) {
        Active a = ACTIVE.get(target.getUUID());
        // a tumble older than the client animation (about 100 ticks) is over for everyone else already
        if (a == null || a.left <= DURATION - CATCH_UP_WINDOW) return;
        PacketDistributor.sendToPlayer(watcher, new RagdollPayload(target.getId(), (float) a.throwSpeed.x, (float) a.throwSpeed.y, (float) a.throwSpeed.z));
    }
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Active a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        if(p.isPassenger())restorePose(p);
        p.resetFallDistance();
        if (a.left <= 1) {
            ACTIVE.remove(p.getUUID());
            restorePose(p);
        }
        else ACTIVE.put(p.getUUID(), new Active(a.left - 1, a.throwSpeed));
    }

    public static void onFall(LivingFallEvent e) {
        if (ACTIVE.containsKey(e.getEntity().getUUID())) e.setCanceled(true);
    }

    private Ragdolls() {}
}
