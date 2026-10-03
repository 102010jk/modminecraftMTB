package com.descentmtb.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import java.util.List;

/** Sneak + use in air selects a valve; click a parked bike to pump (sneak = bleed). */
public final class BikePumpItem extends Item {
    public BikePumpItem(Properties p) { super(p); }
    public static int valve(ItemStack s) {
        return Math.floorMod(s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("valve"), 3);
    }
    public static Component valveName(int mode) { return Component.translatable("descentmtb.pump.valve." + mode); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!p.isShiftKeyDown()) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            int next = (valve(s) + 1) % 3;
            CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.putInt("valve", next));
            p.displayClientMessage(valveName(next), true);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack s, TooltipContext c, List<Component> lines, TooltipFlag flag) {
        lines.add(valveName(valve(s)));
        lines.add(Component.translatable("descentmtb.pump.hint"));
    }
}
