package com.descentmtb.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared, quiet background for MTB editors; never blurs already rendered content. */
public abstract class DescentScreen extends Screen {
    protected DescentScreen(Component title) { super(title); }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xda18252d, 0xee0f171d);
    }
}
