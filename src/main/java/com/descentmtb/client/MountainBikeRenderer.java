package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.client.model.HardtailBikeModel;
import com.descentmtb.entity.BikeType;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;
import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.V3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the enduro bike from the interpolated physics snapshot: frame pose
 * (yaw, pitch, lean) around the centre of mass, plus suspension travel,
 * steering, wheel spin and cranks on the model's bones.
 */
public class MountainBikeRenderer extends EntityRenderer<MountainBikeEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/enduro_bike.png");

    private final EnduroBikeModel model;
    private final HardtailBikeModel hardtail;
    private static final ResourceLocation HARDTAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/hardtail_bike.png");

    public MountainBikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new EnduroBikeModel(ctx.bakeLayer(EnduroBikeModel.LAYER));
        this.hardtail = new HardtailBikeModel(ctx.bakeLayer(HardtailBikeModel.LAYER));
        this.shadowRadius = 0.6f;
    }

    @Override
    public void render(MountainBikeEntity bike, float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers, int light) {
        BikeRenderState a = bike.rsPrev, b = bike.rsCur;
        double t = partialTick;
        BikeParams p = bike.params();

        // the pose stack is at the interpolated entity position; move to the interpolated COM
        V3 com = BikeRenderState.lerp(t, a.com, b.com);
        double ex = net.minecraft.util.Mth.lerp(t, bike.xo, bike.getX());
        double ey = net.minecraft.util.Mth.lerp(t, bike.yo, bike.getY());
        double ez = net.minecraft.util.Mth.lerp(t, bike.zo, bike.getZ());

        pose.pushPose();
        pose.translate(com.x - ex, com.y - ey, com.z - ez);
        pose.mulPose(Axis.YP.rotation((float) (Math.PI - BikeRenderState.lerp(t, a.yaw, b.yaw))));
        pose.mulPose(Axis.XP.rotation((float) BikeRenderState.lerp(t, a.pitch, b.pitch)));
        pose.mulPose(Axis.ZP.rotation((float) -BikeRenderState.lerp(t, a.lean, b.lean)));
        if (b.trick == Trick.TABLETOP) {
            pose.mulPose(Axis.ZP.rotation((float) (b.trickSide * 0.45
                    * TrickAnimation.ease(BikeRenderState.lerp(t, a.trickAmount, b.trickAmount)))));
        }
        // COM → model origin (ground point between the axles at full extension)
        pose.translate(0, p.axleDrop - p.wheelRadius, 0);
        pose.scale(-1, -1, 1);

        float steer = (float) BikeRenderState.lerp(t, a.steer, b.steer);
        float compF = (float) BikeRenderState.lerp(t, a.compF, b.compF);
        float compR = (float) BikeRenderState.lerp(t, a.compR, b.compR);
        float spinF = (float) BikeRenderState.lerp(t, a.spinF, b.spinF);
        float spinR = (float) BikeRenderState.lerp(t, a.spinR, b.spinR);
        float crank = (float) BikeRenderState.lerp(t, a.crank, b.crank);
        if (bike.bikeType() == BikeType.HARDTAIL) {
            hardtail.setupPose(steer, compF, compR, spinF, spinR, crank);
            hardtail.setupBrake((float) BikeRenderState.lerp(t, a.brake, b.brake));
            hardtail.setupTrick(b.trick, (float) BikeRenderState.lerp(t,
                    a.trick == b.trick ? a.trickProgress : 0, b.trickProgress), b.trickSide);
            hardtail.renderCustomized(pose, buffers, light, OverlayTexture.NO_OVERLAY, bike.build());
        } else {
            model.setupPose(steer, compF, compR, spinF, spinR, crank);
            model.setupBrake((float) BikeRenderState.lerp(t, a.brake, b.brake));
            model.renderCustomized(pose, buffers, light, OverlayTexture.NO_OVERLAY, bike.build());
        }
        pose.popPose();
        super.render(bike, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(MountainBikeEntity bike, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(bike, frustum, x, y, z) || bike.isVehicle();
    }

    @Override
    public ResourceLocation getTextureLocation(MountainBikeEntity entity) {
        return entity.bikeType() == BikeType.HARDTAIL ? HARDTAIL_TEXTURE : TEXTURE;
    }
}
