package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Clone mode of the Trail Builder: two corners capture a section, later clicks stamp copies (as a preview). */
public final class TrailClone {
    private record Cell(BlockPos offset,BlockState state,CompoundTag data) {}
    private record Clipboard(int x,int z,List<Cell> cells) {}
    private static final Map<UUID,Clipboard> COPIES=new HashMap<>();
    public static void clearSession() { COPIES.clear(); }
    public static InteractionResult handleClone(UseOnContext c){
        Player p=c.getPlayer();if(p==null)return InteractionResult.PASS;
        Level l=c.getLevel();if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!TrailPermissions.allowed(p))return InteractionResult.FAIL;
        ItemStack stack=c.getItemInHand();CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        Clipboard clip=COPIES.get(p.getUUID());
        if(p.isShiftKeyDown()){COPIES.remove(p.getUUID());clip=null;tag.remove("cloneFirst");}
        try {
            if(clip!=null){
                BlockPos start=c.getClickedPos().relative(c.getClickedFace());Map<BlockPos,TrailEdit.Change> plan=new LinkedHashMap<>();
                for(Cell cell:clip.cells)plan.put(start.offset(cell.offset),new TrailEdit.Change(cell.state,cell.data,null,null,false));
                if(p instanceof net.minecraft.server.level.ServerPlayer sp) TrailDraft.preview(sp,plan,start);
                else p.displayClientMessage(Component.translatable("descentmtb.clone.pasted",TrailEdit.apply(l,p,plan)),true);
                return InteractionResult.CONSUME;
            }
            int[] first=tag.getIntArray("cloneFirst");
            if(first.length!=3||!tag.getString("cloneDimension").equals(l.dimension().location().toString())){
                BlockPos q=c.getClickedPos();CustomData.update(DataComponents.CUSTOM_DATA,stack,t->{t.putIntArray("cloneFirst",new int[]{q.getX(),q.getY(),q.getZ()});t.putString("cloneDimension",l.dimension().location().toString());});
                p.displayClientMessage(Component.translatable("descentmtb.clone.first"),true);return InteractionResult.CONSUME;
            }
            BlockPos a=new BlockPos(first[0],first[1],first[2]),b=c.getClickedPos();
            BlockPos min=new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ()));
            int sx=Math.abs(a.getX()-b.getX())+1,sy=Math.abs(a.getY()-b.getY())+1,sz=Math.abs(a.getZ()-b.getZ())+1;
            if((long)sx*sy*sz>TrailConfig.MAX_BLOCKS.get())throw new IllegalArgumentException("Výběr je příliš velký");
            List<Cell> cells=new ArrayList<>();
            for(BlockPos q:BlockPos.betweenClosed(min,min.offset(sx-1,sy-1,sz-1))){
                if(!l.isLoaded(q))throw new IllegalArgumentException("Výběr není načtený");
                var be=l.getBlockEntity(q);if(be!=null&&!(be instanceof RampBlockEntity)&&!(be instanceof TrailSignEntity))throw new IllegalArgumentException("Nelze klonovat inventáře a cizí bloková data");
                cells.add(new Cell(q.subtract(min),l.getBlockState(q),be==null?null:be.saveWithoutMetadata(l.registryAccess())));
            }
            COPIES.put(p.getUUID(),new Clipboard(sx,sz,cells));
            CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.remove("cloneFirst"));
            p.displayClientMessage(Component.translatable("descentmtb.clone.copied",cells.size()),true);
        }catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),true);return InteractionResult.FAIL;}
        return InteractionResult.CONSUME;
    }
    private TrailClone() {}
}
