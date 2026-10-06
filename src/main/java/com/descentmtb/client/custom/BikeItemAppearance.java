package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.Bell;
import com.descentmtb.custom.BikeParts.Finish;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid="descentmtb",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BikeItemAppearance {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()-> {
            var property=ResourceLocation.fromNamespaceAndPath("descentmtb","shape");
            for(var item:new net.minecraft.world.item.Item[]{ModItems.MOUNTAIN_BIKE.get(),ModItems.HARDTAIL_BIKE.get()})
                ItemProperties.register(item,property,(stack,level,entity,seed)->MountainBikeItem.buildOf(stack).shape().ordinal());
        });
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register(BikeItemAppearance::tint,ModItems.MOUNTAIN_BIKE.get(),ModItems.HARDTAIL_BIKE.get());
    }
    private static int tint(ItemStack stack,int layer) {
        var b=MountainBikeItem.buildOf(stack);
        int rgb=switch(layer) {
            case 1 -> b.tyres().rgb;
            case 2 -> b.rims().rgb;
            case 3 -> b.finish()==Finish.RAW ? 0xB9BEC4 : b.frameColor();
            case 4 -> b.fork().stanchions.rgb;
            case 5 -> b.saddle().rgb;
            case 6 -> b.bell()!=Bell.NONE && b.bell()!=Bell.RUBBER_DUCK ? 0xFFFFFF : -1;
            case 7 -> b.bell()==Bell.RUBBER_DUCK ? 0xFFFFFF : -1;
            case 8 -> b.frontLight() ? b.lightColor().rgb : -1;
            case 9 -> b.rearLight() ? b.lightColor().rgb : -1;
            default -> 0xFFFFFF;
        };
        return rgb<0 ? 0 : 0xFF000000|rgb;
    }
    private BikeItemAppearance() {}
}
