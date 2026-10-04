package com.descentmtb.tape;

import com.descentmtb.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

/**
 * A thin wooden stake with a reflective cap that trail tape is strung between (see {@link TrailTapeItem}).
 * Riders pass straight through it; breaking it removes the tapes on both sides.
 */
public final class BarrierPostBlock extends BaseEntityBlock {
    public static final MapCodec<BarrierPostBlock> CODEC = simpleCodec(BarrierPostBlock::new);
    /** Selection box only (the post has no collision); matches the model. */
    private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);

    public BarrierPostBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarrierPostEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Pick block gives the tape roll, the only item there is. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModBlocks.BARRIER_ITEM.get());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof BarrierPostEntity post) {
            for (BlockPos other : post.links()) {
                if (level.getBlockEntity(other) instanceof BarrierPostEntity partner) {
                    partner.removeLink(pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
