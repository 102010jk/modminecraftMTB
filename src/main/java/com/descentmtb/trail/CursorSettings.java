package com.descentmtb.trail;

import com.descentmtb.trail.CornerEdits.Pick;
import com.descentmtb.trail.CornerEdits.Step;
import net.minecraft.world.item.ItemStack;

/**
 * What the cursor ({@link ShapeMode#AUTO}) of a Trail Shaper is set to: which corners a click picks and how far it
 * moves them. Shift + wheel cycles the sub-type, Ctrl + Shift + wheel the step (see the client's {@code TrailClient}).
 * Both are kept in the tool's custom data by name.
 */
public record CursorSettings(Pick pick, Step step) {
    public static final String PICK_TAG = "CursorPick", STEP_TAG = "CursorStep";
    public static final CursorSettings DEFAULT = new CursorSettings(Pick.AUTO_ZONE, Step.SIXTEENTH);

    public static CursorSettings read(ItemStack tool) {
        var data = ShapeToolItem.data(tool);
        return new CursorSettings(Pick.fromName(data.getString(PICK_TAG)), Step.fromName(data.getString(STEP_TAG)));
    }

    public void store(ItemStack tool) {
        ShapeToolItem.editData(tool, tag -> {
            tag.putString(PICK_TAG, pick.name());
            tag.putString(STEP_TAG, step.name());
        });
    }

    /** These settings with the next (or previous) sub-type. */
    public CursorSettings picking(int direction) {
        return new CursorSettings(pick.cycled(direction), step);
    }

    /** These settings with the next (or previous) step size. */
    public CursorSettings stepping(int direction) {
        return new CursorSettings(pick, step.cycled(direction));
    }
}
