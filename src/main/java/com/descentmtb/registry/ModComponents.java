package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.MotoBuild;
import com.descentmtb.custom.MotoBuildCodecs;
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

    /** Paint and tuning of a motorbike (dirt bike, pit bike): carried by the bike item, the entity and the stand. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MotoBuild>> MOTO_BUILD =
            COMPONENTS.register("moto_build", () -> DataComponentType.<MotoBuild>builder()
                    .persistent(MotoBuildCodecs.CODEC)
                    .networkSynchronized(MotoBuildCodecs.STREAM_CODEC)
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
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<com.descentmtb.map.BikeparkMap>> BIKEPARK_MAP =
            COMPONENTS.register("bikepark_map",()->DataComponentType.<com.descentmtb.map.BikeparkMap>builder().persistent(com.descentmtb.map.BikeparkMap.CODEC).networkSynchronized(com.descentmtb.map.BikeparkMap.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<net.minecraft.resources.ResourceLocation>> TRACK_DIMENSION =
            COMPONENTS.register("track_dimension",()->DataComponentType.<net.minecraft.resources.ResourceLocation>builder().persistent(net.minecraft.resources.ResourceLocation.CODEC).networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>,DataComponentType<net.minecraft.resources.ResourceLocation>> RECORD_DIMENSION =
            COMPONENTS.register("record_dimension",()->DataComponentType.<net.minecraft.resources.ResourceLocation>builder().persistent(net.minecraft.resources.ResourceLocation.CODEC).networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC).build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
