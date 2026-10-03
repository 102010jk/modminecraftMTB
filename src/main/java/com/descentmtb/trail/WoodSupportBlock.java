package com.descentmtb.trail;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
public final class WoodSupportBlock extends Block {
 public WoodSupportBlock(Properties p){super(p);}
 @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(6,0,6,10,16,10);}
}
