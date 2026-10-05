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
 * it is plausible ({@link RiderServer#onState}), moves the entity, and the visual
 * part is broadcast to everyone else through synced entity data.
 *
 * <p>{@code epoch} is the last server-side reposition the client has seen (see
 * {@link BikeResyncPayload}); packets from before it are stale and dropped.
 */
public record BikeStatePayload(int entityId, double x, double y, double z, float yaw, float pitch,
                               float lean, float steer, float compF, float compR,
                               float riderUp, float riderFwd, float crank, byte flags,
                               float vx, float vy, float vz, int trickId, float trickAmount, float trickProgress, int trickSide, float brake,
                               int epoch)
        implements CustomPacketPayload {

    public static final byte AIRBORNE = 1, BAILED = 2, TELEPORT = 4, WALL_RIDE = 8;

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
        b.writeVarInt(trickId);
        b.writeFloat(trickAmount);
        b.writeFloat(trickProgress);
        b.writeByte(trickSide);
        b.writeFloat(brake);
        b.writeVarInt(epoch);
    }

    private static BikeStatePayload read(FriendlyByteBuf b) {
        return new BikeStatePayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readByte(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readFloat(), b.readFloat(), b.readByte(), b.readFloat(),
                b.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BikeStatePayload msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Entity e = player.level().getEntity(msg.entityId);
        // packets that arrive just after a dismount are normal and simply ignored
        if (!(e instanceof MountainBikeEntity bike) || player.getVehicle() != bike) return;
        RiderServer.onState(player, bike, msg);
    }
}
