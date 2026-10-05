package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Trail Shaper, the only editing tool: right-click a block and the selected {@link ShapeMode} does its job.
 * The block presets reshape that single block and never change the blocks around it; neighbours are only read
 * (see {@link ShapePresets}). Plain full blocks (dirt, grass, stone ...) first become a shaped copy of themselves.
 * The other kinds of mode tune copycat ramps ({@link RampTuning}), build a berm from three points
 * ({@link BermBuilder}), build a whole downhill line from two ({@link DownhillBuilder}) or copy and paste blocks
 * ({@link ShapeClipboard}). The jump builder ({@link JumpBuilder}) and the block editor ({@link BlockEditor}) are
 * opened as screens on the client and send their result in their own payloads. Every edit goes through
 * {@link TrailEdit}, so it can be undone.
 *
 * <p>The tool keeps its small state in its custom data: the selected mode, the cursor's sub-type and step
 * ({@link CursorSettings}), the last jump settings, and what the berm and copy modes show on the client (see
 * {@link #data} and {@link #editData}).
 */
public final class ShapeToolItem extends Item {
    private static final String MODE_TAG = "ShapeModeName";

    public ShapeToolItem(Properties properties) {
        super(properties);
    }

    public static boolean usable(ItemStack stack) {
        return stack.getItem() instanceof ShapeToolItem;
    }

    /** A copy of the tool's custom data. */
    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    /** Changes the tool's custom data in place. */
    public static void editData(ItemStack stack, Consumer<CompoundTag> edit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, edit);
    }

    /** The mode saved in the tool; {@link ShapeMode#AUTO} when none (or an unknown one) is saved. */
    public static ShapeMode mode(ItemStack stack) {
        return ShapeMode.fromName(data(stack).getString(MODE_TAG));
    }

    public static void mode(ItemStack stack, ShapeMode mode) {
        editData(stack, tag -> tag.putString(MODE_TAG, mode.name()));
    }

    /** Server side: switches the tool in the player's hand to another mode and drops what the old mode was waiting for. */
    public static void changeMode(ServerPlayer player, ShapeMode mode) {
        ItemStack stack = player.getMainHandItem();
        if (mode(stack) != mode) {
            RampTuning.clearLink(player);
        }
        mode(stack, mode);
    }

    /**
     * Server side of a right-click on a block: applies the tool's mode to the block at {@code pos}.
     *
     * @param facing the player's horizontal facing (ramps rise away from the player)
     * @param shift  ramps rise towards the player; manual clicks lower; berm and copy modes start over
     * @return true when something was changed
     */
    public static boolean shape(ServerPlayer player, BlockPos pos, Vec3 hit, Direction facing, boolean shift) {
        return shape(player, InteractionHand.MAIN_HAND, pos, hit, facing, shift);
    }

    /** Like {@link #shape(ServerPlayer, BlockPos, Vec3, Direction, boolean)}, with the tool in the given hand. */
    public static boolean shape(ServerPlayer player, InteractionHand hand, BlockPos pos, Vec3 hit, Direction facing, boolean shift) {
        var level = player.serverLevel();
        if (!player.mayBuild() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        ItemStack tool = player.getItemInHand(hand);
        ShapeMode mode = mode(tool);
        return switch (mode.kind) {
            case COLUMN -> reshape(player, pos, mode, hit, facing, shift, CursorSettings.read(tool));
            case RAMP -> RampTuning.click(player, pos, facing, shift, RampTuning.Action.of(mode));
            case BERM -> BermBuilder.click(player, tool, pos, shift);
            case COPY -> ShapeClipboard.click(player, tool, pos, shift);
            case DOWNHILL -> DownhillBuilder.click(player, tool, pos, shift);
            case JUMP -> false;   // the client opens the jump screen instead, its Build button builds (JumpBuilder)
        };
    }

    /** The block presets: new corner heights for the one clicked block. */
    private static boolean reshape(ServerPlayer player, BlockPos pos, ShapeMode mode, Vec3 hit, Direction facing, boolean shift,
                                   CursorSettings cursor) {
        var level = player.serverLevel();
        ShapePresets.Plan plan = ShapePresets.plan(level, pos, mode, hit, facing, shift, cursor);
        if (plan == null || !plan.changesAnything()) {
            return false;
        }
        try {
            TrailEdit.apply(level, player, ColumnEditor.rebuild(level, plan.column(), plan.newAbs()));
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
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
        shape(player, context.getHand(), context.getClickedPos(), context.getClickLocation(), player.getDirection(), player.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    /** Shift + right-click in the air: turns the clipboard (copy mode) or forgets the points (berm and downhill modes). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ShapeMode.Kind kind = mode(stack).kind;
        boolean airAction = player.isShiftKeyDown()
                && (kind == ShapeMode.Kind.COPY || kind == ShapeMode.Kind.BERM || kind == ShapeMode.Kind.DOWNHILL);
        if (!airAction) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (kind == ShapeMode.Kind.COPY) {
                ShapeClipboard.rotate(serverPlayer, stack);
            } else if (kind == ShapeMode.Kind.DOWNHILL) {
                DownhillBuilder.clear(serverPlayer, stack);
            } else {
                BermBuilder.clear(serverPlayer, stack);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(mode(stack).key()));
        lines.add(Component.translatable("descentmtb.shape.tooltip"));
    }
}
