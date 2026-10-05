package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.ramp.RampMath;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copycat-ramp editing of the Trail Shaper (the "Ramps" tab). One {@link Action} per mode: make a ramp out of a
 * block, then change its steepness, start height, profile or direction, or link a run of ramps into one slope.
 * Right-click applies the action, Shift + right-click applies the reverse. Every change goes through
 * {@link TrailEdit}, so it can be undone.
 */
public final class RampTuning {
    /** What a ramp mode does when it is clicked. */
    public enum Action {
        MAKE, STEEPNESS, START_HEIGHT, PROFILE, ROTATE, LINK;

        /** The action of a {@link ShapeMode.Kind#RAMP} mode. */
        public static Action of(ShapeMode mode) {
            return switch (mode) {
                case RAMP_MAKE -> MAKE;
                case RAMP_STEEPNESS -> STEEPNESS;
                case RAMP_START -> START_HEIGHT;
                case RAMP_PROFILE -> PROFILE;
                case RAMP_ROTATE -> ROTATE;
                case RAMP_LINK -> LINK;
                default -> throw new IllegalArgumentException(mode + " is not a ramp mode");
            };
        }
    }

    /** One click changes the START / END height by this many sixteenths of a block. */
    private static final int HEIGHT_STEP = 2;
    /** LINK limits: blocks along the run and vertical distance between the first and last block. */
    private static final int LINK_MAX_LENGTH = 32;
    private static final int LINK_MAX_DY = 8;

    /** First block of a pending LINK, and the dimension it is in. */
    private record Start(ResourceKey<Level> dimension, BlockPos pos) {}

    /** The pending LINK start per player. */
    private static final Map<UUID, Start> LINK_START = new HashMap<>();

    public static void clearSession() {
        LINK_START.clear();
    }

    /** Forgets the pending LINK start of a player who left. */
    static void forget(UUID player) {
        LINK_START.remove(player);
    }

    /** Forgets the pending LINK start of the player (when the tool changes mode). */
    public static void clearLink(Player player) {
        LINK_START.remove(player.getUUID());
    }

    /** True for the standalone copycat ramp block (not for shaped trail surfaces). */
    public static boolean isCopycatRamp(BlockState state) {
        return state.is(ModBlocks.RAMP.get());
    }

    /**
     * Applies one click of {@code action} on the block at {@code pos}.
     *
     * @param facing the player's horizontal facing: a new ramp rises away from the player
     * @param shift  the reverse: lower, previous profile, counter-clockwise, ramp facing the player
     * @return true when something was changed
     */
    public static boolean click(ServerPlayer player, BlockPos pos, Direction facing, boolean shift, Action action) {
        Level level = player.serverLevel();
        if (!player.mayBuild() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        try {
            if (action == Action.MAKE) {
                return make(level, player, pos, shift ? facing.getOpposite() : facing);
            }
            BlockState state = level.getBlockState(pos);
            if (!isCopycatRamp(state)) {
                message(player, "descentmtb.ramp_tune.not_ramp");
                return false;
            }
            switch (action) {
                case STEEPNESS -> setHeight(level, player, pos, state, RampBlock.END, shift, "descentmtb.ramp_tune.steepness");
                case START_HEIGHT -> setHeight(level, player, pos, state, RampBlock.START, shift, "descentmtb.ramp_tune.start_height");
                case PROFILE -> cycleProfile(level, player, pos, state, shift);
                case ROTATE -> rotate(level, player, pos, state, shift);
                case LINK -> link(level, player, pos, state, shift);
                default -> throw new IllegalStateException("unhandled action " + action);
            }
            return true;
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /** Replaces a plain full block or a shaped block by a kicker ramp that looks like the block it replaces. */
    private static boolean make(Level level, Player player, BlockPos pos, Direction rises) {
        BlockState current = level.getBlockState(pos);
        if (isCopycatRamp(current)) {
            message(player, "descentmtb.ramp_tune.already_ramp");
            return false;
        }
        BlockState material = materialOf(level, pos);
        if (material == null) {
            message(player, "descentmtb.ramp_tune.cannot_make");
            return false;
        }
        BlockState ramp = ModBlocks.RAMP.get().defaultBlockState()
                .setValue(RampBlock.FACING, rises)
                .setValue(RampBlock.START, 0)
                .setValue(RampBlock.END, 16)
                .setValue(RampBlock.PROFILE, RampBlock.Profile.CONCAVE);
        // the ramp replaces the whole shaped column: its other layers must not stay behind as a stale surface
        Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
        for (BlockPos other : ColumnEditor.otherLayers(level, pos)) {
            plan.put(other, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
        }
        plan.put(pos, new TrailEdit.Change(ramp, null, null, material, false));
        TrailEdit.apply(level, player, plan);
        playClick(level, pos);
        message(player, "descentmtb.ramp_tune.made");
        return true;
    }

    /** What a ramp made from this block should look like; null when the block cannot be turned into a ramp. */
    private static BlockState materialOf(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        if (entity instanceof TrailSurfaceEntity shaped) {
            return shaped.getMaterial();
        }
        BlockState state = level.getBlockState(pos);
        return entity == null && RampBlock.isValidMaterial(state, level, pos) ? state : null;
    }

    private static void setHeight(Level level, Player player, BlockPos pos, BlockState state,
                                  net.minecraft.world.level.block.state.properties.IntegerProperty property,
                                  boolean lower, String messageKey) {
        int value = Mth.clamp(state.getValue(property) + (lower ? -HEIGHT_STEP : HEIGHT_STEP), 0, 16);
        change(level, player, pos, state.setValue(property, value));
        message(player, messageKey, value);
    }

    private static void cycleProfile(Level level, Player player, BlockPos pos, BlockState state, boolean reverse) {
        RampBlock.Profile profile = state.getValue(RampBlock.PROFILE);
        profile = reverse ? profile.previous() : profile.next();
        change(level, player, pos, state.setValue(RampBlock.PROFILE, profile));
        player.displayClientMessage(Component.translatable("descentmtb.ramp_tune.profile",
                Component.translatable("descentmtb.ramp_tune.profile." + profile.getSerializedName())), true);
    }

    private static void rotate(Level level, Player player, BlockPos pos, BlockState state, boolean counterClockwise) {
        Direction facing = state.getValue(RampBlock.FACING);
        facing = counterClockwise ? facing.getCounterClockWise() : facing.getClockWise();
        change(level, player, pos, state.setValue(RampBlock.FACING, facing));
        player.displayClientMessage(Component.translatable("descentmtb.ramp_tune.facing",
                Component.translatable("descentmtb.ramp_tune.dir." + facing.getName())), true);
    }

    /** Applies a new state to a ramp through {@link TrailEdit}, keeping its copycat material and making it undoable. */
    private static void change(Level level, Player player, BlockPos pos, BlockState newState) {
        if (level.getBlockState(pos).equals(newState)) {
            return;
        }
        TrailEdit.apply(level, player, Map.of(pos, retag(level, pos, newState)));
        playClick(level, pos);
    }

    /** A change to {@code newState} that carries the block entity data of {@code pos} along. */
    private static TrailEdit.Change retag(Level level, BlockPos pos, BlockState newState) {
        CompoundTag tag = level.getBlockEntity(pos) instanceof RampBlockEntity ramp
                ? ramp.saveWithoutMetadata(level.registryAccess()) : null;
        return new TrailEdit.Change(newState, tag, null, null, false);
    }

    private static void playClick(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .4f, 1.3f);
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    /**
     * LINK: the first click (or any Shift-click) sets the start ramp; a second click on a ramp in line along the
     * start block's FACING axis re-shapes the whole run into one continuous slope with the start block's profile.
     * The surface runs from the start block's START (back edge) to the target's END (front edge, measured relative
     * to the first block's Y). Multi-block runs get a LINEAR piece of the curve per block; a single block keeps
     * the profile. Heights are clamped to 0..16.
     */
    private static void link(Level level, Player player, BlockPos pos, BlockState target, boolean restart) {
        Start start = LINK_START.get(player.getUUID());
        BlockPos first = start == null || !start.dimension().equals(level.dimension()) ? null : start.pos();
        BlockState firstState = first == null || !level.isLoaded(first) ? null : level.getBlockState(first);
        if (restart || firstState == null || !isCopycatRamp(firstState)) {
            LINK_START.put(player.getUUID(), new Start(level.dimension(), pos.immutable()));
            message(player, "descentmtb.ramp_tune.link.start_set");
            return;
        }
        if (first.equals(pos)) {
            LINK_START.remove(player.getUUID());
            message(player, "descentmtb.ramp_tune.link.cancelled");
            return;
        }

        Direction facing = firstState.getValue(RampBlock.FACING);
        int along = facing.getStepX() * (pos.getX() - first.getX()) + facing.getStepZ() * (pos.getZ() - first.getZ());
        int lateral = facing.getAxis() == Direction.Axis.X ? pos.getZ() - first.getZ() : pos.getX() - first.getX();
        int dy = pos.getY() - first.getY();
        if (lateral != 0 || along < 0) {
            message(player, "descentmtb.ramp_tune.link.not_in_line");
            return;
        }
        if (along + 1 > LINK_MAX_LENGTH || Math.abs(dy) > LINK_MAX_DY) {
            message(player, "descentmtb.ramp_tune.link.too_far");
            return;
        }

        int length = along + 1;
        Map<BlockPos, TrailEdit.Change> changes = linkedRun(level, first, firstState, target, facing, length, dy);
        if (!changes.isEmpty()) {
            TrailEdit.apply(level, player, changes);
        }
        LINK_START.remove(player.getUUID());
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .5f, 1f);
        message(player, "descentmtb.ramp_tune.link.done", length);
    }

    /** The new states of the ramps of a run of {@code length} blocks that rises {@code dy} blocks in total. */
    private static Map<BlockPos, TrailEdit.Change> linkedRun(Level level, BlockPos first, BlockState firstState,
                                                           BlockState target, Direction facing, int length, int dy) {
        int profile = firstState.getValue(RampBlock.PROFILE).ordinal();
        double startHeight = firstState.getValue(RampBlock.START);
        double endHeight = dy * 16 + target.getValue(RampBlock.END);
        Map<BlockPos, TrailEdit.Change> changes = new LinkedHashMap<>();
        for (int k = 0; k < length; k++) {
            long h0 = Math.round(startHeight + (endHeight - startHeight) * RampMath.profile(profile, (double) k / length));
            long h1 = Math.round(startHeight + (endHeight - startHeight) * RampMath.profile(profile, (double) (k + 1) / length));
            int levelOffset = levelOffset(k, length, dy, h0, h1);
            BlockPos p = first.offset(facing.getStepX() * k, levelOffset, facing.getStepZ() * k);
            if (!level.isLoaded(p)) {
                throw new TrailEdit.Rejected("descentmtb.ramp_tune.link.not_loaded");
            }
            BlockState existing = level.getBlockState(p);
            if (!isCopycatRamp(existing)) {
                continue;
            }
            BlockState updated = existing.setValue(RampBlock.FACING, facing)
                    .setValue(RampBlock.START, Mth.clamp((int) (h0 - 16L * levelOffset), 0, 16))
                    .setValue(RampBlock.END, Mth.clamp((int) (h1 - 16L * levelOffset), 0, 16))
                    .setValue(RampBlock.PROFILE, length == 1 ? firstState.getValue(RampBlock.PROFILE) : RampBlock.Profile.LINEAR);
            if (!updated.equals(existing)) {
                changes.put(p, retag(level, p, updated));
            }
        }
        return changes;
    }

    /** How many blocks above the first block the k-th ramp of the run sits. */
    private static int levelOffset(int k, int length, int dy, long h0, long h1) {
        if (k == 0) {
            return 0;
        }
        if (k == length - 1) {
            return dy;
        }
        long max = Math.max(h0, h1);
        long min = Math.min(h0, h1);
        return max == 0 && min == 0 ? 0 : (int) (Math.ceil(max / 16.0) - 1);
    }

    private RampTuning() {}
}
