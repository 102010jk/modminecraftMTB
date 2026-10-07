package com.descentmtb.client.update;

import com.descentmtb.DescentMtb;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** "Descent MTB update" screen, reached from a small button on the title and pause screens. */
@EventBusSubscriber(modid = DescentMtb.MODID, value = Dist.CLIENT)
public final class UpdateScreen extends Screen {
    private final Screen parent;
    private Button action;

    public UpdateScreen(Screen parent) {
        super(Component.translatable("descentmtb.update.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModUpdater.checkIfStale();
        int cx = width / 2, y = height / 2 + 30;
        action = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (ModUpdater.state() == ModUpdater.State.AVAILABLE) ModUpdater.download();
            else ModUpdater.check();
        }).bounds(cx - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(cx + 4, y, 150, 20).build());
    }

    @Override
    public void tick() {
        ModUpdater.State s = ModUpdater.state();
        action.active = s == ModUpdater.State.AVAILABLE || s == ModUpdater.State.UP_TO_DATE || s == ModUpdater.State.ERROR;
        action.setMessage(Component.translatable(s == ModUpdater.State.AVAILABLE ? "descentmtb.update.download" : "descentmtb.update.check"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int cx = width / 2, y = height / 2 - 40;
        g.drawCenteredString(font, title, cx, y, 0xFFFFFF);
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
        g.drawCenteredString(font, line, cx, y + 20, s == ModUpdater.State.ERROR ? 0xFF7070 : s == ModUpdater.State.READY ? 0x80FF80 : 0xD0D0D0);
        if (s == ModUpdater.State.AVAILABLE && !ModUpdater.remoteNotes().isEmpty()) {
            g.drawCenteredString(font, Component.literal(ModUpdater.remoteNotes()), cx, y + 36, 0xA0A0A0);
        }
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
