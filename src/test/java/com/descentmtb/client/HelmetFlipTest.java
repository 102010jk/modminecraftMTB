package com.descentmtb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The helmet camera through a full flip: smooth, continuous, no gimbal flip, matching vanilla where vanilla works. */
class HelmetFlipTest {
    /** Vanilla's Camera.setRotation: rotation = rotationYXZ(pi - yaw, -pitch, -roll) (JOML), as a 3x3 matrix. */
    private static double[][] vanilla(double yawDeg, double pitchDeg, double rollDeg) {
        double[][] r = mul(mul(ry(Math.PI - Math.toRadians(yawDeg)), rx(-Math.toRadians(pitchDeg))), rz(-Math.toRadians(rollDeg)));
        return r;
    }

    private static double[][] rx(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    private static double[][] ry(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    private static double[][] rz(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] m = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) m[i][j] += a[i][k] * b[k][j];
        return m;
    }

    private static double[] apply(double[][] m, double x, double y, double z) {
        return new double[]{m[0][0] * x + m[0][1] * y + m[0][2] * z, m[1][0] * x + m[1][1] * y + m[1][2] * z,
                m[2][0] * x + m[2][1] * y + m[2][2] * z};
    }

    private static double angle(double[] a, double[] b) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.acos(Math.max(-1, Math.min(1, d)));
    }

    private static void assertSameView(double[] q, double[][] m, String what) {
        for (double[] axis : new double[][]{{0, 0, -1}, {0, 1, 0}, {1, 0, 0}}) {
            double[] mine = CameraMath.rotate(q, axis[0], axis[1], axis[2]);
            double[] vanilla = apply(m, axis[0], axis[1], axis[2]);
            assertEquals(0, angle(mine, vanilla), 1e-6, what);
        }
    }

    @Test void matchesVanillaEulerAnglesAwayFromTheVertical() {
        for (double yaw : new double[]{0, 37, -120, 200})
            for (double pitch : new double[]{-80, -30, 0, 24, 60, 85})
                for (double roll : new double[]{0, 6, -9}) {
                    // MC pitch is + = down; our argument is the angle above the horizon
                    double[] q = CameraMath.orientation(Math.toRadians(yaw), -Math.toRadians(pitch), 0, 0, Math.toRadians(roll));
                    assertSameView(q, vanilla(yaw, pitch, roll), "yaw " + yaw + " pitch " + pitch + " roll " + roll);
                }
    }

    @Test void headLookMatchesVanillaOnALevelBike() {
        // looking along the horizon a head turn / nod is the same as a yaw / pitch change
        double[] q = CameraMath.orientation(Math.toRadians(30), 0, Math.toRadians(40), Math.toRadians(10), 0);
        assertSameView(q, vanilla(70, 10, 0), "head turn and nod");
    }

    @Test void helmetPitchAgreesWithTheEulerFormulaInOrdinaryRiding() {
        for (double deg : new double[]{-50, -20, 0, 8, 35, 55}) for (double g : new double[]{0, 0.4, 1}) {
            double old = -Math.toRadians(CameraMath.helmetPitch(Math.toRadians(deg), g, 24));
            assertEquals(old, CameraMath.helmetNoseUp(Math.toRadians(deg), g, 24), 1e-9, deg + " g " + g);
        }
    }

    @Test void aBackflipTurnsTheViewSmoothlyAndContinuously() {
        // the bike's continuous pitch 0 -> 2 pi in 1.4 s at 90 fps; the neck lets go (grounded 1 -> 0, tau 0.2 s)
        double dt = 1 / 90.0, grounded = 1, pitch = 0, yaw = Math.toRadians(70);
        double[] prev = null;
        double maxStep = 0, maxYawStep = 0, prevLookYaw = Double.NaN;
        boolean overTheTop = false, upsideDown = false;
        for (double t = 0; t <= 1.4 + 1e-9; t += dt) {
            grounded = CameraMath.approach(grounded, 0, dt, 0.2);
            pitch = 2 * Math.PI * (t / 1.4);
            double noseUp = CameraMath.helmetNoseUp(pitch, grounded, 24);
            double[] q = CameraMath.orientation(yaw, noseUp, 0, 0, 0);
            double[] f = CameraMath.rotate(q, 0, 0, -1), u = CameraMath.rotate(q, 0, 1, 0);
            if (f[1] > 0.99) overTheTop = true;
            if (u[1] < -0.9) upsideDown = true;
            if (prev != null) maxStep = Math.max(maxStep, angle(prev, f));
            prev = f;
            // away from straight up / down the heading of the view must not jump either
            if (Math.abs(f[1]) < 0.9) {
                double lookYaw = Math.atan2(-f[0], f[2]);
                if (!Double.isNaN(prevLookYaw)) maxYawStep = Math.max(maxYawStep, Math.abs(CameraMath.wrap(lookYaw - prevLookYaw)));
                prevLookYaw = lookYaw;
            } else prevLookYaw = Double.NaN;
        }
        assertTrue(overTheTop, "the view goes right over the top instead of freezing at 89 degrees");
        assertTrue(upsideDown, "and passes through inverted");
        // 2 pi in 1.4 s is 0.07 rad per frame; with the neck letting go it may be a bit more but never a jump
        assertTrue(maxStep < 0.1, "largest per-frame turn " + maxStep);
        assertTrue(maxYawStep < 0.1, "the heading never snaps: " + maxYawStep);
    }

    @Test void afterAFullFlipTheViewIsWhereItStarted() {
        double yaw = Math.toRadians(-40);
        double[] before = CameraMath.rotate(CameraMath.orientation(yaw, CameraMath.helmetNoseUp(0, 1, 24), 0, 0, 0.05), 0, 0, -1);
        double[] after = CameraMath.rotate(CameraMath.orientation(yaw, CameraMath.helmetNoseUp(2 * Math.PI, 1, 24), 0, 0, 0.05), 0, 0, -1);
        assertEquals(0, angle(before, after), 1e-9);
        double[] back = CameraMath.rotate(CameraMath.orientation(yaw, CameraMath.helmetNoseUp(-2 * Math.PI, 1, 24), 0, 0, 0.05), 0, 0, -1);
        assertEquals(0, angle(before, back), 1e-9, "a frontflip too");
    }

    @Test void straightUpAndStraightDownAreValidOrientations() {
        for (double up : new double[]{Math.PI / 2, -Math.PI / 2, Math.PI}) {
            double[] q = CameraMath.orientation(1.1, up, 0, 0, 0);
            double len = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
            assertEquals(1, len, 1e-9);
            double[] f = CameraMath.rotate(q, 0, 0, -1);
            assertEquals(Math.sin(up), f[1], 1e-9);
        }
    }
}
