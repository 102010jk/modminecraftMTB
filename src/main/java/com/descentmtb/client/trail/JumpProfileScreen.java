package com.descentmtb.client.trail;

import com.descentmtb.network.JumpBuildPayload;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.JumpProfiles.Params;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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
import java.util.function.DoubleSupplier;
import java.util.function.DoubleConsumer;

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
    private final List<NumberInput> numbers = new ArrayList<>();
    private record NumberInput(EditBox box, DoubleSupplier value) {}
    private boolean refreshingNumbers;
    private int left, panelWidth, previewX, previewY, previewWidth, previewHeight;
    private Button loadButton, deleteButton, saveButton, buildButton;
    private EditBox nameField;

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
        numbers.clear();

        int y = TOP;
        addRow(y, "descentmtb.jump.field.type", () -> Component.translatable(params.type().key()).getString(), true,
                step -> params = params.withType(params.type().cycled(step)), () -> 1);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.length", true, () -> params.length(),
                value -> params = params.withLength((int) value), 1, () -> Screen.hasShiftDown() ? 3 : 1, false);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.width", true, () -> params.width(),
                value -> params = params.withWidth((int) value), 1, () -> Screen.hasShiftDown() ? 2 : 1, false);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.height", true, () -> params.height(),
                value -> params = params.withHeight(value), JumpProfiles.HEIGHT_STEP, () -> Screen.hasShiftDown() ? 4 : 1, true);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.lip", params.type().hasLip(), () -> params.lip(),
                value -> params = params.withLip((int) value), 1, () -> Screen.hasShiftDown() ? 5 : 1, false);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.deck", params.type().hasDeck(), () -> params.deck(),
                value -> params = params.withDeck((int) value), 1, () -> Screen.hasShiftDown() ? 3 : 1, false);
        y += ROW;
        addNumberRow(y, "descentmtb.jump.field.landing", params.type().hasLanding(), () -> params.landing(),
                value -> params = params.withLanding((int) value), 1, () -> Screen.hasShiftDown() ? 3 : 1, false);
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
        nameField = name;
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
        } else {
            syncNumbers();
        }
    }

    /** Direct entry makes large constructions practical without thousands of button presses. */
    private void addNumberRow(int y, String key, boolean active, DoubleSupplier value,
                              DoubleConsumer set, double step, IntSupplier amount, boolean decimal) {
        addRow(y, key, () -> "", active, direction -> set.accept(value.getAsDouble() + direction * step), amount);
        EditBox box = new EditBox(font, left + 110, y + 1, CONTROLS_WIDTH - 128, 14, Component.translatable(key));
        box.setMaxLength(12);
        box.setFilter(text -> text.matches(decimal ? "[0-9]*\\.?[0-9]*" : "[0-9]*"));
        box.setValue(number(value.getAsDouble()));
        box.setEditable(active);
        box.active = active;
        box.setTooltip(Tooltip.create(Component.translatable("descentmtb.jump.value_hint", Component.translatable(key),
                key.endsWith(".lip") ? "°" : "m")));
        numbers.add(new NumberInput(box, value));
        box.setResponder(text -> {
            if (refreshingNumbers) return;
            try {
                double typed = Double.parseDouble(text);
                if (!Double.isFinite(typed) || typed > Integer.MAX_VALUE) throw new NumberFormatException();
                set.accept(typed);
                params = params.clamped();
                box.setTextColor(VALUE);
            } catch (NumberFormatException ignored) {
                box.setTextColor(0xffff8b76);
            }
            refreshButtons();
        });
        addRenderableWidget(box);
    }

    private void syncNumbers() {
        refreshingNumbers = true;
        for (NumberInput input : numbers) {
            input.box().setValue(number(input.value().getAsDouble()));
            input.box().setTextColor(VALUE);
        }
        refreshingNumbers = false;
        refreshButtons();
    }

    private boolean validNumbers() {
        for (NumberInput input : numbers) {
            if (!input.box().active) continue;
            try {
                double typed = Double.parseDouble(input.box().getValue());
                if (!Double.isFinite(typed) || typed <= 0 || typed > Integer.MAX_VALUE) return false;
            } catch (NumberFormatException ignored) { return false; }
        }
        return true;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void refreshButtons() {
        if (profiles.isEmpty()) {
            return;
        }
        deleteButton.active = !profiles.get(selected).builtIn();
        saveButton.active = !typedName.isBlank() && validNumbers();
        buildButton.active = pos != null && validNumbers();
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
        if (!validNumbers()) return;
        syncNumbers();
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
        if (pos == null || !validNumbers()) {
            return;
        }
        syncNumbers();
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
            if (getFocused() == nameField) save();
            else if (validNumbers()) syncNumbers();
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
            String value = row.value().get();
            if (!value.isEmpty()) g.drawCenteredString(font, value, centre, row.y() + 4, row.active() ? VALUE : DIM);
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

        // Keep grid and labels readable for arbitrarily long profiles; rendering scales with the screen.
        int gridStep = Math.max(1, (int) Math.ceil(6 / scale));
        for (int i = -gridStep; i <= total + 1; i += gridStep) {
            int gx = startX + (int) Math.round(i * scale);
            if (gx >= x0 && gx < x0 + w) {
                g.fill(gx, y0 + 2, gx + 1, ground, 0xff2c3d45);
            }
        }
        for (int k = gridStep; k <= (int) Math.floor(top); k += gridStep) {
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
        int labelStep = Math.max(1, (int) Math.ceil(24 / scale));
        for (int i = 0; i < total; i += labelStep) {
            int cx = startX + (int) Math.round((i + .5) * scale);
            g.drawCenteredString(font, String.valueOf(i + 1), cx, ground + 3, LABEL);
        }

        String lip = params.type().hasLip() || params.type() == JumpProfiles.Type.RAMP
                ? Math.round(params.lipAngle()) + "°" : "-";
        Component first = Component.translatable("descentmtb.jump.preview.size", total, metres(params.height()), params.width());
        if (!params.equals(slopeOf)) {
            slopeOf = params;
            maxSlope = params.maxSlope();
        }
        Component second = Component.translatable("descentmtb.jump.preview.angles", lip, Math.round(maxSlope) + "°");
        g.drawString(font, com.descentmtb.client.ui.UiTheme.fit(font, first.getString(), w - 8), x0 + 4, y0 + h - 22, VALUE);
        g.drawString(font, com.descentmtb.client.ui.UiTheme.fit(font, second.getString(), w - 8), x0 + 4, y0 + h - 11, VALUE);
    }

    private static String blocks(int count) {
        return count + " m";
    }

    private static String metres(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + " m";
    }
}
