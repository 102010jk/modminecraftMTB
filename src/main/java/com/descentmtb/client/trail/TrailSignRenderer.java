package com.descentmtb.client.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.SignArt;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.SignLayout;
import com.descentmtb.trail.TrailSignBlock;
import com.descentmtb.trail.TrailSignEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the front of a trail sign: icons as textured quads, pixel art as coloured quads and text through the
 * vanilla font, all lit by the block light like a vanilla sign. What goes where comes from {@link SignLayout}.
 */
public final class TrailSignRenderer implements BlockEntityRenderer<TrailSignEntity> {
    /** Dark ink on the light wooden board. */
    private static final int TEXT_COLOUR = 0xff2b1b0e;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    /** Where the board face sits in the block, for a sign whose front looks towards +z. */
    private static final float STANDING_FACE_Z = 9 / 16f, STANDING_TOP = 1f;
    private static final float WALL_FACE_Z = 2 / 16f, WALL_TOP = 13 / 16f;
    private static final float BOARD_LEFT = 1 / 16f;
    /** Content floats this far (in font units) in front of the board face, behind the frame lip. */
    private static final float LIFT = 0.15f;

    private final Font font;
    private final Map<TrailSignEntity, Cached> cache = new WeakHashMap<>();
    private Language labelsLanguage;
    private String startLabel = "", finishLabel = "";

    /** Layout of one sign, valid while its content and the label words stay the same. */
    private record Cached(SignContent content, String startLabel, String finishLabel, List<SignLayout.Item> items) {}

    public TrailSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(TrailSignEntity sign, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        SignContent content = sign.content();
        boolean wall = sign.getBlockState().getValue(TrailSignBlock.WALL);
        List<SignLayout.Item> items = layout(sign, content);

        pose.pushPose();
        // Work in a frame where the front of the sign looks towards +z, like the vanilla sign renderer.
        pose.translate(.5, .5, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-sign.getBlockState().getValue(TrailSignBlock.FACING).toYRot()));
        pose.translate(-.5, -.5, -.5);
        // Board top left corner, then one unit = one font pixel (y points down, as in text rendering).
        pose.translate(BOARD_LEFT, wall ? WALL_TOP : STANDING_TOP, wall ? WALL_FACE_Z : STANDING_FACE_Z);
        pose.scale(1 / 96f, -1 / 96f, 1 / 96f);

        for (SignLayout.Item item : items) {
            switch (item) {
                case SignLayout.Icon icon -> drawIcon(icon, pose, buffers, light);
                case SignLayout.Label label -> drawLabel(label, pose, buffers, light);
                case SignLayout.Art art -> drawArt(art, content, pose, buffers, light);
            }
        }
        pose.popPose();
    }

    /**
     * The layout of the sign, from the cache while its content and the label words are unchanged. The label words
     * are looked up again only when the active language changed ({@link Language#getInstance()} is replaced on every
     * language switch and resource reload), not on every frame.
     */
    private List<SignLayout.Item> layout(TrailSignEntity sign, SignContent content) {
        Language language = Language.getInstance();
        if (language != labelsLanguage) {
            labelsLanguage = language;
            startLabel = Component.translatable("descentmtb.sign.label.start").getString();
            finishLabel = Component.translatable("descentmtb.sign.label.finish").getString();
        }
        Cached cached = cache.get(sign);
        if (cached == null || cached.content != content || !cached.startLabel.equals(startLabel)
                || !cached.finishLabel.equals(finishLabel)) {
            cached = new Cached(content, startLabel, finishLabel,
                    SignLayout.build(content, startLabel, finishLabel, font::width));
            cache.put(sign, cached);
        }
        return cached.items;
    }

    static ResourceLocation icon(String name) {
        return ICONS.computeIfAbsent(name,
                n -> ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "textures/sign/" + n + ".png"));
    }

    private static void drawIcon(SignLayout.Icon icon, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(icon(icon.name())));
        float x0 = (float) icon.x(), y0 = (float) icon.y();
        float x1 = (float) (icon.x() + icon.size()), y1 = (float) (icon.y() + icon.size());
        vertex(vc, pose, x0, y1, 0, 1, light);
        vertex(vc, pose, x1, y1, 1, 1, light);
        vertex(vc, pose, x1, y0, 1, 0, light);
        vertex(vc, pose, x0, y0, 0, 0, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, float x, float y, float u, float v, int light) {
        vertex(vc, pose, x, y, u, v, light, 0xffffffff);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, float x, float y, float u, float v, int light, int argb) {
        vc.addVertex(pose.last(), x, y, LIFT)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose.last(), 0, 0, 1);
    }

    private void drawLabel(SignLayout.Label label, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(label.centreX(), label.top(), LIFT);
        pose.scale((float) label.scale(), (float) label.scale(), 1);
        float x = -font.width(label.text()) / 2f;
        font.drawInBatch(label.text(), x, 0, TEXT_COLOUR, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, light);
        pose.popPose();
    }

    /** Pixel art as coloured quads; neighbouring pixels of one colour in a row are merged into one quad. */
    private static void drawArt(SignLayout.Art art, SignContent content, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(WHITE));
        byte[] pixels = content.pixels();
        float cell = (float) art.cell();
        for (int row = 0; row < 16; row++) {
            int column = 0;
            while (column < 16) {
                int colourIndex = pixels[row * 16 + column] & 15;
                int end = column + 1;
                while (end < 16 && (pixels[row * 16 + end] & 15) == colourIndex) {
                    end++;
                }
                int argb = SignArt.PALETTE[colourIndex];
                float x0 = (float) art.x() + column * cell, x1 = (float) art.x() + end * cell;
                float y0 = (float) art.y() + row * cell, y1 = y0 + cell;
                vertex(vc, pose, x0, y1, 0, 1, light, argb);
                vertex(vc, pose, x1, y1, 1, 1, light, argb);
                vertex(vc, pose, x1, y0, 1, 0, light, argb);
                vertex(vc, pose, x0, y0, 0, 0, light, argb);
                column = end;
            }
        }
    }
}
