package com.descentmtb.audio;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * What a boombox is playing, as every client in the dimension knows it. {@code mode} APP = the owner streams a desktop
 * program (others receive {@link AudioNet.ChunkS2C}); DISC = everybody plays the jukebox song {@code disc} locally.
 * {@code active=false} is the "stopped" message.
 */
public record BoomboxState(Emitter emitter, boolean active, UUID owner, String ownerName, Mode mode,
                           Optional<ResourceLocation> disc, int radius, float volume, String label) {
    public enum Mode { APP, DISC }

    public static final int MIN_RADIUS = 10, MAX_RADIUS = 32;

    public static BoomboxState stopped(Emitter e) {
        return new BoomboxState(e, false, new UUID(0, 0), "", Mode.APP, Optional.empty(), MIN_RADIUS, 0, "");
    }

    public BoomboxState sanitized() {
        String n = ownerName.length() > 32 ? ownerName.substring(0, 32) : ownerName;
        String l = label.length() > 64 ? label.substring(0, 64) : label;
        int r = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        float v = Float.isFinite(volume) ? Math.max(0, Math.min(1, volume)) : 1;
        return new BoomboxState(emitter, active, owner, n, mode, mode == Mode.DISC ? disc : Optional.empty(), r, v, l);
    }

    public static final StreamCodec<ByteBuf, BoomboxState> CODEC = new StreamCodec<>() {
        @Override
        public BoomboxState decode(ByteBuf b) {
            Emitter e = Emitter.CODEC.decode(b);
            boolean active = b.readBoolean();
            UUID owner = new UUID(b.readLong(), b.readLong());
            String name = ByteBufCodecs.STRING_UTF8.decode(b);
            Mode mode = b.readByte() == 1 ? Mode.DISC : Mode.APP;
            Optional<ResourceLocation> disc = b.readBoolean() ? Optional.of(ResourceLocation.STREAM_CODEC.decode(b)) : Optional.empty();
            int radius = b.readByte();
            float volume = b.readFloat();
            String label = ByteBufCodecs.STRING_UTF8.decode(b);
            return new BoomboxState(e, active, owner, name, mode, disc, radius, volume, label).sanitized();
        }

        @Override
        public void encode(ByteBuf b, BoomboxState s) {
            Emitter.CODEC.encode(b, s.emitter);
            b.writeBoolean(s.active);
            b.writeLong(s.owner.getMostSignificantBits());
            b.writeLong(s.owner.getLeastSignificantBits());
            ByteBufCodecs.STRING_UTF8.encode(b, s.ownerName);
            b.writeByte(s.mode == Mode.DISC ? 1 : 0);
            b.writeBoolean(s.disc.isPresent());
            s.disc.ifPresent(d -> ResourceLocation.STREAM_CODEC.encode(b, d));
            b.writeByte(s.radius);
            b.writeFloat(s.volume);
            ByteBufCodecs.STRING_UTF8.encode(b, s.label);
        }
    };
}
