package com.descentmtb.map;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/** Writes and reads packed tracks on the wire: the point count, then every value as a zig-zag varint step. */
public final class TrackBytes {
    public static void write(FriendlyByteBuf buf, int[] pts) {
        buf.writeVarInt(TrackGeometry.count(pts));
        for (int v : TrackGeometry.toDeltas(pts)) {
            buf.writeVarInt(v);
        }
    }

    /** Reads a track of at most {@code maxPoints} points; a longer count is a protocol error. */
    public static int[] read(FriendlyByteBuf buf, int maxPoints) {
        int count = buf.readVarInt();
        if (count < 0 || count > maxPoints) {
            throw new DecoderException("Track has " + count + " points, the limit is " + maxPoints);
        }
        int[] deltas = new int[count * 3];
        for (int i = 0; i < deltas.length; i++) {
            deltas[i] = buf.readVarInt();
        }
        return TrackGeometry.fromDeltas(deltas);
    }

    private TrackBytes() {}
}
