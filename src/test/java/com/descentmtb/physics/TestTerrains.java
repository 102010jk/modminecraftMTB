package com.descentmtb.physics;

import java.util.function.DoubleBinaryOperator;

/** Synthetic worlds for the headless feel tests. */
final class TestTerrains {

    /** Smooth analytic height field; everything below it is solid. */
    static Terrain fn(DoubleBinaryOperator h, Terrain.Surface s) {
        return new Terrain() {
            @Override
            public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
                double y = h.applyAsDouble(x, z);
                if (y > yTop || y < yBottom) return false;
                double e = 0.02;
                double dx = (h.applyAsDouble(x + e, z) - h.applyAsDouble(x - e, z)) / (2 * e);
                double dz = (h.applyAsDouble(x, z + e) - h.applyAsDouble(x, z - e)) / (2 * e);
                out.set(y, new V3(-dx, 1, -dz).normalize(), s);
                return true;
            }

            @Override
            public boolean solidAt(double x, double y, double z) {
                return y < h.applyAsDouble(x, z) - 0.05;
            }
        };
    }

    static Terrain flat(double y, Terrain.Surface s) {
        return fn((x, z) -> y, s);
    }

    /** Block world from an integer column-height function (full blocks), through the real smoother. */
    static Terrain blocks(ColumnHeight heights, Terrain.Surface s) {
        return new BlockTerrain(new BlockTerrain.Columns() {
            @Override
            public double top(int x, int z, double yTop, double yBottom) {
                double t = heights.top(x, z);
                // column is solid from -inf up to t; "standable top" must be at/below yTop
                if (t > yTop) {
                    // yTop is inside the solid column: no standable surface in range
                    return Double.NaN;
                }
                return t >= yBottom ? t : Double.NaN;
            }

            @Override
            public Terrain.Surface surface(int x, int z, double topY) {
                return s;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return y < heights.top((int) Math.floor(x), (int) Math.floor(z));
            }
        });
    }

    interface ColumnHeight {
        double top(int x, int z);
    }

    private TestTerrains() {}
}
