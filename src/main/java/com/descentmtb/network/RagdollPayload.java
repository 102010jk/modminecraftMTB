package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/** Server → clients: this player just bailed, tumble them as a ragdoll. */
public record RagdollPayload(int playerId, float vx, float vy, float vz) implements CustomPacketPayload {

    public static final Type<RagdollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "ragdoll"));

    public static final StreamCodec<FriendlyByteBuf, RagdollPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeVarInt(m.playerId);
                b.writeFloat(m.vx);
                b.writeFloat(m.vy);
                b.writeFloat(m.vz);
            },
            b -> new RagdollPayload(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat()));

    /** Installed by the client mod (keeps client classes out of common code). */
    public static Consumer<RagdollPayload> clientHandler = m -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
