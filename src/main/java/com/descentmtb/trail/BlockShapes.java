package com.descentmtb.trail;

/**
 * Pure (Minecraft-free) corner heights of a single block, used by the Trail Shaper presets.
 *
 * <p>Corner order is NW(0) NE(1) SW(2) SE(3); corner {@code i} sits at {@code cx = i % 2},
 * {@code cz = i / 2} inside the block, +x is east and +z is south. Heights are local to the block's Y: 1 is
 * the top of the block, and values above 1 spill into a layer above (see {@link ColumnShaper}). Every
 * result is clamped to {@code [0, MAX_HEIGHT]}.
 */
public final class BlockShapes {
    /** Highest corner a preset may produce (blocks above the block's own Y). */
    public static final double MAX_HEIGHT = 3;
    /** Flattened heights snap to this grid. */
    private static final double GRID = 1.0 / 16;

    /**
     * A plane rising along a cardinal direction.
     *
     * @param base height of the back edge
     * @param rise how much higher the front edge is (negative for a drop)
     * @param dirX direction of the rise on the x axis (-1, 0 or 1)
     * @param dirZ direction of the rise on the z axis (-1, 0 or 1)
     */
    public static double[] slope(double base, double rise, int dirX, int dirZ) {
        double[] corners = new double[4];
        for (int i = 0; i < 4; i++) {
            double t = (i % 2 - .5) * dirX + (i / 2 - .5) * dirZ + .5;   // 0 = back edge, 1 = front edge
            corners[i] = clamp(base + rise * t);
        }
        return corners;
    }

    /** One corner is the peak ({@code base + rise}), the opposite one stays at {@code base}, the others sit halfway. */
    public static double[] cornerBank(double base, double rise, int highCorner) {
        int lowCorner = 3 - highCorner;
        double[] corners = new double[4];
        for (int i = 0; i < 4; i++) {
            double lift = i == highCorner ? rise : i == lowCorner ? 0 : rise / 2;
            corners[i] = clamp(base + lift);
        }
        return corners;
    }

    /** All four corners at their average, rounded to 1/16 of a block. */
    public static double[] flatten(double[] corners) {
        double average = (corners[0] + corners[1] + corners[2] + corners[3]) / 4;
        double level = clamp(Math.round(average / GRID) * GRID);
        return new double[]{level, level, level, level};
    }

    /** A copy of {@code corners} with {@code delta} added to the listed corners. */
    public static double[] nudge(double[] corners, int[] which, double delta) {
        double[] result = corners.clone();
        for (int i : which) {
            result[i] = result[i] + delta;
        }
        for (int i = 0; i < 4; i++) {
            result[i] = clamp(result[i]);
        }
        return result;
    }

    /** The lowest of the four corners. */
    public static double lowest(double[] corners) {
        return Math.min(Math.min(corners[0], corners[1]), Math.min(corners[2], corners[3]));
    }

    private static double clamp(double height) {
        return Math.max(0, Math.min(MAX_HEIGHT, height));
    }

    private BlockShapes() {}
}
