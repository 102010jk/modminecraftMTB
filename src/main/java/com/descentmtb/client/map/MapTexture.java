package com.descentmtb.client.map;

import com.descentmtb.map.BikeparkMap;
import com.descentmtb.map.TrailDifficulty;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The drawn trail map: hand-shaded parchment, every route as a smooth curve coloured by its bike-park grade
 * (green / blue / red / black), start flag and chequered finish. One bounded cache shared by the screen (256 px) and
 * the item frame (128 px, the vanilla map resolution). Pixel x = world east, pixel y = world south, exactly like a
 * vanilla map, so a framed trail map hangs the same way round as a framed vanilla map.
 */
public final class MapTexture {
    private record Key(BikeparkMap data, int size) {}
    private static final Map<Key,ResourceLocation> CACHE=new LinkedHashMap<>(32,.75f,true);
    public record Bounds(double minX,double minZ,double span) {}

    // parchment palette: warm highlights, cool brown shadows (no plain black/white shading)
    private static final int PAPER=0xffe8dcc0, PAPER_LIGHT=0xfff3e9cf, PAPER_DARK=0xffd6c7a4, GRID=0xffd2c39f,
            FRAME=0xff6b5a43, FRAME_LIGHT=0xff9a8462, FRAME_SHADOW=0xff4a3a2e;

    /** The square of the world the map shows: the routes of the first route's dimension, with a margin. */
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

    /** The vanilla-resolution (128 px) map, for item frames. */
    public static ResourceLocation get(BikeparkMap data) { return get(data,128); }

    public static ResourceLocation get(BikeparkMap data,int size) {
        Key key=new Key(data,size);
        ResourceLocation cached=CACHE.get(key);if(cached!=null)return cached;
        NativeImage image=new NativeImage(size,size,false);
        paper(image,size);
        Bounds b=bounds(data);
        int casing=Math.max(1,size/64),width=Math.max(1,size/96);
        // casings first, so crossing trails read as one network
        for(var route:data.routes()) if(route.dimension().equals(data.routes().getFirst().dimension()))
            stroke(image,curve(route.track().pts(),b,size),width+casing,difficultyOf(route)==TrailDifficulty.BLACK?0xfff4ecd0:0xff5b4a37,size);
        for(var route:data.routes()) if(route.dimension().equals(data.routes().getFirst().dimension()))
            stroke(image,curve(route.track().pts(),b,size),width,difficultyOf(route).argb,size);
        for(var route:data.routes()) {
            if(!route.dimension().equals(data.routes().getFirst().dimension()))continue;
            int[] p=route.track().pts();
            if(p.length<6)continue;
            startFlag(image,point(p[0]/10.0,b.minX,b.span,size),point(p[2]/10.0,b.minZ,b.span,size),size);
            finishFlag(image,point(p[p.length-3]/10.0,b.minX,b.span,size),point(p[p.length-1]/10.0,b.minZ,b.span,size),size);
        }
        DynamicTexture texture=new DynamicTexture(image);
        texture.setFilter(false,false);
        ResourceLocation id=Minecraft.getInstance().getTextureManager().register("descentmtb-trail-map",texture);CACHE.put(key,id);
        while(CACHE.size()>32){var first=CACHE.entrySet().iterator();var entry=first.next();Minecraft.getInstance().getTextureManager().release(entry.getValue());first.remove();}
        return id;
    }

    private static final Map<BikeparkMap.Route,TrailDifficulty> GRADES=new java.util.WeakHashMap<>();
    /** Grade of a route (cached: the screen asks every frame). */
    public static TrailDifficulty difficultyOf(BikeparkMap.Route route) {
        return GRADES.computeIfAbsent(route,r->TrailDifficulty.of(r.track().pts()));
    }

    /** Pixel of a world coordinate on a 128 px map. */
    public static int point(double value,double min,double span) { return point(value,min,span,128); }

    public static int point(double value,double min,double span,int size) {
        double margin=size*5/128.0;
        return (int)Math.round(margin+(value-min)/span*(size-2*margin));
    }

    /** Smoothed pixel path of a track (x, y pairs, sub-pixel). */
    public static double[] curve(int[] p,Bounds b,int size) {
        int n=p.length/3;double[] xy=new double[n*2];
        double margin=size*5/128.0;
        for(int i=0;i<n;i++){xy[i*2]=margin+(p[i*3]/10.0-b.minX)/b.span*(size-2*margin);xy[i*2+1]=margin+(p[i*3+2]/10.0-b.minZ)/b.span*(size-2*margin);}
        return TrailDifficulty.smooth(xy,2,6);
    }

    // ------------------------------------------------------------------ painting

    /** Parchment: speckled paper, faint survey grid and a bevelled wooden frame (light from the top left). */
    private static void paper(NativeImage im,int size) {
        int grid=size/8,edge=Math.max(2,size*3/128);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            int c;
            if(x<edge||y<edge||x>=size-edge||y>=size-edge){
                boolean outer=x==0||y==0||x==size-1||y==size-1;
                boolean lit=(x<edge&&y<size-x)||(y<edge&&x<size-y);
                c=outer?FRAME_SHADOW:lit?FRAME_LIGHT:FRAME;
                if(!outer&&(x==edge-1||y==edge-1||x==size-edge||y==size-edge))c=lit?FRAME:FRAME_SHADOW;
            } else {
                int h=hash(x,y);
                c=(h&15)==0?PAPER_DARK:(h&31)==1?PAPER_LIGHT:PAPER;
                if(x%grid==0||y%grid==0)c=GRID;
                // soft vignette towards the frame (ambient occlusion of the paper under the frame lip)
                int d=Math.min(Math.min(x,y),Math.min(size-1-x,size-1-y))-edge;
                if(d<2)c=mix(c,FRAME,d==0?.35f:.18f);
            }
            im.setPixelRGBA(x,y,abgr(c));
        }
    }

    private static void stroke(NativeImage im,double[] xy,int radius,int color,int size) {
        for(int i=2;i<xy.length;i+=2){
            double x0=xy[i-2],y0=xy[i-1],x1=xy[i],y1=xy[i+1];
            int n=(int)Math.ceil(Math.max(Math.abs(x1-x0),Math.abs(y1-y0))*2)+1;
            for(int s=0;s<=n;s++){double t=s/(double)n;disc(im,x0+(x1-x0)*t,y0+(y1-y0)*t,radius,color,size);}
        }
        if(xy.length==2)disc(im,xy[0],xy[1],radius,color,size);
    }

    private static void disc(NativeImage im,double cx,double cy,int r,int color,int size) {
        int x0=(int)Math.floor(cx-r),x1=(int)Math.ceil(cx+r),y0=(int)Math.floor(cy-r),y1=(int)Math.ceil(cy+r);
        double rr=(r+.35)*(r+.35);
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){double dx=x+.5-cx,dy=y+.5-cy;if(dx*dx+dy*dy<=rr)pixel(im,x,y,color,size);}
    }

    private static void startFlag(NativeImage im,int x,int y,int size) {
        int s=Math.max(2,size/48);
        for(int dy=-s-1;dy<=s+1;dy++)for(int dx=-s-1;dx<=s+1;dx++)pixel(im,x+dx,y+dy,0xff2d4a2a,size);
        for(int dy=-s;dy<=s;dy++)for(int dx=-s;dx<=s;dx++)pixel(im,x+dx,y+dy,dy<0&&dx<0?0xff7fd36b:0xff3f9b4f,size);
    }

    private static void finishFlag(NativeImage im,int x,int y,int size) {
        int s=Math.max(2,size/48);
        for(int dy=-s-1;dy<=s+1;dy++)for(int dx=-s-1;dx<=s+1;dx++)pixel(im,x+dx,y+dy,0xff2b2522,size);
        for(int dy=-s;dy<=s;dy++)for(int dx=-s;dx<=s;dx++)pixel(im,x+dx,y+dy,((dx+s)+(dy+s))%2==0?0xfff6efd9:0xff2b2522,size);
    }

    private static int hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return h^(h>>>16);}
    private static int mix(int a,int b,float t){
        int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)((a&255)*(1-t)+(b&255)*t);
        return 0xff000000|r<<16|g<<8|bl;
    }
    private static int abgr(int argb) { return argb&0xff00ff00 | (argb>>16)&255 | (argb&255)<<16; }
    private static void pixel(NativeImage im,int x,int y,int color,int size) {
        int edge=Math.max(2,size*3/128);
        if(x>=edge&&x<size-edge&&y>=edge&&y<size-edge)im.setPixelRGBA(x,y,abgr(color));
    }
}
