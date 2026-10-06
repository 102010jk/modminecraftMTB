package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackRecorderTest {
    @Test
    void samplesEveryHalfBlockOfTravel() {
        TrackRecorder r = new TrackRecorder();
        int kept = 0;
        for (int i = 0; i <= 1000; i++) {          // 10 m in 1 cm steps
            if (r.offer(i * 0.01, 70, 0)) {
                kept++;
            }
        }
        assertTrue(kept >= 20 && kept <= 22, "kept " + kept);
        assertEquals(10, r.travelled(), 0.6);
    }

    @Test
    void standingStillRecordsNothingMore() {
        TrackRecorder r = new TrackRecorder();
        assertTrue(r.offer(0, 70, 0));
        for (int i = 0; i < 100; i++) {
            assertFalse(r.offer(0.1, 70, 0.1));
        }
        assertEquals(1, r.size());
    }

    @Test
    void aStraightRideSimplifiesToTwoPoints() {
        TrackRecorder r = new TrackRecorder();
        for (int i = 0; i < 400; i++) {
            r.offer(i * 0.6, 100 - i * 0.1, 5);
        }
        int[] t = r.finish();
        assertEquals(2, TrackGeometry.count(t));
        TrackGeometry.Stats s = TrackGeometry.stats(t);
        assertEquals(239.4, s.length(), 0.5);
        assertEquals(39.9, s.descent(), 0.5);
    }

    @Test
    void tooShortRidesAreDiscarded() {
        TrackRecorder r = new TrackRecorder();
        r.offer(0, 70, 0);
        r.offer(2, 70, 0);
        assertEquals(0, r.finish().length);
        assertEquals(0, new TrackRecorder().finish().length);
    }

    @Test
    void aVeryLongWindingRideStaysWithinTheLimits() {
        TrackRecorder r = new TrackRecorder();
        for (int i = 0; i < 40_000; i++) {         // 20 km of slalom
            double a = i * 0.02;
            r.offer(i * 0.5, 200 - i * 0.002 + Math.sin(a) * 2, Math.sin(a * 0.7) * 30);
        }
        assertTrue(r.size() <= TrackRecorder.BUFFER_CAP);
        int[] t = r.finish();
        assertTrue(TrackGeometry.count(t) <= TrackGeometry.MAX_POINTS);
        assertTrue(TrackGeometry.valid(t));
        assertTrue(TrackGeometry.stats(t).length() > 19_000);
        assertEquals(TrackGeometry.pack(0), t[0]);
    }
}
