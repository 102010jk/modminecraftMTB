package com.descentmtb.trick;

import com.descentmtb.trick.TrickScore.BailKind;
import com.descentmtb.trick.TrickScore.Grade;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrickScoreTest {
    @Test void gradesGoUpWithAirtimeTricksAndRotation() {
        assertEquals(Grade.NONE, TrickScore.evaluate(List.of(), 1.0, 0, 0).grade());
        assertEquals(Grade.NICE, TrickScore.evaluate(List.of(Trick.NO_HANDER), 0.6, 0, 0).grade());
        assertEquals(Grade.GREAT, TrickScore.evaluate(List.of(Trick.HEELCLICKER), 2.0, 0, 0).grade());
        assertEquals(Grade.SICK, TrickScore.evaluate(List.of(Trick.HEELCLICKER), 3.0, 0, 0).grade());
        TrickScore.Result combo = TrickScore.evaluate(List.of(Trick.TAILWHIP, Trick.BARSPIN), 2.2, 0, 0);
        assertEquals(Grade.HUGE, combo.grade());
        assertTrue(combo.combo());
        assertEquals(Grade.LEGENDARY, TrickScore.evaluate(List.of(Trick.TAILWHIP, Trick.BARSPIN), 2.2, 360, 0).grade());
        assertFalse(TrickScore.evaluate(List.of(Trick.TAILWHIP), 1, 0, 0).combo());
    }

    @Test void moreIsNeverWorse() {
        double one = TrickScore.score(List.of(Trick.TAILWHIP), 1.5, 0, 0);
        double two = TrickScore.score(List.of(Trick.TAILWHIP, Trick.BARSPIN), 1.5, 0, 0);
        double longer = TrickScore.score(List.of(Trick.TAILWHIP), 2.5, 0, 0);
        double flip = TrickScore.score(List.of(Trick.TAILWHIP), 1.5, 0, 1);
        assertTrue(two > one && longer > one && flip > one);
    }

    @Test void repeatingATrickIsWorthLess() {
        double a = TrickScore.score(List.of(Trick.TAILWHIP, Trick.BARSPIN), 2, 0, 0);
        double b = TrickScore.score(List.of(Trick.TAILWHIP, Trick.TAILWHIP), 2, 0, 0);
        assertTrue(a > b);
        double c = TrickScore.score(List.of(Trick.TAILWHIP, Trick.TAILWHIP, Trick.TAILWHIP), 2, 0, 0);
        assertTrue(c > b);
    }

    @Test void everyTrickHasADifficulty() {
        for (Trick t : Trick.values()) {
            if (t != Trick.NONE) assertTrue(TrickScore.difficulty(t) > 0, t.name());
        }
        assertEquals(0, TrickScore.difficulty(Trick.NONE));
    }

    @Test void bailLines() {
        TrickScore.BailLine mid = TrickScore.bailLine("landed sideways (95 deg)", Trick.HEELCLICKER, 200, 0);
        assertEquals(BailKind.MID_TRICK, mid.kind());
        assertEquals("Heelclicker", mid.detail());
        TrickScore.BailLine spin = TrickScore.bailLine("landed sideways (95 deg)", Trick.NONE, -300, 0);
        assertEquals(BailKind.UNDER_SPIN, spin.kind());
        assertEquals("360", spin.detail());
        TrickScore.BailLine flip = TrickScore.bailLine("landed with the nose 80 deg off", Trick.NONE, 0, 300);
        assertEquals(BailKind.UNDER_FLIP, flip.kind());
        assertEquals("Backflip", flip.detail());
        assertEquals(BailKind.NONE, TrickScore.bailLine("landed too hard", Trick.NONE, 0, 0).kind());
        assertEquals(BailKind.NONE, TrickScore.bailLine("landed sideways (50 deg)", Trick.NONE, 20, 0).kind());
    }
}
