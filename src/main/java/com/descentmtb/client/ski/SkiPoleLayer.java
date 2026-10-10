package com.descentmtb.client.ski;

import com.descentmtb.client.RagdollClient;
import com.descentmtb.client.RiderPose;
import com.descentmtb.client.model.SkiModel;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.ski.SkiBrand;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the two ski poles in the skier's fists: a layer on the player renderer, so the poles follow the posed arms
 * exactly (after {@link RiderPose} has aimed them), in third person, in other players' view and in the helmet camera.
 * Only while the player rides skis.
 *
 * <p>Each pole is placed with its grip in the centre of the fist (the bottom of the arm box) and its shaft along the
 * direction {@link SkierPose} chose (trailing back and down, swung forward for a plant, tucked back under the arms,
 * sticking out of a grab), the strap side and the bend of the GS poles toward the back. The pole model is
 * {@link SkiModel#renderPole}, drawn in its pole frame (metres, +Y toward the tip, +Z back).
 */
public final class SkiPoleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** The player renderer's model scale: one unit of the layer's pose is 16 model px = 0.9375 m. */
    private static final float PLAYER_SCALE = 0.9375f;
    /** Fist centre along the arm box from the shoulder pivot at yScale 1 (px). */
    private static final float FIST = SkierPose.FIST;

    /** Adds the layer to both player renderers (wide and slim arms). Mod bus, client. */
    public static void register(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer player) player.addLayer(new SkiPoleLayer(player, skin == PlayerSkin.Model.SLIM));
        }
    }

    private final boolean slim;
    private final Vector3f fist = new Vector3f(), axisY = new Vector3f(), axisZ = new Vector3f(), axisX = new Vector3f();
    private final Quaternionf rot = new Quaternionf();
    private final Matrix3f basis = new Matrix3f();

    public SkiPoleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.slim = slim;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(player.getVehicle() instanceof MountainBikeEntity skis) || !skis.bikeType().ski()) return;
        if (skis.rsCur.bailed || RagdollClient.active(player)) return;
        float[] dirs = RiderPose.poles(player);
        if (dirs == null) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        SkiBrand brand = skis.skiBrand();
        VertexConsumer vc = buffers.getBuffer(SkiModel.renderType(brand));
        if (model.leftArm.visible) pole(pose, vc, light, overlay, brand, model.leftArm, true, dirs, 0);
        if (model.rightArm.visible) pole(pose, vc, light, overlay, brand, model.rightArm, false, dirs, 3);
    }

    private void pole(PoseStack pose, VertexConsumer vc, int light, int overlay, SkiBrand brand,
                      ModelPart arm, boolean left, float[] dirs, int i) {
        // centre of the fist: the arm's box centre line, one px above the end of the (squashed / stretched) box
        float cx = (slim ? 0.5f : 1f) * (left ? 1 : -1);
        fist.set(cx * arm.xScale, FIST * arm.yScale, 0);
        rot.rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(fist);
        fist.add(arm.x, arm.y, arm.z);

        // pole frame: +Y down the shaft toward the tip, +Z (strap, bend of a GS pole) as close to back (+Z) as it goes
        axisY.set(dirs[i], dirs[i + 1], dirs[i + 2]);
        if (axisY.lengthSquared() < 1e-6f) return;
        axisY.normalize();
        axisZ.set(0, 0, 1);
        axisZ.fma(-axisZ.dot(axisY), axisY);
        if (axisZ.lengthSquared() < 1e-4f) axisZ.set(0, 1, 0).fma(-axisY.y, axisY);   // shaft straight back: bend down
        axisZ.normalize();
        axisY.cross(axisZ, axisX);
        basis.setColumn(0, axisX).setColumn(1, axisY).setColumn(2, axisZ);

        pose.pushPose();
        pose.translate(fist.x / 16f, fist.y / 16f, fist.z / 16f);
        pose.mulPose(rot.setFromNormalized(basis));
        pose.scale(1f / PLAYER_SCALE, 1f / PLAYER_SCALE, 1f / PLAYER_SCALE);
        SkiModel.renderPole(pose, vc, light, overlay, brand);
        pose.popPose();
    }
}
