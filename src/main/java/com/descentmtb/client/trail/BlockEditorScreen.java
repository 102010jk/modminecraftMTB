package com.descentmtb.client.trail;

import com.descentmtb.network.BlockEditPayload;
import com.descentmtb.ramp.RampBlock;
import com.descentmtb.trail.CornerEdits;
import com.descentmtb.trail.ShapePresets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.function.UnaryOperator;

/**
 * The block editor of the Trail Shaper (Ctrl + right-click on a block with any mode): the four corner heights of that
 * ONE block in sixteenths above its base, with minus / plus buttons (Shift: four at a time) and number fields laid out
 * like the corners seen from above (north up), a top view and a side view of the shape, and buttons to flatten,
 * reset, turn, mirror, copy and paste the shape, switch the wooden deck and take the material from the off-hand.
 * Apply sends it to the server ({@link BlockEditPayload}), which rebuilds only that column as one undoable edit.
 */
public final class BlockEditorScreen extends Screen {
    private static final int GOLD = 0xffe2c48a, LABEL = 0xffa9b4b8, VALUE = 0xffeee6d2;
    /** Corner names in the order NW NE SW SE (language keys). */
    private static final String[] CORNERS = {"nw", "ne", "sw", "se"};

    /** The shape copied with Copy; it survives closing the editor (this game session only). */
    private static int[] clipboard;
    private static boolean clipboardDeck;

    private final BlockPos pos;
    private final int[] heights;
    private boolean deck;
    private boolean offHandMaterial;
    private final EditBox[] boxes = new EditBox[4];
    private Button pasteButton, deckButton, materialButton;
    private int left, panelWidth, viewX, viewY, viewSize;
    /** Set while the boxes are filled from {@link #heights}, so their responders do not write back. */
    private boolean filling;

    public BlockEditorScreen(BlockPos pos, ShapePresets.Editable editable) {
        super(Component.translatable("descentmtb.editor.title"));
        this.pos = pos.immutable();
        this.heights = CornerEdits.toSixteenths(editable.editorLocal());
        this.deck = editable.column().deck();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 16, 360);
        left = (width - panelWidth) / 2;
        int gridTop = 34;
        // the corners as they lie seen from above: north-west top left ... south-east bottom right
        for (int corner = 0; corner < 4; corner++) {
            int x = left + (corner % 2) * 104;
            int y = gridTop + (corner / 2) * 46;
            int index = corner;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> nudge(index, -1)).bounds(x, y + 12, 18, 18).build());
            EditBox box = new EditBox(font, x + 21, y + 13, 36, 16, Component.translatable("descentmtb.editor.corner." + CORNERS[corner]));
            box.setMaxLength(3);
            box.setFilter(text -> text.matches("\\d{0,3}"));
            box.setResponder(text -> {
                if (!filling && !text.isEmpty()) {
                    heights[index] = CornerEdits.clampSixteenths(Integer.parseInt(text));
                }
            });
            boxes[corner] = addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("+"), b -> nudge(index, 1)).bounds(x + 60, y + 12, 18, 18).build());
        }
        viewSize = Math.max(36, Math.min(56, (panelWidth - 196) / 2 - 4));
        viewX = left + panelWidth - 2 * viewSize - 10;
        viewY = gridTop + 4;

        int rowY = gridTop + 98;
        int w = (panelWidth - 12) / 4;
        button(Component.translatable("descentmtb.editor.flatten"), 0, rowY, w, () -> edit(CornerEdits.toSixteenths(
                com.descentmtb.trail.BlockShapes.flatten(CornerEdits.fromSixteenths(heights)))));
        button(Component.translatable("descentmtb.editor.reset"), 1, rowY, w, () -> edit(new int[]{16, 16, 16, 16}));
        button(Component.translatable("descentmtb.editor.rotate"), 2, rowY, w, () -> transform(CornerEdits::rotateClockwise));
        button(Component.translatable("descentmtb.editor.mirror_x"), 3, rowY, w, () -> transform(CornerEdits::mirrorX));
        rowY += 22;
        button(Component.translatable("descentmtb.editor.mirror_z"), 0, rowY, w, () -> transform(CornerEdits::mirrorZ));
        button(Component.translatable("descentmtb.editor.copy"), 1, rowY, w, () -> {
            clipboard = heights.clone();
            clipboardDeck = deck;
            pasteButton.active = true;
        });
        pasteButton = button(Component.translatable("descentmtb.editor.paste"), 2, rowY, w, () -> {
            if (clipboard != null) {
                deck = clipboardDeck;
                edit(clipboard.clone());
            }
        });
        pasteButton.active = clipboard != null;
        deckButton = button(Component.empty(), 3, rowY, w, () -> deck = !deck);
        rowY += 22;
        materialButton = addRenderableWidget(Button.builder(Component.empty(), b -> offHandMaterial = !offHandMaterial)
                .bounds(left, rowY, panelWidth - 4, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("descentmtb.editor.apply"), b -> apply())
                .bounds(width / 2 - 116, height - 26, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.editor.cancel"), b -> onClose())
                .bounds(width / 2 + 6, height - 26, 110, 20).build());
        fill();
    }

    private Button button(Component label, int column, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(label, b -> action.run()).bounds(left + column * (width + 4), y, width, 20).build());
    }

    /** One corner up or down by one sixteenth, four with Shift. */
    private void nudge(int corner, int direction) {
        int[] next = heights.clone();
        next[corner] = CornerEdits.clampSixteenths(next[corner] + direction * (Screen.hasShiftDown() ? 4 : 1));
        edit(next);
    }

    private void transform(UnaryOperator<double[]> operation) {
        edit(CornerEdits.toSixteenths(operation.apply(CornerEdits.fromSixteenths(heights))));
    }

    private void edit(int[] next) {
        for (int i = 0; i < 4; i++) {
            heights[i] = CornerEdits.clampSixteenths(next[i]);
        }
        fill();
    }

    /** Writes the heights into the boxes. */
    private void fill() {
        filling = true;
        for (int i = 0; i < 4; i++) {
            if (boxes[i] != null) {
                boxes[i].setValue(String.valueOf(heights[i]));
            }
        }
        filling = false;
    }

    private void apply() {
        PacketDistributor.sendToServer(new BlockEditPayload(pos, heights.clone(), deck, offHandMaterial));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- drawing ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xd018252d);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        deckButton.setMessage(Component.translatable(deck ? "descentmtb.editor.deck_on" : "descentmtb.editor.deck_off"));
        materialButton.setMessage(materialLabel());
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 6, GOLD);
        g.drawCenteredString(font, Component.translatable("descentmtb.editor.hint", pos.getX(), pos.getY(), pos.getZ()), width / 2, 18, LABEL);
        for (int corner = 0; corner < 4; corner++) {
            int x = left + (corner % 2) * 104;
            int y = 34 + (corner / 2) * 46;
            g.drawString(font, Component.translatable("descentmtb.editor.corner." + CORNERS[corner]), x, y + 1, LABEL);
            g.drawString(font, blocks(heights[corner]), x + 22, y + 32, VALUE);
        }
        drawTopView(g, viewX, viewY, viewSize);
        drawSideView(g, viewX + viewSize + 8, viewY, viewSize);
    }

    private Component materialLabel() {
        ItemStack held = Minecraft.getInstance().player.getOffhandItem();
        boolean usable = held.getItem() instanceof BlockItem item
                && RampBlock.isValidMaterial(item.getBlock().defaultBlockState(), Minecraft.getInstance().level, pos);
        if (!offHandMaterial) {
            return Component.translatable("descentmtb.editor.material_off");
        }
        return usable ? Component.translatable("descentmtb.editor.material_on", held.getHoverName())
                : Component.translatable("descentmtb.editor.material_none");
    }

    /** Height (sixteenths) of the shape at (fx, fz) inside the block, bilinear like the surface. */
    private double heightAt(double fx, double fz) {
        return (heights[0] * (1 - fx) + heights[1] * fx) * (1 - fz) + (heights[2] * (1 - fx) + heights[3] * fx) * fz;
    }

    /** Seen from above, north up: lighter is higher. */
    private void drawTopView(GuiGraphics g, int x, int y, int size) {
        int low = Math.min(Math.min(heights[0], heights[1]), Math.min(heights[2], heights[3]));
        int high = Math.max(Math.max(heights[0], heights[1]), Math.max(heights[2], heights[3]));
        int cells = 14, cell = Math.max(2, size / cells);
        g.fill(x - 1, y - 1, x + cells * cell + 1, y + cells * cell + 1, 0xff0f171b);
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                double h = heightAt((i + .5) / cells, (j + .5) / cells);
                double t = high == low ? .5 : (h - low) / (high - low);
                int shade = (int) (70 + 120 * t);
                int colour = 0xff000000 | (shade << 16) | ((int) (shade * .78) << 8) | (int) (shade * .5);
                g.fill(x + i * cell, y + j * cell, x + (i + 1) * cell, y + (j + 1) * cell, colour);
            }
        }
        g.drawCenteredString(font, "N", x + cells * cell / 2, y - 10, LABEL);
    }

    /** Seen from the south (west on the left): the highest point of each slice, on a grid of 1/4 blocks. */
    private void drawSideView(GuiGraphics g, int x, int y, int size) {
        int scale = size / 3;   // pixels per block: the view holds the highest shape (3 blocks)
        int ground = y + size;
        g.fill(x - 1, y - 1, x + size + 1, ground + 1, 0xff0f171b);
        for (int k = 1; k < 12; k++) {
            int gy = ground - k * scale / 4;
            g.fill(x, gy, x + size, gy + 1, k % 4 == 0 ? 0xff3c4f58 : 0xff22323a);
        }
        for (int px = 0; px < size; px++) {
            double fx = (px + .5) / size;
            double h = Math.max(heightAt(fx, 0), heightAt(fx, 1)) / 16.0;
            int top = ground - (int) Math.round(h * scale);
            g.fill(x + px, top, x + px + 1, ground, deck ? 0xffa27c4a : 0xff8a6239);
            g.fill(x + px, top, x + px + 1, top + 1, deck ? 0xffc9a36a : 0xff7fb24b);
        }
    }

    private static String blocks(int sixteenths) {
        return String.format(java.util.Locale.ROOT, "%.3f", sixteenths / 16.0).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
