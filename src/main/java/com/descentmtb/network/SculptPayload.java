package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.ShapingBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: left click with a shaping item lowers the picked corner / edge / block. */
public record SculptPayload(BlockPos pos, double hitX, double hitY, double hitZ) implements CustomPacketPayload {
    public static final Type<SculptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "sculpt"));

    public static final StreamCodec<FriendlyByteBuf, SculptPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeBlockPos(m.pos);
                b.writeDouble(m.hitX);
                b.writeDouble(m.hitY);
                b.writeDouble(m.hitZ);
            },
            b -> new SculptPayload(b.readBlockPos(), b.readDouble(), b.readDouble(), b.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SculptPayload m, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ShapingBlockItem)) {
            return;
        }
        Vec3 hit = new Vec3(m.hitX, m.hitY, m.hitZ);
        if (!Double.isFinite(hit.x) || !Double.isFinite(hit.y) || !Double.isFinite(hit.z)
                || hit.distanceTo(Vec3.atCenterOf(m.pos)) > 3 || !player.level().isLoaded(m.pos)
                || player.distanceToSqr(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(player.level(),hit))>10*10) {
            return;
        }
        ShapingBlockItem.sculpt(player, stack, m.pos, hit, true);
    }
}
