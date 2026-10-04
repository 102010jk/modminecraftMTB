package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.descentmtb.trail.TrailMath.Point;

/**
 * Trail Machete (tool 2): the simple tool. Right-click a trunk to fell the whole tree, right-click the
 * ground to clear plants and lightly smooth the terrain. It never builds berms or any trail shape.
 */
public final class ClearingToolItem extends Item {
    private static final int MAX_TREE_BLOCKS = 512;
    private static final int LEAF_REACH = 6;
    private static final double TIDY_RADIUS = 2.5, TIDY_RADIUS_LARGE = 4.0;

    public ClearingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        if (state.is(BlockTags.LOGS)) {
            return fellTree(context, player, level, clicked, state);
        }
        return tidyGround(context, player, level);
    }

    // ------------------------------------------------------------------ felling

    private InteractionResult fellTree(UseOnContext context, ServerPlayer player, Level level, BlockPos start, BlockState state) {
        Set<BlockPos> tree = collectTree(level, start, family(state));
        if(player.isShiftKeyDown()) {
            Direction forward=player.getDirection();
            for(int step=1;step<=7&&tree.size()<MAX_TREE_BLOCKS;step++) for(int across=-1;across<=1;across++)for(int dy=-2;dy<=4;dy++) {
                BlockPos q=start.relative(forward,step).relative(forward.getClockWise(),across).above(dy);
                if(level.isLoaded(q)&&level.getBlockState(q).is(BlockTags.LOGS)) {
                    var next=collectTree(level,q,family(level.getBlockState(q)));
                    if(tree.size()+next.size()<=MAX_TREE_BLOCKS)tree.addAll(next);
                }
            }
        }
        try {
            for(var pos:tree) if(!player.mayBuild()||!level.mayInteract(player,pos)||level.getBlockEntity(pos)!=null)
                throw new IllegalArgumentException("Koridor obsahuje chráněné bloky");
            if (player.isCreative()) {
                // creative: an undoable edit, no drops
                Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
                for (BlockPos pos : tree) {
                    plan.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                }
                TrailEdit.apply(level, player, plan);
            } else {
                for (BlockPos pos : tree) {
                    level.destroyBlock(pos, true, player);
                }
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
            return InteractionResult.FAIL;
        }
        for (BlockPos pos : tree.stream().limit(24).toList()) {
            level.levelEvent(2001, pos, Block.getId(state));
        }
        player.displayClientMessage(Component.translatable("descentmtb.machete.felled", tree.size()), true);
        return InteractionResult.CONSUME;
    }

    private static String family(BlockState state) {
        return TreeNames.family(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
    }

    /** The trunk (same wood, 26-neighbourhood) plus the natural leaves hanging on it. */
    static Set<BlockPos> collectTree(Level level, BlockPos start, String family) {
        Set<BlockPos> trunk = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        trunk.add(start);
        queue.add(start);
        while (!queue.isEmpty() && trunk.size() < MAX_TREE_BLOCKS) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos q = p.offset(dx, dy, dz);
                        if (trunk.contains(q) || !level.isLoaded(q) || trunk.size() >= MAX_TREE_BLOCKS) {
                            continue;
                        }
                        BlockState s = level.getBlockState(q);
                        if (s.is(BlockTags.LOGS) && family(s).equals(family)) {
                            trunk.add(q);
                            queue.add(q);
                        }
                    }
                }
            }
        }

        Set<BlockPos> all = new LinkedHashSet<>(trunk);
        Set<BlockPos> frontier = new LinkedHashSet<>(trunk);
        for (int depth = 1; depth <= LEAF_REACH && !frontier.isEmpty() && all.size() < MAX_TREE_BLOCKS; depth++) {
            Set<BlockPos> next = new LinkedHashSet<>();
            for (BlockPos p : frontier) {
                for (Direction d : Direction.values()) {
                    BlockPos q = p.relative(d);
                    if (all.contains(q) || !level.isLoaded(q) || all.size() >= MAX_TREE_BLOCKS) {
                        continue;
                    }
                    BlockState s = level.getBlockState(q);
                    if (s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT)) {
                        all.add(q);
                        next.add(q);
                    }
                }
            }
            frontier = next;
        }
        return all;
    }

    // ------------------------------------------------------------------ tidying

    private InteractionResult tidyGround(UseOnContext context, ServerPlayer player, Level level) {
        if (!TrailPermissions.allowed(player)) {
            return InteractionResult.FAIL;
        }
        double radius = player.isShiftKeyDown() ? TIDY_RADIUS_LARGE : TIDY_RADIUS;
        var hit = context.getClickLocation();
        Point centre = new Point(hit.x, hit.y, hit.z);

        Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
        collectPlants(level, centre, radius, plan);
        int plants = plan.size();
        try {
            plan.putAll(SurfacePlans.smooth(level, centre, radius, .35, .8));
            if (plan.isEmpty()) {
                player.displayClientMessage(Component.translatable("descentmtb.machete.nothing"), true);
                return InteractionResult.CONSUME;
            }
            TrailEdit.apply(level, player, plan);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
            return InteractionResult.FAIL;
        }
        context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
        player.displayClientMessage(Component.translatable("descentmtb.machete.tidied", plants, plan.size() - plants), true);
        return InteractionResult.CONSUME;
    }

    private static void collectPlants(Level level, Point centre, double radius, Map<BlockPos, TrailEdit.Change> plan) {
        int baseY = (int) Math.floor(centre.y());
        for (int x = (int) Math.floor(centre.x() - radius); x <= Math.ceil(centre.x() + radius); x++) {
            for (int z = (int) Math.floor(centre.z() - radius); z <= Math.ceil(centre.z() + radius); z++) {
                if (Math.hypot(x + .5 - centre.x(), z + .5 - centre.z()) > radius) {
                    continue;
                }
                for (int y = baseY + 4; y >= baseY - 1; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos) && isPlant(level.getBlockState(pos))) {
                        plan.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                    }
                }
            }
        }
    }

    private static boolean isPlant(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.machete.hint"));
    }
}
