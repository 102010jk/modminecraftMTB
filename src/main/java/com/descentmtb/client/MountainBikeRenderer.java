package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.model.DirtBikeModel;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.client.model.HardtailBikeModel;
import com.descentmtb.client.model.MotoModel;
import com.descentmtb.client.model.PitBikeModel;
import com.descentmtb.client.model.SkiModel;
import com.descentmtb.client.model.PartTable;
import com.descentmtb.entity.BikeType;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;
import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.OneHand;
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
 * Draws the bike (bicycle or motorbike) from the interpolated physics snapshot: frame pose
 * (yaw, pitch, lean) around the centre of mass, plus suspension travel,
 * steering, wheel spin and cranks on the model's bones.
 */
public class MountainBikeRenderer extends EntityRenderer<MountainBikeEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/enduro_bike.png");

    private final EnduroBikeModel model;
    private final HardtailBikeModel hardtail;
    /** The motorbike models by {@link BikeType#ordinal()}; null for the bicycles. */
    private final MotoModel[] motos = new MotoModel[BikeType.values().length];
    /** Both ski types, every brand (one layer, a subtree per brand). */
    private final SkiModel skis;
    private static final ResourceLocation HARDTAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/hardtail_bike.png");

    public MountainBikeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new EnduroBikeModel(ctx.bakeLayer(EnduroBikeModel.LAYER));
        this.hardtail = new HardtailBikeModel(ctx.bakeLayer(HardtailBikeModel.LAYER));
        motos[BikeType.DIRT_BIKE.ordinal()] = new DirtBikeModel(ctx.bakeLayer(DirtBikeModel.LAYER));
        motos[BikeType.PIT_BIKE.ordinal()] = new PitBikeModel(ctx.bakeLayer(PitBikeModel.LAYER));
        this.skis = new SkiModel(ctx.bakeLayer(SkiModel.LAYER));
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
        PartTable.mudLevel = !ClientConfig.SPEC.isLoaded() || ClientConfig.MUD_EFFECTS.get() ? bike.mud() : 0;
        PartTable.duckSqueeze = b.oneHand
                ? (float) OneHand.squeeze(a.oneHand ? BikeRenderState.lerp(t, a.oneHandTime, b.oneHandTime) : b.oneHandTime)
                : 0f;
        MotoModel moto = motos[bike.bikeType().ordinal()];
        if (bike.bikeType().ski()) {
            // each ski (binding, boot) where SkiStance puts that foot this frame
            skis.renderPair(pose, buffers.getBuffer(SkiModel.renderType(bike.skiBrand())), light,
                    OverlayTexture.NO_OVERLAY, bike, partialTick);
        } else if (moto != null) {
            moto.setupPose(steer, compF, compR, spinF, spinR);
            moto.renderPainted(pose, buffers.getBuffer(moto.renderType(moto.texture())),
                    light, OverlayTexture.NO_OVERLAY, bike.moto()::colorOf);
        } else if (bike.bikeType() == BikeType.HARDTAIL) {
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
        PartTable.duckSqueeze = 0f;
        PartTable.mudLevel = 0f;
        pose.popPose();
        super.render(bike, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public boolean shouldRender(MountainBikeEntity bike, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(bike, frustum, x, y, z) || bike.isVehicle();
    }

    @Override
    public ResourceLocation getTextureLocation(MountainBikeEntity entity) {
        if (entity.bikeType().ski()) return SkiModel.texture(entity.skiBrand());
        return switch (entity.bikeType()) {
            case HARDTAIL -> HARDTAIL_TEXTURE;
            case DIRT_BIKE -> DirtBikeModel.TEXTURE;
            case PIT_BIKE -> PitBikeModel.TEXTURE;
            default -> TEXTURE;
        };
    }
}
