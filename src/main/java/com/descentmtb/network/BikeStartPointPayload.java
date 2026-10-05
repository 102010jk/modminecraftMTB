package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Rider → server: "I just armed the START sign at this position". The server checks it really is a START sign
 * close to the rider and remembers it as that player's respawn-at-start point (see {@link BikeRespawnPayload}).
 */
public record BikeStartPointPayload(BlockPos sign) implements CustomPacketPayload {
    public static final Type<BikeStartPointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_start_point"));

    public static final StreamCodec<FriendlyByteBuf, BikeStartPointPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> b.writeBlockPos(m.sign),
            b -> new BikeStartPointPayload(b.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeStartPointPayload m, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) RiderServer.onStartPoint(player, m.sign);
    }
}
