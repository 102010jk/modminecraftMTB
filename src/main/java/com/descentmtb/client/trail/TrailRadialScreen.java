package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.network.TrailActionPayload;
import com.descentmtb.trail.WandMode;
import com.descentmtb.trail.WandSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Radial menu of the Trail Builder. Hold the menu key, move the mouse to a mode and release (or click).
 * The five tabs on top switch between the families of modes.
 */
public final class TrailRadialScreen extends Screen {
    private static final String[] CATEGORY_ICONS = {"category_lines", "category_jumps", "category_turns", "category_wood", "category_gear"};
    private static final int TAB_WIDTH = 66, TAB_HEIGHT = 22, TAB_Y = 28;
    private static final int CELL_W = 74, CELL_H = 40;

    private final WandSettings current;
    private int category;
    private WandMode hover;

    public TrailRadialScreen() {
        super(Component.translatable("descentmtb.wand.title"));
        current = WandSettings.read(net.minecraft.client.Minecraft.getInstance().player.getMainHandItem());
        category = current.mode().category;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<WandMode> modes() {
        return Arrays.stream(WandMode.values()).filter(m -> m.category == category).toList();
    }

    static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath("descentmtb", "textures/gui/trail/" + name + ".png");
    }

    static ResourceLocation icon(WandMode mode) {
        return icon(mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x95121a20);
        int cx = width / 2;
        int cy = height / 2 + 12;
        g.drawCenteredString(font, title, cx, 10, 0xffe4c488);

        drawTabs(g, cx);
        drawModes(g, mouseX, mouseY, cx, cy);
        drawCentre(g, cx, cy);
        drawSettingsButton(g, cx);
    }

    private void drawTabs(GuiGraphics g, int cx) {
        int left = cx - 5 * TAB_WIDTH / 2;
        for (int i = 0; i < CATEGORY_ICONS.length; i++) {
            int x = left + i * TAB_WIDTH;
            boolean selected = i == category;
            g.fill(x, TAB_Y, x + TAB_WIDTH - 3, TAB_Y + TAB_HEIGHT, selected ? 0xff806744 : 0xff29373f);
            g.fill(x, TAB_Y + TAB_HEIGHT - 2, x + TAB_WIDTH - 3, TAB_Y + TAB_HEIGHT, selected ? 0xffe3b65e : 0xff3b4a52);
            g.blit(icon(CATEGORY_ICONS[i]), x + 3, TAB_Y + 3, 0, 0, 16, 16, 16, 16);
            g.drawString(font, Component.translatable("descentmtb.wand.category." + i), x + 21, TAB_Y + 7, 0xffe6e2d7, false);
        }
    }

    private void drawModes(GuiGraphics g, int mouseX, int mouseY, int cx, int cy) {
        hover = null;
        List<WandMode> modes = modes();
        double radiusX = Math.min(120, width * .27);
        double radiusY = Math.max(44, (height - 150) / 2.0);
        for (int i = 0; i < modes.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / modes.size();
            int x = (int) (cx + Math.cos(angle) * radiusX - CELL_W / 2.0);
            int y = (int) (cy + Math.sin(angle) * radiusY - CELL_H / 2.0);
            WandMode mode = modes.get(i);
            boolean over = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
            if (over) {
                hover = mode;
            }
            boolean active = mode == current.mode();
            g.fill(x - 1, y - 1, x + CELL_W + 1, y + CELL_H + 1, over ? 0xff72d5c3 : active ? 0xffdfb65e : 0xff74634e);
            g.fill(x, y, x + CELL_W, y + CELL_H, over ? 0xff314b4e : 0xff25343d);
            g.blit(icon(mode), x + CELL_W / 2 - 8, y + 4, 0, 0, 16, 16, 16, 16);
            g.drawCenteredString(font, Component.translatable(mode.key()), x + CELL_W / 2, y + 25, over ? 0xffcaffed : 0xffe0d7c1);
        }
    }

    private void drawCentre(GuiGraphics g, int cx, int cy) {
        WandMode shown = hover != null ? hover : current.mode();
        g.blit(icon(shown), cx - 16, cy - 30, 0, 0, 32, 32, 32, 32);
        g.drawCenteredString(font, Component.translatable(shown.key()), cx, cy + 6, 0xfff5d087);
        g.drawCenteredString(font, String.format(Locale.ROOT, "%.1f × %.2f m", current.width(), current.height()), cx, cy + 18, 0xff9cb9b5);
    }

    private void drawSettingsButton(GuiGraphics g, int cx) {
        g.fill(cx - 80, height - 30, cx + 80, height - 8, 0xff34454c);
        g.blit(icon("settings"), cx - 74, height - 27, 0, 0, 16, 16, 16, 16);
        g.drawCenteredString(font, Component.translatable("descentmtb.wand.settings"), cx + 8, height - 23, 0xffe7d5aa);
    }

    private void choose(WandMode mode) {
        WandSettings chosen = new WandSettings(mode, current.width(), current.height(), current.spacing(), current.repeats(),
                current.radius(), current.strength(), current.softness());
        PacketDistributor.sendToServer(new TrailActionPayload(TrailActionPayload.CONFIGURE, chosen.tag(), "", 0, 0, 0));
        chosen.store(minecraft.player.getMainHandItem());
        onClose();
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (ModKeyMappings.TRAIL_MENU.matches(key, scan)) {
            if (hover != null) {
                choose(hover);
            } else {
                onClose();
            }
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2;
        int left = cx - 5 * TAB_WIDTH / 2;
        if (my >= TAB_Y && my <= TAB_Y + TAB_HEIGHT && mx >= left && mx < left + 5 * TAB_WIDTH) {
            category = (int) ((mx - left) / TAB_WIDTH);
            return true;
        }
        if (my >= height - 30 && my < height - 8 && Math.abs(mx - cx) < 80) {
            minecraft.setScreen(new TrailSettingsScreen());
            return true;
        }
        if (button == 0 && hover != null) {
            choose(hover);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
