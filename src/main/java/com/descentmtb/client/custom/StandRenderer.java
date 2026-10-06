package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeStandBlock;
import com.descentmtb.custom.BikeStandBlockEntity;
import com.descentmtb.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="descentmtb",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StandRenderer implements BlockEntityRenderer<BikeStandBlockEntity> {
    public StandRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.BIKE_STAND_BE.get(),StandRenderer::new);
    }
    @SubscribeEvent public static void layersReloaded(EntityRenderersEvent.AddLayers event) {
        BikeBuildRenderer.invalidate();
        StickerIcons.clear();
    }
    @Override public void render(BikeStandBlockEntity stand,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(!stand.hasBike()) return;
        var facing=stand.getBlockState().getValue(BikeStandBlock.FACING);
        pose.pushPose();
        pose.translate(.5,.15,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
        if(stand.bikeType()==com.descentmtb.entity.BikeType.HARDTAIL) pose.translate(0,.3,.077);
        pose.scale(-1,-1,1);
        BikeBuildRenderer.render(stand.bikeType(),stand.build(),pose,buffers,light);
        pose.popPose();
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(BikeStandBlockEntity stand) {
        return new net.minecraft.world.phys.AABB(stand.getBlockPos()).inflate(1.2,1,1.2);
    }
}
