package com.descentmtb.client.ski;

import com.descentmtb.trick.Trick;

/**
 * The skier's body, solved in the vanilla player model's space (pixels, y down, -Z forward, +X = the rider's left,
 * feet at y = 24, 1 m = 17.07 px; see {@code RiderPose}). {@code RiderPose} turns it into limb angles, the pole layer
 * draws the poles from it. Pure maths (headless tests).
 *
 * <p>Athletic stance: boots hip-width in the bindings (from {@link SkiStance}), knees flexed with the rider's legs
 * ({@code riderUp}), hips back, chest forward, hands forward and a little above the hips holding the poles, which
 * trail back and down. Crouched and leaning forward at speed the skier folds into a race tuck: back flat, hands in
 * front of the face, poles tucked under the arms pointing back. Turning, the inside pole is planted at the start of
 * each new turn. Every ski trick has its own body and hand pose; the grabbing hand ends exactly on the ski where
 * {@link SkiStance#grabPoint} says.
 */
public final class SkierPose {
    public static final float PX = 16f / 0.9375f;
    /** Leg pivot x (hip joints) and shoulder pivot x in the model, px. */
    public static final float HIP_X = 1.9f, SHOULDER_X = 5f;
    /** Fist centre along the arm box (px from the shoulder pivot at yScale 1) and the arm's yScale range. */
    public static final float FIST = 9f, ARM_SCALE_MIN = 0.55f, ARM_SCALE_MAX = 1.15f;
    /** The leg box's yScale range (12 px at 1: hip to sole). */
    public static final float LEG_SCALE_MIN = 0.42f, LEG_SCALE_MAX = 1f;
    /** Comfortable shortest hip-to-sole distance before the body follows the boots up (m). */
    private static final float LEG_MIN = 0.36f;
    /** How far the fist reaches from the shoulder pivot at full stretch, px (a little slack for the assist). */
    private static final float REACH = FIST * ARM_SCALE_MAX * 0.98f;

    /** Everything the body depends on. */
    public static final class Input {
        public final SkiStance.Input stance = new SkiStance.Input();
        /** Body fore / aft (m, + = forward). */
        public float riderFwd;
        /** Pole grips from the boots at rest (BikeType): forward, up, half-width (m). */
        public float gripFwd = 0.30f, gripUp = 0.62f, gripHalf = 0.30f;
        /** Roll of the skis (frame) and of the rider's body (rad). */
        public float lean, riderLean;
        /** Ski model origin below the frame COM (m, negative: {@code axleDrop - wheelRadius}). */
        public float skiDrop = -0.52f;
        /**
         * Where the rider is drawn (the player's position) from the frame COM, in the frame's yaw / pitch axes before
         * the renderer's flip (m): x = -(along the rider's left), y = along up, z = -(along forward).
         */
        public float feetX, feetY = -0.48f, feetZ;
        /** How far the pole shaft bends back below the grip in all (rad): bent GS race poles, 0 for straight ones. */
        public float poleBend;
        /** Pole plant in progress: hand side (+1 left, -1 right, 0 none) and phase 0..1. */
        public int plantSide;
        public float plantPhase;
        public boolean bailed;
    }

    // ------------------------------------------------------------------ outputs
    /** Feet in ski model space (see {@link SkiStance#foot}) ... */
    public final float[] footL = new float[6], footR = new float[6];
    /** ... and the boot soles in model px. */
    public final float[] soleL = new float[3], soleR = new float[3];
    /**
     * Sideways offset of the whole body (body.x, head.x), px: the skis roll about the frame COM, the rider's model
     * about the player's position, so on a carve the skis sit off to one side of the player's feet. The body is
     * moved over the skis instead of the legs reaching across.
     */
    public float originX;
    /** Hip joints (legs' pivots): x of the left / right hip, y and z, px. */
    public float hipXL, hipXR, hipY, hipZ;
    /** Torso: forward pitch (body.xRot), twist (body.yRot), top of the torso y / z (neck) in px. */
    public float theta, bodyYaw, bend, shoulderZ;
    public float headPitch, headYaw;
    /** Race tuck 0..1 (also lowers the helmet camera). */
    public float tuck;
    /** Shoulder pivots and hand (fist) targets, px. */
    public final float[] shoulderL = new float[3], shoulderR = new float[3];
    public final float[] handL = new float[3], handR = new float[3];
    /** Pole shaft directions from the fist toward the tip, unit, model px axes (y down). */
    public final float[] poleL = new float[3], poleR = new float[3];
    /** How each leg turns about its own axis with its ski (leg yRot). */
    public float legYawL, legYawR;
    /**
     * Lean of each leg box in its boot (rad, + = forward toward the tip, in the ski's side plane), for the boot cuff
     * ({@code SkiModel.setCuffLean}).
     */
    public float cuffLeanL, cuffLeanR;

    private final float[] tmp = new float[3], grab = new float[3];

    public void solve(Input in) {
        SkiStance.Input st = in.stance;
        SkiStance.foot(st, 1, footL);
        SkiStance.foot(st, -1, footR);
        toRider(in, footL[0], footL[1], footL[2], soleL);
        toRider(in, footR[0], footR[1], footR[2], soleR);
        // the stance centre (between the boots) in the rider's model: the body stands over it
        toRider(in, 0, SkiStance.BOOT_SOLE, 0, tmp);
        float ox = tmp[0], oy = tmp[1] - 24f, oz = tmp[2];
        originX = ox;
        hipXL = ox + HIP_X;
        hipXR = ox - HIP_X;
        // the leg turns with its ski: ski yaw + (tip to +X) is a leg yRot of -yaw in the model
        legYawL = -footL[SkiStance.YAW];
        legYawR = -footR[SkiStance.YAW];

        Trick trick = st.trick;
        float k = trick == Trick.NONE ? 0f : clamp(st.amount, 0, 1);
        int ts = st.trickSide < 0 ? -1 : 1;
        float riderUp = st.riderUp, fwd = in.riderFwd;

        // ---------------- stance: knees, hips, chest ----------------
        float c = clamp(-riderUp / 0.28f, 0, 1);          // knee flex
        float e = clamp(riderUp / 0.15f, 0, 1);           // standing tall
        float brake = clamp(st.brake, 0, 1);
        // race tuck: crouched and leaning forward at speed (half a tuck without the forward lean), never in a trick
        tuck = smooth((c - 0.30f) / 0.45f) * smooth((st.speed - 7f) / 7f) * clamp(0.5f + fwd / 0.12f, 0, 1)
                * (1 - brake) * (1 - k);
        float hipH = 0.60f - 0.19f * c + 0.07f * e - 0.03f * tuck;
        float hipBack = clamp(0.04f + 0.09f * c - fwd * 0.55f, -0.15f, 0.25f) + 0.04f * tuck + 0.04f * brake;
        float th = clamp(0.42f + 0.42f * c - 0.08f * e + fwd * 1.1f, 0.12f, 1.15f) - 0.10f * brake;
        th += (1.32f - th) * tuck;
        float yawBody = 0;

        // trick body: torso pitch, knees, twist
        float tTheta = th, tHip = 0;
        switch (trick) {
            case SPREAD_EAGLE -> tTheta = 0.25f;
            case DAFFY -> tTheta = 0.35f;
            case IRON_CROSS -> { tTheta = 0.55f; tHip = -0.06f; }
            case BACK_SCRATCHER -> tTheta = 0.10f;
            case TIP_GRAB -> { tTheta = 1.15f; tHip = -0.08f; }
            case MUTE_GRAB -> { tTheta = 1.25f; tHip = -0.08f; }
            case JAPAN_GRAB -> { tTheta = 1.25f; tHip = -0.10f; }
            case SAFETY_GRAB -> { tTheta = 1.15f; tHip = -0.08f; }
            case TAIL_GRAB -> { tTheta = -0.05f; tHip = -0.10f; yawBody = -ts * 0.35f; }
            case TRUCK_DRIVER -> { tTheta = 0.70f; tHip = -0.10f; }
            default -> {}
        }
        th += (tTheta - th) * k;
        hipH += tHip * k;
        yawBody *= k;
        // in the air the body follows the boots up when a trick pulls them toward the hips (the legs cannot fold
        // shorter than the leg box allows)
        if (st.airborne || k > 0) {
            hipH = Math.max(hipH, minHipHeight(soleL, hipXL, oy, oz, hipBack));
            hipH = Math.max(hipH, minHipHeight(soleR, hipXR, oy, oz, hipBack));
        }
        hipY = 24f + oy - hipH * PX;
        hipZ = oz + hipBack * PX;
        theta = th;
        bodyYaw = yawBody;
        placeTorso();
        // a grab curls the body down to the ski where the arm alone is too short
        if (k > 0 && SkiStance.grabbedSide(trick, ts) != 0) reachAssist(in, trick, ts, k);

        // head: eyes down the hill (and into the turn), level even when the chest folds
        headPitch = -th * 0.92f + 0.12f;
        if (trick == Trick.BACK_SCRATCHER) headPitch -= 0.25f * k;
        headYaw = st.steer * 0.6f - yawBody * 0.8f;

        // ---------------- hands and poles ----------------
        restHand(in, 1, c, handL, poleL);
        restHand(in, -1, c, handR, poleR);
        if (k > 0) {
            trickHand(in, trick, ts, 1, k, handL, poleL);
            trickHand(in, trick, ts, -1, k, handR, poleR);
        }
        if (in.bailed) {
            // arms thrown out
            rel(1, 0.45f, -0.25f, 0.15f, handL);
            rel(-1, 0.45f, -0.25f, 0.15f, handR);
            dir(poleL, 0.7f, 0.5f, 0.4f);
            dir(poleR, -0.7f, 0.5f, 0.4f);
        }
        normalize(poleL);
        normalize(poleR);
        cuffLeanL = shinLean(in, footL, soleL, hipXL);
        cuffLeanR = shinLean(in, footR, soleR, hipXR);
    }

    /** The leg box's lean (sole up to the hip) in the ski's own frame: + = toward the tip. */
    private float shinLean(Input in, float[] foot, float[] sole, float hipX) {
        // rider px -> pre-flip rider frame (m) -> undo the rider's roll -> the skis' roll -> ski model space
        double X = -(hipX - sole[0]) / PX, Y = -(hipY - sole[1]) / PX, Z = (hipZ - sole[2]) / PX;
        double c = Math.cos(-in.riderLean), s = Math.sin(-in.riderLean);
        double x1 = X * c - Y * s, y1 = X * s + Y * c;
        c = Math.cos(in.lean);
        s = Math.sin(in.lean);
        double x2 = x1 * c - y1 * s, y2 = x1 * s + y1 * c;
        double vx = -x2, vy = y2, vz = Z;
        // into the ski's frame: the inverse of Ry(-yaw) Rx(pitch) Rz(-roll)
        c = Math.cos(foot[SkiStance.YAW]);
        s = Math.sin(foot[SkiStance.YAW]);
        double ax = vx * c + vz * s, az = -vx * s + vz * c;
        c = Math.cos(-foot[SkiStance.PITCH]);
        s = Math.sin(-foot[SkiStance.PITCH]);
        double by = vy * c - az * s, bz = vy * s + az * c;
        c = Math.cos(foot[SkiStance.ROLL]);
        s = Math.sin(foot[SkiStance.ROLL]);
        double cy = ax * s + by * c;
        return (float) Math.atan2(-bz, cy);
    }

    /** Rest hands (and the pole plant / tuck), poles trailing back and down. */
    private void restHand(Input in, int s, float c, float[] hand, float[] pole) {
        SkiStance.Input st = in.stance;
        // hands forward of the knees, a little above the hips, slightly wider than the shoulders
        float x = originX + s * in.gripHalf * PX;
        float y = hipY - (in.gripUp - 0.48f) * PX;
        float z = hipZ - (in.gripFwd + 0.08f + 0.18f * c + in.riderFwd * 0.3f) * PX;
        // braking: hands come forward and out for balance
        float br = clamp(st.brake, 0, 1);
        z -= 0.06f * br * PX;
        x += s * 0.04f * br * PX;
        float back = 0.80f, out = 0.16f;
        // pole plant: the hand reaches forward and down, the tip swings ahead into the snow and the skier passes it
        if (in.plantSide == s && in.plantPhase > 0 && in.plantPhase < 1) {
            float p = in.plantPhase;
            float reach = p < 0.25f ? smooth(p / 0.25f) : 1 - smooth((p - 0.25f) / 0.75f);
            z -= 0.10f * reach * PX;
            y += 0.05f * reach * PX;
            x += s * 0.03f * reach * PX;
            back -= 1.05f * reach;
            out -= 0.08f * reach;
        }
        float sb = (float) Math.sin(back), cb = (float) Math.cos(back);
        pole[0] = s * (float) Math.sin(out);
        pole[1] = cb;
        pole[2] = sb;
        // race tuck: fists in front of the chin, poles back under the arms along the ribs
        if (tuck > 0) {
            float hx = originX + s * 3.4f, hy = bend + 1.6f, hz = shoulderZ - 6.0f;
            x += (hx - x) * tuck;
            y += (hy - y) * tuck;
            z += (hz - z) * tuck;
            normalize(pole);
            // a bent GS pole's grip points down-back so the bend carries the shaft straight back under the arm
            float a = 0.7f * in.poleBend;
            float px = s * 0.10f, py = (float) Math.sin(a) - 0.06f, pz = (float) Math.cos(a);
            float n = (float) Math.sqrt(px * px + py * py + pz * pz);
            pole[0] += (px / n - pole[0]) * tuck;
            pole[1] += (py / n - pole[1]) * tuck;
            pole[2] += (pz / n - pole[2]) * tuck;
        }
        hand[0] = x;
        hand[1] = y;
        hand[2] = z;
    }

    /** Blends one hand (and its pole) toward the trick's pose by {@code k}. */
    private void trickHand(Input in, Trick trick, int ts, int s, float k, float[] hand, float[] pole) {
        float[] t = tmp;
        float px, py, pz;          // pole direction (unnormalised, model px axes, y down)
        boolean grabbing = false;
        int g = SkiStance.grabbedSide(trick, ts);
        switch (trick) {
            case SPREAD_EAGLE -> {
                rel(s, 0.50f, -0.22f, 0.10f, t);              // arms flung wide and up
                px = s * 0.75f; py = 0.6f; pz = 0.2f;
            }
            case DAFFY -> {
                if (s == ts) {                                // the lead leg's arm swings back
                    rel(s, 0.18f, 0.40f, -0.25f, t);
                    px = s * 0.1f; py = 0.35f; pz = 1f;
                } else {                                      // the other arm reaches forward
                    rel(s, 0.12f, 0.22f, 0.45f, t);
                    px = s * 0.1f; py = 0.85f; pz = 0.5f;
                }
            }
            case IRON_CROSS -> {
                rel(s, 0.32f, 0.28f, 0.32f, t);
                px = s * 0.4f; py = 0.7f; pz = 0.6f;
            }
            case BACK_SCRATCHER -> {
                rel(s, 0.30f, 0.30f, 0.38f, t);
                px = s * 0.3f; py = 0.9f; pz = 0.3f;
            }
            case TRUCK_DRIVER -> {
                grabAt(in, trick, s, t);                      // each hand on its own ski's tip
                grabbing = true;
                px = s * 0.6f; py = 0.35f; pz = 0.7f;
            }
            case TIP_GRAB, MUTE_GRAB, JAPAN_GRAB, SAFETY_GRAB, TAIL_GRAB -> {
                if (s == ts) {
                    grabAt(in, trick, g, t);
                    grabbing = true;
                    px = s * 0.5f; py = 0.3f; pz = 0.8f;
                } else {
                    // the free arm out for balance (thrown up and back on a Japan)
                    if (trick == Trick.JAPAN_GRAB) rel(s, 0.35f, -0.15f, 0.05f, t);
                    else if (trick == Trick.TAIL_GRAB) rel(s, 0.35f, 0.05f, 0.35f, t);
                    else rel(s, 0.42f, 0.18f, 0.05f, t);
                    px = s * 0.6f; py = 0.7f; pz = 0.4f;
                }
            }
            default -> {
                return;
            }
        }
        // a grabbing hand gets there a little behind the ski so it does not trail it
        float kh = grabbing ? Math.min(1f, k * 1.15f) : k;
        hand[0] += (t[0] - hand[0]) * kh;
        hand[1] += (t[1] - hand[1]) * kh;
        hand[2] += (t[2] - hand[2]) * kh;
        float n = (float) Math.sqrt(px * px + py * py + pz * pz);
        normalize(pole);
        pole[0] += (px / n - pole[0]) * k;
        pole[1] += (py / n - pole[1]) * k;
        pole[2] += (pz / n - pole[2]) * k;
    }

    private void placeTorso() {
        bend = hipY - 12f * (float) Math.cos(theta);
        shoulderZ = hipZ - 12f * (float) Math.sin(theta);
        shoulder(1, shoulderL);
        shoulder(-1, shoulderR);
    }

    /**
     * Moves the whole torso (hips, shoulders) toward the grab until the grabbing fist reaches the ski: the skier
     * curls up around the knees. Only along the body's own plane (y / z); the legs never fold shorter than the leg
     * box can show.
     */
    private void reachAssist(Input in, Trick trick, int ts, float k) {
        float need = 0, sy = 0, sz = 0;
        for (int s = -1; s <= 1; s += 2) {
            if (trick != Trick.TRUCK_DRIVER && s != ts) continue;
            int ski = trick == Trick.TRUCK_DRIVER ? s : SkiStance.grabbedSide(trick, ts);
            grabAt(in, trick, ski, tmp);
            float[] sh = s > 0 ? shoulderL : shoulderR;
            float dx = tmp[0] - sh[0], dy = tmp[1] - sh[1], dz = tmp[2] - sh[2];
            float r = (float) Math.sqrt(dy * dy + dz * dz);
            float rt = (float) Math.sqrt(Math.max(0, REACH * REACH - dx * dx));
            if (r > rt && r - rt > need) {
                need = r - rt;
                sy = dy / r * need;
                sz = dz / r * need;
            }
        }
        if (need <= 0) return;
        hipY += sy * k;
        hipZ += sz * k;
        keepLeg(soleL, hipXL);
        keepLeg(soleR, hipXR);
        placeTorso();
    }

    /** Pushes the hips straight away from a sole that has come closer than the shortest leg box. */
    private void keepLeg(float[] sole, float hipX) {
        float min = 12f * LEG_SCALE_MIN;
        float dx = sole[0] - hipX, dy = hipY - sole[1], dz = hipZ - sole[2];
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= min) return;
        float ryz = (float) Math.sqrt(dy * dy + dz * dz);
        float want = (float) Math.sqrt(Math.max(0, min * min - dx * dx));
        if (ryz < 1e-4f) {
            hipY = sole[1] - want;
            return;
        }
        hipY = sole[1] + dy / ryz * want;
        hipZ = sole[2] + dz / ryz * want;
    }

    /** The grab point on ski {@code ski} in model px. */
    private void grabAt(Input in, Trick trick, int ski, float[] out) {
        float[] foot = ski > 0 ? footL : footR;
        SkiStance.grabPoint(trick, ski, in.stance.length, grab);
        SkiStance.skiPoint(foot, grab[0], grab[1], grab[2], out);
        toRider(in, out[0], out[1], out[2], out);
    }

    /** A hand target relative to its shoulder: out (m, away from the body), down (m), forward (m). */
    private void rel(int s, float out, float down, float fwd, float[] o) {
        float[] sh = s > 0 ? shoulderL : shoulderR;
        o[0] = sh[0] + s * out * PX;
        o[1] = sh[1] + down * PX;
        o[2] = sh[2] - fwd * PX;
    }

    /** Shoulder pivot: the arm joints (+-5, 2, 0) on the pitched and twisted torso. */
    private void shoulder(int s, float[] o) {
        float lx = s * SHOULDER_X, ly = 2f;
        float y1 = ly * (float) Math.cos(theta), z1 = ly * (float) Math.sin(theta);
        float cy = (float) Math.cos(bodyYaw), sy = (float) Math.sin(bodyYaw);
        o[0] = originX + lx * cy + z1 * sy;
        o[1] = bend + y1;
        o[2] = shoulderZ + (-lx * sy + z1 * cy);
    }

    /**
     * Lowest hip height (m above the stance centre) at which the leg from {@code hipX} still reaches a sole this close
     * to the hips; {@code oy, oz} = the stance centre's offset from the model's feet (px).
     */
    private static float minHipHeight(float[] sole, float hipX, float oy, float oz, float hipBack) {
        float footUp = (24f + oy - sole[1]) / PX;
        float dx = (sole[0] - hipX) / PX, dz = (sole[2] - oz) / PX - hipBack;
        float h2 = LEG_MIN * LEG_MIN - dx * dx - dz * dz;
        return h2 > 0 ? footUp + (float) Math.sqrt(h2) : footUp;
    }

    /**
     * Ski model space (m, y up, +X left, -Z forward) to the rider's model px: through the skis' roll about the frame
     * COM, to the rider's position, into the rider's own roll (riders roll less than their skis in the air).
     */
    public static void toRider(Input in, float xs, float ys, float zs, float[] out) {
        // pre-flip frame coordinates relative to the COM, before the skis' lean
        double X = -xs, Y = ys + in.skiDrop, Z = zs;
        double c = Math.cos(-in.lean), s = Math.sin(-in.lean);
        double x1 = X * c - Y * s, y1 = X * s + Y * c;
        x1 -= in.feetX;
        y1 -= in.feetY;
        double z1 = Z - in.feetZ;
        c = Math.cos(in.riderLean);
        s = Math.sin(in.riderLean);
        double x2 = x1 * c - y1 * s, y2 = x1 * s + y1 * c;
        out[0] = (float) (-x2 * PX);
        out[1] = (float) (24 - y2 * PX);
        out[2] = (float) (z1 * PX);
    }

    private static void dir(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
    }

    private static void normalize(float[] v) {
        float n = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (n < 1e-6f) {
            v[0] = 0; v[1] = 1; v[2] = 0;
            return;
        }
        v[0] /= n;
        v[1] /= n;
        v[2] /= n;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float smooth(float t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
