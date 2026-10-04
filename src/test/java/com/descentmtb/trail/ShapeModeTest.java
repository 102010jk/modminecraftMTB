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
        assertEquals(ShapeMode.BERM_BUILD, ShapeMode.BANK_LEFT_HALF.cycled(-1));
        assertEquals(ShapeMode.AUTO, ShapeMode.COPY.cycled(1));
        assertEquals(ShapeMode.RAMP_MAKE, ShapeMode.RAMP_LINK.cycled(1));
    }

    @Test
    void everyCategoryHasModes() {
        for (int category = 0; category < ShapeMode.CATEGORIES; category++) {
            assertEquals(false, ShapeMode.inCategory(category).isEmpty());
        }
    }

    @Test
    void onlyTheBlockPresetsReshapeASingleBlock() {
        assertEquals(true, ShapeMode.RAMP_HALF.reshapesBlock());
        assertEquals(true, ShapeMode.RESET.reshapesBlock());
        assertEquals(false, ShapeMode.BERM_BUILD.reshapesBlock());
        assertEquals(false, ShapeMode.COPY.reshapesBlock());
        assertEquals(false, ShapeMode.RAMP_LINK.reshapesBlock());
    }

    @Test
    void everyModeHasAnIconAndAnActionOrPreset() {
        for (ShapeMode mode : ShapeMode.values()) {
            assertEquals(true, mode.category >= 0 && mode.category < ShapeMode.CATEGORIES, mode.name());
            if (mode.kind == ShapeMode.Kind.RAMP) {
                RampTuning.Action.of(mode);   // throws for a ramp mode without an action
            }
        }
    }
}
