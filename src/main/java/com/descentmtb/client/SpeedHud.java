package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeSim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/** Speedometer, air time and trick / bail messages while riding. */
public class SpeedHud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        TrickToast.render(g);
        MountainBikeEntity bike = BikeClientController.riding();
        if (mc.player == null || mc.options.hideGui || bike == null || bike.sim() == null) return;
        BikeSim sim = bike.sim();
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 58;

        int kmh = (int) Math.round(sim.speed() * 3.6);
        g.drawCenteredString(mc.font, Component.translatable("descentmtb.hud.speed", kmh), cx, y, 0xFFFFFFFF);
        if (sim.engine != null) tachometer(g, mc, sim, cx, y + 11);
        if (sim.airborne && sim.airTime > 0.3) {
            g.drawCenteredString(mc.font, Component.translatable("descentmtb.hud.air", String.format(java.util.Locale.ROOT, "%.1f", sim.airTime)),
                    cx, y - 12, 0xFF55FFFF);
        }
        if (BikeClientController.messageTicks > 0) {
            int alpha = Math.min(255, BikeClientController.messageTicks * 20);
            g.drawCenteredString(mc.font, BikeClientController.message, cx, g.guiHeight() / 2 - 40,
                    (alpha << 24) | BikeClientController.messageColor);
        }
    }

    /** Dirt bike: gear and a rev bar that turns from green through amber to red at the limiter. */
    private static void tachometer(GuiGraphics g, Minecraft mc, BikeSim sim, int cx, int y) {
        var p = sim.p;
        double frac = Math.max(0, Math.min(1, (sim.engine.rpm - p.idleRpm) / (p.limitRpm - p.idleRpm)));
        int w = 80, x0 = cx - w / 2;
        g.fill(x0 - 1, y - 1, x0 + w + 1, y + 4, 0xA0101018);
        int filled = (int) Math.round(w * frac);
        for (int i = 0; i < filled; i++) {
            double t = i / (double) w;
            int color = t < 0.6 ? 0xFF5BCB6A : t < 0.85 ? 0xFFF0B43C : 0xFFE5483A;
            if (sim.engine.limiting && (mc.player.tickCount / 2) % 2 == 0) color = 0xFFFFFFFF;
            g.fill(x0 + i, y, x0 + i + 1, y + 3, color);
        }
        String gear = String.valueOf(sim.engine.gear + 1);
        g.drawString(mc.font, gear, x0 + w + 5, y - 3, 0xFFFFE08A, true);
    }
}
