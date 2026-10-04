package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.trail.BermShapes.Steepness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Development-only: the Trail Shaper's berm creator through its real item code path (three right-clicks), on a
 * fresh grass field next to the other tests. Builds a 90 degree berm of two steepnesses, checks the blocks, undoes
 * one, and rides the real blocks with the headless bike ({@link DevRideSim}). Called from {@link DevSculptTests}.
 */
final class DevBermTests {
    private static final int RADIUS = 8;

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[sculpttest] PASS: {}", what);
    }

    static void run(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        int ox = x + 56, oz = z + 8;
        layField(l, ox - 18, oz - 6, 36, RADIUS + 26, y);

        ItemStack tool = new ItemStack(ModBlocks.TRAIL_SHOVEL.get());
        ShapeToolItem.mode(tool, ShapeMode.BERM_BUILD);
        new BermBuilder.Settings(Steepness.MEDIUM, 4).store(tool);
        p.setItemInHand(InteractionHand.MAIN_HAND, tool);

        // two points are only remembered, Shift + click forgets them
        click(p, entry(ox, y, oz), false);
        click(p, apex(ox, y, oz), false);
        check(BermBuilder.points(tool).length == 2, "berm: two clicks only place points");
        click(p, exit(ox, y, oz), true);
        check(BermBuilder.points(tool).length == 0, "berm: Shift + click forgets the points");

        // the third click builds, as one undo step
        buildAndRide(p, l, tool, ox, y, oz, Steepness.MEDIUM, 7);
        int blocks = surfaceBlocks(l, ox, y, oz);
        TrailEdit.undo(l, p);
        check(blocks > 0 && surfaceBlocks(l, ox, y, oz) == 0, "berm: one undo removes the whole berm (" + blocks + " blocks)");

        buildAndRide(p, l, tool, ox, y, oz, Steepness.WALLRIDE, 9);
        buildAndRide(p, l, tool, ox, y, oz, Steepness.GENTLE, 7);
    }

    private static BlockPos entry(int ox, int y, int oz) {
        return new BlockPos(ox, y - 1, oz);
    }

    private static BlockPos apex(int ox, int y, int oz) {
        return new BlockPos(ox + 6, y - 1, oz + 2);
    }

    private static BlockPos exit(int ox, int y, int oz) {
        return new BlockPos(ox + RADIUS, y - 1, oz + RADIUS);
    }

    private static void click(ServerPlayer p, BlockPos pos, boolean shift) {
        ShapeToolItem.shape(p, pos, Vec3.atCenterOf(pos), Direction.EAST, shift);
    }

    private static void buildAndRide(ServerPlayer p, ServerLevel l, ItemStack tool, int ox, int y, int oz,
                                     Steepness steepness, double speed) {
        new BermBuilder.Settings(steepness, 4).store(tool);
        click(p, entry(ox, y, oz), false);
        click(p, apex(ox, y, oz), false);
        click(p, exit(ox, y, oz), false);
        check(BermBuilder.points(tool).length == 0, "berm " + steepness + ": the third click builds and forgets the points");
        double top = highestSurface(l, ox, y, oz) - y;
        check(top >= steepness.top * .8, String.format("berm %s: the bank is %.2f m high (design %.1f m)", steepness, top, steepness.top));

        var result = DevRideSim.ride(l, path(ox + .5, oz + .5 + RADIUS), false, y + .3, 30, speed, BikeType.ENDURO, null);
        DescentMtb.LOG.info("[sculpttest] berm {} ridden at {} m/s on the real blocks: {}", steepness, speed, result.summary());
        if (steepness != Steepness.WALLRIDE) {
            check(!result.bailed(), "berm " + steepness + " can be ridden without bailing");
        }
        if (steepness != Steepness.MEDIUM) {
            TrailEdit.undo(l, p);
        }
    }

    /** Lead-in straight, 90 degree arc around (cx, cz), lead-out straight; about 0.5 m between points. */
    private static double[][] path(double cx, double cz) {
        List<double[]> points = new ArrayList<>();
        for (double px = cx - 14; px < cx; px += .5) {
            points.add(new double[]{px, cz - RADIUS});
        }
        for (double phi = 0; phi < Math.PI / 2; phi += .5 / RADIUS) {
            points.add(new double[]{cx + RADIUS * Math.sin(phi), cz - RADIUS * Math.cos(phi)});
        }
        for (double pz = cz; pz <= cz + 14; pz += .5) {
            points.add(new double[]{cx + RADIUS, pz});
        }
        return points.toArray(new double[0][]);
    }

    private static void layField(ServerLevel l, int x0, int z0, int width, int depth, int y) {
        for (int bx = x0; bx < x0 + width; bx++) {
            for (int bz = z0; bz < z0 + depth; bz++) {
                l.setBlock(new BlockPos(bx, y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int by = y; by <= y + 8; by++) {
                    l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static int surfaceBlocks(ServerLevel l, int ox, int y, int oz) {
        int count = 0;
        for (int bx = ox - 8; bx <= ox + RADIUS + 10; bx++) {
            for (int bz = oz - 8; bz <= oz + RADIUS + 10; bz++) {
                for (int by = y - 1; by <= y + 8; by++) {
                    if (l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** The highest shaped surface point (absolute Y) around the berm. */
    private static double highestSurface(ServerLevel l, int ox, int y, int oz) {
        double top = y;
        for (int bx = ox - 8; bx <= ox + RADIUS + 10; bx++) {
            for (int bz = oz - 8; bz <= oz + RADIUS + 10; bz++) {
                for (int by = y - 1; by <= y + 8; by++) {
                    if (l.getBlockEntity(new BlockPos(bx, by, bz)) instanceof TrailSurfaceEntity shaped) {
                        for (double corner : shaped.corners()) {
                            top = Math.max(top, by + Math.min(1, corner));
                        }
                    }
                }
            }
        }
        return top;
    }

    private DevBermTests() {}
}
