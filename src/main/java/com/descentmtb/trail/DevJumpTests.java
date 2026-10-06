package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.trail.JumpProfiles.Params;
import com.descentmtb.trail.JumpProfiles.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the jump builder of the Trail Shaper through its server path ({@link JumpBuilder#build}, what the
 * jump screen's Build button ends in) on a fresh grass field: the reach check, a 3 block kicker whose blocks carry
 * exactly the profile's heights and leave the columns beside it alone, undo, a 4 m step-up stacked over several
 * layers, and the real blocks of a table top and of a kicker with a landing ridden with the headless bike
 * ({@link DevRideSim}). The player is moved next to the field for the reach check and put back afterwards.
 * Called from {@link DevSculptTests}.
 */
final class DevJumpTests {
    private static final int LENGTH = 70, DEPTH = 16;

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        int ox = x + 60, oz = z - 30;
        Vec3 home = p.position();
        ItemStack held = p.getMainHandItem();
        forceChunks(l, ox, oz, true);
        try {
            layField(l, ox, oz, y);
            ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
            ShapeToolItem.mode(tool, ShapeMode.JUMP_BUILD);
            p.setItemInHand(InteractionHand.MAIN_HAND, tool);

            Params kicker = new Params(Type.KICKER, 3, 3, 1.5, 35, 3, 5);
            BlockPos start = new BlockPos(ox + 25, y - 1, oz + 8);   // the grass block the jump starts on
            p.teleportTo(l, ox + 60, y + 20, oz + 8, 0, 0);
            check(!JumpBuilder.build(p, tool, start, Direction.EAST, kicker) && surfaceBlocks(l, ox, oz, y) == 0,
                    "jump: a block out of reach is refused");
            check(JumpBuilder.read(tool).equals(kicker), "jump: the settings are kept in the tool even when the build is refused");

            p.teleportTo(l, ox + 22.5, y, oz + 8.5, -90, 0);
            check(JumpBuilder.build(p, tool, start, Direction.EAST, kicker), "jump: the 3 block kicker is built");
            assertProfile(l, JumpBuilder.layout(start, Direction.EAST, kicker), "kicker");
            for (int dx = 0; dx < 3; dx++) {
                for (int side : new int[]{-2, 2}) {
                    BlockPos beside = new BlockPos(ox + 25 + dx, y - 1, oz + 8 + side);
                    check(l.getBlockState(beside).is(Blocks.GRASS_BLOCK) && l.getBlockState(beside.above()).isAir(),
                            "jump: the column beside the kicker stays grass (vertical sides) at " + beside);
                }
            }
            check(l.getBlockState(new BlockPos(ox + 28, y - 1, oz + 8)).is(Blocks.GRASS_BLOCK)
                    && l.getBlockState(new BlockPos(ox + 28, y, oz + 8)).isAir(), "jump: the ground after the lip is untouched");
            check(l.getBlockState(new BlockPos(ox + 24, y - 1, oz + 8)).is(Blocks.GRASS_BLOCK), "jump: the ground before it is untouched");

            TrailEdit.undo(l, p);
            check(surfaceBlocks(l, ox, oz, y) == 0 && l.getBlockState(start).is(Blocks.GRASS_BLOCK), "jump: one undo takes the whole jump back");

            // a tall step-up is stacked over several layers like every other shaped surface
            Params stepUp = new Params(Type.STEP_UP, 3, 1, 4, 30, 3, 5);
            check(JumpBuilder.build(p, tool, start, Direction.EAST, stepUp), "jump: the 4 m step-up is built");
            assertProfile(l, JumpBuilder.layout(start, Direction.EAST, stepUp), "step-up");
            int platform = ox + 25 + stepUp.length() + JumpProfiles.STEP_UP_FACE;
            check(l.getBlockEntity(new BlockPos(platform, y + 3, oz + 8)) instanceof TrailSurfaceEntity top
                    && java.util.Arrays.equals(top.corners(), new double[]{1, 1, 1, 1}), "jump: the platform's surface is the shaped block at y+3");
            for (int by = y; by <= y + 2; by++) {
                check(!l.getBlockState(new BlockPos(platform, by, oz + 8)).isAir(), "jump: the platform is filled solid below at y+" + (by - y));
            }
            TrailEdit.undo(l, p);
            check(surfaceBlocks(l, ox, oz, y) == 0, "jump: the step-up is undone");

            // ride the real blocks: a table top, then a kicker with a landing
            Params table = new Params(Type.TABLE, 3, 3, 1.25, 30, 4, 6);
            check(JumpBuilder.build(p, tool, start, Direction.EAST, table), "jump: the table top is built");
            ride(l, ox, oz, y, 9, "table top", true);
            TrailEdit.undo(l, p);

            Params landing = new Params(Type.LANDING, 6, 3, 1.5, 35, 3, 5);
            check(JumpBuilder.build(p, tool, start, Direction.EAST, kicker), "jump: the kicker is built again");
            BlockPos landingStart = start.east(kicker.total() + 3);
            p.teleportTo(l, ox + 30.5, y, oz + 8.5, -90, 0);
            check(JumpBuilder.build(p, tool, landingStart, Direction.EAST, landing), "jump: a landing is built 3 m after the lip");
            assertProfile(l, JumpBuilder.layout(landingStart, Direction.EAST, landing), "landing");
            ride(l, ox, oz, y, 14, "kicker 3 bl / 1.5 m over a 3 m gap onto a landing", false);
            TrailEdit.undo(l, p);
            TrailEdit.undo(l, p);
            check(surfaceBlocks(l, ox, oz, y) == 0, "jump: kicker and landing are undone");
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            p.teleportTo(l, home.x, home.y, home.z, p.getYRot(), p.getXRot());
            forceChunks(l, ox, oz, false);
        }
    }

    /** Every column of the jump carries exactly the profile's corner heights (so neighbours share their edges). */
    private static void assertProfile(ServerLevel l, JumpProfiles.Layout layout, String name) {
        int[] box = layout.bounds();
        int columns = 0;
        for (int bx = box[0]; bx <= box[2]; bx++) {
            for (int bz = box[1]; bz <= box[3]; bz++) {
                // read at the layer that holds the lowest corner: below a surface higher than one block there is
                // plain dirt fill (like under every SurfacePlans surface), which a read at the start height would find
                double lowest = Double.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    lowest = Math.min(lowest, layout.height(bx + i % 2, bz + i / 2));
                }
                int surfaceY = (int) Math.floor(lowest + 1e-6);
                ColumnEditor.Column column = ColumnEditor.read(l, bx, bz, surfaceY);
                if (column == null) {
                    throw new IllegalStateException(name + ": nothing to stand on at " + bx + "," + bz);
                }
                int top = (int) Math.ceil(Math.max(Math.max(column.abs()[0], column.abs()[1]), Math.max(column.abs()[2], column.abs()[3])) - 1e-6) - 1;
                if (!(l.getBlockEntity(new BlockPos(bx, top, bz)) instanceof TrailSurfaceEntity)) {
                    throw new IllegalStateException(name + ": the surface of " + bx + "," + bz + " is not a shaped block");
                }
                for (int i = 0; i < 4; i++) {
                    double expected = layout.height(bx + i % 2, bz + i / 2);
                    if (Math.abs(column.abs()[i] - expected) > 1e-6) {
                        throw new IllegalStateException(String.format("%s: corner %d of %d,%d is %.4f, the profile says %.4f",
                                name, i, bx, bz, column.abs()[i], expected));
                    }
                }
                columns++;
            }
        }
        check(columns == layout.params().total() * layout.params().width(), name + ": " + columns + " columns carry the profile exactly");
    }

    /** Rides the field's centre line from west to east over whatever stands on it. */
    private static void ride(ServerLevel l, int ox, int oz, int y, double target, String name, boolean strict) {
        double[][] path = new double[(LENGTH - 6) * 2][];
        for (int i = 0; i < path.length; i++) {
            path[i] = new double[]{ox + 2 + i * .5, oz + 8.5};
        }
        var result = DevRideSim.ride(l, path, false, y + .3, 20, target, BikeType.ENDURO, null);
        DescentMtb.LOG.info("[sculpttest] jump ride over the {} on the real blocks: {}", name, result.summary());
        if (strict) {
            check(!result.bailed() && result.laps() > .95, "jump: the " + name + " can be ridden without a bail");
        } else if (result.bailed()) {
            DescentMtb.LOG.warn("[sculpttest] jump ride over the {} bailed ({}); the headless JumpRideTest covers the speeds", name, result.why());
        }
    }

    private static void layField(ServerLevel l, int ox, int oz, int y) {
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                l.setBlock(new BlockPos(bx, y - 2, bz), Blocks.DIRT.defaultBlockState(), 2);
                l.setBlock(new BlockPos(bx, y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int by = y; by <= y + 8; by++) {
                    l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static int surfaceBlocks(ServerLevel l, int ox, int oz, int y) {
        int count = 0;
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                for (int by = y - 2; by <= y + 8; by++) {
                    if (l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void forceChunks(ServerLevel l, int ox, int oz, boolean force) {
        for (int cx = ox >> 4; cx <= (ox + LENGTH) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + DEPTH) >> 4; cz++) {
                l.setChunkForced(cx, cz, force);
            }
        }
    }

    private DevJumpTests() {}
}
