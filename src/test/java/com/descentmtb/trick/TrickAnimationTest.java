package com.descentmtb.trick;

import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrickAnimationTest {
    @Test void holdBlendsInAndOutWithoutSnapping() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.NO_HANDER, 1, true, .05);
        assertTrue(a.amount > 0 && a.amount < 1);
        for (int i = 0; i < 4; i++) a.tick(Trick.NO_HANDER, 1, true, .05);
        assertEquals(1, a.amount);
        a.tick(Trick.NONE, 1, true, .05);
        assertEquals(Trick.NO_HANDER, a.trick);
        assertTrue(a.amount > 0 && a.amount < 1);
        for (int i = 0; i < 3; i++) a.tick(Trick.NONE, 1, true, .05);
        assertEquals(Trick.NONE, a.trick);
    }
    @Test void spinFinishesOnReleaseAndDoesNotRepeatWhileHeld() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.TAILWHIP, -1, true, .05);
        assertEquals(-1, a.side);
        for (int i = 0; i < 6; i++) a.tick(Trick.NONE, 1, true, .05);
        assertEquals(Trick.TAILWHIP, a.trick);
        assertTrue(a.progress > .4 && a.progress < .7);
        for (int i = 0; i < 15; i++) a.tick(Trick.TAILWHIP, 1, true, .05);
        assertEquals(Trick.NONE, a.trick);
        a.tick(Trick.NONE, 1, true, .05);
        a.tick(Trick.TAILWHIP, 1, true, .05);
        assertEquals(Trick.TAILWHIP, a.trick);
    }
    @Test void groundAndLandingResetTricks() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.BARSPIN, 1, false, .05);
        assertEquals(Trick.NONE, a.trick);
        a.tick(Trick.BARSPIN, 1, true, .05);
        assertEquals(Trick.BARSPIN, a.trick);
        a.tick(Trick.BARSPIN, 1, false, .05);
        assertEquals(0, a.amount);
        assertEquals(0, a.progress);
    }
    @Test void eachBikeHasItsOwnTricksAndSuspension() {
        assertEquals(Trick.NAC_NAC, BikeType.ENDURO.trickFor(1, 0));
        assertEquals(Trick.BARSPIN, BikeType.HARDTAIL.trickFor(1, 0));
        assertEquals(Trick.TAILWHIP, BikeType.HARDTAIL.trickFor(-1, -1));
        assertEquals(Trick.SUPERMAN_SEATGRAB, BikeType.HARDTAIL.trickFor(0, -1));
        assertTrue(BikeType.HARDTAIL.params().shockTravel < BikeType.ENDURO.params().shockTravel);
    }
}
