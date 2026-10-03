package com.descentmtb.trail;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class TrailSignBlock extends BaseEntityBlock {
 public static final DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
 public static java.util.function.Consumer<TrailSignEntity> editor=be->{};
 public static final MapCodec<TrailSignBlock> CODEC=simpleCodec(TrailSignBlock::new);
 public TrailSignBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
 @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
 @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
 @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new TrailSignEntity(p,s);}
 @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player user,BlockHitResult hit){if(l.isClientSide&&l.getBlockEntity(p) instanceof TrailSignEntity be)editor.accept(be);return InteractionResult.sidedSuccess(l.isClientSide);}
 @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(FACING).getAxis()==Direction.Axis.X?Shapes.or(Block.box(4,4,1,12,16,15),Block.box(7,0,7,9,4,9)):Shapes.or(Block.box(1,4,4,15,16,12),Block.box(7,0,7,9,4,9));}
 @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
}
