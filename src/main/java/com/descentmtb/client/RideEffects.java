package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Visual feedback only. Uses contact snapshots for the local rider and synchronized poses for nearby bikes. */
public final class RideEffects {
    private static Level world;
    private static double speed, lastSpeed;
    private static int ticks;
    public static void setup(){
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());
    }
    private static boolean enabled(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue option){return !ClientConfig.SPEC.isLoaded()||option.get();}
    private static void tick(){
        Minecraft mc=Minecraft.getInstance();
        if(world!=mc.level){world=mc.level;speed=lastSpeed=0;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        ticks++;lastSpeed=speed;
        MountainBikeEntity local=BikeClientController.riding();
        double target=local==null?0:local.rsCur.vel.length();speed+=(target-speed)*.22;
        if(!enabled(ClientConfig.ROOST_PARTICLES))return;
        int count=0;for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof MountainBikeEntity bike)||bike.distanceToSqr(mc.player)>32*32||count++>16)continue;
            roost(mc,bike);
        }
    }
    private static void roost(Minecraft mc,MountainBikeEntity bike){
        if(bike.bikeType().ski()){skiSpray(mc,bike);return;}
        var rs=bike.rsCur;double velocity=rs.vel.length();if(rs.airborne||rs.bailed||velocity<(bike.bikeType().motor()?0.5:3))return;
        double lateral=Math.abs(rs.vel.x*Math.cos(rs.yaw)+rs.vel.z*Math.sin(rs.yaw));
        double slip=lateral/Math.max(velocity,1),force=Math.max(rs.brake*.55,Math.max(0,slip-.08)*2);
        // dirt bike: a spinning rear tyre throws a roost of dirt behind it (the rider's own sim knows the wheelspin,
        // other players' bikes show it while they launch with the throttle open)
        if(bike.bikeType().motor()){
            double spin=bike.sim()!=null?bike.sim().wheelspin*1.6:(com.descentmtb.physics.Engine.decodeThrottle(rs.crank)&&velocity<14?.5:0);
            force=Math.max(force,spin);
        }
        if(force<.08||ticks%2!=0)return;
        double fx=-Math.sin(rs.yaw),fz=Math.cos(rs.yaw),x=bike.getX()-fx*bike.params().halfWheelbase,z=bike.getZ()-fz*bike.params().halfWheelbase,y=bike.getY();
        var block=mc.level.getBlockState(BlockPos.containing(x,y-.12,z));
        if(bike.sim()!=null&&bike.sim().rear.contact){var patch=bike.sim().rear.patch;x=patch.x;y=patch.y;z=patch.z;block=mc.level.getBlockState(BlockPos.containing(x,y-.03,z));}
        boolean soft=block.is(net.minecraft.tags.BlockTags.DIRT)||block.is(net.minecraft.tags.BlockTags.SAND)||block.is(net.minecraft.world.level.block.Blocks.GRAVEL)||block.is(com.descentmtb.registry.ModBlocks.TRAIL_SURFACE.get());
        if(!soft)return;
        if(mc.level.getBlockEntity(BlockPos.containing(x,y-.03,z)) instanceof com.descentmtb.ramp.RampBlockEntity shaped){block=shaped.getMaterial();if(block.is(net.minecraft.tags.BlockTags.PLANKS))return;}
        int particles=Math.min(bike.bikeType().motor()?9:5,1+(int)(force*4));
        if(ClientConfig.SPEC.isLoaded())particles=(int)Math.ceil(particles*ClientConfig.ROOST_DENSITY.get());
        for(int i=0;i<particles;i++){
            double scatter=(mc.level.random.nextDouble()-.5)*.8,kick=Math.min(1.1,velocity*.04)*(force+.25);
            if(bike.bikeType().motor())kick=Math.max(kick,.35+.4*force); // roost flies back even from a standing start
            mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,block),x+(mc.level.random.nextDouble()-.5)*.15,y+.07,z+(mc.level.random.nextDouble()-.5)*.15,-fx*kick+fz*scatter,.12+mc.level.random.nextDouble()*.15,-fz*kick-fx*scatter);
        }
    }
    /**
     * Skis (never reached by a bike): snow sprays out from under the edges in a hard carve, a snowplough or hockey stop
     * and on landing; where the bases grind over stone they kick up dust of that block and the odd spark. The rider's
     * own skis read the simulation (surface, scrape), everyone else's are judged from the block under them.
     */
    private static void skiSpray(Minecraft mc,MountainBikeEntity bike){
        var rs=bike.rsCur;var sim=bike.sim();var random=mc.level.random;
        if(rs.bailed||rs.airborne)return;
        boolean landed=bike.rsPrev.airborne;
        double velocity=rs.vel.length(),x=bike.getX(),y=bike.getY(),z=bike.getZ();
        if(sim!=null&&(sim.front.contact||sim.rear.contact)){
            V3 a=sim.front.contact?sim.front.patch:sim.rear.patch,b=sim.rear.contact?sim.rear.patch:sim.front.patch;
            x=(a.x+b.x)*.5;y=(a.y+b.y)*.5;z=(a.z+b.z)*.5;
        }
        BlockPos pos=BlockPos.containing(x,y-.03,z);var block=mc.level.getBlockState(pos);
        if(block.isAir()){pos=pos.below();block=mc.level.getBlockState(pos);}
        if(block.isAir())return;
        if(mc.level.getBlockEntity(pos) instanceof com.descentmtb.ramp.RampBlockEntity shaped)block=shaped.getMaterial();
        Terrain.Surface surface;
        if(sim!=null&&(sim.front.contact||sim.rear.contact))surface=sim.rear.contact?sim.rear.surface:sim.front.surface;
        else if(block.is(Blocks.SNOW)||block.is(Blocks.SNOW_BLOCK)||block.is(Blocks.POWDER_SNOW))surface=Terrain.Surface.SNOW;
        else if(block.is(BlockTags.ICE)||block.getBlock().getFriction()>.9f)surface=Terrain.Surface.ICE;
        else if(block.is(com.descentmtb.registry.ModBlocks.AIRBAG.get()))surface=Terrain.Surface.AIRBAG;
        else if(block.is(BlockTags.PLANKS)||block.is(BlockTags.LOGS)||block.is(BlockTags.WOODEN_SLABS)||block.is(BlockTags.WOODEN_STAIRS))surface=Terrain.Surface.WOOD;
        else surface=Terrain.Surface.ROCK;
        double fx=-Math.sin(rs.yaw),fz=Math.cos(rs.yaw),rx=-fz,rz=fx;   // forward and right (forward x up)
        double density=ClientConfig.SPEC.isLoaded()?ClientConfig.ROOST_DENSITY.get():1;
        var dust=new BlockParticleOption(ParticleTypes.BLOCK,block);
        // ---- grind: stone dust from under the bases, a spark now and then (a wooden box slides clean) ----
        if(com.descentmtb.ski.SkiSurface.grinds(surface)){
            double scrape=sim!=null?sim.skiScrape:Mth.clamp((velocity-1)/6,0,1);
            if(scrape<.05||velocity<.4)return;
            int n=(int)Math.ceil((1+scrape*3)*density);
            for(int i=0;i<n;i++){
                double along=(random.nextDouble()-.5)*1.2,side=(random.nextBoolean()?1:-1)*.11;
                mc.level.addParticle(dust,x+fx*along+rx*side,y+.04,z+fz*along+rz*side,
                        -fx*.08+(random.nextDouble()-.5)*.08,.05+random.nextDouble()*.08,-fz*.08+(random.nextDouble()-.5)*.08);
            }
            if(random.nextDouble()<.35*scrape){
                double along=(random.nextDouble()-.5)*1.2;
                mc.level.addParticle(ParticleTypes.CRIT,x+fx*along,y+.05,z+fz*along,
                        -fx*.25+(random.nextDouble()-.5)*.3,.12+random.nextDouble()*.15,-fz*.25+(random.nextDouble()-.5)*.3);
            }
            if(random.nextDouble()<.2*scrape)mc.level.addParticle(ParticleTypes.SMOKE,x,y+.08,z,-fx*.03,.02,-fz*.03);
            return;
        }
        if(surface!=Terrain.Surface.SNOW&&surface!=Terrain.Surface.ICE)return;
        boolean ice=surface==Terrain.Surface.ICE;

        // ---- landing: a burst of snow all around the skis ----
        if(landed){
            double hit=Mth.clamp(-bike.rsPrev.vel.y/8,.25,1);
            int n=(int)Math.ceil((4+10*hit)*density*(ice?.5:1));
            for(int i=0;i<n;i++){
                double angle=random.nextDouble()*Math.PI*2,kick=.08+.18*hit*random.nextDouble();
                mc.level.addParticle(i%3==0&&!ice?ParticleTypes.SNOWFLAKE:dust,x+(random.nextDouble()-.5)*.6,y+.08,z+(random.nextDouble()-.5)*.6,
                        Math.cos(angle)*kick+rs.vel.x*.04,.12+.18*hit*random.nextDouble(),Math.sin(angle)*kick+rs.vel.z*.04);
            }
            return;
        }
        if(velocity<2||ticks%2!=0)return;

        // ---- carve / snowplough / hockey stop: a fan of snow thrown sideways off the edges ----
        double lateralVel=rs.vel.x*rx+rs.vel.z*rz,slip=Math.abs(lateralVel)/Math.max(velocity,1);
        double carve=Mth.clamp((Math.abs(rs.lean)-.35)/.5,0,1)*Mth.clamp((velocity-5)/10,0,1);
        double skid=Mth.clamp((slip-.12)*2.5,0,1)*Mth.clamp((velocity-2)/6,0,1);
        double plough=Mth.clamp(rs.brake,0,1)*Mth.clamp((velocity-2)/6,0,1);
        double force=Math.max(carve*.8,Math.max(skid,plough));
        if(force<.1)return;
        // sliding skis push the snow the way they slide; a clean carve throws it off the outside of the turn
        double side=skid>=carve*.8||plough>=carve*.8?Math.signum(lateralVel):-Math.signum(rs.lean);
        if(side==0)side=random.nextBoolean()?1:-1;
        int n=(int)Math.ceil((2+force*6)*density*(ice?.5:1));
        double kick=.1+.25*force*Math.min(1,velocity/10);
        for(int i=0;i<n;i++){
            double along=(random.nextDouble()-.5)*1.1,out=kick*(.6+.6*random.nextDouble());
            mc.level.addParticle(i%3==0&&!ice?ParticleTypes.SNOWFLAKE:dust,x+fx*along+rx*side*.2,y+.06,z+fz*along+rz*side*.2,
                    rx*side*out+rs.vel.x*.05,.1+.15*force*random.nextDouble(),rz*side*out+rs.vel.z*.05);
        }
    }
    public static void render(GuiGraphics g,float partial){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||mc.options.hideGui||BikeClientController.riding()==null)return;
        int w=g.guiWidth(),h=g.guiHeight();
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
