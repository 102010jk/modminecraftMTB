package com.descentmtb.client;

import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.entity.BikeType;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Poses the player model on the bike every frame: feet on the pedals (turning
 * with the cranks), hands on the grips (following the bars), torso in an
 * attack position that bends and stretches with the rider's legs, plus the
 * Descenders tweak tricks in the air.
 *
 * <p>Model space is the vanilla player model: pixels, y down, forward = -Z,
 * feet at y = 24, rider's right arm/leg at -X. The player renderer scales by
 * 0.9375, so 1 m = 17.07 px.
 *
 * <p>The player model is shared by every player, and vanilla's {@code setupAnim} does not reset everything this
 * class writes (the body / head depth, the limb scales and the sleeves' and pants' copies of them). Each model that
 * was posed is remembered, and {@link #reset} puts back what {@link #apply} changed the next time that model
 * animates someone who is not riding. Everything here runs on the render thread, which is why the scratch storage
 * below can be static.
 */
public final class RiderPose {
    private static final float PX = 16f / 0.9375f;
    private static final float CRANK_PX = 0.17f * PX;

    /** What {@link #apply} did to one model, so {@link #reset} can undo it. */
    private static final class Applied {
        /** The model was posed since the last reset. */
        boolean posed;
        /** The helmet camera hid the head and hat, whose visibility was {@code headVisible} / {@code hatVisible}. */
        boolean headHidden, headVisible, hatVisible;
    }

    private static final Map<PlayerModel<?>, Applied> APPLIED = Collections.synchronizedMap(new WeakHashMap<>());

    // scratch storage reused every frame (render thread only)
    private static final ModelPart[] LIMBS = new ModelPart[4];
    private static final float[][] NORMAL = new float[4][6];
    private static final float[] SCALE = new float[4];
    private static final Vector3f RIGHT_GRIP = new Vector3f(), LEFT_GRIP = new Vector3f(), GRIP_OFFSET = new Vector3f();

    /** Body stance in model px: how far the shoulders drop, how far forward they sit, torso pitch. */
    public record Stance(float bend, float shoulderFwd, float theta) {
        /** Eye position relative to the feet in metres: {forward, up}. */
        public double[] eye() {
            return new double[]{(shoulderFwd + 2.5f) / PX, (24f - bend + 2.0f) / PX};
        }
    }

    public static Stance stance(float riderUp, float riderFwd) {
        // attack position: knees and elbows bent, chest low, head over the stem
        float bend = 4.5f + clamp(-riderUp, -0.15f, 0.30f) * PX * 0.9f;
        float shoulderFwd = clamp(0.06f + riderFwd * 0.8f, -0.10f, 0.35f) * PX;
        float theta = clamp(0.52f + 0.025f * (bend - 4.5f) + riderFwd * 0.9f, 0.2f, 1.1f);
        return new Stance(bend, shoulderFwd, theta);
    }

    public static void apply(PlayerModel<?> m, LivingEntity entity, MountainBikeEntity bike, float pt) {
        BikeRenderState a = bike.rsPrev, b = bike.rsCur;
        float riderUp = (float) BikeRenderState.lerp(pt, a.riderUp, b.riderUp);
        float riderFwd = (float) BikeRenderState.lerp(pt, a.riderFwd, b.riderFwd);
        float crank = (float) BikeRenderState.lerp(pt, a.crank, b.crank);
        float steer = (float) BikeRenderState.lerp(pt, a.steer, b.steer);
        float brake = (float) BikeRenderState.lerp(pt, a.brake, b.brake);
        // never blend the progress / amount of one trick into another's: the new trick starts from its own values
        boolean sameTrick = a.trick == b.trick;
        float progress = sameTrick ? (float) BikeRenderState.lerp(pt, a.trickProgress, b.trickProgress) : (float) b.trickProgress;
        Trick trick = b.trick;
        float amount = (float) TrickAnimation.ease(sameTrick ? BikeRenderState.lerp(pt, a.trickAmount, b.trickAmount) : b.trickAmount);
        BikeType type = bike.bikeType();
        boolean bailed = b.bailed;

        // ---------------- torso ----------------
        Stance st = stance(riderUp, riderFwd);
        float bend = st.bend();
        float sz = -st.shoulderFwd();                 // shoulders ahead of the pedals (-Z = forward)
        float theta = st.theta();
        if (trick == Trick.SUPERMAN || trick == Trick.SUPERMAN_SEATGRAB) theta += (1.35f - theta) * amount;
        if (bailed) theta = 0.2f;

        m.body.xRot = theta;
        m.body.yRot = 0;
        m.body.zRot = 0;
        m.body.y = bend;
        m.body.z = sz;
        m.head.y = bend;
        m.head.z = sz;
        m.head.xRot = -theta * 0.85f + 0.15f;     // eyes on the trail
        m.head.yRot = steer * 0.4f;

        float hipZ = sz + 12f * (float) Math.sin(theta);
        float hipY = bend + 12f * (float) Math.cos(theta);

        // ---------------- legs to the pedals ----------------
        legTo(m.rightLeg, -1.9f, hipY, hipZ, crank);
        legTo(m.leftLeg, 1.9f, hipY, hipZ, crank + (float) Math.PI);

        // ---------------- arms to the grips ----------------
        float shoulderY = bend + 2f;
        float gz = -type.gripFwd * PX, gy = 24f - type.gripUp * PX, gx = type.gripHalf * PX;
        // grips turn about the raked head tube; when the rider rolls less than the bike (table) follow the bike
        float bikeLean = (float) BikeRenderState.lerp(pt, a.lean, b.lean);
        float riderLean = (float) BikeRenderState.lerp(pt, a.riderLean, b.riderLean);
        Vector3f right = RIGHT_GRIP.set(-gx, gy, gz).add(GripGeometry.gripOffset(type, false, steer, GRIP_OFFSET));
        Vector3f left = LEFT_GRIP.set(gx, gy, gz).add(GripGeometry.gripOffset(type, true, steer, GRIP_OFFSET));
        GripGeometry.intoRiderRollInPlace(right, riderLean, bikeLean);
        GripGeometry.intoRiderRollInPlace(left, riderLean, bikeLean);
        armTo(m.rightArm, -5f, shoulderY, sz, right.x, right.y, right.z);
        armTo(m.leftArm, 5f, shoulderY, sz, left.x, left.y, left.z);

        // ---------------- tricks ----------------
        ModelPart[] limbs = LIMBS;
        limbs[0] = m.rightArm;
        limbs[1] = m.leftArm;
        limbs[2] = m.rightLeg;
        limbs[3] = m.leftLeg;
        for (int i = 0; i < limbs.length; i++) {
            ModelPart limb = limbs[i];
            float[] n = NORMAL[i];
            n[0] = limb.x; n[1] = limb.y; n[2] = limb.z; n[3] = limb.xRot; n[4] = limb.yRot; n[5] = limb.zRot;
            SCALE[i] = limb.yScale;
        }
        if (trick == Trick.NO_HANDER || trick == Trick.TABLETOP || bailed) {
            m.rightArm.yScale = 1f;
            m.leftArm.yScale = 1f;
        }
        if (trick == Trick.SUPERMAN || trick == Trick.SUPERMAN_SEATGRAB || trick == Trick.NAC_NAC || trick == Trick.CAN_CAN || trick == Trick.TAILWHIP) {
            m.leftLeg.yScale = 1f;
            if (trick == Trick.SUPERMAN || trick == Trick.SUPERMAN_SEATGRAB || trick == Trick.TAILWHIP) m.rightLeg.yScale = 1f;
        }
        switch (trick) {
            case NO_HANDER -> {
                m.rightArm.xRot = -2.6f;
                m.rightArm.zRot = 0.6f;
                m.leftArm.xRot = -2.6f;
                m.leftArm.zRot = -0.6f;
            }
            case TUCK_NO_HANDER -> {
                m.rightArm.xRot = m.leftArm.xRot = -1.8f;
                m.rightArm.zRot = 1.4f; m.leftArm.zRot = -1.4f;
                m.rightArm.yScale = m.leftArm.yScale = 1f;
            }
            case TABLETOP -> {
                m.rightLeg.xRot = -0.65f;
                m.leftLeg.xRot = -0.35f;
                m.rightLeg.zRot = 0.35f * b.trickSide;
                m.leftLeg.zRot = 0.35f * b.trickSide;
            }
            case SUPERMAN, SUPERMAN_SEATGRAB -> {
                m.rightLeg.xRot = 1.25f;
                m.leftLeg.xRot = 1.25f;
                m.rightLeg.z = hipZ + 2f;
                m.leftLeg.z = hipZ + 2f;
                if (trick == Trick.SUPERMAN_SEATGRAB) {
                    m.leftArm.xRot = 0.8f; m.leftArm.yScale = 1f;
                }
            }
            case NAC_NAC -> {
                m.leftLeg.xRot = 0.4f;
                m.leftLeg.zRot = -1.1f;
            }
            case CAN_CAN -> {
                m.leftLeg.xRot = -0.9f;
                m.leftLeg.zRot = 0.5f;
            }
            case BARSPIN -> {
                // Hands follow the catch/release in a full timed bar rotation.
                m.rightArm.xRot = -1.3f - .45f * (float) Math.sin(progress * Math.PI * 2);
                m.leftArm.xRot = -1.1f + .45f * (float) Math.sin(progress * Math.PI * 2);
                m.rightArm.zRot = 0.65f * b.trickSide;
                m.leftArm.zRot = -0.85f * b.trickSide;
                m.rightArm.yScale = m.leftArm.yScale = 0.8f;
            }
            case TAILWHIP -> {
                m.rightArm.xRot -= .2f * (float) Math.sin(progress * Math.PI);
                m.leftArm.xRot += .15f * (float) Math.sin(progress * Math.PI);
                m.rightLeg.xRot = m.leftLeg.xRot = -0.9f;
                m.rightLeg.zRot = 0.65f * b.trickSide;
                m.leftLeg.zRot = -0.65f * b.trickSide;
                m.rightLeg.y = m.leftLeg.y = hipY - 2f;
            }
            default -> {}
        }
        for (int i = 0; i < limbs.length; i++) {
            ModelPart part = limbs[i];
            float[] n = NORMAL[i];
            part.x = mix(n[0], part.x, amount); part.y = mix(n[1], part.y, amount); part.z = mix(n[2], part.z, amount);
            part.xRot = mix(n[3], part.xRot, amount); part.yRot = mix(n[4], part.yRot, amount); part.zRot = mix(n[5], part.zRot, amount);
            part.yScale = mix(SCALE[i], part.yScale, amount);
        }
        // Keep braking visible even without an active trick.
        m.rightArm.xRot -= brake * .09f; m.leftArm.xRot -= brake * .09f;
        m.rightArm.zRot += brake * .04f; m.leftArm.zRot -= brake * .04f;
        if (bailed) {
            m.rightArm.xRot = -1.6f;
            m.leftArm.xRot = -1.4f;
            m.rightArm.zRot = 0.7f;
            m.leftArm.zRot = -0.7f;
        }

        // first-person helmet cam: we render our own body, but not the head we are looking out of
        Applied applied = APPLIED.computeIfAbsent(m, k -> new Applied());
        applied.posed = true;
        boolean hideHead = entity == Minecraft.getInstance().player && BikeCamera.helmet();
        if (hideHead) {
            if (!applied.headHidden || m.head.visible) {
                applied.headVisible = m.head.visible;      // what the skin settings made of them
                applied.hatVisible = m.hat.visible;
            }
            applied.headHidden = true;
            m.head.visible = m.hat.visible = false;
        } else if (applied.headHidden) {
            restoreHead(m, applied);
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
        // the leg box is 12 px; squash it to the hip-pedal distance (reads as a bent knee)
        leg.yScale = clamp((float) Math.sqrt(vy * vy + vz * vz) / 12f, 0.55f, 1.0f);
    }

    /** Points an arm from the shoulder at (sx, sy, sz) toward a grip at (gx, gy, gz). */
    private static void armTo(ModelPart arm, float sx, float sy, float sz, float gx, float gy, float gz) {
        arm.x = sx;
        arm.y = sy;
        arm.z = sz;
        float vx = gx - sx, vy = gy - sy, vz = gz - sz;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return;
        vx /= len;
        vy /= len;
        vz /= len;
        // rest pose points down (+Y); rotationZYX applies X first, then Z
        arm.xRot = (float) Math.asin(clamp(vz, -1, 1));
        arm.yRot = 0;
        arm.zRot = (float) Math.atan2(-vx, vy);
        arm.yScale = clamp(len / 11.0f, 0.6f, 1.0f);   // bent elbows: hands end on the grips
    }

    /**
     * Undoes what {@link #apply} did to a model that now animates someone not on a bike. Runs after vanilla's
     * {@code setupAnim}, which already restored every rotation and most offsets, so only what vanilla never writes
     * is put back: body / head depth and roll, the leg spread, the limb scales and the head / hat visibility if the
     * helmet camera hid them. Costs one map lookup when the model was not posed on a bike.
     */
    public static void reset(PlayerModel<?> m) {
        Applied applied = APPLIED.get(m);
        if (applied == null || !applied.posed) {
            return;
        }
        applied.posed = false;
        if (applied.headHidden) {
            restoreHead(m, applied);
        }
        restoreOrigin(m.body);
        restoreOrigin(m.head);
        m.rightLeg.x = m.rightLeg.getInitialPose().x;
        m.leftLeg.x = m.leftLeg.getInitialPose().x;
        unscale(m.rightArm);
        unscale(m.leftArm);
        unscale(m.rightLeg);
        unscale(m.leftLeg);
        // vanilla copied the stale parts onto the overlay layers before we got here
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /**
     * Shows the head and hat again, but only while they are still in the state the helmet camera left them in: the
     * player renderer sets every part's visibility from the skin settings before each player is animated, and a
     * visible head means that already happened for the player now drawn, whose own hat setting must win.
     */
    private static void restoreHead(PlayerModel<?> m, Applied applied) {
        if (!m.head.visible && !m.hat.visible) {
            m.head.visible = applied.headVisible;
            m.hat.visible = applied.hatVisible;
        }
        applied.headHidden = false;
    }

    /** Back to the model's own x, z and roll (vanilla animates the other rotations and y every frame). */
    private static void restoreOrigin(ModelPart part) {
        var initial = part.getInitialPose();
        part.x = initial.x;
        part.z = initial.z;
        part.zRot = initial.zRot;
    }

    private static void unscale(ModelPart part) {
        part.xScale = part.yScale = part.zScale = 1f;
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private RiderPose() {}
}
