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
 * Full-suspension 29" enduro / downhill mountain bike, built from a vanilla {@link ModelPart}
 * hierarchy (no GeckoLib) so every moving joint can be posed procedurally each frame.
 *
 * <p>Scale: 1 block = 1 m = 16 model units. Entity-model convention: model-space +Y points DOWN
 * (the renderer applies {@code scale(-1,-1,1)}), forward is -Z, +X is the rider's LEFT. The model
 * origin is on the ground midway between the two wheel contact patches.
 *
 * <pre>
 * root
 *  |- frame                 static front triangle, seatpost, saddle, shock mounts
 *  |   |- shock_body        pivot = frame-side shock eye, aims at the swingarm-side eye
 *  |   |   `- shock_spring  coil, z-scaled with shock length
 *  |   |- steer_axis        pivot on the steering axis (head tube centre), xRot = -head tilt (fixed)
 *  |   |   `- steer         yRot = steering angle (rotates about the tilted steering axis)
 *  |   |       |- fork_upper   crown, stanchions, stem, handlebar, grips, levers
 *  |   |       `- fork_lower   lowers + arch; local y slides along the steering axis (fork travel)
 *  |   |           `- front_wheel   pivot = front axle, xRot = spin
 *  |   |- cranks            pivot = bottom bracket, xRot = crank angle (arms, chainring)
 *  |   |   |- pedal_right / pedal_left   pivot = pedal spindle, counter-rotated to stay level
 *  |   |- chain_top / chain_bottom       thin boxes re-aimed between chainring and cassette
 *  |- swingarm              pivot = main pivot, xRot = suspension angle (+ = rear axle goes up)
 *      |- shock_shaft       pivot = swingarm-side shock eye, aims at the frame-side eye
 *      `- rear_wheel        pivot = rear axle, xRot = spin (cassette included)
 * </pre>
 *
 * <p>All box geometry below is a literal table (one {@code cube(...)} line per box, every box has its
 * own UV rectangle in the {@code 128}x{@code 512} texture). {@code tools/gen_enduro_texture.py}
 * and {@code tools/preview_enduro.py} parse this table, so keep the call format intact.
 * Material tags also drive per-component tints and visibility through PartTable.
 * After editing any box (size, position, texOffs) run {@code python tools/gen_enduro_texture.py}: it
 * re-validates that the UV rectangles do not overlap and repaints the texture.
 */
public class EnduroBikeModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "enduro_bike"), "main");

    // ------------------------------------------------------------------ geometry constants (metres)
    public static final float WHEEL_RADIUS_M = 0.375f;
    public static final float WHEELBASE_M = 1.26f;
    public static final float BB_HEIGHT_M = 0.35f;
    public static final float FORK_TRAVEL_M = 0.17f;
    public static final float REAR_TRAVEL_M = 0.16f;

    /** Rider anchor points: metres, Y UP, -Z forward, +X = rider's left, origin = ground between the wheels.
     *  Grips are at steering angle 0. */
    public static final Vector3f GRIP_LEFT = new Vector3f(0.3312f, 1.1138f, -0.2718f);
    public static final Vector3f GRIP_RIGHT = new Vector3f(-0.3312f, 1.1138f, -0.2718f);
    public static final Vector3f SADDLE_TOP = new Vector3f(0.0f, 0.9719f, 0.3688f);
    /** Bottom bracket / crank axis. */
    public static final Vector3f PEDAL_AXIS = new Vector3f(0.0f, 0.35f, 0.19f);

    // ------------------------------------------------------------------ pose constants (model units, Y down)
    private static final float PX = 16f;
    /** Main pivot / rear axle / shock eyes / bottom bracket (model space, rest pose). */
    private static final float PIV_Y = -8.3f, PIV_Z = 4.2f;
    private static final float AXLE_Y = -6.0f, AXLE_Z = 10.08f;
    private static final float SHOCK_FRAME_Y = -12.0f, SHOCK_FRAME_Z = -0.6f;
    private static final float SHOCK_ARM_Y = -11.4f, SHOCK_ARM_Z = 3.1f;
    private static final float BB_Y = -5.6f, BB_Z = 3.04f;
    private static final float SHOCK_LEN0 = 3.7483f;      // eye-to-eye length, fully extended
    private static final float SPRING_PAD = 0.72f;     // spring inset from each eye
    private static final float CHAIN_BASE = 10f;
    private static final float CHAINRING_R = 1.0f, COG_R = 0.82f;
    /** Swingarm geometry: axle sits SW_LEN away from the pivot, SW_PHI below the horizontal. */
    private static final float SW_LEN = (float) Math.hypot(AXLE_Z - PIV_Z, AXLE_Y - PIV_Y);
    private static final float SW_PHI = (float) Math.atan2(AXLE_Y - PIV_Y, AXLE_Z - PIV_Z);

    private static final PartTable TABLE = new PartTable();
    private final PartTable.Node customRoot;
    private final ModelPart root;
    private final ModelPart swingarm, shockBody, shockShaft, shockSpring;
    private final ModelPart steer, forkLower, frontWheel, rearWheel;
    private final ModelPart leverLeft, leverRight;
    private final ModelPart cranks, pedalLeft, pedalRight, chainTop, chainBottom;
    private final ModelPart catalogUpper, catalogLower, catalogStays;
    private float rearRise;

    public EnduroBikeModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.customRoot = TABLE.bind(root);
        ModelPart frame = root.getChild("frame");
        this.swingarm = root.getChild("swingarm");
        this.shockBody = frame.getChild("shock_body");
        this.shockSpring = this.shockBody.getChild("shock_spring");
        this.shockShaft = this.swingarm.getChild("shock_shaft");
        this.steer = frame.getChild("steer_axis").getChild("steer");
        this.leverLeft = steer.getChild("fork_upper").getChild("lever_l");
        this.leverRight = steer.getChild("fork_upper").getChild("lever_r");
        this.forkLower = this.steer.getChild("fork_lower");
        this.frontWheel = this.forkLower.getChild("front_wheel");
        this.rearWheel = this.swingarm.getChild("rear_wheel");
        this.cranks = frame.getChild("cranks");
        this.pedalLeft = this.cranks.getChild("pedal_left");
        this.pedalRight = this.cranks.getChild("pedal_right");
        this.chainTop = frame.getChild("chain_top");
        this.chainBottom = frame.getChild("chain_bottom");
        this.catalogUpper = frame.getChild("catalog_upper");
        this.catalogLower = frame.getChild("catalog_lower");
        this.catalogStays = frame.getChild("catalog_stays");
    }

    // ------------------------------------------------------------------ pose
    /**
     * Pose every moving bone. All lengths in metres, all angles in radians.
     *
     * @param steerRad       steering angle about the steering axis; POSITIVE = steer to the rider's RIGHT
     * @param forkTravelM    fork compression 0..0.17 (lowers slide up the steering axis)
     * @param rearTravelM    VERTICAL rear-axle travel 0..0.16 (swingarm rotates to raise the axle by this much)
     * @param frontWheelRad  front wheel spin; POSITIVE = rolling forward (top of the tyre moves toward -Z)
     * @param rearWheelRad   rear wheel spin, same sign convention
     * @param crankRad       crank angle; 0 = right crank pointing forward (horizontal), POSITIVE = pedalling forward
     */
    public void setupPose(float steerRad, float forkTravelM, float rearTravelM,
                          float frontWheelRad, float rearWheelRad, float crankRad) {
        float fork = Mth.clamp(forkTravelM, 0f, FORK_TRAVEL_M) * PX;
        float rise = Mth.clamp(rearTravelM, 0f, REAR_TRAVEL_M) * PX;
        rearRise = rise;

        // --- front end: yaw about the (tilted) steering axis, lowers slide up that same axis
        this.steer.yRot = steerRad;
        this.forkLower.y = -fork;                 // local -Y of steer_axis = up along the steering axis
        this.frontWheel.xRot = frontWheelRad;

        // --- swingarm: exact angle that lifts the rear axle by `rise` (pure rotation about the pivot)
        float s = Mth.clamp(rise / SW_LEN - Mth.sin(SW_PHI), -1f, 1f);
        float alpha = SW_PHI + (float) Math.asin(s);
        this.swingarm.xRot = alpha;               // + = rear axle moves up
        this.rearWheel.xRot = rearWheelRad;

        // --- shock: swingarm-side eye in frame space, then aim both halves at each other
        float cs = Mth.cos(alpha), sn = Mth.sin(alpha);
        float ry = SHOCK_ARM_Y - PIV_Y, rz = SHOCK_ARM_Z - PIV_Z;
        float eyeY = PIV_Y + ry * cs - rz * sn;
        float eyeZ = PIV_Z + ry * sn + rz * cs;
        float dy = eyeY - SHOCK_FRAME_Y, dz = eyeZ - SHOCK_FRAME_Z;
        float len = Mth.sqrt(dy * dy + dz * dz);
        this.shockBody.xRot = (float) Math.atan2(-dy, dz);
        this.shockShaft.xRot = (float) Math.atan2(dy, -dz) - alpha;   // swingarm already rotated by alpha
        this.shockSpring.zScale = Math.max(0.2f, (len - 2f * SPRING_PAD) / (SHOCK_LEN0 - 2f * SPRING_PAD));

        // --- cranks and level pedals
        this.cranks.xRot = crankRad;
        this.pedalLeft.xRot = -crankRad;
        this.pedalRight.xRot = -crankRad;

        // --- chain: two boxes between chainring and rear cog (rear axle in frame space)
        float ay = PIV_Y + (AXLE_Y - PIV_Y) * cs - (AXLE_Z - PIV_Z) * sn;
        float az = PIV_Z + (AXLE_Y - PIV_Y) * sn + (AXLE_Z - PIV_Z) * cs;
        aimChain(this.chainTop, BB_Y - CHAINRING_R, BB_Z, ay - COG_R, az);
        aimChain(this.chainBottom, BB_Y + CHAINRING_R, BB_Z, ay + COG_R, az);
    }

    public void setupBrake(float pressure) {
        leverLeft.loadPose(leverLeft.getInitialPose()); leverRight.loadPose(leverRight.getInitialPose());
        leverLeft.yRot -= pressure * .28f; leverRight.yRot += pressure * .28f;
    }

    private static void aimChain(ModelPart chain, float y0, float z0, float y1, float z1) {
        float dy = y1 - y0, dz = z1 - z0;
        chain.y = (y0 + y1) * 0.5f;
        chain.z = (z0 + z1) * 0.5f;
        chain.xRot = (float) Math.atan2(-dy, dz);
        chain.zScale = Mth.sqrt(dy * dy + dz * dz) / CHAIN_BASE;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
        this.root.render(pose, vc, light, overlay, color);
    }


    public void renderCustomized(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                 int light, int overlay, com.descentmtb.custom.BikeBuild build) {
        var texture = com.descentmtb.client.custom.BikeTextures.base(com.descentmtb.entity.BikeType.ENDURO);
        var finish = com.descentmtb.client.custom.BikeTextures.finish(com.descentmtb.entity.BikeType.ENDURO, build.finish());
        EnduroLinkage layout = FrameLayouts.forShape(build.shape());
        if (layout == null) { customRoot.render(pose, buffers, light, overlay, build, texture, finish); return; }
        ModelPart[] changed = {swingarm, catalogUpper, catalogLower, catalogStays, rearWheel,
                shockBody, shockShaft, shockSpring, chainTop, chainBottom};
        PartPose[] poses = new PartPose[changed.length];
        float[] scales = new float[changed.length];
        for (int i = 0; i < changed.length; i++) { poses[i] = changed[i].storePose(); scales[i] = changed[i].zScale; }
        try {
            EnduroLinkage.Pose linkage = layout.atTravel(rearRise);
            var zero = new EnduroLinkage.Point(0, 0);
            var pivot = new EnduroLinkage.Point(PIV_Y, PIV_Z);
            mapBone(catalogUpper, layout.d, layout.d, linkage.upperAngle(), zero);
            mapBone(catalogLower, layout.a, layout.a, linkage.lowerAngle(), zero);
            mapBone(catalogStays, layout.b, linkage.b(), linkage.rearAngle(), zero);
            if (layout.dual) mapBone(swingarm, layout.b, linkage.b(), linkage.rearAngle(), pivot);
            else mapBone(swingarm, layout.a, layout.a, linkage.lowerAngle(), pivot);
            var armOrigin = new EnduroLinkage.Point(swingarm.y, swingarm.z);
            var localAxle = linkage.axle().minus(armOrigin).rotate(-swingarm.xRot);
            rearWheel.y = localAxle.y(); rearWheel.z = localAxle.z();
            // Keep wheel spin independent of changes in the member carrying its axle.
            rearWheel.xRot += poses[0].xRot - swingarm.xRot;
            var localEye = linkage.shock().minus(armOrigin).rotate(-swingarm.xRot);
            float dy = linkage.shock().y() - layout.fixedEye.y();
            float dz = linkage.shock().z() - layout.fixedEye.z();
            float length = (float) Math.hypot(dy, dz);
            shockBody.y = layout.fixedEye.y(); shockBody.z = layout.fixedEye.z();
            shockBody.xRot = (float) Math.atan2(-dy, dz);
            shockShaft.y = localEye.y(); shockShaft.z = localEye.z();
            shockShaft.xRot = (float) Math.atan2(dy, -dz) - swingarm.xRot;
            // Constant body/shaft dimensions per frame; compression telescopes the shaft.
            float nominal = layout.restShockLength / SHOCK_LEN0;
            shockBody.zScale = shockShaft.zScale = nominal;
            shockSpring.zScale = Math.max(.2f, (length / nominal - 2 * SPRING_PAD) / (SHOCK_LEN0 - 2 * SPRING_PAD));
            aimChain(chainTop, BB_Y - CHAINRING_R, BB_Z, linkage.axle().y() - COG_R, linkage.axle().z());
            aimChain(chainBottom, BB_Y + CHAINRING_R, BB_Z, linkage.axle().y() + COG_R, linkage.axle().z());
            customRoot.render(pose, buffers, light, overlay, build, texture, finish);
        } finally {
            for (int i = 0; i < changed.length; i++) { changed[i].loadPose(poses[i]); changed[i].zScale = scales[i]; }
        }
    }

    private static void mapBone(ModelPart bone, EnduroLinkage.Point rest, EnduroLinkage.Point moved,
                                float angle, EnduroLinkage.Point origin) {
        var offset = rest.minus(origin).rotate(angle);
        bone.y = moved.y() - offset.y(); bone.z = moved.z() - offset.z(); bone.xRot = angle;
    }

    public ModelPart root() {
        return this.root;
    }

    // ------------------------------------------------------------------ geometry table
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        TABLE.start(root);
        PartDefinition frame = bone(root, "frame", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(frame, "head_tube", "frame", 76, 24, 0.0f, -15.5319f, -4.5454f, 1.35f, 1.35f, 1.7f, 63.5f, 0.0f, 0.0f);
        cube(frame, "headset_lo", "black", 36, 56, 0.0f, -14.7712f, -4.9246f, 1.55f, 1.55f, 0.3f, 63.5f, 0.0f, 0.0f);
        cube(frame, "headset_hi", "accent", 41, 56, 0.0f, -16.2926f, -4.1661f, 1.55f, 1.55f, 0.3f, 63.5f, 0.0f, 0.0f);
        cube(frame, "down_tube__chl", "frame", 0, 0, 0.0f, -10.3199f, -0.8754f, 1.05f, 1.25f, 12.965f, -50.3224f, 0.0f, 0.0f);
        cube(frame, "top_tube__ch", "frame", 30, 0, 0.0f, -14.2717f, 0.1775f, 0.8f, 0.95f, 10.0603f, -19.0112f, 0.0f, 0.0f);
        cube(frame, "seat_tube", "frame", 97, 0, 0.0f, -9.4895f, 3.9738f, 0.82f, 0.82f, 8.4f, 76.5f, 0.0f, 0.0f);
        cube(frame, "seat_collar", "black", 66, 56, 0.0f, -13.2817f, 4.8842f, 1.0f, 1.0f, 0.4f, 76.5f, 0.0f, 0.0f);
        cube(frame, "seatpost", "black", 112, 150, 0.0f, -13.8700f, 5.0254f, 0.52f, 0.52f, 1.81f, 76.5f, 0.0f, 0.0f);
        cube(frame, "dropper_lower", "silver", 70, 56, 0.0f, -13.8165f, 5.0126f, 0.58f, 0.58f, 0.9f, 76.5f, 0.0f, 0.0f);
        cube(frame, "saddle_nose", "saddle", 64, 46, 0.0f, -15.3f, 4.45f, 0.95f, 0.4f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_mid", "saddle", 71, 46, 0.0f, -15.28f, 5.9f, 1.5f, 0.44f, 1.4f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rear", "saddle", 12, 30, 0.0f, -15.3f, 7.4f, 2.0f, 0.55f, 1.6f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_under", "saddle", 30, 24, 0.0f, -14.98f, 6.1f, 1.2f, 0.18f, 3.1f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rail_l", "silver", 84, 24, 0.55f, -14.86f, 6.25f, 0.1f, 0.12f, 3.3f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_rail_r", "silver", 92, 24, -0.55f, -14.86f, 6.25f, 0.1f, 0.12f, 3.3f, 0.0f, 0.0f, 0.0f);
        cube(frame, "saddle_clamp", "black", 51, 56, 0.0f, -14.78f, 5.2367f, 0.9f, 0.3f, 0.85f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_shell", "frame", 37, 30, 0.0f, -5.6f, 3.04f, 1.5f, 1.1f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_cup_l", "silver", 40, 59, 0.8f, -5.6f, 3.04f, 0.2f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(frame, "bb_cup_r", "silver", 43, 59, -0.8f, -5.6f, 3.04f, 0.2f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(frame, "pivot_boss__cl", "frame", 56, 46, 0.0f, -8.3f, 4.2f, 2.3f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(frame, "pivot_gusset__cl", "frame", 76, 42, 0.0f, -8.35f, 3.8f, 0.8f, 1.2f, 1.2f, 0.0f, 0.0f, 0.0f);
        cube(frame, "pivot_axle__chl", "silver", 18, 62, 0.0f, -8.3f, 4.2f, 3.5f, 0.34f, 0.34f, 0.0f, 0.0f, 0.0f);
        cube(frame, "shock_mount_l__chl", "frame", 12, 46, 0.5f, -11.3189f, -0.6f, 0.2f, 2.0621f, 0.6f, 0.0f, 0.0f, 0.0f);
        cube(frame, "shock_mount_r__chl", "frame", 15, 46, -0.5f, -11.3189f, -0.6f, 0.2f, 2.0621f, 0.6f, 0.0f, 0.0f, 0.0f);
        cube(frame, "shock_mount_bolt__chl", "silver", 41, 62, 0.0f, -12.0f, -0.6f, 1.3f, 0.28f, 0.28f, 0.0f, 0.0f, 0.0f);
        cube(frame, "shock_mount_web__chl", "frame", 56, 56, 0.0f, -10.4379f, -0.6f, 1.0f, 0.5f, 0.6f, 0.0f, 0.0f, 0.0f);
        PartDefinition shock_body = bone(frame, "shock_body", 0.0f, -12.0f, -0.6f, 0.0f, 0.0f, 0.0f);
        cube(shock_body, "eye", "black", 74, 56, 0.0f, 0.0f, 0.0f, 0.82f, 0.55f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(shock_body, "body", "shockbody", 44, 30, 0.0f, 0.0f, 1.25f, 0.62f, 0.62f, 2.3f, 0.0f, 0.0f, 0.0f);
        cube(shock_body, "body_cap", "silver", 120, 65, 0.0f, 0.0f, 2.5f, 0.7f, 0.7f, 0.14f, 0.0f, 0.0f, 0.0f);
        cube(shock_body, "reservoir", "shockres", 78, 56, 0.0f, 0.55f, 1.0f, 0.45f, 0.4f, 0.85f, 0.0f, 0.0f, 0.0f);
        cube(shock_body, "perch", "black", 82, 56, 0.0f, 0.0f, 0.62f, 1.2f, 1.2f, 0.1f, 0.0f, 0.0f, 0.0f);
        PartDefinition steer_axis = bone(frame, "steer_axis", 0.0f, -15.5319f, -4.5454f, -26.5f, 0.0f, 0.0f);
        PartDefinition steer = bone(steer_axis, "steer", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition fork_upper = bone(steer, "fork_upper", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        fork_upper.addOrReplaceChild("mini_pump", CubeListBuilder.create().texOffs(70, 16)
                .addBox(1.9f, 2.2f, 0.1f, 0.35f, 4.3f, 0.35f), PartPose.ZERO);
        cube(fork_upper, "crown", "black", 0, 30, 0.0f, 1.375f, 0.05f, 3.75f, 0.85f, 1.7f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "steerer", "silver", 116, 24, 0.0f, -0.45f, 0.0f, 0.62f, 2.9f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "top_cap", "black", 78, 46, 0.0f, -1.05f, 0.0f, 1.45f, 0.4f, 1.45f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_clamp", "black", 99, 46, 0.0f, -1.55f, 0.0f, 1.15f, 0.7f, 1.15f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_arm", "black", 105, 46, 0.0f, -1.6f, -0.75f, 1.1f, 0.56f, 0.95f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stanchion_l", "stanchion", 70, 16, 1.3f, 4.1f, 0.0f, 0.6f, 4.6f, 0.6f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_bolt_l", "silver", 54, 69, 0.45f, -1.9f, 0.0f, 0.22f, 0.12f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stanchion_r", "stanchion", 74, 16, -1.3f, 4.1f, 0.0f, 0.6f, 4.6f, 0.6f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "stem_bolt_r", "silver", 56, 69, -0.45f, -1.9f, 0.0f, 0.22f, 0.12f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "bar_clamp", "bars", 61, 56, 0.0f, -1.6f, -1.25f, 1.2f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "bar_c_l", "bars", 28, 42, 0.75f, -1.6f, -1.25f, 0.5f, 0.5f, 1.6f, 0.0f, 90.0f, 0.0f);
        cube(fork_upper, "bar_m_l", "bars", 40, 24, 2.95f, -1.8046f, -1.0954f, 0.5f, 0.5f, 3.045f, 7.9869f, 83.9137f, 0.0f);
        cube(fork_upper, "grip_l", "grip", 51, 30, 5.3f, -2.1362f, -0.8448f, 0.66f, 0.66f, 2.2341f, 7.9869f, 83.9137f, 0.0f);
        cube(fork_upper, "lever_clamp_l", "brake", 86, 56, 3.7f, -1.8604f, -0.9154f, 0.5f, 0.5f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "lever_l", "brake", 34, 42, 3.925f, -1.0059f, -1.9891f, 0.16f, 0.34f, 2.2644f, -39.8684f, 164.9938f, 0.0f);
        cube(fork_upper, "bar_c_r", "bars", 40, 42, -0.75f, -1.6f, -1.25f, 0.5f, 0.5f, 1.6f, 0.0f, -90.0f, 0.0f);
        cube(fork_upper, "bar_m_r", "bars", 49, 24, -2.95f, -1.8046f, -1.0954f, 0.5f, 0.5f, 3.045f, 7.9869f, -83.9137f, 0.0f);
        cube(fork_upper, "grip_r", "grip", 58, 30, -5.3f, -2.1362f, -0.8448f, 0.66f, 0.66f, 2.2341f, 7.9869f, -83.9137f, 0.0f);
        cube(fork_upper, "lever_clamp_r", "brake", 90, 56, -3.7f, -1.8604f, -0.9154f, 0.5f, 0.5f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(fork_upper, "lever_r", "brake", 46, 42, -3.925f, -1.0059f, -1.9891f, 0.16f, 0.34f, 2.2644f, -39.8684f, -164.9938f, 0.0f);
        PartDefinition fork_lower = bone(steer, "fork_lower", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_up_l", "lower", 58, 16, 1.3f, 6.76f, -0.15f, 0.95f, 4.28f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_lo_l", "lower", 98, 16, 1.3f, 10.05f, -0.5f, 0.95f, 2.9f, 1.2f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "axle_cap_l", "silver", 94, 56, 1.55f, 11.0f, -0.7f, 0.3f, 0.75f, 0.75f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "seal_l", "stanchion", 46, 62, 1.3f, 4.7f, 0.0f, 0.7f, 0.14f, 0.7f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_up_r", "lower", 64, 16, -1.3f, 6.76f, -0.15f, 0.95f, 4.28f, 1.1f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "leg_lo_r", "lower", 104, 16, -1.3f, 10.05f, -0.5f, 0.95f, 2.9f, 1.2f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "axle_cap_r", "silver", 98, 56, -1.55f, 11.0f, -0.7f, 0.3f, 0.75f, 0.75f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "seal_r", "stanchion", 50, 62, -1.3f, 4.7f, 0.0f, 0.7f, 0.14f, 0.7f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "arch", "lower", 36, 46, 0.0f, 4.77f, -0.35f, 3.65f, 0.3f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "caliper_f", "brake", 81, 42, 1.3f, 9.1f, 0.0f, 0.85f, 1.25f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(fork_lower, "caliper_f_bolt", "silver", 58, 69, 1.3f, 8.6f, 0.55f, 0.3f, 0.14f, 0.14f, 0.0f, 0.0f, 0.0f);
        PartDefinition front_wheel = bone(fork_lower, "front_wheel", 0.0f, 11.0f, -0.7f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire", "tire", 65, 30, 0.0f, 5.43f, 0.0f, 1.0f, 0.9f, 1.6102f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire1", "tire", 72, 30, 0.0f, 5.245f, 1.4054f, 1.0f, 0.9f, 1.6102f, 15.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire2", "tire", 79, 30, 0.0f, 4.7025f, 2.715f, 1.0f, 0.9f, 1.6102f, 30.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire3", "tire", 86, 30, 0.0f, 3.8396f, 3.8396f, 1.0f, 0.9f, 1.6102f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire4", "tire", 93, 30, 0.0f, 2.715f, 4.7025f, 1.0f, 0.9f, 1.6102f, 60.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire5", "tire", 100, 30, 0.0f, 1.4054f, 5.245f, 1.0f, 0.9f, 1.6102f, 75.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire6", "tire", 107, 30, 0.0f, 0.0f, 5.43f, 1.0f, 0.9f, 1.6102f, 90.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire7", "tire", 114, 30, 0.0f, -1.4054f, 5.245f, 1.0f, 0.9f, 1.6102f, 105.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire8", "tire", 121, 30, 0.0f, -2.715f, 4.7025f, 1.0f, 0.9f, 1.6102f, 120.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire9", "tire", 0, 34, 0.0f, -3.8396f, 3.8396f, 1.0f, 0.9f, 1.6102f, 135.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire10", "tire", 7, 34, 0.0f, -4.7025f, 2.715f, 1.0f, 0.9f, 1.6102f, 150.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire11", "tire", 14, 34, 0.0f, -5.245f, 1.4054f, 1.0f, 0.9f, 1.6102f, 165.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire12", "tire", 21, 34, 0.0f, -5.43f, 0.0f, 1.0f, 0.9f, 1.6102f, 180.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire13", "tire", 28, 34, 0.0f, -5.245f, -1.4054f, 1.0f, 0.9f, 1.6102f, 195.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire14", "tire", 35, 34, 0.0f, -4.7025f, -2.715f, 1.0f, 0.9f, 1.6102f, 210.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire15", "tire", 42, 34, 0.0f, -3.8396f, -3.8396f, 1.0f, 0.9f, 1.6102f, 225.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire16", "tire", 49, 34, 0.0f, -2.715f, -4.7025f, 1.0f, 0.9f, 1.6102f, 240.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire17", "tire", 56, 34, 0.0f, -1.4054f, -5.245f, 1.0f, 0.9f, 1.6102f, 255.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire18", "tire", 63, 34, 0.0f, 0.0f, -5.43f, 1.0f, 0.9f, 1.6102f, 270.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire19", "tire", 70, 34, 0.0f, 1.4054f, -5.245f, 1.0f, 0.9f, 1.6102f, 285.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire20", "tire", 77, 34, 0.0f, 2.715f, -4.7025f, 1.0f, 0.9f, 1.6102f, 300.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire21", "tire", 84, 34, 0.0f, 3.8396f, -3.8396f, 1.0f, 0.9f, 1.6102f, 315.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire22", "tire", 91, 34, 0.0f, 4.7025f, -2.715f, 1.0f, 0.9f, 1.6102f, 330.0f, 0.0f, 0.0f);
        cube(front_wheel, "tire23", "tire", 98, 34, 0.0f, 5.245f, -1.4054f, 1.0f, 0.9f, 1.6102f, 345.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob", "tread", 54, 62, 0.0f, 5.8495f, 0.7701f, 0.5f, 0.2f, 0.62f, 7.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob1", "tread", 123, 65, 0.34f, 5.9f, 0.0f, 0.3f, 0.2f, 0.7f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob2", "tread", 58, 62, 0.0f, 5.4509f, 2.2578f, 0.5f, 0.2f, 0.62f, 22.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob3", "tread", 0, 67, -0.34f, 5.699f, 1.527f, 0.3f, 0.2f, 0.7f, 15.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob4", "tread", 62, 62, 0.0f, 4.6808f, 3.5917f, 0.5f, 0.2f, 0.62f, 37.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob5", "tread", 3, 67, 0.34f, 5.1095f, 2.95f, 0.3f, 0.2f, 0.7f, 30.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob6", "tread", 66, 62, 0.0f, 3.5917f, 4.6808f, 0.5f, 0.2f, 0.62f, 52.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob7", "tread", 6, 67, -0.34f, 4.1719f, 4.1719f, 0.3f, 0.2f, 0.7f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob8", "tread", 70, 62, 0.0f, 2.2578f, 5.4509f, 0.5f, 0.2f, 0.62f, 67.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob9", "tread", 9, 67, 0.34f, 2.95f, 5.1095f, 0.3f, 0.2f, 0.7f, 60.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob10", "tread", 74, 62, 0.0f, 0.7701f, 5.8495f, 0.5f, 0.2f, 0.62f, 82.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob11", "tread", 12, 67, -0.34f, 1.527f, 5.699f, 0.3f, 0.2f, 0.7f, 75.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob12", "tread", 78, 62, 0.0f, -0.7701f, 5.8495f, 0.5f, 0.2f, 0.62f, 97.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob13", "tread", 15, 67, 0.34f, 0.0f, 5.9f, 0.3f, 0.2f, 0.7f, 90.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob14", "tread", 82, 62, 0.0f, -2.2578f, 5.4509f, 0.5f, 0.2f, 0.62f, 112.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob15", "tread", 18, 67, -0.34f, -1.527f, 5.699f, 0.3f, 0.2f, 0.7f, 105.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob16", "tread", 86, 62, 0.0f, -3.5917f, 4.6808f, 0.5f, 0.2f, 0.62f, 127.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob17", "tread", 21, 67, 0.34f, -2.95f, 5.1095f, 0.3f, 0.2f, 0.7f, 120.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob18", "tread", 90, 62, 0.0f, -4.6808f, 3.5917f, 0.5f, 0.2f, 0.62f, 142.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob19", "tread", 24, 67, -0.34f, -4.1719f, 4.1719f, 0.3f, 0.2f, 0.7f, 135.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob20", "tread", 94, 62, 0.0f, -5.4509f, 2.2578f, 0.5f, 0.2f, 0.62f, 157.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob21", "tread", 27, 67, 0.34f, -5.1095f, 2.95f, 0.3f, 0.2f, 0.7f, 150.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob22", "tread", 98, 62, 0.0f, -5.8495f, 0.7701f, 0.5f, 0.2f, 0.62f, 172.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob23", "tread", 30, 67, -0.34f, -5.699f, 1.527f, 0.3f, 0.2f, 0.7f, 165.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob24", "tread", 102, 62, 0.0f, -5.8495f, -0.7701f, 0.5f, 0.2f, 0.62f, 187.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob25", "tread", 33, 67, 0.34f, -5.9f, 0.0f, 0.3f, 0.2f, 0.7f, 180.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob26", "tread", 106, 62, 0.0f, -5.4509f, -2.2578f, 0.5f, 0.2f, 0.62f, 202.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob27", "tread", 36, 67, -0.34f, -5.699f, -1.527f, 0.3f, 0.2f, 0.7f, 195.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob28", "tread", 110, 62, 0.0f, -4.6808f, -3.5917f, 0.5f, 0.2f, 0.62f, 217.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob29", "tread", 39, 67, 0.34f, -5.1095f, -2.95f, 0.3f, 0.2f, 0.7f, 210.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob30", "tread", 114, 62, 0.0f, -3.5917f, -4.6808f, 0.5f, 0.2f, 0.62f, 232.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob31", "tread", 42, 67, -0.34f, -4.1719f, -4.1719f, 0.3f, 0.2f, 0.7f, 225.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob32", "tread", 118, 62, 0.0f, -2.2578f, -5.4509f, 0.5f, 0.2f, 0.62f, 247.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob33", "tread", 45, 67, 0.34f, -2.95f, -5.1095f, 0.3f, 0.2f, 0.7f, 240.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob34", "tread", 122, 62, 0.0f, -0.7701f, -5.8495f, 0.5f, 0.2f, 0.62f, 262.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob35", "tread", 48, 67, -0.34f, -1.527f, -5.699f, 0.3f, 0.2f, 0.7f, 255.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob36", "tread", 0, 65, 0.0f, 0.7701f, -5.8495f, 0.5f, 0.2f, 0.62f, 277.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob37", "tread", 51, 67, 0.34f, 0.0f, -5.9f, 0.3f, 0.2f, 0.7f, 270.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob38", "tread", 4, 65, 0.0f, 2.2578f, -5.4509f, 0.5f, 0.2f, 0.62f, 292.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob39", "tread", 54, 67, -0.34f, 1.527f, -5.699f, 0.3f, 0.2f, 0.7f, 285.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob40", "tread", 8, 65, 0.0f, 3.5917f, -4.6808f, 0.5f, 0.2f, 0.62f, 307.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob41", "tread", 57, 67, 0.34f, 2.95f, -5.1095f, 0.3f, 0.2f, 0.7f, 300.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob42", "tread", 12, 65, 0.0f, 4.6808f, -3.5917f, 0.5f, 0.2f, 0.62f, 322.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob43", "tread", 60, 67, -0.34f, 4.1719f, -4.1719f, 0.3f, 0.2f, 0.7f, 315.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob44", "tread", 16, 65, 0.0f, 5.4509f, -2.2578f, 0.5f, 0.2f, 0.62f, 337.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob45", "tread", 63, 67, 0.34f, 5.1095f, -2.95f, 0.3f, 0.2f, 0.7f, 330.0f, 0.0f, 0.0f);
        cube(front_wheel, "knob46", "tread", 20, 65, 0.0f, 5.8495f, -0.7701f, 0.5f, 0.2f, 0.62f, 352.5f, 0.0f, 0.0f);
        cube(front_wheel, "knob47", "tread", 66, 67, -0.34f, 5.699f, -1.527f, 0.3f, 0.2f, 0.7f, 345.0f, 0.0f, 0.0f);
        cube(front_wheel, "rim", "rim", 111, 46, 0.0f, 4.7589f, 0.6265f, 0.66f, 0.38f, 1.3796f, 7.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim1", "rim", 117, 46, 0.0f, 4.4346f, 1.8369f, 0.66f, 0.38f, 1.3796f, 22.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim2", "rim", 0, 50, 0.0f, 3.8081f, 2.9221f, 0.66f, 0.38f, 1.3796f, 37.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim3", "rim", 6, 50, 0.0f, 2.9221f, 3.8081f, 0.66f, 0.38f, 1.3796f, 52.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim4", "rim", 12, 50, 0.0f, 1.8369f, 4.4346f, 0.66f, 0.38f, 1.3796f, 67.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim5", "rim", 18, 50, 0.0f, 0.6265f, 4.7589f, 0.66f, 0.38f, 1.3796f, 82.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim6", "rim", 24, 50, 0.0f, -0.6265f, 4.7589f, 0.66f, 0.38f, 1.3796f, 97.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim7", "rim", 30, 50, 0.0f, -1.8369f, 4.4346f, 0.66f, 0.38f, 1.3796f, 112.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim8", "rim", 36, 50, 0.0f, -2.9221f, 3.8081f, 0.66f, 0.38f, 1.3796f, 127.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim9", "rim", 42, 50, 0.0f, -3.8081f, 2.9221f, 0.66f, 0.38f, 1.3796f, 142.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim10", "rim", 48, 50, 0.0f, -4.4346f, 1.8369f, 0.66f, 0.38f, 1.3796f, 157.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim11", "rim", 54, 50, 0.0f, -4.7589f, 0.6265f, 0.66f, 0.38f, 1.3796f, 172.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim12", "rim", 60, 50, 0.0f, -4.7589f, -0.6265f, 0.66f, 0.38f, 1.3796f, 187.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim13", "rim", 66, 50, 0.0f, -4.4346f, -1.8369f, 0.66f, 0.38f, 1.3796f, 202.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim14", "rim", 72, 50, 0.0f, -3.8081f, -2.9221f, 0.66f, 0.38f, 1.3796f, 217.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim15", "rim", 78, 50, 0.0f, -2.9221f, -3.8081f, 0.66f, 0.38f, 1.3796f, 232.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim16", "rim", 84, 50, 0.0f, -1.8369f, -4.4346f, 0.66f, 0.38f, 1.3796f, 247.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim17", "rim", 90, 50, 0.0f, -0.6265f, -4.7589f, 0.66f, 0.38f, 1.3796f, 262.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim18", "rim", 96, 50, 0.0f, 0.6265f, -4.7589f, 0.66f, 0.38f, 1.3796f, 277.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim19", "rim", 102, 50, 0.0f, 1.8369f, -4.4346f, 0.66f, 0.38f, 1.3796f, 292.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim20", "rim", 108, 50, 0.0f, 2.9221f, -3.8081f, 0.66f, 0.38f, 1.3796f, 307.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim21", "rim", 114, 50, 0.0f, 3.8081f, -2.9221f, 0.66f, 0.38f, 1.3796f, 322.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim22", "rim", 120, 50, 0.0f, 4.4346f, -1.8369f, 0.66f, 0.38f, 1.3796f, 337.5f, 0.0f, 0.0f);
        cube(front_wheel, "rim23", "rim", 0, 53, 0.0f, 4.7589f, -0.6265f, 0.66f, 0.38f, 1.3796f, 352.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke", "spoke", 110, 16, 0.12f, 2.6273f, 0.3459f, 0.07f, 4.1f, 0.07f, 7.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke1", "spoke", 112, 16, -0.12f, 2.1024f, 1.6132f, 0.07f, 4.1f, 0.07f, 37.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke2", "spoke", 114, 16, 0.12f, 1.0141f, 2.4483f, 0.07f, 4.1f, 0.07f, 67.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke3", "spoke", 116, 16, -0.12f, -0.3459f, 2.6273f, 0.07f, 4.1f, 0.07f, 97.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke4", "spoke", 118, 16, 0.12f, -1.6132f, 2.1024f, 0.07f, 4.1f, 0.07f, 127.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke5", "spoke", 120, 16, -0.12f, -2.4483f, 1.0141f, 0.07f, 4.1f, 0.07f, 157.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke6", "spoke", 122, 16, 0.12f, -2.6273f, -0.3459f, 0.07f, 4.1f, 0.07f, 187.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke7", "spoke", 124, 16, -0.12f, -2.1024f, -1.6132f, 0.07f, 4.1f, 0.07f, 217.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke8", "spoke", 126, 16, 0.12f, -1.0141f, -2.4483f, 0.07f, 4.1f, 0.07f, 247.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke9", "spoke", 0, 24, -0.12f, 0.3459f, -2.6273f, 0.07f, 4.1f, 0.07f, 277.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke10", "spoke", 2, 24, 0.12f, 1.6132f, -2.1024f, 0.07f, 4.1f, 0.07f, 307.5f, 0.0f, 0.0f);
        cube(front_wheel, "spoke11", "spoke", 4, 24, -0.12f, 2.4483f, -1.0141f, 0.07f, 4.1f, 0.07f, 337.5f, 0.0f, 0.0f);
        cube(front_wheel, "hub_barrel", "hub", 6, 53, 0.0f, 0.0f, 0.0f, 1.4f, 0.62f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange", "hub", 96, 42, 0.7f, 0.0f, 0.0f, 0.12f, 1.15f, 1.15f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange_x", "hub", 100, 42, 0.7f, 0.0f, 0.0f, 0.11f, 1.15f, 1.15f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "axle_end", "silver", 69, 67, 0.79f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange1", "hub", 104, 42, -0.7f, 0.0f, 0.0f, 0.12f, 1.15f, 1.15f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "hub_flange_x1", "hub", 108, 42, -0.7f, 0.0f, 0.0f, 0.11f, 1.15f, 1.15f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "axle_end1", "silver", 72, 67, -0.79f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor", "rotor", 46, 59, 0.78f, 1.35f, 0.0f, 0.05f, 0.4f, 0.8639f, 0.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor1", "rotor", 49, 59, 0.78f, 1.1691f, 0.675f, 0.05f, 0.4f, 0.8639f, 30.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor2", "rotor", 52, 59, 0.78f, 0.675f, 1.1691f, 0.05f, 0.4f, 0.8639f, 60.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor3", "rotor", 55, 59, 0.78f, 0.0f, 1.35f, 0.05f, 0.4f, 0.8639f, 90.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor4", "rotor", 58, 59, 0.78f, -0.675f, 1.1691f, 0.05f, 0.4f, 0.8639f, 120.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor5", "rotor", 61, 59, 0.78f, -1.1691f, 0.675f, 0.05f, 0.4f, 0.8639f, 150.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor6", "rotor", 64, 59, 0.78f, -1.35f, 0.0f, 0.05f, 0.4f, 0.8639f, 180.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor7", "rotor", 67, 59, 0.78f, -1.1691f, -0.675f, 0.05f, 0.4f, 0.8639f, 210.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor8", "rotor", 70, 59, 0.78f, -0.675f, -1.1691f, 0.05f, 0.4f, 0.8639f, 240.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor9", "rotor", 73, 59, 0.78f, 0.0f, -1.35f, 0.05f, 0.4f, 0.8639f, 270.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor10", "rotor", 76, 59, 0.78f, 0.675f, -1.1691f, 0.05f, 0.4f, 0.8639f, 300.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor11", "rotor", 79, 59, 0.78f, 1.1691f, -0.675f, 0.05f, 0.4f, 0.8639f, 330.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor_arm", "rotor", 6, 62, 0.78f, 0.5834f, 0.5834f, 0.056f, 0.85f, 0.22f, 45.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor_arm1", "rotor", 8, 62, 0.78f, -0.5834f, 0.5834f, 0.056f, 0.85f, 0.22f, 135.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor_arm2", "rotor", 10, 62, 0.78f, -0.5834f, -0.5834f, 0.056f, 0.85f, 0.22f, 225.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor_arm3", "rotor", 12, 62, 0.78f, 0.5834f, -0.5834f, 0.056f, 0.85f, 0.22f, 315.0f, 0.0f, 0.0f);
        cube(front_wheel, "rotor_bolts", "silver", 82, 59, 0.84f, 0.0f, 0.0f, 0.07f, 0.9f, 0.9f, 0.0f, 0.0f, 0.0f);
        PartDefinition cranks = bone(frame, "cranks", 0.0f, -5.6f, 3.04f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "spindle", "silver", 27, 62, 0.0f, 0.0f, 0.0f, 2.5f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_r", "black", 58, 24, -1.15f, 0.0f, -1.36f, 0.4f, 0.66f, 3.34f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_l", "black", 67, 24, 1.15f, 0.0f, 1.36f, 0.4f, 0.66f, 3.34f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_r_bolt", "silver", 75, 67, -1.38f, 0.0f, 0.0f, 0.08f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "arm_l_bolt", "silver", 78, 67, 1.38f, 0.0f, 0.0f, 0.08f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring", "silver", 52, 42, -0.86f, 0.0f, 0.0f, 0.1f, 0.8036f, 1.9401f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring1", "silver", 18, 46, -0.86f, 0.0f, 0.0f, 0.088f, 1.9401f, 0.8036f, 0.0f, 0.0f, 0.0f);
        cube(cranks, "chainring2", "silver", 58, 42, -0.86f, 0.0f, 0.0f, 0.076f, 0.8036f, 1.9401f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "chainring3", "silver", 21, 46, -0.86f, 0.0f, 0.0f, 0.064f, 1.9401f, 0.8036f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "spider", "black", 60, 69, -1.0f, 0.3536f, 0.3536f, 0.3f, 0.5f, 0.18f, 45.0f, 0.0f, 0.0f);
        cube(cranks, "spider1", "black", 62, 69, -1.0f, -0.3536f, 0.3536f, 0.3f, 0.5f, 0.18f, 135.0f, 0.0f, 0.0f);
        cube(cranks, "spider2", "black", 64, 69, -1.0f, -0.3536f, -0.3536f, 0.3f, 0.5f, 0.18f, 225.0f, 0.0f, 0.0f);
        cube(cranks, "spider3", "black", 66, 69, -1.0f, 0.3536f, -0.3536f, 0.3f, 0.5f, 0.18f, 315.0f, 0.0f, 0.0f);
        PartDefinition pedal_right = bone(cranks, "pedal_right", -2.18f, 0.0f, -2.72f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "platform", "pedal", 21, 30, 0.0f, 0.0f, 0.0f, 1.55f, 0.34f, 1.7f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "pins", "silver", 85, 46, 0.0f, 0.0f, 0.0f, 1.4f, 0.44f, 1.5f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "spindle", "silver", 81, 67, 0.82f, 0.0f, 0.0f, 0.55f, 0.22f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(pedal_right, "end_cap", "black", 84, 67, -0.7f, 0.0f, 0.0f, 0.18f, 0.4f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition pedal_left = bone(cranks, "pedal_left", 2.18f, 0.0f, 2.72f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "platform", "pedal", 29, 30, 0.0f, 0.0f, 0.0f, 1.55f, 0.34f, 1.7f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "pins", "silver", 92, 46, 0.0f, 0.0f, 0.0f, 1.4f, 0.44f, 1.5f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "spindle", "silver", 87, 67, -0.82f, 0.0f, 0.0f, 0.55f, 0.22f, 0.22f, 0.0f, 0.0f, 0.0f);
        cube(pedal_left, "end_cap", "black", 90, 67, 0.7f, 0.0f, 0.0f, 0.18f, 0.4f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition chain_top = bone(frame, "chain_top", -0.8f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(chain_top, "chain", "chain", 53, 0, 0.0f, 0.0f, 0.0f, 0.1f, 0.2f, 10.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition chain_bottom = bone(frame, "chain_bottom", -0.8f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(chain_bottom, "chain", "chain", 75, 0, 0.0f, 0.0f, 0.0f, 0.1f, 0.2f, 10.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition swingarm = bone(root, "swingarm", 0.0f, -8.3f, 4.2f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "yoke_l__chl", "frame", 100, 24, 1.05f, 1.125f, 0.3f, 0.5f, 0.62f, 2.6633f, -71.7946f, -45.0f, 0.0f);
        cube(swingarm, "chainstay_l__chl", "frame", 30, 16, 1.0f, 2.275f, 3.175f, 0.46f, 0.62f, 5.6655f, -0.5441f, 4.357f, 0.0f);
        cube(swingarm, "seatstay_l__chl", "frame", 0, 16, 1.26f, 0.95f, 3.0f, 0.46f, 0.56f, 6.2529f, -16.8852f, -0.8185f, 0.0f);
        cube(swingarm, "rocker_l__chl", "frame", 78, 16, 1.3f, -1.55f, -0.55f, 0.42f, 0.7f, 3.8894f, 70.4633f, 180.0f, 0.0f);
        cube(swingarm, "dropout_l__chl", "black", 112, 42, 1.2f, 2.2f, 5.88f, 0.42f, 1.3f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "pivot_cap_l__chl", "silver", 102, 56, 1.62f, 0.0f, 0.0f, 0.22f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "rocker_bolt_l__chl", "silver", 93, 67, 1.0f, -3.1f, -1.1f, 0.5f, 0.3f, 0.3f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "yoke_r__chl", "frame", 108, 24, -1.05f, 1.125f, 0.3f, 0.5f, 0.62f, 2.6633f, -71.7946f, 45.0f, 0.0f);
        cube(swingarm, "chainstay_r__chl", "frame", 44, 16, -1.0f, 2.275f, 3.175f, 0.46f, 0.62f, 5.6655f, -0.5441f, -4.357f, 0.0f);
        cube(swingarm, "seatstay_r__chl", "frame", 15, 16, -1.26f, 0.95f, 3.0f, 0.46f, 0.56f, 6.2529f, -16.8852f, 0.8185f, 0.0f);
        cube(swingarm, "rocker_r__chl", "frame", 88, 16, -1.3f, -1.55f, -0.55f, 0.42f, 0.7f, 3.8894f, 70.4633f, 180.0f, 0.0f);
        cube(swingarm, "dropout_r__chl", "black", 116, 42, -1.2f, 2.2f, 5.88f, 0.42f, 1.3f, 1.0f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "pivot_cap_r__chl", "silver", 106, 56, -1.62f, 0.0f, 0.0f, 0.22f, 0.8f, 0.8f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "rocker_bolt_r__chl", "silver", 96, 67, -1.0f, -3.1f, -1.1f, 0.5f, 0.3f, 0.3f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "rocker_bar__chl", "frame", 47, 46, 0.0f, -3.1f, -1.1f, 2.9f, 0.62f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "caliper_r__chl", "brake", 120, 42, 0.85f, 1.05f, 4.9f, 0.4f, 1.15f, 0.9f, 0.0f, 0.0f, 0.0f);
        cube(swingarm, "stay_brace__chl", "frame", 34, 62, 0.0f, 1.0f, 2.5f, 2.4f, 0.45f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition shock_shaft = bone(swingarm, "shock_shaft", 0.0f, -3.1f, -1.1f, 0.0f, 0.0f, 0.0f);
        cube(shock_shaft, "eye", "black", 110, 56, 0.0f, 0.0f, 0.0f, 0.82f, 0.55f, 0.55f, 0.0f, 0.0f, 0.0f);
        cube(shock_shaft, "shaft", "silver", 105, 34, 0.0f, 0.0f, 1.3f, 0.3f, 0.3f, 2.6f, 0.0f, 0.0f, 0.0f);
        cube(shock_shaft, "perch", "black", 114, 56, 0.0f, 0.0f, 0.62f, 1.2f, 1.2f, 0.1f, 0.0f, 0.0f, 0.0f);
        PartDefinition rear_wheel = bone(swingarm, "rear_wheel", 0.0f, 2.3f, 5.88f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire", "tire", 112, 34, 0.0f, 5.43f, 0.0f, 1.0f, 0.9f, 1.6102f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire1", "tire", 119, 34, 0.0f, 5.245f, 1.4054f, 1.0f, 0.9f, 1.6102f, 15.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire2", "tire", 0, 38, 0.0f, 4.7025f, 2.715f, 1.0f, 0.9f, 1.6102f, 30.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire3", "tire", 7, 38, 0.0f, 3.8396f, 3.8396f, 1.0f, 0.9f, 1.6102f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire4", "tire", 14, 38, 0.0f, 2.715f, 4.7025f, 1.0f, 0.9f, 1.6102f, 60.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire5", "tire", 21, 38, 0.0f, 1.4054f, 5.245f, 1.0f, 0.9f, 1.6102f, 75.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire6", "tire", 28, 38, 0.0f, 0.0f, 5.43f, 1.0f, 0.9f, 1.6102f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire7", "tire", 35, 38, 0.0f, -1.4054f, 5.245f, 1.0f, 0.9f, 1.6102f, 105.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire8", "tire", 42, 38, 0.0f, -2.715f, 4.7025f, 1.0f, 0.9f, 1.6102f, 120.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire9", "tire", 49, 38, 0.0f, -3.8396f, 3.8396f, 1.0f, 0.9f, 1.6102f, 135.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire10", "tire", 56, 38, 0.0f, -4.7025f, 2.715f, 1.0f, 0.9f, 1.6102f, 150.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire11", "tire", 63, 38, 0.0f, -5.245f, 1.4054f, 1.0f, 0.9f, 1.6102f, 165.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire12", "tire", 70, 38, 0.0f, -5.43f, 0.0f, 1.0f, 0.9f, 1.6102f, 180.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire13", "tire", 77, 38, 0.0f, -5.245f, -1.4054f, 1.0f, 0.9f, 1.6102f, 195.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire14", "tire", 84, 38, 0.0f, -4.7025f, -2.715f, 1.0f, 0.9f, 1.6102f, 210.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire15", "tire", 91, 38, 0.0f, -3.8396f, -3.8396f, 1.0f, 0.9f, 1.6102f, 225.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire16", "tire", 98, 38, 0.0f, -2.715f, -4.7025f, 1.0f, 0.9f, 1.6102f, 240.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire17", "tire", 105, 38, 0.0f, -1.4054f, -5.245f, 1.0f, 0.9f, 1.6102f, 255.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire18", "tire", 112, 38, 0.0f, 0.0f, -5.43f, 1.0f, 0.9f, 1.6102f, 270.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire19", "tire", 119, 38, 0.0f, 1.4054f, -5.245f, 1.0f, 0.9f, 1.6102f, 285.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire20", "tire", 0, 42, 0.0f, 2.715f, -4.7025f, 1.0f, 0.9f, 1.6102f, 300.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire21", "tire", 7, 42, 0.0f, 3.8396f, -3.8396f, 1.0f, 0.9f, 1.6102f, 315.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire22", "tire", 14, 42, 0.0f, 4.7025f, -2.715f, 1.0f, 0.9f, 1.6102f, 330.0f, 0.0f, 0.0f);
        cube(rear_wheel, "tire23", "tire", 21, 42, 0.0f, 5.245f, -1.4054f, 1.0f, 0.9f, 1.6102f, 345.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob", "tread", 24, 65, 0.0f, 5.8495f, 0.7701f, 0.5f, 0.2f, 0.62f, 7.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob1", "tread", 99, 67, 0.34f, 5.9f, 0.0f, 0.3f, 0.2f, 0.7f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob2", "tread", 28, 65, 0.0f, 5.4509f, 2.2578f, 0.5f, 0.2f, 0.62f, 22.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob3", "tread", 102, 67, -0.34f, 5.699f, 1.527f, 0.3f, 0.2f, 0.7f, 15.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob4", "tread", 32, 65, 0.0f, 4.6808f, 3.5917f, 0.5f, 0.2f, 0.62f, 37.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob5", "tread", 105, 67, 0.34f, 5.1095f, 2.95f, 0.3f, 0.2f, 0.7f, 30.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob6", "tread", 36, 65, 0.0f, 3.5917f, 4.6808f, 0.5f, 0.2f, 0.62f, 52.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob7", "tread", 108, 67, -0.34f, 4.1719f, 4.1719f, 0.3f, 0.2f, 0.7f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob8", "tread", 40, 65, 0.0f, 2.2578f, 5.4509f, 0.5f, 0.2f, 0.62f, 67.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob9", "tread", 111, 67, 0.34f, 2.95f, 5.1095f, 0.3f, 0.2f, 0.7f, 60.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob10", "tread", 44, 65, 0.0f, 0.7701f, 5.8495f, 0.5f, 0.2f, 0.62f, 82.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob11", "tread", 114, 67, -0.34f, 1.527f, 5.699f, 0.3f, 0.2f, 0.7f, 75.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob12", "tread", 48, 65, 0.0f, -0.7701f, 5.8495f, 0.5f, 0.2f, 0.62f, 97.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob13", "tread", 117, 67, 0.34f, 0.0f, 5.9f, 0.3f, 0.2f, 0.7f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob14", "tread", 52, 65, 0.0f, -2.2578f, 5.4509f, 0.5f, 0.2f, 0.62f, 112.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob15", "tread", 120, 67, -0.34f, -1.527f, 5.699f, 0.3f, 0.2f, 0.7f, 105.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob16", "tread", 56, 65, 0.0f, -3.5917f, 4.6808f, 0.5f, 0.2f, 0.62f, 127.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob17", "tread", 123, 67, 0.34f, -2.95f, 5.1095f, 0.3f, 0.2f, 0.7f, 120.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob18", "tread", 60, 65, 0.0f, -4.6808f, 3.5917f, 0.5f, 0.2f, 0.62f, 142.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob19", "tread", 0, 69, -0.34f, -4.1719f, 4.1719f, 0.3f, 0.2f, 0.7f, 135.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob20", "tread", 64, 65, 0.0f, -5.4509f, 2.2578f, 0.5f, 0.2f, 0.62f, 157.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob21", "tread", 3, 69, 0.34f, -5.1095f, 2.95f, 0.3f, 0.2f, 0.7f, 150.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob22", "tread", 68, 65, 0.0f, -5.8495f, 0.7701f, 0.5f, 0.2f, 0.62f, 172.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob23", "tread", 6, 69, -0.34f, -5.699f, 1.527f, 0.3f, 0.2f, 0.7f, 165.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob24", "tread", 72, 65, 0.0f, -5.8495f, -0.7701f, 0.5f, 0.2f, 0.62f, 187.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob25", "tread", 9, 69, 0.34f, -5.9f, 0.0f, 0.3f, 0.2f, 0.7f, 180.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob26", "tread", 76, 65, 0.0f, -5.4509f, -2.2578f, 0.5f, 0.2f, 0.62f, 202.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob27", "tread", 12, 69, -0.34f, -5.699f, -1.527f, 0.3f, 0.2f, 0.7f, 195.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob28", "tread", 80, 65, 0.0f, -4.6808f, -3.5917f, 0.5f, 0.2f, 0.62f, 217.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob29", "tread", 15, 69, 0.34f, -5.1095f, -2.95f, 0.3f, 0.2f, 0.7f, 210.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob30", "tread", 84, 65, 0.0f, -3.5917f, -4.6808f, 0.5f, 0.2f, 0.62f, 232.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob31", "tread", 18, 69, -0.34f, -4.1719f, -4.1719f, 0.3f, 0.2f, 0.7f, 225.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob32", "tread", 88, 65, 0.0f, -2.2578f, -5.4509f, 0.5f, 0.2f, 0.62f, 247.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob33", "tread", 21, 69, 0.34f, -2.95f, -5.1095f, 0.3f, 0.2f, 0.7f, 240.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob34", "tread", 92, 65, 0.0f, -0.7701f, -5.8495f, 0.5f, 0.2f, 0.62f, 262.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob35", "tread", 24, 69, -0.34f, -1.527f, -5.699f, 0.3f, 0.2f, 0.7f, 255.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob36", "tread", 96, 65, 0.0f, 0.7701f, -5.8495f, 0.5f, 0.2f, 0.62f, 277.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob37", "tread", 27, 69, 0.34f, 0.0f, -5.9f, 0.3f, 0.2f, 0.7f, 270.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob38", "tread", 100, 65, 0.0f, 2.2578f, -5.4509f, 0.5f, 0.2f, 0.62f, 292.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob39", "tread", 30, 69, -0.34f, 1.527f, -5.699f, 0.3f, 0.2f, 0.7f, 285.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob40", "tread", 104, 65, 0.0f, 3.5917f, -4.6808f, 0.5f, 0.2f, 0.62f, 307.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob41", "tread", 33, 69, 0.34f, 2.95f, -5.1095f, 0.3f, 0.2f, 0.7f, 300.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob42", "tread", 108, 65, 0.0f, 4.6808f, -3.5917f, 0.5f, 0.2f, 0.62f, 322.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob43", "tread", 36, 69, -0.34f, 4.1719f, -4.1719f, 0.3f, 0.2f, 0.7f, 315.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob44", "tread", 112, 65, 0.0f, 5.4509f, -2.2578f, 0.5f, 0.2f, 0.62f, 337.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob45", "tread", 39, 69, 0.34f, 5.1095f, -2.95f, 0.3f, 0.2f, 0.7f, 330.0f, 0.0f, 0.0f);
        cube(rear_wheel, "knob46", "tread", 116, 65, 0.0f, 5.8495f, -0.7701f, 0.5f, 0.2f, 0.62f, 352.5f, 0.0f, 0.0f);
        cube(rear_wheel, "knob47", "tread", 42, 69, -0.34f, 5.699f, -1.527f, 0.3f, 0.2f, 0.7f, 345.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rim", "rim", 12, 53, 0.0f, 4.7589f, 0.6265f, 0.66f, 0.38f, 1.3796f, 7.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim1", "rim", 18, 53, 0.0f, 4.4346f, 1.8369f, 0.66f, 0.38f, 1.3796f, 22.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim2", "rim", 24, 53, 0.0f, 3.8081f, 2.9221f, 0.66f, 0.38f, 1.3796f, 37.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim3", "rim", 30, 53, 0.0f, 2.9221f, 3.8081f, 0.66f, 0.38f, 1.3796f, 52.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim4", "rim", 36, 53, 0.0f, 1.8369f, 4.4346f, 0.66f, 0.38f, 1.3796f, 67.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim5", "rim", 42, 53, 0.0f, 0.6265f, 4.7589f, 0.66f, 0.38f, 1.3796f, 82.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim6", "rim", 48, 53, 0.0f, -0.6265f, 4.7589f, 0.66f, 0.38f, 1.3796f, 97.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim7", "rim", 54, 53, 0.0f, -1.8369f, 4.4346f, 0.66f, 0.38f, 1.3796f, 112.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim8", "rim", 60, 53, 0.0f, -2.9221f, 3.8081f, 0.66f, 0.38f, 1.3796f, 127.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim9", "rim", 66, 53, 0.0f, -3.8081f, 2.9221f, 0.66f, 0.38f, 1.3796f, 142.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim10", "rim", 72, 53, 0.0f, -4.4346f, 1.8369f, 0.66f, 0.38f, 1.3796f, 157.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim11", "rim", 78, 53, 0.0f, -4.7589f, 0.6265f, 0.66f, 0.38f, 1.3796f, 172.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim12", "rim", 84, 53, 0.0f, -4.7589f, -0.6265f, 0.66f, 0.38f, 1.3796f, 187.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim13", "rim", 90, 53, 0.0f, -4.4346f, -1.8369f, 0.66f, 0.38f, 1.3796f, 202.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim14", "rim", 96, 53, 0.0f, -3.8081f, -2.9221f, 0.66f, 0.38f, 1.3796f, 217.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim15", "rim", 102, 53, 0.0f, -2.9221f, -3.8081f, 0.66f, 0.38f, 1.3796f, 232.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim16", "rim", 108, 53, 0.0f, -1.8369f, -4.4346f, 0.66f, 0.38f, 1.3796f, 247.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim17", "rim", 114, 53, 0.0f, -0.6265f, -4.7589f, 0.66f, 0.38f, 1.3796f, 262.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim18", "rim", 120, 53, 0.0f, 0.6265f, -4.7589f, 0.66f, 0.38f, 1.3796f, 277.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim19", "rim", 0, 56, 0.0f, 1.8369f, -4.4346f, 0.66f, 0.38f, 1.3796f, 292.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim20", "rim", 6, 56, 0.0f, 2.9221f, -3.8081f, 0.66f, 0.38f, 1.3796f, 307.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim21", "rim", 12, 56, 0.0f, 3.8081f, -2.9221f, 0.66f, 0.38f, 1.3796f, 322.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim22", "rim", 18, 56, 0.0f, 4.4346f, -1.8369f, 0.66f, 0.38f, 1.3796f, 337.5f, 0.0f, 0.0f);
        cube(rear_wheel, "rim23", "rim", 24, 56, 0.0f, 4.7589f, -0.6265f, 0.66f, 0.38f, 1.3796f, 352.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke", "spoke", 6, 24, 0.12f, 2.6273f, 0.3459f, 0.07f, 4.1f, 0.07f, 7.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke1", "spoke", 8, 24, -0.12f, 2.1024f, 1.6132f, 0.07f, 4.1f, 0.07f, 37.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke2", "spoke", 10, 24, 0.12f, 1.0141f, 2.4483f, 0.07f, 4.1f, 0.07f, 67.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke3", "spoke", 12, 24, -0.12f, -0.3459f, 2.6273f, 0.07f, 4.1f, 0.07f, 97.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke4", "spoke", 14, 24, 0.12f, -1.6132f, 2.1024f, 0.07f, 4.1f, 0.07f, 127.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke5", "spoke", 16, 24, -0.12f, -2.4483f, 1.0141f, 0.07f, 4.1f, 0.07f, 157.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke6", "spoke", 18, 24, 0.12f, -2.6273f, -0.3459f, 0.07f, 4.1f, 0.07f, 187.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke7", "spoke", 20, 24, -0.12f, -2.1024f, -1.6132f, 0.07f, 4.1f, 0.07f, 217.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke8", "spoke", 22, 24, 0.12f, -1.0141f, -2.4483f, 0.07f, 4.1f, 0.07f, 247.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke9", "spoke", 24, 24, -0.12f, 0.3459f, -2.6273f, 0.07f, 4.1f, 0.07f, 277.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke10", "spoke", 26, 24, 0.12f, 1.6132f, -2.1024f, 0.07f, 4.1f, 0.07f, 307.5f, 0.0f, 0.0f);
        cube(rear_wheel, "spoke11", "spoke", 28, 24, -0.12f, 2.4483f, -1.0141f, 0.07f, 4.1f, 0.07f, 337.5f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_barrel", "hub", 30, 56, 0.0f, 0.0f, 0.0f, 1.7f, 0.62f, 0.62f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange", "hub", 124, 42, 0.85f, 0.0f, 0.0f, 0.12f, 1.15f, 1.15f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange_x", "hub", 0, 46, 0.85f, 0.0f, 0.0f, 0.11f, 1.15f, 1.15f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "axle_end", "silver", 45, 69, 1.13f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange1", "hub", 4, 46, -0.85f, 0.0f, 0.0f, 0.12f, 1.15f, 1.15f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "hub_flange_x1", "hub", 8, 46, -0.85f, 0.0f, 0.0f, 0.11f, 1.15f, 1.15f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "axle_end1", "silver", 48, 69, -1.13f, 0.0f, 0.0f, 0.2f, 0.42f, 0.42f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor", "rotor", 85, 59, 0.92f, 1.2f, 0.0f, 0.05f, 0.4f, 0.7803f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor1", "rotor", 88, 59, 0.92f, 1.0392f, 0.6f, 0.05f, 0.4f, 0.7803f, 30.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor2", "rotor", 91, 59, 0.92f, 0.6f, 1.0392f, 0.05f, 0.4f, 0.7803f, 60.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor3", "rotor", 94, 59, 0.92f, 0.0f, 1.2f, 0.05f, 0.4f, 0.7803f, 90.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor4", "rotor", 97, 59, 0.92f, -0.6f, 1.0392f, 0.05f, 0.4f, 0.7803f, 120.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor5", "rotor", 100, 59, 0.92f, -1.0392f, 0.6f, 0.05f, 0.4f, 0.7803f, 150.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor6", "rotor", 103, 59, 0.92f, -1.2f, 0.0f, 0.05f, 0.4f, 0.7803f, 180.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor7", "rotor", 106, 59, 0.92f, -1.0392f, -0.6f, 0.05f, 0.4f, 0.7803f, 210.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor8", "rotor", 109, 59, 0.92f, -0.6f, -1.0392f, 0.05f, 0.4f, 0.7803f, 240.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor9", "rotor", 112, 59, 0.92f, 0.0f, -1.2f, 0.05f, 0.4f, 0.7803f, 270.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor10", "rotor", 115, 59, 0.92f, 0.6f, -1.0392f, 0.05f, 0.4f, 0.7803f, 300.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor11", "rotor", 118, 59, 0.92f, 1.0392f, -0.6f, 0.05f, 0.4f, 0.7803f, 330.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm", "rotor", 68, 69, 0.92f, 0.5303f, 0.5303f, 0.056f, 0.7f, 0.22f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm1", "rotor", 70, 69, 0.92f, -0.5303f, 0.5303f, 0.056f, 0.7f, 0.22f, 135.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm2", "rotor", 72, 69, 0.92f, -0.5303f, -0.5303f, 0.056f, 0.7f, 0.22f, 225.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_arm3", "rotor", 74, 69, 0.92f, 0.5303f, -0.5303f, 0.056f, 0.7f, 0.22f, 315.0f, 0.0f, 0.0f);
        cube(rear_wheel, "rotor_bolts", "silver", 121, 59, 0.98f, 0.0f, 0.0f, 0.07f, 0.9f, 0.9f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog", "silver", 64, 42, -0.6f, 0.0f, 0.0f, 0.1f, 0.8036f, 1.9401f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog1", "silver", 24, 46, -0.6f, 0.0f, 0.0f, 0.088f, 1.9401f, 0.8036f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog2", "silver", 70, 42, -0.6f, 0.0f, 0.0f, 0.076f, 0.8036f, 1.9401f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog3", "silver", 27, 46, -0.6f, 0.0f, 0.0f, 0.064f, 1.9401f, 0.8036f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog4", "silver", 86, 42, -0.72f, 0.0f, 0.0f, 0.1f, 0.6506f, 1.5706f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog5", "silver", 30, 46, -0.72f, 0.0f, 0.0f, 0.088f, 1.5706f, 0.6506f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog6", "silver", 91, 42, -0.72f, 0.0f, 0.0f, 0.076f, 0.6506f, 1.5706f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog7", "silver", 33, 46, -0.72f, 0.0f, 0.0f, 0.064f, 1.5706f, 0.6506f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog8", "silver", 118, 56, -0.83f, 0.0f, 0.0f, 0.1f, 0.5051f, 1.2195f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog9", "silver", 124, 59, -0.83f, 0.0f, 0.0f, 0.088f, 1.2195f, 0.5051f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog10", "silver", 122, 56, -0.83f, 0.0f, 0.0f, 0.076f, 0.5051f, 1.2195f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog11", "silver", 0, 62, -0.83f, 0.0f, 0.0f, 0.064f, 1.2195f, 0.5051f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog12", "silver", 0, 59, -0.93f, 0.0f, 0.0f, 0.1f, 0.3827f, 0.9239f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog13", "silver", 14, 62, -0.93f, 0.0f, 0.0f, 0.088f, 0.9239f, 0.3827f, 0.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog14", "silver", 3, 62, -0.93f, 0.0f, 0.0f, 0.076f, 0.3827f, 0.9239f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cog15", "silver", 16, 62, -0.93f, 0.0f, 0.0f, 0.064f, 0.9239f, 0.3827f, 45.0f, 0.0f, 0.0f);
        cube(rear_wheel, "cassette_lock", "black", 51, 69, -1.0f, 0.0f, 0.0f, 0.1f, 0.5f, 0.5f, 0.0f, 0.0f, 0.0f);
        PartDefinition shock_spring = bone(shock_body, "shock_spring", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(shock_spring, "coil", "spring", 4, 59, 0.0f, 0.0f, 0.8482f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 0.0f);
        cube(shock_spring, "coil1", "spring", 8, 59, 0.0f, 0.0f, 1.1047f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 45.0f);
        cube(shock_spring, "coil2", "spring", 12, 59, 0.0f, 0.0f, 1.3612f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 0.0f);
        cube(shock_spring, "coil3", "spring", 16, 59, 0.0f, 0.0f, 1.6177f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 45.0f);
        cube(shock_spring, "coil4", "spring", 20, 59, 0.0f, 0.0f, 1.8742f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 0.0f);
        cube(shock_spring, "coil5", "spring", 24, 59, 0.0f, 0.0f, 2.1306f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 45.0f);
        cube(shock_spring, "coil6", "spring", 28, 59, 0.0f, 0.0f, 2.3871f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 0.0f);
        cube(shock_spring, "coil7", "spring", 32, 59, 0.0f, 0.0f, 2.6436f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 45.0f);
        cube(shock_spring, "coil8", "spring", 36, 59, 0.0f, 0.0f, 2.9001f, 1.0f, 1.0f, 0.15f, 0.0f, 0.0f, 0.0f);
                cube(frame, "top_front__l", "frame", 0, 128, 0.0000f, -14.3410f, -2.2004f, 0.8000f, 0.9000f, 5.6981f, -33.4229f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__l", "frame", 14, 128, 0.0000f, -12.7024f, 2.5554f, 0.8000f, 0.9000f, 4.7578f, -1.6692f, 0.0000f, 0.0000f);
        cube(frame, "pivot_boss__h", "frame", 27, 128, 0.0000f, -10.3000f, 4.2000f, 2.3000f, 1.0000f, 1.0000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "pivot_gusset__h", "frame", 35, 128, 0.0000f, -10.3500f, 3.8000f, 0.8000f, 1.2000f, 1.2000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "idler__h", "hub", 40, 128, 1.2000f, -10.3000f, 4.2000f, 0.4000f, 1.8000f, 1.8000f, 0.0000f, 0.0000f, 0.0000f);
        cube(shock_body, "air_can", "aircan", 46, 128, 0.0000f, 0.0000f, 1.6000f, 1.2500f, 1.2500f, 2.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_ding_bell", "chrome", 55, 128, 3.0000f, -2.6000f, -1.0000f, 0.7000f, 0.5500f, 0.7000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_ding_mount", "black", 59, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_mini_bell", "chrome", 62, 128, 3.0000f, -2.6000f, -1.0000f, 0.5000f, 0.5500f, 0.5000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_mini_mount", "black", 65, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_classic_bell", "chrome", 68, 128, 3.0000f, -2.6000f, -1.0000f, 0.9000f, 0.5500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_classic_mount", "black", 73, 128, 3.0000f, -2.1000f, -1.0000f, 0.4000f, 0.5000f, 0.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_horn_body", "horn", 76, 128, 3.0000f, -2.6000f, -1.6000f, 0.8000f, 0.8000f, 1.6000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_horn_mouth", "chrome", 82, 128, 3.0000f, -2.6000f, -2.5000f, 1.2000f, 1.2000f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_body", "duck", 86, 128, 3.0000f, -2.9000f, -1.0000f, 1.0000f, 0.8000f, 1.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_head", "duck", 92, 128, 3.0000f, -3.6000f, -1.5000f, 0.8000f, 0.8000f, 0.8000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_beak", "beak", 97, 128, 3.0000f, -3.4500f, -2.0000f, 0.5500f, 0.2000f, 0.4500f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_eyer", "eye", 100, 128, 2.5900f, -3.7000f, -1.6500f, 0.0500f, 0.1200f, 0.1200f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_duck_eyel", "eye", 102, 128, 3.4100f, -3.7000f, -1.6500f, 0.0500f, 0.1200f, 0.1200f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_flight_body", "lightbody", 104, 128, 0.0000f, -2.3000f, -1.6000f, 0.8500f, 0.7000f, 1.4000f, 0.0000f, 0.0000f, 0.0000f);
        cube(fork_upper, "acc_flight_lens", "lens", 110, 128, 0.0000f, -2.3000f, -2.3200f, 0.6500f, 0.5000f, 0.0400f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "acc_rlight_body", "lightbody", 113, 128, 0.0000f, -14.0000f, 5.0000f, 0.5500f, 0.7000f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "acc_rlight_lens", "lens", 117, 128, 0.0000f, -14.0000f, 5.3500f, 0.4000f, 0.5000f, 0.0400f, 0.0000f, 0.0000f, 0.0000f);





        // BEGIN FRAME CATALOG
        PartDefinition catalog_upper = bone(frame, "catalog_upper", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition catalog_lower = bone(frame, "catalog_lower", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        PartDefinition catalog_stays = bone(frame, "catalog_stays", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        cube(frame, "top_front__n", "frame", 0, 160, 0.0000f, -14.6000f, -1.8850f, 0.8200f, 0.8200f, 5.2578f, -29.6368f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__n", "frame", 14, 160, 0.0000f, -13.3000f, 2.6410f, 0.8200f, 0.8200f, 4.4820f, -0.0000f, 0.0000f, 0.0000f);
        cube(frame, "down_front__n", "frame", 26, 160, 0.0000f, -11.9550f, -2.0250f, 1.3000f, 1.3910f, 8.3980f, -53.0346f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__n", "frame", 47, 160, 0.0000f, -7.1000f, 1.7700f, 1.3000f, 1.3910f, 3.9309f, -49.7465f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__n", "frame", 59, 160, 0.4800f, -7.7153f, 1.2181f, 0.2500f, 0.5000f, 0.0473f, -139.7465f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__n", "frame", 61, 160, -0.4800f, -7.7153f, 1.2181f, 0.2500f, 0.5000f, 0.0473f, -139.7465f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__n", "silver", 63, 160, 0.0000f, -7.7000f, 1.2000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__n", "frame", 68, 160, 0.0000f, -6.6000f, 3.2800f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__n", "silver", 74, 160, 0.0000f, -6.6000f, 3.2800f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__n", "frame", 80, 160, 0.0000f, -10.8000f, 4.2884f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__n", "silver", 86, 160, 0.0000f, -10.8000f, 4.2884f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__n", "frame", 92, 160, 0.7500f, -10.2000f, 5.0442f, 0.3200f, 0.5000f, 1.9300f, -38.4447f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__n", "frame", 98, 160, -0.7500f, -10.2000f, 5.0442f, 0.3200f, 0.5000f, 1.9300f, -38.4447f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__n", "silver", 104, 160, 0.0000f, -9.6000f, 5.8000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_front_l__n", "frame", 109, 160, 0.4800f, -7.1000f, 3.6400f, 0.2800f, 0.4500f, 1.2322f, 54.2461f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_front_r__n", "frame", 114, 160, -0.4800f, -7.1000f, 3.6400f, 0.2800f, 0.4500f, 1.2322f, 54.2461f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_rear_l__n", "frame", 119, 160, 0.4800f, -7.6000f, 4.3750f, 0.2800f, 0.4500f, 0.7500f, -0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_rear_r__n", "frame", 123, 160, -0.4800f, -7.6000f, 4.3750f, 0.2800f, 0.4500f, 0.7500f, -0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_arm_bolt__n", "silver", 0, 171, 0.0000f, -7.6000f, 4.0000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_link_l__n", "frame", 5, 171, 0.7500f, -7.1000f, 4.0150f, 0.3200f, 0.5000f, 1.7779f, 34.2264f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_link_r__n", "frame", 11, 171, -0.7500f, -7.1000f, 4.0150f, 0.3200f, 0.5000f, 1.7779f, 34.2264f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_rear_pivot__n", "silver", 17, 171, 0.0000f, -7.6000f, 4.7500f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__n", "frame", 22, 171, 1.0000f, 1.5000f, 3.2150f, 0.4000f, 0.5800f, 5.5650f, -16.7091f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__n", "frame", 35, 171, -1.0000f, 1.5000f, 3.2150f, 0.4000f, 0.5800f, 5.5650f, -16.7091f, 0.0000f, 0.0000f);
        cube(swingarm, "seatstay_l__n", "frame", 48, 171, 1.0000f, 0.5000f, 3.7400f, 0.3800f, 0.5000f, 5.5927f, -40.0679f, 0.0000f, 0.0000f);
        cube(swingarm, "seatstay_r__n", "frame", 61, 171, -1.0000f, 0.5000f, 3.7400f, 0.3800f, 0.5000f, 5.5927f, -40.0679f, 0.0000f, 0.0000f);
        cube(swingarm, "rear_upright_l__n", "frame", 74, 171, 1.0000f, -0.3000f, 1.0750f, 0.3800f, 0.5200f, 2.2589f, 62.3005f, 0.0000f, 0.0000f);
        cube(swingarm, "rear_upright_r__n", "frame", 81, 171, -1.0000f, -0.3000f, 1.0750f, 0.3800f, 0.5200f, 2.2589f, 62.3005f, 0.0000f, 0.0000f);
        cube(swingarm, "dropout_l__n", "black", 88, 171, 1.0000f, 2.3000f, 5.8800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "dropout_r__n", "black", 92, 171, -1.0000f, 2.3000f, 5.8800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "caliper_r__n", "brake", 96, 171, -0.8500f, 1.0500f, 4.9000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "top_front__t", "frame", 100, 171, 0.0000f, -15.3000f, -2.0850f, 1.0000f, 1.0000f, 4.3392f, -16.0542f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__t", "frame", 112, 171, 0.0000f, -14.1500f, 2.4770f, 1.0000f, 1.0000f, 5.0747f, -12.5190f, 0.0000f, 0.0000f);
        cube(frame, "down_front__t", "frame", 0, 179, 0.0000f, -11.2550f, -1.6750f, 1.4500f, 1.5515f, 9.9416f, -54.6633f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__t", "frame", 24, 179, 0.0000f, -6.4000f, 2.1200f, 1.4500f, 1.5515f, 2.4384f, -41.0091f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__t", "frame", 33, 179, 0.4800f, -14.1500f, -0.1500f, 0.2500f, 0.5000f, 1.1402f, -105.2551f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__t", "frame", 37, 179, -0.4800f, -14.1500f, -0.1500f, 0.2500f, 0.5000f, 1.1402f, -105.2551f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__t", "silver", 41, 179, 0.0000f, -13.6000f, -0.3000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__t", "frame", 46, 179, 0.0000f, -8.3582f, 3.9570f, 1.6000f, 0.7000f, 0.4996f, -13.4604f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__t", "silver", 52, 179, 0.0000f, -8.3000f, 4.2000f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__t", "frame", 58, 179, 0.0000f, -11.2000f, 4.3840f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__t", "silver", 64, 179, 0.0000f, -11.2000f, 4.3840f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__t", "frame", 70, 179, 0.7500f, -10.9250f, 5.0920f, 0.3200f, 0.5000f, 1.5191f, -21.2271f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__t", "frame", 75, 179, -0.7500f, -10.9250f, 5.0920f, 0.3200f, 0.5000f, 1.5191f, -21.2271f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__t", "silver", 80, 179, 0.0000f, -10.6500f, 5.8000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_l__t", "frame", 85, 179, 0.4800f, -10.9500f, 4.0170f, 0.2800f, 0.4500f, 0.8881f, -145.7374f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_r__t", "frame", 89, 179, -0.4800f, -10.9500f, 4.0170f, 0.2800f, 0.4500f, 0.8881f, -145.7374f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_l__t", "frame", 93, 179, 0.4800f, -10.6750f, 4.7250f, 0.2800f, 0.4500f, 2.1506f, -1.3322f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_r__t", "frame", 99, 179, -0.4800f, -10.6750f, 4.7250f, 0.2800f, 0.4500f, 2.1506f, -1.3322f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_arm_bolt__t", "silver", 105, 179, 0.0000f, -10.7000f, 3.6500f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__t", "frame", 110, 179, 1.0000f, 1.2250f, 2.5000f, 0.4000f, 0.5800f, 5.5680f, -26.1049f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__t", "frame", 0, 192, -1.0000f, 1.2250f, 2.5000f, 0.4000f, 0.5800f, 5.5680f, -26.1049f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_l__t", "frame", 13, 192, 1.0000f, -8.3250f, 7.9400f, 0.3800f, 0.5000f, 6.3199f, -47.3726f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_r__t", "frame", 28, 192, -1.0000f, -8.3250f, 7.9400f, 0.3800f, 0.5000f, 6.3199f, -47.3726f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_l__t", "frame", 43, 192, 1.0000f, -5.9250f, 9.6400f, 0.3800f, 0.5000f, 0.8927f, -170.3266f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_r__t", "frame", 47, 192, -1.0000f, -5.9250f, 9.6400f, 0.3800f, 0.5000f, 0.8927f, -170.3266f, 0.0000f, 0.0000f);
        cube(swingarm, "horst_pivot__t", "silver", 51, 192, 0.0000f, 2.4500f, 5.0000f, 2.2000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_l__t", "black", 57, 192, 1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_r__t", "black", 61, 192, -1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "caliper_r__t", "brake", 65, 192, -0.8500f, -7.2500f, 9.1000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "top_front__u", "frame", 69, 192, 0.0000f, -14.5000f, -1.8850f, 0.9500f, 0.9500f, 5.3596f, -31.4954f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__u", "frame", 83, 192, 0.0000f, -12.7500f, 2.5330f, 0.9500f, 0.9500f, 4.3230f, -9.3185f, 0.0000f, 0.0000f);
        cube(frame, "down_front__u", "frame", 95, 192, 0.0000f, -11.8550f, -2.1750f, 1.5000f, 1.6050f, 8.3851f, -55.4950f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__u", "frame", 116, 192, 0.0000f, -7.0000f, 1.6200f, 1.5000f, 1.6050f, 3.9882f, -44.5937f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__u", "frame", 0, 203, 0.4800f, -11.4973f, -1.4949f, 0.2500f, 0.5000f, 0.7157f, 34.5050f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__u", "frame", 3, 203, -0.4800f, -11.4973f, -1.4949f, 0.2500f, 0.5000f, 0.7157f, 34.5050f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__u", "silver", 6, 203, 0.0000f, -11.7000f, -1.2000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__u", "frame", 11, 203, 0.0000f, -8.3582f, 3.9570f, 1.6000f, 0.7000f, 0.4996f, -13.4604f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__u", "silver", 17, 203, 0.0000f, -8.3000f, 4.2000f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__u", "frame", 23, 203, 0.0000f, -10.9000f, 4.3120f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__u", "silver", 29, 203, 0.0000f, -10.9000f, 4.3120f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__u", "frame", 35, 203, 0.7500f, -10.6250f, 5.0560f, 0.3200f, 0.5000f, 1.5864f, -20.2855f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__u", "frame", 40, 203, -0.7500f, -10.6250f, 5.0560f, 0.3200f, 0.5000f, 1.5864f, -20.2855f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__u", "silver", 45, 203, 0.0000f, -10.3500f, 5.8000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_l__u", "frame", 50, 203, 0.4800f, -10.2500f, 3.9810f, 0.2800f, 0.4500f, 1.4589f, -116.9866f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_r__u", "frame", 55, 203, -0.4800f, -10.2500f, 3.9810f, 0.2800f, 0.4500f, 1.4589f, -116.9866f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_l__u", "frame", 60, 203, 0.4800f, -9.9750f, 4.7250f, 0.2800f, 0.4500f, 2.2771f, 19.2307f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_r__u", "frame", 67, 203, -0.4800f, -9.9750f, 4.7250f, 0.2800f, 0.4500f, 2.2771f, 19.2307f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_arm_bolt__u", "silver", 74, 203, 0.0000f, -9.6000f, 3.6500f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__u", "frame", 79, 203, 1.0000f, 1.2500f, 2.4750f, 0.4000f, 0.5800f, 5.5455f, -26.7961f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__u", "frame", 92, 203, -1.0000f, 1.2500f, 2.4750f, 0.4000f, 0.5800f, 5.5455f, -26.7961f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_l__u", "frame", 105, 203, 1.0000f, -8.1750f, 7.9400f, 0.3800f, 0.5000f, 6.1025f, -45.4647f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_r__u", "frame", 0, 211, -1.0000f, -8.1750f, 7.9400f, 0.3800f, 0.5000f, 6.1025f, -45.4647f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_l__u", "frame", 14, 211, 1.0000f, -5.9000f, 9.6150f, 0.3800f, 0.5000f, 0.9513f, -167.8632f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_r__u", "frame", 18, 211, -1.0000f, -5.9000f, 9.6150f, 0.3800f, 0.5000f, 0.9513f, -167.8632f, 0.0000f, 0.0000f);
        cube(swingarm, "horst_pivot__u", "silver", 22, 211, 0.0000f, 2.5000f, 4.9500f, 2.2000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_l__u", "black", 28, 211, 1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_r__u", "black", 32, 211, -1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "caliper_r__u", "brake", 36, 211, -0.8500f, -7.2500f, 9.1000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "top_front__m", "frame", 40, 211, 0.0000f, -15.5500f, -1.9850f, 0.9000f, 0.9000f, 4.4257f, -9.1005f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__m", "frame", 52, 211, 0.0000f, -14.4000f, 2.5770f, 0.9000f, 0.9000f, 5.0160f, -18.6011f, 0.0000f, 0.0000f);
        cube(frame, "down_front__m", "frame", 65, 211, 0.0000f, -12.6550f, -2.1750f, 1.1200f, 1.1984f, 7.1245f, -48.1861f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__m", "frame", 83, 211, 0.0000f, -7.8000f, 1.6200f, 1.1200f, 1.1984f, 5.2369f, -57.1596f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__m", "frame", 97, 211, 0.4800f, -9.7213f, 0.3330f, 0.2500f, 0.5000f, 0.0787f, -147.1596f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__m", "frame", 99, 211, -0.4800f, -9.7213f, 0.3330f, 0.2500f, 0.5000f, 0.0787f, -147.1596f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__m", "silver", 101, 211, 0.0000f, -9.7000f, 0.3000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__m", "frame", 106, 211, 0.0000f, -6.4000f, 3.2320f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__m", "silver", 112, 211, 0.0000f, -6.4000f, 3.2320f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__m", "frame", 118, 211, 0.0000f, -10.7000f, 4.2640f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__m", "silver", 0, 221, 0.0000f, -10.7000f, 4.2640f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__m", "frame", 6, 221, 0.7500f, -9.7000f, 4.9820f, 0.3200f, 0.5000f, 2.4621f, -54.3217f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__m", "frame", 13, 221, -0.7500f, -9.7000f, 4.9820f, 0.3200f, 0.5000f, 2.4621f, -54.3217f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__m", "silver", 20, 221, 0.0000f, -8.7000f, 5.7000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_front_l__m", "frame", 25, 221, 0.4800f, -6.9500f, 3.6160f, 0.2800f, 0.4500f, 1.3416f, 55.0780f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_front_r__m", "frame", 30, 221, -0.4800f, -6.9500f, 3.6160f, 0.2800f, 0.4500f, 1.3416f, 55.0780f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_rear_l__m", "frame", 35, 221, 0.4800f, -7.2500f, 4.3500f, 0.2800f, 0.4500f, 0.8602f, -35.5377f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_lever_rear_r__m", "frame", 39, 221, -0.4800f, -7.2500f, 4.3500f, 0.2800f, 0.4500f, 0.8602f, -35.5377f, 0.0000f, 0.0000f);
        cube(catalog_lower, "shock_arm_bolt__m", "silver", 43, 221, 0.0000f, -7.5000f, 4.0000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_link_l__m", "frame", 48, 221, 0.7500f, -6.7000f, 3.9660f, 0.3200f, 0.5000f, 1.5859f, 22.2308f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_link_r__m", "frame", 53, 221, -0.7500f, -6.7000f, 3.9660f, 0.3200f, 0.5000f, 1.5859f, 22.2308f, 0.0000f, 0.0000f);
        cube(catalog_lower, "lower_rear_pivot__m", "silver", 58, 221, 0.0000f, -7.0000f, 4.7000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__m", "frame", 63, 221, 1.0000f, 1.8000f, 3.1900f, 0.4000f, 0.5800f, 5.4721f, -10.5296f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__m", "frame", 76, 221, -1.0000f, 1.8000f, 3.1900f, 0.4000f, 0.5800f, 5.4721f, -10.5296f, 0.0000f, 0.0000f);
        cube(swingarm, "seatstay_l__m", "frame", 89, 221, 1.0000f, 0.9500f, 3.6900f, 0.3800f, 0.5000f, 5.1453f, -31.6513f, 0.0000f, 0.0000f);
        cube(swingarm, "seatstay_r__m", "frame", 102, 221, -1.0000f, 0.9500f, 3.6900f, 0.3800f, 0.5000f, 5.1453f, -31.6513f, 0.0000f, 0.0000f);
        cube(swingarm, "rear_upright_l__m", "frame", 115, 221, 1.0000f, 0.4500f, 1.0000f, 0.3800f, 0.5200f, 1.9723f, 59.5345f, 0.0000f, 0.0000f);
        cube(swingarm, "rear_upright_r__m", "frame", 121, 221, -1.0000f, 0.4500f, 1.0000f, 0.3800f, 0.5200f, 1.9723f, 59.5345f, 0.0000f, 0.0000f);
        cube(swingarm, "dropout_l__m", "black", 0, 229, 1.0000f, 2.3000f, 5.8800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "dropout_r__m", "black", 4, 229, -1.0000f, 2.3000f, 5.8800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "caliper_r__m", "brake", 8, 229, -0.8500f, 1.0500f, 4.9000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "top_front__g", "frame", 12, 229, 0.0000f, -15.1500f, -2.0850f, 0.9000f, 0.9000f, 4.4316f, -19.7843f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__g", "frame", 24, 229, 0.0000f, -13.4500f, 2.3450f, 0.9000f, 0.9000f, 5.0602f, -22.0537f, 0.0000f, 0.0000f);
        cube(frame, "down_front__g", "frame", 37, 229, 0.0000f, -11.7550f, -1.9750f, 1.4200f, 1.5194f, 8.7792f, -54.0830f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__g", "frame", 59, 229, 0.0000f, -6.9000f, 1.8200f, 1.4200f, 1.5194f, 3.5656f, -46.8183f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__g", "frame", 70, 229, 0.4800f, -13.4650f, 0.1099f, 0.2500f, 0.5000f, 1.6507f, -112.0537f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__g", "frame", 75, 229, -0.4800f, -13.4650f, 0.1099f, 0.2500f, 0.5000f, 1.6507f, -112.0537f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__g", "silver", 80, 229, 0.0000f, -12.7000f, -0.2000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__g", "frame", 85, 229, 0.0000f, -8.3582f, 3.9570f, 1.6000f, 0.7000f, 0.4996f, -13.4604f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__g", "silver", 91, 229, 0.0000f, -8.3000f, 4.2000f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__g", "frame", 97, 229, 0.0000f, -11.3000f, 4.4080f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__g", "silver", 103, 229, 0.0000f, -11.3000f, 4.4080f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__g", "frame", 109, 229, 0.7500f, -11.0000f, 5.1290f, 0.3200f, 0.5000f, 1.5618f, -22.5916f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__g", "frame", 114, 229, -0.7500f, -11.0000f, 5.1290f, 0.3200f, 0.5000f, 1.5618f, -22.5916f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__g", "silver", 119, 229, 0.0000f, -10.7000f, 5.8500f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_l__g", "frame", 0, 241, 0.4800f, -10.8000f, 4.0290f, 0.2800f, 0.4500f, 1.2548f, -127.1621f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_r__g", "frame", 5, 241, -0.4800f, -10.8000f, 4.0290f, 0.2800f, 0.4500f, 1.2548f, -127.1621f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_l__g", "frame", 10, 241, 0.4800f, -10.5000f, 4.7500f, 0.2800f, 0.4500f, 2.2361f, 10.3048f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_r__g", "frame", 17, 241, -0.4800f, -10.5000f, 4.7500f, 0.2800f, 0.4500f, 2.2361f, 10.3048f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_arm_bolt__g", "silver", 24, 241, 0.0000f, -10.3000f, 3.6500f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__g", "frame", 29, 241, 1.0000f, 1.2000f, 2.4750f, 0.4000f, 0.5800f, 5.5011f, -25.8664f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__g", "frame", 42, 241, -1.0000f, 1.2000f, 2.4750f, 0.4000f, 0.5800f, 5.5011f, -25.8664f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_l__g", "frame", 55, 241, 1.0000f, -8.3500f, 7.9650f, 0.3800f, 0.5000f, 6.3232f, -48.0128f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_r__g", "frame", 70, 241, -1.0000f, -8.3500f, 7.9650f, 0.3800f, 0.5000f, 6.3232f, -48.0128f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_l__g", "frame", 85, 241, 1.0000f, -5.9500f, 9.6150f, 0.3800f, 0.5000f, 0.9354f, -173.8627f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_r__g", "frame", 89, 241, -1.0000f, -5.9500f, 9.6150f, 0.3800f, 0.5000f, 0.9354f, -173.8627f, 0.0000f, 0.0000f);
        cube(swingarm, "horst_pivot__g", "silver", 93, 241, 0.0000f, 2.4000f, 4.9500f, 2.2000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_l__g", "black", 99, 241, 1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_r__g", "black", 103, 241, -1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "caliper_r__g", "brake", 107, 241, -0.8500f, -7.2500f, 9.1000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "top_front__j", "frame", 111, 241, 0.0000f, -14.7000f, -1.6850f, 0.7200f, 0.7200f, 5.5191f, -25.7758f, 0.0000f, 0.0000f);
        cube(frame, "top_rear__j", "frame", 0, 249, 0.0000f, -13.5500f, 2.8770f, 0.7200f, 0.7200f, 4.1552f, 1.3790f, 0.0000f, 0.0000f);
        cube(frame, "down_front__j", "frame", 11, 249, 0.0000f, -12.4050f, -2.7750f, 1.1800f, 1.2626f, 6.8087f, -58.5744f, 0.0000f, 0.0000f);
        cube(frame, "down_rear__j", "frame", 28, 249, 0.0000f, -7.5500f, 1.0200f, 1.1800f, 1.2626f, 5.6153f, -43.9899f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_l__j", "frame", 43, 249, 0.4800f, -13.5678f, 0.2310f, 0.2500f, 0.5000f, 0.3727f, -115.7758f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_r__j", "frame", 46, 249, -0.4800f, -13.5678f, 0.2310f, 0.2500f, 0.5000f, 0.3727f, -115.7758f, 0.0000f, 0.0000f);
        cube(frame, "shock_mount_bolt__j", "silver", 49, 249, 0.0000f, -13.4000f, 0.1500f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot_boss__j", "frame", 54, 249, 0.0000f, -8.3582f, 3.9570f, 1.6000f, 0.7000f, 0.4996f, -13.4604f, 0.0000f, 0.0000f);
        cube(frame, "lower_pivot__j", "silver", 60, 249, 0.0000f, -8.3000f, 4.2000f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot_boss__j", "frame", 66, 249, 0.0000f, -10.9000f, 4.3120f, 1.7500f, 0.6500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(frame, "upper_pivot__j", "silver", 72, 249, 0.0000f, -10.9000f, 4.3120f, 2.0500f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_l__j", "frame", 78, 249, 0.7500f, -11.0750f, 5.0560f, 0.3200f, 0.5000f, 1.5286f, 13.2362f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_link_r__j", "frame", 83, 249, -0.7500f, -11.0750f, 5.0560f, 0.3200f, 0.5000f, 1.5286f, 13.2362f, 0.0000f, 0.0000f);
        cube(catalog_upper, "upper_rear_pivot__j", "silver", 88, 249, 0.0000f, -11.2500f, 5.8000f, 1.7000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_l__j", "frame", 93, 249, 0.4800f, -11.0000f, 3.9560f, 0.2800f, 0.4500f, 0.7396f, 164.3100f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_front_r__j", "frame", 97, 249, -0.4800f, -11.0000f, 3.9560f, 0.2800f, 0.4500f, 0.7396f, 164.3100f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_l__j", "frame", 101, 249, 0.4800f, -11.1750f, 4.7000f, 0.2800f, 0.4500f, 2.2051f, 3.9005f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_lever_rear_r__j", "frame", 107, 249, -0.4800f, -11.1750f, 4.7000f, 0.2800f, 0.4500f, 2.2051f, 3.9005f, 0.0000f, 0.0000f);
        cube(catalog_upper, "shock_arm_bolt__j", "silver", 113, 249, 0.0000f, -11.1000f, 3.6000f, 1.3000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_l__j", "frame", 0, 259, 1.0000f, 1.2250f, 2.5000f, 0.4000f, 0.5800f, 5.5680f, -26.1049f, 0.0000f, 0.0000f);
        cube(swingarm, "chainstay_r__j", "frame", 13, 259, -1.0000f, 1.2250f, 2.5000f, 0.4000f, 0.5800f, 5.5680f, -26.1049f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_l__j", "frame", 26, 259, 1.0000f, -8.6250f, 7.9400f, 0.3800f, 0.5000f, 6.7735f, -50.8118f, 0.0000f, 0.0000f);
        cube(catalog_stays, "seatstay_r__j", "frame", 42, 259, -1.0000f, -8.6250f, 7.9400f, 0.3800f, 0.5000f, 6.7735f, -50.8118f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_l__j", "frame", 58, 259, 1.0000f, -5.9250f, 9.6400f, 0.3800f, 0.5000f, 0.8927f, -170.3266f, 0.0000f, 0.0000f);
        cube(catalog_stays, "horst_rear_r__j", "frame", 62, 259, -1.0000f, -5.9250f, 9.6400f, 0.3800f, 0.5000f, 0.8927f, -170.3266f, 0.0000f, 0.0000f);
        cube(swingarm, "horst_pivot__j", "silver", 66, 259, 0.0000f, 2.4500f, 5.0000f, 2.2000f, 0.2500f, 0.2500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_l__j", "black", 72, 259, 1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "dropout_r__j", "black", 76, 259, -1.0000f, -6.0000f, 10.0800f, 0.4000f, 0.7500f, 0.6500f, 0.0000f, 0.0000f, 0.0000f);
        cube(catalog_stays, "caliper_r__j", "brake", 80, 259, -0.8500f, -7.2500f, 9.1000f, 0.4000f, 1.1500f, 0.9000f, 0.0000f, 0.0000f, 0.0000f);
        // END FRAME CATALOG
        return LayerDefinition.create(mesh, 128, 512);
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
     * (vanilla Z-Y-X order). {@code mat} is the texture material tag (see tools/gen_enduro_texture.py).
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
