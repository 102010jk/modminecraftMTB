package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, DescentMtb.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MountainBikeEntity>> MOUNTAIN_BIKE =
            ENTITIES.register("mountain_bike", () -> EntityType.Builder
                    .<MountainBikeEntity>of(MountainBikeEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.0f)
                    // where the rider sits (x, y, z) in blocks relative to the bike origin
                    .passengerAttachments(new Vec3(0.0, 0.62, -0.12))
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("mountain_bike"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}
