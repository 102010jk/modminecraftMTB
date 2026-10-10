package com.descentmtb.client.ski;

import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.ski.SkiBrand;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;

/**
 * Where each foot (= boot = ski) is, shared by the ski model ({@code SkiModel}, drawn by the bike renderer) and the
 * rider's legs ({@code RiderPose}), so the boots always sit in the bindings, also during the tricks (Daffy, Iron
 * Cross, grabs ...) and the snowplough / hockey-stop brake.
 *
 * <p>Ski model space: metres, origin on the snow between the two skis under the boots, +X = the rider's left, +Y up,
 * -Z forward (the bike models' convention before the renderer's {@code scale(-1, -1, 1)} flip).
 *
 * <p>What the feet do:
 * <ul>
 *   <li>Riding: hip-width parallel stance; carving, the inside ski leads a little and the stance opens.</li>
 *   <li>Brake (Space): a snowplough wedge (tips together, tails apart, both skis on their inside edges). At speed and
 *       while steering, a hockey stop instead: both skis turned across toward the steering side, on their uphill
 *       edges.</li>
 *   <li>Tricks: see {@link #trickFoot}. The grabbing hand of a grab meets the ski at {@link #grabPoint}.</li>
 * </ul>
 * Everything follows the eased trick amount, so tricks blend in and out with the rider's limbs.
 *
 * <p>Pure maths apart from {@link Input#read}: the headless tests drive {@link #foot(Input, int, float[])} directly.
 */
public final class SkiStance {
    /** Half the distance between the two skis' centre lines (hip-width stance), m. */
    public static final float HALF_STANCE = 0.11f;
    /** Height of the boot sole (top of ski + binding plate) above the snow, m. */
    public static final float BOOT_SOLE = 0.045f;

    /** Indices into the {@link #foot} output. */
    public static final int X = 0, Y = 1, Z = 2, PITCH = 3, YAW = 4, ROLL = 5;

    /** Share of the ski's length ahead of the boot centre (the binding sits a little behind the middle). */
    public static final float TIP_SHARE = 0.53f;
    /** Snowplough: each ski's angle off the travel line (rad) and the gap left between the tips (m). */
    private static final float WEDGE = 0.26f, TIP_GAP = 0.07f;
    /** Hockey stop: how far both skis turn across (rad) and how hard they are edged. */
    private static final float HOCKEY_YAW = 0.95f, HOCKEY_EDGE = 0.45f;

    /** Everything the stance depends on, read once per frame (render thread). */
    public static final class Input {
        public Trick trick = Trick.NONE;
        /** Eased trick amount 0..1 and progress (as {@code RiderPose} uses them). */
        public float amount, progress;
        /** +1 / -1: the trick's side (which hand / ski leads). */
        public int trickSide = 1;
        /** Brake 0..1, speed (m/s), carve / steering angle (rad, + = right), legs (m, - = crouched). */
        public float brake, speed, steer, riderUp;
        public boolean airborne;
        /** Ski length (m). */
        public float length = 1.80f;

        /** Fills this from the interpolated render state of a pair of skis. */
        public Input read(MountainBikeEntity skis, BikeRenderState a, BikeRenderState b, float partialTick) {
            double t = partialTick;
            // never blend the amount of one trick into another's (as RiderPose)
            boolean same = a.trick == b.trick;
            trick = b.trick;
            amount = trick == Trick.NONE ? 0f
                    : (float) TrickAnimation.ease(same ? BikeRenderState.lerp(t, a.trickAmount, b.trickAmount) : b.trickAmount);
            progress = (float) (same ? BikeRenderState.lerp(t, a.trickProgress, b.trickProgress) : b.trickProgress);
            trickSide = b.trickSide < 0 ? -1 : 1;
            brake = (float) BikeRenderState.lerp(t, a.brake, b.brake);
            steer = (float) BikeRenderState.lerp(t, a.steer, b.steer);
            riderUp = (float) BikeRenderState.lerp(t, a.riderUp, b.riderUp);
            speed = (float) BikeRenderState.lerp(t, a.vel.length(), b.vel.length());
            airborne = b.airborne;
            SkiBrand brand = skis == null ? null : skis.skiBrand();
            length = brand == null ? 1.80f : brand.lengthCm / 100f;
            return this;
        }
    }

    private static final Input SCRATCH = new Input();

    /**
     * Pose of one foot / ski at this frame. {@code side} = +1 the rider's left foot, -1 the right foot.
     * Fills {@code out} with: the boot-sole centre (x, y, z in ski model space, metres) and the ski's rotation about
     * that point: pitch (+ = tip up), yaw (+ = tip turned toward +X, the rider's left), roll (+ = rolled onto the
     * ski's left edge), radians, applied in the order yaw, pitch, roll.
     *
     * <p>As a matrix in ski model space (Y up): {@code R = Ry(-yaw) * Rx(pitch) * Rz(-roll)} (right-handed axis
     * rotations; pose-stack order: yaw first, roll last), so a point of the ski at {@code (across, up, -along)} from
     * the boot sole lies at {@code foot + R (across, up, -along)}: see {@link #skiPoint}.
     */
    public static void foot(MountainBikeEntity skis, BikeRenderState a, BikeRenderState b, float partialTick, int side, float[] out) {
        foot(SCRATCH.read(skis, a, b, partialTick), side, out);
    }

    /** {@link #foot(MountainBikeEntity, BikeRenderState, BikeRenderState, float, int, float[])} from read inputs. */
    public static void foot(Input in, int side, float[] out) {
        float s = side < 0 ? -1f : 1f;
        float x = s * HALF_STANCE, y = BOOT_SOLE, z = 0, pitch = 0, yaw = 0, roll = 0;

        if (!in.airborne) {
            // carving: the inside ski leads by a few centimetres and the stance opens a little
            float carve = clamp(Math.abs(in.steer) / 0.25f, 0, 1) * clamp(in.speed / 6f, 0, 1);
            boolean inside = in.steer > 0 ? s < 0 : s > 0;
            if (inside) z -= 0.07f * carve;
            x += s * 0.02f * carve;

            float br = clamp(in.brake, 0, 1);
            if (br > 0) {
                // fast and steering: hockey stop to the steering side; otherwise a snowplough wedge
                float fast = smooth((in.speed - 5f) / 6f);
                float dir = clamp(in.steer / 0.06f, -1, 1);
                float hockey = br * fast * Math.abs(dir);
                float wedge = br - hockey;
                // snowplough: tips together, tails apart, both skis on their inside edges
                float tip = in.length * TIP_SHARE;
                float wx = s * (TIP_GAP * 0.5f + tip * (float) Math.sin(WEDGE));
                x += (wx - x) * wedge;
                yaw += -s * WEDGE * wedge;
                roll += -s * 0.30f * wedge;
                // hockey stop: the whole stance turns across the travel line, skis edged against the skid
                if (hockey > 0) {
                    float yh = -Math.signum(dir) * HOCKEY_YAW * hockey;   // steer right (+) = tips to the right (-X)
                    float c = (float) Math.cos(yh), sn = (float) Math.sin(yh);
                    // a yaw of +yh is a right-handed turn about +Y by -yh
                    float nx = x * c - z * sn, nz = x * sn + z * c;
                    x = nx * (1 + 0.25f * hockey);
                    z = nz;
                    yaw += yh;
                    roll += Math.signum(yh) * HOCKEY_EDGE * hockey;
                }
            }
        }

        if (in.trick != Trick.NONE && in.amount > 0) {
            float[] f = TMP;
            f[X] = x; f[Y] = y; f[Z] = z; f[PITCH] = pitch; f[YAW] = yaw; f[ROLL] = roll;
            trickFoot(in.trick, clamp(in.amount, 0, 1), in.trickSide < 0 ? -1 : 1, s, f);
            x = f[X]; y = f[Y]; z = f[Z]; pitch = f[PITCH]; yaw = f[YAW]; roll = f[ROLL];
        }

        out[X] = x;
        out[Y] = y;
        out[Z] = z;
        out[PITCH] = pitch;
        out[YAW] = yaw;
        out[ROLL] = roll;
    }

    private static final float[] TMP = new float[6];

    /**
     * The trick motion of one foot, blended in by {@code k} (eased amount). {@code ts} = the trick's side, {@code s} =
     * this foot's side (+1 left). The trick-side foot leads (Daffy, Iron Cross on top); a grab uses the trick-side
     * hand, see {@link #grabbedSide}.
     */
    private static void trickFoot(Trick trick, float k, int ts, float s, float[] f) {
        boolean lead = s == ts;
        switch (trick) {
            case SPREAD_EAGLE -> {
                // legs thrown wide to the sides: the feet swing out and up about the hips, skis in a wide V
                f[X] += s * 0.30f * k;
                f[Y] += 0.09f * k;
                f[YAW] += s * 0.30f * k;
                f[ROLL] -= s * 0.45f * k;         // the soles turn out with the legs: outer edges up
            }
            case DAFFY -> {
                // a running split: the lead ski kicked forward tip up, the other back tail up
                if (lead) {
                    f[Z] -= 0.36f * k;
                    f[Y] += 0.10f * k;
                    f[PITCH] += 0.50f * k;
                } else {
                    f[Z] += 0.36f * k;
                    f[Y] += 0.12f * k;
                    f[PITCH] -= 0.55f * k;
                }
            }
            case IRON_CROSS -> {
                // knees bent, feet up behind, tips crossed in an X ahead of the boots (the lead ski on top)
                f[X] += (s * 0.15f - f[X]) * k;
                f[Y] += (0.16f + (lead ? 0.06f : 0f)) * k;
                f[Z] += 0.10f * k;
                f[PITCH] -= 0.30f * k;
                f[YAW] -= s * 0.27f * k;
                f[ROLL] += s * 0.20f * k;
            }
            case BACK_SCRATCHER -> {
                // knees bent hard, boots pulled up behind, tips down and tails up toward the back
                f[X] += (s * 0.09f - f[X]) * k;
                f[Y] += 0.30f * k;
                f[Z] += 0.22f * k;
                f[PITCH] -= 1.15f * k;
            }
            case TIP_GRAB -> {
                // knees up, both tips raised in front; the grabbed one a little higher
                f[Y] += 0.20f * k;
                f[Z] -= 0.08f * k;
                f[PITCH] += (lead ? 0.65f : 0.50f) * k;
            }
            case MUTE_GRAB -> {
                // knees tucked; the opposite ski is pulled up across toward the grabbing hand, rolled by the grab
                f[Y] += 0.22f * k;
                f[Z] -= 0.02f * k;
                if (s == grabbedSide(trick, ts)) {
                    f[Y] += 0.10f * k;
                    f[X] += (0f - f[X]) * k;
                    f[YAW] -= s * 0.15f * k;
                    f[ROLL] += s * 0.35f * k;
                }
            }
            case JAPAN_GRAB -> {
                if (s == grabbedSide(trick, ts)) {
                    // the grabbed ski tucked up behind and tweaked: tip down, rolled hard, tip turned out
                    f[X] += (s * 0.02f - f[X]) * k;
                    f[Y] += 0.34f * k;
                    f[Z] += 0.08f * k;
                    f[PITCH] -= 0.75f * k;
                    f[YAW] += s * 0.25f * k;
                    f[ROLL] += s * 0.55f * k;
                } else {
                    // the other knee driven forward
                    f[Y] += 0.18f * k;
                    f[Z] -= 0.08f * k;
                    f[PITCH] += 0.15f * k;
                }
            }
            case SAFETY_GRAB -> {
                // knees tucked; the grabbed (same-side) ski lifted and rolled outer edge up into the hand
                f[Y] += 0.20f * k;
                if (s == grabbedSide(trick, ts)) {
                    f[Y] += 0.10f * k;
                    f[X] += s * 0.04f * k;
                    f[ROLL] -= s * 0.20f * k;
                }
            }
            case TAIL_GRAB -> {
                if (s == grabbedSide(trick, ts)) {
                    // boot pulled up behind, tail raised to the reaching hand
                    f[Y] += 0.32f * k;
                    f[Z] += 0.18f * k;
                    f[PITCH] -= 0.85f * k;
                } else {
                    f[Y] += 0.15f * k;
                    f[Z] -= 0.04f * k;
                }
            }
            case TRUCK_DRIVER -> {
                // both knees up, both tips raised in front to the hands like a steering wheel
                f[X] += (s * 0.13f - f[X]) * k;
                f[Y] += 0.22f * k;
                f[Z] -= 0.12f * k;
                f[PITCH] += 0.80f * k;
            }
            default -> {}
        }
    }

    /**
     * The ski a grab takes hold of (+1 left, -1 right), or 0 for tricks without a grab. Truck Driver grabs both
     * (returns the trick side; each hand takes its own ski). The grabbing hand is always the trick-side hand.
     */
    public static int grabbedSide(Trick trick, int trickSide) {
        int ts = trickSide < 0 ? -1 : 1;
        return switch (trick) {
            case TIP_GRAB, SAFETY_GRAB, TAIL_GRAB, TRUCK_DRIVER -> ts;
            case MUTE_GRAB, JAPAN_GRAB -> -ts;
            default -> 0;
        };
    }

    /**
     * Where on the grabbed ski the hand holds (ski-local, metres from the boot-sole centre): {along (+ toward the
     * tip), across (+ toward +X), up}. {@code length} = ski length, {@code ski} = the grabbed ski's side.
     * Truck Driver: each hand at its own ski's tip.
     */
    public static void grabPoint(Trick trick, int ski, float length, float[] out) {
        float s = ski < 0 ? -1 : 1;
        float tip = length * TIP_SHARE;
        float along = 0, across = 0, up = 0.02f;
        switch (trick) {
            case TIP_GRAB -> along = tip - 0.14f;
            case TRUCK_DRIVER -> along = Math.min(0.65f, tip - 0.12f);
            case MUTE_GRAB -> { along = 0.14f; across = -s * 0.045f; }     // inside (toe) edge ahead of the boot
            case JAPAN_GRAB -> { along = 0.16f; across = -s * 0.045f; }
            case SAFETY_GRAB -> { along = 0.02f; across = s * 0.05f; up = 0f; }  // outside edge under the boot
            case TAIL_GRAB -> along = -0.40f;
            default -> {}
        }
        out[0] = along;
        out[1] = across;
        out[2] = up;
    }

    /**
     * A point fixed to a ski, given in the ski's own frame from the boot-sole centre ({@code along} + toward the
     * tip, {@code across} + toward the ski's left edge, {@code up} + off the top sheet), in ski model space.
     */
    public static void skiPoint(float[] foot, float along, float across, float up, float[] out) {
        double vx = across, vy = up, vz = -along;
        // Rz(-roll)
        double c = Math.cos(-foot[ROLL]), sn = Math.sin(-foot[ROLL]);
        double x1 = vx * c - vy * sn, y1 = vx * sn + vy * c, z1 = vz;
        // Rx(pitch)
        c = Math.cos(foot[PITCH]);
        sn = Math.sin(foot[PITCH]);
        double y2 = y1 * c - z1 * sn, z2 = y1 * sn + z1 * c, x2 = x1;
        // Ry(-yaw)
        c = Math.cos(-foot[YAW]);
        sn = Math.sin(-foot[YAW]);
        double x3 = x2 * c + z2 * sn, z3 = -x2 * sn + z2 * c;
        out[0] = (float) (foot[X] + x3);
        out[1] = (float) (foot[Y] + y2);
        out[2] = (float) (foot[Z] + z3);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float smooth(float t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private SkiStance() {}
}
