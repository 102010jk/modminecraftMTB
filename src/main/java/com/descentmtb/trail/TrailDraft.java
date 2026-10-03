package com.descentmtb.trail;
import com.descentmtb.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Authoritative pending edit: no world mutations until the user confirms the ghost. */
public final class TrailDraft {
 private record Draft(Level level,BlockPos origin,Map<BlockPos,TrailEdit.Change> plan){}
 private static final Map<UUID,Draft> DRAFTS=new HashMap<>();
 public static void clearSession(){DRAFTS.clear();}
 public static void preview(ServerPlayer p,Map<BlockPos,TrailEdit.Change> plan,BlockPos origin){if(plan.size()>TrailConfig.MAX_BLOCKS.get())throw new IllegalArgumentException("Návrh je příliš velký");DRAFTS.put(p.getUUID(),new Draft(p.level(),origin,plan));send(p,plan);p.displayClientMessage(Component.translatable("descentmtb.wand.preview",plan.size()),true);}
 private static void send(ServerPlayer p,Map<BlockPos,TrailEdit.Change> plan){var cells=new ArrayList<TrailPreviewPayload.Cell>();plan.forEach((pos,c)->{double[] h=c.heights();if(h==null&&c.tag()!=null&&c.tag().contains("Corner0")){h=new double[]{c.tag().getDouble("Corner0"),c.tag().getDouble("Corner1"),c.tag().getDouble("Corner2"),c.tag().getDouble("Corner3")};}if(h==null)h=new double[]{1,1,1,1};cells.add(new TrailPreviewPayload.Cell(pos,(float)clamp(h[0]),(float)clamp(h[1]),(float)clamp(h[2]),(float)clamp(h[3]),c.state().isAir()));});PacketDistributor.sendToPlayer(p,new TrailPreviewPayload(cells));}
 private static double clamp(double v){return Math.max(-16,Math.min(16,v));}
 public static void action(ServerPlayer p,TrailActionPayload m){
  if(!(p.getMainHandItem().getItem() instanceof TrailWandItem)||!TrailPermissions.allowed(p))return;
  try{
   if(m.action()==TrailActionPayload.TUNE_SUB){
    var next=TrailWandItem.cycleRampSubAction(p.getMainHandItem(),Integer.signum(m.dx()));
    p.displayClientMessage(Component.translatable("descentmtb.ramp_tune.sub",next.displayName()),true);
    return;
   }
   if(m.action()==TrailActionPayload.CONFIGURE){WandSettings.read(m.settings()==null?new net.minecraft.nbt.CompoundTag():m.settings()).store(p.getMainHandItem());return;}
   if(m.action()==TrailActionPayload.UNDO){p.displayClientMessage(Component.translatable("descentmtb.builder.undone",TrailEdit.undo(p.level(),p)),true);return;}
   if(m.action()==TrailActionPayload.CANCEL){DRAFTS.remove(p.getUUID());send(p,Map.of());return;}
   String name=m.name().strip();if(name.isEmpty())name="trail";
   if(m.action()==TrailActionPayload.LOAD){BlockPos origin=p.blockPosition().relative(p.getDirection(),4);preview(p,TrailLibrary.get(p.serverLevel()).recall(p.serverLevel(),p.getUUID(),name,origin),origin);return;}
   Draft draft=DRAFTS.get(p.getUUID());if(draft==null||draft.level!=p.level())throw new IllegalArgumentException("Nejdříve připrav náhled");
   if(p.position().distanceToSqr(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(p.level(),net.minecraft.world.phys.Vec3.atCenterOf(draft.origin)))>160*160)throw new IllegalArgumentException("Přesuň se zpět k návrhu");
   if(m.action()==TrailActionPayload.SAVE){TrailLibrary.get(p.serverLevel()).store(p.getUUID(),name,draft.plan,draft.origin);p.displayClientMessage(Component.translatable("descentmtb.wand.saved",name),true);return;}
   if(m.action()==TrailActionPayload.CONFIRM){int count=TrailEdit.apply(p.level(),p,draft.plan);DRAFTS.remove(p.getUUID());send(p,Map.of());p.displayClientMessage(Component.translatable("descentmtb.builder.done",count),true);return;}
   var result=new LinkedHashMap<BlockPos,TrailEdit.Change>();
   BlockPos move=new BlockPos(Math.max(-8,Math.min(8,m.dx())),Math.max(-4,Math.min(4,m.dy())),Math.max(-8,Math.min(8,m.dz())));
   if(m.action()!=TrailActionPayload.MOVE&&m.action()!=TrailActionPayload.ROTATE)return;
   for(var e:draft.plan.entrySet()){
    BlockPos q=e.getKey().subtract(draft.origin);var c=e.getValue();
    if(m.action()==TrailActionPayload.ROTATE){q=new BlockPos(-q.getZ(),q.getY(),q.getX());double[] h=c.heights();if(h!=null)h=new double[]{h[2],h[0],h[3],h[1]};var t=c.tag()==null?null:c.tag().copy();if(t!=null&&t.contains("Corner0")){double[] a={t.getDouble("Corner2"),t.getDouble("Corner0"),t.getDouble("Corner3"),t.getDouble("Corner1")};for(int i=0;i<4;i++)t.putDouble("Corner"+i,a[i]);}c=new TrailEdit.Change(c.state().rotate(Rotation.CLOCKWISE_90),t,h,c.material(),c.deck());}
    result.put(draft.origin.offset(q).offset(m.action()==TrailActionPayload.MOVE?move:BlockPos.ZERO),c);
   }
   preview(p,result,m.action()==TrailActionPayload.MOVE?draft.origin.offset(move):draft.origin);
  }catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),true);}
 }
 private TrailDraft(){}
}
