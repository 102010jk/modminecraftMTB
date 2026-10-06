package com.descentmtb.audio;

/**
 * G.711 mu-law: 8-bit companded samples for the boombox stream that other players hear (16 kHz mono = 16 kB/s).
 * Pure, shared by client and server (the server only relays the bytes).
 */
public final class MuLaw {
    private MuLaw() {}

    private static final int BIAS = 0x84, CLIP = 32635;
    private static final short[] DECODE = new short[256];

    static {
        for (int i = 0; i < 256; i++) {
            int u = ~i & 0xFF;
            int sign = u & 0x80, exponent = (u >> 4) & 7, mantissa = u & 0x0F;
            int s = ((mantissa << 3) + BIAS) << exponent;
            s -= BIAS;
            DECODE[i] = (short) (sign != 0 ? -s : s);
        }
    }

    /** Encodes a sample in -1..1. */
    public static byte encode(float sample) {
        int s = Math.round(Math.max(-1f, Math.min(1f, sample)) * 32767f);
        int sign = (s >> 8) & 0x80;
        if (sign != 0) s = -s;
        if (s > CLIP) s = CLIP;
        s += BIAS;
        int exponent = 7;
        for (int mask = 0x4000; (s & mask) == 0 && exponent > 0; mask >>= 1) exponent--;
        int mantissa = (s >> (exponent + 3)) & 0x0F;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    /** Decodes to -1..1. */
    public static float decode(byte b) {
        return DECODE[b & 0xFF] / 32768f;
    }
}
