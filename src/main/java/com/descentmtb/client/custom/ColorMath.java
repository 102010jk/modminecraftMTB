package com.descentmtb.client.custom;

/**
 * Colour helpers of the workshop colour picker: HSV (hue in degrees 0..360, saturation and value 0..1) to and from
 * 0xRRGGBB ints, and hex text parsing / printing. Pure Java (no Minecraft classes), unit tested.
 */
public final class ColorMath {

    /** Hue in degrees (wrapped into 0..360), s and v clamped to 0..1 -> 0xRRGGBB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float hp = h / 60f;
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = v - c;
        return (to255(r + m) << 16) | (to255(g + m) << 8) | to255(b + m);
    }

    /** 0xRRGGBB -> {hue 0..360, saturation 0..1, value 0..1}. Grey gives hue 0. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1e-6f) {
            h = 0;
        } else if (max == r) {
            h = 60f * (((g - b) / d) % 6f);
        } else if (max == g) {
            h = 60f * ((b - r) / d + 2f);
        } else {
            h = 60f * ((r - g) / d + 4f);
        }
        if (h < 0) {
            h += 360f;
        }
        return new float[]{h, max < 1e-6f ? 0f : d / max, max};
    }

    /**
     * Parses "#rgb" or "#rrggbb" (the # and a 0x prefix are optional, case does not matter, outer spaces are
     * ignored). Returns -1 when the text is not a colour, otherwise 0xRRGGBB.
     */
    public static int parseHex(String text) {
        if (text == null) {
            return -1;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            int digit = c < 128 ? Character.digit(c, 16) : -1;
            if (digit < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /** 0xRRGGBB -> "#RRGGBB". */
    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** True for characters the hex field accepts while typing. */
    public static boolean isHexChar(char c) {
        return c == '#' || (c < 128 && Character.digit(c, 16) >= 0);
    }

    /** Black or white, whichever is readable on top of {@code rgb}. */
    public static int contrastOn(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? 0x000000 : 0xFFFFFF;
    }

    private static int to255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255f)));
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
    }

    private ColorMath() {}
}
