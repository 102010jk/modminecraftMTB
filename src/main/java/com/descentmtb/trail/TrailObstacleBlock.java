package com.descentmtb.trail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;

/** Small rideable roots/rocks with curved tyre support rather than an invisible full cube. */
public final class TrailObstacleBlock extends Block {
    private final boolean rock;
    public TrailObstacleBlock(Properties p,boolean rock){super(p);this.rock=rock;registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> codec(){return simpleCodec(p->new TrailObstacleBlock(p,rock));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,c.getHorizontalDirection());}
    @Override protected BlockState rotate(BlockState s,Rotation r){return s.setValue(BlockStateProperties.HORIZONTAL_FACING,r.rotate(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));}
    @Override protected BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(BlockStateProperties.HORIZONTAL_FACING)));}
    public double height(BlockState s,double x,double z){
        if(rock){double r=Math.pow((x-.5)/.48,2)+Math.pow((z-.5)/.48,2);return .38*Math.max(0,1-r);}
        double t=com.descentmtb.ramp.RampMath.alongT(s.getValue(BlockStateProperties.HORIZONTAL_FACING).get2DDataValue(),x,z);
        double d=Math.min(Math.abs(t-.3),Math.abs(t-.72))/.12;
        return d>=1?0:.18*Math.sqrt(1-d*d);
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        VoxelShape shape=Shapes.empty();
        for(int x=0;x<8;x++)for(int z=0;z<8;z++){
            double h=height(s,(x+.5)/8,(z+.5)/8);
            if(h>0)shape=Shapes.or(shape,Shapes.box(x/8.0,0,z/8.0,(x+1)/8.0,h,(z+1)/8.0));
        }
        return shape.optimize();
    }
}
