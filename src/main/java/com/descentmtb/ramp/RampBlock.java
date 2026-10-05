package com.descentmtb.ramp;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Customizable "copycat" ramp. FACING = direction the ramp rises toward; START/END = surface height
 * (1/16 block) at the back/front edge; PROFILE = shape of the curve between them. The look is the
 * material block stored in the {@link RampBlockEntity}, drawn by the block-entity renderer.
 *
 * <p>The static {@code heightAt / slopeX / slopeZ} helpers are pure maths (see {@link RampMath}) for the
 * physics engine; they do no level access and allocate nothing.
 */
public class RampBlock extends BaseEntityBlock {
    public static final MapCodec<RampBlock> CODEC = simpleCodec(RampBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty START = IntegerProperty.create("start", 0, 16);
    public static final IntegerProperty END = IntegerProperty.create("end", 0, 16);
    public static final EnumProperty<Profile> PROFILE = EnumProperty.create("profile", Profile.class);

    /** Curve shape; ordinal matches the {@link RampMath} profile codes. */
    public enum Profile implements StringRepresentable {
        LINEAR("linear"), CONCAVE("concave"), CONVEX("convex");

        private final String key;

        Profile(String key) { this.key = key; }

        @Override
        public String getSerializedName() { return key; }

        public Profile next() { return values()[(ordinal() + 1) % 3]; }

        public Profile previous() { return values()[(ordinal() + 2) % 3]; }
    }

    private static final int SLICES = 8;
    /** facing(4) x start(17) x end(17) x profile(3), built lazily. */
    private static final VoxelShape[] SHAPES = new VoxelShape[4 * 17 * 17 * 3];

    public RampBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(START, 0)
                .setValue(END, 8)
                .setValue(PROFILE, Profile.LINEAR));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, START, END, PROFILE);
    }

    // ---- pure maths API (physics) -------------------------------------------------------------

    public static boolean isRamp(BlockState s) { return s.getBlock() instanceof RampBlock; }

    /** Surface height (0..1 block) at in-block coords fx,fz in [0,1). */
    public static double heightAt(BlockState s, double fx, double fz) {
        return RampMath.height(s.getValue(START), s.getValue(END), s.getValue(PROFILE).ordinal(),
                s.getValue(FACING).get2DDataValue(), fx, fz);
    }

    /** d(height)/dx in blocks per block at fx,fz. */
    public static double slopeX(BlockState s, double fx, double fz) {
        return RampMath.slopeX(s.getValue(START), s.getValue(END), s.getValue(PROFILE).ordinal(),
                s.getValue(FACING).get2DDataValue(), fx, fz);
    }

    /** d(height)/dz in blocks per block at fx,fz. */
    public static double slopeZ(BlockState s, double fx, double fz) {
        return RampMath.slopeZ(s.getValue(START), s.getValue(END), s.getValue(PROFILE).ordinal(),
                s.getValue(FACING).get2DDataValue(), fx, fz);
    }

    // ---- shapes ----------------------------------------------------------------------------------

    private static VoxelShape shapeFor(BlockState s) {
        int facing = s.getValue(FACING).get2DDataValue();
        int start = s.getValue(START), end = s.getValue(END), prof = s.getValue(PROFILE).ordinal();
        int idx = ((facing * 17 + start) * 17 + end) * 3 + prof;
        VoxelShape shape = SHAPES[idx];
        if (shape == null) {
            shape = buildShape(facing, start, end, prof);
            SHAPES[idx] = shape;
        }
        return shape;
    }

    private static VoxelShape buildShape(int facing, int start, int end, int prof) {
        VoxelShape shape = Shapes.empty();
        for (int i = 0; i < SLICES; i++) {
            double t0 = (double) i / SLICES, t1 = (double) (i + 1) / SLICES;
            double h = Math.max(RampMath.heightAtT(start, end, prof, t0), RampMath.heightAtT(start, end, prof, t1));
            h = Math.max(h, 1.0 / 16.0); // never thinner than 1px
            switch (facing) {
                case 0 -> shape = Shapes.or(shape, Shapes.box(0, 0, t0, 1, h, t1));         // south: t = z
                case 1 -> shape = Shapes.or(shape, Shapes.box(1 - t1, 0, 0, 1 - t0, h, 1)); // west: t = 1 - x
                case 2 -> shape = Shapes.or(shape, Shapes.box(0, 0, 1 - t1, 1, h, 1 - t0)); // north: t = 1 - z
                default -> shape = Shapes.or(shape, Shapes.box(t0, 0, 0, t1, h, 1));        // east: t = x
            }
        }
        return shape.optimize();
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(s);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapeFor(s);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState s) { return true; }

    @Override
    // meshed into the chunk by ShapedBakedModel (a block-entity renderer would redraw every block every frame)
    protected RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    // ---- placement / state ----------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection())
                .setValue(START, 0).setValue(END, 8).setValue(PROFILE, Profile.LINEAR);
    }

    @Override
    protected BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState s) { return new RampBlockEntity(pos, s); }

    // ---- interaction ------------------------------------------------------------------------------

    /** A material must be a plain full solid cube: not a ramp, no block entity. */
    public static boolean isValidMaterial(BlockState mat, BlockGetter level, BlockPos pos) {
        if (mat.isAir() || mat.getBlock() instanceof RampBlock || mat.hasBlockEntity()) return false;
        if (mat.getRenderShape() != RenderShape.MODEL) return false;
        return Block.isShapeFullBlock(mat.getCollisionShape(level, pos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState s, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BlockItem bi)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        BlockState mat = bi.getBlock().defaultBlockState();
        if (!isValidMaterial(mat, level, pos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof RampBlockEntity be)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return ItemInteractionResult.FAIL;
        if (be.getMaterial().getBlock() == mat.getBlock()) return ItemInteractionResult.CONSUME;
        if (!level.isClientSide) {
            boolean creative = player.getAbilities().instabuild;
            if (be.isConsumed()) { // hand the previous material back
                Block.popResource(level, pos, new ItemStack(be.getMaterial().getBlock()));
            }
            if (!creative) stack.shrink(1);
            be.setMaterial(mat, !creative);
            SoundType st = mat.getSoundType();
            level.playSound(null, pos, st.getPlaceSound(), SoundSource.BLOCKS, (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState s, Player player) {
        if (player.isCreative() && level.getBlockEntity(pos) instanceof RampBlockEntity be) be.setConsumedQuiet(false);
        return super.playerWillDestroy(level, pos, s, player);
    }

    @Override
    protected void onRemove(BlockState s, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // while the Trail Shaper replaces the block it settles paid materials itself (exactly one refund)
        if (!s.is(newState.getBlock()) && !level.isClientSide && !com.descentmtb.trail.TrailEdit.isBeingEdited(pos)
                && level.getBlockEntity(pos) instanceof RampBlockEntity be && be.isConsumed()) {
            Block.popResource(level, pos, new ItemStack(be.getMaterial().getBlock()));
        }
        super.onRemove(s, level, pos, newState, moved);
    }
}
