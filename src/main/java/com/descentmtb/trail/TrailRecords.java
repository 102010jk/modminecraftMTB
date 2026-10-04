package com.descentmtb.trail;

import com.descentmtb.network.TrailBestPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the trail timer: validates submitted runs and keeps each player's personal best per trail in
 * the player's persistent data ({@code descentmtb / TrailBests}), which is saved with the player.
 */
public final class TrailRecords {
    /** Longest trail name the server accepts; the same limit as the sign editor. */
    public static final int NAME_LIMIT = SignContent.NAME_MAX;

    private static final String ROOT = "descentmtb";
    private static final String BESTS = "TrailBests";

    /** Personal best of the player on a trail in milliseconds, or -1 if none. */
    public static int best(Player player, String trail) {
        CompoundTag bests = bests(player, false);
        String key = TrailTimes.key(trail);
        return bests != null && bests.contains(key, Tag.TAG_INT) ? bests.getInt(key) : -1;
    }

    /**
     * Stores a run if it beats the previous best. Does not validate and does not message the player; see
     * {@link #handle}.
     */
    public static TrailTimes.Result record(Player player, String trail, int timeMs) {
        int previous = best(player, trail);
        TrailTimes.Result result = TrailTimes.evaluate(previous, timeMs);
        if (result.record()) {
            bests(player, true).putInt(TrailTimes.key(trail), timeMs);
        }
        return result;
    }

    /** Forgets the stored best of a trail (used by tests to leave no trace). */
    public static void forget(Player player, String trail) {
        CompoundTag bests = bests(player, false);
        if (bests != null) {
            bests.remove(TrailTimes.key(trail));
        }
    }

    /**
     * Handles a {@code TrailTimePayload}: {@code timeMs == 0} asks for the best time; anything else is a run
     * which is validated, recorded, announced in chat and answered with a {@link TrailBestPayload}.
     */
    public static void handle(ServerPlayer player, String trail, int timeMs) {
        String name = trail == null ? "" : trail.strip();
        if (name.isEmpty() || name.length() > NAME_LIMIT) {
            return;
        }
        if (timeMs == 0) {
            PacketDistributor.sendToPlayer(player, new TrailBestPayload(name, 0, best(player, name), -1, false));
            return;
        }
        if (!TrailTimes.isValid(timeMs)) {
            return;
        }
        TrailTimes.Result result = record(player, name, timeMs);
        PacketDistributor.sendToPlayer(player,
                new TrailBestPayload(name, timeMs, result.best(), result.previous(), result.record()));
        player.sendSystemMessage(announcement(name, timeMs, result));
    }

    private static Component announcement(String name, int timeMs, TrailTimes.Result result) {
        String time = TrailTimes.format(timeMs);
        if (result.record()) {
            return Component.translatable("descentmtb.trail.finish.record", name, time);
        }
        return Component.translatable("descentmtb.trail.finish.time", name, time,
                TrailTimes.format(result.best()), TrailTimes.formatDelta(result.deltaCentis(timeMs)));
    }

    /** Copies the personal bests to the new player instance after death or returning from the End. */
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(ROOT, Tag.TAG_COMPOUND)) {
            event.getEntity().getPersistentData().put(ROOT, old.getCompound(ROOT).copy());
        }
    }

    private static CompoundTag bests(Player player, boolean create) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(ROOT, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            data.put(ROOT, new CompoundTag());
        }
        CompoundTag root = data.getCompound(ROOT);
        if (!root.contains(BESTS, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            root.put(BESTS, new CompoundTag());
        }
        return root.getCompound(BESTS);
    }

    private TrailRecords() {}
}
