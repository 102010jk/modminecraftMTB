package com.descentmtb.ramp;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Trail Tool: shapes {@link RampBlock}s. The mode (and the LINK start position) live in the item's
 * {@code CUSTOM_DATA} ({@code mode} int, {@code link} int[3]). Sneak + right-click in the air cycles the
 * mode; right-click on a ramp applies it (sneak = the reverse action; in LINK mode sneak sets the start).
 */
public class TrailToolItem extends Item {
    public enum Mode {
        STEEPNESS("steepness"), START_HEIGHT("start_height"), PROFILE("profile"), ROTATE("rotate"), LINK("link");

        public final String key;

        Mode(String key) { this.key = key; }

        public Component displayName() { return Component.translatable("descentmtb.trail_tool.mode." + key); }

        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** LINK limits: max blocks along the run and max vertical distance between first and last block. */
    private static final int LINK_MAX_LENGTH = 32;
    private static final int LINK_MAX_DY = 8;

    public TrailToolItem(Properties props) { super(props); }

    // ---- stored data ---------------------------------------------------------------------------------

    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int m = tag.getInt("mode");
        return Mode.values()[Math.floorMod(m, Mode.values().length)];
    }

    private static void setMode(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt("mode", mode.ordinal());
            tag.remove("link");
        });
    }

    private static BlockPos getLink(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int[] a = tag.getIntArray("link");
        return a.length == 3 ? new BlockPos(a[0], a[1], a[2]) : null;
    }

    private static void setLink(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (pos == null) tag.remove("link");
            else tag.putIntArray("link", new int[]{pos.getX(), pos.getY(), pos.getZ()});
        });
    }

    // ---- interaction ---------------------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
            if (hit.getType() == HitResult.Type.MISS) {
                if (!level.isClientSide) {
                    Mode next = getMode(stack).next();
                    setMode(stack, next);
                    player.displayClientMessage(Component.translatable("descentmtb.trail_tool.mode", next.displayName()), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!RampBlock.isRamp(state)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = ctx.getItemInHand();
        boolean sneak = player.isShiftKeyDown();
        Mode mode = getMode(stack);

        switch (mode) {
            case STEEPNESS -> {
                int end = Mth.clamp(state.getValue(RampBlock.END) + (sneak ? -2 : 2), 0, 16);
                apply(level, pos, state.setValue(RampBlock.END, end));
                msg(player, "descentmtb.trail_tool.steepness", end);
            }
            case START_HEIGHT -> {
                int st = Mth.clamp(state.getValue(RampBlock.START) + (sneak ? -2 : 2), 0, 16);
                apply(level, pos, state.setValue(RampBlock.START, st));
                msg(player, "descentmtb.trail_tool.start_height", st);
            }
            case PROFILE -> {
                RampBlock.Profile p = state.getValue(RampBlock.PROFILE);
                p = sneak ? p.previous() : p.next();
                apply(level, pos, state.setValue(RampBlock.PROFILE, p));
                player.displayClientMessage(Component.translatable("descentmtb.trail_tool.profile",
                        Component.translatable("descentmtb.trail_tool.profile." + p.getSerializedName())), true);
            }
            case ROTATE -> {
                Direction f = state.getValue(RampBlock.FACING);
                f = sneak ? f.getCounterClockWise() : f.getClockWise();
                apply(level, pos, state.setValue(RampBlock.FACING, f));
                player.displayClientMessage(Component.translatable("descentmtb.trail_tool.facing",
                        Component.translatable("descentmtb.trail_tool.dir." + f.getName())), true);
            }
            case LINK -> link(level, player, stack, pos, state, sneak);
        }
        return InteractionResult.SUCCESS;
    }


    private static void msg(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static void apply(Level level, BlockPos pos, BlockState newState) {
        level.setBlock(pos, newState, 3);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.3F);
    }

    /**
     * LINK: first click (or any sneak-click) sets the start ramp; the second click on a ramp in line along the
     * first block's FACING axis (up to {@value #LINK_MAX_LENGTH} blocks ahead, up to {@value #LINK_MAX_DY}
     * blocks higher/lower) re-shapes the whole run into one continuous slope with the first block's profile.
     * The surface runs from the first block's START (at its back edge) to the target's END (at its front edge,
     * measured relative to the first block's Y). Each block of a multi-block run gets a LINEAR piece of the
     * curve (block-resolution approximation of the profile); a single-block run keeps the profile. Each block is
     * looked up at the Y level the curve passes through at its column; a block missing there is skipped, and
     * values are clamped to 0..16, so a rise inside one block that crosses a level boundary is truncated.
     */
    private void link(Level level, Player player, ItemStack stack, BlockPos pos, BlockState target, boolean sneak) {
        BlockPos first = getLink(stack);
        if (sneak || first == null) {
            setLink(stack, pos);
            msg(player, "descentmtb.trail_tool.link.start_set");
            return;
        }
        BlockState fs = level.getBlockState(first);
        if (!RampBlock.isRamp(fs)) {
            setLink(stack, pos);
            msg(player, "descentmtb.trail_tool.link.start_set");
            return;
        }
        if (first.equals(pos)) {
            setLink(stack, null);
            msg(player, "descentmtb.trail_tool.link.cancelled");
            return;
        }
        Direction f = fs.getValue(RampBlock.FACING);
        int dx = pos.getX() - first.getX(), dz = pos.getZ() - first.getZ(), dy = pos.getY() - first.getY();
        boolean xAxis = f.getAxis() == Direction.Axis.X;
        int n = xAxis ? dx * f.getStepX() : dz * f.getStepZ();
        int lateral = xAxis ? dz : dx;
        if (lateral != 0 || n < 0) {
            msg(player, "descentmtb.trail_tool.link.not_in_line");
            return;
        }
        if (n + 1 > LINK_MAX_LENGTH || Math.abs(dy) > LINK_MAX_DY) {
            msg(player, "descentmtb.trail_tool.link.too_far");
            return;
        }

        int profile = fs.getValue(RampBlock.PROFILE).ordinal();
        int len = n + 1;
        double a0 = fs.getValue(RampBlock.START);
        double a1 = dy * 16 + target.getValue(RampBlock.END);
        int changed = 0;
        for (int k = 0; k < len; k++) {
            double s0 = (double) k / len, s1 = (double) (k + 1) / len;
            double h0 = a0 + (a1 - a0) * RampMath.profile(profile, s0);
            double h1 = a0 + (a1 - a0) * RampMath.profile(profile, s1);
            long r0 = Math.round(h0), r1 = Math.round(h1);
            int lvl;
            if (k == 0) lvl = 0;
            else if (k == len - 1) lvl = dy;
            else {
                long max = Math.max(r0, r1), min = Math.min(r0, r1);
                lvl = (max == 0 && min == 0) ? 0 : (int) (Math.ceil(max / 16.0) - 1);
            }
            BlockPos p = first.offset(f.getStepX() * k, lvl, f.getStepZ() * k);
            BlockState bs = level.getBlockState(p);
            if (!RampBlock.isRamp(bs)) continue;
            int st = Mth.clamp((int) (r0 - 16L * lvl), 0, 16);
            int en = Mth.clamp((int) (r1 - 16L * lvl), 0, 16);
            BlockState ns = bs.setValue(RampBlock.FACING, f)
                    .setValue(RampBlock.START, st)
                    .setValue(RampBlock.END, en)
                    .setValue(RampBlock.PROFILE, len == 1 ? fs.getValue(RampBlock.PROFILE) : RampBlock.Profile.LINEAR);
            if (ns != bs) {
                level.setBlock(p, ns, 3);
                changed++;
            }
        }
        setLink(stack, null);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 1.0F);
        msg(player, "descentmtb.trail_tool.link.done", len);
    }

    // ---- tooltip -------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.trail_tool.mode", getMode(stack).displayName()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("descentmtb.trail_tool.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
