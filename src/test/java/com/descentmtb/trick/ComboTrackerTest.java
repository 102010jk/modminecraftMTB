package com.descentmtb.trick;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComboTrackerTest {
    @Test void collectsTricksInOrderAndResetsOnTakeoffAndLanding() {
        ComboTracker c = new ComboTracker();
        c.complete(Trick.TAILWHIP);                    // not in the air: ignored
        assertTrue(c.isEmpty());
        c.begin();
        c.complete(Trick.TAILWHIP);
        c.complete(Trick.NONE);
        c.complete(Trick.BARSPIN);
        c.complete(Trick.TAILWHIP);
        assertEquals(List.of(Trick.TAILWHIP, Trick.BARSPIN, Trick.TAILWHIP), c.completed());
        assertEquals(Trick.TAILWHIP, c.last());
        ComboTracker.Summary s = c.finish(Trick.HEELCLICKER, .8);
        assertEquals(3, s.size());
        assertTrue(s.hasUnfinished());
        assertEquals(Trick.HEELCLICKER, s.unfinished());
        assertFalse(c.active());
        assertTrue(c.isEmpty());
        c.begin();
        assertTrue(c.isEmpty());
        assertTrue(c.finish(null, 0).isEmpty());
    }
}
