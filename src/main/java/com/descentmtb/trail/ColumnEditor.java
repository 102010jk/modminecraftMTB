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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * World side of hand sculpting: reads the shaped surface of a block column, and turns new corner heights into
 * the block changes (via {@link ColumnShaper}). Works on shaped blocks, reads standalone ramps without converting them, and on
 * plain terrain (a plain block becomes a shaped copy of itself).
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
        if(column.copycat())throw new IllegalArgumentException("Samostatnou rampu upravuj pomocí Trail Tool");
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
            } else if (fillingAir && level.getBlockState(pos).isAir()) {
                changes.put(pos, TrailEdit.Change.block(Blocks.DIRT.defaultBlockState()));
            } else {
                break;
            }
        }
        if(column.deck())DeckSupports.add(level,new Column(column.x(),column.z(),newAbs,column.material(),true,column.decoration()),changes);
        return changes;
    }

    /** Remove only layers of the old edited plane; a separate bridge is a different object. */
    private static boolean sameSurface(Level level,BlockPos pos,Column column) {
        if(!(level.getBlockEntity(pos) instanceof TrailSurfaceEntity be)||be.deck()!=column.deck())return false;
        double[] old=ColumnShaper.absolute(pos.getY(),be.corners());
        for(int i=0;i<4;i++)if(Math.abs(old[i]-column.abs()[i])>EPS)return false;
        return true;
    }

    /**
     * Moves the picked vertices of the block column at (bx, bz) by {@code delta} blocks and returns every
     * changed block. All four columns that share a vertex get the same new height, so the surface stays continuous.
     */
    public static Map<BlockPos, TrailEdit.Change> sculpt(Level level, int bx, int bz, int yHint, double fx, double fz,
                                                         double delta, double lowest, double highest) {
        return sculptVertices(level,bx,bz,yHint,ColumnShaper.pickVertices(bx,bz,fx,fz),delta,lowest,highest,false);
    }

    public static Map<BlockPos, TrailEdit.Change> sculptVertices(Level level, int bx, int bz, int yHint,
            java.util.List<ColumnShaper.Vertex> vertices, double delta, double lowest, double highest, boolean snap) {
        Column clicked = read(level, bx, bz, yHint);
        if (clicked == null) {
            throw new IllegalArgumentException("Tady není co tvarovat");
        }
        Map<Long, Column> columns = new HashMap<>();
        Map<Long, double[]> heights = new HashMap<>();
        for (ColumnShaper.Vertex v : vertices) {
            double now = clicked.abs()[(v.z() - bz) * 2 + (v.x() - bx)];
            double next = Math.max(lowest, Math.min(highest, now + delta));
            if (snap) {
                double closest=.035;
                for(int dz=0;dz<=1;dz++) for(int dx=0;dx<=1;dx++) {
                    int cx=v.x()-1+dx,cz=v.z()-1+dz;
                    if(cx==bx&&cz==bz) continue;
                    Column neighbor=read(level,cx,cz,yHint);
                    if(neighbor==null) continue;
                    double candidate=neighbor.abs()[(1-dz)*2+(1-dx)];
                    double distance=Math.abs(candidate-next);
                    if(distance<closest&&Math.abs(candidate-now)>EPS&&(candidate-now)*delta>0) {
                        next=candidate;closest=distance;
                    }
                }
            }
            // the four columns around this vertex: (vx-1,vz-1) corner 3, (vx,vz-1) corner 2, (vx-1,vz) corner 1, (vx,vz) corner 0
            for (int dz = 0; dz <= 1; dz++) {
                for (int dx = 0; dx <= 1; dx++) {
                    int cx = v.x() - 1 + dx, cz = v.z() - 1 + dz;
                    long key = BlockPos.asLong(cx, 0, cz);
                    Column col = cx == bx && cz == bz ? clicked : columns.computeIfAbsent(key, k -> read(level, cx, cz, yHint));
                    if (col == null || col.copycat()) {
                        continue;
                    }
                    if(clicked.deck() && !col.deck())continue;
                    columns.put(key, col);
                    double[] abs = heights.computeIfAbsent(key, k -> col.abs().clone());
                    abs[(1 - dz) * 2 + (1 - dx)] = next;
                }
            }
        }
        Map<BlockPos, TrailEdit.Change> changes = new LinkedHashMap<>();
        for (var e : heights.entrySet()) {
            changes.putAll(rebuild(level, columns.get(e.getKey()), e.getValue()));
        }
        return changes;
    }

    /**
     * Corner heights for a freshly placed shaping block: matches the shaped neighbours it touches, or becomes a
     * gentle half-block bump when it stands alone.
     */
    public static double[] initialCorners(Level level, BlockPos pos) {
        double[] local = new double[4];
        Column extrapolate=null;
        for(Direction d:Direction.Plane.HORIZONTAL) {
            var neighbor=pos.relative(d);
            if(level.getBlockEntity(neighbor) instanceof TrailSurfaceEntity) {
                extrapolate=read(level,neighbor.getX(),neighbor.getZ(),pos.getY());break;
            }
        }
        for (int i = 0; i < 4; i++) {
            int vx = pos.getX() + i % 2, vz = pos.getZ() + i / 2;
            double sum = 0;
            int found = 0;
            for (int dz = 0; dz <= 1; dz++) {
                for (int dx = 0; dx <= 1; dx++) {
                    int cx = vx - 1 + dx, cz = vz - 1 + dz;
                    if (cx == pos.getX() && cz == pos.getZ()) {
                        continue;
                    }
                    Column col = read(level, cx, cz, pos.getY());
                    boolean shaped = col != null && !(col.abs()[0] == col.abs()[1] && col.abs()[1] == col.abs()[2]
                            && col.abs()[2] == col.abs()[3] && Math.abs(col.abs()[0] - Math.round(col.abs()[0])) < EPS);
                    if (shaped) {
                        sum += col.abs()[(1 - dz) * 2 + (1 - dx)] - pos.getY();
                        found++;
                    }
                }
            }
            double extended=.5;
            if(extrapolate!=null) extended=ColumnShaper.surfaceAt(extrapolate.abs(),vx-extrapolate.x(),vz-extrapolate.z())-pos.getY();
            local[i] = found == 0 ? extended : sum / found;
        }
        return local;
    }

    private ColumnEditor() {}
}
