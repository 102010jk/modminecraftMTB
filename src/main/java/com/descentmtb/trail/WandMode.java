package com.descentmtb.trail;

/**
 * Plan types of the pure planners ({@link SurfacePlans}, {@link EquipmentPlans}, {@link TrailBuilder}), kept for
 * the dev commands and tests. Players shape with the Trail Shaper ({@link ShapeMode}) instead.
 */
public enum WandMode {
    FLOW(0), PUMP_LINE(0), PUMP_LOOP(0),
    DIRT_JUMP(1), WOOD_KICKER(1), WOOD_DROP(1), DROP_EDGE(1),
    BERM(2), ENDURO(2), SHARKFIN(2),
    BOARDWALK(3), SUPPORT(3), CLONE(3), TEMPLATE(3),
    ROOTS(4), ROCKS(4), ROCK_GARDEN(4), BARRIER(4), AIRBAG(4), SIGN(4), RAMP_TUNE(4), MEASURE(4), UNDO(4),
    RAISE(0),LOWER(0),SMOOTH(0),FLATTEN(0);

    public final int category;

    WandMode(int category) {
        this.category = category;
    }

    public static WandMode from(int index) {
        return values()[Math.floorMod(index, values().length)];
    }

    /** Mode order before the terrain brushes were removed (saved in old items as an ordinal). */
    private static final String[] LEGACY_ORDER = {
            "FLOW", "RAISE", "LOWER", "SMOOTH", "FLATTEN", "PUMP_LINE", "PUMP_LOOP", "DIRT_JUMP", "WOOD_KICKER", "WOOD_DROP",
            "DROP_EDGE", "BERM", "ENDURO", "SHARKFIN", "BOARDWALK", "SUPPORT", "CLONE", "TEMPLATE", "ROOTS", "ROCKS",
            "ROCK_GARDEN", "BARRIER", "AIRBAG", "SIGN", "MEASURE", "UNDO"};

    public static WandMode fromName(String name) {
        for (WandMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return FLOW;   // unknown or removed mode (old brushes)
    }

    public static WandMode fromLegacyOrdinal(int ordinal) {
        return fromName(ordinal >= 0 && ordinal < LEGACY_ORDER.length ? LEGACY_ORDER[ordinal] : "FLOW");
    }
}
