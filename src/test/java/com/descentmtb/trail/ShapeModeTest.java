package com.descentmtb.trail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShapeModeTest {
    @Test
    void unknownNamesFallBackToAuto() {
        assertEquals(ShapeMode.AUTO, ShapeMode.fromName("CURVE"));
        assertEquals(ShapeMode.AUTO, ShapeMode.fromName(""));
        assertEquals(ShapeMode.RAMP_HALF, ShapeMode.fromName("RAMP_HALF"));
    }

    @Test
    void cyclingStaysInsideTheCategoryAndWraps() {
        assertEquals(ShapeMode.RAMP_HALF, ShapeMode.RAMP_QUARTER.cycled(1));
        assertEquals(ShapeMode.DROP_HALF, ShapeMode.RAMP_QUARTER.cycled(-1));
        assertEquals(ShapeMode.RAMP_QUARTER, ShapeMode.DROP_HALF.cycled(1));
        assertEquals(ShapeMode.CORNER_BANK, ShapeMode.BANK_LEFT_HALF.cycled(-1));
        assertEquals(ShapeMode.AUTO, ShapeMode.RESET.cycled(1));
    }

    @Test
    void everyCategoryHasModes() {
        for (int category = 0; category < ShapeMode.CATEGORIES; category++) {
            assertEquals(false, ShapeMode.inCategory(category).isEmpty());
        }
    }
}
