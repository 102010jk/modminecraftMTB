package com.descentmtb.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrickInputTest {
    // slots 0..4 hold except 2 and 3 which spin (hardtail-like), 5 hold (heelclicker)
    private static final boolean[] SPIN = {false, false, true, true, false, false};

    private final TrickInput in = new TrickInput();
    private final boolean[] down = new boolean[6];
    private final int[] clicks = new int[6];

    private int tick(boolean air) {
        int r = in.update(air, down.clone(), clicks.clone(), SPIN);
        java.util.Arrays.fill(clicks, 0);
        return r;
    }

    TrickInputTest() {
        tick(false);                                   // the first update after a reset only learns the keys
    }

    @Test void aTapShorterThanATickStillFires() {
        clicks[5] = 1;                                 // pressed and released between two ticks
        assertEquals(5, tick(true));
        assertEquals(-1, tick(true));
        clicks[3] = 1;
        assertEquals(3, tick(true));
    }

    @Test void holdTrickStaysWhileHeldAndEarlierOneReturns() {
        down[0] = true; clicks[0] = 1;
        assertEquals(0, tick(true));
        assertEquals(0, tick(true));
        down[4] = true; clicks[4] = 1;                 // a second hold key: the newest wins
        assertEquals(4, tick(true));
        down[4] = false;
        assertEquals(0, tick(true));                   // and the first returns
        down[0] = false;
        assertEquals(-1, tick(true));
    }

    @Test void heldSpinKeyFiresOnceAndKeyRepeatDoesNotRepeatIt() {
        down[2] = true; clicks[2] = 1;
        assertEquals(2, tick(true));
        for (int i = 0; i < 10; i++) {
            clicks[2] = 1;                             // OS key repeat while held
            assertEquals(-1, tick(true));
        }
        down[2] = false;
        assertEquals(-1, tick(true));
        down[2] = true; clicks[2] = 1;
        assertEquals(2, tick(true));                   // a fresh press fires again
    }

    @Test void twoSpinsInOneTickAreBothDeliveredWithAGapBetween() {
        clicks[2] = 1;
        clicks[3] = 1;
        int first = tick(true);
        int gap = tick(true);
        int second = tick(true);
        assertEquals(2, first);
        assertEquals(-1, gap);
        assertEquals(3, second);
        // the same spin pressed twice quickly is delivered twice, never back to back
        assertEquals(-1, tick(true));                  // (the gap after the last delivery)
        clicks[2] = 2;
        assertEquals(2, tick(true));
        assertEquals(-1, tick(true));
        assertEquals(2, tick(true));
    }

    @Test void spinPressDuringHeldHoldTrickIsDeliveredOnceThenHoldReturns() {
        down[0] = true; clicks[0] = 1;
        assertEquals(0, tick(true));
        down[2] = true; clicks[2] = 1;
        assertEquals(2, tick(true));
        assertEquals(0, tick(true));                   // the hold request returns the tick after
        down[2] = false;
        assertEquals(0, tick(true));
    }

    @Test void nothingIsLatchedOnTheGround() {
        clicks[2] = 1;
        assertEquals(-1, tick(false));
        assertEquals(-1, tick(true));
        down[2] = true;                                // spin held through take-off: no fire
        tick(false);
        assertEquals(-1, tick(true));
        down[2] = false;
        tick(true);
        down[0] = true;                                // a hold key held on the ground is ready in the air
        assertEquals(0, tick(false));
        assertEquals(0, tick(true));
    }

    @Test void resetIgnoresKeysAlreadyDown() {
        down[3] = true;
        in.reset();
        assertEquals(-1, tick(true));
        assertEquals(-1, tick(true));
    }
}
