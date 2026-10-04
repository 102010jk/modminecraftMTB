package com.descentmtb.trail;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockShapesTest {
    private static final double EPS = 1e-9;

    @Test
    void slopeFacingEastRisesOnTheEastCorners() {
        assertArrayEquals(new double[]{1, 1.5, 1, 1.5}, BlockShapes.slope(1, .5, 1, 0), EPS);
    }

    @Test
    void slopeFacingNorthRisesOnTheNorthCorners() {
        assertArrayEquals(new double[]{1.5, 1.5, 1, 1}, BlockShapes.slope(1, .5, 0, -1), EPS);
    }

    @Test
    void slopeFacingSouthAndWestMirrorTheOthers() {
        assertArrayEquals(new double[]{1, 1, 1.25, 1.25}, BlockShapes.slope(1, .25, 0, 1), EPS);
        assertArrayEquals(new double[]{1.25, 1, 1.25, 1}, BlockShapes.slope(1, .25, -1, 0), EPS);
    }

    @Test
    void aDropFallsTowardsTheFrontEdge() {
        assertArrayEquals(new double[]{1, .5, 1, .5}, BlockShapes.slope(1, -.5, 1, 0), EPS);
    }

    @Test
    void cornerBankPeaksAtOneCornerAndStaysLowAtTheOpposite() {
        assertArrayEquals(new double[]{1.5, 1.25, 1.25, 1}, BlockShapes.cornerBank(1, .5, 0), EPS);
        assertArrayEquals(new double[]{1, 1.25, 1.25, 1.5}, BlockShapes.cornerBank(1, .5, 3), EPS);
        assertArrayEquals(new double[]{1.25, 1.5, 1, 1.25}, BlockShapes.cornerBank(1, .5, 1), EPS);
    }

    @Test
    void flattenAveragesAndRoundsToSixteenths() {
        assertArrayEquals(new double[]{.75, .75, .75, .75}, BlockShapes.flatten(new double[]{.5, 1, .5, 1}), EPS);
        // average .55 is 8.8/16 and rounds to 9/16
        double[] flat = BlockShapes.flatten(new double[]{.5, .5, .5, .7});
        assertEquals(.5625, flat[0], EPS);
        assertEquals(flat[0], flat[3], EPS);
    }

    @Test
    void nudgeOnlyTouchesTheListedCorners() {
        assertArrayEquals(new double[]{1, 1.0625, 1, 1.0625}, BlockShapes.nudge(new double[]{1, 1, 1, 1}, new int[]{1, 3}, 1.0 / 16), EPS);
    }

    @Test
    void resultsAreClampedToTheBlockRange() {
        assertArrayEquals(new double[]{0, 0, 0, 0}, BlockShapes.nudge(new double[]{.03, 0, .01, 0}, new int[]{0, 1, 2, 3}, -.5), EPS);
        double[] steep = BlockShapes.slope(2.8, 1, 1, 0);
        assertEquals(2.8, steep[0], EPS);
        assertEquals(3, steep[1], EPS);
        assertArrayEquals(new double[]{0, 0, .5, .5}, BlockShapes.slope(-1, 1.5, 0, 1), EPS);
    }

    @Test
    void lowestPicksTheMinimum() {
        assertEquals(.25, BlockShapes.lowest(new double[]{1, .25, .75, 1}), EPS);
    }
}
