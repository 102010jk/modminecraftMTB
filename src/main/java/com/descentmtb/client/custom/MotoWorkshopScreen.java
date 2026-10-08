package com.descentmtb.client.custom;

import com.descentmtb.client.custom.WorkshopPanel.Chip;
import com.descentmtb.client.custom.WorkshopPanel.Chips;
import com.descentmtb.client.custom.WorkshopPanel.ColorPicker;
import com.descentmtb.client.custom.WorkshopPanel.Header;
import com.descentmtb.client.custom.WorkshopPanel.Item;
import com.descentmtb.client.custom.WorkshopPanel.Note;
import com.descentmtb.client.custom.WorkshopPanel.Swatch;
import com.descentmtb.client.custom.WorkshopPanel.Swatches;
import com.descentmtb.client.model.MotoModel;
import com.descentmtb.custom.BikeParts.Anodized;
import com.descentmtb.custom.MotoBuild;
import com.descentmtb.custom.MotoBuild.Exhaust;
import com.descentmtb.custom.MotoBuild.Suspension;
import com.descentmtb.custom.MotoTuning;
import com.descentmtb.custom.WorkshopNet;
import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeParams;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import com.descentmtb.client.ui.DescentScreen;
import com.descentmtb.client.ui.UiTheme;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The motorbike workshop: opened by a right-click on a stand that holds a dirt bike or a pit bike. A live 3D preview
 * of the painted bike on the left (drag to turn, wheel to zoom), the options on the right: a colour per paint group
 * (swatches, a picker and "stock"), the rear sprocket, the exhaust, the suspension and the race number.
 *
 * <p>Edits stay local until Apply ({@link WorkshopNet#applyMoto}); closing or taking the bike with unsaved changes asks
 * first, like the bicycle {@link WorkshopScreen}. Built from the same {@link WorkshopPanel} widgets.
 */
public final class MotoWorkshopScreen extends DescentScreen {
    private static final int GOLD = WorkshopPanel.GOLD, LABEL = WorkshopPanel.LABEL, VALUE = WorkshopPanel.VALUE,
            EDGE = WorkshopPanel.EDGE;
    private static final int MARGIN = 4, BAR_H = 20;
    private static final float DEFAULT_YAW = 65f, DEFAULT_PITCH = 12f;

    /** Swatches of the colour palette: the anodised colours of the bicycle workshop plus white and yellow. */
    private static final int[] PRESETS = {
            Anodized.BLACK.rgb, 0xF2F0EA, Anodized.SILVER.rgb, Anodized.GUNMETAL.rgb, Anodized.RED.rgb, Anodized.ORANGE.rgb,
            0xF2C418, Anodized.GOLD.rgb, Anodized.GREEN.rgb, Anodized.TURQUOISE.rgb, Anodized.BLUE.rgb, Anodized.PURPLE.rgb,
            Anodized.PINK.rgb, Anodized.BRONZE.rgb};

    private enum Pending { NONE, CLOSE, TAKE }

    private final BlockPos stand;
    private final BikeType type;
    private MotoBuild saved, build;

    private int group;                            // the paint group being edited
    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, zoom = 1f;
    private Pending pending = Pending.NONE;
    private int savedFlash;
    private boolean draggingView;

    private int pvX0, pvY0, pvX1, pvY1, panelX, panelW;
    private WorkshopPanel panel;
    private ColorPicker picker;
    private Button stockButton, applyButton, takeButton, closeButton, dlgSave, dlgDiscard, dlgCancel;
    private int dlgX, dlgY, dlgW, dlgH;

    public MotoWorkshopScreen(BlockPos stand, BikeType type, MotoBuild build) {
        super(Component.translatable("descentmtb.workshop.title"));
        this.stand = stand.immutable();
        this.type = type;
        this.saved = this.build = build;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean dirty() {
        return !build.equals(saved);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("descentmtb.workshop." + key, args);
    }

    private static Component m(String key, Object... args) {
        return Component.translatable("descentmtb.moto." + key, args);
    }

    // ================================================================== layout

    @Override
    protected void init() {
        int contentTop = MARGIN + 12;
        int barY = height - MARGIN - BAR_H;
        int contentBottom = barY - MARGIN;
        panelW = Math.max(150, Math.min(260, Math.round(width * 0.42f)));
        panelX = width - MARGIN - panelW;
        pvX0 = MARGIN;
        pvY0 = contentTop;
        pvX1 = panelX - MARGIN;
        pvY1 = contentBottom;

        panel = new WorkshopPanel(font);
        panel.setBounds(panelX, contentTop, panelW, contentBottom - contentTop);
        picker = new ColorPicker("pick.moto", font, () -> {});
        picker.bind(() -> shownColor(group), rgb -> edit(build.withColor(group, rgb)));

        stockButton = Button.builder(t("button.stock"), b -> edit(MotoBuild.DEFAULT)).bounds(0, 0, 40, BAR_H).build();
        applyButton = Button.builder(t("button.apply"), b -> apply()).bounds(0, 0, 40, BAR_H).build();
        takeButton = Button.builder(t("button.take"), b -> requestAction(Pending.TAKE)).bounds(0, 0, 40, BAR_H).build();
        closeButton = Button.builder(t("button.close"), b -> requestAction(Pending.CLOSE)).bounds(0, 0, 40, BAR_H).build();
        stockButton.setTooltip(Tooltip.create(m("tip.stock")));
        applyButton.setTooltip(Tooltip.create(t("tip.apply")));
        takeButton.setTooltip(Tooltip.create(t("tip.take")));
        int x = MARGIN;
        for (Button b : new Button[]{stockButton}) {
            x = place(b, x, barY, 1);
        }
        x = width - MARGIN;
        for (Button b : new Button[]{closeButton, takeButton, applyButton}) {
            x = place(b, x, barY, -1);
        }
        for (Button b : new Button[]{stockButton, applyButton, takeButton, closeButton}) {
            addRenderableWidget(b);
        }

        // confirmation dialog (drawn and clicked by hand, only while it is up)
        dlgW = Math.min(width - 16, 250);
        int lines = Math.max(font.split(t("confirm.text"), dlgW - 16).size(), font.split(t("confirm.text.take"), dlgW - 16).size());
        dlgH = 50 + lines * 9;
        dlgX = (width - dlgW) / 2;
        dlgY = (height - dlgH) / 2;
        int bw = (dlgW - 16 - 8) / 3;
        dlgSave = Button.builder(t("confirm.save"), b -> resolve(true)).bounds(dlgX + 8, dlgY + dlgH - 26, bw, 18).build();
        dlgDiscard = Button.builder(t("confirm.discard"), b -> resolve(false)).bounds(dlgX + 12 + bw, dlgY + dlgH - 26, bw, 18).build();
        dlgCancel = Button.builder(t("confirm.cancel"), b -> pending = Pending.NONE).bounds(dlgX + 16 + 2 * bw, dlgY + dlgH - 26, bw, 18).build();
        rebuild();
    }

    /** Puts a button at x (growing right for dir 1, left for -1) and returns the next x. */
    private int place(Button b, int x, int y, int dir) {
        int w = font.width(b.getMessage()) + 14;
        b.setWidth(w);
        b.setY(y);
        b.setX(dir > 0 ? x : x - w);
        return dir > 0 ? x + w + 3 : x - w - 3;
    }

    // ================================================================== edits

    private void edit(MotoBuild next) {
        if (next.equals(build)) return;
        build = next;
        rebuild();
    }

    private int stockColor(int g) {
        return MotoModel.defaultColor(MotoBuild.GROUPS[g]);
    }

    /** The colour a group shows right now: its paint, or the stock colour. */
    private int shownColor(int g) {
        int c = build.colors().get(g);
        return c < 0 ? stockColor(g) : c;
    }

    private Component groupName(int g) {
        return m("group." + MotoBuild.GROUPS[g]);
    }

    private void rebuild() {
        List<Item> list = new ArrayList<>();
        list.add(new Header(m("section.paint")));
        List<Chip> groups = new ArrayList<>();
        for (int g = 0; g < MotoBuild.GROUPS.length; g++) {
            int index = g;
            groups.add(new Chip(groupName(g), g == group, true, shownColor(g), () -> {
                group = index;
                rebuild();
            }));
        }
        list.add(new Chips(groups));
        List<Swatch> swatches = new ArrayList<>();
        int stock = stockColor(group);
        swatches.add(new Swatch(stock, m("stock_colour"), build.colors().get(group) < 0,
                () -> edit(build.withColor(group, MotoBuild.STOCK_COLOUR)),
                (g, x, y, size) -> {
                    g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xff0c1114);
                    g.fill(x, y, x + size, y + size, 0xff000000 | stock);
                    Font f = net.minecraft.client.Minecraft.getInstance().font;
                    g.drawString(f, "S", x + size / 2 - 2, y + size / 2 - 4, 0xff000000 | ColorMath.contrastOn(stock), false);
                }));
        for (int rgb : PRESETS) {
            swatches.add(Swatch.of(rgb, Component.literal(ColorMath.toHex(rgb)), build.colors().get(group) == rgb,
                    () -> edit(build.withColor(group, rgb))));
        }
        list.add(new Swatches(swatches, 14));
        list.add(picker);

        list.add(new Header(m("section.sprocket")));
        list.add(new Stepper(m("sprocket"), () -> sprocketText(), () -> edit(build.withSprocket(build.sprocket() - 1)),
                () -> edit(build.withSprocket(build.sprocket() + 1)),
                () -> build.sprocket() > MotoBuild.MIN_SPROCKET, () -> build.sprocket() < MotoBuild.MAX_SPROCKET));
        list.add(new Note(m("sprocket.hint"), WorkshopPanel.DIM));
        list.add(new Note(m("top_speed", Math.round(topSpeedKmh())), LABEL));

        list.add(new Header(m("section.exhaust")));
        List<Chip> pipes = new ArrayList<>();
        for (Exhaust e : Exhaust.values()) {
            pipes.add(Chip.of(Component.translatable(e.key()), build.exhaust() == e, () -> edit(build.withExhaust(e))));
        }
        list.add(new Chips(pipes));
        list.add(new Note(m("exhaust.hint"), WorkshopPanel.DIM));

        list.add(new Header(m("section.suspension")));
        List<Chip> springs = new ArrayList<>();
        for (Suspension s : Suspension.values()) {
            springs.add(Chip.of(Component.translatable(s.key()), build.suspension() == s, () -> edit(build.withSuspension(s))));
        }
        list.add(new Chips(springs));
        list.add(new Note(m("suspension.hint"), WorkshopPanel.DIM));

        list.add(new Header(m("section.number")));
        list.add(new Stepper(m("number"), () -> build.number() == 0 ? "-" : String.valueOf(build.number()),
                () -> edit(build.withNumber(build.number() - 1)), () -> edit(build.withNumber(build.number() + 1)),
                () -> build.number() > 0, () -> build.number() < MotoBuild.MAX_NUMBER));
        panel.setItems(list);
    }

    private String sprocketText() {
        int teeth = type.params().rearSprocket + build.sprocket();
        return (build.sprocket() > 0 ? "+" : "") + build.sprocket() + " (" + teeth + "T)";
    }

    /** Top-gear speed on the limiter with the chosen sprocket (what the hint shows). */
    private double topSpeedKmh() {
        BikeParams base = type.params(), tuned = type.params();
        MotoTuning.apply(tuned, base, build);
        return MotoTuning.gearedTopSpeedKmh(tuned);
    }

    // ================================================================== actions

    private void apply() {
        if (dirty()) {
            WorkshopNet.applyMoto(stand, build);
        }
    }

    void saveResult(BlockPos at, boolean accepted, MotoBuild authoritative) {
        if (!stand.equals(at)) return;
        if (accepted) {
            saved = authoritative;
            savedFlash = 50;
        } else {
            savedFlash = 0;
        }
    }

    /** Close / take-off with unsaved changes asks first. */
    private void requestAction(Pending action) {
        if (dirty()) {
            pending = action;
            panel.unfocusAll();
        } else {
            run(action);
        }
    }

    private void resolve(boolean save) {
        Pending action = pending;
        pending = Pending.NONE;
        if (save) apply();
        run(action);
    }

    private void run(Pending action) {
        if (action == Pending.TAKE) WorkshopNet.takeBike(stand);
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        requestAction(Pending.CLOSE);
    }

    @Override
    public void tick() {
        if (savedFlash > 0) savedFlash--;
    }

    // ================================================================== drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean modal = pending != Pending.NONE;
        int mx = modal ? -9999 : mouseX, my = modal ? -9999 : mouseY;
        stockButton.active = !modal && !build.equals(MotoBuild.DEFAULT);
        applyButton.active = !modal && dirty();
        takeButton.active = closeButton.active = !modal;
        super.render(g, mx, my, partialTick);

        Component heading = t("title.full", Component.translatable("descentmtb.workshop.type." + type.id));
        Component status = savedFlash > 0 ? t("status.saved") : dirty() ? t("status.unsaved") : null;
        int headingWidth = width - 2 * MARGIN - (status == null ? 0 : font.width(status) + 10);
        g.drawString(font, UiTheme.fit(font, heading.getString(), headingWidth), MARGIN, MARGIN + 1, GOLD, false);
        if (status != null) {
            g.drawString(font, status, width - MARGIN - font.width(status), MARGIN + 1, savedFlash > 0 ? 0xff7fd18b : LABEL, false);
        }
        renderPreview(g);
        panel.render(g, mx, my);
        if (!modal) {
            Component tip = panel.tooltip(mx, my);
            if (tip != null) g.renderTooltip(font, tip, mx, my);
        }
        if (modal) {
            g.fill(0, 0, width, height, 0xa0000000);
            g.fill(dlgX - 1, dlgY - 1, dlgX + dlgW + 1, dlgY + dlgH + 1, GOLD);
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xff18252d);
            g.drawCenteredString(font, t("confirm.title"), width / 2, dlgY + 7, GOLD);
            int yy = dlgY + 20;
            for (FormattedCharSequence line : font.split(t(pending == Pending.TAKE ? "confirm.text.take" : "confirm.text"), dlgW - 16)) {
                g.drawString(font, line, dlgX + 8, yy, VALUE, false);
                yy += 9;
            }
            dlgSave.render(g, mouseX, mouseY, partialTick);
            dlgDiscard.render(g, mouseX, mouseY, partialTick);
            dlgCancel.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderPreview(GuiGraphics g) {
        g.fillGradient(pvX0, pvY0, pvX1, pvY1, 0xff2b3d48, 0xff121a20);
        g.enableScissor(pvX0, pvY0, pvX1, pvY1);
        float scale = Math.min((pvX1 - pvX0) / 2.3f, (pvY1 - pvY0) / 1.55f) * zoom;
        BikeBuildRenderer.renderMotoInGui(g, type, build, (pvX0 + pvX1) / 2f, (pvY0 + pvY1) / 2f, scale, yaw, pitch);
        List<FormattedCharSequence> lines = font.split(t("hint.view"), pvX1 - pvX0 - 8);
        int yy = pvY1 - 3 - lines.size() * 9;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, pvX0 + 4, yy, 0xff7d8f98, false);
            yy += 9;
        }
        g.disableScissor();
        g.renderOutline(pvX0 - 1, pvY0 - 1, pvX1 - pvX0 + 2, pvY1 - pvY0 + 2, EDGE);
    }

    private boolean inPreview(double mx, double my) {
        return mx >= pvX0 && mx < pvX1 && my >= pvY0 && my < pvY1;
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (pending != Pending.NONE) {
            for (Button b : new Button[]{dlgSave, dlgDiscard, dlgCancel}) {
                if (b.mouseClicked(mx, my, button)) break;
            }
            return true;
        }
        if (panel.mouseClicked(mx, my, button)) return true;
        if (super.mouseClicked(mx, my, button)) return true;
        if (inPreview(mx, my)) {
            draggingView = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pending != Pending.NONE) return true;
        if (panel.mouseDragged(mx, my)) return true;
        if (draggingView) {
            yaw += (float) dx * 0.6f;
            pitch = Math.max(-25f, Math.min(60f, pitch + (float) dy * 0.4f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = panel.mouseReleased() || draggingView;
        draggingView = false;
        return was || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (pending != Pending.NONE) return true;
        if (panel.mouseScrolled(mx, my, scrollY)) return true;
        if (inPreview(mx, my)) {
            zoom = Math.max(0.5f, Math.min(3f, zoom * (float) Math.pow(1.1, scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (pending != Pending.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                pending = Pending.NONE;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                resolve(true);
            }
            return true;
        }
        if (panel.anyFocused()) {
            panel.keyPressed(key, scan, mods);
            return true;                  // typing in the hex field never reaches the screen's own shortcuts
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) {
            apply();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (pending != Pending.NONE) return true;
        return panel.charTyped(c, mods) || super.charTyped(c, mods);
    }

    // ================================================================== widgets

    /** A "label  [-] value [+]" row. */
    private static final class Stepper extends Item {
        private static final int BTN = 14, VALUE_W = 44;
        private final Component label;
        private final Supplier<String> value;
        private final Runnable dec, inc;
        private final BooleanSupplier canDec, canInc;

        Stepper(Component label, Supplier<String> value, Runnable dec, Runnable inc, BooleanSupplier canDec, BooleanSupplier canInc) {
            this.label = label;
            this.value = value;
            this.dec = dec;
            this.inc = inc;
            this.canDec = canDec;
            this.canInc = canInc;
        }

        @Override
        int layout(Font font, int width) {
            return BTN + 2;
        }

        private int plusX() {
            return ax + w - BTN;
        }

        private int minusX() {
            return plusX() - VALUE_W - BTN;
        }

        private void button(GuiGraphics g, Font font, int x, String text, boolean enabled, boolean hot) {
            g.fill(x, ay, x + BTN, ay + BTN, hot && enabled ? 0xff33454e : 0xff24323a);
            g.renderOutline(x, ay, BTN, BTN, EDGE);
            g.drawCenteredString(font, text, x + BTN / 2, ay + 3, enabled ? VALUE : WorkshopPanel.DIM);
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            g.drawString(font, label, ax, ay + 3, LABEL, false);
            boolean row = my >= ay && my < ay + BTN;
            button(g, font, minusX(), "-", canDec.getAsBoolean(), row && mx >= minusX() && mx < minusX() + BTN);
            button(g, font, plusX(), "+", canInc.getAsBoolean(), row && mx >= plusX() && mx < plusX() + BTN);
            g.drawCenteredString(font, font.plainSubstrByWidth(value.get(), VALUE_W), minusX() + BTN + VALUE_W / 2, ay + 3, VALUE);
        }

        @Override
        boolean click(double mx, double my) {
            if (my < ay || my >= ay + BTN) return false;
            if (mx >= minusX() && mx < minusX() + BTN && canDec.getAsBoolean()) {
                dec.run();
                return true;
            }
            if (mx >= plusX() && mx < plusX() + BTN && canInc.getAsBoolean()) {
                inc.run();
                return true;
            }
            return false;
        }
    }
}
