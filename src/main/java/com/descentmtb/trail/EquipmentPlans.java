package com.descentmtb.trail;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static com.descentmtb.trail.TrailMath.Point;
public final class EquipmentPlans {
 public static Map<BlockPos,TrailEdit.Change> plan(Level l,Point a,Point b,WandSettings s,Direction facing){
  var out=new LinkedHashMap<BlockPos,TrailEdit.Change>();int x=(int)Math.floor(a.x()),z=(int)Math.floor(a.z()),y=(int)Math.floor(a.y()+.001);
  switch(s.mode()){
   case AIRBAG->{int width=(int)Math.round(s.width()),length=Math.max(4,Math.min(12,s.repeats()));for(int dx=-width/2;dx<=width/2;dx++)for(int dz=0;dz<length;dz++){BlockPos p=new BlockPos(x+dx,y,z+dz);out.put(p,TrailEdit.Change.block(ModBlocks.AIRBAG.get().defaultBlockState()));if(l.getBlockState(p.below()).isAir())out.put(p.below(),TrailEdit.Change.block(Blocks.OAK_PLANKS.defaultBlockState()));}}
   case SUPPORT->{int start=y-1;for(int j=y;j>=y-1;j--){var deckPos=new BlockPos(x,j,z);if(l.getBlockEntity(deckPos) instanceof TrailSurfaceEntity be&&be.deck()){var tag=be.saveWithoutMetadata(l.registryAccess());tag.putBoolean("Beam",true);out.put(deckPos,new TrailEdit.Change(l.getBlockState(deckPos),tag,null,null,false));start=j-1;break;}}for(int j=start;j>=start-12;j--){var p=new BlockPos(x,j,z);if(!l.getBlockState(p).isAir())break;out.put(p,TrailEdit.Change.block(ModBlocks.WOOD_SUPPORT.get().defaultBlockState()));}}
   case SIGN->out.put(new BlockPos(x,y,z),TrailEdit.Change.block(ModBlocks.TRAIL_SIGN.get().defaultBlockState().setValue(TrailSignBlock.FACING,facing.getOpposite())));
   case BARRIER,DROP_EDGE->{double len=Math.hypot(b.x()-a.x(),b.z()-a.z());if(len>TrailConfig.MAX_LENGTH.get())throw new IllegalArgumentException("Zkrať úsek");for(int i=0;i<=Math.ceil(len);i++){double t=i/Math.max(1,Math.ceil(len));int bx=(int)Math.floor(a.x()+(b.x()-a.x())*t),bz=(int)Math.floor(a.z()+(b.z()-a.z())*t),by=(int)Math.floor(a.y()+(b.y()-a.y())*t);var p=new BlockPos(bx,by,bz);out.put(p,TrailEdit.Change.block(s.mode()==WandMode.BARRIER?ModBlocks.CLOTH_BARRIER.get().defaultBlockState():Blocks.OAK_SLAB.defaultBlockState()));if(s.mode()==WandMode.DROP_EDGE&&i%3==0)for(int j=1;j<10&&l.getBlockState(p.below(j)).isAir();j++)out.put(p.below(j),TrailEdit.Change.block(Blocks.OAK_FENCE.defaultBlockState()));}}
   default->{int length=s.mode()==WandMode.ROCK_GARDEN?6:1,width=s.mode()==WandMode.ROCK_GARDEN?3:1;for(int i=0;i<length;i++)for(int j=0;j<width;j++){int bx=x+facing.getStepX()*i+facing.getClockWise().getStepX()*j,bz=z+facing.getStepZ()*i+facing.getClockWise().getStepZ()*j;double top=SurfacePlans.terrain(l,bx+.5,bz+.5,a.y());var p=new BlockPos(bx,(int)Math.ceil(top-.001),bz);var surfacePos=p.below();if(l.getBlockEntity(surfacePos) instanceof com.descentmtb.ramp.RampBlockEntity && !(l.getBlockEntity(surfacePos) instanceof TrailSurfaceEntity))continue;if(l.getBlockEntity(surfacePos) instanceof TrailSurfaceEntity){out.putAll(SurfaceOverlayItem.plan(l,surfacePos,s.mode()==WandMode.ROOTS?1:2));continue;}if(l.getBlockState(p).isAir())out.put(p,TrailEdit.Change.block((s.mode()==WandMode.ROOTS?ModBlocks.TRAIL_ROOTS:ModBlocks.TRAIL_ROCK).get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing)));}}
  }
  return out;
 }
 private EquipmentPlans(){}
}

