package com.descentmtb.map;

/**
 * The pure maths and palette behind the drawn trail map (no Minecraft types, unit-tested): colour mixing, the muted
 * hand-drawn terrain palette, hue-shifted hillshading (light from the top left: highlights drift towards gold, shadows
 * towards blue-violet, never plain black or white), ambient occlusion, depth-shaded water, contour detection and the
 * scale bar length. Colours are 0xAARRGGBB.
 */
public final class MapPalette {
    private MapPalette() {}

    /** Warm highlight and cool shadow the shading mixes towards. */
    public static final int GOLD = 0xffffe19a, VIOLET = 0xff2e2d64, OCCLUDE = 0xff3a3050;
    private static final int GRASS = 0xff7da45a, GRASS_DARK = 0xff4f7d43, DIRT = 0xff9b7650, SAND = 0xffd9c58f,
            SNOW = 0xffe9eef2, STONE_DARK = 0xff6b6870, STONE_LIGHT = 0xffa9a49b;
    private static final int WATER_SHALLOW = 0xff86cdc4, WATER_MID = 0xff4f97b4, WATER_DEEP = 0xff263f7c;
    /** Largest depth (blocks) the water shading still distinguishes. */
    public static final int WATER_DEPTH = 14;

    /** Linear blend of two colours (alpha forced opaque), {@code t} in [0,1] towards {@code b}. */
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t + .5f);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t + .5f);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t + .5f);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    /** Perceptual-ish brightness 0..255. */
    public static int luma(int rgb) {
        return (int) (0.30 * ((rgb >> 16) & 255) + 0.59 * ((rgb >> 8) & 255) + 0.11 * (rgb & 255));
    }

    private static int desaturate(int rgb, float t) {
        int l = luma(rgb);
        return mix(rgb, 0xff000000 | l << 16 | l << 8 | l, t);
    }

    /**
     * A block's map colour pulled towards the hand-drawn palette: greens become meadow/forest green, browns path
     * dirt, greys stone, pale yellows sand, whites snow; anything else is only desaturated.
     */
    public static int muted(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max / 255f, sat = max == 0 ? 0 : (max - min) / (float) max;
        float hue = 0;
        if (max != min) {
            float d = max - min;
            hue = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
            hue = (hue * 60 + 360) % 360;
        }
        int target;
        if (sat < .10f) target = v > .88f ? SNOW : mix(STONE_DARK, STONE_LIGHT, (v - .3f) / .5f);
        else if (hue >= 65 && hue <= 170) target = v < .55f ? GRASS_DARK : GRASS;
        else if (hue >= 38 && hue < 65 && v > .75f) target = SAND;
        else if (hue < 45 || hue >= 330) target = DIRT;
        else return desaturate(rgb, .4f);
        return mix(desaturate(rgb, .35f), target, .65f);
    }

    /**
     * Hillshade in [-1,1], light from the top left: positive when the surface faces the light (the north-west
     * neighbour is lower), negative when it faces away. Heights are in blocks; {@code gain} scales the relief
     * (below 1 for maps that cover many blocks per pixel). Saturates smoothly.
     */
    public static float hillshade(int h, int hN, int hW, int hNW, double gain) {
        double d = (0.5 * (h - hNW) + 0.25 * (h - hN) + 0.25 * (h - hW)) * gain;
        return (float) (d / (Math.abs(d) + 1.2));
    }

    /** Ambient occlusion in [0,1]: how much higher the four neighbours are than this pixel (a hollow). */
    public static float occlusion(int h, int hN, int hW, int hE, int hS) {
        double rise = (hN + hW + hE + hS) / 4.0 - h;
        return (float) Math.max(0, Math.min(1, rise / 4.0));
    }

    /** Lights or shades a colour: lit tones shift towards warm gold, shadows towards deep blue-violet. */
    public static int tone(int rgb, float shade) {
        return shade >= 0 ? mix(rgb, GOLD, .38f * shade) : mix(rgb, VIOLET, .5f * -shade);
    }

    /** Water colour by depth in blocks: shallow light teal, through a clear blue, to deep indigo. */
    public static int waterColor(int depth) {
        float t = Math.max(0, Math.min(1, depth / (float) WATER_DEPTH));
        return t < .45f ? mix(WATER_SHALLOW, WATER_MID, t / .45f) : mix(WATER_MID, WATER_DEEP, (t - .45f) / .55f);
    }

    /**
     * Contour line at this pixel: 0 none, 1 a normal line, 2 an index line (every 5th). A line is where the height
     * band (height / interval) differs from the west or north neighbour, so lines are one pixel wide.
     */
    public static int contour(int h, int hW, int hN, int interval) {
        int level = Math.floorDiv(h, interval), line = Integer.MIN_VALUE;
        for (int n : new int[] {hW, hN}) {
            int ln = Math.floorDiv(n, interval);
            if (ln != level) line = Math.max(line, Math.max(level, ln));
        }
        if (line == Integer.MIN_VALUE) return 0;
        return Math.floorMod(line, 5) == 0 ? 2 : 1;
    }

    private static final int[] NICE = {1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000};

    /** The scale bar length in blocks: the largest round number that fits in {@code maxPixels}. */
    public static int scaleBarBlocks(double blocksPerPixel, int maxPixels) {
        int best = NICE[0];
        for (int n : NICE) if (n / blocksPerPixel <= maxPixels) best = n;
        return best;
    }

    public static String scaleLabel(int blocks) { return blocks + " m"; }
}
