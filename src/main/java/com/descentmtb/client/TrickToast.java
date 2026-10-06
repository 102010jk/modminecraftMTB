package com.descentmtb.client;

import com.descentmtb.trick.TrickScore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Punchy condensed type, outline, pop and colour accents; stays readable against bright sky. Three flavours:
 * the plain trick name (in the air), a graded banner on a clean landing (Nice .. Legendary: bigger, brighter and longer
 * the better the grade) and a dry grey line after a bad landing.
 *
 * <p>The big title uses the banner font, which has no star or arrow glyphs, so those go in the detail line (default font).
 */
public final class TrickToast {
    private static String title = "", detail = "";
    private static long started;
    private static TrickScore.Grade grade = TrickScore.Grade.NONE;
    private static boolean dry;
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("descentmtb", "banner");
    /** Title colour per grade (index = Grade ordinal): none, nice, great, sick, huge, legendary. */
    private static final int[] TITLE = {0xFFF3BB, 0xFFF3BB, 0xB9FF9E, 0xFFE066, 0xFFA24D, 0xFF7BEA};
    private static final int[] RULE = {0x48E2D0, 0x48E2D0, 0x6BFF9A, 0xFFD23F, 0xFF7A1F, 0xB05CFF};
    private static final int DRY_TITLE = 0xC9CED6, DRY_RULE = 0x7C8591;

    public static void show(String name, String info) {
        set(name, info, TrickScore.Grade.NONE, false);
    }

    /** A clean landing: {@code headline} is the big grade line ("HUGE COMBO"), {@code line} the star-framed detail. */
    public static void showGraded(TrickScore.Grade g, String headline, String line) {
        set(headline, line, g, false);
    }

    /** A bad landing: a short dry line in grey. */
    public static void showDry(String line, String info) {
        set(line, info, TrickScore.Grade.NONE, true);
    }

    private static void set(String name, String info, TrickScore.Grade g, boolean dryLine) {
        if (name.isBlank()) return;
        title = name.toUpperCase(java.util.Locale.ROOT);
        detail = info;
        grade = g;
        dry = dryLine;
        started = Util.getMillis();
    }

    public static void render(GuiGraphics g) {
        if (title.isEmpty() || (ClientConfig.SPEC.isLoaded() && !ClientConfig.TRICK_BANNER.get())) return;
        int level = grade.ordinal();
        double life = 2.8 + 0.35 * level;
        double t = (Util.getMillis() - started) / 1000.0;
        if (t > life) { title = ""; return; }
        int alpha = (int) (255 * Math.min(1, (life - t) / .45));
        if (alpha < 5) return;
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.literal(title).setStyle(Style.EMPTY.withFont(FONT));
        float base = dry ? 1.7f : 2.2f + 0.16f * level;
        float pop = dry ? 0 : (float) (.28 * Math.exp(-t * 7) * Math.cos(t * 18));
        float scale = base + pop;
        scale = Math.min(scale, (g.guiWidth() - 30f) / Math.max(1, mc.font.width(text)));
        int cx = g.guiWidth() / 2, y = Math.max(16, g.guiHeight() / 8);
        g.pose().pushPose(); g.pose().translate(cx, y, 0); g.pose().scale(scale, scale, 1);
        int x = -mc.font.width(text) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (dx != 0 || dy != 0) g.drawString(mc.font, text, x + dx, dy + 1, (alpha << 24) | 0x18212C, false);
        g.drawString(mc.font, text, x, 0, (alpha << 24) | (dry ? DRY_TITLE : TITLE[level]), false);
        g.pose().popPose();
        int width = Math.min(80 + 8 * level, 15 + title.length() * 3);
        int ruleY = y + (int) (12 * scale) + 2;
        g.fill(cx - width, ruleY, cx + width, ruleY + 2, (alpha << 24) | (dry ? DRY_RULE : RULE[level]));
        float dScale = Math.min(grade.ordinal() >= TrickScore.Grade.HUGE.ordinal() ? 1.25f : 1f,
                (g.guiWidth() - 20f) / Math.max(1, mc.font.width(detail)));
        g.pose().pushPose(); g.pose().translate(cx, ruleY + 8, 0); g.pose().scale(dScale, dScale, 1);
        g.drawCenteredString(mc.font, detail, 0, 0, (alpha << 24) | (dry ? 0x9AA3AE : 0xDAFFF6));
        g.pose().popPose();
    }

    private TrickToast() {}
}
