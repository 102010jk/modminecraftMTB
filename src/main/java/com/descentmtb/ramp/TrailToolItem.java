package com.descentmtb.ramp;

import com.descentmtb.trail.RampTuning;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import java.util.List;

/** The original standalone copycat-ramp tool, independent of terrain brushes and the Trail Builder. */
public final class TrailToolItem extends Item {
    public TrailToolItem(Properties properties){super(properties);}

    /** Keep the original item data key so old tool stacks retain their selected mode. */
    public static RampTuning.SubAction mode(ItemStack stack) {
        int value=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getInt("mode");
        return RampTuning.SubAction.values()[Math.floorMod(value,RampTuning.SubAction.values().length)];
    }

    public static void cycle(Player player,ItemStack stack,int direction) {
        var next=mode(stack).next(direction);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->{tag.putInt("mode",next.ordinal());tag.remove("link");});
        RampTuning.clearLink(player);
        player.displayClientMessage(Component.translatable("descentmtb.trail_tool.mode",next.displayName()),true);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        if(player.isShiftKeyDown()&&getPlayerPOVHitResult(level,player,ClipContext.Fluid.NONE).getType()==HitResult.Type.MISS) {
            if(!level.isClientSide)cycle(player,stack,1);
            return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level=context.getLevel();var pos=context.getClickedPos();
        if(!RampTuning.isCopycatRamp(level.getBlockState(pos)))return InteractionResult.PASS;
        if(!level.isClientSide&&context.getPlayer()!=null)
            RampTuning.tune(level,context.getPlayer(),pos,mode(context.getItemInHand()),context.getPlayer().isShiftKeyDown());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.trail_tool.mode",mode(stack).displayName()));
        lines.add(Component.translatable("descentmtb.trail_tool.hint"));
    }
}
