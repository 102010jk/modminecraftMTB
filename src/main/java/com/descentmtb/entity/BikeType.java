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
            new float[]{0.4618f, 0.7638f, 0.3312f},  // grips from the feet: forward, up, half-width (m)
            new Trick[]{Trick.NO_HANDER, Trick.TABLETOP, Trick.NAC_NAC, Trick.CAN_CAN, Trick.SUPERMAN}),
    /** 26" dirt-jump hardtail, 100 mm fork: light, poppy, spins and whips. */
    HARDTAIL("hardtail",
            new float[]{-0.17f, -0.14f},
            new float[]{0.4141f, 0.6897f, 0.3144f},
            new Trick[]{Trick.TUCK_NO_HANDER, Trick.TABLETOP, Trick.BARSPIN, Trick.TAILWHIP, Trick.SUPERMAN_SEATGRAB}),
    /**
     * 250 cc four-stroke motocross bike: ~105 kg, 21"/18" wheels, 300 mm upside-down fork and a progressive
     * linkage monoshock, toothed steel footpegs instead of pedals, the throttle drives the rear wheel.
     * Freestyle-MX trick set. Always appended last: the type is saved by ordinal.
     */
    DIRT_BIKE("dirt_bike",
            new float[]{-0.25f, -0.06f},             // footpegs from the bike COM: up, forward (m)
            new float[]{0.56f, 0.70f, 0.40f},        // grips from the pegs: forward, up, half-width (800 mm bars)
            new Trick[]{Trick.NO_HANDER, Trick.TABLETOP, Trick.NAC_NAC, Trick.CAN_CAN, Trick.SUPERMAN});

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

    /** Trick slots: the five stick directions plus the Heelclicker (extra slot, same on every bike). */
    public static final int TRICK_SLOTS = 6;
    public static final int HEEL_SLOT = 5;
    /**
     * The Heelclicker is not a stick direction. It is requested with {@code y == 1, |x| == HEEL_X}, a point outside the
     * unit circle that no radial stick reaches and the arrow keys never produce (so it never collides with a direction).
     */
    public static final float HEEL_X = 0.25f;

    /** Trick in a slot: 0 up, 1 up+side, 2 side, 3 down+side, 4 down, 5 Heelclicker. */
    public Trick trickAt(int slot) {
        if (slot >= 0 && slot < tricks.length) return tricks[slot];
        return slot == HEEL_SLOT ? Trick.HEELCLICKER : Trick.NONE;
    }

    /**
     * The (x, y) {@link #trickFor} maps back to the trick of a slot; the sign of x is the side (whip direction).
     * Used by the independent trick keys, which pick the trick directly instead of through a stick.
     */
    public static float[] stickForSlot(int slot, int side) {
        float s = side < 0 ? -1 : 1;
        return switch (slot) {
            case 0 -> new float[]{0.1f * s, 1};
            case 1 -> new float[]{s, 1};
            case 2 -> new float[]{s, 0};
            case 3 -> new float[]{s, -1};
            case 4 -> new float[]{0.1f * s, -1};
            case HEEL_SLOT -> new float[]{HEEL_X * s, 1};
            default -> new float[]{0, 0};
        };
    }

    /** Trick for a right-stick direction (LB held), Descenders layout. */
    public Trick trickFor(float x, float y) {
        if (y >= 0.999f && Math.abs(Math.abs(x) - HEEL_X) < 0.002f) return Trick.HEELCLICKER;
        if (Math.abs(x) < 0.35f && Math.abs(y) < 0.35f) return Trick.NONE;
        boolean side = Math.abs(x) >= 0.35f;
        if (y > 0.35f) return side ? tricks[1] : tricks[0];
        if (y < -0.35f) return side ? tricks[3] : tricks[4];
        return tricks[2];
    }

    /** A motorbike: engine instead of pedals, footpegs instead of cranks. */
    public boolean motor() {
        return this == DIRT_BIKE;
    }

    /** Physics preset (fresh copy). */
    public BikeParams params() {
        BikeParams p = new BikeParams();
        if (this == DIRT_BIKE) {
            p.motor = true;
            p.bikeMass = 105.0;
            p.riderMass = 78.0;             // rider in boots, armour and helmet
            p.inertiaPitch = 24.0;
            p.inertiaYaw = 34.0;
            p.inertiaRoll = 9.0;
            p.wheelRadius = 0.355;          // 80/100-21 front, 110/90-19 rear
            p.halfWheelbase = 0.74;         // 1.48 m wheelbase
            p.axleDrop = -0.25;             // COM ~0.6 m up at full extension
            p.riderHeight = 0.55;
            p.riderForward = -0.05;
            p.barHalfWidth = 0.40;
            p.forkTravel = 0.30;            // 300 mm USD fork
            p.shockTravel = 0.31;           // monoshock, rear wheel travel
            p.forkRate = 8800;
            p.shockRate = 9800;
            p.forkProgression = 1.5;
            p.shockProgression = 2.2;        // progressive linkage
            p.forkCompDamp = 780;
            p.forkRebDamp = 1100;
            p.shockCompDamp = 820;
            p.shockRebDamp = 1250;
            p.brakeForce = 2600;
            p.brakeFrontShare = 0.65;
            p.dragArea = 0.62;
            p.softSpeedCap = 31.0;          // ~110 km/h, fifth gear on the limiter
            p.maxSteerAngle = 0.55;
            p.legPushMax = 1600;            // you do not bunny-hop 105 kg
            p.legPullMax = 1100;
            p.flipRate = 4.6;
            p.spinRate = 4.4;
            p.bailImpactSpeed = 17.0;       // 300 mm of travel swallows big flat landings
            p.crashSpeed = 5.0;
            p.wallCrashSpeed = 9.0;
            p.manualAssist = 5200;          // holds a power wheelie on the balance point (heavy: scaled with inertia)
            p.manualTargetPitch = 0.6;
            p.tyreGrip = 1.05;              // knobbies bite into dirt
        }
        if (this == HARDTAIL) {
            p.bikeMass = 13.0;
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
