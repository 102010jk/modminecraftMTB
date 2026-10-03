package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class TrailSurfaces {
    public static double height(BlockState s, BlockGetter l, BlockPos p, double x, double z) {
        return l.getBlockEntity(p) instanceof TrailSurfaceEntity be ? be.height(x,z) : RampBlock.heightAt(s,x,z);
    }
    public static double slopeX(BlockState s, BlockGetter l, BlockPos p, double x, double z) {
        return l.getBlockEntity(p) instanceof TrailSurfaceEntity be ? be.slopeX(x,z) : RampBlock.slopeX(s,x,z);
    }
    public static double slopeZ(BlockState s, BlockGetter l, BlockPos p, double x, double z) {
        return l.getBlockEntity(p) instanceof TrailSurfaceEntity be ? be.slopeZ(x,z) : RampBlock.slopeZ(s,x,z);
    }
    public static boolean solid(BlockState s, BlockGetter l, BlockPos p, double x, double y, double z) {
        double h = height(s,l,p,x,z);
        if(l.getBlockEntity(p) instanceof TrailSurfaceEntity be && be.beam()&&x>=.375&&x<=.625&&z>=.375&&z<=.625&&y<be.bottom(x,z))return true;
        return y < h && (!(l.getBlockEntity(p) instanceof TrailSurfaceEntity be) || y > be.bottom(x,z));
    }
    private TrailSurfaces() {}
}
