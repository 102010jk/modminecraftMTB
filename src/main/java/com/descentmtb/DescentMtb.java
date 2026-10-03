package com.descentmtb;

import com.descentmtb.network.ModNetwork;
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

    public DescentMtb(IEventBus modBus) {
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModNetwork.register(modBus);
    }
}
