package com.descentmtb.world;

import com.descentmtb.DescentMtb;
import com.descentmtb.physics.*;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Isolated development-world checks of the exact Level.clip path used by the ridden bike. */
public final class DevWallTests {
    public static volatile boolean PASSED, FAILED;
    private static final Controls INTO = new Controls(0, 0, 0, 0, 0, 1, false, 0, 0);

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
        DescentMtb.LOG.info("[walltest] PASS: {}", message);
    }

    private static BikeSim bike(McColumns columns, double x, double y, double z, double lift, double yaw, V3 velocity) {
        BikeSim sim = new BikeSim(new BikeParams(), columns.terrain());
        columns.newTick();
        sim.place(x, y, z, yaw);
        sim.pos = sim.pos.addScaled(V3.Y, lift);
        sim.riderPos = sim.riderPos.addScaled(V3.Y, lift);
        sim.vel = sim.riderVel = velocity;
        return sim;
    }

    public static void run(ServerLevel level, BlockPos origin) {
        if (!Boolean.getBoolean("descentmtb.wallrideOnly")) return;
        PASSED = FAILED = false;
        int x = origin.getX() + 14, y = origin.getY(), z = origin.getZ();
        try {
            for (int bx = x; bx <= x + 12; bx++) for (int bz = z; bz <= z + 36; bz++) {
                level.setBlock(new BlockPos(bx, y - 1, bz), Blocks.STONE.defaultBlockState(), 3);
                for (int by = y; by <= y + 10; by++) level.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 3);
            }
            for (int bz = z; bz <= z + 24; bz++) for (int by = y; by <= y + 8; by++)
                level.setBlock(new BlockPos(x, by, bz), Blocks.STONE.defaultBlockState(), 3);
            McColumns columns = new McColumns(level);
            Terrain terrain = columns.terrain();
            Terrain.RayHit hit = new Terrain.RayHit();
            check(terrain.raycast(new V3(x + 1.5, y + 3, z + 8), new V3(x + .8, y + 3, z + 8), hit)
                    && Math.abs(hit.distance - .5) < .001 && hit.normal.x > .99, "Minecraft collider reports the wall distance and outward normal");
            BikeSim sim = bike(columns, x + 1.5, y, z + 6, 3, 0, new V3(-2, 0, 12));
            for (int i = 0; i < 16; i++) {
                columns.newTick(); sim.tick(Controls.NONE, .05);
                check(sim.wallRide && !sim.bailed && sim.pos.x > x + 1.4, "glancing flight retains real block support, tick " + i);
            }
            check(Math.abs(sim.lean) > 1.1, "real block wallride reaches its riding lean");
            sim = bike(columns, x + 1.5, y, z + 6, 0, 0, new V3(0, 0, 14));
            for (int i = 0; i < 15; i++) {
                columns.newTick(); sim.tick(INTO, .05);
                check(!sim.wallRide, "grounded wall brushing does not latch, tick " + i);
            }
            for (int by = y; by <= y + 8; by++) level.setBlock(new BlockPos(x + 6, by, z + 8), Blocks.STONE.defaultBlockState(), 3);
            sim = bike(columns, x + 7.5, y, z + 8.5, 3, 0, new V3(-1, 0, 12));
            columns.newTick(); sim.tick(INTO, .05);
            check(!sim.wallRide, "a Minecraft pillar is not a continuous wallride");
            BlockPos shaped = new BlockPos(x + 8, y + 3, z + 12);
            level.setBlock(shaped, ModBlocks.RAMP.get().defaultBlockState().setValue(com.descentmtb.ramp.RampBlock.END, 16), 3);
            columns.newTick();
            check(terrain.raycast(new V3(shaped.getX() + 1.002, shaped.getY() + .25, shaped.getZ() + .5),
                    new V3(shaped.getX() + .998, shaped.getY() + .25, shaped.getZ() + .548), hit)
                    && hit.normal.x > .99, "short grazing collision on a real copycat ramp retains its face");
            PASSED = true;
            DescentMtb.LOG.info("[walltest] BLOCK COLLIDERS ALL PASSED");
        } catch (Exception ex) {
            FAILED = true;
            DescentMtb.LOG.error("[walltest] FAIL", ex);
        }
    }

    private DevWallTests() {}
}
