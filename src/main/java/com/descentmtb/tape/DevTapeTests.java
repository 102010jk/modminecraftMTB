package com.descentmtb.tape;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Development-only server checks of the trail tape, run from {@link com.descentmtb.trail.DevTrailTests}: posts are
 * placed and linked through {@link TrailTapeItem#click}, the same code path a player's right-click takes.
 */
public final class DevTapeTests {
    private static final BlockPos NOWHERE = BlockPos.ZERO;

    public static void run(ServerPlayer player, ServerLevel level, int x, int y, int z) {
        // stone "ground" blocks the tape is clicked on, with posts landing on top of them
        BlockPos[] ground = {new BlockPos(x + 14, y + 9, z + 3), new BlockPos(x + 20, y + 9, z + 3),
                new BlockPos(x + 20, y + 9, z + 8), new BlockPos(x + 60, y + 9, z + 3)};
        for (BlockPos g : ground) {
            level.setBlock(g, Blocks.STONE.defaultBlockState(), 3);
        }
        try {
            checks(player, level, ground);
        } finally {
            for (BlockPos g : ground) {
                level.removeBlock(g.above(), false);
                level.removeBlock(g, false);
            }
        }
    }

    private static void checks(ServerPlayer player, ServerLevel level, BlockPos[] ground) {
        BlockPos p1 = ground[0].above(), p2 = ground[1].above(), p3 = ground[2].above();
        boolean survival = !player.getAbilities().instabuild;
        ItemStack tape = new ItemStack(ModBlocks.BARRIER_ITEM.get(), 8);

        check(TrailTapeItem.click(player, level, tape, ground[0], Direction.UP) == InteractionResult.SUCCESS
                && level.getBlockState(p1).is(ModBlocks.BARRIER_POST.get()), "the first click puts a post on the ground");
        check(p1.equals(TrailTapeItem.pending(tape, level)), "the first post is remembered in the item");
        check(TrailTapeItem.click(player, level, tape, ground[1], Direction.UP) == InteractionResult.SUCCESS
                && level.getBlockState(p2).is(ModBlocks.BARRIER_POST.get()), "the second click puts the second post");
        check(links(level, p1).equals(List.of(p2)) && links(level, p2).equals(List.of(p1)), "both posts link to each other");
        check(TrailTapeItem.pending(tape, level) == null, "the pending first post is cleared after linking");
        check(!survival || tape.getCount() == 6, "each new post costs one roll in survival");

        // a third post hangs on the second one, a fence of three
        TrailTapeItem.click(player, level, tape, p2, Direction.UP);
        check(p2.equals(TrailTapeItem.pending(tape, level)), "clicking an existing post selects it");
        TrailTapeItem.click(player, level, tape, ground[2], Direction.UP);
        check(links(level, p2).size() == 2 && links(level, p3).equals(List.of(p2)), "a post can hold two tapes");

        // limits: a full post, the same link twice, too long a tape
        ItemStack other = new ItemStack(ModBlocks.BARRIER_ITEM.get(), 8);
        check(TrailTapeItem.click(player, level, other, p2, Direction.UP) == InteractionResult.FAIL
                && TrailTapeItem.pending(other, level) == null, "a post with two tapes cannot start a third");
        TrailTapeItem.click(player, level, other, p1, Direction.UP);
        check(TrailTapeItem.click(player, level, other, p2, Direction.UP) == InteractionResult.FAIL, "a tape cannot be strung twice");
        check(TrailTapeItem.click(player, level, other, ground[3], Direction.UP) == InteractionResult.FAIL
                && !level.getBlockState(ground[3].above()).is(ModBlocks.BARRIER_POST.get()), "a tape longer than the limit is refused");

        CompoundTag saved = level.getBlockEntity(p2).saveWithoutMetadata(level.registryAccess());
        var reloaded = new BarrierPostEntity(p2, level.getBlockState(p2));
        reloaded.loadWithComponents(saved, level.registryAccess());
        check(reloaded.links().equals(links(level, p2)), "the links survive an NBT round trip");

        // breaking a post removes the tape on the other side too
        level.removeBlock(p2, false);
        check(links(level, p1).isEmpty() && links(level, p3).isEmpty(), "breaking a post removes its tapes from the neighbours");
        level.removeBlock(p1, false);
        level.removeBlock(p3, false);
    }

    private static List<BlockPos> links(ServerLevel level, BlockPos post) {
        return level.getBlockEntity(post) instanceof BarrierPostEntity entity ? List.copyOf(entity.links()) : List.of(NOWHERE);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[trailtest] PASS: {}", what);
    }

    private DevTapeTests() {}
}
