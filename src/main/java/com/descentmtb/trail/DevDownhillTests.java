package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.trail.DownhillShapes.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only: the Trail Shaper's downhill line through its real item code path (two right-clicks) on a 40 x 160 m
 * hillside of grass blocks (about 15 % grade with waves and a 2 m step), in each {@link Style}. Checks the blocks,
 * undoes one line, and rides the real blocks with the headless bike ({@link DevRideSim}). Called from {@link DevSculptTests}.
 */
final class DevDownhillTests {
    private static final int WIDTH = 40, DEPTH = 165;

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    /** Top of the grass of the hillside column (x, z) relative to its start (0, 0). */
    private static int top(int y, int x, int z) {
        return (int) Math.floor(y + 2 - .15 * z + 1.2 * Math.sin(x / 9.0) + .8 * Math.sin(z / 7.0) - (z > 100 ? 2 : 0));
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        int ox = x + 170, oz = z - 10;
        for (int cx = ox >> 4; cx <= (ox + WIDTH) >> 4; cx++) {
            for (int cz = oz >> 4; cz <= (oz + DEPTH) >> 4; cz++) {
                l.setChunkForced(cx, cz, true);
            }
        }
        try {
            layHillside(l, ox, oz, y);
            BlockPos start = new BlockPos(ox + 10, top(y, 10, 3) , oz + 3);
            BlockPos finish = new BlockPos(ox + 26, top(y, 26, 150), oz + 150);

            ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
            ShapeToolItem.mode(tool, ShapeMode.DOWNHILL);
            p.setItemInHand(InteractionHand.MAIN_HAND, tool);

            // the first click only remembers the start, Shift + click forgets it
            click(p, start, false);
            check(DownhillBuilder.points(tool).length == 1, "downhill: the first click only places the start");
            click(p, finish, true);
            check(DownhillBuilder.points(tool).length == 0, "downhill: Shift + click forgets the start");

            for (Style style : Style.values()) {
                new DownhillBuilder.Settings(style, 3).store(tool);
                buildAndRide(p, l, tool, start, finish, style);
                if (style == Style.FLOW) {
                    int blocks = surfaceBlocks(l, ox, oz, y);
                    TrailEdit.undo(l, p);
                    check(blocks > 100 && surfaceBlocks(l, ox, oz, y) == 0, "downhill: one undo removes the whole line (" + blocks + " blocks)");
                    check(l.getBlockState(new BlockPos(ox + 10, top(y, 10, 3), oz + 3)).is(Blocks.GRASS_BLOCK), "downhill: the grass is back after the undo");
                    buildAndRide(p, l, tool, start, finish, style);   // the same click gives the same line again
                }
                TrailEdit.undo(l, p);
            }
        } finally {
            for (int cx = ox >> 4; cx <= (ox + WIDTH) >> 4; cx++) {
                for (int cz = oz >> 4; cz <= (oz + DEPTH) >> 4; cz++) {
                    l.setChunkForced(cx, cz, false);
                }
            }
        }
    }

    private static void click(ServerPlayer p, BlockPos pos, boolean shift) {
        ShapeToolItem.shape(p, pos, Vec3.atCenterOf(pos), Direction.SOUTH, shift);
    }

    private static void buildAndRide(ServerPlayer p, ServerLevel l, ItemStack tool, BlockPos start, BlockPos finish, Style style) {
        // plan it the way the item will (on the untouched ground), so the ride can follow the same centre line
        HillGround ground = new HillGround(l);
        DownhillShapes line = DownhillShapes.plan(ground::height, start.getX() + .5, start.getZ() + .5, finish.getX() + .5, finish.getZ() + .5,
                new DownhillShapes.Params(style, 3, TrailConfig.MAX_DOWNHILL.get()), start.asLong() * 31 + finish.asLong());
        click(p, start, false);
        click(p, finish, false);
        check(DownhillBuilder.points(tool).length == 0, "downhill " + style + ": the second click builds and forgets the start");
        check(surfaceNear(l, start), "downhill " + style + ": the track starts at the first click");

        double[][] path = line.centre();
        var result = DevRideSim.ride(l, path, false, ground.height(path[0][0], path[0][1]) + .3, line.length() / 6 + 25, 9,
                BikeType.ENDURO, null, DevRideSim.Rider.DOWNHILL);
        DescentMtb.LOG.info("[sculpttest] downhill {} ({} m, {} jumps, {} berms) ridden on the real blocks: {}", style,
                (int) line.length(), line.jumps(), line.berms(), result.summary());
        check(!result.bailed(), "downhill " + style + " can be ridden without bailing");
        check(result.laps() > .97, "downhill " + style + " reaches the finish (" + (int) (result.laps() * 100) + " %)");
        check(result.maxOffLine() < 2, "downhill " + style + " keeps the rider on the line (" + result.maxOffLine() + " m)");
    }

    /** A hillside of dirt and grass blocks, air above it. */
    private static void layHillside(ServerLevel l, int ox, int oz, int y) {
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                int top = top(y, dx, dz);
                for (int by = top - 3; by < top; by++) {
                    l.setBlock(new BlockPos(ox + dx, by, oz + dz), Blocks.DIRT.defaultBlockState(), 2);
                }
                l.setBlock(new BlockPos(ox + dx, top, oz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int by = top + 1; by <= top + 12; by++) {
                    l.setBlock(new BlockPos(ox + dx, by, oz + dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static boolean surfaceNear(ServerLevel l, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -2, -1), pos.offset(1, 2, 1))) {
            if (l.getBlockEntity(near) instanceof TrailSurfaceEntity) {
                return true;
            }
        }
        return false;
    }

    private static int surfaceBlocks(ServerLevel l, int ox, int oz, int y) {
        int count = 0;
        for (int dx = 0; dx < WIDTH; dx++) {
            for (int dz = 0; dz < DEPTH; dz++) {
                for (int by = y - 28; by <= y + 14; by++) {
                    if (l.getBlockEntity(new BlockPos(ox + dx, by, oz + dz)) instanceof TrailSurfaceEntity) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private DevDownhillTests() {}
}
