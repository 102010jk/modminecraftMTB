package com.descentmtb.ski;

import com.descentmtb.physics.BikeParams;

/**
 * Ski presets for {@link com.descentmtb.physics.BikeSim}. The simulation's two "wheels" are the front and rear contact
 * points of the skis, the "frame" is the boots and shins, the rider body sits on top through the legs (the knees are
 * the suspension).
 *
 * <p>What the sim does with them when {@link BikeParams#ski} is set:
 * <ul>
 *   <li>Contacts glide with the {@link SkiSurface} glide μ and hold sideways with the edge coefficient; bare ground
 *       grinds (strong braking, scrape meter, a bail when it is full).</li>
 *   <li>Steering is the carve: the tightest carve at speed v follows the sidecut, R = R<sub>sidecut</sub>·cos(edge)
 *       with sin(edge) = v²/(g·R<sub>sidecut</sub>), up to {@link BikeParams#skiMaxEdge}; above that only the lateral
 *       g limits it. Slower, a skidded (pivot) turn is allowed and scrubs speed. Both ski ends steer, half the angle
 *       each, so the skis pivot under the boots.</li>
 *   <li>Brake = hockey stop / snowplough, pedal = pole push (low speed only) or a walk over bare ground, crouch +
 *       lean forward = tuck (less drag).</li>
 * </ul>
 */
public final class SkiPhysics {
    /** Boots (pair) plus the shins and feet carried by the "frame" (kg). */
    private static final double BOOTS_KG = 3.8, SHINS_KG = 5.8;
    /** Total skier mass the presets are made for (kg). */
    private static final double SKIER_KG = 85;

    /** Base parameters of a ski type (fills a fresh {@link BikeParams}), with the type's default pair applied. */
    public static void defaults(BikeParams p, boolean freestyle) {
        p.ski = true;
        p.motor = false;
        p.wallRides = false;
        p.manualAssist = 0;
        p.wheelRadius = 0.04;           // contact "radius": ski + plate thickness
        p.axleDrop = -0.48;             // contact points 0.52 m under the frame COM (MountainBikeEntity.COM_HEIGHT)
        p.halfWheelbase = 0.75;
        p.riderHeight = 0.42;           // skier COM ~0.94 m above the snow standing, ~0.64 m in a tuck
        p.riderForward = 0.0;
        p.barHalfWidth = 0.35;          // hands / pole grips
        p.inertiaRoll = 1.0;

        // ski flex, boot flex and the snow packing under the base: a few centimetres (stiff at the end of the stroke);
        // the knees do the real absorbing. The sag leaves the skis room to follow a crest instead of skipping off it.
        p.forkTravel = p.shockTravel = 0.10;
        p.forkRate = p.shockRate = 13000;
        p.forkProgression = p.shockProgression = 3.0;
        p.forkCompDamp = p.shockCompDamp = 450;
        p.forkRebDamp = p.shockRebDamp = 550;

        // tyre fields that still matter: kinetic / static ratio of a skidding edge, constraint stiffness
        p.slideFriction = 0.85;
        p.lateralStiffness = 0.65;
        p.tyreGrip = p.tyreRolling = 1;
        p.corneringGrip = 1;
        p.brakeForce = 0;               // braking is the hockey stop μ (SkiSurface.brake)

        // steering (the carve limit is computed from the sidecut; these are the hard bounds)
        p.maxSteerAngle = 1.25;
        p.minSteerAngle = 0;
        p.leanFactor = 0.9;             // visual edge angle
        p.bodyLeanVisual = 0.2;
        p.skiSkidAccel = 1.1;
        p.skiPivotRadius = 1.2;
        p.skiSkidScrub = 0.35;
        p.skiStepTurnRate = 1.8;

        // pole push / walking (independent of the bike's pedal power config)
        p.skiPoleForce = 230;
        p.skiPushFull = 3.0;
        p.skiPushZero = 6.5;
        p.skiWalkSpeed = 1.3;
        p.skiWalkGlide = 0.08;

        // rider: a skier's stance. Crouch deep into the tuck, little room to lean back ("back seat")
        p.riderCrouch = -0.30;
        p.riderStretch = 0.12;
        p.riderMin = -0.38;
        p.riderMax = 0.14;
        p.riderTuck = -0.20;
        p.legStiffness = 9500;
        p.legDamping = 1300;
        p.legDriveDamping = 300;
        p.legPushMax = 2300;
        p.legPullMax = 1300;
        p.riderLeanFwd = 0.14;
        p.riderLeanBack = -0.18;
        p.riderForeAftMin = -0.25;
        p.riderForeAftMax = 0.22;

        // aero: upright stance vs tuck; the soft cap is the "rough terrain" ceiling on top of that
        p.dragArea = 0.50;
        p.softCapDrag = 8.0;

        // scrape on bare ground
        p.skiScrapeTime = 0.85;
        p.skiScrapeDrain = 1.2;
        p.skiRockImpactFactor = 0.55;
        p.skiBrakeGlide = 0.25;
        p.crashSpeed = 4.5;
        p.wallCrashSpeed = 7.0;         // a tree at 25 km/h on skis is a crash

        if (freestyle) {
            p.skiMaxEdge = Math.toRadians(60);
            p.skiGlide = 1.0;
            p.skiEdgeGrip = 0.95;
            p.skiTuckDragArea = 0.24;
            p.softSpeedCap = 23.0;      // ~83 km/h: park skis, upright, not a speed event
            p.leanMax = 1.0;
            p.flipRate = 6.6;
            p.spinRate = 8.2;           // a 360 in ~0.9 s off a good pop
            p.airBudgetBase = 0.40;
            p.airSpinWindup = 0.18;
            p.bailImpactSpeed = 13.5;
            p.skiSwitch = true;         // twin tips land and ride switch
            tune(p, SkiBrand.ARMADA_ARV_96);
        } else {
            p.skiMaxEdge = Math.toRadians(66);   // cos 66° = 0.4: the tightest carve is 0.4 x the sidecut radius
            p.skiGlide = 0.8;           // race wax and a stone-ground base
            p.skiEdgeGrip = 1.1;
            p.skiTuckDragArea = 0.17;
            p.softSpeedCap = 34.0;      // ~122 km/h
            p.leanMax = 1.1;
            p.flipRate = 4.4;
            p.spinRate = 5.2;
            p.airBudgetBase = 0.30;
            p.bailImpactSpeed = 12.5;
            p.skiSwitch = false;        // race tails dig in when you land backwards
            tune(p, SkiBrand.ATOMIC_REDSTER_G9);
        }
    }

    /**
     * Applies one pair's length, sidecut and weight on top of its type's {@link #defaults}. Absolute values only, so
     * applying it twice (or a different brand afterwards) is safe.
     */
    public static void tune(BikeParams p, SkiBrand brand) {
        if (brand == null) return;
        double length = brand.lengthCm / 100.0;
        // contact points: the running surface is ~84% of the length (tip and tail rise)
        p.halfWheelbase = 0.42 * length;
        p.skiSidecut = brand.radiusM;
        p.bikeMass = brand.pairMassKg + BOOTS_KG + SHINS_KG;
        p.riderMass = SKIER_KG - BOOTS_KG - SHINS_KG;
        // a long, heavy pair is slow to swing round and steady in pitch; a light short one pivots and spins easily
        double ski = brand.pairMassKg * length * length / 12;
        p.inertiaYaw = 2.2 + ski;
        p.inertiaPitch = 1.6 + ski;
        // a long-radius GS ski is slow from edge to edge, a slalom ski snaps in
        p.steerResponse = 0.03 + 0.0026 * brand.radiusM;
        // wide twin tips: a touch less edge hold on hard snow than a narrow race waist
        if (brand.twinTip()) p.skiEdgeGrip = 0.95 - 0.004 * (brand.waistMm - 96);
        // lighter skis spin faster in the air
        if (brand.twinTip()) p.spinRate = 8.2 * Math.sqrt(4.6 / brand.pairMassKg);
    }

    private SkiPhysics() {}
}
