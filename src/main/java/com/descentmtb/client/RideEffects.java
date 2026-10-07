package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Terrain;
import com.descentmtb.network.TearOffPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Visual feedback only. Uses contact snapshots for the local rider and synchronized poses for nearby bikes. */
public final class RideEffects {
    private static final ResourceLocation SPLASH=ResourceLocation.fromNamespaceAndPath("descentmtb","textures/gui/mud_splash.png");
    private static Level world;
    private static float lens, lastLens, tear;
    private static double speed, lastSpeed;
    private static int ticks;
    public static void setup(){
        TearOffPayload.client=()->{tear=1;};
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());
    }
    private static boolean enabled(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue option){return !ClientConfig.SPEC.isLoaded()||option.get();}
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level){world=mc.level;lens=lastLens=tear=0;speed=lastSpeed=0;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        ticks++;lastLens=lens;lastSpeed=speed;
        while(ModKeyMappings.TEAR_OFF.consumeClick())if(mc.screen==null)PacketDistributor.sendToServer(new TearOffPayload());
        if(tear>0){tear=Math.max(0,tear-.12f);if(tear<.55f)lens=lastLens=0;}
        MountainBikeEntity local=BikeClientController.riding();
        double target=local==null?0:local.rsCur.vel.length();speed+=(target-speed)*.22;
        if(local!=null&&local.sim()!=null&&BikeCamera.helmet()){
            var sim=local.sim();var rear=sim.rear;
            boolean dirty=rear.surface==Terrain.Surface.MUD || mc.level.isRainingAt(local.blockPosition().above())&&(rear.surface==Terrain.Surface.DIRT||rear.surface==Terrain.Surface.GRASS||rear.surface==Terrain.Surface.TRAIL);
            if(rear.contact&&dirty&&sim.speed()>3&&enabled(ClientConfig.MUD_EFFECTS))lens=Math.min(.65f,lens+(float)(sim.speed()*.00012*(.2+sim.brake)));
            if(local.isInWater())lens=Math.max(0,lens-.04f);
        }
        if(!enabled(ClientConfig.ROOST_PARTICLES))return;
        int count=0;for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof MountainBikeEntity bike)||bike.distanceToSqr(mc.player)>32*32||count++>16)continue;
            roost(mc,bike);
        }
    }
    private static void roost(Minecraft mc,MountainBikeEntity bike){
        var rs=bike.rsCur;double velocity=rs.vel.length();if(rs.airborne||rs.bailed||velocity<3)return;
        double lateral=Math.abs(rs.vel.x*Math.cos(rs.yaw)+rs.vel.z*Math.sin(rs.yaw));
        double slip=lateral/Math.max(velocity,1),force=Math.max(rs.brake*.55,Math.max(0,slip-.08)*2);
        if(force<.08||ticks%2!=0)return;
        double fx=-Math.sin(rs.yaw),fz=Math.cos(rs.yaw),x=bike.getX()-fx*bike.params().halfWheelbase,z=bike.getZ()-fz*bike.params().halfWheelbase,y=bike.getY();
        var block=mc.level.getBlockState(BlockPos.containing(x,y-.12,z));
        if(bike.sim()!=null&&bike.sim().rear.contact){var patch=bike.sim().rear.patch;x=patch.x;y=patch.y;z=patch.z;block=mc.level.getBlockState(BlockPos.containing(x,y-.03,z));}
        boolean soft=block.is(net.minecraft.tags.BlockTags.DIRT)||block.is(net.minecraft.tags.BlockTags.SAND)||block.is(net.minecraft.world.level.block.Blocks.GRAVEL)||block.is(com.descentmtb.registry.ModBlocks.TRAIL_SURFACE.get());
        if(!soft)return;
        if(mc.level.getBlockEntity(BlockPos.containing(x,y-.03,z)) instanceof com.descentmtb.ramp.RampBlockEntity shaped){block=shaped.getMaterial();if(block.is(net.minecraft.tags.BlockTags.PLANKS))return;}
        int particles=Math.min(5,1+(int)(force*4));
        if(ClientConfig.SPEC.isLoaded())particles=(int)Math.ceil(particles*ClientConfig.ROOST_DENSITY.get());
        for(int i=0;i<particles;i++){
            double scatter=(mc.level.random.nextDouble()-.5)*.8,kick=Math.min(1.1,velocity*.04)*(force+.25);
            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,block),x+(mc.level.random.nextDouble()-.5)*.15,y+.07,z+(mc.level.random.nextDouble()-.5)*.15,-fx*kick+fz*scatter,.12+mc.level.random.nextDouble()*.15,-fz*kick-fx*scatter);
        }
    }
    public static void render(GuiGraphics g,float partial){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||mc.options.hideGui||BikeClientController.riding()==null)return;
        int w=g.guiWidth(),h=g.guiHeight();
        float amount=Mth.lerp(partial,lastLens,lens);
        // mud on the lens is a helmet-cam thing; the speed rush works from every camera
        if(BikeCamera.helmet()&&enabled(ClientConfig.MUD_EFFECTS)&&amount>.01){
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1,1,1,amount);int slide=(int)(tear>0?(1-tear)*w:0);
            g.blit(SPLASH,slide,0,w,h,0,0,256,144,256,144);g.setColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
        if(!enabled(ClientConfig.SPEED_LINES))return;
        double v=Mth.lerp(partial,lastSpeed,speed),strength=ClientConfig.SPEC.isLoaded()?ClientConfig.SPEED_LINE_STRENGTH.get():.5;
        double intensity=Mth.clamp((v*3.6-28)/45,0,1)*strength*(BikeCamera.helmet()?1:.7);
        if(intensity<.01)return;
        float time=(ticks+partial)/8f;
        for(int i=0;i<22;i++){
            double angle=i*Math.PI*2/22,phase=(time+i*.617)%1,inner=.77+phase*.24,outer=inner+.05+.04*intensity;
            int x0=(int)(w/2.0+Math.cos(angle)*w*.62*inner),y0=(int)(h/2.0+Math.sin(angle)*h*.62*inner),x1=(int)(w/2.0+Math.cos(angle)*w*.62*outer),y1=(int)(h/2.0+Math.sin(angle)*h*.62*outer);
            line(g,x0,y0,x1,y1,((int)(110*intensity*(1-phase))<<24)|0xe1eeec,w,h);
        }
    }
    private static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int color,int width,int height){
        int dx=x1-x0,dy=y1-y0,n=Math.max(Math.abs(dx),Math.abs(dy));
        for(int i=0;i<=n;i++){double t=n==0?0:i/(double)n;int x=(int)(x0+dx*t),y=(int)(y0+dy*t);if(x>=0&&y>=0&&x<width&&y<height)g.fill(x,y,x+1,y+1,color);}
    }
}
