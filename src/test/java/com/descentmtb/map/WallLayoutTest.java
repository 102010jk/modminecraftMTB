package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WallLayoutTest {
    private static Set<WallLayout.Cell> block(int u0, int v0, int w, int h) {
        Set<WallLayout.Cell> cells = new HashSet<>();
        for (int u = u0; u < u0 + w; u++) for (int v = v0; v < v0 + h; v++) cells.add(new WallLayout.Cell(u, v));
        return cells;
    }

    @Test void aloneFrameIsOneByOne() {
        var r = WallLayout.around(block(0, 0, 1, 1), new WallLayout.Cell(0, 0), 8, 8);
        assertEquals(new WallLayout.Rect(0, 0, 1, 1), r);
    }

    @Test void filledRectangleIsOneMapFromAnyFrame() {
        var cells = block(3, 5, 3, 2);
        for (var c : cells) assertEquals(new WallLayout.Rect(3, 5, 3, 2), WallLayout.around(cells, c, 8, 8));
    }

    @Test void gappedRowsSplitIntoSeparateMaps() {
        var cells = block(0, 0, 2, 1);
        cells.addAll(block(3, 0, 2, 1)); // a hole at u = 2
        assertEquals(new WallLayout.Rect(0, 0, 2, 1), WallLayout.around(cells, new WallLayout.Cell(1, 0), 8, 8));
        assertEquals(new WallLayout.Rect(3, 0, 2, 1), WallLayout.around(cells, new WallLayout.Cell(3, 0), 8, 8));
    }

    @Test void lShapeIsCutIntoNonOverlappingRectanglesAllFramesAgreeOn() {
        var cells = block(0, 0, 3, 1);
        cells.addAll(block(0, 1, 1, 2)); // 3 wide at the bottom, a 1x2 arm on top of its left end
        Set<WallLayout.Cell> covered = new HashSet<>();
        Set<WallLayout.Rect> rects = new HashSet<>();
        for (var c : cells) {
            var r = WallLayout.around(cells, c, 8, 8);
            assertTrue(r.contains(c.u(), c.v()));
            rects.add(r);
        }
        for (var r : rects) for (int u = r.u0(); u < r.u0() + r.w(); u++) for (int v = r.v0(); v < r.v0() + r.h(); v++) {
            assertTrue(cells.contains(new WallLayout.Cell(u, v)), "rectangle must be filled");
            assertTrue(covered.add(new WallLayout.Cell(u, v)), "rectangles must not overlap");
        }
        assertEquals(cells, covered);
    }

    @Test void randomGroupsAlwaysPartitionConsistently() {
        Random random = new Random(7);
        for (int round = 0; round < 200; round++) {
            Set<WallLayout.Cell> cells = new HashSet<>();
            for (int u = 0; u < 6; u++) for (int v = 0; v < 6; v++) if (random.nextInt(4) != 0) cells.add(new WallLayout.Cell(u, v));
            Set<WallLayout.Cell> covered = new HashSet<>();
            Set<WallLayout.Rect> rects = new HashSet<>();
            for (var c : cells) rects.add(WallLayout.around(cells, c, 8, 8));
            for (var r : rects) for (int u = r.u0(); u < r.u0() + r.w(); u++) for (int v = r.v0(); v < r.v0() + r.h(); v++) {
                assertTrue(cells.contains(new WallLayout.Cell(u, v)));
                assertTrue(covered.add(new WallLayout.Cell(u, v)), "overlap in round " + round);
            }
            assertEquals(cells, covered);
        }
    }

    @Test void bigWallIsCappedAtEightByEight() {
        var cells = block(0, 0, 11, 9);
        var r = WallLayout.around(cells, new WallLayout.Cell(0, 0), WallLayout.MAX_SIDE, WallLayout.MAX_SIDE);
        assertEquals(new WallLayout.Rect(0, 0, 8, 8), r);
        var far = WallLayout.around(cells, new WallLayout.Cell(10, 8), WallLayout.MAX_SIDE, WallLayout.MAX_SIDE);
        assertTrue(far.w() <= 8 && far.h() <= 8 && far.contains(10, 8));
    }

    @Test void tilePositionsCountFromTheTopLeft() {
        var r = new WallLayout.Rect(10, 20, 3, 2);
        assertEquals(0, r.column(10));
        assertEquals(2, r.column(12));
        assertEquals(0, r.rowFromTop(21));
        assertEquals(1, r.rowFromTop(20));
    }

    @Test void textureGrowsWithFramesUpToTheCap() {
        assertEquals(128, WallLayout.textureSide(1));
        assertEquals(384, WallLayout.textureSide(3));
        assertEquals(1024, WallLayout.textureSide(8));
        assertEquals(1024, WallLayout.textureSide(20));
    }

    @Test void startOutsideTheGroupIsAlone() {
        assertEquals(new WallLayout.Rect(9, 9, 1, 1), WallLayout.around(block(0, 0, 2, 2), new WallLayout.Cell(9, 9), 8, 8));
    }
}
