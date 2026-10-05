package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Rider → server: "put me back" (R: the last safe point, Backspace: the start). The server knows where the
 * rider has been and where the trail starts, moves the bike itself and answers with a {@link BikeResyncPayload}.
 */
public record BikeRespawnPayload(boolean atStart) implements CustomPacketPayload {
    public static final Type<BikeRespawnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_respawn"));

    public static final StreamCodec<FriendlyByteBuf, BikeRespawnPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> b.writeBoolean(m.atStart),
            b -> new BikeRespawnPayload(b.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeRespawnPayload m, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) RiderServer.onRespawn(player, m.atStart);
    }
}
