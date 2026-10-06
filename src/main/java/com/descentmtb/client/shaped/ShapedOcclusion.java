package com.descentmtb.client.shaped;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.ShapeKey;
import com.descentmtb.trail.TrailSurfaceBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative boundary coverage. Shape state alone cannot identify a trail block's exposed faces. */
final class ShapedOcclusion {
    static boolean covered(BlockState ownState, ShapeKey own, BlockState neighborState, ShapeKey neighbor, Direction face) {
        if (face.getAxis() == Direction.Axis.Y) {
            // Cull only when the neighbor occupies the entire shared horizontal boundary.
            // Interior sloped tops / raised deck undersides are unculled quads, so are unaffected.
            for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
                double raw = raw(neighborState, neighbor, x, z);
                double bottom = neighbor.deck() ? clamp(raw - .14) : 0, top = clamp(raw);
                if (face == Direction.UP) {
                    if (bottom > .00001 || top <= .00001) return false;
                } else if (top < 1 - .00001 || bottom >= 1 - .00001) return false;
            }
            return true;
        }
        boolean any = false;
        // Includes every boundary vertex of the 8-cell mesh, plus midpoints. Deck thickness matters.
        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0;
            double x = face.getAxis() == Direction.Axis.X ? (face == Direction.WEST ? 0 : 1) : t;
            double z = face.getAxis() == Direction.Axis.Z ? (face == Direction.NORTH ? 0 : 1) : t;
            double nx = face.getAxis() == Direction.Axis.X ? 1 - x : x;
            double nz = face.getAxis() == Direction.Axis.Z ? 1 - z : z;
            double a = raw(ownState, own, x, z), b = raw(neighborState, neighbor, nx, nz);
            double aTop = clamp(a), bTop = clamp(b);
            double aBottom = own.deck() ? clamp(a - .14) : 0;
            double bBottom = neighbor.deck() ? clamp(b - .14) : 0;
            if (aTop <= aBottom + .00001) continue;
            any = true;
            if (bTop < aTop - .00001 || bBottom > aBottom + .00001 || bTop <= bBottom + .00001) return false;
        }
        return any;
    }

    private static double raw(BlockState state, ShapeKey shape, double x, double z) {
        if (state.getBlock() instanceof TrailSurfaceBlock) {
            // Match bilerp without allocating a corners array for every sample during chunk meshing.
            return ((shape.c0() * (1 - x) + shape.c1() * x) * (1 - z)
                    + (shape.c2() * (1 - x) + shape.c3() * x) * z) / 1024.0;
        }
        return RampBlock.heightAt(state, x, z);
    }

    private static double clamp(double height) { return Math.max(0, Math.min(1, height)); }
    private ShapedOcclusion() {}
}
