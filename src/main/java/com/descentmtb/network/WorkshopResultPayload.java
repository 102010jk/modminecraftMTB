package com.descentmtb.network;

import com.descentmtb.custom.BikeBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Authoritative save acknowledgement: never display "saved" for a rejected edit. */
public record WorkshopResultPayload(BlockPos pos, boolean accepted, BikeBuild build) implements CustomPacketPayload {
    public static final Type<WorkshopResultPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb","workshop_result"));
    public static final StreamCodec<FriendlyByteBuf,WorkshopResultPayload> CODEC=StreamCodec.composite(
            BlockPos.STREAM_CODEC,WorkshopResultPayload::pos,ByteBufCodecs.BOOL,WorkshopResultPayload::accepted,
            BikeBuild.STREAM_CODEC,WorkshopResultPayload::build,WorkshopResultPayload::new);
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(WorkshopResultPayload message,IPayloadContext context) {
        com.descentmtb.client.custom.WorkshopScreens.result(message.pos,message.accepted,message.build);
    }
}
