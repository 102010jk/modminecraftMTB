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

    // ------------------------------------------------------------------ helmet cam orientation (flips)

    /** The neck stops levelling the view beyond this bike pitch and has fully let go at {@link #NECK_FREE}. */
    private static final double NECK_FADE = Math.toRadians(60), NECK_FREE = Math.toRadians(100);

    /**
     * {@link #helmetPitch} for a camera that may go all the way round: how far the view looks above the horizon, in
     * radians (+ = up, <b>not</b> limited to +-90 degrees). {@code bikePitchRad} is the bike's continuous (unwrapped)
     * pitch, so a full backflip runs 0 -> 2 pi. The neck compensation and the steep-ground tilt look at the pitch
     * wrapped to +-pi and fade out beyond 60..100 degrees, so they are continuous through the flip (and 0 upside
     * down); for ordinary riding (|pitch| < 60 degrees) it equals {@code -toRadians(helmetPitch(...))}.
     */
    public static double helmetNoseUp(double bikePitchRad, double grounded, double tiltDeg) {
        double g = clamp(grounded, 0, 1);
        double w = wrap(bikePitchRad);
        double abs = Math.abs(w);
        double neck = 0.55 * g * w * (1 - smoothstep(NECK_FADE, NECK_FREE, abs));
        double steep = clamp(abs / 0.5, 0, 1);
        double tilt = tiltDeg - 8 * steep * g;
        return bikePitchRad - neck - Math.toRadians(tilt);
    }

    /**
     * Orientation of a camera as a quaternion {x, y, z, w} in Minecraft's camera convention (what
     * {@code Camera.rotation} holds: camera space looks along -Z, up is +Y, +X is to its right). Built by chaining
     * rotations, never by extracting Euler angles, so there is no gimbal flip at +-90 degrees of pitch and no
     * sudden 180 degree yaw or roll change when a bike flips through vertical: continuous angles in, continuous
     * orientation out. Order: yaw about the world vertical, the bike's pitch about the view's own left-right axis,
     * the head turn about the view's own up axis, the head nod, and last the roll about the line of sight. With
     * the head angles 0 and |pitch| &lt; 90 degrees it is exactly vanilla's {@code rotationYXZ(pi - yaw, -pitch,
     * -roll)}.
     *
     * @param yaw       Minecraft yaw (rad): 0 looks along +Z, positive turns right
     * @param noseUp    pitch above the horizon (rad, + = up), unlimited
     * @param headYaw   head turn relative to that (rad, + = to the right)
     * @param headDown  head nod (rad, + = looking down)
     * @param roll      tilt about the line of sight (rad, + = head to the right shoulder)
     */
    public static double[] orientation(double yaw, double noseUp, double headYaw, double headDown, double roll) {
        // view basis in world space: forward, up, left
        double[] f = {-Math.sin(yaw), 0, Math.cos(yaw)};
        double[] u = {0, 1, 0};
        double[] l = {Math.cos(yaw), 0, Math.sin(yaw)};
        turn(f, u, noseUp);                        // nose up: forward toward up
        turn(f, l, -headYaw);                      // head to the right: forward toward right (= away from left)
        turn(f, u, -headDown);                     // look down: forward away from up
        turn(u, l, -roll);                         // right shoulder down: up away from left
        return basisToQuaternion(f, u, l);
    }

    /** Turns the orthonormal vectors a and b in their common plane: a swings toward b by {@code angle}. */
    private static void turn(double[] a, double[] b, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        for (int i = 0; i < 3; i++) {
            double ai = a[i], bi = b[i];
            a[i] = ai * c + bi * s;
            b[i] = bi * c - ai * s;
        }
    }

    /** Quaternion {x, y, z, w} of the camera whose view basis (world space) is forward {@code f}, up {@code u}, left {@code l}. */
    static double[] basisToQuaternion(double[] f, double[] u, double[] l) {
        // columns of the rotation: camera +X = right = -left, +Y = up, +Z = -forward
        double m00 = -l[0], m10 = -l[1], m20 = -l[2];
        double m01 = u[0], m11 = u[1], m21 = u[2];
        double m02 = -f[0], m12 = -f[1], m22 = -f[2];
        double trace = m00 + m11 + m22, x, y, z, w;
        if (trace > 0) {
            double k = Math.sqrt(trace + 1) * 2;
            w = 0.25 * k;
            x = (m21 - m12) / k;
            y = (m02 - m20) / k;
            z = (m10 - m01) / k;
        } else if (m00 > m11 && m00 > m22) {
            double k = Math.sqrt(1 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / k;
            x = 0.25 * k;
            y = (m01 + m10) / k;
            z = (m02 + m20) / k;
        } else if (m11 > m22) {
            double k = Math.sqrt(1 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / k;
            x = (m01 + m10) / k;
            y = 0.25 * k;
            z = (m12 + m21) / k;
        } else {
            double k = Math.sqrt(1 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / k;
            x = (m02 + m20) / k;
            y = (m12 + m21) / k;
            z = 0.25 * k;
        }
        double len = Math.sqrt(x * x + y * y + z * z + w * w);
        return new double[]{x / len, y / len, z / len, w / len};
    }

    /** Rotates the vector (vx, vy, vz) by the quaternion {x, y, z, w}. */
    public static double[] rotate(double[] q, double vx, double vy, double vz) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        // v' = v + 2 w (q x v) + 2 q x (q x v)
        double tx = 2 * (y * vz - z * vy), ty = 2 * (z * vx - x * vz), tz = 2 * (x * vy - y * vx);
        return new double[]{vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx)};
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
