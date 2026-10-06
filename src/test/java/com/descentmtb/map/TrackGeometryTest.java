package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackGeometryTest {
    private static int[] line(int n, double stepX, double stepY) {
        int[] p = new int[n * 3];
        for (int i = 0; i < n; i++) {
            p[i * 3] = TrackGeometry.pack(i * stepX);
            p[i * 3 + 1] = TrackGeometry.pack(100 + i * stepY);
            p[i * 3 + 2] = 0;
        }
        return p;
    }

    @Test
    void simplifyKeepsEndsAndDropsCollinearPoints() {
        int[] p = line(200, 0.5, -0.25);
        int[] s = TrackGeometry.simplify(p, 0.3);
        assertEquals(2, TrackGeometry.count(s));
        assertArrayEquals(new int[]{p[0], p[1], p[2]}, new int[]{s[0], s[1], s[2]});
        assertEquals(p[597], s[3]);
    }

    @Test
    void simplifyKeepsACorner() {
        int[] p = new int[11 * 3];
        for (int i = 0; i <= 10; i++) {       // out along x, then back up along z
            p[i * 3] = TrackGeometry.pack(i <= 5 ? i : 5);
            p[i * 3 + 1] = TrackGeometry.pack(10);
            p[i * 3 + 2] = TrackGeometry.pack(i <= 5 ? 0 : i - 5);
        }
        int[] s = TrackGeometry.simplify(p, 0.3);
        assertEquals(3, TrackGeometry.count(s));
        assertEquals(TrackGeometry.pack(5), s[3]);
    }

    @Test
    void simplifyKeepsABumpAboveToleranceButNotBelow() {
        int[] p = line(21, 0.5, 0);
        p[10 * 3 + 1] += TrackGeometry.pack(0.6);     // 0.6 m bump
        assertTrue(TrackGeometry.count(TrackGeometry.simplify(p, 0.3)) >= 3);
        p = line(21, 0.5, 0);
        p[10 * 3 + 1] += TrackGeometry.pack(0.2);     // 0.2 m wobble
        assertEquals(2, TrackGeometry.count(TrackGeometry.simplify(p, 0.3)));
    }

    @Test
    void simplifyToRespectsTheLimitEvenForNoisyTracks() {
        int n = 3000;
        int[] p = new int[n * 3];
        java.util.Random rnd = new java.util.Random(7);
        for (int i = 0; i < n; i++) {
            p[i * 3] = i * 5 + rnd.nextInt(30);
            p[i * 3 + 1] = 1000 + rnd.nextInt(40);
            p[i * 3 + 2] = rnd.nextInt(30);
        }
        int[] s = TrackGeometry.simplifyTo(p, 0.3, 500);
        assertTrue(TrackGeometry.count(s) <= 500);
        assertTrue(TrackGeometry.count(s) >= 2);
        assertEquals(p[0], s[0]);
        assertEquals(p[(n - 1) * 3], s[s.length - 3]);
    }

    @Test
    void decimateKeepsBothEnds() {
        int[] p = line(100, 1, 0);
        int[] s = TrackGeometry.decimate(p, 10);
        assertEquals(10, TrackGeometry.count(s));
        assertEquals(p[0], s[0]);
        assertEquals(p[99 * 3], s[27]);
    }

    @Test
    void statsOfASlope() {
        int[] p = line(101, 1, -0.2);          // 100 m horizontally, 20 m down
        TrackGeometry.Stats st = TrackGeometry.stats(p);
        assertEquals(100, st.length(), 0.01);
        assertEquals(20, st.descent(), 0.01);
        assertEquals(0, st.ascent(), 0.01);
        assertEquals(20, st.grade(), 0.01);
    }

    @Test
    void statsCountAscentAndDescentSeparately() {
        int[] p = {0, 1000, 0, 100, 900, 0, 200, 950, 0, 300, 800, 0};   // down 10, up 5, down 15
        TrackGeometry.Stats st = TrackGeometry.stats(p);
        assertEquals(30, st.length(), 0.01);
        assertEquals(25, st.descent(), 0.01);
        assertEquals(5, st.ascent(), 0.01);
    }

    @Test
    void statsOfTooShortTracksAreEmpty() {
        assertEquals(TrackGeometry.Stats.EMPTY, TrackGeometry.stats(new int[]{1, 2, 3}));
        assertEquals(TrackGeometry.Stats.EMPTY, TrackGeometry.stats(new int[0]));
    }

    @Test
    void distancesAccumulate() {
        double[] d = TrackGeometry.distances(line(5, 2, 0));
        assertArrayEquals(new double[]{0, 2, 4, 6, 8}, d, 1e-9);
    }

    @Test
    void deltasRoundTrip() {
        int[] p = {500, -20, 7000, 510, -35, 6980, 530, 12, 7001, -300000000, 5000, 300000000};
        assertArrayEquals(p, TrackGeometry.fromDeltas(TrackGeometry.toDeltas(p)));
        for (int v : new int[]{0, 1, -1, 123456, -123456, Integer.MAX_VALUE / 2, Integer.MIN_VALUE / 2}) {
            assertEquals(v, TrackGeometry.unzigzag(TrackGeometry.zigzag(v)));
        }
        assertTrue(TrackGeometry.zigzag(-1) < 4 && TrackGeometry.zigzag(1) < 4);
    }

    @Test
    void validRejectsMalformedAndOutOfWorldTracks() {
        assertTrue(TrackGeometry.valid(line(10, 1, 0)));
        assertFalse(TrackGeometry.valid(new int[]{1, 2}));
        assertFalse(TrackGeometry.valid(new int[(TrackGeometry.MAX_POINTS + 1) * 3]));
        assertFalse(TrackGeometry.valid(new int[]{0, 100_000, 0}));          // 10 km up
        assertFalse(TrackGeometry.valid(new int[]{2_000_000_000, 0, 0}));
        assertFalse(TrackGeometry.valid(null));
    }

    @Test
    void boundsCoverTheTrack() {
        double[] b = TrackGeometry.bounds(new int[]{100, 0, -50, 300, 0, 80});
        assertArrayEquals(new double[]{10, -5, 30, 8}, b, 1e-9);
    }
}
