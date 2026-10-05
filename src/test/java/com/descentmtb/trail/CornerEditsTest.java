package com.descentmtb.trail;

import com.descentmtb.trail.CornerEdits.Pick;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pure corner edits of the cursor sub-types and the block editor. */
class CornerEditsTest {
    private static final double EPS = 1e-9;
    /** A ramp rising to the east: NW NE SW SE. */
    private static final double[] EAST_RAMP = {0, 1, 0, 1};

    @Test
    void rotatingTurnsClockwiseSeenFromAbove() {
        assertArrayEquals(new double[]{0, 0, 1, 1}, CornerEdits.rotateClockwise(EAST_RAMP), EPS);   // now rises to the south
        double[] shape = {.1, .2, .3, .4};
        double[] turned = shape;
        for (int i = 0; i < 4; i++) {
            turned = CornerEdits.rotateClockwise(turned);
        }
        assertArrayEquals(shape, turned, EPS, "four quarter turns are a full turn");
        assertEquals(.1, CornerEdits.rotateClockwise(shape)[1], EPS, "the north-west corner moves to the north-east");
    }

    @Test
    void mirroringSwapsTheSides() {
        assertArrayEquals(new double[]{1, 0, 1, 0}, CornerEdits.mirrorX(EAST_RAMP), EPS);
        assertArrayEquals(EAST_RAMP, CornerEdits.mirrorZ(EAST_RAMP), EPS, "an east ramp is symmetric north to south");
        double[] shape = {.1, .2, .3, .4};
        assertArrayEquals(new double[]{.3, .4, .1, .2}, CornerEdits.mirrorZ(shape), EPS);
        assertArrayEquals(shape, CornerEdits.mirrorX(CornerEdits.mirrorX(shape)), EPS);
    }

    @Test
    void stepsMoveOnTheirGrid() {
        assertEquals(1 + 1.0 / 16, CornerEdits.stepped(1, 1.0 / 16, 1), EPS);
        assertEquals(1 - 1.0 / 16, CornerEdits.stepped(1, 1.0 / 16, -1), EPS);
        assertEquals(1.25, CornerEdits.stepped(1, .25, 1), EPS);
        // off the grid: snaps to the next grid line in that direction
        assertEquals(.5, CornerEdits.stepped(5.0 / 16, .25, 1), EPS);
        assertEquals(.25, CornerEdits.stepped(5.0 / 16, .25, -1), EPS);
        assertEquals(.25, CornerEdits.stepped(3.0 / 16, 1.0 / 8, 1), EPS);
        // float noise on a grid line still counts as on it
        assertEquals(.75, CornerEdits.stepped(.5 + 1e-12, .25, 1), EPS);
        assertEquals(.25, CornerEdits.stepped(.5 - 1e-12, .25, -1), EPS);
    }

    @Test
    void nudgeMovesOnlyThePickedCornersAndClamps() {
        assertArrayEquals(new double[]{0, 1.125, 0, 1}, CornerEdits.nudge(EAST_RAMP, new int[]{1}, .125, 1), EPS);
        assertArrayEquals(new double[]{0, 1, 0, 1}, CornerEdits.nudge(EAST_RAMP, new int[]{0, 2}, .25, -1), EPS);
        assertEquals(BlockShapes.MAX_HEIGHT, CornerEdits.nudge(new double[]{3, 3, 3, 3}, new int[]{0}, .25, 1)[0], EPS);
    }

    @Test
    void subTypesPickTheirCorners() {
        assertArrayEquals(new int[]{3}, CornerEdits.picked(Pick.CORNER, .6, .9));
        assertArrayEquals(new int[]{0}, CornerEdits.picked(Pick.CORNER, .4, .1));
        assertArrayEquals(new int[]{2}, CornerEdits.picked(Pick.CORNER, .5 - 1e-3, .5));   // a click on the middle line counts as the south / east half
        assertArrayEquals(new int[]{1, 3}, CornerEdits.picked(Pick.EDGE, .9, .5));
        assertArrayEquals(new int[]{0, 2}, CornerEdits.picked(Pick.EDGE, .1, .4));
        assertArrayEquals(new int[]{0, 1}, CornerEdits.picked(Pick.EDGE, .45, .05));
        assertArrayEquals(new int[]{2, 3}, CornerEdits.picked(Pick.EDGE, .6, .8));
        assertArrayEquals(new int[]{0, 1, 2, 3}, CornerEdits.picked(Pick.WHOLE, .1, .1));
        // the zones: corner, edge, middle
        assertArrayEquals(new int[]{0}, CornerEdits.picked(Pick.AUTO_ZONE, .1, .1));
        assertEquals(2, CornerEdits.picked(Pick.AUTO_ZONE, .95, .5).length);
        assertEquals(4, CornerEdits.picked(Pick.AUTO_ZONE, .5, .5).length);
    }

    @Test
    void sixteenthsRoundTripAndClamp() {
        int[] s = CornerEdits.toSixteenths(new double[]{0, .5, 1.03, 2.9});
        assertArrayEquals(new int[]{0, 8, 16, 46}, s);
        assertArrayEquals(new double[]{0, .5, 1, 46 / 16.0}, CornerEdits.fromSixteenths(s), EPS);
        assertArrayEquals(new double[]{0, 3, 0, 0}, CornerEdits.fromSixteenths(new int[]{-5, 99, 0, 0}), EPS);
    }

    @Test
    void subTypesAndStepsCycleAndFallBack() {
        assertEquals(Pick.CORNER, Pick.AUTO_ZONE.cycled(1));
        assertEquals(Pick.WHOLE, Pick.AUTO_ZONE.cycled(-1));
        assertEquals(Pick.AUTO_ZONE, Pick.fromName("nonsense"));
        assertEquals(CornerEdits.Step.QUARTER, CornerEdits.Step.SIXTEENTH.cycled(-1));
        assertEquals(CornerEdits.Step.SIXTEENTH, CornerEdits.Step.fromName(""));
    }
}
