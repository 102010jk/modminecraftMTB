package com.descentmtb.client;

import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Poses the player model on the bike every frame: feet on the pedals (turning
 * with the cranks), hands on the grips (following the bars), torso in an
 * attack position that bends and stretches with the rider's legs, plus the
 * Descenders tweak tricks in the air.
 *
 * <p>Model space is the vanilla player model: pixels, y down, forward = -Z,
 * feet at y = 24, rider's right arm/leg at -X. The player renderer scales by
 * 0.9375, so 1 m = 17.07 px.
 */
public final class RiderPose {
    private static final float PX = 16f / 0.9375f;
    /** Grips relative to the feet (pedal axis): forward / up / half-width, metres. */
    private static final float GRIP_FWD = 0.46f, GRIP_UP = 0.74f, GRIP_HALF = 0.33f;
    private static final float CRANK_PX = 0.17f * PX;

    /** Descenders enduro tweaks (LB + right stick). */
    public enum Trick { NONE, NO_HANDER, TABLETOP, NAC_NAC, CAN_CAN, SUPERMAN }

    public static Trick trickFor(float x, float y) {
        if (Math.abs(x) < 0.35f && Math.abs(y) < 0.35f) return Trick.NONE;
        boolean side = Math.abs(x) >= 0.35f;
        if (y > 0.35f) return side ? Trick.TABLETOP : Trick.NO_HANDER;
        if (y < -0.35f) return side ? Trick.CAN_CAN : Trick.SUPERMAN;
        return Trick.NAC_NAC;
    }

    public static void apply(PlayerModel<?> m, LivingEntity entity, MountainBikeEntity bike, float pt) {
        BikeRenderState a = bike.rsPrev, b = bike.rsCur;
        float riderUp = (float) BikeRenderState.lerp(pt, a.riderUp, b.riderUp);
        float riderFwd = (float) BikeRenderState.lerp(pt, a.riderFwd, b.riderFwd);
        float crank = (float) BikeRenderState.lerp(pt, a.crank, b.crank);
        float steer = (float) BikeRenderState.lerp(pt, a.steer, b.steer);
        Trick trick = (bike == BikeClientController.riding()) ? BikeClientController.trick() : Trick.NONE;
        boolean bailed = b.bailed;

        // ---------------- torso ----------------
        float bend = 3.0f + clamp(-riderUp, -0.15f, 0.35f) * PX * 0.9f;    // px the shoulders drop
        float theta = clamp(0.38f + 0.05f * bend + riderFwd * 1.4f, 0.1f, 1.15f);
        if (trick == Trick.SUPERMAN) theta = 1.25f;
        if (bailed) theta = 0.2f;

        m.body.xRot = theta;
        m.body.yRot = 0;
        m.body.zRot = 0;
        m.body.y = bend;
        m.body.z = 0;
        m.head.y = bend;
        m.head.z = 0;
        m.head.xRot = -theta * 0.85f + 0.15f;     // eyes on the trail
        m.head.yRot = steer * 0.4f;

        float hipZ = 12f * (float) Math.sin(theta);
        float hipY = bend + 12f * (float) Math.cos(theta);

        // ---------------- legs to the pedals ----------------
        legTo(m.rightLeg, -1.9f, hipY, hipZ, crank);
        legTo(m.leftLeg, 1.9f, hipY, hipZ, crank + (float) Math.PI);

        // ---------------- arms to the grips ----------------
        float shoulderY = bend + 2f;
        float gz = -GRIP_FWD * PX, gy = 24f - GRIP_UP * PX, gx = GRIP_HALF * PX;
        float swing = (float) Math.sin(steer) * GRIP_HALF * PX;   // bars turning moves the grips
        armTo(m.rightArm, -5f, shoulderY, -gx, gy, gz - swing);
        armTo(m.leftArm, 5f, shoulderY, gx, gy, gz + swing);

        // ---------------- tricks ----------------
        switch (trick) {
            case NO_HANDER, TABLETOP -> {
                m.rightArm.xRot = -2.6f;
                m.rightArm.zRot = 0.6f;
                m.leftArm.xRot = -2.6f;
                m.leftArm.zRot = -0.6f;
            }
            case SUPERMAN -> {
                m.rightLeg.xRot = 1.25f;
                m.leftLeg.xRot = 1.25f;
                m.rightLeg.z = hipZ + 2f;
                m.leftLeg.z = hipZ + 2f;
            }
            case NAC_NAC -> {
                m.leftLeg.xRot = 0.4f;
                m.leftLeg.zRot = -1.1f;
            }
            case CAN_CAN -> {
                m.leftLeg.xRot = -0.9f;
                m.leftLeg.zRot = 0.5f;
            }
            default -> {}
        }
        if (bailed) {
            m.rightArm.xRot = -1.6f;
            m.leftArm.xRot = -1.4f;
            m.rightArm.zRot = 0.7f;
            m.leftArm.zRot = -0.7f;
        }

        // first-person helmet cam: we render our own body, but not the head we are looking out of
        if (entity == Minecraft.getInstance().player && BikeCamera.helmet()) {
            m.head.visible = false;
            m.hat.visible = false;
        }

        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** Points a leg from its hip pivot toward its pedal. */
    private static void legTo(ModelPart leg, float x, float hipY, float hipZ, float crank) {
        float footZ = -(float) Math.cos(crank) * CRANK_PX;
        float footY = 23.0f + (float) Math.sin(crank) * CRANK_PX;
        leg.x = x;
        leg.y = hipY;
        leg.z = hipZ;
        float vy = footY - hipY, vz = footZ - hipZ;
        leg.xRot = (float) Math.atan2(vz, vy);
        leg.yRot = 0;
        leg.zRot = x < 0 ? 0.04f : -0.04f;
    }

    /** Points an arm from the shoulder at (sx, sy, 0) toward a grip at (gx, gy, gz). */
    private static void armTo(ModelPart arm, float sx, float sy, float gx, float gy, float gz) {
        arm.x = sx;
        arm.y = sy;
        arm.z = 0;
        float vx = gx - sx, vy = gy - sy, vz = gz;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return;
        vx /= len;
        vy /= len;
        vz /= len;
        // rest pose points down (+Y); rotationZYX applies X first, then Z
        arm.xRot = (float) Math.asin(clamp(vz, -1, 1));
        arm.yRot = 0;
        arm.zRot = (float) Math.atan2(-vx, vy);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private RiderPose() {}
}
