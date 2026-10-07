package com.descentmtb.map;

/** Which part of the world a map texture of a given shape shows (pure, unit-tested). */
public final class MapFit {
    /** The smallest stretch of world a map shows, in blocks, and the margin around the routes. */
    private static final double MIN_EXTENT = 10, MARGIN = 1.12;

    private MapFit() {}

    /**
     * The world rectangle {@code {minX, minZ, spanX, spanZ}} (blocks) around the routes' extent with a margin, centred on
     * it and shaped like the picture: {@code aspect} is its width over its height, so one block spans the same number of
     * pixels both ways. For a square picture this is the square of the longer side. Non-finite extents (no routes)
     * give the empty-map default.
     */
    public static double[] fit(double minX, double maxX, double minZ, double maxZ, double aspect) {
        aspect = aspect > 0 && Double.isFinite(aspect) ? aspect : 1;
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minZ) || !Double.isFinite(maxZ)) {
            double spanX = 100 * Math.max(1, aspect), spanZ = 100 * Math.max(1, 1 / aspect);
            return new double[]{-spanX / 2, -spanZ / 2, spanX, spanZ};
        }
        double dx = Math.max(MIN_EXTENT, maxX - minX), dz = Math.max(MIN_EXTENT, maxZ - minZ);
        double spanX = Math.max(dx, dz * aspect) * MARGIN, spanZ = spanX / aspect;
        return new double[]{(minX + maxX - spanX) / 2, (minZ + maxZ - spanZ) / 2, spanX, spanZ};
    }
}
