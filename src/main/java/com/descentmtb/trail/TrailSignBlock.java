package com.descentmtb.trail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Consumer;

/**
 * A trail sign: an oak board on a post, or flush against a wall. Right-click opens the editor; placing one
 * opens it straight away. {@link #FACING} is the direction the front of the board looks.
 */
public final class TrailSignBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** True when the sign hangs on the wall behind it instead of standing on a post. */
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    /** Installed by the client: opens the editor for a sign. */
    public static Consumer<TrailSignEntity> editor = be -> {};

    public static final MapCodec<TrailSignBlock> CODEC = simpleCodec(TrailSignBlock::new);

    /**
     * Collision / outline boxes in pixels for a sign facing north (front towards -z): the board with its
     * frame lip, and the post behind it. Must match the models.
     */
    private static final double[][] STANDING_BOXES = {
            {1, 6, 6.5, 15, 16, 9},
            {7, 0, 9, 9, 16, 11},
    };
    private static final double[][] WALL_BOXES = {
            {1, 3, 13.5, 15, 13, 16},
    };
    /** Indexed by {@code horizontal index * 2 + (wall ? 1 : 0)}. */
    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES[shapeIndex(facing, false)] = shape(STANDING_BOXES, facing);
            SHAPES[shapeIndex(facing, true)] = shape(WALL_BOXES, facing);
        }
    }

    public TrailSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
    }

    private static int shapeIndex(Direction facing, boolean wall) {
        return facing.get2DDataValue() * 2 + (wall ? 1 : 0);
    }

    /** Turns the north-facing boxes so the front looks towards {@code facing} (same rotation as the blockstate). */
    private static VoxelShape shape(double[][] boxes, Direction facing) {
        VoxelShape result = Shapes.empty();
        for (double[] b : boxes) {
            double[] lo = rotate(b[0], b[2], facing);
            double[] hi = rotate(b[3], b[5], facing);
            result = Shapes.or(result, Block.box(
                    Math.min(lo[0], hi[0]), b[1], Math.min(lo[1], hi[1]),
                    Math.max(lo[0], hi[0]), b[4], Math.max(lo[1], hi[1])));
        }
        return result;
    }

    /** Rotates a point about the block centre, clockwise seen from above, by 90 degrees per step away from north. */
    private static double[] rotate(double x, double z, Direction facing) {
        double px = x - 8, pz = z - 8;
        return switch (facing) {
            case EAST -> new double[]{8 - pz, 8 + px};
            case SOUTH -> new double[]{8 - px, 8 - pz};
            case WEST -> new double[]{8 + pz, 8 - px};
            default -> new double[]{x, z};
        };
    }

    /**
     * The direction a rider heads when they start a trail at this sign. A standing sign faces the riders as
     * they approach, so they ride on past it, away from its front. A wall sign cannot be ridden through; riders
     * pass along the wall in reading direction, to the reader's right.
     */
    public static Direction rideHeading(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(WALL) ? facing.getCounterClockWise() : facing.getOpposite();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState onWall = defaultBlockState().setValue(FACING, clicked).setValue(WALL, true);
            if (onWall.canSurvive(context.getLevel(), context.getClickedPos())) {
                return onWall;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL, false);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.getValue(WALL)) {
            return true;
        }
        Direction back = state.getValue(FACING).getOpposite();
        BlockPos support = pos.relative(back);
        return level.getBlockState(support).isFaceSturdy(level, support, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WALL) && direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // Only the placing player runs this on the client, so this opens the editor for them alone.
        if (level.isClientSide && placer instanceof Player && level.getBlockEntity(pos) instanceof TrailSignEntity sign) {
            editor.accept(sign);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrailSignEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player user, BlockHitResult hit) {
        if (user.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof TrailSignEntity sign) {
            editor.accept(sign);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[shapeIndex(state.getValue(FACING), state.getValue(WALL))];
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
