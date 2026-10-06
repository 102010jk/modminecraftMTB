package com.descentmtb.client.custom;

import com.descentmtb.client.model.PartTable.Spec;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeParts.Tube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.EnumMap;
import com.descentmtb.custom.BikeParts.StickerDesign;

/** Decals attach to the posed tube, including suspension, steering and tailwhips. */
public final class BikeDecorations {
    static boolean preview;
    private static final EnumMap<StickerDesign,ResourceLocation> STICKERS=new EnumMap<>(StickerDesign.class);
    static { for(var d:StickerDesign.values()) STICKERS.put(d,ResourceLocation.fromNamespaceAndPath("descentmtb",d.texture())); }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, BikeBuild build, Spec spec) {
        String name=spec.baseName();
        Tube tube=tube(name);
        if(tube!=null) for(var sticker:build.stickers()) {
            if(sticker.tube()!=tube) continue;
            boolean left=name.endsWith("_l"),right=name.endsWith("_r");
            if(left && sticker.side()>0 || right && sticker.side()<0) continue;
            float local=sticker.t();
            if(tube==Tube.TOP) {
                local=1-local;
                if(name.equals("top_front")) { if(local>=.5f) continue; local*=2; }
                if(name.equals("top_rear")) { if(local<.5f) continue; local=(local-.5f)*2; }
                if(name.equals("top_join")) continue;
            }
            if(tube==Tube.DOWN) {
                if(name.equals("down_front")) { if(local>=.5f) continue;local*=2; }
                if(name.equals("down_rear")) { if(local<.5f) continue;local=(local-.5f)*2; }
            }
            float z=tube==Tube.FORK_LEG ? 0 : (local-.5f)*spec.sz()/16;
            float y=tube==Tube.FORK_LEG ? (local-.5f)*spec.sy()/16 : 0;
            for(int side=-1;side<=1;side+=2) {
                // The DJ head tube is rotated 180 degrees about Y, swapping local X sides.
                int outward=name.equals("head_tube") && !build.shape().fullSuspension ? -side : side;
                if(sticker.side()!=0 && outward!=-sticker.side()) continue;
                if(left && side<0 || right && side>0) continue;
                pose.pushPose();
                pose.translate(side*(spec.sx()/32+.0015),y,z);
                if(tube==Tube.FORK_LEG) pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.translate(0,sticker.across()*(tube==Tube.FORK_LEG ? spec.sz() : spec.sy())/32,0);
                pose.mulPose(Axis.XP.rotationDegrees(side*sticker.rotation()));
                if(!sticker.text().isBlank()) {
                    var font=net.minecraft.client.Minecraft.getInstance().font;
                    float width=Math.max(1,font.width(sticker.text()));
                    float scale=.11f*sticker.scale()/width;
                    pose.mulPose(Axis.YP.rotationDegrees(side*90));
                    pose.scale(sticker.mirrored() ? -scale : scale,scale,scale);
                    font.drawInBatch(sticker.text(),-width/2,-4,0xFF000000|sticker.tint(),false,pose.last().pose(),buffers,
                            net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,light);
                    pose.popPose();continue;
                }
                float halfW=.055f*sticker.scale(),halfH=halfW/StickerIcons.aspectRatio(sticker.design());
                var vc=buffers.getBuffer(RenderType.entityCutoutNoCull(STICKERS.get(sticker.design())));
                var p=pose.last();int color=0xFF000000|sticker.tint();
                int u0=sticker.mirrored() ? 1 : 0,u1=1-u0;
                // Each outward side reads left-to-right and top-to-bottom, including text decals.
                vc.addVertex(p,0,-halfH,side*halfW).setColor(color).setUv(u0,0).setOverlay(overlay).setLight(light).setNormal(p,side,0,0);
                vc.addVertex(p,0,halfH,side*halfW).setColor(color).setUv(u0,1).setOverlay(overlay).setLight(light).setNormal(p,side,0,0);
                vc.addVertex(p,0,halfH,-side*halfW).setColor(color).setUv(u1,1).setOverlay(overlay).setLight(light).setNormal(p,side,0,0);
                vc.addVertex(p,0,-halfH,-side*halfW).setColor(color).setUv(u1,0).setOverlay(overlay).setLight(light).setNormal(p,side,0,0);
                pose.popPose();
            }
        }
        // Real block lighting and emissive lenses remain; no volume crossing water or other geometry.
    }

    public static Tube tube(String name) {
        if(name.startsWith("top_tube") || name.equals("top_front") || name.equals("top_rear") || name.equals("top_straight")) return Tube.TOP;
        if(name.equals("down_tube") || name.equals("down_front") || name.equals("down_rear")) return Tube.DOWN;
        if(name.equals("seat_tube")) return Tube.SEAT;
        if(name.startsWith("seatstay_")) return Tube.SEAT_STAY;
        if(name.startsWith("chainstay_")) return Tube.CHAIN_STAY;
        if(name.equals("head_tube")) return Tube.HEAD;
        if(name.startsWith("leg_up_")) return Tube.FORK_LEG;
        return null;
    }

    private BikeDecorations() {}
}
