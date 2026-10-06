package com.descentmtb.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Bounded live PCM queue. A temporary underrun yields silence rather than ending the stream. */
public final class PcmStream implements AudioStream {
    private final PcmRing ring;
    private final int rate;
    private volatile boolean closed;
    public PcmStream(PcmRing ring, int rate) { this.ring = ring; this.rate = rate; }
    @Override public AudioFormat getFormat() { return new AudioFormat(rate, 16, 1, true, false); }
    @Override public ByteBuffer read(int bytes) {
        if (closed) return BufferUtils.createByteBuffer(0);
        int count = Math.max(2, bytes & ~1) / 2;
        float[] samples = new float[count];
        ring.read(samples, 0, count);
        ByteBuffer result = BufferUtils.createByteBuffer(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        AudioDsp.toPcm16(samples, count, result);
        return result.flip();
    }
    @Override public void close() { closed = true; }
}
