package com.descentmtb.client.map;

import com.descentmtb.map.BikeparkMap;
import com.descentmtb.map.MapFit;
import com.descentmtb.map.MapPalette;
import com.descentmtb.map.TerrainShader;
import com.descentmtb.map.TrailDifficulty;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The drawn trail map: a hand-shaded chart of the real world under the routes (muted palette, hillshading from the top
 * left, contours, depth-shaded water; parchment where the world is not loaded or is another dimension), every route
 * as a smooth two-tone-cased curve coloured by its bike-park grade (green / blue / red / black), start flag, chequered
 * finish, scale bar and north arrow. One bounded cache shared by the screen (512 px), the item frame (128 px, the
 * vanilla map resolution) and walls of item frames (one picture of 128 px per frame, up to 1024 px a side, drawn at the
 * same scale per frame as a single map so the style is the same). Pixel x = world east, pixel y = world south, exactly
 * like a vanilla map, so a framed trail map hangs the same way round as a framed vanilla map.
 */
public final class MapTexture {
    private record Key(BikeparkMap data, int w, int h, int unit, boolean terrain) {}
    private static final Map<Key,ResourceLocation> CACHE=new LinkedHashMap<>(32,.75f,true);
    private static final long MAX_CACHED_PIXELS=6_000_000L;
    /** The part of the world a map shows: a square of {@code span} blocks, or a rectangle of the picture's shape. */
    public record Bounds(double minX,double minZ,double spanX,double spanZ) {
        public Bounds(double minX,double minZ,double span){this(minX,minZ,span,span);}
        public double span(){return Math.max(spanX,spanZ);}
    }
    /** Shape of a picture: pixels across and down, and {@code unit}, the pixels one item frame's worth of map has (everything is drawn relative to it). */
    private record Dim(int w,int h,int unit) {
        int edge(){return Math.max(2,unit*3/128);}
        double margin(){return unit*5/128.0;}
        int grid(){return unit/8;}
        int scale(){return Math.max(1,unit/128);}
        double aspect(){return (w-2*margin())/(h-2*margin());}
    }

    // parchment palette: warm highlights, cool brown shadows (no plain black/white shading)
    private static final int PAPER=0xffe8dcc0, PAPER_LIGHT=0xfff3e9cf, PAPER_DARK=0xffd6c7a4, GRID=0xffd2c39f,
            FRAME=0xff6b5a43, FRAME_LIGHT=0xff9a8462, FRAME_SHADOW=0xff4a3a2e,
            CASE_DARK=0xff33283a, CASE_LIGHT=0xfff7ebc6, INK=0xff2f2840, HALO=0xfff3e7c8, ARROW_LIT=0xfff6e6b8, ARROW_SHADE=0xff4b3f5c;

    /** The square of the world the map shows: the routes of the first route's dimension, with a margin. */
    public static Bounds bounds(BikeparkMap data) { return bounds(data,1); }

    /** The world rectangle of the picture's inner shape ({@code aspect} = width over height) around the routes of the first route's dimension. */
    private static Bounds bounds(BikeparkMap data,double aspect) {
        double minX=Double.POSITIVE_INFINITY,maxX=-minX,minZ=minX,maxZ=-minX;
        for(var route:data.routes()) { if(!route.dimension().equals(data.routes().getFirst().dimension()))continue;
        for(int i=0;i<route.track().pts().length;i+=3) {
            var p=route.track().pts();double x=p[i]/10.0,z=p[i+2]/10.0;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
        }}
        double[] f=MapFit.fit(minX,maxX,minZ,maxZ,aspect);
        return new Bounds(f[0],f[1],f[2],f[3]);
    }

    /** The vanilla-resolution (128 px) map, for item frames. */
    public static ResourceLocation get(BikeparkMap data) { return get(data,128); }

    public static ResourceLocation get(BikeparkMap data,int size) { return get(data,size,size,size); }

    /** A picture of {@code w} by {@code h} pixels, {@code unit} of them to a frame's worth of map (a wall of frames shows one big map). */
    public static ResourceLocation get(BikeparkMap data,int w,int h,int unit) {
        Dim d=new Dim(w,h,unit);
        Bounds b=bounds(data,d.aspect());
        ResourceLocation dimension=data.routes().isEmpty()?null:data.routes().getFirst().dimension();
        Minecraft mc=Minecraft.getInstance();
        boolean terrain=TerrainSampler.available(mc.level,dimension,b);
        Key key=new Key(data,w,h,unit,terrain);
        ResourceLocation cached=CACHE.get(key);if(cached!=null)return cached;
        NativeImage image=new NativeImage(w,h,false);
        paper(image,d);
        TerrainShader.Terrain sampled=terrain?TerrainSampler.sample(mc.level,b,w,h,unit):null;
        if(sampled!=null)terrain(image,sampled,d);
        scaleBar(image,b,d);
        northArrow(image,d);
        int casing=Math.max(1,unit/64),width=Math.max(1,unit/96),outer=width+casing,light=outer-Math.max(1,casing/2);
        // two-tone casing, dark outside and light inside (readable on any terrain), all casings first so crossing trails read as one network
        for(var route:data.routes()) if(route.dimension().equals(dimension))
            stroke(image,curve(route.track().pts(),b,w,h,unit),outer,CASE_DARK,d);
        for(var route:data.routes()) if(route.dimension().equals(dimension))
            stroke(image,curve(route.track().pts(),b,w,h,unit),light,CASE_LIGHT,d);
        for(var route:data.routes()) if(route.dimension().equals(dimension))
            stroke(image,curve(route.track().pts(),b,w,h,unit),width,difficultyOf(route).argb,d);
        for(var route:data.routes()) {
            if(!route.dimension().equals(dimension))continue;
            int[] p=route.track().pts();
            if(p.length<6)continue;
            startFlag(image,pixelX(p[0]/10.0,b,d),pixelY(p[2]/10.0,b,d),d);
            finishFlag(image,pixelX(p[p.length-3]/10.0,b,d),pixelY(p[p.length-1]/10.0,b,d),d);
        }
        DynamicTexture texture=new DynamicTexture(image);
        texture.setFilter(false,false);
        ResourceLocation id=mc.getTextureManager().register("descentmtb-trail-map",texture);CACHE.put(key,id);
        while(CACHE.size()>1&&(CACHE.size()>32||cachedPixels()>MAX_CACHED_PIXELS)){var first=CACHE.entrySet().iterator();var entry=first.next();mc.getTextureManager().release(entry.getValue());first.remove();}
        return id;
    }

    private static long cachedPixels() { long n=0;for(Key k:CACHE.keySet())n+=(long)k.w()*k.h();return n; }

    /** Drops every cached map (releasing the textures) so the next draw samples the world again, e.g. when the screen opens and more chunks have loaded. */
    public static void invalidate() {
        var textures=Minecraft.getInstance().getTextureManager();
        for(ResourceLocation id:CACHE.values())textures.release(id);
        CACHE.clear();
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

    private static int pixelX(double x,Bounds b,Dim d) { return (int)Math.round(d.margin()+(x-b.minX)/b.spanX*(d.w-2*d.margin())); }
    private static int pixelY(double z,Bounds b,Dim d) { return (int)Math.round(d.margin()+(z-b.minZ)/b.spanZ*(d.h-2*d.margin())); }

    /** Smoothed pixel path of a track (x, y pairs, sub-pixel). */
    public static double[] curve(int[] p,Bounds b,int size) { return curve(p,b,size,size,size); }

    public static double[] curve(int[] p,Bounds b,int w,int h,int unit) {
        int n=p.length/3;double[] xy=new double[n*2];
        double margin=unit*5/128.0;
        for(int i=0;i<n;i++){xy[i*2]=margin+(p[i*3]/10.0-b.minX)/b.spanX*(w-2*margin);xy[i*2+1]=margin+(p[i*3+2]/10.0-b.minZ)/b.spanZ*(h-2*margin);}
        return TrailDifficulty.smooth(xy,2,6);
    }

    // ------------------------------------------------------------------ painting

    /** Parchment: speckled paper, faint survey grid and a bevelled wooden frame (light from the top left). */
    private static void paper(NativeImage im,Dim dim) {
        int grid=dim.grid(),edge=dim.edge(),w=dim.w,h=dim.h;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c;
            if(x<edge||y<edge||x>=w-edge||y>=h-edge){
                boolean outer=x==0||y==0||x==w-1||y==h-1;
                boolean lit=(x<edge&&y<h-x)||(y<edge&&x<w-y);
                c=outer?FRAME_SHADOW:lit?FRAME_LIGHT:FRAME;
                if(!outer&&(x==edge-1||y==edge-1||x==w-edge||y==h-edge))c=lit?FRAME:FRAME_SHADOW;
            } else {
                int hv=hash(x,y);
                c=(hv&15)==0?PAPER_DARK:(hv&31)==1?PAPER_LIGHT:PAPER;
                if(x%grid==0||y%grid==0)c=GRID;
                // soft vignette towards the frame (ambient occlusion of the paper under the frame lip)
                int d=Math.min(Math.min(x,y),Math.min(w-1-x,h-1-y))-edge;
                if(d<2)c=mix(c,FRAME,d==0?.35f:.18f);
            }
            im.setPixelRGBA(x,y,abgr(c));
        }
    }

    /** Lays the shaded terrain over the parchment inside the frame; unsampled pixels keep the paper, with a faint survey cross at each grid intersection. */
    private static void terrain(NativeImage im,TerrainShader.Terrain t,Dim dim) {
        int edge=dim.edge(),grid=dim.grid(),w=dim.w,h=dim.h;
        int[] argb=TerrainShader.shade(t,edge);
        for(int y=edge;y<h-edge;y++)for(int x=edge;x<w-edge;x++){
            int c=argb[y*w+x];if(c==0)continue;
            int gx=x%grid,gy=y%grid;
            boolean cross=(gx==0&&(gy<=1||gy==grid-1))||(gy==0&&(gx<=1||gx==grid-1));
            if(cross)c=MapPalette.mix(c,PAPER_LIGHT,.32f);
            im.setPixelRGBA(x,y,abgr(c));
        }
    }

    // 3x5 pixel glyphs for the labels: rows top to bottom, '#' set
    private static final String GLYPH_CHARS="0123456789mN ";
    private static final String[][] GLYPHS=java.util.stream.Stream.of("###,#.#,#.#,#.#,###","##.,.#.,.#.,.#.,###","###,..#,###,#..,###","###,..#,###,..#,###",
            "#.#,#.#,###,..#,..#","###,#..,###,..#,###","###,#..,###,#.#,###","###,..#,..#,..#,..#","###,#.#,###,#.#,###","###,#.#,###,..#,###",
            "...,###,###,#.#,#.#","#.#,###,###,#.#,#.#","...,...,...,...,...").map(g->g.split(",")).toArray(String[][]::new);

    private static String[] textRows(String text) {
        String[] rows=new String[5];java.util.Arrays.fill(rows,"");
        for(int i=0;i<text.length();i++){
            int k=GLYPH_CHARS.indexOf(text.charAt(i));String[] g=GLYPHS[k<0?GLYPH_CHARS.length()-1:k];
            for(int r=0;r<5;r++)rows[r]+=g[r]+(i<text.length()-1?".":"");
        }
        return rows;
    }

    /**
     * Draws a sprite of {@code scale}-pixel cells: '#' ink, 'L' lit and 'D' shaded arrow, '.' empty, with a one-cell
     * halo around it so it reads on any terrain.
     */
    private static void sprite(NativeImage im,int x,int y,String[] rows,int scale,int halo,Dim size) {
        for(int pass=0;pass<2;pass++)for(int r=0;r<rows.length;r++)for(int c=0;c<rows[r].length();c++){
            char ch=rows[r].charAt(c);if(ch=='.')continue;
            int color=ch=='L'?ARROW_LIT:ch=='D'?ARROW_SHADE:INK;
            for(int dy=pass==0?-1:0;dy<=(pass==0?1:0);dy++)for(int dx=pass==0?-1:0;dx<=(pass==0?1:0);dx++)
                for(int sy=0;sy<scale;sy++)for(int sx=0;sx<scale;sx++)pixel(im,x+(c+dx)*scale+sx,y+(r+dy)*scale+sy,pass==0?halo:color,size);
        }
    }

    /** Scale bar with a block label in the bottom-left corner, its length a round number of blocks that fits a quarter of the map. */
    private static void scaleBar(NativeImage im,Bounds b,Dim size) {
        int s=size.scale(),edge=size.edge();
        double margin=size.margin(),pixelsPerBlock=(size.w-2*margin)/b.spanX;
        int blocks=MapPalette.scaleBarBlocks(1/pixelsPerBlock,Math.min(size.w,size.h)/4);
        int cells=Math.max(5,(int)Math.round(blocks*pixelsPerBlock/s));
        StringBuilder tick=new StringBuilder("#");
        for(int i=1;i<cells-1;i++)tick.append(i==cells/2?'#':'.');
        tick.append('#');
        int x=edge+4*s,y=size.h-edge-4*s-2*s;
        sprite(im,x,y,new String[] {tick.toString(),"#".repeat(cells)},s,HALO,size);
        sprite(im,x,y-7*s,textRows(MapPalette.scaleLabel(blocks)),s,HALO,size);
    }

    /** North arrow with an "N" in the top-right corner; north is up the map (towards -Z). */
    private static void northArrow(NativeImage im,Dim size) {
        int s=size.scale(),edge=size.edge();
        int x=size.w-edge-4*s-5*s,y=edge+4*s+7*s;
        sprite(im,x,y,new String[] {"..L..",".LLD.","LLLDD","..LD.","..LD."},s,CASE_DARK,size);
        sprite(im,x+s,y-7*s,textRows("N"),s,HALO,size);
    }

    private static void stroke(NativeImage im,double[] xy,int radius,int color,Dim size) {
        for(int i=2;i<xy.length;i+=2){
            double x0=xy[i-2],y0=xy[i-1],x1=xy[i],y1=xy[i+1];
            int n=(int)Math.ceil(Math.max(Math.abs(x1-x0),Math.abs(y1-y0))*2)+1;
            for(int s=0;s<=n;s++){double t=s/(double)n;disc(im,x0+(x1-x0)*t,y0+(y1-y0)*t,radius,color,size);}
        }
        if(xy.length==2)disc(im,xy[0],xy[1],radius,color,size);
    }

    private static void disc(NativeImage im,double cx,double cy,int r,int color,Dim size) {
        int x0=(int)Math.floor(cx-r),x1=(int)Math.ceil(cx+r),y0=(int)Math.floor(cy-r),y1=(int)Math.ceil(cy+r);
        double rr=(r+.35)*(r+.35);
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){double dx=x+.5-cx,dy=y+.5-cy;if(dx*dx+dy*dy<=rr)pixel(im,x,y,color,size);}
    }

    private static void startFlag(NativeImage im,int x,int y,Dim size) {
        int s=Math.max(2,size.unit/48);
        for(int dy=-s-2;dy<=s+2;dy++)for(int dx=-s-2;dx<=s+2;dx++)pixel(im,x+dx,y+dy,CASE_LIGHT,size);
        for(int dy=-s-1;dy<=s+1;dy++)for(int dx=-s-1;dx<=s+1;dx++)pixel(im,x+dx,y+dy,0xff2d4a2a,size);
        for(int dy=-s;dy<=s;dy++)for(int dx=-s;dx<=s;dx++)pixel(im,x+dx,y+dy,dy<0&&dx<0?0xff7fd36b:0xff3f9b4f,size);
    }

    private static void finishFlag(NativeImage im,int x,int y,Dim size) {
        int s=Math.max(2,size.unit/48);
        for(int dy=-s-2;dy<=s+2;dy++)for(int dx=-s-2;dx<=s+2;dx++)pixel(im,x+dx,y+dy,CASE_LIGHT,size);
        for(int dy=-s-1;dy<=s+1;dy++)for(int dx=-s-1;dx<=s+1;dx++)pixel(im,x+dx,y+dy,0xff2b2522,size);
        for(int dy=-s;dy<=s;dy++)for(int dx=-s;dx<=s;dx++)pixel(im,x+dx,y+dy,((dx+s)+(dy+s))%2==0?0xfff6efd9:0xff2b2522,size);
    }

    private static int hash(int x,int y){int h=x*374761393+y*668265263;h=(h^(h>>>13))*1274126177;return h^(h>>>16);}
    private static int mix(int a,int b,float t){
        int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)((a&255)*(1-t)+(b&255)*t);
        return 0xff000000|r<<16|g<<8|bl;
    }
    private static int abgr(int argb) { return argb&0xff00ff00 | (argb>>16)&255 | (argb&255)<<16; }
    private static void pixel(NativeImage im,int x,int y,int color,Dim size) {
        int edge=size.edge();
        if(x>=edge&&x<size.w-edge&&y>=edge&&y<size.h-edge)im.setPixelRGBA(x,y,abgr(color));
    }
}
