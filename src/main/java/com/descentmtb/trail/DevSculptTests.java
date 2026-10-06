package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the Trail Shaper through its real item code paths (placing full blocks, jump and berm
 * presets, manual edits, plain ground, undo), then the berm, downhill, jump builder and block editor tests.
 * Called from {@link DevTrailTests} on a prepared grass field at y.
 * The key assertion: a preset changes ONLY the clicked block, never its neighbours.
 */
final class DevSculptTests {
    private static final double SIXTEENTH = 1.0 / 16;

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    /** Right-click the grass under (x, y, z) with the item in hand: places a block at (x, y, z). */
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
                check(Math.abs(c[i] - expected[i]) < 1e-9,
                        String.format("corner %d at %d,%d is %.4f (expected %.4f)", i, x, z, c[i], expected[i]));
            }
        }
    }

    /** Shapes the block at (x, y, z) with the given mode, clicking at the in-block position (fx, fz), facing east. */
    private static void shape(ServerPlayer p, ShapeMode mode, int x, int y, int z, double fx, double fz, boolean shift) {
        ItemStack shaper = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
        ShapeToolItem.mode(shaper, mode);
        p.setItemInHand(InteractionHand.MAIN_HAND, shaper);
        ShapeToolItem.shape(p, new BlockPos(x, y, z), new Vec3(x + fx, y + .5, z + fz), Direction.EAST, shift);
    }

    private static void shape(ServerPlayer p, ShapeMode mode, int x, int y, int z) {
        shape(p, mode, x, y, z, .5, .5, false);
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        int bz = z + 33;
        placing(p, l, x, y, bz);
        jumps(p, l, x + 3, y, bz);
        berms(p, l, x, y, bz);
        manual(p, l, x, y, bz);
        plainGround(p, l, x + 20, y, bz);
        undo(p, l, x + 22, y, bz);
        withOverlayAndDeck(p, l, x, y, bz);
        ramps(p, l, x, y, bz + 2);
        paidMaterial(p, l, x + 26, y, bz);
        copyAndPaste(p, l, x, y, bz + 2);
        DevBermTests.run(p, l, x, y, z);
        DevDownhillTests.run(p, l, x, y, z);
        DevJumpTests.run(p, l, x, y, z);
        DevLineTests.run(p, l, x, y, z);
        DevBlockEditorTests.run(p, l, x, y, z);
        DescentMtb.LOG.info("[sculpttest] ALL PASSED");
    }

    /** Placing Trail Dirt always gives a full block and never touches what is already there. */
    private static void placing(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, x + 3, y, bz);
        placeAt(p, x + 4, y, bz);
        placeAt(p, x + 5, y, bz);
        assertCorners(l, x + 3, y, bz, 1, 1, 1, 1);
        assertCorners(l, x + 4, y, bz, 1, 1, 1, 1);
        assertCorners(l, x + 5, y, bz, 1, 1, 1, 1);
    }

    /** Ramps continue the block behind them and leave their neighbours alone. */
    private static void jumps(ServerPlayer p, ServerLevel l, int ax, int y, int bz) {
        // a row A B C facing east; the grass behind A is level with the ground
        shape(p, ShapeMode.RAMP_HALF, ax, y, bz);
        assertCorners(l, ax, y, bz, 0, .5, 0, .5);
        assertCorners(l, ax + 1, y, bz, 1, 1, 1, 1);   // the neighbour is untouched
        assertCorners(l, ax + 2, y, bz, 1, 1, 1, 1);

        // the next block starts where A ended: a continuous kicker
        shape(p, ShapeMode.RAMP_HALF, ax + 1, y, bz);
        assertCorners(l, ax + 1, y, bz, .5, 1, .5, 1);
        assertCorners(l, ax, y, bz, 0, .5, 0, .5);     // A did not move
        assertCorners(l, ax + 2, y, bz, 1, 1, 1, 1);

        // a full ramp at 1.0 -> 2.0 needs a second layer
        shape(p, ShapeMode.RAMP_FULL, ax + 2, y, bz);
        assertCorners(l, ax + 2, y, bz, 1, 2, 1, 2);
        assertCorners(l, ax + 2, y + 1, bz, 0, 1, 0, 1);
        assertCorners(l, ax + 1, y, bz, .5, 1, .5, 1);

        // Shift: rises towards the player (west), continuing the grass behind (east)
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, ax + 5, y, bz);
        shape(p, ShapeMode.RAMP_HALF, ax + 5, y, bz, .5, .5, true);
        assertCorners(l, ax + 5, y, bz, .5, 0, .5, 0);

        // a drop starts at the lip of the kicker (C ends at 2.0) and falls away from the player
        placeAt(p, ax + 3, y, bz);
        shape(p, ShapeMode.DROP_HALF, ax + 3, y, bz);
        assertCorners(l, ax + 3, y + 1, bz, 1, .5, 1, .5);     // the surface 2.0 -> 1.5 sits in the layer above ...
        assertCorners(l, ax + 3, y, bz, 1, 1, 1, 1);           // ... on a solid block
        assertCorners(l, ax + 2, y, bz, 1, 2, 1, 2);
    }

    /** Banks follow the player's left / right; the corner bank peaks at the clicked corner. */
    private static void berms(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        for (int bx : new int[]{x + 14, x + 16, x + 18}) {
            placeAt(p, bx, y, bz);
        }
        shape(p, ShapeMode.BANK_LEFT_HALF, x + 14, y, bz);          // facing east, left is north
        assertCorners(l, x + 14, y, bz, 1.5, 1.5, 1, 1);
        shape(p, ShapeMode.BANK_RIGHT_FULL, x + 16, y, bz);         // right is south
        assertCorners(l, x + 16, y, bz, 1, 1, 2, 2);
        shape(p, ShapeMode.CORNER_BANK, x + 18, y, bz, .9, .9, false);
        assertCorners(l, x + 18, y, bz, 1, 1.25, 1.25, 1.5);
        check(l.getBlockState(new BlockPos(x + 15, y, bz)).isAir(), "a berm preset leaves the empty block beside it empty");
    }

    /** Manual clicks move 1/16, flatten averages, reset makes a full block. */
    private static void manual(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        int mx = x + 27;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, mx, y, bz);
        placeAt(p, mx + 1, y, bz);

        shape(p, ShapeMode.AUTO, mx, y, bz, .95, .5, false);     // east edge
        assertCorners(l, mx, y, bz, 1, 1 + SIXTEENTH, 1, 1 + SIXTEENTH);
        assertCorners(l, mx + 1, y, bz, 1, 1, 1, 1);              // the neighbour shares no vertex any more
        shape(p, ShapeMode.AUTO, mx, y, bz, .95, .5, true);       // Shift lowers it again
        assertCorners(l, mx, y, bz, 1, 1, 1, 1);

        shape(p, ShapeMode.AUTO, mx, y, bz, .1, .1, false);       // NW corner only
        assertCorners(l, mx, y, bz, 1 + SIXTEENTH, 1, 1, 1);
        // the whole surface is now above the block boundary: it moves to the layer above, on a solid block
        shape(p, ShapeMode.WHOLE, mx, y, bz);
        assertCorners(l, mx, y + 1, bz, 2 * SIXTEENTH, SIXTEENTH, SIXTEENTH, SIXTEENTH);
        assertCorners(l, mx, y, bz, 1, 1, 1, 1);
        // clicking the visible upper layer works on the same surface
        shape(p, ShapeMode.FLATTEN, mx, y + 1, bz);               // average 1.078 snaps to 17/16
        assertCorners(l, mx, y + 1, bz, SIXTEENTH, SIXTEENTH, SIXTEENTH, SIXTEENTH);
        shape(p, ShapeMode.RESET, mx, y + 1, bz);
        assertCorners(l, mx, y, bz, 1, 1, 1, 1);
        check(l.getBlockState(new BlockPos(mx, y + 1, bz)).isAir(), "reset removes the upper layer");
        assertCorners(l, mx + 1, y, bz, 1, 1, 1, 1);
    }

    /** A plain grass block becomes a shaped copy of itself; the plain blocks around it stay plain. */
    private static void plainGround(ServerPlayer p, ServerLevel l, int gx, int y, int bz) {
        BlockPos ground = new BlockPos(gx, y - 1, bz);
        check(l.getBlockState(ground).is(Blocks.GRASS_BLOCK), "test field is plain grass");
        shape(p, ShapeMode.AUTO, gx, y - 1, bz, .1, .1, true);
        assertCorners(l, gx, y - 1, bz, 1 - SIXTEENTH, 1, 1, 1);
        check(((TrailSurfaceEntity) l.getBlockEntity(ground)).getMaterial().is(Blocks.GRASS_BLOCK), "it keeps the grass look");
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos next = ground.relative(d);
            check(l.getBlockState(next).is(Blocks.GRASS_BLOCK) && l.getBlockEntity(next) == null,
                    "the grass to the " + d + " stays plain");
        }
    }

    /** An edit can be taken back with undo. */
    private static void undo(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, x, y, bz);
        shape(p, ShapeMode.RAMP_QUARTER, x, y, bz);
        assertCorners(l, x, y, bz, 0, .25, 0, .25);
        TrailEdit.undo(l, p);
        assertCorners(l, x, y, bz, 1, 1, 1, 1);
    }

    private static BlockState stateAt(ServerLevel l, int x, int y, int z) {
        return l.getBlockState(new BlockPos(x, y, z));
    }

    /** The Ramps tab: make a copycat ramp from a shaped block, tune it, link a run, undo. */
    private static void ramps(ServerPlayer p, ServerLevel l, int x, int y, int rz) {
        int rx = x + 3;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        for (int i = 0; i < 4; i++) {
            placeAt(p, rx + i, y, rz);
        }

        shape(p, ShapeMode.RAMP_MAKE, rx, y, rz);
        BlockState ramp = stateAt(l, rx, y, rz);
        check(ramp.is(ModBlocks.RAMP.get()), "Make ramp turns the shaped block into a copycat ramp");
        check(ramp.getValue(RampBlock.FACING) == Direction.EAST, "the ramp rises away from the player (east)");
        check(ramp.getValue(RampBlock.START) == 0 && ramp.getValue(RampBlock.END) == 16
                && ramp.getValue(RampBlock.PROFILE) == RampBlock.Profile.CONCAVE, "a new ramp is a kicker from 0 to 16");
        check(l.getBlockEntity(new BlockPos(rx, y, rz)) instanceof RampBlockEntity, "the ramp has its copycat material");
        shape(p, ShapeMode.RAMP_MAKE, rx + 3, y, rz, .5, .5, true);
        check(stateAt(l, rx + 3, y, rz).getValue(RampBlock.FACING) == Direction.WEST, "Shift makes the ramp face the player");

        shape(p, ShapeMode.RAMP_STEEPNESS, rx, y, rz, .5, .5, true);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.END) == 14, "Shift + steepness lowers the end by 2/16");
        shape(p, ShapeMode.RAMP_START, rx, y, rz);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.START) == 2, "start height goes up by 2/16");
        shape(p, ShapeMode.RAMP_PROFILE, rx, y, rz);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.PROFILE) == RampBlock.Profile.CONVEX, "profile cycles kicker to roller");
        shape(p, ShapeMode.RAMP_PROFILE, rx, y, rz, .5, .5, true);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.PROFILE) == RampBlock.Profile.CONCAVE, "Shift cycles the profile back");
        shape(p, ShapeMode.RAMP_ROTATE, rx, y, rz);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.FACING) == Direction.SOUTH, "rotate turns clockwise (east to south)");
        TrailEdit.undo(l, p);
        check(stateAt(l, rx, y, rz).getValue(RampBlock.FACING) == Direction.EAST, "a ramp edit can be undone");
        check(l.getBlockEntity(new BlockPos(rx, y, rz)) instanceof RampBlockEntity, "undo keeps the ramp block entity");

        // three ramps in a row become one continuous slope
        shape(p, ShapeMode.RAMP_START, rx, y, rz, .5, .5, true);   // back to 0
        shape(p, ShapeMode.RAMP_STEEPNESS, rx, y, rz);              // back to 16
        shape(p, ShapeMode.RAMP_MAKE, rx + 1, y, rz);
        shape(p, ShapeMode.RAMP_MAKE, rx + 2, y, rz);
        shape(p, ShapeMode.RAMP_LINK, rx, y, rz);
        shape(p, ShapeMode.RAMP_LINK, rx + 2, y, rz);
        BlockState first = stateAt(l, rx, y, rz), second = stateAt(l, rx + 1, y, rz), third = stateAt(l, rx + 2, y, rz);
        check(first.getValue(RampBlock.START) == 0 && third.getValue(RampBlock.END) == 16, "link spans the whole run");
        check(first.getValue(RampBlock.END) == second.getValue(RampBlock.START)
                && second.getValue(RampBlock.END) == third.getValue(RampBlock.START), "linked ramps meet end to start");
        check(second.getValue(RampBlock.PROFILE) == RampBlock.Profile.LINEAR, "the pieces of a linked run are straight");
    }

    /**
     * A material paid for on a shaped block (right-click with a block) is refunded exactly once: an edit that keeps
     * the block keeps it paid, an edit that removes it hands the item to the player (and the block drops nothing),
     * and undo brings it back unpaid, so breaking it afterwards refunds nothing again.
     */
    private static void paidMaterial(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        BlockPos pos = new BlockPos(x, y, bz);
        var stone = net.minecraft.world.item.Items.STONE;
        var nearby = new net.minecraft.world.phys.AABB(pos).inflate(2);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, x, y, bz);
        check(l.getBlockEntity(pos) instanceof TrailSurfaceEntity, "paid material: a shaped block to pay on");
        ((TrailSurfaceEntity) l.getBlockEntity(pos)).setMaterial(Blocks.STONE.defaultBlockState(), true);
        int before = p.getInventory().countItem(stone);

        shape(p, ShapeMode.RAMP_QUARTER, x, y, bz);
        check(l.getBlockEntity(pos) instanceof TrailSurfaceEntity shaped && shaped.isConsumed(), "an edit that keeps the block keeps it paid");

        TrailEdit.apply(l, p, java.util.Map.of(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState())), false);
        check(p.getInventory().countItem(stone) == before + 1, "removing a paid block refunds the material once");
        check(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby).isEmpty(), "the removed block drops nothing on top of the refund");

        TrailEdit.undo(l, p);
        check(l.getBlockEntity(pos) instanceof TrailSurfaceEntity shaped && !shaped.isConsumed(), "undo brings the block back unpaid");
        l.removeBlock(pos, false);
        check(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, nearby).isEmpty(), "breaking the restored block refunds nothing again");
    }

    /** One right-click of the copy mode with the given tool. */
    private static void copyClick(ServerPlayer p, ItemStack tool, int x, int y, int z, boolean shift) {
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        ShapeToolItem.shape(p, new BlockPos(x, y, z), new Vec3(x + .5, y + .5, z + .5), Direction.EAST, shift);
    }

    /** Copy two blocks (a shaped ramp and a copycat ramp), paste them, paste them turned, undo the paste. */
    private static void copyAndPaste(ServerPlayer p, ServerLevel l, int x, int y, int rz) {
        int sx = x + 12;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, sx, y, rz);
        placeAt(p, sx + 1, y, rz);
        shape(p, ShapeMode.RAMP_HALF, sx, y, rz);
        shape(p, ShapeMode.RAMP_MAKE, sx + 1, y, rz);

        ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
        ShapeToolItem.mode(tool, ShapeMode.COPY);
        copyClick(p, tool, sx, y, rz, false);
        check(ShapeToolItem.data(tool).contains(ShapeClipboard.FIRST_TAG), "copy: the first corner is remembered");
        copyClick(p, tool, sx + 1, y, rz, false);
        check(java.util.Arrays.equals(ShapeToolItem.data(tool).getIntArray(ShapeClipboard.SIZE_TAG), new int[]{2, 5, 1}),
                "copy: two corners capture a 2 x 5 x 1 box (4 blocks of headroom)");

        copyClick(p, tool, sx + 5, y - 1, rz, false);
        assertCorners(l, sx + 5, y, rz, 0, .5, 0, .5);
        check(stateAt(l, sx + 6, y, rz).is(ModBlocks.RAMP.get())
                && stateAt(l, sx + 6, y, rz).getValue(RampBlock.FACING) == Direction.EAST, "paste: the shaped block and the ramp land in order");
        check(l.getBlockEntity(new BlockPos(sx + 6, y, rz)) instanceof RampBlockEntity, "paste: the ramp keeps its material");

        ShapeClipboard.rotate(p, tool);
        check(java.util.Arrays.equals(ShapeToolItem.data(tool).getIntArray(ShapeClipboard.SIZE_TAG), new int[]{1, 5, 2}),
                "rotate: the footprint turns from 2 x 1 to 1 x 2");
        copyClick(p, tool, sx + 8, y - 1, rz, false);
        assertCorners(l, sx + 8, y, rz, 0, 0, .5, .5);
        check(stateAt(l, sx + 8, y, rz + 1).getValue(RampBlock.FACING) == Direction.SOUTH, "rotate: the ramp now rises to the south");

        TrailEdit.undo(l, p);
        check(stateAt(l, sx + 8, y, rz).isAir() && stateAt(l, sx + 8, y, rz + 1).isAir(), "one undo takes the whole paste back");
        assertCorners(l, sx + 5, y, rz, 0, .5, 0, .5);

        copyClick(p, tool, sx, y, rz, true);
        check(ShapeToolItem.data(tool).contains(ShapeClipboard.FIRST_TAG) && !ShapeToolItem.data(tool).contains(ShapeClipboard.SIZE_TAG),
                "Shift + click on a block starts a new selection");
    }

    /** Roots follow the plane and survive shaping; an elevated deck gets posts down to the ground. */
    private static void withOverlayAndDeck(ServerPlayer p, ServerLevel l, int x, int y, int bz) {
        var sloped = new BlockPos(x + 24, y, bz);
        l.setBlock(sloped, ModBlocks.TRAIL_SURFACE.get().defaultBlockState(), 3);
        ((TrailSurfaceEntity) l.getBlockEntity(sloped)).setShape(new double[]{.3, .5, .3, .5}, false);

        // placing next to a shaped block makes a plain full block and does not move the shaped one
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_DIRT.get()));
        placeAt(p, x + 25, y, bz);
        assertCorners(l, x + 25, y, bz, 1, 1, 1, 1);
        assertCorners(l, x + 24, y, bz, .3, .5, .3, .5);

        // only the clicked corner moves, and the neighbour does not follow
        shape(p, ShapeMode.AUTO, x + 24, y, bz, .9, .9, false);
        assertCorners(l, x + 24, y, bz, .3, .5, .3, .5 + SIXTEENTH);
        assertCorners(l, x + 25, y, bz, 1, 1, 1, 1);

        ItemStack roots = new ItemStack(ModBlocks.TRAIL_ROOTS.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, roots);
        roots.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(sloped), Direction.UP, sloped, false)));
        var decorated = (TrailSurfaceEntity) l.getBlockEntity(sloped);
        check(decorated.overlay() == 1, "roots are stored on the shaped surface");
        double rz = .51 + .08 * Math.sin(2.5 + .51);
        check(decorated.height(.5, rz) > decorated.rawHeight(.5, rz) + .09, "roots follow the banked plane and lift the tyre contact");
        var saved = decorated.saveWithoutMetadata(l.registryAccess());
        var restored = new TrailSurfaceEntity(sloped, decorated.getBlockState());
        restored.loadWithComponents(saved, l.registryAccess());
        check(restored.overlay() == 1 && Math.abs(restored.height(.5, rz) - decorated.height(.5, rz)) < 1e-9,
                "surface overlay survives save and load");
        shape(p, ShapeMode.AUTO, x + 24, y, bz, .9, .9, true);
        check(((TrailSurfaceEntity) l.getBlockEntity(sloped)).overlay() == 1, "shaping preserves roots");

        // a deck placed against an elevated anchor stands on its own: no posts are built, the wood is the off-hand planks
        var anchor = new BlockPos(x + 30, y + 3, bz);
        l.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        ItemStack deck = new ItemStack(ModBlocks.TRAIL_DECK.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, deck);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.SPRUCE_PLANKS));
        deck.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(anchor).add(.5, 0, 0), Direction.EAST, anchor, false)));
        var deckPos = anchor.east();
        check(l.getBlockEntity(deckPos) instanceof TrailSurfaceEntity, "elevated deck is placed");
        assertCorners(l, deckPos.getX(), deckPos.getY(), deckPos.getZ(), 1, 1, 1, 1);
        check(((TrailSurfaceEntity) l.getBlockEntity(deckPos)).getMaterial().is(Blocks.SPRUCE_PLANKS), "the deck takes the wood of the off-hand planks");
        check(l.getBlockState(deckPos.below()).isAir() && l.getBlockState(new BlockPos(deckPos.getX(), y, deckPos.getZ())).isAir(),
                "no automatic posts under the deck");

        // shaping the deck builds no posts either, and a ramp made from it keeps the spruce
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        shape(p, ShapeMode.WHOLE, deckPos.getX(), deckPos.getY(), deckPos.getZ(), .5, .5, true);
        check(l.getBlockState(deckPos.below()).isAir(), "shaping a deck does not add posts");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.TRAIL_SHOVEL.get()));
        ShapeToolItem.mode(p.getMainHandItem(), ShapeMode.RAMP_MAKE);
        ShapeToolItem.shape(p, deckPos, Vec3.atCenterOf(deckPos), Direction.EAST, false);
        check(l.getBlockEntity(deckPos) instanceof com.descentmtb.ramp.RampBlockEntity ramp && ramp.getMaterial().is(Blocks.SPRUCE_PLANKS),
                "Make ramp keeps the wood of a deck");
        // an unrelated plank block works too, and a plain oak default needs no off-hand
        var planksPos = deckPos.south();
        l.setBlock(planksPos, Blocks.BIRCH_PLANKS.defaultBlockState(), 3);
        ShapeToolItem.shape(p, planksPos, Vec3.atCenterOf(planksPos), Direction.EAST, false);
        check(l.getBlockEntity(planksPos) instanceof com.descentmtb.ramp.RampBlockEntity ramp && ramp.getMaterial().is(Blocks.BIRCH_PLANKS),
                "Make ramp keeps the wood of a planks block");
    }

    private DevSculptTests() {}
}
