package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Rides with keyboard-like inputs (keys are 0 or 1, exactly what BikeInputHandler produces) and prints what
 * a player would feel: heading changes in the air with no keys held, how fast the bike turns, whether it
 * settles after a turn. Used to tune the ride; the assertions live in the other tests once a behaviour is fixed.
 */
class RealInputTest {
    static final double DT = 0.05;

    static double deg(double r) {
        return Math.toDegrees(r);
    }

    static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    static Controls keys(boolean z, boolean left, boolean right, boolean up, boolean down, boolean x) {
        float steer = (right ? 1 : 0) - (left ? 1 : 0);
        float lean = (up ? 1 : 0) - (down ? 1 : 0);
        return new Controls(steer, lean, z ? 1 : 0, 0, x ? -1 : 0, 0, false, 0, 0);
    }

    /** Kicker 3 blocks wide in a block world (smoothed by the real BlockTerrain), sides drop off. */
    static Terrain blockKicker(int halfWidth) {
        return TestTerrains.blocks((x, z) -> {
            if (Math.abs(x) > halfWidth || z < 10) return 64;
            if (z <= 13) return 64 + (z - 9) * .5;      // 0.5, 1.0, 1.5, 2.0 rising in 4 blocks
            if (z <= 19) return 62;                      // pit
            return 64;
        }, Terrain.Surface.DIRT);
    }

    void jump(String name, Terrain terrain, double x0, double yawDeg, double speed, boolean pop) {
        BikeSim sim = new BikeSim(new BikeParams(), terrain);
        sim.place(x0, 64, 0, Math.toRadians(yawDeg));
        sim.vel = sim.forward().mul(speed);
        sim.riderVel = sim.vel;
        double yawTakeoff = 0, latTakeoff = 0, maxYawDev = 0, maxLean = 0, yawRateTakeoff = 0;
        boolean wasAir = false;
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < 80 && !sim.bailed; i++) {
            boolean beforeLip = sim.pos.z < 9.5;
            boolean popNow = pop && sim.pos.z > 11.2 && sim.pos.z < 13.5;
            Controls c = new Controls(0, 0, beforeLip ? 1 : 0, 0, popNow ? 1 : (pop && sim.pos.z > 9.5 && sim.pos.z <= 11.2 ? -1 : 0), 0, false, 0, 0);
            sim.tick(c, DT);
            sim.events.clear();
            if (sim.airborne && !wasAir) {
                yawTakeoff = sim.yaw;
                V3 right = new V3(Math.cos(sim.yaw), 0, Math.sin(sim.yaw));
                latTakeoff = sim.vel.dot(right);
                yawRateTakeoff = -sim.omega.dot(V3.Y);
            }
            if (sim.airborne) {
                maxYawDev = Math.max(maxYawDev, Math.abs(wrap(sim.yaw - yawTakeoff)));
                maxLean = Math.max(maxLean, Math.abs(sim.lean));
                if (i % 2 == 0) trace.append(String.format(Locale.ROOT, " %.0f", deg(wrap(sim.yaw - yawTakeoff))));
            }
            wasAir = sim.airborne;
        }
        System.out.printf(Locale.ROOT, "[real] %-28s takeoff yawRate %.2f rad/s lateral %.2f m/s | air yaw dev max %.0f° | lean max %.0f° | bail=%s %s | yaw:%s%n",
                name, yawRateTakeoff, latTakeoff, deg(maxYawDev), deg(maxLean), sim.bailed, sim.bailReason == null ? "" : sim.bailReason, trace);
    }

    @Test void jumpsWithNoKeysInTheAir() {
        jump("analytic kicker centred", FeelSpecTest.kickerLine(), 0, 0, 9.2, false);
        jump("analytic kicker pop", FeelSpecTest.kickerLine(), 0, 0, 9.2, true);
        jump("block kicker centred", blockKicker(1), .5, 0, 8.5, false);
        jump("block kicker pop", blockKicker(1), .5, 0, 8.5, true);
        jump("block kicker off-centre", blockKicker(1), 1.2, 0, 8.5, false);
        jump("block kicker 8° approach", blockKicker(1), .5, 8, 8.5, false);
        jump("block kicker 1 wide", blockKicker(0), .5, 0, 8.5, false);
    }

    /** Full lock from straight: time to reach 45° and 90° heading, and how the yaw rate dies after release. */
    void turn(String name, double speed, Terrain.Surface surface) {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(64, surface));
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(speed);
        sim.riderVel = sim.vel;
        double y0 = sim.yaw, t45 = -1, t90 = -1;
        int i = 0;
        for (; i < 80; i++) {
            sim.tick(keys(true, false, true, false, false, false), DT);
            sim.events.clear();
            double d = Math.abs(wrap(sim.yaw - y0));
            if (t45 < 0 && d > Math.PI / 4) t45 = (i + 1) * DT;
            if (d > Math.PI / 2) {
                t90 = (i + 1) * DT;
                break;
            }
        }
        double rateAtRelease = -sim.omega.dot(V3.Y);
        double yawAtRelease = sim.yaw;
        for (int k = 0; k < 10; k++) {
            sim.tick(keys(true, false, false, false, false, false), DT);
            sim.events.clear();
        }
        double overshoot = deg(Math.abs(wrap(sim.yaw - yawAtRelease)));
        double radius = speed / Math.max(.01, Math.abs(rateAtRelease));
        System.out.printf(Locale.ROOT, "[real] turn %-7s %4.1f m/s: 45° in %.2f s, 90° in %.2f s, radius %.1f m, after release +%.0f° more, speed %.1f, bail=%s%n",
                surface, speed, t45, t90, radius, overshoot, sim.speed(), sim.bailed);
    }

    @Test void turnsWithKeys() {
        for (double v : new double[]{3, 5, 7, 10}) {
            turn("", v, Terrain.Surface.DIRT);
        }
        turn("", 7, Terrain.Surface.GRASS);
    }
}
