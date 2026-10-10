package com.descentmtb.client.ski;

import com.descentmtb.trick.Trick;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Headless checks of the ski stance and the skier's body: boots, legs and hands meet where they should. */
class SkierPoseTest {
    /** The fist can reach this far from the shoulder pivot (arm box stretched / squashed, px). */
    private static final float REACH_MIN = SkierPose.ARM_SCALE_MIN * SkierPose.FIST, REACH_MAX = SkierPose.ARM_SCALE_MAX * SkierPose.FIST;
    /** The leg box spans this hip-to-sole distance (px). */
    private static final float LEG_MIN = SkierPose.LEG_SCALE_MIN * 12f, LEG_MAX = SkierPose.LEG_SCALE_MAX * 12f;

    private static SkierPose.Input input(Trick trick, float amount, boolean air) {
        SkierPose.Input in = new SkierPose.Input();
        in.stance.trick = trick;
        in.stance.amount = amount;
        in.stance.trickSide = 1;
        in.stance.airborne = air;
        in.stance.speed = 10;
        in.stance.riderUp = air ? -0.16f : 0f;
        in.stance.length = 1.80f;
        in.skiDrop = -0.52f;
        in.feetY = -0.48f;
        return in;
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void assertLimbsReach(SkierPose p, String what) {
        java.util.List<String> bad = new java.util.ArrayList<>();
        limbsReach(p, what, bad);
        assertTrue(bad.isEmpty(), String.join(" | ", bad));
    }

    private static void limbsReach(SkierPose p, String what, java.util.List<String> bad) {
        float[] hipL = {p.hipXL, p.hipY, p.hipZ}, hipR = {p.hipXR, p.hipY, p.hipZ};
        float l = dist(hipL, p.soleL), r = dist(hipR, p.soleR);
        if (!(l >= LEG_MIN - 0.3f && l <= LEG_MAX + 0.3f)) bad.add(what + ": left leg " + l);
        if (!(r >= LEG_MIN - 0.3f && r <= LEG_MAX + 0.3f)) bad.add(what + ": right leg " + r);
        float hl = dist(p.shoulderL, p.handL), hr = dist(p.shoulderR, p.handR);
        if (!(hl >= REACH_MIN - 0.3f && hl <= REACH_MAX + 0.3f)) bad.add(what + ": left arm " + hl);
        if (!(hr >= REACH_MIN - 0.3f && hr <= REACH_MAX + 0.3f)) bad.add(what + ": right arm " + hr);
    }

    @Test
    void standingStanceIsHipWideAndParallel() {
        float[] l = new float[6], r = new float[6];
        SkiStance.Input in = input(Trick.NONE, 0, false).stance;
        SkiStance.foot(in, 1, l);
        SkiStance.foot(in, -1, r);
        assertEquals(SkiStance.HALF_STANCE, l[SkiStance.X], 1e-6);
        assertEquals(-SkiStance.HALF_STANCE, r[SkiStance.X], 1e-6);
        assertEquals(0, l[SkiStance.YAW], 1e-6);
        SkierPose p = new SkierPose();
        SkierPose.Input pin = input(Trick.NONE, 0, false);
        p.solve(pin);
        // boots under the hips, soles on the player's feet line
        assertEquals(SkierPose.HIP_X, p.soleL[0], 0.1);
        assertEquals(24f, p.soleL[1], 0.2);
        assertLimbsReach(p, "stance");
        assertEquals(0, p.tuck, 1e-6);
        // hips a little behind the boots: the leg box leans back a little in the cuff
        assertTrue(p.cuffLeanL < 0 && p.cuffLeanL > -0.3f, "cuff " + p.cuffLeanL);
        assertEquals(p.cuffLeanL, p.cuffLeanR, 1e-5);
    }

    @Test
    void crouchedForwardAtSpeedTucks() {
        SkierPose p = new SkierPose();
        SkierPose.Input in = input(Trick.NONE, 0, false);
        in.stance.riderUp = -0.28f;
        in.riderFwd = 0.15f;
        in.stance.speed = 20;
        p.solve(in);
        assertTrue(p.tuck > 0.95f, "tuck " + p.tuck);
        assertTrue(p.theta > 1.2f, "back flat");
        // fists ahead of the shoulders, poles pointing back
        assertTrue(p.handL[2] < p.shoulderL[2]);
        assertTrue(p.poleL[2] > 0.9f && p.poleR[2] > 0.9f);
        assertLimbsReach(p, "tuck");
    }

    @Test
    void snowploughBringsTipsTogetherTailsApart() {
        SkiStance.Input in = input(Trick.NONE, 0, false).stance;
        in.brake = 1;
        in.speed = 2;
        float[] l = new float[6], r = new float[6], tipL = new float[3], tipR = new float[3], tailL = new float[3], tailR = new float[3];
        SkiStance.foot(in, 1, l);
        SkiStance.foot(in, -1, r);
        float tip = in.length * SkiStance.TIP_SHARE, tail = in.length - tip;
        SkiStance.skiPoint(l, tip, 0, 0, tipL);
        SkiStance.skiPoint(r, tip, 0, 0, tipR);
        SkiStance.skiPoint(l, -tail, 0, 0, tailL);
        SkiStance.skiPoint(r, -tail, 0, 0, tailR);
        float tipGap = tipL[0] - tipR[0], tailGap = tailL[0] - tailR[0];
        assertTrue(tipGap > 0.02f && tipGap < 0.15f, "tips " + tipGap);
        assertTrue(tailGap > 0.6f, "tails " + tailGap);
        // both on their inside edges
        assertTrue(l[SkiStance.ROLL] < 0 && r[SkiStance.ROLL] > 0);
    }

    @Test
    void hockeyStopTurnsBothSkisAcross() {
        SkiStance.Input in = input(Trick.NONE, 0, false).stance;
        in.brake = 1;
        in.speed = 14;
        in.steer = 0.2f;          // right
        float[] l = new float[6], r = new float[6];
        SkiStance.foot(in, 1, l);
        SkiStance.foot(in, -1, r);
        assertTrue(l[SkiStance.YAW] < -0.8f && r[SkiStance.YAW] < -0.8f);
        assertEquals(l[SkiStance.YAW], r[SkiStance.YAW], 1e-5);
    }

    @Test
    void ironCrossCrossesTheTips() {
        SkiStance.Input in = input(Trick.IRON_CROSS, 1, true).stance;
        float[] l = new float[6], r = new float[6], tipL = new float[3], tipR = new float[3];
        SkiStance.foot(in, 1, l);
        SkiStance.foot(in, -1, r);
        float tip = in.length * SkiStance.TIP_SHARE;
        SkiStance.skiPoint(l, tip, 0, 0, tipL);
        SkiStance.skiPoint(r, tip, 0, 0, tipR);
        assertTrue(tipL[0] < tipR[0], "tips crossed");
        assertTrue(l[SkiStance.X] > r[SkiStance.X], "boots not crossed");
    }

    @Test
    void everySkiTrickKeepsLimbsInReachAndGrabsTheSki() {
        Trick[] tricks = {Trick.SPREAD_EAGLE, Trick.DAFFY, Trick.IRON_CROSS, Trick.BACK_SCRATCHER, Trick.TIP_GRAB,
                Trick.MUTE_GRAB, Trick.JAPAN_GRAB, Trick.SAFETY_GRAB, Trick.TAIL_GRAB, Trick.TRUCK_DRIVER};
        float[] g = new float[3], point = new float[3];
        java.util.List<String> bad = new java.util.ArrayList<>();
        for (int side : new int[]{1, -1}) {
            for (Trick t : tricks) {
                SkierPose p = new SkierPose();
                SkierPose.Input in = input(t, 1, true);
                in.stance.trickSide = side;
                p.solve(in);
                limbsReach(p, t + " side " + side, bad);
                int ski = SkiStance.grabbedSide(t, side);
                if (ski == 0) continue;
                // the grabbing hand is on the ski's grab point
                SkiStance.grabPoint(t, ski, in.stance.length, g);
                SkiStance.skiPoint(ski > 0 ? p.footL : p.footR, g[0], g[1], g[2], point);
                SkierPose.toRider(in, point[0], point[1], point[2], point);
                float[] hand = side > 0 ? p.handL : p.handR;
                if (dist(hand, point) > 0.05f) bad.add(t + ": hand off the ski by " + dist(hand, point));
            }
        }
        assertTrue(bad.isEmpty(), String.join(" | ", bad));
    }

    @Test
    void ridersRollKeepsBootsOnTheSkis() {
        // in the air the body rolls less than the skis: the boots tilt about the stance centre, the legs follow
        SkierPose p = new SkierPose();
        SkierPose.Input in = input(Trick.NONE, 0, true);
        in.lean = 0.6f;
        in.riderLean = 0.18f;
        p.solve(in);
        assertTrue(Math.abs(p.soleL[1] - p.soleR[1]) > 1f, "boots tilted with the skis");
        assertEquals(p.originX, (p.soleL[0] + p.soleR[0]) / 2, 0.05f);
        assertLimbsReach(p, "rolled");
    }

    @Test
    void carvingBodyStandsOverTheSkis() {
        // on the ground the rider rolls with the skis, but about the player's position instead of the skis' COM:
        // the whole body moves over the boots, the legs stay straight under the hips
        SkierPose p = new SkierPose();
        SkierPose.Input in = input(Trick.NONE, 0, false);
        in.lean = in.riderLean = 0.6f;
        p.solve(in);
        assertTrue(Math.abs(p.originX) > 2f, "body moved over the skis: " + p.originX);
        assertEquals(p.hipXL, p.soleL[0], 0.1f);
        assertEquals(p.hipXR, p.soleR[0], 0.1f);
        assertEquals(p.soleL[1], p.soleR[1], 0.05f);
        assertLimbsReach(p, "carve");
    }
}
