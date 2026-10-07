package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.client.model.HardtailBikeModel;
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
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import com.descentmtb.network.RagdollPayload;
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
        modBus.addListener(com.descentmtb.client.shaped.ShapedGeometry::register);
        modBus.addListener(com.descentmtb.client.shaped.ShapedGeometry::registerColors);
        modBus.addListener(this::registerLayers);
        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::registerGuiLayers);

        com.descentmtb.client.trail.TrailClient.setup();
        com.descentmtb.client.trail.SignClient.setup(modBus);
        com.descentmtb.client.map.MapClient.setup();
        com.descentmtb.client.audio.AudioClient.setup();
        RideEffects.setup();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->com.descentmtb.client.trail.TrailClient.tick());
        NeoForge.EVENT_BUS.addListener(com.descentmtb.client.trail.ShapingHighlight::render);
        MountainBikeEntity.clientTicker = BikeClientController::tick;
        RagdollPayload.clientHandler = RagdollClient::onPayload;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> RagdollClient.tick());
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e) -> {
            if (RagdollClient.localLocked()) {
                e.getInput().forwardImpulse = 0;
                e.getInput().leftImpulse = 0;
                e.getInput().jumping = false;
                e.getInput().shiftKeyDown = false;
                e.getInput().up = e.getInput().down = e.getInput().left = e.getInput().right = false;
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> BikeClientController.checkDismount());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> BikeInputHandler.clientTick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> BikeInputHandler.preTick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> com.descentmtb.client.custom.BikeBellClient.tick());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> com.descentmtb.client.sound.BikeSoundController.tick());
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFov e) -> {
            if (e.usedConfiguredFov()) e.setFOV(e.getFOV() + BikeCamera.fovBoost());
        });
        NeoForge.EVENT_BUS.addListener((RenderHandEvent e) -> {
            if (BikeCamera.helmet()) e.setCanceled(true);
        });
        // Trail Shaper: Shift + wheel cycles the modes of the current category
        NeoForge.EVENT_BUS.addListener(com.descentmtb.client.trail.TrailClient::onScroll);
        // Trail Shaper: right-clicks that open the block editor or the jump screen
        NeoForge.EVENT_BUS.addListener(com.descentmtb.client.trail.TrailClient::onInteract);
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
            if (RagdollClient.transform(e.getEntity(), e.getPoseStack(), e.getPartialTick())) return;
            if (e.getEntity().getVehicle() instanceof MountainBikeEntity bike) {
                BikeRenderState a = bike.rsPrev, b = bike.rsCur;
                double t = e.getPartialTick();
                double yaw = BikeRenderState.lerp(t, a.yaw, b.yaw);
                PoseStack pose = e.getPoseStack();
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation((float) (Math.PI - yaw)));
                pose.mulPose(Axis.XP.rotation((float) BikeRenderState.lerp(t, a.pitch, b.pitch)));
                pose.mulPose(Axis.ZP.rotation((float) -BikeRenderState.lerp(t, a.riderLean, b.riderLean)));
                pose.mulPose(Axis.YP.rotation((float) -(Math.PI - yaw)));
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Post e) -> {
            if (RagdollClient.active(e.getEntity()) || e.getEntity().getVehicle() instanceof MountainBikeEntity) {
                e.getPoseStack().popPose();
            }
        });
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOUNTAIN_BIKE.get(), MountainBikeRenderer::new);
        event.registerBlockEntityRenderer(com.descentmtb.registry.ModBlocks.SIGN_BE.get(),com.descentmtb.client.trail.TrailSignRenderer::new);
        event.registerBlockEntityRenderer(com.descentmtb.registry.ModBlocks.POST_BE.get(),com.descentmtb.client.tape.BarrierPostRenderer::new);
    }

    private void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(EnduroBikeModel.LAYER, EnduroBikeModel::createLayer);
        event.registerLayerDefinition(HardtailBikeModel.LAYER, HardtailBikeModel::createLayer);
        event.registerLayerDefinition(com.descentmtb.client.model.DirtBikeModel.LAYER, com.descentmtb.client.model.DirtBikeModel::createLayer);
        event.registerLayerDefinition(com.descentmtb.client.model.PitBikeModel.LAYER, com.descentmtb.client.model.PitBikeModel::createLayer);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : ModKeyMappings.ALL) event.register(k);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR,ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID,"immersion"),
                (graphics,tracker)->RideEffects.render(graphics,tracker.getGameTimeDeltaPartialTick(false)));
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail"),
                (graphics, tracker) -> com.descentmtb.client.trail.TrailClient.hud(graphics));
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "speed"), new SpeedHud());
    }
}
