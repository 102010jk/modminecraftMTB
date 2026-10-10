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
            new Trick[]{Trick.NO_HANDER, Trick.TABLETOP, Trick.NAC_NAC, Trick.CAN_CAN, Trick.SUPERMAN}),
    /**
     * 125 cc four-stroke pit bike: ~68 kg, 17"/14" wheels, 160 mm fork and shock, a four-speed box. Small, light and
     * twitchy, ~70 km/h flat out. Same freestyle-MX trick set. Appended after the dirt bike: the type is saved by ordinal.
     */
    PIT_BIKE("pit_bike",
            new float[]{-0.21f, -0.04f},             // footpegs from the bike COM: up, forward (m)
            new float[]{0.32f, 0.59f, 0.33f},        // grips from the pegs: forward, up, half-width (660 mm bars)
            new Trick[]{Trick.NO_HANDER, Trick.TABLETOP, Trick.NAC_NAC, Trick.CAN_CAN, Trick.SUPERMAN}),
    /**
     * Alpine race skis (slalom / giant slalom): long, stiff, narrow-waisted, a sidecut made for carving at speed. The
     * two "wheels" of the simulation are the ski's front and rear contact points, the steering is the carve. The brand
     * and model (length, sidecut radius) come from {@link com.descentmtb.ski.SkiBrand}. Appended after the motorbikes:
     * the type is saved by ordinal. Old-school aerials as the trick set.
     */
    SKI_RACE("ski_race",
            new float[]{-0.48f, 0.0f},              // boot soles from the "frame" COM (shins): up, forward (m)
            new float[]{0.30f, 0.62f, 0.30f},       // pole grips from the boots: forward, up, half-width (m)
            new Trick[]{Trick.SPREAD_EAGLE, Trick.DAFFY, Trick.IRON_CROSS, Trick.BACK_SCRATCHER, Trick.TIP_GRAB},
            Trick.DAFFY),
    /**
     * Freestyle twin-tip skis (park and big air): softer, wider, turned-up tails so they ride switch (fakie) as well
     * as forwards, a mid-radius sidecut. Brand and model from {@link com.descentmtb.ski.SkiBrand}. Grab tricks.
     */
    SKI_FREESTYLE("ski_freestyle",
            new float[]{-0.48f, 0.0f},
            new float[]{0.28f, 0.60f, 0.30f},
            new Trick[]{Trick.MUTE_GRAB, Trick.JAPAN_GRAB, Trick.SAFETY_GRAB, Trick.TAIL_GRAB, Trick.TRUCK_DRIVER},
            Trick.IRON_CROSS);

    public final String id;
    public final float feetUp, feetFwd;
    public final float gripFwd, gripUp, gripHalf;
    /** Tricks by stick direction: up, up+side, side, down+side, down. */
    private final Trick[] tricks;
    /** The extra (sixth) slot: the Heelclicker on the bikes, a ski trick on the skis. */
    private final Trick heel;

    BikeType(String id, float[] feet, float[] grip, Trick[] tricks) {
        this(id, feet, grip, tricks, Trick.HEELCLICKER);
    }

    BikeType(String id, float[] feet, float[] grip, Trick[] tricks, Trick heel) {
        this.id = id;
        this.feetUp = feet[0];
        this.feetFwd = feet[1];
        this.gripFwd = grip[0];
        this.gripUp = grip[1];
        this.gripHalf = grip[2];
        this.tricks = tricks;
        this.heel = heel;
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
        return slot == HEEL_SLOT ? heel : Trick.NONE;
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
        if (y >= 0.999f && Math.abs(Math.abs(x) - HEEL_X) < 0.002f) return heel;
        if (Math.abs(x) < 0.35f && Math.abs(y) < 0.35f) return Trick.NONE;
        boolean side = Math.abs(x) >= 0.35f;
        if (y > 0.35f) return side ? tricks[1] : tricks[0];
        if (y < -0.35f) return side ? tricks[3] : tricks[4];
        return tricks[2];
    }

    /** A motorbike: engine instead of pedals, footpegs instead of cranks. */
    public boolean motor() {
        return this == DIRT_BIKE || this == PIT_BIKE;
    }

    /** Skis (race or freestyle): no wheels, pedals, tyres or suspension; they glide on snow and ice. */
    public boolean ski() {
        return this == SKI_RACE || this == SKI_FREESTYLE;
    }

    /**
     * The motorbikes' bail windows: a wider pitch / yaw landing error, a later loop-out and over-the-bars, a stronger
     * and longer landing assist, and no mid-trick bail on a soft landing. The impact and crash speeds are set per bike.
     */
    private static void forgiveLandings(BikeParams p) {
        p.bailPitchError = Math.toRadians(130);
        p.bailYawError = Math.toRadians(140);
        p.riskYawLimit = Math.toRadians(80);
        p.landingAssistAngle = Math.toRadians(70);
        p.landingAssistRate = 8.0;
        p.landingAssistTime = 0.30;
        p.loopOutAngle = Math.toRadians(100);
        p.loopOutTime = 0.40;
        p.overBarsAngle = Math.toRadians(85);
        p.midTrickBailImpact = 6.0;
    }

    /** Physics preset (fresh copy). */
    public BikeParams params() {
        BikeParams p = new BikeParams();
        if (ski()) {
            com.descentmtb.ski.SkiPhysics.defaults(p, this == SKI_FREESTYLE);
            return p;
        }
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
            // forgiving by design (a freestyle-MX bike lands what a bicycle never could): ~2x the bicycle's impact limit
            p.bailImpactSpeed = 26.0;       // 300 mm of travel swallows big flat landings
            p.crashSpeed = 8.5;
            p.wallCrashSpeed = 13.0;
            forgiveLandings(p);
            p.manualAssist = 5200;          // holds a power wheelie on the balance point (heavy: scaled with inertia)
            p.manualTargetPitch = 0.6;
            p.tyreGrip = 1.05;              // knobbies bite into dirt
        }
        if (this == PIT_BIKE) {
            p.motor = true;
            p.bikeMass = 68.0;
            p.riderMass = 78.0;
            p.inertiaPitch = 11.0;          // nimbler: a short, light bike turns about its axes easily
            p.inertiaYaw = 17.0;
            p.inertiaRoll = 5.0;
            p.wheelRadius = 0.28;           // 70/100-17 front, 90/100-14 rear
            p.halfWheelbase = 0.56;         // 1.12 m wheelbase
            p.axleDrop = -0.20;             // COM ~0.48 m up at full extension
            p.riderHeight = 0.50;
            p.riderForward = -0.04;
            p.barHalfWidth = 0.33;
            p.forkTravel = 0.16;
            p.shockTravel = 0.15;
            p.forkRate = 14500;             // ~30% sag under bike + rider
            p.shockRate = 16000;
            p.forkProgression = 1.4;
            p.shockProgression = 2.0;
            p.forkCompDamp = 890;
            p.forkRebDamp = 1250;
            p.shockCompDamp = 950;
            p.shockRebDamp = 1450;
            p.brakeForce = 2000;
            p.brakeFrontShare = 0.65;
            p.dragArea = 0.55;
            p.softSpeedCap = 23.0;          // above the ~20 m/s the gearing allows
            p.maxSteerAngle = 0.62;
            p.legPushMax = 1900;
            p.legPullMax = 1300;
            p.flipRate = 5.6;
            p.spinRate = 5.5;
            p.bailImpactSpeed = 22.0;       // ~1.6x the bicycle's limit
            p.crashSpeed = 7.5;
            p.wallCrashSpeed = 12.0;
            forgiveLandings(p);
            p.manualAssist = 2400;          // scaled with the inertia of the dirt bike
            p.manualTargetPitch = 0.6;
            p.tyreGrip = 1.0;
            // 125 cc single: ~10 N m at 7000 rpm, revs to 9800, four gears, ~70-75 km/h flat out
            p.peakTorque = 10;
            p.peakTorqueRpm = 7000;
            p.idleRpm = 1700;
            p.launchRpm = 3600;
            p.shiftUpRpm = 9300;
            p.shiftDownRpm = 3700;
            p.limitRpm = 9800;
            p.backfireRpm = 6500;
            p.primaryRatio = 3.7;
            p.finalRatio = 3.6;
            p.gearRatios = new double[]{2.6, 1.8, 1.35, 1.05};
            p.shiftTime = 0.10;
            p.engineBrakeTorque = 1.8;
            p.airThrottlePitch = 120;
            p.airBrakePitch = 160;
            p.rearSprocket = 37;
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
