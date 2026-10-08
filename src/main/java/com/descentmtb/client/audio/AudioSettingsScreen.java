package com.descentmtb.client.audio;

import com.descentmtb.audio.AudioNet;
import com.descentmtb.audio.BoomboxState;
import com.descentmtb.audio.Emitter;
import com.descentmtb.client.audio.win.WinAudioSessions;
import com.descentmtb.client.ui.DescentScreen;
import com.descentmtb.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.JukeboxSong;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Choose a music source with bounded labels and explicit volume/range controls. */
public final class AudioSettingsScreen extends DescentScreen {
    private static final int ROWS = 4, PANEL_HEIGHT = 224;
    private final Emitter emitter;
    private List<WinAudioSessions.Session> sources = List.of();
    private Component status = Component.empty();
    private int radius = 20, page, refreshId;
    private float volume = 1;
    private int panelX, panelY, panelWidth;
    private Button previousButton, nextButton, stopButton;

    public AudioSettingsScreen(Emitter emitter) {
        super(Component.translatable("descentmtb.audio.title"));
        this.emitter = emitter;
        if (emitter == null) volume = AudioClient.personalVolume();
        else {
            BoomboxState state = AudioClient.stateOf(emitter);
            if (state != null) { radius = state.radius(); volume = state.volume(); }
        }
    }

    @Override protected void init() {
        panelWidth = Math.min(340, width - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;
        refresh();
    }

    private Component t(String key, Object... args) {
        return Component.translatable("descentmtb.audio." + key, args);
    }

    private int pages() { return Math.max(1, (sources.size() + ROWS - 1) / ROWS); }

    private void refresh() {
        int request = ++refreshId;
        sources = List.of();
        status = DesktopCapture.supported() ? t("loading") : t("windows");
        layout();
        if (!DesktopCapture.supported()) return;
        DesktopCapture.listSources().whenComplete((list, error) -> minecraft.execute(() -> {
            if (minecraft.screen != this || request != refreshId) return;
            sources = error == null ? list : List.of();
            status = error == null ? (sources.isEmpty() ? t("empty") : Component.empty())
                    : t("source_error");
            page = Math.min(page, pages() - 1);
            layout();
        }));
    }

    private void layout() {
        clearWidgets();
        int x = panelX + 8, y = panelY + 29, contentWidth = panelWidth - 16;
        Button refresh = addRenderableWidget(Button.builder(t("refresh"), b -> refresh()).bounds(x, y, 80, 20).build());
        refresh.active = DesktopCapture.supported();
        int remaining = contentWidth - 84;
        int sliderWidth = emitter == null ? remaining : (remaining - 4) / 2;
        addRenderableWidget(new DeviceSlider(x + 84, y, sliderWidth, false));
        if (emitter != null) addRenderableWidget(new DeviceSlider(x + 88 + sliderWidth, y, remaining - sliderWidth - 4, true));

        for (int i = 0; i < ROWS; i++) {
            int index = page * ROWS + i;
            if (index >= sources.size()) break;
            var source = sources.get(index);
            String app = AudioApps.displayName(source.exePath());
            String song = AudioApps.cleanTitle(source.title());
            String label = (source.peak() > .01f ? "♪ " : "") + app
                    + (song.isBlank() || song.equalsIgnoreCase(app) ? "" : " — " + song);
            Button button = addRenderableWidget(Button.builder(Component.literal(UiTheme.fit(font, label, contentWidth - 12)), b -> {
                AudioClient.start(emitter, source, radius, volume);
                onClose();
            }).bounds(x, panelY + 76 + i * 24, contentWidth, 20)
                    .tooltip(Tooltip.create(Component.literal(label))).build());
            button.active = emitter != null || AudioClient.wearing();
        }

        int footerY = panelY + 174;
        previousButton = addRenderableWidget(Button.builder(Component.literal("‹"), b -> changePage(-1)).bounds(x, footerY, 22, 20)
                .tooltip(Tooltip.create(t("previous"))).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal("›"), b -> changePage(1)).bounds(x + 26, footerY, 22, 20)
                .tooltip(Tooltip.create(t("next"))).build());
        int actionWidth = emitter == null ? contentWidth - 56 : (contentWidth - 60) / 2;
        stopButton = addRenderableWidget(Button.builder(t("stop"), b -> AudioClient.stop(emitter)).bounds(x + 56, footerY, actionWidth, 20).build());
        if (emitter != null) addRenderableWidget(Button.builder(t("disc"), b -> playDisc())
                .bounds(x + 60 + actionWidth, footerY, contentWidth - 60 - actionWidth, 20).build());
        addRenderableWidget(Button.builder(t("close"), b -> onClose()).bounds(width / 2 - 60, panelY + 198, 120, 20).build());
        updateButtons();
    }

    private void changePage(int direction) {
        page = Math.max(0, Math.min(pages() - 1, page + direction));
        layout();
    }

    private void updateButtons() {
        previousButton.active = page > 0;
        nextButton.active = page + 1 < pages();
        BoomboxState state = emitter == null ? null : AudioClient.stateOf(emitter);
        stopButton.active = emitter == null ? AudioClient.personal() : state != null && state.active();
    }

    private final class DeviceSlider extends AbstractSliderButton {
        private final boolean range;
        DeviceSlider(int x, int y, int width, boolean range) {
            super(x, y, width, 20, Component.empty(), range ? (radius - 10) / 22.0 : volume / 2.0);
            this.range = range;
            updateMessage();
            setTooltip(Tooltip.create(t(range ? "radius_hint" : "volume_hint")));
        }
        @Override protected void updateMessage() {
            setMessage(t(range ? "radius" : "volume", range ? radius : Math.round(volume * 100)));
        }
        @Override protected void applyValue() {
            if (range) radius = 10 + (int) Math.round(value * 22);
            else volume = Math.round(value * 200) / 100f;
        }
    }

    private void playDisc() {
        if (emitter == null || minecraft.player == null || minecraft.level == null) return;
        for (var stack : minecraft.player.getInventory().items) {
            var song = JukeboxSong.fromStack(minecraft.level.registryAccess(), stack);
            if (song.isEmpty() || song.get().unwrapKey().isEmpty()) continue;
            AudioClient.stop(null);
            String label = song.get().value().description().getString();
            if (label.length() > 64) label = label.substring(0, 64);
            PacketDistributor.sendToServer(new AudioNet.ControlC2S(emitter, true, BoomboxState.Mode.DISC,
                    Optional.of(song.get().unwrapKey().get().location()), radius, volume, label));
            onClose();
            return;
        }
        status = t("no_disc");
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        UiTheme.panel(graphics, panelX, panelY, panelWidth, PANEL_HEIGHT);
        graphics.drawCenteredString(font, title, width / 2, panelY + 10, UiTheme.ACCENT);
        Component note = emitter == null && !AudioClient.wearing() ? t("equip")
                : status.getString().isEmpty() ? t("sources", page + 1, pages()) : status;
        UiTheme.wrapped(graphics, font, note, panelX + 8, panelY + 52, panelWidth - 16, 2, UiTheme.MUTED);
        updateButtons();
        for (var widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
