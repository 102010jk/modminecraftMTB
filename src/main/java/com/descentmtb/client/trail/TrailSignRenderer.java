package com.descentmtb.client.trail;
import com.descentmtb.trail.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
/** Pixel geometry avoids dynamic texture churn and shows the synchronized canvas to every viewer. */
public final class TrailSignRenderer implements BlockEntityRenderer<TrailSignEntity> {
 public TrailSignRenderer(BlockEntityRendererProvider.Context c){}
 @Override public void render(TrailSignEntity be,float t,PoseStack pose,MultiBufferSource source,int light,int overlay){pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-be.getBlockState().getValue(TrailSignBlock.FACING).toYRot()+180));pose.translate(-.5,0,-.5);var vc=source.getBuffer(RenderType.debugQuads());byte[] p=be.pixels();for(int y=0;y<16;y++)for(int x=0;x<16;x++){float a=.0625f+x*.875f/16,b=.9375f-y*.625f/16,d=.875f/16,h=.625f/16;int color=SignArt.PALETTE[p[y*16+x]&15];vc.addVertex(pose.last(),a,b,.247f).setColor(color);vc.addVertex(pose.last(),a+d,b,.247f).setColor(color);vc.addVertex(pose.last(),a+d,b-h,.247f).setColor(color);vc.addVertex(pose.last(),a,b-h,.247f).setColor(color);}pose.popPose();}
}
