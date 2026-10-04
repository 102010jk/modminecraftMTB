package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
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
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Development-only: {@code /mtbdevmanual <x> <z>} builds a 90° berm by placing Shaping Dirt on every column
 * and moving the shared vertices with {@link DevFixtures} (coarse sculpting), then rides it through the real blocks.
 */
public final class DevManualBerm {
    public static final int Y = 200;
    public static volatile boolean BUILT, FAILED;
    public static volatile DevRideSim.Result RIDE;
    public static volatile int PLACED, SCULPTS;

    private static final double RADIUS = 9, HALF_WIDTH = 2.5, BANK_HEIGHT = 1.4;

    public static void register(RegisterCommandsEvent e) {
        if (!Boolean.getBoolean("descentmtb.autopilot")) return;
        e.getDispatcher().register(Commands.literal("mtbdevmanual").requires(s -> s.hasPermission(2))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> build(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))));
    }

    /** Bank height at distance r from the arc's centre: flat on the track, rising on the outside. */
    static double bank(double r) {
        double t = Math.max(0, Math.min(1, (r - (RADIUS - 1)) / 4));
        return BANK_HEIGHT * t * t;
    }

    private static int build(ServerPlayer p, int x0, int z0) {
        BUILT = FAILED = false;
        PLACED = SCULPTS = 0;
        ServerLevel l = p.serverLevel();
        try {
            for (int bx = x0 - 6; bx <= x0 + 40; bx++) {
                for (int bz = z0 - 6; bz <= z0 + 40; bz++) {
                    l.setBlock(new BlockPos(bx, Y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int by = Y; by <= Y + 5; by++) l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            double cx = x0 + 16, cz = z0 + 16;
            ItemStack dirt = new ItemStack(ModBlocks.TRAIL_DIRT.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, dirt);

            // 1. place a Shaping Dirt block on every column of the berm (the real right-click path)
            int lo = (int) Math.floor(RADIUS - HALF_WIDTH - 2.5), hi = (int) Math.ceil(RADIUS + HALF_WIDTH + 2.5);
            List<BlockPos> columns = new ArrayList<>();
            for (int bx = (int) cx - hi; bx <= cx + hi; bx++) {
                for (int bz = (int) cz - hi; bz <= cz + hi; bz++) {
                    double dx = bx + .5 - cx, dz = bz + .5 - cz, r = Math.hypot(dx, dz);
                    if (r >= lo && r <= hi && dx >= -2 && dz >= -2) {
                        columns.add(new BlockPos(bx, Y, bz));
                    }
                }
            }
            for (BlockPos pos : columns) {
                var hit = new BlockHitResult(new Vec3(pos.getX() + .5, Y, pos.getZ() + .5), Direction.UP, pos.below(), false);
                dirt.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
                PLACED++;
            }

            // 2. raise every vertex to the berm profile by clicking the block that owns it, one step at a time
            var vertices = new java.util.HashSet<Long>();
            for (BlockPos pos : columns) {
                for (int i = 0; i < 4; i++) vertices.add(BlockPos.asLong(pos.getX() + i % 2, 0, pos.getZ() + i / 2));
            }
            for (long key : vertices) {
                int vx = BlockPos.getX(key), vz = BlockPos.getZ(key);
                // a fresh Shaping Dirt block is a full block: lower the vertex to the ground, then build the bank on it
                int steps = (int) Math.round((bank(Math.hypot(vx - cx, vz - cz)) - 1) / DevFixtures.STEP);
                BlockPos owner = new BlockPos(vx, Y, vz);
                if (!(l.getBlockEntity(owner) instanceof TrailSurfaceEntity)) {
                    continue;   // vertex on the rim of the area: its owner column has no block
                }
                for (int s = 0; s < Math.abs(steps); s++) {
                    DevFixtures.sculpt(p, owner, new Vec3(vx + .05, Y + .5, vz + .05), steps < 0);
                    SCULPTS++;
                }
            }

            // 3. ride it: straight in (east), the 90° arc, straight out (north)
            List<double[]> path = new ArrayList<>();
            for (double x = cx - 14; x < cx - .01; x += .5) path.add(new double[]{x, cz + RADIUS});
            for (double a = Math.PI / 2; a > 0; a -= .5 / RADIUS) path.add(new double[]{cx + RADIUS * Math.cos(a), cz + RADIUS * Math.sin(a)});
            for (double z = cz; z > cz - 14; z -= .5) path.add(new double[]{cx + RADIUS, z});
            RIDE = DevRideSim.ride(l, path.toArray(new double[0][]), false, Y + .3, 30, 7, BikeType.ENDURO, null);
            DescentMtb.LOG.info("[manualberm] placed {} blocks, {} sculpt clicks | ride: {}", PLACED, SCULPTS, RIDE.summary());
            BUILT = true;
            return 1;
        } catch (Exception ex) {
            FAILED = true;
            DescentMtb.LOG.error("[manualberm] FAILED", ex);
            return 0;
        }
    }

    private DevManualBerm() {}
}
