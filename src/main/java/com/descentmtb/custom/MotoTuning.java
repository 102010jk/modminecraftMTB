package com.descentmtb.custom;

import com.descentmtb.physics.BikeParams;

/**
 * What the workshop's mechanical choices ({@link MotoBuild}) do to a motorbike's physics parameters. Pure maths, no
 * Minecraft classes.
 */
public final class MotoTuning {
    /**
     * Applies the build to {@code p}, a bike's live parameters, taking the stock values from {@code base} (the
     * bike type's own preset). Call it right after {@link com.descentmtb.physics.BikeTuning#apply}, on every
     * place that does: that call resets the fork values from {@code base} (with the fork pressure), so scaling
     * them here never compounds; the shock and the engine are set from {@code base} outright.
     *
     * <ul>
     *   <li>Rear sprocket {@code +d} teeth: the final reduction grows with (stock + d) / stock - quicker
     *       acceleration, a lower top speed (the limiter hits earlier); negative is the opposite.</li>
     *   <li>Race exhaust: {@link MotoBuild.Exhaust#torque} on the torque curve.</li>
     *   <li>Suspension: spring rates times {@link MotoBuild.Suspension#spring}, compression and rebound damping
     *       by its square root (a stiffer spring needs proportionally more damping to stay critically damped).</li>
     * </ul>
     */
    public static void apply(BikeParams p, BikeParams base, MotoBuild build) {
        int stock = Math.max(1, base.rearSprocket);
        p.finalRatio = base.finalRatio * (stock + build.sprocket()) / stock;
        p.peakTorque = base.peakTorque * build.exhaust().torque;
        double k = build.suspension().spring, damp = Math.sqrt(k);
        p.forkRate *= k;
        p.forkCompDamp *= damp;
        p.forkRebDamp *= damp;
        p.shockRate = base.shockRate * k;
        p.shockCompDamp = base.shockCompDamp * damp;
        p.shockRebDamp = base.shockRebDamp * damp;
    }

    /**
     * Speed (km/h) of the top gear on the rev limiter, drag and rolling resistance ignored: what the sprocket
     * choice moves; the workshop shows it as a hint. Real top speed is a few per cent lower.
     */
    public static double gearedTopSpeedKmh(BikeParams p) {
        double ratio = p.primaryRatio * p.gearRatios[p.gearRatios.length - 1] * p.finalRatio;
        return p.limitRpm / ratio * 2 * Math.PI * p.wheelRadius / 60 * 3.6;
    }

    private MotoTuning() {}
}
