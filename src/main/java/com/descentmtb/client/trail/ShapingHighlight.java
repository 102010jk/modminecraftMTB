package com.descentmtb.client.trail;

import com.descentmtb.trail.ColumnEditor;
import com.descentmtb.trail.ShapingBlockItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Shows which corner / edge / block the next click with a shaping item will move: a small brass
 * diamond on every picked vertex, at the height it currently has.
 */
public final class ShapingHighlight {
    private static final float RADIUS = .16f;
    private static final int COLOR = 0xd8f2c14a;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player.getMainHandItem().getItem() instanceof ShapingBlockItem)) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = hit.getBlockPos();
        if (!(mc.level.getBlockState(pos).getBlock() instanceof com.descentmtb.ramp.RampBlock)) {
            return;   // placing, not sculpting
        }
        var column = ColumnEditor.read(mc.level, pos.getX(), pos.getZ(), pos.getY());
        if (column == null) {
            return;
        }
        Vec3 at = hit.getLocation();
        var vertices = com.descentmtb.trail.ColumnShaper.pickVertices(pos.getX(), pos.getZ(), clamp01(at.x - pos.getX()), clamp01(at.z - pos.getZ()));

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var source = mc.renderBuffers().bufferSource();
        VertexConsumer vc = source.getBuffer(RenderType.debugQuads());
        for (var v : vertices) {
            double height = column.abs()[(v.z() - pos.getZ()) * 2 + (v.x() - pos.getX())];
            diamond(vc, pose, v.x() - camera.x, height + .03 - camera.y, v.z() - camera.z);
        }
        source.endBatch(RenderType.debugQuads());
    }

    private static void diamond(VertexConsumer vc, PoseStack pose, double x, double y, double z) {
        var m = pose.last();
        float cx = (float) x, cy = (float) y, cz = (float) z;
        vc.addVertex(m, cx - RADIUS, cy, cz).setColor(COLOR);
        vc.addVertex(m, cx, cy, cz + RADIUS).setColor(COLOR);
        vc.addVertex(m, cx + RADIUS, cy, cz).setColor(COLOR);
        vc.addVertex(m, cx, cy, cz - RADIUS).setColor(COLOR);
        // visible from below as well
        vc.addVertex(m, cx, cy, cz - RADIUS).setColor(COLOR);
        vc.addVertex(m, cx + RADIUS, cy, cz).setColor(COLOR);
        vc.addVertex(m, cx, cy, cz + RADIUS).setColor(COLOR);
        vc.addVertex(m, cx - RADIUS, cy, cz).setColor(COLOR);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private ShapingHighlight() {}
}
