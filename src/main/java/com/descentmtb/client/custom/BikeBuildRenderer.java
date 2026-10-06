package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.entity.BikeType;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws a customised bike outside the world (workshop screen preview). CONTRACT between the rendering work and
 * the workshop screen: the rendering agent implements the body, the screen only calls it.
 */
public final class BikeBuildRenderer {
    /**
     * Renders the bike model with all parts, colours, accessories and stickers of {@code build}, centred at (x, y) in
     * GUI pixels, {@code scale} GUI pixels per metre, turned by {@code yawDeg} around the vertical axis and tilted by
     * {@code pitchDeg} toward the viewer. Must be callable every frame (no allocation-heavy work).
     */
    public static void renderInGui(GuiGraphics g, BikeType type, BikeBuild build, float x, float y, float scale,
                                   float yawDeg, float pitchDeg) {
        // implemented by the rendering work
    }

    private BikeBuildRenderer() {}
}
