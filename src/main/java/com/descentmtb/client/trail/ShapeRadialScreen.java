package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Radial menu of the Trail Shaper. Hold the menu key, move the mouse to a mode and release (or click).
 * The tabs on top switch between jumps, berms, manual edits (and copying), copycat ramps and whole lines.
 */
public final class ShapeRadialScreen extends Screen {
    private static final int TAB_WIDTH = 84, TAB_HEIGHT = 22, TAB_Y = 28;
    private static final int CELL_W = 74, CELL_H = 40;

    private final ShapeMode current;
    private int category;
    private ShapeMode hover;

    public ShapeRadialScreen() {
        super(Component.translatable("descentmtb.shape.title"));
        current = ShapeToolItem.mode(Minecraft.getInstance().player.getMainHandItem());
        category = current.category;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The icon of a mode: a 16x16 picture in {@code textures/gui/shape/}. */
    public static ResourceLocation icon(ShapeMode mode) {
        return ResourceLocation.fromNamespaceAndPath("descentmtb", "textures/gui/shape/" + mode.fileName() + ".png");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x95121a20);
        int cx = width / 2;
        int cy = height / 2 + 12;
        g.drawCenteredString(font, title, cx, 10, 0xffe4c488);

        drawTabs(g);
        drawModes(g, mouseX, mouseY, cx, cy);
        drawCentre(g, cx, cy);
    }

    private int tabsLeft() {
        return width / 2 - ShapeMode.CATEGORIES * TAB_WIDTH / 2;
    }

    private void drawTabs(GuiGraphics g) {
        int left = tabsLeft();
        for (int i = 0; i < ShapeMode.CATEGORIES; i++) {
            int x = left + i * TAB_WIDTH;
            boolean selected = i == category;
            g.fill(x, TAB_Y, x + TAB_WIDTH - 3, TAB_Y + TAB_HEIGHT, selected ? 0xff806744 : 0xff29373f);
            g.fill(x, TAB_Y + TAB_HEIGHT - 2, x + TAB_WIDTH - 3, TAB_Y + TAB_HEIGHT, selected ? 0xffe3b65e : 0xff3b4a52);
            g.drawCenteredString(font, Component.translatable("descentmtb.shape.category." + i),
                    x + (TAB_WIDTH - 3) / 2, TAB_Y + 7, 0xffe6e2d7);
        }
    }

    private void drawModes(GuiGraphics g, int mouseX, int mouseY, int cx, int cy) {
        hover = null;
        List<ShapeMode> modes = ShapeMode.inCategory(category);
        double radiusX = Math.min(120, width * .27);
        double radiusY = Math.max(44, (height - 150) / 2.0);
        for (int i = 0; i < modes.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / modes.size();
            int x = (int) (cx + Math.cos(angle) * radiusX - CELL_W / 2.0);
            int y = (int) (cy + Math.sin(angle) * radiusY - CELL_H / 2.0);
            ShapeMode mode = modes.get(i);
            boolean over = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
            if (over) {
                hover = mode;
            }
            int border = over ? 0xff72d5c3 : mode == current ? 0xffdfb65e : 0xff74634e;
            g.fill(x - 1, y - 1, x + CELL_W + 1, y + CELL_H + 1, border);
            g.fill(x, y, x + CELL_W, y + CELL_H, over ? 0xff314b4e : 0xff25343d);
            g.blit(icon(mode), x + CELL_W / 2 - 8, y + 4, 0, 0, 16, 16, 16, 16);
            g.drawCenteredString(font, Component.translatable(mode.key()), x + CELL_W / 2, y + 25,
                    over ? 0xffcaffed : 0xffe0d7c1);
        }
    }

    private void drawCentre(GuiGraphics g, int cx, int cy) {
        ShapeMode shown = hover != null ? hover : current;
        g.blit(icon(shown), cx - 16, cy - 30, 32, 32, 0, 0, 16, 16, 16, 16);
        g.drawCenteredString(font, Component.translatable(shown.key()), cx, cy + 6, 0xfff5d087);
        g.drawCenteredString(font, Component.translatable(shown.descriptionKey()), cx, cy + 18, 0xff9cb9b5);
    }

    private void choose(ShapeMode mode) {
        PacketDistributor.sendToServer(new ShapeTunePayload(mode));
        ShapeToolItem.mode(minecraft.player.getMainHandItem(), mode);
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
        int left = tabsLeft();
        if (my >= TAB_Y && my <= TAB_Y + TAB_HEIGHT && mx >= left && mx < left + ShapeMode.CATEGORIES * TAB_WIDTH) {
            category = (int) ((mx - left) / TAB_WIDTH);
            return true;
        }
        if (button == 0 && hover != null) {
            choose(hover);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
