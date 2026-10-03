package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: hand sculpting through the real item code paths (place, raise, lower, layering,
 * conversion of plain ground). Called from {@link DevTrailTests} on a prepared grass field at y.
 */
final class DevSculptTests {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    private static void placeAt(ServerPlayer p, int x, int y, int z) {
        var hit = new BlockHitResult(new Vec3(x + .5, y, z + .5), Direction.UP, new BlockPos(x, y - 1, z), false);
        p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
    }

    private static double[] corners(ServerLevel l, int x, int y, int z) {
        return l.getBlockEntity(new BlockPos(x, y, z)) instanceof TrailSurfaceEntity shaped ? shaped.corners() : null;
    }

    private static void assertCorners(ServerLevel l, int x, int y, int z, double... expected) {
        double[] c = corners(l, x, y, z);
        check(c != null, "shaped block at " + x + "," + y + "," + z);
        for (int i = 0; i < 4; i++) {
            if (!Double.isNaN(expected[i])) {
                check(Math.abs(c[i] - expected[i]) < 1e-9, String.format("corner %d at %d,%d is %.3f (expected %.3f)", i, x, z, c[i], expected[i]));
            }
        }
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        ItemStack dirt = new ItemStack(ModBlocks.TRAIL_DIRT.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, dirt);
        int bx = x + 3, bz = z + 33;
        double n = Double.NaN, half = .5, up = half + ShapingBlockItem.STEP;

        // placing: a lone block is a half-block bump, the neighbour matches it
        placeAt(p, bx, y, bz);
        assertCorners(l, bx, y, bz, half, half, half, half);
        placeAt(p, bx + 1, y, bz);
        assertCorners(l, bx + 1, y, bz, half, half, half, half);

        // right-click near the east edge of block 1 raises that edge, and the neighbour shares it
        var first = new BlockPos(bx, y, bz);
        ShapingBlockItem.sculpt(p, dirt, first, new Vec3(bx + .95, y + .5, bz + .5), false);
        assertCorners(l, bx, y, bz, half, up, half, up);
        assertCorners(l, bx + 1, y, bz, up, half, up, half);

        // left-click lowers it again
        ShapingBlockItem.sculpt(p, dirt, first, new Vec3(bx + .95, y + .5, bz + .5), true);
        assertCorners(l, bx, y, bz, half, half, half, half);

        // a high ridge crosses the block boundary: a second layer appears and holds the same plane
        for (int i = 0; i < 5; i++) {
            ShapingBlockItem.sculpt(p, dirt, first, new Vec3(bx + .95, y + .5, bz + .5), false);
        }
        double ridge = half + 5 * ShapingBlockItem.STEP;
        assertCorners(l, bx, y, bz, half, ridge, half, ridge);
        assertCorners(l, bx, y + 1, bz, n, ridge - 1, n, ridge - 1);
        for (int i = 0; i < 5; i++) {
            ShapingBlockItem.sculpt(p, dirt, first, new Vec3(bx + .95, y + .5, bz + .5), true);
        }
        check(l.getBlockState(new BlockPos(bx, y + 1, bz)).isAir(), "the upper layer disappears when the ridge is lowered");
        assertCorners(l, bx, y, bz, half, half, half, half);

        // the middle of a block moves all four corners
        ShapingBlockItem.sculpt(p, dirt, first, new Vec3(bx + .5, y + .5, bz + .5), false);
        assertCorners(l, bx, y, bz, up, up, up, up);

        // plain ground becomes a shaped copy of itself when lowered
        int gx = bx + 6;
        var ground = new BlockPos(gx, y - 1, bz);
        check(l.getBlockState(ground).is(Blocks.GRASS_BLOCK), "test field is plain grass");
        ShapingBlockItem.sculpt(p, dirt, ground, new Vec3(gx + .1, y, bz + .1), true);
        double dug = 1 - ShapingBlockItem.STEP;
        assertCorners(l, gx, y - 1, bz, dug, 1, 1, 1);
        check(((TrailSurfaceEntity) l.getBlockEntity(ground)).getMaterial().is(Blocks.GRASS_BLOCK), "it keeps the grass look");
        assertCorners(l, gx - 1, y - 1, bz, n, dug, n, n);          // neighbour west shares the dug vertex
        assertCorners(l, gx, y - 1, bz - 1, n, n, dug, n);          // neighbour north shares it (its south-west corner)
        DescentMtb.LOG.info("[sculpttest] ALL PASSED");
    }

    private DevSculptTests() {}
}
