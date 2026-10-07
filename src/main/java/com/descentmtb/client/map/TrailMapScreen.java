package com.descentmtb.client.map;

import com.descentmtb.client.trail.TrailTimer;
import com.descentmtb.map.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * The bike-park map: smooth trails coloured by grade, a list with grade badges and personal bests, and an elevation
 * profile of the selected trail with height and distance axes, total descent and average gradient.
 */
public final class TrailMapScreen extends Screen {
    private final BikeparkMap data;
    private int selected;
    private static final int MAP_TEXTURE = 256, LIST_WIDTH = 200, ROW = 14, PROFILE_HEIGHT = 46;
    public TrailMapScreen(BikeparkMap data){super(Component.translatable("descentmtb.map.title"));this.data=data;}
    @Override protected void init(){
        selected=clampSelection(selected);
        addRenderableWidget(Button.builder(Component.translatable("descentmtb.audio.close"),b->onClose()).bounds(width/2-50,height-25,100,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}

    private int clampSelection(int index){return data.routes().isEmpty()?0:Math.max(0,Math.min(index,data.routes().size()-1));}
    private int mapSize(){return Math.max(64,Math.min(height-PROFILE_HEIGHT-95,width-LIST_WIDTH-35));}
    private int mapLeft(){return Math.max(10,(width-mapSize()-LIST_WIDTH)/2);}
    private static final int TOP=30;
    private int rows(){return Math.max(1,(mapSize()-20)/ROW);}
    private int firstRow(){return Math.max(0,selected-rows()+1);}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        selected=clampSelection(selected);
        renderBackground(g,mouseX,mouseY,partialTick);g.drawCenteredString(font,title,width/2,12,0xfff1bf);
        int size=mapSize(),left=mapLeft(),top=TOP;
        List<BikeparkMap.Route> routes=data.routes();
        BikeparkMap.Route route=routes.isEmpty()?null:routes.get(selected);
        BikeparkMap visible=route==null?data:new BikeparkMap(routes.stream().filter(r->r.dimension().equals(route.dimension())).toList());
        g.blit(MapTexture.get(visible,MAP_TEXTURE),left,top,size,size,0,0,MAP_TEXTURE,MAP_TEXTURE,MAP_TEXTURE,MAP_TEXTURE);
        MapTexture.Bounds bounds=MapTexture.bounds(visible);
        if(route!=null)highlight(g,route,bounds,left,top,size);
        ResourceLocation dimension=minecraft.level==null?null:minecraft.level.dimension().location();
        boolean here=route!=null&&route.dimension().equals(dimension);
        if(here&&minecraft.player!=null){
            int px=left+MapTexture.point(minecraft.player.getX(),bounds.minX(),bounds.span(),size),pz=top+MapTexture.point(minecraft.player.getZ(),bounds.minZ(),bounds.span(),size);
            if(px>=left&&px<left+size&&pz>=top&&pz<top+size){g.fill(px-3,pz-3,px+4,pz+4,0xff172b3a);g.fill(px-2,pz-2,px+3,pz+3,0xffffd25a);g.fill(px-1,pz-2,px+1,pz,0xfffff1c2);}
        }
        g.drawString(font,"N",left+size-10,top+6,0xff4a3a2e,false);

        int x=left+size+12;
        for(int i=firstRow();i<Math.min(routes.size(),firstRow()+rows());i++){
            var r=routes.get(i);int y=top+4+(i-firstRow())*ROW;
            boolean sel=i==selected;
            if(sel)g.fill(x-3,y-3,x+LIST_WIDTH-12,y+ROW-3,0x40ffe8a8);
            badge(g,MapTexture.difficultyOf(r),x,y);
            String name=r.name().isBlank()?"Trail "+(i+1):r.name();
            int best=TrailTimer.bestMs(name);
            String time=best>0?time(best):"";
            int nameWidth=LIST_WIDTH-30-font.width(time)-6;
            g.drawString(font,font.plainSubstrByWidth(name,nameWidth),x+12,y,sel?0xffefba64:0xffd7dedb,false);
            if(!time.isEmpty())g.drawString(font,time,x+LIST_WIDTH-18-font.width(time),y,0xff9fd6a6,false);
        }
        if(routes.isEmpty()){
            var lines=font.split(Component.translatable("descentmtb.map.empty"),size-24);
            int y=top+(size-lines.size()*font.lineHeight)/2;
            for(var line:lines){g.drawString(font,line,left+(size-font.width(line))/2,y,0xff443b2d,false);y+=font.lineHeight;}
        } else {
            var stats=route.track().stats();
            TrailDifficulty grade=MapTexture.difficultyOf(route);
            TrailDifficulty.Profile shape=TrailDifficulty.profile(route.track().pts());
            String line=String.format(Locale.ROOT,"%s   %.0f m   ↓ %.0f m   ↑ %.0f m   avg %.0f %%   max %.0f %%",
                    grade.label,stats.length(),stats.descent(),stats.ascent(),shape.averageGrade(),shape.steepestGrade());
            g.drawString(font,line,left,top+size+6,0xffd7dedb,false);
            g.drawString(font,route.dimension().toString(),x,top+size-12,0xff8faba7,false);
            profile(g,route.track(),left+26,top+size+20,size+LIST_WIDTH-38,PROFILE_HEIGHT);
        }
        // Screen.render would blur the background a second time, including the finished map.
        for(var widget:renderables)widget.render(g,mouseX,mouseY,partialTick);
    }

    /** The selected trail drawn over the map with a light halo, so it stands out from the network. */
    private void highlight(GuiGraphics g,BikeparkMap.Route route,MapTexture.Bounds b,int left,int top,int size){
        double[] xy=MapTexture.curve(route.track().pts(),b,size);
        int color=MapTexture.difficultyOf(route).argb;
        for(int i=0;i<xy.length;i+=2){int px=left+(int)xy[i],py=top+(int)xy[i+1];g.fill(px-2,py-2,px+2,py+2,0x90fff6dc);}
        for(int i=0;i<xy.length;i+=2){int px=left+(int)xy[i],py=top+(int)xy[i+1];g.fill(px-1,py-1,px+1,py+1,color);}
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

    /** Elevation profile: height (Y) over distance, shaded by local steepness, with axis labels. */
    private void profile(GuiGraphics g,TrailTrack track,int x,int y,int w,int h){
        int[] p=track.pts();if(p.length<6)return;
        int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;for(int i=1;i<p.length;i+=3){low=Math.min(low,p[i]);high=Math.max(high,p[i]);}
        g.fill(x-1,y-1,x+w+1,y+h+1,0xff0d151b);g.fill(x,y,x+w,y+h,0xff15212a);
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
        g.drawString(font,(high/10)+"",x-4-font.width((high/10)+""),y,0xff8faba7,false);
        g.drawString(font,(low/10)+"",x-4-font.width((low/10)+""),y+h-8,0xff8faba7,false);
        String end=String.format(Locale.ROOT,"%.0f m",track.stats().length());
        g.drawString(font,"0",x,y+h+3,0xff8faba7,false);
        g.drawString(font,end,x+w-font.width(end),y+h+3,0xff8faba7,false);
        g.drawString(font,"Y",x-4-font.width("Y"),y+h/2-4,0xff5f7b77,false);
    }

    @Override public boolean mouseClicked(double mx,double my,int button){
        int size=mapSize(),left=mapLeft(),index=firstRow()+(int)Math.floor((my-TOP-1)/ROW);
        if(button==0&&mx>left+size&&my>=TOP+1&&my<TOP+1+rows()*ROW&&index>=0&&index<data.routes().size()){selected=index;return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!data.routes().isEmpty()){selected=Math.floorMod(selected+(dy<0?1:-1),data.routes().size());return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(!data.routes().isEmpty()&&(key==264||key==265)){selected=Math.floorMod(selected+(key==264?1:-1),data.routes().size());return true;} // down / up arrows
        return super.keyPressed(key,scan,mods);
    }
}
