package com.descentmtb.entity;

import com.descentmtb.physics.BikeParams;
import com.descentmtb.trick.Trick;

/**
 * The bikes. Each has its own physics preset, rider geometry (where the feet and
 * hands go) and Descenders trick set.
 */
public enum BikeType {
    /** 29" enduro / DH, 170/160 mm: fast and planted. */
    ENDURO("enduro",
            new float[]{-0.15f, -0.19f},             // feet (pedal axis) from the frame COM: up, forward (m)
            new float[]{0.46f, 0.74f, 0.33f},        // grips from the feet: forward, up, half-width (m)
            new Trick[]{Trick.NO_HANDER, Trick.TABLETOP, Trick.NAC_NAC, Trick.CAN_CAN, Trick.SUPERMAN}),
    /** 26" dirt-jump hardtail, 100 mm fork: light, poppy, spins and whips. */
    HARDTAIL("hardtail",
            new float[]{-0.17f, -0.14f},
            new float[]{0.42f, 0.78f, 0.38f},
            new Trick[]{Trick.TUCK_NO_HANDER, Trick.TABLETOP, Trick.BARSPIN, Trick.TAILWHIP, Trick.SUPERMAN_SEATGRAB});

    public final String id;
    public final float feetUp, feetFwd;
    public final float gripFwd, gripUp, gripHalf;
    /** Tricks by stick direction: up, up+side, side, down+side, down. */
    private final Trick[] tricks;

    BikeType(String id, float[] feet, float[] grip, Trick[] tricks) {
        this.id = id;
        this.feetUp = feet[0];
        this.feetFwd = feet[1];
        this.gripFwd = grip[0];
        this.gripUp = grip[1];
        this.gripHalf = grip[2];
        this.tricks = tricks;
    }

    public static BikeType byId(int ordinal) {
        BikeType[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : ENDURO;
    }

    /** Trick for a right-stick direction (LB held), Descenders layout. */
    public Trick trickFor(float x, float y) {
        if (Math.abs(x) < 0.35f && Math.abs(y) < 0.35f) return Trick.NONE;
        boolean side = Math.abs(x) >= 0.35f;
        if (y > 0.35f) return side ? tricks[1] : tricks[0];
        if (y < -0.35f) return side ? tricks[3] : tricks[4];
        return tricks[2];
    }

    /** Physics preset (fresh copy). */
    public BikeParams params() {
        BikeParams p = new BikeParams();
        if (this == HARDTAIL) {
            p.bikeMass = 12.0;
            p.inertiaPitch = 1.9;
            p.inertiaYaw = 4.6;
            p.wheelRadius = 0.33;
            p.halfWheelbase = 0.53;
            p.axleDrop = -0.15;
            p.forkTravel = 0.10;
            p.forkRate = 10500;
            p.forkCompDamp = 520;
            p.forkRebDamp = 760;
            // rigid rear: only the tyre gives a little
            p.shockTravel = 0.025;
            p.shockRate = 42000;
            p.shockProgression = 3.0;
            p.shockCompDamp = 1500;
            p.shockRebDamp = 1900;
            p.maxSteerAngle = 0.70;
            p.gearRatio = 2.1;
            p.dragArea = 0.50;
            p.softSpeedCap = 15.5;     // twitchy at speed, not a downhill bike
            p.flipRate = 7.2;
            p.spinRate = 7.8;
            p.bailImpactSpeed = 11.5;  // no rear suspension to save you
            p.legPushMax = 2300;       // light bike = bigger pops
        }
        return p;
    }
}
