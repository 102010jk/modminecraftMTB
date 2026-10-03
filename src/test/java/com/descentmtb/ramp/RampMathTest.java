package com.descentmtb.ramp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RampMathTest {
    private static final int[][] CASES = {{0, 8}, {0, 16}, {4, 12}, {16, 2}, {3, 3}};

    @Test
    void endpointsMatchStartAndEndForAllFacings() {
        for (int[] c : CASES) for (int p = 0; p < 3; p++) {
            // t=0 back edge, t=1 front edge, per facing: S(fz), W(1-fx), N(1-fz), E(fx)
            double[][] back = {{0.5, 0.0}, {1.0, 0.5}, {0.5, 1.0}, {0.0, 0.5}};
            double[][] front = {{0.5, 1.0}, {0.0, 0.5}, {0.5, 0.0}, {1.0, 0.5}};
            for (int f = 0; f < 4; f++) {
                assertEquals(c[0] / 16.0, RampMath.height(c[0], c[1], p, f, back[f][0], back[f][1]), 1e-9);
                assertEquals(c[1] / 16.0, RampMath.height(c[0], c[1], p, f, front[f][0], front[f][1]), 1e-9);
            }
        }
    }

    @Test
    void slopeMatchesFiniteDifferenceForAllFacingsAndProfiles() {
        double e = 1e-6;
        for (int[] c : CASES) for (int p = 0; p < 3; p++) for (int f = 0; f < 4; f++) {
            for (double fx = 0.05; fx < 0.96; fx += 0.1) for (double fz = 0.05; fz < 0.96; fz += 0.1) {
                double nx = (RampMath.height(c[0], c[1], p, f, fx + e, fz) - RampMath.height(c[0], c[1], p, f, fx - e, fz)) / (2 * e);
                double nz = (RampMath.height(c[0], c[1], p, f, fx, fz + e) - RampMath.height(c[0], c[1], p, f, fx, fz - e)) / (2 * e);
                assertEquals(nx, RampMath.slopeX(c[0], c[1], p, f, fx, fz), 1e-5, "dx f=" + f + " p=" + p);
                assertEquals(nz, RampMath.slopeZ(c[0], c[1], p, f, fx, fz), 1e-5, "dz f=" + f + " p=" + p);
            }
        }
    }

    @Test
    void risesTowardFacing() {
        // NORTH (2) rises toward -z; EAST (3) toward +x
        assertEquals(true, RampMath.height(0, 16, 0, 2, 0.5, 0.1) > RampMath.height(0, 16, 0, 2, 0.5, 0.9));
        assertEquals(-1.0, RampMath.slopeZ(0, 16, 0, 2, 0.5, 0.5), 1e-9);
        assertEquals(1.0, RampMath.slopeX(0, 16, 0, 3, 0.5, 0.5), 1e-9);
        assertEquals(0.0, RampMath.slopeX(0, 16, 0, 2, 0.5, 0.5), 1e-9);
    }

    @Test
    void concaveSteepensAndConvexFlattens() {
        double a = RampMath.profileD(RampMath.CONCAVE, 0.1), b = RampMath.profileD(RampMath.CONCAVE, 0.9);
        assertEquals(true, b > a * 3);
        double c = RampMath.profileD(RampMath.CONVEX, 0.1), d = RampMath.profileD(RampMath.CONVEX, 0.9);
        assertEquals(true, c > d * 3);
        assertEquals(1.0, RampMath.profile(RampMath.CONCAVE, 1.0), 1e-12);
        assertEquals(0.0, RampMath.profile(RampMath.CONVEX, 0.0), 1e-12);
    }
}
