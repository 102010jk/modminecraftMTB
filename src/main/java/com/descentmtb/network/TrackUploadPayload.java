package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.map.TrackBytes;
import com.descentmtb.map.TrackGeometry;
import com.descentmtb.map.TrailMarkerItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Player to server: a GPS recording ended and this is the finished track (packed points, may be empty if the ride was
 * too short). The server only accepts it for a GPS unit in the player's own inventory that carries the same recording
 * session, and checks the size and coordinates again.
 */
public record TrackUploadPayload(long session, int[] pts) implements CustomPacketPayload {
    public static final Type<TrackUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "track_upload"));

    public static final StreamCodec<FriendlyByteBuf, TrackUploadPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeLong(m.session);
                TrackBytes.write(b, m.pts);
            },
            b -> new TrackUploadPayload(b.readLong(), TrackBytes.read(b, TrackGeometry.MAX_POINTS)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TrackUploadPayload message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TrailMarkerItem.applyUpload(player, message.session, message.pts);
        }
    }
}
