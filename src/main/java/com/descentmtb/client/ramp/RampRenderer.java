package com.descentmtb.client.ramp;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.ramp.RampMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a {@link RampBlock} as geometry textured with its copycat material: the top surface in 8 strips along
 * the facing axis (exact heights at the strip edges so curves look curved), two side walls, back wall, front
 * wall and the bottom. Sprites/tint come from the material's baked model.
 */
public class RampRenderer<T extends RampBlockEntity> implements BlockEntityRenderer<T> {
    private static final int SLICES = 8;
    private static final Direction[] DIRS = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final RandomSource RANDOM = RandomSource.create(42L);
    /** Unit vector along the facing direction (x,z), indexed by get2DDataValue (S, W, N, E). */
    private static final int[][] ALONG = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    /** Unit lateral vector (x,z) = direction of increasing w. */
    private static final int[][] LAT = {{1, 0}, {0, 1}, {1, 0}, {0, 1}};

    // render-thread scratch (no per-frame allocation in the hot loops)
    private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
    private final int[] tints = new int[6];
    private final float[] q = new float[12];
    private final double[] heights = new double[SLICES + 1];

    public RampRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (!RampBlock.isRamp(st)) return;
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        BlockState mat = be.getMaterial();

        resolveFaces(mat, level, pos);
        if (be instanceof com.descentmtb.trail.TrailSurfaceEntity shaped) {
            renderShaped(shaped, pose, buffers, level == null ? light : LevelRenderer.getLightColor(level,pos.above())); return;
        }

        if (level != null) {
            int above = LevelRenderer.getLightColor(level, pos.above());
            light = LightTexture.pack(Math.max(LightTexture.block(light), LightTexture.block(above)),
                    Math.max(LightTexture.sky(light), LightTexture.sky(above)));
        }

        int facing = st.getValue(RampBlock.FACING).get2DDataValue();
        int start = st.getValue(RampBlock.START), end = st.getValue(RampBlock.END);
        int prof = st.getValue(RampBlock.PROFILE).ordinal();
        boolean flat = start == 0 && end == 0; // lift to 1px so it stays visible (matches the collision slab)
        double[] h = heights;
        for (int i = 0; i <= SLICES; i++) {
            double v = RampMath.heightAtT(start, end, prof, (double) i / SLICES);
            h[i] = flat ? 1.0 / 16.0 : v;
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose last = pose.last();
        int ax = ALONG[facing][0], az = ALONG[facing][1];
        int lx = LAT[facing][0], lz = LAT[facing][1];

        // top surface strips
        for (int i = 0; i < SLICES; i++) {
            double t0 = (double) i / SLICES, t1 = (double) (i + 1) / SLICES;
            double dh = (h[i + 1] - h[i]) * SLICES; // dh/dt (blocks per unit t, constant in the strip)
            float nx = (float) -(dh * ax), ny = 1f, nz = (float) -(dh * az);
            float inv = (float) (1.0 / Math.sqrt(nx * nx + 1 + nz * nz));
            pt(facing, t0, 0, h[i], q, 0);
            pt(facing, t0, 1, h[i], q, 3);
            pt(facing, t1, 1, h[i + 1], q, 6);
            pt(facing, t1, 0, h[i + 1], q, 9);
            emit(vc, last, light, Direction.UP, nx * inv, ny * inv, nz * inv);
        }

        // side walls (w = 0 and w = 1)
        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? -1f : 1f;
            Direction face = Direction.fromDelta(lx * (int) sgn, 0, lz * (int) sgn);
            for (int i = 0; i < SLICES; i++) {
                if (h[i] <= 0 && h[i + 1] <= 0) continue;
                double t0 = (double) i / SLICES, t1 = (double) (i + 1) / SLICES;
                pt(facing, t0, side, 0, q, 0);
                pt(facing, t1, side, 0, q, 3);
                pt(facing, t1, side, h[i + 1], q, 6);
                pt(facing, t0, side, h[i], q, 9);
                emit(vc, last, light, face, lx * sgn, 0f, lz * sgn);
            }
        }

        // back wall (t = 0) and front wall (t = 1)
        if (h[0] > 0) {
            pt(facing, 0, 0, 0, q, 0);
            pt(facing, 0, 1, 0, q, 3);
            pt(facing, 0, 1, h[0], q, 6);
            pt(facing, 0, 0, h[0], q, 9);
            emit(vc, last, light, Direction.fromDelta(-ax, 0, -az), -ax, 0f, -az);
        }
        if (h[SLICES] > 0) {
            pt(facing, 1, 0, 0, q, 0);
            pt(facing, 1, 1, 0, q, 3);
            pt(facing, 1, 1, h[SLICES], q, 6);
            pt(facing, 1, 0, h[SLICES], q, 9);
            emit(vc, last, light, Direction.fromDelta(ax, 0, az), ax, 0f, az);
        }

        // bottom
        q[0] = 0; q[1] = 0; q[2] = 0;
        q[3] = 1; q[4] = 0; q[5] = 0;
        q[6] = 1; q[7] = 0; q[8] = 1;
        q[9] = 0; q[10] = 0; q[11] = 1;
        emit(vc, last, light, Direction.DOWN, 0f, -1f, 0f);
    }

    private void renderShaped(com.descentmtb.trail.TrailSurfaceEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        for (int ix=0; ix<8; ix++) for (int iz=0; iz<8; iz++) {
            double x=ix/8.0, z=iz/8.0, d=.125;
            double[] h={be.height(x,z),be.height(x+d,z),be.height(x+d,z+d),be.height(x,z+d)};
            if (java.util.Arrays.stream(h).max().orElse(0)<.001 || (be.deck()&&java.util.Arrays.stream(h).min().orElse(0)>=1&&be.bottom(x,z)>=1&&be.bottom(x+d,z+d)>=1)) continue;
            double[][] corners={{x,z},{x+d,z},{x+d,z+d},{x,z+d}};
            for(int i=0;i<4;i++){q[i*3]=(float)corners[i][0];q[i*3+1]=(float)h[i];q[i*3+2]=(float)corners[i][1];}
            float nx=(float)-be.slopeX(x+d/2,z+d/2), nz=(float)-be.slopeZ(x+d/2,z+d/2);
            float inv=(float)(1/Math.sqrt(nx*nx+1+nz*nz));
            emit(vc,pose.last(),light,Direction.UP,nx*inv,inv,nz*inv);
            for(int side=0;side<4;side++) {
                if ((side==0&&iz!=0)||(side==1&&ix!=7)||(side==2&&iz!=7)||(side==3&&ix!=0)) continue;
                int next=(side+1)%4;
                double low0=be.bottom(corners[side][0],corners[side][1]),low1=be.bottom(corners[next][0],corners[next][1]);
                double[][] v={{corners[side][0],h[side],corners[side][1]},{corners[next][0],h[next],corners[next][1]},
                        {corners[next][0],low1,corners[next][1]},{corners[side][0],low0,corners[side][1]}};
                for(int i=0;i<4;i++)for(int j=0;j<3;j++)q[i*3+j]=(float)v[i][j];
                Direction face=new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}[side];
                emit(vc,pose.last(),light,face,face.getStepX(),0,face.getStepZ());
            }
            if(be.deck()) {
                for(int i=0;i<4;i++){q[i*3]=(float)corners[i][0];q[i*3+1]=(float)be.bottom(corners[i][0],corners[i][1]);q[i*3+2]=(float)corners[i][1];}
                emit(vc,pose.last(),light,Direction.DOWN,0,-1,0);
            }
        }
        if(be.beam()) {
            float low=(float)be.bottom(.5,.5);
            for(Direction face:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}){
                float ax=face==Direction.WEST?.375f:.625f,az=face==Direction.NORTH?.375f:.625f;
                if(face.getAxis()==Direction.Axis.X){q[0]=ax;q[1]=0;q[2]=.375f;q[3]=ax;q[4]=low;q[5]=.375f;q[6]=ax;q[7]=low;q[8]=.625f;q[9]=ax;q[10]=0;q[11]=.625f;}
                else{q[0]=.375f;q[1]=0;q[2]=az;q[3]=.375f;q[4]=low;q[5]=az;q[6]=.625f;q[7]=low;q[8]=az;q[9]=.625f;q[10]=0;q[11]=az;}
                emit(vc,pose.last(),light,face,face.getStepX(),0,face.getStepZ());
            }
        }
    }

    /** Block-local position for along coordinate t, lateral w, height y. */
    private static void pt(int facing, double t, double w, double y, float[] out, int o) {
        double x, z;
        switch (facing) {
            case 0 -> { x = w; z = t; }
            case 1 -> { x = 1 - t; z = w; }
            case 2 -> { x = w; z = 1 - t; }
            default -> { x = t; z = w; }
        }
        out[o] = (float) x;
        out[o + 1] = (float) y;
        out[o + 2] = (float) z;
    }

    /** Emits the quad in {@link #q}; vertices are re-ordered if needed so it faces along the given normal. */
    private void emit(VertexConsumer vc, PoseStack.Pose pose, int light, Direction face, float nx, float ny, float nz) {
        // Newell normal of the (possibly degenerate) quad
        float gx = 0, gy = 0, gz = 0;
        for (int i = 0; i < 4; i++) {
            int a = i * 3, b = ((i + 1) & 3) * 3;
            gx += (q[a + 1] - q[b + 1]) * (q[a + 2] + q[b + 2]);
            gy += (q[a + 2] - q[b + 2]) * (q[a] + q[b]);
            gz += (q[a] - q[b]) * (q[a + 1] + q[b + 1]);
        }
        if (gx * gx + gy * gy + gz * gz < 1e-10f) return;
        boolean reverse = gx * nx + gy * ny + gz * nz < 0;

        int fi = indexOf(face);
        TextureAtlasSprite sprite = sprites[fi];
        int tint = tints[fi];
        float shade = switch (face) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            default -> 0.6f;
        };
        float r = shade, g = shade, b = shade;
        if (tint != -1) {
            r *= ((tint >> 16) & 255) / 255f;
            g *= ((tint >> 8) & 255) / 255f;
            b *= (tint & 255) / 255f;
        }
        for (int k = 0; k < 4; k++) {
            int idx = (reverse ? 3 - k : k) * 3;
            float x = q[idx], y = q[idx + 1], z = q[idx + 2];
            float u, v;
            switch (face.getAxis()) {
                case Y -> { u = x; v = z; }
                case X -> { u = face.getStepX() > 0 ? 1 - z : z; v = 1 - y; }
                default -> { u = face.getStepZ() > 0 ? x : 1 - x; v = 1 - y; }
            }
            vc.addVertex(pose, x, y, z)
                    .setColor(r, g, b, 1f)
                    .setUv(sprite.getU(u), sprite.getV(v))
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    private static int indexOf(Direction d) {
        for (int i = 0; i < DIRS.length; i++) if (DIRS[i] == d) return i;
        return 0;
    }

    /** Looks up sprite + tint colour per face from the material's baked model. */
    private void resolveFaces(BlockState mat, Level level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getBlockRenderer().getBlockModel(mat);
        for (int i = 0; i < DIRS.length; i++) {
            List<BakedQuad> quads = model.getQuads(mat, DIRS[i], RANDOM, ModelData.EMPTY, null);
            if (quads.isEmpty()) {
                sprites[i] = model.getParticleIcon(ModelData.EMPTY);
                tints[i] = -1;
            } else {
                BakedQuad bq = quads.get(0);
                sprites[i] = bq.getSprite();
                tints[i] = bq.isTinted() ? mc.getBlockColors().getColor(mat, level, pos, bq.getTintIndex()) : -1;
            }
        }
    }
}
