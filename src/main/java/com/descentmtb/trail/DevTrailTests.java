package com.descentmtb.trail;
import com.descentmtb.DescentMtb;
import com.descentmtb.network.TrailActionPayload;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.world.McColumns;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;
import static com.descentmtb.trail.TrailMath.Point;
/** Development-only tests of registered blocks, world edits and the real wand interaction path. */
public final class DevTrailTests {
 public static volatile boolean PASSED,FAILED;
 public static void register(RegisterCommandsEvent e){if(!Boolean.getBoolean("descentmtb.autopilot"))return;e.getDispatcher().register(Commands.literal("mtbdevtrail").requires(s->s.hasPermission(2)).executes(c->run(c.getSource().getPlayerOrException())));}
 private static void check(boolean b,String m){if(!b)throw new IllegalStateException(m);DescentMtb.LOG.info("[trailtest] PASS: {}",m);}
 private static void click(ServerPlayer p,double x,double y,double z){var hit=new BlockHitResult(new Vec3(x,y,z),Direction.UP,BlockPos.containing(x,y-.001,z),false);p.getMainHandItem().getItem().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));}
 private static TrailActionPayload action(int a){return new TrailActionPayload(a,new CompoundTag(),"live_test",0,0,0);}
 private static int run(ServerPlayer p){PASSED=FAILED=false;var l=p.serverLevel();ItemStack saved=p.getMainHandItem();int x=p.blockPosition().getX()+14,z=p.blockPosition().getZ(),y=200;var origin=new BlockPos(x,y,z);
  try{
   for(int bx=x;bx<=x+42;bx++)for(int bz=z;bz<=z+36;bz++){l.setBlock(new BlockPos(bx,y-1,bz),Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int by=y;by<=y+5;by++)l.setBlock(new BlockPos(bx,by,bz),Blocks.AIR.defaultBlockState(),3);}
   var wand=new ItemStack(ModBlocks.TRAIL_WAND.get());p.setItemInHand(InteractionHand.MAIN_HAND,wand);
   // A continuous deck crossing y=201 must retain both layers and the original plane.
   var a=new Point(x+.5,y+.9,z+.5);var c=new Point(x+.5,y+1.1,z+4.5);
   var deck=TrailBuilder.plan(l,a,TrailBuilder.middle(a,c),c,TrailBuilder.Shape.BOARDWALK);
   check(deck.containsKey(origin),"boardwalk starts at its first guide");TrailEdit.apply(l,p,deck);
   var columns=new McColumns(l);double h1=columns.collisionTop(x+.5,z+2.4,y+3,y-1),h2=columns.collisionTop(x+.5,z+2.6,y+3,y-1);
   check(Math.abs(h1-(y+.995))<.005&&Math.abs(h2-(y+1.005))<.005,"continuous surface crosses an integer block height without phantom step");
   // a bump on the wooden deck (hand sculpted), then the machete's smoothing must keep deck + wood
   var bumpItem=new ItemStack(ModBlocks.TRAIL_DECK.get());
   for(int i=0;i<3;i++)ShapingBlockItem.sculpt(p,bumpItem,new BlockPos(x,y,z+2),new Vec3(x+.5,y+1,z+2.5),false);
   var brush=SurfacePlans.smooth(l,new Point(x+.5,y+1.2,z+2.5),2,.6,.8);
   check(brush.values().stream().filter(v->v.heights()!=null).anyMatch(v->v.deck()&&v.material().is(Blocks.OAK_PLANKS)),"smoothing preserves deck and wood material");
   // Real three-click wand, ghost remains separate until confirmation.
   new WandSettings(WandMode.PUMP_LINE,4,.8,4,4,3,.3,.8).store(wand);
   click(p,x+10.5,y,z+1.5);click(p,x+10.5,y,z+9.5);click(p,x+10.5,y,z+17.5);
   check(l.getBlockState(new BlockPos(x+10,y,z+3)).isAir(),"wand creates preview without modifying terrain");
   TrailDraft.action(p,action(TrailActionPayload.SAVE));TrailDraft.action(p,action(TrailActionPayload.CONFIRM));
   check(l.getBlockEntity(new BlockPos(x+10,y,z+3)) instanceof TrailSurfaceEntity,"confirm creates rideable pumptrack surfaces");
   check(!TrailLibrary.get(l).recall(l,p.getUUID(),"live_test",new BlockPos(x+25,y,z)).isEmpty(),"custom template is saved and restored");
   // Clone one deck tile, rotate the captured design, stamp and undo it.
   var source=origin;new WandSettings(WandMode.CLONE,5,.75,5,5,3,.3,.75).store(wand);
   click(p,x+.5,y+1,z+.5);click(p,x+.5,y+1,z+.5);click(p,x+5.5,y,z+.5);
   TrailDraft.action(p,action(TrailActionPayload.ROTATE));TrailDraft.action(p,action(TrailActionPayload.CONFIRM));
   var target=new BlockPos(x+5,y,z);check(l.getBlockEntity(target) instanceof TrailSurfaceEntity,"clone preserves block entity data after rotation");
   TrailEdit.undo(l,p);check(l.getBlockState(target).isAir(),"undo restores the previous terrain");
   var loop=SurfacePlans.pump(l,new Point(x+20,y,z),new Point(x+30,y,z+14),new Point(x+40,y,z+28),new WandSettings(WandMode.PUMP_LOOP,3,.7,5,10,3,.3,.75));
   TrailEdit.apply(l,p,loop);check(loop.size()>100,"closed pumptrack loop with banks generated");
   // Clicked lower floor wins over a nearby bridge ceiling.
   l.setBlock(new BlockPos(x+6,y+4,z+3),Blocks.OAK_PLANKS.defaultBlockState(),3);
   check(Math.abs(SurfacePlans.terrain(l,x+6.5,z+3.5,y)-y)<.001,"brush chooses clicked ground under a bridge");
   var support=EquipmentPlans.plan(l,new Point(x+.5,y+.9,z+.5),a,new WandSettings(WandMode.SUPPORT,5,.75,5,5,3,.3,.75),Direction.SOUTH);check(!support.isEmpty(),"support starts below a partial wooden deck");TrailEdit.apply(l,p,support);
   var signPos=new BlockPos(x+4,y,z+5);l.setBlock(signPos,ModBlocks.TRAIL_SIGN.get().defaultBlockState(),3);var sign=(TrailSignEntity)l.getBlockEntity(signPos);var canvas=new SignArt(new byte[256]);canvas.template(0);sign.setPixels(canvas.pixels());var tag=sign.saveWithoutMetadata(l.registryAccess());var second=new TrailSignEntity(signPos,sign.getBlockState());second.loadWithComponents(tag,l.registryAccess());check(Arrays.equals(sign.pixels(),second.pixels()),"16x16 sign artwork survives block entity serialization");
   var bag=EquipmentPlans.plan(l,new Point(x+5,y,z+12),a,new WandSettings(WandMode.AIRBAG,5,.75,5,6,3,.3,.75),Direction.SOUTH);TrailEdit.apply(l,p,bag);check(bag.size()>=30,"landing airbag prefab built");
   DevSculptTests.run(p,l,x,y,z);
   PASSED=true;DescentMtb.LOG.info("[trailtest] ALL PASSED");
  }catch(Exception e){FAILED=true;DescentMtb.LOG.error("[trailtest] FAIL",e);}
  finally{TrailDraft.action(p,action(TrailActionPayload.CANCEL));p.setItemInHand(InteractionHand.MAIN_HAND,saved);}
  return PASSED?1:0;
 }
 private DevTrailTests(){}
}
