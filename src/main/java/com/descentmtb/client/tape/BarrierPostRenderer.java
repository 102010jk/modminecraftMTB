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

/**
 * Draws the tapes of a {@link BarrierPostEntity} as red and white striped ribbons that sag a little and are
 * twisted along their length. Every tape is drawn once, from the post with the smaller position; the ribbon is a
 * front and a back strip so each side is lit correctly. Light is blended between the two ends.
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

    public BarrierPostRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BarrierPostEntity post, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        Level level = post.getLevel();
        if (level == null || post.links().isEmpty()) {
            return;
        }
        BlockPos from = post.getBlockPos();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (BlockPos to : post.links()) {
            if (from.asLong() < to.asLong() && level.getBlockState(to).is(ModBlocks.BARRIER_POST.get())) {
                drawTape(consumer, pose, from, to, LevelRenderer.getLightColor(level, from), LevelRenderer.getLightColor(level, to));
            }
        }
    }

    private static void drawTape(VertexConsumer vc, PoseStack pose, BlockPos from, BlockPos to, int lightFrom, int lightTo) {
        double[] a = {.5, TapeCurve.HEIGHT, .5};
        double[] b = {to.getX() - from.getX() + .5, to.getY() - from.getY() + TapeCurve.HEIGHT, to.getZ() - from.getZ() + .5};
        double length = Math.sqrt(Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
        int n = TapeCurve.SEGMENTS;

        double[][] centre = new double[n + 1][];
        double[][] offset = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            centre[i] = TapeCurve.point(a, b, i / (double) n);
        }
        for (int i = 0; i <= n; i++) {
            double[] before = centre[Math.max(i - 1, 0)], after = centre[Math.min(i + 1, n)];
            offset[i] = ribbonOffset(after[0] - before[0], after[1] - before[1], after[2] - before[2], length * i / n);
        }

        var last = pose.last();
        for (int i = 0; i < n; i++) {
            double t0 = i / (double) n, t1 = (i + 1) / (double) n;
            float u0 = (float) (length * t0 / TILE_LENGTH), u1 = (float) (length * t1 / TILE_LENGTH);
            int l0 = TapeCurve.blendLight(lightFrom, lightTo, t0), l1 = TapeCurve.blendLight(lightFrom, lightTo, t1);
            // normal of the strip: along-tape direction x across-tape offset
            double[] along = {centre[i + 1][0] - centre[i][0], centre[i + 1][1] - centre[i][1], centre[i + 1][2] - centre[i][2]};
            float[] normal = cross(along, offset[i]);
            for (int side = 0; side < 2; side++) {
                float k = side == 0 ? 1 : -1; // the back strip faces the other way
                // corners in order: bottom/top at the start, top/bottom at the end (reversed for the back)
                double[][] corner = {
                        {i, -1, u0, l0}, {i, 1, u0, l0}, {i + 1, 1, u1, l1}, {i + 1, -1, u1, l1}};
                for (int c = 0; c < 4; c++) {
                    double[] q = corner[side == 0 ? c : 3 - c];
                    int at = (int) q[0];
                    vertex(vc, last, centre[at], offset[at], q[1], (float) q[2], q[1] < 0 ? 1 : 0, (int) q[3],
                            k * normal[0], k * normal[1], k * normal[2]);
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
        AABB box = new AABB(post.getBlockPos()).expandTowards(0, 1, 0);
        for (BlockPos other : post.links()) {
            box = box.minmax(new AABB(other).expandTowards(0, 1, 0));
        }
        return box;
    }

    @Override
    public boolean shouldRenderOffScreen(BarrierPostEntity post) {
        return true;
    }
}
