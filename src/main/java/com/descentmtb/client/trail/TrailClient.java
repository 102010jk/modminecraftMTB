package com.descentmtb.client.trail;
import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.network.*;
import com.descentmtb.trail.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
public final class TrailClient {
 public static List<TrailPreviewPayload.Cell> ghost=List.of();private static Object level;
 public static void setup(){TrailPreviewPayload.clientHandler=p->ghost=p.cells();TrailSignBlock.editor=be->Minecraft.getInstance().setScreen(new SignEditorScreen(be));}
 public static void tick(){var mc=Minecraft.getInstance();if(mc.level!=level){ghost=List.of();level=mc.level;}if(mc.player!=null&&mc.screen==null&&mc.player.getMainHandItem().getItem() instanceof TrailWandItem&&ModKeyMappings.TRAIL_MENU.consumeClick())mc.setScreen(new TrailRadialScreen());}
 public static void hud(GuiGraphics g){var mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||!(mc.player.getMainHandItem().getItem() instanceof TrailWandItem))return;var s=WandSettings.read(mc.player.getMainHandItem());int x=12,y=g.guiHeight()-66;g.fill(x-5,y-5,x+225,y+27,0xbe18252b);g.fill(x-5,y-5,x-3,y+27,0xffdfb65e);g.drawString(mc.font,net.minecraft.network.chat.Component.translatable(s.mode().key()),x,y,0xffe7d7ad);g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%.1f m / %.2f m  •  [%s]",s.width(),s.height(),ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage().getString()),x,y+13,0xff91cbbb);}

 private static float clip(double h){return (float)Math.max(0,Math.min(1,h));}
 private static net.minecraft.world.phys.Vec3 project(dev.ryanhcode.sable.companion.ClientSubLevelAccess sub,double x,double y,double z,float t){var v=new net.minecraft.world.phys.Vec3(x,y,z);return sub==null?v:sub.renderPose(t).transformPosition(v);}
 private static void vertex(VertexConsumer vc,PoseStack pose,net.minecraft.world.phys.Vec3 v,net.minecraft.world.phys.Vec3 camera,int color){var p=v.subtract(camera);vc.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(color);}
 public static void renderGhost(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||ghost.isEmpty())return;
  var mc=Minecraft.getInstance();if(mc.level==null)return;PoseStack pose=e.getPoseStack();pose.pushPose();var camera=e.getCamera().getPosition();var source=mc.renderBuffers().bufferSource();var vc=source.getBuffer(RenderType.debugQuads());float pt=e.getPartialTick().getGameTimeDeltaPartialTick(false);
  for(var c:ghost){var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContainingClient(c.pos());if(project(sub,c.pos().getX()+.5,c.pos().getY()+.5,c.pos().getZ()+.5,pt).distanceToSqr(camera)>128*128)continue;
   double x=c.pos().getX(),y=c.pos().getY()+.01,z=c.pos().getZ();int color=c.remove()?0x50ec755e:0x6056d9c6;int slices=c.nw()<0||c.ne()<0||c.sw()<0||c.se()<0||c.nw()>1||c.ne()>1||c.sw()>1||c.se()>1?4:1;double[] h={c.nw(),c.ne(),c.sw(),c.se()};
   for(int ix=0;ix<slices;ix++)for(int iz=0;iz<slices;iz++){double a=ix/(double)slices,b=iz/(double)slices,d=1.0/slices;float h0=clip(TrailMath.bilerp(h,a,b)),h1=clip(TrailMath.bilerp(h,a,b+d)),h2=clip(TrailMath.bilerp(h,a+d,b+d)),h3=clip(TrailMath.bilerp(h,a+d,b));if(h0+h1+h2+h3<.001)continue;vertex(vc,pose,project(sub,x+a,y+h0,z+b,pt),camera,color);vertex(vc,pose,project(sub,x+a,y+h1,z+b+d,pt),camera,color);vertex(vc,pose,project(sub,x+a+d,y+h2,z+b+d,pt),camera,color);vertex(vc,pose,project(sub,x+a+d,y+h3,z+b,pt),camera,color);}
  }
  source.endBatch(RenderType.debugQuads());var lines=source.getBuffer(RenderType.lines());int[][] edges={{0,1},{0,2},{0,4},{1,3},{1,5},{2,3},{2,6},{3,7},{4,5},{4,6},{5,7},{6,7}};
  for(var c:ghost){var sub=dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContainingClient(c.pos());if(project(sub,c.pos().getX()+.5,c.pos().getY()+.5,c.pos().getZ()+.5,pt).distanceToSqr(camera)>96*96)continue;var points=new net.minecraft.world.phys.Vec3[8];for(int i=0;i<8;i++)points[i]=project(sub,c.pos().getX()+((i&1)!=0?1:0),c.pos().getY()+((i&2)!=0?1:0),c.pos().getZ()+((i&4)!=0?1:0),pt).subtract(camera);for(int[] edge:edges){var a=points[edge[0]];var b=points[edge[1]];var n=b.subtract(a).normalize();for(var v:new net.minecraft.world.phys.Vec3[]{a,b})lines.addVertex(pose.last(),(float)v.x,(float)v.y,(float)v.z).setColor(c.remove()?0x80ee7766:0x7053d0bd).setNormal(pose.last(),(float)n.x,(float)n.y,(float)n.z);}}
  source.endBatch(RenderType.lines());pose.popPose();
 }
}
