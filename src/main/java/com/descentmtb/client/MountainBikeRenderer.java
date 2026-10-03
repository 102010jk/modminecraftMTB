package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.MountainBikeModel;
import com.descentmtb.entity.MountainBikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class MountainBikeRenderer extends EntityRenderer<MountainBikeEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/mountain_bike.png");

    /** Lifts the model so the wheels sit on the ground. Tweak if it floats/sinks. */
    private static final float MODEL_Y = 0.44f;

    private final MountainBikeModel model;

    public MountainBikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new MountainBikeModel(ctx.bakeLayer(MountainBikeModel.LAYER));
        this.shadowRadius = 0.7f;
    }

    @Override
    public void render(MountainBikeEntity bike, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();

        float roll = bike.getRoll(partialTick);
        float pitch = bike.getPitchVis(partialTick);
        float fork = bike.getFork(partialTick);
        float wheel = bike.getWheelRot(partialTick);
        float steer = bike.getSteerVis(partialTick);

        pose.translate(0.0, MODEL_Y, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        pose.mulPose(Axis.ZP.rotation(roll));
        pose.mulPose(Axis.XP.rotation(-pitch));
        pose.scale(-1.0f, -1.0f, 1.0f);

        this.model.setState(wheel, fork, steer);
        VertexConsumer vc = buffers.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        pose.popPose();
        super.render(bike, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MountainBikeEntity entity) {
        return TEXTURE;
    }
}
