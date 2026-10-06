package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The two clicks of the line tools ({@link ShapeMode#CLEAR_PATH}, {@link ShapeMode#STRAIGHT_LINE}): the first click
 * remembers point A in the tool's custom data (with its dimension, so the client can draw it and a point from another
 * dimension is forgotten), the second one is point B and finishes the line. Shift + right-click forgets point A.
 */
public final class LinePoints {
    /** Custom data keys: point A (a packed block position) and its dimension. */
    public static final String POINT_TAG = "LinePoint", DIMENSION_TAG = "LineDim";

    /** Point A when it is placed, else null. */
    public static BlockPos first(ItemStack tool) {
        var data = ShapeToolItem.data(tool);
        return data.contains(POINT_TAG) ? BlockPos.of(data.getLong(POINT_TAG)) : null;
    }

    /**
     * A right-click on a block with a line tool: Shift forgets point A; otherwise the block is point A, or, when A is
     * placed, point B.
     *
     * @return the two points {@code {A, B}} when this click finished a line (point A is forgotten then), else null
     */
    public static BlockPos[] click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        if (shift) {
            clear(player, tool);
            return null;
        }
        String dimension = player.serverLevel().dimension().location().toString();
        BlockPos first = dimension.equals(ShapeToolItem.data(tool).getString(DIMENSION_TAG)) ? first(tool) : null;
        if (first == null) {
            ShapeToolItem.editData(tool, tag -> {
                tag.putLong(POINT_TAG, pos.asLong());
                tag.putString(DIMENSION_TAG, dimension);
            });
            player.serverLevel().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .5f, 1.4f);
            player.displayClientMessage(Component.translatable("descentmtb.line.point"), true);
            return null;
        }
        forget(tool);
        return new BlockPos[]{first, pos.immutable()};
    }

    /** Forgets point A and tells the player. */
    public static void clear(ServerPlayer player, ItemStack tool) {
        forget(tool);
        player.displayClientMessage(Component.translatable("descentmtb.line.cleared"), true);
    }

    /** Forgets point A without a word (the tool was switched to another mode, say). */
    public static void forget(ItemStack tool) {
        ShapeToolItem.editData(tool, tag -> {
            tag.remove(POINT_TAG);
            tag.remove(DIMENSION_TAG);
        });
    }

    private LinePoints() {}
}
