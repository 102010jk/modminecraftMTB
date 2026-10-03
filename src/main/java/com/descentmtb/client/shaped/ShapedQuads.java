package com.descentmtb.client.shaped;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampMath;
import com.descentmtb.ramp.ShapeKey;
import com.descentmtb.trail.TrailMath;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the baked quads of a ramp or trail surface. Runs on chunk-meshing threads, once per distinct shape
 * (results are cached by {@link ShapedBakedModel}); keep it free of shared mutable state.
 */
final class ShapedQuads {
    /** Per face sprite and tint index of the copycat material. */
    record Faces(TextureAtlasSprite[] sprite, int[] tint) {
        int index(Direction d) {
            return d.ordinal();   // Direction order: DOWN UP NORTH SOUTH WEST EAST
        }
    }

    private static final int[][] ALONG = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private static final int[][] LAT = {{1, 0}, {0, 1}, {1, 0}, {0, 1}};
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final Faces faces;
    private final List<BakedQuad> out = new ArrayList<>();
    private final float[] q = new float[12];

    private ShapedQuads(Faces faces) {
        this.faces = faces;
    }

    static List<BakedQuad> build(BlockState state, ShapeKey key, Faces faces) {
        ShapedQuads b = new ShapedQuads(faces);
        if (state.getBlock() instanceof com.descentmtb.trail.TrailSurfaceBlock) {
            b.surface(key);
        } else {
            b.ramp(state);
        }
        return List.copyOf(b.out);
    }

    // ------------------------------------------------------------------ ramp

    private void ramp(BlockState st) {
        int facing = st.getValue(RampBlock.FACING).get2DDataValue();
        int start = st.getValue(RampBlock.START), end = st.getValue(RampBlock.END);
        int profile = st.getValue(RampBlock.PROFILE).ordinal();
        boolean flat = start == 0 && end == 0;
        int slices = profile == RampBlock.Profile.LINEAR.ordinal() ? 1 : 8;
        double[] h = new double[slices + 1];
        for (int i = 0; i <= slices; i++) {
            h[i] = flat ? 1.0 / 16 : RampMath.heightAtT(start, end, profile, (double) i / slices);
        }
        int ax = ALONG[facing][0], az = ALONG[facing][1];
        int lx = LAT[facing][0], lz = LAT[facing][1];

        for (int i = 0; i < slices; i++) {
            double t0 = (double) i / slices, t1 = (double) (i + 1) / slices;
            double slope = (h[i + 1] - h[i]) * slices;
            pt(facing, t0, 0, h[i], 0);
            pt(facing, t0, 1, h[i], 3);
            pt(facing, t1, 1, h[i + 1], 6);
            pt(facing, t1, 0, h[i + 1], 9);
            emit(Direction.UP, (float) -(slope * ax), 1f, (float) -(slope * az));
        }
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1f : 1f;
            Direction face = Direction.fromDelta(lx * (int) sign, 0, lz * (int) sign);
            for (int i = 0; i < slices; i++) {
                if (h[i] <= 0 && h[i + 1] <= 0) {
                    continue;
                }
                double t0 = (double) i / slices, t1 = (double) (i + 1) / slices;
                pt(facing, t0, side, 0, 0);
                pt(facing, t1, side, 0, 3);
                pt(facing, t1, side, h[i + 1], 6);
                pt(facing, t0, side, h[i], 9);
                emit(face, lx * sign, 0f, lz * sign);
            }
        }
        if (h[0] > 0) {
            wall(facing, 0, h[0], Direction.fromDelta(-ax, 0, -az));
        }
        if (h[slices] > 0) {
            wall(facing, 1, h[slices], Direction.fromDelta(ax, 0, az));
        }
        bottom();
    }

    private void wall(int facing, double t, double height, Direction face) {
        pt(facing, t, 0, 0, 0);
        pt(facing, t, 1, 0, 3);
        pt(facing, t, 1, height, 6);
        pt(facing, t, 0, height, 9);
        emit(face, face.getStepX(), 0f, face.getStepZ());
    }

    private void bottom() {
        set(0, 0, 0, 0);
        set(3, 1, 0, 0);
        set(6, 1, 0, 1);
        set(9, 0, 0, 1);
        emit(Direction.DOWN, 0f, -1f, 0f);
    }

    /** Block-local position for along coordinate t, lateral w, height y. */
    private void pt(int facing, double t, double w, double y, int o) {
        double x, z;
        switch (facing) {
            case 0 -> { x = w; z = t; }
            case 1 -> { x = 1 - t; z = w; }
            case 2 -> { x = w; z = 1 - t; }
            default -> { x = t; z = w; }
        }
        set(o, x, y, z);
    }

    private void set(int o, double x, double y, double z) {
        q[o] = (float) x;
        q[o + 1] = (float) y;
        q[o + 2] = (float) z;
    }

    // ------------------------------------------------------------------ trail surface

    private static double bilerp(double[] c, double x, double z) {
        return TrailMath.bilerp(c, x, z);
    }

    private static double height(double[] c, double x, double z) {
        return Math.max(0, Math.min(1, bilerp(c, x, z)));
    }

    private static double bottomOf(double[] c, boolean deck, double x, double z) {
        return deck ? Math.max(0, Math.min(1, bilerp(c, x, z) - .14)) : 0;
    }

    private void surface(ShapeKey key) {
        double[] c = key.corners();
        boolean deck = key.deck();
        // A plane that stays inside the block and is not twisted is a single quad; anything else is cut into 8x8
        // cells so the clamping at the block's top / bottom follows the real clipped surface.
        double twist = Math.abs(c[0] + c[3] - c[1] - c[2]);
        boolean inside = true;
        for (double v : c) {
            inside &= v > .001 && v < .999;
        }
        int n = inside && twist < .04 && !deck ? 1 : 8;
        double d = 1.0 / n;

        for (int ix = 0; ix < n; ix++) {
            for (int iz = 0; iz < n; iz++) {
                double x = ix * d, z = iz * d;
                double[] h = {height(c, x, z), height(c, x + d, z), height(c, x + d, z + d), height(c, x, z + d)};
                double max = Math.max(Math.max(h[0], h[1]), Math.max(h[2], h[3]));
                double min = Math.min(Math.min(h[0], h[1]), Math.min(h[2], h[3]));
                if (max < .001 || (deck && min >= 1 && bottomOf(c, true, x, z) >= 1 && bottomOf(c, true, x + d, z + d) >= 1)) {
                    continue;
                }
                double[][] corner = {{x, z}, {x + d, z}, {x + d, z + d}, {x, z + d}};
                for (int i = 0; i < 4; i++) {
                    set(i * 3, corner[i][0], h[i], corner[i][1]);
                }
                double mx = x + d / 2, mz = z + d / 2;
                double raw = bilerp(c, mx, mz);
                float sx = 0, sz = 0;
                if (raw > 0 && raw < 1) {
                    sx = (float) -((c[1] - c[0]) * (1 - mz) + (c[3] - c[2]) * mz);
                    sz = (float) -((c[2] - c[0]) * (1 - mx) + (c[3] - c[1]) * mx);
                }
                emit(Direction.UP, sx, 1f, sz);

                for (int side = 0; side < 4; side++) {
                    boolean edge = (side == 0 && iz == 0) || (side == 1 && ix == n - 1) || (side == 2 && iz == n - 1) || (side == 3 && ix == 0);
                    if (!edge) {
                        continue;
                    }
                    int next = (side + 1) % 4;
                    double low0 = bottomOf(c, deck, corner[side][0], corner[side][1]);
                    double low1 = bottomOf(c, deck, corner[next][0], corner[next][1]);
                    set(0, corner[side][0], h[side], corner[side][1]);
                    set(3, corner[next][0], h[next], corner[next][1]);
                    set(6, corner[next][0], low1, corner[next][1]);
                    set(9, corner[side][0], low0, corner[side][1]);
                    Direction face = SIDES[side];
                    emit(face, face.getStepX(), 0f, face.getStepZ());
                }
                if (deck) {
                    for (int i = 0; i < 4; i++) {
                        set(i * 3, corner[i][0], bottomOf(c, true, corner[i][0], corner[i][1]), corner[i][1]);
                    }
                    emit(Direction.DOWN, 0f, -1f, 0f);
                }
            }
        }
        if (key.beam()) {
            beam(bottomOf(c, deck, .5, .5));
        }
    }

    private void beam(double low) {
        for (Direction face : SIDES) {
            float ax = face == Direction.WEST ? .375f : .625f, az = face == Direction.NORTH ? .375f : .625f;
            if (face.getAxis() == Direction.Axis.X) {
                set(0, ax, 0, .375);
                set(3, ax, low, .375);
                set(6, ax, low, .625);
                set(9, ax, 0, .625);
            } else {
                set(0, .375, 0, az);
                set(3, .375, low, az);
                set(6, .625, low, az);
                set(9, .625, 0, az);
            }
            emit(face, face.getStepX(), 0f, face.getStepZ());
        }
    }

    // ------------------------------------------------------------------ quad emission

    /** Emits the quad in {@link #q}; the vertex order is flipped when needed so it faces along the given normal. */
    private void emit(Direction face, float nx, float ny, float nz) {
        float gx = 0, gy = 0, gz = 0;   // Newell normal of the (possibly degenerate) quad
        for (int i = 0; i < 4; i++) {
            int a = i * 3, b = ((i + 1) & 3) * 3;
            gx += (q[a + 1] - q[b + 1]) * (q[a + 2] + q[b + 2]);
            gy += (q[a + 2] - q[b + 2]) * (q[a] + q[b]);
            gz += (q[a] - q[b]) * (q[a + 1] + q[b + 1]);
        }
        if (gx * gx + gy * gy + gz * gz < 1e-10f) {
            return;
        }
        boolean reverse = gx * nx + gy * ny + gz * nz < 0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;

        int fi = faces.index(face);
        TextureAtlasSprite sprite = faces.sprite()[fi];
        var vc = new QuadBakingVertexConsumer();
        vc.setSprite(sprite);
        vc.setDirection(face);
        vc.setTintIndex(faces.tint()[fi]);
        vc.setShade(true);
        vc.setHasAmbientOcclusion(true);
        for (int k = 0; k < 4; k++) {
            int idx = (reverse ? 3 - k : k) * 3;
            float x = q[idx], y = q[idx + 1], z = q[idx + 2];
            float u, v;
            switch (face.getAxis()) {
                case Y -> { u = x; v = z; }
                case X -> { u = face.getStepX() > 0 ? 1 - z : z; v = 1 - y; }
                default -> { u = face.getStepZ() > 0 ? x : 1 - x; v = 1 - y; }
            }
            vc.addVertex(x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(u), sprite.getV(v))
                    .setNormal(nx, ny, nz);
        }
        out.add(vc.bakeQuad());
    }
}
