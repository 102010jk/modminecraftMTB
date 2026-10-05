package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Server → rider: "your bike is here now". Sent when the server repositions the bike itself, either because it
 * rejected the client's state (a resync) or because the rider asked to respawn. The position is the bike
 * entity's (the ground point under the frame) and the client puts its simulation there.
 *
 * <p>{@code epoch} is the new reposition counter: the client echoes it in every state packet, so the server can
 * tell packets sent before the client heard about the move (stale, dropped) from fresh ones.
 */
public record BikeResyncPayload(int entityId, double x, double y, double z, float yawRad, int epoch, byte kind)
        implements CustomPacketPayload {

    /** Just put the bike back where the server has it (no message). */
    public static final byte RESYNC = 0;
    /** Respawned at the last safe point. */
    public static final byte RESPAWN = 1;
    /** Respawned at the ride's start. */
    public static final byte RESPAWN_START = 2;
    /** Respawned at the START sign of a timed trail (re-arms the trail timer). */
    public static final byte RESPAWN_SIGN = 3;

    public static final Type<BikeResyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "bike_resync"));

    public static final StreamCodec<FriendlyByteBuf, BikeResyncPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeVarInt(m.entityId);
                b.writeDouble(m.x);
                b.writeDouble(m.y);
                b.writeDouble(m.z);
                b.writeFloat(m.yawRad);
                b.writeVarInt(m.epoch);
                b.writeByte(m.kind);
            },
            b -> new BikeResyncPayload(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                    b.readFloat(), b.readVarInt(), b.readByte()));

    /** Installed by the client mod (keeps client classes out of common code). */
    public static Consumer<BikeResyncPayload> clientHandler = m -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
