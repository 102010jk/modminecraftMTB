package com.descentmtb.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignContentTest {
    @Test
    void blankSignIsAnEmptyTrailSign() {
        SignContent blank = SignContent.blank();
        assertEquals(SignContent.Type.TRAIL, blank.type());
        assertEquals("", blank.name());
        assertEquals(SignContent.PIXELS, blank.pixels().length);
    }

    @Test
    void textIsCleanedAndCutToItsLimit() {
        String raw = "  §cRed\tTrail  " + "x".repeat(40);
        SignContent sign = new SignContent(SignContent.Type.TRAIL, raw, null, null, null, raw, null);
        assertEquals(SignContent.NAME_MAX, sign.name().length());
        assertFalse(sign.name().contains("§"));
        assertFalse(sign.name().contains("\t"));
        assertTrue(sign.name().startsWith("cRedTrail"));
        assertEquals(SignContent.TEXT_MAX, sign.text().length());
    }

    @Test
    void missingFieldsGetDefaults() {
        SignContent sign = new SignContent(null, null, null, null, null, null, null);
        assertEquals(SignContent.Type.TRAIL, sign.type());
        assertEquals(SignContent.Difficulty.BLUE, sign.difficulty());
        assertEquals(SignContent.Arrow.NONE, sign.arrow());
        assertEquals(SignContent.Warning.CAUTION, sign.warning());
        assertEquals("", sign.text());
    }

    @Test
    void pixelsAreMaskedToThePaletteAndWrongSizesAreDropped() {
        byte[] raw = new byte[SignContent.PIXELS];
        raw[3] = (byte) 0xF7;
        assertEquals(7, SignContent.legacy(raw).pixels()[3]);
        SignContent wrongSize = SignContent.legacy(new byte[5]);
        assertEquals(SignContent.PIXELS, wrongSize.pixels().length);
        assertEquals(0, wrongSize.pixels()[0]);
    }

    @Test
    void pixelCopiesAreIndependent() {
        byte[] raw = new byte[SignContent.PIXELS];
        SignContent sign = SignContent.legacy(raw);
        raw[0] = 9;
        sign.pixels()[1] = 9;
        assertEquals(0, sign.pixels()[0]);
        assertEquals(0, sign.pixels()[1]);
    }

    @Test
    void legacySignsAreCustomArt() {
        byte[] raw = new byte[SignContent.PIXELS];
        raw[17] = 5;
        SignContent sign = SignContent.legacy(raw);
        assertEquals(SignContent.Type.CUSTOM, sign.type());
        assertEquals(5, sign.pixels()[17]);
    }

    @Test
    void enumsAreParsedByNameWithAFallback() {
        assertEquals(SignContent.Difficulty.DOUBLE_BLACK,
                SignContent.parse(SignContent.Difficulty.class, "double_black", SignContent.Difficulty.GREEN));
        assertEquals(SignContent.Difficulty.GREEN,
                SignContent.parse(SignContent.Difficulty.class, "nonsense", SignContent.Difficulty.GREEN));
        assertEquals(SignContent.Difficulty.GREEN,
                SignContent.parse(SignContent.Difficulty.class, null, SignContent.Difficulty.GREEN));
    }

    @Test
    void equalContentIsEqualAndHashesTheSame() {
        SignContent a = new SignContent(SignContent.Type.START, "Rock Garden", SignContent.Difficulty.BLACK, null, null, "", null);
        SignContent b = new SignContent(SignContent.Type.START, "Rock Garden", SignContent.Difficulty.BLACK, null, null, "", null);
        SignContent other = new SignContent(SignContent.Type.FINISH, "Rock Garden", SignContent.Difficulty.BLACK, null, null, "", null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, other);
    }

    @Test
    void iconNamesMatchTheTextureFiles() {
        assertEquals("diff_double_black", SignContent.Difficulty.DOUBLE_BLACK.icon());
        assertEquals("arrow_left", SignContent.Arrow.LEFT.icon());
        assertNull(SignContent.Arrow.NONE.icon());
        assertEquals("warn_rocks", SignContent.Warning.ROCKS.icon());
    }
}
