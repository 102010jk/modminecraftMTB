package com.descentmtb.physics;

/**
 * Tiny immutable double-precision 3D vector for the physics core.
 *
 * <p>The physics package deliberately has no Minecraft (or JOML) imports so it
 * can be unit-tested headless; this is all the vector maths it needs.
 */
public final class V3 {
    public static final V3 ZERO = new V3(0, 0, 0);
    public static final V3 Y = new V3(0, 1, 0);

    public final double x, y, z;

    public V3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 mul(double s) { return new V3(x * s, y * s, z * s); }
    /** this + o * s */
    public V3 addScaled(V3 o, double s) { return new V3(x + o.x * s, y + o.y * s, z + o.z * s); }
    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double lengthSq() { return x * x + y * y + z * z; }
    public double length() { return Math.sqrt(lengthSq()); }
    public double horizontalLength() { return Math.sqrt(x * x + z * z); }
    public V3 horizontal() { return new V3(x, 0, z); }

    public V3 normalize() {
        double l = length();
        return l < 1e-12 ? ZERO : mul(1.0 / l);
    }

    /** Removes the component along unit vector {@code n}. */
    public V3 reject(V3 n) { return addScaled(n, -dot(n)); }

    @Override
    public String toString() {
        return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
}
