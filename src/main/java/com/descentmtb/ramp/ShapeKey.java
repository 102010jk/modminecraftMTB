package com.descentmtb.ramp;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything the model of a shaped block depends on besides its block state, as an immutable value (so
 * baked quads can be cached by it). Corner heights are quantised to 1/1024 block.
 *
 * <p>For a plain {@link RampBlock} the geometry comes from its block state (facing / start / end / profile)
 * and only the material matters; for a trail surface the four corners, the deck flag and the beam do.
 */
public record ShapeKey(int c0, int c1, int c2, int c3, boolean deck, boolean beam, BlockState material) {
    private static final double SCALE = 1024;

    public static ShapeKey ramp(BlockState material) {
        return new ShapeKey(0, 0, 0, 0, false, false, material);
    }

    public static ShapeKey surface(double[] corners, boolean deck, boolean beam, BlockState material) {
        return new ShapeKey(q(corners[0]), q(corners[1]), q(corners[2]), q(corners[3]), deck, beam, material);
    }

    private static int q(double v) {
        return (int) Math.round(v * SCALE);
    }

    /** Corner heights in blocks, order NW NE SW SE. */
    public double[] corners() {
        return new double[]{c0 / SCALE, c1 / SCALE, c2 / SCALE, c3 / SCALE};
    }
}
