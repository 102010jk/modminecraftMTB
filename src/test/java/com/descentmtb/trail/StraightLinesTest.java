package com.descentmtb.trail;

import com.descentmtb.trail.StraightLines.Layout;
import com.descentmtb.trail.StraightLines.Problem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The geometry of the straight line: it runs exactly from the top of block A to the top of block B, has one grade all the way,
 * is level across, and two neighbouring blocks read the same height at the corners they share.
 */
class StraightLinesTest {
    private static final double EPS = 1e-9;

    @Test
    void theLineRunsFromTheTopOfAToTheTopOfB() {
        Layout line = new Layout(10, 64, 20, 30, 68, 20, 3);
        assertEquals(64 + 1, line.height(10.5, 20.5), EPS);
        assertEquals(68 + 1, line.height(30.5, 20.5), EPS);
        assertEquals(20, line.horizontal(), EPS);
        assertEquals(Math.hypot(20, 4), line.length(), EPS);
        assertEquals(20, line.percent(), EPS);
        assertEquals(Math.toDegrees(Math.atan(.2)), line.degrees(), EPS);
    }

    @Test
    void theGradeIsTheSameEverywhereAndLevelAcross() {
        for (Layout line : new Layout[]{new Layout(0, 70, 0, 37, 52, 11, 4), new Layout(5, 60, 5, 5, 66, 40, 1), new Layout(-8, 80, 3, -20, 80, -9, 5)}) {
            double step = .37;
            double ux = (line.bx() - line.ax()) / line.horizontal(), uz = (line.bz() - line.az()) / line.horizontal();
            for (double along = 0; along < line.horizontal() - step; along += step) {
                double x = line.startX() + ux * along, z = line.startZ() + uz * along;
                double rise = line.height(x + ux * step, z + uz * step) - line.height(x, z);
                assertEquals(line.slope() * step, rise, 1e-9, "constant grade along " + line);
                for (double side : new double[]{-2.3, -1, .6, 2.4}) {
                    assertEquals(line.height(x, z), line.height(x - uz * side, z + ux * side), 1e-9, "level across " + line);
                }
            }
        }
    }

    @Test
    void neighbouringBlocksShareTheirCorners() {
        // the height is one function of the vertex, so the east corners of a column and the west corners of the next one
        // (which the builder reads as height(x + 1, z) for the first and height(x', z) with x' = x + 1 for the second) agree
        Layout line = new Layout(3, 64, 3, 29, 71, 15, 5);
        int shared = 0;
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 25; z++) {
                if (!line.contains(x + .5, z + .5) || !line.contains(x + 1.5, z + .5)) {
                    continue;
                }
                int nextX = x + 1;
                assertEquals(line.height(x + 1, z), line.height(nextX, z), 0);
                assertEquals(line.height(x + 1, z + 1), line.height(nextX, z + 1), 0);
                // and the rise from the west to the east corners of one block is the same in both rows of corners
                assertEquals(line.height(x + 1, z) - line.height(x, z), line.height(x + 1, z + 1) - line.height(x, z + 1), 1e-9);
                shared++;
            }
        }
        assertTrue(shared > 50);
    }

    @Test
    void theFootprintHasTheWidthAndTheEnds() {
        Layout line = new Layout(10, 64, 20, 30, 64, 20, 3);
        int columns = 0;
        for (int x = 0; x < 50; x++) {
            for (int z = 0; z < 40; z++) {
                if (line.contains(x + .5, z + .5)) {
                    columns++;
                    assertTrue(x >= 10 && x <= 30 && z >= 19 && z <= 21, "inside the strip: " + x + "," + z);
                }
            }
        }
        assertEquals(21 * 3, columns, "21 columns long (both ends included), 3 wide");
        for (int width = 1; width <= 5; width++) {
            Layout alongX = new Layout(10, 64, 20, 40, 64, 20, width), alongZ = new Layout(10, 64, 20, 10, 64, 50, width);
            int acrossX = 0, acrossZ = 0;
            for (int k = -5; k <= 5; k++) {
                acrossX += alongX.contains(20.5, 20.5 + k) ? 1 : 0;
                acrossZ += alongZ.contains(10.5 + k, 30.5) ? 1 : 0;
            }
            assertEquals(width, acrossX, "width " + width + " along x");
            assertEquals(width, acrossZ, "width " + width + " along z");
        }
    }

    @Test
    void aDiagonalLineIsOneConnectedStripOfBlocks() {
        Layout line = new Layout(10, 64, 10, 30, 69, 30, 1);
        int columns = 0;
        for (int x = 0; x < 50; x++) {
            for (int z = 0; z < 50; z++) {
                columns += line.contains(x + .5, z + .5) ? 1 : 0;
            }
        }
        assertTrue(columns >= 21 && columns <= 45, "a thin diagonal is about a column per block along it, not gaps: " + columns);
        // every column of it touches the next along an edge, not only at a corner
        for (int k = 0; k < 20; k++) {
            boolean joined = line.contains(10 + k + 1.5, 10 + k + .5) || line.contains(10 + k + .5, 10 + k + 1.5);
            assertTrue(joined, "the block at " + k + " is joined to its neighbour along an edge");
        }
    }

    @Test
    void theLimitsOfLengthAndGrade() {
        assertNull(new Layout(0, 64, 0, 60, 64, 0, 3).problem(StraightLines.MAX_LENGTH));
        assertEquals(Problem.TOO_LONG, new Layout(0, 64, 0, 70, 64, 0, 3).problem(StraightLines.MAX_LENGTH));
        assertNull(new Layout(0, 64, 0, 120, 64, 0, 3).problem(StraightLines.MAX_LENGTH_CREATIVE));
        assertEquals(Problem.TOO_SHORT, new Layout(0, 64, 0, 0, 90, 0, 3).problem(StraightLines.MAX_LENGTH));
        assertNull(new Layout(0, 64, 0, 10, 74, 0, 3).problem(StraightLines.MAX_LENGTH), "exactly 45 degrees is fine");
        assertEquals(Problem.TOO_STEEP, new Layout(0, 64, 0, 10, 75, 0, 3).problem(StraightLines.MAX_LENGTH));
        assertEquals(Problem.TOO_STEEP, new Layout(0, 64, 0, 10, 53, 0, 3).problem(StraightLines.MAX_LENGTH), "downhill counts too");
    }

    @Test
    void creativePlayersAndOperatorsMayBuildAndClearMore() {
        assertEquals(64, StraightLines.maxLength(false));
        assertEquals(128, StraightLines.maxLength(true));
        assertEquals(32, StraightLines.maxClearLength(false));
        assertEquals(128, StraightLines.maxClearLength(true));
    }

    @Test
    void theCorridorHasAMarginAroundTheWidth() {
        Layout line = new Layout(10, 64, 20, 30, 64, 20, 3);
        assertTrue(line.inCorridor(20.5, 20.5 + 2, 1));
        assertFalse(line.inCorridor(20.5, 20.5 + 3, 1));
        assertTrue(line.inCorridor(10.5 - 1, 20.5, 1));
        assertFalse(line.inCorridor(10.5 - 3, 20.5, 1));
        int[] box = line.bounds(1);
        assertTrue(box[0] <= 10 - 2 && box[2] >= 30 + 2 && box[1] <= 20 - 3 && box[3] >= 20 + 3);
    }
}
