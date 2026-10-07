package com.descentmtb.client.map;

import com.descentmtb.map.TerrainShader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Reads the surface of the loaded world under a map: one column per texture pixel, only in chunks the client already
 * has (never forces a chunk load). Runs once per texture build, not per frame.
 */
final class TerrainSampler {
    private TerrainSampler() {}

    /** Is there terrain to draw: the client is in the routes' dimension and the middle of the map is loaded. */
    static boolean available(Level level, ResourceLocation dimension, MapTexture.Bounds b) {
        if (level == null || dimension == null || !level.dimension().location().equals(dimension)) return false;
        int cx = (int) Math.floor(b.minX() + b.span() / 2) >> 4, cz = (int) Math.floor(b.minZ() + b.span() / 2) >> 4;
        return level.hasChunk(cx, cz);
    }

    /** The sampled picture, or null when no column of the area is loaded. */
    static TerrainShader.Terrain sample(Level level, MapTexture.Bounds b, int size) {
        int n = size * size, loaded = 0;
        byte[] kind = new byte[n];
        int[] rgb = new int[n], height = new int[n], ground = new int[n], depth = new int[n];
        double margin = size * 5 / 128.0, perPixel = b.span() / (size - 2 * margin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int min = level.getMinBuildHeight();
        for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
            int bx = (int) Math.floor(b.minX() + (px - margin) * perPixel), bz = (int) Math.floor(b.minZ() + (py - margin) * perPixel);
            if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            if (top < min) continue;
            int i = py * size + px;
            pos.set(bx, top, bz);
            BlockState state = level.getBlockState(pos);
            height[i] = top;
            ground[i] = Math.max(min, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1);
            if (state.getFluidState().is(FluidTags.WATER)) {
                kind[i] = TerrainShader.WATER;
                depth[i] = Math.max(1, top - (level.getHeight(Heightmap.Types.OCEAN_FLOOR, bx, bz) - 1));
            } else if (state.is(BlockTags.LEAVES)) {
                kind[i] = TerrainShader.LEAVES;
            } else {
                kind[i] = TerrainShader.LAND;
                MapColor color = state.getMapColor(level, pos);
                // grass, flowers and other walk-through plants: take the ground colour underneath
                if (top > min && color != MapColor.SNOW && state.getCollisionShape(level, pos).isEmpty()) {
                    pos.setY(top - 1);
                    color = level.getBlockState(pos).getMapColor(level, pos);
                }
                rgb[i] = color == MapColor.NONE ? 0x976d4d : color.col;
            }
            loaded++;
        }
        return loaded == 0 ? null : new TerrainShader.Terrain(size, kind, rgb, height, ground, depth, perPixel);
    }
}
