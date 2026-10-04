package com.descentmtb.trail;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Modes of the Trail Shaper. {@link #category} is the tab of the radial menu: 0 jumps, 1 berms, 2 manual
 * (and copying), 3 copycat ramps. {@link #rise} is the height gained across the block (negative for a drop); only
 * the {@link Kind#COLUMN} modes use it. The mode is stored in the tool by its {@link #name()}.
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
    BERM_BUILD(1, 0, Kind.BERM),

    AUTO(2, 0),
    WHOLE(2, 0),
    FLATTEN(2, 0),
    RESET(2, 0),
    COPY(2, 0, Kind.COPY),

    RAMP_MAKE(3, 0, Kind.RAMP),
    RAMP_STEEPNESS(3, 0, Kind.RAMP),
    RAMP_START(3, 0, Kind.RAMP),
    RAMP_PROFILE(3, 0, Kind.RAMP),
    RAMP_ROTATE(3, 0, Kind.RAMP),
    RAMP_LINK(3, 0, Kind.RAMP);

    public static final int CATEGORIES = 4;

    /** What a click does, which decides the code that handles it. */
    public enum Kind {
        /** Reshapes the one clicked block ({@link ShapePresets}). */
        COLUMN,
        /** Tunes a copycat ramp block ({@link RampTuning}). */
        RAMP,
        /** Collects three points and builds a berm ({@link BermBuilder}). */
        BERM,
        /** Copies and pastes a box of blocks ({@link ShapeClipboard}). */
        COPY
    }

    public final int category;
    public final double rise;
    public final Kind kind;

    ShapeMode(int category, double rise) {
        this(category, rise, Kind.COLUMN);
    }

    ShapeMode(int category, double rise, Kind kind) {
        this.category = category;
        this.rise = rise;
        this.kind = kind;
    }

    /** Language key of the mode's name. */
    public String key() {
        return "descentmtb.shape.mode." + fileName();
    }

    /** Language key of the one-sentence description. */
    public String descriptionKey() {
        return "descentmtb.shape.desc." + fileName();
    }

    /** Language key of the hint line in the HUD: one per tab, except for the modes that work differently. */
    public String hintKey() {
        return switch (kind) {
            case BERM -> "descentmtb.shape.hud.hint.berm_build";
            case COPY -> "descentmtb.shape.hud.hint.copy";
            default -> "descentmtb.shape.hud.hint." + category;
        };
    }

    /** Lower-case name, also the file name of the icon in {@code textures/gui/shape/}. */
    public String fileName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for the presets that reshape the one clicked block and can therefore be previewed with {@link ShapePresets}. */
    public boolean reshapesBlock() {
        return kind == Kind.COLUMN;
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
