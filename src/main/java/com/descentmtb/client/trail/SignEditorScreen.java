package com.descentmtb.client.trail;

import com.descentmtb.network.SignContentPayload;
import com.descentmtb.trail.SignArt;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.SignLayout;
import com.descentmtb.trail.TrailSignEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Sign editor: five type tabs on top, a live preview of the board on the left and the fields of the chosen
 * type on the right (name, difficulty, arrow, warning icon, or the pixel canvas for custom signs).
 * Save sends everything in one packet.
 */
public final class SignEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int TAB_TOP = 22;
    private static final int CONTENT_TOP = 58;
    private static final int LABEL_COLOUR = 0xffa9b4b8;
    private static final int GOLD = 0xffe2c48a;

    /** Pixel canvas copied with the Copy button; survives closing the editor. */
    private static byte[] clipboard;

    private final BlockPos pos;
    private final SignArt art;
    private SignContent.Type type;
    private String name;
    private SignContent.Difficulty difficulty;
    private SignContent.Arrow arrow;
    private SignContent.Warning warning;
    private String text;

    // pixel editor state
    private int colour = 1, tool = 0, lastX, lastY;

    // layout computed in init()
    private int left, previewWidth, rightX, rightWidth, cell;

    public SignEditorScreen(TrailSignEntity sign) {
        super(Component.translatable("descentmtb.sign.editor"));
        SignContent content = sign.content();
        this.pos = sign.getBlockPos();
        this.art = new SignArt(content.pixels());
        this.type = content.type();
        this.name = content.name();
        this.difficulty = content.difficulty();
        this.arrow = content.arrow();
        this.warning = content.warning();
        this.text = content.text();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- layout ----

    @Override
    protected void init() {
        int panel = Math.min(width - 16, PANEL_WIDTH);
        left = (width - panel) / 2;
        previewWidth = panel * 45 / 100;
        rightX = left + previewWidth + 16;
        rightWidth = left + panel - rightX;
        cell = Math.max(5, Math.min(8, (height - CONTENT_TOP - 80) / 16));

        addTabs(panel);
        switch (type) {
            case TRAIL -> {
                addNameField(CONTENT_TOP + 12);
                addDifficultyRow(CONTENT_TOP + 52);
                addArrowRow(CONTENT_TOP + 104);
            }
            case START -> {
                addNameField(CONTENT_TOP + 12);
                addDifficultyRow(CONTENT_TOP + 52);
            }
            case FINISH -> addNameField(CONTENT_TOP + 12);
            case WARNING -> {
                addWarningRow(CONTENT_TOP + 12);
                addTextField(CONTENT_TOP + 64);
            }
            case CUSTOM -> addPixelTools();
        }
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.sign.cancel"), b -> onClose())
                .bounds(width / 2 - 116, height - 28, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.sign.save"), b -> save())
                .bounds(width / 2 + 6, height - 28, 110, 20).build());
    }

    private void addTabs(int panel) {
        int gap = 2;
        int tabWidth = (panel - 4 * gap) / 5;
        for (SignContent.Type t : SignContent.Type.values()) {
            Component label = Component.translatable("descentmtb.sign.type." + t.name().toLowerCase(Locale.ROOT));
            if (t == type) {
                label = label.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
            addRenderableWidget(Button.builder(label, b -> {
                type = t;
                rebuildWidgets();
            }).bounds(left + t.ordinal() * (tabWidth + gap), TAB_TOP, tabWidth, 20).build());
        }
    }

    private void addNameField(int y) {
        EditBox box = new EditBox(font, rightX, y, rightWidth, 18, Component.translatable("descentmtb.sign.field.name"));
        box.setMaxLength(SignContent.NAME_MAX);
        box.setValue(name);
        box.setResponder(value -> name = value);
        addRenderableWidget(box);
        setInitialFocus(box);
    }

    private void addTextField(int y) {
        EditBox box = new EditBox(font, rightX, y, rightWidth, 18, Component.translatable("descentmtb.sign.field.text"));
        box.setMaxLength(SignContent.TEXT_MAX);
        box.setValue(text);
        box.setResponder(value -> text = value);
        addRenderableWidget(box);
    }

    private void addDifficultyRow(int y) {
        int size = 34, gap = 4;
        for (SignContent.Difficulty d : SignContent.Difficulty.values()) {
            addRenderableWidget(new IconButton(rightX + d.ordinal() * (size + gap), y, size, size, d.icon(), null,
                    Component.translatable("descentmtb.sign.difficulty." + d.name().toLowerCase(Locale.ROOT)),
                    () -> difficulty == d, () -> difficulty = d));
        }
    }

    private void addArrowRow(int y) {
        int size = 28, gap = 4;
        for (SignContent.Arrow a : SignContent.Arrow.values()) {
            addRenderableWidget(new IconButton(rightX + a.ordinal() * (size + gap), y, size, size, a.icon(), "-",
                    Component.translatable("descentmtb.sign.arrow." + a.name().toLowerCase(Locale.ROOT)),
                    () -> arrow == a, () -> arrow = a));
        }
    }

    private void addWarningRow(int y) {
        int size = 30, gap = 4;
        for (SignContent.Warning w : SignContent.Warning.values()) {
            addRenderableWidget(new IconButton(rightX + w.ordinal() * (size + gap), y, size, size, w.icon(), null,
                    Component.translatable("descentmtb.sign.warning." + w.name().toLowerCase(Locale.ROOT)),
                    () -> warning == w, () -> warning = w));
        }
    }

    /** Draw / erase / fill, undo / copy / paste and the templates, in two columns next to the canvas. */
    private void addPixelTools() {
        int toolsX = rightX + 16 * cell + 8;
        int columnWidth = (rightX + rightWidth - toolsX - 2) / 2;
        int row = CONTENT_TOP + 12;
        for (int i = 0; i < 3; i++) {
            int n = i;
            Component label = Component.translatable("descentmtb.sign.tool." + i);
            if (tool == i) {
                label = label.copy().withStyle(ChatFormatting.GOLD);
            }
            addToolButton(label, toolsX + (i % 2) * (columnWidth + 2), row + (i / 2) * 20, columnWidth, () -> {
                tool = n;
                rebuildWidgets();
            });
        }
        addToolButton(Component.translatable("descentmtb.sign.undo"), toolsX + columnWidth + 2, row + 20, columnWidth, art::undo);
        row += 44;
        addToolButton(Component.translatable("descentmtb.sign.copy"), toolsX, row, columnWidth, () -> clipboard = art.pixels());
        addToolButton(Component.translatable("descentmtb.sign.paste"), toolsX + columnWidth + 2, row, columnWidth, () -> {
            if (clipboard != null) {
                art.replace(clipboard);
            }
        });
        row += 24;
        for (int i = 0; i < 5; i++) {
            int n = i;
            addToolButton(Component.translatable("descentmtb.sign.template." + i), toolsX + (i % 2) * (columnWidth + 2),
                    row + (i / 2) * 20, columnWidth, () -> art.template(n));
        }
    }

    private void addToolButton(Component label, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, width, 18).build());
    }

    private SignContent content() {
        return new SignContent(type, name, difficulty, arrow, warning, text, art.pixels());
    }

    private void save() {
        PacketDistributor.sendToServer(new SignContentPayload(pos, content()));
        onClose();
    }

    // ---- drawing ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xd018252d);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, GOLD);

        double scale = previewWidth / SignLayout.WIDTH;
        SignPreview.draw(g, font, content(), left, CONTENT_TOP + 12, scale);
        g.drawString(font, Component.translatable("descentmtb.sign.preview"), left, CONTENT_TOP, LABEL_COLOUR, false);
        int previewBottom = CONTENT_TOP + 12 + (int) Math.round(SignLayout.HEIGHT * scale);
        g.drawWordWrap(font, Component.translatable("descentmtb.sign.hint." + type.name().toLowerCase(Locale.ROOT)),
                left, previewBottom + 8, previewWidth, LABEL_COLOUR);

        switch (type) {
            case TRAIL -> {
                label(g, "descentmtb.sign.field.name", CONTENT_TOP);
                label(g, "descentmtb.sign.field.difficulty", CONTENT_TOP + 40);
                label(g, "descentmtb.sign.field.arrow", CONTENT_TOP + 92);
            }
            case START -> {
                label(g, "descentmtb.sign.field.name", CONTENT_TOP);
                label(g, "descentmtb.sign.field.difficulty", CONTENT_TOP + 40);
            }
            case FINISH -> label(g, "descentmtb.sign.field.name", CONTENT_TOP);
            case WARNING -> {
                label(g, "descentmtb.sign.field.warning", CONTENT_TOP);
                label(g, "descentmtb.sign.field.text", CONTENT_TOP + 52);
            }
            case CUSTOM -> renderCanvas(g);
        }
    }

    private void label(GuiGraphics g, String key, int y) {
        g.drawString(font, Component.translatable(key), rightX, y, LABEL_COLOUR, false);
    }

    private void renderCanvas(GuiGraphics g) {
        int top = CONTENT_TOP + 12;
        g.fill(rightX - 3, top - 3, rightX + 16 * cell + 3, top + 16 * cell + 3, 0xffa98c58);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                g.fill(rightX + x * cell, top + y * cell, rightX + (x + 1) * cell - 1, top + (y + 1) * cell - 1, art.colour(x, y));
            }
        }
        int step = paletteStep();
        for (int i = 0; i < 16; i++) {
            int x = paletteX() + (i % 8) * step, y = paletteY() + (i / 8) * step;
            g.fill(x - 1, y - 1, x + step - 1, y + step - 1, i == colour ? 0xffe8d398 : 0xff59656a);
            g.fill(x, y, x + step - 2, y + step - 2, SignArt.PALETTE[i]);
        }
    }

    private int paletteStep() {
        return cell * 2;
    }

    private int paletteX() {
        return rightX;
    }

    private int paletteY() {
        return CONTENT_TOP + 12 + 16 * cell + 8;
    }

    // ---- input ----

    private boolean overCanvas(double x, double y) {
        int top = CONTENT_TOP + 12;
        return x >= rightX && y >= top && x < rightX + 16 * cell && y < top + 16 * cell;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (type == SignContent.Type.CUSTOM && button == 0) {
            int step = paletteStep();
            if (x >= paletteX() && x < paletteX() + 8 * step && y >= paletteY() && y < paletteY() + 2 * step) {
                colour = (int) ((y - paletteY()) / step) * 8 + (int) ((x - paletteX()) / step);
                return true;
            }
            if (overCanvas(x, y)) {
                int cx = (int) (x - rightX) / cell, cy = (int) (y - CONTENT_TOP - 12) / cell;
                if (tool == 2) {
                    art.fill(cx, cy, colour);
                } else {
                    art.beginStroke();
                    art.paint(cx, cy, tool == 1 ? 0 : colour);
                    lastX = cx;
                    lastY = cy;
                }
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (type == SignContent.Type.CUSTOM && button == 0 && tool != 2 && overCanvas(x, y)) {
            int cx = (int) (x - rightX) / cell, cy = (int) (y - CONTENT_TOP - 12) / cell;
            int steps = Math.max(Math.abs(cx - lastX), Math.abs(cy - lastY));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) Math.max(1, steps);
                art.paint((int) Math.round(lastX + (cx - lastX) * t), (int) Math.round(lastY + (cy - lastY) * t),
                        tool == 1 ? 0 : colour);
            }
            lastX = cx;
            lastY = cy;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** A square button showing a sign icon (or a short text when it has none), highlighted when selected. */
    private static final class IconButton extends Button {
        private final String icon;
        private final String fallbackText;
        private final BooleanSupplier selected;

        IconButton(int x, int y, int width, int height, String icon, String fallbackText, Component tooltip,
                   BooleanSupplier selected, Runnable action) {
            super(x, y, width, height, tooltip, b -> action.run(), DEFAULT_NARRATION);
            this.icon = icon;
            this.fallbackText = fallbackText;
            this.selected = selected;
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int border = selected.getAsBoolean() ? 0xffe8d398 : isHoveredOrFocused() ? 0xffffffff : 0xff59656a;
            g.fill(getX(), getY(), getX() + width, getY() + height, border);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, 0xff2a353b);
            if (icon != null) {
                ResourceLocation texture = TrailSignRenderer.icon(icon);
                int size = Math.min(width, height) - 6;
                g.blit(texture, getX() + (width - size) / 2, getY() + (height - size) / 2, size, size, 0, 0, 16, 16, 16, 16);
            } else if (fallbackText != null) {
                g.drawCenteredString(Minecraft.getInstance().font, fallbackText, getX() + width / 2, getY() + (height - 8) / 2, 0xffe0e0e0);
            }
        }
    }
}
