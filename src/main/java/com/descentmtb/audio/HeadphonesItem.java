package com.descentmtb.audio;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class HeadphonesItem extends Item implements Equipable {
    public static Runnable editor = () -> {};
    public HeadphonesItem(Properties properties) { super(properties); }
    @Override public EquipmentSlot getEquipmentSlot() { return EquipmentSlot.HEAD; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) editor.run();
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }
}
