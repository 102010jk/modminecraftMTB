package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.world.McColumns;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.descentmtb.trail.TrailMath.Point;

/**
 * Development-only: {@code /mtbdevtrail} runs the planners, registered blocks and world edits on a grass field
 * at y = 200, then the Trail Shaper tests in {@link DevSculptTests}. Registered only with
 * {@code -Ddescentmtb.autopilot=true}.
 */
public final class DevTrailTests {
    public static volatile boolean PASSED, FAILED;

    public static void register(RegisterCommandsEvent e) {
        if (!Boolean.getBoolean("descentmtb.autopilot")) {
            return;
        }
        e.getDispatcher().register(Commands.literal("mtbdevtrail").requires(s -> s.hasPermission(2))
                .executes(c -> run(c.getSource().getPlayerOrException())));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[trailtest] PASS: {}", what);
    }

    private static WandSettings settings(WandMode mode, double width, double height, double spacing, int repeats) {
        return new WandSettings(mode, width, height, spacing, repeats, 3, .3, .75);
    }

    private static void clearField(ServerLevel l, int x, int y, int z) {
        for (int bx = x; bx <= x + 42; bx++) {
            for (int bz = z; bz <= z + 36; bz++) {
                l.setBlock(new BlockPos(bx, y - 1, bz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                for (int by = y; by <= y + 5; by++) {
                    l.setBlock(new BlockPos(bx, by, bz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static int run(ServerPlayer p) {
        PASSED = FAILED = false;
        ServerLevel l = p.serverLevel();
        ItemStack saved = p.getMainHandItem();
        int x = p.blockPosition().getX() + 14, z = p.blockPosition().getZ(), y = 200;
        BlockPos origin = new BlockPos(x, y, z);
        try {
            clearField(l, x, y, z);
            boardwalk(p, l, x, y, z, origin);
            pumpLine(p, l, x, y, z);
            undo(p, l, x, y, z);
            pumpLoop(p, l, x, y, z);
            equipment(p, l, x, y, z);
            DevSculptTests.run(p, l, x, y, z);
            PASSED = true;
            DescentMtb.LOG.info("[trailtest] ALL PASSED");
        } catch (Exception e) {
            FAILED = true;
            DescentMtb.LOG.error("[trailtest] FAIL", e);
        } finally {
            p.setItemInHand(InteractionHand.MAIN_HAND, saved);
        }
        return PASSED ? 1 : 0;
    }

    /** A continuous deck crossing y = 201 keeps both layers and the original plane; smoothing keeps deck and wood. */
    private static void boardwalk(ServerPlayer p, ServerLevel l, int x, int y, int z, BlockPos origin) {
        Point a = new Point(x + .5, y + .9, z + .5), c = new Point(x + .5, y + 1.1, z + 4.5);
        var deck = TrailBuilder.plan(l, a, TrailBuilder.middle(a, c), c, TrailBuilder.Shape.BOARDWALK);
        check(deck.containsKey(origin), "boardwalk starts at its first guide");
        TrailEdit.apply(l, p, deck);
        var columns = new McColumns(l);
        double h1 = columns.collisionTop(x + .5, z + 2.4, y + 3, y - 1), h2 = columns.collisionTop(x + .5, z + 2.6, y + 3, y - 1);
        check(Math.abs(h1 - (y + .995)) < .005 && Math.abs(h2 - (y + 1.005)) < .005,
                "continuous surface crosses an integer block height without phantom step");

        ItemStack bumpItem = new ItemStack(ModBlocks.TRAIL_DECK.get());
        for (int i = 0; i < 3; i++) {
            ShapingBlockItem.sculpt(p, bumpItem, new BlockPos(x, y, z + 2), new Vec3(x + .5, y + 1, z + 2.5), false);
        }
        var brush = SurfacePlans.smooth(l, new Point(x + .5, y + 1.2, z + 2.5), 2, .6, .8);
        check(brush.values().stream().anyMatch(v -> v.heights() != null && v.deck() && v.material().is(Blocks.OAK_PLANKS)),
                "smoothing preserves deck and wood material");
    }

    /** The pump-line planner turns three guide points into rideable surfaces. */
    private static void pumpLine(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        var plan = SurfacePlans.pump(l, new Point(x + 10.5, y, z + 1.5), new Point(x + 10.5, y, z + 9.5),
                new Point(x + 10.5, y, z + 17.5), settings(WandMode.PUMP_LINE, 4, .8, 4, 4));
        check(l.getBlockState(new BlockPos(x + 10, y, z + 3)).isAir(), "a plan does not modify the terrain");
        TrailEdit.apply(l, p, plan);
        check(l.getBlockEntity(new BlockPos(x + 10, y, z + 3)) instanceof TrailSurfaceEntity, "applying creates rideable pumptrack surfaces");
    }

    /** Every edit is stored for undo and restores the previous terrain. */
    private static void undo(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        BlockPos target = new BlockPos(x + 5, y, z);
        Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
        plan.put(target, new TrailEdit.Change(ModBlocks.TRAIL_SURFACE.get().defaultBlockState(), null,
                new double[]{1, 1, 1, 1}, Blocks.COARSE_DIRT.defaultBlockState(), false));
        TrailEdit.apply(l, p, plan);
        check(l.getBlockEntity(target) instanceof TrailSurfaceEntity, "an applied edit creates its shaped block");
        TrailEdit.undo(l, p);
        check(l.getBlockState(target).isAir(), "undo restores the previous terrain");
    }

    private static void pumpLoop(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        var loop = SurfacePlans.pump(l, new Point(x + 20, y, z), new Point(x + 30, y, z + 14), new Point(x + 40, y, z + 28),
                settings(WandMode.PUMP_LOOP, 3, .7, 5, 10));
        TrailEdit.apply(l, p, loop);
        check(loop.size() > 100, "closed pumptrack loop with banks generated");
    }

    /** Supports, signs and the landing airbag prefab. */
    private static void equipment(ServerPlayer p, ServerLevel l, int x, int y, int z) {
        Point a = new Point(x + .5, y + .9, z + .5);
        l.setBlock(new BlockPos(x + 6, y + 4, z + 3), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        check(Math.abs(SurfacePlans.terrain(l, x + 6.5, z + 3.5, y) - y) < .001, "terrain lookup chooses clicked ground under a bridge");

        var support = EquipmentPlans.plan(l, a, a, settings(WandMode.SUPPORT, 5, .75, 5, 5), Direction.SOUTH);
        check(!support.isEmpty(), "support starts below a partial wooden deck");
        TrailEdit.apply(l, p, support);

        BlockPos signPos = new BlockPos(x + 4, y, z + 5);
        l.setBlock(signPos, ModBlocks.TRAIL_SIGN.get().defaultBlockState(), 3);
        var sign = (TrailSignEntity) l.getBlockEntity(signPos);
        var canvas = new SignArt(new byte[256]);
        canvas.template(0);
        sign.setPixels(canvas.pixels());
        var tag = sign.saveWithoutMetadata(l.registryAccess());
        var second = new TrailSignEntity(signPos, sign.getBlockState());
        second.loadWithComponents(tag, l.registryAccess());
        check(Arrays.equals(sign.pixels(), second.pixels()), "16x16 sign artwork survives block entity serialization");

        var bag = EquipmentPlans.plan(l, new Point(x + 5, y, z + 12), a, settings(WandMode.AIRBAG, 5, .75, 5, 6), Direction.SOUTH);
        TrailEdit.apply(l, p, bag);
        check(bag.size() >= 30, "landing airbag prefab built");
    }

    private DevTrailTests() {}
}
