package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.Map;

/** Roots and rocks remain ordinary placeable items; on a shaped surface they follow its plane. */
public final class SurfaceOverlayItem extends BlockItem {
    private final int kind;
    public SurfaceOverlayItem(net.minecraft.world.level.block.Block block,Properties p,int kind){super(block,p);this.kind=kind;}
    public static Map<BlockPos,TrailEdit.Change> plan(Level level,BlockPos pos,int kind){
        if(!(level.getBlockEntity(pos) instanceof TrailSurfaceEntity))throw new IllegalArgumentException("Kořeny a kameny patří na tvarovanou hlínu nebo lávku");
        var col=ColumnEditor.read(level,pos.getX(),pos.getZ(),pos.getY());
        if(col==null)throw new IllegalArgumentException("Tady není povrch pro kameny nebo kořeny");
        CompoundTag tag=col.decoration()==null?new CompoundTag():col.decoration().copy();tag.putInt("Overlay",kind);
        return ColumnEditor.rebuild(level,new ColumnEditor.Column(col.x(),col.z(),col.abs(),col.material(),col.deck(),tag),col.abs());
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof com.descentmtb.ramp.RampBlockEntity
                && !(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof TrailSurfaceEntity))return InteractionResult.PASS;
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof com.descentmtb.ramp.RampBlockEntity))return super.useOn(c);
        if(c.getPlayer() instanceof ServerPlayer p){
            if(!p.mayBuild())return InteractionResult.FAIL;
            try {TrailEdit.apply(c.getLevel(),p,plan(c.getLevel(),c.getClickedPos(),p.isShiftKeyDown()?0:kind));
                if(!p.isCreative()&&!p.isShiftKeyDown())c.getItemInHand().shrink(1);
            }catch(IllegalArgumentException ex){p.displayClientMessage(net.minecraft.network.chat.Component.literal(ex.getMessage()),true);return InteractionResult.FAIL;}
        }
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext c,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag f){lines.add(net.minecraft.network.chat.Component.translatable("descentmtb.overlay.hint"));}
}
