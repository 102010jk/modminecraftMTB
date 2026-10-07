package com.descentmtb.client.model;

import com.descentmtb.DescentMtb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.function.ToIntFunction;

/**
 * STUB (the real one comes with the model work): the 125 cc pit bike. Same bones as {@link DirtBikeModel}
 * (frame, steer_axis, steer, fork_lower, front_wheel, swingarm, rear_wheel), no cubes yet.
 */
public class PitBikeModel extends MotoModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "pit_bike"), "main");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/pit_bike.png");

    public static final float UNITS_PER_M = 32f;
    public static final float FORK_TRAVEL_M = 0.16f, REAR_TRAVEL_M = 0.15f;
    public static final Vector3f GRIP_LEFT = new Vector3f(0.33f, 0.86f, -0.28f);
    public static final Vector3f FOOTPEG_LEFT = new Vector3f(0.15f, 0.27f, 0.04f);
    public static final Vector3f STEER_PIVOT_PX = new Vector3f(0f, -12.48f, -4.8f);
    public static final Vector3f STEER_AXIS_DOWN = new Vector3f(0f, (float) Math.cos(Math.toRadians(25)), (float) -Math.sin(Math.toRadians(25)));

    private final ModelPart root, steer, forkLower, frontWheel, rearWheel;

    public PitBikeModel(ModelPart root) {
        this.root = root;
        ModelPart frame = root.getChild("frame");
        this.steer = frame.getChild("steer_axis").getChild("steer");
        this.forkLower = steer.getChild("fork_lower");
        this.frontWheel = forkLower.getChild("front_wheel");
        this.rearWheel = frame.getChild("swingarm").getChild("rear_wheel");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition frame = mesh.getRoot().addOrReplaceChild("frame", CubeListBuilder.create(), PartPose.ZERO);
        frame.addOrReplaceChild("steer_axis", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("steer", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("fork_lower", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("front_wheel", CubeListBuilder.create(), PartPose.ZERO);
        frame.addOrReplaceChild("swingarm", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("rear_wheel", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ResourceLocation texture() {
        return TEXTURE;
    }

    @Override
    public void setupPose(float steerRad, float forkTravelM, float rearTravelM, float frontSpin, float rearSpin) {
        steer.yRot = steerRad;
        forkLower.y = -Mth.clamp(forkTravelM, 0f, FORK_TRAVEL_M) * UNITS_PER_M;
        frontWheel.xRot = frontSpin;
        rearWheel.xRot = rearSpin;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        pose.pushPose();
        float k = 16f / UNITS_PER_M;
        pose.scale(k, k, k);
        root.render(pose, vc, light, overlay, color);
        pose.popPose();
    }

    @Override
    public void renderPainted(PoseStack pose, VertexConsumer vc, int light, int overlay, ToIntFunction<String> color) {
        renderToBuffer(pose, vc, light, overlay, -1);
    }
}
