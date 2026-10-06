package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.entity.BikeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.client.model.HardtailBikeModel;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Shared posed, material-tinted bike renderer for the workshop preview and the repair stand.
 */
public final class BikeBuildRenderer {
    private static EntityModelSet bakedFrom;
    private static EnduroBikeModel enduro;
    private static HardtailBikeModel hardtail;

    /** EntityModelSet keeps its identity when a resource reload replaces its layers. */
    public static void invalidate() { bakedFrom=null; enduro=null; hardtail=null; }

    private static void models() {
        var set=Minecraft.getInstance().getEntityModels();
        if(set==bakedFrom) return;
        enduro=new EnduroBikeModel(set.bakeLayer(EnduroBikeModel.LAYER));
        hardtail=new HardtailBikeModel(set.bakeLayer(HardtailBikeModel.LAYER));
        bakedFrom=set;
    }

    /** Model-space origin at the tyre ground line; the caller chooses orientation. */
    public static void render(BikeType type,BikeBuild build,PoseStack pose,MultiBufferSource buffers,int light) {
        models();
        boolean previous=BikeDecorations.preview;
        BikeDecorations.preview=true;
        try {
        if(type==BikeType.HARDTAIL) {
            hardtail.setupPose(0,0,0,0,0,0);
            hardtail.setupBrake(0);
            hardtail.setupTrick(com.descentmtb.trick.Trick.NONE,0,1);
            hardtail.renderCustomized(pose,buffers,light,OverlayTexture.NO_OVERLAY,build);
        } else {
            enduro.setupPose(0,0,0,0,0,0);
            enduro.setupBrake(0);
            enduro.renderCustomized(pose,buffers,light,OverlayTexture.NO_OVERLAY,build);
        }
        } finally { BikeDecorations.preview=previous; }
    }
    /**
     * Renders the bike model with all parts, colours, accessories and stickers of {@code build}, centred at (x, y) in
     * GUI pixels, {@code scale} GUI pixels per metre, turned by {@code yawDeg} around the vertical axis and tilted by
     * {@code pitchDeg} toward the viewer. Must be callable every frame (no allocation-heavy work).
     */
    public static void renderInGui(GuiGraphics g, BikeType type, BikeBuild build, float x, float y, float scale,
                                   float yawDeg, float pitchDeg) {
        g.flush();
        var pose=g.pose();
        pose.pushPose();
        pose.translate(x,y,100);
        pose.scale(scale,scale,-scale);
        pose.translate(0,.6,0);
        pose.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        pose.mulPose(Axis.YP.rotationDegrees(-yawDeg));
        Lighting.setupForEntityInInventory();
        BikeDecorations.preview=true;
        try {
            render(type,build,pose,g.bufferSource(),0xF000F0);
            g.flush();
        } finally {
            BikeDecorations.preview=false;
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private BikeBuildRenderer() {}
}
