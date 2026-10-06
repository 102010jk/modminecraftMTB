package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.entity.BikeType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DescentMtb.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DescentMtb.MODID);

    public static final DeferredItem<MountainBikeItem> MOUNTAIN_BIKE =
            ITEMS.registerItem("mountain_bike",
                    p -> new MountainBikeItem(p.component(ModComponents.BIKE_BUILD.get(), BikeBuild.ENDURO_DEFAULT)),
                    new Item.Properties().stacksTo(1));
    public static final DeferredItem<MountainBikeItem> HARDTAIL_BIKE =
            ITEMS.registerItem("hardtail_bike",
                    p -> new MountainBikeItem(p.component(ModComponents.BIKE_BUILD.get(), BikeBuild.HARDTAIL_DEFAULT), BikeType.HARDTAIL),
                    new Item.Properties().stacksTo(1));
    public static final DeferredItem<com.descentmtb.item.BikePumpItem> BIKE_PUMP =
            ITEMS.registerItem("bike_pump", com.descentmtb.item.BikePumpItem::new, new Item.Properties().stacksTo(1));
    /** The GPS unit: records a ride as a trail track, see {@link com.descentmtb.map.TrailMarkerItem}. */
    public static final DeferredItem<com.descentmtb.map.TrailMarkerItem> TRAIL_GPS =
            ITEMS.registerItem("trail_gps", com.descentmtb.map.TrailMarkerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.descentmtb"))
                    .icon(() -> new ItemStack(MOUNTAIN_BIKE.get()))
                    .displayItems((params, output) -> {
                        output.accept(MOUNTAIN_BIKE.get());
                        output.accept(HARDTAIL_BIKE.get());
                        output.accept(BIKE_PUMP.get());
                        output.accept(TRAIL_GPS.get());
                        ModBlocks.TAB_ITEMS.forEach(s -> output.accept(s.get()));
                    })
                    .build());

    /** The bike item of a type. */
    public static MountainBikeItem itemFor(BikeType type) {
        return (type == BikeType.HARDTAIL ? HARDTAIL_BIKE : MOUNTAIN_BIKE).get();
    }

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
