package com.descentmtb.physics;

/**
 * Every tunable of the bike simulation, SI units (m, kg, s, N, rad).
 * Defaults describe a 29" enduro bike (170/160 mm) with a 75 kg rider and are
 * tuned toward the Descenders feel spec in docs/archive/PLAN.md. Fields are mutable so a
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
    /**
     * Water: how strongly it brakes the bike per unit of submersion (1/s, plus a part growing with speed), and how
     * much of gravity it carries when fully under (bike + rider float a little below neutral).
     */
    public double waterDrag = 0.45, waterDragPerSpeed = 0.28, waterBuoyancy = 0.85;
    // ---------------- engine (dirt bike only) ----------------
    /** True for a motorbike: the throttle drives the rear wheel through {@link Engine} instead of pedalling. */
    public boolean motor = false;
    /** Crank torque peak (N m) and where it peaks (rpm); idle, launch (extra rpm the auto clutch holds at full
     *  throttle), shift points, limiter and the revs above which snapping the throttle shut pops the exhaust. */
    public double peakTorque = 24, peakTorqueRpm = 8500, idleRpm = 1500, launchRpm = 4200,
            shiftUpRpm = 10900, shiftDownRpm = 4300, limitRpm = 11500, backfireRpm = 7500;
    /** Primary drive, gearbox and final drive (13/51) reductions of a 250 F. */
    public double primaryRatio = 3.3, finalRatio = 3.92;
    public double[] gearRatios = {2.14, 1.73, 1.44, 1.21, 1.05};
    /** Teeth of the stock rear sprocket: the workshop's sprocket choice scales {@link #finalRatio} against it. */
    public int rearSprocket = 51;
    /** Seconds without drive during an upshift; engine braking torque at the limiter (N m). */
    public double shiftTime = 0.12, engineBrakeTorque = 4;
    /** Pitch torque (N m) the spinning rear wheel and the rider give in the air: gas lifts the nose, brake drops it. */
    public double airThrottlePitch = 260, airBrakePitch = 340;

    /** Half the handlebar width: the bar ends are collision probes (trees, door frames). */
    public double barHalfWidth = 0.33;
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
    public double dragArea = 0.45;            // CdA m² (attack position)
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
    public double steerResponse = 0.06;       // s, stick → bar smoothing
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
    public double airAlignRate = 2.6;         // 1/s
    /** With no spin input the yaw rate dies with this time constant (s) ... */
    public double airSpinDamping = 0.15;
    /** ... and the bike turns toward its flight direction at this rate (1/s), Descenders-style. */
    public double airYawAlignRate = 1.4;
    /** Spin input below this stick deflection does nothing in the air (a brushed stick must not turn the bike). */
    public double airSpinDeadzone = 0.35;
    /** Multiplier of the spin rate (config "airRotationSensitivity"). */
    public double airSpinSensitivity = 1.0;
    /** Seconds of held spin input until the full spin rate is reached (a deliberate hold, not a twitch). */
    public double airSpinWindup = 0.25;
    /** Risk & reward: landing mid-trick or clearly sideways is a crash (config "experimentalRiskReward"). */
    public boolean riskReward = true;
    /** With risk & reward, landing with more yaw error than this (rad) is a crash. */
    public double riskYawLimit = Math.toRadians(55);

    // ---------------- landing / bails ----------------
    public double bailPitchError = Math.toRadians(100);   // only landing on your back / nose
    public double bailYawError = Math.toRadians(115);     // only landing properly sideways
    public double bailImpactSpeed = 13.5;     // m/s into the ground (≈ 9 m drop to flat)
    /** Frame / bars / head must hit things at least this fast (m/s) to count as a crash. */
    public double crashSpeed = 4.5;
    public double wallCrashSpeed = 8.0;
    public double landingAssistAngle = Math.toRadians(42);
    public double landingAssistRate = 5.5;   // 1/s pitch snap toward the slope
    /** Seconds the landing assist keeps snapping the bike onto the slope after touchdown. */
    public double landingAssistTime = 0.18;
    /** On the ground: more than this far (rad) from the slope pitch for {@link #loopOutTime} is a loop-out / flip-over. */
    public double loopOutAngle = Math.toRadians(80);
    public double loopOutTime = 0.25;
    /** Saddle / bars touching the ground this hard count as "over the bars" only beyond this angle (rad) off the slope. */
    public double overBarsAngle = Math.toRadians(65);
    /** Landing mid-trick (risk &amp; reward) only bails above this speed into the ground (m/s); 0 = any landing. */
    public double midTrickBailImpact = 0;

    // ---------------- assists (0 = sim, 1 = full arcade help) ----------------
    public double balanceAssist = 1.0;        // reserved: roll is kinematic today

    public double totalMass() {
        return bikeMass + riderMass;
    }
}
