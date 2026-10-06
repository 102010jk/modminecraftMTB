package com.descentmtb.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One replaceable goggle film; the server consumes it before confirming a clean lens. */
public final class TearOffItem extends Item {
    public TearOffItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer server){
            if(!player.getAbilities().instabuild)stack.shrink(1);
            player.getCooldowns().addCooldown(this,12);
            com.descentmtb.network.TearOffPayload.cleaned(server);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
