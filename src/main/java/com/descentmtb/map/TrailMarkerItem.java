package com.descentmtb.map;

import com.descentmtb.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * The trail GPS unit. Right-click starts or stops a recording; while it records, the client samples the bike
 * (or the player) every half block and, when the recording ends, uploads the finished track, which the server
 * stores on the unit. Right-click a trail sign with a recorded unit to link the track to that sign, see
 * {@link BikeparkMap}.
 */
public final class TrailMarkerItem extends Item {
    public TrailMarkerItem(Properties properties) {
        super(properties);
    }

    public static GpsState state(ItemStack stack) {
        return stack.getOrDefault(ModComponents.GPS_STATE.get(), GpsState.IDLE);
    }

    public static TrailTrack track(ItemStack stack) {
        return stack.get(ModComponents.TRAIL_TRACK.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            toggle(player, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void toggle(Player player, ItemStack stack) {
        GpsState state = state(stack);
        if (state.active()) {
            stack.set(ModComponents.GPS_STATE.get(), state.stopped());
            player.displayClientMessage(Component.translatable("descentmtb.gps.stopped"), true);
        } else {
            long session = player.getRandom().nextLong();
            stack.set(ModComponents.GPS_STATE.get(), new GpsState(session == 0 ? 1 : session, true));
            player.displayClientMessage(Component.translatable("descentmtb.gps.started"), true);
        }
    }

    /**
     * The client finished recording {@code session} and uploads the track. Finds the unit that carries that session
     * anywhere in the player's inventory, stores the track on it (if it is long enough) and puts the unit back to
     * idle. An unknown session (unit dropped, or a forged packet) is ignored.
     */
    public static void applyUpload(ServerPlayer player, long session, int[] pts) {
        if (session == 0) {
            return;
        }
        ItemStack unit = find(player.getInventory(), session);
        if (unit == null) {
            return;
        }
        unit.set(ModComponents.GPS_STATE.get(), GpsState.IDLE);
        int[] track = TrackGeometry.valid(pts) ? pts : new int[0];
        TrackGeometry.Stats stats = TrackGeometry.stats(track);
        if (TrackGeometry.count(track) < TrackGeometry.MIN_POINTS || stats.length() < TrackGeometry.MIN_LENGTH) {
            player.displayClientMessage(Component.translatable("descentmtb.gps.too_short"), true);
            return;
        }
        unit.set(ModComponents.TRAIL_TRACK.get(), new TrailTrack(track));
        player.displayClientMessage(Component.translatable("descentmtb.gps.saved", meters(stats.length()), meters(stats.descent())), true);
    }

    private static ItemStack find(Inventory inventory, long session) {
        int size = inventory.getContainerSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof TrailMarkerItem && state(stack).session() == session) {
                return stack;
            }
        }
        return null;
    }

    public static String meters(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return state(stack).active();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (state(stack).active()) {
            lines.add(Component.translatable("descentmtb.gps.tooltip.recording").withStyle(ChatFormatting.RED));
        }
        TrailTrack track = track(stack);
        if (track == null) {
            lines.add(Component.translatable("descentmtb.gps.tooltip.empty").withStyle(ChatFormatting.GRAY));
        } else {
            TrackGeometry.Stats s = track.stats();
            lines.add(Component.translatable("descentmtb.gps.tooltip.length", meters(s.length())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("descentmtb.gps.tooltip.descent", meters(s.descent())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("descentmtb.gps.tooltip.ascent", meters(s.ascent())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("descentmtb.gps.tooltip.grade", String.format(Locale.ROOT, "%.1f", s.grade()))
                    .withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("descentmtb.gps.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
