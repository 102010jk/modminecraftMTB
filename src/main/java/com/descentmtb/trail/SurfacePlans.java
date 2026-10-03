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
   double min=Arrays.stream(h).min().orElse(reference),max=Arrays.stream(h).max().orElse(reference);
   int bottom=(int)Math.floor(min-(deck?.14:0)-.001),top=(int)Math.ceil(max)-1;
   if(top-bottom>8)throw new IllegalArgumentException("Příliš prudký přechod; zmenši sílu nebo nejprve vyhlaď terén");
   for(int y=bottom;y<=top;y++){double[] local=h.clone();for(int i=0;i<4;i++)local[i]-=y;out.put(new BlockPos(x,y,z),new TrailEdit.Change(ModBlocks.TRAIL_SURFACE.get().defaultBlockState(),null,local,material,deck));}
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
 public static Map<BlockPos,TrailEdit.Change> brush(Level l,Point centre,WandSettings s){
  double r=s.radius();var cache=new HashMap<Long,Double>();
  DoubleBinaryOperator old=(x,z)->cache.computeIfAbsent(BlockPos.asLong((int)x,0,(int)z),k->terrain(l,x,z,centre.y()));
  double target=terrain(l,centre.x(),centre.z(),centre.y());
  DoubleBinaryOperator height=(x,z)->{
   double base=old.applyAsDouble(x,z),f=PumpMath.edge(Math.hypot(x-centre.x(),z-centre.z()),r,s.softness());
   return switch(s.mode()){
    case RAISE->base+s.strength()*f;case LOWER->base-s.strength()*f;
    case FLATTEN->base+(target-base)*Math.min(1,s.strength())*f;
    default->{double sum=0;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)sum+=old.applyAsDouble(x+dx,z+dz);yield base+(sum/9-base)*Math.min(1,s.strength())*f;}
   };
  };
  return surface(l,(int)Math.floor(centre.x()-r),(int)Math.floor(centre.z()-r),(int)Math.ceil(centre.x()+r),(int)Math.ceil(centre.z()+r),centre.y(),height,(x,z)->Math.hypot(x-centre.x(),z-centre.z())<r,false,true);
 }
 public static Map<BlockPos,TrailEdit.Change> pump(Level l,Point a,Point b,Point c,WandSettings s){
  validate(a);validate(b);validate(c);
  if(Math.abs(c.x()-a.x())>TrailConfig.MAX_LENGTH.get()||Math.abs(c.z()-a.z())>TrailConfig.MAX_LENGTH.get())throw new IllegalArgumentException("Oblast přesahuje maximální délku serveru");
  double half=s.width()/2,margin=2.0;
  if(s.mode()==WandMode.PUMP_LOOP){
   // An area selection defines an oval: straights carry rollers, ends are smoothly banked.
   double cx=(a.x()+c.x())/2,cz=(a.z()+c.z())/2,rx=Math.abs(c.x()-a.x())/2-half-margin,rz=Math.abs(c.z()-a.z())/2-half-margin;
   if(rx<4||rz<4)throw new IllegalArgumentException("Pro okruh vyber oblast alespoň "+(int)(2*(half+margin+4))+" × "+(int)(2*(half+margin+4))+" m");
   double perimeter=Math.PI*(3*(rx+rz)-Math.sqrt((3*rx+rz)*(rx+3*rz))),spacing=s.spacing(),active=Math.min(perimeter,spacing*s.repeats());
   DoubleBinaryOperator height=(x,z)->{
    double nx=(x-cx)/rx,nz=(z-cz)/rz,theta=Math.atan2(nz,nx),rad=Math.sqrt(nx*nx+nz*nz),side=(rad-1)*Math.min(rx,rz),base=terrain(l,x,z,a.y());
    double edge=PumpMath.edge(Math.abs(side),half+margin,.6),bank=Math.max(0,side)/half;
    double distance=(theta+Math.PI)/Math.PI/2*perimeter;
    double wave=distance<=active?PumpMath.wave(distance,spacing,s.height())*PumpMath.smooth((active-distance)/2):0;
    double target=a.y()+(c.y()-a.y())*Math.max(0,Math.min(1,((x-a.x())*(c.x()-a.x())+(z-a.z())*(c.z()-a.z()))/Math.max(1,Math.pow(c.x()-a.x(),2)+Math.pow(c.z()-a.z(),2))));
    return base+edge*(target-base+wave+Math.min(2,bank*bank)*s.height()*.9);
   };
   return surface(l,(int)Math.floor(Math.min(a.x(),c.x())),(int)Math.floor(Math.min(a.z(),c.z())),(int)Math.ceil(Math.max(a.x(),c.x())),(int)Math.ceil(Math.max(a.z(),c.z())),a.y(),height,(x,z)->Math.abs((Math.hypot((x-cx)/rx,(z-cz)/rz)-1)*Math.min(rx,rz))<half+margin,false);
  }
  double[] distances=new double[129];Point last=a;for(int i=1;i<=128;i++){Point p=curve(a,b,c,i/128.0);distances[i]=distances[i-1]+Math.hypot(p.x()-last.x(),p.z()-last.z());last=p;}
  double length=distances[128];if(length<2||length>TrailConfig.MAX_LENGTH.get())throw new IllegalArgumentException("Zkrať trasu na 2–"+TrailConfig.MAX_LENGTH.get()+" m");
  double active=Math.min(length,s.spacing()*s.repeats());
  DoubleBinaryOperator height=(x,z)->{
   double t=nearest(a,b,c,x,z);Point p=curve(a,b,c,t);double lateral=Math.hypot(x-p.x(),z-p.z()),base=terrain(l,x,z,a.y());
   int i=Math.min(127,(int)(t*128));double d=distances[i]+(distances[i+1]-distances[i])*(t*128-i);
   double fade=PumpMath.smooth(d/2)*PumpMath.smooth((active-d)/2)*PumpMath.edge(lateral,half+margin,.5);
   return base+(curve(a,b,c,t).y()-base+PumpMath.wave(d,s.spacing(),s.height()))*fade;
  };
  return surface(l,(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))-half-margin),(int)Math.floor(Math.min(a.z(),Math.min(b.z(),c.z()))-half-margin),(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))+half+margin),(int)Math.ceil(Math.max(a.z(),Math.max(b.z(),c.z()))+half+margin),a.y(),height,(x,z)->{Point p=curve(a,b,c,nearest(a,b,c,x,z));return Math.hypot(x-p.x(),z-p.z())<half+margin;},false);
 }
 public static void validate(Point p){if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||Math.abs(p.x())>30000000||Math.abs(p.z())>30000000)throw new IllegalArgumentException("Neplatný vodicí bod");}
 private SurfacePlans(){}
}
