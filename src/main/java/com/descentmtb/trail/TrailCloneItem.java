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

/** Two corners capture a section, subsequent clicks stamp copies. Sneak-use rotates or starts a new selection. */
public final class TrailCloneItem extends Item {
    private record Cell(BlockPos offset,BlockState state,CompoundTag data) {}
    private record Clipboard(int x,int z,List<Cell> cells) {}
    private static final Map<UUID,Clipboard> COPIES=new HashMap<>();
    public static void clearSession() { COPIES.clear(); }
    public TrailCloneItem(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext c){return handleClone(c);}
    public static InteractionResult handleClone(UseOnContext c){
        Player p=c.getPlayer();if(p==null)return InteractionResult.PASS;
        Level l=c.getLevel();if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!TrackBuilderItem.allowed(p))return InteractionResult.FAIL;
        ItemStack stack=c.getItemInHand();CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        Clipboard clip=COPIES.get(p.getUUID());
        if(p.isShiftKeyDown()){COPIES.remove(p.getUUID());clip=null;tag.remove("cloneFirst");}
        try {
            if(clip!=null){
                BlockPos start=c.getClickedPos().relative(c.getClickedFace());Map<BlockPos,TrailEdit.Change> plan=new LinkedHashMap<>();
                for(Cell cell:clip.cells)plan.put(start.offset(cell.offset),new TrailEdit.Change(cell.state,cell.data,null,null,false));
                if(stack.getItem() instanceof TrailWandItem && p instanceof net.minecraft.server.level.ServerPlayer sp) TrailDraft.preview(sp,plan,start);
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
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){
        ItemStack s=p.getItemInHand(hand);if(!p.isShiftKeyDown())return InteractionResultHolder.pass(s);
        if(!l.isClientSide&&TrackBuilderItem.allowed(p)){
            Clipboard clip=COPIES.get(p.getUUID());
            if(clip!=null){
                List<Cell> cells=new ArrayList<>();
                for(Cell c:clip.cells){
                    CompoundTag t=c.data==null?null:c.data.copy();
                    if(t!=null&&t.contains("Corner0")){
                        double[] h={t.getDouble("Corner2"),t.getDouble("Corner0"),t.getDouble("Corner3"),t.getDouble("Corner1")};
                        for(int i=0;i<4;i++)t.putDouble("Corner"+i,h[i]);
                    }
                    cells.add(new Cell(new BlockPos(clip.z-1-c.offset.getZ(),c.offset.getY(),c.offset.getX()),c.state.rotate(Rotation.CLOCKWISE_90),t));
                }
                COPIES.put(p.getUUID(),new Clipboard(clip.z,clip.x,cells));
                p.displayClientMessage(Component.translatable("descentmtb.clone.rotated"),true);
            }
        }
        return InteractionResultHolder.sidedSuccess(s,l.isClientSide);
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag f){lines.add(Component.translatable("descentmtb.clone.hint"));}
}
