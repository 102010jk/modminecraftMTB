package com.descentmtb.client.trail;

import com.descentmtb.network.TrailBestPayload;
import com.descentmtb.trail.TrailTimes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Top-centre timer box: trail name, the clock and a line with the personal best. While the finished run is
 * shown the clock turns green on a new personal best, otherwise the difference to the best is shown.
 */
public final class TrailTimerHud {
    private static final int BACKGROUND = 0xc81a242b;
    private static final int BRASS = 0xffdfb65e;
    private static final int NAME_COLOUR = 0xffe7d7ad;
    private static final int TIME_COLOUR = 0xffffffff;
    private static final int DIM_COLOUR = 0xff8d989c;
    private static final int GOOD_COLOUR = 0xff6fe08a;
    private static final int SLOW_COLOUR = 0xffe9856f;
    private static final float TIME_SCALE = 2f;

    public static void render(GuiGraphics g) {
        TrailTimer.State state = TrailTimer.state();
        Minecraft mc = Minecraft.getInstance();
        if (state == TrailTimer.State.IDLE || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        String name = TrailTimer.trailName();
        String time = TrailTimes.format(TrailTimer.elapsedMs());
        TrailBestPayload verdict = TrailTimer.verdict();

        int timeColour = TIME_COLOUR;
        String detail = "";
        int detailColour = DIM_COLOUR;
        int best = TrailTimer.bestMs(name);
        switch (state) {
            case ARMED -> {
                timeColour = DIM_COLOUR;
                detail = Component.translatable("descentmtb.timer.ready").getString();
            }
            case RUNNING -> {
                if (best > 0) {
                    detail = Component.translatable("descentmtb.timer.best", TrailTimes.format(best)).getString();
                }
            }
            case FINISHED -> {
                if (verdict != null && verdict.record()) {
                    timeColour = GOOD_COLOUR;
                    detailColour = GOOD_COLOUR;
                    detail = Component.translatable("descentmtb.timer.new_best").getString();
                } else if (verdict != null) {
                    int delta = TrailTimes.deltaCentis((int) TrailTimer.elapsedMs(), verdict.bestMs());
                    detailColour = SLOW_COLOUR;
                    detail = TrailTimes.formatDelta(delta) + "   "
                            + Component.translatable("descentmtb.timer.best", TrailTimes.format(verdict.bestMs())).getString();
                }
            }
            default -> {}
        }

        int timeWidth = Math.round(font.width(time) * TIME_SCALE);
        int width = Math.max(Math.max(font.width(name), timeWidth), font.width(detail)) + 22;
        int height = 12 + 9 + Math.round(8 * TIME_SCALE) + (detail.isEmpty() ? 0 : 12) + 6;
        int x = (g.guiWidth() - width) / 2;
        int y = 6;

        box(g, x, y, width, height);
        g.drawCenteredString(font, name, g.guiWidth() / 2, y + 6, NAME_COLOUR);
        g.pose().pushPose();
        g.pose().translate(g.guiWidth() / 2f, y + 18, 0);
        g.pose().scale(TIME_SCALE, TIME_SCALE, 1);
        g.drawString(font, time, -font.width(time) / 2, 0, timeColour, true);
        g.pose().popPose();
        if (!detail.isEmpty()) {
            g.drawCenteredString(font, detail, g.guiWidth() / 2, y + 18 + Math.round(8 * TIME_SCALE) + 4, detailColour);
        }
    }

    /** A dark box with rounded corners and a brass line along its bottom edge. */
    private static void box(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x + 2, y, x + width - 2, y + height, BACKGROUND);
        g.fill(x, y + 2, x + 2, y + height - 2, BACKGROUND);
        g.fill(x + width - 2, y + 2, x + width, y + height - 2, BACKGROUND);
        g.fill(x + 1, y + 1, x + 2, y + 2, BACKGROUND);
        g.fill(x + width - 2, y + 1, x + width - 1, y + 2, BACKGROUND);
        g.fill(x + 1, y + height - 2, x + 2, y + height - 1, BACKGROUND);
        g.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, BACKGROUND);
        g.fill(x + 6, y + height - 3, x + width - 6, y + height - 1, BRASS);
    }

    private TrailTimerHud() {}
}
