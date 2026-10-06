package com.descentmtb.trail;

import net.minecraft.world.item.ItemStack;

/**
 * The width (blocks) of the two-click line tools ({@link ShapeMode#CLEAR_PATH}, {@link ShapeMode#STRAIGHT_LINE}), kept in the
 * Trail Shaper's custom data. Ctrl + Shift + wheel changes it. The path clearing never works narrower than
 * {@link #CLEAR_MIN} blocks.
 */
public record LineSettings(int width) {
    /** Custom data key of the width. */
    public static final String WIDTH_TAG = "LineWidth";
    public static final LineSettings DEFAULT = new LineSettings(3);
    /** The narrowest path that is cleared (blocks): a bike and its rider need room. */
    public static final int CLEAR_MIN = 3;

    public static LineSettings read(ItemStack tool) {
        var data = ShapeToolItem.data(tool);
        return data.contains(WIDTH_TAG) ? new LineSettings(data.getInt(WIDTH_TAG)).bounded() : DEFAULT;
    }

    public void store(ItemStack tool) {
        ShapeToolItem.editData(tool, tag -> tag.putInt(WIDTH_TAG, bounded().width));
    }

    /** These settings with the width inside {@link StraightLines#MIN_WIDTH} .. {@link StraightLines#MAX_WIDTH} (what a client sends is not trusted). */
    public LineSettings bounded() {
        return new LineSettings(Math.max(StraightLines.MIN_WIDTH, Math.min(StraightLines.MAX_WIDTH, width)));
    }

    /** These settings with the width {@code step} blocks wider (narrower when negative). */
    public LineSettings wider(int step) {
        return new LineSettings(width + step).bounded();
    }

    /** The width the path clearing works with. */
    public int clearWidth() {
        return Math.max(CLEAR_MIN, width);
    }
}
