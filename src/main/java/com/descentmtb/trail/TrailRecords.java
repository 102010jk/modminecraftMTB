package com.descentmtb.trail;

import com.descentmtb.network.TrailBestPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the trail timer: validates submitted runs and keeps each player's personal best per trail in
 * the player's persistent data ({@code descentmtb / TrailBests}), which is saved with the player.
 *
 * <p>A client can send anything, so a run is only recorded when the time is plausible, the player does not submit
 * more than once every {@link #MIN_GAP_TICKS} ticks, has fewer than {@link #MAX_TRAILS} distinct trails, and the
 * server sees a FINISH sign of that name near the player. The name is stripped of formatting codes. (The server
 * does not track whether the player passed the START sign: that would need a check every tick.)
 */
public final class TrailRecords {
    /** Longest trail name the server accepts; the same limit as the sign editor. */
    public static final int NAME_LIMIT = SignContent.NAME_MAX;

    /** Most distinct trails whose best time is stored per player. */
    public static final int MAX_TRAILS = 64;
    /** Shortest time between two submitted runs of a player (2 s). */
    public static final int MIN_GAP_TICKS = 40;
    /** How far (blocks) from the player a FINISH sign of the trail must be. */
    public static final double FINISH_RANGE = 64;

    /** Game time of each player's last submitted run. */
    private static final Map<UUID, Long> LAST_RUN = new HashMap<>();

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
        String name = clean(trail);
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
        long now = player.serverLevel().getGameTime();
        Long last = LAST_RUN.put(player.getUUID(), now);
        if (last != null && now >= last && now - last < MIN_GAP_TICKS) {
            return;   // one run at a time
        }
        if (best(player, name) < 0 && storedTrails(player) >= MAX_TRAILS) {
            player.displayClientMessage(Component.translatable("descentmtb.trail.limit", MAX_TRAILS), true);
            return;
        }
        if (!finishSignNear(player, name)) {
            player.displayClientMessage(Component.translatable("descentmtb.trail.no_finish"), true);
            return;
        }
        TrailTimes.Result result = record(player, name, timeMs);
        PacketDistributor.sendToPlayer(player,
                new TrailBestPayload(name, timeMs, result.best(), result.previous(), result.record()));
        player.sendSystemMessage(announcement(name, timeMs, result));
    }

    /** The trail name as it is stored and shown: outer spaces, formatting codes ({@code §x}) and control characters removed. */
    public static String clean(String raw) {
        return raw == null ? "" : raw.replaceAll("§.?", "").replaceAll("\\p{Cntrl}", "").strip();
    }

    /** How many different trails the player has a stored time for. */
    public static int storedTrails(Player player) {
        CompoundTag bests = bests(player, false);
        return bests == null ? 0 : bests.getAllKeys().size();
    }

    /** True when a FINISH sign of the trail stands within {@link #FINISH_RANGE} blocks of the player (loaded chunks only). */
    private static boolean finishSignNear(ServerPlayer player, String name) {
        ServerLevel level = player.serverLevel();
        String key = TrailTimes.key(name);
        ChunkPos centre = player.chunkPosition();
        int radius = ((int) FINISH_RANGE >> 4) + 1;
        for (int cx = centre.x - radius; cx <= centre.x + radius; cx++) {
            for (int cz = centre.z - radius; cz <= centre.z + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof TrailSignEntity sign && sign.content().type() == SignContent.Type.FINISH
                            && TrailTimes.key(clean(sign.content().name())).equals(key)
                            && sign.getBlockPos().distToCenterSqr(player.position()) <= FINISH_RANGE * FINISH_RANGE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static void clearSession() {
        LAST_RUN.clear();
    }

    /** Forgets the rate limit of a player who left. */
    static void forget(UUID player) {
        LAST_RUN.remove(player);
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
