package com.descentmtb.client;

import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.V3;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Riding cameras, driven every frame from the same interpolated pose the bike renderer draws (so there is
 * never a tick of lag between bike and camera). V cycles the modes, B re-centres, the mouse orbits the
 * follow cameras for a moment.
 * <ul>
 *   <li><b>FIRST_PERSON</b> - helmet cam, eyes on the rider's head, leans and pitches with the bike;</li>
 *   <li><b>SECOND_PERSON</b> - in front of the bike, looking back at the rider;</li>
 *   <li><b>THIRD_PERSON</b> - chase cam behind the rider, follows the direction of travel;</li>
 *   <li><b>DRONE</b> - far chase cam, heavier smoothing, looks a little ahead.</li>
 * </ul>
 * Distances, heights and smoothing are config values ({@link ClientConfig}); the maths is in {@link CameraMath}.
 * Applied by {@code CameraMixin} at the end of {@code Camera.setup}.
 */
public final class BikeCamera {
    public enum Mode {
        FIRST_PERSON, SECOND_PERSON, THIRD_PERSON, DRONE;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record View(Vec3 pos, float yaw, float pitch, float roll) {}

    /** Mode used until the config is loaded. */
    private static Mode fallbackMode = Mode.FIRST_PERSON;
    private static Mode lastMode;
    private static CameraType savedType;
    private static boolean init;
    private static double camYaw, slope, focusY, fovBoost, collisionScale = 1, lookAheadX, lookAheadZ;
    private static double hYaw, hPitch, hRoll;
    private static final CameraMath.Orbit orbit = new CameraMath.Orbit();
    /** Dev autopilot only: 0 = off, ±1 = side view, 2 = front view. */
    static int debugSide;
    private static boolean hInit;
    private static long lastNanos;

    private static CameraType applied;

    /** Helmet cam looks this far below the bike's heading, like a tilted action cam. */
    private static final double HELMET_TILT_DEG = 24.0;
    private static final double HELMET_EXTRA_FOV = 22.0;
    /** Mouse units to degrees (vanilla {@code Entity.turn}). */
    private static final double MOUSE_DEGREES = 0.15;
    /** A camera never gets closer than this to the focus, and stops this far before terrain. */
    private static final double COLLISION_MARGIN = 0.2, MIN_CAMERA_DISTANCE = 0.15;
    private static final double RELAX_TAU = 0.25, ORBIT_RETURN_TAU = 0.2;

    public static Mode mode() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.CAMERA_MODE.get() : fallbackMode;
    }

    /** V: next mode, remembered in the config; a toast names it. */
    public static void cycle() {
        Mode m = mode().next();
        fallbackMode = m;
        if (ClientConfig.SPEC.isLoaded()) {
            try {
                ClientConfig.CAMERA_MODE.set(m);
                ClientConfig.CAMERA_MODE.save();
            } catch (RuntimeException ignored) {
                // a read-only config must not break riding; the mode is still kept for this session
            }
        }
        snapBehind();
        applyCameraType();
        BikeClientController.toast(Component.translatable(switch (m) {
            case FIRST_PERSON -> "descentmtb.camera.first_person";
            case SECOND_PERSON -> "descentmtb.camera.second_person";
            case THIRD_PERSON -> "descentmtb.camera.third_person";
            case DRONE -> "descentmtb.camera.drone";
        }).getString());
    }

    /** B: re-centre at once (also after a respawn). */
    public static void snapBehind() {
        init = false;
        hInit = false;
        orbit.reset();
    }

    /** Mouse look while riding orbits the follow cameras ({@code Entity.turn}, raw mouse units). */
    public static void onMouseLook(double yRot, double xRot) {
        if (mode() == Mode.FIRST_PERSON || debugSide != 0) return;
        orbit.add(Math.toRadians(yRot * MOUSE_DEGREES), Math.toRadians(xRot * MOUSE_DEGREES));
    }

    static void onMount() {
        Minecraft mc = Minecraft.getInstance();
        if (savedType == null) savedType = mc.options.getCameraType();
        snapBehind();
        applyCameraType();
    }

    static void onDismount() {
        Minecraft mc = Minecraft.getInstance();
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        applied = null;
        fovBoost = 0;
        orbit.reset();
    }

    static CameraType originalCameraType() {
        return savedType != null ? savedType : Minecraft.getInstance().options.getCameraType();
    }

    /** Always third-person underneath: it is what makes vanilla draw our own body (the
     *  helmet cam hides just the head); position and angles come from this class. */
    private static CameraType wanted() {
        return CameraType.THIRD_PERSON_BACK;
    }

    private static void applyCameraType() {
        applied = wanted();
        Minecraft.getInstance().options.setCameraType(applied);
    }

    public static boolean helmet() {
        return BikeClientController.riding() != null && mode() == Mode.FIRST_PERSON && debugSide == 0;
    }

    public static double fovBoost() {
        return BikeClientController.riding() != null ? fovBoost : 0;
    }

    // ------------------------------------------------------------------ config

    private static double cfg(ModConfigSpec.DoubleValue v) {
        return ClientConfig.SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    /** Distance / height / smoothing seconds of a follow mode. */
    private record Follow(double dist, double height, double tau) {}

    private static Follow follow(Mode m) {
        return switch (m) {
            case SECOND_PERSON -> new Follow(cfg(ClientConfig.CAM_SECOND_DIST), cfg(ClientConfig.CAM_SECOND_HEIGHT), cfg(ClientConfig.CAM_SECOND_SMOOTH));
            case DRONE -> new Follow(cfg(ClientConfig.CAM_DRONE_DIST), cfg(ClientConfig.CAM_DRONE_HEIGHT), cfg(ClientConfig.CAM_DRONE_SMOOTH));
            default -> new Follow(cfg(ClientConfig.CAM_THIRD_DIST), cfg(ClientConfig.CAM_THIRD_HEIGHT), cfg(ClientConfig.CAM_THIRD_SMOOTH));
        };
    }

    // ------------------------------------------------------------------ per frame

    /** @return the camera for this frame, or null when not riding. */
    public static View compute(float partialTick) {
        View crash = RagdollClient.cameraView(partialTick);
        if (crash != null) return crash;
        MountainBikeEntity bike = BikeClientController.riding();
        Minecraft mc = Minecraft.getInstance();
        if (bike == null || mc.level == null) return null;
        // keep the camera type we need even if the player pressed F5
        // vanilla F5 changed the perspective -> treat it as our camera switch
        if (applied != null && mc.options.getCameraType() != applied) cycle();
        else if (mc.options.getCameraType() != wanted()) applyCameraType();

        Mode mode = mode();
        if (mode != lastMode) {                 // switched by V, F5 or the config screen
            lastMode = mode;
            snapBehind();
        }

        long now = System.nanoTime();
        double dt = init || hInit ? Math.min(0.1, Math.max(0, (now - lastNanos) / 1e9)) : 0;
        lastNanos = now;
        double g = cfg(ClientConfig.CAMERA_SMOOTHING);       // global lag multiplier

        BikeRenderState a = bike.rsPrev, b = bike.rsCur;
        double t = partialTick;
        V3 rider = BikeRenderState.lerp(t, a.riderPos, b.riderPos);
        V3 com = BikeRenderState.lerp(t, a.com, b.com);
        double yaw = BikeRenderState.lerp(t, a.yaw, b.yaw);
        double pitch = BikeRenderState.lerp(t, a.pitch, b.pitch);
        double lean = BikeRenderState.lerp(t, a.lean, b.lean);
        V3 vel = BikeRenderState.lerp(t, a.vel, b.vel);     // interpolated: no stepping at the tick rate
        double speed = vel.length();

        double kmh = speed * 3.6;
        double fovTarget = Math.max(0, Math.min(55, kmh - 15)) * 0.22;
        if(ClientConfig.SPEC.isLoaded()) fovTarget*=ClientConfig.SPEED_FOV_STRENGTH.get();
        if (mode == Mode.DRONE) fovTarget *= 0.5;                 // gentle
        if (mode == Mode.FIRST_PERSON) fovTarget += HELMET_EXTRA_FOV;   // action-cam wide angle
        fovBoost += (fovTarget - fovBoost) * CameraMath.blend(dt, 0.4);

        if (debugSide != 0) {
            // dev autopilot: look at the bike from its side (debugSide = ±1) or from the front (2)
            double sy = Math.sin(yaw), cy = Math.cos(yaw);
            V3 fH = new V3(-sy, 0, cy);
            V3 rightH = fH.cross(V3.Y);
            V3 target = com.addScaled(V3.Y, 0.45);
            V3 eye = debugSide == 2 ? target.addScaled(fH, 3.2).addScaled(V3.Y, 0.3)
                    : target.addScaled(rightH, 3.0 * debugSide).addScaled(V3.Y, 0.25);
            V3 d = target.sub(eye);
            init = true;
            double[] ang = CameraMath.lookAngles(d.x, d.y, d.z);
            return new View(new Vec3(eye.x, eye.y, eye.z), (float) ang[0], (float) ang[1], 0f);
        }

        if (mode == Mode.FIRST_PERSON) return helmetView(a, b, t, dt, g, yaw, pitch);

        // ---------------- follow cameras ----------------
        Follow f = follow(mode);
        double tau = f.tau() * g;
        double side = mode == Mode.SECOND_PERSON ? 1 : -1;      // ahead of / behind the direction of travel
        double heading = CameraMath.headingYaw(vel.x, vel.z, yaw, 4.0);
        double slopeFactor = mode == Mode.DRONE ? 0.35 : 0.45;
        double slopeTarget = b.airborne ? 0 : pitch * slopeFactor * side;   // stay above the slope line
        if (!init) {
            camYaw = heading;
            slope = slopeTarget;
            focusY = rider.y;
            lookAheadX = vel.x;
            lookAheadZ = vel.z;
            collisionScale = 1;
            init = true;
        }
        camYaw = CameraMath.approachAngle(camYaw, heading, dt, tau);
        slope = CameraMath.approach(slope, slopeTarget, dt, 0.30 * g);
        focusY = CameraMath.approach(focusY, rider.y, dt, 0.07 * g);
        lookAheadX = CameraMath.approach(lookAheadX, vel.x, dt, 0.4 * g);
        lookAheadZ = CameraMath.approach(lookAheadZ, vel.z, dt, 0.4 * g);
        orbit.update(dt, cfg(ClientConfig.CAMERA_ORBIT_RETURN), ORBIT_RETURN_TAU);

        double dist = f.dist() + speed * (mode == Mode.DRONE ? 0.05 : 0.025);
        Vec3 focus = new Vec3(rider.x, focusY + 0.3, rider.z);
        double[] off = CameraMath.eyeOffset(camYaw, orbit.yaw, dist, f.height(), slope + orbit.pitch, side);
        Vec3 offset = new Vec3(off[0], off[1], off[2]);

        // pull in at once in front of terrain, ease back out when the way is free
        double rayLen = offset.length();
        double hit = castDistance(mc.level, focus, focus.add(offset), bike, partialTick);
        double limit = CameraMath.collisionScale(rayLen, hit, COLLISION_MARGIN, MIN_CAMERA_DISTANCE);
        collisionScale = CameraMath.relaxScale(collisionScale, limit, dt, RELAX_TAU);
        Vec3 pos = focus.add(offset.scale(collisionScale));

        // what the camera looks at
        double free = Math.max(0, 1 - Math.abs(orbit.yaw) / (Math.PI / 2));    // look-ahead fades while orbiting
        Vec3 lookAt;
        if (mode == Mode.THIRD_PERSON) {
            lookAt = focus.add(-Math.sin(camYaw) * 1.2 * free, -0.15, Math.cos(camYaw) * 1.2 * free);
        } else if (mode == Mode.DRONE) {
            double lx = lookAheadX * 0.3, lz = lookAheadZ * 0.3, len = Math.hypot(lx, lz);
            if (len > 3) {
                lx *= 3 / len;
                lz *= 3 / len;
            }
            lookAt = focus.add(lx * free, -0.2, lz * free);
        } else {
            lookAt = focus;                                     // second person: the rider's chest
        }
        Vec3 d = lookAt.subtract(pos);
        double[] ang = CameraMath.lookAngles(d.x, d.y, d.z);
        float roll = mode == Mode.THIRD_PERSON ? (float) Math.toDegrees(lean * 0.12) : 0f;
        return new View(pos, (float) ang[0], (float) ang[1], roll);
    }

    /** Eyes of the posed rider: same stance maths as RiderPose, little smoothing, leans with the bike. */
    private static View helmetView(BikeRenderState a, BikeRenderState b, double t, double dt, double g,
                                   double yaw, double pitch) {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        V3 fH = new V3(-sy, 0, cy);
        V3 fwd = fH.mul(cp).addScaled(V3.Y, sp);
        V3 up = fH.mul(-sp).addScaled(V3.Y, cp);
        RiderPose.Stance st = RiderPose.stance(
                (float) BikeRenderState.lerp(t, a.riderUp, b.riderUp),
                (float) BikeRenderState.lerp(t, a.riderFwd, b.riderFwd));
        double[] eye = st.eye();
        V3 feet = BikeRenderState.lerp(t, a.feet(), b.feet());
        V3 head = feet.addScaled(up, eye[1]).addScaled(fwd, eye[0]);
        double roll = BikeRenderState.lerp(t, a.lean, b.lean);
        // the neck soaks up the chatter, but only a few milliseconds of it
        double s = cfg(ClientConfig.CAM_FIRST_SMOOTH) * g;
        if (!hInit) {
            hYaw = yaw;
            hPitch = pitch;
            hRoll = roll;
            hInit = true;
        }
        hYaw = CameraMath.approachAngle(hYaw, yaw, dt, s);
        hPitch = CameraMath.approach(hPitch, pitch, dt, s);
        hRoll = CameraMath.approach(hRoll, roll, dt, s);
        return new View(new Vec3(head.x, head.y, head.z),
                (float) Math.toDegrees(hYaw),
                (float) (-Math.toDegrees(hPitch) + HELMET_TILT_DEG),
                (float) Math.toDegrees(hRoll * 0.3));
    }

    /**
     * Distance from {@code from} to the first solid block towards {@code to}, or -1. Leaves are ignored: the bike
     * rides through them, so the camera may as well.
     */
    private static double castDistance(Level level, Vec3 from, Vec3 to, Entity entity, float partialTick) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1e-6) return -1;
        Vec3 n = dir.scale(1 / len);
        Vec3 start = from;
        for (int i = 0; i < 40; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) return -1;
            Vec3 at = ClipCoordinates.world(hit, partialTick);
            if (!level.getBlockState(hit.getBlockPos()).is(BlockTags.LEAVES)) return at.subtract(from).length();
            start = at.add(n.scale(0.3));                       // step through the leaf block
            if (start.subtract(from).dot(n) >= len) return -1;
        }
        return -1;
    }

    private BikeCamera() {}
}
