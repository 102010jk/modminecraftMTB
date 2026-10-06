package com.descentmtb.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Recording state of a {@code trail_gps} item. {@code session} identifies one recording (0 = none) so the client
 * can hand its buffer to the server even if the stack was moved or copied meanwhile; {@code active} is whether the
 * unit is recording right now.
 */
public record GpsState(long session, boolean active) {
    public static final GpsState IDLE = new GpsState(0, false);

    public static final Codec<GpsState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("session").forGetter(GpsState::session),
            Codec.BOOL.fieldOf("active").forGetter(GpsState::active)).apply(i, GpsState::new));

    public static final StreamCodec<ByteBuf, GpsState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, GpsState::session,
            ByteBufCodecs.BOOL, GpsState::active,
            GpsState::new);

    /** Same recording, stopped. */
    public GpsState stopped() {
        return new GpsState(session, false);
    }
}
