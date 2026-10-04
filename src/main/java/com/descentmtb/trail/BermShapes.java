package com.descentmtb.trail;

import java.util.function.DoubleBinaryOperator;

import static com.descentmtb.trail.TrailMath.Point;
import static com.descentmtb.trail.TrailMath.curve;
import static com.descentmtb.trail.TrailMath.nearest;
import static com.descentmtb.trail.TrailMath.side;
import static com.descentmtb.trail.TrailMath.turn;

/**
 * Pure (Minecraft-free) height field of the Trail Shaper's berm: the centre line is a smooth curve through the
 * entry, apex and exit point, the track is flat, and the OUTSIDE of the turn rises into a bank. The bank is a
 * circular arc that gets as steep as the chosen {@link Steepness} and then carries on straight up to the top,
 * so you can ride up into it; behind the top it falls away to the terrain. The bank grows from nothing at the entry
 * to full height and shrinks again towards the exit.
 *
 * <p>Heights are absolute (world Y); the terrain supplies the ground the berm is feathered into. The same
 * numbers build the blocks and drive the ride tests. The block grid is 1 m, so the slope the bike really meets is
 * the height difference between neighbouring grid corners: a wall of 3 m ends up as roughly a 65 to 72 degree face.
 */
public final class BermShapes {
    /** How steep the outer bank gets. */
    public enum Steepness {
        /** About 30 degrees, a gentle banked turn. */
        GENTLE(30, 5.0, 1.0),
        /** About 45 degrees. */
        MEDIUM(45, 3.5, 1.5),
        /** About 60 degrees. */
        STEEP(60, 2.0, 2.3),
        /** Almost vertical, over 3 m high, with a curved run-up. */
        WALLRIDE(82, 1.6, 3.2);

        /** Steepest slope of the bank, in degrees. */
        public final double degrees;
        /** Radius of the curved run-up (m): small is steep over a short distance. */
        public final double radius;
        /** Height of the bank above the track (m). */
        public final double top;
        private final double arcX, arcZ, straightX, backLength;

        Steepness(double degrees, double radius, double top) {
            double angle = Math.toRadians(degrees);
            this.degrees = degrees;
            this.radius = radius;
            this.top = top;
            this.arcX = radius * Math.sin(angle);
            this.arcZ = radius * (1 - Math.cos(angle));
            this.straightX = (top - arcZ) / Math.sin(angle) * Math.cos(angle);
            this.backLength = Math.max(2, 1.2 * top);
        }

        /** Language key of the name ("Gentle" ...). */
        public String key() {
            return "descentmtb.berm.steepness." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The next (or previous, with a negative step) steepness; it stops at the ends. */
        public Steepness shifted(int step) {
            return values()[Math.max(0, Math.min(values().length - 1, ordinal() + step))];
        }

        /** Height (m) of the bank at {@code x} metres beyond its foot. */
        public double bank(double x) {
            if (x <= 0) {
                return 0;
            }
            if (x <= arcX) {
                return radius - Math.sqrt(radius * radius - x * x);
            }
            if (x <= arcX + straightX) {
                return arcZ + (x - arcX) * Math.tan(Math.toRadians(degrees));
            }
            double behindTop = x - arcX - straightX - SHOULDER;
            return behindTop <= 0 ? top : top * (1 - PumpMath.smooth(behindTop / backLength));
        }

        /** Distance (m) from the foot of the bank to where it has fallen back to the ground. */
        public double reach() {
            return arcX + straightX + SHOULDER + backLength;
        }
    }

    /** Width of the flat track (m) and the steepness of the bank. */
    public record Params(double width, Steepness steepness) {}

    /** The three points are not a usable berm; {@link #key} is a language key. */
    public static final class Rejected extends IllegalArgumentException {
        private final Object[] args;

        Rejected(String key, Object... args) {
            super(key);
            this.args = args;
        }

        /** Language key of the reason. */
        public String key() {
            return getMessage();
        }

        public Object[] args() {
            return args;
        }
    }

    /** Flat top of the bank before it falls away (m). */
    private static final double SHOULDER = .5;
    /** Fade (m) from the berm into the surrounding terrain. */
    private static final double MARGIN = 2;
    /** The shortest curve (m) that is still a berm. */
    private static final double MIN_LENGTH = 6;
    /** Below this turning angle (sine) the points count as a straight line. */
    private static final double MIN_TURN = .2;

    private final Point start, control, end;
    private final double outwardSign, half, foot, length, taper, outerEnd;
    private final double[] distances;
    private final Steepness steepness;

    private BermShapes(Point a, Point control, Point c, double turnSign, Params params, double[] distances) {
        this.start = a;
        this.control = control;
        this.end = c;
        this.outwardSign = -turnSign;
        this.half = params.width() / 2;
        this.foot = half / 2;
        this.steepness = params.steepness();
        this.distances = distances;
        this.length = distances[128];
        this.taper = Math.min(length / 3, 5);
        this.outerEnd = foot + steepness.reach();
    }

    /**
     * @param a      entry, {@code b} apex (the curve passes through it), {@code c} exit
     * @param maxLen longest curve (m) accepted
     * @throws Rejected when the points are in a line, too close together or too far apart
     */
    public static BermShapes of(Point a, Point b, Point c, Params params, double maxLen) {
        double ab = Math.hypot(b.x() - a.x(), b.z() - a.z());
        double bc = Math.hypot(c.x() - b.x(), c.z() - b.z());
        if (ab < 2 || bc < 2) {
            throw new Rejected("descentmtb.berm.too_close");
        }
        double cross = (b.x() - a.x()) * (c.z() - b.z()) - (b.z() - a.z()) * (c.x() - b.x());
        if (Math.abs(cross) / (ab * bc) < MIN_TURN) {
            throw new Rejected("descentmtb.berm.straight");
        }
        // a quadratic curve touches its control point only at the end of the pull; this control point puts the curve through b
        Point control = new Point(2 * b.x() - (a.x() + c.x()) / 2, 2 * b.y() - (a.y() + c.y()) / 2, 2 * b.z() - (a.z() + c.z()) / 2);
        double[] distances = PumpShapes.distances(a, control, c);
        if (distances[128] < MIN_LENGTH) {
            throw new Rejected("descentmtb.berm.too_close");
        }
        if (distances[128] > maxLen) {
            throw new Rejected("descentmtb.berm.too_long", (int) maxLen);
        }
        return new BermShapes(a, control, c, turn(a, b, c), params, distances);
    }

    /** Length of the centre line (m). */
    public double length() {
        return length;
    }

    /** Point of the centre line at {@code t} in 0..1 (absolute height). */
    public Point centre(double t) {
        return curve(start, control, end, t);
    }

    /** Distance (m) outward from the centre line: negative on the inside of the turn. */
    private double outward(double t, double x, double z) {
        return side(start, control, end, t, x, z) * outwardSign;
    }

    /** The bank height (m above the track) at a point, with the entry and exit taper applied. */
    public double bankAt(double x, double z) {
        double t = nearest(start, control, end, x, z);
        return taperAt(t) * steepness.bank(outward(t, x, z) - foot);
    }

    private double taperAt(double t) {
        int i = Math.min(127, (int) (t * 128));
        double d = distances[i] + (distances[i + 1] - distances[i]) * (t * 128 - i);
        return PumpMath.smooth(d / taper) * PumpMath.smooth((length - d) / taper);
    }

    /** Absolute height of the berm at (x, z), feathered into {@code terrain}. */
    public DoubleBinaryOperator heights(DoubleBinaryOperator terrain) {
        return (x, z) -> {
            double t = nearest(start, control, end, x, z);
            double s = outward(t, x, z);
            double ground = terrain.applyAsDouble(x, z);
            double inner = -s - half, outer = s - outerEnd;
            double weight = 1 - PumpMath.smooth(Math.max(inner, outer) / MARGIN);
            double target = centre(t).y() + taperAt(t) * steepness.bank(s - foot);
            return ground + weight * (target - ground);
        };
    }

    /** True for the ground columns the berm changes (its footprint, a little beyond the fade). */
    public boolean contains(double x, double z) {
        double t = nearest(start, control, end, x, z);
        double s = outward(t, x, z);
        if (-s > half + MARGIN || s > outerEnd + MARGIN) {
            return false;
        }
        return !beyondTheEnds(t, x, z);
    }

    /** At the very ends the nearest point is the end point itself; points past it along the track are not part of it. */
    private boolean beyondTheEnds(double t, double x, double z) {
        if (t > 0 && t < 1) {
            return false;
        }
        boolean atStart = t == 0;
        Point from = atStart ? start : control;
        Point to = atStart ? control : end;
        Point at = atStart ? start : end;
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        double along = ((x - at.x()) * dx + (z - at.z()) * dz) / Math.max(1e-9, Math.hypot(dx, dz));
        return atStart ? along < -.01 : along > .01;
    }

    /** Block bounds {@code {minX, minZ, maxX, maxZ}} of the footprint. */
    public int[] bounds() {
        double reach = Math.max(half, outerEnd) + MARGIN + 1;
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i <= 64; i++) {
            Point p = centre(i / 64.0);
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        return new int[]{(int) Math.floor(minX - reach), (int) Math.floor(minZ - reach),
                (int) Math.ceil(maxX + reach), (int) Math.ceil(maxZ + reach)};
    }
}
