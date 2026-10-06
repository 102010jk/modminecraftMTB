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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the two line tools of the Trail Shaper through their item code path (two right-clicks) on a fresh
 * grass field: the straight line (a rising line over air with the exact grade, undo, a wooden bridge from the off-hand block
 * paid for in survival, refused without enough planks, too steep) and the path clearing (a fake tree with its leaves taken
 * whole, a bush and a snow layer, while a cabin of logs, planks and the grass itself stay; the survival length limit and
 * undo). Called from {@link DevSculptTests}.
 */
final class DevLineTests {
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
        ItemStack held = p.getMainHandItem(), offHand = p.getOffhandItem();
        GameType mode = p.gameMode.getGameModeForPlayer();
        forceChunks(l, ox, oz, true);
        try {
            p.setGameMode(GameType.CREATIVE);
            layField(l, ox, oz, y);
            p.teleportTo(l, ox + 3.5, y, oz + 4.5, -90, 0);
            ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, tool);
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            straightLine(p, l, tool, ox, oz, y);
            p.teleportTo(l, ox + 3.5, y, oz + 4.5, -90, 0);
            clearPath(p, l, tool, ox, oz, y);
        } finally {
            p.setGameMode(mode);
            p.setItemInHand(InteractionHand.MAIN_HAND, held);
            p.setItemInHand(InteractionHand.OFF_HAND, offHand);
            p.teleportTo(l, home.x, home.y, home.z, p.getYRot(), p.getXRot());
            forceChunks(l, ox, oz, false);
        }
    }

    private static void click(ServerPlayer p, BlockPos pos, boolean shift) {
        ShapeToolItem.shape(p, pos, Vec3.atCenterOf(pos), Direction.EAST, shift);
    }

    // ---- the straight line -----------------------------------------------------------------------------------------

    private static void straightLine(ServerPlayer p, ServerLevel l, ItemStack tool, int ox, int oz, int y) {
        ShapeToolItem.mode(tool, ShapeMode.STRAIGHT_LINE);
        new LineSettings(3).store(tool);
        // from the grass to the top of a stone pillar four blocks higher, twenty blocks away: a ramp over air
        BlockPos a = new BlockPos(ox + 5, y - 1, oz + 8), b = new BlockPos(ox + 25, y + 3, oz + 8);
        for (int by = y; by <= y + 3; by++) {
            l.setBlock(new BlockPos(b.getX(), by, b.getZ()), Blocks.STONE.defaultBlockState(), 2);
        }
        click(p, a, false);
        check(LinePoints.first(tool) != null && LinePoints.first(tool).equals(a) && surfaceBlocks(l, ox, oz, y) == 0, "line: the first click only places point A");
        click(p, a.east(), true);
        check(LinePoints.first(tool) == null, "line: Shift + click forgets point A");
        click(p, a, false);
        click(p, b, false);
        check(LinePoints.first(tool) == null && surfaceBlocks(l, ox, oz, y) > 0, "line: the second click builds the line and forgets point A");
        StraightLines.Layout layout = new StraightLines.Layout(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), 3);
        int columns = assertProfile(l, layout, "line");
        check(Math.abs(layout.height(a.getX() + .5, a.getZ() + .5) - y) < 1e-9 && Math.abs(layout.height(b.getX() + .5, b.getZ() + .5) - (y + 4)) < 1e-9,
                "line: it runs from the top of A to the top of B");
        double slope = layout.slope();
        for (int bx = a.getX(); bx < b.getX(); bx++) {
            double here = topCorner(l, bx, a.getZ(), y), next = topCorner(l, bx + 1, a.getZ(), y);
            check(Math.abs((next - here) - slope) < 1e-6, "line: the grade is constant at x=" + bx + " (" + (next - here) + ")");
        }
        int mid = ox + 15;
        double[] midCorners = {layout.height(mid, oz + 8), layout.height(mid + 1, oz + 8), layout.height(mid, oz + 9), layout.height(mid + 1, oz + 9)};
        int bottom = ColumnShaper.layers(midCorners, false).bottom();
        check(bottom > y && l.getBlockState(new BlockPos(mid, bottom - 1, oz + 8)).isAir(), "line: over open ground nothing supports it (it is a bridge)");
        check(l.getBlockState(new BlockPos(ox + 40, y - 1, oz + 8)).is(Blocks.GRASS_BLOCK) && l.getBlockState(new BlockPos(ox + 15, y - 1, oz + 12)).is(Blocks.GRASS_BLOCK),
                "line: the ground beside it is untouched");
        TrailEdit.undo(l, p);
        check(surfaceBlocks(l, ox, oz, y) == 0 && l.getBlockState(b).is(Blocks.STONE), "line: one undo takes the whole line back");

        // a width of one block, and a line too steep or too short
        new LineSettings(1).store(tool);
        click(p, a, false);
        click(p, a.offset(10, 0, 0), false);
        check(countColumns(l, ox, oz, y) == 11, "line: a width of 1 is one column wide (" + countColumns(l, ox, oz, y) + " columns)");
        TrailEdit.undo(l, p);
        new LineSettings(3).store(tool);
        click(p, a, false);
        click(p, a.offset(10, 12, 0), false);
        check(surfaceBlocks(l, ox, oz, y) == 0, "line: a line steeper than 45 degrees is refused");
        click(p, a, false);
        click(p, a.east(), false);
        check(surfaceBlocks(l, ox, oz, y) == 0, "line: two neighbouring blocks are too close together");

        // survival: trail dirt for every block, and the block in the off-hand is the material, one of it per column
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(ModBlocks.TRAIL_DIRT.get(), 400));
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.OAK_PLANKS, 3));
        click(p, a, false);
        click(p, b, false);
        check(surfaceBlocks(l, ox, oz, y) == 0 && p.getInventory().countItem(Items.OAK_PLANKS) == 3, "line: with too few planks nothing is built and nothing is taken");

        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.OAK_PLANKS, 64));
        p.getInventory().add(new ItemStack(Blocks.OAK_PLANKS, 64));
        int planksBefore = p.getInventory().countItem(Items.OAK_PLANKS), dirtBefore = p.getInventory().countItem(ModBlocks.TRAIL_DIRT.get().asItem());
        click(p, a, false);
        click(p, b, false);
        assertProfile(l, layout, "bridge");
        check(l.getBlockEntity(new BlockPos(mid, bottom, oz + 8)) instanceof TrailSurfaceEntity s && s.getMaterial().is(Blocks.OAK_PLANKS),
                "line: the bridge is made of the planks in the off-hand");
        check(planksBefore - p.getInventory().countItem(Items.OAK_PLANKS) == columns, "line: one plank per column was taken (" + columns + ")");
        check(dirtBefore - p.getInventory().countItem(ModBlocks.TRAIL_DIRT.get().asItem()) > 0, "line: trail dirt was taken for the blocks");
        TrailEdit.undo(l, p);
        check(p.getInventory().countItem(Items.OAK_PLANKS) == planksBefore && p.getInventory().countItem(ModBlocks.TRAIL_DIRT.get().asItem()) == dirtBefore
                        && surfaceBlocks(l, ox, oz, y) == 0, "line: undo hands the planks and the dirt back");
        p.setGameMode(GameType.CREATIVE);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Blocks.STONE_BRICKS));
        click(p, a, false);
        click(p, b, false);
        check(l.getBlockEntity(new BlockPos(mid, bottom, oz + 8)) instanceof TrailSurfaceEntity s && s.getMaterial().is(Blocks.STONE_BRICKS),
                "line: creative builds it from the off-hand block too");
        TrailEdit.undo(l, p);
        check(surfaceBlocks(l, ox, oz, y) == 0, "line: the stone line is undone");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int by = y; by <= y + 3; by++) {
            l.setBlock(new BlockPos(b.getX(), by, b.getZ()), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Height of the north-west corner of the highest shaped block of the column. */
    private static double topCorner(ServerLevel l, int bx, int bz, int y) {
        for (int by = y + 10; by >= y - 2; by--) {
            if (l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity shaped) {
                return ColumnShaper.absolute(by, shaped.corners())[0];
            }
        }
        throw new IllegalStateException("no shaped block at " + bx + "," + bz);
    }

    /** Every column of the line carries exactly the plane's corner heights, as a shaped block on top. @return the columns */
    private static int assertProfile(ServerLevel l, StraightLines.Layout layout, String name) {
        int[] box = layout.bounds(0);
        int columns = 0;
        for (int bx = box[0]; bx <= box[2]; bx++) {
            for (int bz = box[1]; bz <= box[3]; bz++) {
                if (!layout.contains(bx + .5, bz + .5)) {
                    continue;
                }
                double lowest = Double.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    lowest = Math.min(lowest, layout.height(bx + i % 2, bz + i / 2));
                }
                ColumnEditor.Column column = ColumnEditor.read(l, bx, bz, (int) Math.floor(lowest + 1e-6));
                if (column == null) {
                    throw new IllegalStateException(name + ": nothing to stand on at " + bx + "," + bz);
                }
                for (int i = 0; i < 4; i++) {
                    double expected = layout.height(bx + i % 2, bz + i / 2);
                    if (Math.abs(column.abs()[i] - expected) > 1e-6) {
                        throw new IllegalStateException(String.format("%s: corner %d of %d,%d is %.4f, the line says %.4f", name, i, bx, bz, column.abs()[i], expected));
                    }
                }
                columns++;
            }
        }
        check(columns > 0, name + ": " + columns + " columns carry the line exactly");
        return columns;
    }

    // ---- the path clearing -----------------------------------------------------------------------------------------

    private static void clearPath(ServerPlayer p, ServerLevel l, ItemStack tool, int ox, int oz, int y) {
        ShapeToolItem.mode(tool, ShapeMode.CLEAR_PATH);
        new LineSettings(3).store(tool);
        int cz = oz + 8;
        BlockPos a = new BlockPos(ox + 30, y - 1, cz), b = new BlockPos(ox + 50, y - 1, cz);
        int treeX = ox + 38;
        tree(l, treeX, y, cz);                                           // a trunk in the middle of the path, its crown wider than the corridor
        tree(l, ox + 44, y, cz + 7);                                     // a tree beside the corridor: its trunk is out of it
        l.setBlock(new BlockPos(ox + 33, y, cz + 1), Blocks.SHORT_GRASS.defaultBlockState(), 2);
        l.setBlock(new BlockPos(ox + 34, y, cz - 1), Blocks.POPPY.defaultBlockState(), 2);
        l.setBlock(new BlockPos(ox + 35, y, cz), Blocks.SNOW.defaultBlockState(), 2);
        l.setBlock(new BlockPos(ox + 42, y, cz + 2), Blocks.OAK_PLANKS.defaultBlockState(), 2);     // placed by a player: stays
        for (int by = y; by <= y + 2; by++) {
            l.setBlock(new BlockPos(ox + 46, by, cz - 1), Blocks.OAK_LOG.defaultBlockState(), 2);   // a log pillar without leaves: stays
        }
        l.setBlock(new BlockPos(ox + 47, y, cz + 1), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2);   // a hedge: leaves go
        int logs = count(l, ox, oz, y, Blocks.OAK_LOG), leaves = count(l, ox, oz, y, Blocks.OAK_LEAVES);
        int lost = leafCount() + 1;   // the crown of the tree in the path, and the hedge

        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockPos farB = new BlockPos(ox + 68, y - 1, cz);
        click(p, a, false);
        click(p, b, false);
        check(l.getBlockState(new BlockPos(treeX, y, cz)).isAir() && l.getBlockState(new BlockPos(treeX, y + 3, cz)).isAir(), "clear: the trunk in the path is gone");
        check(count(l, ox, oz, y, Blocks.OAK_LEAVES) == leaves - lost, "clear: all the leaves of that tree are gone, even beyond the corridor");
        check(l.getBlockState(new BlockPos(ox + 44, y, cz + 7)).is(Blocks.OAK_LOG), "clear: a tree outside the corridor stays");
        check(l.getBlockState(new BlockPos(ox + 33, y, cz + 1)).isAir() && l.getBlockState(new BlockPos(ox + 34, y, cz - 1)).isAir()
                && l.getBlockState(new BlockPos(ox + 35, y, cz)).isAir(), "clear: grass, a flower and a snow layer are gone");
        check(l.getBlockState(new BlockPos(ox + 47, y, cz + 1)).isAir(), "clear: leaves a player placed go as well");
        check(l.getBlockState(new BlockPos(ox + 42, y, cz + 2)).is(Blocks.OAK_PLANKS) && l.getBlockState(new BlockPos(ox + 46, y + 2, cz - 1)).is(Blocks.OAK_LOG),
                "clear: planks and a log pillar a player built stay");
        check(l.getBlockState(new BlockPos(treeX, y - 1, cz)).is(Blocks.GRASS_BLOCK) && l.getBlockState(new BlockPos(ox + 40, y - 1, cz)).is(Blocks.GRASS_BLOCK),
                "clear: the ground itself is never touched");
        check(p.getInventory().countItem(ModBlocks.TRAIL_DIRT.get().asItem()) == 0 && p.getInventory().countItem(Items.OAK_LOG) == 0,
                "clear: it is free in survival (nothing taken, nothing handed out)");

        TrailEdit.undo(l, p);
        check(count(l, ox, oz, y, Blocks.OAK_LOG) == logs && count(l, ox, oz, y, Blocks.OAK_LEAVES) == leaves
                && l.getBlockState(new BlockPos(ox + 33, y, cz + 1)).is(Blocks.SHORT_GRASS), "clear: one undo plants everything back");

        // the player of the dev tests is an operator, so even in survival the limit of a survival player (32 m, see
        // StraightLinesTest) does not apply: 38 m are cleared at once
        p.setGameMode(GameType.CREATIVE);
        click(p, a, false);
        click(p, farB, false);
        check(l.getBlockState(new BlockPos(ox + 44, y, cz + 7)).is(Blocks.OAK_LOG) && l.getBlockState(new BlockPos(treeX, y, cz)).isAir(),
                "clear: a creative player clears 38 m at once");
        TrailEdit.undo(l, p);
        check(count(l, ox, oz, y, Blocks.OAK_LOG) == logs, "clear: the long clearing is undone");
    }

    /** A trunk of four logs on the grass at (x, y, z) with natural leaves around its top: each leaf knows its distance to the nearest log. */
    private static void tree(ServerLevel l, int x, int y, int z) {
        for (int by = y; by < y + 4; by++) {
            l.setBlock(new BlockPos(x, by, z), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int by = y + 2; by <= y + 6; by++) {
                    int distance = Math.abs(dx) + Math.abs(dz) + Math.max(0, by - (y + 3));
                    BlockPos pos = new BlockPos(x + dx, by, z + dz);
                    if (distance >= 1 && distance <= 3 && l.getBlockState(pos).isAir()) {
                        l.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, distance), 2);
                    }
                }
            }
        }
    }

    /** How many leaf blocks {@link #tree} places. */
    private static int leafCount() {
        int count = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int by = 2; by <= 6; by++) {
                    int distance = Math.abs(dx) + Math.abs(dz) + Math.max(0, by - 3);
                    if (distance >= 1 && distance <= 3) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int count(ServerLevel l, int ox, int oz, int y, Block block) {
        int count = 0;
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                for (int by = y - 1; by <= y + 10; by++) {
                    if (l.getBlockState(new BlockPos(bx, by, bz)).is(block)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ---- the field ---------------------------------------------------------------------------------------------------

    private static void layField(ServerLevel l, int ox, int oz, int y) {
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                l.setBlock(new BlockPos(bx, y - 2, bz), Blocks.DIRT.defaultBlockState(), 2);
                l.setBlock(new BlockPos(bx, y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int by = y; by <= y + 10; by++) {
                    l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static int surfaceBlocks(ServerLevel l, int ox, int oz, int y) {
        int count = 0;
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                for (int by = y - 2; by <= y + 10; by++) {
                    if (l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** The columns that have a shaped block (one per column on a line that stays within a block of height). */
    private static int countColumns(ServerLevel l, int ox, int oz, int y) {
        int count = 0;
        for (int bx = ox; bx < ox + LENGTH; bx++) {
            for (int bz = oz; bz < oz + DEPTH; bz++) {
                boolean any = false;
                for (int by = y - 2; by <= y + 10; by++) {
                    any |= l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity;
                }
                count += any ? 1 : 0;
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

    private DevLineTests() {}
}
