package com.descentmtb.client.map;

import com.descentmtb.client.BikeClientController;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.map.GpsState;
import com.descentmtb.map.TrackRecorder;
import com.descentmtb.map.TrailMarkerItem;
import com.descentmtb.network.TrackUploadPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the GPS unit. While a unit in the hotbar or offhand is recording, every client tick the position of
 * the bike the player rides (or the player on foot) is offered to a {@link TrackRecorder}. When the unit stops
 * recording, leaves the hotbar or the player leaves the world, the finished track is uploaded to the server under the
 * recording's session id (see {@link TrailMarkerItem#applyUpload}).
 */
public final class GpsRecorderClient {
    private static TrackRecorder recorder;
    private static long session;
    private static int hudTicks;

    public static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            recorder = null;
            session = 0;
            return;
        }
        long active = activeSession(player, session);
        if (session != 0 && active != session) {
            finish();
        }
        if (session == 0 && active != 0) {
            session = active;
            recorder = new TrackRecorder();
            hudTicks = 0;
        }
        if (session == 0) {
            return;
        }
        MountainBikeEntity bike = BikeClientController.riding();
        Vec3 at = bike != null && !bike.isRemoved() ? bike.position() : player.position();
        recorder.offer(at.x, at.y, at.z);
        if (hudTicks++ % 20 == 0) {
            mc.gui.setOverlayMessage(Component.translatable("descentmtb.gps.hud", TrailMarkerItem.meters(recorder.travelled())), false);
        }
    }

    /** Session of an active GPS unit in the hotbar or offhand: {@code preferred} if that one is still there, else any, else 0. */
    private static long activeSession(Player player, long preferred) {
        long found = 0;
        for (int slot = 0; slot < 9; slot++) {
            found = pick(player.getInventory().getItem(slot), preferred, found);
        }
        found = pick(player.getOffhandItem(), preferred, found);
        return found;
    }

    private static long pick(ItemStack stack, long preferred, long found) {
        if (!(stack.getItem() instanceof TrailMarkerItem)) {
            return found;
        }
        GpsState state = TrailMarkerItem.state(stack);
        if (!state.active()) {
            return found;
        }
        return state.session() == preferred ? preferred : found == 0 ? state.session() : found;
    }

    private static void finish() {
        if (recorder != null) {
            PacketDistributor.sendToServer(new TrackUploadPayload(session, recorder.finish()));
        }
        recorder = null;
        session = 0;
    }

    private GpsRecorderClient() {}
}
