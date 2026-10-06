package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeStands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Workshop screen to server: save this build on the bike standing on the stand at {@code pos}. The server checks reach,
 * permission and that the stand holds a bike, and clamps the build to what the bike type allows
 * ({@link BikeStands#apply}); the stand then syncs the result to every viewer.
 */
public record WorkshopApplyPayload(BlockPos pos, BikeBuild build) implements CustomPacketPayload {
    public static final Type<WorkshopApplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "workshop_apply"));

    public static final StreamCodec<FriendlyByteBuf, WorkshopApplyPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WorkshopApplyPayload::pos,
            BikeBuild.STREAM_CODEC, WorkshopApplyPayload::build,
            WorkshopApplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(WorkshopApplyPayload m, IPayloadContext ctx) {
        BikeStands.apply(ctx.player(), m.pos, m.build);
    }
}
