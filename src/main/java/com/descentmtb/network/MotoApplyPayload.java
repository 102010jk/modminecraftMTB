package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeStands;
import com.descentmtb.custom.MotoBuild;
import com.descentmtb.custom.MotoBuildCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Motorbike workshop screen to server: save this paint and tuning on the motorbike standing on the stand at {@code pos}.
 * The server checks reach, permission, that the stand holds a motorbike ({@link BikeStands#applyMoto}); the values
 * are clamped by {@link MotoBuild} itself. It answers with a {@link MotoResultPayload}; the stand syncs the new look to
 * every viewer.
 */
public record MotoApplyPayload(BlockPos pos, MotoBuild moto) implements CustomPacketPayload {
    public static final Type<MotoApplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "moto_apply"));

    public static final StreamCodec<FriendlyByteBuf, MotoApplyPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MotoApplyPayload::pos,
            MotoBuildCodecs.STREAM_CODEC, MotoApplyPayload::moto,
            MotoApplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MotoApplyPayload m, IPayloadContext ctx) {
        boolean accepted = BikeStands.applyMoto(ctx.player(), m.pos, m.moto);
        var stand = BikeStands.usable(ctx.player(), m.pos);
        PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) ctx.player(),
                new MotoResultPayload(m.pos, accepted, accepted && stand != null ? stand.moto() : m.moto));
    }
}
