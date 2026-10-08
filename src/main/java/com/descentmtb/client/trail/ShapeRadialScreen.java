package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.client.ui.UiTheme;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.descentmtb.client.ui.DescentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Radial menu of the Trail Shaper. Hold the menu key, move the mouse to a mode and release (or click).
 * When the key is already up as the screen opens (a quick tap) it works as a plain click menu instead: it stays
 * open until a mode is clicked, or until a click on empty space or Esc closes it.
 * The tabs on top switch between jumps, berms, manual edits (and copying), copycat ramps and whole lines.
 */
public final class ShapeRadialScreen extends DescentScreen {
    private static final int TAB_WIDTH = 84, TAB_HEIGHT = 22, TAB_Y = 28;
    private static final int CELL_W = 74, CELL_H = 40;

    private final ShapeMode current;
    private int category;
    private ShapeMode hover;
    private boolean started, clickMode;
    private int keyboardIndex = -1, lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;

    public ShapeRadialScreen() {
        super(Component.translatable("descentmtb.shape.title"));
        current = ShapeToolItem.mode(Minecraft.getInstance().player.getMainHandItem());
        category = current.category;
    }

    @Override
    protected void init() {
        if (!started) {
            started = true;
            clickMode = !menuKeyHeld();
        }
    }

    /** Whether the menu key is still down; {@code KeyMapping.isDown} is useless here, opening a screen releases all keys. */
    private boolean menuKeyHeld() {
        InputConstants.Key key = ModKeyMappings.TRAIL_MENU.getKey();
        long window = minecraft.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
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
        renderBackground(g, mouseX, mouseY, partialTick);
        if (mouseX != lastMouseX || mouseY != lastMouseY) keyboardIndex = -1;
        lastMouseX = mouseX; lastMouseY = mouseY;
        int cx = width / 2;
        int cy = centreY();
        g.drawCenteredString(font, title, cx, 10, 0xffe4c488);

        drawTabs(g);
        drawModes(g, mouseX, mouseY, cx, cy);
        drawCentre(g, cx, cy);
    }

    private int tabsLeft() {
        return (width - ShapeMode.CATEGORIES * tabWidth()) / 2;
    }

    private int tabWidth() {
        return Math.min(TAB_WIDTH, Math.max(1, (width - 16) / ShapeMode.CATEGORIES));
    }

    private int centreY() {
        return (TAB_Y + TAB_HEIGHT + 8 + height - 50) / 2;
    }

    private double radiusY() {
        return Math.min(80, Math.max(32, (height - TAB_Y - TAB_HEIGHT - 8 - 50 - CELL_H) / 2.0));
    }

    private void drawTabs(GuiGraphics g) {
        int left = tabsLeft();
        int tabWidth = tabWidth();
        for (int i = 0; i < ShapeMode.CATEGORIES; i++) {
            int x = left + i * tabWidth;
            boolean selected = i == category;
            g.fill(x, TAB_Y, x + tabWidth - 3, TAB_Y + TAB_HEIGHT, selected ? 0xff3d413c : 0xff29373f);
            g.fill(x, TAB_Y + TAB_HEIGHT - 2, x + tabWidth - 3, TAB_Y + TAB_HEIGHT, selected ? UiTheme.ACCENT : UiTheme.EDGE);
            String label = UiTheme.fit(font, Component.translatable("descentmtb.shape.category." + i).getString(), tabWidth - 8);
            g.drawCenteredString(font, label, x + (tabWidth - 3) / 2, TAB_Y + 7, UiTheme.TEXT);
        }
    }

    private void drawModes(GuiGraphics g, int mouseX, int mouseY, int cx, int cy) {
        List<ShapeMode> modes = ShapeMode.inCategory(category);
        hover = keyboardIndex < 0 ? null : modes.get(Math.floorMod(keyboardIndex, modes.size()));
        double radiusX = Math.min(120, width * .27);
        double radiusY = radiusY();
        for (int i = 0; i < modes.size(); i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / modes.size();
            int x = (int) (cx + Math.cos(angle) * radiusX - CELL_W / 2.0);
            int y = (int) (cy + Math.sin(angle) * radiusY - CELL_H / 2.0);
            ShapeMode mode = modes.get(i);
            boolean over = keyboardIndex >= 0 ? mode == hover
                    : mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
            if (over && keyboardIndex < 0) {
                hover = mode;
            }
            int border = over ? 0xff72d5c3 : mode == current ? 0xffdfb65e : 0xff74634e;
            g.fill(x - 1, y - 1, x + CELL_W + 1, y + CELL_H + 1, border);
            g.fill(x, y, x + CELL_W, y + CELL_H, over ? 0xff314b4e : 0xff25343d);
            g.blit(icon(mode), x + CELL_W / 2 - 8, y + 4, 0, 0, 16, 16, 16, 16);
            g.drawCenteredString(font, UiTheme.fit(font, Component.translatable(mode.key()).getString(), CELL_W - 6), x + CELL_W / 2, y + 25,
                    over ? 0xffcaffed : 0xffe0d7c1);
        }
    }

    private void drawCentre(GuiGraphics g, int cx, int cy) {
        ShapeMode shown = hover != null ? hover : current;
        int footerY = (int) Math.round(cy + radiusY() + CELL_H / 2.0 + 5);
        g.blit(icon(shown), cx - 12, cy - 12, 24, 24, 0, 0, 16, 16, 16, 16);
        g.drawCenteredString(font, Component.translatable(shown.key()), cx, footerY, UiTheme.ACCENT);
        var lines = font.split(Component.translatable(shown.descriptionKey()), width - 32);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            var line = lines.get(i);
            g.drawString(font, line, cx - font.width(line) / 2, footerY + 12 + i * 10, UiTheme.MUTED, false);
        }
        g.drawCenteredString(font, Component.translatable(clickMode ? "descentmtb.shape.keyboard_hint" : "descentmtb.shape.release_hint",
                ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage()), cx, footerY + 35, UiTheme.MUTED);
    }

    private void choose(ShapeMode mode) {
        if (minecraft.player == null || !(minecraft.player.getMainHandItem().getItem() instanceof ShapeToolItem)) {
            onClose();
            return;
        }
        PacketDistributor.sendToServer(new ShapeTunePayload(mode));
        ShapeToolItem.mode(minecraft.player.getMainHandItem(), mode);
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) {
            category = Math.floorMod(category + (Screen.hasShiftDown() ? -1 : 1), ShapeMode.CATEGORIES);
            keyboardIndex = -1;
            hover = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN) {
            var modes = ShapeMode.inCategory(category);
            int index = keyboardIndex < 0 ? Math.max(0, modes.indexOf(current)) : keyboardIndex;
            keyboardIndex = Math.floorMod(index + (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1 : 1), modes.size());
            hover = modes.get(keyboardIndex);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            choose(hover != null ? hover : current.category == category ? current : ShapeMode.inCategory(category).getFirst());
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!clickMode && ModKeyMappings.TRAIL_MENU.matchesMouse(button)) {
            if (hover != null) choose(hover); else onClose();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (!clickMode && ModKeyMappings.TRAIL_MENU.matches(key, scan)) {
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
        if (button == 0 && my >= TAB_Y && my < TAB_Y + TAB_HEIGHT && mx >= left && mx < left + ShapeMode.CATEGORIES * tabWidth()) {
            category = (int) ((mx - left) / tabWidth());
            keyboardIndex = -1;
            hover = null;
            return true;
        }
        if (button == 0 && hover != null) {
            choose(hover);
            return true;
        }
        if (clickMode && button == 0) {
            onClose();   // a click on empty space dismisses the menu
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
