package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.item.MountainBikeItem;
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
            ITEMS.registerItem("mountain_bike", MountainBikeItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.descentmtb"))
                    .icon(() -> new ItemStack(MOUNTAIN_BIKE.get()))
                    .displayItems((params, output) -> {
                        output.accept(MOUNTAIN_BIKE.get());
                        ModBlocks.TAB_ITEMS.forEach(s -> output.accept(s.get()));
                    })
                    .build());

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
