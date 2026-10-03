package com.descentmtb.trail;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;
import static com.descentmtb.trail.TrailMath.*;

/** Shared builder for flow trails, banked turns, sharkfins, elevated decks and pumptrack rollers. */
public final class TrailBuilder {
    public enum Shape { FLOW, BERM, ENDURO, SHARKFIN, BOARDWALK, KICKER, DROP, DIRT_JUMP, ROLLERS }
    public static Map<BlockPos,TrailEdit.Change> plan(Level level, Point a, Point b, Point c, Shape shape) {
        return plan(level,a,b,c,shape,new WandSettings(WandMode.FLOW,TrailConfig.WIDTH.get(),TrailConfig.ROLLER_HEIGHT.get(),5,5,3,.3,.75));
    }
    public static Map<BlockPos,TrailEdit.Change> plan(Level level,Point a,Point b,Point c,Shape shape,WandSettings settings) {
        SurfacePlans.validate(a);SurfacePlans.validate(b);SurfacePlans.validate(c);
        double length=Math.hypot(b.x()-a.x(),b.z()-a.z())+Math.hypot(c.x()-b.x(),c.z()-b.z());
        if(length<2||length>TrailConfig.MAX_LENGTH.get()) throw new IllegalArgumentException("Vytyč úsek dlouhý 2–"+TrailConfig.MAX_LENGTH.get()+" bloků");
        boolean wood=shape==Shape.BOARDWALK||shape==Shape.KICKER||shape==Shape.DROP;
        double half=settings.width()/2.0;
        int minX=(int)Math.floor(Math.min(a.x(),Math.min(b.x(),c.x()))-half), maxX=(int)Math.ceil(Math.max(a.x(),Math.max(b.x(),c.x()))+half);
        int minZ=(int)Math.floor(Math.min(a.z(),Math.min(b.z(),c.z()))-half), maxZ=(int)Math.ceil(Math.max(a.z(),Math.max(b.z(),c.z()))+half);
        Map<BlockPos,TrailEdit.Change> out=new LinkedHashMap<>();
        double turn=turn(a,b,c);
        if((shape==Shape.BERM||shape==Shape.ENDURO||shape==Shape.SHARKFIN)&&turn==0) throw new IllegalArgumentException("Pro klopenku vytyč zatáčku, ne přímku");
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            double t=nearest(a,b,c,x+.5,z+.5); Point p=curve(a,b,c,t);
            if(Math.hypot(x+.5-p.x(),z+.5-p.z())>half) continue;
            double[] absolute=new double[4];
            for(int i=0;i<4;i++) {
                double px=x+(i%2),pz=z+(i/2),q=nearest(a,b,c,px,pz);
                double h=curve(a,b,c,q).y();
                double s=side(a,b,c,q,px,pz);
                if(shape==Shape.BERM||shape==Shape.ENDURO||shape==Shape.SHARKFIN)
                    h+=bankHeight(s,half,q,turn,settings.height()*2.4*(shape==Shape.ENDURO?.7:1),shape==Shape.SHARKFIN);
                if(shape==Shape.KICKER||shape==Shape.DIRT_JUMP) h+=settings.height()*2.1*q*q;
                if(shape==Shape.DROP) h+=settings.height()*1.75;
                if(shape==Shape.ROLLERS) h+=settings.height()*Math.pow(Math.sin(q*Math.PI*Math.max(1,Math.round(length/5))),2);
                absolute[i]=h;
            }
            double min=java.util.Arrays.stream(absolute).min().orElse(0), max=java.util.Arrays.stream(absolute).max().orElse(0);
            int bottom=(int)Math.floor(min-.001), top=(int)Math.ceil(max)-1;
            for(int y=bottom;y<=top;y++) {
                // Preserve the original plane in every vertical layer. Clamp AFTER interpolation,
                // so crossing an integer height does not create a phantom flat floor.
                double[] h=new double[4]; for(int i=0;i<4;i++)h[i]=absolute[i]-y;
                out.put(new BlockPos(x,y,z),new TrailEdit.Change(ModBlocks.TRAIL_SURFACE.get().defaultBlockState(),null,h,
                        wood?Blocks.OAK_PLANKS.defaultBlockState():Blocks.COARSE_DIRT.defaultBlockState(),wood));
            }
            if(!wood) {
                double original=new com.descentmtb.world.McColumns(level).collisionTop(x+.5,z+.5,bottom+.01,bottom-12);
                if(!Double.isFinite(original)||bottom-original>12)throw new IllegalArgumentException("Hlinitá rampa musí mít zem do 12 m pod sebou; ve vzduchu použij dřevěnou konstrukci");
                for(int y=bottom-1;y>=Math.floor(original)-1;y--) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!level.getBlockState(pos).isAir()) break;
                    out.put(pos,TrailEdit.Change.block(Blocks.DIRT.defaultBlockState()));
                }
                // Remove bumps/head obstructions only in the trail corridor, leaving the surrounding hill intact.
                for(int y=top+1;y<=top+3;y++) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!level.getBlockState(pos).isAir()) out.put(pos,TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                }
            } else if(Math.floorMod(x,3)==0&&Math.floorMod(z,3)==0) {
                for(int y=bottom-1;y>=bottom-10;y--) {
                    BlockPos pos=new BlockPos(x,y,z); if(!level.getBlockState(pos).isAir()) break;
                    out.put(pos,TrailEdit.Change.block(Blocks.OAK_FENCE.defaultBlockState()));
                }
            }
            if(out.size()>TrailConfig.MAX_BLOCKS.get()) throw new IllegalArgumentException("Úprava je příliš velká; zkrať úsek nebo zmenši šířku v configu");
        }
        return out;
    }
    public static Point middle(Point a,Point c) {return new Point((a.x()+c.x())/2,(a.y()+c.y())/2,(a.z()+c.z())/2);}
    private TrailBuilder() {}
}
