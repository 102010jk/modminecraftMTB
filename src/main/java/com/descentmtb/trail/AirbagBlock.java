package com.descentmtb.trail;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
/** Energy-absorbing fabric cushion. Vanilla players get a soft landing and no bounce. */
public final class AirbagBlock extends Block {
 public AirbagBlock(Properties p){super(p);}
 @Override public void fallOn(Level l,net.minecraft.world.level.block.state.BlockState s,BlockPos p,Entity e,float distance){e.causeFallDamage(distance,.08f,e.damageSources().fall());e.resetFallDistance();}
 @Override public void updateEntityAfterFallOn(BlockGetter l,Entity e){var v=e.getDeltaMovement();if(v.y<0)e.setDeltaMovement(v.x*.85,0,v.z*.85);}
}
