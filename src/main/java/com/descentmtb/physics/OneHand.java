package com.descentmtb.physics;

/**
 * Ringing the bell takes the left hand off the grip for a moment. While it is off, the bars are only held by one
 * hand: a hard landing or a sudden bump makes the rider lose control. Pure rules, used by {@link BikeSim}.
 */
public final class OneHand {
    /** How long (s) the hand stays off the grip. */
    public static final double TIME = 0.45;
    /** Minimum time (s) between two rings (matches the server's bell cooldown of 10 ticks). */
    public static final double COOLDOWN = 0.5;
    /** A landing from the air above this share of the normal bail impact speed throws a one-handed rider. */
    public static final double LANDING_FRACTION = 0.45;
    /** A wheel pushed into the bike this fast (share of the crash speed) at a step or obstacle throws a one-handed rider. */
    public static final double BUMP_FRACTION = 0.65;
    /** A bottomed-out suspension (m past its travel) throws a one-handed rider as well. */
    public static final double BOTTOM_OUT = 0.03;
    /** The air time (s) below which a landing is only a hop. */
    public static final double MIN_AIR_TIME = 0.25;

    /** Landing from the air with one hand on the bars. */
    public static boolean landingBails(double airTime, double impactSpeed, double bailImpactSpeed) {
        return airTime > MIN_AIR_TIME && impactSpeed > LANDING_FRACTION * bailImpactSpeed;
    }

    /** A wheel hitting a step or obstacle on the ground: closing speed along the surface normal, and bottom-out depth. */
    public static boolean bumpBails(double closingSpeed, double overshoot, double crashSpeed) {
        return closingSpeed > BUMP_FRACTION * crashSpeed || overshoot > BOTTOM_OUT;
    }

    /** 0..1 how far the hand is off the grip {@code elapsed} seconds after the press: reaches out fast, comes back fast. */
    public static double reach(double elapsed) {
        if (elapsed <= 0 || elapsed >= TIME) return 0;
        double out = smooth(elapsed / 0.10);
        double back = smooth((TIME - elapsed) / 0.14);
        return Math.min(out, back);
    }

    /** 0..1 how much the duck is squeezed {@code elapsed} seconds after the press (a short pulse as the hand presses it). */
    public static double squeeze(double elapsed) {
        if (elapsed < 0.08 || elapsed > 0.26) return 0;
        double t = (elapsed - 0.08) / 0.18;
        return Math.sin(Math.PI * t);
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private OneHand() {}
}
