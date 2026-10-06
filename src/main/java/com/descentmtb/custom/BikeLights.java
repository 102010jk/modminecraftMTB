package com.descentmtb.custom;

import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Real light from a ridden bike's front / rear light: one vanilla {@code minecraft:light} block a couple of blocks
 * ahead (level 12) and one behind (level 6), moved through AIR blocks only, at most every 2 ticks, and taken away the
 * moment the rider gets off, the bike unloads or the server stops.
 *
 * <p>Every light block we put down is also recorded in the level's {@link Store} (saved data), so if the game dies
 * between placing and removing one, the next sweep deletes the leftover instead of leaving an invisible light behind.
 * Server thread only.
 */
public final class BikeLights {
    public static final int FRONT_LEVEL = 12, REAR_LEVEL = 6;
    /** Sky light (after the time-of-day dimming) and block light below which it counts as dark enough for a light. */
    private static final int DARK_BELOW = 8, STILL_DARK_BELOW = 9;

    /** What one lit bike currently owns. */
    private static final class Active {
        final ServerLevel level;
        BlockPos front, rear;
        long lastMove = Long.MIN_VALUE;

        Active(ServerLevel level) {
            this.level = level;
        }

        boolean lit() {
            return front != null || rear != null;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> releaseAll());
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.getGameTime() % 40 == 7) {
                sweep(level);
            }
        });
    }

    /** Server tick of a bike: keeps its light going while someone rides it. */
    public static void tick(MountainBikeEntity bike) {
        update(bike, null, false);
    }

    /**
     * @param darkOverride null = measure the surroundings; otherwise force dark / bright (dev tests)
     * @param force        skip the "at most every 2 ticks" limit (dev tests)
     */
    public static void update(MountainBikeEntity bike, Boolean darkOverride, boolean force) {
        if (!(bike.level() instanceof ServerLevel level)) {
            return;
        }
        BikeBuild build = bike.build();
        if (!(bike.getControllingPassenger() instanceof Player) || !(build.frontLight() || build.rearLight())
                || !CustomizationConfig.dynamicBikeLights()) {
            release(bike);
            return;
        }
        Active a = ACTIVE.computeIfAbsent(bike.getUUID(), id -> new Active(level));
        long now = level.getGameTime();
        if (!force && now - a.lastMove < 2) {
            return;
        }
        a.lastMove = now;
        boolean dark = darkOverride != null ? darkOverride : isDark(level, bike.blockPosition().above(), a);
        if (!dark) {
            clear(a);   // daylight: no light block; the entry stays so it measures again
            return;
        }
        Vec3 fwd = forward(bike);
        a.front = move(level, a.front, build.frontLight() ? find(level, bike, fwd, new double[]{2.5, 1.8, 3.2}, a.front) : null, FRONT_LEVEL);
        a.rear = move(level, a.rear, build.rearLight() ? find(level, bike, fwd, new double[]{-1.6, -1.1, -2.2}, a.rear) : null, REAR_LEVEL);
    }

    /** Dark enough? The sky counts after the day / night dimming; our own light is discounted so it does not flicker. */
    private static boolean isDark(ServerLevel level, BlockPos at, Active a) {
        boolean on = a.lit();
        int sky = level.getBrightness(LightLayer.SKY, at) - level.getSkyDarken();
        int block = level.getBrightness(LightLayer.BLOCK, at);
        int own = 0;
        if (a.front != null) {
            own = Math.max(own, FRONT_LEVEL - a.front.distManhattan(at));
        }
        if (a.rear != null) {
            own = Math.max(own, REAR_LEVEL - a.rear.distManhattan(at));
        }
        int natural = on && block <= own ? 0 : block;   // ours dominates: assume nothing else lights the spot
        return Math.max(sky, natural) < (on ? STILL_DARK_BELOW : DARK_BELOW);
    }

    /** The bike's heading as a horizontal unit vector (same convention as the physics: yaw 0 looks along +z). */
    private static Vec3 forward(MountainBikeEntity bike) {
        double yaw = Math.toRadians(bike.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** The first position near the wanted spot that holds air (or is already our own light). */
    private static BlockPos find(ServerLevel level, MountainBikeEntity bike, Vec3 fwd, double[] distances, BlockPos current) {
        for (double dy : new double[]{1.0, 0.4, 1.7}) {
            for (double d : distances) {
                BlockPos p = BlockPos.containing(bike.getX() + fwd.x * d, bike.getY() + dy, bike.getZ() + fwd.z * d);
                if (p.equals(current) || level.isLoaded(p) && level.getBlockState(p).isAir()) {
                    return p;
                }
            }
        }
        return null;
    }

    /** Moves one light block from {@code from} to {@code to} (either may be null); never touches a non-air block. */
    private static BlockPos move(ServerLevel level, BlockPos from, BlockPos to, int lightLevel) {
        if (from != null && from.equals(to) && level.getBlockState(from).is(Blocks.LIGHT)) {
            return from;
        }
        BlockPos placed = null;
        if (to != null && level.isLoaded(to) && level.getBlockState(to).isAir()) {
            level.setBlock(to, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel), 2);
            Store.of(level).add(to);
            placed = to;
        }
        if (from != null && !from.equals(placed)) {
            remove(level, from);
        }
        return placed;
    }

    /** Removes our light block at a position (if it is still a light block) and forgets it. */
    private static void remove(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) {
            return;   // stays in the store; the sweep removes it when the chunk is back
        }
        if (level.getBlockState(p).is(Blocks.LIGHT)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        Store.of(level).remove(p);
    }

    /** The bike is not lit any more (rider left, unloaded, removed). */
    public static void release(MountainBikeEntity bike) {
        Active a = ACTIVE.remove(bike.getUUID());
        if (a != null) {
            clear(a);
        }
    }

    private static void clear(Active a) {
        if (a.front != null) {
            remove(a.level, a.front);
            a.front = null;
        }
        if (a.rear != null) {
            remove(a.level, a.rear);
            a.rear = null;
        }
    }

    private static void releaseAll() {
        for (Active a : new ArrayList<>(ACTIVE.values())) {
            clear(a);
        }
        ACTIVE.clear();
    }

    /** Light blocks in the store that no bike owns any more (after a crash, or a chunk that was not loaded): remove them. */
    static void sweep(ServerLevel level) {
        Store store = Store.of(level);
        if (store.placed.isEmpty()) {
            return;
        }
        Set<Long> owned = new HashSet<>();
        for (Active a : ACTIVE.values()) {
            if (a.level == level) {
                if (a.front != null) owned.add(a.front.asLong());
                if (a.rear != null) owned.add(a.rear.asLong());
            }
        }
        for (long packed : new ArrayList<>(store.placed)) {
            if (!owned.contains(packed)) {
                remove(level, BlockPos.of(packed));
            }
        }
    }

    /** Dev tests: the number of recorded light blocks of a level. */
    public static int recorded(ServerLevel level) {
        return Store.of(level).placed.size();
    }

    /** The light blocks this feature has placed in one level and not removed yet. */
    static final class Store extends SavedData {
        private static final String NAME = "descentmtb_bike_lights";
        final Set<Long> placed = new HashSet<>();

        static Store of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Store::new, Store::load, null), NAME);
        }

        static Store load(CompoundTag tag, HolderLookup.Provider registries) {
            Store s = new Store();
            for (Tag t : tag.getList("Placed", Tag.TAG_LONG)) {
                s.placed.add(((LongTag) t).getAsLong());
            }
            return s;
        }

        void add(BlockPos p) {
            if (placed.add(p.asLong())) {
                setDirty();
            }
        }

        void remove(BlockPos p) {
            if (placed.remove(p.asLong())) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (long p : placed) {
                list.add(LongTag.valueOf(p));
            }
            tag.put("Placed", list);
            return tag;
        }
    }

    private BikeLights() {}
}
