package com.descentmtb.trail;

import java.util.Arrays;
import java.util.Locale;

/**
 * Everything a trail sign shows. Pure data (no Minecraft types) so it can be unit-tested; the block entity
 * stores it as NBT and the network payload copies it field by field.
 *
 * <p>Every instance is already sanitised: strings are stripped of control characters and cut to their maximum
 * length, enums fall back to a default and the pixel array always has exactly {@value #PIXELS} entries in the
 * palette range.
 *
 * @param type       which kind of sign this is
 * @param name       trail name (TRAIL, START and FINISH signs)
 * @param difficulty difficulty marker (TRAIL and START signs)
 * @param arrow      direction arrow under the name (TRAIL signs)
 * @param warning    warning icon (WARNING signs)
 * @param text       short extra text (WARNING signs)
 * @param pixels     16 x 16 palette indices (CUSTOM signs)
 */
public record SignContent(Type type, String name, Difficulty difficulty, Arrow arrow, Warning warning,
                          String text, byte[] pixels) {
    public static final int NAME_MAX = 24;
    public static final int TEXT_MAX = 24;
    public static final int PIXELS = 256;

    /** The five kinds of sign. */
    public enum Type { TRAIL, START, FINISH, WARNING, CUSTOM }

    /** Trail difficulty, shown as a coloured piste-style marker. */
    public enum Difficulty {
        GREEN, BLUE, BLACK, DOUBLE_BLACK, PRO;

        /** Texture name under {@code textures/sign/}. */
        public String icon() {
            return "diff_" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Direction arrow shown under the trail name. */
    public enum Arrow {
        NONE, LEFT, RIGHT, UP;

        /** Texture name under {@code textures/sign/}, or null for no arrow. */
        public String icon() {
            return this == NONE ? null : "arrow_" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Warning pictogram. */
    public enum Warning {
        JUMP, DROP, GAP, ROCKS, SLOW, CAUTION;

        /** Texture name under {@code textures/sign/}. */
        public String icon() {
            return "warn_" + name().toLowerCase(Locale.ROOT);
        }
    }

    public SignContent {
        type = type == null ? Type.TRAIL : type;
        name = clean(name, NAME_MAX);
        difficulty = difficulty == null ? Difficulty.BLUE : difficulty;
        arrow = arrow == null ? Arrow.NONE : arrow;
        warning = warning == null ? Warning.CAUTION : warning;
        text = clean(text, TEXT_MAX);
        pixels = cleanPixels(pixels);
    }

    /** A blank trail sign: blue marker, no name. */
    public static SignContent blank() {
        return new SignContent(Type.TRAIL, "", Difficulty.BLUE, Arrow.NONE, Warning.CAUTION, "", null);
    }

    /** What signs saved before sign types existed turn into: just their pixel art. */
    public static SignContent legacy(byte[] pixels) {
        return new SignContent(Type.CUSTOM, "", Difficulty.BLUE, Arrow.NONE, Warning.CAUTION, "", pixels);
    }

    /** Copy of the pixel canvas; changing it does not change this sign. */
    @Override
    public byte[] pixels() {
        return pixels.clone();
    }

    /** Looks an enum constant up by name (any case); unknown or missing names give the fallback. */
    public static <E extends Enum<E>> E parse(Class<E> type, String id, E fallback) {
        if (id != null) {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(id)) {
                    return constant;
                }
            }
        }
        return fallback;
    }

    /** Removes control characters and section signs, trims, and cuts to {@code max} characters. */
    static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127 && c != '§') {
                out.append(c);
            }
        }
        String text = out.toString().strip();
        if (text.length() > max) {
            text = text.substring(0, max);
            if (Character.isHighSurrogate(text.charAt(max - 1))) {
                text = text.substring(0, max - 1);
            }
            text = text.stripTrailing();
        }
        return text;
    }

    private static byte[] cleanPixels(byte[] raw) {
        byte[] out = new byte[PIXELS];
        if (raw != null && raw.length == PIXELS) {
            for (int i = 0; i < PIXELS; i++) {
                out[i] = (byte) (raw[i] & 15);
            }
        }
        return out;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SignContent c && type == c.type && name.equals(c.name) && difficulty == c.difficulty
                && arrow == c.arrow && warning == c.warning && text.equals(c.text) && Arrays.equals(pixels, c.pixels);
    }

    @Override
    public int hashCode() {
        int hash = Arrays.hashCode(pixels);
        hash = 31 * hash + type.hashCode();
        hash = 31 * hash + name.hashCode();
        hash = 31 * hash + difficulty.hashCode();
        hash = 31 * hash + arrow.hashCode();
        hash = 31 * hash + warning.hashCode();
        return 31 * hash + text.hashCode();
    }

    @Override
    public String toString() {
        return "SignContent[" + type + ", name=" + name + ", " + difficulty + ", " + arrow + ", " + warning
                + ", text=" + text + "]";
    }
}
