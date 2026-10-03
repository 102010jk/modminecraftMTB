package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-only companion mod (same id, {@code dist = CLIENT}). Wires up the
 * renderer, model layer, key mappings and the speed HUD on the mod event bus.
 * The controller/keyboard polling lives in {@link BikeInputHandler} on the game
 * bus via {@code @EventBusSubscriber}.
 */
@Mod(value = DescentMtb.MODID, dist = Dist.CLIENT)
public final class DescentMtbClient {

    public DescentMtbClient(IEventBus modBus) {
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerLayers);
        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::registerGuiLayers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOUNTAIN_BIKE.get(), MountainBikeRenderer::new);
    }

    private void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                com.descentmtb.client.model.MountainBikeModel.LAYER,
                com.descentmtb.client.model.MountainBikeModel::createLayer);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.BRAKE);
        event.register(ModKeyMappings.LEAN_LEFT);
        event.register(ModKeyMappings.LEAN_RIGHT);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "speed"),
                new SpeedHud());
    }
}
