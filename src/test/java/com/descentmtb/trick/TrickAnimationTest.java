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
    private static void run(TrickAnimation a, Trick request, int ticks) {
        for (int i = 0; i < ticks; i++) a.tick(request, 1, true, .05);
    }

    @Test void spinsChainWithoutLockingInput() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.TAILWHIP, 1, true, .05);          // tap tailwhip
        run(a, Trick.NONE, 3);
        a.tick(Trick.BARSPIN, 1, true, .05);           // barspin tapped mid-whip: queued, not lost
        run(a, Trick.NONE, 1);
        assertEquals(Trick.TAILWHIP, a.trick);
        run(a, Trick.NONE, 40);                        // whip ends, barspin follows at once, ends too
        assertEquals(Trick.NONE, a.trick);
        assertEquals(java.util.List.of(Trick.TAILWHIP, Trick.BARSPIN), a.combo.completed());
        // after both: a third trick starts immediately
        a.tick(Trick.TAILWHIP, 1, true, .05);
        assertEquals(Trick.TAILWHIP, a.trick);
    }

    @Test void nextSpinStartsOnTheTickTheLastOneFinishes() {
        boolean seen = false;
        // no tick in between where nothing plays
        TrickAnimation b = new TrickAnimation();
        b.tick(Trick.BARSPIN, 1, true, .05);
        b.tick(Trick.NONE, 1, true, .05);
        b.tick(Trick.TAILWHIP, 1, true, .05);
        for (int i = 0; i < 40 && !seen; i++) {
            b.tick(Trick.NONE, 1, true, .05);
            if (b.trick == Trick.NONE) seen = true;
            if (b.combo.size() == 1) assertEquals(Trick.TAILWHIP, b.trick);
        }
        assertTrue(seen);
        assertEquals(java.util.List.of(Trick.BARSPIN, Trick.TAILWHIP), b.combo.completed());
    }

    @Test void holdTricksChainAndAHeldOneReturns() {
        TrickAnimation a = new TrickAnimation();
        run(a, Trick.NO_HANDER, 8);                    // holding No Hander
        assertEquals(1, a.amount);
        run(a, Trick.SUPERMAN, 1);                     // switch to Superman: the No Hander blends out first
        assertEquals(Trick.NO_HANDER, a.trick);
        run(a, Trick.SUPERMAN, 2);
        assertEquals(Trick.SUPERMAN, a.trick);
        run(a, Trick.SUPERMAN, 8);
        assertEquals(1, a.amount);
        assertEquals(java.util.List.of(Trick.NO_HANDER), a.combo.completed());
        // a spin tapped while Superman is held, then back to the still-held Superman
        run(a, Trick.BARSPIN, 1);
        run(a, Trick.SUPERMAN, 40);
        assertEquals(Trick.SUPERMAN, a.trick);
        assertEquals(java.util.List.of(Trick.NO_HANDER, Trick.SUPERMAN, Trick.BARSPIN), a.combo.completed());
    }

    @Test void aQuickTapOfAHoldTrickStillPlaysAndCompletes() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.HEELCLICKER, 1, true, .05);       // one tick of request only
        run(a, Trick.NONE, 2);
        assertEquals(Trick.HEELCLICKER, a.trick);
        assertTrue(a.amount > .6);
        run(a, Trick.NONE, 10);
        assertEquals(Trick.NONE, a.trick);
        assertEquals(java.util.List.of(Trick.HEELCLICKER), a.combo.completed());
    }

    @Test void unfinishedWhileMidAnimation() {
        TrickAnimation a = new TrickAnimation();
        assertFalse(a.unfinished());
        a.tick(Trick.TAILWHIP, 1, true, .05);
        assertTrue(a.unfinished());                    // spin not completed
        run(a, Trick.NONE, 30);
        assertFalse(a.unfinished());                   // completed, limbs back
        run(a, Trick.HEELCLICKER, 8);
        assertTrue(a.unfinished());                    // fully in
        run(a, Trick.NONE, 20);
        assertFalse(a.unfinished());
        a.tick(Trick.NO_HANDER, 1, true, .05);
        assertTrue(a.trick != Trick.NONE);
        run(a, Trick.NO_HANDER, 2);
        assertTrue(a.unfinished());
        run(a, Trick.NONE, 12);
        assertFalse(a.unfinished());
    }

    @Test void landingMidTrickLeavesASummaryWithTheUnfinishedTrick() {
        TrickAnimation a = new TrickAnimation();
        a.tick(Trick.BARSPIN, 1, true, .05);
        run(a, Trick.NONE, 30);                        // barspin done
        run(a, Trick.HEELCLICKER, 6);                  // heelclicker in
        assertNull(a.takeSummary());                   // none yet: still in the air
        a.tick(Trick.HEELCLICKER, 1, false, .05);      // touchdown
        ComboTracker.Summary s = a.takeSummary();
        assertNotNull(s);
        assertEquals(java.util.List.of(Trick.BARSPIN), s.tricks());
        assertEquals(Trick.HEELCLICKER, s.unfinished());
        assertTrue(s.unfinishedPeak() > .9);
        assertNull(a.takeSummary());                   // taken once
        assertEquals(Trick.NONE, a.trick);
        assertTrue(a.combo.isEmpty());
    }

    @Test void listenersHearStartsAndEnds() {
        java.util.List<String> log = new java.util.ArrayList<>();
        TrickListener l = new TrickListener() {
            public void onTrickStart(Trick t, int side) { log.add("start " + t.name() + " " + side); }
            public void onTrickEnd(Trick t, boolean completed) { log.add("end " + t.name() + " " + completed); }
        };
        TrickAnimation.addListener(l);
        try {
            TrickAnimation a = new TrickAnimation();
            a.tick(Trick.BARSPIN, -1, true, .05);
            run(a, Trick.NONE, 20);
            a.tick(Trick.HEELCLICKER, 1, true, .05);
            run(a, Trick.HEELCLICKER, 4);
            a.tick(Trick.NONE, 1, false, .05);          // landed mid heelclicker: cancelled
        } finally {
            TrickAnimation.removeListener(l);
        }
        assertEquals(java.util.List.of("start BARSPIN -1", "end BARSPIN true",
                "start HEELCLICKER 1", "end HEELCLICKER false"), log);
    }

    @Test void respawnResetKeepsNothing() {
        TrickAnimation a = new TrickAnimation();
        run(a, Trick.NO_HANDER, 6);
        a.reset();
        assertEquals(Trick.NONE, a.trick);
        assertEquals(0, a.amount);
        assertFalse(a.unfinished());
    }

    @Test void eachBikeHasItsOwnTricksAndSuspension() {
        assertEquals(Trick.HEELCLICKER, BikeType.ENDURO.trickFor(BikeType.HEEL_X, 1));
        assertEquals(Trick.HEELCLICKER, BikeType.HARDTAIL.trickFor(-BikeType.HEEL_X, 1));
        assertEquals(Trick.HEELCLICKER, BikeType.HARDTAIL.trickAt(BikeType.HEEL_SLOT));
        for (BikeType t : BikeType.values()) {
            for (int slot = 0; slot < BikeType.TRICK_SLOTS; slot++) {
                for (int side : new int[]{-1, 1}) {
                    float[] st = BikeType.stickForSlot(slot, side);
                    assertEquals(t.trickAt(slot), t.trickFor(st[0], st[1]), t + " slot " + slot);
                    assertEquals(side, st[0] < 0 ? -1 : 1);
                }
            }
        }
        assertEquals(Trick.NAC_NAC, BikeType.ENDURO.trickFor(1, 0));
        assertEquals(Trick.BARSPIN, BikeType.HARDTAIL.trickFor(1, 0));
        assertEquals(Trick.TAILWHIP, BikeType.HARDTAIL.trickFor(-1, -1));
        assertEquals(Trick.SUPERMAN_SEATGRAB, BikeType.HARDTAIL.trickFor(0, -1));
        assertTrue(BikeType.HARDTAIL.params().shockTravel < BikeType.ENDURO.params().shockTravel);
    }
}
