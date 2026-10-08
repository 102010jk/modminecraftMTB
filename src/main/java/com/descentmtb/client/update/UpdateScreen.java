package com.descentmtb.client.update;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import com.descentmtb.client.ui.DescentScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** "Descent MTB update" screen, reached from a small button on the title and pause screens. */
@EventBusSubscriber(modid = DescentMtb.MODID, value = Dist.CLIENT)
public final class UpdateScreen extends DescentScreen {
    private final Screen parent;
    private Button action;
    private int panelX, panelY, panelWidth;
    private static final int PANEL_HEIGHT = 184;

    public UpdateScreen(Screen parent) {
        super(Component.translatable("descentmtb.update.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModUpdater.checkIfStale();
        panelWidth = Math.min(340, width - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        int buttonWidth = (panelWidth - 28) / 2, y = panelY + PANEL_HEIGHT - 30;
        action = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (ModUpdater.state() == ModUpdater.State.AVAILABLE) ModUpdater.download();
            else ModUpdater.check();
        }).bounds(panelX + 10, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(panelX + 18 + buttonWidth, y, buttonWidth, 20).build());
        tick();
    }

    @Override
    public void tick() {
        ModUpdater.State s = ModUpdater.state();
        action.active = s == ModUpdater.State.AVAILABLE || s == ModUpdater.State.UP_TO_DATE || s == ModUpdater.State.ERROR;
        action.setMessage(Component.translatable(s == ModUpdater.State.AVAILABLE ? "descentmtb.update.download" : "descentmtb.update.check"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        UiTheme.panel(g, panelX, panelY, panelWidth, PANEL_HEIGHT);
        g.drawCenteredString(font, title, width / 2, panelY + 12, UiTheme.ACCENT);
        ModUpdater.State s = ModUpdater.state();
        Component line = switch (s) {
            case IDLE, CHECKING -> Component.translatable("descentmtb.update.checking");
            case UP_TO_DATE -> Component.translatable("descentmtb.update.latest");
            case AVAILABLE -> Component.translatable("descentmtb.update.available", ModUpdater.remoteVersion(),
                    String.format(java.util.Locale.ROOT, "%.1f", ModUpdater.remoteSize() / 1048576.0));
            case DOWNLOADING -> Component.translatable("descentmtb.update.downloading", (int) Math.round(ModUpdater.progress() * 100));
            case READY -> Component.translatable("descentmtb.update.ready");
            case DEV -> Component.translatable("descentmtb.update.dev");
            case ERROR -> Component.translatable("descentmtb.update.error", ModUpdater.message());
        };
        UiTheme.wrapped(g, font, line, panelX + 12, panelY + 35, panelWidth - 24, 3,
                s == ModUpdater.State.ERROR ? 0xffef9990 : s == ModUpdater.State.READY ? 0xff8bd8a2 : UiTheme.TEXT);
        if (s == ModUpdater.State.AVAILABLE && !ModUpdater.remoteNotes().isEmpty()) {
            UiTheme.wrapped(g, font, Component.literal(ModUpdater.remoteNotes()), panelX + 12, panelY + 76, panelWidth - 24, 5, UiTheme.MUTED);
        }
        if (s == ModUpdater.State.DOWNLOADING) {
            int x = panelX + 12, y = panelY + 130, barWidth = panelWidth - 24;
            g.fill(x, y, x + barWidth, y + 4, UiTheme.EDGE);
            g.fill(x, y, x + (int) Math.round(barWidth * Math.max(0, Math.min(1, ModUpdater.progress()))), y + 4, UiTheme.ACCENT);
        }
        for (var widget : renderables) widget.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @SubscribeEvent
    static void addButton(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) return;
        if (screen instanceof PauseScreen p && !p.showsPauseMenu()) return;
        if (screen instanceof TitleScreen) ModUpdater.checkIfStale();
        boolean ready = ModUpdater.state() == ModUpdater.State.AVAILABLE;
        event.addListener(Button.builder(Component.translatable(ready ? "descentmtb.update.button_new" : "descentmtb.update.button"),
                b -> screen.getMinecraft().setScreen(new UpdateScreen(screen))).bounds(4, 4, 98, 20).build());
    }
}
