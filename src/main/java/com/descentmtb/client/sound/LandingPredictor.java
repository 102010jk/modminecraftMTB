package com.descentmtb.client.sound;

import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;

/**
 * Steps a flight path forward to find when (and on what slope) the wheels will touch down - the same ballistic
 * walk the physics uses for its landing assist, but readable from outside, with the time kept. Pure.
 */
public final class LandingPredictor {
    /** Result: seconds until touch-down and the pitch (rad) of the ground along the heading there. */
    public record Landing(double time, double groundPitch) {
        public static final Landing NONE = new Landing(Double.POSITIVE_INFINITY, 0);
        public boolean known() {
            return Double.isFinite(time);
        }
    }

    public static final double STEP = 0.05, MAX_TIME = 1.2;

    /**
     * @param com       the frame's centre of mass now
     * @param vel       its velocity
     * @param clearance height of the centre of mass above the ground when the tyres touch
     * @param yaw       the bike's yaw (Minecraft: forward = (-sin, 0, cos)), for the slope along the heading
     */
    public static Landing predict(Terrain terrain, V3 com, V3 vel, double clearance, double yaw, double gravity) {
        Terrain.GroundHit hit = new Terrain.GroundHit();
        V3 pt = com;
        V3 v = vel;
        int steps = (int) Math.round(MAX_TIME / STEP);
        for (int i = 1; i <= steps; i++) {
            v = v.addScaled(V3.Y, -gravity * STEP);
            pt = pt.addScaled(v, STEP);
            if (terrain.ground(pt.x, pt.z, pt.y + 0.5, pt.y - 1.5, hit) && pt.y - clearance <= hit.height + 0.05) {
                return new Landing(i * STEP, groundPitch(hit.normal, yaw));
            }
        }
        return Landing.NONE;
    }

    /** Pitch of a slope with this normal along a heading. */
    public static double groundPitch(V3 normal, double yaw) {
        V3 heading = new V3(-Math.sin(yaw), 0, Math.cos(yaw));
        V3 t = heading.reject(normal).normalize();
        return Math.asin(BikeSoundMath.clamp(t.y, -1, 1));
    }

    private LandingPredictor() {}
}
