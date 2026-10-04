package com.descentmtb.trail;

/**
 * Pure (Minecraft-free) geometry of the Trail Shaper's clipboard: how big a selection may be, and how a copied
 * box turns by quarter turns clockwise (seen from above, so east becomes south).
 *
 * <p>A box has {@code sizeX} x {@code sizeZ} columns; a block sits at the offset {@code (dx, dz)} from its
 * north-west corner. A shaped block keeps its four corner heights in the order NW NE SW SE (see
 * {@link BlockShapes}).
 */
public final class ClipboardMath {
    public static final int MAX_SIZE_X = 32, MAX_SIZE_Z = 32, MAX_HEIGHT = 24;
    /** Air above the highest selected block that is copied (and cleared again when pasting). */
    public static final int HEADROOM = 4;

    /** True when a box of this size may be copied. */
    public static boolean fits(int sizeX, int sizeY, int sizeZ) {
        return sizeX >= 1 && sizeZ >= 1 && sizeY >= 1 && sizeX <= MAX_SIZE_X && sizeZ <= MAX_SIZE_Z && sizeY <= MAX_HEIGHT;
    }

    /** Height of the box spanned by two corners at the given block Ys: the span between them plus the headroom. */
    public static int boxHeight(int y1, int y2) {
        return Math.abs(y1 - y2) + 1 + HEADROOM;
    }

    /** Footprint {@code {sizeX, sizeZ}} of a box after {@code turns} quarter turns. */
    public static int[] rotatedSize(int sizeX, int sizeZ, int turns) {
        return Math.floorMod(turns, 2) == 0 ? new int[]{sizeX, sizeZ} : new int[]{sizeZ, sizeX};
    }

    /** Where the block at offset {@code (dx, dz)} of a {@code sizeX} x {@code sizeZ} box ends up: {@code {dx', dz'}}. */
    public static int[] rotateOffset(int dx, int dz, int sizeX, int sizeZ, int turns) {
        int x = dx, z = dz, width = sizeX, depth = sizeZ;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            int nextX = depth - 1 - z;
            int nextZ = x;
            x = nextX;
            z = nextZ;
            int swap = width;
            width = depth;
            depth = swap;
        }
        return new int[]{x, z};
    }

    /** The corner heights {@code {NW, NE, SW, SE}} of a shaped block after {@code turns} quarter turns clockwise. */
    public static double[] rotateCorners(double[] corners, int turns) {
        double[] result = corners.clone();
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            result = new double[]{result[2], result[0], result[3], result[1]};
        }
        return result;
    }

    private ClipboardMath() {}
}
