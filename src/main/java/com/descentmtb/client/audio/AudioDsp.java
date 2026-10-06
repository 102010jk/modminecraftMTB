package com.descentmtb.client.audio;

/**
 * Small signal helpers for the music devices. Pure, unit-tested.
 */
public final class AudioDsp {
    private AudioDsp() {}

    /** Interleaved stereo → mono (average). Returns the number of mono samples written. */
    public static int downmix(float[] stereo, int frames, float[] mono) {
        for (int i = 0; i < frames; i++) mono[i] = (stereo[2 * i] + stereo[2 * i + 1]) * 0.5f;
        return frames;
    }

    /** Soft limiter: transparent below ~0.8, never exceeds 1. */
    public static float limit(float x) {
        float a = Math.abs(x);
        if (a <= 0.8f) return x;
        float over = (a - 0.8f) / 0.2f;
        float y = 0.8f + 0.2f * (float) Math.tanh(over);
        return Math.copySign(Math.min(1f, y), x);
    }

    /** Float -1..1 → signed 16-bit little endian. */
    public static void toPcm16(float[] src, int n, java.nio.ByteBuffer out) {
        for (int i = 0; i < n; i++) {
            float v = Math.max(-1f, Math.min(1f, src[i]));
            out.putShort((short) Math.round(v * 32767f));
        }
    }

    /**
     * 3:1 decimator 48 kHz → 16 kHz with a short low-pass in front, keeping its state between blocks.
     */
    public static final class Decimator3 {
        private float z1, z2;
        private int phase;

        /** Returns the number of output samples written to {@code out}. */
        public int process(float[] in, int n, float[] out) {
            int o = 0;
            for (int i = 0; i < n; i++) {
                // two cascaded one-pole low-passes, ~6 kHz corner at 48 kHz
                z1 += 0.55f * (in[i] - z1);
                z2 += 0.55f * (z1 - z2);
                if (++phase == 3) {
                    phase = 0;
                    out[o++] = z2;
                }
            }
            return o;
        }
    }

    /**
     * "Headphones hanging round the neck": thin, quiet and a bit crunchy. Band-pass ~350 Hz – 4 kHz, gain and a soft
     * clip. State per channel; {@code channels} interleaved.
     */
    public static final class NeckFilter {
        private final float[] hp, lpState, prevIn;
        private final int channels;
        private final float hpA, lpA;

        public NeckFilter(int channels, float rate) {
            this.channels = channels;
            hp = new float[channels];
            lpState = new float[channels];
            prevIn = new float[channels];
            float dt = 1f / rate;
            float rcHp = 1f / (2f * (float) Math.PI * 350f), rcLp = 1f / (2f * (float) Math.PI * 4000f);
            hpA = rcHp / (rcHp + dt);
            lpA = dt / (rcLp + dt);
        }

        public void process(float[] buf, int samples, float amount) {
            if (amount <= 0) return;
            for (int i = 0; i < samples; i++) {
                int c = i % channels;
                float x = buf[i];
                float h = hpA * (hp[c] + x - prevIn[c]);
                prevIn[c] = x;
                hp[c] = h;
                lpState[c] += lpA * (h - lpState[c]);
                float wet = (float) Math.tanh(lpState[c] * 2.2f) * 0.35f;
                buf[i] = x + (wet - x) * amount;
            }
        }
    }
}
