package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

/** Tiny speedometer shown while riding. */
public class SpeedHud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!(mc.player.getVehicle() instanceof MountainBikeEntity bike)) {
            return;
        }
        double blocksPerTick = bike.getDeltaMovement().horizontalDistance();
        int kmh = (int) Math.round(blocksPerTick * 20.0 * 3.6); // blocks/tick -> m/s -> km/h
        String text = kmh + " km/h";
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 56;
        graphics.drawCenteredString(mc.font, text, x, y, 0xFFFFFFFF);
    }
}
