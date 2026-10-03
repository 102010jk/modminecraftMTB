package com.descentmtb.trail;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.*;

public final class ObstacleToolItem extends Item {
    public ObstacleToolItem(Properties p){super(p);}
    private int mode(ItemStack s){return Math.floorMod(s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("obstacleMode"),3);}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){
        ItemStack s=p.getItemInHand(hand);if(!p.isShiftKeyDown())return InteractionResultHolder.pass(s);
        if(!l.isClientSide){CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putInt("obstacleMode",t.getInt("obstacleMode")+1));p.displayClientMessage(Component.translatable("descentmtb.obstacle.mode."+mode(s)),true);}
        return InteractionResultHolder.sidedSuccess(s,l.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext c){
        Player p=c.getPlayer();if(p==null)return InteractionResult.PASS;Level l=c.getLevel();if(l.isClientSide)return InteractionResult.SUCCESS;
        if(!TrackBuilderItem.allowed(p))return InteractionResult.FAIL;
        int mode=mode(c.getItemInHand());var facing=p.getDirection();var side=facing.getClockWise();
        Map<BlockPos,TrailEdit.Change> plan=new LinkedHashMap<>();BlockPos start=c.getClickedPos().above();
        for(int along=0;along<(mode==2?6:1);along++)for(int across=-1;across<=1;across++){
            if(mode==2&&Math.floorMod(along+across,3)==0)continue;
            BlockPos q=start.relative(facing,along).relative(side,across);
            if(!l.getBlockState(q).isAir())continue;
            plan.put(q,TrailEdit.Change.block((mode==0?ModBlocks.TRAIL_ROOTS.get():ModBlocks.TRAIL_ROCK.get()).defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,facing)));
        }
        try{p.displayClientMessage(Component.translatable("descentmtb.builder.done",TrailEdit.apply(l,p,plan)),true);}catch(IllegalArgumentException e){p.displayClientMessage(Component.literal(e.getMessage()),true);return InteractionResult.FAIL;}
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack s,TooltipContext c,List<Component> lines,TooltipFlag f){lines.add(Component.translatable("descentmtb.obstacle.mode."+mode(s)));lines.add(Component.translatable("descentmtb.builder.hint"));}
}
