package com.descentmtb.physics;

/**
 * Turns a blocky world into a rideable surface: the wheels see a smoothed
 * height field instead of 1 m stair steps, while real drops and walls stay sharp.
 *
 * <ol>
 *   <li>Each block column reports the top of its collision shape near the query
 *       height (so slabs / stairs / snow layers count).</li>
 *   <li>Column heights are blurred with a 3×3 [1 2 1] kernel; neighbours more
 *       than {@link #STEP_LIMIT} away (ledges, walls) are excluded so they stay
 *       sharp edges you can drop off.</li>
 *   <li>The blurred heights at cell centres are bilinearly interpolated; again a
 *       corner across a ledge is replaced by the nearest corner's height.</li>
 * </ol>
 * A single 1-block step becomes a ~27° ramp spread over ~3 m; a 2+ block ledge
 * stays a ledge. Pure Java - the Minecraft adapter supplies a {@link Columns}.
 */
public final class BlockTerrain implements Terrain {
    /** Height differences above this (m) are cliffs, not bumps. */
    public static final double STEP_LIMIT = 1.25;

    /** Source of block data. Implemented over a Minecraft level in the mod. */
    public interface Columns {
        /**
         * Highest collision-shape top in column (x, z) scanning down from {@code yTop} to
         * {@code yBottom}; NaN if the column is empty there. Must start below any solid
         * block containing {@code yTop} (i.e. return the first surface you could stand on).
         */
        double top(int x, int z, double yTop, double yBottom);

        /** Surface material of the block whose top is at {@code topY} in column (x, z). */
        Surface surface(int x, int z, double topY);

        /** True if (x, y, z) is inside a block's collision shape. */
        boolean solid(double x, double y, double z);
    }

    private final Columns cols;

    public BlockTerrain(Columns cols) {
        this.cols = cols;
    }

    @Override
    public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
        double fx = x - 0.5, fz = z - 0.5;
        int i0 = (int) Math.floor(fx), j0 = (int) Math.floor(fz);
        double tx = fx - i0, tz = fz - j0;

        // reference: the column the point is actually in
        int ci = (int) Math.floor(x), cj = (int) Math.floor(z);
        double ref = cols.top(ci, cj, yTop, yBottom);
        if (Double.isNaN(ref)) return false;
        double lo = ref - 3.0, hi = ref + 2.5;

        double h00 = blurred(i0, j0, ref, lo, hi);
        double h10 = blurred(i0 + 1, j0, ref, lo, hi);
        double h01 = blurred(i0, j0 + 1, ref, lo, hi);
        double h11 = blurred(i0 + 1, j0 + 1, ref, lo, hi);

        double h = bilerp(h00, h10, h01, h11, tx, tz);
        double dhdx = (h10 - h00) * (1 - tz) + (h11 - h01) * tz;
        double dhdz = (h01 - h00) * (1 - tx) + (h11 - h10) * tx;
        // never let smoothing lift the wheel more than half a block above the real top,
        // nor sink it more than half a block into it
        h = Math.max(ref - 0.5, Math.min(ref + 0.5, h));

        V3 n = new V3(-dhdx, 1, -dhdz).normalize();
        out.set(h, n, cols.surface(ci, cj, ref));
        return true;
    }

    /** Kernel-blurred height at cell (i, j); cliffs relative to {@code ref} are clamped out. */
    private double blurred(int i, int j, double ref, double lo, double hi) {
        double centre = colTop(i, j, ref, lo, hi);
        double sum = 0, wsum = 0;
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                double w = (di == 0 ? 2 : 1) * (dj == 0 ? 2 : 1);
                double t = (di == 0 && dj == 0) ? centre : colTop(i + di, j + dj, ref, lo, hi);
                if (Math.abs(t - centre) > STEP_LIMIT) t = centre;
                sum += w * t;
                wsum += w;
            }
        }
        return sum / wsum;
    }

    /** Column top near {@code ref}; a missing column or a cliff collapses to {@code ref}. */
    private double colTop(int i, int j, double ref, double lo, double hi) {
        double t = cols.top(i, j, hi, lo);
        if (Double.isNaN(t) || Math.abs(t - ref) > STEP_LIMIT) return ref;
        return t;
    }

    private static double bilerp(double h00, double h10, double h01, double h11, double tx, double tz) {
        double a = h00 + (h10 - h00) * tx;
        double b = h01 + (h11 - h01) * tx;
        return a + (b - a) * tz;
    }

    @Override
    public boolean solidAt(double x, double y, double z) {
        return cols.solid(x, y, z);
    }
}
