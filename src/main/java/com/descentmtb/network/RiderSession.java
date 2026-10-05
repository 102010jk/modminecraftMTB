package com.descentmtb.network;

import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;

/**
 * Everything the server remembers about one player's riding: the movement budget, the packet reject counters,
 * the reposition epoch and the places a respawn can return to. The ride-specific part starts over on every
 * mount ({@link #beginRide}); the START sign survives until the player leaves.
 */
final class RiderSession {
    /** A place to put the bike: the entity position (ground point under the frame) and its heading. */
    record Spot(double x, double y, double z, double yawRad) {}

    private static final long NEVER = Long.MIN_VALUE / 4;
    /** Same cadence and history as the client used to keep: a point every 10 packets, the last 40. */
    private static final int SAFE_EVERY = 10, SAFE_KEEP = 40;
    private static final double SAFE_MIN_SPEED = 0.5;

    final MovementBudget budget = new MovementBudget();
    final RejectTracker rejects = new RejectTracker();

    /** Bumped on every server-side reposition; the client echoes it in its state packets. */
    int epoch;
    long lastResyncMs = NEVER, lastRespawnMs = NEVER, lastStateMs = NEVER;
    byte lastFlags;
    double lastSpeed;

    final ArrayDeque<Spot> safe = new ArrayDeque<>();
    Spot rideStart;
    private int safeTimer;

    /** The START sign this player last armed, verified by the server. */
    BlockPos startSign;
    ResourceKey<Level> startDimension;

    void beginRide(long nowMs) {
        epoch = 0;
        budget.reset(nowMs);
        lastResyncMs = lastRespawnMs = lastStateMs = NEVER;
        lastFlags = 0;
        lastSpeed = 0;
        safe.clear();
        rideStart = null;
        safeTimer = 0;
    }

    void endRide() {
        lastStateMs = NEVER;
        lastFlags = 0;
        lastSpeed = 0;
    }

    boolean lastBailed() {
        return (lastFlags & BikeStatePayload.BAILED) != 0;
    }

    /** Bookkeeping for a state packet the server accepted (the bike has already been moved to it). */
    void accepted(BikeStatePayload m, MountainBikeEntity bike, long nowMs) {
        lastFlags = BikeStateLimits.maskFlags(m.flags());
        lastSpeed = Math.sqrt((double) m.vx() * m.vx() + (double) m.vy() * m.vy() + (double) m.vz() * m.vz());
        lastStateMs = nowMs;
        recordSafePoint(bike);
    }

    private void recordSafePoint(MountainBikeEntity bike) {
        if (++safeTimer < SAFE_EVERY) return;
        safeTimer = 0;
        if ((lastFlags & (BikeStatePayload.AIRBORNE | BikeStatePayload.BAILED)) != 0) return;
        Spot p = new Spot(bike.getX(), bike.getY(), bike.getZ(), Math.toRadians(bike.getYRot()));
        if (rideStart == null) rideStart = p;
        if (lastSpeed < SAFE_MIN_SPEED && !safe.isEmpty()) return;     // standing still adds nothing
        safe.addLast(p);
        while (safe.size() > SAFE_KEEP) safe.removeFirst();
    }
}
