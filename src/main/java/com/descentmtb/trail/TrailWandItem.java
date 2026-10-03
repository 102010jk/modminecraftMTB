package com.descentmtb.trail;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.*;
import static com.descentmtb.trail.TrailMath.*;
/** One mechanical trail wand; modes selected in the configurable radial menu. */
public final class TrailWandItem extends Item {
 public TrailWandItem(Properties p){super(p);}
 private static Point read(CompoundTag t,String k){return new Point(t.getDouble(k+"x"),t.getDouble(k+"y"),t.getDouble(k+"z"));}
 @Override public InteractionResult useOn(UseOnContext c){
  if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.SUCCESS;
  if(!TrackBuilderItem.allowed(p))return InteractionResult.FAIL;
  var stack=c.getItemInHand();var s=WandSettings.read(stack);var l=p.serverLevel();
  if(s.mode()==WandMode.CLONE)return TrailCloneItem.handleClone(c);
  if(s.mode()==WandMode.UNDO){TrailDraft.action(p,new com.descentmtb.network.TrailActionPayload(5,new CompoundTag(),"",0,0,0));return InteractionResult.CONSUME;}
  if(p.isShiftKeyDown()){CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.remove("WandGuides"));p.displayClientMessage(Component.translatable("descentmtb.builder.cleared"),true);return InteractionResult.CONSUME;}
  var v=c.getClickLocation();Point q=l.getBlockState(c.getClickedPos()).is(com.descentmtb.registry.ModBlocks.TRAIL_STAKE.get())?new Point(c.getClickedPos().getX()+.5,c.getClickedPos().getY(),c.getClickedPos().getZ()+.5):new Point(v.x,v.y+.01,v.z);
  var t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();var guides=t.getCompound("WandGuides");
  int required=switch(s.mode()){case RAISE,LOWER,SMOOTH,FLATTEN,SUPPORT,ROOTS,ROCKS,ROCK_GARDEN,AIRBAG,SIGN,TEMPLATE->1;case PUMP_LOOP,BOARDWALK,WOOD_KICKER,WOOD_DROP,DROP_EDGE,BARRIER,MEASURE->2;default->3;};
  int count=guides.getInt("Mode")==s.mode().ordinal()&&guides.getString("Dimension").equals(l.dimension().location().toString())?guides.getInt("Count"):0;
  if(count<required-1){final int index=count;CustomData.update(DataComponents.CUSTOM_DATA,stack,data->{var g=data.getCompound("WandGuides");g.putDouble("p"+index+"x",q.x());g.putDouble("p"+index+"y",q.y());g.putDouble("p"+index+"z",q.z());g.putInt("Count",index+1);g.putInt("Mode",s.mode().ordinal());g.putString("Dimension",l.dimension().location().toString());data.put("WandGuides",g);});p.displayClientMessage(Component.translatable("descentmtb.builder.guide",count+1,required),true);return InteractionResult.CONSUME;}
  Point a=required>1?read(guides,"p0"):q,b=required>2?read(guides,"p1"):TrailBuilder.middle(a,q);
  try{
   if(s.mode()==WandMode.MEASURE){double dist=Math.hypot(q.x()-a.x(),q.z()-a.z());p.displayClientMessage(Component.literal(String.format(Locale.ROOT,"%.1f m • Δ %.1f m • %.1f°",dist,q.y()-a.y(),Math.toDegrees(Math.atan2(q.y()-a.y(),dist)))),false);}
   else {
    Map<BlockPos,TrailEdit.Change> plan=switch(s.mode()){
     case RAISE,LOWER,SMOOTH,FLATTEN->SurfacePlans.brush(l,q,s);
     case PUMP_LINE,PUMP_LOOP->SurfacePlans.pump(l,a,b,q,s);
     case SUPPORT,ROOTS,ROCKS,ROCK_GARDEN,BARRIER,AIRBAG,SIGN,DROP_EDGE->EquipmentPlans.plan(l,a,q,s,p.getDirection());
     case TEMPLATE->TrailLibrary.get(l).recall(l,p.getUUID(),"trail",BlockPos.containing(q.x(),q.y(),q.z()));
     default->TrailBuilder.plan(l,a,b,q,switch(s.mode()){case BERM->TrailBuilder.Shape.BERM;case ENDURO->TrailBuilder.Shape.ENDURO;case SHARKFIN->TrailBuilder.Shape.SHARKFIN;case BOARDWALK->TrailBuilder.Shape.BOARDWALK;case WOOD_KICKER->TrailBuilder.Shape.KICKER;case DIRT_JUMP->TrailBuilder.Shape.DIRT_JUMP;case WOOD_DROP->TrailBuilder.Shape.DROP;default->TrailBuilder.Shape.FLOW;},s);
    };
    // Dirt kicker uses the same continuous profile, filled to the ground instead of a thin deck.
    if(s.mode()==WandMode.DIRT_JUMP){var dirt=new LinkedHashMap<BlockPos,TrailEdit.Change>();plan.forEach((pos,change)->dirt.put(pos,new TrailEdit.Change(change.state(),change.tag(),change.heights(),net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState(),false)));plan=dirt;}
    TrailDraft.preview(p,plan,BlockPos.containing(a.x(),a.y(),a.z()));
   }
   CustomData.update(DataComponents.CUSTOM_DATA,stack,data->data.remove("WandGuides"));
  }catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),true);return InteractionResult.FAIL;}
  return InteractionResult.CONSUME;
 }
 @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag f){lines.add(Component.translatable(WandSettings.read(s).mode().key()));lines.add(Component.translatable("descentmtb.wand.hint"));}
}
