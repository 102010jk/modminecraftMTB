package com.descentmtb.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegister);
        RiderSessions.registerEvents();
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("11");
        r.playToClient(WorkshopResultPayload.TYPE,WorkshopResultPayload.CODEC,WorkshopResultPayload::handle);
        r.playToServer(ShapeTunePayload.TYPE,ShapeTunePayload.CODEC,ShapeTunePayload::handle);
        r.playToServer(RagdollRecoveryPayload.TYPE,RagdollRecoveryPayload.CODEC,RagdollRecoveryPayload::handle);
        r.playToServer(TrailUndoPayload.TYPE, TrailUndoPayload.CODEC, TrailUndoPayload::handle);
        r.playToServer(JumpBuildPayload.TYPE, JumpBuildPayload.CODEC, JumpBuildPayload::handle);
        r.playToServer(BlockEditPayload.TYPE, BlockEditPayload.CODEC, BlockEditPayload::handle);
        r.playToServer(SignContentPayload.TYPE, SignContentPayload.CODEC, SignContentPayload::handle);
        r.playToServer(TrailTimePayload.TYPE, TrailTimePayload.CODEC, TrailTimePayload::handle);
        r.playToClient(TrailBestPayload.TYPE, TrailBestPayload.CODEC, (m, ctx) -> TrailBestPayload.clientHandler.accept(m));
        r.playToServer(BikeStatePayload.TYPE, BikeStatePayload.CODEC, BikeStatePayload::handle);
        r.playToServer(BikeBailPayload.TYPE, BikeBailPayload.CODEC, BikeBailPayload::handle);
        r.playToServer(BikeRespawnPayload.TYPE, BikeRespawnPayload.CODEC, BikeRespawnPayload::handle);
        r.playToServer(BikeStartPointPayload.TYPE, BikeStartPointPayload.CODEC, BikeStartPointPayload::handle);
        r.playToClient(BikeResyncPayload.TYPE, BikeResyncPayload.CODEC, (m, ctx) -> BikeResyncPayload.clientHandler.accept(m));
        r.playToServer(WorkshopApplyPayload.TYPE, WorkshopApplyPayload.CODEC, WorkshopApplyPayload::handle);
        r.playToServer(MotoApplyPayload.TYPE, MotoApplyPayload.CODEC, MotoApplyPayload::handle);
        r.playToClient(MotoResultPayload.TYPE, MotoResultPayload.CODEC, MotoResultPayload::handle);
        r.playToServer(WorkshopTakePayload.TYPE, WorkshopTakePayload.CODEC, WorkshopTakePayload::handle);
        r.playToServer(BikeBellPayload.TYPE, BikeBellPayload.CODEC, BikeBellPayload::handle);
        r.playToServer(TrackUploadPayload.TYPE, TrackUploadPayload.CODEC, TrackUploadPayload::handle);
        r.playToClient(RagdollPayload.TYPE, RagdollPayload.CODEC, (m, ctx) -> RagdollPayload.clientHandler.accept(m));
    }

    private ModNetwork() {}
}
