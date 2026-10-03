package com.descentmtb.world;

import com.descentmtb.physics.BlockTerrain;
import com.descentmtb.physics.Terrain;
import com.descentmtb.ramp.RampBlock;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link BlockTerrain.Columns} over a live Minecraft level: column tops from
 * block collision shapes (slabs, stairs, snow layers, paths all count), surface
 * material from block tags, and point-in-solid tests for wall probes.
 *
 * <p>Column lookups are cached per game tick ({@link #newTick()}): the physics
 * runs 12 substeps a tick and the same columns get asked again and again.
 */
public final class McColumns implements BlockTerrain.Columns {
    private Level level;
    private final Long2DoubleOpenHashMap cache = new Long2DoubleOpenHashMap();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    public McColumns(Level level) {
        this.level = level;
    }

    public void setLevel(Level level) {
        this.level = level;
        cache.clear();
    }

    public void newTick() {
        cache.clear();
    }

    @Override
    public double top(int x, int z, double yTop, double yBottom) {
        int yStart = (int) Math.floor(yTop);
        int yEnd = (int) Math.floor(yBottom);
        long key = (((long) x & 0x3FFFFFL) << 42) | (((long) z & 0x3FFFFFL) << 20) | (((long) yStart & 0x3FFL) << 10) | ((long) (yStart - yEnd) & 0x3FFL);
        if (cache.containsKey(key)) return cache.get(key);
        double result = Double.NaN;
        boolean aboveFree = !hasCollision(x, yStart + 1, z);
        for (int y = yStart; y >= yEnd; y--) {
            VoxelShape shape = shapeAt(x, y, z);
            boolean solid = !shape.isEmpty();
            if (solid && aboveFree && RampBlock.isRamp(level.getBlockState(mpos.set(x, y, z)))) {
                break; // ramps are exact surfaces; NaN keeps them out of the neighbours' blur
            }
            if (solid && aboveFree) {
                double t = y + shape.max(Direction.Axis.Y);
                if (t <= yTop + 1e-3) {
                    result = t;
                    break;
                }
            }
            aboveFree = !solid || shape.min(Direction.Axis.Y) > 0.5; // (a top slab leaves headroom below it)
        }
        cache.put(key, result);
        return result;
    }

    @Override
    public boolean exactSurface(double x, double z, double yTop, double yBottom, double[] out) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int yStart = (int) Math.floor(yTop), yEnd = (int) Math.floor(yBottom);
        for (int y = yStart; y >= yEnd; y--) {
            mpos.set(bx, y, bz);
            if (!level.isLoaded(mpos)) return false;
            BlockState s = level.getBlockState(mpos);
            if (s.getCollisionShape(level, mpos).isEmpty()) continue;
            if (!RampBlock.isRamp(s)) return false;          // ordinary block: use the smoother
            double fx = x - bx, fz = z - bz;
            double h = y + RampBlock.heightAt(s, fx, fz);
            if (h > yTop + 1e-3) return false;
            out[0] = h;
            out[1] = RampBlock.slopeX(s, fx, fz);
            out[2] = RampBlock.slopeZ(s, fx, fz);
            return true;
        }
        return false;
    }

    @Override
    public Terrain.Surface surface(int x, int z, double topY) {
        mpos.set(x, (int) Math.floor(topY - 0.01), z);
        BlockState s = level.getBlockState(mpos);
        if (RampBlock.isRamp(s)) return Terrain.Surface.TRAIL;
        // snow layer on top of something
        if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) return Terrain.Surface.SNOW;
        if (s.is(BlockTags.ICE) || s.getBlock().getFriction() > 0.9f) return Terrain.Surface.ICE;
        if (s.is(Blocks.DIRT_PATH) || s.is(Blocks.PACKED_MUD)) return Terrain.Surface.TRAIL;
        if (s.is(Blocks.MUD) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.CLAY)) return Terrain.Surface.MUD;
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MYCELIUM) || s.is(BlockTags.LEAVES)) return Terrain.Surface.GRASS;
        if (s.is(BlockTags.SAND) || s.is(Blocks.SOUL_SAND)) return Terrain.Surface.SAND;
        if (s.is(Blocks.GRAVEL) || s.is(Blocks.SUSPICIOUS_GRAVEL)) return Terrain.Surface.GRAVEL;
        if (s.is(BlockTags.DIRT)) return Terrain.Surface.DIRT;
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_SLABS)
                || s.is(BlockTags.WOODEN_STAIRS)) return Terrain.Surface.WOOD;
        return Terrain.Surface.ROCK;
    }

    @Override
    public boolean solid(double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        VoxelShape shape = shapeAt(bx, by, bz);
        if (shape.isEmpty()) return false;
        double lx = x - bx, ly = y - by, lz = z - bz;
        for (AABB box : shape.toAabbs()) {
            if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCollision(int x, int y, int z) {
        return !shapeAt(x, y, z).isEmpty();
    }

    private VoxelShape shapeAt(int x, int y, int z) {
        mpos.set(x, y, z);
        if (!level.isLoaded(mpos)) return Block.box(0, 0, 0, 16, 16, 16); // unloaded = solid wall
        BlockState s = level.getBlockState(mpos);
        return s.getCollisionShape(level, mpos);
    }
}
