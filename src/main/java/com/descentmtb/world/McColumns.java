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
    private SableTerrain terrain;
    public Terrain terrain() {
        if (terrain == null) terrain = new SableTerrain(new BlockTerrain(this), this, level);
        return terrain;
    }
    private final Long2DoubleOpenHashMap cache = new Long2DoubleOpenHashMap();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    public McColumns(Level level) {
        this.level = level;
    }

    public void setLevel(Level level) {
        if (this.level != level) terrain = null;
        this.level = level;
        cache.clear();
    }

    public void newTick() {
        cache.clear();
        if (terrain != null) terrain.newTick();
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
            if (solid && aboveFree && (RampBlock.isRamp(level.getBlockState(mpos.set(x, y, z))) || level.getBlockState(mpos).getBlock() instanceof com.descentmtb.trail.TrailObstacleBlock)) {
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
            if (passable(s) || s.getCollisionShape(level, mpos).isEmpty()) continue;
            if (s.getBlock() instanceof com.descentmtb.trail.TrailObstacleBlock obstacle) {
                double fx=x-bx,fz=z-bz,h=y+obstacle.height(s,fx,fz);
                if(h>yTop+.001)return false;
                out[0]=h;
                out[1]=(obstacle.height(s,fx+.001,fz)-obstacle.height(s,fx-.001,fz))/.002;
                out[2]=(obstacle.height(s,fx,fz+.001)-obstacle.height(s,fx,fz-.001))/.002;
                return true;
            }
            if (!RampBlock.isRamp(s)) {
                // A ceiling or overhang reaching above the query (a tunnel roof over a shaped trail): it cannot be
                // the riding surface, so look on below it instead of giving the wheel up to the smoother, which
                // leaves ramps out and would drop the bike through the shaped block.
                if (y + s.getCollisionShape(level, mpos).max(Direction.Axis.Y) > yTop + 1e-3) continue;
                return false;                                   // ordinary block: use the smoother
            }
            double fx = x - bx, fz = z - bz;
            if(level.getBlockEntity(mpos) instanceof com.descentmtb.trail.TrailSurfaceEntity shaped && !shaped.hasSurface(fx,fz)) continue;
            double h = y + com.descentmtb.trail.TrailSurfaces.height(s, level, mpos, fx, fz);
            if (h > yTop + 1e-3) return false;
            out[0] = h;
            out[1] = com.descentmtb.trail.TrailSurfaces.slopeX(s, level, mpos, fx, fz);
            out[2] = com.descentmtb.trail.TrailSurfaces.slopeZ(s, level, mpos, fx, fz);
            return true;
        }
        return false;
    }

    @Override
    public Terrain.Surface surface(int x, int z, double topY) {
        mpos.set(x, (int) Math.floor(topY - 0.01), z);
        BlockState s = level.getBlockState(mpos);
        if(s.isAir()){mpos.move(Direction.DOWN);s=level.getBlockState(mpos);}
        if(s.is(com.descentmtb.registry.ModBlocks.AIRBAG.get()))return Terrain.Surface.AIRBAG;
        if (RampBlock.isRamp(s)) {
            if(level.getBlockEntity(mpos) instanceof com.descentmtb.trail.TrailSurfaceEntity shaped && shaped.overlay()!=0)
                return shaped.overlay()==1?Terrain.Surface.WOOD:Terrain.Surface.ROCK;
            if(level.getBlockEntity(mpos) instanceof com.descentmtb.ramp.RampBlockEntity be && be.getMaterial().is(BlockTags.PLANKS))return Terrain.Surface.WOOD;
            return Terrain.Surface.TRAIL;
        }
        if(s.is(com.descentmtb.registry.ModBlocks.TRAIL_ROOTS.get()))return Terrain.Surface.WOOD;
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
        BlockState state = level.getBlockState(mpos);
        // Bike probes use the same continuous surface as the tyres. Vanilla's
        // stair-shaped collision approximation is only for walking players.
        if(state.getBlock() instanceof com.descentmtb.trail.TrailObstacleBlock obstacle) return ly<obstacle.height(state,lx,lz);
        if (RampBlock.isRamp(state)) {
            return com.descentmtb.trail.TrailSurfaces.solid(state, level, mpos, lx, ly, lz);
        }
        for (AABB box : shape.toAabbs()) {
            if (lx >= box.minX && lx <= box.maxX && ly >= box.minY && ly <= box.maxY && lz >= box.minZ && lz <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double waterTop(int x, int z, double yTop, double yBottom) {
        for (int y = (int) Math.floor(yTop); y >= (int) Math.floor(yBottom); y--) {
            mpos.set(x, y, z);
            if (!level.isLoaded(mpos)) return Double.NaN;
            var fluid = level.getFluidState(mpos);
            if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                // a full column of water reads up to the block above's surface; the topmost block has its own height
                mpos.set(x, y + 1, z);
                boolean waterAbove = level.getFluidState(mpos).is(net.minecraft.tags.FluidTags.WATER);
                mpos.set(x, y, z);
                return waterAbove ? y + 1 : y + fluid.getHeight(level, mpos);
            }
            if (!shapeAt(x, y, z).isEmpty()) return Double.NaN; // solid ground before any water
        }
        return Double.NaN;
    }

    private boolean hasCollision(int x, int y, int z) {
        return !shapeAt(x, y, z).isEmpty();
    }

    @Override
    public double collisionTop(double x, double z, double top, double bottom) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double fx = x - bx, fz = z - bz;
        for (int y = (int) Math.floor(top); y >= Math.floor(bottom); y--) {
            VoxelShape shape = shapeAt(bx, y, bz);
            BlockState state = level.getBlockState(mpos);
            if (RampBlock.isRamp(state)) {
                if(level.getBlockEntity(mpos) instanceof com.descentmtb.trail.TrailSurfaceEntity shaped && !shaped.hasSurface(fx,fz)) continue;
                double h = y + com.descentmtb.trail.TrailSurfaces.height(state, level, mpos, fx, fz);
                if (h <= top + 1e-4 && h >= bottom) return h;
                continue;
            }
            for (AABB box : shape.toAabbs()) {
                double h = y + box.maxY;
                if (fx >= box.minX && fx <= box.maxX && fz >= box.minZ && fz <= box.maxZ
                        && h <= top + 1e-4 && h >= bottom) return h;
            }
        }
        return Double.NaN;
    }

    private VoxelShape shapeAt(int x, int y, int z) {
        mpos.set(x, y, z);
        if (!level.isLoaded(mpos)) return Block.box(0, 0, 0, 16, 16, 16); // unloaded = solid wall
        BlockState s = level.getBlockState(mpos);
        return passable(s) ? net.minecraft.world.phys.shapes.Shapes.empty() : s.getCollisionShape(level, mpos);
    }

    /** Leaves don't stop a bike: you ride through the bushes and low branches (the physics ignores them). */
    static boolean passable(BlockState s) {
        return s.is(BlockTags.LEAVES);
    }
}
