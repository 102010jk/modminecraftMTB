package com.descentmtb.client.audio;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;
import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.DoubleSupplier;

/** Amplifies PCM before OpenAL, whose per-sound volume is capped at unity by Minecraft. */
public final class GainAudioStream implements AudioStream {
    private final AudioStream source;
    private final DoubleSupplier gain;
    /** Reused copy target for read-only source blocks (uploaded to OpenAL before the next read). */
    private ByteBuffer copy;

    public GainAudioStream(AudioStream source, DoubleSupplier gain) {
        this.source = source;
        this.gain = gain;
    }

    @Override public AudioFormat getFormat() { return source.getFormat(); }

    @Override public ByteBuffer read(int bytes) throws IOException {
        ByteBuffer pcm = source.read(bytes);
        AudioFormat format = getFormat();
        if (format.getSampleSizeInBits() != 16 || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) return pcm;
        if (pcm.isReadOnly()) {
            if (copy == null || copy.capacity() < pcm.remaining()) copy = BufferUtils.createByteBuffer(pcm.remaining());
            copy.clear();
            copy.put(pcm.duplicate()).flip();
            pcm = copy;
        }
        ByteOrder previous = pcm.order();
        pcm.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        double value = gain.getAsDouble();
        float amplification = Double.isFinite(value) ? (float) Math.max(0, Math.min(16, value)) : 1;
        for (int i = pcm.position(); i + 1 < pcm.limit(); i += 2) {
            float sample = pcm.getShort(i) / 32768f;
            float output = AudioDsp.limit(sample * amplification);
            pcm.putShort(i, (short) Math.round(output * 32767f));
        }
        return pcm.order(previous);
    }

    @Override public void close() throws IOException { source.close(); }
}
