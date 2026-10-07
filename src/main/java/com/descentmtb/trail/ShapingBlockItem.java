package com.descentmtb.trail;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Placeable trail dirt / deck. Placing always makes a FULL block (all four corners at 1.0) and never changes
 * its neighbours; the shape is made afterwards with the Trail Shaper ({@link ShapeToolItem}). A deck is made of
 * the planks held in the off-hand ({@link #deckMaterial}) and is built without supports.
 */
public final class ShapingBlockItem extends BlockItem {
    private static final double[] FULL_BLOCK = {1, 1, 1, 1};

    private final boolean deck;

    public ShapingBlockItem(Block block, Properties properties, boolean deck) {
        super(block, properties);
        this.deck = deck;
    }

    @Override
    public String getDescriptionId() {
        return net.minecraft.Util.makeDescriptionId("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this));
    }

    public boolean isDeck() {
        return deck;
    }

    /**
     * The wood a deck is made of when {@code player} places it: the planks in the off-hand (any block tagged
     * {@code minecraft:planks}), oak when there are none.
     */
    public static BlockState deckMaterial(Player player) {
        if (player != null && player.getOffhandItem().getItem() instanceof BlockItem planks) {
            BlockState state = planks.getBlock().defaultBlockState();
            if (state.is(BlockTags.PLANKS)) {
                return state;
            }
        }
        return com.descentmtb.registry.ModBlocks.defaultDeckWood();
    }

    /** A deck gets no posts of its own: they are placed by hand (Wooden support). */
    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        // Also on the placing client: its predicted block shows the finished full shape straight away instead of a
        // flat sliver until the server's block entity arrives (which then simply confirms it).
        if (!placed || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TrailSurfaceEntity shaped)) {
            return placed;
        }
        shaped.setShape(FULL_BLOCK.clone(), deck);
        shaped.setMaterial(deck ? deckMaterial(context.getPlayer()) : com.descentmtb.registry.ModBlocks.defaultTrailDirt(), false);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.shaping.hint.place"));
        if (deck) {
            lines.add(Component.translatable("descentmtb.shaping.hint.deck"));
        }
    }
}
