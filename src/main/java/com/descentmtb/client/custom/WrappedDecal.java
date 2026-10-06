package com.descentmtb.client.custom;

import com.descentmtb.client.model.PartTable.Spec;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeParts.Tube;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import java.util.ArrayList;
import java.util.List;

/** Surface decals on all four faces of a cuboid tube, clipped at bends and UV borders. */
final class WrappedDecal {
    private record Vertex(float x,float y,float z,float u,float v) {
        Vertex mix(Vertex b,float t) { return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t); }
    }
    static void render(PoseStack pose,MultiBufferSource buffers,int light,int overlay,BikeBuild build,
                       BikeBuild.Sticker sticker,Spec spec,Tube tube) {
        String name=spec.baseName();
        boolean top=tube==Tube.TOP,front=name.equals("top_front") || name.equals("down_front"),
                rear=name.equals("top_rear") || name.equals("down_rear");
        float t0=0,t1=1;
        if(front) { t0=top ? .5f : 0;t1=top ? 1 : .5f; }
        if(rear) { t0=top ? 0 : .5f;t1=top ? .5f : 1; }
        float d0=distance(build,tube,t0),d1=distance(build,tube,t1),center=distance(build,tube,sticker.t());
        float length=.11f*sticker.scale(),start=center-length/2;
        float lo=Math.max(d0,start),hi=Math.min(d1,start+length);
        if(hi<=lo || d1-d0<.00001f) return;
        boolean fork=tube==Tube.FORK_LEG;
        float axial=(fork ? spec.sy() : spec.sz())/16;
        float z0=((lo-d0)/(d1-d0)-.5f)*axial,z1=((hi-d0)/(d1-d0)-.5f)*axial;
        if(top) { z0=-z0;z1=-z1; }
        float x=spec.sx()/32+.0015f,y=(fork ? spec.sz() : spec.sy())/32+.0015f;
        float[][] corners={{x,-y},{x,y},{-x,y},{-x,-y},{x,-y}};
        float perimeter=4*(x+y),s=0;
        var asset=StickerAssets.of(sticker);
        var vc=buffers.getBuffer(RenderType.entityCutoutNoCull(asset.id()));
        float angle=(float)Math.toRadians(sticker.rotation()),cos=(float)Math.cos(angle),sin=(float)Math.sin(angle);
        pose.pushPose();if(fork) pose.mulPose(Axis.XP.rotationDegrees(-90));
        for(int face=0;face<4;face++) {
            float[] a=corners[face],b=corners[face+1];float edge=(float)Math.hypot(b[0]-a[0],b[1]-a[1]);
            float u0=s/perimeter,u1=(s+edge)/perimeter;s+=edge;
            float v0=(lo-start)/length,v1=(hi-start)/length;
            // Split the angular phase at the seam; adjacent faces share the same perimeter UV.
            for(int turn=-1;turn<=1;turn++) {
                List<Vertex> polygon=new ArrayList<>();
                float phase=sticker.across()*.5f+turn;
                polygon.add(new Vertex(a[0],a[1],z0,u0+phase,v0));
                polygon.add(new Vertex(b[0],b[1],z0,u1+phase,v0));
                polygon.add(new Vertex(b[0],b[1],z1,u1+phase,v1));
                polygon.add(new Vertex(a[0],a[1],z1,u0+phase,v1));
                // Partition at the angular seam before scaling: enlarged stickers must not overdraw copies.
                for(int border=0;border<2 && !polygon.isEmpty();border++) polygon=clip(polygon,border);
                polygon=polygon.stream().map(p->uv(p.x,p.y,p.z,p.u,p.v,sticker,cos,sin)).toList();
                for(int border=0;border<4 && !polygon.isEmpty();border++) polygon=clip(polygon,border);
                float nx=face==0 ? 1 : face==2 ? -1 : 0,ny=face==1 ? 1 : face==3 ? -1 : 0;
                for(int i=1;i+1<polygon.size();i++) {
                    emit(vc,pose,polygon.get(0),sticker.tint(),light,overlay,nx,ny);
                    emit(vc,pose,polygon.get(i),sticker.tint(),light,overlay,nx,ny);
                    emit(vc,pose,polygon.get(i+1),sticker.tint(),light,overlay,nx,ny);
                    emit(vc,pose,polygon.get(i+1),sticker.tint(),light,overlay,nx,ny);
                }
            }
        }
        pose.popPose();
    }
    private static float distance(BikeBuild build,Tube tube,float t) {
        var shape=build.shape();boolean full=shape.fullSuspension;
        float[] a=StickerAnchors.pointOn(full,shape,tube,0),b=StickerAnchors.pointOn(full,shape,tube,.5f),c=StickerAnchors.pointOn(full,shape,tube,1);
        float first=(float)Math.hypot(b[0]-a[0],b[1]-a[1]),second=(float)Math.hypot(c[0]-b[0],c[1]-b[1]);
        return t<=.5f ? first*t*2 : first+second*(t-.5f)*2;
    }
    private static Vertex uv(float x,float y,float z,float u,float v,BikeBuild.Sticker sticker,float cos,float sin) {
        u=(u-.5f)/sticker.scale();v-=.5f;
        float ru=.5f+u*cos-v*sin,rv=.5f+u*sin+v*cos;
        return new Vertex(x,y,z,sticker.mirrored() ? 1-ru : ru,rv);
    }
    private static float inside(Vertex p,int border) {
        return switch(border) { case 0 -> p.u;case 1 -> 1-p.u;case 2 -> p.v;default -> 1-p.v; };
    }
    private static List<Vertex> clip(List<Vertex> in,int border) {
        List<Vertex> out=new ArrayList<>();Vertex a=in.get(in.size()-1);float da=inside(a,border);
        for(Vertex b:in) {
            float db=inside(b,border);
            if((da>=0)!=(db>=0)) out.add(a.mix(b,da/(da-db)));
            if(db>=0) out.add(b);a=b;da=db;
        }
        return out;
    }
    private static void emit(VertexConsumer vc,PoseStack pose,Vertex p,int tint,int light,int overlay,float nx,float ny) {
        vc.addVertex(pose.last(),p.x,p.y,p.z).setColor(0xFF000000|tint).setUv(p.u,p.v)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(),nx,ny,0);
    }
    private WrappedDecal() {}
}
