package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
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
import net.minecraft.resources.ResourceLocation;
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
    public static final DeferredBlock<com.descentmtb.trail.TrailSurfaceBlock> TRAIL_SURFACE = BLOCKS.registerBlock("trail_surface", com.descentmtb.trail.TrailSurfaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(.8f).sound(SoundType.GRAVEL).noOcclusion().dynamicShape()
                    .isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.descentmtb.trail.TrailSurfaceEntity>> TRAIL_BE =
            BLOCK_ENTITIES.register("trail_surface", () -> BlockEntityType.Builder.of(com.descentmtb.trail.TrailSurfaceEntity::new, TRAIL_SURFACE.get()).build(null));
    public static final DeferredItem<com.descentmtb.trail.TrailWandItem> TRAIL_WAND=ITEMS.registerItem("trail_wand",com.descentmtb.trail.TrailWandItem::new,new Item.Properties().stacksTo(1));
    /** Tool 2: fells trees (6x speed on logs and leaves) and lightly tidies the ground. */
    public static final DeferredItem<com.descentmtb.trail.ClearingToolItem> CLEARING_TOOL = ITEMS.registerItem(
            "clearing_tool", com.descentmtb.trail.ClearingToolItem::new,
            new Item.Properties().stacksTo(1).durability(250).component(net.minecraft.core.component.DataComponents.TOOL,
                    new net.minecraft.world.item.component.Tool(List.of(
                            net.minecraft.world.item.component.Tool.Rule.overrideSpeed(net.minecraft.tags.BlockTags.LOGS, 6F),
                            net.minecraft.world.item.component.Tool.Rule.overrideSpeed(net.minecraft.tags.BlockTags.LEAVES, 6F)),
                            1.0F, 1)));
    public static final DeferredBlock<com.descentmtb.trail.WoodSupportBlock> WOOD_SUPPORT=BLOCKS.registerBlock("wood_support",com.descentmtb.trail.WoodSupportBlock::new,BlockBehaviour.Properties.of().strength(1f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> SUPPORT_ITEM=ITEMS.registerSimpleBlockItem("wood_support",WOOD_SUPPORT);
    public static final DeferredBlock<com.descentmtb.trail.AirbagBlock> AIRBAG=BLOCKS.registerBlock("airbag",com.descentmtb.trail.AirbagBlock::new,BlockBehaviour.Properties.of().strength(.5f).sound(SoundType.WOOL));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CLOTH_BARRIER=BLOCKS.registerBlock("cloth_barrier",net.minecraft.world.level.block.Block::new,BlockBehaviour.Properties.of().strength(.2f).sound(SoundType.WOOL).noOcclusion().noCollission());
    public static final DeferredBlock<com.descentmtb.trail.TrailSignBlock> TRAIL_SIGN=BLOCKS.registerBlock("trail_sign",com.descentmtb.trail.TrailSignBlock::new,BlockBehaviour.Properties.of().strength(.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<com.descentmtb.trail.TrailSignEntity>> SIGN_BE=BLOCK_ENTITIES.register("trail_sign",()->BlockEntityType.Builder.of(com.descentmtb.trail.TrailSignEntity::new,TRAIL_SIGN.get()).build(null));
    public static final DeferredItem<BlockItem> AIRBAG_ITEM=ITEMS.registerSimpleBlockItem("airbag",AIRBAG);
    public static final DeferredItem<BlockItem> BARRIER_ITEM=ITEMS.registerSimpleBlockItem("cloth_barrier",CLOTH_BARRIER);
    public static final DeferredItem<BlockItem> SIGN_ITEM=ITEMS.registerSimpleBlockItem("trail_sign",TRAIL_SIGN);

    public static final DeferredBlock<com.descentmtb.trail.TrailObstacleBlock> TRAIL_ROOTS=BLOCKS.registerBlock("trail_roots",p->new com.descentmtb.trail.TrailObstacleBlock(p,false),BlockBehaviour.Properties.of().strength(.3f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<com.descentmtb.trail.TrailObstacleBlock> TRAIL_ROCK=BLOCKS.registerBlock("trail_rock",p->new com.descentmtb.trail.TrailObstacleBlock(p,true),BlockBehaviour.Properties.of().strength(.8f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> ROOT_ITEM=ITEMS.registerSimpleBlockItem("trail_roots",TRAIL_ROOTS);
    public static final DeferredItem<BlockItem> ROCK_ITEM=ITEMS.registerSimpleBlockItem("trail_rock",TRAIL_ROCK);
    public static final DeferredBlock<net.minecraft.world.level.block.Block> TRAIL_STAKE=BLOCKS.registerBlock("trail_stake",net.minecraft.world.level.block.Block::new,
            BlockBehaviour.Properties.of().strength(.2f).sound(SoundType.WOOD).noOcclusion().noCollission());
    public static final DeferredItem<BlockItem> STAKE_ITEM=ITEMS.registerSimpleBlockItem("trail_stake",TRAIL_STAKE);
    public static final List<Supplier<? extends ItemLike>> TAB_ITEMS = List.of(TRAIL_WAND, CLEARING_TOOL, RAMP_ITEM, STAKE_ITEM, ROOT_ITEM, ROCK_ITEM, SUPPORT_ITEM, AIRBAG_ITEM, BARRIER_ITEM, SIGN_ITEM);

    private ModBlocks() {}

    /** Items of v1.0 that were merged into the Trail Builder; old inventories and chests still load. */
    private static final String[] RETIRED_TOOLS = {
            "berm_tool", "route_tool", "boardwalk_tool", "roller_tool", "undo_tool", "measure_tool",
            "clone_tool", "obstacle_tool", "trail_tool"};

    public static void register(IEventBus bus) {
        for (String retired : RETIRED_TOOLS) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, retired),
                    ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_wand"));
        }
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
    }
}
