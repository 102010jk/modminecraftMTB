package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Precision tools share their shaping modes with the builder and never consume building material. */
public final class ShapeToolItem extends Item {
    public ShapeToolItem(Properties p){super(p);}
    public static ShapeMode mode(ItemStack stack){return ShapeMode.values()[Math.floorMod(stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("ShapeMode"),ShapeMode.values().length)];}
    public static void mode(ItemStack stack,int value){CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt("ShapeMode",Math.floorMod(value,ShapeMode.values().length)));}
    public static boolean usable(ItemStack stack) {return stack.getItem() instanceof ShapeToolItem;}
    public static void tune(ServerPlayer p,BlockPos pos,Vec3 hit,int direction) {
        if(!p.mayBuild()||!p.level().isLoaded(pos)||!p.level().mayInteract(p,pos))return;
        var column=ColumnEditor.read(p.level(),pos.getX(),pos.getZ(),pos.getY());
        if(column==null||!(p.level().getBlockEntity(pos) instanceof TrailSurfaceEntity)) return;
        double delta=Math.signum(direction)/32.0;
        ShapeMode mode=mode(p.getMainHandItem());
        var vertices=mode.vertices(pos.getX(),pos.getZ(),Math.max(0,Math.min(1,hit.x-pos.getX())),Math.max(0,Math.min(1,hit.z-pos.getZ())));
        try {
            Map<BlockPos,TrailEdit.Change> changes=ColumnEditor.sculptVertices(p.level(),pos.getX(),pos.getZ(),pos.getY(),vertices,delta,pos.getY()-6,pos.getY()+12,true);
            if(mode==ShapeMode.CURVE) {
                changes=continuousCurve(p,pos,delta);
            }
            TrailEdit.apply(p.level(),p,changes);
        } catch(IllegalArgumentException ex){p.displayClientMessage(Component.literal(ex.getMessage()),true);}
    }
    private static Map<BlockPos,TrailEdit.Change> continuousCurve(ServerPlayer p,BlockPos pos,double delta) {
        var out=new LinkedHashMap<BlockPos,TrailEdit.Change>();var d=p.getDirection();
        var vertices=new LinkedHashMap<ColumnShaper.Vertex,Double>();
        for(int side=-1;side<=1;side++) {
            var q=pos.relative(d,side);
            if(!(p.level().getBlockEntity(q) instanceof TrailSurfaceEntity))continue;
            for(var v:ShapeMode.WHOLE.vertices(q.getX(),q.getZ(),.5,.5))vertices.merge(v,side==0?delta:delta*.5,(a,b)->Math.abs(a)>Math.abs(b)?a:b);
        }
        var columns=new LinkedHashMap<Long,ColumnEditor.Column>();var heights=new LinkedHashMap<Long,double[]>();
        for(var entry:vertices.entrySet())for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++) {
            var v=entry.getKey();int x=v.x()-dx,z=v.z()-dz;long key=BlockPos.asLong(x,0,z);
            var col=columns.computeIfAbsent(key,k->ColumnEditor.read(p.level(),x,z,pos.getY()));if(col==null||col.copycat())continue;
            var h=heights.computeIfAbsent(key,k->col.abs().clone());int corner=dz*2+dx;
            h[corner]=col.abs()[corner]+entry.getValue();
        }
        heights.forEach((k,h)->out.putAll(ColumnEditor.rebuild(p.level(),columns.get(k),h)));return out;
    }
    @Override public InteractionResult useOn(UseOnContext c) {
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof TrailSurfaceEntity))return InteractionResult.PASS;
        if(c.getPlayer() instanceof ServerPlayer p)tune(p,c.getClickedPos(),c.getClickLocation(),p.isShiftKeyDown()?-1:1);
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag f){lines.add(Component.translatable(mode(s).key()));lines.add(Component.translatable("descentmtb.shape.hint"));}
}
