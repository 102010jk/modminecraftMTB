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

    /** First solid face along a segment. Adapters can provide exact collision-shape rays. */
    default boolean raycast(V3 from, V3 to, RayHit out) {
        V3 direction = to.sub(from).normalize();
        double length = to.sub(from).length();
        double distance = rayDistance(from, direction, length);
        if (Double.isNaN(distance)) return false;
        V3 point = from.addScaled(direction, distance);
        // Fit the local face from adjacent parallel rays, including continuous shaped dirt.
        V3 tangent = direction.cross(V3.Y).normalize();
        if (tangent.lengthSq() < .5) tangent = new V3(1, 0, 0);
        V3 second = adjacentRay(from, direction, length, tangent);
        V3 third = adjacentRay(from, direction, length, direction.cross(tangent).normalize());
        V3 normal = second == null || third == null ? V3.ZERO : second.sub(point).cross(third.sub(point)).normalize();
        if (normal.lengthSq() < .5) {
            double e = .00001;
            normal = new V3(
                    (solidAt(point.x - e, point.y, point.z) ? 1 : 0) - (solidAt(point.x + e, point.y, point.z) ? 1 : 0),
                    (solidAt(point.x, point.y - e, point.z) ? 1 : 0) - (solidAt(point.x, point.y + e, point.z) ? 1 : 0),
                    (solidAt(point.x, point.y, point.z - e) ? 1 : 0) - (solidAt(point.x, point.y, point.z + e) ? 1 : 0)).normalize();
            if (normal.lengthSq() < .5) normal = direction.mul(-1);
        }
        if (normal.dot(direction) > 0) normal = normal.mul(-1);
        out.set(point, normal, distance, V3.ZERO);
        return true;
    }

    private V3 adjacentRay(V3 from, V3 direction, double length, V3 offset) {
        // Keep the fit local: a wider offset can hit a different wall beyond a corner.
        for (double size = .0001; size > .0000001; size *= .5) {
            for (int sign : new int[]{1, -1}) {
                V3 start = from.addScaled(offset, sign * size);
                // A short grazing segment may end before an offset ray reaches the face.
                double distance = rayDistance(start, direction, length + .02);
                if (!Double.isNaN(distance)) return start.addScaled(direction, distance);
            }
        }
        return null;
    }

    private double rayDistance(V3 from, V3 direction, double length) {
        if (length < 1e-6 || solidAt(from.x, from.y, from.z)) return Double.NaN;
        int steps = (int) Math.ceil(length / .04);
        for (int i = 1; i <= steps; i++) {
            double hi = length * i / steps;
            V3 point = from.addScaled(direction, hi);
            if (!solidAt(point.x, point.y, point.z)) continue;
            double lo = length * (i - 1) / steps;
            for (int j = 0; j < 24; j++) {
                double mid = (lo + hi) * .5;
                point = from.addScaled(direction, mid);
                if (solidAt(point.x, point.y, point.z)) hi = mid; else lo = mid;
            }
            return (lo + hi) * .5;
        }
        return Double.NaN;
    }

    final class RayHit {
        public V3 point = V3.ZERO, normal = V3.ZERO, velocity = V3.ZERO;
        public double distance;
        public void set(V3 point, V3 normal, double distance, V3 velocity) {
            this.point = point; this.normal = normal;
            this.distance = distance; this.velocity = velocity;
        }
    }

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
        // rolling resistance tuned for game feel (hard-packed trail, fast tyres): momentum carries like Descenders
        DIRT(1.00, 0.013),
        TRAIL(1.05, 0.009),
        GRASS(0.85, 0.026),
        GRAVEL(0.70, 0.022),
        ROCK(0.95, 0.010),
        WOOD(0.90, 0.008),
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
