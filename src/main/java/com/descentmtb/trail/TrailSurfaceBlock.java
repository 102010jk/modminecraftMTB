package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Same copycat interaction as a ramp, with a surface banked in two axes. */
public final class TrailSurfaceBlock extends RampBlock {
    public static final MapCodec<TrailSurfaceBlock> CODEC = simpleCodec(TrailSurfaceBlock::new);
    public TrailSurfaceBlock(Properties p) { super(p); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TrailSurfaceEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return level.getBlockEntity(pos) instanceof TrailSurfaceEntity be ? be.shape() : Shapes.block();
    }
    /** Breaking a shaped block gives back one shaping item (dirt or deck), whatever the planner built it from. */
    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        var be = builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        boolean deck = be instanceof TrailSurfaceEntity shaped && shaped.deck();
        return java.util.List.of(new net.minecraft.world.item.ItemStack(
                deck ? com.descentmtb.registry.ModBlocks.TRAIL_DECK.get() : com.descentmtb.registry.ModBlocks.TRAIL_DIRT.get()));
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighborState, net.minecraft.core.Direction face) {
        // The two block states contain no corner heights. Equal states may have completely different
        // exposed walls; only the baked model's world/model-data comparison can safely hide them.
        return false;
    }

    @Override protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return getShape(s, l, p, c); }
}
