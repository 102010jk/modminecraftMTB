package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeBuild;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data components of the mod. */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DescentMtb.MODID);

    /** What the player chose for this bike: carried by the bike item, the entity and the stand. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BikeBuild>> BIKE_BUILD =
            COMPONENTS.register("bike_build", () -> DataComponentType.<BikeBuild>builder()
                    .persistent(BikeBuild.CODEC)
                    .networkSynchronized(BikeBuild.STREAM_CODEC)
                    .build());

    /** A finished GPS track recorded with the GPS unit ({@code trail_gps}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.descentmtb.map.TrailTrack>> TRAIL_TRACK =
            COMPONENTS.register("trail_track", () -> DataComponentType.<com.descentmtb.map.TrailTrack>builder()
                    .persistent(com.descentmtb.map.TrailTrack.CODEC)
                    .networkSynchronized(com.descentmtb.map.TrailTrack.STREAM_CODEC)
                    .build());

    /** Whether the GPS unit is recording, and which recording it is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.descentmtb.map.GpsState>> GPS_STATE =
            COMPONENTS.register("gps_state", () -> DataComponentType.<com.descentmtb.map.GpsState>builder()
                    .persistent(com.descentmtb.map.GpsState.CODEC)
                    .networkSynchronized(com.descentmtb.map.GpsState.STREAM_CODEC)
                    .build());

    private ModComponents() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
