package com.descentmtb.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Common colours and bounded text for menus and compact HUD cards. */
public final class UiTheme {
    public static final int PANEL = 0xf018252d;
    public static final int EDGE = 0xff354650;
    public static final int ACCENT = 0xffe2c48a;
    public static final int TEXT = 0xffeee6d2;
    public static final int MUTED = 0xffa9b4b8;

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, EDGE);
    }

    public static String fit(Font font, String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        String suffix = "…";
        if (font.width(suffix) > width) return "";
        return font.plainSubstrByWidth(text, width - font.width(suffix)) + suffix;
    }

    public static int wrapped(GuiGraphics graphics, Font font, Component text, int x, int y,
                              int width, int maxLines, int color) {
        var lines = font.split(text, Math.max(1, width));
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            var line = lines.get(i);
            if (i == count - 1 && lines.size() > count) {
                // Preserve the missing-content cue even when a paragraph exceeds its allotted space.
                StringBuilder value = new StringBuilder();
                line.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
                graphics.drawString(font, fit(font, value + "…", width), x, y, color, false);
            } else graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 1;
        }
        return y;
    }

    private UiTheme() {}
}
