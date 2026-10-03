package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Ladit rampu" mode of the Trail Builder: adjusts the {@link RampBlock} under the cursor.
 *
 * <p>The sub-action (steepness, start height, profile, rotate, link) is chosen with Shift + mouse wheel
 * and kept on the wand; right-click applies it, Shift + right-click applies the reverse.
 */
public final class RampTuning {
    public enum SubAction {
        STEEPNESS, START_HEIGHT, PROFILE, ROTATE, LINK;

        public SubAction next(int step) {
            return values()[Math.floorMod(ordinal() + step, values().length)];
        }

        public Component displayName() {
            return Component.translatable("descentmtb.ramp_tune.action." + name().toLowerCase());
        }
    }

    /** LINK limits: blocks along the run and vertical distance between the first and last block. */
    private static final int LINK_MAX_LENGTH = 32;
    private static final int LINK_MAX_DY = 8;

    /** First block of a pending LINK per player. */
    private static final Map<UUID, BlockPos> LINK_START = new HashMap<>();

    public static void clearSession() {
        LINK_START.clear();
    }

    /** Applies one click of the chosen sub-action on the ramp at {@code pos}. */
    public static void tune(Level level, Player player, BlockPos pos, SubAction action, boolean reverse) {
        BlockState state = level.getBlockState(pos);
        if (!RampBlock.isRamp(state)) {
            return;
        }
        switch (action) {
            case STEEPNESS -> {
                int end = Mth.clamp(state.getValue(RampBlock.END) + (reverse ? -2 : 2), 0, 16);
                change(level, pos, state.setValue(RampBlock.END, end));
                message(player, "descentmtb.ramp_tune.steepness", end);
            }
            case START_HEIGHT -> {
                int start = Mth.clamp(state.getValue(RampBlock.START) + (reverse ? -2 : 2), 0, 16);
                change(level, pos, state.setValue(RampBlock.START, start));
                message(player, "descentmtb.ramp_tune.start_height", start);
            }
            case PROFILE -> {
                RampBlock.Profile profile = state.getValue(RampBlock.PROFILE);
                profile = reverse ? profile.previous() : profile.next();
                change(level, pos, state.setValue(RampBlock.PROFILE, profile));
                player.displayClientMessage(Component.translatable("descentmtb.ramp_tune.profile",
                        Component.translatable("descentmtb.ramp_tune.profile." + profile.getSerializedName())), true);
            }
            case ROTATE -> {
                Direction facing = state.getValue(RampBlock.FACING);
                facing = reverse ? facing.getCounterClockWise() : facing.getClockWise();
                change(level, pos, state.setValue(RampBlock.FACING, facing));
                player.displayClientMessage(Component.translatable("descentmtb.ramp_tune.facing",
                        Component.translatable("descentmtb.ramp_tune.dir." + facing.getName())), true);
            }
            case LINK -> link(level, player, pos, state, reverse);
        }
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void change(Level level, BlockPos pos, BlockState newState) {
        level.setBlock(pos, newState, 3);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.3F);
    }

    /**
     * LINK: the first click (or any Shift-click) sets the start ramp; a second click on a ramp in line along
     * the start block's FACING axis re-shapes the whole run into one continuous slope with the start block's
     * profile. The surface runs from the start block's START (back edge) to the target's END (front edge,
     * measured relative to the first block's Y). Multi-block runs get a LINEAR piece of the curve per block;
     * a single block keeps the profile. Values are clamped to 0..16.
     */
    private static void link(Level level, Player player, BlockPos pos, BlockState target, boolean reverse) {
        BlockPos first = LINK_START.get(player.getUUID());
        BlockState firstState = first == null ? null : level.getBlockState(first);
        if (reverse || firstState == null || !RampBlock.isRamp(firstState)) {
            LINK_START.put(player.getUUID(), pos);
            message(player, "descentmtb.ramp_tune.link.start_set");
            return;
        }
        if (first.equals(pos)) {
            LINK_START.remove(player.getUUID());
            message(player, "descentmtb.ramp_tune.link.cancelled");
            return;
        }

        Direction facing = firstState.getValue(RampBlock.FACING);
        int dx = pos.getX() - first.getX();
        int dz = pos.getZ() - first.getZ();
        int dy = pos.getY() - first.getY();
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int along = alongX ? dx * facing.getStepX() : dz * facing.getStepZ();
        int lateral = alongX ? dz : dx;
        if (lateral != 0 || along < 0) {
            message(player, "descentmtb.ramp_tune.link.not_in_line");
            return;
        }
        if (along + 1 > LINK_MAX_LENGTH || Math.abs(dy) > LINK_MAX_DY) {
            message(player, "descentmtb.ramp_tune.link.too_far");
            return;
        }

        int profile = firstState.getValue(RampBlock.PROFILE).ordinal();
        int length = along + 1;
        double startHeight = firstState.getValue(RampBlock.START);
        double endHeight = dy * 16 + target.getValue(RampBlock.END);
        for (int k = 0; k < length; k++) {
            double s0 = (double) k / length;
            double s1 = (double) (k + 1) / length;
            long h0 = Math.round(startHeight + (endHeight - startHeight) * RampMath.profile(profile, s0));
            long h1 = Math.round(startHeight + (endHeight - startHeight) * RampMath.profile(profile, s1));
            int levelOffset;
            if (k == 0) {
                levelOffset = 0;
            } else if (k == length - 1) {
                levelOffset = dy;
            } else {
                long max = Math.max(h0, h1);
                long min = Math.min(h0, h1);
                levelOffset = (max == 0 && min == 0) ? 0 : (int) (Math.ceil(max / 16.0) - 1);
            }
            BlockPos p = first.offset(facing.getStepX() * k, levelOffset, facing.getStepZ() * k);
            BlockState existing = level.getBlockState(p);
            if (!RampBlock.isRamp(existing)) {
                continue;
            }
            BlockState updated = existing.setValue(RampBlock.FACING, facing)
                    .setValue(RampBlock.START, Mth.clamp((int) (h0 - 16L * levelOffset), 0, 16))
                    .setValue(RampBlock.END, Mth.clamp((int) (h1 - 16L * levelOffset), 0, 16))
                    .setValue(RampBlock.PROFILE, length == 1 ? firstState.getValue(RampBlock.PROFILE) : RampBlock.Profile.LINEAR);
            if (updated != existing) {
                level.setBlock(p, updated, 3);
            }
        }
        LINK_START.remove(player.getUUID());
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
        message(player, "descentmtb.ramp_tune.link.done", length);
    }

    private RampTuning() {}
}
