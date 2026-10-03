package com.descentmtb.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Punchy condensed type, outline, pop and colour accents; stays readable against bright sky. */
public final class TrickToast {
    private static String title = "", detail = "";
    private static long started;
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("descentmtb", "banner");
    public static void show(String name, String info) {
        if (name.isBlank()) return;
        title = name.toUpperCase(java.util.Locale.ROOT); detail = info; started = Util.getMillis();
    }
    public static void render(GuiGraphics g) {
        if (title.isEmpty() || (ClientConfig.SPEC.isLoaded() && !ClientConfig.TRICK_BANNER.get())) return;
        double t = (Util.getMillis() - started) / 1000.0;
        if (t > 2.8) { title = ""; return; }
        int alpha = (int) (255 * Math.min(1, (2.8 - t) / .45));
        if (alpha < 5) return;
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.literal(title).setStyle(Style.EMPTY.withFont(FONT));
        float scale = (float) (2.2 + .28 * Math.exp(-t * 7) * Math.cos(t * 18));
        scale = Math.min(scale, (g.guiWidth() - 30f) / Math.max(1, mc.font.width(text)));
        int cx = g.guiWidth() / 2, y = Math.max(16, g.guiHeight() / 8);
        g.pose().pushPose(); g.pose().translate(cx, y, 0); g.pose().scale(scale, scale, 1);
        int x = -mc.font.width(text) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(mc.font, text, x + dx, dy + 1, (alpha << 24) | 0x18212C, false);
        g.drawString(mc.font, text, x, 0, (alpha << 24) | 0xFFF3BB, false);
        g.pose().popPose();
        int width = Math.min(80, 15 + title.length() * 3);
        g.fill(cx - width, y + 26, cx + width, y + 28, (alpha << 24) | 0x48E2D0);
        g.drawCenteredString(mc.font, detail, cx, y + 34, (alpha << 24) | 0xDAFFF6);
    }
    private TrickToast() {}
}
