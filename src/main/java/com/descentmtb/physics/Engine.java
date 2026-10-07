package com.descentmtb.physics;

/**
 * A 250 cc four-stroke single with a five-speed box and an automatic clutch, as the dirt bike's rear wheel feels it.
 * The throttle does not ask for a speed: it opens the engine, whose torque (through the gear and chain) pushes on the
 * rear tyre. Revs follow the rear wheel while the clutch is in, the clutch slips at launch, gears change by
 * themselves, the limiter cuts sparks at the top, and closing the throttle brakes with the engine. Pure (no
 * Minecraft classes), unit-tested.
 */
public final class Engine {
    /** Engine speed (rpm) and the selected gear (0 = first). */
    public double rpm;
    public int gear;
    /** The throttle as the engine saw it last step (0..1, smoothed slightly like a real cable and carburettor). */
    public double throttle;
    /** True while the rev limiter is cutting sparks. */
    public boolean limiting;
    /** Set when snapping the throttle shut at high revs makes the exhaust pop; the sound takes (and clears) it. */
    public boolean backfire;
    /** Seconds without drive while a gear change goes through. */
    private double shiftTimer;
    /** Throttle a moment ago, to catch a snap closed. */
    private double recentThrottle;

    public Engine(BikeParams p) {
        reset(p);
    }

    public void reset(BikeParams p) {
        rpm = p.idleRpm;
        gear = 0;
        throttle = 0;
        limiting = backfire = false;
        shiftTimer = recentThrottle = 0;
    }

    /** Overall reduction (engine turns per rear-wheel turn) in gear {@code g}. */
    public static double ratio(BikeParams p, int g) {
        return p.primaryRatio * p.gearRatios[Math.max(0, Math.min(p.gearRatios.length - 1, g))] * p.finalRatio;
    }

    /** Crankshaft torque (N m) at full throttle: a broad four-stroke curve peaking at {@link BikeParams#peakTorqueRpm}. */
    public static double torque(BikeParams p, double rpm) {
        double x = (rpm - p.peakTorqueRpm) / p.peakTorqueRpm;
        double shape = 1 - 0.55 * x * x;
        if (rpm < p.idleRpm) shape *= Math.max(0, rpm / p.idleRpm);
        return p.peakTorque * Math.max(0.25, Math.min(1, shape));
    }

    /**
     * One step. {@code wheelOmega} is the rear wheel's angular speed (rad/s, + = forward), {@code grounded} whether the
     * rear tyre touches the ground. Returns the drive force at the rear contact patch (N, + = forward, negative =
     * engine braking) before the tyre's grip is taken into account.
     */
    public double step(BikeParams p, double throttleIn, double wheelOmega, boolean grounded, double dt) {
        double want = Math.max(0, Math.min(1, throttleIn));
        throttle += (want - throttle) * (1 - Math.exp(-dt / 0.04));
        // a throttle snapped shut from high revs pops in the pipe
        if (recentThrottle > 0.7 && throttle < 0.15 && rpm > p.backfireRpm) backfire = true;
        recentThrottle = Math.max(throttle, recentThrottle - dt / 0.25);

        double ratio = ratio(p, gear);
        double wheelRpm = Math.max(0, wheelOmega) * ratio * 60 / (2 * Math.PI);
        double clutchRpm = p.idleRpm + throttle * p.launchRpm;
        boolean clutchIn = grounded && wheelRpm >= clutchRpm * 0.95;
        double target = !grounded ? p.idleRpm + throttle * (p.limitRpm + 300 - p.idleRpm)   // revving freely in the air
                : clutchIn ? wheelRpm : Math.max(clutchRpm, wheelRpm);
        rpm += (target - rpm) * (1 - Math.exp(-dt / (clutchIn ? 0.03 : 0.12)));
        rpm = Math.max(p.idleRpm * 0.8, rpm);

        // automatic box: up near the limiter, down when the revs sag (never while airborne or mid-change)
        if (shiftTimer > 0) shiftTimer -= dt;
        else if (grounded && clutchIn && rpm > p.shiftUpRpm && gear < p.gearRatios.length - 1 && throttle > 0.2) {
            gear++;
            shiftTimer = p.shiftTime;
            rpm *= ratio(p, gear) / ratio;
        } else if (grounded && gear > 0 && rpm < p.shiftDownRpm) {
            gear--;
            shiftTimer = p.shiftTime * 0.5;
            rpm = Math.min(p.limitRpm, rpm * ratio(p, gear) / ratio);
        }
        ratio = ratio(p, gear);

        limiting = rpm >= p.limitRpm && throttle > 0.3;
        if (!grounded || shiftTimer > 0) return 0;
        if (limiting) return 0;                               // sparks cut: no push this step
        double wheelForce;
        if (throttle > 0.05) {
            wheelForce = throttle * torque(p, rpm) * ratio / p.wheelRadius;
        } else if (clutchIn && rpm > p.idleRpm * 1.5) {
            wheelForce = -p.engineBrakeTorque * (rpm / p.limitRpm) * ratio / p.wheelRadius;
        } else {
            wheelForce = 0;
        }
        return wheelForce;
    }

    /** Takes the backfire flag (true once per pop). */
    public boolean takeBackfire() {
        boolean b = backfire;
        backfire = false;
        return b;
    }

    /**
     * Engine state packed into one synced float for players watching someone else ride: rpm in thousands, positive
     * with the throttle open, negative with it shut. {@link #decodeRpm} / {@link #decodeThrottle} unpack it.
     */
    public float encode() {
        double k = Math.max(0.5, rpm / 1000);
        return (float) (throttle > 0.3 ? k : -k);
    }

    public static double decodeRpm(double code) {
        return Math.abs(code) * 1000;
    }

    public static boolean decodeThrottle(double code) {
        return code > 0;
    }
}
