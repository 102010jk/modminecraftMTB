package com.descentmtb.client.trail;
import com.descentmtb.trail.*;
import com.descentmtb.network.TrailActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class TrailSettingsScreen extends Screen {
 private final WandSettings initial;private final double[] values;private EditBox name;private int mode;
 public TrailSettingsScreen(){super(Component.translatable("descentmtb.wand.settings"));initial=WandSettings.read(net.minecraft.client.Minecraft.getInstance().player.getMainHandItem());mode=initial.mode().ordinal();values=new double[]{initial.width(),initial.height(),initial.spacing(),initial.repeats(),initial.radius(),initial.strength(),initial.softness()};}
 @Override public boolean isPauseScreen(){return false;}
 private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.translatable(key),b->action.run()).bounds(x,y,w,20).build());}
 private WandSettings settings(){return new WandSettings(WandMode.from(mode),values[0],values[1],values[2],(int)Math.round(values[3]),values[4],values[5],values[6]).bounded();}
 private void send(int action,int dx,int dy,int dz){var s=settings();s.store(minecraft.player.getMainHandItem());PacketDistributor.sendToServer(new TrailActionPayload(0,s.tag(),"",0,0,0));PacketDistributor.sendToServer(new TrailActionPayload(action,s.tag(),name.getValue(),dx,dy,dz));}
 @Override protected void init(){int x=width/2-168,y=32;
  addRenderableWidget(Button.builder(Component.translatable(WandMode.from(mode).key()),b->{mode=(mode+1)%WandMode.values().length;b.setMessage(Component.translatable(WandMode.from(mode).key()));}).bounds(x,y,336,20).build());
  String[] keys={"width","height","spacing","repeats","radius","strength","softness"};double[] min={2,.1,2,1,1,.05,0},max={9,3,12,24,12,2,1};
  for(int i=0;i<7;i++){final int n=i;addRenderableWidget(new AbstractSliderButton(x+(i%2)*170,y+25+(i/2)*21,166,20,Component.empty(),(values[i]-min[i])/(max[i]-min[i])){
   {updateMessage();}protected void updateMessage(){setMessage(Component.translatable("descentmtb.wand.setting."+keys[n],String.format(Locale.ROOT,n==3?"%.0f":"%.2f",values[n])));}protected void applyValue(){values[n]=min[n]+value*(max[n]-min[n]);updateMessage();}
  });}
  int py=y+109;button("descentmtb.wand.confirm",x,py,110,()->{send(1,0,0,0);onClose();});button("descentmtb.wand.cancel",x+113,py,110,()->{send(2,0,0,0);onClose();});button("descentmtb.wand.rotate",x+226,py,110,()->send(3,0,0,0));
  int row=py+22;String[] labels={"← X","X →","↑ Y","↓ Y","← Z","Z →"};int[][] d={{-1,0,0},{1,0,0},{0,1,0},{0,-1,0},{0,0,-1},{0,0,1}};for(int i=0;i<6;i++){final int n=i;addRenderableWidget(Button.builder(Component.literal(labels[i]),b->send(4,d[n][0],d[n][1],d[n][2])).bounds(x+i*56,row,53,20).build());}
  name=new EditBox(font,x,row+24,140,20,Component.translatable("descentmtb.wand.template"));name.setMaxLength(24);name.setValue("trail");addRenderableWidget(name);
  button("descentmtb.wand.save",x+144,row+24,94,()->send(6,0,0,0));button("descentmtb.wand.load",x+242,row+24,94,()->send(7,0,0,0));
  button("descentmtb.wand.undo",x,height-29,110,()->send(5,0,0,0));button("descentmtb.wand.apply",x+226,height-29,110,()->{send(0,0,0,0);onClose();});
 }
 @Override public void render(GuiGraphics g,int x,int y,float pt){g.fill(0,0,width,height,0xc918242d);g.drawCenteredString(font,title,width/2,12,0xffe3c58d);super.render(g,x,y,pt);}
}
