package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.TrailRecords;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Server to rider: the personal best on a trail, and the verdict on a run that was just submitted.
 *
 * @param trail      trail name as the rider sent it
 * @param timeMs     the submitted run, or 0 if this only answers a question for the best time
 * @param bestMs     personal best after this run, or -1 if the rider has none yet
 * @param previousMs personal best before this run, or -1 if there was none
 * @param record     true if the submitted run is a new personal best
 */
public record TrailBestPayload(String trail, int timeMs, int bestMs, int previousMs, boolean record)
        implements CustomPacketPayload {
    public static final Type<TrailBestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_best"));

    public static final StreamCodec<FriendlyByteBuf, TrailBestPayload> CODEC = StreamCodec.ofMember(
            (m, b) -> {
                b.writeUtf(m.trail, TrailRecords.NAME_LIMIT);
                b.writeVarInt(m.timeMs);
                b.writeInt(m.bestMs);
                b.writeInt(m.previousMs);
                b.writeBoolean(m.record);
            },
            b -> new TrailBestPayload(b.readUtf(TrailRecords.NAME_LIMIT), b.readVarInt(), b.readInt(), b.readInt(),
                    b.readBoolean()));

    /** Installed by the client mod (keeps client classes out of common code). */
    public static Consumer<TrailBestPayload> clientHandler = m -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
