package com.descentmtb.client.map;

import com.descentmtb.map.BikeparkMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** One bounded cache shared by the screen and item-frame renderer. */
public final class MapTexture {
    private static final Map<BikeparkMap,ResourceLocation> CACHE=new LinkedHashMap<>(32,.75f,true);
    public record Bounds(double minX,double minZ,double span) {}
    public static Bounds bounds(BikeparkMap data) {
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
        for(var route:data.routes()) { if(!route.dimension().equals(data.routes().getFirst().dimension()))continue;
        for(int i=0;i<route.track().pts().length;i+=3) {
            var p=route.track().pts();double x=p[i]/10.0,z=p[i+2]/10.0;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }}
        if(!Double.isFinite(minX))return new Bounds(-50,-50,100);
        double span=Math.max(10,Math.max(maxX-minX,maxZ-minZ))*1.12;
        return new Bounds((minX+maxX-span)/2,(minZ+maxZ-span)/2,span);
    }
    public static ResourceLocation get(BikeparkMap data) {
        ResourceLocation cached=CACHE.get(data);if(cached!=null)return cached;
        NativeImage image=new NativeImage(128,128,false);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setPixelRGBA(x,y,abgr(x<3||y<3||x>124||y>124?0xff6c685a:(x%16==0||y%16==0?0xffccc9ad:0xffe6dfc6)));
        Bounds b=bounds(data);int index=0;
        int[] colors={0xff28705a,0xff457bac,0xffc98534,0xff955866,0xff7561a8,0xff4d8985};
        for(var route:data.routes()) {
            if(!route.dimension().equals(data.routes().getFirst().dimension()))continue;
            int[] p=route.track().pts();int color=colors[index++%colors.length];
            for(int i=3;i<p.length;i+=3)line(image,point(p[i-3]/10.0,b.minX,b.span),point(p[i-1]/10.0,b.minZ,b.span),point(p[i]/10.0,b.minX,b.span),point(p[i+2]/10.0,b.minZ,b.span),color);
            if(p.length>=6) {
                dot(image,point(p[0]/10.0,b.minX,b.span),point(p[2]/10.0,b.minZ,b.span),0xff37a261);
                dot(image,point(p[p.length-3]/10.0,b.minX,b.span),point(p[p.length-1]/10.0,b.minZ,b.span),0xffcf5745);
            }
        }
        DynamicTexture texture=new DynamicTexture(image);
        texture.setFilter(false,false);
        ResourceLocation id=Minecraft.getInstance().getTextureManager().register("descentmtb-trail-map",texture);CACHE.put(data,id);
        while(CACHE.size()>32){var first=CACHE.entrySet().iterator();var entry=first.next();Minecraft.getInstance().getTextureManager().release(entry.getValue());first.remove();}
        return id;
    }
    public static int point(double value,double min,double span) { return (int)Math.round(5+(value-min)/span*117); }
    private static int abgr(int argb) { return argb&0xff00ff00 | (argb>>16)&255 | (argb&255)<<16; }
    private static void dot(NativeImage im,int x,int y,int color) { for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)pixel(im,x+dx,y+dy,color); }
    private static void pixel(NativeImage im,int x,int y,int color) { if(x>=3&&x<=124&&y>=3&&y<=124)im.setPixelRGBA(x,y,abgr(color)); }
    private static void line(NativeImage im,int x0,int y0,int x1,int y1,int color) {
        int dx=x1-x0,dy=y1-y0,n=Math.max(Math.abs(dx),Math.abs(dy));
        for(int i=0;i<=n;i++){double t=n==0?0:i/(double)n;int x=(int)Math.round(x0+dx*t),y=(int)Math.round(y0+dy*t);pixel(im,x,y,color);pixel(im,x+1,y,color);}
    }
}
