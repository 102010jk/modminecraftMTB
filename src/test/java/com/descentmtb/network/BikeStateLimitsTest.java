package com.descentmtb.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BikeStateLimitsTest {
    @Test
    void clampsVisualFloatsToWhatTheSimCanProduce() {
        assertEquals(1.6f, BikeStateLimits.lean(5f));
        assertEquals(-1.6f, BikeStateLimits.lean(-1e9f));
        assertEquals(0.4f, BikeStateLimits.lean(0.4f));
        assertEquals(1f, BikeStateLimits.steer(Float.POSITIVE_INFINITY));
        assertEquals(0f, BikeStateLimits.compression(-0.5f));
        assertEquals(0.3f, BikeStateLimits.compression(2f));
        assertEquals(-1f, BikeStateLimits.rider(-3f));
        assertEquals(1f, BikeStateLimits.unit(7f));
        assertEquals(0f, BikeStateLimits.unit(-0.1f));
    }

    @Test
    void nanBecomesZero() {
        assertEquals(0f, BikeStateLimits.lean(Float.NaN));
        assertEquals(0f, BikeStateLimits.compression(Float.NaN));
        assertEquals(0f, BikeStateLimits.unit(Float.NaN));
        assertEquals(0f, BikeStateLimits.crank(Float.NaN));
        assertEquals(0.0, BikeStateLimits.wrapAngle(Double.NaN));
    }

    @Test
    void masksFlagsToDefinedStateBits() {
        assertEquals(BikeStatePayload.AIRBORNE | BikeStatePayload.BAILED | BikeStatePayload.WALL_RIDE,
                BikeStateLimits.maskFlags((byte) 0xFF));
        assertEquals(0, BikeStateLimits.maskFlags(BikeStatePayload.TELEPORT), "TELEPORT is a request, never stored");
        assertEquals(BikeStatePayload.BAILED, BikeStateLimits.maskFlags(BikeStatePayload.BAILED));
    }

    @Test
    void wrapsAnglesIntoMinusPiToPi() {
        assertEquals(0.5, BikeStateLimits.wrapAngle(0.5 + 4 * Math.PI), 1e-9);
        assertEquals(-Math.PI + 0.1, BikeStateLimits.wrapAngle(Math.PI + 0.1), 1e-9);
        assertEquals(-Math.PI, BikeStateLimits.wrapAngle(Math.PI), 1e-9);
        for (double a = -50; a < 50; a += 0.37) {
            double w = BikeStateLimits.wrapAngle(a);
            assertTrue(w >= -Math.PI && w < Math.PI, "wrapped " + a + " to " + w);
            assertEquals(Math.sin(a), Math.sin(w), 1e-9);
        }
    }

    @Test
    void crankFoldKeepsTheVisibleAngle() {
        assertEquals(12.5f, BikeStateLimits.crank(12.5f));
        float big = (float) (BikeStateLimits.CRANK_PERIOD * 3 + 1.0);
        float folded = BikeStateLimits.crank(big);
        assertTrue(Math.abs(folded) <= BikeStateLimits.CRANK_PERIOD);
        // float precision at this size is ~0.002 rad, so compare loosely
        assertEquals(Math.sin(big), Math.sin(folded), 1e-2);
    }

    @Test
    void trickIdAndSideFallBackToDefaults() {
        assertEquals(0, BikeStateLimits.trickId(-1, 5));
        assertEquals(0, BikeStateLimits.trickId(5, 5));
        assertEquals(4, BikeStateLimits.trickId(4, 5));
        assertEquals(-1, BikeStateLimits.trickSide(-7));
        assertEquals(1, BikeStateLimits.trickSide(0));
    }

    @Test
    void pressureRejectsNonFiniteValues() {
        assertEquals(26f, BikeStateLimits.pressure(Float.NaN, 26f, 5, 65));
        assertEquals(26f, BikeStateLimits.pressure(Float.POSITIVE_INFINITY, 26f, 5, 65));
        assertEquals(65f, BikeStateLimits.pressure(1000f, 26f, 5, 65));
        assertEquals(30f, BikeStateLimits.pressure(30f, 26f, 5, 65));
    }

    @Test
    void speedScaleCapsTheHandoffSpeed() {
        assertEquals(1.0, BikeStateLimits.speedScale(10 * 10, 80));
        assertEquals(0.5, BikeStateLimits.speedScale(160 * 160, 80), 1e-12);
    }

    @Test
    void angularRateNeedsNearbyReports() {
        assertEquals(0, BikeStateLimits.angularRate(0, 1, 0));
        assertEquals(0, BikeStateLimits.angularRate(0, 1, 10));
        assertEquals(2.0, BikeStateLimits.angularRate(0, 0.1, 1), 1e-9);
        // across the +-pi seam the short way round is taken
        assertEquals(-2.0, BikeStateLimits.angularRate(-Math.PI + 0.05, Math.PI - 0.05, 1), 1e-9);
        assertEquals(BikeStateLimits.MAX_ANGULAR_RATE, BikeStateLimits.angularRate(0, 3, 1));
    }
}
