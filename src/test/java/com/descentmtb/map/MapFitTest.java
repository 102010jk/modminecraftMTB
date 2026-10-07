package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapFitTest {
    @Test void squarePictureIsTheSquareOfTheLongerSideWithMargin() {
        double[] f = MapFit.fit(0, 100, 0, 40, 1);
        assertEquals(112, f[2], 1e-9);
        assertEquals(112, f[3], 1e-9);
        assertEquals(-6, f[0], 1e-9);
        assertEquals(40 / 2.0 - 56, f[1], 1e-9);
    }

    @Test void tinyRoutesStillShowAtLeastTenBlocks() {
        double[] f = MapFit.fit(5, 6, 5, 6, 1);
        assertEquals(11.2, f[2], 1e-9);
    }

    @Test void widePictureKeepsBlocksSquareAndContainsTheRoutes() {
        double[] f = MapFit.fit(0, 60, 0, 60, 3);
        assertEquals(3, f[2] / f[3], 1e-9);
        assertTrue(f[0] <= 0 && f[0] + f[2] >= 60);
        assertTrue(f[1] <= 0 && f[1] + f[3] >= 60);
    }

    @Test void longRoutesInAWidePictureAreFitByTheirWidth() {
        double[] f = MapFit.fit(0, 300, 0, 30, 2);
        assertEquals(300 * 1.12, f[2], 1e-9);
        assertEquals(300 * 1.12 / 2, f[3], 1e-9);
    }

    @Test void noRoutesGivesTheDefaultAreaCentredOnTheOrigin() {
        double[] f = MapFit.fit(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1);
        assertArrayEquals(new double[]{-50, -50, 100, 100}, f, 1e-9);
        double[] wide = MapFit.fit(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 2);
        assertEquals(2, wide[2] / wide[3], 1e-9);
        assertEquals(wide[2] / -2, wide[0], 1e-9);
    }
}
