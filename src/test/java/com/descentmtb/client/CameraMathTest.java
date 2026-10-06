package com.descentmtb.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraMathTest {
    @Test
    void smoothingIsFrameRateIndependent() {
        double a = 0, b = 0;
        for (int i = 0; i < 60; i++) a = CameraMath.approach(a, 1, 1 / 60.0, 0.2);
        for (int i = 0; i < 144; i++) b = CameraMath.approach(b, 1, 1 / 144.0, 0.2);
        assertEquals(a, b, 1e-9);
        assertEquals(1 - Math.exp(-1 / 0.2), a, 1e-9);
        assertEquals(0, CameraMath.blend(0, 0.2));
        assertEquals(1, CameraMath.blend(0.016, 0));          // tau 0 = rigid
    }

    @Test
    void anglesTakeTheShortWay() {
        double cur = Math.toRadians(170), target = Math.toRadians(-170);
        double next = CameraMath.approachAngle(cur, target, 0.05, 0.1);
        assertTrue(Math.abs(CameraMath.wrap(next - cur)) > 0, "moved");
        assertTrue(CameraMath.wrap(next - cur) > 0, "crossed +-180 forwards, not the long way round");
        assertEquals(0, CameraMath.wrap(2 * Math.PI), 1e-12);
    }

    @Test
    void headingFollowsTravelButNotStandingOrBackwards() {
        double yaw = 0.3;
        assertEquals(yaw, CameraMath.headingYaw(0, 0, yaw, 4), 1e-12);                    // standing
        double bx = Math.sin(yaw) * 5, bz = -Math.cos(yaw) * 5;                            // rolling backwards
        assertEquals(yaw, CameraMath.headingYaw(bx, bz, yaw, 4), 1e-12);
        // fast, travelling 20 degrees off the bike's yaw: camera takes the travel direction
        double travel = yaw + Math.toRadians(20);
        double h = CameraMath.headingYaw(-Math.sin(travel) * 10, Math.cos(travel) * 10, yaw, 4);
        assertEquals(travel, h, 1e-9);
        // a 90 degree slide is followed by at most 70 degrees
        double slide = yaw + Math.toRadians(90);
        h = CameraMath.headingYaw(-Math.sin(slide) * 10, Math.cos(slide) * 10, yaw, 4);
        assertTrue(h - yaw <= Math.toRadians(70) + 1e-9);
    }

    @Test
    void orbitHoldsThenSpringsBackAfterTheDelay() {
        CameraMath.Orbit o = new CameraMath.Orbit();
        o.add(Math.toRadians(80), Math.toRadians(20));
        double yaw0 = o.yaw;
        for (int i = 0; i < 90; i++) o.update(1 / 60.0, 1.5, 0.2);       // 1.5 s, still held
        assertEquals(yaw0, o.yaw, 1e-9);
        for (int i = 0; i < 120; i++) o.update(1 / 60.0, 1.5, 0.2);      // 2 s of spring-back
        assertEquals(0, o.yaw, 1e-3);
        assertEquals(0, o.pitch, 1e-3);
        assertFalse(o.active() && Math.abs(o.yaw) > 1e-3);
        o.add(0.5, 0.5);
        o.update(10, 1.5, 0.2);
        o.reset();
        assertFalse(o.active());
    }

    @Test
    void movingTheMouseKeepsTheOrbitHeld() {
        CameraMath.Orbit o = new CameraMath.Orbit();
        for (int i = 0; i < 240; i++) {                                   // 4 s of steady mouse movement
            o.add(0.002, 0);
            o.update(1 / 60.0, 1.5, 0.2);
        }
        assertEquals(240 * 0.002, o.yaw, 1e-9);
        o.add(0, 10);
        assertEquals(CameraMath.MAX_ORBIT_PITCH, o.pitch, 1e-12);
    }

    @Test
    void eyeOffsetHasTheConfiguredDistanceAndHeight() {
        for (double side : new double[]{-1, 1}) {
            double yaw = 0.7;
            double[] o = CameraMath.eyeOffset(yaw, 0, 3.5, 1.6, 0, side);
            assertEquals(3.5, Math.hypot(o[0], o[2]), 1e-9);
            assertEquals(1.6, o[1], 1e-9);
            // behind (-1) is against the travel direction, ahead (+1) along it
            double along = o[0] * -Math.sin(yaw) + o[2] * Math.cos(yaw);
            assertEquals(side * 3.5, along, 1e-9);
        }
    }

    @Test
    void orbitSwingsTheCameraAroundWithoutChangingItsDistance() {
        double[] o = CameraMath.eyeOffset(0, Math.PI / 2, 8, 4.5, 0, -1);
        assertEquals(Math.hypot(8, 4.5), Math.sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2]), 1e-9);
        assertEquals(0, o[2], 1e-9);                                       // swung round to the side
        assertTrue(Math.abs(o[0]) > 7.9);
        // tilting up raises it, never past the limit
        double[] up = CameraMath.eyeOffset(0, 0, 3.5, 1.6, Math.toRadians(500), -1);
        assertEquals(Math.hypot(3.5, 1.6) * Math.sin(CameraMath.MAX_ELEVATION), up[1], 1e-9);
    }

    @Test
    void terrainPullsTheCameraInAndItEasesBackOut() {
        assertEquals(1, CameraMath.collisionScale(3.5, -1, 0.2, 0.15), 1e-12);            // no hit
        assertEquals((1.0 - 0.2) / 3.5, CameraMath.collisionScale(3.5, 1.0, 0.2, 0.15), 1e-12);
        assertEquals(0, CameraMath.collisionScale(3.5, 0.1, 0.2, 0.15), 1e-12);   // wall at the rider
        assertEquals(1, CameraMath.collisionScale(3.5, 9, 0.2, 0.15), 1e-12);             // hit beyond the ray

        double s = 1;
        s = CameraMath.relaxScale(s, 0.3, 1 / 60.0, 0.25);
        assertEquals(0.3, s, 1e-12);                                      // in at once
        double prev = s;
        for (int i = 0; i < 30; i++) {
            s = CameraMath.relaxScale(s, 1, 1 / 60.0, 0.25);
            assertTrue(s > prev && s <= 1, "eases out monotonically without overshoot");
            prev = s;
        }
        for (int i = 0; i < 300; i++) s = CameraMath.relaxScale(s, 1, 1 / 60.0, 0.25);
        assertEquals(1, s, 1e-3);
    }

    @Test
    void closeObstacleAlwaysWinsOverMinimumDistance() {
        for (double hit : new double[]{0, .01, .08, .15, .3}) {
            double distance = CameraMath.collisionScale(4, hit, .2, .15) * 4;
            assertTrue(distance <= hit, "eye must stay on the near side");
        }
    }

    @Test
    void orbitReturnIsIndependentOfFramesStraddlingHold() {
        CameraMath.Orbit slow = new CameraMath.Orbit(), fast = new CameraMath.Orbit();
        slow.add(1, .4);
        fast.add(1, .4);
        for (int i = 0; i < 20; i++) slow.update(.05, .733, .4);
        for (int i = 0; i < 100; i++) fast.update(.01, .733, .4);
        assertEquals(Math.exp(-.267 / .4), slow.yaw, 1e-12);
        assertEquals(slow.yaw, fast.yaw, 1e-12);
        assertEquals(slow.pitch, fast.pitch, 1e-12);
    }

    @Test
    void lookAnglesPointAtTheTarget() {
        double[] a = CameraMath.lookAngles(0, 0, 5);                      // +Z is yaw 0
        assertEquals(0, a[0], 1e-9);
        assertEquals(0, a[1], 1e-9);
        a = CameraMath.lookAngles(-5, 0, 0);                              // -X is yaw 90
        assertEquals(90, a[0], 1e-9);
        a = CameraMath.lookAngles(0, -5, 5);                              // looking down = positive pitch
        assertEquals(45, a[1], 1e-9);
    }
}
