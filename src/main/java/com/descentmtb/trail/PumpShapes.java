package com.descentmtb.trail;

import java.util.function.DoubleBinaryOperator;

import static com.descentmtb.trail.TrailMath.*;

/**
 * Pure (Minecraft-free) height fields of the pumptrack modes, so the same maths
 * that builds the blocks can be ridden headless in unit tests.
 *
 * <p>Heights are absolute (world Y). {@code terrain} supplies the existing ground the
 * track is feathered into.
 */
public final class PumpShapes {
    /** The dimensions a pumptrack needs (metres) - kept apart from {@code WandSettings} so tests stay Minecraft-free. */
    public record Params(double width, double height, double spacing, int repeats) {}

    /** The oval of a closed circuit: centre line radii and the length of its centre line. */
    public record Oval(double cx, double cz, double rx, double rz, double perimeter, double half, double margin) {
        /** Outward distance (m) from the centre line (negative = inside), measured along the local normal. */
        public double side(double x, double z) {
            double nx = (x - cx) / rx, nz = (z - cz) / rz;
            // gradient of the implicit ellipse f = nx^2 + nz^2 gives the true normal direction
            double f = Math.sqrt(nx * nx + nz * nz);
            if (f < 1e-9) return -Math.min(rx, rz);
            double gx = nx / rx, gz = nz / rz, g = Math.hypot(gx, gz);
            return (f - 1) * f / Math.max(1e-9, g) * 1.0;
        }

        /** Position along the centre line in metres (0 at -x, counter-clockwise in screen terms). */
        public double distance(double x, double z) {
            double theta = Math.atan2((z - cz) / rz, (x - cx) / rx);
            return (theta + Math.PI) / (2 * Math.PI) * perimeter;
        }

        /** 0 on the straights, 1 at the middle of each end (how tight the turn is here). */
        public double turnAmount(double x, double z) {
            double theta = Math.atan2((z - cz) / rz, (x - cx) / rx);
            // ends of the long axis are at theta = 0 and PI when rx >= rz
            double c = Math.abs(Math.cos(rx >= rz ? theta : theta - Math.PI / 2));
            return PumpMath.smooth((c - 0.45) / 0.5);
        }

        public boolean contains(double x, double z) {
            return Math.abs(side(x, z)) < half + margin;
        }
    }

    public static Oval oval(Point a, Point c, Params s) {
        double half = s.width() / 2, margin = 2.0;
        double cx = (a.x() + c.x()) / 2, cz = (a.z() + c.z()) / 2;
        double rx = Math.abs(c.x() - a.x()) / 2 - half - margin, rz = Math.abs(c.z() - a.z()) / 2 - half - margin;
        if (rx < 4 || rz < 4) {
            throw new IllegalArgumentException("Pro okruh vyber oblast alespoň " + (int) (2 * (half + margin + 4))
                    + " × " + (int) (2 * (half + margin + 4)) + " m");
        }
        double perimeter = Math.PI * (3 * (rx + rz) - Math.sqrt((3 * rx + rz) * (rx + 3 * rz)));
        return new Oval(cx, cz, rx, rz, perimeter, half, margin);
    }

    /**
     * Closed circuit: rollers along the straights that fade out into the turns, and the outside of each
     * turn banked into a berm (height grows with the turn amount, so straights stay level).
     */
    public static DoubleBinaryOperator loop(DoubleBinaryOperator terrain, Point a, Point c, Params s) {
        Oval o = oval(a, c, s);
        double spacing = s.spacing(), active = Math.min(o.perimeter(), spacing * s.repeats());
        double ax = c.x() - a.x(), az = c.z() - a.z(), len2 = Math.max(1, ax * ax + az * az);
        return (x, z) -> {
            double side = o.side(x, z), base = terrain.applyAsDouble(x, z);
            double edge = PumpMath.edge(Math.abs(side), o.half() + o.margin(), .6);
            double outward = Math.max(0, side) / o.half();
            double turn = o.turnAmount(x, z);
            double distance = o.distance(x, z);
            // rollers only on the straights; blend out toward the berms
            double wave = distance <= active
                    ? PumpMath.wave(distance, spacing, s.height()) * PumpMath.smooth((active - distance) / 2) * (1 - turn)
                    : 0;
            double bank = Math.min(1.6, outward * outward) * s.height() * 2.2 * turn;
            double along = Math.max(0, Math.min(1, ((x - a.x()) * ax + (z - a.z()) * az) / len2));
            double target = a.y() + (c.y() - a.y()) * along;
            return base + edge * (target - base + wave + bank);
        };
    }

    /** Distance-along table for a guide curve (128 segments). */
    public static double[] distances(Point a, Point b, Point c) {
        double[] d = new double[129];
        Point last = a;
        for (int i = 1; i <= 128; i++) {
            Point p = curve(a, b, c, i / 128.0);
            d[i] = d[i - 1] + Math.hypot(p.x() - last.x(), p.z() - last.z());
            last = p;
        }
        return d;
    }

    /** Straight/curved line of rollers along a guide curve, fading in and out and into the terrain. */
    public static DoubleBinaryOperator line(DoubleBinaryOperator terrain, Point a, Point b, Point c, Params s) {
        double half = s.width() / 2, margin = 2.0;
        double[] distances = distances(a, b, c);
        double length = distances[128], active = Math.min(length, s.spacing() * s.repeats());
        return (x, z) -> {
            double t = nearest(a, b, c, x, z);
            Point p = curve(a, b, c, t);
            double lateral = Math.hypot(x - p.x(), z - p.z()), base = terrain.applyAsDouble(x, z);
            int i = Math.min(127, (int) (t * 128));
            double d = distances[i] + (distances[i + 1] - distances[i]) * (t * 128 - i);
            double fade = PumpMath.smooth(d / 2) * PumpMath.smooth((active - d) / 2) * PumpMath.edge(lateral, half + margin, .5);
            return base + (p.y() - base + PumpMath.wave(d, s.spacing(), s.height())) * fade;
        };
    }

    private PumpShapes() {}
}
