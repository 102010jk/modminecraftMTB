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
 * Rider → server, every tick: the bike state computed by the rider's client
 * (the rider's client is authoritative, like vanilla boats). The server checks
 * it is plausible, moves the entity, and the visual part is broadcast to
 * everyone else through synced entity data.
 */
public record BikeStatePayload(int entityId, double x, double y, double z, float yaw, float pitch,
                               float lean, float steer, float compF, float compR,
                               float riderUp, float riderFwd, float crank, byte flags,
                               float vx, float vy, float vz)
        implements CustomPacketPayload {

    public static final byte AIRBORNE = 1, BAILED = 2, TELEPORT = 4;

    public static final Type<BikeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_state"));

    public static final StreamCodec<FriendlyByteBuf, BikeStatePayload> CODEC =
            StreamCodec.ofMember(BikeStatePayload::write, BikeStatePayload::read);

    private void write(FriendlyByteBuf b) {
        b.writeVarInt(entityId);
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeFloat(yaw);
        b.writeFloat(pitch);
        b.writeFloat(lean);
        b.writeFloat(steer);
        b.writeFloat(compF);
        b.writeFloat(compR);
        b.writeFloat(riderUp);
        b.writeFloat(riderFwd);
        b.writeFloat(crank);
        b.writeByte(flags);
        b.writeFloat(vx);
        b.writeFloat(vy);
        b.writeFloat(vz);
    }

    private static BikeStatePayload read(FriendlyByteBuf b) {
        return new BikeStatePayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(),
                b.readFloat(), b.readFloat(), b.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeStatePayload msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Entity e = player.level().getEntity(msg.entityId);
        if (!(e instanceof MountainBikeEntity bike) || player.getVehicle() != bike) return;
        if (!Double.isFinite(msg.x) || !Double.isFinite(msg.y) || !Double.isFinite(msg.z)) return;
        double d2 = bike.distanceToSqr(msg.x, msg.y - MountainBikeEntity.COM_HEIGHT, msg.z);
        double limit = (msg.flags & TELEPORT) != 0 ? 96 : 12;   // 12 blocks a tick = 860 km/h
        if (d2 > limit * limit) {
            DescentMtb.LOG.warn("{} bike moved too far in one tick ({} blocks), ignoring", player.getName().getString(), Math.sqrt(d2));
            return;
        }
        bike.applyRiderState(msg);
    }
}
