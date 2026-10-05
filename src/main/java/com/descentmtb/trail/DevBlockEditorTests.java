package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the block editor's server path ({@link BlockEditor#apply}, what the editor screen's Apply button
 * ends in) and the cursor's sub-types and steps through the real item code. Checks that only the edited column
 * changes, layering above one block, a plain grass block becoming a shaped copy, the wooden deck switch, the reach
 * check, undo, and the survival price of a material from the off-hand (paid once, handed back by undo). The player
 * is moved next to the blocks and put back afterwards; the game mode is restored. Called from {@link DevSculptTests}.
 */
final class DevBlockEditorTests {
    private static final double SIXTEENTH = 1.0 / 16;

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    private static double[] corners(ServerLevel l, int x, int y, int z) {
        return l.getBlockEntity(new BlockPos(x, y, z)) instanceof TrailSurfaceEntity shaped ? shaped.corners() : null;
    }

    private static void assertCorners(ServerLevel l, int x, int y, int z, double... expected) {
        double[] c = corners(l, x, y, z);
        check(c != null, "editor: shaped block at " + x + "," + y + "," + z);
        for (int i = 0; i < 4; i++) {
            check(Math.abs(c[i] - expected[i]) < 1e-9, String.format("editor: corner %d at %d,%d,%d is %.4f (expected %.4f)", i, x, y, z, c[i], expected[i]));
        }
    }

    private static void place(ServerLevel l, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        l.setBlock(pos, ModBlocks.TRAIL_SURFACE.get().defaultBlockState(), 3);
        ((TrailSurfaceEntity) l.getBlockEntity(pos)).setShape(new double[]{1, 1, 1, 1}, false);
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        int ex = x + 60, ez = z - 10;   // a strip of the jump field, south of the jumps
        Vec3 home = p.position();
        ItemStack held = p.getMainHandItem(), offHand = p.getOffhandItem();
        GameType mode = p.gameMode.getGameModeForPlayer();
        try {
            for (int bx = ex; bx < ex + 12; bx++) {
                for (int bz = ez; bz < ez + 3; bz++) {
                    l.setBlock(new BlockPos(bx, y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    for (int by = y; by <= y + 4; by++) {
                        l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, tool);
            int bz = ez + 1;
            place(l, ex + 2, y, bz);
            place(l, ex + 3, y, bz);

            p.teleportTo(l, ex + 40, y + 30, bz, 0, 0);
            check(!BlockEditor.apply(p, new BlockPos(ex + 2, y, bz), new int[]{0, 8, 16, 24}, false, false), "editor: a block out of reach is refused");
            assertCorners(l, ex + 2, y, bz, 1, 1, 1, 1);

            p.teleportTo(l, ex + 5.5, y, bz + .5, 90, 0);
            check(BlockEditor.apply(p, new BlockPos(ex + 2, y, bz), new int[]{0, 8, 16, 24}, false, false), "editor: corner heights are applied");
            assertCorners(l, ex + 2, y, bz, 0, .5, 1, 1.5);
            assertCorners(l, ex + 2, y + 1, bz, -1, -.5, 0, .5);   // above one block the surface is stacked
            assertCorners(l, ex + 3, y, bz, 1, 1, 1, 1);            // only the edited column changes
            check(l.getBlockState(new BlockPos(ex + 1, y, bz)).isAir(), "editor: the air beside it stays air");
            // the corner at the block's foot now makes the block below part of the stack, so the base is one lower
            check(java.util.Arrays.equals(CornerEdits.toSixteenths(ShapePresets.editable(l, new BlockPos(ex + 2, y + 1, bz)).editorLocal()),
                    new int[]{16, 24, 32, 40}), "editor: the upper layer shows the same shape");
            check(!BlockEditor.apply(p, new BlockPos(ex + 2, y + 1, bz), new int[]{16, 24, 32, 40}, false, false),
                    "editor: applying the same heights (from the upper layer) changes nothing");
            TrailEdit.undo(l, p);
            assertCorners(l, ex + 2, y, bz, 1, 1, 1, 1);
            check(l.getBlockState(new BlockPos(ex + 2, y + 1, bz)).isAir(), "editor: undo removes the upper layer again");

            // a plain grass block becomes a shaped copy of itself
            BlockPos grass = new BlockPos(ex + 6, y - 1, bz);
            check(BlockEditor.apply(p, grass, new int[]{16, 16, 16, 12}, false, false), "editor: a plain block can be edited");
            assertCorners(l, ex + 6, y - 1, bz, 1, 1, 1, 1 - 4 * SIXTEENTH);
            check(((TrailSurfaceEntity) l.getBlockEntity(grass)).getMaterial().is(Blocks.GRASS_BLOCK), "editor: it keeps the grass look");
            check(l.getBlockState(grass.east()).is(Blocks.GRASS_BLOCK) && l.getBlockEntity(grass.east()) == null, "editor: the grass beside it stays plain");

            // the deck switch: a surface 1.5 blocks high becomes a wooden deck
            check(BlockEditor.apply(p, new BlockPos(ex + 3, y, bz), new int[]{24, 24, 24, 24}, false, false), "editor: raised to 1.5");
            check(BlockEditor.apply(p, new BlockPos(ex + 3, y + 1, bz), new int[]{24, 24, 24, 24}, true, false), "editor: switched to a deck");
            check(l.getBlockEntity(new BlockPos(ex + 3, y + 1, bz)) instanceof TrailSurfaceEntity deck && deck.deck(), "editor: the surface is a deck");
            check(l.getBlockEntity(new BlockPos(ex + 3, y, bz)) instanceof TrailSurfaceEntity below && !below.deck(),
                    "editor: the full block the deck stands on stays ground");
            assertCorners(l, ex + 2, y, bz, 1, 1, 1, 1);

            // material from the off-hand: paid once in survival, handed back by undo
            p.setGameMode(GameType.SURVIVAL);
            place(l, ex + 9, y, bz);
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE));
            int stoneBefore = p.getInventory().countItem(Items.STONE);
            BlockPos paid = new BlockPos(ex + 9, y, bz);
            check(BlockEditor.apply(p, paid, new int[]{16, 16, 16, 16}, false, true), "editor: the off-hand material is applied in survival");
            check(l.getBlockEntity(paid) instanceof TrailSurfaceEntity s && s.getMaterial().is(Blocks.STONE) && s.isConsumed(),
                    "editor: the block is stone and paid for");
            check(p.getInventory().countItem(Items.STONE) == stoneBefore - 1, "editor: one stone was taken");
            TrailEdit.undo(l, p);
            check(p.getInventory().countItem(Items.STONE) == stoneBefore, "editor: undo hands the stone back");
            check(l.getBlockEntity(paid) instanceof TrailSurfaceEntity s && !s.getMaterial().is(Blocks.STONE) && !s.isConsumed(),
                    "editor: undo restores the old, unpaid material");
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
            check(!BlockEditor.apply(p, paid, new int[]{16, 16, 16, 16}, false, true), "editor: a stick is no material");
            p.setGameMode(mode);

            cursor(p, l, ex, y, ez + 2);
        } finally {
            p.setGameMode(mode);
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            p.setItemInHand(InteractionHand.OFF_HAND, offHand);
            p.teleportTo(l, home.x, home.y, home.z, p.getYRot(), p.getXRot());
        }
    }

    /** The cursor's sub-types pick a corner or an edge wherever the block is clicked, and its step is used. */
    private static void cursor(ServerPlayer p, ServerLevel l, int ex, int y, int cz) {
        place(l, ex + 2, y, cz);
        place(l, ex + 4, y, cz);
        ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
        ShapeToolItem.mode(tool, ShapeMode.AUTO);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);

        new CursorSettings(CornerEdits.Pick.CORNER, CornerEdits.Step.QUARTER).store(tool);
        click(p, ex + 2, y, cz, .45, .55, false);   // the middle of the block: by zone this would be the whole block
        assertCorners(l, ex + 2, y, cz, 1, 1, 1.25, 1);
        assertCorners(l, ex + 4, y, cz, 1, 1, 1, 1);
        new CursorSettings(CornerEdits.Pick.EDGE, CornerEdits.Step.EIGHTH).store(tool);
        click(p, ex + 2, y, cz, .5, .45, true);     // nearest edge: north, lowered by 1/8
        assertCorners(l, ex + 2, y, cz, .875, .875, 1.25, 1);
        new CursorSettings(CornerEdits.Pick.WHOLE, CornerEdits.Step.SIXTEENTH).store(tool);
        click(p, ex + 4, y, cz, .05, .05, false);
        assertCorners(l, ex + 4, y, cz, 1 + SIXTEENTH, 1 + SIXTEENTH, 1 + SIXTEENTH, 1 + SIXTEENTH);
        check(CursorSettings.read(tool).pick() == CornerEdits.Pick.WHOLE, "cursor: the sub-type is kept in the tool");
    }

    private static void click(ServerPlayer p, int x, int y, int z, double fx, double fz, boolean shift) {
        ShapeToolItem.shape(p, new BlockPos(x, y, z), new Vec3(x + fx, y + 1, z + fz), Direction.EAST, shift);
    }

    private DevBlockEditorTests() {}
}
