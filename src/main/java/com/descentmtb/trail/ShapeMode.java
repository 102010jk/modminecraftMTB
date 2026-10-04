package com.descentmtb.trail;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Presets of the Trail Shaper. {@link #category} is the tab of the radial menu: 0 jumps, 1 berms, 2 manual.
 * {@link #rise} is the height gained across the block (negative for a drop); the manual modes ignore it.
 * The mode is stored in the tool by its {@link #name()}.
 */
public enum ShapeMode {
    RAMP_QUARTER(0, .25),
    RAMP_HALF(0, .5),
    RAMP_FULL(0, 1.0),
    DROP_HALF(0, -.5),

    BANK_LEFT_HALF(1, .5),
    BANK_RIGHT_HALF(1, .5),
    BANK_LEFT_FULL(1, 1.0),
    BANK_RIGHT_FULL(1, 1.0),
    CORNER_BANK(1, .5),

    AUTO(2, 0),
    WHOLE(2, 0),
    FLATTEN(2, 0),
    RESET(2, 0);

    public static final int CATEGORIES = 3;

    public final int category;
    public final double rise;

    ShapeMode(int category, double rise) {
        this.category = category;
        this.rise = rise;
    }

    /** Language key of the mode's name. */
    public String key() {
        return "descentmtb.shape.mode." + fileName();
    }

    /** Language key of the one-sentence description. */
    public String descriptionKey() {
        return "descentmtb.shape.desc." + fileName();
    }

    /** Lower-case name, also the file name of the icon in {@code textures/gui/shape/}. */
    public String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<ShapeMode> inCategory(int category) {
        return Arrays.stream(values()).filter(mode -> mode.category == category).toList();
    }

    /** The next (or previous, with a negative step) mode of the same category, wrapping around. */
    public ShapeMode cycled(int step) {
        List<ShapeMode> group = inCategory(category);
        return group.get(Math.floorMod(group.indexOf(this) + step, group.size()));
    }

    /** @return the mode of that name; {@link #AUTO} for an unknown or missing one. */
    public static ShapeMode fromName(String name) {
        for (ShapeMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return AUTO;
    }
}
