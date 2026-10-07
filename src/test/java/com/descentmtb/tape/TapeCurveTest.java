package com.descentmtb.tape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TapeCurveTest {
    private static final double[] A = {0, 1, 0}, B = {10, 1, 0};

    @Test void tapeEndsAtItsFixingPoints() {
        assertArrayEquals(A, TapeCurve.point(A, B, 0), 1e-9);
        assertArrayEquals(B, TapeCurve.point(A, B, 1), 1e-9);
    }

    @Test void middleHangsByFourPercentOfTheLength() {
        double[] mid = TapeCurve.point(A, B, .5);
        assertEquals(5, mid[0], 1e-9);
        assertEquals(1 - 0.4, mid[1], 1e-9);
    }

    @Test void sagIsSymmetricAndNeverAboveTheLine() {
        for (int i = 0; i <= TapeCurve.SEGMENTS; i++) {
            double t = i / (double) TapeCurve.SEGMENTS;
            double[] p = TapeCurve.point(A, B, t), q = TapeCurve.point(A, B, 1 - t);
            assertEquals(p[1], q[1], 1e-9);
            assertTrue(p[1] <= 1 + 1e-9);
        }
    }

    @Test void longSpanNeverHangsIntoTheGround() {
        for (double length = 1; length <= 32; length += 1) {
            double[] a = {0, TapeCurve.HEIGHT, 0}, b = {length, TapeCurve.HEIGHT, 0};
            double lowest = TapeCurve.point(a, b, .5)[1];
            assertTrue(lowest >= TapeCurve.MIN_CLEARANCE - 1e-9, length + " blocks: " + lowest);
        }
    }

    @Test void lightIsBlendedPerComponent() {
        int dark = 0, bright = 15 << 20 | 15 << 4;
        assertEquals(dark, TapeCurve.blendLight(dark, bright, 0));
        assertEquals(bright, TapeCurve.blendLight(dark, bright, 1));
        int half = TapeCurve.blendLight(15 << 20, 15 << 4, .5);
        assertEquals(8, (half >> 20) & 0xF);
        assertEquals(8, (half >> 4) & 0xF);
    }
}
