package com.descentmtb.client.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.network.TrailBestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client wiring of the trail signs: the trail timer (tick + HUD layer) and the server's personal-best replies.
 * The sign editor itself is opened through {@code TrailSignBlock.editor}, installed by {@link TrailClient}.
 */
public final class SignClient {
    public static void setup(IEventBus modBus) {
        TrailBestPayload.clientHandler = TrailTimer::onBest;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> TrailTimer.tick(Minecraft.getInstance()));
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_timer"),
                (graphics, tracker) -> TrailTimerHud.render(graphics)));
    }

    private SignClient() {}
}
