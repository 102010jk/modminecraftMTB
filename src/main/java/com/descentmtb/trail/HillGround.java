package com.descentmtb.trail;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;


/**
 * The terrain of a hillside as the downhill planner sees it: the top of the ground in every block column, with
 * trees (logs, leaves) and plants looked through, and NaN for columns under water or in chunks that are not loaded.
 * Between the column centres the height is interpolated, so the planner works on a continuous surface.
 */
final class HillGround {
    /** How far (blocks) below the surface of a column the ground is looked for under trees. */
    private static final int SEARCH_DEPTH = 48;

    private final Level level;
    /** Every column is scanned once per plan (the planner asks for the same columns again and again). */
    private final Long2DoubleOpenHashMap columns = new Long2DoubleOpenHashMap();

    HillGround(Level level) {
        this.level = level;
    }

    /** Top of the ground of the block column (x, z) as an absolute height; NaN when there is none to build on. */
    double column(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        if (columns.containsKey(key)) {
            return columns.get(key);
        }
        double top = scan(x, z);
        columns.put(key, top);
        return top;
    }

    private double scan(int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getMinBuildHeight(), z);
        if (!level.isLoaded(pos)) {
            return Double.NaN;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = surface; y > Math.max(level.getMinBuildHeight(), surface - SEARCH_DEPTH); y--) {
            BlockState state = level.getBlockState(pos.setY(y));
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                return Double.NaN;   // a lake or a river
            }
            var shape = state.getCollisionShape(level, pos);
            if (shape != Shapes.empty() && !shape.isEmpty()) {
                return y + shape.max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return Double.NaN;
    }

    /**
     * Height of the ground at (x, z), interpolated between the column centres around it; NaN at the centre of a
     * column that cannot be built on (and wherever all four columns around the point cannot).
     */
    double height(double x, double z) {
        double fx = x - .5, fz = z - .5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j, sum = 0, weight = 0;
        for (int k = 0; k < 4; k++) {
            double h = column(i + k % 2, j + k / 2);
            double w = (k % 2 == 0 ? 1 - tx : tx) * (k / 2 == 0 ? 1 - tz : tz);
            if (!Double.isNaN(h) && w > 0) {
                sum += w * h;
                weight += w;
            }
        }
        return weight == 0 ? Double.NaN : sum / weight;
    }
}
