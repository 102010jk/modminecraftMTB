package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.*;
import static com.descentmtb.trail.TrailMath.Point;

/** Guide tools: 3 clicks form a turn/flow curve; 2 clicks form a deck/roller line. */
public final class TrackBuilderItem extends Item {
    public enum Kind { BERM, ROUTE, WOOD, ROLLERS, UNDO, MEASURE }
    private final Kind kind;
    public TrackBuilderItem(Properties p,Kind kind){super(p);this.kind=kind;}
    private TrailBuilder.Shape[] modes() {
        return switch(kind) {
            case BERM -> new TrailBuilder.Shape[]{TrailBuilder.Shape.BERM,TrailBuilder.Shape.ENDURO,TrailBuilder.Shape.SHARKFIN};
            case WOOD -> new TrailBuilder.Shape[]{TrailBuilder.Shape.BOARDWALK,TrailBuilder.Shape.KICKER,TrailBuilder.Shape.DROP};
            case ROLLERS -> new TrailBuilder.Shape[]{TrailBuilder.Shape.ROLLERS};
            default -> new TrailBuilder.Shape[]{TrailBuilder.Shape.FLOW};
        };
    }
    private TrailBuilder.Shape mode(ItemStack s){return modes()[Math.floorMod(s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("shapeMode"),modes().length)];}
    private static Point point(CompoundTag t,String key){return new Point(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));}
    private static void put(CompoundTag t,String k,Point p){t.putDouble(k+"X",p.x());t.putDouble(k+"Y",p.y());t.putDouble(k+"Z",p.z());}
    public static boolean allowed(Player p) {
        if(TrailConfig.CREATIVE_ONLY.get()&&!p.isCreative()){p.displayClientMessage(Component.translatable("descentmtb.builder.creative"),true);return false;}return true;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){
        ItemStack s=p.getItemInHand(hand);
        if(l.isClientSide) return InteractionResultHolder.success(s);
        if(!allowed(p)) return InteractionResultHolder.fail(s);
        if(kind==Kind.UNDO){undo(l,p);return InteractionResultHolder.consume(s);}
        if(p.isShiftKeyDown()){
            CustomData.update(DataComponents.CUSTOM_DATA,s,t->{t.putInt("shapeMode",t.getInt("shapeMode")+1);t.remove("guideCount");});
            p.displayClientMessage(Component.translatable("descentmtb.builder.shape."+mode(s).name().toLowerCase(Locale.ROOT)),true);
            return InteractionResultHolder.consume(s);
        }
        return InteractionResultHolder.pass(s);
    }
    @Override public InteractionResult useOn(UseOnContext ctx){
        Player p=ctx.getPlayer();if(p==null)return InteractionResult.PASS;
        Level l=ctx.getLevel();if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!allowed(p))return InteractionResult.FAIL;
        if(kind==Kind.UNDO){undo(l,p);return InteractionResult.CONSUME;}
        ItemStack s=ctx.getItemInHand();
        if(p.isShiftKeyDown()){CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.remove("guideCount"));p.displayClientMessage(Component.translatable("descentmtb.builder.cleared"),true);return InteractionResult.CONSUME;}
        CompoundTag t=s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String dimension=l.dimension().location().toString();
        int count=t.getString("guideDimension").equals(dimension)?t.getInt("guideCount"):0;
        var v=ctx.getClickLocation();Point q=ctx.getLevel().getBlockState(ctx.getClickedPos()).is(com.descentmtb.registry.ModBlocks.TRAIL_STAKE.get())
                ?new Point(ctx.getClickedPos().getX()+.5,ctx.getClickedPos().getY(),ctx.getClickedPos().getZ()+.5):new Point(v.x,v.y+.025,v.z);
        int required=kind==Kind.BERM||kind==Kind.ROUTE?3:2;
        if(count<required-1){
            final int index=count;
            CustomData.update(DataComponents.CUSTOM_DATA,s,tag->{put(tag,"guide"+index,q);tag.putInt("guideCount",index+1);tag.putString("guideDimension",dimension);});
            marker((ServerLevel)l,q);
            p.displayClientMessage(Component.translatable("descentmtb.builder.guide",count+1,required),true);
            return InteractionResult.CONSUME;
        }
        Point a=point(t,"guide0"),b=required==3?point(t,"guide1"):TrailBuilder.middle(a,q);
        if(kind==Kind.MEASURE){
            double distance=Math.hypot(q.x()-a.x(),q.z()-a.z()),dy=q.y()-a.y();
            p.displayClientMessage(Component.literal(String.format(Locale.ROOT,"%.1f m  •  Δ %.1f m  •  %.1f° (%.0f%%)",distance,dy,Math.toDegrees(Math.atan2(dy,distance)),100*dy/Math.max(.01,distance))),false);
            CustomData.update(DataComponents.CUSTOM_DATA,s,tag->tag.remove("guideCount"));return InteractionResult.CONSUME;
        }
        try{
            int blocks=TrailEdit.apply(l,p,TrailBuilder.plan(l,a,b,q,mode(s)));
            CustomData.update(DataComponents.CUSTOM_DATA,s,tag->tag.remove("guideCount"));
            p.displayClientMessage(Component.translatable("descentmtb.builder.done",blocks),true);
        }catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);return InteractionResult.FAIL;}
        return InteractionResult.CONSUME;
    }
    private static void undo(Level l,Player p){try{p.displayClientMessage(Component.translatable("descentmtb.builder.undone",TrailEdit.undo(l,p)),true);}catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),true);}}
    private static void marker(ServerLevel l,Point p){
        if(!TrailConfig.MARKERS.get())return;
        var world=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(l,new net.minecraft.world.phys.Vec3(p.x(),p.y(),p.z()));
        l.sendParticles(new DustParticleOptions(new org.joml.Vector3f(.2f,1,.8f),1.5f),world.x,world.y+.5,world.z,24,.1,.6,.1,0);
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext ctx,List<Component> lines,TooltipFlag flag){
        lines.add(Component.translatable("descentmtb.builder.shape."+(kind==Kind.MEASURE?"measure":mode(s).name().toLowerCase(Locale.ROOT))));
        lines.add(Component.translatable("descentmtb.builder.hint"));
    }
}
