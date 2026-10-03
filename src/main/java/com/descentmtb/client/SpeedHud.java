package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeSim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/** Speedometer, air time and trick / bail messages while riding. */
public class SpeedHud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MountainBikeEntity bike = BikeClientController.riding();
        if (mc.player == null || mc.options.hideGui || bike == null || bike.sim() == null) return;
        BikeSim sim = bike.sim();
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 58;

        int kmh = (int) Math.round(sim.speed() * 3.6);
        g.drawCenteredString(mc.font, kmh + " km/h", cx, y, 0xFFFFFFFF);
        if (sim.airborne && sim.airTime > 0.3) {
            g.drawCenteredString(mc.font, String.format(java.util.Locale.ROOT, "AIR %.1f s", sim.airTime), cx, y - 12, 0xFF55FFFF);
        }
        if (BikeClientController.messageTicks > 0) {
            int alpha = Math.min(255, BikeClientController.messageTicks * 20);
            g.drawCenteredString(mc.font, BikeClientController.message, cx, g.guiHeight() / 2 - 40,
                    (alpha << 24) | BikeClientController.messageColor);
        }
    }
}
