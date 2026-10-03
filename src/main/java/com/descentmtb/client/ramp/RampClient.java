package com.descentmtb.client.ramp;

import com.descentmtb.registry.ModBlocks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client wiring for the ramp block. Call {@link #registerRenderers} from the mod-bus RegisterRenderers event. */
public final class RampClient {
    private RampClient() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(ModBlocks.RAMP_BE.get(), RampRenderer::new);
    }
}
