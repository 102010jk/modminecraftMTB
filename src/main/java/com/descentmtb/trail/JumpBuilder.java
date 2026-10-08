package com.descentmtb.trail;

import com.descentmtb.trail.JumpProfiles.Params;
import com.descentmtb.trail.JumpProfiles.Type;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;

/**
 * The jump builder of the Trail Shaper ({@link ShapeMode#JUMP_BUILD}): a right-click on a block opens the jump screen
 * on the client, and its Build button sends the chosen {@link Params} here. The jump starts at the clicked block,
 * runs the way the player faces and is centred across on the clicked block. Its surface is ONE height function
 * ({@link JumpProfiles}) sampled at the block corners, so neighbouring blocks meet exactly; blocks above one block
 * of height are stacked by {@link SurfacePlans} / {@link ColumnShaper} like every other planner does. The whole jump
 * is one {@link TrailEdit} step: undo with Ctrl+Z, protection and the survival price apply.
 *
 * <p>A full block in the off-hand is the material the jump is made of (trail dirt without one); in survival the visible
 * block of every column then costs one item of it, besides the trail dirt every edit costs.
 *
 * <p>The last settings are kept in the tool's custom data ({@link #TAG}), so the screen opens with them again and the
 * client can preview the jump where the player aims.
 */
public final class JumpBuilder {
    /** Custom data key of the last used settings. */
    public static final String TAG = "JumpProfile";
    /** How far (blocks, from the eyes) the clicked block may be. */
    public static final double REACH = 8;
    /** Air kept clear above the jump (blocks): plants and the like are removed. */
    private static final int HEADROOM = 2;

    /** The settings kept in the tool, {@link Params#DEFAULT} when there are none. */
    public static Params read(ItemStack tool) {
        var data = ShapeToolItem.data(tool);
        return data.contains(TAG) ? fromTag(data.getCompound(TAG)) : Params.DEFAULT;
    }

    public static void store(ItemStack tool, Params params) {
        ShapeToolItem.editData(tool, tag -> tag.put(TAG, toTag(params.clamped())));
    }

    public static CompoundTag toTag(Params p) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", p.type().name());
        tag.putInt("Length", p.length());
        tag.putInt("Width", p.width());
        tag.putInt("Height16", (int) Math.round(p.height() * 16));
        tag.putInt("Lip", p.lip());
        tag.putInt("Deck", p.deck());
        tag.putInt("Landing", p.landing());
        return tag;
    }

    public static Params fromTag(CompoundTag tag) {
        return new Params(Type.fromName(tag.getString("Type")), tag.getInt("Length"), tag.getInt("Width"),
                tag.getInt("Height16") / 16.0, tag.getInt("Lip"), tag.getInt("Deck"), tag.getInt("Landing")).clamped();
    }

    /** Where a jump built at the clicked block {@code pos} towards {@code facing} lies (see {@link JumpProfiles.Layout}). */
    public static JumpProfiles.Layout layout(BlockPos pos, Direction facing, Params params) {
        return new JumpProfiles.Layout(pos.getX(), pos.getY(), pos.getZ(), facing.getStepX(), facing.getStepZ(), params);
    }

    /**
     * Server side of the Build button. Checks the reach and the right to build, clamps the settings, remembers them
     * in the tool and builds the jump as one undoable edit.
     *
     * @return true when blocks were changed
     */
    public static boolean build(ServerPlayer player, ItemStack tool, BlockPos pos, Direction facing, Params requested) {
        Params params = requested.clamped();
        store(tool, params);
        ServerLevel level = player.serverLevel();
        if (!facing.getAxis().isHorizontal()) {
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
        try {
            int blocks = TrailEdit.applyConstruction(level, player, plan(level, layout(pos, facing, params), BlockEditor.offHandMaterial(player, pos)));
            level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .8f, .9f);
            player.displayClientMessage(summary(params), true);
            return blocks > 0;
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /** The block changes that build the jump: its surface on the footprint, the ground below filled, the air above cleared. */
    public static Map<BlockPos, TrailEdit.Change> plan(Level level, JumpProfiles.Layout layout) {
        return plan(level, layout, null);
    }

    /** Like {@link #plan(Level, JumpProfiles.Layout)}, made of {@code material} (a full block; null for trail dirt), paid for in survival. */
    public static Map<BlockPos, TrailEdit.Change> plan(Level level, JumpProfiles.Layout layout, BlockState material) {
        int[] box = layout.bounds();
        double reference = layout.y() + 1;
        return SurfacePlans.construction(level, box[0], box[1], box[2], box[3],
                (x, z) -> SurfacePlans.terrain(level, x, z, reference), layout::height, layout::contains, HEADROOM, material, material != null);
    }

    /** "Jump (kicker): 3 m long, 1.5 m high, lip 35°" for the action bar. */
    public static Component summary(Params p) {
        return describe(p, "descentmtb.jump.built");
    }

    /**
     * The settings in words: {@code key} gets the type, the length (m), the height (m) and the lip angle; for a jump
     * without a lip {@code key + "_no_lip"} gets the first three.
     */
    public static Component describe(Params p, String key) {
        Component type = Component.translatable(p.type().key());
        String height = String.format(Locale.ROOT, "%.4f", p.height()).replaceAll("0+$", "").replaceAll("\\.$", "");
        if (p.type().hasLip() || p.type() == Type.RAMP) {
            return Component.translatable(key, type, p.total(), height, Math.round(p.lipAngle()));
        }
        return Component.translatable(key + "_no_lip", type, p.total(), height);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private JumpBuilder() {}
}
