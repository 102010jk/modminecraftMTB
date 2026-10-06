package com.descentmtb.client.model;

import com.descentmtb.DescentMtb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * 26" dirt-jump / street hardtail (the bike for tailwhips and barspins), built from a vanilla
 * {@link ModelPart} hierarchy exactly like {@link EnduroBikeModel}.
 *
 * <p>Scale: 1 block = 1 m = 16 model units. Entity-model convention: model-space +Y points DOWN
 * (the renderer applies {@code scale(-1,-1,1)}), forward is -Z, +X is the rider's LEFT. The model
 * origin is on the ground midway between the two wheel contact patches.
 *
 * <pre>
 * root
 *  |- frame                 EVERYTHING rigid: front triangle, rigid rear triangle, seat, brake caliper, static chain
 *  |   |- steer_axis        pivot on the steering axis (head tube centre), xRot = -21 deg (fixed head-angle tilt)
 *  |   |   `- steer         yRot = steering angle (rotates about the tilted steering axis)
 *  |   |       |- fork_upper   crown, stanchions, steerer, stem, riser bar, grips, brake lever
 *  |   |       `- fork_lower   lowers; local y slides along the steering axis (fork travel)
 *  |   |           `- front_wheel   pivot = front axle, xRot = spin
 *  |   |- cranks            pivot = bottom bracket, xRot = crank angle (arms, single chainring)
 *  |   |   |- pedal_right / pedal_left   pivot = pedal spindle, counter-rotated to stay level
 *  |   `- rear_wheel        pivot = rear axle, xRot = spin (single cog + brake rotor included)
 * </pre>
 *
 * <p>Geometry (metres): wheels 26 x 2.3" (radius 0.33), wheelbase 1.06, BB height 0.31, chainstays ~0.39,
 * head angle 69 deg, 100 mm fork with 0.6 px offset, 35 mm stem, 0.76 m riser bars, saddle slammed at 0.75 m.
 *
 * <p>All box geometry below is a literal table (one {@code cube(...)} line per box, every box has its
 * own UV rectangle in the {@code 128}x{@code 256} texture). {@code tools/gen_hardtail_texture.py}
 * and {@code tools/preview_hardtail.py} parse this table, so keep the call format intact.
 * Material tags also drive per-component tints and visibility through PartTable.
 * After editing any box (size, position, texOffs) run {@code python tools/gen_hardtail_texture.py}: it
 * re-validates that the UV rectangles do not overlap and repaints the texture.
 */
public class HardtailBikeModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "hardtail_bike"), "main");

    // ------------------------------------------------------------------ geometry constants (metres)
    public static final float WHEEL_RADIUS_M = 0.33f;
    public static final float WHEELBASE_M = 1.06f;
    public static final float BB_HEIGHT_M = 0.31f;
    public static final float FORK_TRAVEL_M = 0.10f;
    /** Rigid frame: no rear travel (the {@code rearTravelM} argument of {@link #setupPose} is ignored). */
    public static final float REAR_TRAVEL_M = 0.0f;

    /** Rider anchor points: metres, Y UP, -Z forward, +X = rider's left, origin = ground between the wheels.
     *  Grips (grip centre) are at steering angle 0. */
    public static final Vector3f GRIP_LEFT = new Vector3f(0.3144f, 0.9997f, -0.2735f);
    public static final Vector3f GRIP_RIGHT = new Vector3f(-0.3144f, 0.9997f, -0.2735f);
    /** Top of the (slammed) saddle where the rider sits. */
    public static final Vector3f SADDLE_TOP = new Vector3f(0.0f, 0.625f, 0.2925f);
    /** Bottom bracket / crank axis. */
    public static final Vector3f PEDAL_AXIS = new Vector3f(0.0f, BB_HEIGHT_M, 0.1406f);

    /**
     * Steering axis for trick animations (tailwhip / barspin / 360 bar): a point on it (MODEL px, Y down,
     * = head tube centre) and its unit direction pointing DOWN the axis toward the front axle (Y down, -Z forward).
     * Both are in the same model space as the cube table, i.e. rotate the frame (or the bars) about
     * {@code STEER_PIVOT_PX} along {@code STEER_AXIS_DOWN}.
     */
    public static final Vector3f STEER_PIVOT_PX = new Vector3f(0.0f, -12.8137f, -4.9454f);
    public static final Vector3f STEER_AXIS_DOWN = new Vector3f(0.0f, 0.9336f, -0.3584f);

    // ------------------------------------------------------------------ pose constants (model units, Y down)
    private static final float PX = 16f;
    /** Head angle 69 deg = steering axis 21 deg from vertical; the front axle sits AXLE_TO_PIVOT px down the axis and FORK_OFFSET px ahead of it. */
    private static final float HEAD_TILT_DEG = 21.0f;
    private static final float AXLE_TO_PIVOT = 8.3f;
    private static final float FORK_OFFSET = 0.6f;
    private static final float BB_Y = -4.96f, BB_Z = 2.25f;
    private static final float CHAINRING_R = 1.05f;

    private static final PartTable TABLE = new PartTable();
    private final PartTable.Node customRoot;
    private final ModelPart root;
    private final ModelPart steer, forkLower, frontWheel, rearWheel;
    private final ModelPart cranks, pedalLeft, pedalRight;
    private final ModelPart steerAxis;
    private final ModelPart lever;
    private float tailwhip;

    public HardtailBikeModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.customRoot = TABLE.bind(root);
        ModelPart frame = root.getChild("frame");
        this.steerAxis = frame.getChild("steer_axis");
        this.steer = steerAxis.getChild("steer");
        this.lever = steer.getChild("fork_upper").getChild("lever_l");
        this.forkLower = this.steer.getChild("fork_lower");
        this.frontWheel = this.forkLower.getChild("front_wheel");
        this.rearWheel = frame.getChild("rear_wheel");
        this.cranks = frame.getChild("cranks");
        this.pedalLeft = this.cranks.getChild("pedal_left");
        this.pedalRight = this.cranks.getChild("pedal_right");
    }

    // ------------------------------------------------------------------ pose
    /**
     * Pose every moving bone. All lengths in metres, all angles in radians.
     *
     * @param steerRad       steering angle about the steering axis; POSITIVE = steer to the rider's RIGHT
     *                       (any value works, e.g. continuous rotation for barspins)
     * @param forkTravelM    fork compression 0..0.10 (lowers slide up the steering axis)
     * @param rearTravelM    ignored (rigid rear triangle); kept so the signature matches {@link EnduroBikeModel}
     * @param frontWheelRad  front wheel spin; POSITIVE = rolling forward (top of the tyre moves toward -Z)
     * @param rearWheelRad   rear wheel spin, same sign convention
     * @param crankRad       crank angle; 0 = right crank pointing forward (horizontal), POSITIVE = pedalling forward
     */
    public void setupPose(float steerRad, float forkTravelM, float rearTravelM,
                          float frontWheelRad, float rearWheelRad, float crankRad) {
        float fork = Mth.clamp(forkTravelM, 0f, FORK_TRAVEL_M) * PX;

        // --- front end: yaw about the (tilted) steering axis, lowers slide up that same axis
        this.steer.yRot = steerRad;
        this.forkLower.y = -fork;                 // local -Y of steer_axis = up along the steering axis
        this.frontWheel.xRot = frontWheelRad;

        // --- rigid rear
        this.rearWheel.xRot = rearWheelRad;

        // --- cranks and level pedals
        this.cranks.xRot = crankRad;
        this.pedalLeft.xRot = -crankRad;
        this.pedalRight.xRot = -crankRad;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        if (tailwhip == 0) { root.render(pose, vc, light, overlay, color); return; }
        // Rotate the frame/rear wheel around the head tube while the fork and
        // handlebars remain in the rider's hands.
        pose.pushPose();
        pose.translate(STEER_PIVOT_PX.x / 16, STEER_PIVOT_PX.y / 16, STEER_PIVOT_PX.z / 16);
        pose.mulPose(new org.joml.Quaternionf().rotationAxis(tailwhip, STEER_AXIS_DOWN.x, STEER_AXIS_DOWN.y, STEER_AXIS_DOWN.z));
        pose.translate(-STEER_PIVOT_PX.x / 16, -STEER_PIVOT_PX.y / 16, -STEER_PIVOT_PX.z / 16);
        steerAxis.visible = false;
        root.render(pose, vc, light, overlay, color);
        steerAxis.visible = true;
        pose.popPose();
        steerAxis.render(pose, vc, light, overlay, color);
    }

    public void setupTrick(com.descentmtb.trick.Trick trick, float progress, int side) {
        float angle = (float) (2 * Math.PI * progress * side);
        tailwhip = trick == com.descentmtb.trick.Trick.TAILWHIP ? angle : 0;
        if (trick == com.descentmtb.trick.Trick.BARSPIN) steer.yRot += angle;
    }

    public void setupBrake(float pressure) {
        lever.loadPose(lever.getInitialPose()); lever.yRot -= pressure * .28f;
    }


    public void renderCustomized(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                 int light, int overlay, com.descentmtb.custom.BikeBuild build) {
        var texture = com.descentmtb.client.custom.BikeTextures.base(com.descentmtb.entity.BikeType.HARDTAIL);
        var finish = com.descentmtb.client.custom.BikeTextures.finish(com.descentmtb.entity.BikeType.HARDTAIL, build.finish());
        if (tailwhip == 0) { customRoot.render(pose,buffers,light,overlay,build,texture,finish); return; }
        pose.pushPose();
        pose.translate(STEER_PIVOT_PX.x/16, STEER_PIVOT_PX.y/16, STEER_PIVOT_PX.z/16);
        pose.mulPose(new org.joml.Quaternionf().rotationAxis(tailwhip,STEER_AXIS_DOWN.x,STEER_AXIS_DOWN.y,STEER_AXIS_DOWN.z));
        pose.translate(-STEER_PIVOT_PX.x/16,-STEER_PIVOT_PX.y/16,-STEER_PIVOT_PX.z/16);
        steerAxis.visible=false;
        customRoot.render(pose,buffers,light,overlay,build,texture,finish);
        steerAxis.visible=true;
        pose.popPose();
        customRoot.find(steerAxis).render(pose,buffers,light,overlay,build,texture,finish);
    }

    public ModelPart root() {
        return this.root;
    }

    // ------------------------------------------------------------------ geometry table (331 cubes)
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TABLE.start(root);
        PartDefinition frame = bone(root, "frame", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(frame, "head_tube", "frame", 77, 31, 0.0f, -12.8137f, -4.9454f, 1.3f, 1.3f, 1.6f, -69.0f, 180.0f, 0.0f);
        cube(frame, "headset_lo", "black", 120, 36, 0.0f, -11.9455f, -5.2787f, 1.5f, 1.5f, 0.3f, -69.0f, 180.0f, 0.0f);
        cube(frame, "headset_hi", "accent", 0, 40, 0.0f, -13.6819f, -4.6121f, 1.5f, 1.5f, 0.3f, -69.0f, 180.0f, 0.0f);
        cube(frame, "top_tube__c", "frame", 25, 0, 0.0f, -11.0469f, -0.5548f, 0.9f, 0.9f, 9.5194f, -25.5f, 0.0f, 0.0f);
        cube(frame, "down_tube", "frame", 0, 0, 0.0f, -8.6768f, -1.4283f, 1.1f, 1.1f, 10.4584f, -45.298f, 0.0f, 0.0f);
        cube(frame, "seat_tube", "frame", 47, 0, 0.0f, -6.98f, 2.985f, 0.85f, 0.85f, 4.31f, 70.0f, 0.0f, 0.0f);
        cube(frame, "seat_collar", "black", 35, 55, 0.0f, -9.0750f, 3.7557f, 1.05f, 1.05f, 0.3725f, 70.0f, 0.0f, 0.0f);
        cube(frame, "seatpost", "silver", 39, 55, 0.0f, -9.3000f, 3.8376f, 0.52f, 0.52f, 0.6385f, 70.0f, 0.0f, 0.0f);
        cube(frame, "saddle_nose", "saddle", 96, 36, 0.0f, -9.8100f, 2.9800f, 0.9f, 0.38f, 1.5f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_mid", "saddle", 89, 36, 0.0f, -9.7800f, 4.2800f, 1.45f, 0.44f, 1.5f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rear", "saddle", 36, 31, 0.0f, -9.7800f, 5.6800f, 1.95f, 0.44f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_under", "saddle", 66, 13, 0.0f, -9.5200f, 4.3800f, 1.2f, 0.16f, 3.5f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rail_l", "silver", 117, 26, 0.55f, -9.3800f, 4.4800f, 0.1f, 0.12f, 3.3f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rail_r", "silver", 0, 31, -0.55f, -9.3800f, 4.4800f, 0.1f, 0.12f, 3.3f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_clamp", "black", 5, 40, 0.0f, -9.3400f, 3.9021f, 0.9f, 0.3f, 0.85f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_shell", "frame", 84, 31, 0.0f, -4.96f, 2.25f, 1.5f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_cup_l", "silver", 99, 55, 0.82f, -4.96f, 2.25f, 0.2f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_cup_r", "silver", 102, 55, -0.82f, -4.96f, 2.25f, 0.2f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(frame, "chainstay_l", "frame", 95, 0, 1.0f, -5.12f, 5.365f, 0.42f, 0.6f, 6.0494f, 3.9812f, 2.2792f, 0.0f);
        cube(frame, "seatstay_l", "frame", 63, 0, 0.96f, -7.14f, 6.095f, 0.4f, 0.5f, 6.06f, -37.5f, 4.4531f, 0.0f);
        cube(frame, "dropout_l", "black", 37, 36, 1.2f, -5.28f, 8.55f, 0.4f, 1.3f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(frame, "chainstay_r", "frame", 109, 0, -1.0f, -5.12f, 5.365f, 0.42f, 0.6f, 6.0494f, 3.9812f, -2.2792f, 0.0f);
        cube(frame, "seatstay_r", "frame", 79, 0, -0.96f, -7.14f, 6.095f, 0.4f, 0.5f, 6.06f, -37.5f, -4.4531f, 0.0f);
        cube(frame, "dropout_r", "black", 41, 36, -1.2f, -5.28f, 8.55f, 0.4f, 1.3f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(frame, "seat_bridge", "frame", 102, 36, 0.0f, -8.60f, 4.321f, 1.8f, 0.5f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(frame, "caliper_r", "brake", 43, 55, 0.85f, -4.43f, 7.63f, 0.55f, 1.0f, 0.9f, 0.0f, 0.0f, 0.0f);
        cube(frame, "caliper_bolt", "silver", 123, 60, 1.15f, -4.68f, 7.58f, 0.14f, 0.14f, 0.14f, 0.0f, 0.0f, 0.0f);
        cube(frame, "chain_top", "chain", 0, 13, -0.8f, -5.955f, 5.365f, 0.1f, 0.2f, 6.231f, -1.0115f, 0.0f, 0.0f);
        cube(frame, "chain_bottom", "chain", 14, 13, -0.8f, -4.285f, 5.365f, 0.1f, 0.2f, 6.275f, 6.8645f, 0.0f, 0.0f);
        PartDefinition steer_axis = bone(frame, "steer_axis", 0.0f, -12.8137f, -4.9454f, -21.0f, 0.0f, 0.0f);
        PartDefinition steer = bone(steer_axis, "steer", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition fork_upper = bone(steer, "fork_upper", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        fork_upper.addOrReplaceChild("mini_pump", CubeListBuilder.create().texOffs(58, 13)
                .addBox(1.9f, 1.8f, 0.1f, 0.35f, 3.8f, 0.35f), PartPose.ZERO);
        cube(fork_upper, "crown", "black", 24, 31, 0.0f, 1.05f, 0.05f, 3.5f, 0.6f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "steerer", "silver", 20, 31, 0.0f, -0.7f, 0.0f, 0.6f, 3.0f, 0.6f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "top_cap", "silver", 10, 40, 0.0f, -2.3f, 0.0f, 1.0f, 0.22f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_body", "black", 91, 31, 0.0f, -1.55f, -0.3f, 1.25f, 0.85f, 1.55f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_bolt_l", "silver", 125, 60, 0.42f, -1.55f, -1.1f, 0.22f, 0.22f, 0.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_bolt_r", "silver", 0, 62, -0.42f, -1.55f, -1.1f, 0.22f, 0.22f, 0.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "spacer", "silver", 15, 40, 0.0f, -1.0f, 0.0f, 0.95f, 0.16f, 0.95f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stanchion_l", "stanchion", 58, 13, 1.3f, 3.325f, 0.0f, 0.62f, 3.95f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stanchion_r", "stanchion", 62, 13, -1.3f, 3.325f, 0.0f, 0.62f, 3.95f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "bar_c", "bars", 98, 31, 0.0f, -1.55f, -0.56f, 0.5f, 0.5f, 2.1f, 0.0f, 90.0f, 0.0f);
        cube(fork_upper, "bar_riser_l", "bars", 7, 36, 1.5f, -2.1335f, -0.784f, 0.5f, 0.5f, 1.5403f, 49.2559f, 116.4611f, 0.0f);
        cube(fork_upper, "bar_top_l", "bars", 105, 31, 2.965f, -2.8676f, -0.8762f, 0.5f, 0.5f, 2.0691f, 8.3705f, 82.6054f, 0.0f);
        cube(fork_upper, "grip_l", "grip", 112, 31, 5.03f, -3.174f, -0.6082f, 0.68f, 0.68f, 2.1404f, 8.3705f, 82.6054f, 0.0f);
        cube(fork_upper, "bar_riser_r", "bars", 13, 36, -1.5f, -2.1335f, -0.784f, 0.5f, 0.5f, 1.5403f, 49.2559f, -116.4611f, 0.0f);
        cube(fork_upper, "bar_top_r", "bars", 119, 31, -2.965f, -2.8676f, -0.8762f, 0.5f, 0.5f, 2.0691f, 8.3705f, -82.6054f, 0.0f);
        cube(fork_upper, "grip_r", "grip", 0, 36, -5.03f, -3.174f, -0.6082f, 0.68f, 0.68f, 2.1404f, 8.3705f, -82.6054f, 0.0f);
        cube(fork_upper, "lever_clamp_l", "brake", 47, 55, 3.7f, -2.9766f, -0.7808f, 0.5f, 0.5f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "lever_l", "brake", 19, 36, 4.0f, -2.2428f, -1.7577f, 0.16f, 0.34f, 1.9098f, -33.8721f, 172.7541f, 0.0f);
        PartDefinition fork_lower = bone(steer, "fork_lower", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_up_l", "lower", 46, 13, 1.3f, 5.35f, -0.1f, 0.95f, 3.5f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_lo_l", "lower", 8, 31, 1.3f, 8.1f, -0.5f, 0.95f, 2.0f, 1.2f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "axle_cap_l", "silver", 51, 55, 1.55f, 8.3f, -0.6f, 0.3f, 0.75f, 0.75f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "seal_l", "silver", 121, 55, 1.3f, 3.65f, 0.0f, 0.72f, 0.12f, 0.72f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_up_r", "lower", 52, 13, -1.3f, 5.35f, -0.1f, 0.95f, 3.5f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_lo_r", "lower", 14, 31, -1.3f, 8.1f, -0.5f, 0.95f, 2.0f, 1.2f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "axle_cap_r", "silver", 55, 55, -1.55f, 8.3f, -0.6f, 0.3f, 0.75f, 0.75f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "seal_r", "silver", 0, 58, -1.3f, 3.65f, 0.0f, 0.72f, 0.12f, 0.72f, 0.0f, 0.0f, 0.0f);
        PartDefinition front_wheel = bone(fork_lower, "front_wheel", 0.0f, 8.3f, -0.6f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire0", "tire", 20, 40, 0.0f, 4.72f, 0.0f, 0.95f, 0.8f, 1.04f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire1", "tire", 25, 40, 0.0f, 4.6293f, 0.9208f, 0.95f, 0.8f, 1.04f, 11.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire2", "tire", 30, 40, 0.0f, 4.3607f, 1.8063f, 0.95f, 0.8f, 1.04f, 22.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire3", "tire", 35, 40, 0.0f, 3.9245f, 2.6223f, 0.95f, 0.8f, 1.04f, 33.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire4", "tire", 40, 40, 0.0f, 3.3375f, 3.3375f, 0.95f, 0.8f, 1.04f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire5", "tire", 45, 40, 0.0f, 2.6223f, 3.9245f, 0.95f, 0.8f, 1.04f, 56.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire6", "tire", 50, 40, 0.0f, 1.8063f, 4.3607f, 0.95f, 0.8f, 1.04f, 67.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire7", "tire", 55, 40, 0.0f, 0.9208f, 4.6293f, 0.95f, 0.8f, 1.04f, 78.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire8", "tire", 60, 40, 0.0f, 0.0f, 4.72f, 0.95f, 0.8f, 1.04f, 90.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire9", "tire", 65, 40, 0.0f, -0.9208f, 4.6293f, 0.95f, 0.8f, 1.04f, 101.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire10", "tire", 70, 40, 0.0f, -1.8063f, 4.3607f, 0.95f, 0.8f, 1.04f, 112.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire11", "tire", 75, 40, 0.0f, -2.6223f, 3.9245f, 0.95f, 0.8f, 1.04f, 123.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire12", "tire", 80, 40, 0.0f, -3.3375f, 3.3375f, 0.95f, 0.8f, 1.04f, 135.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire13", "tire", 85, 40, 0.0f, -3.9245f, 2.6223f, 0.95f, 0.8f, 1.04f, 146.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire14", "tire", 90, 40, 0.0f, -4.3607f, 1.8063f, 0.95f, 0.8f, 1.04f, 157.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire15", "tire", 95, 40, 0.0f, -4.6293f, 0.9208f, 0.95f, 0.8f, 1.04f, 168.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire16", "tire", 100, 40, 0.0f, -4.72f, 0.0f, 0.95f, 0.8f, 1.04f, 180.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire17", "tire", 105, 40, 0.0f, -4.6293f, -0.9208f, 0.95f, 0.8f, 1.04f, 191.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire18", "tire", 110, 40, 0.0f, -4.3607f, -1.8063f, 0.95f, 0.8f, 1.04f, 202.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire19", "tire", 115, 40, 0.0f, -3.9245f, -2.6223f, 0.95f, 0.8f, 1.04f, 213.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire20", "tire", 120, 40, 0.0f, -3.3375f, -3.3375f, 0.95f, 0.8f, 1.04f, 225.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire21", "tire", 0, 43, 0.0f, -2.6223f, -3.9245f, 0.95f, 0.8f, 1.04f, 236.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire22", "tire", 5, 43, 0.0f, -1.8063f, -4.3607f, 0.95f, 0.8f, 1.04f, 247.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire23", "tire", 10, 43, 0.0f, -0.9208f, -4.6293f, 0.95f, 0.8f, 1.04f, 258.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire24", "tire", 15, 43, 0.0f, 0.0f, -4.72f, 0.95f, 0.8f, 1.04f, 270.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire25", "tire", 20, 43, 0.0f, 0.9208f, -4.6293f, 0.95f, 0.8f, 1.04f, 281.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire26", "tire", 25, 43, 0.0f, 1.8063f, -4.3607f, 0.95f, 0.8f, 1.04f, 292.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire27", "tire", 30, 43, 0.0f, 2.6223f, -3.9245f, 0.95f, 0.8f, 1.04f, 303.75f, 0.0f, 0.0f);
        cube(front_wheel, "tire28", "tire", 35, 43, 0.0f, 3.3375f, -3.3375f, 0.95f, 0.8f, 1.04f, 315.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire29", "tire", 40, 43, 0.0f, 3.9245f, -2.6223f, 0.95f, 0.8f, 1.04f, 326.25f, 0.0f, 0.0f);
        cube(front_wheel, "tire30", "tire", 45, 43, 0.0f, 4.3607f, -1.8063f, 0.95f, 0.8f, 1.04f, 337.5f, 0.0f, 0.0f);
        cube(front_wheel, "tire31", "tire", 50, 43, 0.0f, 4.6293f, -0.9208f, 0.95f, 0.8f, 1.04f, 348.75f, 0.0f, 0.0f);
        cube(front_wheel, "knob0", "tread", 4, 58, 0.0f, 5.175f, 0.5097f, 0.46f, 0.16f, 0.56f, 5.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob1", "tread", 92, 58, 0.3f, 4.9761f, 1.5095f, 0.3f, 0.16f, 0.56f, 16.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob2", "tread", 95, 58, -0.3f, 4.586f, 2.4513f, 0.3f, 0.16f, 0.56f, 28.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob3", "tread", 8, 58, 0.0f, 4.0197f, 3.2988f, 0.46f, 0.16f, 0.56f, 39.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob4", "tread", 98, 58, 0.3f, 3.2988f, 4.0197f, 0.3f, 0.16f, 0.56f, 50.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob5", "tread", 101, 58, -0.3f, 2.4513f, 4.586f, 0.3f, 0.16f, 0.56f, 61.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob6", "tread", 12, 58, 0.0f, 1.5095f, 4.9761f, 0.46f, 0.16f, 0.56f, 73.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob7", "tread", 104, 58, 0.3f, 0.5097f, 5.175f, 0.3f, 0.16f, 0.56f, 84.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob8", "tread", 107, 58, -0.3f, -0.5097f, 5.175f, 0.3f, 0.16f, 0.56f, 95.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob9", "tread", 16, 58, 0.0f, -1.5095f, 4.9761f, 0.46f, 0.16f, 0.56f, 106.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob10", "tread", 110, 58, 0.3f, -2.4513f, 4.586f, 0.3f, 0.16f, 0.56f, 118.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob11", "tread", 113, 58, -0.3f, -3.2988f, 4.0197f, 0.3f, 0.16f, 0.56f, 129.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob12", "tread", 20, 58, 0.0f, -4.0197f, 3.2988f, 0.46f, 0.16f, 0.56f, 140.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob13", "tread", 116, 58, 0.3f, -4.586f, 2.4513f, 0.3f, 0.16f, 0.56f, 151.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob14", "tread", 119, 58, -0.3f, -4.9761f, 1.5095f, 0.3f, 0.16f, 0.56f, 163.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob15", "tread", 24, 58, 0.0f, -5.175f, 0.5097f, 0.46f, 0.16f, 0.56f, 174.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob16", "tread", 122, 58, 0.3f, -5.175f, -0.5097f, 0.3f, 0.16f, 0.56f, 185.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob17", "tread", 125, 58, -0.3f, -4.9761f, -1.5095f, 0.3f, 0.16f, 0.56f, 196.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob18", "tread", 28, 58, 0.0f, -4.586f, -2.4513f, 0.46f, 0.16f, 0.56f, 208.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob19", "tread", 0, 60, 0.3f, -4.0197f, -3.2988f, 0.3f, 0.16f, 0.56f, 219.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob20", "tread", 3, 60, -0.3f, -3.2988f, -4.0197f, 0.3f, 0.16f, 0.56f, 230.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob21", "tread", 32, 58, 0.0f, -2.4513f, -4.586f, 0.46f, 0.16f, 0.56f, 241.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob22", "tread", 6, 60, 0.3f, -1.5095f, -4.9761f, 0.3f, 0.16f, 0.56f, 253.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob23", "tread", 9, 60, -0.3f, -0.5097f, -5.175f, 0.3f, 0.16f, 0.56f, 264.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob24", "tread", 36, 58, 0.0f, 0.5097f, -5.175f, 0.46f, 0.16f, 0.56f, 275.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob25", "tread", 12, 60, 0.3f, 1.5095f, -4.9761f, 0.3f, 0.16f, 0.56f, 286.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob26", "tread", 15, 60, -0.3f, 2.4513f, -4.586f, 0.3f, 0.16f, 0.56f, 298.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob27", "tread", 40, 58, 0.0f, 3.2988f, -4.0197f, 0.46f, 0.16f, 0.56f, 309.375f, 0.0f, 0.0f);
        cube(front_wheel, "knob28", "tread", 18, 60, 0.3f, 4.0197f, -3.2988f, 0.3f, 0.16f, 0.56f, 320.625f, 0.0f, 0.0f);
        cube(front_wheel, "knob29", "tread", 21, 60, -0.3f, 4.586f, -2.4513f, 0.3f, 0.16f, 0.56f, 331.875f, 0.0f, 0.0f);
        cube(front_wheel, "knob30", "tread", 44, 58, 0.0f, 4.9761f, -1.5095f, 0.46f, 0.16f, 0.56f, 343.125f, 0.0f, 0.0f);
        cube(front_wheel, "knob31", "tread", 24, 60, 0.3f, 5.175f, -0.5097f, 0.3f, 0.16f, 0.56f, 354.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim0", "rim", 55, 43, 0.0f, 4.0803f, 0.4019f, 0.78f, 0.45f, 0.88f, 5.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim1", "rim", 60, 43, 0.0f, 3.9235f, 1.1902f, 0.78f, 0.45f, 0.88f, 16.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim2", "rim", 65, 43, 0.0f, 3.6159f, 1.9327f, 0.78f, 0.45f, 0.88f, 28.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim3", "rim", 70, 43, 0.0f, 3.1693f, 2.601f, 0.78f, 0.45f, 0.88f, 39.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim4", "rim", 75, 43, 0.0f, 2.601f, 3.1693f, 0.78f, 0.45f, 0.88f, 50.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim5", "rim", 80, 43, 0.0f, 1.9327f, 3.6159f, 0.78f, 0.45f, 0.88f, 61.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim6", "rim", 85, 43, 0.0f, 1.1902f, 3.9235f, 0.78f, 0.45f, 0.88f, 73.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim7", "rim", 90, 43, 0.0f, 0.4019f, 4.0803f, 0.78f, 0.45f, 0.88f, 84.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim8", "rim", 95, 43, 0.0f, -0.4019f, 4.0803f, 0.78f, 0.45f, 0.88f, 95.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim9", "rim", 100, 43, 0.0f, -1.1902f, 3.9235f, 0.78f, 0.45f, 0.88f, 106.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim10", "rim", 105, 43, 0.0f, -1.9327f, 3.6159f, 0.78f, 0.45f, 0.88f, 118.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim11", "rim", 110, 43, 0.0f, -2.601f, 3.1693f, 0.78f, 0.45f, 0.88f, 129.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim12", "rim", 115, 43, 0.0f, -3.1693f, 2.601f, 0.78f, 0.45f, 0.88f, 140.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim13", "rim", 120, 43, 0.0f, -3.6159f, 1.9327f, 0.78f, 0.45f, 0.88f, 151.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim14", "rim", 0, 46, 0.0f, -3.9235f, 1.1902f, 0.78f, 0.45f, 0.88f, 163.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim15", "rim", 5, 46, 0.0f, -4.0803f, 0.4019f, 0.78f, 0.45f, 0.88f, 174.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim16", "rim", 10, 46, 0.0f, -4.0803f, -0.4019f, 0.78f, 0.45f, 0.88f, 185.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim17", "rim", 15, 46, 0.0f, -3.9235f, -1.1902f, 0.78f, 0.45f, 0.88f, 196.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim18", "rim", 20, 46, 0.0f, -3.6159f, -1.9327f, 0.78f, 0.45f, 0.88f, 208.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim19", "rim", 25, 46, 0.0f, -3.1693f, -2.601f, 0.78f, 0.45f, 0.88f, 219.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim20", "rim", 30, 46, 0.0f, -2.601f, -3.1693f, 0.78f, 0.45f, 0.88f, 230.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim21", "rim", 35, 46, 0.0f, -1.9327f, -3.6159f, 0.78f, 0.45f, 0.88f, 241.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim22", "rim", 40, 46, 0.0f, -1.1902f, -3.9235f, 0.78f, 0.45f, 0.88f, 253.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim23", "rim", 45, 46, 0.0f, -0.4019f, -4.0803f, 0.78f, 0.45f, 0.88f, 264.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim24", "rim", 50, 46, 0.0f, 0.4019f, -4.0803f, 0.78f, 0.45f, 0.88f, 275.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim25", "rim", 55, 46, 0.0f, 1.1902f, -3.9235f, 0.78f, 0.45f, 0.88f, 286.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim26", "rim", 60, 46, 0.0f, 1.9327f, -3.6159f, 0.78f, 0.45f, 0.88f, 298.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim27", "rim", 65, 46, 0.0f, 2.601f, -3.1693f, 0.78f, 0.45f, 0.88f, 309.375f, 0.0f, 0.0f);
        cube(front_wheel, "rim28", "rim", 70, 46, 0.0f, 3.1693f, -2.601f, 0.78f, 0.45f, 0.88f, 320.625f, 0.0f, 0.0f);
        cube(front_wheel, "rim29", "rim", 75, 46, 0.0f, 3.6159f, -1.9327f, 0.78f, 0.45f, 0.88f, 331.875f, 0.0f, 0.0f);
        cube(front_wheel, "rim30", "rim", 80, 46, 0.0f, 3.9235f, -1.1902f, 0.78f, 0.45f, 0.88f, 343.125f, 0.0f, 0.0f);
        cube(front_wheel, "rim31", "rim", 85, 46, 0.0f, 4.0803f, -0.4019f, 0.78f, 0.45f, 0.88f, 354.375f, 0.0f, 0.0f);
        cube(front_wheel, "spoke0", "spoke", 77, 13, 0.22f, 2.0438f, 0.9272f, 0.08f, 0.08f, 3.4318f, -57.2919f, 0.0f, 0.0f);
        cube(front_wheel, "spoke1", "spoke", 86, 13, -0.22f, 2.2431f, -0.0745f, 0.08f, 0.08f, 3.4318f, -79.7919f, 180.0f, 0.0f);
        cube(front_wheel, "spoke2", "spoke", 95, 13, 0.22f, 0.7896f, 2.1008f, 0.08f, 0.08f, 3.4318f, -12.2919f, 0.0f, 0.0f);
        cube(front_wheel, "spoke3", "spoke", 104, 13, -0.22f, 1.6388f, 1.5334f, 0.08f, 0.08f, 3.4318f, -55.2081f, 0.0f, 0.0f);
        cube(front_wheel, "spoke4", "spoke", 113, 13, 0.22f, -0.9272f, 2.0438f, 0.08f, 0.08f, 3.4318f, 32.7081f, 0.0f, 0.0f);
        cube(front_wheel, "spoke5", "spoke", 0, 21, -0.22f, 0.0745f, 2.2431f, 0.08f, 0.08f, 3.4318f, -10.2081f, 0.0f, 0.0f);
        cube(front_wheel, "spoke6", "spoke", 9, 21, 0.22f, -2.1008f, 0.7896f, 0.08f, 0.08f, 3.4318f, 77.7081f, 0.0f, 0.0f);
        cube(front_wheel, "spoke7", "spoke", 18, 21, -0.22f, -1.5334f, 1.6388f, 0.08f, 0.08f, 3.4318f, 34.7919f, 0.0f, 0.0f);
        cube(front_wheel, "spoke8", "spoke", 27, 21, 0.22f, -2.0438f, -0.9272f, 0.08f, 0.08f, 3.4318f, 57.2919f, 180.0f, 0.0f);
        cube(front_wheel, "spoke9", "spoke", 36, 21, -0.22f, -2.2431f, 0.0745f, 0.08f, 0.08f, 3.4318f, 79.7919f, 0.0f, 0.0f);
        cube(front_wheel, "spoke10", "spoke", 45, 21, 0.22f, -0.7896f, -2.1008f, 0.08f, 0.08f, 3.4318f, 12.2919f, 180.0f, 0.0f);
        cube(front_wheel, "spoke11", "spoke", 54, 21, -0.22f, -1.6388f, -1.5334f, 0.08f, 0.08f, 3.4318f, 55.2081f, 180.0f, 0.0f);
        cube(front_wheel, "spoke12", "spoke", 63, 21, 0.22f, 0.9272f, -2.0438f, 0.08f, 0.08f, 3.4318f, -32.7081f, 180.0f, 0.0f);
        cube(front_wheel, "spoke13", "spoke", 72, 21, -0.22f, -0.0745f, -2.2431f, 0.08f, 0.08f, 3.4318f, 10.2081f, 180.0f, 0.0f);
        cube(front_wheel, "spoke14", "spoke", 81, 21, 0.22f, 2.1008f, -0.7896f, 0.08f, 0.08f, 3.4318f, -77.7081f, 180.0f, 0.0f);
        cube(front_wheel, "spoke15", "spoke", 90, 21, -0.22f, 1.5334f, -1.6388f, 0.08f, 0.08f, 3.4318f, -34.7919f, 180.0f, 0.0f);
        cube(front_wheel, "hub_barrel", "hub", 108, 36, 0.0f, 0.0f, 0.0f, 1.6f, 0.55f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange", "hub", 45, 36, 0.55f, 0.0f, 0.0f, 0.12f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange_x", "hub", 49, 36, 0.55f, 0.0f, 0.0f, 0.11f, 1.1f, 1.1f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "axle_end", "silver", 27, 60, 0.9f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange1", "hub", 53, 36, -0.55f, 0.0f, 0.0f, 0.12f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange_x1", "hub", 57, 36, -0.55f, 0.0f, 0.0f, 0.11f, 1.1f, 1.1f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "axle_end1", "silver", 30, 60, -0.9f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        PartDefinition cranks = bone(frame, "cranks", 0.0f, -4.96f, 2.25f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "spindle", "silver", 114, 55, 0.0f, 0.0f, 0.0f, 2.5f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_r", "black", 28, 13, -1.15f, 0.0f, -1.4f, 0.4f, 0.66f, 3.4f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_l", "black", 37, 13, 1.15f, 0.0f, 1.4f, 0.4f, 0.66f, 3.4f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_r_bolt", "silver", 33, 60, -1.38f, 0.0f, 0.0f, 0.08f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_l_bolt", "silver", 36, 60, 1.38f, 0.0f, 0.0f, 0.08f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring", "silver", 25, 36, -0.8f, 0.0f, 0.0f, 0.1f, 0.8438f, 2.0371f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring_b", "silver", 77, 36, -0.8f, 0.0f, 0.0f, 0.088f, 2.0371f, 0.8438f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring_c", "silver", 31, 36, -0.8f, 0.0f, 0.0f, 0.076f, 0.8438f, 2.0371f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "chainring_d", "silver", 80, 36, -0.8f, 0.0f, 0.0f, 0.064f, 2.0371f, 0.8438f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "spider", "black", 2, 62, -0.97f, 0.3536f, 0.3536f, 0.3f, 0.5f, 0.18f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "spider1", "black", 4, 62, -0.97f, -0.3536f, 0.3536f, 0.3f, 0.5f, 0.18f, 135.0f, 0.0f, 0.0f);
        cube(cranks, "spider2", "black", 6, 62, -0.97f, -0.3536f, -0.3536f, 0.3f, 0.5f, 0.18f, 225.0f, 0.0f, 0.0f);
        cube(cranks, "spider3", "black", 8, 62, -0.97f, 0.3536f, -0.3536f, 0.3f, 0.5f, 0.18f, 315.0f, 0.0f, 0.0f);
        PartDefinition pedal_right = bone(cranks, "pedal_right", -2.18f, 0.0f, -2.8f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "platform", "pedal", 45, 31, 0.0f, 0.0f, 0.0f, 1.6f, 0.4f, 1.8f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "pins", "silver", 53, 31, 0.0f, 0.0f, 0.0f, 1.45f, 0.5f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "spindle", "silver", 39, 60, 0.82f, 0.0f, 0.0f, 0.55f, 0.22f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "end_cap", "black", 42, 60, -0.72f, 0.0f, 0.0f, 0.18f, 0.4f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition pedal_left = bone(cranks, "pedal_left", 2.18f, 0.0f, 2.8f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "platform", "pedal", 61, 31, 0.0f, 0.0f, 0.0f, 1.6f, 0.4f, 1.8f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "pins", "silver", 69, 31, 0.0f, 0.0f, 0.0f, 1.45f, 0.5f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "spindle", "silver", 45, 60, -0.82f, 0.0f, 0.0f, 0.55f, 0.22f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "end_cap", "black", 48, 60, 0.72f, 0.0f, 0.0f, 0.18f, 0.4f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition rear_wheel = bone(frame, "rear_wheel", 0.0f, -5.28f, 8.48f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire0", "tire", 90, 46, 0.0f, 4.72f, 0.0f, 0.95f, 0.8f, 1.04f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire1", "tire", 95, 46, 0.0f, 4.6293f, 0.9208f, 0.95f, 0.8f, 1.04f, 11.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire2", "tire", 100, 46, 0.0f, 4.3607f, 1.8063f, 0.95f, 0.8f, 1.04f, 22.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire3", "tire", 105, 46, 0.0f, 3.9245f, 2.6223f, 0.95f, 0.8f, 1.04f, 33.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire4", "tire", 110, 46, 0.0f, 3.3375f, 3.3375f, 0.95f, 0.8f, 1.04f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire5", "tire", 115, 46, 0.0f, 2.6223f, 3.9245f, 0.95f, 0.8f, 1.04f, 56.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire6", "tire", 120, 46, 0.0f, 1.8063f, 4.3607f, 0.95f, 0.8f, 1.04f, 67.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire7", "tire", 0, 49, 0.0f, 0.9208f, 4.6293f, 0.95f, 0.8f, 1.04f, 78.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire8", "tire", 5, 49, 0.0f, 0.0f, 4.72f, 0.95f, 0.8f, 1.04f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire9", "tire", 10, 49, 0.0f, -0.9208f, 4.6293f, 0.95f, 0.8f, 1.04f, 101.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire10", "tire", 15, 49, 0.0f, -1.8063f, 4.3607f, 0.95f, 0.8f, 1.04f, 112.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire11", "tire", 20, 49, 0.0f, -2.6223f, 3.9245f, 0.95f, 0.8f, 1.04f, 123.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire12", "tire", 25, 49, 0.0f, -3.3375f, 3.3375f, 0.95f, 0.8f, 1.04f, 135.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire13", "tire", 30, 49, 0.0f, -3.9245f, 2.6223f, 0.95f, 0.8f, 1.04f, 146.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire14", "tire", 35, 49, 0.0f, -4.3607f, 1.8063f, 0.95f, 0.8f, 1.04f, 157.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire15", "tire", 40, 49, 0.0f, -4.6293f, 0.9208f, 0.95f, 0.8f, 1.04f, 168.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire16", "tire", 45, 49, 0.0f, -4.72f, 0.0f, 0.95f, 0.8f, 1.04f, 180.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire17", "tire", 50, 49, 0.0f, -4.6293f, -0.9208f, 0.95f, 0.8f, 1.04f, 191.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire18", "tire", 55, 49, 0.0f, -4.3607f, -1.8063f, 0.95f, 0.8f, 1.04f, 202.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire19", "tire", 60, 49, 0.0f, -3.9245f, -2.6223f, 0.95f, 0.8f, 1.04f, 213.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire20", "tire", 65, 49, 0.0f, -3.3375f, -3.3375f, 0.95f, 0.8f, 1.04f, 225.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire21", "tire", 70, 49, 0.0f, -2.6223f, -3.9245f, 0.95f, 0.8f, 1.04f, 236.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire22", "tire", 75, 49, 0.0f, -1.8063f, -4.3607f, 0.95f, 0.8f, 1.04f, 247.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire23", "tire", 80, 49, 0.0f, -0.9208f, -4.6293f, 0.95f, 0.8f, 1.04f, 258.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire24", "tire", 85, 49, 0.0f, 0.0f, -4.72f, 0.95f, 0.8f, 1.04f, 270.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire25", "tire", 90, 49, 0.0f, 0.9208f, -4.6293f, 0.95f, 0.8f, 1.04f, 281.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire26", "tire", 95, 49, 0.0f, 1.8063f, -4.3607f, 0.95f, 0.8f, 1.04f, 292.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire27", "tire", 100, 49, 0.0f, 2.6223f, -3.9245f, 0.95f, 0.8f, 1.04f, 303.75f, 0.0f, 0.0f);
        cube(rear_wheel, "tire28", "tire", 105, 49, 0.0f, 3.3375f, -3.3375f, 0.95f, 0.8f, 1.04f, 315.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire29", "tire", 110, 49, 0.0f, 3.9245f, -2.6223f, 0.95f, 0.8f, 1.04f, 326.25f, 0.0f, 0.0f);
        cube(rear_wheel, "tire30", "tire", 115, 49, 0.0f, 4.3607f, -1.8063f, 0.95f, 0.8f, 1.04f, 337.5f, 0.0f, 0.0f);
        cube(rear_wheel, "tire31", "tire", 120, 49, 0.0f, 4.6293f, -0.9208f, 0.95f, 0.8f, 1.04f, 348.75f, 0.0f, 0.0f);
        cube(rear_wheel, "knob0", "tread", 48, 58, 0.0f, 5.175f, 0.5097f, 0.46f, 0.16f, 0.56f, 5.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob1", "tread", 51, 60, 0.3f, 4.9761f, 1.5095f, 0.3f, 0.16f, 0.56f, 16.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob2", "tread", 54, 60, -0.3f, 4.586f, 2.4513f, 0.3f, 0.16f, 0.56f, 28.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob3", "tread", 52, 58, 0.0f, 4.0197f, 3.2988f, 0.46f, 0.16f, 0.56f, 39.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob4", "tread", 57, 60, 0.3f, 3.2988f, 4.0197f, 0.3f, 0.16f, 0.56f, 50.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob5", "tread", 60, 60, -0.3f, 2.4513f, 4.586f, 0.3f, 0.16f, 0.56f, 61.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob6", "tread", 56, 58, 0.0f, 1.5095f, 4.9761f, 0.46f, 0.16f, 0.56f, 73.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob7", "tread", 63, 60, 0.3f, 0.5097f, 5.175f, 0.3f, 0.16f, 0.56f, 84.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob8", "tread", 66, 60, -0.3f, -0.5097f, 5.175f, 0.3f, 0.16f, 0.56f, 95.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob9", "tread", 60, 58, 0.0f, -1.5095f, 4.9761f, 0.46f, 0.16f, 0.56f, 106.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob10", "tread", 69, 60, 0.3f, -2.4513f, 4.586f, 0.3f, 0.16f, 0.56f, 118.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob11", "tread", 72, 60, -0.3f, -3.2988f, 4.0197f, 0.3f, 0.16f, 0.56f, 129.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob12", "tread", 64, 58, 0.0f, -4.0197f, 3.2988f, 0.46f, 0.16f, 0.56f, 140.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob13", "tread", 75, 60, 0.3f, -4.586f, 2.4513f, 0.3f, 0.16f, 0.56f, 151.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob14", "tread", 78, 60, -0.3f, -4.9761f, 1.5095f, 0.3f, 0.16f, 0.56f, 163.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob15", "tread", 68, 58, 0.0f, -5.175f, 0.5097f, 0.46f, 0.16f, 0.56f, 174.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob16", "tread", 81, 60, 0.3f, -5.175f, -0.5097f, 0.3f, 0.16f, 0.56f, 185.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob17", "tread", 84, 60, -0.3f, -4.9761f, -1.5095f, 0.3f, 0.16f, 0.56f, 196.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob18", "tread", 72, 58, 0.0f, -4.586f, -2.4513f, 0.46f, 0.16f, 0.56f, 208.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob19", "tread", 87, 60, 0.3f, -4.0197f, -3.2988f, 0.3f, 0.16f, 0.56f, 219.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob20", "tread", 90, 60, -0.3f, -3.2988f, -4.0197f, 0.3f, 0.16f, 0.56f, 230.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob21", "tread", 76, 58, 0.0f, -2.4513f, -4.586f, 0.46f, 0.16f, 0.56f, 241.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob22", "tread", 93, 60, 0.3f, -1.5095f, -4.9761f, 0.3f, 0.16f, 0.56f, 253.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob23", "tread", 96, 60, -0.3f, -0.5097f, -5.175f, 0.3f, 0.16f, 0.56f, 264.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob24", "tread", 80, 58, 0.0f, 0.5097f, -5.175f, 0.46f, 0.16f, 0.56f, 275.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob25", "tread", 99, 60, 0.3f, 1.5095f, -4.9761f, 0.3f, 0.16f, 0.56f, 286.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob26", "tread", 102, 60, -0.3f, 2.4513f, -4.586f, 0.3f, 0.16f, 0.56f, 298.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob27", "tread", 84, 58, 0.0f, 3.2988f, -4.0197f, 0.46f, 0.16f, 0.56f, 309.375f, 0.0f, 0.0f);
        cube(rear_wheel, "knob28", "tread", 105, 60, 0.3f, 4.0197f, -3.2988f, 0.3f, 0.16f, 0.56f, 320.625f, 0.0f, 0.0f);
        cube(rear_wheel, "knob29", "tread", 108, 60, -0.3f, 4.586f, -2.4513f, 0.3f, 0.16f, 0.56f, 331.875f, 0.0f, 0.0f);
        cube(rear_wheel, "knob30", "tread", 88, 58, 0.0f, 4.9761f, -1.5095f, 0.46f, 0.16f, 0.56f, 343.125f, 0.0f, 0.0f);
        cube(rear_wheel, "knob31", "tread", 111, 60, 0.3f, 5.175f, -0.5097f, 0.3f, 0.16f, 0.56f, 354.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim0", "rim", 0, 52, 0.0f, 4.0803f, 0.4019f, 0.78f, 0.45f, 0.88f, 5.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim1", "rim", 5, 52, 0.0f, 3.9235f, 1.1902f, 0.78f, 0.45f, 0.88f, 16.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim2", "rim", 10, 52, 0.0f, 3.6159f, 1.9327f, 0.78f, 0.45f, 0.88f, 28.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim3", "rim", 15, 52, 0.0f, 3.1693f, 2.601f, 0.78f, 0.45f, 0.88f, 39.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim4", "rim", 20, 52, 0.0f, 2.601f, 3.1693f, 0.78f, 0.45f, 0.88f, 50.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim5", "rim", 25, 52, 0.0f, 1.9327f, 3.6159f, 0.78f, 0.45f, 0.88f, 61.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim6", "rim", 30, 52, 0.0f, 1.1902f, 3.9235f, 0.78f, 0.45f, 0.88f, 73.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim7", "rim", 35, 52, 0.0f, 0.4019f, 4.0803f, 0.78f, 0.45f, 0.88f, 84.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim8", "rim", 40, 52, 0.0f, -0.4019f, 4.0803f, 0.78f, 0.45f, 0.88f, 95.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim9", "rim", 45, 52, 0.0f, -1.1902f, 3.9235f, 0.78f, 0.45f, 0.88f, 106.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim10", "rim", 50, 52, 0.0f, -1.9327f, 3.6159f, 0.78f, 0.45f, 0.88f, 118.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim11", "rim", 55, 52, 0.0f, -2.601f, 3.1693f, 0.78f, 0.45f, 0.88f, 129.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim12", "rim", 60, 52, 0.0f, -3.1693f, 2.601f, 0.78f, 0.45f, 0.88f, 140.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim13", "rim", 65, 52, 0.0f, -3.6159f, 1.9327f, 0.78f, 0.45f, 0.88f, 151.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim14", "rim", 70, 52, 0.0f, -3.9235f, 1.1902f, 0.78f, 0.45f, 0.88f, 163.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim15", "rim", 75, 52, 0.0f, -4.0803f, 0.4019f, 0.78f, 0.45f, 0.88f, 174.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim16", "rim", 80, 52, 0.0f, -4.0803f, -0.4019f, 0.78f, 0.45f, 0.88f, 185.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim17", "rim", 85, 52, 0.0f, -3.9235f, -1.1902f, 0.78f, 0.45f, 0.88f, 196.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim18", "rim", 90, 52, 0.0f, -3.6159f, -1.9327f, 0.78f, 0.45f, 0.88f, 208.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim19", "rim", 95, 52, 0.0f, -3.1693f, -2.601f, 0.78f, 0.45f, 0.88f, 219.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim20", "rim", 100, 52, 0.0f, -2.601f, -3.1693f, 0.78f, 0.45f, 0.88f, 230.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim21", "rim", 105, 52, 0.0f, -1.9327f, -3.6159f, 0.78f, 0.45f, 0.88f, 241.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim22", "rim", 110, 52, 0.0f, -1.1902f, -3.9235f, 0.78f, 0.45f, 0.88f, 253.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim23", "rim", 115, 52, 0.0f, -0.4019f, -4.0803f, 0.78f, 0.45f, 0.88f, 264.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim24", "rim", 120, 52, 0.0f, 0.4019f, -4.0803f, 0.78f, 0.45f, 0.88f, 275.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim25", "rim", 0, 55, 0.0f, 1.1902f, -3.9235f, 0.78f, 0.45f, 0.88f, 286.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim26", "rim", 5, 55, 0.0f, 1.9327f, -3.6159f, 0.78f, 0.45f, 0.88f, 298.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim27", "rim", 10, 55, 0.0f, 2.601f, -3.1693f, 0.78f, 0.45f, 0.88f, 309.375f, 0.0f, 0.0f);
        cube(rear_wheel, "rim28", "rim", 15, 55, 0.0f, 3.1693f, -2.601f, 0.78f, 0.45f, 0.88f, 320.625f, 0.0f, 0.0f);
        cube(rear_wheel, "rim29", "rim", 20, 55, 0.0f, 3.6159f, -1.9327f, 0.78f, 0.45f, 0.88f, 331.875f, 0.0f, 0.0f);
        cube(rear_wheel, "rim30", "rim", 25, 55, 0.0f, 3.9235f, -1.1902f, 0.78f, 0.45f, 0.88f, 343.125f, 0.0f, 0.0f);
        cube(rear_wheel, "rim31", "rim", 30, 55, 0.0f, 4.0803f, -0.4019f, 0.78f, 0.45f, 0.88f, 354.375f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke0", "spoke", 99, 21, 0.22f, 2.0438f, 0.9272f, 0.08f, 0.08f, 3.4318f, -57.2919f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke1", "spoke", 108, 21, -0.22f, 2.2431f, -0.0745f, 0.08f, 0.08f, 3.4318f, -79.7919f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke2", "spoke", 117, 21, 0.22f, 0.7896f, 2.1008f, 0.08f, 0.08f, 3.4318f, -12.2919f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke3", "spoke", 0, 26, -0.22f, 1.6388f, 1.5334f, 0.08f, 0.08f, 3.4318f, -55.2081f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke4", "spoke", 9, 26, 0.22f, -0.9272f, 2.0438f, 0.08f, 0.08f, 3.4318f, 32.7081f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke5", "spoke", 18, 26, -0.22f, 0.0745f, 2.2431f, 0.08f, 0.08f, 3.4318f, -10.2081f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke6", "spoke", 27, 26, 0.22f, -2.1008f, 0.7896f, 0.08f, 0.08f, 3.4318f, 77.7081f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke7", "spoke", 36, 26, -0.22f, -1.5334f, 1.6388f, 0.08f, 0.08f, 3.4318f, 34.7919f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke8", "spoke", 45, 26, 0.22f, -2.0438f, -0.9272f, 0.08f, 0.08f, 3.4318f, 57.2919f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke9", "spoke", 54, 26, -0.22f, -2.2431f, 0.0745f, 0.08f, 0.08f, 3.4318f, 79.7919f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke10", "spoke", 63, 26, 0.22f, -0.7896f, -2.1008f, 0.08f, 0.08f, 3.4318f, 12.2919f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke11", "spoke", 72, 26, -0.22f, -1.6388f, -1.5334f, 0.08f, 0.08f, 3.4318f, 55.2081f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke12", "spoke", 81, 26, 0.22f, 0.9272f, -2.0438f, 0.08f, 0.08f, 3.4318f, -32.7081f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke13", "spoke", 90, 26, -0.22f, -0.0745f, -2.2431f, 0.08f, 0.08f, 3.4318f, 10.2081f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke14", "spoke", 99, 26, 0.22f, 2.1008f, -0.7896f, 0.08f, 0.08f, 3.4318f, -77.7081f, 180.0f, 0.0f);
        cube(rear_wheel, "spoke15", "spoke", 108, 26, -0.22f, 1.5334f, -1.6388f, 0.08f, 0.08f, 3.4318f, -34.7919f, 180.0f, 0.0f);
        cube(rear_wheel, "hub_barrel", "hub", 114, 36, 0.0f, 0.0f, 0.0f, 1.8f, 0.55f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange", "hub", 61, 36, 0.62f, 0.0f, 0.0f, 0.12f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange_x", "hub", 65, 36, 0.62f, 0.0f, 0.0f, 0.11f, 1.1f, 1.1f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "axle_end", "silver", 114, 60, 1.0f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange1", "hub", 69, 36, -0.62f, 0.0f, 0.0f, 0.12f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange_x1", "hub", 73, 36, -0.62f, 0.0f, 0.0f, 0.11f, 1.1f, 1.1f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "axle_end1", "silver", 117, 60, -1.0f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor0", "rotor", 59, 55, 0.8f, 1.0f, 0.0f, 0.05f, 0.4f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor1", "rotor", 63, 55, 0.8f, 0.7071f, 0.7071f, 0.05f, 0.4f, 1.0f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor2", "rotor", 67, 55, 0.8f, 0.0f, 1.0f, 0.05f, 0.4f, 1.0f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor3", "rotor", 71, 55, 0.8f, -0.7071f, 0.7071f, 0.05f, 0.4f, 1.0f, 135.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor4", "rotor", 75, 55, 0.8f, -1.0f, 0.0f, 0.05f, 0.4f, 1.0f, 180.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor5", "rotor", 79, 55, 0.8f, -0.7071f, -0.7071f, 0.05f, 0.4f, 1.0f, 225.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor6", "rotor", 83, 55, 0.8f, 0.0f, -1.0f, 0.05f, 0.4f, 1.0f, 270.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor7", "rotor", 87, 55, 0.8f, 0.7071f, -0.7071f, 0.05f, 0.4f, 1.0f, 315.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm", "rotor", 83, 36, 0.8f, 0.0f, 0.0f, 0.056f, 1.9f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm1", "rotor", 85, 36, 0.8f, 0.0f, 0.0f, 0.056f, 1.9f, 0.22f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm2", "rotor", 87, 36, 0.8f, 0.0f, 0.0f, 0.056f, 1.9f, 0.22f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_bolts", "silver", 105, 55, 0.85f, 0.0f, 0.0f, 0.07f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog", "silver", 91, 55, -0.8f, 0.0f, 0.0f, 0.1f, 0.4982f, 1.2029f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog_b", "silver", 108, 55, -0.8f, 0.0f, 0.0f, 0.088f, 1.2029f, 0.4982f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog_c", "silver", 95, 55, -0.8f, 0.0f, 0.0f, 0.076f, 0.4982f, 1.2029f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog_d", "silver", 111, 55, -0.8f, 0.0f, 0.0f, 0.064f, 1.2029f, 0.4982f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog_lock", "black", 120, 60, -0.92f, 0.0f, 0.0f, 0.1f, 0.45f, 0.45f, 0.0f, 0.0f, 0.0f);
                cube(frame, "top_front__v", "frame", 0, 128, 0.0000f, -11.3215f, -2.7028f, 0.8000f, 0.9000f, 5.5724f, -39.5613f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__v", "frame", 14, 128, 0.0000f, -9.2723f, 1.5932f, 0.8000f, 0.9000f, 4.3310f, -7.2838f, 0.0000f, 0.0000f);
        cube(frame, "top_straight__s", "frame", 26, 128, 0.0000f, -10.5550f, -0.6800f, 0.8000f, 0.9000f, 9.2813f, -21.4231f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_ding_bell", "chrome", 57, 128, 3.0000f, -2.6000f, -1.0000f, 0.7000f, 0.5500f, 0.7000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_ding_mount", "black", 61, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_mini_bell", "chrome", 64, 128, 3.0000f, -2.6000f, -1.0000f, 0.5000f, 0.5500f, 0.5000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_mini_mount", "black", 67, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_classic_bell", "chrome", 70, 128, 3.0000f, -2.6000f, -1.0000f, 0.9000f, 0.5500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_classic_mount", "black", 75, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_horn_body", "horn", 78, 128, 3.0000f, -2.6000f, -1.6000f, 0.8000f, 0.8000f, 1.6000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_horn_mouth", "chrome", 84, 128, 3.0000f, -2.6000f, -2.5000f, 1.2000f, 1.2000f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_body", "duck", 88, 128, 3.0000f, -2.9000f, -1.0000f, 1.0000f, 0.8000f, 1.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_head", "duck", 94, 128, 3.0000f, -3.6000f, -1.5000f, 0.8000f, 0.8000f, 0.8000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_beak", "beak", 99, 128, 3.0000f, -3.4500f, -2.0000f, 0.5500f, 0.2000f, 0.4500f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_eyer", "eye", 102, 128, 2.5900f, -3.7000f, -1.6500f, 0.0500f, 0.1200f, 0.1200f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_eyel", "eye", 104, 128, 3.4100f, -3.7000f, -1.6500f, 0.0500f, 0.1200f, 0.1200f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_flight_body", "lightbody", 106, 128, 0.0000f, -2.3000f, -1.6000f, 0.8500f, 0.7000f, 1.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_flight_lens", "lens", 112, 128, 0.0000f, -2.3000f, -2.3200f, 0.6500f, 0.5000f, 0.0400f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "acc_rlight_body", "lightbody", 115, 128, 0.0000f, -10.0000f, 3.0000f, 0.5500f, 0.7000f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "acc_rlight_lens", "lens", 119, 128, 0.0000f, -10.0000f, 3.3500f, 0.4000f, 0.5000f, 0.0400f, 0.0000f, 0.0000f, 0.0000f);
        return LayerDefinition.create(mesh, 128, 256);
    }

    /** Bone: pivot (x,y,z) relative to the parent, rest rotation in degrees. */
    private static PartDefinition bone(PartDefinition parent, String name, float x, float y, float z,
                                       float rxDeg, float ryDeg, float rzDeg) {
        PartDefinition child = parent.addOrReplaceChild(name, CubeListBuilder.create(),
                PartPose.offsetAndRotation(x, y, z, rad(rxDeg), rad(ryDeg), rad(rzDeg)));
        TABLE.bone(parent, child, name);
        return child;
    }

    /**
     * Box centred at (cx,cy,cz) in the bone's local space with size (sx,sy,sz) and rotation in degrees
     * (vanilla Z-Y-X order). {@code mat} is the texture material tag (see tools/gen_hardtail_texture.py).
     */
    private static void cube(PartDefinition bone, String name, String mat, int u, int v,
                             float cx, float cy, float cz, float sx, float sy, float sz,
                             float rxDeg, float ryDeg, float rzDeg) {
        TABLE.cube(bone, name, mat, sx, sy, sz);
        bone.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                        .addBox(-sx / 2f, -sy / 2f, -sz / 2f, sx, sy, sz),
                PartPose.offsetAndRotation(cx, cy, cz, rad(rxDeg), rad(ryDeg), rad(rzDeg)));
    }

    private static float rad(float deg) {
        return deg * ((float) Math.PI / 180f);
    }
}
