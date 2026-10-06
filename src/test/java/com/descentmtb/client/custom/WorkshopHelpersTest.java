package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.Tube;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pure helpers of the workshop screen (no Minecraft classes involved). */
class WorkshopHelpersTest {

    // ---- colours ----

    @Test
    void primaryColoursConvertBothWays() {
        assertEquals(0xFF0000, ColorMath.hsvToRgb(0, 1, 1));
        assertEquals(0x00FF00, ColorMath.hsvToRgb(120, 1, 1));
        assertEquals(0x0000FF, ColorMath.hsvToRgb(240, 1, 1));
        assertEquals(0xFFFFFF, ColorMath.hsvToRgb(77, 0, 1));
        assertEquals(0x000000, ColorMath.hsvToRgb(77, 1, 0));
        assertEquals(ColorMath.hsvToRgb(0, 1, 1), ColorMath.hsvToRgb(360, 1, 1), "hue wraps");
        assertEquals(ColorMath.hsvToRgb(300, .5f, .5f), ColorMath.hsvToRgb(-60, .5f, .5f), "negative hue wraps");
        assertArrayEquals(new float[]{0, 1, 1}, ColorMath.rgbToHsv(0xFF0000), 1e-4f);
        assertArrayEquals(new float[]{240, 1, 1}, ColorMath.rgbToHsv(0x0000FF), 1e-4f);
        assertArrayEquals(new float[]{0, 0, 0}, ColorMath.rgbToHsv(0x000000), 1e-4f);
        assertEquals(0, ColorMath.rgbToHsv(0x808080)[1], 1e-6);
    }

    @Test
    void everyColourOfTheCatalogueSurvivesTheRoundTrip() {
        for (int rgb : new int[]{0x1F8A8F, 0xC73A2E, 0x23262B, 0xF2F0EA, 0x2F6FD0, 0xE8752A, 0x5C8A3A, 0x7B49B8,
                0xD9A93A, 0x8A8F96, 0xE85D9B, 0x3B3F2E, 0x123456, 0xFEDCBA, 0x010203}) {
            float[] hsv = ColorMath.rgbToHsv(rgb);
            assertEquals(rgb, ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2]), "round trip of " + Integer.toHexString(rgb));
        }
        // and a coarse sweep over the whole cube
        for (int r = 0; r < 256; r += 51) {
            for (int g = 0; g < 256; g += 51) {
                for (int b = 0; b < 256; b += 51) {
                    int rgb = (r << 16) | (g << 8) | b;
                    float[] hsv = ColorMath.rgbToHsv(rgb);
                    assertEquals(rgb, ColorMath.hsvToRgb(hsv[0], hsv[1], hsv[2]));
                }
            }
        }
    }

    @Test
    void outOfRangeHsvIsClampedNotBroken() {
        assertEquals(0xFFFFFF, ColorMath.hsvToRgb(10, -3, 7));
        assertEquals(0x000000, ColorMath.hsvToRgb(10, 0.4f, -1));
        assertEquals(0x000000, ColorMath.hsvToRgb(Float.NaN, Float.NaN, Float.NaN));
    }

    @Test
    void hexParsingAcceptsTheUsualForms() {
        assertEquals(0x1F8A8F, ColorMath.parseHex("#1F8A8F"));
        assertEquals(0x1F8A8F, ColorMath.parseHex("1f8a8f"));
        assertEquals(0x1F8A8F, ColorMath.parseHex("  #1f8A8f "));
        assertEquals(0x1F8A8F, ColorMath.parseHex("0x1f8a8f"));
        assertEquals(0xFF8800, ColorMath.parseHex("#f80"));
        assertEquals(0x000000, ColorMath.parseHex("#000"));
        assertEquals(0xFFFFFF, ColorMath.parseHex("FFFFFF"));
    }

    @Test
    void hexParsingRejectsGarbage() {
        assertEquals(-1, ColorMath.parseHex(null));
        assertEquals(-1, ColorMath.parseHex(""));
        assertEquals(-1, ColorMath.parseHex("#"));
        assertEquals(-1, ColorMath.parseHex("#12"));
        assertEquals(-1, ColorMath.parseHex("#1234"));
        assertEquals(-1, ColorMath.parseHex("#1234567"));
        assertEquals(-1, ColorMath.parseHex("#12345g"));
        assertEquals(-1, ColorMath.parseHex("#12٣456"), "no non-ASCII digits");
        assertEquals(-1, ColorMath.parseHex("rgb(1,2,3)"));
    }

    @Test
    void hexPrintingIsPaddedUppercaseAndMaskedTo24Bits() {
        assertEquals("#000000", ColorMath.toHex(0));
        assertEquals("#00FF7F", ColorMath.toHex(0x00FF7F));
        assertEquals("#123456", ColorMath.toHex(0xFF123456));
        assertEquals(0x0A0B0C, ColorMath.parseHex(ColorMath.toHex(0x0A0B0C)));
        assertTrue(ColorMath.isHexChar('#') && ColorMath.isHexChar('a') && ColorMath.isHexChar('F') && ColorMath.isHexChar('7'));
        assertFalse(ColorMath.isHexChar('g') || ColorMath.isHexChar(' ') || ColorMath.isHexChar('٣'));
        assertEquals(0x000000, ColorMath.contrastOn(0xFFFFFF));
        assertEquals(0xFFFFFF, ColorMath.contrastOn(0x101010));
    }

    // ---- undo / redo ----

    @Test
    void undoAndRedoWalkTheHistory() {
        EditHistory<String> h = new EditHistory<>("a", 10);
        assertFalse(h.canUndo() || h.canRedo());
        assertTrue(h.record("b"));
        assertTrue(h.record("c"));
        assertEquals("c", h.current());
        assertEquals("b", h.undo());
        assertEquals("a", h.undo());
        assertFalse(h.canUndo());
        assertEquals("a", h.undo(), "nothing left: unchanged");
        assertEquals("b", h.redo());
        assertEquals("c", h.redo());
        assertEquals("c", h.redo());
        assertFalse(h.canRedo());
    }

    @Test
    void anEditAfterUndoDropsTheRedoBranch() {
        EditHistory<Integer> h = new EditHistory<>(0, 10);
        h.record(1);
        h.record(2);
        h.undo();
        assertTrue(h.canRedo());
        h.record(9);
        assertFalse(h.canRedo());
        assertEquals(1, h.undo());
        assertEquals(0, h.undo());
    }

    @Test
    void unchangedValuesAreNotRecorded() {
        EditHistory<String> h = new EditHistory<>("x", 10);
        assertFalse(h.record("x"));
        assertFalse(h.canUndo());
        h.record("y");
        assertFalse(h.record("y"));
        assertEquals("x", h.undo());
        assertFalse(h.canUndo());
    }

    @Test
    void aGestureIsOneUndoStep() {
        EditHistory<Integer> h = new EditHistory<>(0, 10);
        for (int i = 1; i <= 20; i++) {
            h.record(i, "drag");        // a mouse drag: twenty values, one step
        }
        assertEquals(20, h.current());
        h.endGesture();
        h.record(21, "drag");           // a second drag is a second step
        assertEquals(20, h.undo());
        assertEquals(0, h.undo());
        assertFalse(h.canUndo());

        h.record(5, "a");
        h.record(6, "b");               // another gesture key starts a new step
        assertEquals(5, h.undo());
        h.record(7, "b");
        h.record(8, "b");
        assertEquals(5, h.undo(), "undo ends a gesture");
    }

    @Test
    void theHistoryHasALimitAndCanBeReset() {
        EditHistory<Integer> h = new EditHistory<>(0, 3);
        for (int i = 1; i <= 10; i++) {
            h.record(i);
        }
        int steps = 0;
        while (h.canUndo()) {
            h.undo();
            steps++;
        }
        assertEquals(3, steps);
        assertEquals(7, h.current(), "the oldest steps fell off");
        h.reset(99);
        assertEquals(99, h.current());
        assertFalse(h.canUndo() || h.canRedo());
    }

    // ---- sticker fallback ----

    @Test
    void aClickOnTheMiddleOfEachTubePicksThatTube() {
        for (boolean fs : new boolean[]{true, false}) {
            FrameShape shape = fs ? FrameShape.ENDURO_CLASSIC : FrameShape.DJ_CLASSIC;
            for (Tube tube : Tube.values()) {
                float[] mid = StickerPicker.pointOn(fs, shape, tube, 0.5f);
                StickerPicker.Pick pick = StickerPicker.nearest(fs, shape, mid[0], mid[1]);
                // tubes meet at the joints, so the middle may be closest to another tube only if it lies on it too
                assertEquals(0f, pick.distance(), 1e-5f, tube + " mid point is on the frame");
                if (pick.tube() == tube) {
                    assertEquals(0.5f, pick.t(), 1e-4f);
                }
            }
        }
    }

    @Test
    void positionsRoundTripAlongTheTube() {
        for (boolean fs : new boolean[]{true, false}) {
            FrameShape shape = fs ? FrameShape.ENDURO_LOW_SLUNG : FrameShape.DJ_CURVED;
            for (float t : new float[]{0.2f, 0.35f, 0.5f, 0.65f, 0.8f}) {
                float[] p = StickerPicker.pointOn(fs, shape, Tube.DOWN, t);
                StickerPicker.Pick pick = StickerPicker.pick(fs, shape, p[0], p[1]);
                assertNotNull(pick);
                assertEquals(Tube.DOWN, pick.tube());
                assertEquals(t, pick.t(), 1e-3f);
            }
        }
    }

    @Test
    void clicksOffTheFrameAreRejectedByPickButNotByNearest() {
        assertNull(StickerPicker.pick(true, FrameShape.ENDURO_CLASSIC, 0.0f, 2.0f), "far above the bike");
        assertNull(StickerPicker.pick(false, FrameShape.DJ_CLASSIC, -3f, 0.5f));
        StickerPicker.Pick far = StickerPicker.nearest(true, FrameShape.ENDURO_CLASSIC, 0.0f, 2.0f);
        assertNotNull(far);
        assertTrue(far.distance() > StickerPicker.MAX_DISTANCE);
        assertTrue(far.t() >= 0 && far.t() <= 1);
    }

    @Test
    void positionsAreClampedAndSegmentsAreCopies() {
        float[] a = StickerPicker.pointOn(true, FrameShape.ENDURO_CLASSIC, Tube.TOP, -5f);
        float[] b = StickerPicker.pointOn(true, FrameShape.ENDURO_CLASSIC, Tube.TOP, 0f);
        assertArrayEquals(b, a);
        float[] seg = StickerPicker.segment(true, FrameShape.ENDURO_CLASSIC, Tube.TOP);
        seg[0] = 99;
        assertNotEquals(99f, StickerPicker.segment(true, FrameShape.ENDURO_CLASSIC, Tube.TOP)[0]);
        // the two bikes have their own frames
        assertFalse(java.util.Arrays.equals(StickerPicker.segment(true, FrameShape.ENDURO_CLASSIC, Tube.CHAIN_STAY),
                StickerPicker.segment(false, FrameShape.DJ_CLASSIC, Tube.CHAIN_STAY)));
    }
}
