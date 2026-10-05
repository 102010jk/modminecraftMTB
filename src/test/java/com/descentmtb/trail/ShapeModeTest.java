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
        assertEquals(ShapeMode.JUMP_BUILD, ShapeMode.RAMP_QUARTER.cycled(-1));
        assertEquals(ShapeMode.JUMP_BUILD, ShapeMode.DROP_HALF.cycled(1));
        assertEquals(ShapeMode.RAMP_QUARTER, ShapeMode.JUMP_BUILD.cycled(1));
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
    void theDownhillLineHasItsOwnTabAndAnIcon() {
        assertEquals(ShapeMode.Kind.DOWNHILL, ShapeMode.DOWNHILL.kind);
        assertEquals(4, ShapeMode.DOWNHILL.category);
        assertEquals(false, ShapeMode.DOWNHILL.reshapesBlock());
        assertEquals("descentmtb.shape.hud.hint.downhill", ShapeMode.DOWNHILL.hintKey());
        assertEquals(ShapeMode.DOWNHILL, ShapeMode.DOWNHILL.cycled(1));
        assertEquals(true, getClass().getResource("/assets/descentmtb/textures/gui/shape/downhill.png") != null);
    }

    @Test
    void theJumpBuilderSitsInTheJumpsTab() {
        assertEquals(ShapeMode.Kind.JUMP, ShapeMode.JUMP_BUILD.kind);
        assertEquals(0, ShapeMode.JUMP_BUILD.category);
        assertEquals(false, ShapeMode.JUMP_BUILD.reshapesBlock());
        assertEquals("descentmtb.shape.hud.hint.jump_build", ShapeMode.JUMP_BUILD.hintKey());
        assertEquals("descentmtb.shape.hud.hint.auto", ShapeMode.AUTO.hintKey());
    }

    @Test
    void everyModeAndCursorSubTypeHasAnIcon() {
        for (ShapeMode mode : ShapeMode.values()) {
            assertEquals(true, getClass().getResource("/assets/descentmtb/textures/gui/shape/" + mode.fileName() + ".png") != null, mode.name());
        }
        for (CornerEdits.Pick pick : CornerEdits.Pick.values()) {
            String file = "cursor_" + pick.name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(true, getClass().getResource("/assets/descentmtb/textures/gui/shape/" + file + ".png") != null, file);
        }
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
