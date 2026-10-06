package com.descentmtb.client.sound;

/**
 * Detects the hard compressions (and the snap back on take-off) of a fork or shock from its compression speed.
 * One instance per damper; a cooldown keeps one landing from hissing five times.
 */
public final class SuspensionHiss {
    /** Compression speed (m/s) above which a hit is "hard", rebound speed (m/s, negative) of a snap back. */
    public static final double COMPRESS_THRESHOLD = 1.1, REBOUND_THRESHOLD = -2.0;
    public static final int COOLDOWN_TICKS = 7;
    private int cooldown;

    /** Advances one tick; returns the volume (0..1) of a hiss to play now, or 0. */
    public double tick(double compressionSpeed) {
        if (cooldown > 0) cooldown--;
        if (cooldown > 0 || !Double.isFinite(compressionSpeed)) return 0;
        if (compressionSpeed > COMPRESS_THRESHOLD) {
            cooldown = COOLDOWN_TICKS;
            return BikeSoundMath.clamp((compressionSpeed - COMPRESS_THRESHOLD) / 3.0, 0.2, 1.0) * 0.85;
        }
        if (compressionSpeed < REBOUND_THRESHOLD) {
            cooldown = COOLDOWN_TICKS;
            return BikeSoundMath.clamp((-compressionSpeed + REBOUND_THRESHOLD) / 4.0 + 0.2, 0.2, 0.5);
        }
        return 0;
    }

    public void reset() {
        cooldown = 0;
    }
}
