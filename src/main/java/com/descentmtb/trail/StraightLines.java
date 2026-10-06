package com.descentmtb.trail;

/**
 * Pure (Minecraft-free) geometry of the straight line and of the corridor the path clearing works in
 * ({@link ShapeMode#STRAIGHT_LINE}, {@link ShapeMode#CLEAR_PATH}). A line runs from the middle of the top face of block A
 * to the middle of the top face of block B and has ONE constant grade between them. Its surface is a plane: the height
 * depends only on how far along the line a point is, so it is the same across the whole width (level cross-section) and
 * two neighbouring blocks that share a corner read exactly the same height there. A line may rise, fall or run level, and
 * it does not care what is under it: over air it is a bridge.
 */
public final class StraightLines {
    /** The longest line (m): survival players, and creative players / operators. */
    public static final int MAX_LENGTH = 64, MAX_LENGTH_CREATIVE = 128;
    /** The longest path (m) a survival player may clear; creative players and operators may clear {@link #MAX_LENGTH_CREATIVE}. */
    public static final int SURVIVAL_CLEAR_LENGTH = 32;

    /** The longest line (m) a player may build; {@code bulk} is true for creative players and operators. */
    public static int maxLength(boolean bulk) {
        return bulk ? MAX_LENGTH_CREATIVE : MAX_LENGTH;
    }

    /** The longest path (m) a player may clear; {@code bulk} is true for creative players and operators. */
    public static int maxClearLength(boolean bulk) {
        return bulk ? MAX_LENGTH_CREATIVE : SURVIVAL_CLEAR_LENGTH;
    }
    /** The steepest line (degrees). */
    public static final double MAX_DEGREES = 45;
    private static final double EPS = 1e-9;
    /** The two points must be at least this far apart on the ground (m). */
    public static final double MIN_HORIZONTAL = 2;
    /** The widths (blocks) a line may have. */
    public static final int MIN_WIDTH = 1, MAX_WIDTH = 5;

    /** Why a line cannot be built. */
    public enum Problem {
        TOO_SHORT, TOO_LONG, TOO_STEEP;

        /** Language key of the message. */
        public String key() {
            return "descentmtb.line." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * A line from block A to block B.
     *
     * @param width blocks across, centred on the line
     */
    public record Layout(int ax, int ay, int az, int bx, int by, int bz, int width) {
        /** Start of the line: the middle of the top face of A. */
        public double startX() {
            return ax + .5;
        }

        public double startZ() {
            return az + .5;
        }

        public double startY() {
            return ay + 1;
        }

        /** Distance on the ground between the two points (m). */
        public double horizontal() {
            return Math.hypot(bx - ax, bz - az);
        }

        /** Length of the line itself (m). */
        public double length() {
            return Math.hypot(horizontal(), by - ay);
        }

        /** Rise over run: positive when B is higher than A. */
        public double slope() {
            return horizontal() < 1e-9 ? 0 : (by - ay) / horizontal();
        }

        /** The grade in percent (rise over run times 100), negative downhill. */
        public double percent() {
            return slope() * 100;
        }

        /** The angle of the line (degrees), negative downhill. */
        public double degrees() {
            return Math.toDegrees(Math.atan(slope()));
        }

        /** What is wrong with the line for a player who may build up to {@code maxLength} m, or null when it is fine. */
        public Problem problem(double maxLength) {
            if (horizontal() < MIN_HORIZONTAL) {
                return Problem.TOO_SHORT;
            }
            if (length() > maxLength + 1e-9) {
                return Problem.TOO_LONG;
            }
            return Math.abs(degrees()) > MAX_DEGREES + 1e-9 ? Problem.TOO_STEEP : null;
        }

        /** Metres along the line (on the ground) of the point (px, pz); 0 at A, {@link #horizontal} at B. */
        public double along(double px, double pz) {
            double length = horizontal();
            return ((px - startX()) * (bx - ax) + (pz - startZ()) * (bz - az)) / length;
        }

        /** Metres to the right (looking from A to B) of the line of the point (px, pz). */
        public double across(double px, double pz) {
            double length = horizontal();
            return ((px - startX()) * -(bz - az) + (pz - startZ()) * (bx - ax)) / length;
        }

        /** Absolute height of the surface at the vertex or point (px, pz): the plane through the two ends. */
        public double height(double px, double pz) {
            return startY() + slope() * along(px, pz);
        }

        /**
         * True for the centre (px, pz) of a column that belongs to the surface of the line: along it from A to B and
         * within the width across. The width is cut [-w/2, w/2) so an even width is one column more on one side, and it
         * is widened a little on a diagonal so the blocks of a thin line still touch along their edges.
         */
        public boolean contains(double px, double pz) {
            double u = along(px, pz), v = across(px, pz), widen = (diagonal() - 1) / 2;
            return u >= -.5 * diagonal() - EPS && u <= horizontal() + .5 * diagonal() + EPS
                    && v >= -width / 2.0 - widen - EPS && v < width / 2.0 + widen - EPS;
        }

        /** How far a column extends along an axis of the line: 1 for a line along a block axis, up to sqrt 2 on a diagonal. */
        private double diagonal() {
            double length = horizontal();
            return (Math.abs(bx - ax) + Math.abs(bz - az)) / length;
        }

        /**
         * True for the centre (px, pz) of a column within {@code margin} blocks of a corridor of the line's width: along
         * it from A to B (the ends included) and across it. Used by the path clearing.
         */
        public boolean inCorridor(double px, double pz, double margin) {
            double u = along(px, pz), v = across(px, pz);
            return u >= -margin - .5 && u <= horizontal() + margin + .5 && Math.abs(v) <= width / 2.0 + margin + (diagonal() - 1) / 2 + EPS;
        }

        /** Block bounds {@code {minX, minZ, maxX, maxZ}} of the columns within {@code margin} blocks of the corridor. */
        public int[] bounds(double margin) {
            double reach = width / 2.0 + margin + 1;
            int minX = (int) Math.floor(Math.min(ax, bx) - reach), maxX = (int) Math.ceil(Math.max(ax, bx) + reach);
            int minZ = (int) Math.floor(Math.min(az, bz) - reach), maxZ = (int) Math.ceil(Math.max(az, bz) + reach);
            return new int[]{minX, minZ, maxX, maxZ};
        }
    }

    private StraightLines() {}
}
