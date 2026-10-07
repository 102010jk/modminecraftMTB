package com.descentmtb.trick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpinCurveTest {
    @Test void spinIsThrownThenCaught() {
        assertEquals(0, TrickAnimation.spinCurve(0), 1e-12);
        assertEquals(1, TrickAnimation.spinCurve(1), 1e-12);
        double prev = 0, fastest = 0, fastestAt = 0;
        for (int i = 1; i <= 100; i++) {
            double p = i / 100.0, v = TrickAnimation.spinCurve(p);
            assertTrue(v >= prev - 1e-12, "monotonic at " + p);
            if (v - prev > fastest) { fastest = v - prev; fastestAt = p; }
            prev = v;
        }
        assertTrue(fastestAt < 0.35, "flicked early: " + fastestAt);
        double catchStep = TrickAnimation.spinCurve(1) - TrickAnimation.spinCurve(0.99);
        assertTrue(catchStep < 0.05 * fastest, "slows right down into the catch");
    }
}
