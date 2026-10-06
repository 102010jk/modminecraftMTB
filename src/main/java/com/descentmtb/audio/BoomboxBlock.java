package com.descentmtb.audio;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BoomboxBlock extends Block {
    public static final MapCodec<BoomboxBlock> CODEC = simpleCodec(BoomboxBlock::new);
    public static java.util.function.Consumer<Emitter> editor = e -> {};
    public BoomboxBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context) {
        return Block.box(0,0,4,16,10,12);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) editor.accept(Emitter.block(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
