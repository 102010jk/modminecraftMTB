package com.descentmtb.client.sound;

import com.descentmtb.custom.BikeParts.HubType;
import com.descentmtb.physics.Terrain.Surface;

/**
 * The rules that turn a bike's motion into sound levels and pitches. Pure functions - everything here is unit
 * tested ({@code BikeSoundMathTest}); the Minecraft side only feeds numbers in and plays what comes out.
 */
public final class BikeSoundMath {
    /** Reference speed (m/s) at which a hub's buzz loop plays at pitch 1: 25 km/h. */
    public static final double REF_SPEED = 25 / 3.6;
    /** Below this click rate (per second) a freehub is played as single clicks, above it as the buzz loop. */
    public static final double SINGLE_CLICK_BELOW = 30;
    /** Between these rates the single clicks hand over to the buzz loop (loop volume ramps 0 -> 1). */
    public static final double BUZZ_FADE_FROM = 24, BUZZ_FADE_TO = 42;
    /** Minimum rear wheel speed (rad/s) at which the freehub makes any noise. */
    public static final double MIN_OMEGA = 1.2;

    // ------------------------------------------------------------------ hub

    /** Clicks per second: the points of engagement times the revolutions per second of the rear wheel. */
    public static double clickRate(HubType hub, double rearOmega) {
        return hub.poe * Math.abs(rearOmega) / (2 * Math.PI);
    }

    /** Whether the hub is audible at all: coasting (not pedalling), turning, and not the silent sprag clutch. */
    public static boolean freewheelAudible(HubType hub, double rearOmega, boolean pedalling, boolean bailed) {
        return !hub.silent() && !pedalling && !bailed && Math.abs(rearOmega) >= MIN_OMEGA;
    }

    /**
     * Playback pitch of the hub's buzz loop for a click rate. The rate spans a factor of ~40 between a walk and a
     * fast descent but a sound can only be pitched over two octaves, so the pitch follows the rate compressed
     * (exponent 0.55) around the rate at 25 km/h, which is what the loop sample was baked at.
     */
    public static double buzzPitch(HubType hub, double rate) {
        if (hub.silent() || hub.loopHz <= 0 || rate <= 0) return 1;
        return clamp(Math.pow(rate / hub.loopHz, 0.55), 0.5, 2.0);
    }

    /** 0 = only single clicks, 1 = only the buzz loop. */
    public static double buzzBlend(double rate) {
        return smooth((rate - BUZZ_FADE_FROM) / (BUZZ_FADE_TO - BUZZ_FADE_FROM));
    }

    public static boolean singleClicks(double rate) {
        return rate > 0.5 && rate < SINGLE_CLICK_BELOW;
    }

    /** Loudness of the freewheel at a speed; the hubs differ in how much they carry. */
    public static double freewheelVolume(HubType hub, double speed) {
        double kmh = speed * 3.6;
        return hubGain(hub) * (0.42 + 0.28 * clamp(kmh / 40, 0, 1));
    }

    public static double hubGain(HubType hub) {
        return switch (hub) {
            case INDUSTRY_NINE_HYDRA -> 0.75;
            case DT_SWISS_RATCHET -> 0.85;
            case CHRIS_KING -> 1.0;
            case HOPE_PRO -> 1.1;
            case ONYX_VESPER -> 0;
        };
    }

    // ------------------------------------------------------------------ wind

    /** Wind in the rider's ears: gentle up to ~30 km/h, strong above ~35 km/h, and a rush in the air. */
    public static double windVolume(double speed, boolean airborne) {
        double kmh = speed * 3.6;
        double base = Math.pow(clamp((kmh - 6) / 70, 0, 1), 1.3) * 0.55;
        double strong = clamp((kmh - 35) / 30, 0, 1) * 0.35;
        double air = airborne ? (0.10 + 0.20 * clamp(kmh / 40, 0, 1)) * clamp(speed / 3, 0, 1) : 0;
        return clamp(base + strong + air, 0, 1);
    }

    public static double windPitch(double speed) {
        return 0.7 + 0.55 * clamp(speed * 3.6 / 90, 0, 1);
    }

    // ------------------------------------------------------------------ tyres

    /** The three tyre-noise samples. */
    public enum RollFamily { SOFT, HARD, WOOD }

    public static RollFamily family(Surface s) {
        return switch (s) {
            case DIRT, TRAIL, GRASS, SAND, MUD, SNOW, AIRBAG -> RollFamily.SOFT;
            case GRAVEL, ROCK, ICE -> RollFamily.HARD;
            case WOOD -> RollFamily.WOOD;
        };
    }

    public static double surfaceGain(Surface s) {
        return switch (s) {
            case DIRT -> 1.0;
            case TRAIL -> 0.9;
            case GRASS -> 0.75;
            case SAND -> 0.55;
            case MUD -> 0.65;
            case SNOW -> 0.45;
            case AIRBAG -> 0.3;
            case GRAVEL -> 1.0;
            case ROCK -> 0.9;
            case ICE -> 0.5;
            case WOOD -> 1.0;
        };
    }

    /** Tyre noise level: grows with speed, scales with how many tyres touch the ground, per surface. */
    public static double rollVolume(double speed, double contact, Surface s) {
        if (speed < 0.3) return 0;
        return Math.pow(clamp(speed / 7.0, 0, 1), 0.8) * 0.6 * clamp(contact, 0, 1) * surfaceGain(s);
    }

    public static double rollPitch(double speed) {
        return 0.6 + 0.8 * clamp(speed / 18, 0, 1);
    }

    /** The surface that dominates: under the rear tyre if it touches, else under the front one. */
    public static Surface dominantSurface(BikeAudioFrame f) {
        return f.rearContact || !f.frontContact ? f.rearSurface : f.frontSurface;
    }

    public static double contactFraction(BikeAudioFrame f) {
        return (f.frontContact ? 0.4 : 0) + (f.rearContact ? 0.6 : 0);
    }

    // ------------------------------------------------------------------ helpers

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static double smooth(double t) {
        double x = clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private BikeSoundMath() {}
}
