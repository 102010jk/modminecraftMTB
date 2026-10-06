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
            idle += Math.max(0, dt);
            if (idle <= hold) return;
            double k = blend(dt, returnTau);
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
        return clamp(Math.max(minDistance, hitDistance - margin) / rayLength, 0, 1);
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
