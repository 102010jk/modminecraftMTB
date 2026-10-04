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
    public static volatile DevRideSim.Result RIDE;

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
            RIDE = ride(l, a, c);
            DescentMtb.LOG.info("[pumptest] ride: {}", RIDE.summary());
            BUILT = true;
            return 1;
        } catch (Exception ex) {
            FAILED = true;
            DescentMtb.LOG.error("[pumptest] build FAILED", ex);
            return 0;
        }
    }

    /** Rides the finished loop through the real blocks, pumping the rollers. */
    private static DevRideSim.Result ride(net.minecraft.server.level.ServerLevel level, Point a, Point c) {
        var oval = PumpShapes.oval(a, c, PARAMS);
        int steps = (int) (oval.perimeter() / .5);
        double[][] path = new double[steps][];
        for (int i = 0; i < steps; i++) {
            double th = -Math.PI / 2 + 2 * Math.PI * i / steps;
            path[i] = new double[]{oval.cx() + oval.rx() * Math.cos(th), oval.cz() + oval.rz() * Math.sin(th)};
        }
        double spacing = PARAMS.spacing(), phase = Math.toRadians(120);
        return DevRideSim.ride(level, path, true, Y + .3, 60, 7.5, com.descentmtb.entity.BikeType.ENDURO,
                sim -> Math.cos(2 * Math.PI * (oval.distance(sim.pos.x, sim.pos.z) - spacing / 2) / spacing + phase)
                        * (1 - oval.turnAmount(sim.pos.x, sim.pos.z)));
    }

    private DevPumpTrack() {}
}
