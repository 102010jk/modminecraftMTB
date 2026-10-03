package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static com.descentmtb.trail.TrailMath.Point;

/**
 * Development-only: {@code /mtbdevpump <x> <z>} builds the largest closed pumptrack the server allows
 * (64 x 44 m, 5 m wide) on a flat grass field at y = 200, using the real Trail Wand planner, so the
 * autopilot can ride it. Registered only when {@code -Ddescentmtb.autopilot=true}.
 */
public final class DevPumpTrack {
    public static final int Y = 200, SIZE_X = 64, SIZE_Z = 44;
    public static final PumpShapes.Params PARAMS = new PumpShapes.Params(5, .75, 5, 24);

    public static volatile boolean BUILT, FAILED;

    public static void register(RegisterCommandsEvent e) {
        if (!Boolean.getBoolean("descentmtb.autopilot")) return;
        e.getDispatcher().register(Commands.literal("mtbdevpump").requires(s -> s.hasPermission(2))
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(c -> build(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z"))))));
    }

    private static int build(ServerPlayer p, int x0, int z0) {
        BUILT = FAILED = false;
        var l = p.serverLevel();
        try {
            for (int bx = x0 - 8; bx <= x0 + SIZE_X + 8; bx++) {
                for (int bz = z0 - 8; bz <= z0 + SIZE_Z + 8; bz++) {
                    l.setBlock(new BlockPos(bx, Y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    for (int by = Y; by <= Y + 6; by++) l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            var a = new Point(x0, Y, z0);
            var c = new Point(x0 + SIZE_X, Y, z0 + SIZE_Z);
            var settings = new WandSettings(WandMode.PUMP_LOOP, PARAMS.width(), PARAMS.height(), PARAMS.spacing(), PARAMS.repeats(), 3, .3, .75);
            var plan = SurfacePlans.pump(l, a, TrailBuilder.middle(a, c), c, settings);
            TrailEdit.apply(l, p, plan);
            DescentMtb.LOG.info("[pumptest] built {} x {} m pumptrack at {},{} from {} blocks", SIZE_X, SIZE_Z, x0, z0, plan.size());
            BUILT = true;
            return 1;
        } catch (Exception ex) {
            FAILED = true;
            DescentMtb.LOG.error("[pumptest] build FAILED", ex);
            return 0;
        }
    }

    private DevPumpTrack() {}
}
