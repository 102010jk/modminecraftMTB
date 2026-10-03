package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

/** Scratch: prints an energy trace of a scenario (not an assertion test). */
class DebugRun {
    @Test
    void trace() {
        BikeParams p = new BikeParams();
        BikeSim s = new BikeSim(p, FeelSpecTest.kickerLine());
        s.place(0, 64, 0, 0);
        s.vel = s.forward().mul(9.2);
        s.riderVel = s.vel;
        for (int i = 0; i < 75; i++) {
            s.tick(Controls.NONE, 0.05);
            double ke = 0.5 * p.bikeMass * s.vel.lengthSq() + 0.5 * p.riderMass * s.riderVel.lengthSq();
            double w = s.omega.dot(s.rightAxis());
            double rot = 0.5 * p.inertiaPitch * w * w;
            double pe = p.gravity * (p.bikeMass * s.pos.y + p.riderMass * s.riderPos.y) - p.gravity * 91 * 64.5;
            System.out.printf(java.util.Locale.ROOT,
                    "%2d z=%6.2f y=%6.2f vb=(%5.2f,%5.2f) vr=(%5.2f,%5.2f) E=%7.0f (ke %6.0f rot %4.0f pe %6.0f) pitch=%5.1f F[%s %.3f ov%.3f %4.0f] R[%s %.3f ov%.3f %4.0f] rUp=%.3f rFwd=%.3f%n",
                    i, s.pos.z, s.pos.y, s.vel.z, s.vel.y, s.riderVel.z, s.riderVel.y, ke + rot + pe, ke, rot, pe,
                    Math.toDegrees(s.pitch), s.front.contact ? "c" : "-", s.front.compression, s.front.overshoot, s.front.load,
                    s.rear.contact ? "c" : "-", s.rear.compression, s.rear.overshoot, s.rear.load, s.riderUp, s.riderFwd);
            s.events.clear();
        }
    }
}
