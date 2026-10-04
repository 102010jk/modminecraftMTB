package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Trail Shaper, the only editing tool: right-click a block and the selected {@link ShapeMode} reshapes
 * that single block. It never changes the blocks around it; neighbours are only read (see {@link ShapePresets}).
 * Plain full blocks (dirt, grass, stone ...) first become a shaped copy of themselves. Every edit goes through
 * {@link TrailEdit}, so it can be undone.
 */
public final class ShapeToolItem extends Item {
    private static final String MODE_TAG = "ShapeModeName";

    public ShapeToolItem(Properties properties) {
        super(properties);
    }

    public static boolean usable(ItemStack stack) {
        return stack.getItem() instanceof ShapeToolItem;
    }

    /** The mode saved in the tool; {@link ShapeMode#AUTO} when none (or an unknown one) is saved. */
    public static ShapeMode mode(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return ShapeMode.fromName(tag.getString(MODE_TAG));
    }

    public static void mode(ItemStack stack, ShapeMode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(MODE_TAG, mode.name()));
    }

    /**
     * Server side of a right-click: applies the tool's mode to the block at {@code pos}.
     *
     * @param facing the player's horizontal facing (ramps rise away from the player)
     * @param shift  ramps rise towards the player; manual clicks lower
     * @return true when the block was changed
     */
    public static boolean shape(ServerPlayer player, BlockPos pos, Vec3 hit, Direction facing, boolean shift) {
        var level = player.serverLevel();
        if (!player.mayBuild() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        ShapeMode mode = mode(player.getMainHandItem());
        ShapePresets.Plan plan = ShapePresets.plan(level, pos, mode, hit, facing, shift);
        if (plan == null || !plan.changesAnything()) {
            return false;
        }
        try {
            TrailEdit.apply(level, player, ColumnEditor.rebuild(level, plan.column(), plan.newAbs()));
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
            return false;
        }
        level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .5f, shift ? .8f : 1.1f);
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        shape(player, context.getClickedPos(), context.getClickLocation(), player.getDirection(), player.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(mode(stack).key()));
        lines.add(Component.translatable("descentmtb.shape.tooltip"));
    }
}
