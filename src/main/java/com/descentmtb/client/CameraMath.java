package com.descentmtb.client;

/**
 * Pure maths of the riding cameras (no Minecraft types, so it can be unit tested): frame-rate independent
 * smoothing, the direction-of-travel heading, mouse orbit with spring-back, the eye offset of the follow
 * cameras and the pull-in against terrain. Angles are radians, yaw 0 looks along +Z and turns towards -X
 * (the Minecraft convention).
 */
public final class CameraMath {
    /** Lowest / highest elevation (rad) of a follow camera above its focus. */
    public static final double MIN_ELEVATION = Math.toRadians(-12), MAX_ELEVATION = Math.toRadians(80);
    /** How far the mouse may tilt the follow camera from its default elevation. */
    public static final double MAX_ORBIT_PITCH = Math.toRadians(70);

    /** Share of the way to the target covered in {@code dt} seconds; {@code tau <= 0} means no smoothing. */
    public static double blend(double dt, double tau) {
        if (dt <= 0) return 0;
        if (tau <= 1e-4) return 1;
        return 1 - Math.exp(-dt / tau);
    }

    public static double approach(double current, double target, double dt, double tau) {
        return current + (target - current) * blend(dt, tau);
    }

    /** Like {@link #approach} for angles: takes the short way round. */
    public static double approachAngle(double current, double target, double dt, double tau) {
        return current + wrap(target - current) * blend(dt, tau);
    }

    /** Wraps an angle into [-pi, pi). */
    public static double wrap(double a) {
        return a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI));
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = clamp((x - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Yaw the camera should follow: the direction of travel instead of the bike's (twitchy) yaw. Slow, standing
     * or rolling backwards keeps the bike's yaw; a slide is followed by at most 70 degrees.
     */
    public static double headingYaw(double vx, double vz, double bikeYaw, double fullSpeed) {
        double speed = Math.hypot(vx, vz);
        if (speed < 0.5) return bikeYaw;
        double along = vx * -Math.sin(bikeYaw) + vz * Math.cos(bikeYaw);
        if (along <= 0) return bikeYaw;
        double travel = Math.atan2(-vx, vz);
        double off = clamp(wrap(travel - bikeYaw), Math.toRadians(-70), Math.toRadians(70));
        return bikeYaw + off * smoothstep(0.5, fullSpeed, speed);
    }

    /**
     * Mouse orbit around the rider: free look that holds while the mouse moves, waits {@code hold} seconds after
     * the last movement, then springs back to the default view.
     */
    public static final class Orbit {
        public double yaw, pitch, idle;

        public void add(double dYaw, double dPitch) {
            yaw = wrap(yaw + dYaw);
            pitch = clamp(pitch + dPitch, -MAX_ORBIT_PITCH, MAX_ORBIT_PITCH);
            idle = 0;
        }

        public void update(double dt, double hold, double returnTau) {
            double before = idle;
            idle += Math.max(0, dt);
            if (idle <= hold) return;
            double k = blend(idle - Math.max(before, Math.max(0, hold)), returnTau);
            yaw -= yaw * k;
            pitch -= pitch * k;
            if (Math.abs(yaw) < 1e-4) yaw = 0;
            if (Math.abs(pitch) < 1e-4) pitch = 0;
        }

        public void reset() {
            yaw = pitch = idle = 0;
        }

        public boolean active() {
            return yaw != 0 || pitch != 0;
        }
    }

    // ------------------------------------------------------------------ helmet cam

    /** Free look from the helmet: how far the head turns from the bike's heading. */
    public static final double HEAD_MAX_YAW = Math.toRadians(70), HEAD_MAX_PITCH = Math.toRadians(45);

    /**
     * The rider turning their head while riding: mouse input within a cone of {@link #HEAD_MAX_YAW} to the sides and
     * {@link #HEAD_MAX_PITCH} up and down, easing back to the trail ahead after a moment without input.
     */
    public static final class HeadLook {
        public double yaw, pitch, idle;

        public void add(double dYaw, double dPitch) {
            yaw = clamp(yaw + dYaw, -HEAD_MAX_YAW, HEAD_MAX_YAW);
            pitch = clamp(pitch + dPitch, -HEAD_MAX_PITCH, HEAD_MAX_PITCH);
            idle = 0;
        }

        public void update(double dt, double hold, double returnTau) {
            double before = idle;
            idle += Math.max(0, dt);
            if (idle <= hold) return;
            double k = blend(idle - Math.max(before, Math.max(0, hold)), returnTau);
            yaw -= yaw * k;
            pitch -= pitch * k;
            if (Math.abs(yaw) < 1e-4) yaw = 0;
            if (Math.abs(pitch) < 1e-4) pitch = 0;
        }

        public void reset() {
            yaw = pitch = idle = 0;
        }
    }

    /**
     * Helmet cam view pitch in Minecraft degrees (+ = looking down). On flat ground it looks {@code tiltDeg} below the
     * heading like a tilted action cam. On the ground the neck takes out part of the bike's pitch and looks further
     * ahead on steep ground, so a steep chute shows the trail below, not the dirt under the front wheel, and a climb
     * does not stare into the sky. In the air ({@code grounded} → 0) the head stays locked to the bike for flips.
     */
    public static double helmetPitch(double bikePitchRad, double grounded, double tiltDeg) {
        double g = clamp(grounded, 0, 1);
        double compensation = 0.55 * g;
        double steep = clamp(Math.abs(bikePitchRad) / 0.5, 0, 1);
        double tilt = tiltDeg - 8 * steep * g;
        return -Math.toDegrees(bikePitchRad) * (1 - compensation) + tilt;
    }

    /**
     * Camera shake ("trauma"): hits add trauma, it drains at {@link #TRAUMA_DECAY} per second, and the shake grows with
     * its square, so small knocks are a twitch and a bottom-out is a proper jolt. Smooth noise, not random jitter.
     */
    public static final class Trauma {
        public static final double TRAUMA_DECAY = 1.6;
        /** Shake at full trauma, degrees. */
        public static final double MAX_YAW = 3.0, MAX_PITCH = 4.0, MAX_ROLL = 5.0;
        private double trauma, time;

        public void add(double amount) {
            if (amount > 0 && Double.isFinite(amount)) trauma = clamp(trauma + amount, 0, 1);
        }

        public double trauma() {
            return trauma;
        }

        public void update(double dt) {
            time += Math.max(0, dt);
            trauma = Math.max(0, trauma - TRAUMA_DECAY * Math.max(0, dt));
        }

        /** {yaw, pitch, roll} shake in degrees for this moment. */
        public double[] shake() {
            double s = trauma * trauma;
            if (s < 1e-6) return new double[3];
            return new double[]{MAX_YAW * s * noise(time, 0.0), MAX_PITCH * s * noise(time, 1.7), MAX_ROLL * s * noise(time, 3.1)};
        }

        /** Smooth, roughly -1..1, ~20 Hz dominant (sum of incommensurate sines). */
        static double noise(double t, double seed) {
            return 0.55 * Math.sin(t * 117.0 + seed * 5.3) + 0.3 * Math.sin(t * 71.3 + seed * 11.1) + 0.15 * Math.sin(t * 191.7 + seed * 2.9);
        }

        public void reset() {
            trauma = 0;
        }
    }

    /**
     * Trauma for a landing: nothing for a soft touch-down, rising to a big jolt as the impact nears the speed that
     * would throw the rider off; a bottomed-out fork or shock adds its own clunk on top.
     */
    public static double landingTrauma(double impact, double bailImpact, boolean bottomedOut) {
        double t = clamp((impact - 3.0) / Math.max(1, bailImpact - 3.0), 0, 1) * 0.65;
        return clamp(t + (bottomedOut ? 0.35 : 0), 0, 1);
    }

    /**
     * Offset from the focus to the camera: {@code dist} horizontally and {@code height} up, on the {@code side}
     * of the heading (-1 behind the direction of travel, +1 ahead of it); {@code extraElevation} tilts the whole
     * offset up, {@code orbitYaw} swings it around the focus. The length of the offset stays constant.
     *
     * @return {x, y, z}
     */
    public static double[] eyeOffset(double heading, double orbitYaw, double dist, double height,
                                     double extraElevation, double side) {
        double length = Math.hypot(dist, height);
        double elevation = clamp(Math.atan2(height, dist) + extraElevation, MIN_ELEVATION, MAX_ELEVATION);
        double horizontal = length * Math.cos(elevation);
        double yaw = heading + orbitYaw;
        return new double[]{-Math.sin(yaw) * side * horizontal, length * Math.sin(elevation), Math.cos(yaw) * side * horizontal};
    }

    /**
     * Part of the focus-to-camera ray the camera may keep so that it stops {@code margin} before a hit.
     *
     * @param hitDistance distance from the focus to the first hit, negative for no hit
     */
    public static double collisionScale(double rayLength, double hitDistance, double margin, double minDistance) {
        if (hitDistance < 0 || rayLength <= 1e-6) return 1;
        // A comfort minimum must never put the eye beyond a nearby wall.
        double safe = Math.max(0, hitDistance - margin);
        return clamp(safe / rayLength, 0, 1);
    }

    /** The camera is pulled in at once (never inside terrain) and eases back out when the way is free again. */
    public static double relaxScale(double current, double limit, double dt, double tau) {
        return limit < current ? limit : current + (limit - current) * blend(dt, tau);
    }

    /** @return {yaw, pitch} in degrees (Minecraft: positive pitch looks down) of a view along {@code (dx, dy, dz)}. */
    public static double[] lookAngles(double dx, double dy, double dz) {
        return new double[]{Math.toDegrees(Math.atan2(-dx, dz)), -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }

    private CameraMath() {}
}
