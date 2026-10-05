package com.descentmtb.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The small pure rules around riding packets: bail acceptance, reject counting and the idle send throttle. */
class RiderRulesTest {
    @Test
    void bailNeedsACrashAndANearbyPosition() {
        assertTrue(BailRules.accept(true, 0, 50, 1));
        assertTrue(BailRules.accept(false, 12, 50, 1), "a fast bike may crash without a BAILED frame");
        assertFalse(BailRules.accept(false, 0.2, 50, 1), "a parked bike cannot bail");
        assertFalse(BailRules.accept(true, 0, 50, 4.5), "too far from the server's bike");
        assertFalse(BailRules.accept(true, 0, 50, Double.NaN));
        assertFalse(BailRules.accept(true, 20, 10_000, 1), "the state it relies on is too old");
    }

    @Test
    void rejectLogIsRateLimitedPerPlayer() {
        RejectTracker t = new RejectTracker();
        assertTrue(t.record(0, true).log());
        assertFalse(t.record(100, true).log());
        assertFalse(t.record(4_999, true).log());
        assertTrue(t.record(5_000, true).log());
        assertFalse(t.record(5_001, false).log(), "stale packets are never logged");
    }

    @Test
    void floodIsKickedButOccasionalRejectsAreNot() {
        RejectTracker slow = new RejectTracker();
        for (int i = 0; i < 1000; i++) assertFalse(slow.record(i * 100L, true).kick(), "10 per second is no flood");

        RejectTracker flood = new RejectTracker();
        boolean kicked = false;
        for (int i = 0; i < RejectTracker.KICK_COUNT; i++) kicked = flood.record(i * 10L, true).kick();
        assertTrue(kicked);
    }

    @Test
    void movingBikeSendsEveryTick() {
        IdleSendThrottle t = new IdleSendThrottle();
        for (int i = 0; i < 30; i++) assertTrue(t.shouldSend(false, 0, 0, 0, 0));
    }

    @Test
    void parkedBikeSendsAFewTimesASecond() {
        IdleSendThrottle t = new IdleSendThrottle();
        int sent = 0;
        for (int i = 0; i < 40; i++) if (t.shouldSend(true, 0, 0, 0, 0)) sent++;
        assertEquals(40 / IdleSendThrottle.IDLE_INTERVAL, sent);
    }

    @Test
    void parkedBikeSendsInputChangesAtOnce() {
        IdleSendThrottle t = new IdleSendThrottle();
        assertTrue(t.shouldSend(true, 0, 0, 0, 0));
        assertFalse(t.shouldSend(true, 0, 0, 0, 0));
        assertTrue(t.shouldSend(true, 0.5f, 0, 0, 0), "steering");
        assertFalse(t.shouldSend(true, 0.5f, 0, 0, 0));
        assertTrue(t.shouldSend(true, 0.5f, 0, -1, 0), "crouching");
        assertTrue(t.shouldSend(true, 0.5f, 0.3f, -1, 0), "leaning");
        assertFalse(t.shouldSend(true, 0.51f, 0.3f, -1, 0), "jitter below the threshold");
    }

    @Test
    void throttleResetSendsAtOnce() {
        IdleSendThrottle t = new IdleSendThrottle();
        t.shouldSend(true, 0, 0, 0, 0);
        assertFalse(t.shouldSend(true, 0, 0, 0, 0));
        t.reset();
        assertTrue(t.shouldSend(true, 0, 0, 0, 0));
    }
}
