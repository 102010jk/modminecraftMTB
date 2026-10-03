package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import com.descentmtb.entity.BikeRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only companion mod (same id, {@code dist = CLIENT}): renderer, model
 * layer, key mappings, HUD, camera hooks, and the per-bike client ticker.
 */
@Mod(value = DescentMtb.MODID, dist = Dist.CLIENT)
public final class DescentMtbClient {

    public DescentMtbClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
        // no digging / placing while riding (Steam Input maps the triggers to mouse clicks!)
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (BikeClientController.riding() != null) {
                e.setSwingHand(false);
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (BikeClientController.riding() != null && e.getName().equals(VanillaGuiLayers.CROSSHAIR)) e.setCanceled(true);
        });
        // the rider pitches and leans with the bike (rotation about the pedals)
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre e) -> {
            if (e.getEntity().getVehicle() instanceof MountainBikeEntity bike) {
                BikeRenderState a = bike.rsPrev, b = bike.rsCur;
                double t = e.getPartialTick();
                double yaw = BikeRenderState.lerp(t, a.yaw, b.yaw);
                PoseStack pose = e.getPoseStack();
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation((float) (Math.PI - yaw)));
                pose.mulPose(Axis.XP.rotation((float) BikeRenderState.lerp(t, a.pitch, b.pitch)));
                pose.mulPose(Axis.ZP.rotation((float) -BikeRenderState.lerp(t, a.lean, b.lean)));
                pose.mulPose(Axis.YP.rotation((float) -(Math.PI - yaw)));
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> {
            if (e.getEntity().getVehicle() instanceof MountainBikeEntity) e.getPoseStack().popPose();
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
