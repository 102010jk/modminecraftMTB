package com.descentmtb.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementBudgetTest {
    /** One server tick of travel at the given speed (m/s). */
    private static double tick(double speed) {
        return speed * 0.05;
    }

    @Test
    void fastLegitimateRidingIsNeverRejected() {
        MovementBudget b = new MovementBudget();
        b.reset(0);
        for (int t = 1; t <= 20 * 60; t++) {           // a minute at 40 m/s, one packet per tick
            assertTrue(b.tryConsume(tick(40), t * 50L), "tick " + t);
        }
    }

    @Test
    void sustainedSpeedHackRunsOutOfBudget() {
        MovementBudget b = new MovementBudget();
        b.reset(0);
        int accepted = 0;
        for (int t = 1; t <= 20 * 10; t++) {           // 10 s at 100 m/s
            if (b.tryConsume(tick(100), t * 50L)) accepted++;
        }
        assertTrue(accepted < 200 * 0.7, "accepted " + accepted);
    }

    @Test
    void lagSpikeDeliveringSeveralTicksAtOnceIsAccepted() {
        MovementBudget b = new MovementBudget();
        b.reset(0);
        // nothing for a second, then 20 ticks of 40 m/s riding arrive together
        for (int i = 0; i < 20; i++) assertTrue(b.tryConsume(tick(40), 1000));
    }

    @Test
    void singleHugeJumpIsRejectedAndCostsNothing() {
        MovementBudget b = new MovementBudget();
        b.reset(0);
        assertFalse(b.tryConsume(500, 0));
        assertEquals(MovementBudget.CAPACITY, b.tokens(), 1e-9);
        assertFalse(b.tryConsume(Double.NaN, 0));
        assertFalse(b.tryConsume(Double.POSITIVE_INFINITY, 0));
        assertEquals(MovementBudget.CAPACITY, b.tokens(), 1e-9);
    }

    @Test
    void resetRefillsTheBucket() {
        MovementBudget b = new MovementBudget();
        b.reset(0);
        assertTrue(b.tryConsume(MovementBudget.CAPACITY - 1, 0));
        assertFalse(b.tryConsume(10, 0));
        b.reset(0);
        assertTrue(b.tryConsume(10, 0));
    }

    @Test
    void clockGoingBackwardsDoesNotMintTokens() {
        MovementBudget b = new MovementBudget();
        b.reset(10_000);
        assertTrue(b.tryConsume(MovementBudget.CAPACITY, 10_000));
        assertFalse(b.tryConsume(1, 5_000));
    }
}
