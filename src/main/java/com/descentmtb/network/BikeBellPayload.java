package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeBells;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Rider to server: "ring my bell". The server plays the bell of the bike's build for everyone nearby, with a cooldown. */
public record BikeBellPayload() implements CustomPacketPayload {
    public static final Type<BikeBellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_bell"));

    public static final StreamCodec<FriendlyByteBuf, BikeBellPayload> CODEC = StreamCodec.unit(new BikeBellPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeBellPayload m, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            BikeBells.ring(player);
        }
    }
}
