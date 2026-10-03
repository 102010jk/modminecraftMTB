package com.descentmtb.client;

import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.V3;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Descenders-style cameras, driven every frame (not every tick):
 * <ul>
 *   <li><b>CHASE</b> - behind and above, follows the direction of travel with a
 *       little lag, looks down the slope, never clips into terrain;</li>
 *   <li><b>CHASE_FAR</b> - the same, further back;</li>
 *   <li><b>HELMET</b> - helmet cam, leans and shakes with the bike.</li>
 * </ul>
 * Applied by {@code CameraMixin} at the end of {@code Camera.setup}.
 */
public final class BikeCamera {
    public enum Mode { HELMET, CHASE, CHASE_FAR }

    public record View(Vec3 pos, float yaw, float pitch, float roll) {}

    private static Mode mode = Mode.HELMET;
    private static CameraType savedType;
    private static boolean init;
    private static double camYaw, camPitch, focusY, fovBoost;
    private static double hYaw, hPitch, hRoll;
    /** Dev autopilot only: 0 = off, ±1 = side view, 2 = front view. */
    static int debugSide;
    private static boolean hInit;
    private static long lastNanos;

    private static CameraType applied;

    public static void cycle() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        applyCameraType();
        BikeClientController.toast(switch (mode) {
            case HELMET -> "Kamera: první osoba";
            case CHASE -> "Kamera: třetí osoba";
            case CHASE_FAR -> "Kamera: třetí osoba (daleko)";
        });
    }

    public static Mode mode() {
        return mode;
    }

    /** Helmet cam looks this far below the bike's heading, like a tilted action cam. */
    private static final double HELMET_TILT_DEG = 24.0;
    private static final double HELMET_EXTRA_FOV = 22.0;

    public static void snapBehind() {
        init = false;
        hInit = false;
    }

    static void onMount() {
        Minecraft mc = Minecraft.getInstance();
        if (savedType == null) savedType = mc.options.getCameraType();
        init = false;
        applyCameraType();
    }

    static void onDismount() {
        Minecraft mc = Minecraft.getInstance();
        if (savedType != null) mc.options.setCameraType(savedType);
        savedType = null;
        applied = null;
        fovBoost = 0;
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
        return BikeClientController.riding() != null && mode == Mode.HELMET;
    }

    public static double fovBoost() {
        return BikeClientController.riding() != null ? fovBoost : 0;
    }

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

        long now = System.nanoTime();
        double dt = init ? Math.min(0.1, Math.max(0, (now - lastNanos) / 1e9)) : 0;
        lastNanos = now;

        BikeRenderState a = bike.rsPrev, b = bike.rsCur;
        double t = partialTick;
        V3 com = BikeRenderState.lerp(t, a.com, b.com);
        V3 rider = BikeRenderState.lerp(t, a.riderPos, b.riderPos);
        double yaw = BikeRenderState.lerp(t, a.yaw, b.yaw);
        double pitch = BikeRenderState.lerp(t, a.pitch, b.pitch);
        double lean = BikeRenderState.lerp(t, a.lean, b.lean);
        double steer = BikeRenderState.lerp(t, a.steer, b.steer);
        V3 vel = b.vel;
        double speed = vel.length();

        double kmh = speed * 3.6;
        double fovTarget = Math.max(0, Math.min(55, kmh - 15)) * 0.22;
        if (mode == Mode.HELMET) fovTarget += HELMET_EXTRA_FOV;   // action-cam wide angle
        fovBoost += (fovTarget - fovBoost) * (1 - Math.exp(-dt / 0.4));

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
            return new View(new Vec3(eye.x, eye.y, eye.z), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                    (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength())), 0f);
        }

        if (mode == Mode.HELMET) {
            double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
            V3 fH = new V3(-sy, 0, cy);
            V3 fwd = fH.mul(cp).addScaled(V3.Y, sp);
            V3 up = fH.mul(-sp).addScaled(V3.Y, cp);
            // eyes of the posed body (same stance maths as RiderPose)
            RiderPose.Stance st = RiderPose.stance(
                    (float) BikeRenderState.lerp(t, a.riderUp, b.riderUp),
                    (float) BikeRenderState.lerp(t, a.riderFwd, b.riderFwd));
            double[] eye = st.eye();
            V3 feet = BikeRenderState.lerp(t, a.feet(), b.feet());
            V3 head = feet.addScaled(up, eye[1]).addScaled(fwd, eye[0]);
            // the neck soaks up the chatter: smooth the view, never twitch with the bars
            if (!hInit) {
                hYaw = yaw;
                hPitch = pitch;
                hRoll = lean;
                hInit = true;
            }
            hYaw += wrap(yaw - hYaw) * (1 - Math.exp(-dt / 0.06));
            hPitch += wrap(pitch - hPitch) * (1 - Math.exp(-dt / 0.10));
            hRoll += (lean - hRoll) * (1 - Math.exp(-dt / 0.12));
            init = true;
            return new View(new Vec3(head.x, head.y, head.z),
                    (float) Math.toDegrees(hYaw),
                    (float) (-Math.toDegrees(hPitch) + HELMET_TILT_DEG),
                    (float) Math.toDegrees(hRoll * 0.3));
        }

        // ---------------- chase ----------------
        double heading = vel.horizontalLength() > 1.5 ? Math.atan2(-vel.x, vel.z) : yaw;
        double desiredYaw = yaw + wrap(heading - yaw) * 0.75;      // mostly the direction of travel
        double desiredPitch = Math.toRadians(13) - pitch * 0.45;   // look down the slope
        if (b.airborne) desiredPitch = Math.toRadians(13);
        if (!init) {
            camYaw = desiredYaw;
            camPitch = desiredPitch;
            focusY = rider.y;
            init = true;
        }
        camYaw += wrap(desiredYaw - camYaw) * (1 - Math.exp(-dt / 0.16));
        camPitch += (desiredPitch - camPitch) * (1 - Math.exp(-dt / 0.30));
        focusY += (rider.y - focusY) * (1 - Math.exp(-dt / 0.07));

        double dist = (mode == Mode.CHASE_FAR ? 6.0 : 3.4) + speed * 0.025;
        Vec3 focus = new Vec3(rider.x, focusY + 0.3, rider.z);
        Vec3 dir = new Vec3(-Math.sin(camYaw) * Math.cos(camPitch), -Math.sin(camPitch), Math.cos(camYaw) * Math.cos(camPitch));
        Vec3 pos = focus.subtract(dir.scale(dist));

        BlockHitResult hit = mc.level.clip(new ClipContext(focus, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, bike));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = focus.subtract(hit.getLocation()).normalize().scale(0.25);
            pos = hit.getLocation().add(back);
        }
        // look slightly ahead of the rider
        Vec3 lookAt = focus.add(-Math.sin(camYaw) * 1.2, -0.15, Math.cos(camYaw) * 1.2);
        Vec3 d = lookAt.subtract(pos);
        float viewYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float viewPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new View(pos, viewYaw, viewPitch, (float) Math.toDegrees(lean * 0.12));
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private BikeCamera() {}
}
