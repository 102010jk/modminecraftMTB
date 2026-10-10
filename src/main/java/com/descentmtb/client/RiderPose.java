package com.descentmtb.client;

import com.descentmtb.client.model.SkiModel;
import com.descentmtb.client.ski.SkiPoleLayer;
import com.descentmtb.client.ski.SkiStance;
import com.descentmtb.client.ski.SkierPose;
import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.OneHand;
import com.descentmtb.physics.V3;
import com.descentmtb.ski.SkiBrand;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Poses the player model on the bike every frame: feet on the pedals (turning
 * with the cranks), hands on the grips (following the bars), torso in an
 * attack position that bends and stretches with the rider's legs, plus the
 * Descenders tweak tricks in the air. On skis the skier instead (see {@link #applySki}).
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

    /** Last drawn limb pose per rider, so one trick flows into the next instead of snapping through neutral. */
    private static final class LimbMemory {
        final float[][] v = new float[4][7];
        long last;
        double window;
        boolean valid;
    }

    private static final java.util.Map<LivingEntity, LimbMemory> MEMORY = new java.util.WeakHashMap<>();
    /** Limb inertia during and just after tricks (s); short enough that a catch still looks snappy. */
    private static final double LIMB_TAU = 0.07, CARRY_WINDOW = 0.3;

    /**
     * Gives the limbs a little inertia while tricks play and for a moment after: chaining a whip into a barspin, the
     * hands and feet travel from one pose to the next. Off otherwise, so the feet stay locked to turning pedals.
     */
    private static void carryThrough(LivingEntity entity, ModelPart[] limbs, boolean tricking) {
        LimbMemory mem = MEMORY.computeIfAbsent(entity, k -> new LimbMemory());
        long now = System.nanoTime();
        double dt = mem.valid ? Math.max(0, Math.min(0.1, (now - mem.last) / 1e9)) : 0;
        mem.last = now;
        mem.window = tricking ? CARRY_WINDOW : Math.max(0, mem.window - dt);
        boolean smooth = mem.valid && mem.window > 0;
        float k = (float) (1 - Math.exp(-dt / LIMB_TAU));
        for (int i = 0; i < limbs.length; i++) {
            ModelPart p = limbs[i];
            float[] v = mem.v[i];
            if (smooth) {
                p.x = v[0] + (p.x - v[0]) * k; p.y = v[1] + (p.y - v[1]) * k; p.z = v[2] + (p.z - v[2]) * k;
                p.xRot = v[3] + (p.xRot - v[3]) * k; p.yRot = v[4] + (p.yRot - v[4]) * k; p.zRot = v[5] + (p.zRot - v[5]) * k;
                p.yScale = v[6] + (p.yScale - v[6]) * k;
            }
            v[0] = p.x; v[1] = p.y; v[2] = p.z; v[3] = p.xRot; v[4] = p.yRot; v[5] = p.zRot; v[6] = p.yScale;
        }
        mem.valid = true;
    }

    public static Stance stance(float riderUp, float riderFwd) {
        // attack position: knees and elbows bent, chest low, head over the stem
        float bend = 4.5f + clamp(-riderUp, -0.15f, 0.30f) * PX * 0.9f;
        float shoulderFwd = clamp(0.06f + riderFwd * 0.8f, -0.10f, 0.35f) * PX;
        float theta = clamp(0.52f + 0.025f * (bend - 4.5f) + riderFwd * 0.9f, 0.2f, 1.1f);
        return new Stance(bend, shoulderFwd, theta);
    }

    public static void apply(PlayerModel<?> m, LivingEntity entity, MountainBikeEntity bike, float pt) {
        if (bike.bikeType().ski()) {
            applySki(m, entity, bike, pt);
            finish(m, entity);
            return;
        }
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
        if (type.motor()) {
            // dirt bike: both boots planted on the footpegs, knees out a little
            legToFoot(m.rightLeg, -1.9f, hipY, hipZ, 23.0f, 0f, 0.10f);
            legToFoot(m.leftLeg, 1.9f, hipY, hipZ, 23.0f, 0f, 0.10f);
        } else {
            legTo(m.rightLeg, -1.9f, hipY, hipZ, crank);
            legTo(m.leftLeg, 1.9f, hipY, hipZ, crank + (float) Math.PI);
        }

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
        // ringing the bell: the left hand leaves the grip for the bell on the left of the stem and comes back
        if (b.oneHand && !bailed) {
            float t = (float) (a.oneHand ? BikeRenderState.lerp(pt, a.oneHandTime, b.oneHandTime) : b.oneHandTime);
            float reach = (float) OneHand.reach(t);
            // the bell sits ~0.19 m left of the stem, a little above and behind the grip line
            float bx = 3.4f, by = gy - 1.6f, bz = gz + 1.2f;
            float press = (float) OneHand.squeeze(t) * 0.9f;       // thumb pressing down on it
            left.set(left.x + (bx - left.x) * reach, left.y + (by + press - left.y) * reach, left.z + (bz - left.z) * reach);
        }
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
        if (trick == Trick.HEELCLICKER) {
            m.rightLeg.yScale = 1f;
            m.leftLeg.yScale = 1f;
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
            case HEELCLICKER -> {
                // Both legs thrown forward and up in front of the bars (negative xRot), the heels knocking together:
                // the legs point forward, so the roll sign is flipped: positive swings the right foot inward, negative the left.
                // Arms stay on the grips (the default pose).
                float knock = 0.5f + 0.5f * (float) Math.sin((entity.tickCount + pt) * 1.1f);
                float inward = 0.20f + 0.22f * knock * knock;
                m.rightLeg.xRot = m.leftLeg.xRot = -2.05f;
                m.rightLeg.zRot = inward;
                m.leftLeg.zRot = -inward;
                m.rightLeg.y = m.leftLeg.y = hipY - 1.5f;
            }
            case BARSPIN -> {
                // Hands follow the catch/release in a full timed bar rotation.
                // hands follow the bars on the same thrown-then-caught curve the bars spin on
                float turn = (float) (TrickAnimation.spinCurve(progress) * Math.PI * 2);
                m.rightArm.xRot = -1.3f - .45f * (float) Math.sin(turn);
                m.leftArm.xRot = -1.1f + .45f * (float) Math.sin(turn);
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
        carryThrough(entity, limbs, trick != Trick.NONE && !bailed);
        // Keep braking visible even without an active trick.
        m.rightArm.xRot -= brake * .09f; m.leftArm.xRot -= brake * .09f;
        m.rightArm.zRot += brake * .04f; m.leftArm.zRot -= brake * .04f;
        if (bailed) {
            m.rightArm.xRot = -1.6f;
            m.leftArm.xRot = -1.4f;
            m.rightArm.zRot = 0.7f;
            m.leftArm.zRot = -0.7f;
        }

        finish(m, entity);
    }

    /** Helmet camera head hiding and the overlay layers (hat, jacket, sleeves, pants) following the posed parts. */
    private static void finish(PlayerModel<?> m, LivingEntity entity) {
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

    // =====================================================================
    //  Skis
    // =====================================================================

    private static final SkierPose SKIER = new SkierPose();
    private static final SkierPose.Input SKIER_IN = new SkierPose.Input();
    /** Pole shafts of the skier last posed, per rider, for {@link SkiPoleLayer}: {left xyz, right xyz} (model px, unit). */
    private static final Map<LivingEntity, float[]> POLES = Collections.synchronizedMap(new WeakHashMap<>());

    /** A pole plant in progress, per rider: started when the carve changes side. */
    private static final class PlantMemory {
        float lastSteer;
        int side;
        long start, lastPlant;
    }

    private static final Map<LivingEntity, PlantMemory> PLANTS = new WeakHashMap<>();
    /** One plant (reach, touch, swing past) and the shortest time between two plants (s). */
    private static final double PLANT_TIME = 0.5, PLANT_GAP = 0.55;
    /** |carve angle| that counts as turning to one side (rad). */
    private static final float PLANT_STEER = 0.035f;

    /** Total backward bend of the bent GS poles below the grip (24 + 12 degrees, see {@link SkiModel#renderPole}). */
    private static final float POLE_BEND = (float) Math.toRadians(36);

    /** The giant-slalom race pairs come with bent poles (SkiModel draws them for Atomic and Fischer). */
    private static boolean bentPoles(SkiBrand brand) {
        return brand == SkiBrand.ATOMIC_REDSTER_G9 || brand == SkiBrand.FISCHER_RC4_WC;
    }

    /** The pole directions of the last ski pose of {@code rider} (null when it is not on skis). */
    public static float[] poles(LivingEntity rider) {
        return POLES.get(rider);
    }

    /**
     * The skier: legs end in the boots where {@link SkiStance} puts the skis, athletic stance or race tuck, hands
     * holding the poles, the ski tricks. All the geometry is solved by {@link SkierPose}; this only aims the parts.
     */
    private static void applySki(PlayerModel<?> m, LivingEntity entity, MountainBikeEntity skis, float pt) {
        BikeRenderState a = skis.rsPrev, b = skis.rsCur;
        double t = pt;
        BikeType type = skis.bikeType();
        SkierPose.Input in = SKIER_IN;
        in.stance.read(skis, a, b, pt);
        in.riderFwd = (float) BikeRenderState.lerp(t, a.riderFwd, b.riderFwd);
        in.gripFwd = type.gripFwd;
        in.gripUp = type.gripUp;
        in.gripHalf = type.gripHalf;
        in.lean = (float) BikeRenderState.lerp(t, a.lean, b.lean);
        in.riderLean = (float) BikeRenderState.lerp(t, a.riderLean, b.riderLean);
        BikeParams p = skis.params();
        in.skiDrop = (float) (p.axleDrop - p.wheelRadius);
        in.bailed = b.bailed;
        in.poleBend = bentPoles(skis.skiBrand()) ? POLE_BEND : 0f;
        // where the player is drawn, from the skis' COM in the frame's own axes (the skis roll about the COM, the
        // rider about this point): exact whatever the vehicle does with the rider's position
        double yaw = BikeRenderState.lerp(t, a.yaw, b.yaw), pitch = BikeRenderState.lerp(t, a.pitch, b.pitch);
        V3 com = BikeRenderState.lerp(t, a.com, b.com);
        Vec3 at = entity.getPosition(pt);
        double dx = at.x - com.x, dy = at.y - com.y, dz = at.z - com.z;
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double along = -dx * sy * cp + dy * sp + dz * cy * cp;          // forward = (-sy cp, sp, cy cp)
        double up = dx * sy * sp + dy * cp - dz * cy * sp;              // up = (sy sp, cp, -cy sp)
        double left = dx * cy + dz * sy;                                // left = (cy, 0, sy)
        if (b.bailed || along * along + up * up + left * left > 4) {
            left = 0;
            up = type.feetUp;
            along = type.feetFwd;
        }
        in.feetX = (float) -left;
        in.feetY = (float) up;
        in.feetZ = (float) -along;
        plant(entity, in);

        SkierPose sk = SKIER;
        sk.solve(in);

        m.body.xRot = sk.theta;
        m.body.yRot = sk.bodyYaw;
        m.body.zRot = 0;
        m.body.x = sk.originX;
        m.body.y = sk.bend;
        m.body.z = sk.shoulderZ;
        m.head.x = sk.originX;
        m.head.y = sk.bend;
        m.head.z = sk.shoulderZ;
        m.head.xRot = sk.headPitch;
        m.head.yRot = sk.headYaw;
        legAim(m.leftLeg, sk.hipXL, sk.hipY, sk.hipZ, sk.soleL, sk.legYawL);
        legAim(m.rightLeg, sk.hipXR, sk.hipY, sk.hipZ, sk.soleR, sk.legYawR);
        armAim(m.leftArm, sk.shoulderL, sk.handL);
        armAim(m.rightArm, sk.shoulderR, sk.handR);

        SkiModel.setCuffLean(skis, sk.cuffLeanL, sk.cuffLeanR);

        float[] poles = POLES.computeIfAbsent(entity, k -> new float[6]);
        System.arraycopy(sk.poleL, 0, poles, 0, 3);
        System.arraycopy(sk.poleR, 0, poles, 3, 3);
    }

    private static final SkierPose EYE_POSE = new SkierPose();
    private static final SkierPose.Input EYE_IN = new SkierPose.Input();

    /**
     * Eye position of the skier relative to the feet in metres: {forward, up}, as {@link Stance#eye} for the bikes:
     * the knees, hips and race tuck move the helmet camera, the tricks do not (as on the bikes).
     */
    public static double[] skiEye(MountainBikeEntity skis, BikeRenderState a, BikeRenderState b, float pt) {
        SkierPose.Input in = EYE_IN;
        in.stance.read(skis, a, b, pt);
        in.stance.trick = Trick.NONE;
        in.stance.amount = 0;
        BikeType type = b.bikeType;
        in.riderFwd = (float) BikeRenderState.lerp(pt, a.riderFwd, b.riderFwd);
        in.gripFwd = type.gripFwd;
        in.gripUp = type.gripUp;
        in.gripHalf = type.gripHalf;
        in.lean = in.riderLean = 0;
        if (skis != null) {
            BikeParams p = skis.params();
            in.skiDrop = (float) (p.axleDrop - p.wheelRadius);
        }
        in.feetX = 0;
        in.feetY = type.feetUp;
        in.feetZ = -type.feetFwd;
        in.plantSide = 0;
        in.bailed = false;
        EYE_POSE.solve(in);
        return new double[]{(-EYE_POSE.shoulderZ + 2.5f) / PX, (24f - EYE_POSE.bend + 2.0f) / PX};
    }

    /** Starts a pole plant with the inside hand when the carve swaps sides, and feeds the running one to the pose. */
    private static void plant(LivingEntity entity, SkierPose.Input in) {
        PlantMemory pm = PLANTS.computeIfAbsent(entity, k -> new PlantMemory());
        long now = System.nanoTime();
        SkiStance.Input st = in.stance;
        boolean riding = !st.airborne && !in.bailed && st.trick == Trick.NONE;
        boolean can = riding && st.brake < 0.3f && st.speed > 2.5f && st.speed < 16f;
        float steer = st.steer;
        if (Math.abs(steer) > PLANT_STEER) {
            if (can && pm.lastSteer != 0 && Math.signum(steer) != Math.signum(pm.lastSteer)
                    && (now - pm.lastPlant) / 1e9 > PLANT_GAP) {
                pm.side = steer > 0 ? -1 : 1;           // the inside hand of the new turn (turning right: the right)
                pm.start = pm.lastPlant = now;
            }
            pm.lastSteer = steer;
        }
        double phase = pm.side == 0 ? 1 : (now - pm.start) / 1e9 / PLANT_TIME;
        if (phase >= 1 || !riding) pm.side = 0;
        in.plantSide = pm.side;
        in.plantPhase = pm.side == 0 ? 0 : (float) phase;
    }

    /**
     * Points a leg from the hip at the sole (model px), turned about its own axis by {@code twist} (with its ski);
     * the leg box is squashed to the hip-sole distance (reads as a bent knee).
     */
    private static void legAim(ModelPart leg, float x, float hipY, float hipZ, float[] sole, float twist) {
        leg.x = x;
        leg.y = hipY;
        leg.z = hipZ;
        float vx = sole[0] - x, vy = sole[1] - hipY, vz = sole[2] - hipZ;
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return;
        aim(leg, vx / len, vy / len, vz / len, twist);
        leg.yScale = clamp(len / 12f, SkierPose.LEG_SCALE_MIN, SkierPose.LEG_SCALE_MAX);
    }

    /** Points an arm from the shoulder pivot so the fist centre lands on the hand target (model px). */
    private static void armAim(ModelPart arm, float[] shoulder, float[] hand) {
        arm.x = shoulder[0];
        arm.y = shoulder[1];
        arm.z = shoulder[2];
        float vx = hand[0] - shoulder[0], vy = hand[1] - shoulder[1], vz = hand[2] - shoulder[2];
        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1e-3f) return;
        aim(arm, vx / len, vy / len, vz / len, 0);
        arm.yScale = clamp(len / SkierPose.FIST, SkierPose.ARM_SCALE_MIN, SkierPose.ARM_SCALE_MAX);
    }

    /**
     * Rotates a limb whose rest pose hangs down (+Y) to point along the unit vector (dx, dy, dz), twisted by
     * {@code yRot = twist}. ModelPart applies X, then Y, then Z: the pitch is solved with the twist in place, the
     * roll swings the result onto the target.
     */
    private static void aim(ModelPart part, float dx, float dy, float dz, float twist) {
        float ct = (float) Math.cos(twist);
        float xr = (float) Math.asin(clamp(dz / Math.max(ct, 0.3f), -1, 1));
        float wx = (float) (Math.sin(xr) * Math.sin(twist)), wy = (float) Math.cos(xr);
        part.xRot = xr;
        part.yRot = twist;
        part.zRot = (float) (Math.atan2(dy, dx) - Math.atan2(wy, wx));
    }

    /** Points a leg from its hip pivot toward its pedal. */
    private static void legTo(ModelPart leg, float x, float hipY, float hipZ, float crank) {
        float footZ = -(float) Math.cos(crank) * CRANK_PX;
        float footY = 23.0f + (float) Math.sin(crank) * CRANK_PX;
        legToFoot(leg, x, hipY, hipZ, footY, footZ, 0.04f);
    }

    /** Points a leg from the hip at the foot (y, z in player-model pixels); {@code splay} rolls the knee outwards. */
    private static void legToFoot(ModelPart leg, float x, float hipY, float hipZ, float footY, float footZ, float splay) {
        leg.x = x;
        leg.y = hipY;
        leg.z = hipZ;
        float vy = footY - hipY, vz = footZ - hipZ;
        leg.xRot = (float) Math.atan2(vz, vy);
        leg.yRot = 0;
        leg.zRot = x < 0 ? splay : -splay;
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
