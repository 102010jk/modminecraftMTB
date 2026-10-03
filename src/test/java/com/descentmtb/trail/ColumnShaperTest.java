package com.descentmtb.trail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColumnShaperTest {
    @Test void aLevelSurfaceInsideOneBlockIsOneLayer() {
        var l = ColumnShaper.layers(new double[]{64.2, 64.2, 64.2, 64.2}, false);
        assertEquals(1, l.layers().size());
        assertEquals(64, l.bottom());
        assertEquals(64, l.top());
        assertArrayEquals(new double[]{.2, .2, .2, .2}, l.layers().get(0).heights(), 1e-12);
    }

    @Test void crossingAnIntegerHeightKeepsOnePlaneInEveryLayer() {
        double[] abs = {63.9, 64.5, 64.1, 65.3};
        var l = ColumnShaper.layers(abs, false);
        assertEquals(63, l.bottom());
        assertEquals(65, l.top());
        assertEquals(3, l.layers().size());
        for (var layer : l.layers()) {
            assertArrayEquals(abs, ColumnShaper.absolute(layer.y(), layer.heights()), 1e-12, "every layer holds the same plane");
        }
    }

    @Test void stackedLayersGiveTheContinuousSurface() {
        double[] abs = {63.9, 64.5, 64.1, 65.3};
        var l = ColumnShaper.layers(abs, false);
        for (double fx = .02; fx < 1; fx += .12) {
            for (double fz = .02; fz < 1; fz += .12) {
                assertEquals(ColumnShaper.surfaceAt(abs, fx, fz), ColumnShaper.stackedSurfaceAt(l, fx, fz), 1e-9,
                        "no phantom step at " + fx + "," + fz);
            }
        }
    }

    @Test void aDeckReachesBelowItsSurface() {
        var l = ColumnShaper.layers(new double[]{64.05, 64.05, 64.05, 64.05}, true);
        assertEquals(63, l.bottom(), "the deck underside (64.05 - 0.14) lies in the block below");
        assertEquals(64, l.top());
    }

    @Test void tooSteepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ColumnShaper.layers(new double[]{60, 60, 80, 80}, false));
    }

    @Test void negativeHeightsStillLayerCorrectly() {
        var l = ColumnShaper.layers(new double[]{-0.5, -0.2, -0.3, -0.1}, false);
        assertEquals(-1, l.bottom());
        assertEquals(-1, l.top());
    }
}
