package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Works out what a Trail Shaper mode does to ONE block: the new corner heights of the clicked block's column.
 * It reads the neighbours (a ramp continues the height of the block behind it) but never plans a change
 * to any other column. The server applies the {@link Plan}; the client draws it as a preview.
 */
public final class ShapePresets {
    /** One manual click moves a corner by this much. */
    public static final double NUDGE = 1.0 / 16;
    /** A neighbour further than this above or below the clicked block is not continued. */
    private static final double MAX_NEIGHBOUR_STEP = 1.5;
    private static final double EPS = 1e-6;
    private static final int[] ALL_CORNERS = {0, 1, 2, 3};

    /**
     * @param column   the clicked block's column as it is now
     * @param newAbs   its new absolute corner heights, NW NE SW SE
     * @param affected the corners the preset deliberately moves (shown in the preview)
     */
    public record Plan(ColumnEditor.Column column, double[] newAbs, int[] affected) {
        /** False when the preset would leave the block exactly as it is. */
        public boolean changesAnything() {
            for (int i = 0; i < 4; i++) {
                if (Math.abs(newAbs[i] - column.abs()[i]) > EPS) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * @param hit    where the block was clicked (world coordinates)
     * @param facing the player's horizontal facing: ramps rise away from the player
     * @param shift  Shift held: ramps rise towards the player, manual clicks lower
     * @return the plan, or null when the block cannot be shaped (air, a chest, a standalone ramp ...)
     */
    public static Plan plan(Level level, BlockPos pos, ShapeMode mode, Vec3 hit, Direction facing, boolean shift) {
        ColumnEditor.Column column = shapeableColumn(level, pos);
        if (column == null) {
            return null;
        }
        int frame = frameOf(level, pos, column);
        double[] local = new double[4];
        for (int i = 0; i < 4; i++) {
            local[i] = column.abs()[i] - frame;
        }
        double fx = clamp01(hit.x - pos.getX()), fz = clamp01(hit.z - pos.getZ());

        double[] shaped;
        int[] affected = ALL_CORNERS;
        switch (mode) {
            case RAMP_QUARTER, RAMP_HALF, RAMP_FULL, DROP_HALF -> {
                int dirX = shift ? -facing.getStepX() : facing.getStepX();
                int dirZ = shift ? -facing.getStepZ() : facing.getStepZ();
                double base = backEdgeHeight(level, pos, column, dirX, dirZ) - frame;
                if (Double.isNaN(base)) {
                    base = BlockShapes.lowest(local);
                }
                shaped = BlockShapes.slope(base, mode.rise, dirX, dirZ);
            }
            case BANK_LEFT_HALF, BANK_LEFT_FULL -> shaped = bank(local, mode, facing.getCounterClockWise());
            case BANK_RIGHT_HALF, BANK_RIGHT_FULL -> shaped = bank(local, mode, facing.getClockWise());
            case CORNER_BANK -> {
                int high = (fx >= .5 ? 1 : 0) + (fz >= .5 ? 2 : 0);
                shaped = BlockShapes.cornerBank(BlockShapes.lowest(local), mode.rise, high);
            }
            case AUTO -> {
                affected = pickedCorners(pos, fx, fz);
                shaped = BlockShapes.nudge(local, affected, shift ? -NUDGE : NUDGE);
            }
            case WHOLE -> shaped = BlockShapes.nudge(local, ALL_CORNERS, shift ? -NUDGE : NUDGE);
            case FLATTEN -> shaped = BlockShapes.flatten(local);
            case RESET -> shaped = new double[]{1, 1, 1, 1};
            default -> throw new IllegalStateException("unhandled mode " + mode);
        }
        double[] newAbs = new double[4];
        for (int i = 0; i < 4; i++) {
            newAbs[i] = frame + shaped[i];
        }
        return new Plan(column, newAbs, affected);
    }

    /**
     * The block Y that heights are local to: the lowest layer of the clicked column (the clicked block itself unless
     * it is an upper layer). Clicking any layer of a stacked column therefore gives the same result, and a
     * surface can be lowered back across a block boundary.
     */
    private static int frameOf(Level level, BlockPos pos, ColumnEditor.Column column) {
        int frame = Math.min(pos.getY(), ColumnShaper.layers(column.abs(), column.deck()).bottom());
        for (int i = 0; i < ColumnShaper.MAX_LAYERS && isSupportLayer(level, new BlockPos(pos.getX(), frame - 1, pos.getZ()), column); i++) {
            frame--;
        }
        return frame;
    }

    /** True for a layer of the same shaped surface, or the full solid block that supports it. */
    private static boolean isSupportLayer(Level level, BlockPos below, ColumnEditor.Column column) {
        if (!(level.getBlockEntity(below) instanceof TrailSurfaceEntity layer) || layer.deck() != column.deck()) {
            return false;
        }
        double[] heights = layer.corners();
        boolean full = true;
        boolean samePlane = true;
        for (int i = 0; i < 4; i++) {
            full &= heights[i] >= 1 - EPS;
            samePlane &= Math.abs(below.getY() + heights[i] - column.abs()[i]) < EPS;
        }
        return full || samePlane;
    }

    private static double[] bank(double[] local, ShapeMode mode, Direction side) {
        return BlockShapes.slope(BlockShapes.lowest(local), mode.rise, side.getStepX(), side.getStepZ());
    }

    /** The column of a shaped block, or of a plain full block (which the shaper turns into a shaped copy of itself). */
    private static ColumnEditor.Column shapeableColumn(Level level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        boolean shaped = entity instanceof TrailSurfaceEntity;
        boolean plain = entity == null && RampBlock.isValidMaterial(level.getBlockState(pos), level, pos);
        if (!shaped && !plain) {
            return null;
        }
        ColumnEditor.Column column = ColumnEditor.read(level, pos.getX(), pos.getZ(), pos.getY());
        return column == null || column.copycat() ? null : column;
    }

    /**
     * Absolute height where the block behind the clicked one (against the rise direction) meets it: the average
     * of the two neighbour corners on the shared edge. NaN when there is no neighbour or it is too far off.
     * Read-only.
     */
    private static double backEdgeHeight(Level level, BlockPos pos, ColumnEditor.Column column, int dirX, int dirZ) {
        ColumnEditor.Column behind = ColumnEditor.read(level, pos.getX() - dirX, pos.getZ() - dirZ, pos.getY());
        if (behind == null) {
            return Double.NaN;
        }
        double sum = 0;
        int count = 0;
        for (int corner = 0; corner < 4; corner++) {
            double t = (corner % 2 - .5) * dirX + (corner / 2 - .5) * dirZ + .5;
            if (t > .99) {   // the neighbour's front edge touches our back edge
                sum += behind.abs()[corner];
                count++;
            }
        }
        double edge = sum / count;
        double top = Math.max(Math.max(column.abs()[0], column.abs()[1]), Math.max(column.abs()[2], column.abs()[3]));
        return Math.abs(edge - top) <= MAX_NEIGHBOUR_STEP ? edge : Double.NaN;
    }

    /** Corners (0..3) of this block that a click at (fx, fz) refers to: a corner zone, an edge or the whole block. */
    private static int[] pickedCorners(BlockPos pos, double fx, double fz) {
        return ColumnShaper.pickVertices(pos.getX(), pos.getZ(), fx, fz).stream()
                .mapToInt(v -> (v.x() - pos.getX()) + 2 * (v.z() - pos.getZ())).toArray();
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private ShapePresets() {}
}
