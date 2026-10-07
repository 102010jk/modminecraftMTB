package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainShaderTest {
    private static final int N = 48;

    private static TerrainShader.Terrain flat(byte kind) {
        byte[] k = new byte[N * N];
        java.util.Arrays.fill(k, kind);
        int[] rgb = new int[N * N], h = new int[N * N], depth = new int[N * N];
        java.util.Arrays.fill(rgb, 0x7fb238);
        java.util.Arrays.fill(h, 70);
        java.util.Arrays.fill(depth, 3);
        return new TerrainShader.Terrain(N, k, rgb, h, h.clone(), depth, 1);
    }

    private static int r(int c) { return (c >> 16) & 255; }

    @Test void unsampledPixelsStayEmptyAndEverythingElseIsOpaque() {
        var t = flat(TerrainShader.LAND);
        t.kind()[24 * N + 24] = TerrainShader.NONE;
        int[] out = TerrainShader.shade(t, 3);
        assertEquals(0, out[24 * N + 24]);
        for (int i = 0; i < out.length; i++) if (i != 24 * N + 24) assertEquals(0xff, out[i] >>> 24);
    }

    @Test void unsampledNeighboursCastNoRelief() {
        var t = flat(TerrainShader.LAND);
        t.kind()[24 * N + 23] = TerrainShader.NONE; // height 70 anyway, but make the stored value absurd
        t.height()[24 * N + 23] = 0;
        int[] with = TerrainShader.shade(t, 3), without = TerrainShader.shade(flat(TerrainShader.LAND), 3);
        assertEquals(without[24 * N + 24], with[24 * N + 24]);
    }

    @Test void slopeFacingTheLightIsBrighterAndWarmerThanTheOneFacingAway() {
        var t = flat(TerrainShader.LAND);
        // a ridge along x: the west flank rises towards the east (faces the top left), the east flank falls away
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.height()[y * N + x] = 70 + (x < 24 ? x / 2 : (47 - x) / 2);
        int[] out = TerrainShader.shade(t, 3);
        int west = out[24 * N + 12], east = out[24 * N + 36];
        assertTrue(MapPalette.luma(west) > MapPalette.luma(east), "the flank facing the top left is lit");
        assertTrue(r(west) - (west & 255) > r(east) - (east & 255), "and warmer");
    }

    @Test void contourLineMarksOnlyTheBandCrossing() {
        // the same one-block step twice: 63 -> 64 crosses a 4-block band edge, 61 -> 62 does not
        int line = stepAt(63, 64), plain = stepAt(61, 62);
        assertTrue(MapPalette.luma(line) < MapPalette.luma(plain), "the crossing gets a darker contour line");
        int[] flat = TerrainShader.shade(flat(TerrainShader.LAND), 3);
        assertEquals(flat[24 * N + 20], flat[24 * N + 28], "no lines on flat ground");
    }

    /** The shaded pixel at the foot of a one-block step from {@code low} to {@code high} running along x. */
    private static int stepAt(int low, int high) {
        var t = flat(TerrainShader.LAND);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.height()[y * N + x] = x < 24 ? low : high;
        return TerrainShader.shade(t, 3)[24 * N + 24];
    }

    @Test void waterGetsDarkerWithDepthAndHasADarkShoreline() {
        var t = flat(TerrainShader.WATER);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t.depth()[y * N + x] = x / 3;
        t.kind()[24 * N + 30] = TerrainShader.LAND; // an islet
        int[] out = TerrainShader.shade(t, 3);
        assertTrue(MapPalette.luma(out[10 * N + 10]) > MapPalette.luma(out[10 * N + 40]));
        // the water pixel beside the islet is darker than its other neighbour
        assertTrue(MapPalette.luma(out[24 * N + 29]) < MapPalette.luma(out[24 * N + 27]));
    }

    @Test void treesAreDarkerGreenThanMeadow() {
        var meadow = TerrainShader.shade(flat(TerrainShader.LAND), 3);
        var forest = TerrainShader.shade(flat(TerrainShader.LEAVES), 3);
        assertTrue(MapPalette.luma(forest[24 * N + 24]) < MapPalette.luma(meadow[24 * N + 24]));
        int c = forest[24 * N + 24];
        assertTrue(((c >> 8) & 255) > r(c) && ((c >> 8) & 255) > (c & 255), "still green");
    }

    @Test void vignetteDarkensTowardsTheFrame() {
        int[] out = TerrainShader.shade(flat(TerrainShader.LAND), 3);
        assertTrue(MapPalette.luma(out[24 * N + 3]) < MapPalette.luma(out[24 * N + 24]));
    }
}
