package com.descentmtb.client.custom;

import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Actual assembled bike in the inventory, including all components and decals. */
@EventBusSubscriber(modid="descentmtb",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BikeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private BikeItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()); }
    @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BikeItemRenderer renderer;
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if(renderer==null) renderer=new BikeItemRenderer();
                return renderer;
            }
        },ModItems.MOUNTAIN_BIKE.get(),ModItems.HARDTAIL_BIKE.get());
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(!(stack.getItem() instanceof MountainBikeItem item)) return;
        pose.pushPose();
        pose.translate(.5,.22,.5);
        pose.mulPose(Axis.YP.rotationDegrees(context==ItemDisplayContext.GUI ? -90 : -60));
        pose.scale(.43f,-.43f,.43f);
        BikeBuildRenderer.render(item.bikeType(),MountainBikeItem.buildOf(stack),pose,buffers,light);
        pose.popPose();
    }
}

