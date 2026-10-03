package com.descentmtb.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("4");
        r.playToServer(RagdollRecoveryPayload.TYPE,RagdollRecoveryPayload.CODEC,RagdollRecoveryPayload::handle);
        r.playToServer(TrailActionPayload.TYPE,TrailActionPayload.CODEC,TrailActionPayload::handle);
        r.playToServer(SignArtPayload.TYPE,SignArtPayload.CODEC,SignArtPayload::handle);
        r.playToClient(TrailPreviewPayload.TYPE,TrailPreviewPayload.CODEC,(m,ctx)->TrailPreviewPayload.clientHandler.accept(m));
        r.playToServer(BikeStatePayload.TYPE, BikeStatePayload.CODEC, BikeStatePayload::handle);
        r.playToServer(BikeBailPayload.TYPE, BikeBailPayload.CODEC, BikeBailPayload::handle);
        r.playToClient(RagdollPayload.TYPE, RagdollPayload.CODEC, (m, ctx) -> RagdollPayload.clientHandler.accept(m));
    }

    private ModNetwork() {}
}
