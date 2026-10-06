package com.descentmtb.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * A finished GPS track carried by a {@code trail_gps} item: the packed points of {@link TrackGeometry}. Saved as an
 * int array and sent as zig-zag varint steps between points, which keeps a track of a few hundred points to
 * about a kilobyte.
 *
 * @param pts packed x, y, z triples (tenths of a block)
 */
public record TrailTrack(int[] pts) {
    public static final Codec<TrailTrack> CODEC = Codec.INT_STREAM
            .xmap(stream -> new TrailTrack(stream.toArray()), track -> IntStream.of(track.pts))
            .validate(track -> TrackGeometry.valid(track.pts) && TrackGeometry.count(track.pts) >= TrackGeometry.MIN_POINTS
                    ? DataResult.success(track) : DataResult.error(() -> "Invalid GPS track"));

    public static final StreamCodec<ByteBuf, TrailTrack> STREAM_CODEC = StreamCodec.of(
            (buf, track) -> TrackBytes.write(new FriendlyByteBuf(buf), track.pts),
            buf -> new TrailTrack(TrackBytes.read(new FriendlyByteBuf(buf), TrackGeometry.MAX_POINTS)));

    public TrackGeometry.Stats stats() {
        return TrackGeometry.stats(pts);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TrailTrack t && Arrays.equals(pts, t.pts);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pts);
    }

    @Override
    public String toString() {
        return "TrailTrack[" + TrackGeometry.count(pts) + " points]";
    }
}
