package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only companion mod (same id, {@code dist = CLIENT}): renderer, model
 * layer, key mappings, HUD, camera hooks, and the per-bike client ticker.
 */
@Mod(value = DescentMtb.MODID, dist = Dist.CLIENT)
public final class DescentMtbClient {

    public DescentMtbClient(IEventBus modBus) {
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerLayers);
        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::registerGuiLayers);

        MountainBikeEntity.clientTicker = BikeClientController::tick;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> BikeClientController.checkDismount());
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFov e) -> {
            if (e.usedConfiguredFov()) e.setFOV(e.getFOV() + BikeCamera.fovBoost());
        });
        NeoForge.EVENT_BUS.addListener((RenderHandEvent e) -> {
            if (BikeCamera.helmet()) e.setCanceled(true);
        });
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOUNTAIN_BIKE.get(), MountainBikeRenderer::new);
    }

    private void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EnduroBikeModel.LAYER, EnduroBikeModel::createLayer);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : ModKeyMappings.ALL) event.register(k);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "speed"), new SpeedHud());
    }
}
