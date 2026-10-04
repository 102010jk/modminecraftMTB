package com.descentmtb.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("7");
        r.playToServer(ShapeTunePayload.TYPE,ShapeTunePayload.CODEC,ShapeTunePayload::handle);
        r.playToServer(RagdollRecoveryPayload.TYPE,RagdollRecoveryPayload.CODEC,RagdollRecoveryPayload::handle);
        r.playToServer(TrailUndoPayload.TYPE, TrailUndoPayload.CODEC, TrailUndoPayload::handle);
        r.playToServer(SignContentPayload.TYPE, SignContentPayload.CODEC, SignContentPayload::handle);
        r.playToServer(TrailTimePayload.TYPE, TrailTimePayload.CODEC, TrailTimePayload::handle);
        r.playToClient(TrailBestPayload.TYPE, TrailBestPayload.CODEC, (m, ctx) -> TrailBestPayload.clientHandler.accept(m));
        r.playToServer(BikeStatePayload.TYPE, BikeStatePayload.CODEC, BikeStatePayload::handle);
        r.playToServer(BikeBailPayload.TYPE, BikeBailPayload.CODEC, BikeBailPayload::handle);
        r.playToClient(RagdollPayload.TYPE, RagdollPayload.CODEC, (m, ctx) -> RagdollPayload.clientHandler.accept(m));
    }

    private ModNetwork() {}
}
