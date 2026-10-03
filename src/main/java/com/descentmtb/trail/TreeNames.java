package com.descentmtb.trail;

/** Name helpers for the machete (pure strings, unit-tested). */
public final class TreeNames {
    private static final String[] TRUNK_SUFFIXES = {"_log", "_wood", "_stem", "_hyphae"};

    /**
     * Wood family of a log-like block id path: {@code stripped_oak_log} and {@code oak_wood} are both
     * "oak", so a felled oak never takes the neighbouring birch with it.
     */
    public static String family(String blockPath) {
        String name = blockPath.startsWith("stripped_") ? blockPath.substring("stripped_".length()) : blockPath;
        for (String suffix : TRUNK_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }

    private TreeNames() {}
}
