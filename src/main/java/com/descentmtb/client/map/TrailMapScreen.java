package com.descentmtb.client.map;

import com.descentmtb.client.trail.TrailTimer;
import com.descentmtb.map.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import com.descentmtb.client.ui.DescentScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The bike-park map as an interactive map view: a big zoomable, draggable map on the left, a side panel with the route
 * list and the details of the selected trail, and the elevation profile along the bottom. The mouse wheel zooms
 * around the cursor, dragging pans, hovering a trail (or a list row) highlights it with a tooltip, a click selects it,
 * the player shows as a heading arrow and hovering the profile marks the matching spot on the trail. All the view
 * maths lives in {@link MapView}; this class only lays out and draws.
 */
public final class TrailMapScreen extends DescentScreen {
    private static final int TEX = 512, ROW = 14, LINE = 10, MARGIN = 8, GAP = 8, TOP = 24, HIT = 5;
    private static final int PANEL = 0xff15212a, EDGE = 0xff0b1217, TEXT = 0xffd7dedb, MUTED = 0xff8faba7, SHADE = 0xc00f171d;

    private final BikeparkMap data;
    private final MapView view = new MapView(TEX);
    private int selected;
    private Layer layer;
    private int mapX, mapY, mapS, panelX, panelY, panelW, panelH, detailsH, rows, profX, profY, profW, profH;
    private boolean dragging;
    private double dragDistance, mouseX, mouseY;
    private long lastClick;
    private int lastRow = -1;
    private MapView.Sample sample;

    /** The routes shown on the map: those in the selected route's dimension, as a texture-space polyline each. */
    private record Layer(ResourceLocation dimension, BikeparkMap visible, MapTexture.Bounds bounds, int[] index, double[][] curves) {}

    public TrailMapScreen(BikeparkMap data){super(Component.translatable("descentmtb.map.title"));this.data=data;}

    @Override protected void init(){
        MapTexture.invalidate(); // repaint the terrain: chunks loaded since the map was last drawn now show up
        selected=clampSelection(selected);
        layout();
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.audio.close"),b->onClose()).bounds(width-MARGIN-56,4,56,14).build());
        Component label=Component.translatable("descentmtb.map.reset");
        addRenderableWidget(Button.builder(label,b->view.reset()).bounds(mapX+4,mapY+4,font.width(label)+10,14)
                .tooltip(Tooltip.create(Component.translatable("descentmtb.map.reset_tooltip"))).build());
    }
    @Override public boolean isPauseScreen(){return false;}

    private int clampSelection(int index){return data.routes().isEmpty()?0:Math.max(0,Math.min(index,data.routes().size()-1));}
    private BikeparkMap.Route selectedRoute(){return data.routes().isEmpty()?null:data.routes().get(clampSelection(selected));}
    private String routeName(int i){String n=data.routes().get(i).name();return n.isBlank()?"Trail "+(i+1):n;}
    private int firstRow(){return Math.max(0,selected-rows+1);}

    /** Map left (square), panel right, profile along the bottom; sizes are clamped so nothing overlaps on small windows. */
    private void layout(){
        profH=Math.max(18,Math.min(50,(height-TOP)/6));profY=height-MARGIN-12-profH;
        int available=Math.max(48,profY-4-TOP);
        mapS=Math.max(48,Math.min(available,width-2*MARGIN-GAP-140));
        panelW=Math.max(60,Math.min(250,width-2*MARGIN-GAP-mapS));panelH=available;
        mapX=Math.max(0,(width-mapS-GAP-panelW)/2);mapY=TOP;panelX=mapX+mapS+GAP;panelY=TOP;
        profX=mapX+30;profW=Math.max(20,panelX+panelW-profX);
        detailsH=Math.min((3+(6+columns()-1)/columns()+1)*LINE+8,panelH*55/100);
        rows=Math.max(1,(panelH-detailsH-6)/ROW);
        view.setRect(mapX,mapY,mapS,mapS);
    }
    private int columns(){return panelW>=236?2:1;}

    /** Curves of the routes sharing the selected route's dimension; rebuilt (and the view reset) when that changes. */
    private Layer layer(BikeparkMap.Route route){
        if(route==null)return null;
        if(layer!=null&&layer.dimension.equals(route.dimension()))return layer;
        List<BikeparkMap.Route> all=data.routes(),same=new ArrayList<>();int[] index=new int[all.size()];int n=0;
        for(int i=0;i<all.size();i++)if(all.get(i).dimension().equals(route.dimension())){same.add(all.get(i));index[n++]=i;}
        BikeparkMap visible=new BikeparkMap(same);MapTexture.Bounds b=MapTexture.bounds(visible);
        double[][] curves=new double[n][];for(int k=0;k<n;k++)curves[k]=MapTexture.curve(same.get(k).track().pts(),b,TEX);
        view.reset();
        return layer=new Layer(route.dimension(),visible,b,java.util.Arrays.copyOf(index,n),curves);
    }
    private double[] curveOf(int routeIndex){
        if(layer!=null)for(int k=0;k<layer.index.length;k++)if(layer.index[k]==routeIndex)return layer.curves[k];
        return null;
    }

    // ------------------------------------------------------------------ hit-testing

    /** List index of the route within {@link #HIT} screen pixels of (mx, my) on the map, or -1. */
    private int routeAt(double mx,double my){
        if(layer==null||!view.contains(mx,my))return -1;
        double tx=view.texX(mx),ty=view.texY(my),best=HIT/view.scale();int found=-1;
        for(int k=0;k<layer.curves.length;k++){
            var n=MapView.nearest(layer.curves[k],tx,ty);
            if(n!=null&&n.distance()<=best){best=n.distance();found=layer.index[k];}
        }
        return found;
    }
    private int rowAt(double mx,double my){
        if(mx<panelX||mx>=panelX+panelW||my<panelY+1||my>=panelY+1+rows*ROW)return -1;
        int i=firstRow()+(int)Math.floor((my-panelY-1)/ROW);
        return i>=0&&i<data.routes().size()?i:-1;
    }

    // ------------------------------------------------------------------ render

    @Override public void render(GuiGraphics g,int mx,int my,float partialTick){
        mouseX=mx;mouseY=my;selected=clampSelection(selected);
        renderBackground(g,mx,my,partialTick);
        g.drawString(font,title,mapX,8,0xfff1bf,false);
        int hintX=mapX+font.width(title)+14,hintW=width-MARGIN-56-8-hintX;
        if(hintW>60)g.drawString(font,font.plainSubstrByWidth(Component.translatable("descentmtb.map.controls").getString(),hintW),hintX,8,0xff5f7b77,false);
        BikeparkMap.Route route=selectedRoute();
        Layer layer=layer(route);
        int listHover=rowAt(mx,my),hover=listHover>=0?listHover:dragging?-1:routeAt(mx,my);
        sample=route==null?null:profileSample(route,mx,my);
        drawMap(g,route,layer,hover,partialTick);
        drawPanel(g,route,listHover);
        if(route!=null)drawProfile(g,route);
        // Screen.render would blur the background a second time, including the finished map.
        for(var widget:renderables)widget.render(g,mx,my,partialTick);
        if(hover>=0&&listHover<0&&!dragging)g.renderComponentTooltip(font,tooltip(hover),mx,my);
    }

    private void drawMap(GuiGraphics g,BikeparkMap.Route route,Layer layer,int hover,float partialTick){
        int x=mapX,y=mapY,s=mapS;
        g.fill(x-1,y-1,x+s+1,y+s+1,EDGE);
        g.enableScissor(x,y,x+s,y+s);
        g.fill(x,y,x+s,y+s,0xff10181e);
        PoseStack pose=g.pose();pose.pushPose();
        pose.translate((float)view.screenX(0),(float)view.screenY(0),0);pose.scale((float)view.scale(),(float)view.scale(),1);
        g.blit(MapTexture.get(layer==null?data:layer.visible,TEX),0,0,TEX,TEX,0,0,TEX,TEX,TEX,TEX);
        pose.popPose();
        if(layer!=null){
            if(hover>=0&&hover!=selected)trace(g,curveOf(hover),MapTexture.difficultyOf(data.routes().get(hover)).argb,0xffffffff);
            double[] sel=curveOf(selected);
            trace(g,sel,MapTexture.difficultyOf(route).argb,0xffffe9a8);
            markers(g,route,sel);
            if(sample!=null){
                double[] t=MapTexture.curve(new int[]{TrackGeometry.pack(sample.x()),0,TrackGeometry.pack(sample.z())},layer.bounds,TEX);
                int dx=(int)Math.round(view.screenX(t[0])),dy=(int)Math.round(view.screenY(t[1]));
                disc(g,dx,dy,5,0xff10181e);disc(g,dx,dy,4,0xffffffff);disc(g,dx,dy,2,MapTexture.difficultyOf(route).argb);
            }
            player(g,layer,partialTick);
        }
        g.disableScissor();
        if(layer==null){
            var lines=font.split(Component.translatable("descentmtb.map.empty"),s-24);
            int ty=y+(s-lines.size()*font.lineHeight)/2;
            for(var line:lines){g.drawString(font,line,x+(s-font.width(line))/2,ty,0xff443b2d,false);ty+=font.lineHeight;}
        }
        // map furniture: north, zoom, legend
        Component north=Component.translatable("descentmtb.map.north");
        g.fill(x+s-15,y+4,x+s-4,y+15,SHADE);g.drawString(font,north,x+s-9-font.width(north)/2,y+6,0xfff1bf,false);
        if(view.zoom()>1.001){String z=String.format(Locale.ROOT,"%.1fx",view.zoom());g.fill(x+s-font.width(z)-8,y+s-14,x+s-4,y+s-4,SHADE);g.drawString(font,z,x+s-font.width(z)-6,y+s-13,MUTED,false);}
        if(s>=150)legend(g,x+4,y+s-4);
    }

    /** A route's smooth curve as a haloed line, in screen space through the view transform. */
    private void trace(GuiGraphics g,double[] xy,int color,int halo){
        if(xy==null||xy.length<2)return;
        double[] sc=new double[xy.length];
        for(int i=0;i<xy.length;i+=2){sc[i]=view.screenX(xy[i]);sc[i+1]=view.screenY(xy[i+1]);}
        for(int pass=0;pass<2;pass++)
            for(int i=2;i<sc.length;i+=2){
                double x0=sc[i-2],y0=sc[i-1],x1=sc[i],y1=sc[i+1];
                if(Math.max(x0,x1)<mapX-6||Math.min(x0,x1)>mapX+mapS+6||Math.max(y0,y1)<mapY-6||Math.min(y0,y1)>mapY+mapS+6)continue;
                line(g,x0,y0,x1,y1,pass==0?2:1,pass==0?halo:color);
            }
    }

    /** Start (green disc) and finish (chequered) on top of the selected route, with its name by the start. */
    private void markers(GuiGraphics g,BikeparkMap.Route route,double[] xy){
        if(xy==null||xy.length<4)return;
        int sx=(int)Math.round(view.screenX(xy[0])),sy=(int)Math.round(view.screenY(xy[1]));
        int fx=(int)Math.round(view.screenX(xy[xy.length-2])),fy=(int)Math.round(view.screenY(xy[xy.length-1]));
        g.fill(fx-5,fy-5,fx+5,fy+5,0xff2b2522);
        for(int cy=0;cy<4;cy++)for(int cx=0;cx<4;cx++)g.fill(fx-4+cx*2,fy-4+cy*2,fx-2+cx*2,fy-2+cy*2,(cx+cy)%2==0?0xfff6efd9:0xff2b2522);
        disc(g,sx,sy,6,0xff1b2a1d);disc(g,sx,sy,5,0xff3f9b4f);disc(g,sx-1,sy-1,2,0xffa6f0a0);
        String name=routeName(selected);int w=font.width(name);
        int lx=Math.max(mapX+3,Math.min(sx+9,mapX+mapS-w-5)),ly=sy-16<mapY+2?sy+8:sy-16;
        g.fill(lx-3,ly-2,lx+w+3,ly+font.lineHeight+1,0xd00f171d);g.fill(lx-3,ly-2,lx-1,ly+font.lineHeight+1,MapTexture.difficultyOf(route).argb|0xff000000);
        g.drawString(font,name,lx+1,ly,0xfff1bf,false);
    }

    /** The player: a heading arrow (vanilla yaw 0 = south/+Z, 90 = west/-X), pinned to the edge pointing at them when off screen. */
    private void player(GuiGraphics g,Layer layer,float partialTick){
        if(minecraft==null||minecraft.player==null||minecraft.level==null||!layer.dimension.equals(minecraft.level.dimension().location()))return;
        var p=minecraft.player;
        double[] t=MapTexture.curve(new int[]{TrackGeometry.pack(p.getX()),0,TrackGeometry.pack(p.getZ())},layer.bounds,TEX);
        double sx=view.screenX(t[0]),sy=view.screenY(t[1]);
        double[] pin=MapView.pin(mapX,mapY,mapS,mapS,sx,sy,8);
        boolean inside=Math.abs(pin[0]-sx)<1e-6&&Math.abs(pin[1]-sy)<1e-6;
        arrow(g,(float)pin[0],(float)pin[1],inside?(float)(Math.toRadians(p.getViewYRot(partialTick))+Math.PI):(float)pin[2]);
    }

    /** Arrow pointing up, rotated clockwise by {@code angle}: a light outline round a blue head. */
    private static void arrow(GuiGraphics g,float x,float y,float angle){
        PoseStack pose=g.pose();pose.pushPose();pose.translate(x,y,0);pose.mulPose(Axis.ZP.rotation(angle));
        for(int r=-8;r<=5;r++){int hw=(r+8)*6/13;g.fill(-hw,r,hw+1,r+1,0xfff7f2de);}
        for(int r=-5;r<=3;r++){int hw=(r+5)*3/8;g.fill(-hw,r,hw+1,r+1,0xff2d86f0);}
        pose.popPose();
    }

    /** Grade legend: green circle, blue square, red square, black diamond. */
    private void legend(GuiGraphics g,int x,int bottom){
        int w=0;for(var d:TrailDifficulty.values())w=Math.max(w,font.width(gradeName(d)));
        int h=TrailDifficulty.values().length*11+4,y=bottom-h;
        g.fill(x,y,x+w+22,y+h,SHADE);
        for(var d:TrailDifficulty.values()){badge(g,d,x+4,y+3+d.ordinal()*11);g.drawString(font,gradeName(d),x+16,y+3+d.ordinal()*11,TEXT,false);}
    }

    private void drawPanel(GuiGraphics g,BikeparkMap.Route route,int listHover){
        g.fill(panelX-1,panelY-1,panelX+panelW+1,panelY+panelH+1,EDGE);g.fill(panelX,panelY,panelX+panelW,panelY+panelH,PANEL);
        var routes=data.routes();int x=panelX+6;
        for(int i=firstRow();i<Math.min(routes.size(),firstRow()+rows);i++){
            var r=routes.get(i);int y=panelY+4+(i-firstRow())*ROW;
            boolean sel=i==selected;
            if(sel)g.fill(panelX+2,y-3,panelX+panelW-2,y+ROW-3,0x40ffe8a8);else if(i==listHover)g.fill(panelX+2,y-3,panelX+panelW-2,y+ROW-3,0x20ffffff);
            badge(g,MapTexture.difficultyOf(r),x,y);
            int best=TrailTimer.bestMs(routeName(i));String time=best>0?time(best):"";
            g.drawString(font,font.plainSubstrByWidth(routeName(i),panelW-12-14-(time.isEmpty()?0:font.width(time)+6)),x+12,y,sel?0xffefba64:TEXT,false);
            if(!time.isEmpty())g.drawString(font,time,panelX+panelW-6-font.width(time),y,0xff9fd6a6,false);
        }
        if(route!=null){g.fill(panelX+4,panelY+panelH-detailsH-1,panelX+panelW-4,panelY+panelH-detailsH,0xff2a3b47);details(g,route);}
    }

    /** Details of the selected trail, most important first; lines that do not fit are dropped. */
    private void details(GuiGraphics g,BikeparkMap.Route route){
        var stats=route.track().stats();var shape=TrailDifficulty.profile(route.track().pts());var grade=MapTexture.difficultyOf(route);
        int best=TrailTimer.bestMs(routeName(selected)),cols=columns();
        List<Component[]> lines=new ArrayList<>();
        lines.add(new Component[]{Component.literal(routeName(selected))});
        lines.add(new Component[]{gradeName(grade).copy().withStyle(s->s.withColor(TextColor.fromRgb(light(grade)&0xffffff)))});
        lines.add(new Component[]{best>0?stat("best",time(best)):Component.translatable("descentmtb.map.no_time")});
        Component[] cells={stat("length",metres(stats.length())),stat("descent",metres(stats.descent())),stat("avg_grade",percent(shape.averageGrade())),
                stat("steepest",percent(shape.steepestGrade())),stat("ascent",metres(stats.ascent())),stat("biggest_drop",String.format(Locale.ROOT,"%.1f m",shape.biggestDrop()))};
        for(int i=0;i<cells.length;i+=cols)lines.add(cols==2?new Component[]{cells[i],cells[i+1]}:new Component[]{cells[i]});
        lines.add(new Component[]{Component.literal(route.dimension().toString())});
        int x=panelX+6,w=panelW-12,y=panelY+panelH-detailsH+3,bottom=panelY+panelH-2;
        for(int r=0;r<lines.size();r++,y+=LINE){
            if(y+LINE>bottom)break;
            if(r==0){badge(g,MapTexture.difficultyOf(route),x,y);text(g,lines.get(0)[0],x+12,y,w-12,0xffefba64);continue;}
            int color=r==lines.size()-1?MUTED:r==2&&best<=0?MUTED:r==2?0xff9fd6a6:TEXT;
            for(int c=0;c<lines.get(r).length;c++)text(g,lines.get(r)[c],x+c*w/2,y,lines.get(r).length==2?w/2-4:w,color);
        }
    }

    private void text(GuiGraphics g,Component c,int x,int y,int maxWidth,int color){
        g.drawString(font,Language.getInstance().getVisualOrder(font.substrByWidth(c,Math.max(8,maxWidth))),x,y,color,false);
    }
    private static Component stat(String key,String value){return Component.translatable("descentmtb.map."+key,value);}
    private static Component gradeName(TrailDifficulty d){return Component.translatable("descentmtb.map.grade."+d.name().toLowerCase(Locale.ROOT));}
    private static String metres(double v){return String.format(Locale.ROOT,"%.0f m",v);}
    private static String percent(double v){return String.format(Locale.ROOT,"%.0f %%",v);}

    /** Grade colour that stays readable as text on a dark background. */
    private static int light(TrailDifficulty d){
        if(d==TrailDifficulty.BLACK)return 0xffd8d0bc;
        int c=d.argb;return 0xff000000|((c>>16&255)+(255-(c>>16&255))*2/5)<<16|((c>>8&255)+(255-(c>>8&255))*2/5)<<8|((c&255)+(255-(c&255))*2/5);
    }

    private List<Component> tooltip(int index){
        var route=data.routes().get(index);var stats=route.track().stats();var shape=TrailDifficulty.profile(route.track().pts());var grade=MapTexture.difficultyOf(route);
        List<Component> out=new ArrayList<>();
        out.add(Component.literal(routeName(index)));
        out.add(gradeName(grade).copy().withStyle(s->s.withColor(TextColor.fromRgb(light(grade)&0xffffff))));
        out.add(stat("length",metres(stats.length())).copy().append("   ").append(stat("descent",metres(stats.descent()))));
        out.add(stat("avg_grade",percent(shape.averageGrade())));
        int best=TrailTimer.bestMs(routeName(index));
        if(best>0)out.add(stat("best",time(best)));
        return out;
    }

    // ------------------------------------------------------------------ elevation profile

    /** The track point under the mouse while it hovers the profile, else null. */
    private MapView.Sample profileSample(BikeparkMap.Route route,double mx,double my){
        if(mx<profX||mx>=profX+profW||my<profY||my>=profY+profH||route.track().pts().length<6)return null;
        return MapView.sampleAt(route.track().pts(),(mx-profX)/(profW-1));
    }

    /** Elevation profile: height (Y) over distance, shaded by local steepness, with axis labels and a hover crosshair. */
    private void drawProfile(GuiGraphics g,BikeparkMap.Route route){
        TrailTrack track=route.track();int[] p=track.pts();if(p.length<6)return;
        int x=profX,y=profY,w=profW,h=profH;
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;for(int i=1;i<p.length;i+=3){low=Math.min(low,p[i]);high=Math.max(high,p[i]);}
        g.fill(x-1,y-1,x+w+1,y+h+1,EDGE);g.fill(x,y,x+w,y+h,PANEL);
        for(int k=1;k<4;k++)g.fill(x,y+h*k/4,x+w,y+h*k/4+1,0xff1d2c37);
        double total=Math.max(1,track.stats().length()*10),distance=0;double range=Math.max(1,high-low);
        for(int i=3;i<p.length;i+=3){
            double run=Math.hypot((double)p[i]-p[i-3],(double)p[i+2]-p[i-1]);double previous=distance;distance+=run;
            int px=x+(int)(previous/total*w),next=x+(int)(distance/total*w);
            int py=y+h-1-(int)((p[i+1]-low)/range*(h-3));
            double grade=run<1e-6?0:(p[i-2]-p[i+1])/run*100; // positive = downhill
            int fill=grade>=30?0xffb4503f:grade>=15?0xffc98a3c:grade>=5?0xff65a88a:0xff4f8a9e;
            g.fill(px,py,Math.max(px+1,next),y+h,fill);
            g.fill(px,py,Math.max(px+1,next),py+1,0xffe9f1e4);
        }
        g.drawString(font,(high/10)+"",x-4-font.width((high/10)+""),y,MUTED,false);
        g.drawString(font,(low/10)+"",x-4-font.width((low/10)+""),y+h-8,MUTED,false);
        String end=metres(track.stats().length());
        g.drawString(font,"0",x,y+h+3,MUTED,false);
        g.drawString(font,end,x+w-font.width(end),y+h+3,MUTED,false);
        g.drawString(font,"Y",x-4-font.width("Y"),y+h/2-4,0xff5f7b77,false);
        if(sample!=null){
            int cx=(int)Math.max(x,Math.min(x+w-1,mouseX)),cy=y+h-1-(int)((sample.height()*TrackGeometry.UNIT-low)/range*(h-3));
            g.fill(cx,y,cx+1,y+h,0xd0ffffff);g.fill(cx-2,cy-2,cx+3,cy+3,0xff10181e);g.fill(cx-1,cy-1,cx+2,cy+2,0xffffe9a8);
            String label=String.format(Locale.ROOT,"Y %.0f   %.0f m",sample.height(),sample.distance());int lw=font.width(label);
            int lx=cx+6+lw+4>x+w?cx-6-lw-4:cx+6;
            g.fill(lx,y+2,lx+lw+6,y+font.lineHeight+4,SHADE);g.drawString(font,label,lx+3,y+4,0xfff1bf,false);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A straight line of 2*{@code half} pixels thickness, drawn as one rotated, stretched quad. */
    private static void line(GuiGraphics g,double x0,double y0,double x1,double y1,int half,int color){
        double dx=x1-x0,dy=y1-y0,len=Math.hypot(dx,dy);
        if(len<1e-6)return;
        PoseStack pose=g.pose();pose.pushPose();
        pose.translate((float)x0,(float)y0,0);pose.mulPose(Axis.ZP.rotation((float)Math.atan2(dy,dx)));pose.scale((float)len+.6f,1,1);
        g.fill(0,-half,1,half,color);
        pose.popPose();
    }

    private static void disc(GuiGraphics g,int cx,int cy,int r,int color){
        for(int dy=-r;dy<=r;dy++){int half=(int)Math.round(Math.sqrt(r*r+r*.5-dy*dy));g.fill(cx-half,cy+dy,cx+half+1,cy+dy+1,color);}
    }

    /** Trail-sign badge: green circle, blue square, red square, black diamond (drawn in pixels). */
    private void badge(GuiGraphics g,TrailDifficulty d,int x,int y){
        int c=d.argb;
        switch(d){
            case GREEN->{g.fill(x+1,y,x+8,y+8,0xff1f2a22);g.fill(x,y+1,x+9,y+7,0xff1f2a22);g.fill(x+2,y+1,x+7,y+7,c);g.fill(x+1,y+2,x+8,y+6,c);g.fill(x+2,y+2,x+4,y+3,0xff8fe08a);}
            case BLUE,RED->{g.fill(x,y,x+9,y+8,0xff1b1f26);g.fill(x+1,y+1,x+8,y+7,c);g.fill(x+1,y+1,x+8,y+2,d==TrailDifficulty.BLUE?0xff6fa3e8:0xfff07a5c);}
            case BLACK->{for(int i=0;i<5;i++){g.fill(x+4-i,y+i,x+5+i,y+i+1,0xffd8d0bc);g.fill(x+4-i,y+8-i,x+5+i,y+9-i,0xffd8d0bc);}
                for(int i=0;i<4;i++){g.fill(x+4-i,y+1+i,x+5+i,y+2+i,c);g.fill(x+4-i,y+7-i,x+5+i,y+8-i,c);}}
        }
    }

    private static String time(int ms){int m=ms/60000,s=ms/1000%60,c=ms/10%100;return m>0?String.format(Locale.ROOT,"%d:%02d.%02d",m,s,c):String.format(Locale.ROOT,"%d.%02d",s,c);}

    // ------------------------------------------------------------------ input

    private void select(int index){selected=clampSelection(index);}

    /** Zooms around the cursor when it is over the map, else around the map's centre. */
    private void zoom(double factor){
        if(view.contains(mouseX,mouseY))view.zoomAt(mouseX,mouseY,factor);else view.zoomCentre(factor);
    }

    /** Zooms the map to the selected route. */
    private void focus(){
        double[] xy=curveOf(selected);if(xy==null||xy.length<2)return;
        double x0=Double.MAX_VALUE,y0=x0,x1=-x0,y1=-x0;
        for(int i=0;i<xy.length;i+=2){x0=Math.min(x0,xy[i]);x1=Math.max(x1,xy[i]);y0=Math.min(y0,xy[i+1]);y1=Math.max(y1,xy[i+1]);}
        view.fit(x0,y0,x1,y1,14);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        if(super.mouseClicked(mx,my,button))return true;
        if(button!=0)return false;
        int row=rowAt(mx,my);
        if(row>=0){
            long now=System.currentTimeMillis();
            select(row);
            if(row==lastRow&&now-lastClick<350){focus();lastRow=-1;}else{lastRow=row;lastClick=now;}
            return true;
        }
        if(view.contains(mx,my)){dragging=true;dragDistance=0;return true;}
        return false;
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(dragging&&button==0){view.pan(dx,dy);dragDistance+=Math.abs(dx)+Math.abs(dy);return true;}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(dragging&&button==0){
            dragging=false;
            if(dragDistance<3){int hit=routeAt(mx,my);if(hit>=0)select(hit);}
            return true;
        }
        return super.mouseReleased(mx,my,button);
    }
    @Override public void mouseMoved(double mx,double my){mouseX=mx;mouseY=my;super.mouseMoved(mx,my);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(view.contains(x,y)){view.zoomAt(x,y,Math.pow(1.25,Math.signum(dy)));return true;}
        if(!data.routes().isEmpty()){selected=Math.floorMod(selected+(dy<0?1:-1),data.routes().size());return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!data.routes().isEmpty()&&(key==264||key==265)){selected=Math.floorMod(selected+(key==264?1:-1),data.routes().size());return true;} // down / up arrows
        switch(key){
            case 82->{view.reset();return true;}                       // R
            case 70->{focus();return true;}                            // F
            case 61,334->{zoom(1.25);return true;}                     // + (= key, keypad)
            case 45,333->{zoom(.8);return true;}                       // - (minus key, keypad)
            default->{}
        }
        return super.keyPressed(key,scan,mods);
    }
}
