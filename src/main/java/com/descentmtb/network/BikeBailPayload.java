package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Rider → server: "I crashed". The server throws the player off the bike with
 * the rider's momentum and tells everyone to ragdoll them; the bike keeps
 * tumbling on its own (riderless physics on the server).
 */
public record BikeBailPayload(int entityId, double x, double y, double z, float vx, float vy, float vz)
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
            },
            b -> new BikeBailPayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readFloat(), b.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeBailPayload m, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Entity e = player.level().getEntity(m.entityId);
        if (!(e instanceof MountainBikeEntity bike) || player.getVehicle() != bike) return;
        if (!Double.isFinite(m.x) || !Double.isFinite(m.y) || !Double.isFinite(m.z)
                || player.distanceToSqr(m.x, m.y, m.z) > 16 * 16) return;
        Vec3 v = new Vec3(clamp(m.vx), clamp(m.vy), clamp(m.vz));   // m/s

        // Capture the final bike pose/momentum before detaching its rider.
        // The state packet is sent before this bail packet by the client.
        player.stopRiding();
        // rider's centre of mass is ~0.9 m above the feet
        Vec3 feet = com.descentmtb.world.SafeDismount.find(player.level(), player, new Vec3(m.x, m.y - .9, m.z));
        player.teleportTo(feet.x, feet.y, feet.z);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.setDeltaMovement(v.scale(0.05));                   // retain the throw, no artificial upward kick
        player.hurtMarked = true;
        player.resetFallDistance();
        RagdollPayload rag = new RagdollPayload(player.getId(), (float) v.x, (float) v.y, (float) v.z);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, rag);
        Ragdolls.start(player);
    }

    private static float clamp(float v) {
        return Float.isFinite(v) ? Math.max(-40f, Math.min(40f, v)) : 0f;
    }
}
