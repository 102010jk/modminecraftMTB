package com.descentmtb.trail;

/**
 * Pure (Minecraft-free) edits of the four corner heights of one block, used by the cursor sub-types and by the
 * block editor: which corners a click picks, stepping on a grid, turning and mirroring.
 *
 * <p>Corner order is NW(0) NE(1) SW(2) SE(3) like {@link BlockShapes}: corner {@code i} sits at {@code x = i % 2},
 * {@code z = i / 2}; +x is east and +z is south.
 */
public final class CornerEdits {
    private static final double EPS = 1e-6;

    /** How the cursor ({@link ShapeMode#AUTO}) decides which corners a click moves. */
    public enum Pick {
        /** By the click zone: a corner zone moves that corner, an edge zone its edge, the middle the whole block. */
        AUTO_ZONE,
        /** Always the corner nearest to the click. */
        CORNER,
        /** Always the two corners of the edge nearest to the click. */
        EDGE,
        /** Always all four corners. */
        WHOLE;

        /** Language key of the name. */
        public String key() {
            return "descentmtb.cursor.pick." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The next (or previous, with a negative step) sub-type, wrapping around. */
        public Pick cycled(int step) {
            Pick[] all = values();
            return all[Math.floorMod(ordinal() + step, all.length)];
        }

        /** The sub-type of that name; {@link #AUTO_ZONE} for an unknown one. */
        public static Pick fromName(String name) {
            for (Pick pick : values()) {
                if (pick.name().equals(name)) {
                    return pick;
                }
            }
            return AUTO_ZONE;
        }
    }

    /** How far one cursor click moves a corner. */
    public enum Step {
        SIXTEENTH(1.0 / 16, "1/16"), EIGHTH(1.0 / 8, "1/8"), QUARTER(1.0 / 4, "1/4");

        public final double size;
        public final String label;

        Step(double size, String label) {
            this.size = size;
            this.label = label;
        }

        /** The next (or previous) step size, wrapping around. */
        public Step cycled(int step) {
            Step[] all = values();
            return all[Math.floorMod(ordinal() + step, all.length)];
        }

        /** The step of that name; {@link #SIXTEENTH} for an unknown one. */
        public static Step fromName(String name) {
            for (Step step : values()) {
                if (step.name().equals(name)) {
                    return step;
                }
            }
            return SIXTEENTH;
        }
    }

    /**
     * The corners (0..3) a click at the in-block position (fx, fz) picks with the given sub-type.
     * {@link Pick#AUTO_ZONE} uses the zones of {@link ColumnShaper#pickVertices}.
     */
    public static int[] picked(Pick pick, double fx, double fz) {
        return switch (pick) {
            case AUTO_ZONE -> ColumnShaper.pickVertices(0, 0, fx, fz).stream().mapToInt(v -> v.x() + 2 * v.z()).toArray();
            case CORNER -> new int[]{(fx >= .5 ? 1 : 0) + (fz >= .5 ? 2 : 0)};
            case EDGE -> {
                double west = fx, east = 1 - fx, north = fz, south = 1 - fz;
                double nearest = Math.min(Math.min(west, east), Math.min(north, south));
                if (nearest == west) {
                    yield new int[]{0, 2};
                }
                if (nearest == east) {
                    yield new int[]{1, 3};
                }
                yield nearest == north ? new int[]{0, 1} : new int[]{2, 3};
            }
            case WHOLE -> new int[]{0, 1, 2, 3};
        };
    }

    /**
     * The next value on the {@code step} grid above {@code value} ({@code direction > 0}) or below it: a height on the
     * grid moves by exactly one step, a height between two grid lines snaps to the next line.
     */
    public static double stepped(double value, double step, int direction) {
        double cells = value / step;
        double moved = direction > 0 ? Math.floor(cells + EPS) + 1 : Math.ceil(cells - EPS) - 1;
        return moved * step;
    }

    /** A copy of {@code corners} with the listed corners moved one {@code step} up or down (see {@link #stepped}). */
    public static double[] nudge(double[] corners, int[] which, double step, int direction) {
        double[] result = corners.clone();
        for (int i : which) {
            result[i] = Math.max(0, Math.min(BlockShapes.MAX_HEIGHT, stepped(result[i], step, direction)));
        }
        return result;
    }

    /** The shape turned 90 degrees clockwise seen from above: the north-west height moves to the north-east and so on. */
    public static double[] rotateClockwise(double[] c) {
        return new double[]{c[2], c[0], c[3], c[1]};
    }

    /** The shape mirrored east to west (along the x axis). */
    public static double[] mirrorX(double[] c) {
        return new double[]{c[1], c[0], c[3], c[2]};
    }

    /** The shape mirrored north to south (along the z axis). */
    public static double[] mirrorZ(double[] c) {
        return new double[]{c[2], c[3], c[0], c[1]};
    }

    /** The heights as whole sixteenths of a block, clamped to {@code 0 .. 16 * MAX_HEIGHT}. */
    public static int[] toSixteenths(double[] corners) {
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            out[i] = clampSixteenths((int) Math.round(corners[i] * 16));
        }
        return out;
    }

    /** Sixteenths back to block heights (clamped). */
    public static double[] fromSixteenths(int[] sixteenths) {
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            out[i] = clampSixteenths(sixteenths[i]) / 16.0;
        }
        return out;
    }

    /** A height in sixteenths limited to what a column may hold ({@code 0 .. 16 * MAX_HEIGHT}). */
    public static int clampSixteenths(int value) {
        return Math.max(0, Math.min((int) Math.round(16 * BlockShapes.MAX_HEIGHT), value));
    }

    private CornerEdits() {}
}
