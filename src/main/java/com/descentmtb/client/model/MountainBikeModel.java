package com.descentmtb.client.model;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Low-poly mountain bike built entirely in code (no external model files).
 *
 * <p>Authoring uses the standard entity-model convention (+Y points down,
 * front of the bike at -Z); the renderer applies the usual {@code scale(-1,-1,1)}
 * flip. Wheels are 8-segment rings that spin about their axle (X); the front
 * end is parented under a steering pivot and a compressible fork.
 *
 * <p>The texture is divided into four solid colour quadrants and every cube's
 * UV is parked inside the matching quadrant, so exact UV layout never matters.
 */
public class MountainBikeModel extends EntityModel<MountainBikeEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "mountain_bike"), "main");

    // texture quadrant origins (128x128): frame / tire / metal / dark
    private static final int FRAME_U = 4,  FRAME_V = 4;
    private static final int TIRE_U  = 68, TIRE_V  = 4;
    private static final int METAL_U = 4,  METAL_V = 68;
    private static final int DARK_U  = 68, DARK_V  = 68;

    private final ModelPart root;
    private final ModelPart steer;
    private final ModelPart fork;
    private final ModelPart frontWheel;
    private final ModelPart rearWheel;

    public MountainBikeModel(ModelPart root) {
        this.root = root;
        this.steer = root.getChild("steer");
        this.fork = this.steer.getChild("fork");
        this.frontWheel = this.fork.getChild("front_wheel");
        this.rearWheel = root.getChild("body").getChild("rear_wheel");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);

        // ---- frame (thin in X, lives in the X=0 plane) ----
        tube(body, "chain_stay", 0f, 0f, 0f, 14f, 2.0f, FRAME_U, FRAME_V);
        tube(body, "seat_tube", 0f, 0f, -15f, 9f, 2.0f, FRAME_U, FRAME_V);
        tube(body, "down_tube", 0f, 0f, -13f, -11f, 2.0f, FRAME_U, FRAME_V);
        tube(body, "top_tube", -15f, 9f, -13f, -11f, 2.0f, FRAME_U, FRAME_V);
        tube(body, "seat_stay", -15f, 9f, 0f, 14f, 1.6f, FRAME_U, FRAME_V);
        tube(body, "head_tube", -13f, -11f, -2f, -12f, 2.4f, FRAME_U, FRAME_V);

        // ---- saddle + drivetrain ----
        body.addOrReplaceChild("seat", CubeListBuilder.create()
                .texOffs(DARK_U, DARK_V).addBox(-1.5f, -17.5f, 6f, 3f, 2f, 8f), PartPose.ZERO);
        body.addOrReplaceChild("cranks", CubeListBuilder.create()
                .texOffs(DARK_U, DARK_V).addBox(-3.5f, -1f, -1f, 7f, 2f, 2f), PartPose.ZERO);
        body.addOrReplaceChild("pedal_l", CubeListBuilder.create()
                .texOffs(DARK_U, DARK_V).addBox(2.5f, 3f, -2.5f, 4f, 1f, 5f), PartPose.ZERO);
        body.addOrReplaceChild("pedal_r", CubeListBuilder.create()
                .texOffs(DARK_U, DARK_V).addBox(-6.5f, -4f, -2.5f, 4f, 1f, 5f), PartPose.ZERO);

        addWheel(body, "rear_wheel", 0f, 14f);

        // ---- steering column (pivots at the head-tube top) ----
        PartDefinition steer = root.addOrReplaceChild("steer",
                CubeListBuilder.create(), PartPose.offset(0f, -13f, -11f));
        steer.addOrReplaceChild("bars", CubeListBuilder.create()
                .texOffs(METAL_U, METAL_V).addBox(-6f, -1f, -1.5f, 12f, 1.6f, 1.6f), PartPose.ZERO);
        steer.addOrReplaceChild("stem", CubeListBuilder.create()
                .texOffs(METAL_U, METAL_V).addBox(-1f, 0f, -2f, 2f, 2f, 3f), PartPose.ZERO);

        PartDefinition fork = steer.addOrReplaceChild("fork",
                CubeListBuilder.create(), PartPose.offset(0f, 0f, 0f));
        // fork blade: from the pivot down to the front axle (relative coords)
        tube(fork, "fork_blade", 0f, 0f, 13f, -3f, 2.0f, METAL_U, METAL_V);
        addWheel(fork, "front_wheel", 13f, -3f);

        return LayerDefinition.create(mesh, 128, 128);
    }

    /** A frame tube in the X=0 plane between two (y,z) points, rotated about X. */
    private static void tube(PartDefinition parent, String name,
                             float y1, float z1, float y2, float z2, float thick, int u, int v) {
        float dy = y2 - y1, dz = z2 - z1;
        float len = Mth.sqrt(dy * dy + dz * dz);
        float midY = (y1 + y2) * 0.5f, midZ = (z1 + z2) * 0.5f;
        float angle = (float) Math.atan2(dy, dz);
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(u, v)
                        .addBox(-thick * 0.5f, -thick * 0.5f, -len * 0.5f, thick, thick, len),
                PartPose.offsetAndRotation(0f, midY, midZ, angle, 0f, 0f));
    }

    /** An 8-segment wheel ring (in the Y-Z plane) + hub, centred at (0,yc,zc). */
    private static void addWheel(PartDefinition parent, String name, float yc, float zc) {
        PartDefinition wheel = parent.addOrReplaceChild(name,
                CubeListBuilder.create(), PartPose.offset(0f, yc, zc));
        int seg = 8;
        float r = 7f;
        for (int i = 0; i < seg; i++) {
            float ang = (float) (i * Math.PI * 2.0 / seg);
            float sy = Mth.cos(ang) * r;
            float sz = Mth.sin(ang) * r;
            wheel.addOrReplaceChild("seg" + i, CubeListBuilder.create()
                            .texOffs(TIRE_U, TIRE_V)
                            .addBox(-1.2f, -1.4f, -2.6f, 2.4f, 2.8f, 5.2f),
                    PartPose.offsetAndRotation(0f, sy, sz, ang, 0f, 0f));
        }
        wheel.addOrReplaceChild(name + "_hub", CubeListBuilder.create()
                .texOffs(METAL_U, METAL_V).addBox(-2f, -1.5f, -1.5f, 4f, 3f, 3f), PartPose.ZERO);
    }

    /** Drive the animated parts. */
    public void setState(float wheelRot, float compression, float steerAngle) {
        this.steer.yRot = steerAngle;
        this.frontWheel.xRot = wheelRot;
        this.rearWheel.xRot = wheelRot;
        this.fork.y = -compression * 2.5f; // suspension travel (up = -Y in model space)
    }

    @Override
    public void setupAnim(MountainBikeEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // animation is driven explicitly via setState() from the renderer
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        this.root.render(pose, vc, light, overlay, color);
    }
}
