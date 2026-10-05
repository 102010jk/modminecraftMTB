package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;

/**
 * Roots and rocks remain ordinary placeable items; on a shaped surface they follow its plane. Right-click a shaped
 * block to give its whole surface the overlay (the layer you clicked is the one that is read), Shift + right-click
 * to take it off again. The item is only used up when the overlay really changes.
 */
public final class SurfaceOverlayItem extends BlockItem {
    private final int kind;

    public SurfaceOverlayItem(Block block, Properties properties, int kind) {
        super(block, properties);
        this.kind = kind;
    }

    /**
     * The edit that gives the surface of the shaped block at {@code pos} the overlay {@code kind} (0 = none).
     *
     * @throws TrailEdit.Rejected when there is no shaped block at {@code pos}
     */
    public static Map<BlockPos, TrailEdit.Change> plan(Level level, BlockPos pos, int kind) {
        if (!(level.getBlockEntity(pos) instanceof TrailSurfaceEntity layer)) {
            throw new TrailEdit.Rejected("descentmtb.overlay.wrong_block");
        }
        CompoundTag decoration = layer.saveWithoutMetadata(level.registryAccess());
        decoration.putInt("Overlay", kind);
        ColumnEditor.Column column = new ColumnEditor.Column(pos.getX(), pos.getZ(),
                ColumnShaper.absolute(pos.getY(), layer.corners()), layer.getMaterial(), layer.deck(), decoration);
        return ColumnEditor.rebuild(level, column, column.abs());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        var entity = level.getBlockEntity(pos);
        if (entity instanceof RampBlockEntity && !(entity instanceof TrailSurfaceEntity)) {
            return InteractionResult.PASS;   // a standalone copycat ramp keeps its own look
        }
        if (!(entity instanceof TrailSurfaceEntity layer)) {
            return super.useOn(context);
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!player.mayBuild()) {
                return InteractionResult.FAIL;
            }
            int wanted = player.isShiftKeyDown() ? 0 : kind;
            if (layer.overlay() == wanted) {
                player.displayClientMessage(Component.translatable("descentmtb.overlay.unchanged"), true);
                return InteractionResult.CONSUME;   // nothing changes, nothing is used up
            }
            try {
                TrailEdit.apply(level, player, plan(level, pos, wanted));
                if (!player.isCreative() && wanted != 0) {
                    context.getItemInHand().shrink(1);
                }
            } catch (IllegalArgumentException e) {
                player.displayClientMessage(TrailEdit.describe(e), true);
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.overlay.hint"));
    }
}
