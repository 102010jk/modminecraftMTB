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
    /** One click of {@link ShapeMode#WHOLE} moves the block by this much (the cursor has its own step, {@link CursorSettings}). */
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
     * @return the plan, or null when the block cannot be shaped (air, a chest, a standalone ramp ...) or the
     *         mode does not reshape single blocks (ramp tuning, berm building, copying)
     */
    public static Plan plan(Level level, BlockPos pos, ShapeMode mode, Vec3 hit, Direction facing, boolean shift) {
        return plan(level, pos, mode, hit, facing, shift, CursorSettings.DEFAULT);
    }

    /**
     * Like {@link #plan(Level, BlockPos, ShapeMode, Vec3, Direction, boolean)}, with the cursor's sub-type and step
     * (they only matter for {@link ShapeMode#AUTO}).
     */
    public static Plan plan(Level level, BlockPos pos, ShapeMode mode, Vec3 hit, Direction facing, boolean shift,
                            CursorSettings cursor) {
        Editable editable = mode.reshapesBlock() ? editable(level, pos) : null;
        if (editable == null) {
            return null;
        }
        ColumnEditor.Column column = editable.column();
        int frame = editable.frame();
        double[] local = editable.local();
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
                affected = CornerEdits.picked(cursor.pick(), fx, fz);
                shaped = CornerEdits.nudge(local, affected, cursor.step().size, shift ? -1 : 1);
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
     * A block the shaper can edit: its column and the block Y its corner heights are local to (see {@link #frameOf}).
     * The block editor shows and sends heights relative to {@code frame}.
     */
    public record Editable(ColumnEditor.Column column, int frame) {
        /** The column's corner heights relative to {@link #frame}. */
        public double[] local() {
            return relativeTo(frame);
        }

        /**
         * The block Y the block editor's heights are relative to: {@link #frame}, unless the shape reaches more than
         * {@link BlockShapes#MAX_HEIGHT} above it (a tall stack of support blocks); then the block of the lowest corner,
         * so the editor never shows a height it could not keep.
         */
        public int editorFrame() {
            double[] local = local();
            double high = Math.max(Math.max(local[0], local[1]), Math.max(local[2], local[3]));
            return high <= BlockShapes.MAX_HEIGHT + EPS ? frame : (int) Math.floor(BlockShapes.lowest(column.abs()) - EPS);
        }

        /** The column's corner heights relative to {@link #editorFrame}. */
        public double[] editorLocal() {
            return relativeTo(editorFrame());
        }

        private double[] relativeTo(int base) {
            double[] local = new double[4];
            for (int i = 0; i < 4; i++) {
                local[i] = column.abs()[i] - base;
            }
            return local;
        }
    }

    /** The block at {@code pos} as the shaper edits it, or null when it cannot be shaped (air, a chest, a standalone ramp ...). */
    public static Editable editable(Level level, BlockPos pos) {
        ColumnEditor.Column column = shapeableColumn(level, pos);
        return column == null ? null : new Editable(column, frameOf(level, pos, column));
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

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private ShapePresets() {}
}
