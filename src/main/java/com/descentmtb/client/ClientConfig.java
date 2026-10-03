package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeParams;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Player settings ({@code config/descentmtb-client.toml}, also editable in-game:
 * Mods → Descent MTB → Config). The physics runs on the rider's client, so these
 * are client settings and apply live.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue MANUAL_ASSIST;
    public static final ModConfigSpec.DoubleValue MANUAL_ASSIST_STRENGTH;
    public static final ModConfigSpec.DoubleValue BAIL_TOLERANCE;
    public static final ModConfigSpec.BooleanValue LANDING_ASSIST;
    public static final ModConfigSpec.DoubleValue PEDAL_POWER, PEDAL_FORCE, PEDAL_SPEED;
    public static final ModConfigSpec.DoubleValue BRAKE_STRENGTH, MANUAL_ANGLE, AIR_CONTROL, AIR_RECOVERY;
    public static final ModConfigSpec.DoubleValue FLIP_RATE, SPIN_RATE, PRESSURE_EFFECT;
    public static final ModConfigSpec.BooleanValue WALL_RIDES, TRICK_BANNER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("riding");
        MANUAL_ASSIST = b.comment("Help balance manuals: leaning back lifts the front wheel and holds it at the balance point.")
                .translation("descentmtb.config.manual_assist")
                .define("manualAssist", true);
        MANUAL_ASSIST_STRENGTH = b.comment("How much the manual assist helps (0 = barely, 1 = default, 2 = a lot).")
                .translation("descentmtb.config.manual_assist_strength")
                .defineInRange("manualAssistStrength", 1.0, 0.0, 2.0);
        LANDING_ASSIST = b.comment("Ease the bike onto the landing slope and finish under-rotated spins (Descenders-style).")
                .translation("descentmtb.config.landing_assist")
                .define("landingAssist", true);
        BAIL_TOLERANCE = b.comment("Bail tolerance: 1 = default, higher = harder to crash, lower = stricter.")
                .translation("descentmtb.config.bail_tolerance")
                .defineInRange("bailTolerance", 1.0, 0.5, 2.0);
        PEDAL_POWER = number(b, "pedalPower", "Sprint power in watts; controls how quickly pedalling builds speed.", 1050, 100, 2500);
        PEDAL_FORCE = number(b, "pedalForce", "Maximum drive force in newtons at low speed.", 900, 100, 1600);
        PEDAL_SPEED = number(b, "pedalSpeedKmh", "Speed where pedalling starts to fade; final speed is a little higher.", 26, 10, 65);
        BRAKE_STRENGTH = number(b, "brakeStrength", "Brake force multiplier.", 1, .25, 2);
        MANUAL_ANGLE = number(b, "manualAngleDegrees", "Manual balance angle above the ground slope.", 30, 18, 45);
        AIR_CONTROL = number(b, "airControl", "Air steering and flip response multiplier.", 1, .3, 2);
        AIR_RECOVERY = number(b, "airRecovery", "Automatic alignment to the landing slope.", .85, 0, 1);
        FLIP_RATE = number(b, "flipRate", "Flip speed multiplier.", 1, .5, 1.6);
        SPIN_RATE = number(b, "spinRate", "Spin speed multiplier.", 1, .5, 1.6);
        PRESSURE_EFFECT = number(b, "pressureEffect", "How strongly tyre pressure affects rolling resistance and grip.", 1, 0, 2);
        WALL_RIDES = b.translation("descentmtb.config.wallRides").define("wallRides", true);
        TRICK_BANNER = b.translation("descentmtb.config.trickBanner").define("trickBanner", true);
        b.pop();
        SPEC = b.build();
    }

    private static final BikeParams DEFAULTS = new BikeParams();

    private static ModConfigSpec.DoubleValue number(ModConfigSpec.Builder b, String key, String comment, double value, double min, double max) {
        return b.comment(comment).translation("descentmtb.config."+key).defineInRange(key, value, min, max);
    }

    /** Copies the settings into the shared physics parameters (cheap; done every tick). */
    static void apply(MountainBikeEntity bike) {
        if (!SPEC.isLoaded()) return;
        BikeParams p = bike.params();
        BikeParams defaults = bike.bikeType().params();
        p.manualAssist = MANUAL_ASSIST.get() ? DEFAULTS.manualAssist * MANUAL_ASSIST_STRENGTH.get() : 0;
        p.landingAssistRate = LANDING_ASSIST.get() ? DEFAULTS.landingAssistRate : 0;
        double tol = BAIL_TOLERANCE.get();
        p.bailPitchError = Math.min(Math.toRadians(175), DEFAULTS.bailPitchError * tol);
        p.bailYawError = Math.min(Math.toRadians(175), DEFAULTS.bailYawError * tol);
        p.bailImpactSpeed = defaults.bailImpactSpeed * tol;
        p.crashSpeed = DEFAULTS.crashSpeed * tol;
        p.wallCrashSpeed = DEFAULTS.wallCrashSpeed * tol;
        p.pedalPower = PEDAL_POWER.get(); p.pedalMaxForce = PEDAL_FORCE.get();
        p.pedalSpinOut = PEDAL_SPEED.get() / 3.6;
        p.brakeForce = defaults.brakeForce * BRAKE_STRENGTH.get();
        p.manualTargetPitch = Math.toRadians(MANUAL_ANGLE.get());
        p.airControlResponse = defaults.airControlResponse / AIR_CONTROL.get();
        p.airAlignAssist = AIR_RECOVERY.get();
        p.flipRate = defaults.flipRate * FLIP_RATE.get(); p.spinRate = defaults.spinRate * SPIN_RATE.get();
        p.wallRides = WALL_RIDES.get();
        com.descentmtb.physics.BikeTuning.apply(p, defaults, bike.frontPsi(), bike.rearPsi(), bike.forkPsi(), PRESSURE_EFFECT.get());
    }

    private ClientConfig() {}
}
