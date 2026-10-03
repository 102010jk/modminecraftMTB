package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.ramp.TrailToolItem;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Ramp block, its block entity, and the Trail Tool. Call {@link #register(IEventBus)} from the mod constructor. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DescentMtb.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DescentMtb.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DescentMtb.MODID);

    public static final DeferredBlock<RampBlock> RAMP = BLOCKS.registerBlock("ramp", RampBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(0.8F)
                    .sound(SoundType.GRAVEL)
                    .noOcclusion()
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RampBlockEntity>> RAMP_BE =
            BLOCK_ENTITIES.register("ramp", () -> BlockEntityType.Builder.of(RampBlockEntity::new, RAMP.get()).build(null));

    public static final DeferredItem<BlockItem> RAMP_ITEM = ITEMS.registerSimpleBlockItem("ramp", RAMP);
    public static final DeferredItem<TrailToolItem> TRAIL_TOOL =
            ITEMS.registerItem("trail_tool", TrailToolItem::new, new Item.Properties().stacksTo(1));

    /** Items to show in the creative tab. */
    public static final List<Supplier<? extends ItemLike>> TAB_ITEMS = List.of(RAMP_ITEM, TRAIL_TOOL);

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
    }
}
