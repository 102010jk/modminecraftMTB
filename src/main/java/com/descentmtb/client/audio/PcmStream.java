package com.descentmtb.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Short live buffers: Minecraft's default one-second reads are unsuitable for a live producer. */
public final class PcmStream implements AudioStream {
    public static final int BUFFER_MS = 80;
    public static final int PREFILL_BUFFERS = 5; // Channel queues four; leave one block for scheduling jitter
    private final PcmRing ring;
    private final int rate, channels, blockSamples;
    private final float[] samples, last;
    private boolean underrun;
    private volatile boolean closed;
    public PcmStream(PcmRing ring, int rate, int channels) {
        this.ring = ring; this.rate = rate; this.channels = channels;
        blockSamples = rate * BUFFER_MS / 1000 * channels;
        samples = new float[blockSamples]; last = new float[channels];
    }
    public static int prefillSamples(int rate, int channels) { return rate * BUFFER_MS / 1000 * channels * PREFILL_BUFFERS; }
    @Override public AudioFormat getFormat() { return new AudioFormat(rate, 16, channels, true, false); }
    @Override public ByteBuffer read(int bytes) {
        if (closed) return BufferUtils.createByteBuffer(0);
        int count = Math.min(blockSamples, Math.max(channels, bytes / (2 * channels) * channels));
        int n = ring.read(samples, 0, count);
        int fadeFrames = Math.max(1, rate / 500);
        if (underrun) for (int i = 0; i < Math.min(n, fadeFrames * channels); i++)
            samples[i] *= Math.min(1f, (i / channels + 1f) / fadeFrames);
        if (n > 0) for (int c = 0; c < channels; c++) last[c] = samples[n - channels + c];
        // A short fade instead of a sample discontinuity when a genuine capture/network gap occurs.
        for (int i = n; i < count; i++) {
            int frame = (i - n) / channels;
            samples[i] = last[i % channels] * Math.max(0, 1f - (frame + 1f) / fadeFrames);
        }
        underrun = n < count;
        if (underrun) java.util.Arrays.fill(last, 0);
        ByteBuffer result = BufferUtils.createByteBuffer(count * 2).order(ByteOrder.LITTLE_ENDIAN);
        AudioDsp.toPcm16(samples, count, result);
        return result.flip();
    }
    @Override public void close() { closed = true; }
}
