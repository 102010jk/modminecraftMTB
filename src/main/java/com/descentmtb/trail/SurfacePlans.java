package com.descentmtb.trail;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.world.McColumns;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.util.function.DoubleBinaryOperator;
import static com.descentmtb.trail.TrailMath.*;
/** Shared vertex heights, integer-layer clipping and smooth feathering to existing terrain. */
public final class SurfacePlans {
 public static double terrain(Level l,double x,double z,double y){var columns=new McColumns(l);double top=y+6,best=Double.NaN,distance=Double.MAX_VALUE;
  for(int i=0;i<20;i++){double v=columns.collisionTop(x+.00001,z+.00001,top,y-12);if(!Double.isFinite(v))break;double d=Math.abs(v-y);if(d<distance){distance=d;best=v;}if(d<.03)break;top=v-.001;}
  return Double.isFinite(best)?best:y;}
 public static Map<BlockPos,TrailEdit.Change> surface(Level l,int x0,int z0,int x1,int z1,double reference,DoubleBinaryOperator height,java.util.function.BiPredicate<Double,Double> contains,boolean wood){
  return surface(l,x0,z0,x1,z1,reference,height,contains,wood,false);
 }
 private static Map<BlockPos,TrailEdit.Change> surface(Level l,int x0,int z0,int x1,int z1,double reference,DoubleBinaryOperator height,java.util.function.BiPredicate<Double,Double> contains,boolean wood,boolean preserve){
  if((long)(x1-x0+1)*(z1-z0+1)>20000 || x1-x0>TrailConfig.MAX_LENGTH.get()+32 || z1-z0>TrailConfig.MAX_LENGTH.get()+32)throw new IllegalArgumentException("Oblast je příliš velká");
  var samples=new HashMap<Long,Double>();DoubleBinaryOperator sampled=(x,z)->samples.computeIfAbsent(BlockPos.asLong((int)x,0,(int)z),k->height.applyAsDouble(x,z));
  var out=new LinkedHashMap<BlockPos,TrailEdit.Change>();
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
   if(!contains.test(x+.5,z+.5))continue;
   boolean deck=wood;var material=wood?Blocks.OAK_PLANKS.defaultBlockState():Blocks.COARSE_DIRT.defaultBlockState();
   double old=terrain(l,x+.5,z+.5,reference);
   if(preserve && l.getBlockEntity(new BlockPos(x,(int)Math.floor(old-.0001),z)) instanceof com.descentmtb.ramp.RampBlockEntity be){material=be.getMaterial();deck=be instanceof TrailSurfaceEntity shaped&&shaped.deck();}
   double[] h={sampled.applyAsDouble(x,z),sampled.applyAsDouble(x+1,z),sampled.applyAsDouble(x,z+1),sampled.applyAsDouble(x+1,z+1)};
   var stack=ColumnShaper.layers(h,deck);
   int bottom=stack.bottom(),top=stack.top();
   for(var layer:stack.layers())out.put(new BlockPos(x,layer.y(),z),new TrailEdit.Change(ModBlocks.TRAIL_SURFACE.get().defaultBlockState(),null,layer.heights(),material,deck));
   if(deck){
    // Remove obsolete layers when a wooden wave is moved up/down, retaining the empty underside.
    for(int y=(int)Math.floor(old-.15);y<=(int)Math.ceil(old);y++)if(y<bottom||y>top){var p=new BlockPos(x,y,z);if(l.getBlockEntity(p) instanceof TrailSurfaceEntity oldDeck&&oldDeck.deck())out.put(p,TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));}
   }
   if(!deck){
    for(int y=top+1;y<=Math.min(reference+8,Math.ceil(old));y++)if(!l.getBlockState(new BlockPos(x,y,z)).isAir())out.put(new BlockPos(x,y,z),TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
    for(int y=bottom-1;y>=Math.max(bottom-12,Math.floor(old)-1);y--){var p=new BlockPos(x,y,z);if(!l.getBlockState(p).isAir())break;out.put(p,TrailEdit.Change.block(Blocks.DIRT.defaultBlockState()));}
   }
   if(out.size()>TrailConfig.MAX_BLOCKS.get())throw new IllegalArgumentException("Zmenši oblast; návrh překročil limit bloků");
  }
  return out;
 }
 /**
  * Light terrain smoothing (machete): averages the heights inside a soft-edged circle. It never moves the
  * ground more than one block and leaves columns that would barely change untouched.
  */
 public static Map<BlockPos,TrailEdit.Change> smooth(Level l,Point centre,double radius,double strength,double softness){
  var cache=new HashMap<Long,Double>();
  DoubleBinaryOperator old=(x,z)->cache.computeIfAbsent(BlockPos.asLong((int)x,0,(int)z),k->terrain(l,x,z,centre.y()));
  DoubleBinaryOperator height=(x,z)->{
   double base=old.applyAsDouble(x,z);
   double falloff=PumpMath.edge(Math.hypot(x-centre.x(),z-centre.z()),radius,softness);
   double sum=0;
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)sum+=old.applyAsDouble(x+dx,z+dz);
   double delta=(sum/9-base)*Math.min(1,strength)*falloff;
   return base+Math.max(-1,Math.min(1,delta));
  };
  java.util.function.BiPredicate<Double,Double> changes=(x,z)->{
   if(Math.hypot(x-centre.x(),z-centre.z())>=radius)return false;
   int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
   for(int i=0;i<4;i++){
    double px=bx+(i%2),pz=bz+(i/2);
    if(Math.abs(height.applyAsDouble(px,pz)-old.applyAsDouble(px,pz))>.04)return true;
   }
   return false;
  };
  return surface(l,(int)Math.floor(centre.x()-radius),(int)Math.floor(centre.z()-radius),(int)Math.ceil(centre.x()+radius),(int)Math.ceil(centre.z()+radius),centre.y(),height,changes,false,true);
 }
 public static Map<BlockPos,TrailEdit.Change> pump(Level l,Point a,Point b,Point c,WandSettings s){
  validate(a);validate(b);validate(c);
  if(Math.abs(c.x()-a.x())>TrailConfig.MAX_LENGTH.get()||Math.abs(c.z()-a.z())>TrailConfig.MAX_LENGTH.get())throw new IllegalArgumentException("Oblast přesahuje maximální délku serveru");
  double half=s.width()/2,margin=2.0;
  DoubleBinaryOperator ground=(x,z)->terrain(l,x,z,a.y());
  if(s.mode()==WandMode.PUMP_LOOP){
   // An area selection defines an oval: rollers on the straights, banked berms in the turns.
   var oval=PumpShapes.oval(a,c,s.pump());
   return surface(l,(int)Math.floor(Math.min(a.x(),c.x())),(int)Math.floor(Math.min(a.z(),c.z())),(int)Math.ceil(Math.max(a.x(),c.x())),(int)Math.ceil(Math.max(a.z(),c.z())),a.y(),PumpShapes.loop(ground,a,c,s.pump()),oval::contains,false);
  }
  double length=PumpShapes.distances(a,b,c)[128];if(length<2||length>TrailConfig.MAX_LENGTH.get())throw new IllegalArgumentException("Zkrať trasu na 2–"+TrailConfig.MAX_LENGTH.get()+" m");
  return surface(l,(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))-half-margin),(int)Math.floor(Math.min(a.z(),Math.min(b.z(),c.z()))-half-margin),(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))+half+margin),(int)Math.ceil(Math.max(a.z(),Math.max(b.z(),c.z()))+half+margin),a.y(),PumpShapes.line(ground,a,b,c,s.pump()),(x,z)->{Point p=curve(a,b,c,nearest(a,b,c,x,z));return Math.hypot(x-p.x(),z-p.z())<half+margin;},false);
 }
 public static void validate(Point p){if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||Math.abs(p.x())>30000000||Math.abs(p.z())>30000000)throw new IllegalArgumentException("Neplatný vodicí bod");}
 private SurfacePlans(){}
}
