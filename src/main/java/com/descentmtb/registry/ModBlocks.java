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

/** Ramp block (no item any more), trail surface blocks, the Trail Shaper and the decoration blocks. Call {@link #register(IEventBus)} from the mod constructor. */
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

    public static final DeferredBlock<com.descentmtb.trail.TrailSurfaceBlock> TRAIL_SURFACE = BLOCKS.registerBlock("trail_surface", com.descentmtb.trail.TrailSurfaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(.8f).sound(SoundType.GRAVEL).noOcclusion().dynamicShape()
                    .isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.descentmtb.trail.TrailSurfaceEntity>> TRAIL_BE =
            BLOCK_ENTITIES.register("trail_surface", () -> BlockEntityType.Builder.of(com.descentmtb.trail.TrailSurfaceEntity::new, TRAIL_SURFACE.get()).build(null));
    /** The one editing tool: shapes a single block (jump, berm and manual presets), see {@link com.descentmtb.trail.ShapeToolItem}. */
    public static final DeferredItem<com.descentmtb.trail.ShapeToolItem> TRAIL_SHOVEL = ITEMS.registerItem(
            "trail_shovel", com.descentmtb.trail.ShapeToolItem::new, new Item.Properties().stacksTo(1));
    /** Hand-sculpted shaped blocks: no tool needed, see {@link com.descentmtb.trail.ShapingBlockItem}. */
    public static final DeferredItem<com.descentmtb.trail.ShapingBlockItem> TRAIL_DIRT = ITEMS.registerItem(
            "trail_dirt", p -> new com.descentmtb.trail.ShapingBlockItem(TRAIL_SURFACE.get(), p, false), new Item.Properties());
    public static final DeferredItem<com.descentmtb.trail.ShapingBlockItem> TRAIL_DECK = ITEMS.registerItem(
            "trail_deck", p -> new com.descentmtb.trail.ShapingBlockItem(TRAIL_SURFACE.get(), p, true), new Item.Properties());
    public static final DeferredBlock<com.descentmtb.trail.WoodSupportBlock> WOOD_SUPPORT=BLOCKS.registerBlock("wood_support",com.descentmtb.trail.WoodSupportBlock::new,BlockBehaviour.Properties.of().strength(1f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> SUPPORT_ITEM=ITEMS.registerSimpleBlockItem("wood_support",WOOD_SUPPORT);
    public static final DeferredBlock<com.descentmtb.trail.AirbagBlock> AIRBAG=BLOCKS.registerBlock("airbag",com.descentmtb.trail.AirbagBlock::new,BlockBehaviour.Properties.of().strength(.5f).sound(SoundType.WOOL));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> CLOTH_BARRIER=BLOCKS.registerBlock("cloth_barrier",net.minecraft.world.level.block.Block::new,BlockBehaviour.Properties.of().strength(.2f).sound(SoundType.WOOL).noOcclusion().noCollission());
    public static final DeferredBlock<com.descentmtb.trail.TrailSignBlock> TRAIL_SIGN=BLOCKS.registerBlock("trail_sign",com.descentmtb.trail.TrailSignBlock::new,BlockBehaviour.Properties.of().strength(.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<com.descentmtb.trail.TrailSignEntity>> SIGN_BE=BLOCK_ENTITIES.register("trail_sign",()->BlockEntityType.Builder.of(com.descentmtb.trail.TrailSignEntity::new,TRAIL_SIGN.get()).build(null));
    public static final DeferredItem<BlockItem> AIRBAG_ITEM=ITEMS.registerSimpleBlockItem("airbag",AIRBAG);
    /** The trail tape roll (it kept the id of the old cloth barrier item, so old inventories still load); see {@link com.descentmtb.tape.TrailTapeItem}. */
    public static final DeferredItem<com.descentmtb.tape.TrailTapeItem> BARRIER_ITEM=ITEMS.registerItem("cloth_barrier",com.descentmtb.tape.TrailTapeItem::new,new Item.Properties());
    /** The stake trail tape is strung between; it has no item of its own. The old {@link #CLOTH_BARRIER} block stays registered so worlds load. */
    public static final DeferredBlock<com.descentmtb.tape.BarrierPostBlock> BARRIER_POST=BLOCKS.registerBlock("barrier_post",com.descentmtb.tape.BarrierPostBlock::new,BlockBehaviour.Properties.of().strength(.3f).sound(SoundType.WOOD).noOcclusion().noCollission());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<com.descentmtb.tape.BarrierPostEntity>> POST_BE=BLOCK_ENTITIES.register("barrier_post",()->BlockEntityType.Builder.of(com.descentmtb.tape.BarrierPostEntity::new,BARRIER_POST.get()).build(null));
    public static final DeferredItem<BlockItem> SIGN_ITEM=ITEMS.registerSimpleBlockItem("trail_sign",TRAIL_SIGN);

    public static final DeferredBlock<com.descentmtb.trail.TrailObstacleBlock> TRAIL_ROOTS=BLOCKS.registerBlock("trail_roots",p->new com.descentmtb.trail.TrailObstacleBlock(p,false),BlockBehaviour.Properties.of().strength(.3f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<com.descentmtb.trail.TrailObstacleBlock> TRAIL_ROCK=BLOCKS.registerBlock("trail_rock",p->new com.descentmtb.trail.TrailObstacleBlock(p,true),BlockBehaviour.Properties.of().strength(.8f).sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<com.descentmtb.trail.SurfaceOverlayItem> ROOT_ITEM=ITEMS.registerItem("trail_roots",p->new com.descentmtb.trail.SurfaceOverlayItem(TRAIL_ROOTS.get(),p,1),new Item.Properties());
    public static final DeferredItem<com.descentmtb.trail.SurfaceOverlayItem> ROCK_ITEM=ITEMS.registerItem("trail_rock",p->new com.descentmtb.trail.SurfaceOverlayItem(TRAIL_ROCK.get(),p,2),new Item.Properties());
    /** The bike work stand: hang a bike on it and open the workshop to customise it. */
    public static final DeferredBlock<com.descentmtb.custom.BikeStandBlock> BIKE_STAND=BLOCKS.registerBlock("bike_stand",com.descentmtb.custom.BikeStandBlock::new,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<com.descentmtb.custom.BikeStandBlockEntity>> BIKE_STAND_BE=BLOCK_ENTITIES.register("bike_stand",()->BlockEntityType.Builder.of(com.descentmtb.custom.BikeStandBlockEntity::new,BIKE_STAND.get()).build(null));
    public static final DeferredItem<BlockItem> BIKE_STAND_ITEM=ITEMS.registerSimpleBlockItem("bike_stand",BIKE_STAND);
    public static final DeferredBlock<com.descentmtb.audio.BoomboxBlock> BOOMBOX = BLOCKS.registerBlock("boombox",com.descentmtb.audio.BoomboxBlock::new,BlockBehaviour.Properties.of().strength(1f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> BOOMBOX_ITEM = ITEMS.registerSimpleBlockItem("boombox",BOOMBOX);
    /** Tamped trail dirt with pebbles: the default material of hand-built trail surfaces. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> PACKED_TRAIL_DIRT = BLOCKS.registerBlock("packed_trail_dirt",
            net.minecraft.world.level.block.Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F).sound(SoundType.ROOTED_DIRT));
    public static final DeferredItem<BlockItem> PACKED_TRAIL_DIRT_ITEM = ITEMS.registerSimpleBlockItem("packed_trail_dirt", PACKED_TRAIL_DIRT);
    /** Nailed deck boards (tagged as planks): the default wood of decks and north-shore. */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> TRAIL_BOARDS = BLOCKS.registerBlock("trail_boards",
            net.minecraft.world.level.block.Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredItem<BlockItem> TRAIL_BOARDS_ITEM = ITEMS.registerSimpleBlockItem("trail_boards", TRAIL_BOARDS);

    /** The dirt a trail surface is made of when nothing else was chosen. */
    public static net.minecraft.world.level.block.state.BlockState defaultTrailDirt() {
        return PACKED_TRAIL_DIRT.get().defaultBlockState();
    }

    /** The wood a deck is made of when the builder holds no planks. */
    public static net.minecraft.world.level.block.state.BlockState defaultDeckWood() {
        return TRAIL_BOARDS.get().defaultBlockState();
    }

    public static final List<Supplier<? extends ItemLike>> TAB_ITEMS = List.of(TRAIL_SHOVEL, TRAIL_DIRT, TRAIL_DECK, ROOT_ITEM, ROCK_ITEM, SUPPORT_ITEM, AIRBAG_ITEM, BARRIER_ITEM, SIGN_ITEM, BIKE_STAND_ITEM, BOOMBOX_ITEM, PACKED_TRAIL_DIRT_ITEM, TRAIL_BOARDS_ITEM);

    private ModBlocks() {}

    /** Items that were merged into the Trail Shaper; old inventories and chests still load. */
    private static final String[] RETIRED_TOOLS = {
            "trail_wand", "clearing_tool", "trail_hammer", "trail_tool",
            "berm_tool", "route_tool", "boardwalk_tool", "roller_tool", "undo_tool", "measure_tool",
            "clone_tool", "obstacle_tool"};

    /** Items that became plain Trail Dirt (the ramp block itself stays registered so old worlds load). */
    private static final String[] RETIRED_BUILDING_ITEMS = {"ramp", "trail_stake"};

    public static void register(IEventBus bus) {
        for (String retired : RETIRED_TOOLS) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, retired),
                    ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_shovel"));
        }
        for (String retired : RETIRED_BUILDING_ITEMS) {
            ITEMS.addAlias(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, retired),
                    ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_dirt"));
        }
        // the old stake block became the tape post: placed stakes keep standing instead of vanishing from old worlds
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_stake"),
                ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "barrier_post"));
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
    }
}
