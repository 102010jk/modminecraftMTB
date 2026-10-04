package com.descentmtb.client.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.SignArt;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.SignLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Draws the front of a sign into a GUI, using the same {@link SignLayout} as the in-world renderer. */
public final class SignPreview {
    private static final ResourceLocation BOARD =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/block/trail_sign.png");
    private static final int FRAME_COLOUR = 0xff4a3220;
    private static final int TEXT_COLOUR = 0xff2b1b0e;

    /**
     * @param x     left edge of the board in GUI pixels
     * @param y     top edge of the board in GUI pixels
     * @param scale GUI pixels per layout unit
     */
    public static void draw(GuiGraphics g, Font font, SignContent sign, int x, int y, double scale) {
        int w = (int) Math.round(SignLayout.WIDTH * scale), h = (int) Math.round(SignLayout.HEIGHT * scale);
        int frame = (int) Math.round(SignLayout.FRAME * scale);
        g.blit(BOARD, x, y, w, h, 0, 0, 16, 16, 16, 16);
        g.fill(x, y, x + w, y + frame, FRAME_COLOUR);
        g.fill(x, y + h - frame, x + w, y + h, FRAME_COLOUR);
        g.fill(x, y + frame, x + frame, y + h - frame, FRAME_COLOUR);
        g.fill(x + w - frame, y + frame, x + w, y + h - frame, FRAME_COLOUR);

        String startLabel = Component.translatable("descentmtb.sign.label.start").getString();
        String finishLabel = Component.translatable("descentmtb.sign.label.finish").getString();
        for (SignLayout.Item item : SignLayout.build(sign, startLabel, finishLabel, font::width)) {
            switch (item) {
                case SignLayout.Icon icon -> g.blit(TrailSignRenderer.icon(icon.name()),
                        x + (int) Math.round(icon.x() * scale), y + (int) Math.round(icon.y() * scale),
                        (int) Math.round(icon.size() * scale), (int) Math.round(icon.size() * scale), 0, 0, 16, 16, 16, 16);
                case SignLayout.Label label -> drawLabel(g, font, label, x, y, scale);
                case SignLayout.Art art -> drawArt(g, sign, art, x, y, scale);
            }
        }
    }

    private static void drawLabel(GuiGraphics g, Font font, SignLayout.Label label, int x, int y, double scale) {
        g.pose().pushPose();
        g.pose().translate(x + label.centreX() * scale, y + label.top() * scale, 0);
        float s = (float) (scale * label.scale());
        g.pose().scale(s, s, 1);
        g.drawString(font, label.text(), -font.width(label.text()) / 2, 0, TEXT_COLOUR, false);
        g.pose().popPose();
    }

    private static void drawArt(GuiGraphics g, SignContent sign, SignLayout.Art art, int x, int y, double scale) {
        byte[] pixels = sign.pixels();
        for (int row = 0; row < 16; row++) {
            for (int column = 0; column < 16; column++) {
                int x0 = x + (int) Math.round((art.x() + column * art.cell()) * scale);
                int x1 = x + (int) Math.round((art.x() + (column + 1) * art.cell()) * scale);
                int y0 = y + (int) Math.round((art.y() + row * art.cell()) * scale);
                int y1 = y + (int) Math.round((art.y() + (row + 1) * art.cell()) * scale);
                g.fill(x0, y0, x1, y1, SignArt.PALETTE[pixels[row * 16 + column] & 15]);
            }
        }
    }

    private SignPreview() {}
}
