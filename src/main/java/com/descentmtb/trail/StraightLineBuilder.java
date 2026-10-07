package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The straight line of the Trail Shaper ({@link ShapeMode#STRAIGHT_LINE}): right-click block A and then block B, and a
 * trail surface of the chosen width ({@link LineSettings}) is built exactly from A to B with ONE grade (see
 * {@link StraightLines}): no jumps, no rollers, no berms. It may climb, fall or run level and it is not supported by
 * anything, so over air it is a bridge (the player adds the supports). The surface is stacked by {@link ColumnShaper} like every
 * other planner's, so the slope is smooth and continuous. As one {@link TrailEdit} step it can be undone, protection and the
 * survival price apply.
 *
 * <p>A full block in the off-hand is the material of the line (trail dirt without one): a plank bridge, a stone road. In
 * survival the visible block of every column then costs one item of it, besides the trail dirt every edit costs; with
 * too few of them nothing is built. A line is at most {@link StraightLines#MAX_LENGTH} m long
 * ({@link StraightLines#MAX_LENGTH_CREATIVE} m for creative players and operators) and at most {@link StraightLines#MAX_DEGREES}
 * degrees steep.
 */
public final class StraightLineBuilder {
    /** Air kept clear above the surface (blocks): plants and the like, and a hill the line cuts through. */
    private static final int HEADROOM = 2;

    /** A click on a block: point A, then point B and the line is built. */
    public static boolean click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        BlockPos[] ends = LinePoints.click(player, tool, pos, shift);
        return ends != null && build(player, ends[0], ends[1], LineSettings.read(tool).width());
    }

    /** What the player may build: the longest line (m). */
    public static int maxLength(ServerPlayer player) {
        return StraightLines.maxLength(TrailEdit.mayBulkEdit(player));
    }

    private static boolean build(ServerPlayer player, BlockPos a, BlockPos b, int width) {
        ServerLevel level = player.serverLevel();
        StraightLines.Layout layout = new StraightLines.Layout(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), width);
        StraightLines.Problem problem = layout.problem(maxLength(player));
        if (problem != null) {
            Object[] arguments = problem == StraightLines.Problem.TOO_LONG ? new Object[]{maxLength(player)}
                    : problem == StraightLines.Problem.TOO_STEEP ? new Object[]{(int) StraightLines.MAX_DEGREES} : new Object[0];
            player.displayClientMessage(Component.translatable(problem.key(), arguments), true);
            return false;
        }
        try {
            BlockState material = BlockEditor.offHandMaterial(player, a);
            int blocks = TrailEdit.apply(level, player, plan(level, layout, material));
            level.playSound(null, b, material == null ? SoundEvents.GRAVEL_PLACE : material.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .8f, .9f);
            player.displayClientMessage(summary(layout), true);
            return blocks > 0;
        } catch (IllegalArgumentException e) {
            DescentMtb.LOG.info("Straight line not built: {}", e.getMessage());
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /**
     * The block changes that build the line: its surface on the footprint (layered by {@link ColumnShaper}, with the
     * visible block of every column paid for in survival when it is made of {@code material}), and the air above it cleared.
     *
     * @param material a full block (the copycat material of the surface), null for trail dirt
     */
    public static Map<BlockPos, TrailEdit.Change> plan(Level level, StraightLines.Layout layout, BlockState material) {
        BlockState surface = ModBlocks.TRAIL_SURFACE.get().defaultBlockState();
        BlockState made = material == null ? com.descentmtb.registry.ModBlocks.defaultTrailDirt() : material;
        int[] box = layout.bounds(0);
        Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                if (!layout.contains(x + .5, z + .5)) {
                    continue;
                }
                double[] corners = {layout.height(x, z), layout.height(x + 1, z), layout.height(x, z + 1), layout.height(x + 1, z + 1)};
                ColumnShaper.Layers stack = ColumnShaper.layers(corners, false);
                for (ColumnShaper.Layer layer : stack.layers()) {
                    BlockPos pos = new BlockPos(x, layer.y(), z);
                    TrailEdit.Change change = new TrailEdit.Change(surface, null, layer.heights(), made, false);
                    plan.put(pos, material != null && layer.y() == stack.top() ? change.paid() : change);
                }
                for (int y = stack.top() + 1; y <= stack.top() + HEADROOM; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) {
                        throw new TrailEdit.Rejected("descentmtb.edit.not_loaded");
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        plan.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                    }
                }
                if (plan.size() > TrailConfig.MAX_BLOCKS.get()) {
                    throw new TrailEdit.Rejected("descentmtb.edit.too_big", TrailConfig.MAX_BLOCKS.get());
                }
            }
        }
        return plan;
    }

    /** "Straight line: 23 m, grade 12 %" for the action bar. */
    public static Component summary(StraightLines.Layout layout) {
        return Component.translatable("descentmtb.line.built", String.format(Locale.ROOT, "%.0f", layout.length()),
                String.format(Locale.ROOT, "%.0f", layout.percent()));
    }

    private StraightLineBuilder() {}
}
