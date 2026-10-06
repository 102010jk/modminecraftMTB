package com.descentmtb.custom;

import com.descentmtb.client.custom.WorkshopScreens;
import com.descentmtb.item.MountainBikeItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bike work stand. Right-click with a bike item hangs the bike on it, a bare right-click opens the workshop
 * screen, sneak + bare right-click takes the bike back. {@link #FACING} is the way the bike's front points (the
 * bike lies along that axis); the bike itself is the block entity's stack.
 */
public final class BikeStandBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<BikeStandBlock> CODEC = simpleCodec(BikeStandBlock::new);

    /** Feet + post + the clamp arm (along the facing axis); must match models/block/bike_stand.json. */
    private static final VoxelShape ALONG_Z = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14), Block.box(7, 2, 7, 9, 13, 9), Block.box(6, 11, 2, 10, 15, 14));
    private static final VoxelShape ALONG_X = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14), Block.box(7, 2, 7, 9, 13, 9), Block.box(2, 11, 6, 14, 15, 10));

    public BikeStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BikeStandBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    /** A bike item in hand: hang it on an empty stand. Anything else falls through to the bare click (workshop). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof MountainBikeItem)
                || !(level.getBlockEntity(pos) instanceof BikeStandBlockEntity stand) || stand.hasBike()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            BikeStands.mount(player, pos, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BikeStandBlockEntity stand)) {
            return InteractionResult.PASS;
        }
        if (!stand.hasBike()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("descentmtb.stand.empty"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                BikeStands.take(player, pos);
            }
        } else if (level.isClientSide) {
            WorkshopScreens.open(pos, stand.bikeType(), stand.build());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Breaking the stand drops the bike with it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof BikeStandBlockEntity stand && stand.hasBike()) {
            Block.popResource(level, pos, stand.bike().copy());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
