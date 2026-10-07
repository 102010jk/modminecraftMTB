package com.descentmtb.client.tape;

import com.descentmtb.DescentMtb;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.tape.BarrierPostEntity;
import com.descentmtb.tape.TapeCurve;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the tapes of a {@link BarrierPostEntity} as red and white striped ribbons that sag a little and are
 * twisted along their length. Every tape is drawn once, from the post with the smaller position; the ribbon is a
 * front and a back strip so each side is lit correctly. Light is blended between the two ends.
 *
 * <p>The geometry of the tapes and the bounding box are computed once per post and rebuilt only when its links
 * change; a frame only blends the light and writes the vertices.
 */
public final class BarrierPostRenderer implements BlockEntityRenderer<BarrierPostEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/entity/trail_tape.png");
    /** Height of the ribbon in blocks. */
    private static final double WIDTH = 0.12;
    /** The texture (16 x 4 pixels) repeats once per this many blocks of tape. */
    private static final double TILE_LENGTH = 0.48;
    /** Largest tilt of the ribbon from vertical, in radians, and how quickly it turns along the tape. */
    private static final double TWIST = 0.55, TWIST_PER_BLOCK = 1.9;

    /**
     * Everything about one tape that does not change with the light: the sagging centre line, the ribbon offsets,
     * the strip normals and the texture coordinates along the tape.
     */
    private record Tape(BlockPos to, boolean owned, double[][] centre, double[][] offset, float[][] normal, float[] u) {}

    /** The tapes of one post and the box around them, valid while the post's links stay the same. */
    private record Shape(List<BlockPos> links, List<Tape> tapes, AABB box) {}

    private final Map<BarrierPostEntity, Shape> cache = new WeakHashMap<>();

    public BarrierPostRenderer(BlockEntityRendererProvider.Context context) {}

    /** The cached shape of the post; rebuilt when its links changed. */
    private Shape shape(BarrierPostEntity post) {
        Shape shape = cache.get(post);
        if (shape == null || !shape.links.equals(post.links())) {
            shape = build(post.getBlockPos(), List.copyOf(post.links()));
            cache.put(post, shape);
        }
        return shape;
    }

    private static Shape build(BlockPos from, List<BlockPos> links) {
        List<Tape> tapes = new ArrayList<>(links.size());
        AABB box = new AABB(from).expandTowards(0, 1, 0);
        for (BlockPos to : links) {
            box = box.minmax(new AABB(to).expandTowards(0, 1, 0));
            // every tape is drawn once, from the post with the smaller position; the other post keeps a copy for
            // when that one's chunk is not loaded on this client (the tape must not vanish with it)
            tapes.add(buildTape(from, to, from.asLong() < to.asLong()));
        }
        return new Shape(links, List.copyOf(tapes), box);
    }

    @Override
    public void render(BarrierPostEntity post, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = post.getLevel();
        if (level == null || post.links().isEmpty()) {
            return;
        }
        BlockPos from = post.getBlockPos();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (Tape tape : shape(post).tapes) {
            boolean otherLoaded = level.isLoaded(tape.to);
            if (tape.owned ? !otherLoaded || level.getBlockState(tape.to).is(ModBlocks.BARRIER_POST.get()) : !otherLoaded) {
                int lightFrom = LevelRenderer.getLightColor(level, from);
                drawTape(consumer, pose, tape, lightFrom, otherLoaded ? LevelRenderer.getLightColor(level, tape.to) : lightFrom);
            }
        }
    }

    private static Tape buildTape(BlockPos from, BlockPos to, boolean owned) {
        double[] a = {.5, TapeCurve.HEIGHT, .5};
        double[] b = {to.getX() - from.getX() + .5, to.getY() - from.getY() + TapeCurve.HEIGHT, to.getZ() - from.getZ() + .5};
        double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int n = TapeCurve.SEGMENTS;

        double[][] centre = new double[n + 1][];
        double[][] offset = new double[n + 1][];
        float[] u = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            centre[i] = TapeCurve.point(a, b, i / (double) n);
            u[i] = (float) (length * (i / (double) n) / TILE_LENGTH);
        }
        for (int i = 0; i <= n; i++) {
            double[] before = centre[Math.max(i - 1, 0)], after = centre[Math.min(i + 1, n)];
            offset[i] = ribbonOffset(after[0] - before[0], after[1] - before[1], after[2] - before[2], length * i / n);
        }
        // normal of each strip: along-tape direction x across-tape offset
        float[][] normal = new float[n][];
        for (int i = 0; i < n; i++) {
            double[] along = {centre[i + 1][0] - centre[i][0], centre[i + 1][1] - centre[i][1], centre[i + 1][2] - centre[i][2]};
            normal[i] = cross(along, offset[i]);
        }
        return new Tape(to.immutable(), owned, centre, offset, normal, u);
    }

    private static void drawTape(VertexConsumer vc, PoseStack pose, Tape tape, int lightFrom, int lightTo) {
        var last = pose.last();
        int n = TapeCurve.SEGMENTS;
        for (int i = 0; i < n; i++) {
            int l0 = TapeCurve.blendLight(lightFrom, lightTo, i / (double) n);
            int l1 = TapeCurve.blendLight(lightFrom, lightTo, (i + 1) / (double) n);
            float[] normal = tape.normal[i];
            for (int side = 0; side < 2; side++) {
                float k = side == 0 ? 1 : -1; // the back strip faces the other way
                // corners in order: bottom/top at the start, top/bottom at the end (reversed for the back)
                for (int c = 0; c < 4; c++) {
                    int corner = side == 0 ? c : 3 - c;
                    boolean atEnd = corner >= 2;
                    int at = atEnd ? i + 1 : i;
                    double sign = corner == 0 || corner == 3 ? -1 : 1;
                    vertex(vc, last, tape.centre[at], tape.offset[at], sign, tape.u[at], sign < 0 ? 1 : 0,
                            atEnd ? l1 : l0, k * normal[0], k * normal[1], k * normal[2]);
                }
            }
        }
    }

    /** Half-height vector across the ribbon: mostly up, tilted sideways by an angle that wanders along the tape. */
    private static double[] ribbonOffset(double dx, double dy, double dz, double distance) {
        double sx = dz, sz = -dx; // horizontal, perpendicular to the tape
        double len = Math.hypot(sx, sz);
        if (len < 1e-6) {
            sx = 1;
            sz = 0;
        } else {
            sx /= len;
            sz /= len;
        }
        double angle = TWIST * Math.sin(distance * TWIST_PER_BLOCK);
        double up = Math.cos(angle) * WIDTH / 2, side = Math.sin(angle) * WIDTH / 2;
        return new double[]{sx * side, up, sz * side};
    }

    private static float[] cross(double[] u, double[] v) {
        double x = u[1] * v[2] - u[2] * v[1], y = u[2] * v[0] - u[0] * v[2], z = u[0] * v[1] - u[1] * v[0];
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1e-9 ? new float[]{0, 1, 0} : new float[]{(float) (x / len), (float) (y / len), (float) (z / len)};
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, double[] centre, double[] offset, double sign,
                               float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, (float) (centre[0] + offset[0] * sign), (float) (centre[1] + offset[1] * sign), (float) (centre[2] + offset[2] * sign))
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    /** The box around the post and every tape drawn from it, so the tapes are not culled with their post off screen. */
    @Override
    public AABB getRenderBoundingBox(BarrierPostEntity post) {
        return shape(post).box;
    }

    @Override
    public boolean shouldRenderOffScreen(BarrierPostEntity post) {
        return true;
    }
}
