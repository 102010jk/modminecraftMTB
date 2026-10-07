package com.descentmtb.custom;

import java.util.ArrayList;
import java.util.List;

/**
 * What the player chose for one motorbike (dirt bike or pit bike): the paint of every body group and the mechanical
 * tuning. Lives on the bike item as a data component, travels with the bike entity (synced to every viewer) and is
 * edited on the bike stand. Immutable; use the {@code with...} methods. Minecraft-free (the save and network formats
 * are in {@link MotoBuildCodecs}), so it is unit-tested.
 *
 * <p>Always valid: the constructor clamps everything (a client or edited NBT can send anything), so a decoded or
 * hand-made build needs no further sanitising on the server.
 *
 * @param colors     one entry per {@link #GROUPS}: a 0xRRGGBB paint colour, or {@link #STOCK_COLOUR} for the stock one
 * @param sprocket   rear sprocket teeth against stock ({@link #MIN_SPROCKET}..{@link #MAX_SPROCKET}): + = quicker
 *                   acceleration and a lower top speed, - the opposite
 * @param exhaust    the pipe
 * @param suspension spring and damper setting
 * @param number     race number on the plates, 1..99 (0 = none); stored, drawn only where the model has plates
 */
public record MotoBuild(List<Integer> colors, int sprocket, Exhaust exhaust, Suspension suspension, int number) {
    /** The paint groups of a motorbike (same names and order as {@code MotoModel.GROUPS}, which is client-only). */
    public static final String[] GROUPS = {"plastic", "fender", "frame", "seat", "rim", "spring", "anodized"};
    /** Colour entry meaning "the stock colour of this group". */
    public static final int STOCK_COLOUR = -1;
    public static final int MIN_SPROCKET = -3, MAX_SPROCKET = 3, MAX_NUMBER = 99;

    /** The pipe: the race exhaust makes 8% more torque and is louder. */
    public enum Exhaust {
        STOCK(1.0), RACE(1.08);

        /** Multiplier of the engine's torque. */
        public final double torque;

        Exhaust(double torque) {
            this.torque = torque;
        }

        public String key() {
            return "descentmtb.moto.exhaust." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Spring rates of fork and shock (damping follows by the square root); SOFT = 15% softer, STIFF = 15% stiffer. */
    public enum Suspension {
        SOFT(0.85), STOCK(1.0), STIFF(1.15);

        /** Multiplier of the spring rates. */
        public final double spring;

        Suspension(double spring) {
            this.spring = spring;
        }

        public String key() {
            return "descentmtb.moto.suspension." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Stock everything: stock colours, stock gearing, pipe and suspension, no number. */
    public static final MotoBuild DEFAULT = new MotoBuild(List.of(), 0, Exhaust.STOCK, Suspension.STOCK, 0);

    public MotoBuild {
        List<Integer> clean = new ArrayList<>(GROUPS.length);
        for (int i = 0; i < GROUPS.length; i++) {
            Integer c = colors != null && i < colors.size() ? colors.get(i) : null;
            clean.add(c == null || c < 0 ? STOCK_COLOUR : c & 0xFFFFFF);
        }
        colors = List.copyOf(clean);
        sprocket = Math.max(MIN_SPROCKET, Math.min(MAX_SPROCKET, sprocket));
        exhaust = exhaust == null ? Exhaust.STOCK : exhaust;
        suspension = suspension == null ? Suspension.STOCK : suspension;
        number = Math.max(0, Math.min(MAX_NUMBER, number));
    }

    /** Index of a paint group, -1 for an unknown name. */
    public static int groupIndex(String group) {
        for (int i = 0; i < GROUPS.length; i++) {
            if (GROUPS[i].equals(group)) return i;
        }
        return -1;
    }

    /** Paint colour of a group (0xRRGGBB), or {@link #STOCK_COLOUR} when it keeps the stock one (or the group is unknown). */
    public int colorOf(String group) {
        int i = groupIndex(group);
        return i < 0 ? STOCK_COLOUR : colors.get(i);
    }

    public boolean isStock() {
        return equals(DEFAULT);
    }

    public MotoBuild withColor(int group, int rgb) {
        List<Integer> next = new ArrayList<>(colors);
        next.set(group, rgb < 0 ? STOCK_COLOUR : rgb & 0xFFFFFF);
        return new MotoBuild(next, sprocket, exhaust, suspension, number);
    }

    /** The stock look with the tuning kept. */
    public MotoBuild withStockPaint() {
        return new MotoBuild(List.of(), sprocket, exhaust, suspension, number);
    }

    public MotoBuild withSprocket(int teeth) {
        return new MotoBuild(colors, teeth, exhaust, suspension, number);
    }

    public MotoBuild withExhaust(Exhaust value) {
        return new MotoBuild(colors, sprocket, value, suspension, number);
    }

    public MotoBuild withSuspension(Suspension value) {
        return new MotoBuild(colors, sprocket, exhaust, value, number);
    }

    public MotoBuild withNumber(int value) {
        return new MotoBuild(colors, sprocket, exhaust, suspension, value);
    }
}
