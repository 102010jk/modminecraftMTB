package com.descentmtb.client;

import com.descentmtb.network.RagdollPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * After a bail the player tumbles through the air, slides to a stop lying on
 * the ground, then gets back up and is a normal player again (walk back to
 * your bike). Purely visual on top of the player's real (vanilla) motion; the
 * local player also gets a crash camera and can't move until they stand up.
 */
public final class RagdollClient {
    private static final int MIN_DOWN = 28;     // ticks lying on the ground at least
    private static final int MAX_TICKS = 110;
    private static final int GET_UP = 14;

    private static final class State {
        float yaw;              // direction of the throw (rad)
        float angle, angleO;    // tumble angle (rad)
        float spin;             // rad/tick while airborne
        float lie, lieO;        // 0 = upright/tumbling height, 1 = lying flat
        int age, downTicks, getUp = -1;
        boolean landed;
        CameraType savedCamera;
        net.minecraft.world.entity.Pose savedPose;
    }

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static float camYaw;

    public static void onPayload(RagdollPayload m) {
        State s = new State();
        double speed = Math.sqrt(m.vx() * m.vx() + m.vy() * m.vy() + m.vz() * m.vz());
        s.yaw = (float) Math.atan2(-m.vx(), m.vz());
        s.spin = (float) Math.max(0.08, Math.min(0.32, speed * 0.018));
        STATES.put(m.playerId(), s);
        if (DevAutopilot.ENABLED) com.descentmtb.DescentMtb.LOG.info("[ragdoll] received for {}", m.playerId());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && m.playerId() == mc.player.getId()) {
            camYaw = s.yaw;
            s.savedCamera = BikeCamera.originalCameraType();
            mc.player.getAbilities().flying = false;
            s.savedPose = mc.player.getForcedPose();
            mc.player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        }
    }

    public static boolean active(Entity e) {
        return STATES.containsKey(e.getId());
    }

    public static boolean localActive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && STATES.containsKey(mc.player.getId());
    }

    /** Local player is still on the ground (input locked). */
    public static boolean localLocked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        State s = STATES.get(mc.player.getId());
        return s != null && s.getUp < GET_UP / 2;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        STATES.entrySet().removeIf(en -> {
            Entity e = mc.level.getEntity(en.getKey());
            State s = en.getValue();
            if (!(e instanceof LivingEntity)) return true;
            // The animation packet can reach the client before vanilla's
            // passenger update. Give that dismount time to arrive.
            if (e.isPassenger() && s.age > 10) { restore(mc, e, s); return true; }
            if (e.isPassenger()) { s.age++; return false; }
            s.age++;
            s.angleO = s.angle;
            s.lieO = s.lie;
            if (!s.landed) {
                double moving = Math.min(1, e.getDeltaMovement().length() / .25);
                s.angle += s.spin * Math.exp(-s.age / 24.0) * (float) moving;
                if (e.onGround() && s.age > 3) s.landed = true;
            } else if (s.getUp < 0) {
                s.downTicks++;
                s.lie = Math.min(1f, s.lie + 0.2f);
                // settle the tumble onto the nearest "face down" orientation
                float target = nearestLying(s.angle);
                s.angle += (target - s.angle) * 0.35f;
                if (s.downTicks > MIN_DOWN && e.getDeltaMovement().horizontalDistanceSqr() < 0.004) s.getUp = 0;
            }
            if (s.age > MAX_TICKS && s.getUp < 0) s.getUp = 0;
            if (s.getUp >= 0) {
                s.getUp++;
                s.lie = Math.max(0f, 1f - s.getUp / (float) GET_UP);
                float target = nearestLying(s.angle) - (float) (Math.PI / 2);   // stand up
                s.angle += (target - s.angle) * 0.3f;
                if (s.getUp >= GET_UP) {
                    restore(mc, e, s);
                    return true;
                }
            }
            return false;
        });
        if (localActive() && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);   // so our own body is drawn
        }
    }

    private static void restore(Minecraft mc, Entity e, State s) {
        if (e == mc.player) {
            mc.player.setForcedPose(s.savedPose);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.descentmtb.network.RagdollRecoveryPayload());
            if (s.savedCamera != null) mc.options.setCameraType(s.savedCamera);
        }
    }

    private static float nearestLying(float a) {
        double twoPi = 2 * Math.PI;
        double base = Math.floor((a - Math.PI / 2) / twoPi) * twoPi + Math.PI / 2;
        return (float) (a - base < Math.PI ? base : base + twoPi);
    }

    /** Whole-body tumble; pushes onto the pose stack (caller pops). */
    static boolean transform(LivingEntity e, PoseStack pose, float pt) {
        State s = STATES.get(e.getId());
        if (s == null) return false;
        float angle = s.angleO + (s.angle - s.angleO) * pt;
        float lie = s.lieO + (s.lie - s.lieO) * pt;
        // The projected body extent keeps a rotating body above its real collision floor.
        float pivot = .9f * Math.abs((float) Math.cos(angle)) + .25f * Math.abs((float) Math.sin(angle)) + .03f;
        pose.pushPose();
        pose.translate(0, pivot, 0);
        pose.mulPose(Axis.YP.rotation((float) Math.PI - s.yaw));
        pose.mulPose(Axis.XP.rotation(angle));
        pose.mulPose(Axis.YP.rotation(-((float) Math.PI - s.yaw)));
        pose.translate(0, -0.9f, 0);
        return true;
    }

    /** Limp limbs while down. */
    public static void limbs(PlayerModel<?> m, LivingEntity e, float ageInTicks) {
        State s = STATES.get(e.getId());
        if (s == null) return;
        m.body.xRot=0;m.body.y=0;m.body.z=0;m.head.y=0;m.head.z=0;
        m.rightArm.y=m.leftArm.y=2;m.rightArm.z=m.leftArm.z=0;
        m.rightLeg.y=m.leftLeg.y=12;m.rightLeg.z=m.leftLeg.z=0;
        float flail = s.landed ? 0.05f : 0.35f;
        float w = (float) Math.sin(ageInTicks * 0.9f) * flail;
        m.rightArm.xRot = -0.4f + w;
        m.rightArm.zRot = 1.1f;
        m.leftArm.xRot = -0.3f - w;
        m.leftArm.zRot = -1.0f;
        m.rightLeg.xRot = 0.25f - w;
        m.rightLeg.zRot = 0.35f;
        m.leftLeg.xRot = -0.2f + w;
        m.leftLeg.zRot = -0.3f;
        m.head.xRot = 0.3f;
        m.hat.copyFrom(m.head);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** Crash camera for the local player: from behind the throw, watching the tumble. */
    static BikeCamera.View cameraView(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!localActive() || mc.player == null || mc.level == null) return null;
        Vec3 c = mc.player.getPosition(pt).add(0, 0.6, 0);
        Vec3 dir = new Vec3(-Math.sin(camYaw), 0, Math.cos(camYaw));
        Vec3 want = c.subtract(dir.scale(4.2)).add(0, 1.8, 0);
        var hit = mc.level.clip(new ClipContext(c, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        Vec3 pos = hit.getType() == HitResult.Type.MISS ? want
                : hit.getLocation().add(c.subtract(hit.getLocation()).normalize().scale(0.25));
        Vec3 d = c.subtract(pos);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new BikeCamera.View(pos, yaw, pitch, 0f);
    }

    private RagdollClient() {}
}
