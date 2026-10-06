package com.descentmtb.client.custom;

import com.descentmtb.client.custom.WorkshopPanel.Chip;
import com.descentmtb.client.custom.WorkshopPanel.Chips;
import com.descentmtb.client.custom.WorkshopPanel.ColorPicker;
import com.descentmtb.client.custom.WorkshopPanel.Header;
import com.descentmtb.client.custom.WorkshopPanel.Item;
import com.descentmtb.client.custom.WorkshopPanel.Note;
import com.descentmtb.client.custom.WorkshopPanel.Option;
import com.descentmtb.client.custom.WorkshopPanel.Options;
import com.descentmtb.client.custom.WorkshopPanel.Slider;
import com.descentmtb.client.custom.WorkshopPanel.Swatch;
import com.descentmtb.client.custom.WorkshopPanel.Swatches;
import com.descentmtb.client.custom.WorkshopPanel.Toggle;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeBuild.Sticker;
import com.descentmtb.custom.BikeParts;
import com.descentmtb.custom.BikeParts.Anodized;
import com.descentmtb.custom.BikeParts.Bars;
import com.descentmtb.custom.BikeParts.Bell;
import com.descentmtb.custom.BikeParts.Brakes;
import com.descentmtb.custom.BikeParts.Coating;
import com.descentmtb.custom.BikeParts.Finish;
import com.descentmtb.custom.BikeParts.Fork;
import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.LightColor;
import com.descentmtb.custom.BikeParts.Shock;
import com.descentmtb.custom.BikeParts.Soft;
import com.descentmtb.custom.BikeParts.StickerDesign;
import com.descentmtb.custom.BikeParts.Tube;
import com.descentmtb.custom.BikeParts.TyreWall;
import com.descentmtb.custom.WorkshopNet;
import com.descentmtb.entity.BikeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The bike workshop: opened by a right-click on a stand that holds a bike. A big live 3D preview of the build on the
 * left (drag to turn, wheel to zoom), the options on the right in six tabs (frame, suspension, wheels, cockpit,
 * accessories, stickers), a bar of actions at the bottom (undo / redo, stock, random, apply, take the bike off).
 *
 * <p>All edits go to a local {@link EditHistory}; nothing reaches the server until Apply ({@link WorkshopNet#apply}).
 * Closing with unsaved changes asks what to do. The layout is computed from the GUI size, so it fits down to a
 * 320 x 180 GUI (1280 x 720 at GUI scale 4).
 */
public final class WorkshopScreen extends Screen {
    private static final int GOLD = WorkshopPanel.GOLD, LABEL = WorkshopPanel.LABEL, VALUE = WorkshopPanel.VALUE,
            DIM = WorkshopPanel.DIM, EDGE = WorkshopPanel.EDGE;
    private static final int MARGIN = 4, BAR_H = 20, TAB_H = 14;

    /** Default view of the 3D preview (a three-quarter view from slightly above). */
    private static final float DEFAULT_YAW = 65f, DEFAULT_PITCH = 12f;
    /**
     * The fixed side view used while placing stickers. TUNE HERE once the renderer is in: the yaw that shows the
     * bike from its side, whether the front then points to the right of the screen, and the model height (m) that
     * {@link BikeBuildRenderer#renderInGui} puts at the y it is given (0.6 = the bike is centred, 0 = its ground).
     */
    private static final float SIDE_YAW = 90f;
    private static final boolean FRONT_ON_RIGHT = true;
    private static final float SIDE_CENTRE_Y = 0.6f;

    private enum Tab {
        FRAME("frame"), SUSPENSION("suspension"), WHEELS("wheels"), COCKPIT("cockpit"), ACCESSORIES("accessories"),
        STICKERS("stickers");

        final String id;

        Tab(String id) {
            this.id = id;
        }
    }

    private enum Pending { NONE, CLOSE, TAKE }

    // ---- what is being edited ----
    private final BlockPos stand;
    private final BikeType type;
    private final boolean fullSuspension;
    private BikeBuild saved;
    private final EditHistory<BikeBuild> history;
    private final BellPreview bells = new BellPreview();
    private final Random random = new Random();

    // ---- screen state that survives a resize ----
    private Tab tab = Tab.FRAME;
    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, zoom = 1f;
    private boolean paintTarget = true;           // frame tab: true = paint colour, false = accent colour
    private StickerDesign selDesign;
    private int selSticker = -1;
    private Pending pending = Pending.NONE;
    private int savedFlash;

    // ---- interaction ----
    private boolean draggingView, draggingSticker;

    // ---- layout ----
    private int pvX0, pvY0, pvX1, pvY1;
    private int panelX, listY, panelW, listH, tabsPerRow, tabW, tabsY;
    private float pvScale, pvCx, pvCy;

    private WorkshopPanel panel;
    private ColorPicker framePicker, tintPicker;
    private WorkshopPanel.TextField bikeNameField,designNameField,stickerTextField;
    private String designName="",libraryError="";
    private List<BikeDesignStore.Design> designs=List.of();
    private Button undoButton, redoButton, stockButton, randomButton, applyButton, takeButton, resetViewButton;
    private Button dlgSave, dlgDiscard, dlgCancel;
    private int dlgX, dlgY, dlgW, dlgH;

    public WorkshopScreen(BlockPos stand, BikeType type, BikeBuild build) {
        super(Component.translatable("descentmtb.workshop.title"));
        this.stand = stand.immutable();
        this.type = type;
        this.fullSuspension = type == BikeType.ENDURO;
        this.saved = build;
        this.history = new EditHistory<>(build.sanitized(fullSuspension), 100);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private BikeBuild build() {
        return history.current();
    }

    private boolean dirty() {
        return !build().equals(saved);
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("descentmtb.workshop." + key, args);
    }

    // ================================================================== layout

    @Override
    protected void init() {
        StickerIcons.clear();
        int contentTop = MARGIN + 12;
        boolean narrow = width < 440;
        int barY = height - MARGIN - (narrow ? 2 * BAR_H + 3 : BAR_H);
        int contentBottom = barY - MARGIN;

        panelW = Math.max(150, Math.min(260, Math.round(width * 0.42f)));
        panelX = width - MARGIN - panelW;
        tabsPerRow = panelW >= 300 ? 6 : panelW >= 200 ? 3 : 2;
        int rows = Tab.values().length / tabsPerRow;
        tabW = (panelW - (tabsPerRow - 1) * 2) / tabsPerRow;
        tabsY = contentTop;
        listY = tabsY + rows * (TAB_H + 1) + 2;
        listH = Math.max(20, contentBottom - listY);

        pvX0 = MARGIN;
        pvY0 = contentTop;
        pvX1 = panelX - MARGIN;
        pvY1 = contentBottom;
        updatePreviewMetrics();

        panel = new WorkshopPanel(font);
        panel.setBounds(panelX, listY, panelW, listH);
        framePicker = new ColorPicker("pick.frame", font, history::endGesture);
        framePicker.bind(this::frameColorOfTarget, this::setFrameColor);
        tintPicker = new ColorPicker("pick.tint", font, history::endGesture);
        tintPicker.bind(() -> sticker(selSticker) != null ? sticker(selSticker).tint() : 0xFFFFFF, this::setStickerTint);
        bikeNameField=new WorkshopPanel.TextField("bike.name",font,t("bike_name"),48,history::endGesture);
        bikeNameField.bind(()->build().name(),s->commit(build().with(b->b.name(s)),"bike.name"));
        designNameField=new WorkshopPanel.TextField("design.name",font,t("design_name"),48,()->{});
        designNameField.bind(()->designName,s->{ designName=s;rebuild(); });
        reloadDesigns();
        stickerTextField=new WorkshopPanel.TextField("sticker.text",font,t("sticker.text"),32,history::endGesture);
        stickerTextField.bind(()->sticker(selSticker)==null ? "" : sticker(selSticker).text(),
                s->editSticker(selSticker,old->old.withText(s),"sticker.text"+selSticker));

        // bottom bar: undo, redo, stock, random on the left; apply, take on the right
        undoButton = button("undo", b -> undo());
        redoButton = button("redo", b -> redo());
        stockButton = button("stock", b -> resetToStock());
        randomButton = button("random", b -> randomise());
        applyButton = button("apply", b -> apply());
        takeButton = button("take", b -> requestAction(Pending.TAKE));
        undoButton.setTooltip(Tooltip.create(t("tip.undo")));
        redoButton.setTooltip(Tooltip.create(t("tip.redo")));
        stockButton.setTooltip(Tooltip.create(t("tip.stock")));
        randomButton.setTooltip(Tooltip.create(t("tip.random")));
        applyButton.setTooltip(Tooltip.create(t("tip.apply")));
        takeButton.setTooltip(Tooltip.create(t("tip.take")));
        layoutBar(barY);
        for (Button b : new Button[]{undoButton, redoButton, stockButton, randomButton, applyButton, takeButton}) {
            addRenderableWidget(b);
        }

        Component reset = t("reset_view");
        resetViewButton = Button.builder(reset, b -> resetView())
                .bounds(pvX0 + 3, pvY0 + 3, font.width(reset) + 10, 14).build();
        addRenderableWidget(resetViewButton);

        // confirmation dialog (drawn and clicked by hand, only while it is up)
        dlgW = Math.min(width - 16, 250);
        int confirmLines = Math.max(font.split(t("confirm.text"), dlgW - 16).size(),
                font.split(t("confirm.text.take"), dlgW - 16).size());
        dlgH = 50 + confirmLines * 9;
        dlgX = (width - dlgW) / 2;
        dlgY = (height - dlgH) / 2;
        int bw = (dlgW - 16 - 8) / 3;
        dlgSave = Button.builder(t("confirm.save"), b -> resolve(true)).bounds(dlgX + 8, dlgY + dlgH - 26, bw, 18).build();
        dlgDiscard = Button.builder(t("confirm.discard"), b -> resolve(false)).bounds(dlgX + 12 + bw, dlgY + dlgH - 26, bw, 18).build();
        dlgCancel = Button.builder(t("confirm.cancel"), b -> pending = Pending.NONE).bounds(dlgX + 16 + 2 * bw, dlgY + dlgH - 26, bw, 18).build();

        rebuild();
    }

    private Button button(String id, Button.OnPress press) {
        return Button.builder(t("button." + id), press).bounds(0, 0, 40, BAR_H).build();
    }

    private void layoutBar(int y) {
        Button[] left = {undoButton, redoButton, stockButton, randomButton};
        Button[] right = {applyButton, takeButton};
        int avail = width - 2 * MARGIN;
        int pad = 12;
        int total;
        do {
            total = 0;
            for (Button b : left) {
                total += font.width(b.getMessage()) + pad + 3;
            }
            for (Button b : right) {
                total += font.width(b.getMessage()) + pad + 3;
            }
            total += 6;
            if (total > avail) {
                pad -= 2;
            }
        } while (total > avail && pad > 2);
        int x = MARGIN;
        for (Button b : left) {
            int w = font.width(b.getMessage()) + pad;
            b.setX(x);
            b.setY(y);
            b.setWidth(w);
            x += w + 3;
        }
        x = width - MARGIN;
        for (int i = right.length - 1; i >= 0; i--) {
            int w = font.width(right[i].getMessage()) + pad;
            x -= w;
            right[i].setX(x);
            right[i].setY(width < 440 ? y + BAR_H + 3 : y);
            right[i].setWidth(w);
            x -= 3;
        }
    }

    private void updatePreviewMetrics() {
        float w = pvX1 - pvX0, h = pvY1 - pvY0;
        pvScale = Math.min(w / 2.3f, h / 1.55f) * zoom;
        pvCx = (pvX0 + pvX1) / 2f;
        pvCy = (pvY0 + pvY1) / 2f;
    }

    // ================================================================== edits

    private void commit(BikeBuild next, Object gesture) {
        if (history.record(next, gesture)) {
            afterChange();
        }
    }

    private void edit(Function<BikeBuild.Builder, BikeBuild.Builder> change) {
        commit(build().with(change), null);
    }

    private void afterChange() {
        List<Sticker> list = build().stickers();
        if (selSticker >= list.size()) {
            selSticker = list.size() - 1;
        }
        rebuild();
    }

    private void undo() {
        if (history.canUndo()) {
            history.undo();
            afterChange();
        }
    }

    private void redo() {
        if (history.canRedo()) {
            history.redo();
            afterChange();
        }
    }

    private void resetToStock() {
        selSticker = -1;
        commit(BikeBuild.defaultFor(fullSuspension), null);
    }

    private void randomise() {
        List<FrameShape> shapes = BikeParts.shapesFor(fullSuspension);
        List<Fork> forks = new ArrayList<>();
        for (Fork f : Fork.values()) {
            if (f.enduro == fullSuspension) {
                forks.add(f);
            }
        }
        Fork fork = pick(forks);
        List<Integer> accents = BikeParts.FRAME_PRESETS;
        int paint = random.nextInt(3) == 0 ? pick(accents)
                : ColorMath.hsvToRgb(random.nextFloat() * 360f, .5f + random.nextFloat() * .45f, .45f + random.nextFloat() * .5f);
        BikeBuild next = build().with(b -> {
            b.shape(pick(shapes)).frameColor(paint).accentColor(pick(accents)).finish(pick(List.of(Finish.values())));
            b.fork(fork).forkLowerColor(fork.lowerColors[random.nextInt(fork.lowerColors.length)]);
            if (fullSuspension) {
                b.shock(pick(List.of(Shock.values())));
            }
            b.rims(pick(List.of(Anodized.values()))).hubs(pick(List.of(Anodized.values()))).tyres(pick(List.of(TyreWall.values())));
            b.bars(pick(List.of(Bars.values()))).grips(pick(List.of(Soft.values()))).saddle(pick(List.of(Soft.values())));
            b.pedals(pick(List.of(Anodized.values()))).brakes(pick(List.of(Brakes.values())));
            b.brakeColor(pick(List.of(Anodized.values())));
            b.bell(pick(List.of(Bell.values()))).frontLight(random.nextBoolean()).rearLight(random.nextBoolean());
            b.lightColor(pick(List.of(LightColor.values())));
            List<BikeBuild.Sticker> stickers=new ArrayList<>();
            for(int i=0,n=random.nextInt(4);i<n;i++) stickers.add(new BikeBuild.Sticker(
                    pick(List.of(StickerDesign.values())),pick(List.of(Tube.values())),.2f+random.nextFloat()*.6f,
                    0,random.nextInt(4)*90f,.7f+random.nextFloat()*.6f,0xFFFFFF));
            b.stickers(stickers);
            return b;
        });
        commit(next, null);
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    private void apply() {
        if (!dirty()) {
            return;
        }
        BikeBuild toSend = build();
        WorkshopNet.apply(stand, toSend);
    }

    void saveResult(BlockPos at,boolean accepted,BikeBuild authoritative) {
        if(!stand.equals(at)) return;
        if(accepted) { saved=authoritative; savedFlash=50; }
        else savedFlash=0;
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
        if (save) {
            apply();
        }
        run(action);
    }

    private void run(Pending action) {
        if (action == Pending.TAKE) {
            WorkshopNet.takeBike(stand);
        }
        bells.clear();
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        requestAction(Pending.CLOSE);
    }

    @Override
    public void removed() {
        bells.clear();
        super.removed();
    }

    @Override
    public void tick() {
        bells.tick();
        if (savedFlash > 0) {
            savedFlash--;
        }
    }

    private void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1f;
        updatePreviewMetrics();
    }

    // ================================================================== tab contents

    private void rebuild() {
        if (panel != null) {
            panel.setItems(items());
        }
    }

    private void selectTab(Tab next) {
        if (next == tab) {
            return;
        }
        panel.unfocusAll();
        history.endGesture();
        tab = next;
        if (tab != Tab.STICKERS) {
            selDesign = null;
        }
        panel.resetScroll();
        rebuild();
    }

    private List<Item> items() {
        return switch (tab) {
            case FRAME -> frameItems();
            case SUSPENSION -> suspensionItems();
            case WHEELS -> wheelItems();
            case COCKPIT -> cockpitItems();
            case ACCESSORIES -> accessoryItems();
            case STICKERS -> stickerItems();
        };
    }

    private static Component name(String key) {
        return Component.translatable(key);
    }

    private static Component hex(int rgb) {
        return Component.literal(ColorMath.toHex(rgb));
    }

    // ---- frame ----

    private int frameColorOfTarget() {
        return paintTarget ? build().frameColor() : build().accentColor();
    }

    private void setFrameColor(int rgb) {
        boolean paint = paintTarget;
        commit(build().with(b -> paint ? b.frameColor(rgb) : b.accentColor(rgb)), paint ? "frame" : "accent");
    }

    private List<Item> frameItems() {
        BikeBuild b = build();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("bike_name")));list.add(bikeNameField);
        list.add(new Header(t("design_library")));list.add(designNameField);
        list.add(new Chips(List.of(
                new Chip(t("design_save"),false,!designName.isBlank(),-1,()->libraryAction(true)),
                new Chip(t("design_delete"),false,!designName.isBlank(),-1,()->libraryAction(false)))));
        if(!libraryError.isEmpty()) list.add(new Note(t("design_error"),0xffff8866));
        list.add(new Note(t("design_portable"),DIM));
        List<Option> savedDesigns=new ArrayList<>();
        for(var design:designs) savedDesigns.add(new Option(Component.literal(design.name()),
                name("descentmtb.workshop.type."+design.type().id),null,0,design.build().frameColor(),
                design.name().equals(designName),design.type()==type,()->{
                    designName=design.name();commit(design.build(),null);
                }));
        if(!savedDesigns.isEmpty()) list.add(new Options(savedDesigns));
        list.add(new Header(t("section.shape")));
        List<Option> shapes = new ArrayList<>();
        for (FrameShape s : BikeParts.shapesFor(fullSuspension)) {
            shapes.add(Option.simple(name(s.key()), -1, b.shape() == s, () -> edit(bb -> bb.shape(s))));
        }
        list.add(new Options(shapes));

        list.add(new Header(t("section.paint")));
        list.add(new Chips(List.of(
                new Chip(t("target.paint"), paintTarget, true, b.frameColor(), () -> { paintTarget = true; history.endGesture(); rebuild(); }),
                new Chip(t("target.accent"), !paintTarget, true, b.accentColor(), () -> { paintTarget = false; history.endGesture(); rebuild(); }))));
        int current = frameColorOfTarget();
        List<Swatch> presets = new ArrayList<>();
        for (int rgb : BikeParts.FRAME_PRESETS) {
            presets.add(Swatch.of(rgb, hex(rgb), (rgb & 0xFFFFFF) == (current & 0xFFFFFF), () -> {
                boolean paint = paintTarget;
                commit(build().with(bb -> paint ? bb.frameColor(rgb) : bb.accentColor(rgb)), null);
            }));
        }
        list.add(new Swatches(presets, 14));
        list.add(framePicker);

        list.add(new Header(t("section.finish")));
        List<Chip> finishes = new ArrayList<>();
        for (Finish f : Finish.values()) {
            finishes.add(Chip.of(name(f.key()), b.finish() == f, () -> edit(bb -> bb.finish(f))));
        }
        list.add(new Chips(finishes));
        return list;
    }

    private void reloadDesigns() {
        try { designs=BikeDesignStore.load();libraryError=""; }
        catch(Exception e) { libraryError=e.toString();com.descentmtb.DescentMtb.LOG.warn("Bike design library",e); }
    }
    private void libraryAction(boolean save) {
        panel.unfocusAll();
        try {
            if(save) BikeDesignStore.save(designName,type,build());else BikeDesignStore.delete(designName,type);
            reloadDesigns();
        } catch(Exception e) { libraryError=e.toString();com.descentmtb.DescentMtb.LOG.warn("Bike design library",e); }
        rebuild();
    }

    // ---- suspension ----

    private static Component coatingName(Coating c) {
        return t("coating." + c.name().toLowerCase(Locale.ROOT));
    }

    private List<Item> suspensionItems() {
        BikeBuild b = build();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("section.fork")));
        List<Option> forks = new ArrayList<>();
        for (Fork f : Fork.values()) {
            if (f.enduro != fullSuspension) {
                continue;
            }
            Component sub = f.travelMm > 0 ? Component.literal(f.travelMm + " mm") : t("rigid");
            forks.add(new Option(Component.literal(f.displayName), sub, coatingName(f.stanchions), f.stanchions.rgb,
                    f.lowerColors[0], b.fork() == f, true, () -> edit(bb -> bb.fork(f))));
        }
        list.add(new Options(forks));

        list.add(new Header(t("section.lowers")));
        List<Swatch> lowers = new ArrayList<>();
        for (int rgb : b.fork().lowerColors) {
            lowers.add(Swatch.of(rgb, hex(rgb), (rgb & 0xFFFFFF) == (b.forkLowerColor() & 0xFFFFFF),
                    () -> edit(bb -> bb.forkLowerColor(rgb))));
        }
        list.add(new Swatches(lowers, 16));
        list.add(new Note(t("note.lowers"), DIM));

        if (fullSuspension) {
            list.add(new Header(t("section.shock")));
            List<Option> shocks = new ArrayList<>();
            for (Shock s : Shock.values()) {
                Component sub = s.coil() ? t("shock.coil") : t("shock.air");
                shocks.add(new Option(Component.literal(s.displayName), sub, coatingName(s.body), s.body.rgb,
                        s.coil() ? s.springColor : s.body.rgb, b.shock() == s, true, () -> edit(bb -> bb.shock(s))));
            }
            list.add(new Options(shocks));
        }
        return list;
    }

    // ---- wheels ----

    private List<Swatch> anodizedSwatches(Anodized current, Consumer<Anodized> set) {
        List<Swatch> out = new ArrayList<>();
        for (Anodized a : Anodized.values()) {
            out.add(Swatch.of(a.rgb, name(a.key()), a == current, () -> set.accept(a)));
        }
        return out;
    }

    private List<Swatch> softSwatches(Soft current, Consumer<Soft> set) {
        List<Swatch> out = new ArrayList<>();
        for (Soft s : Soft.values()) {
            out.add(Swatch.of(s.rgb, name(s.key()), s == current, () -> set.accept(s)));
        }
        return out;
    }

    private List<Item> wheelItems() {
        BikeBuild b = build();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("section.rims")));
        list.add(new Swatches(anodizedSwatches(b.rims(), a -> edit(bb -> bb.rims(a))), 16));
        list.add(new Header(t("section.hubs")));
        list.add(new Swatches(anodizedSwatches(b.hubs(), a -> edit(bb -> bb.hubs(a))), 16));
        list.add(new Header(t("section.tyres")));
        List<Chip> tyres = new ArrayList<>();
        for (TyreWall w : TyreWall.values()) {
            tyres.add(new Chip(name(w.key()), b.tyres() == w, true, w.rgb, () -> edit(bb -> bb.tyres(w))));
        }
        list.add(new Chips(tyres));
        return list;
    }

    // ---- cockpit ----

    private List<Item> cockpitItems() {
        BikeBuild b = build();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("section.bars")));
        List<Option> bars = new ArrayList<>();
        for (Bars x : Bars.values()) {
            bars.add(Option.simple(name(x.key()), x.rgb, b.bars() == x, () -> edit(bb -> bb.bars(x))));
        }
        list.add(new Options(bars));

        list.add(new Header(t("section.grips")));
        list.add(new Swatches(softSwatches(b.grips(), s -> edit(bb -> bb.grips(s))), 16));
        list.add(new Header(t("section.saddle")));
        list.add(new Swatches(softSwatches(b.saddle(), s -> edit(bb -> bb.saddle(s))), 16));
        list.add(new Header(t("section.pedals")));
        list.add(new Swatches(anodizedSwatches(b.pedals(), a -> edit(bb -> bb.pedals(a))), 16));

        list.add(new Header(t("section.brakes")));
        List<Option> brakes = new ArrayList<>();
        for (Brakes x : Brakes.values()) {
            int dot = x.rgb >= 0 ? x.rgb : b.brakeColor().rgb;
            brakes.add(new Option(Component.literal(x.displayName), null, null, 0, dot, b.brakes() == x, true,
                    () -> edit(bb -> bb.brakes(x))));
        }
        list.add(new Options(brakes));
        if (b.brakes().rgb < 0) {
            list.add(new Header(t("section.brake_color")));
            list.add(new Swatches(anodizedSwatches(b.brakeColor(), a -> edit(bb -> bb.brakeColor(a))), 16));
        }
        return list;
    }

    // ---- accessories ----

    private List<Item> accessoryItems() {
        BikeBuild b = build();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("section.bell")));
        List<Option> bellRows = new ArrayList<>();
        for (Bell x : Bell.values()) {
            bellRows.add(Option.simple(name(x.key()), -1, b.bell() == x, () -> edit(bb -> bb.bell(x))));
        }
        list.add(new Options(bellRows));
        list.add(new Chips(List.of(new Chip(t("bell.test"), false, b.bell() != Bell.NONE, -1, () -> bells.play(build().bell())))));

        list.add(new Header(t("section.lights")));
        list.add(new Toggle(t("light.front"), b.frontLight(), () -> edit(bb -> bb.frontLight(!build().frontLight()))));
        list.add(new Toggle(t("light.rear"), b.rearLight(), () -> edit(bb -> bb.rearLight(!build().rearLight()))));
        list.add(new Header(t("section.light_color")));
        List<Swatch> colors = new ArrayList<>();
        for (LightColor c : LightColor.values()) {
            colors.add(Swatch.of(c.rgb, name(c.key()), b.lightColor() == c, () -> edit(bb -> bb.lightColor(c))));
        }
        list.add(new Swatches(colors, 16));
        if (!b.frontLight() && !b.rearLight()) {
            list.add(new Note(t("note.lights_off"), DIM));
        }
        return list;
    }

    // ---- stickers ----

    private Sticker sticker(int index) {
        List<Sticker> list = build().stickers();
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private void editSticker(int index, UnaryOperator<Sticker> change, Object gesture) {
        Sticker s = sticker(index);
        if (s == null) {
            return;
        }
        List<Sticker> list = new ArrayList<>(build().stickers());
        list.set(index, change.apply(s));
        commit(build().with(b -> b.stickers(list)), gesture);
    }

    private void setStickerTint(int rgb) {
        int sel = selSticker;
        editSticker(sel, s -> s.changed(s.design(), s.tube(), s.t(), s.side(), s.rotation(), s.scale(), rgb & 0xFFFFFF), "tint" + sel);
    }

    private static Component sideName(int side) {
        return t(side < 0 ? "side.left" : side > 0 ? "side.right" : "side.both");
    }

    private List<Item> stickerItems() {
        BikeBuild b = build();
        List<Sticker> stickers = b.stickers();
        List<Item> list = new ArrayList<>();
        list.add(new Header(t("section.designs")));
        List<Swatch> designs = new ArrayList<>();
        for (StickerDesign d : StickerDesign.values()) {
            designs.add(new Swatch(0, name(d.key()), selDesign == d, () -> {
                selDesign = selDesign == d ? null : d;
                rebuild();
            }, (g, x, y, size) -> StickerIcons.paint(g, d, x, y, size)));
        }
        list.add(new Swatches(designs, 24));
        list.add(new Chips(List.of(new Chip(t("sticker.add_text"),false,stickers.size()<BikeBuild.MAX_STICKERS,-1,()->{
            List<Sticker> next=new ArrayList<>(build().stickers());
            next.add(new Sticker(StickerDesign.LOGO_DESCENT,Tube.DOWN,.5f,0,0,1,0xFFFFFF).withText(t("sticker.default_text").getString()));
            selSticker=next.size()-1;selDesign=null;commit(build().with(bb->bb.stickers(next)),null);
        }))));
        if (stickers.size() >= BikeBuild.MAX_STICKERS) {
            list.add(new Note(t("note.sticker_limit", BikeBuild.MAX_STICKERS), 0xffe07a5f));
        } else if (selDesign != null) {
            list.add(new Note(t("note.sticker_place", name(selDesign.key())), VALUE));
        } else {
            list.add(new Note(t("note.sticker_pick"), DIM));
        }

        Sticker sel = sticker(selSticker);
        if (sel != null) {
            int index = selSticker;
            list.add(new Header(t("section.selected", name(sel.design().key()))));
            list.add(new Note(t("sticker.text"),LABEL));list.add(stickerTextField);
            list.add(new Note(t("sticker.text_hint"),DIM));
            list.add(new Slider("slider.pos", t("sticker.position"), 0, 1, 0.01, () -> cur(index).t(),
                    v -> Math.round(v * 100) + " %",
                    v -> editSticker(index, s -> s.changed(s.design(), s.tube(), (float) v, s.side(), s.rotation(), s.scale(), s.tint()), "pos" + index),
                    history::endGesture));
            list.add(new Slider("slider.across",t("sticker.across"),-.9,.9,.05,()->cur(index).across(),
                    v->Math.round(v*100)+" %",v->editSticker(index,s->s.withAcross((float)v),"across"+index),history::endGesture));
            List<Chip> tubes = new ArrayList<>();
            for (Tube tube : Tube.values()) {
                tubes.add(Chip.of(name(tube.key()), sel.tube() == tube, () -> editSticker(index,
                        s -> s.changed(s.design(), tube, s.t(), s.side(), s.rotation(), s.scale(), s.tint()), null)));
            }
            list.add(new Chips(tubes));
            List<Chip> sides = new ArrayList<>();
            for (int side = -1; side <= 1; side++) {
                int sd = side;
                sides.add(Chip.of(sideName(side), sel.side() == side, () -> editSticker(index,
                        s -> s.changed(s.design(), s.tube(), s.t(), sd, s.rotation(), s.scale(), s.tint()), null)));
            }
            list.add(new Chips(sides));
            list.add(new Slider("slider.rot", t("sticker.rotation"), 0, 360, 5, () -> cur(index).rotation(),
                    v -> Math.round(v) + "°",
                    v -> editSticker(index, s -> s.changed(s.design(), s.tube(), s.t(), s.side(), (float) (v % 360), s.scale(), s.tint()), "rot" + index),
                    history::endGesture));
            list.add(new Slider("slider.scale", t("sticker.scale"), 0.4, 2.5, 0.05, () -> cur(index).scale(),
                    v -> String.format(Locale.ROOT, "%.2f x", v),
                    v -> editSticker(index, s -> s.changed(s.design(), s.tube(), s.t(), s.side(), s.rotation(), (float) v, s.tint()), "scale" + index),
                    history::endGesture));
            list.add(new Note(t("sticker.tint"), LABEL));
            list.add(tintPicker);
            list.add(new Chips(List.of(
                    new Chip(t("sticker.duplicate"),false,stickers.size()<BikeBuild.MAX_STICKERS,-1,()->duplicateSticker(index,false)),
                    new Chip(t("sticker.opposite"),false,stickers.size()<BikeBuild.MAX_STICKERS && sel.side()!=0,-1,()->duplicateSticker(index,true)),
                    Chip.of(t("sticker.flip"),sel.mirrored(),()->editSticker(index,Sticker::flipImage,null)))));
            list.add(new Chips(List.of(Chip.of(t("sticker.delete"), false, () -> {
                List<Sticker> rest = new ArrayList<>(build().stickers());
                rest.remove(index);
                selSticker = -1;
                commit(build().with(bb -> bb.stickers(rest)), null);
            }))));
        }

        list.add(new Header(t("section.list", stickers.size(), BikeBuild.MAX_STICKERS)));
        if (stickers.isEmpty()) {
            list.add(new Note(t("note.no_stickers"), DIM));
        } else {
            List<Option> rows = new ArrayList<>();
            for (int i = 0; i < stickers.size(); i++) {
                Sticker s = stickers.get(i);
                int index = i;
                rows.add(new Option(Component.literal((i + 1) + ". ").append(s.text().isBlank() ? name(s.design().key()) : Component.literal(s.text())),
                        Component.empty().append(name(s.tube().key())).append(" - ").append(sideName(s.side())), null, 0,
                        s.tint(), i == selSticker, true, () -> {
                            selSticker = index;
                            selDesign = null;
                            rebuild();
                        }));
            }
            list.add(new Options(rows));
        }
        return list;
    }

    private void duplicateSticker(int index,boolean opposite) {
        Sticker old=sticker(index);
        if(old==null || build().stickers().size()>=BikeBuild.MAX_STICKERS) return;
        List<Sticker> next=new ArrayList<>(build().stickers());
        next.add(opposite ? old.opposite() : old.changed(old.design(),old.tube(),Math.min(1,old.t()+.05f),old.side(),old.rotation(),old.scale(),old.tint()));
        selSticker=next.size()-1;commit(build().with(b->b.stickers(next)),null);
    }

    private Sticker cur(int index) {
        Sticker s = sticker(index);
        return s != null ? s : new Sticker(StickerDesign.LOGO_DESCENT, Tube.DOWN, 0.5f, 0, 0f, 1f, 0xFFFFFF);
    }

    // ================================================================== preview

    private boolean inPreview(double mx, double my) {
        return mx >= pvX0 && mx < pvX1 && my >= pvY0 && my < pvY1;
    }

    private float screenX(float modelX) {
        return pvCx + (FRONT_ON_RIGHT ? modelX : -modelX) * pvScale;
    }

    private float screenY(float modelY) {
        return pvCy - (modelY - SIDE_CENTRE_Y) * pvScale;
    }

    private float modelX(double sx) {
        return (float) ((sx - pvCx) / pvScale) * (FRONT_ON_RIGHT ? 1 : -1);
    }

    private float modelY(double sy) {
        return (float) (SIDE_CENTRE_Y - (sy - pvCy) / pvScale);
    }

    private void renderPreview(GuiGraphics g, int mx, int my) {
        updatePreviewMetrics();
        g.fillGradient(pvX0, pvY0, pvX1, pvY1, 0xff2b3d48, 0xff121a20);
        boolean side = tab == Tab.STICKERS;
        g.enableScissor(pvX0, pvY0, pvX1, pvY1);
        BikeBuildRenderer.renderInGui(g, type, build(), pvCx, pvCy, pvScale, side ? SIDE_YAW : yaw, side ? 0f : pitch);
        if (side) {
            renderStickerOverlay(g, mx, my);
        }
        // hint, wrapped, from the bottom up
        List<FormattedCharSequence> lines = font.split(t(side ? "hint.sticker" : "hint.view"), pvX1 - pvX0 - 8);
        int yy = pvY1 - 3 - lines.size() * 9;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, pvX0 + 4, yy, 0xff7d8f98, false);
            yy += 9;
        }
        g.disableScissor();
        g.renderOutline(pvX0 - 1, pvY0 - 1, pvX1 - pvX0 + 2, pvY1 - pvY0 + 2, EDGE);
    }

    private void line(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x0 + (x1 - x0) * i / steps), y = Math.round(y0 + (y1 - y0) * i / steps);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void renderStickerOverlay(GuiGraphics g, int mx, int my) {
        FrameShape shape = build().shape();
        StickerAnchors.Pick hover = null;
        boolean placing = selDesign != null && build().stickers().size() < BikeBuild.MAX_STICKERS;
        if (placing && inPreview(mx, my) && !draggingSticker) {
            hover = StickerAnchors.pick(fullSuspension, shape, modelX(mx), modelY(my));
        }
        Sticker sel = sticker(selSticker);
        for (Tube tube : Tube.values()) {
            boolean hot = hover != null && hover.tube() == tube || sel != null && sel.tube() == tube;
            int color=hot ? 0xb0e2c48a : placing ? 0x40ffffff : 0x20ffffff;
            float[] start=StickerAnchors.pointOn(fullSuspension,shape,tube,0);
            float[] middle=StickerAnchors.pointOn(fullSuspension,shape,tube,.5f);
            float[] end=StickerAnchors.pointOn(fullSuspension,shape,tube,1);
            line(g,screenX(start[0]),screenY(start[1]),screenX(middle[0]),screenY(middle[1]),color);
            line(g,screenX(middle[0]),screenY(middle[1]),screenX(end[0]),screenY(end[1]),color);
        }
        List<Sticker> stickers = build().stickers();
        for (int i = 0; i < stickers.size(); i++) {
            Sticker s = stickers.get(i);
            float[] p = StickerAnchors.pointOn(fullSuspension, shape, s.tube(), s.t());
            int x = Math.round(screenX(p[0])), y = Math.round(screenY(p[1]));
            boolean selected = i == selSticker;
            int r = selected ? 5 : 4;
            g.fill(x - r - 1, y - r - 1, x + r + 2, y + r + 2, selected ? GOLD : 0xff0c1114);
            g.fill(x - r, y - r, x + r + 1, y + r + 1, 0xff000000 | s.tint());
            g.drawString(font, String.valueOf(i + 1), x + r + 3, y - 4, selected ? GOLD : 0xffb8c4ca, true);
        }
        if (hover != null) {
            float[] p = StickerAnchors.pointOn(fullSuspension, shape, hover.tube(), hover.t());
            int x = Math.round(screenX(p[0])), y = Math.round(screenY(p[1]));
            g.renderOutline(x - 6, y - 6, 13, 13, 0xffffffff);
            g.renderOutline(x - 5, y - 5, 11, 11, 0xff000000);
        }
    }

    private void previewClick(double mx, double my) {
        if (tab != Tab.STICKERS) {
            draggingView = true;
            return;
        }
        FrameShape shape = build().shape();
        List<Sticker> stickers = build().stickers();
        int hit = -1;
        double best = 8 * 8;
        for (int i = 0; i < stickers.size(); i++) {
            Sticker s = stickers.get(i);
            float[] p = StickerAnchors.pointOn(fullSuspension, shape, s.tube(), s.t());
            double dx = screenX(p[0]) - mx, dy = screenY(p[1]) - my;
            if (dx * dx + dy * dy <= best) {
                best = dx * dx + dy * dy;
                hit = i;
            }
        }
        if (hit >= 0) {
            selSticker = hit;
            selDesign = null;
            draggingSticker = true;
            rebuild();
            return;
        }
        if (selDesign != null && stickers.size() < BikeBuild.MAX_STICKERS) {
            StickerAnchors.Pick pick = StickerAnchors.pick(fullSuspension, shape, modelX(mx), modelY(my));
            if (pick != null) {
                List<Sticker> list = new ArrayList<>(stickers);
                list.add(new Sticker(selDesign, pick.tube(), pick.t(), 0, 0f, 1f, 0xFFFFFF));
                selSticker = list.size() - 1;
                selDesign = null;
                commit(build().with(b -> b.stickers(list)), null);
                return;
            }
        }
        selSticker = -1;
        rebuild();
    }

    // ================================================================== screen plumbing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xd018252d);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean modal = pending != Pending.NONE;
        int mx = modal ? -9999 : mouseX, my = modal ? -9999 : mouseY;
        undoButton.active = !modal && history.canUndo();
        redoButton.active = !modal && history.canRedo();
        stockButton.active = !modal && !build().equals(BikeBuild.defaultFor(fullSuspension));
        applyButton.active = !modal && dirty();
        randomButton.active = takeButton.active = resetViewButton.active = !modal;
        super.render(g, mx, my, partialTick);

        Component heading = t("title.full", build().name().isBlank() ? name("descentmtb.workshop.type." + type.id)
                : Component.literal(build().name()));
        g.drawString(font, heading, MARGIN, MARGIN + 1, GOLD, false);
        Component status = savedFlash > 0 ? t("status.saved") : dirty() ? t("status.unsaved") : null;
        if (status != null) {
            g.drawString(font, status, width - MARGIN - font.width(status), MARGIN + 1, savedFlash > 0 ? 0xff7fd18b : LABEL, false);
        }

        renderPreview(g, mx, my);
        resetViewButton.render(g, mouseX, mouseY, partialTick);

        // tabs
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int x = panelX + (i % tabsPerRow) * (tabW + 2), y = tabsY + (i / tabsPerRow) * (TAB_H + 1);
            boolean on = tabs[i] == tab, hot = !modal && mx >= x && mx < x + tabW && my >= y && my < y + TAB_H;
            g.fill(x, y, x + tabW, y + TAB_H, on ? WorkshopPanel.ROW_SELECTED : hot ? 0xff2a3b44 : 0xff1a262d);
            g.renderOutline(x, y, tabW, TAB_H, on ? GOLD : EDGE);
            String label = font.plainSubstrByWidth(t("tab." + tabs[i].id).getString(), tabW - 4);
            g.drawCenteredString(font, label, x + tabW / 2, y + 3, on ? VALUE : LABEL);
        }

        panel.render(g, mx, my);
        if (!modal) {
            Component tip = panel.tooltip(mx, my);
            if (tip != null) {
                g.renderTooltip(font, tip, mx, my);
            }
        }

        if (modal) {
            g.fill(0, 0, width, height, 0xa0000000);
            g.fill(dlgX - 1, dlgY - 1, dlgX + dlgW + 1, dlgY + dlgH + 1, GOLD);
            g.fill(dlgX, dlgY, dlgX + dlgW, dlgY + dlgH, 0xff18252d);
            g.drawCenteredString(font, t("confirm.title"), width / 2, dlgY + 7, GOLD);
            List<FormattedCharSequence> lines = font.split(t(pending == Pending.TAKE ? "confirm.text.take" : "confirm.text"), dlgW - 16);
            int yy = dlgY + 20;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, dlgX + 8, yy, VALUE, false);
                yy += 9;
            }
            dlgSave.render(g, mouseX, mouseY, partialTick);
            dlgDiscard.render(g, mouseX, mouseY, partialTick);
            dlgCancel.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (pending != Pending.NONE) {
            for (Button b : new Button[]{dlgSave, dlgDiscard, dlgCancel}) {
                if (b.mouseClicked(mx, my, button)) {
                    break;
                }
            }
            return true;
        }
        if (panel.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            Tab[] tabs = Tab.values();
            for (int i = 0; i < tabs.length; i++) {
                int x = panelX + (i % tabsPerRow) * (tabW + 2), y = tabsY + (i / tabsPerRow) * (TAB_H + 1);
                if (mx >= x && mx < x + tabW && my >= y && my < y + TAB_H) {
                    selectTab(tabs[i]);
                    return true;
                }
            }
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (inPreview(mx, my)) {
            if (button == 0) {
                previewClick(mx, my);
            } else if (button == 1 && tab != Tab.STICKERS) {
                draggingView = true;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pending != Pending.NONE) {
            return true;
        }
        if (panel.mouseDragged(mx, my)) {
            return true;
        }
        if (draggingView) {
            yaw += (float) dx * 0.6f;
            pitch = Math.max(-25f, Math.min(60f, pitch + (float) dy * 0.4f));
            return true;
        }
        if (draggingSticker && selSticker >= 0) {
            StickerAnchors.Pick pick = StickerAnchors.nearest(fullSuspension, build().shape(), modelX(mx), modelY(my));
            int index = selSticker;
            editSticker(index, s -> s.changed(s.design(), pick.tube(), pick.t(), s.side(), s.rotation(), s.scale(), s.tint()), "move" + index);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = panel.mouseReleased() || draggingView || draggingSticker;
        draggingView = false;
        if (draggingSticker) {
            draggingSticker = false;
            history.endGesture();
        }
        return was || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (pending != Pending.NONE) {
            return true;
        }
        if (panel.mouseScrolled(mx, my, scrollY)) {
            return true;
        }
        if (inPreview(mx, my)) {
            zoom = Math.max(0.5f, Math.min(3f, zoom * (float) Math.pow(1.1, scrollY)));
            updatePreviewMetrics();
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
            return true;                  // typing in a field never reaches the screen's own shortcuts
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) {
            if (hasShiftDown()) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) {
            apply();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (pending != Pending.NONE) {
            return true;
        }
        if (panel.charTyped(c, mods)) {
            return true;
        }
        return super.charTyped(c, mods);
    }
}
