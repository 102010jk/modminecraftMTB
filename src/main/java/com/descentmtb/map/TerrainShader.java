package com.descentmtb.map;

/**
 * Turns sampled world columns into the cartographic terrain picture (pure, unit-tested): muted land colours,
 * hue-shifted hillshading from the top left, ambient occlusion, contour lines every 4 blocks (index line every 5th),
 * depth-shaded water with a darker shoreline, tree canopy as dark green clumps with a soft drop shadow, and a vignette
 * towards the frame.
 */
public final class TerrainShader {
    private TerrainShader() {}

    public static final byte NONE = 0, LAND = 1, LEAVES = 2, WATER = 3;

    /**
     * One sampled column per pixel (row-major, {@code cols * rows}; {@code unit} is the pixels of one item frame's
     * worth of map, which the vignette scales with): {@code kind}, the block's {@code rgb} map colour,
     * the surface {@code height}, the {@code ground} height under any canopy and the water {@code depth}. Pixels of
     * kind NONE were not loaded.
     */
    public record Terrain(int cols, int rows, int unit, byte[] kind, int[] rgb, int[] height, int[] ground, int[] depth, double blocksPerPixel) {
        /** A square picture that is one frame's worth of map. */
        public Terrain(int size, byte[] kind, int[] rgb, int[] height, int[] ground, int[] depth, double blocksPerPixel) {
            this(size, size, size, kind, rgb, height, ground, depth, blocksPerPixel);
        }
        public int size() { return Math.max(cols, rows); }
    }

    private static final int[] LEAF = {0xff5b8a45, 0xff4a7a3a, 0xff4a7a3a, 0xff35603a, 0xff35603a};
    private static final int SHORE = 0xff2c5670, CONTOUR = 0xff4a3550, VIGNETTE = 0xff3b2c3a, CANOPY_SHADOW = 0xff2a3358;

    /** Final ARGB per pixel, 0 where nothing was sampled. {@code edge} is the width of the frame the vignette sits under. */
    public static int[] shade(Terrain t, int edge) {
        int w = t.cols(), h = t.rows();
        int[] out = new int[w * h];
        double gain = 1 / Math.max(1, t.blocksPerPixel() * .5);
        int interval = t.blocksPerPixel() > 2 ? 8 : 4;
        int fade = Math.max(4, t.unit() / 16);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, kind = t.kind()[i];
            if (kind == NONE) continue;
            int c;
            if (kind == WATER) c = water(t, x, y);
            else {
                int ht = shadeHeight(t, i);
                int hN = shadeHeight(t, near(t, x, y - 1, i)), hW = shadeHeight(t, near(t, x - 1, y, i)),
                    hNW = shadeHeight(t, near(t, x - 1, y - 1, i)), hE = shadeHeight(t, near(t, x + 1, y, i)), hS = shadeHeight(t, near(t, x, y + 1, i));
                int base = kind == LEAVES ? LEAF[(hash(x >> 1, y >> 1) >>> 4) % LEAF.length] : MapPalette.muted(t.rgb()[i]);
                c = MapPalette.tone(base, MapPalette.hillshade(ht, hN, hW, hNW, gain));
                c = MapPalette.mix(c, MapPalette.OCCLUDE, .22f * MapPalette.occlusion(ht, hN, hW, hE, hS));
                if (kind != LEAVES && t.kind()[at(t, x - 1, y - 1, i)] == LEAVES) c = MapPalette.mix(c, CANOPY_SHADOW, .24f);
                int line = MapPalette.contour(contourHeight(t, i), contourHeight(t, near(t, x - 1, y, i)), contourHeight(t, near(t, x, y - 1, i)), interval);
                if (line > 0) c = MapPalette.mix(c, CONTOUR, line == 2 ? .34f : .15f);
            }
            int d = Math.min(Math.min(x, y), Math.min(w - 1 - x, h - 1 - y)) - edge;
            if (d < fade) { float f = 1 - Math.max(0, d) / (float) fade; c = MapPalette.mix(c, VIGNETTE, .42f * f * f); }
            out[i] = c;
        }
        return out;
    }

    private static int water(Terrain t, int x, int y) {
        int i = y * t.cols() + x;
        int c = MapPalette.waterColor(t.depth()[i]);
        boolean nw = land(t, x - 1, y, i) || land(t, x, y - 1, i), se = land(t, x + 1, y, i) || land(t, x, y + 1, i);
        if (nw || se) c = MapPalette.mix(c, SHORE, nw ? .42f : .26f);
        else if ((hash(x, y) & 31) == 0) c = MapPalette.mix(c, MapPalette.GOLD, .16f);
        return c;
    }

    private static boolean land(Terrain t, int x, int y, int self) {
        byte k = t.kind()[at(t, x, y, self)];
        return k == LAND || k == LEAVES;
    }

    /** Index of the neighbour, or {@code self} outside the picture. */
    private static int at(Terrain t, int x, int y, int self) {
        return x < 0 || y < 0 || x >= t.cols() || y >= t.rows() ? self : y * t.cols() + x;
    }

    /** Index of the neighbour, or {@code self} when it is outside the picture or was not sampled (so it casts no relief). */
    private static int near(Terrain t, int x, int y, int self) {
        int n = at(t, x, y, self);
        return t.kind()[n] == NONE ? self : n;
    }

    /** Canopy reads as a clump two blocks above the ground. */
    private static int shadeHeight(Terrain t, int i) { return t.kind()[i] == LEAVES ? t.ground()[i] + 2 : t.height()[i]; }

    private static int contourHeight(Terrain t, int i) { return t.kind()[i] == LEAVES ? t.ground()[i] : t.height()[i]; }

    private static int hash(int x, int y) { int h = x * 374761393 + y * 668265263; h = (h ^ (h >>> 13)) * 1274126177; return h ^ (h >>> 16); }
}
