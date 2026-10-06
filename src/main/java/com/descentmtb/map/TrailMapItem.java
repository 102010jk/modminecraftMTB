package com.descentmtb.map;

import com.descentmtb.registry.ModComponents;
import com.descentmtb.trail.TrailSignEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

public final class TrailMapItem extends Item {
    public static java.util.function.Consumer<ItemStack> viewer=stack->{};
    public TrailMapItem(Properties properties) { super(properties); }
    public static BikeparkMap data(ItemStack stack) { return stack.getOrDefault(ModComponents.BIKEPARK_MAP.get(),BikeparkMap.EMPTY); }
    @Override public InteractionResult useOn(UseOnContext c) {
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof TrailSignEntity sign)) return InteractionResult.PASS;
        if(!c.getLevel().isClientSide && sign.track()!=null) {
            c.getItemInHand().set(ModComponents.BIKEPARK_MAP.get(),data(c.getItemInHand()).add(new BikeparkMap.Route(sign.content().name(),sign.trackDimension(),sign.track())));
            c.getPlayer().displayClientMessage(Component.translatable("descentmtb.map.added",sign.content().name()),true);
        } else if(c.getLevel().isClientSide && sign.track()==null) viewer.accept(c.getItemInHand());
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand),other=player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);
        TrailTrack track=other.getItem() instanceof TrailMarkerItem?TrailMarkerItem.track(other):null;
        if(track!=null) {
            if(!level.isClientSide) {
                stack.set(ModComponents.BIKEPARK_MAP.get(),data(stack).add(new BikeparkMap.Route(other.getHoverName().getString(),other.getOrDefault(ModComponents.TRACK_DIMENSION.get(),level.dimension().location()),track)));
                player.displayClientMessage(Component.translatable("descentmtb.map.added",other.getHoverName()),true);
            }
        } else if(other.getItem() instanceof TrailMapItem && !data(other).routes().isEmpty()) {
            if(!level.isClientSide){BikeparkMap copy=data(stack);for(var r:data(other).routes())copy=copy.add(r);stack.set(ModComponents.BIKEPARK_MAP.get(),copy);
                player.displayClientMessage(Component.translatable("descentmtb.map.copied"),true);}
        } else if(level.isClientSide) viewer.accept(stack);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext c,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.map.count",data(stack).routes().size()));
        lines.add(Component.translatable("descentmtb.map.hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
