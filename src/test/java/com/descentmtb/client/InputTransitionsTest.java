package com.descentmtb.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTransitionsTest {
    @Test void freshPressOnFirstAirFrameWorks() {
        var table = new InputTransitions.AirPress();
        assertFalse(table.update(false, false));
        assertTrue(table.update(true, true));
        assertTrue(table.update(true, true));
        assertFalse(table.update(false, true));
        assertFalse(table.update(true, true));
    }

    @Test void brakeHeldOverLipCannotBecomeTable() {
        var table = new InputTransitions.AirPress();
        table.update(false, false);
        assertFalse(table.update(false, true));
        assertFalse(table.update(true, true));
        assertFalse(table.update(true, false));
        assertTrue(table.update(true, true));
    }

    @Test void resumeWithSpaceHeldRequiresRelease() {
        var table = new InputTransitions.AirPress();
        table.update(true, false);
        table.update(true, true);
        table.reset();
        assertFalse(table.update(true, true));
        assertFalse(table.update(true, false));
        assertTrue(table.update(true, true));
    }

    @Test void hopHasSixTickExtensionAndResetCancelsBurst() {
        var hop = new InputTransitions.Hop();
        assertEquals(0, hop.update(false));
        assertEquals(-1, hop.update(true));
        for (int i = 0; i < 6; i++) assertEquals(1, hop.update(false));
        assertEquals(0, hop.update(false));
        hop.update(true);
        assertEquals(1, hop.update(false));
        hop.reset();
        assertEquals(0, hop.update(false));
    }

    @Test void releaseOfKeyHeldAtMountOrPauseDoesNotPop() {
        var hop = new InputTransitions.Hop();
        hop.update(true);
        assertEquals(0, hop.update(false));
        hop.update(true);
        hop.reset();
        assertEquals(0, hop.update(false));
    }
}
