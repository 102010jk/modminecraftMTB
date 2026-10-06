package com.descentmtb.client.map;

import com.descentmtb.map.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class TrailMapScreen extends Screen {
    private final BikeparkMap data;
    private int selected;
    public TrailMapScreen(BikeparkMap data){super(Component.translatable("descentmtb.map.title"));this.data=data;}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("descentmtb.audio.close"),b->onClose()).bounds(width/2-50,height-25,100,20).build());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g,mouseX,mouseY,partialTick);g.drawCenteredString(font,title,width/2,12,0xfff1bf);
        int size=Math.max(64,Math.min(height-125,width-225)),left=Math.max(10,(width-size-200)/2),top=35;
        var selectedDimension=data.routes().isEmpty()?null:data.routes().get(selected).dimension();
        BikeparkMap visible=new BikeparkMap(data.routes().stream().filter(r->r.dimension().equals(selectedDimension)).toList());
        g.blit(MapTexture.get(visible),left,top,size,size,0,0,128,128,128,128);
        MapTexture.Bounds bounds=MapTexture.bounds(visible);
        ResourceLocation dimension=minecraft.level==null?null:minecraft.level.dimension().location();
        boolean here=visible.routes().stream().anyMatch(r->r.dimension().equals(dimension));
        if(here&&minecraft.player!=null){int px=left+MapTexture.point(minecraft.player.getX(),bounds.minX(),bounds.span())*size/128,pz=top+MapTexture.point(minecraft.player.getZ(),bounds.minZ(),bounds.span())*size/128;
            if(px>=left&&px<left+size&&pz>=top&&pz<top+size){g.fill(px-3,pz-3,px+4,pz+4,0xff172b3a);g.fill(px-1,pz-1,px+2,pz+2,0xffffffff);}}
        int x=left+size+12;
        int rows=Math.max(1,(size-20)/13),first=Math.max(0,selected-rows+1);
        for(int i=first;i<Math.min(data.routes().size(),first+rows);i++){var r=data.routes().get(i);g.drawString(font,(i==selected?"> ":"  ")+font.plainSubstrByWidth(r.name().isBlank()?"Trail "+(i+1):r.name(),155),x,top+6+(i-first)*13,i==selected?0xffefba64:0xffd7dedb,false);}
        if(data.routes().isEmpty()){
            var lines=font.split(Component.translatable("descentmtb.map.empty"),size-24);
            int y=top+(size-lines.size()*font.lineHeight)/2;
            for(var line:lines){g.drawString(font,line,left+(size-font.width(line))/2,y,0xff443b2d,false);y+=font.lineHeight;}
        }
        else {var route=data.routes().get(Math.min(selected,data.routes().size()-1));var stats=route.track().stats();
            g.drawString(font,String.format(java.util.Locale.ROOT,"%.0f m   ↑ %.0f m   ↓ %.0f m",stats.length(),stats.ascent(),stats.descent()),left,top+size+7,0xffd7dedb,false);
            g.drawString(font,route.dimension().toString(),x,top+size-12,0xff8faba7,false);profile(g,route.track(),left,top+size+23,size,25);
        }
        // Screen.render would blur the background a second time, including the finished map.
        for(var widget:renderables)widget.render(g,mouseX,mouseY,partialTick);
    }
    private static void profile(GuiGraphics g,TrailTrack track,int x,int y,int width,int height){
        int[] p=track.pts();int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;for(int i=1;i<p.length;i+=3){low=Math.min(low,p[i]);high=Math.max(high,p[i]);}
        g.fill(x,y,x+width,y+height,0xff15212a);double total=track.stats().length()*10,distance=0;
        for(int i=3;i<p.length;i+=3){double previous=distance;distance+=Math.hypot((double)p[i]-p[i-3],(double)p[i+2]-p[i-1]);
            int px=x+(int)(previous/Math.max(1,total)*width),next=x+(int)(distance/Math.max(1,total)*width),py=y+height-1-(int)((p[i+1]-low)/(double)Math.max(1,high-low)*(height-3));g.fill(px,py,Math.max(px+1,next),y+height,0xff65a88a);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        int size=Math.max(64,Math.min(height-125,width-225)),left=Math.max(10,(width-size-200)/2),rows=Math.max(1,(size-20)/13),first=Math.max(0,selected-rows+1);int index=first+(int)((y-41)/13);
        if(button==0&&x>left+size&&y>=41&&y<41+rows*13&&index>=0&&index<data.routes().size()){selected=index;return true;}return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(!data.routes().isEmpty()){selected=Math.floorMod(selected+(dy<0?1:-1),data.routes().size());return true;}return super.mouseScrolled(x,y,dx,dy);}
}
