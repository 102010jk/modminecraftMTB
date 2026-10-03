package com.descentmtb.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TreeNamesTest {
    @Test void logsWoodAndStrippedShareAFamily() {
        assertEquals("oak", TreeNames.family("oak_log"));
        assertEquals("oak", TreeNames.family("stripped_oak_log"));
        assertEquals("oak", TreeNames.family("oak_wood"));
        assertEquals("dark_oak", TreeNames.family("dark_oak_log"));
        assertEquals("crimson", TreeNames.family("crimson_stem"));
        assertEquals("warped", TreeNames.family("stripped_warped_hyphae"));
    }

    @Test void neighbouringSpeciesAreNotMixedUp() {
        assertNotEquals(TreeNames.family("oak_log"), TreeNames.family("birch_log"));
        assertNotEquals(TreeNames.family("oak_log"), TreeNames.family("dark_oak_log"));
    }
}
