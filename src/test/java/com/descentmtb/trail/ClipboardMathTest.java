package com.descentmtb.trail;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClipboardMathTest {
    @Test
    void sizeLimits() {
        assertTrue(ClipboardMath.fits(32, 24, 32));
        assertFalse(ClipboardMath.fits(33, 5, 5));
        assertFalse(ClipboardMath.fits(5, 25, 5));
        assertFalse(ClipboardMath.fits(0, 5, 5));
        assertEquals(1 + 4, ClipboardMath.boxHeight(70, 70));
        assertEquals(3 + 4, ClipboardMath.boxHeight(72, 70));
    }

    @Test
    void aQuarterTurnMovesTheNorthWestBlockToTheNorthEastCorner() {
        // a 3 (x) by 2 (z) box becomes 2 by 3
        assertArrayEquals(new int[]{1, 0}, ClipboardMath.rotateOffset(0, 0, 3, 2, 1));
        assertArrayEquals(new int[]{1, 2}, ClipboardMath.rotateOffset(2, 0, 3, 2, 1));
        assertArrayEquals(new int[]{0, 2}, ClipboardMath.rotateOffset(2, 1, 3, 2, 1));
        assertArrayEquals(new int[]{2, 3}, ClipboardMath.rotatedSize(3, 2, 1));
        assertArrayEquals(new int[]{3, 2}, ClipboardMath.rotatedSize(3, 2, 2));
    }

    @Test
    void everyOffsetStaysInsideTheRotatedBoxAndFourTurnsComeHome() {
        int sizeX = 5, sizeZ = 3;
        for (int turns = 0; turns < 4; turns++) {
            int[] size = ClipboardMath.rotatedSize(sizeX, sizeZ, turns);
            boolean[][] taken = new boolean[size[0]][size[1]];
            for (int dx = 0; dx < sizeX; dx++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    int[] moved = ClipboardMath.rotateOffset(dx, dz, sizeX, sizeZ, turns);
                    assertTrue(moved[0] >= 0 && moved[0] < size[0] && moved[1] >= 0 && moved[1] < size[1]);
                    assertFalse(taken[moved[0]][moved[1]], "two blocks landed on the same spot");
                    taken[moved[0]][moved[1]] = true;
                }
            }
        }
        assertArrayEquals(new int[]{4, 1}, ClipboardMath.rotateOffset(4, 1, sizeX, sizeZ, 4));
    }

    @Test
    void cornersTurnWithTheBlock() {
        // a ramp rising to the east (NE and SE high) rises to the south after a clockwise quarter turn (SW and SE high)
        assertArrayEquals(new double[]{0, 0, 1, 1}, ClipboardMath.rotateCorners(new double[]{0, 1, 0, 1}, 1));
        // ... to the west after two (NW and SW high), to the north after three (NW and NE high)
        assertArrayEquals(new double[]{1, 0, 1, 0}, ClipboardMath.rotateCorners(new double[]{0, 1, 0, 1}, 2));
        assertArrayEquals(new double[]{1, 1, 0, 0}, ClipboardMath.rotateCorners(new double[]{0, 1, 0, 1}, 3));
        assertArrayEquals(new double[]{0, 1, 2, 3}, ClipboardMath.rotateCorners(new double[]{0, 1, 2, 3}, 4));
        // a single high corner walks clockwise: NW -> NE -> SE -> SW
        assertArrayEquals(new double[]{0, 1, 0, 0}, ClipboardMath.rotateCorners(new double[]{1, 0, 0, 0}, 1));
        assertArrayEquals(new double[]{0, 0, 0, 1}, ClipboardMath.rotateCorners(new double[]{1, 0, 0, 0}, 2));
        assertArrayEquals(new double[]{0, 0, 1, 0}, ClipboardMath.rotateCorners(new double[]{1, 0, 0, 0}, 3));
    }
}
