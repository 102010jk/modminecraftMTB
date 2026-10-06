package com.descentmtb.client.map;

import com.descentmtb.map.TrailMapItem;
import com.descentmtb.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid="descentmtb",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TrailMapRenderer extends BlockEntityWithoutLevelRenderer {
    private TrailMapRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @SubscribeEvent public static void register(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions(){private TrailMapRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new TrailMapRenderer();return renderer;}
        },ModItems.TRAIL_MAP.get());
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();pose.translate(0,1,.5);pose.scale(1/128f,-1/128f,1/128f);
        var vc=buffers.getBuffer(RenderType.entityCutoutNoCull(MapTexture.get(TrailMapItem.data(stack))));
        vertex(vc,pose,0,128,0,1,light);vertex(vc,pose,128,128,1,1,light);vertex(vc,pose,128,0,1,0,light);vertex(vc,pose,0,0,0,0,light);pose.popPose();
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vc,PoseStack pose,float x,float y,float u,float v,int light){vc.addVertex(pose.last(),x,y,0).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,0,1);}
}
