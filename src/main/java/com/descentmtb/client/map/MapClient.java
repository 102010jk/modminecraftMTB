package com.descentmtb.client.map;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring of the GPS recorder and the bikepark map: ticks, packet handlers and screens. */
public final class MapClient {
    public static void setup() {
        com.descentmtb.map.TrailMapItem.viewer=stack->Minecraft.getInstance().setScreen(new TrailMapScreen(com.descentmtb.map.TrailMapItem.data(stack)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> GpsRecorderClient.tick(Minecraft.getInstance()));
    }

    private MapClient() {}
}
