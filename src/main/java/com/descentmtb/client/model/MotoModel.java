package com.descentmtb.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared base of the motorbike models ({@link DirtBikeModel}, {@link PitBikeModel}): the common rig (bones frame /
 * steer_axis / steer / fork_upper / fork_lower / front_wheel / swingarm / rear_wheel), the pose maths, the cube
 * builders used by the generated tables, and the per-part-group paint.
 *
 * <p><b>Painting.</b> The generator paints every paintable material in a neutral light ramp (near-grey, warm
 * highlights, cool shadows), so a multiplicative vertex colour gives the target colour while keeping the shading.
 * The cubes of each group in {@link #GROUPS} are listed by the generated {@code GROUP_CUBES} table of the subclass;
 * {@link #drawPainted} renders the unpainted cubes once and then each group's cubes with its tint (visibility passes
 * over the same baked parts). Decals (race stripes, tank stripe) are separate cubes in the unpainted set; the dark
 * "77" numbers sit in the neutral texture and stay dark under any tint.
 *
 * <p>Built at 32 model units per metre (twice the bicycles' texel density) and drawn at half scale. Entity-model
 * convention as the bicycles: model +Y points DOWN, forward is -Z, +X is the rider's LEFT, origin on the ground
 * below the centre of mass at full extension.
 */
public abstract class MotoModel extends Model {
    /** Model units per metre (the bicycles use 16). */
    public static final float UNITS_PER_M = 32f;

    /** Paintable part groups, in this order. */
    public static final String[] GROUPS = {"plastic", "fender", "frame", "seat", "rim", "spring", "anodized"};

    // <GENERATED>
    /** Tint (0xRRGGBB) that reproduces the stock look of each group through the neutral paint ramp (generated). */
    private static final int[] DEFAULTS = {0xff641f, 0xfefdf4, 0x858a9b, 0x31303b, 0x3d3d49, 0xf14a1e, 0xf96020};
    // </GENERATED>

    /** Stock colour (0xRRGGBB) of a group, so an unpainted bike looks like the original dirt bike. */
    public static int defaultColor(String group) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i].equals(group)) return DEFAULTS[i];
        }
        return 0xFFFFFF;
    }

    protected final ModelPart root;
    private final ModelPart steer, forkLower, frontWheel, swingarm, rearWheel;
    /** The ModelParts of each group's cubes, parallel to {@link #GROUPS}. */
    private final ModelPart[][] groupParts;
    private final float forkMaxM, rearMaxM, armY, armZ;

    /**
     * @param groupCubes cube paths ("frame/steer_axis/steer/fork_upper/top_clamp") per group, parallel to GROUPS
     * @param forkMaxM   fork travel (m); {@code rearMaxM} rear wheel travel (m)
     * @param armYm,armZm rear axle relative to the swingarm pivot at rest (m, Y down / Z back)
     */
    protected MotoModel(ModelPart root, String[][] groupCubes, float forkMaxM, float rearMaxM, float armYm, float armZm) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        ModelPart frame = root.getChild("frame");
        this.steer = frame.getChild("steer_axis").getChild("steer");
        this.forkLower = steer.getChild("fork_lower");
        this.frontWheel = forkLower.getChild("front_wheel");
        this.swingarm = frame.getChild("swingarm");
        this.rearWheel = swingarm.getChild("rear_wheel");
        this.forkMaxM = forkMaxM;
        this.rearMaxM = rearMaxM;
        this.armY = armYm * UNITS_PER_M;
        this.armZ = armZm * UNITS_PER_M;
        this.groupParts = new ModelPart[GROUPS.length][];
        for (int g = 0; g < GROUPS.length; g++) {
            String[] paths = groupCubes[g];
            ModelPart[] parts = new ModelPart[paths.length];
            for (int i = 0; i < paths.length; i++) {
                ModelPart p = root;
                for (String s : paths[i].split("/")) p = p.getChild(s);
                parts[i] = p;
            }
            groupParts[g] = parts;
        }
    }

    /** The baked texture of this bike (neutral-ramp paint for the groups, tinted at render). */
    public abstract ResourceLocation texture();

    /**
     * Poses the moving parts. Lengths in metres, angles in radians.
     *
     * @param steerRad     steering about the raked axis, positive = to the rider's right
     * @param forkTravelM  fork compression (the stanchions slide up into the outer tubes)
     * @param rearTravelM  rear wheel travel (the swingarm swings up about its pivot)
     * @param frontSpin    front wheel spin, positive = rolling forward
     * @param rearSpin     rear wheel spin, same sign
     */
    public abstract void setupPose(float steerRad, float forkTravelM, float rearTravelM, float frontSpin, float rearSpin);

    /**
     * Draws the bike (half scale handled inside); {@code color} gives 0xRRGGBB per group name, or -1 for the stock
     * colour of that group.
     */
    public abstract void renderPainted(PoseStack pose, VertexConsumer vc, int light, int overlay,
                                       ToIntFunction<String> color);

    /** Shared pose implementation for the subclasses' {@link #setupPose}. */
    protected final void applyPose(float steerRad, float forkTravelM, float rearTravelM, float frontSpin, float rearSpin) {
        steer.yRot = steerRad;
        forkLower.y = -Mth.clamp(forkTravelM, 0f, forkMaxM) * UNITS_PER_M;
        frontWheel.xRot = frontSpin;
        swingarm.xRot = swingAngle(armY, armZ, Mth.clamp(rearTravelM, 0f, rearMaxM) * UNITS_PER_M);
        rearWheel.xRot = rearSpin;
    }

    /** Swingarm angle that lifts the rear axle by {@code up} model units (exact, the axle moves on a circle). */
    static float swingAngle(float armY, float armZ, float up) {
        double r = Math.hypot(armY, armZ), phi = Math.atan2(armZ, armY);
        double c = Math.max(-1, Math.min(1, (armY - up) / r));
        return (float) (Math.acos(c) - phi);
    }

    /** Stock colours: every group drawn in its {@link #defaultColor}. */
    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        drawPainted(pose, vc, light, overlay, color, g -> -1);
    }

    /**
     * Renders the unpainted cubes with {@code base} (ARGB, usually -1), then each group's cubes with
     * {@code base} multiplied by the group's tint.
     */
    protected final void drawPainted(PoseStack pose, VertexConsumer vc, int light, int overlay, int base,
                                     ToIntFunction<String> color) {
        pose.pushPose();
        float k = 16f / UNITS_PER_M;
        pose.scale(k, k, k);
        setGroupsVisible(false);
        try {
            root.render(pose, vc, light, overlay, base);
            for (int g = 0; g < GROUPS.length; g++) {
                ModelPart[] parts = groupParts[g];
                if (parts.length == 0) continue;
                int c = color.applyAsInt(GROUPS[g]);
                int tint = tint(base, c == -1 ? DEFAULTS[g] : c);
                for (ModelPart p : parts) p.visible = true;
                root.render(pose, vc, light, overlay, tint);
                for (ModelPart p : parts) p.visible = false;
            }
        } finally {
            setGroupsVisible(true);
        }
        pose.popPose();
    }

    private void setGroupsVisible(boolean v) {
        for (ModelPart[] parts : groupParts) for (ModelPart p : parts) p.visible = v;
    }

    /** {@code base} (ARGB) with its colour channels multiplied by the 0xRRGGBB {@code rgb}. */
    static int tint(int base, int rgb) {
        int r = ((base >> 16) & 0xFF) * ((rgb >> 16) & 0xFF) / 255;
        int g = ((base >> 8) & 0xFF) * ((rgb >> 8) & 0xFF) / 255;
        int b = (base & 0xFF) * (rgb & 0xFF) / 255;
        return (base & 0xFF000000) | r << 16 | g << 8 | b;
    }

    public ModelPart root() {
        return root;
    }

    // ------------------------------------------------------------------ builders for the generated tables
    protected static PartDefinition bone(PartDefinition parent, String name, float x, float y, float z,
                                         float rxDeg, float ryDeg, float rzDeg) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(),
                PartPose.offsetAndRotation(x, y, z, rad(rxDeg), rad(ryDeg), rad(rzDeg)));
    }

    /** Box centred at (cx, cy, cz) in the bone's space, size (sx, sy, sz), rotation in degrees (vanilla Z-Y-X). */
    protected static void cube(PartDefinition bone, String name, int u, int v, float cx, float cy, float cz,
                               float sx, float sy, float sz, float rxDeg, float ryDeg, float rzDeg) {
        bone.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(-sx / 2f, -sy / 2f, -sz / 2f, sx, sy, sz),
                PartPose.offsetAndRotation(cx, cy, cz, rad(rxDeg), rad(ryDeg), rad(rzDeg)));
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180f);
    }
}
