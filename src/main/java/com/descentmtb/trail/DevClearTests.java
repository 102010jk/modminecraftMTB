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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the machete through its real item code path. Fells an oak that touches a birch (the birch
 * must survive), then clears plants from the ground. Called from {@link DevTrailTests} on the grass field at y.
 */
final class DevClearTests {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[cleartest] PASS: {}", what);
    }

    private static void click(ServerPlayer p, ItemStack tool, BlockPos block, Direction face, Vec3 at) {
        tool.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(at, face, block, false)));
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        ItemStack machete = new ItemStack(ModBlocks.CLEARING_TOOL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, machete);

        // an oak (5 logs + natural leaves) with a birch (3 logs) growing right against it
        int tx = x + 30, tz = z + 33;
        for (int i = 0; i < 5; i++) l.setBlock(new BlockPos(tx, y + i, tz), Blocks.OAK_LOG.defaultBlockState(), 2);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
            BlockPos pos = new BlockPos(tx + dx, y + dy, tz + dz);
            if (l.getBlockState(pos).isAir()) {
                l.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 2);
            }
        }
        for (int i = 0; i < 3; i++) l.setBlock(new BlockPos(tx + 1, y + i, tz), Blocks.BIRCH_LOG.defaultBlockState(), 2);
        l.setBlock(new BlockPos(tx + 5, y, tz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2);  // a placed hedge

        click(p, machete, new BlockPos(tx, y + 2, tz), Direction.WEST, new Vec3(tx, y + 2.5, tz + .5));
        for (int i = 0; i < 5; i++) check(l.getBlockState(new BlockPos(tx, y + i, tz)).isAir(), "oak log " + i + " is felled");
        boolean leavesLeft = false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
            leavesLeft |= l.getBlockState(new BlockPos(tx + dx, y + dy, tz + dz)).is(Blocks.OAK_LEAVES);
        }
        check(!leavesLeft, "the oak's natural leaves are gone with it");
        for (int i = 0; i < 3; i++) check(l.getBlockState(new BlockPos(tx + 1, y + i, tz)).is(Blocks.BIRCH_LOG), "the touching birch log " + i + " survives");
        check(l.getBlockState(new BlockPos(tx + 5, y, tz)).is(Blocks.OAK_LEAVES), "a player-placed hedge is not touched");

        // plants on the ground disappear
        int gx = x + 20, gz = z + 33;
        BlockPos[] plants = {new BlockPos(gx, y, gz), new BlockPos(gx + 1, y, gz), new BlockPos(gx, y, gz + 1), new BlockPos(gx - 1, y, gz - 1)};
        l.setBlock(plants[0], Blocks.SHORT_GRASS.defaultBlockState(), 2);
        l.setBlock(plants[1], Blocks.POPPY.defaultBlockState(), 2);
        l.setBlock(plants[2], Blocks.FERN.defaultBlockState(), 2);
        l.setBlock(plants[3], Blocks.OAK_SAPLING.defaultBlockState(), 2);
        click(p, machete, new BlockPos(gx, y - 1, gz), Direction.UP, new Vec3(gx + .5, y, gz + .5));
        for (BlockPos plant : plants) check(l.getBlockState(plant).isAir(), "plant at " + plant.toShortString() + " is cleared");
        check(l.getBlockState(new BlockPos(gx, y - 1, gz)).is(Blocks.GRASS_BLOCK), "the ground itself stays");
        DescentMtb.LOG.info("[cleartest] ALL PASSED");
    }

    private DevClearTests() {}
}
