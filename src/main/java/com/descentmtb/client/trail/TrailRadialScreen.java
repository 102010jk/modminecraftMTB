package com.descentmtb.client.trail;
import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.trail.*;
import com.descentmtb.network.TrailActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Hold the reboundable key, point at a mode and release. Click tabs to change family. */
public final class TrailRadialScreen extends Screen {
 private int category;private WandMode hover;private final WandSettings current;
 public TrailRadialScreen(){super(Component.translatable("descentmtb.wand.title"));var p=net.minecraft.client.Minecraft.getInstance().player;current=WandSettings.read(p.getMainHandItem());category=current.mode().category;}
 @Override public boolean isPauseScreen(){return false;}
 private List<WandMode> modes(){return Arrays.stream(WandMode.values()).filter(m->m.category==category).toList();}
 @Override public void render(GuiGraphics g,int mx,int my,float pt){
  g.fill(0,0,width,height,0x95121a20);int cx=width/2,cy=height/2+10;g.drawCenteredString(font,title,cx,12,0xffe4c488);
  for(int i=0;i<5;i++){int x=cx-165+i*66;g.fill(x,30,x+63,49,i==category?0xff806744:0xff29373f);g.drawCenteredString(font,Component.translatable("descentmtb.wand.category."+i),x+31,36,0xffe6e2d7);}
  hover=null;var modes=modes();double radius=Math.min(112,width*.26),radiusY=Math.max(40,(height-128)/2.0);int w=70,h=34;
  for(int i=0;i<modes.size();i++){double angle=-Math.PI/2+2*Math.PI*i/modes.size();int x=(int)(cx+Math.cos(angle)*radius-w/2),y=(int)(cy+Math.sin(angle)*radiusY-h/2);WandMode mode=modes.get(i);boolean hit=mx>=x&&mx<x+w&&my>=y&&my<y+h;if(hit)hover=mode;g.fill(x-1,y-1,x+w+1,y+h+1,hit?0xff72d5c3:0xff74634e);g.fill(x,y,x+w,y+h,hit?0xff314b4e:0xff25343d);g.fill(x+2,y+2,x+4,y+4,0xffbdb3a0);g.fill(x+w-4,y+2,x+w-2,y+4,0xffbdb3a0);g.blit(ResourceLocation.fromNamespaceAndPath("descentmtb","textures/gui/trail/"+mode.name().toLowerCase(Locale.ROOT)+".png"),x+w/2-8,y+3,0,0,16,16,16,16);g.drawCenteredString(font,Component.translatable(mode.key()),x+w/2,y+22,hit?0xffcaffed:0xffe0d7c1);}
  g.drawCenteredString(font,Component.translatable(current.mode().key()),cx,cy-6,0xfff5d087);g.drawCenteredString(font,String.format(Locale.ROOT,"%.1f × %.2f m",current.width(),current.height()),cx,cy+7,0xff9cb9b5);
  g.fill(cx-78,height-28,cx+78,height-8,0xff34454c);g.drawCenteredString(font,Component.translatable("descentmtb.wand.settings"),cx,height-22,0xffe7d5aa);
 }
 private void choose(WandMode mode){var s=new WandSettings(mode,current.width(),current.height(),current.spacing(),current.repeats(),current.radius(),current.strength(),current.softness());PacketDistributor.sendToServer(new TrailActionPayload(0,s.tag(),"",0,0,0));s.store(minecraft.player.getMainHandItem());onClose();}
 @Override public boolean keyReleased(int key,int scan,int modifiers){if(ModKeyMappings.TRAIL_MENU.matches(key,scan)){if(hover!=null)choose(hover);else onClose();return true;}return super.keyReleased(key,scan,modifiers);}
 @Override public boolean mouseClicked(double x,double y,int button){int cx=width/2;if(y>=30&&y<=49&&x>=cx-165&&x<cx+165){category=(int)((x-cx+165)/66);return true;}if(y>=height-28&&y<height-8&&Math.abs(x-cx)<78){minecraft.setScreen(new TrailSettingsScreen());return true;}if(button==0&&hover!=null){choose(hover);return true;}return super.mouseClicked(x,y,button);}
}
