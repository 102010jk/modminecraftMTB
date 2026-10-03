package com.descentmtb.trail;
import java.util.*;
/** Small pure canvas core; undo snapshots are per stroke, flood fill is bounded to 256 cells. */
public final class SignArt {
 public static final int[] PALETTE={0xff26323b,0xfff0ede2,0xffefb84f,0xffe36a4f,0xff68b483,0xff529bd0,0xffaa78c9,0xff4d4140,0xff84919a,0xffba8e60,0xffd9caab,0xff152027,0xff96d8ce,0xffe8a4bf,0xffc7dc65,0xff707b8c};
 private byte[] pixels=new byte[256];private final ArrayDeque<byte[]> undo=new ArrayDeque<>();
 public SignArt(byte[] initial){if(initial.length==256)pixels=initial.clone();sanitize();}
 private void sanitize(){for(int i=0;i<256;i++)pixels[i]=(byte)(pixels[i]&15);}
 public byte[] pixels(){return pixels.clone();}public int colour(int x,int y){return PALETTE[pixels[y*16+x]&15];}
 public void beginStroke(){undo.addLast(pixels.clone());while(undo.size()>32)undo.removeFirst();}
 public void paint(int x,int y,int colour){if(x>=0&&x<16&&y>=0&&y<16)pixels[y*16+x]=(byte)(colour&15);}
 public void fill(int x,int y,int colour){if(x<0||x>15||y<0||y>15)return;colour&=15;int old=pixels[y*16+x]&15;if(old==colour)return;beginStroke();var q=new ArrayDeque<Integer>();q.add(y*16+x);while(!q.isEmpty()){int i=q.removeFirst();if((pixels[i]&15)!=old)continue;pixels[i]=(byte)colour;int px=i%16,py=i/16;if(px>0)q.add(i-1);if(px<15)q.add(i+1);if(py>0)q.add(i-16);if(py<15)q.add(i+16);}}
 public void undo(){if(!undo.isEmpty())pixels=undo.removeLast();}
 public void replace(byte[] p){if(p.length!=256)return;beginStroke();pixels=p.clone();sanitize();}
 public void template(int mode){beginStroke();Arrays.fill(pixels,(byte)0);for(int y=1;y<15;y++)for(int x=1;x<15;x++){
  boolean mark=switch(mode){case 0->(y>=6&&y<=9&&x>3&&x<13)||(x>=8&&Math.abs(y-7)<x-7);case 1->Math.abs(x-7)+Math.abs(y-7)<6;case 2->y==12||Math.abs(y-(13-x/2))<1&&x>2&&x<13;case 3->y==5&&x<9||x==8&&y>=5&&y<11||y==11&&x>=8;default->y>=2&&y<=13&&Math.abs(x-7)<(y-1)/2;};
  if(mark)paint(x,y,mode==1?4:2);
 }if(mode==4){for(int y=6;y<10;y++)paint(7,y,0);paint(7,11,0);}}
}
