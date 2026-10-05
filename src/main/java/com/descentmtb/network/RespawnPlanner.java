package com.descentmtb.network;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.RiderSession.Spot;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.TrailSignBlock;
import com.descentmtb.trail.TrailSignEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides where a respawn request puts the bike, from what the server itself knows: the rider's recent safe
 * points, the START sign they armed and the point they mounted at. Nothing the client says about positions is
 * used, so there is no distance limit to hit; every candidate is checked for being a sane place to stand.
 */
final class RespawnPlanner {
    /** The result: where, and which {@link BikeResyncPayload} kind to tell the client. */
    record Plan(Spot spot, byte kind) {}

    /** Where the rider is placed relative to a START sign (blocks); matches the client's trail timer. */
    private static final double START_OFFSET = 1.6;
    /** A respawn goes this many safe points back, so you do not re-crash immediately. */
    private static final int SAFE_BACK = 6;

    static Plan plan(ServerLevel level, MountainBikeEntity bike, RiderSession s, boolean atStart) {
        if (atStart) {
            Spot sign = signSpot(level, s);
            if (sign != null && usable(level, sign)) return new Plan(sign, BikeResyncPayload.RESPAWN_SIGN);
            if (s.rideStart != null && usable(level, s.rideStart)) return new Plan(s.rideStart, BikeResyncPayload.RESPAWN_START);
        } else {
            List<Spot> points = new ArrayList<>(s.safe);          // oldest first
            for (int i = Math.max(0, points.size() - SAFE_BACK); i >= 0 && i < points.size(); i--) {
                if (!usable(level, points.get(i))) continue;
                while (s.safe.size() > i + 1) s.safe.removeLast();   // forget the stretch we are leaving
                return new Plan(points.get(i), BikeResyncPayload.RESPAWN);
            }
            if (s.rideStart != null && usable(level, s.rideStart)) return new Plan(s.rideStart, BikeResyncPayload.RESPAWN);
        }
        Spot here = new Spot(bike.getX(), bike.getY(), bike.getZ(), Math.toRadians(bike.getYRot()));
        byte kind = atStart ? BikeResyncPayload.RESPAWN_START : BikeResyncPayload.RESPAWN;
        return usable(level, here) ? new Plan(here, kind) : null;
    }

    /** The spot beside the START sign this player armed, or null if there is none (any more). */
    private static Spot signSpot(ServerLevel level, RiderSession s) {
        if (s.startSign == null || s.startDimension != level.dimension()) return null;
        BlockPos pos = s.startSign;
        loadChunk(level, pos);
        if (!(level.getBlockEntity(pos) instanceof TrailSignEntity sign) || sign.content().type() != SignContent.Type.START) {
            s.startSign = null;                                    // the sign was broken or edited
            return null;
        }
        return besideSign(pos, sign.getBlockState());
    }

    /** Beyond a standing sign, in front of a wall sign, facing the way the trail runs. */
    static Spot besideSign(BlockPos pos, BlockState state) {
        Direction heading = TrailSignBlock.rideHeading(state);
        boolean wall = state.getValue(TrailSignBlock.WALL);
        Direction offset = wall ? state.getValue(TrailSignBlock.FACING) : heading;
        return new Spot(pos.getX() + .5 + offset.getStepX() * START_OFFSET, pos.getY(),
                pos.getZ() + .5 + offset.getStepZ() * START_OFFSET,
                Math.atan2(-heading.getStepX(), heading.getStepZ()));
    }

    /**
     * A bike may be put here: inside the world border and height, with the rider's body room free of solid
     * blocks. The chunk is loaded on demand (a respawn is rare and rate-limited, and the point may be far away).
     */
    private static boolean usable(ServerLevel level, Spot p) {
        if (!BikeStateLimits.finite(p.x(), p.y(), p.z(), p.yawRad())) return false;
        BlockPos at = BlockPos.containing(p.x(), p.y(), p.z());
        if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(p.x(), p.z())) return false;
        loadChunk(level, at);
        return level.noCollision(new AABB(p.x() - .3, p.y() + .5, p.z() - .3, p.x() + .3, p.y() + 1.8, p.z() + .3));
    }

    private static void loadChunk(ServerLevel level, BlockPos pos) {
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private RespawnPlanner() {}
}
