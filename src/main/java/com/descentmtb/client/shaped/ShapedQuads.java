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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the baked quads of a ramp or trail surface. Runs on chunk-meshing threads, once per distinct shape
 * (results are cached by {@link ShapedBakedModel}); keep it free of shared mutable state.
 */
final class ShapedQuads {
    /**
     * The quads of one shape. {@code unculled} are always drawn (sloped top surface and interior features);
     * {@code up}/{@code down} are faces on the block's horizontal boundaries. Side/end walls are directional,
     * so vanilla can cull them against solid ground; model data removes only walls actually covered by the
     * adjoining shaped block. Raised deck undersides remain unculled.
     */
    record Built(
            List<BakedQuad> unculled,
            List<BakedQuad> up,
            List<BakedQuad> down,
            List<BakedQuad> north,
            List<BakedQuad> south,
            List<BakedQuad> west,
            List<BakedQuad> east
    ) {
        List<BakedQuad> forSide(@Nullable Direction side) {
            if (side == null) {
                return unculled;
            }
            return switch (side) {
                case DOWN -> down;
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
                case UP -> up;
            };
        }
    }

    /** Per face sprite and tint index of the copycat material. */
    record Faces(TextureAtlasSprite[] sprite, int[] tint) {
        int index(Direction d) {
            return d.ordinal();   // Direction order: DOWN UP NORTH SOUTH WEST EAST
        }
    }

    private static final int[][] ALONG = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    private static final int[][] LAT = {{1, 0}, {0, 1}, {1, 0}, {0, 1}};
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private Faces faces;
    private final List<BakedQuad> unculled = new ArrayList<>();
    private final List<BakedQuad> up = new ArrayList<>();
    private final List<BakedQuad> down = new ArrayList<>();
    private final List<BakedQuad> north = new ArrayList<>();
    private final List<BakedQuad> south = new ArrayList<>();
    private final List<BakedQuad> west = new ArrayList<>();
    private final List<BakedQuad> east = new ArrayList<>();
    private final float[] q = new float[12];

    private ShapedQuads(Faces faces) {
        this.faces = faces;
    }

    static Built build(BlockState state, ShapeKey key, Faces faces, Faces overlay) {
        ShapedQuads b = new ShapedQuads(faces);
        if (state.getBlock() instanceof com.descentmtb.trail.TrailSurfaceBlock) {
            b.surface(key);
        } else {
            b.ramp(state);
        }
        if (overlay != null && key.overlay() != 0) {
            b.faces = overlay;
            b.overlay(key);
        }
        return new Built(
                List.copyOf(b.unculled),
                List.copyOf(b.up),
                List.copyOf(b.down),
                List.copyOf(b.north),
                List.copyOf(b.south),
                List.copyOf(b.west),
                List.copyOf(b.east)
        );
    }

    private void overlay(ShapeKey key) {
        var c=key.corners();double d=1.0/16;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            double[][] points={{x*d,z*d},{(x+1)*d,z*d},{(x+1)*d,(z+1)*d},{x*d,(z+1)*d}};
            double[] base=new double[4],top=new double[4];boolean visible=false;
            for(int i=0;i<4;i++){
                double px=points[i][0],pz=points[i][1];base[i]=height(c,px,pz);
                top[i]=Math.max(0,Math.min(1,bilerp(c,px,pz)+com.descentmtb.trail.OverlayMath.bump(key.overlay(),px,pz)));
                visible|=top[i]>base[i]+.0001;set(i*3,px,top[i],pz);
            }
            if(!visible)continue;
            float sx=(float)((top[1]+top[2]-top[0]-top[3])/(2*d)),sz=(float)((top[2]+top[3]-top[0]-top[1])/(2*d));
            emit(unculled, Direction.UP,-sx,1,-sz);
            for(int side=0;side<4;side++){
                int next=(side+1)%4;
                set(0,points[side][0],base[side],points[side][1]);set(3,points[next][0],base[next],points[next][1]);
                set(6,points[next][0],top[next],points[next][1]);set(9,points[side][0],top[side],points[side][1]);
                var face=SIDES[side];emit(unculled, face,face.getStepX(),0,face.getStepZ());
            }
        }
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
            emit(unculled, Direction.UP, (float) -(slope * ax), 1f, (float) -(slope * az));
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
                emitToFace(face, lx * sign, 0f, lz * sign);
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
        emitToFace(face, face.getStepX(), 0f, face.getStepZ());
    }

    /** The full bottom face, emitted as a cullable {@link Direction#DOWN} quad (see {@link Built}). */
    private void bottom() {
        set(0, 0, 0, 0);
        set(3, 1, 0, 0);
        set(6, 1, 0, 1);
        set(9, 0, 0, 1);
        emit(down, Direction.DOWN, 0f, -1f, 0f);
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
        if (!deck && c[0] >= 1 && c[1] >= 1 && c[2] >= 1 && c[3] >= 1) {
            // These are real full dirt layers, including the final layer of an integer-height flat.
            // Never omit their exposed top or walls. Six quads instead of 8x8 cells.
            bottom();
            set(0,0,1,0);set(3,1,1,0);set(6,1,1,1);set(9,0,1,1);
            emit(up,Direction.UP,0,1,0);
            double[][] p={{0,0},{1,0},{1,1},{0,1}};
            for (int side=0;side<4;side++) {
                int next=(side+1)%4;
                set(0,p[side][0],0,p[side][1]);set(3,p[next][0],0,p[next][1]);
                set(6,p[next][0],1,p[next][1]);set(9,p[side][0],1,p[side][1]);
                Direction face=SIDES[side];emitToFace(face,face.getStepX(),0,face.getStepZ());
            }
            return;
        }
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
                if (!deck) {
                    soilCell(c, corner, n, ix, iz);
                    continue;
                }
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
                emit(min >= 1 ? up : unculled, Direction.UP, sx, 1f, sz);

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
                    emitToFace(face, face.getStepX(), 0f, face.getStepZ());
                }
                if (deck) {
                    boolean boundary = true;
                    for (int i = 0; i < 4; i++) {
                        double low = bottomOf(c, true, corner[i][0], corner[i][1]);
                        boundary &= low == 0;
                        set(i * 3, corner[i][0], low, corner[i][1]);
                    }
                    emit(boundary ? down : unculled, Direction.DOWN, 0f, -1f, 0f);
                }
            }
        }
        if (!deck) {
            // solid dirt/stone layers are closed underneath too: a jump or line built over air (or seen from below)
            // must not be hollow. Cullable DOWN quad, so it costs nothing when a block lies below.
            bottom();
        }
        if (key.beam()) {
            beam(bottomOf(c, deck, .5, .5));
        }
    }

    /**
     * One cell of a solid soil layer. The surface is CLIPPED to this block's height range instead of clamped: where
     * it dips below the block's floor it belongs to the block underneath and is left out (clamping it to 0 drew a flat
     * shelf sticking out over the real slope at every block boundary of a sloped trail), and where it rises above the
     * ceiling the cell is capped flat at 1.
     */
    private void soilCell(double[] c, double[][] corner, int n, int ix, int iz) {
        List<double[]> poly = new ArrayList<>(4);
        for (double[] p : corner) poly.add(new double[]{p[0], p[1], bilerp(c, p[0], p[1])});
        boolean allHigh = true;
        for (double[] p : poly) allHigh &= p[2] >= 1;

        double mx = (corner[0][0] + corner[2][0]) / 2, mz = (corner[0][1] + corner[2][1]) / 2;
        float sx = (float) -((c[1] - c[0]) * (1 - mz) + (c[3] - c[2]) * mz);
        float sz = (float) -((c[2] - c[0]) * (1 - mx) + (c[3] - c[1]) * mx);
        List<double[]> slope = clip(clip(poly, 0, true), 1, false);
        emitPoly(slope, false, unculled, Direction.UP, sx, 1f, sz);
        List<double[]> cap = clip(poly, 1, true);
        emitPoly(cap, true, allHigh ? up : unculled, Direction.UP, 0f, 1f, 0f);

        for (int side = 0; side < 4; side++) {
            boolean edge = (side == 0 && iz == 0) || (side == 1 && ix == n - 1) || (side == 2 && iz == n - 1) || (side == 3 && ix == 0);
            if (!edge) continue;
            double[] a = poly.get(side), b = poly.get((side + 1) % 4);
            // top edge of the wall: the surface along this boundary, clipped to the floor, flattened at the ceiling
            List<double[]> top = new ArrayList<>(4);
            top.add(a);
            if ((a[2] - 1) * (b[2] - 1) < 0) top.add(lerp(a, b, (1 - a[2]) / (b[2] - a[2])));
            top.add(b);
            top = clipLine(top);
            if (top.size() < 2) continue;
            List<double[]> wall = new ArrayList<>(6);
            for (double[] p : top) wall.add(new double[]{p[0], p[1], Math.min(1, p[2])});
            double[] last = top.get(top.size() - 1), first = top.get(0);
            wall.add(new double[]{last[0], last[1], 0});
            wall.add(new double[]{first[0], first[1], 0});
            Direction face = SIDES[side];
            List<BakedQuad> target = switch (face) {
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                default -> east;
            };
            emitPoly(wall, false, target, face, face.getStepX(), 0f, face.getStepZ());
        }
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Sutherland-Hodgman: keeps the part of a convex polygon (x, z, height) with height >= k (or <= k). */
    private static List<double[]> clip(List<double[]> in, double k, boolean above) {
        List<double[]> out = new ArrayList<>(in.size() + 2);
        for (int i = 0; i < in.size(); i++) {
            double[] p = in.get(i), q = in.get((i + 1) % in.size());
            double dp = above ? p[2] - k : k - p[2], dq = above ? q[2] - k : k - q[2];
            if (dp >= 0) out.add(p);
            if ((dp >= 0) != (dq >= 0)) out.add(lerp(p, q, dp / (dp - dq)));
        }
        return out;
    }

    /** Keeps the part of a polyline (x, z, height) at or above height 0. */
    private static List<double[]> clipLine(List<double[]> in) {
        List<double[]> out = new ArrayList<>(in.size() + 1);
        for (int i = 0; i < in.size(); i++) {
            double[] p = in.get(i);
            if (p[2] >= 0) out.add(p);
            if (i + 1 < in.size()) {
                double[] q = in.get(i + 1);
                if ((p[2] >= 0) != (q[2] >= 0)) out.add(lerp(p, q, p[2] / (p[2] - q[2])));
            }
        }
        // a boundary touching the floor only in a point makes no wall
        double maxH = 0;
        for (double[] p : out) maxH = Math.max(maxH, p[2]);
        return maxH <= .0005 ? List.of() : out;
    }

    /** Emits a convex polygon (x, z, height) as quads; {@code flat} puts every vertex at height 1. */
    private void emitPoly(List<double[]> poly, boolean flat, List<BakedQuad> target, Direction face, float nx, float ny, float nz) {
        int m = poly.size();
        if (m < 3) return;
        for (int i = 1; i + 1 < m; i += 2) {
            double[] p0 = poly.get(0), p1 = poly.get(i), p2 = poly.get(i + 1), p3 = i + 2 < m ? poly.get(i + 2) : p2;
            double[][] v = {p0, p1, p2, p3};
            for (int k = 0; k < 4; k++) set(k * 3, v[k][0], flat ? 1 : v[k][2], v[k][1]);
            emit(target, face, nx, ny, nz);
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
            emit(unculled, face, face.getStepX(), 0f, face.getStepZ());
        }
    }

    // ------------------------------------------------------------------ quad emission

    private void emitToFace(Direction face, float nx, float ny, float nz) {
        List<BakedQuad> target = switch (face) {
            case DOWN -> down;
            case NORTH -> north;
            case SOUTH -> south;
            case WEST -> west;
            case EAST -> east;
            default -> unculled;
        };
        emit(target, face, nx, ny, nz);
    }

    /** Emits the quad in {@link #q}; the vertex order is flipped when needed so it faces along the given normal. */
    private void emit(List<BakedQuad> target, Direction face, float nx, float ny, float nz) {
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
        target.add(vc.bakeQuad());
    }
}
