package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World side of shaping: reads the shaped surface of a block column, and turns new corner heights into
 * the block changes (via {@link ColumnShaper}). {@link #rebuild} only touches the one column it is given. Reads
 * shaped blocks, standalone ramps (read-only) and plain terrain (a plain block becomes a shaped copy of itself).
 */
public final class ColumnEditor {
    /** Search window (blocks) around the Y hint when looking for the surface of a column. */
    private static final int SCAN_UP = 3, SCAN_DOWN = 4;
    private static final double EPS = 1e-4;

    /** The surface of one block column: absolute corner heights {NW, NE, SW, SE} and what it is made of. */
    public record Column(int x, int z, double[] abs, BlockState material, boolean deck, net.minecraft.nbt.CompoundTag decoration,boolean copycat) {
        public Column(int x,int z,double[] abs,BlockState material,boolean deck){this(x,z,abs,material,deck,null);}
        public Column(int x,int z,double[] abs,BlockState material,boolean deck,net.minecraft.nbt.CompoundTag decoration){this(x,z,abs,material,deck,decoration,false);}
    }

    /** @return the column's surface near {@code yHint}, or null when there is nothing to stand on there. */
    public static Column read(Level level, int x, int z, int yHint) {
        BlockPos hint=new BlockPos(x,yHint,z);
        if(!level.isLoaded(hint))return null;
        boolean picked=level.getBlockEntity(hint) instanceof RampBlockEntity
                || !level.getBlockState(hint).getCollisionShape(level,hint).isEmpty();
        // A clicked ground/deck layer takes precedence over a separate bridge above it.
        for (int y = picked?yHint:yHint+SCAN_UP; y >= yHint - SCAN_DOWN; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            var be = level.getBlockEntity(pos);
            if (be instanceof TrailSurfaceEntity shaped) {
                return new Column(x, z, ColumnShaper.absolute(y, shaped.corners()), shaped.getMaterial(), shaped.deck(),shaped.saveWithoutMetadata(level.registryAccess()));
            }
            if (RampBlock.isRamp(state)) {
                BlockState material = be instanceof RampBlockEntity ramp ? ramp.getMaterial() : Blocks.COARSE_DIRT.defaultBlockState();
                double hi = 1 - EPS;
                return new Column(x, z, new double[]{
                        y + RampBlock.heightAt(state, 0, 0), y + RampBlock.heightAt(state, hi, 0),
                        y + RampBlock.heightAt(state, 0, hi), y + RampBlock.heightAt(state, hi, hi)}, material, false,null,true);
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                boolean full = RampBlock.isValidMaterial(state, level, pos);
                return new Column(x, z, new double[]{top, top, top, top},
                        full ? state : Blocks.COARSE_DIRT.defaultBlockState(), false);
            }
        }
        return null;
    }

    /** Block changes that give {@code column} the new corner heights, clearing / filling the layers around. */
    public static Map<BlockPos, TrailEdit.Change> rebuild(Level level, Column column, double[] newAbs) {
        if(column.copycat())throw new TrailEdit.Rejected("descentmtb.edit.old_ramp");
        Map<BlockPos, TrailEdit.Change> changes = new LinkedHashMap<>();
        double[] occupied=newAbs.clone();
        double amplitude=column.decoration()==null?0:OverlayMath.amplitude(column.decoration().getInt("Overlay"));
        if(amplitude>0)for(int i=0;i<4;i++)occupied[i]+=amplitude;
        ColumnShaper.Layers stack = ColumnShaper.layers(occupied, column.deck());
        int bottom=ColumnShaper.layers(newAbs,column.deck()).bottom();
        BlockState surface = ModBlocks.TRAIL_SURFACE.get().defaultBlockState();
        for (int y=bottom;y<=stack.top();y++) {
            double[] local=newAbs.clone();for(int i=0;i<4;i++)local[i]-=y;
            changes.put(new BlockPos(column.x(), y, column.z()),
                    new TrailEdit.Change(surface, column.decoration()==null?null:column.decoration().copy(), local, column.material(), column.deck()));
        }

        // old shaped layers above the new surface are no longer part of it
        for (int y = stack.top() + 1; y <= stack.top() + ColumnShaper.MAX_LAYERS; y++) {
            BlockPos pos = new BlockPos(column.x(), y, column.z());
            if (sameSurface(level, pos, column)) {
                changes.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
            }
        }

        // below it: a deck leaves open space, ground stays solid
        boolean fillingAir = !column.deck();
        for (int y = bottom - 1; y >= bottom - ColumnShaper.MAX_LAYERS; y--) {
            BlockPos pos = new BlockPos(column.x(), y, column.z());
            if (sameSurface(level, pos, column)) {
                if (column.deck()) {
                    changes.put(pos, TrailEdit.Change.block(Blocks.AIR.defaultBlockState()));
                } else {
                    changes.put(pos, new TrailEdit.Change(surface, null, new double[]{1, 1, 1, 1}, column.material(), false));
                }
            } else if (fillingAir && level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
                changes.put(pos, TrailEdit.Change.block(Blocks.DIRT.defaultBlockState()));
            } else {
                break;
            }
        }
        return changes;
    }

    /**
     * The other layers of the shaped surface that {@code clicked} belongs to (above and below it, the same plane),
     * so an edit that replaces the clicked block can remove the whole column in the same step.
     */
    public static java.util.List<BlockPos> otherLayers(Level level, BlockPos clicked) {
        java.util.List<BlockPos> layers = new java.util.ArrayList<>();
        if (!(level.getBlockEntity(clicked) instanceof TrailSurfaceEntity be)) {
            return layers;
        }
        Column column = new Column(clicked.getX(), clicked.getZ(), ColumnShaper.absolute(clicked.getY(), be.corners()),
                be.getMaterial(), be.deck());
        for (int y = clicked.getY() - ColumnShaper.MAX_LAYERS; y <= clicked.getY() + ColumnShaper.MAX_LAYERS; y++) {
            BlockPos pos = new BlockPos(clicked.getX(), y, clicked.getZ());
            if (y != clicked.getY() && level.isLoaded(pos) && sameSurface(level, pos, column)) {
                layers.add(pos);
            }
        }
        return layers;
    }

    /** Remove only layers of the old edited plane; a separate bridge is a different object. */
    private static boolean sameSurface(Level level,BlockPos pos,Column column) {
        if(!level.isLoaded(pos)||!(level.getBlockEntity(pos) instanceof TrailSurfaceEntity be)||be.deck()!=column.deck())return false;
        double[] old=ColumnShaper.absolute(pos.getY(),be.corners());
        for(int i=0;i<4;i++)if(Math.abs(old[i]-column.abs()[i])>EPS)return false;
        return true;
    }

    private ColumnEditor() {}
}
