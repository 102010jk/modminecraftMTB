package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Rider → server: "I crashed". If {@link BailRules} believe it, the server throws the player off the bike with
 * the rider's momentum and tells everyone to ragdoll them; the bike keeps tumbling on its own (riderless
 * physics on the server). See {@link RiderServer#onBail}.
 */
public record BikeBailPayload(int entityId, double x, double y, double z, float vx, float vy, float vz,
                              float yawRate, float pitchRate, int epoch)
        implements CustomPacketPayload {

    public static final Type<BikeBailPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_bail"));

    public static final StreamCodec<FriendlyByteBuf, BikeBailPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeVarInt(m.entityId);
                b.writeDouble(m.x);
                b.writeDouble(m.y);
                b.writeDouble(m.z);
                b.writeFloat(m.vx);
                b.writeFloat(m.vy);
                b.writeFloat(m.vz);
                b.writeFloat(m.yawRate);
                b.writeFloat(m.pitchRate);
                b.writeVarInt(m.epoch);
            },
            b -> new BikeBailPayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeBailPayload m, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Entity e = player.level().getEntity(m.entityId);
        if (!(e instanceof MountainBikeEntity bike) || player.getVehicle() != bike) return;
        RiderServer.onBail(player, bike, m);
    }
}
