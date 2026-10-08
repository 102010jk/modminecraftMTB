package com.descentmtb.trail;

import java.util.Locale;

/**
 * Pure (Minecraft-free) side profiles of the jump builder ({@link ShapeMode#JUMP_BUILD}). A profile is ONE height
 * function of the distance along the jump, so every block of a jump is cut from the same curve: neighbouring blocks
 * sample the same function at their shared edge and meet exactly, whatever the length. The builder samples it at
 * the block corners ({@link JumpBuilder}); the screen and the in-world preview draw it; the tests ride it.
 *
 * <p>Distances are in blocks from the start edge of the clicked block, heights in blocks above the start.
 */
public final class JumpProfiles {
    // Only Minecraft's representable world dimensions remain; no bikepark size presets are enforced.
    public static final int MIN_LENGTH = 1, MAX_LENGTH = 60_000_000;
    public static final int MIN_WIDTH = 1, MAX_WIDTH = 60_000_000;
    public static final double MIN_HEIGHT = 1.0 / 16, MAX_HEIGHT = 4064, HEIGHT_STEP = 1.0 / 16;
    // A height-function take-off must stay below vertical to keep its slope finite.
    public static final int MIN_LIP = 1, MAX_LIP = 89;
    public static final int MIN_DECK = 1, MAX_DECK = MAX_LENGTH;
    public static final int MIN_LANDING = 1, MAX_LANDING = MAX_LENGTH;
    /** Length (blocks) of the face that climbs from the lip of a step-up to its platform. */
    public static final int STEP_UP_FACE = 1;
    /** The lip of a step-up sits at this share of the platform height. */
    public static final double STEP_UP_LIP = .6;

    /** The kinds of jump. Which of the {@link Params} apply depends on the kind. */
    public enum Type {
        /** Concave take-off: rises to the height and leaves at the lip angle. */
        KICKER,
        /** Straight ramp from the ground to the height. */
        RAMP,
        /** Kicker, flat deck at the lip height, landing back down to the ground. */
        TABLE,
        /** Kicker to a lower lip, a short face up to a higher platform, the platform. */
        STEP_UP,
        /** Convex roll-in: flat on top, steepens, flattens out at the bottom. */
        LANDING,
        /** Smooth hump up and down again. */
        ROLLER;

        public boolean hasLip() {
            return this == KICKER || this == TABLE || this == STEP_UP;
        }

        public boolean hasDeck() {
            return this == TABLE || this == STEP_UP;
        }

        public boolean hasLanding() {
            return this == TABLE;
        }

        /** Language key of the name. */
        public String key() {
            return "descentmtb.jump.type." + name().toLowerCase(Locale.ROOT);
        }

        /** The next (or previous, with a negative step) type, wrapping around. */
        public Type cycled(int step) {
            Type[] all = values();
            return all[Math.floorMod(ordinal() + step, all.length)];
        }

        /** The type of that name; {@link #KICKER} for an unknown one. */
        public static Type fromName(String name) {
            for (Type type : values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
            return KICKER;
        }
    }

    /**
     * The settings of one jump.
     *
     * @param length  length of the main part (kicker, ramp, landing, roller) in blocks
     * @param width   blocks across, centred on the clicked block
     * @param height  height in blocks (the platform of a step-up)
     * @param lip     angle of the lip in degrees (kicker, table, step-up)
     * @param deck    length of the flat deck / platform in blocks (table, step-up)
     * @param landing length of the landing of a table in blocks
     */
    public record Params(Type type, int length, int width, double height, int lip, int deck, int landing) {
        public static final Params DEFAULT = new Params(Type.KICKER, 3, 3, 1.5, 35, 3, 5);

        /** These settings inside the allowed ranges, the height on the 1/16 grid (what a client sends is not trusted). */
        public Params clamped() {
            double h = Double.isFinite(height) ? height : DEFAULT.height;
            h = Math.round(clamp(h, MIN_HEIGHT, MAX_HEIGHT) / HEIGHT_STEP) * HEIGHT_STEP;
            return new Params(type == null ? Type.KICKER : type, clamp(length, MIN_LENGTH, MAX_LENGTH),
                    clamp(width, MIN_WIDTH, MAX_WIDTH), h, clamp(lip, MIN_LIP, MAX_LIP),
                    clamp(deck, MIN_DECK, MAX_DECK), clamp(landing, MIN_LANDING, MAX_LANDING));
        }

        public Params withType(Type value) {
            return new Params(value, length, width, height, lip, deck, landing);
        }

        public Params withLength(int value) {
            return new Params(type, value, width, height, lip, deck, landing);
        }

        public Params withWidth(int value) {
            return new Params(type, length, value, height, lip, deck, landing);
        }

        public Params withHeight(double value) {
            return new Params(type, length, width, value, lip, deck, landing);
        }

        public Params withLip(int value) {
            return new Params(type, length, width, height, value, deck, landing);
        }

        public Params withDeck(int value) {
            return new Params(type, length, width, height, lip, value, landing);
        }

        public Params withLanding(int value) {
            return new Params(type, length, width, height, lip, deck, value);
        }

        /** Total length of the jump in blocks: the number of blocks it occupies along its direction. */
        public int total() {
            return switch (type) {
                case TABLE -> length + deck + landing;
                case STEP_UP -> length + STEP_UP_FACE + deck;
                default -> length;
            };
        }

        /** Height of the lip: the end of the kicker part (lower than the platform for a step-up). */
        public double lipHeight() {
            return type == Type.STEP_UP ? Math.round(height * STEP_UP_LIP / HEIGHT_STEP) * HEIGHT_STEP : height;
        }

        /** Height (blocks above the start) at {@code u} blocks along the jump; {@link #startHeight} before it, {@link #endHeight} after it. */
        public double heightAt(double u) {
            if (u <= 0) {
                return startHeight();
            }
            if (u >= total()) {
                return endHeight();
            }
            return switch (type) {
                case KICKER -> kicker(u, length, height, lip);
                case RAMP -> height * u / length;
                case LANDING -> landingCurve(u, length, height);
                case ROLLER -> height * (1 - Math.cos(2 * Math.PI * u / length)) / 2;
                case TABLE -> u <= length ? kicker(u, length, height, lip)
                        : u <= length + deck ? height : landingCurve(u - length - deck, landing, height);
                case STEP_UP -> {
                    double lipHeight = lipHeight();
                    if (u <= length) {
                        yield kicker(u, length, lipHeight, lip);
                    }
                    if (u <= length + STEP_UP_FACE) {
                        yield lipHeight + (height - lipHeight) * smoothstep((u - length) / STEP_UP_FACE);
                    }
                    yield height;
                }
            };
        }

        /** The height the jump starts at (the back edge of the clicked block): the top of a landing, 0 for the others. */
        public double startHeight() {
            return type == Type.LANDING ? height : 0;
        }

        /** The height the jump ends at (the front edge of the last block). */
        public double endHeight() {
            return switch (type) {
                case KICKER, RAMP, STEP_UP -> height;
                default -> 0;
            };
        }

        /** The angle (degrees) the lip actually sends the rider off at: the slope at the end of the kicker part. */
        public double lipAngle() {
            return switch (type) {
                case KICKER, TABLE, STEP_UP -> Math.toDegrees(Math.atan(kickerSlope(length, lipHeight(), lip)));
                case RAMP -> Math.toDegrees(Math.atan(height / length));
                default -> Double.NaN;
            };
        }

        /** Exact maximum slope of each curve, independent of the jump's length. */
        public double maxSlope() {
            double steepest = switch (type) {
                case KICKER -> kickerSlope(length, height, lip);
                case RAMP -> height / length;
                case LANDING -> 1.5 * height / length;
                case ROLLER -> Math.PI * height / length;
                case TABLE -> Math.max(kickerSlope(length, height, lip), 1.5 * height / landing);
                case STEP_UP -> Math.max(kickerSlope(length, lipHeight(), lip),
                        1.5 * (height - lipHeight()) / STEP_UP_FACE);
            };
            return Math.toDegrees(Math.atan(steepest));
        }
    }

    /**
     * Where a jump lies in the world: the clicked block (x, y, z) is its first block, (dirX, dirZ) the horizontal
     * direction it runs in (one of them 0, the other -1 or 1); it is centred across on the clicked block. Shared by the
     * builder and the client's preview.
     */
    public record Layout(int x, int y, int z, int dirX, int dirZ, Params params) {
        /** Blocks along the jump of the vertex or point (px, pz), 0 at the start edge of the clicked block. */
        public double along(double px, double pz) {
            double startX = dirX > 0 ? x : x + 1, startZ = dirZ > 0 ? z : z + 1;
            return dirX != 0 ? dirX * (px - startX) : dirZ * (pz - startZ);
        }

        /** Blocks to the right (looking along the jump) of the clicked block's centre line, for the point (px, pz). */
        public double across(double px, double pz) {
            return rightX() * (px - x - .5) + rightZ() * (pz - z - .5);
        }

        private int rightX() {
            return -dirZ;
        }

        private int rightZ() {
            return dirX;
        }

        /** The first and the last column across (offsets to the right), the clicked block in the middle. */
        public int firstColumn() {
            return -(params.width() - 1) / 2;
        }

        public int lastColumn() {
            return params.width() / 2;
        }

        /** True for the centre (px + .5, pz + .5) of a column that belongs to the jump. */
        public boolean contains(double px, double pz) {
            double u = along(px, pz), v = across(px, pz);
            return u > 0 && u < params.total() && v > firstColumn() - .5 && v < lastColumn() + .5;
        }

        /** Absolute surface height at the vertex or point (px, pz): the profile above the top of the clicked block. */
        public double height(double px, double pz) {
            return y + 1 + params.heightAt(along(px, pz));
        }

        /** The point (x, z) {@code u} blocks along the centre line of the clicked block. */
        public double[] point(double u) {
            double startX = dirX > 0 ? x : dirX < 0 ? x + 1 : x + .5;
            double startZ = dirZ > 0 ? z : dirZ < 0 ? z + 1 : z + .5;
            return new double[]{startX + dirX * u, startZ + dirZ * u};
        }

        /** The block columns the jump covers: min x, min z, max x, max z. */
        public int[] bounds() {
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (int i : new int[]{0, params.total() - 1}) {
                for (int k : new int[]{firstColumn(), lastColumn()}) {
                    int bx = x + dirX * i + rightX() * k, bz = z + dirZ * i + rightZ() * k;
                    x0 = Math.min(x0, bx);
                    z0 = Math.min(z0, bz);
                    x1 = Math.max(x1, bx);
                    z1 = Math.max(z1, bz);
                }
            }
            return new int[]{x0, z0, x1, z1};
        }
    }

    /**
     * A concave take-off that starts flat, rises to {@code height} over {@code length} and leaves at {@code lipDeg}.
     * When a circular transition fits (the way kickers are shaped by hand) it is a flat run-in plus that arc; when the
     * arc is longer than the kicker, a power curve {@code (u/L)^p} with the same exit slope is used instead. A lip
     * angle flatter than a straight ramp of that height is impossible; the kicker is straight then.
     */
    static double kicker(double u, double length, double height, double lipDeg) {
        double theta = Math.toRadians(lipDeg);
        double arc = height / Math.tan(theta / 2);   // horizontal length of a circular transition
        if (arc <= length) {
            double radius = height / (1 - Math.cos(theta));
            double d = u - (length - arc);
            return d <= 0 ? 0 : radius - Math.sqrt(Math.max(0, radius * radius - d * d));
        }
        double power = Math.max(1, Math.tan(theta) * length / height);
        return height * Math.pow(Math.max(0, u) / length, power);
    }

    /** The slope at the lip of {@link #kicker}. */
    static double kickerSlope(double length, double height, double lipDeg) {
        double theta = Math.toRadians(lipDeg);
        if (height / Math.tan(theta / 2) <= length) {
            return Math.tan(theta);
        }
        return Math.max(1, Math.tan(theta) * length / height) * height / length;
    }

    /** A landing from {@code height} down to 0 over {@code length}: flat on top, steepest in the middle, flat at the bottom. */
    static double landingCurve(double u, double length, double height) {
        return height * (1 - smoothstep(u / length));
    }

    private static double smoothstep(double t) {
        double c = Math.max(0, Math.min(1, t));
        return c * c * (3 - 2 * c);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private JumpProfiles() {}
}
