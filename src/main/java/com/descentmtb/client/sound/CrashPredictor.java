package com.descentmtb.client.sound;

import com.descentmtb.physics.V3;

/**
 * "Is this landing going to hurt?" - the Descenders scream. Pure: the caller supplies the velocity, the signed
 * pitch / yaw error against the landing, their rates and the predicted time to ground.
 *
 * <p>A crash is called unavoidable when, within the warning window before impact, the bike
 * <ul>
 *   <li>is falling fast: vertical speed already below {@link #FALL_SPEED} (or the speed it will have at impact is
 *       about to exceed the bail limit of the bike), or</li>
 *   <li>will touch down with its nose / tail far off the slope: the pitch error projected to the moment of
 *       impact (current error + rate x time) is beyond the bail pitch, or</li>
 *   <li>will touch down far off its direction of travel (yaw error projected the same way, but halved: with no
 *       steering input the sim damps the spin).</li>
 * </ul>
 */
public final class CrashPredictor {
    /** Vertical speed (m/s) below which a fall is a scream whatever the bail limit. */
    public static final double FALL_SPEED = 12.0;
    /** The warning window (seconds before impact): start no earlier than this, no later than {@link #MIN_TIME}. */
    public static final double MAX_TIME = 1.0, MIN_TIME = 0.06;
    /** Share of the bail limit that counts as "obviously going to bail". */
    public static final double LIMIT_SHARE = 0.9;
    public static final double GRAVITY = 9.81;

    public static boolean unavoidable(V3 vel, double pitchError, double yawError, double timeToGround,
                                      double pitchRate, double yawRate,
                                      double bailPitch, double bailYaw, double bailImpact) {
        if (!(timeToGround >= MIN_TIME) || timeToGround > MAX_TIME || vel == null) return false;
        // vertical speed: now, and as it will be at impact
        double fallNow = -vel.y;
        double fallAtImpact = -(vel.y - GRAVITY * timeToGround);
        double fallLimit = Math.min(FALL_SPEED, LIMIT_SHARE * bailImpact);
        if (fallNow > fallLimit || (fallAtImpact > LIMIT_SHARE * bailImpact && fallNow > 4)) return true;
        if (Math.abs(wrap(pitchError + pitchRate * timeToGround)) > LIMIT_SHARE * bailPitch) return true;
        return Math.abs(wrap(yawError + 0.5 * yawRate * timeToGround)) > LIMIT_SHARE * bailYaw;
    }

    /** Convenience over a frame. */
    public static boolean unavoidable(BikeAudioFrame f) {
        return f.airborne && f.airTime > 0.3 && !f.bailed
                && unavoidable(f.vel, f.pitchError, f.yawError, f.timeToGround, f.pitchRate, f.yawRate,
                f.bailPitch, f.bailYaw, f.bailImpact);
    }

    static double wrap(double a) {
        if (!Double.isFinite(a)) return 0;
        double r = Math.IEEEremainder(a, 2 * Math.PI);
        return r <= -Math.PI ? r + 2 * Math.PI : r;
    }

    private CrashPredictor() {}
}
