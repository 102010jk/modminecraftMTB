package com.descentmtb.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrailTimesTest {
    @Test
    void formatsMinutesSecondsAndHundredths() {
        assertEquals("0:00.00", TrailTimes.format(0));
        assertEquals("0:09.99", TrailTimes.format(9_999));
        assertEquals("1:23.45", TrailTimes.format(83_450));
        assertEquals("12:05.07", TrailTimes.format(725_070));
        assertEquals("0:00.00", TrailTimes.format(-50));
    }

    @Test
    void formatsDeltas() {
        assertEquals("+0.84 s", TrailTimes.formatDelta(84));
        assertEquals("-0.05 s", TrailTimes.formatDelta(-5));
        assertEquals("+12.00 s", TrailTimes.formatDelta(1200));
        assertEquals("+0.00 s", TrailTimes.formatDelta(0));
    }

    @Test
    void deltaMatchesTheDisplayedTimes() {
        // 83.459 shows as 1:23.45 and 82.610 as 1:22.61 - the difference shown is 0.84
        assertEquals(84, TrailTimes.deltaCentis(83_459, 82_610));
        assertEquals(0, TrailTimes.deltaCentis(5_000, -1));
    }

    @Test
    void theFirstRunIsAlwaysARecord() {
        TrailTimes.Result result = TrailTimes.evaluate(-1, 90_000);
        assertTrue(result.record());
        assertEquals(90_000, result.best());
        assertEquals(-1, result.previous());
    }

    @Test
    void onlyAFasterRunBeatsTheBest() {
        assertTrue(TrailTimes.evaluate(90_000, 89_999).record());
        assertFalse(TrailTimes.evaluate(90_000, 90_000).record());
        TrailTimes.Result slower = TrailTimes.evaluate(90_000, 95_000);
        assertFalse(slower.record());
        assertEquals(90_000, slower.best());
        assertEquals(500, slower.deltaCentis(95_000));
    }

    @Test
    void implausibleTimesAreRejected() {
        assertFalse(TrailTimes.isValid(0));
        assertFalse(TrailTimes.isValid(999));
        assertTrue(TrailTimes.isValid(1_000));
        assertTrue(TrailTimes.isValid(TrailTimes.MAX_MS));
        assertFalse(TrailTimes.isValid(TrailTimes.MAX_MS + 1L));
    }

    @Test
    void trailNamesCompareIgnoringCaseAndOuterSpaces() {
        assertEquals(TrailTimes.key("Rock Garden"), TrailTimes.key("  rock garden "));
        assertNotEquals(TrailTimes.key("Rock Garden"), TrailTimes.key("Rock  Garden"));
    }
}
