package com.descentmtb.entity;

import com.descentmtb.network.BikeStatePayload;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.V3;

/**
 * Everything needed to draw a bike (and place its rider) for one tick. The
 * renderer interpolates between the previous and current snapshot with the
 * partial tick, so it is smooth at any frame rate.
 */
public final class BikeRenderState {
    public V3 com = V3.ZERO, riderPos = V3.ZERO, vel = V3.ZERO;
    /** Unwrapped yaw (rad) - safe to interpolate. */
    public double yaw, pitch, lean, steer;
    public double compF, compR, spinF, spinR, crank;
    public double riderUp, riderFwd;
    public boolean airborne, bailed;

    public void copyFrom(BikeRenderState o) {
        com = o.com;
        riderPos = o.riderPos;
        vel = o.vel;
        yaw = o.yaw;
        pitch = o.pitch;
        lean = o.lean;
        steer = o.steer;
        compF = o.compF;
        compR = o.compR;
        spinF = o.spinF;
        spinR = o.spinR;
        crank = o.crank;
        riderUp = o.riderUp;
        riderFwd = o.riderFwd;
        airborne = o.airborne;
        bailed = o.bailed;
    }

    void fromSim(BikeSim s, BikeRenderState prev) {
        com = s.pos;
        riderPos = s.riderPos;
        vel = s.vel;
        yaw = s.yaw;
        pitch = unwrapNear(s.pitch, prev == null ? s.pitch : prev.pitch);
        lean = s.lean;
        steer = s.steerAngle;
        compF = s.front.compression;
        compR = s.rear.compression;
        spinF = s.front.spinAngle;
        spinR = s.rear.spinAngle;
        crank = s.crankAngle;
        riderUp = s.riderUp;
        riderFwd = s.riderFwd;
        airborne = s.airborne;
        bailed = s.bailed;
    }

    void fromSynced(MountainBikeEntity e, BikeRenderState prev) {
        BikeParams p = MountainBikeEntity.PARAMS;
        V3 newCom = new V3(e.getX(), e.getY() + MountainBikeEntity.COM_HEIGHT, e.getZ());
        double newYaw = Math.toRadians(e.getYRot());
        if (prev != null) newYaw = unwrapNear(newYaw, prev.yaw);
        double newPitch = e.dPitch();
        if (prev != null) newPitch = unwrapNear(newPitch, prev.pitch);

        // wheels roll with the distance travelled along the heading
        double dist = 0;
        if (prev != null) {
            V3 d = newCom.sub(prev.com);
            dist = d.x * -Math.sin(newYaw) + d.z * Math.cos(newYaw);
            vel = d.mul(20);
        }
        boolean air = (e.entityDataFlags() & BikeStatePayload.AIRBORNE) != 0;
        spinF = (prev == null ? 0 : prev.spinF) + dist / p.wheelRadius;
        spinR = (prev == null ? 0 : prev.spinR) + dist / p.wheelRadius;

        com = newCom;
        yaw = newYaw;
        pitch = newPitch;
        lean = e.dLean();
        steer = e.dSteer();
        compF = e.dCompF();
        compR = e.dCompR();
        crank = e.dCrank();
        riderUp = e.dRiderUp();
        riderFwd = e.dRiderFwd();
        airborne = air;
        bailed = e.dBailed();

        // rider centre of mass from the frame pose
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        V3 fH = new V3(-sy, 0, cy);
        V3 fwd = fH.mul(cp).addScaled(V3.Y, sp);
        V3 up = fH.mul(-sp).addScaled(V3.Y, cp);
        riderPos = com.addScaled(up, p.riderHeight + riderUp).addScaled(fwd, p.riderForward + riderFwd);
    }

    static double unwrapNear(double a, double ref) {
        while (a - ref > Math.PI) a -= 2 * Math.PI;
        while (a - ref < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    public static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    public static V3 lerp(double t, V3 a, V3 b) {
        return new V3(lerp(t, a.x, b.x), lerp(t, a.y, b.y), lerp(t, a.z, b.z));
    }
}
