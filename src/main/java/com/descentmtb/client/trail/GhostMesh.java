package com.descentmtb.client.trail;

import com.descentmtb.network.TrailPreviewPayload;
import com.descentmtb.trail.TrailMath;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The translucent preview of a pending trail edit, uploaded to the GPU once when the preview changes and then
 * drawn with a single call per frame (it used to be rebuilt vertex by vertex every frame, with a Sable lookup
 * per cell, which made big previews unplayable).
 */
final class GhostMesh {
    private static final int ADD_COLOR = 0x6056d9c6, REMOVE_COLOR = 0x50ec755e;

    private final VertexBuffer buffer;
    private final double originX, originY, originZ;
    private final double minX, minY, minZ, maxX, maxY, maxZ;

    private GhostMesh(VertexBuffer buffer, double ox, double oy, double oz, double[] bounds) {
        this.buffer = buffer;
        this.originX = ox;
        this.originY = oy;
        this.originZ = oz;
        this.minX = bounds[0];
        this.minY = bounds[1];
        this.minZ = bounds[2];
        this.maxX = bounds[3];
        this.maxY = bounds[4];
        this.maxZ = bounds[5];
    }

    static GhostMesh build(List<TrailPreviewPayload.Cell> cells, float partialTick) {
        if (cells.isEmpty()) {
            return null;
        }
        var first = cells.get(0).pos();
        double ox = first.getX(), oy = first.getY(), oz = first.getZ();
        double[] bounds = {1e18, 1e18, 1e18, -1e18, -1e18, -1e18};
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int vertices = 0;
        for (var cell : cells) {
            ClientSubLevelAccess sub = SableCompanion.INSTANCE.getContainingClient(cell.pos());
            vertices += addCell(builder, cell, sub, partialTick, ox, oy, oz, bounds);
        }
        if (vertices == 0) {
            return null;
        }
        MeshData mesh = builder.buildOrThrow();
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return new GhostMesh(vb, ox, oy, oz, bounds);
    }

    private static int addCell(BufferBuilder b, TrailPreviewPayload.Cell c, ClientSubLevelAccess sub, float pt,
                               double ox, double oy, double oz, double[] bounds) {
        double x = c.pos().getX(), y = c.pos().getY() + .01, z = c.pos().getZ();
        int color = c.remove() ? REMOVE_COLOR : ADD_COLOR;
        double[] h = {c.nw(), c.ne(), c.sw(), c.se()};
        boolean outside = false;
        for (double v : h) {
            outside |= v < 0 || v > 1;
        }
        int slices = outside ? 4 : 1;
        int count = 0;
        double d = 1.0 / slices;
        for (int ix = 0; ix < slices; ix++) {
            for (int iz = 0; iz < slices; iz++) {
                double a = ix * d, bz = iz * d;
                double h0 = clip(TrailMath.bilerp(h, a, bz)), h1 = clip(TrailMath.bilerp(h, a, bz + d));
                double h2 = clip(TrailMath.bilerp(h, a + d, bz + d)), h3 = clip(TrailMath.bilerp(h, a + d, bz));
                if (h0 + h1 + h2 + h3 < .001) {
                    continue;
                }
                count += vertex(b, sub, pt, x + a, y + h0, z + bz, color, ox, oy, oz, bounds);
                count += vertex(b, sub, pt, x + a, y + h1, z + bz + d, color, ox, oy, oz, bounds);
                count += vertex(b, sub, pt, x + a + d, y + h2, z + bz + d, color, ox, oy, oz, bounds);
                count += vertex(b, sub, pt, x + a + d, y + h3, z + bz, color, ox, oy, oz, bounds);
            }
        }
        return count;
    }

    private static int vertex(BufferBuilder b, ClientSubLevelAccess sub, float pt, double x, double y, double z, int color,
                              double ox, double oy, double oz, double[] bounds) {
        Vec3 p = sub == null ? new Vec3(x, y, z) : sub.renderPose(pt).transformPosition(new Vec3(x, y, z));
        b.addVertex((float) (p.x - ox), (float) (p.y - oy), (float) (p.z - oz)).setColor(color);
        bounds[0] = Math.min(bounds[0], p.x);
        bounds[1] = Math.min(bounds[1], p.y);
        bounds[2] = Math.min(bounds[2], p.z);
        bounds[3] = Math.max(bounds[3], p.x);
        bounds[4] = Math.max(bounds[4], p.y);
        bounds[5] = Math.max(bounds[5], p.z);
        return 1;
    }

    private static double clip(double h) {
        return Math.max(0, Math.min(1, h));
    }

    void draw(RenderLevelStageEvent event) {
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix())
                .translate((float) (originX - camera.x), (float) (originY - camera.y), (float) (originZ - camera.z));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        buffer.bind();
        buffer.drawWithShader(modelView, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // one outline around the whole preview instead of twelve lines per cell
        var source = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, source.getBuffer(RenderType.lines()), minX, minY, minZ, maxX, maxY, maxZ, .45f, .95f, .85f, 1f);
        source.endBatch(RenderType.lines());
        pose.popPose();
    }

    void close() {
        buffer.close();
    }
}
