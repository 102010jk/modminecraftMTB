package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.TrailSignEntity;
import com.descentmtb.world.SafeDismount;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * The server's side of riding. The rider's client simulates the bike, so everything here is about not believing
 * it blindly: state packets must stay inside a movement budget, a bail must look like a crash, a respawn is
 * done by the server from what it knows. Whenever the server overrules the client it tells it
 * ({@link BikeResyncPayload}) instead of silently ignoring packets forever.
 */
final class RiderServer {
    /** At most one resync per this long; more would only stack up behind the client's round trip. */
    private static final long RESYNC_INTERVAL_MS = 250;
    private static final long RESPAWN_COOLDOWN_MS = 1000;
    /** A START sign this close to the player (blocks) may be armed; generous because the client rides fast. */
    private static final double MAX_START_DISTANCE = 8;
    /** Highest bail speed per axis the server believes (m/s). */
    private static final float MAX_THROW = 40f;
    /**
     * Development only: the dev autopilot (singleplayer, JVM property) teleports its bike on the client and
     * flags the packet. Never honoured on a dedicated server, so a real client cannot teleport.
     */
    private static final boolean DEV_TELEPORT = Boolean.getBoolean("descentmtb.autopilot");

    // ------------------------------------------------------------------ state

    static void onState(ServerPlayer player, MountainBikeEntity bike, BikeStatePayload m) {
        RiderSession s = RiderSessions.of(player);
        long now = Util.getMillis();
        if (m.epoch() != s.epoch) {
            stale(player, s, now);
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!BikeStateLimits.finite(m.x(), m.y(), m.z(), m.yaw(), m.pitch(), m.vx(), m.vy(), m.vz())) {
            reject(player, bike, s, now, "non-finite values");
            return;
        }
        if (m.y() < level.getMinBuildHeight() - 64 || m.y() > level.getMaxBuildHeight() + 512) {
            reject(player, bike, s, now, "outside the world height");
            return;
        }
        if (!level.getWorldBorder().isWithinBounds(m.x(), m.z())) {
            reject(player, bike, s, now, "outside the world border");
            return;
        }
        if (!level.hasChunkAt(BlockPos.containing(m.x(), m.y(), m.z()))) {
            reject(player, bike, s, now, "unloaded chunk");
            return;
        }
        double dist = Math.sqrt(bike.distanceToSqr(m.x(), m.y() - MountainBikeEntity.COM_HEIGHT, m.z()));
        boolean devTeleport = DEV_TELEPORT && (m.flags() & BikeStatePayload.TELEPORT) != 0
                && !player.server.isDedicatedServer();
        if (devTeleport) {
            s.budget.reset(now);
        } else if (!s.budget.tryConsume(dist, now)) {
            reject(player, bike, s, now, String.format(Locale.ROOT, "moved %.1f blocks (%.1f allowed)", dist, s.budget.tokens()));
            return;
        }
        bike.applyRiderState(m);
        s.accepted(m, bike, now);
    }

    /** A packet from before the client heard about the last reposition: expected for a moment, never an error. */
    private static void stale(ServerPlayer player, RiderSession s, long now) {
        if (s.rejects.record(now, false).kick()) kick(player);
    }

    private static void reject(ServerPlayer player, MountainBikeEntity bike, RiderSession s, long now, String why) {
        RejectTracker.Result r = s.rejects.record(now, true);
        if (r.log()) {
            DescentMtb.LOG.warn("{}: bike packet rejected, {} (further rejections are logged at most every {} s)",
                    player.getName().getString(), why, RejectTracker.LOG_INTERVAL_MS / 1000);
        }
        if (r.kick()) {
            kick(player);
            return;
        }
        if (now - s.lastResyncMs >= RESYNC_INTERVAL_MS) reposition(player, bike, s, BikeResyncPayload.RESYNC, now);
    }

    private static void kick(ServerPlayer player) {
        DescentMtb.LOG.warn("{} kicked: {} invalid bike packets within {} s", player.getName().getString(),
                RejectTracker.KICK_COUNT, RejectTracker.KICK_WINDOW_MS / 1000);
        player.connection.disconnect(Component.translatable("descentmtb.kick.flood"));
    }

    /** Tells the client where its bike is now; packets sent before it hears this are dropped as stale. */
    private static void reposition(ServerPlayer player, MountainBikeEntity bike, RiderSession s, byte kind, long now) {
        s.epoch++;
        s.budget.reset(now);
        s.lastResyncMs = now;
        PacketDistributor.sendToPlayer(player, new BikeResyncPayload(bike.getId(), bike.getX(), bike.getY(), bike.getZ(),
                (float) Math.toRadians(bike.getYRot()), s.epoch, kind));
    }

    // ------------------------------------------------------------------ bail

    static void onBail(ServerPlayer player, MountainBikeEntity bike, BikeBailPayload m) {
        RiderSession s = RiderSessions.of(player);
        long now = Util.getMillis();
        if (m.epoch() != s.epoch) {
            stale(player, s, now);
            return;
        }
        double dist = BikeStateLimits.finite(m.x(), m.y(), m.z())
                ? bike.position().distanceTo(new Vec3(m.x(), m.y(), m.z())) : Double.POSITIVE_INFINITY;
        if (!BailRules.accept(s.lastBailed(), s.lastSpeed, now - s.lastStateMs, dist)) {
            reject(player, bike, s, now, "bail does not match the bike state");
            return;
        }
        Vec3 v = new Vec3(clampThrow(m.vx()), clampThrow(m.vy()), clampThrow(m.vz()));   // m/s

        // The state packet with the crash frame came first, so the bike entity is where it crashed.
        player.stopRiding();
        // rider's centre of mass is ~0.9 m above the feet
        Vec3 feet = SafeDismount.find(player.level(), player, new Vec3(m.x(), m.y() - .9, m.z()));
        player.teleportTo(feet.x, feet.y, feet.z);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.setDeltaMovement(v.scale(0.05));                   // retain the throw, no artificial upward kick
        player.hurtMarked = true;
        player.resetFallDistance();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new RagdollPayload(player.getId(), (float) v.x, (float) v.y, (float) v.z));
        Ragdolls.start(player, v);
    }

    private static float clampThrow(float v) {
        return Float.isFinite(v) ? Math.max(-MAX_THROW, Math.min(MAX_THROW, v)) : 0f;
    }

    // ------------------------------------------------------------------ respawn

    static void onRespawn(ServerPlayer player, boolean atStart) {
        if (!(player.getVehicle() instanceof MountainBikeEntity bike)) return;
        RiderSession s = RiderSessions.of(player);
        long now = Util.getMillis();
        if (now - s.lastRespawnMs < RESPAWN_COOLDOWN_MS) return;
        RespawnPlanner.Plan plan = RespawnPlanner.plan(player.serverLevel(), bike, s, atStart);
        if (plan == null) {
            player.displayClientMessage(Component.translatable("descentmtb.bike.respawn_denied"), true);
            return;
        }
        s.lastRespawnMs = now;
        RiderSession.Spot to = plan.spot();
        bike.moveForRespawn(to.x(), to.y(), to.z(), Math.toDegrees(to.yawRad()));   // the rider is carried along
        s.lastFlags = 0;
        s.lastSpeed = 0;
        reposition(player, bike, s, plan.kind(), now);
    }

    // ------------------------------------------------------------------ start sign

    static void onStartPoint(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_START_DISTANCE * MAX_START_DISTANCE) return;
        if (!(level.getBlockEntity(pos) instanceof TrailSignEntity sign) || sign.content().type() != SignContent.Type.START) return;
        RiderSession s = RiderSessions.of(player);
        s.startSign = pos.immutable();
        s.startDimension = level.dimension();
    }

    private RiderServer() {}
}
