package com.descentmtb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelmetCamTest {
    @Test void headTurnsWithinTheConeAndComesBack() {
        CameraMath.HeadLook look = new CameraMath.HeadLook();
        look.add(Math.toRadians(200), Math.toRadians(-90));
        assertEquals(Math.toRadians(70), look.yaw, 1e-9);
        assertEquals(Math.toRadians(-45), look.pitch, 1e-9);
        look.update(1.0, 1.5, 0.2);                  // still inside the hold time
        assertEquals(Math.toRadians(70), look.yaw, 1e-9);
        for (int i = 0; i < 120; i++) look.update(1 / 60.0, 1.5, 0.2);
        assertEquals(0, look.yaw, 1e-3);
        assertEquals(0, look.pitch, 1e-3);
    }

    @Test void flatGroundKeepsTheActionCamTilt() {
        assertEquals(24, CameraMath.helmetPitch(0, 1, 24), 1e-9);
    }

    @Test void steepChuteLooksAheadNotIntoTheDirt() {
        double descent = Math.toRadians(-30);          // nose down 30°
        double locked = CameraMath.helmetPitch(descent, 0, 24);   // in the air: head locked to the bike
        double riding = CameraMath.helmetPitch(descent, 1, 24);
        assertEquals(54, locked, 1e-9);
        assertTrue(riding < 32, "on a 30° chute the view is no more than ~30° down: " + riding);
        assertTrue(riding > 15, "but still looks down the trail: " + riding);
    }

    @Test void climbDoesNotStareAtTheSky() {
        double riding = CameraMath.helmetPitch(Math.toRadians(30), 1, 24);
        assertTrue(riding >= 0, "still level or looking slightly down on a climb: " + riding);
    }

    @Test void traumaShakesThenSettles() {
        CameraMath.Trauma t = new CameraMath.Trauma();
        t.add(5);
        assertEquals(1, t.trauma(), 1e-9);
        double peak = 0;
        for (int i = 0; i < 30; i++) {
            t.update(1 / 60.0);
            for (double v : t.shake()) peak = Math.max(peak, Math.abs(v));
        }
        assertTrue(peak > 1, "a full jolt is visible: " + peak);
        for (int i = 0; i < 120; i++) t.update(1 / 60.0);
        assertEquals(0, t.trauma(), 1e-9);
        assertArrayEquals(new double[3], t.shake(), 1e-12);
    }

    @Test void smallKnocksAreFarSmallerThanBigOnes() {
        CameraMath.Trauma small = new CameraMath.Trauma(), big = new CameraMath.Trauma();
        small.add(0.2);
        big.add(0.8);
        double s = 0, b = 0;
        for (int i = 0; i < 60; i++) {
            small.update(1 / 600.0);
            big.update(1 / 600.0);
            for (double v : small.shake()) s = Math.max(s, Math.abs(v));
            for (double v : big.shake()) b = Math.max(b, Math.abs(v));
        }
        assertTrue(b > 8 * s, "trauma squared: " + s + " vs " + b);
    }

    @Test void landingTraumaGrowsWithImpact() {
        assertEquals(0, CameraMath.landingTrauma(2, 13.5, false), 1e-9);
        double medium = CameraMath.landingTrauma(8, 13.5, false), hard = CameraMath.landingTrauma(13, 13.5, false);
        assertTrue(medium > 0 && hard > medium);
        assertTrue(CameraMath.landingTrauma(8, 13.5, true) > medium + 0.3);
    }
}
