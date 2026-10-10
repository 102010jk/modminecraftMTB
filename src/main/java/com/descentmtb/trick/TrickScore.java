package com.descentmtb.trick;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure scoring of one clean landing, and the dry line for a bad one. No Minecraft: the client banner only turns the
 * numbers into text.
 *
 * <p>Score = (sum of trick difficulties, a repeated trick counting less, times a combo bonus + rotations) times an
 * airtime factor. Grades: Nice, Great, Sick, Huge, Legendary.
 */
public final class TrickScore {
    public enum Grade { NONE, NICE, GREAT, SICK, HUGE, LEGENDARY }

    /** Lowest score of each grade (NONE = nothing worth a banner). */
    public static final double GREAT_AT = 2.5, SICK_AT = 4.5, HUGE_AT = 7.5, LEGENDARY_AT = 12;

    private static final Map<Trick, Double> DIFFICULTY = new EnumMap<>(Trick.class);
    static {
        DIFFICULTY.put(Trick.NO_HANDER, 1.0);
        DIFFICULTY.put(Trick.TUCK_NO_HANDER, 1.1);
        DIFFICULTY.put(Trick.TABLETOP, 1.2);
        DIFFICULTY.put(Trick.NAC_NAC, 1.4);
        DIFFICULTY.put(Trick.CAN_CAN, 1.6);
        DIFFICULTY.put(Trick.SUPERMAN, 1.8);
        DIFFICULTY.put(Trick.SUPERMAN_SEATGRAB, 2.0);
        DIFFICULTY.put(Trick.HEELCLICKER, 2.2);
        DIFFICULTY.put(Trick.BARSPIN, 2.4);
        DIFFICULTY.put(Trick.TAILWHIP, 2.6);
        // skis: race (old-school aerials) and freestyle (grabs); spins and flips score on top as on the bikes
        DIFFICULTY.put(Trick.SPREAD_EAGLE, 1.0);
        DIFFICULTY.put(Trick.DAFFY, 1.3);
        DIFFICULTY.put(Trick.IRON_CROSS, 1.8);
        DIFFICULTY.put(Trick.BACK_SCRATCHER, 1.9);
        DIFFICULTY.put(Trick.TIP_GRAB, 1.5);
        DIFFICULTY.put(Trick.SAFETY_GRAB, 1.0);
        DIFFICULTY.put(Trick.MUTE_GRAB, 1.3);
        DIFFICULTY.put(Trick.TAIL_GRAB, 1.6);
        DIFFICULTY.put(Trick.TRUCK_DRIVER, 1.9);
        DIFFICULTY.put(Trick.JAPAN_GRAB, 2.2);
    }

    /** One graded landing. {@code combo} = two or more tricks. */
    public record Result(Grade grade, double score, boolean combo) {}

    public static double difficulty(Trick trick) {
        return DIFFICULTY.getOrDefault(trick, 0.0);
    }

    /**
     * @param tricks     tricks finished in the jump, in order
     * @param airTime    seconds in the air
     * @param spinDegrees total yaw spin of the bike in degrees (absolute, multiples of 180)
     * @param flips      number of full flips (absolute)
     */
    public static double score(List<Trick> tricks, double airTime, double spinDegrees, int flips) {
        double sum = 0;
        int count = 0;
        Map<Trick, Integer> seen = new EnumMap<>(Trick.class);
        for (Trick t : tricks) {
            double d = difficulty(t);
            if (d <= 0) continue;
            int before = seen.merge(t, 1, Integer::sum) - 1;
            sum += d * Math.pow(0.6, before);   // the same trick again is worth less
            count++;
        }
        double base = sum * (1 + 0.4 * Math.max(0, count - 1));
        base += Math.max(0, spinDegrees) / 180.0 * 0.9 + Math.abs(flips) * 3.0;
        double airFactor = Math.max(0.8, Math.min(2.2, 0.7 + 0.45 * Math.max(0, airTime)));
        return base * airFactor;
    }

    public static Grade grade(double score) {
        if (score >= LEGENDARY_AT) return Grade.LEGENDARY;
        if (score >= HUGE_AT) return Grade.HUGE;
        if (score >= SICK_AT) return Grade.SICK;
        if (score >= GREAT_AT) return Grade.GREAT;
        return score > 0 ? Grade.NICE : Grade.NONE;
    }

    public static Result evaluate(List<Trick> tricks, double airTime, double spinDegrees, int flips) {
        double s = score(tricks, airTime, spinDegrees, flips);
        int counted = 0;
        for (Trick t : tricks) if (difficulty(t) > 0) counted++;
        return new Result(grade(s), s, counted >= 2);
    }

    // ---------------------------------------------------------------- bad landings

    public enum BailKind { NONE, MID_TRICK, UNDER_SPIN, UNDER_FLIP }

    /** What to say about a bail. {@code detail}: trick name (MID_TRICK), "360" (UNDER_SPIN) or "Backflip" (UNDER_FLIP). */
    public record BailLine(BailKind kind, String detail) {
        public static final BailLine NONE = new BailLine(BailKind.NONE, "");
    }

    /**
     * @param reason       the sim's bail reason ("landed sideways (..)", "landed with the nose .. off", ...)
     * @param unfinished   the trick still going when the rider hit the ground ({@link Trick#NONE} if none)
     * @param airYawDegrees  yaw travelled in the air (signed)
     * @param airPitchDegrees pitch travelled in the air (signed; positive = backflip)
     */
    public static BailLine bailLine(String reason, Trick unfinished, double airYawDegrees, double airPitchDegrees) {
        if (unfinished != null && unfinished != Trick.NONE) return new BailLine(BailKind.MID_TRICK, unfinished.displayName);
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        double yaw = Math.abs(airYawDegrees);
        if (r.startsWith("landed sideways") && yaw >= 120) {
            return new BailLine(BailKind.UNDER_SPIN, Integer.toString((int) Math.ceil(yaw / 180.0) * 180));
        }
        if (r.startsWith("landed with the nose") && Math.abs(airPitchDegrees) >= 150) {
            return new BailLine(BailKind.UNDER_FLIP, airPitchDegrees > 0 ? "Backflip" : "Frontflip");
        }
        return BailLine.NONE;
    }

    private TrickScore() {}
}
