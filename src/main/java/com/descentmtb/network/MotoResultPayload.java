package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.MotoBuild;
import com.descentmtb.custom.MotoBuildCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to the motorbike workshop screen: the authoritative answer to a {@link MotoApplyPayload}. */
public record MotoResultPayload(BlockPos pos, boolean accepted, MotoBuild moto) implements CustomPacketPayload {
    public static final Type<MotoResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "moto_result"));

    public static final StreamCodec<FriendlyByteBuf, MotoResultPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MotoResultPayload::pos,
            ByteBufCodecs.BOOL, MotoResultPayload::accepted,
            MotoBuildCodecs.STREAM_CODEC, MotoResultPayload::moto,
            MotoResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MotoResultPayload m, IPayloadContext ctx) {
        com.descentmtb.client.custom.WorkshopScreens.motoResult(m.pos, m.accepted, m.moto);
    }
}
