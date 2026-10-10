package com.descentmtb.ski;

import com.descentmtb.item.MountainBikeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A pair of skis of one brand and model. Placed and ridden like a bike (right-click a block to put them down,
 * right-click them to step in); the brand travels into the entity, see {@link SkiBrand}.
 */
public class SkiItem extends MountainBikeItem {
    private final SkiBrand brand;

    public SkiItem(Properties props, SkiBrand brand) {
        super(props, brand.type);
        this.brand = brand;
    }

    public SkiBrand brand() {
        return brand;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(brand.twinTip() ? "descentmtb.ski.kind.freestyle" : "descentmtb.ski.kind.race")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("descentmtb.ski.specs", brand.lengthCm, String.format(java.util.Locale.ROOT, "%.0f", brand.radiusM), brand.waistMm)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
