package com.descentmtb;

import com.descentmtb.registry.ModEntities;
import com.descentmtb.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Descent MTB - main (common) mod entrypoint.
 *
 * Registers the bike entity, the bike item and a creative tab. All client-only
 * wiring (renderer, model, key mappings, controller polling, HUD) lives in
 * {@link com.descentmtb.client.DescentMtbClient}, which is a separate
 * {@code @Mod(dist = CLIENT)} companion so this class stays server-safe.
 */
@Mod(DescentMtb.MODID)
public class DescentMtb {
    public static final String MODID = "descentmtb";

    public DescentMtb(IEventBus modBus) {
        ModItems.register(modBus);
        ModEntities.register(modBus);
    }
}
