package com.descentmtb.trail;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side construction bounds and dimensions, shared by all players. */
public final class TrailConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue WIDTH, MAX_LENGTH, MAX_BLOCKS, UNDO_DEPTH;
    public static final ModConfigSpec.DoubleValue BERM_HEIGHT, ROLLER_HEIGHT;
    public static final ModConfigSpec.BooleanValue CREATIVE_ONLY, MARKERS;
    static {
        var b = new ModConfigSpec.Builder(); b.push("trails");
        WIDTH = b.comment("Default trail width in blocks.").defineInRange("width", 5, 2, 9);
        MAX_LENGTH = b.comment("Maximum distance between guide points.").defineInRange("maxLength", 64, 8, 128);
        MAX_BLOCKS = b.comment("Maximum blocks per edit/clone.").defineInRange("maxBlocks", 8192, 256, 32768);
        UNDO_DEPTH = b.comment("Edits retained per player; undo skips blocks changed since that edit.").defineInRange("undoDepth", 8, 1, 24);
        BERM_HEIGHT = b.defineInRange("bermHeight", 1.8, .5, 4);
        ROLLER_HEIGHT = b.defineInRange("rollerHeight", .75, .25, 2);
        CREATIVE_ONLY = b.comment("Construction tools edit whole areas; require creative by default.").define("creativeOnly", true);
        MARKERS = b.define("guideMarkers", true); b.pop(); SPEC = b.build();
    }
    private TrailConfig() {}
}
