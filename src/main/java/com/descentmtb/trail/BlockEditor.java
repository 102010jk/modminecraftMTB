package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Server side of the block editor (Ctrl + right-click on a block with the Trail Shaper opens the editor screen on the
 * client): the four corner heights of ONE block, typed in sixteenths relative to the block's frame
 * ({@link ShapePresets.Editable#editorFrame}), the deck flag and optionally the material from the off-hand. Only the edited column
 * changes, rebuilt by {@link ColumnEditor#rebuild} as one {@link TrailEdit} (undo, protection and the survival price
 * apply; a new material is paid for with one item of it, like a right-click with the block on a ramp).
 */
public final class BlockEditor {
    /** How far (blocks, from the eyes) the edited block may be. */
    public static final double REACH = 8;

    /**
     * @param sixteenths      new corner heights NW NE SW SE in sixteenths above the block's editor frame (clamped)
     * @param deck            the column becomes a wooden deck (true) or ground
     * @param offHandMaterial the block in the off-hand becomes the material
     * @return true when the block changed
     */
    public static boolean apply(ServerPlayer player, BlockPos pos, int[] sixteenths, boolean deck, boolean offHandMaterial) {
        ServerLevel level = player.serverLevel();
        if (sixteenths.length != 4) {
            return false;
        }
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
            message(player, "descentmtb.jump.too_far");
            return false;
        }
        if (!player.mayBuild() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            message(player, "descentmtb.edit.not_allowed");
            return false;
        }
        ShapePresets.Editable editable = ShapePresets.editable(level, pos);
        if (editable == null) {
            message(player, "descentmtb.editor.not_editable");
            return false;
        }
        ColumnEditor.Column column = editable.column();
        double[] local = CornerEdits.fromSixteenths(sixteenths);
        double[] newAbs = new double[4];
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            newAbs[i] = editable.editorFrame() + local[i];
            moved |= Math.abs(newAbs[i] - column.abs()[i]) > 1e-6;
        }

        BlockState material = column.material();
        boolean pay = false;
        if (offHandMaterial) {
            BlockState chosen = offHandMaterial(player, pos);
            if (chosen == null) {
                message(player, "descentmtb.editor.bad_material");
                return false;
            }
            if (chosen.getBlock() != material.getBlock()) {
                material = chosen;
                pay = true;
            }
        }
        if (!moved && deck == column.deck() && !pay) {
            message(player, "descentmtb.editor.unchanged");
            return false;
        }
        try {
            Map<BlockPos, TrailEdit.Change> changes = ColumnEditor.rebuild(level, column, newAbs, deck, material);
            if (pay) {
                payOnTop(changes);
            }
            TrailEdit.apply(level, player, changes);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
        level.playSound(null, pos, material.getSoundType().getPlaceSound(), SoundSource.BLOCKS, .6f, 1f);
        message(player, "descentmtb.editor.applied");
        return true;
    }

    /** The block in the player's off-hand as a copycat material, or null when it cannot be one. */
    public static BlockState offHandMaterial(ServerPlayer player, BlockPos pos) {
        ItemStack held = player.getOffhandItem();
        if (!(held.getItem() instanceof BlockItem item)) {
            return null;
        }
        BlockState state = item.getBlock().defaultBlockState();
        return RampBlock.isValidMaterial(state, player.level(), pos) ? state : null;
    }

    /** Marks the highest shaped layer of the plan (the visible surface) as the one the new material is paid on. */
    private static void payOnTop(Map<BlockPos, TrailEdit.Change> changes) {
        BlockPos top = null;
        for (var entry : changes.entrySet()) {
            if (entry.getValue().heights() != null && (top == null || entry.getKey().getY() > top.getY())) {
                top = entry.getKey();
            }
        }
        if (top != null) {
            changes.put(top, changes.get(top).paid());
        }
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    private BlockEditor() {}
}
