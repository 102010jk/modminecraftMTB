package com.descentmtb.physics;

/**
 * Everything the physics core needs to know about the world. Implemented over
 * Minecraft blocks in the mod, and by analytic test terrains in unit tests.
 */
public interface Terrain {

    /**
     * Ground surface under the vertical line at (x, z), searched from {@code yTop}
     * down to {@code yBottom}. Fills {@code out} and returns true if found.
     */
    boolean ground(double x, double z, double yTop, double yBottom, GroundHit out);

    /** True if the point is inside solid geometry (walls, frame / head strikes). */
    boolean solidAt(double x, double y, double z);

    /** Unsmoothed collision support, including a surface crossed during a fast fall. */
    default boolean floor(double x, double z, double yTop, double yBottom, GroundHit out) {
        return ground(x, z, yTop, yBottom, out);
    }

    /** Mutable result of a ground query. */
    final class GroundHit {
        public double height;
        public V3 normal = V3.Y;
        /** Peak tyre friction coefficient of the surface (dirt = 1.0). */
        public double grip = 1.0;
        /** Rolling-resistance coefficient. */
        public double rollRes = 0.02;
        /** Surface id for sound / particles (see {@link Surface}). */
        public Surface surface = Surface.DIRT;
        public V3 velocity = V3.ZERO;

        public void set(double height, V3 normal, Surface s) {
            this.height = height;
            this.normal = normal;
            this.surface = s;
            this.grip = s.grip;
            this.rollRes = s.rollRes;
            this.velocity = V3.ZERO;
        }
    }

    /** Surface classes with their tyre properties (feel spec F4). */
    enum Surface {
        DIRT(1.00, 0.020),
        TRAIL(1.05, 0.015),
        GRASS(0.85, 0.035),
        GRAVEL(0.70, 0.030),
        ROCK(0.95, 0.015),
        WOOD(0.90, 0.012),
        SAND(0.55, 0.080),
        MUD(0.50, 0.060),
        SNOW(0.45, 0.050),
        ICE(0.15, 0.010),
        AIRBAG(0.65, 0.060);

        public final double grip, rollRes;

        Surface(double grip, double rollRes) {
            this.grip = grip;
            this.rollRes = rollRes;
        }
    }
}
