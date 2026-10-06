package com.descentmtb;

import com.descentmtb.network.ModNetwork;
import com.descentmtb.network.Ragdolls;
import net.neoforged.neoforge.common.NeoForge;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.registry.ModEntities;
import com.descentmtb.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Descent MTB - main (common) mod entrypoint: the bike entity, item, creative
 * tab and network payloads. Client wiring lives in
 * {@link com.descentmtb.client.DescentMtbClient}; the physics in
 * {@link com.descentmtb.physics} (pure Java, unit-tested).
 */
@Mod(DescentMtb.MODID)
public class DescentMtb {
    public static final String MODID = "descentmtb";
    public static final Logger LOG = LogUtils.getLogger();

    public DescentMtb(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, com.descentmtb.trail.TrailConfig.SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.descentmtb.custom.CustomizationConfig.SPEC);
        com.descentmtb.registry.ModComponents.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlocks.register(modBus);
        ModNetwork.register(modBus);
        com.descentmtb.custom.BikeLights.registerEvents();
        com.descentmtb.custom.BikeBells.registerEvents();
        NeoForge.EVENT_BUS.addListener(com.descentmtb.custom.DevCustomTests::register);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.custom.DevCustomTests::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.trail.DevTrailTests::register);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.trail.DevPumpTrack::register);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.trail.DevManualBerm::register);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.world.DevSableTests::register);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.world.DevSableTests::tick);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.trail.TrailRecords::onClone);
        NeoForge.EVENT_BUS.addListener(com.descentmtb.trail.TrailSessions::onLogout);
        NeoForge.EVENT_BUS.addListener(Ragdolls::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(Ragdolls::onFall);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{
            Ragdolls.clearSession();
            com.descentmtb.trail.TrailEdit.clearSession();
        });
    }
}
