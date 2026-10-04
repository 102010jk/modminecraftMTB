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
 * Development-only: the Trail Shaper through its real item code paths (placing full blocks, jump and berm
 * presets, manual edits, plain ground, undo). Called from {@link DevTrailTests} on a prepared grass field at y.
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

        // a deck placed against an elevated anchor gets real posts down to the field
        var anchor = new BlockPos(x + 30, y + 3, bz);
        l.setBlock(anchor, Blocks.STONE.defaultBlockState(), 3);
        ItemStack deck = new ItemStack(ModBlocks.TRAIL_DECK.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, deck);
        deck.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(anchor).add(.5, 0, 0), Direction.EAST, anchor, false)));
        var deckPos = anchor.east();
        check(l.getBlockEntity(deckPos) instanceof TrailSurfaceEntity, "elevated deck is placed");
        assertCorners(l, deckPos.getX(), deckPos.getY(), deckPos.getZ(), 1, 1, 1, 1);
        check(l.getBlockState(deckPos.below()).is(ModBlocks.WOOD_SUPPORT.get()), "automatic post sits under elevated deck");
        check(l.getBlockState(new BlockPos(deckPos.getX(), y, deckPos.getZ())).is(ModBlocks.WOOD_SUPPORT.get()), "automatic post reaches the ground");
    }

    private DevSculptTests() {}
}
