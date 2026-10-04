package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.trail.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Precise editing selection, accessible with the same configurable menu key as the builder. */
public final class ShapeRadialScreen extends Screen {
    private ShapeMode hovered;
    private final ShapeMode current;
    private int category;
    public ShapeRadialScreen(){super(Component.translatable("descentmtb.shape.title"));current=ShapeToolItem.mode(net.minecraft.client.Minecraft.getInstance().player.getMainHandItem());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){
        g.fill(0,0,width,height,0xa0121a20);int cx=width/2,cy=height/2;
        g.drawCenteredString(font,title,cx,12,0xffe4c488);hovered=null;
        String[] tabs={"descentmtb.shape.group.whole","descentmtb.shape.group.corners","descentmtb.shape.group.edges"};
        for(int i=0;i<3;i++){int x=cx-126+i*84;g.fill(x,29,x+81,49,i==category?0xff806744:0xff29373f);g.drawCenteredString(font,Component.translatable(tabs[i]),x+40,36,0xffe6e2d7);}
        ShapeMode[] choices=switch(category){case 1->new ShapeMode[]{ShapeMode.NW,ShapeMode.NE,ShapeMode.SE,ShapeMode.SW};case 2->new ShapeMode[]{ShapeMode.NORTH,ShapeMode.EAST,ShapeMode.SOUTH,ShapeMode.WEST};default->new ShapeMode[]{ShapeMode.AUTO,ShapeMode.WHOLE,ShapeMode.CURVE};};
        for(int index=0;index<choices.length;index++){
            var mode=choices[index];double angle=-Math.PI/2+index*Math.PI*2/choices.length;
            int x=cx+(int)(Math.cos(angle)*Math.min(120,width*.30))-34,y=cy+(int)(Math.sin(angle)*Math.max(48,(height-132)/2))-17;
            boolean over=mx>=x&&mx<x+68&&my>=y&&my<y+34;if(over)hovered=mode;
            g.fill(x-1,y-1,x+69,y+35,over?0xff72d5c3:mode==current?0xffdfb65e:0xff74634e);
            g.fill(x,y,x+68,y+34,0xff25343d);
            int ix=x+26,iy=y+3;g.fill(ix,iy,ix+16,iy+12,0xff4d655f);
            for(var v:mode.vertices(0,0,.5,.5))g.fill(ix+v.x()*12,iy+v.z()*8,ix+v.x()*12+4,iy+v.z()*8+4,0xffe5bc6d);
            g.drawCenteredString(font,Component.translatable(mode.key()),x+34,y+22,0xffe0d7c1);
        }
        var shown=hovered==null?current:hovered;
        g.drawCenteredString(font,Component.translatable(shown.key()),cx,cy-5,0xfff5d087);
        g.drawCenteredString(font,Component.translatable("descentmtb.shape.wheel"),cx,cy+9,0xff9cb9b5);
    }
    private void choose(){if(hovered!=null){PacketDistributor.sendToServer(new ShapeTunePayload(true,hovered.ordinal(),BlockPos.ZERO,Vec3.ZERO));ShapeToolItem.mode(minecraft.player.getMainHandItem(),hovered.ordinal());}onClose();}
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(ModKeyMappings.TRAIL_MENU.matches(key,scan)){choose();return true;}return super.keyReleased(key,scan,modifiers);}
    @Override public boolean mouseClicked(double x,double y,int button){if(y>=29&&y<49&&x>=width/2-126&&x<width/2+126){category=(int)((x-width/2+126)/84);return true;}if(button==0){choose();return true;}return super.mouseClicked(x,y,button);}
}
