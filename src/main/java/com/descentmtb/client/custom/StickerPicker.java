package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.Tube;

/**
 * FALLBACK mapping between a click in the fixed side view of the workshop preview and a place on the frame: the
 * nearest of the seven {@link Tube}s and the position t (0 = start, 1 = end of the segment in the tables below) along
 * it, and the way back (where a sticker sits). Used while the renderer has no anchor table of its own.
 *
 * <p>Model side-view coordinates in metres: x forward, y up, origin on the ground midway between the wheel contact
 * patches (the models' own origin). The tube lines are the frame triangles of the two bike models, read off their
 * constants; the frame shape variants are not told apart. Pure Java (no Minecraft classes), unit tested.
 */
public final class StickerPicker {

    /** Where a click landed: the tube, t along it and the distance (m) from the click to the tube. */
    public record Pick(Tube tube, float t, float distance) {}

    /**
     * Segment ends (x0, y0, x1, y1), indexed by {@link Tube#ordinal()}: TOP, DOWN, SEAT, SEAT_STAY, CHAIN_STAY, HEAD,
     * FORK_LEG.
     */
    private static final float[][] ENDURO = {
            {-0.27f, 0.84f, 0.36f, 0.90f},     // top tube: seat cluster -> head tube top
            {0.41f, 0.70f, -0.19f, 0.35f},     // down tube: head tube bottom -> bottom bracket
            {-0.19f, 0.35f, -0.32f, 0.92f},    // seat tube: bottom bracket -> seat clamp
            {-0.30f, 0.82f, -0.63f, 0.375f},   // seat stay: seat cluster -> rear axle
            {-0.19f, 0.35f, -0.63f, 0.375f},   // chain stay: bottom bracket -> rear axle
            {0.41f, 0.70f, 0.36f, 0.93f},      // head tube: bottom -> top
            {0.43f, 0.66f, 0.63f, 0.375f},     // fork leg: crown -> front axle
    };

    private static final float[][] HARDTAIL = {
            {-0.22f, 0.80f, 0.31f, 0.82f},
            {0.35f, 0.62f, -0.14f, 0.31f},
            {-0.14f, 0.31f, -0.26f, 0.84f},
            {-0.24f, 0.74f, -0.53f, 0.33f},
            {-0.14f, 0.31f, -0.53f, 0.33f},
            {0.35f, 0.62f, 0.31f, 0.85f},
            {0.37f, 0.60f, 0.53f, 0.33f},
    };

    /** A click farther than this (m) from every tube is not on the frame. */
    public static final float MAX_DISTANCE = 0.16f;

    private static float[][] table(boolean fullSuspension) {
        return fullSuspension ? ENDURO : HARDTAIL;
    }

    /** The tube nearest to the point (x forward, y up, metres), or null when none is within {@link #MAX_DISTANCE}. */
    public static Pick pick(boolean fullSuspension, FrameShape shape, float x, float y) {
        Pick best = nearest(fullSuspension, shape, x, y);
        return best.distance <= MAX_DISTANCE ? best : null;
    }

    /** Like {@link #pick} but always answers: the nearest tube however far away. */
    public static Pick nearest(boolean fullSuspension, FrameShape shape, float x, float y) {
        float[][] t = table(fullSuspension);
        Pick best = null;
        for (Tube tube : Tube.values()) {
            float[] s = t[tube.ordinal()];
            float dx = s[2] - s[0], dy = s[3] - s[1];
            float len2 = dx * dx + dy * dy;
            float along = len2 < 1e-9f ? 0 : Math.max(0f, Math.min(1f, ((x - s[0]) * dx + (y - s[1]) * dy) / len2));
            float dist = (float) Math.hypot(x - (s[0] + dx * along), y - (s[1] + dy * along));
            if (best == null || dist < best.distance) {
                best = new Pick(tube, along, dist);
            }
        }
        return best;
    }

    /** The side-view point {x, y} (metres) at position t of a tube. */
    public static float[] pointOn(boolean fullSuspension, FrameShape shape, Tube tube, float t) {
        float[] s = table(fullSuspension)[tube.ordinal()];
        t = Math.max(0f, Math.min(1f, t));
        return new float[]{s[0] + (s[2] - s[0]) * t, s[1] + (s[3] - s[1]) * t};
    }

    /** The two ends {x0, y0, x1, y1} of a tube, for drawing a guide line. */
    public static float[] segment(boolean fullSuspension, FrameShape shape, Tube tube) {
        return table(fullSuspension)[tube.ordinal()].clone();
    }

    private StickerPicker() {}
}
