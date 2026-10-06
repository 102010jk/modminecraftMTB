package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeStands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Workshop screen to server: take the bike off the stand at {@code pos} into my inventory ({@link BikeStands#take}). */
public record WorkshopTakePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<WorkshopTakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "workshop_take"));

    public static final StreamCodec<FriendlyByteBuf, WorkshopTakePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WorkshopTakePayload::pos, WorkshopTakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(WorkshopTakePayload m, IPayloadContext ctx) {
        BikeStands.take(ctx.player(), m.pos);
    }
}
