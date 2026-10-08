package com.descentmtb.client.trail;

import com.descentmtb.network.JumpBuildPayload;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.JumpProfiles.Params;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import com.descentmtb.client.ui.DescentScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The jump screen of the Trail Shaper's jump builder: the kind of jump and its numbers on the left, a live side profile
 * over a block grid on the right, saved profiles and the Build button at the bottom. Opened by a right-click on a block
 * with {@link com.descentmtb.trail.ShapeMode#JUMP_BUILD} (the jump starts at that block and runs the way the player
 * faces) or by Shift + right-click in the air (no block: the settings can be changed and saved, but not built).
 * Closing the screen keeps the settings in the tool, so the in-world preview and the next opening use them.
 */
public final class JumpProfileScreen extends DescentScreen {
    private static final int GOLD = 0xffe2c48a, LABEL = 0xffa9b4b8, VALUE = 0xffeee6d2, DIM = 0xff5f6a6e;
    private static final int ROW = 19, TOP = 26, CONTROLS_WIDTH = 176;

    private final BlockPos pos;
    private final Direction facing;
    private Params params;
    private List<JumpProfileStore.Profile> profiles = List.of();
    private int selected;
    private String typedName = "";
    private boolean sent;
    /** The steepest slope of {@link #slopeOf}, worked out once per change of the settings. */
    private Params slopeOf;
    private double maxSlope;

    /** The rows of numbers, rebuilt with the widgets. */
    private final List<Row> rows = new ArrayList<>();
    private int left, panelWidth, previewX, previewY, previewWidth, previewHeight;
    private Button loadButton, deleteButton, saveButton, buildButton;

    /** One line of the controls: a label, the value and the minus / plus buttons. */
    private record Row(int y, Component label, java.util.function.Supplier<String> value, boolean active) {}

    /**
     * @param pos    the clicked block, or null when the screen was opened in the air
     * @param facing the way the jump runs (the player's horizontal facing)
     */
    public JumpProfileScreen(BlockPos pos, Direction facing, Params params) {
        super(Component.translatable("descentmtb.jump.title"));
        this.pos = pos;
        this.facing = facing;
        this.params = params.clamped();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        profiles = JumpProfileStore.load();
        selected = Math.max(0, Math.min(profiles.size() - 1, selected));
        panelWidth = Math.min(width - 16, 440);
        left = (width - panelWidth) / 2;
        rows.clear();

        int y = TOP;
        addRow(y, "descentmtb.jump.field.type", () -> Component.translatable(params.type().key()).getString(), true,
                step -> params = params.withType(params.type().cycled(step)), () -> 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.length", () -> blocks(params.length()), true,
                step -> params = params.withLength(params.length() + step), () -> Screen.hasShiftDown() ? 3 : 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.width", () -> blocks(params.width()), true,
                step -> params = params.withWidth(params.width() + step), () -> Screen.hasShiftDown() ? 2 : 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.height", () -> metres(params.height()), true,
                step -> params = params.withHeight(params.height() + step * JumpProfiles.HEIGHT_STEP), () -> Screen.hasShiftDown() ? 4 : 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.lip", () -> params.lip() + "°", params.type().hasLip(),
                step -> params = params.withLip(params.lip() + step), () -> Screen.hasShiftDown() ? 5 : 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.deck", () -> blocks(params.deck()), params.type().hasDeck(),
                step -> params = params.withDeck(params.deck() + step), () -> Screen.hasShiftDown() ? 3 : 1);
        y += ROW;
        addRow(y, "descentmtb.jump.field.landing", () -> blocks(params.landing()), params.type().hasLanding(),
                step -> params = params.withLanding(params.landing() + step), () -> Screen.hasShiftDown() ? 3 : 1);
        y += ROW;

        previewX = left + CONTROLS_WIDTH + 10;
        previewY = TOP - 2;
        previewWidth = left + panelWidth - previewX;
        previewHeight = y - previewY - 4;

        // saved profiles
        int profileY = Math.max(y + 6, height - 3 * 22 - 6);
        int x = left;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> pick(-1)).bounds(x, profileY, 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> pick(1)).bounds(x + 160, profileY, 16, 18).build());
        int buttonWidth = Math.max(40, Math.min(70, (panelWidth - 190) / 2));
        loadButton = addRenderableWidget(Button.builder(Component.translatable("descentmtb.jump.load"), b -> load())
                .bounds(x + 182, profileY, buttonWidth, 18).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("descentmtb.jump.delete"), b -> delete())
                .bounds(x + 186 + buttonWidth, profileY, buttonWidth, 18).build());

        EditBox name = new EditBox(font, x + 1, profileY + 23, 174, 16, Component.translatable("descentmtb.jump.name"));
        name.setMaxLength(32);
        name.setHint(Component.translatable("descentmtb.jump.name_hint"));
        name.setValue(typedName);
        name.setResponder(value -> {
            typedName = value;
            refreshButtons();
        });
        addRenderableWidget(name);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("descentmtb.jump.save"), b -> save())
                .bounds(x + 182, profileY + 22, buttonWidth, 18).build());

        int buttonsY = profileY + 46;
        buildButton = addRenderableWidget(Button.builder(Component.translatable("descentmtb.jump.build"), b -> build())
                .bounds(width / 2 - 116, buttonsY, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.jump.close"), b -> onClose())
                .bounds(width / 2 + 6, buttonsY, 110, 20).build());
        refreshButtons();
    }

    /** A row with minus / plus buttons; {@code amount} is read at the click (Shift makes bigger steps). */
    private void addRow(int y, String key, java.util.function.Supplier<String> value, boolean active, Consumer<Integer> change,
                        IntSupplier amount) {
        rows.add(new Row(y, Component.translatable(key), value, active));
        Button minus = Button.builder(Component.literal("-"), b -> adjust(change, -amount.getAsInt()))
                .bounds(left + 92, y, 16, 16).build();
        Button plus = Button.builder(Component.literal("+"), b -> adjust(change, amount.getAsInt()))
                .bounds(left + CONTROLS_WIDTH - 16, y, 16, 16).build();
        minus.active = plus.active = active;
        addRenderableWidget(minus);
        addRenderableWidget(plus);
    }

    private void adjust(Consumer<Integer> change, int step) {
        JumpProfiles.Type before = params.type();
        change.accept(step);
        params = params.clamped();
        if (params.type() != before) {
            rebuildWidgets();   // other rows apply to the new kind of jump
        }
    }

    private void refreshButtons() {
        if (profiles.isEmpty()) {
            return;
        }
        deleteButton.active = !profiles.get(selected).builtIn();
        saveButton.active = !typedName.isBlank();
        buildButton.active = pos != null;
    }

    private void pick(int step) {
        selected = Math.floorMod(selected + step, profiles.size());
        refreshButtons();
    }

    private void load() {
        params = profiles.get(selected).params().clamped();
        rebuildWidgets();
    }

    private void delete() {
        JumpProfileStore.Profile profile = profiles.get(selected);
        if (!profile.builtIn()) {
            JumpProfileStore.delete(profile.name());
            rebuildWidgets();
        }
    }

    private void save() {
        String name = typedName.trim();
        if (name.isEmpty()) {
            return;
        }
        JumpProfileStore.save(name, params);
        profiles = JumpProfileStore.load();
        for (int i = 0; i < profiles.size(); i++) {
            if (name.equals(profiles.get(i).name())) {
                selected = i;
            }
        }
        typedName = "";
        rebuildWidgets();
    }

    private void build() {
        if (pos == null) {
            return;
        }
        remember();
        PacketDistributor.sendToServer(new JumpBuildPayload(true, pos, facing, params));
        sent = true;
        onClose();
    }

    /** Keeps the settings in the tool on this side too, so the preview and the HUD follow at once. */
    private void remember() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            JumpBuilder.store(player.getMainHandItem(), params);
        }
    }

    @Override
    public void onClose() {
        if (!sent) {
            remember();
            PacketDistributor.sendToServer(JumpBuildPayload.remember(params));
            sent = true;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof EditBox) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 6, GOLD);
        for (Row row : rows) {
            g.drawString(font, row.label(), left, row.y() + 4, row.active() ? LABEL : DIM);
            int centre = (left + 108 + left + CONTROLS_WIDTH - 16) / 2;
            g.drawCenteredString(font, row.active() ? row.value().get() : "-", centre, row.y() + 4, row.active() ? VALUE : DIM);
        }
        drawProfile(g);

        if (!profiles.isEmpty()) {
            int profileY = loadButton.getY();
            JumpProfileStore.Profile profile = profiles.get(selected);
            Component label = profile.label();
            g.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), 136), left + 88, profileY + 5,
                    profile.builtIn() ? GOLD : VALUE);
        }
        if (pos == null) {
            g.drawCenteredString(font, Component.translatable("descentmtb.jump.no_target"), width / 2, 16, LABEL);
        }
    }

    /** The side profile of the jump over a block grid, with its numbers. */
    private void drawProfile(GuiGraphics g) {
        int x0 = previewX, y0 = previewY, w = previewWidth, h = previewHeight;
        g.fill(x0, y0, x0 + w, y0 + h, 0xff1d2a31);
        int textLines = 2;
        int ground = y0 + h - 6 - textLines * 10;
        int total = params.total();
        double top = Math.max(params.height(), 1) + .4;
        double scale = Math.min((w - 8) / (total + 2.0), (ground - y0 - 6) / top);
        int startX = x0 + 4 + (int) Math.round(scale);   // one block of run-in on the left

        // grid: one line per block along and per block of height
        for (int i = -1; i <= total + 1; i++) {
            int gx = startX + (int) Math.round(i * scale);
            if (gx >= x0 && gx < x0 + w) {
                g.fill(gx, y0 + 2, gx + 1, ground, 0xff2c3d45);
            }
        }
        for (int k = 1; k <= (int) Math.floor(top); k++) {
            int gy = ground - (int) Math.round(k * scale);
            g.fill(x0 + 2, gy, x0 + w - 2, gy + 1, 0xff2c3d45);
        }

        // the profile, filled like dirt with a grass edge
        int endX = startX + (int) Math.round(total * scale);
        for (int px = x0 + 2; px < x0 + w - 2; px++) {
            double u = (px + .5 - startX) / scale;
            double height = u < 0 ? 0 : u > total ? 0 : params.heightAt(u);
            int surface = ground - (int) Math.round(height * scale);
            g.fill(px, surface, px + 1, ground + 1, px >= startX && px < endX ? 0xff8a6239 : 0xff5c4a37);
            g.fill(px, surface - 1, px + 1, surface + 1, px >= startX && px < endX ? 0xff7fb24b : 0xff4f6e3a);
        }
        // block numbers under the ground line
        for (int i = 0; i < total; i++) {
            int cx = startX + (int) Math.round((i + .5) * scale);
            if (scale >= 9 || i % 2 == 0) {
                g.drawCenteredString(font, String.valueOf(i + 1), cx, ground + 3, LABEL);
            }
        }

        String lip = params.type().hasLip() || params.type() == JumpProfiles.Type.RAMP
                ? Math.round(params.lipAngle()) + "°" : "-";
        Component first = Component.translatable("descentmtb.jump.preview.size", total, metres(params.height()), params.width());
        if (!params.equals(slopeOf)) {
            slopeOf = params;
            maxSlope = params.maxSlope();
        }
        Component second = Component.translatable("descentmtb.jump.preview.angles", lip, Math.round(maxSlope) + "°");
        g.drawString(font, first, x0 + 4, y0 + h - 22, VALUE);
        g.drawString(font, second, x0 + 4, y0 + h - 11, VALUE);
    }

    private static String blocks(int count) {
        return count + " m";
    }

    private static String metres(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + " m";
    }
}
