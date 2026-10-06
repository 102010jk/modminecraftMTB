package com.descentmtb.client.model;

/** Planar four-bar closure; rest geometry and animation share the same pivots.
 * Dual-link frames move a rigid rear triangle. Horst frames have a separate
 * chainstay and wheel/seatstay member. This is visual kinematics, not a force solver.
 */
final class EnduroLinkage {
    record Point(float y, float z) {
        Point plus(Point p) { return new Point(y + p.y, z + p.z); }
        Point minus(Point p) { return new Point(y - p.y, z - p.z); }
        Point rotate(float angle) {
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            return new Point(y * cos - z * sin, y * sin + z * cos);
        }
        float length() { return (float) Math.hypot(y, z); }
    }
    record Pose(Point b, Point c, Point axle, Point shock, float lowerAngle, float upperAngle, float rearAngle) {}
    static final Point AXLE = new Point(-6f, 10.08f);
    final boolean dual;
    final Point a, b, c, d, fixedEye, movingEye;
    final float restShockLength;
    private final float bcLength, dcLength, branch;
    private final float[] angles = new float[321], rises = new float[321];
    private int samples;

    EnduroLinkage(boolean dual, Point a, Point b, Point c, Point d, Point fixedEye, Point movingEye) {
        this.dual = dual; this.a = a; this.b = b; this.c = c; this.d = d;
        this.fixedEye = fixedEye; this.movingEye = movingEye;
        this.restShockLength = movingEye.minus(fixedEye).length();
        this.bcLength = c.minus(b).length(); this.dcLength = c.minus(d).length();
        Point bd = d.minus(b), bc = c.minus(b);
        this.branch = Math.signum(bd.y * bc.z - bd.z * bc.y);
        float sign = solve(.001f).axle.y < AXLE.y ? 1f : -1f;
        samples = 1;
        // Build a monotonic axle-travel lookup once, stopping before a linkage toggle.
        for (int i = 1; i < angles.length; i++) {
            float angle = sign * i * .005f;
            Point movedB = b.minus(a).rotate(angle).plus(a);
            float span = d.minus(movedB).length();
            if (span >= bcLength + dcLength - .001f || span <= Math.abs(bcLength - dcLength) + .001f) break;
            float rise = AXLE.y - solve(angle).axle.y;
            if (!Float.isFinite(rise) || rise <= rises[i - 1]) break;
            angles[i] = angle; rises[i] = rise; samples++;
            if (rise >= 2.56f) break;
        }
    }

    Pose atTravel(float rise) {
        rise = Math.max(0, Math.min(rise, rises[samples - 1]));
        int lo = 0, hi = samples - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (rises[mid] < rise) lo = mid; else hi = mid;
        }
        float span = rises[hi] - rises[lo];
        float t = span > 0 ? (rise - rises[lo]) / span : 0;
        return solve(angles[lo] + t * (angles[hi] - angles[lo]));
    }

    private Pose solve(float lowerAngle) {
        Point movedB = b.minus(a).rotate(lowerAngle).plus(a);
        Point delta = d.minus(movedB);
        float span = Math.max(.0001f, delta.length());
        float along = (bcLength * bcLength - dcLength * dcLength + span * span) / (2 * span);
        float height = (float) Math.sqrt(Math.max(0, bcLength * bcLength - along * along));
        float uy = delta.y / span, uz = delta.z / span;
        Point movedC = new Point(movedB.y + uy * along - uz * height * branch,
                                 movedB.z + uz * along + uy * height * branch);
        float upperAngle = angleBetween(c.minus(d), movedC.minus(d));
        float rearAngle = angleBetween(c.minus(b), movedC.minus(movedB));
        Point axle = AXLE.minus(b).rotate(rearAngle).plus(movedB);
        Point shockPivot = dual ? a : d;
        Point shock = movingEye.minus(shockPivot).rotate(dual ? lowerAngle : upperAngle).plus(shockPivot);
        return new Pose(movedB, movedC, axle, shock, lowerAngle, upperAngle, rearAngle);
    }

    private static float angleBetween(Point rest, Point moved) {
        return (float) Math.atan2(rest.y * moved.z - rest.z * moved.y, rest.y * moved.y + rest.z * moved.z);
    }
}
