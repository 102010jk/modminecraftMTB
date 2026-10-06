package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.TreeScan.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Clear path of the Trail Shaper ({@link ShapeMode#CLEAR_PATH}): right-click point A and point B and the corridor between
 * them is cleared for a trail: the width of the line setting ({@link LineSettings#clearWidth}, 3 to 5 blocks) plus one
 * block of margin on each side, from the ground up to {@link #HEADROOM} blocks above it. What goes: whole trees
 * ({@link TreeScan}: logs, their natural leaves and vines, even where the crown reaches beyond the corridor), other
 * leaves, bushes, plants, flowers, snow layers and vines. What never goes: the ground itself, blocks a player placed (other
 * than plants and leaves), anything with a block entity, and a tree that holds one (a bee nest). It is one
 * {@link TrailEdit} step, so it can be undone, and it is free (nothing drops, nothing is refunded).
 *
 * <p>Survival players may clear {@value #SURVIVAL_LENGTH} m at a time, creative players and operators up to
 * {@link StraightLines#MAX_LENGTH_CREATIVE} m.
 */
public final class ClearPathBuilder {
    /** Blocks above the ground that are cleared. */
    public static final int HEADROOM = 3;
    /** Blocks of margin on each side of the path. */
    public static final int MARGIN = 1;
    /** The longest path (m) a survival player may clear. */
    public static final int SURVIVAL_LENGTH = StraightLines.SURVIVAL_CLEAR_LENGTH;
    /** How far below the surface of a column the ground is looked for. */
    private static final int SEARCH_DEPTH = 64;

    /** What was found to clear: the blocks, and how many whole trees are among them. */
    public record Plan(Map<BlockPos, TrailEdit.Change> changes, int trees) {}

    /** A click on a block: point A, then point B and the path is cleared. */
    public static boolean click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        BlockPos[] ends = LinePoints.click(player, tool, pos, shift);
        return ends != null && build(player, ends[0], ends[1], LineSettings.read(tool).clearWidth());
    }

    /** The longest path (m) the player may clear. */
    public static int maxLength(ServerPlayer player) {
        return StraightLines.maxClearLength(TrailEdit.mayBulkEdit(player));
    }

    private static boolean build(ServerPlayer player, BlockPos a, BlockPos b, int width) {
        ServerLevel level = player.serverLevel();
        StraightLines.Layout layout = new StraightLines.Layout(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), width);
        if (layout.horizontal() < 1) {
            player.displayClientMessage(Component.translatable(StraightLines.Problem.TOO_SHORT.key()), true);
            return false;
        }
        if (layout.horizontal() > maxLength(player)) {
            player.displayClientMessage(Component.translatable(TrailEdit.mayBulkEdit(player) ? "descentmtb.line.too_long" : "descentmtb.clear.creative_only",
                    maxLength(player)), true);
            return false;
        }
        try {
            Plan plan = plan(level, layout);
            if (plan.changes().isEmpty()) {
                player.displayClientMessage(Component.translatable("descentmtb.clear.nothing"), true);
                return false;
            }
            int blocks = TrailEdit.apply(level, player, plan.changes(), false);
            level.playSound(null, b, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, .8f, .9f);
            player.displayClientMessage(Component.translatable("descentmtb.clear.done", plan.trees(), blocks), true);
            return blocks > 0;
        } catch (IllegalArgumentException e) {
            DescentMtb.LOG.info("Path not cleared: {}", e.getMessage());
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /** The block changes that clear the corridor of {@code layout}, and the number of whole trees in them. */
    public static Plan plan(Level level, StraightLines.Layout layout) {
        WorldKinds world = new WorldKinds(level);
        Map<BlockPos, TrailEdit.Change> changes = new LinkedHashMap<>();
        Set<BlockPos> treesSeen = new HashSet<>();
        int trees = 0;
        int[] box = layout.bounds(MARGIN);
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                if (!layout.inCorridor(x + .5, z + .5, MARGIN)) {
                    continue;
                }
                if (!level.isLoaded(new BlockPos(x, level.getMinBuildHeight(), z))) {
                    throw new TrailEdit.Rejected("descentmtb.edit.not_loaded");
                }
                int ground = groundTop(level, world, x, z);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                for (int y = ground + 1; y <= ground + HEADROOM; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    switch (world.kind(pos)) {
                        case LEAF, PLACED_LEAF, PLANT, VINE -> {
                            if (!world.hasData(pos)) {
                                changes.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                            }
                        }
                        case LOG -> {
                            if (!treesSeen.contains(pos)) {
                                Set<TreeScan.Pos> tree = TreeScan.tree(world, new TreeScan.Pos(x, y, z));
                                treesSeen.add(pos);
                                if (!tree.isEmpty()) {
                                    trees++;
                                    for (TreeScan.Pos at : tree) {
                                        BlockPos block = new BlockPos(at.x(), at.y(), at.z());
                                        treesSeen.add(block);
                                        changes.put(block, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                                    }
                                }
                            }
                        }
                        default -> { }
                    }
                }
                if (changes.size() > TrailConfig.MAX_BLOCKS.get()) {
                    throw new TrailEdit.Rejected("descentmtb.edit.too_big", TrailConfig.MAX_BLOCKS.get());
                }
            }
        }
        return new Plan(changes, trees);
    }

    /** Y of the highest block of the column that is ground (not air, plants, leaves, logs or vines), or {@link Integer#MIN_VALUE}. */
    private static int groundTop(Level level, WorldKinds world, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > Math.max(level.getMinBuildHeight(), top - SEARCH_DEPTH); y--) {
            if (world.kind(pos.setY(y)) == Kind.GROUND) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** What kind of block a state is for the clearing. */
    static Kind kind(BlockState state) {
        if (state.isAir()) {
            return Kind.AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return Kind.GROUND;
        }
        if (state.is(BlockTags.LOGS)) {
            return Kind.LOG;
        }
        if (state.is(BlockTags.LEAVES)) {
            return state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT) ? Kind.PLACED_LEAF : Kind.LEAF;
        }
        if (state.getBlock() instanceof VineBlock) {
            return Kind.VINE;
        }
        if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof SnowLayerBlock || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.REPLACEABLE)) {
            return Kind.PLANT;
        }
        return Kind.GROUND;
    }

    /** The loaded world as {@link TreeScan} sees it; what is not loaded counts as ground, so nothing there is touched. */
    private record WorldKinds(Level level) implements TreeScan.World {
        Kind kind(BlockPos pos) {
            return level.isLoaded(pos) && !level.isOutsideBuildHeight(pos) ? ClearPathBuilder.kind(level.getBlockState(pos)) : Kind.GROUND;
        }

        boolean hasData(BlockPos pos) {
            return level.isLoaded(pos) && level.getBlockEntity(pos) != null;
        }

        @Override
        public Kind kind(TreeScan.Pos pos) {
            return kind(new BlockPos(pos.x(), pos.y(), pos.z()));
        }

        @Override
        public int leafDistance(TreeScan.Pos pos) {
            BlockState state = level.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
            return state.hasProperty(LeavesBlock.DISTANCE) ? state.getValue(LeavesBlock.DISTANCE) : 7;
        }

        @Override
        public boolean hasData(TreeScan.Pos pos) {
            return hasData(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
    }

    private ClearPathBuilder() {}
}
