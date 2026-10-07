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
    /** Motorbike on the stand: height of its ground line above the block bottom and its shift along the facing axis (blocks). TUNE with the final model. */
    private static final double MOTO_LIFT=12.0/16,MOTO_SHIFT=0;
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
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
        if(stand.bikeType().motor()) {
            // a motorbike rests on the stand's arm by its belly, wheels clear of the floor
            pose.translate(0,MOTO_LIFT,MOTO_SHIFT);
            pose.scale(-1,-1,1);
            BikeBuildRenderer.renderMoto(stand.bikeType(),stand.moto(),pose,buffers,light);
            pose.popPose();
            return;
        }
        pose.translate(0,16.5/16,5.0/16);
        pose.scale(-1,-1,1);
        boolean dj=stand.bikeType()==com.descentmtb.entity.BikeType.HARDTAIL;
        // Put the actual seatpost centre inside the jaws, independently of frame height.
        pose.translate(0,dj ? 9.3/16 : 14.0645/16,-(dj ? 3.8376/16 : 5.0721/16));
        BikeBuildRenderer.render(stand.bikeType(),stand.build(),pose,buffers,light);
        pose.popPose();
        var mc=net.minecraft.client.Minecraft.getInstance();
        String name=stand.build().name();
        if(!name.isBlank() && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getBlockPos().equals(stand.getBlockPos())) {
            pose.pushPose();pose.translate(.5,1.4,.5);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.015f,-.015f,.015f);
            mc.font.drawInBatch(name,-mc.font.width(name)/2f,0,0xFFE2C48A,false,pose.last().pose(),buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x80000000,light);
            pose.popPose();
        }
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(BikeStandBlockEntity stand) {
        return new net.minecraft.world.phys.AABB(stand.getBlockPos()).inflate(1.2,1,1.2);
    }
}
