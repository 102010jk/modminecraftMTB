package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.TrailRecords;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Rider to server: a trail run, or (with {@code timeMs == 0}) a question for the personal best on a trail.
 * The server validates the run, keeps the personal best and answers with a {@link TrailBestPayload}.
 */
public record TrailTimePayload(String trail, int timeMs) implements CustomPacketPayload {
    public static final Type<TrailTimePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_time"));

    public static final StreamCodec<FriendlyByteBuf, TrailTimePayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeUtf(m.trail, TrailRecords.NAME_LIMIT);
                b.writeVarInt(m.timeMs);
            },
            b -> new TrailTimePayload(b.readUtf(TrailRecords.NAME_LIMIT), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TrailTimePayload message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TrailRecords.handle(player, message.trail, message.timeMs);
        }
    }
}
