package com.descentmtb.audio;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a boombox sound comes from: a placed boombox block ({@link Kind#BLOCK}, ref = {@link BlockPos#asLong()}) or a
 * boombox clipped onto a bike ({@link Kind#BIKE}, ref = the bike's entity id). Dimension-local.
 */
public record Emitter(Kind kind, long ref) {
    public enum Kind { BLOCK, BIKE }

    public static Emitter block(BlockPos pos) {
        return new Emitter(Kind.BLOCK, pos.asLong());
    }

    public static Emitter bike(int entityId) {
        return new Emitter(Kind.BIKE, entityId);
    }

    public BlockPos pos() {
        return BlockPos.of(ref);
    }

    public int entityId() {
        return (int) ref;
    }

    public static final StreamCodec<ByteBuf, Emitter> CODEC = new StreamCodec<>() {
        @Override
        public Emitter decode(ByteBuf buf) {
            int k = buf.readByte();
            return new Emitter(k == 1 ? Kind.BIKE : Kind.BLOCK, buf.readLong());
        }

        @Override
        public void encode(ByteBuf buf, Emitter e) {
            buf.writeByte(e.kind == Kind.BIKE ? 1 : 0);
            buf.writeLong(e.ref);
        }
    };
}
