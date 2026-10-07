package com.descentmtb.client.sound;

/**
 * How the dirt bike's engine is mixed from four recorded-rpm loops. Each loop sounds right near the revs it was made
 * at; between two of them the mix crossfades with equal power and every loop is pitched by {@code rpm / its rpm}.
 * Open throttle (load) is louder and leans on the upper layer. Pure, unit-tested.
 */
public final class MotoSoundMath {
    private MotoSoundMath() {}

    /** The revs each loop was rendered at: idle, low, mid, high. */
    public static final double[] LAYER_RPM = {1500, 3500, 7000, 11000};
    /** Minecraft clamps sound pitch to this range. */
    public static final double MIN_PITCH = 0.5, MAX_PITCH = 2.0;

    /** Gain of each layer (0..1) at these revs: two neighbours share it with equal power, the rest are silent. */
    public static double[] weights(double rpm) {
        double[] w = new double[LAYER_RPM.length];
        if (!(rpm > LAYER_RPM[0])) { w[0] = 1; return w; }
        if (rpm >= LAYER_RPM[LAYER_RPM.length - 1]) { w[LAYER_RPM.length - 1] = 1; return w; }
        for (int i = 0; i < LAYER_RPM.length - 1; i++) {
            if (rpm <= LAYER_RPM[i + 1]) {
                double t = (rpm - LAYER_RPM[i]) / (LAYER_RPM[i + 1] - LAYER_RPM[i]);
                w[i] = Math.cos(t * Math.PI / 2);
                w[i + 1] = Math.sin(t * Math.PI / 2);
                return w;
            }
        }
        return w;
    }

    /** Playback pitch of a layer so it matches the engine's revs. */
    public static double pitch(int layer, double rpm) {
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, rpm / LAYER_RPM[layer]));
    }

    /** Overall engine loudness: a lazy idle, a hard pull under load. */
    public static double loudness(double rpm, double throttle) {
        double revs = Math.max(0, Math.min(1, (rpm - LAYER_RPM[0]) / (LAYER_RPM[3] - LAYER_RPM[0])));
        double load = Math.max(0, Math.min(1, throttle));
        return 0.35 + 0.35 * revs + 0.3 * load;
    }
}
