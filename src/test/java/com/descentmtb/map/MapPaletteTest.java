package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapPaletteTest {
    private static int r(int c) { return (c >> 16) & 255; }
    private static int g(int c) { return (c >> 8) & 255; }
    private static int b(int c) { return c & 255; }

    @Test void mixEndpointsAndMiddle() {
        assertEquals(0xff102030, MapPalette.mix(0xff102030, 0xffffffff, 0));
        assertEquals(0xffffffff, MapPalette.mix(0xff102030, 0xffffffff, 1));
        assertEquals(0xff808080, MapPalette.mix(0xff000000, 0xffffffff, .5f));
        assertEquals(0xffffffff, MapPalette.mix(0xff000000, 0xffffffff, 7), "t is clamped");
    }

    @Test void hillshadeIsLitWhenFacingTopLeft() {
        // the north-west neighbour is lower: the slope faces the light
        assertTrue(MapPalette.hillshade(10, 9, 9, 8, 1) > 0.3f);
        // the north-west neighbour is higher: in shadow
        assertTrue(MapPalette.hillshade(10, 11, 11, 12, 1) < -0.3f);
        assertEquals(0f, MapPalette.hillshade(10, 10, 10, 10, 1), 1e-6);
    }

    @Test void hillshadeIsBoundedMonotoneAndScalesWithGain() {
        float last = 0;
        for (int d = 1; d < 200; d++) {
            float s = MapPalette.hillshade(d, 0, 0, 0, 1);
            assertTrue(s > last && s < 1, "monotone and below 1 at " + d);
            last = s;
        }
        assertTrue(MapPalette.hillshade(10, 6, 6, 2, .25) < MapPalette.hillshade(10, 6, 6, 2, 1));
    }

    @Test void highlightsShiftWarmAndShadowsShiftCool() {
        int grass = 0xff7da45a;
        int lit = MapPalette.tone(grass, .8f), dark = MapPalette.tone(grass, -.8f);
        assertEquals(grass, MapPalette.tone(grass, 0));
        assertTrue(MapPalette.luma(lit) > MapPalette.luma(grass) && MapPalette.luma(dark) < MapPalette.luma(grass));
        assertTrue(r(lit) - b(lit) > r(grass) - b(grass), "lit side is warmer");
        assertTrue(b(dark) - r(dark) > b(grass) - r(grass), "shadow side is cooler");
        // never a plain black or white
        assertNotEquals(0xff000000, MapPalette.tone(0xff202020, -1));
        assertNotEquals(0xffffffff, MapPalette.tone(0xfff0f0f0, 1));
    }

    @Test void blockColoursBecomeMutedPalette() {
        int grass = MapPalette.muted(0x7fb238), stone = MapPalette.muted(0x707070), snow = MapPalette.muted(0xffffff),
            dirt = MapPalette.muted(0x976d4d), sand = MapPalette.muted(0xf7e9a3), plant = MapPalette.muted(0x007c00);
        assertTrue(g(grass) > r(grass) && g(grass) > b(grass), "grass stays green");
        assertTrue(Math.abs(r(stone) - b(stone)) < 24, "stone stays grey");
        assertTrue(MapPalette.luma(snow) > 200 && MapPalette.luma(snow) > MapPalette.luma(stone));
        assertTrue(r(dirt) > g(dirt) && g(dirt) > b(dirt), "dirt is brown");
        assertTrue(MapPalette.luma(sand) > MapPalette.luma(dirt));
        assertTrue(g(plant) > r(plant) && MapPalette.luma(plant) < MapPalette.luma(grass), "foliage is the darker green");
        // muting desaturates: vanilla grass is more saturated than the drawn meadow
        assertTrue(g(grass) - b(grass) < 0xb2 - 0x38);
    }

    @Test void waterGetsDeeperAndBluer() {
        int shallow = MapPalette.waterColor(1), mid = MapPalette.waterColor(6), deep = MapPalette.waterColor(40);
        assertTrue(MapPalette.luma(shallow) > MapPalette.luma(mid) && MapPalette.luma(mid) > MapPalette.luma(deep));
        assertTrue(g(shallow) > r(shallow) + 40 && b(shallow) > r(shallow), "shallows read as light teal");
        assertTrue(b(deep) > g(deep) && b(deep) > r(deep) * 2, "deep water reads as blue");
        assertEquals(deep, MapPalette.waterColor(MapPalette.WATER_DEPTH), "depth saturates");
    }

    @Test void contoursAppearWhereTheHeightBandChanges() {
        assertEquals(0, MapPalette.contour(5, 5, 6, 4), "flat band");
        assertEquals(0, MapPalette.contour(4, 7, 5, 4), "same band");
        assertEquals(1, MapPalette.contour(8, 7, 8, 4), "crossing y=8 from the west");
        assertEquals(1, MapPalette.contour(8, 8, 7, 4), "crossing y=8 from the north");
        assertEquals(1, MapPalette.contour(7, 8, 7, 4), "downhill side of the crossing is marked too");
        assertEquals(2, MapPalette.contour(20, 19, 20, 4), "every 5th line (band 5 = y 20) is an index line");
        assertEquals(2, MapPalette.contour(-20, -21, -20, 4), "negative heights band correctly");
        assertEquals(1, MapPalette.contour(-4, -5, -4, 4));
    }

    @Test void scaleBarPicksTheLargestRoundLengthThatFits() {
        assertEquals(50, MapPalette.scaleBarBlocks(1, 64));
        assertEquals(20, MapPalette.scaleBarBlocks(.5, 40));
        assertEquals(100, MapPalette.scaleBarBlocks(2.4, 60), "100 / 2.4 = 41 px fits, 200 does not");
        assertEquals(1, MapPalette.scaleBarBlocks(100, 0), "never below one block");
        for (double bpp : new double[] {.3, .8, 1.7, 4, 9})
            assertTrue(MapPalette.scaleBarBlocks(bpp, 64) / bpp <= 64 || MapPalette.scaleBarBlocks(bpp, 64) == 1);
        assertEquals("50 m", MapPalette.scaleLabel(50));
    }

    @Test void occlusionDarkensHollowsOnly() {
        assertEquals(0f, MapPalette.occlusion(10, 10, 10, 10, 10));
        assertEquals(0f, MapPalette.occlusion(10, 8, 8, 8, 8), "a hilltop is not occluded");
        assertEquals(1f, MapPalette.occlusion(0, 9, 9, 9, 9));
        assertTrue(MapPalette.occlusion(10, 12, 12, 12, 12) > 0);
    }
}
