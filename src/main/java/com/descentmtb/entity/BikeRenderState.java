package com.descentmtb.entity;

import com.descentmtb.network.BikeStatePayload;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.OneHand;
import com.descentmtb.physics.V3;
import com.descentmtb.trick.Trick;

/**
 * Everything needed to draw a bike (and place its rider) for one tick. The
 * renderer interpolates between the previous and current snapshot with the
 * partial tick, so it is smooth at any frame rate.
 */
public final class BikeRenderState {
    public V3 com = V3.ZERO, riderPos = V3.ZERO, vel = V3.ZERO;
    /** Unwrapped yaw (rad) - safe to interpolate. */
    public double yaw, pitch, lean, steer;
    /** Roll of the rider's body: the bike's lean on the ground, much less of it in the air (table / tweak). */
    public double riderLean;
    public double compF, compR, spinF, spinR, crank;
    public double riderUp, riderFwd;
    public double brake;
    public boolean airborne, bailed;
    /** The left hand is off the grip ringing the bell, and for how long (s) it has been. */
    public boolean oneHand;
    public double oneHandTime;
    public BikeType bikeType = BikeType.ENDURO;
    public Trick trick = Trick.NONE;
    public double trickAmount, trickProgress;
    public int trickSide = 1;

    public void copyFrom(BikeRenderState o) {
        com = o.com;
        riderPos = o.riderPos;
        vel = o.vel;
        yaw = o.yaw;
        pitch = o.pitch;
        lean = o.lean;
        riderLean = o.riderLean;
        steer = o.steer;
        compF = o.compF;
        compR = o.compR;
        spinF = o.spinF;
        spinR = o.spinR;
        crank = o.crank;
        riderUp = o.riderUp;
        riderFwd = o.riderFwd;
        brake = o.brake;
        airborne = o.airborne;
        bailed = o.bailed;
        oneHand = o.oneHand;
        oneHandTime = o.oneHandTime;
        bikeType = o.bikeType;
        trick = o.trick;
        trickAmount = o.trickAmount;
        trickProgress = o.trickProgress;
        trickSide = o.trickSide;
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
        brake = s.brake;
        airborne = s.airborne;
        bailed = s.bailed;
        oneHand = s.oneHandTimer > 0;
        oneHandTime = oneHand ? OneHand.TIME - s.oneHandTimer : 0;
        bikeType = s.bikeType;
        trick = s.tricks.trick;
        trickAmount = s.tricks.amount;
        trickProgress = s.tricks.progress;
        trickSide = s.tricks.side;
        riderLean = followRider(prev, lean, airborne);
    }

    /** In the air the rider counter-leans: the bike lays over under them, the body stays nearly upright. */
    private static double followRider(BikeRenderState prev, double lean, boolean airborne) {
        double target = airborne ? lean * 0.3 : lean;
        return prev == null ? target : prev.riderLean + (target - prev.riderLean) * 0.45;
    }

    void fromSynced(MountainBikeEntity e, BikeRenderState prev) {
        BikeParams p = e.params();
        bikeType = e.bikeType();
        trick = Trick.byId(e.dTrick());
        trickAmount = e.dTrickAmount();
        trickProgress = e.dTrickProgress();
        trickSide = e.dTrickSide();
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
        brake = e.dBrake();
        airborne = air;
        bailed = e.dBailed();
        oneHand = (e.entityDataFlags() & BikeStatePayload.ONE_HAND) != 0;
        oneHandTime = oneHand ? (prev != null && prev.oneHand ? prev.oneHandTime + 0.05 : 0) : 0;

        // rider centre of mass from the frame pose
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        V3 fH = new V3(-sy, 0, cy);
        V3 fwd = fH.mul(cp).addScaled(V3.Y, sp);
        V3 up = fH.mul(-sp).addScaled(V3.Y, cp);
        riderPos = com.addScaled(up, p.riderHeight + riderUp).addScaled(fwd, p.riderForward + riderFwd);
        riderLean = followRider(prev, lean, airborne);
    }

    /** Rider's feet: on the pedals (bottom-bracket height), fixed to the frame. */
    public V3 feet() {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        V3 fH = new V3(-sy, 0, cy);
        V3 fwd = fH.mul(cp).addScaled(V3.Y, sp);
        V3 up = fH.mul(-sp).addScaled(V3.Y, cp);
        return com.addScaled(up, bikeType.feetUp).addScaled(fwd, bikeType.feetFwd);
    }

    public V3 upAxis() {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        return new V3(-sy, 0, cy).mul(-sp).addScaled(V3.Y, cp);
    }

    public V3 forwardAxis() {
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        return new V3(-sy, 0, cy).mul(cp).addScaled(V3.Y, sp);
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
