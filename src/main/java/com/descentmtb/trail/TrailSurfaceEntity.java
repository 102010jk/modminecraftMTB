package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Continuous four-corner surface. A wooden deck has a thin underside and real open space below. */
public final class TrailSurfaceEntity extends RampBlockEntity {
    private final double[] h = {.1, .1, .1, .1}; // NW NE SW SE
    private boolean deck,beam;
    private int overlay;
    private VoxelShape shape;
    public TrailSurfaceEntity(BlockPos pos, BlockState state) { super(ModBlocks.TRAIL_BE.get(), pos, state); }
    public void setShape(double[] heights, boolean deck) {
        for (int i = 0; i < 4; i++) h[i] = Double.isFinite(heights[i])?Math.max(-16,Math.min(16,heights[i])):0;
        this.deck = deck; shape = null; shapeChanged(); setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    /** The four corner heights relative to this block (copy), order NW NE SW SE. */
    public double[] corners() { return h.clone(); }
    @Override protected com.descentmtb.ramp.ShapeKey buildShapeKey() {
        return com.descentmtb.ramp.ShapeKey.surface(h, deck, beam, getMaterial()).withOverlay(overlay);
    }
    public boolean beam(){return beam;}
    public boolean deck() { return deck; }
    public int overlay(){return overlay;}
    public double rawHeight(double x, double z) { return TrailMath.bilerp(h, x, z); }
    public double height(double x, double z) { return Math.max(0, Math.min(1, rawHeight(x,z)+OverlayMath.bump(overlay,x,z))); }
    public double bottom(double x, double z) { return deck ? Math.max(0, Math.min(1, rawHeight(x,z)-.14)) : 0; }
    public boolean hasSurface(double x,double z) { return height(x,z)>.00001 && height(x,z)>bottom(x,z)+.00001; }
    public double slopeX(double x, double z) { if(overlay!=0)return (height(x+.001,z)-height(x-.001,z))/.002;double v=rawHeight(x,z);return v>0&&v<1?(h[1]-h[0])*(1-z)+(h[3]-h[2])*z:0; }
    public double slopeZ(double x, double z) { if(overlay!=0)return (height(x,z+.001)-height(x,z-.001))/.002;double v=rawHeight(x,z);return v>0&&v<1?(h[2]-h[0])*(1-x)+(h[3]-h[1])*x:0; }
    public VoxelShape shape() {
        if (shape != null) return shape;
        VoxelShape s = Shapes.empty();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            double a = x / 4.0, b = z / 4.0;
            double top = Math.max(Math.max(height(a, b), height(a + .25, b)), Math.max(height(a, b + .25), height(a + .25, b + .25)));
            double low=deck?Math.min(Math.min(bottom(a,b),bottom(a+.25,b)),Math.min(bottom(a,b+.25),bottom(a+.25,b+.25))):0;
            if (top < .001 || top<=low+.00001) continue;
            s = Shapes.or(s, Shapes.box(a, low, b, a + .25, top, b + .25));
        }
        if(beam && bottom(.5,.5)>0)s=Shapes.or(s,Shapes.box(.375,0,.375,.625,bottom(.5,.5),.625));
        return shape = s.optimize();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < 4; i++) tag.putDouble("Corner" + i, h[i]); tag.putBoolean("Deck", deck);tag.putBoolean("Beam",beam);
        tag.putInt("Overlay",overlay);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < 4; i++) h[i] = Math.max(-16, Math.min(16, tag.getDouble("Corner" + i)));
        deck = tag.getBoolean("Deck"); beam=tag.getBoolean("Beam");overlay=Math.max(0,Math.min(2,tag.getInt("Overlay"))); shape = null;shapeChanged();
    }
}
