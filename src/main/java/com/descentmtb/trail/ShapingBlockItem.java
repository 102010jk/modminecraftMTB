package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Placeable trail dirt / deck. Placing always makes a FULL block (all four corners at 1.0) and never changes
 * its neighbours; the shape is made afterwards with the Trail Shaper ({@link ShapeToolItem}).
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

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (!placed || context.getLevel().isClientSide
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TrailSurfaceEntity shaped)) {
            return placed;
        }
        BlockPos pos = context.getClickedPos();
        shaped.setShape(FULL_BLOCK.clone(), deck);
        shaped.setMaterial(deck ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), false);
        if (deck && context.getPlayer() instanceof ServerPlayer player) {
            addDeckSupports(context, state, shaped, pos, player);
        }
        return true;
    }

    /** A deck placed above the ground gets posts down to it (see {@link DeckSupports}). */
    private static void addDeckSupports(BlockPlaceContext context, BlockState state, TrailSurfaceEntity shaped,
                                        BlockPos pos, ServerPlayer player) {
        var level = context.getLevel();
        var column = new ColumnEditor.Column(pos.getX(), pos.getZ(), ColumnShaper.absolute(pos.getY(), FULL_BLOCK), shaped.getMaterial(), true);
        var changes = new LinkedHashMap<BlockPos, TrailEdit.Change>();
        changes.put(pos, new TrailEdit.Change(state, shaped.saveWithoutMetadata(level.registryAccess()), FULL_BLOCK.clone(), shaped.getMaterial(), true));
        DeckSupports.add(level, column, changes);
        try {
            TrailEdit.apply(level, player, changes);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.shaping.hint.place"));
    }
}
