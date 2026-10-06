package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import java.util.Locale;

/** Prints how quickly the bike answers the keys: heading after a steer key, body after lean/crouch keys. */
class ResponseTest {
    static final double DT = 0.05;

    static double[] steer(BikeParams p, double speed) {
        BikeSim sim = new BikeSim(p, TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, 0);
        sim.vel = sim.forward().mul(speed);
        sim.riderVel = sim.vel;
        double y0 = sim.yaw, t5 = -1, t15 = -1;
        for (int i = 0; i < 40; i++) {
            sim.tick(new Controls(1, 0, .5f, 0, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            double d = Math.toDegrees(Math.abs(RealInputTest.wrap(sim.yaw - y0)));
            if (t5 < 0 && d >= 5) t5 = (i + 1) * DT;
            if (t15 < 0 && d >= 15) { t15 = (i + 1) * DT; break; }
        }
        return new double[]{t5, t15};
    }

    static double[] body(BikeParams p, boolean crouch) {
        BikeSim sim = new BikeSim(p, TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, 0);
        for (int i = 0; i < 10; i++) { sim.tick(Controls.NONE, DT); sim.events.clear(); }
        double target = crouch ? p.riderCrouch : p.riderLeanBack;
        double t63 = -1;
        for (int i = 0; i < 40; i++) {
            sim.tick(crouch ? new Controls(0, 0, 0, 0, -1, 0, false, 0, 0) : new Controls(0, -1, 0, 0, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            double v = crouch ? sim.riderUp : sim.riderFwd;
            if (t63 < 0 && Math.abs(v) >= .63 * Math.abs(target)) t63 = (i + 1) * DT;
        }
        return new double[]{t63};
    }

    @Test void report() {
        BikeParams p = new BikeParams();
        for (double v : new double[]{4, 8}) {
            double[] s = steer(p, v);
            System.out.printf(Locale.ROOT, "[response] steer key at %.0f m/s: 5° after %.2f s, 15° after %.2f s%n", v, s[0], s[1]);
        }
        System.out.printf(Locale.ROOT, "[response] crouch 63%% after %.2f s, lean back 63%% after %.2f s%n", body(p, true)[0], body(p, false)[0]);
    }
}
