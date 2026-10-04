package com.descentmtb.client.trail;

import com.descentmtb.client.BikeClientController;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.TrailBestPayload;
import com.descentmtb.network.TrailTimePayload;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.TrailSignBlock;
import com.descentmtb.trail.TrailSignEntity;
import com.descentmtb.trail.TrailSignRegistry;
import com.descentmtb.trail.TrailTimes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The trail timer. Riding a bike past a START sign starts the clock for that trail and makes it the
 * "respawn at start" point; riding past the FINISH sign of the same trail stops it and reports the time to the
 * server, which keeps the personal best. A bail does not stop the clock; respawning at the start re-arms it.
 */
public final class TrailTimer {
    /** Idle: nothing running. Armed: back at the start, the clock starts when the bike moves. */
    public enum State { IDLE, ARMED, RUNNING, FINISHED }

    /** A rider this close to a START or FINISH sign (blocks, horizontally) passes it. */
    private static final double TRIGGER_RADIUS = 2.5;
    /** Signs further away than this are not even looked at. */
    private static final double SCAN_RANGE = 32;
    /** Speed (m/s) at which an armed timer starts running. */
    private static final double LAUNCH_SPEED = 1.0;
    /** Speed (m/s) towards the trail needed to start by riding past a START sign. */
    private static final double START_SPEED = 0.3;
    /** Where the rider is placed relative to a START sign when respawning at the start (blocks). */
    private static final double START_OFFSET = 1.6;
    private static final long FINISH_SHOWN_NANOS = 6_000_000_000L;
    private static final long GIVE_UP_NANOS = 30L * 60 * 1_000_000_000L;
    private static final String DEFAULT_NAME = "Trail";

    private static State state = State.IDLE;
    private static String trail = "";
    private static long startNanos, finishNanos;
    private static long finalMs;
    /** Verdict from the server for the finished run; null until it arrives. */
    private static TrailBestPayload verdict;
    private static final Map<String, Integer> BESTS = new HashMap<>();
    private static final Set<BlockPos> INSIDE = new HashSet<>();
    private static ClientLevel lastLevel;

    public static State state() {
        return state;
    }

    public static String trailName() {
        return trail;
    }

    /** Milliseconds on the clock: live while running, frozen once finished, 0 otherwise. */
    public static long elapsedMs() {
        return switch (state) {
            case RUNNING -> (System.nanoTime() - startNanos) / 1_000_000;
            case FINISHED -> finalMs;
            default -> 0;
        };
    }

    /** The server's verdict on the finished run, or null while waiting for it. */
    public static TrailBestPayload verdict() {
        return verdict;
    }

    /** Personal best on the trail in milliseconds as last reported by the server, or -1. */
    public static int bestMs(String trailName) {
        return BESTS.getOrDefault(TrailTimes.key(trailName), -1);
    }

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        long now = System.nanoTime();
        if (state == State.FINISHED && now - finishNanos > FINISH_SHOWN_NANOS) {
            state = State.IDLE;
        }
        if (state == State.RUNNING && now - startNanos > GIVE_UP_NANOS) {
            state = State.IDLE;
        }
        MountainBikeEntity bike = BikeClientController.riding();
        if (mc.level == null || bike == null || bike.sim() == null) {
            return;
        }
        if (state == State.ARMED && bike.sim().speed() > LAUNCH_SPEED) {
            state = State.RUNNING;
            startNanos = now;
        }
        passSigns(mc, bike, now);
    }

    private static void passSigns(Minecraft mc, MountainBikeEntity bike, long now) {
        Set<BlockPos> current = new HashSet<>();
        for (TrailSignEntity sign : TrailSignRegistry.near(mc.level, bike.getX(), bike.getY(), bike.getZ(), SCAN_RANGE,
                SignContent.Type.START, SignContent.Type.FINISH)) {
            BlockPos pos = sign.getBlockPos();
            double dx = pos.getX() + .5 - bike.getX(), dz = pos.getZ() + .5 - bike.getZ();
            if (dx * dx + dz * dz > TRIGGER_RADIUS * TRIGGER_RADIUS || Math.abs(pos.getY() + .5 - bike.getY()) > 3) {
                continue;
            }
            current.add(pos);
            if (INSIDE.contains(pos)) {
                continue;
            }
            if (sign.content().type() == SignContent.Type.START) {
                passStart(sign, bike, now);
            } else {
                passFinish(sign, now);
            }
        }
        INSIDE.clear();
        INSIDE.addAll(current);
    }

    private static void passStart(TrailSignEntity sign, MountainBikeEntity bike, long now) {
        Direction heading = TrailSignBlock.rideHeading(sign.getBlockState());
        var velocity = bike.sim().vel;
        double towardsTrail = velocity.x * heading.getStepX() + velocity.z * heading.getStepZ();
        if (towardsTrail < START_SPEED) {
            return;
        }
        String name = nameOf(sign);
        state = State.RUNNING;
        trail = name;
        startNanos = now;
        verdict = null;
        setRespawnPoint(sign);
        BikeClientController.toast(Component.translatable("descentmtb.trail.started", name).getString());
        PacketDistributor.sendToServer(new TrailTimePayload(name, 0));
    }

    private static void passFinish(TrailSignEntity sign, long now) {
        if (state != State.RUNNING || !TrailTimes.key(nameOf(sign)).equals(TrailTimes.key(trail))) {
            return;
        }
        long ms = (now - startNanos) / 1_000_000;
        state = State.FINISHED;
        finishNanos = now;
        finalMs = ms;
        verdict = null;
        if (TrailTimes.isValid(ms)) {
            PacketDistributor.sendToServer(new TrailTimePayload(trail, (int) ms));
        }
    }

    private static String nameOf(TrailSignEntity sign) {
        String name = sign.content().name();
        return name.isEmpty() ? DEFAULT_NAME : name;
    }

    /** Backspace puts the rider back at the START sign: the clock stops and starts again when the bike moves. */
    private static void setRespawnPoint(TrailSignEntity sign) {
        var state = sign.getBlockState();
        Direction heading = TrailSignBlock.rideHeading(state);
        boolean wall = state.getValue(TrailSignBlock.WALL);
        // Beyond a standing sign, in front of a wall sign.
        Direction offset = wall ? state.getValue(TrailSignBlock.FACING) : heading;
        BlockPos pos = sign.getBlockPos();
        double x = pos.getX() + .5 + offset.getStepX() * START_OFFSET;
        double z = pos.getZ() + .5 + offset.getStepZ() * START_OFFSET;
        double yaw = Math.atan2(-heading.getStepX(), heading.getStepZ());
        BikeClientController.setStartPoint(x, pos.getY(), z, yaw);
    }

    /** Called when the rider respawns at the start; {@code atSign} tells whether that start is a START sign. */
    public static void onRespawnAtStart(boolean atSign) {
        if (atSign && !trail.isEmpty()) {
            state = State.ARMED;
            verdict = null;
        }
    }

    /** The server's answer: the personal best, and for a finished run the verdict. */
    public static void onBest(TrailBestPayload message) {
        if (message.bestMs() > 0) {
            BESTS.put(TrailTimes.key(message.trail()), message.bestMs());
        }
        if (message.timeMs() > 0 && state == State.FINISHED
                && TrailTimes.key(message.trail()).equals(TrailTimes.key(trail))) {
            verdict = message;
        }
    }

    private static void reset() {
        state = State.IDLE;
        trail = "";
        verdict = null;
        INSIDE.clear();
        BESTS.clear();
        BikeClientController.clearStartPoint();
        TrailSignRegistry.retainOnly(lastLevel);
    }

    private TrailTimer() {}
}
