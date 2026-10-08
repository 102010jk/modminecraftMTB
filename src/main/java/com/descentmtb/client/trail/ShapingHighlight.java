package com.descentmtb.client.trail;

import com.descentmtb.trail.ClipboardMath;
import com.descentmtb.trail.CursorSettings;
import com.descentmtb.trail.CornerEdits;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.LinePoints;
import com.descentmtb.trail.ShapeClipboard;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapePresets;
import com.descentmtb.trail.ShapeToolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Previews what the Trail Shaper is about to do. Block presets: a small brass diamond on every corner the mode
 * moves, drawn at the height that corner will have afterwards (the numbers come from {@link ShapePresets}, the same
 * code the server runs, so the preview matches the result). Copy mode: a line box around the selection and, once
 * something is copied, around the place the clipboard will land. Berm and downhill modes: a diamond on every point
 * placed so far. Jump builder: a line box around the blocks the jump would take from the aimed block, and its profile
 * as a line along the middle (the settings kept in the tool, the same height function the server builds from). The
 * cursor's diamonds follow its sub-type and step ({@link CursorSettings}). The line tools (clear path, straight line): a
 * diamond on point A and a line from there to the aimed block, which carries a second diamond.
 *
 * <p>Nothing is recomputed per frame: the tool's data is copied only when its custom data component changes, and the
 * block preset's plan only when the aim, the mode, the facing or Shift changed, or a game tick passed (so an edit of
 * the aimed blocks shows up). All of it runs on the render thread.
 */
public final class ShapingHighlight {
    private static final float RADIUS = .16f;
    private static final int COLOR = 0xd8f2c14a;
    private static final int POINT_COLOR = 0xe6f2c14a;

    /** The custom data component the cached tool state below was read from (compared by identity, it is immutable). */
    private static CustomData cachedData;
    private static CompoundTag data = new CompoundTag();
    private static ShapeMode mode;
    private static CursorSettings cursor = CursorSettings.DEFAULT;
    private static JumpProfiles.Params jump = JumpProfiles.Params.DEFAULT;

    /** Inputs of the cached plan; {@code planPos == null} means nothing is cached. */
    private static BlockPos planPos;
    private static int planSelection;
    private static ShapeMode planMode;
    private static Direction planFacing;
    private static boolean planShift;
    private static long planTick;
    private static Level planLevel;
    private static ShapePresets.Plan plan;
    private static BlockPos jumpPos;
    private static Direction jumpFacing;
    private static JumpProfiles.Params jumpParams;
    private static int[] jumpBounds;
    private static double[][] jumpPoints;
    private static double jumpTop;

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ShapeToolItem.usable(mc.player.getMainHandItem())) {
            return;
        }
        var stack = mc.player.getMainHandItem();
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (custom != cachedData || mode == null) {
            cachedData = custom;
            data = ShapeToolItem.data(stack);
            mode = ShapeToolItem.mode(stack);
            cursor = CursorSettings.read(stack);
            jump = JumpBuilder.read(stack);
            planPos = null;   // the cursor settings may have changed
        }
        BlockHitResult hit = mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var source = mc.renderBuffers().bufferSource();

        switch (mode.kind) {
            case COLUMN -> previewBlock(mc, hit, mode, camera, pose, source);
            case COPY -> previewCopy(data, hit == null ? null : hit.getBlockPos(), camera, pose, source);
            case BERM -> previewPoints(data.getLongArray(com.descentmtb.trail.BermBuilder.POINTS_TAG), camera, pose, source);
            case DOWNHILL -> previewPoints(data.getLongArray(com.descentmtb.trail.DownhillBuilder.POINTS_TAG), camera, pose, source);
            case JUMP -> previewJump(mc, hit, camera, pose, source);
            case CLEAR, LINE -> previewLine(data, hit, camera, pose, source);
            case RAMP -> { }
        }
    }

    private static void previewBlock(Minecraft mc, BlockHitResult hit, ShapeMode mode, Vec3 camera, PoseStack pose,
                                     MultiBufferSource.BufferSource source) {
        if (hit == null) {
            return;
        }
        var pos = hit.getBlockPos();
        var plan = plan(mc, pos, hit.getLocation(), mode);
        if (plan == null) {
            return;
        }
        VertexConsumer vc = source.getBuffer(RenderType.debugQuads());
        for (int corner : plan.affected()) {
            double x = pos.getX() + corner % 2, z = pos.getZ() + corner / 2;
            diamond(vc, pose, x - camera.x, plan.newAbs()[corner] + .03 - camera.y, z - camera.z, RADIUS, COLOR);
        }
        source.endBatch(RenderType.debugQuads());
    }

    /** The preset's plan for the aimed block, from the cache while nothing it depends on changed. */
    private static ShapePresets.Plan plan(Minecraft mc, BlockPos pos, Vec3 hitAt, ShapeMode mode) {
        Direction facing = mc.player.getDirection();
        boolean shift = mc.player.isShiftKeyDown();
        long tick = mc.level.getGameTime();
        double fx = Math.max(0, Math.min(1, hitAt.x - pos.getX()));
        double fz = Math.max(0, Math.min(1, hitAt.z - pos.getZ()));
        int selection = 0;
        if (mode == ShapeMode.AUTO) {
            for (int corner : CornerEdits.picked(cursor.pick(), fx, fz)) selection |= 1 << corner;
        } else if (mode == ShapeMode.CORNER_BANK) {
            selection = (fx >= .5 ? 1 : 0) + (fz >= .5 ? 2 : 0);
        }
        if (planPos == null || !planPos.equals(pos) || planSelection != selection || planMode != mode || planFacing != facing
                || planShift != shift || planTick != tick || planLevel != mc.level) {
            planPos = pos.immutable();
            planSelection = selection;
            planMode = mode;
            planFacing = facing;
            planShift = shift;
            planTick = tick;
            planLevel = mc.level;
            plan = ShapePresets.plan(mc.level, planPos, mode, hitAt, facing, shift, cursor);
        }
        return plan;
    }

    /** The footprint of the jump from the aimed block, and its profile along the clicked block's centre line. */
    private static void previewJump(Minecraft mc, BlockHitResult hit, Vec3 camera, PoseStack pose, MultiBufferSource.BufferSource source) {
        if (hit == null) {
            return;
        }
        BlockPos start = hit.getBlockPos();
        Direction facing = mc.player.getDirection();
        if (!start.equals(jumpPos) || facing != jumpFacing || !jump.equals(jumpParams)) {
            jumpPos = start.immutable();
            jumpFacing = facing;
            jumpParams = jump;
            JumpProfiles.Layout layout = JumpBuilder.layout(start, facing, jump);
            jumpBounds = layout.bounds();
            jumpTop = 0;
            int segments = (int) Math.min(2048L, jump.total() * 8L);
            jumpPoints = new double[segments + 1][];
            for (int i = 0; i < jumpPoints.length; i++) {
                double u = jump.total() * (i / (double) segments);
                double[] point = layout.point(u);
                double height = jump.heightAt(u);
                jumpTop = Math.max(jumpTop, height);
                jumpPoints[i] = new double[]{point[0], start.getY() + 1 + height + .04, point[1]};
            }
        }
        int[] box = jumpBounds;
        VertexConsumer lines = source.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines,
                box[0] - camera.x, start.getY() - camera.y, box[1] - camera.z,
                box[2] + 1 - camera.x, start.getY() + 1 + Math.max(.05, jumpTop) - camera.y, box[3] + 1 - camera.z,
                .95f, .76f, .29f, 1f);
        for (int i = 1; i < jumpPoints.length; i++) {
            double[] a = jumpPoints[i - 1], b = jumpPoints[i];
            segment(lines, pose, camera, a[0], a[1], a[2], b[0], b[1], b[2]);
        }
        source.endBatch(RenderType.lines());
    }

    /** Point A of a line tool and a line from it to the aimed block (with a diamond on that too). */
    private static void previewLine(CompoundTag data, BlockHitResult hit, Vec3 camera, PoseStack pose, MultiBufferSource.BufferSource source) {
        if (!data.contains(LinePoints.POINT_TAG)) {
            return;
        }
        BlockPos a = BlockPos.of(data.getLong(LinePoints.POINT_TAG));
        if (hit == null) {
            previewPoints(new long[]{a.asLong()}, camera, pose, source);
            return;
        }
        BlockPos b = hit.getBlockPos();
        previewPoints(new long[]{a.asLong(), b.asLong()}, camera, pose, source);
        VertexConsumer lines = source.getBuffer(RenderType.lines());
        segment(lines, pose, camera, a.getX() + .5, a.getY() + 1.1, a.getZ() + .5, b.getX() + .5, b.getY() + 1.1, b.getZ() + .5);
        source.endBatch(RenderType.lines());
    }

    /** One teal line segment between two points (world coordinates). */
    private static void segment(VertexConsumer lines, PoseStack pose, Vec3 camera, double x0, double y0, double z0, double x1, double y1, double z1) {
        var last = pose.last();
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        float length = Math.max(1e-4f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        lines.addVertex(last, (float) (x0 - camera.x), (float) (y0 - camera.y), (float) (z0 - camera.z))
                .setColor(.45f, .84f, .76f, 1f).setNormal(last, dx / length, dy / length, dz / length);
        lines.addVertex(last, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z))
                .setColor(.45f, .84f, .76f, 1f).setNormal(last, dx / length, dy / length, dz / length);
    }

    /** Corner A and the box to the aimed block; the footprint of the clipboard at the aimed block. */
    private static void previewCopy(CompoundTag data, BlockPos aimed, Vec3 camera, PoseStack pose,
                                    MultiBufferSource.BufferSource source) {
        VertexConsumer lines = source.getBuffer(RenderType.lines());
        if (data.contains(ShapeClipboard.FIRST_TAG)) {
            BlockPos first = BlockPos.of(data.getLong(ShapeClipboard.FIRST_TAG));
            lineBox(lines, pose, camera, first, first, 0.95f, 0.76f, 0.29f);
            if (aimed != null) {
                int sizeX = Math.abs(first.getX() - aimed.getX()) + 1;
                int sizeZ = Math.abs(first.getZ() - aimed.getZ()) + 1;
                int sizeY = ClipboardMath.boxHeight(first.getY(), aimed.getY());
                boolean fits = ClipboardMath.fits(sizeX, sizeY, sizeZ);
                BlockPos low = new BlockPos(Math.min(first.getX(), aimed.getX()), Math.min(first.getY(), aimed.getY()),
                        Math.min(first.getZ(), aimed.getZ()));
                lineBox(lines, pose, camera, low, low.offset(sizeX - 1, sizeY - 1, sizeZ - 1),
                        fits ? .95f : .9f, fits ? .76f : .25f, fits ? .29f : .2f);
            }
        }
        int[] size = data.getIntArray(ShapeClipboard.SIZE_TAG);
        if (size.length == 3 && aimed != null) {
            BlockPos origin = aimed.above();
            lineBox(lines, pose, camera, origin, origin.offset(size[0] - 1, size[1] - 1, size[2] - 1), .45f, .84f, .76f);
        }
        source.endBatch(RenderType.lines());
    }

    /** A diamond on top of every point (packed block position) placed so far. */
    private static void previewPoints(long[] points, Vec3 camera, PoseStack pose, MultiBufferSource.BufferSource source) {
        if (points.length == 0) {
            return;
        }
        VertexConsumer vc = source.getBuffer(RenderType.debugQuads());
        for (long packed : points) {
            BlockPos pos = BlockPos.of(packed);
            diamond(vc, pose, pos.getX() + .5 - camera.x, pos.getY() + 1.1 - camera.y, pos.getZ() + .5 - camera.z, .3f, POINT_COLOR);
        }
        source.endBatch(RenderType.debugQuads());
    }

    private static void lineBox(VertexConsumer lines, PoseStack pose, Vec3 camera, BlockPos low, BlockPos high,
                                float red, float green, float blue) {
        LevelRenderer.renderLineBox(pose, lines,
                low.getX() - camera.x, low.getY() - camera.y, low.getZ() - camera.z,
                high.getX() + 1 - camera.x, high.getY() + 1 - camera.y, high.getZ() + 1 - camera.z,
                red, green, blue, 1f);
    }

    private static void diamond(VertexConsumer vc, PoseStack pose, double x, double y, double z, float radius, int color) {
        var m = pose.last();
        float cx = (float) x, cy = (float) y, cz = (float) z;
        vc.addVertex(m, cx - radius, cy, cz).setColor(color);
        vc.addVertex(m, cx, cy, cz + radius).setColor(color);
        vc.addVertex(m, cx + radius, cy, cz).setColor(color);
        vc.addVertex(m, cx, cy, cz - radius).setColor(color);
        // visible from below as well
        vc.addVertex(m, cx, cy, cz - radius).setColor(color);
        vc.addVertex(m, cx + radius, cy, cz).setColor(color);
        vc.addVertex(m, cx, cy, cz + radius).setColor(color);
        vc.addVertex(m, cx - radius, cy, cz).setColor(color);
    }

    private ShapingHighlight() {}
}
