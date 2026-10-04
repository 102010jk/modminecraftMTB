package com.descentmtb.physics;

/**
 * Every tunable of the bike simulation, SI units (m, kg, s, N, rad).
 * Defaults describe a 29" enduro bike (170/160 mm) with a 75 kg rider and are
 * tuned toward the Descenders feel spec in PLAN.md. Fields are mutable so a
 * config file can live-reload them.
 */
public final class BikeParams {
    // ---------------- world ----------------
    public double gravity = 9.81;
    public int substeps = 12;                 // per 50 ms game tick -> 240 Hz

    // ---------------- masses / geometry ----------------
    public double bikeMass = 15.5;
    public double riderMass = 60.0;
    /** Bike pitch / yaw / roll inertia about its own COM (kg m²). Yaw includes the rider's own body. */
    public double inertiaPitch = 2.6, inertiaYaw = 5.5, inertiaRoll = 1.2;
    public double wheelRadius = 0.375;
    public double halfWheelbase = 0.63;
    /** Axle height relative to the bike COM at full extension (negative = below). */
    public double axleDrop = -0.15;
    /** Rider COM neutral position relative to bike COM (along bike up / forward). */
    public double riderHeight = 0.62, riderForward = -0.09;

    // ---------------- suspension ----------------
    public double forkTravel = 0.17, shockTravel = 0.16;
    public double forkRate = 8400, shockRate = 11200;          // N/m at the wheel
    public double forkProgression = 1.0, shockProgression = 1.4; // extra rate at bottom-out (x times)
    public double forkCompDamp = 480, forkRebDamp = 700;        // N s/m
    public double shockCompDamp = 560, shockRebDamp = 820;

    // ---------------- tyres ----------------
    /** Kinetic / static friction ratio once a tyre slides (drift feel). */
    public double slideFriction = 0.93;
    /** Fraction of the lateral slip velocity removed per substep while gripping. */
    public double lateralStiffness = 0.65;
    public double tyreGrip = 1, tyreRolling = 1;
    /** Extra lateral (cornering) grip of the tyres over the surface friction: arcade-planted corners. */
    public double corneringGrip = 1.3;
    public boolean wallRides = true;
    public double wallRideMinSpeed = 8;       // m/s along the wall, not into it

    // ---------------- drivetrain / brakes ----------------
    public double pedalPower = 1050;          // short sprint; 20–30 km/h without a long run-up
    public double pedalMaxForce = 900;        // N at the contact patch
    public double pedalSpinOut = 7.2;         // m/s, normal pedalling settles near 30 km/h
    public double brakeForce = 950;           // N total at full LT
    public double brakeFrontShare = 0.6;
    public double gearRatio = 2.6;            // wheel revs per crank rev

    // ---------------- aero ----------------
    public double dragArea = 0.55;            // CdA m²
    public double airDensity = 1.2;
    /** Above this speed an extra "rough terrain" drag kicks in (Descenders-ish speed cap). */
    public double softSpeedCap = 19.0;        // ≈ 68 km/h
    public double softCapDrag = 4.0;          // N / (m/s)²

    // ---------------- steering ----------------
    /** Steering angle limit at walking pace (rad). */
    public double maxSteerAngle = 0.60;
    /** Full stick asks for this many g of cornering (× surface grip); >1 lets you slide. */
    public double steerGripDemand = 0.78;
    /** Rear brake + full lock multiplies the cornering demand by this (a controlled drift). */
    public double driftDemandBoost = 1.25;
    public double steerResponse = 0.09;       // s, stick → bar smoothing
    /** Visible lean = this × the physical lean angle atan(a/g); 1 = fully realistic. */
    public double leanFactor = 0.75;
    /** Visible lean limit on the ground (rad). */
    public double leanMax = 0.85;
    /** Lean forward + hard steering: rear tyre grip is multiplied by this (the rear steps out = drift). */
    public double driftRearGrip = 0.55;
    /** Right stick X on the ground: leaning the body into the turn tightens it by up to this fraction. */
    public double carveBoost = 0.22;
    /** ... and adds this much visible lean (rad) on top of the cornering lean. */
    public double bodyLeanVisual = 0.35;
    public double minSteerAngle = 0.03;

    // ---------------- rider (pop / pump / hop) ----------------
    public double riderCrouch = -0.26, riderStretch = 0.15;   // body targets (m)
    public double riderMin = -0.32, riderMax = 0.17;          // hard limits
    public double riderTuck = -0.16;                          // auto-tuck target in the air
    public double legStiffness = 9000, legDamping = 1300, legDriveDamping = 300;
    public double legPushMax = 2100, legPullMax = 1300;       // N beyond body weight
    public double riderLeanFwd = 0.15, riderLeanBack = -0.30; // fore/aft targets (m)
    public double riderForeAftMin = -0.36, riderForeAftMax = 0.26;
    public double armStiffness = 4500, armDamping = 900, armDriveDamping = 250, armMax = 900;
    /** Pitch-up torque helper while leaning back on the rear wheel (manual), N m. */
    /** Manual helper torque at full lean-back (N m); 0 = pure physics (config: manualAssist). */
    public double manualAssist = 650;
    public double manualTargetPitch = 0.52;   // comfortable ~30° balance point

    // ---------------- air ----------------
    public double flipRate = 6.4;             // rad/s at full stick and full pop (backflip ≈ 1.15 s)
    public double spinRate = 6.6;             // rad/s at full pop (360 ≈ 1.1 s)
    public double airControlResponse = 0.09;  // s
    /** Rotation authority with no pop at all (a lazy roll-off can only twitch). */
    public double airBudgetBase = 0.32;
    /** Rotation authority ramps in over this long after take-off. */
    public double airRampTime = 0.18;
    /** 0..1: how strongly the bike noses toward its flight path with no input (feel F8). */
    public double airAlignAssist = 0.85;
    public double airAlignRate = 3.5;         // 1/s
    /** With no spin input the yaw rate dies with this time constant (s) ... */
    public double airSpinDamping = 0.15;
    /** ... and the bike turns toward its flight direction at this rate (1/s), Descenders-style. */
    public double airYawAlignRate = 2.2;

    // ---------------- landing / bails ----------------
    public double bailPitchError = Math.toRadians(100);   // only landing on your back / nose
    public double bailYawError = Math.toRadians(115);     // only landing properly sideways
    public double bailImpactSpeed = 13.5;     // m/s into the ground (≈ 9 m drop to flat)
    /** Frame / bars / head must hit things at least this fast (m/s) to count as a crash. */
    public double crashSpeed = 4.5;
    public double wallCrashSpeed = 8.0;
    public double landingAssistAngle = Math.toRadians(42);
    public double landingAssistRate = 10.0;   // 1/s pitch snap toward the slope

    // ---------------- assists (0 = sim, 1 = full arcade help) ----------------
    public double balanceAssist = 1.0;        // reserved: roll is kinematic today

    public double totalMass() {
        return bikeMass + riderMass;
    }
}
