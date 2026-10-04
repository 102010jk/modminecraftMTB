package com.descentmtb.client.trail;

import com.descentmtb.trail.ShapePresets;
import com.descentmtb.trail.ShapeToolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Previews the next click with the Trail Shaper: a small brass diamond on every corner the mode moves, drawn at
 * the height that corner will have afterwards. The numbers come from {@link ShapePresets}, the same code the
 * server runs, so the preview matches the result (neighbouring blocks are only read).
 */
public final class ShapingHighlight {
    private static final float RADIUS = .16f;
    private static final int COLOR = 0xd8f2c14a;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ShapeToolItem.usable(mc.player.getMainHandItem())) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = hit.getBlockPos();
        var mode = ShapeToolItem.mode(mc.player.getMainHandItem());
        var plan = ShapePresets.plan(mc.level, pos, mode, hit.getLocation(), mc.player.getDirection(), mc.player.isShiftKeyDown());
        if (plan == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var source = mc.renderBuffers().bufferSource();
        VertexConsumer vc = source.getBuffer(RenderType.debugQuads());
        for (int corner : plan.affected()) {
            double x = pos.getX() + corner % 2, z = pos.getZ() + corner / 2;
            diamond(vc, pose, x - camera.x, plan.newAbs()[corner] + .03 - camera.y, z - camera.z);
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

    private ShapingHighlight() {}
}
