package com.descentmtb.client.trail;
import com.descentmtb.trail.*;
import com.descentmtb.network.SignArtPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
public final class SignEditorScreen extends Screen {
 private static byte[] clipboard;private final SignArt art;private final BlockPos pos;private int colour=1,tool=0,cell=10,left,top,lastX,lastY;
 public SignEditorScreen(TrailSignEntity be){super(Component.translatable("descentmtb.sign.editor"));art=new SignArt(be.pixels());pos=be.getBlockPos();}
 @Override public boolean isPauseScreen(){return false;}
 private void button(String key,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.translatable(key),b->run.run()).bounds(x,y,w,18).build());}
 @Override protected void init(){cell=Math.max(6,Math.min(12,(height-90)/16));left=width/2-164;top=40;int x=left+16*cell+14;
  for(int i=0;i<3;i++){final int n=i;button("descentmtb.sign.tool."+i,x+i*45,top+42,43,()->tool=n);}
  button("descentmtb.sign.undo",x,top+63,65,art::undo);button("descentmtb.sign.copy",x+69,top+63,65,()->clipboard=art.pixels());button("descentmtb.sign.paste",x,top+84,134,()->{if(clipboard!=null)art.replace(clipboard);});
  for(int i=0;i<5;i++){final int n=i;button("descentmtb.sign.template."+i,x+(i%2)*69,top+105+(i/2)*19,65,()->art.template(n));}
  button("descentmtb.sign.save",width/2+6,height-29,110,()->{PacketDistributor.sendToServer(new SignArtPayload(pos,art.pixels()));onClose();});button("descentmtb.sign.cancel",width/2-116,height-29,110,this::onClose);
 }
 @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xd018252d);g.drawCenteredString(font,title,width/2,15,0xffe2c48a);g.fill(left-3,top-3,left+16*cell+3,top+16*cell+3,0xffa98c58);
  for(int y=0;y<16;y++)for(int x=0;x<16;x++)g.fill(left+x*cell,top+y*cell,left+(x+1)*cell-1,top+(y+1)*cell-1,art.colour(x,y));int px=left+16*cell+14;
  for(int i=0;i<16;i++){int x=px+(i%8)*17,y=top+(i/8)*18;g.fill(x-1,y-1,x+15,y+15,i==colour?0xffe8d398:0xff59656a);g.fill(x,y,x+14,y+14,SignArt.PALETTE[i]);}super.render(g,mx,my,pt);
 }
 private boolean inside(double x,double y){return x>=left&&y>=top&&x<left+16*cell&&y<top+16*cell;}
 @Override public boolean mouseClicked(double x,double y,int button){int px=left+16*cell+14;if(x>=px&&x<px+8*17&&y>=top&&y<top+36){colour=(int)((y-top)/18)*8+(int)((x-px)/17);return true;}if(button==0&&inside(x,y)){int cx=(int)(x-left)/cell,cy=(int)(y-top)/cell;if(tool==2)art.fill(cx,cy,colour);else{art.beginStroke();art.paint(cx,cy,tool==1?0:colour);lastX=cx;lastY=cy;}return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&tool!=2&&inside(x,y)){int cx=(int)(x-left)/cell,cy=(int)(y-top)/cell,steps=Math.max(Math.abs(cx-lastX),Math.abs(cy-lastY));for(int i=0;i<=steps;i++){double t=i/(double)Math.max(1,steps);art.paint((int)Math.round(lastX+(cx-lastX)*t),(int)Math.round(lastY+(cy-lastY)*t),tool==1?0:colour);}lastX=cx;lastY=cy;return true;}return super.mouseDragged(x,y,button,dx,dy);}
}
