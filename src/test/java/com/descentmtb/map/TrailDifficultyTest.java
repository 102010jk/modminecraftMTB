package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrailDifficultyTest {
    /** A straight track along +x: {@code heights[i]} at x = i * step blocks. */
    private static int[] track(double step, double... heights) {
        int[] p = new int[heights.length * 3];
        for (int i = 0; i < heights.length; i++) {
            p[i * 3] = TrackGeometry.pack(i * step);
            p[i * 3 + 1] = TrackGeometry.pack(heights[i]);
        }
        return p;
    }

    private static double[] grade(double percent, int points, double step) {
        double[] h = new double[points];
        for (int i = 0; i < points; i++) h[i] = 100 - i * step * percent / 100;
        return h;
    }

    @Test void gentleFlowIsGreen() {
        assertEquals(TrailDifficulty.GREEN, TrailDifficulty.of(track(4, grade(4, 30, 4))));
    }

    @Test void moderateDescentIsBlue() {
        assertEquals(TrailDifficulty.BLUE, TrailDifficulty.of(track(4, grade(10, 30, 4))));
    }

    @Test void steepChuteIsRed() {
        assertEquals(TrailDifficulty.RED, TrailDifficulty.of(track(4, grade(20, 20, 4))));
    }

    @Test void bigDropMakesAnEasyTrailBlack() {
        double[] h = grade(3, 30, 4);
        for (int i = 15; i < h.length; i++) h[i] -= 4;   // a 4-block drop between point 14 and 15 ...
        int[] t = track(4, h);
        t[15 * 3] = t[14 * 3] + TrackGeometry.pack(1);    // ... over one block of ground
        for (int i = 16; i < h.length; i++) t[i * 3] = t[15 * 3] + TrackGeometry.pack((i - 15) * 4);
        TrailDifficulty.Profile p = TrailDifficulty.profile(t);
        assertTrue(p.biggestDrop() >= 3.9, "drop " + p.biggestDrop());
        assertEquals(TrailDifficulty.BLACK, TrailDifficulty.of(p));
    }

    @Test void climbingIsNotDescent() {
        double[] h = grade(-20, 20, 4); // uphill all the way
        TrailDifficulty.Profile p = TrailDifficulty.profile(track(4, h));
        assertEquals(0, p.averageGrade(), 1e-9);
        assertEquals(TrailDifficulty.GREEN, TrailDifficulty.of(p));
    }

    @Test void smoothPassesThroughEveryPoint() {
        double[] pts = {0, 0, 10, 0, 10, 10, 20, 10};
        double[] s = TrailDifficulty.smooth(pts, 2, 8);
        assertEquals((3 * 8 + 1) * 2, s.length);
        for (int i = 0; i < 4; i++) {
            assertEquals(pts[i * 2], s[i * 8 * 2], 1e-9);
            assertEquals(pts[i * 2 + 1], s[i * 8 * 2 + 1], 1e-9);
        }
    }

    @Test void tinyTracksAreHarmless() {
        assertEquals(TrailDifficulty.GREEN, TrailDifficulty.of(new int[0]));
        assertArrayEquals(new double[]{1, 2}, TrailDifficulty.smooth(new double[]{1, 2}, 2, 8));
    }
}
