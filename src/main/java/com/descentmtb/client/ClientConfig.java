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
        b.pop();
        SPEC = b.build();
    }

    private static final BikeParams DEFAULTS = new BikeParams();

    /** Copies the settings into the shared physics parameters (cheap; done every tick). */
    static void apply() {
        if (!SPEC.isLoaded()) return;
        BikeParams p = MountainBikeEntity.PARAMS;
        p.manualAssist = MANUAL_ASSIST.get() ? DEFAULTS.manualAssist * MANUAL_ASSIST_STRENGTH.get() : 0;
        p.landingAssistRate = LANDING_ASSIST.get() ? DEFAULTS.landingAssistRate : 0;
        double tol = BAIL_TOLERANCE.get();
        p.bailPitchError = Math.min(Math.toRadians(89), DEFAULTS.bailPitchError * tol);
        p.bailYawError = Math.min(Math.toRadians(120), DEFAULTS.bailYawError * tol);
        p.bailImpactSpeed = DEFAULTS.bailImpactSpeed * tol;
        p.crashSpeed = DEFAULTS.crashSpeed * tol;
        p.wallCrashSpeed = DEFAULTS.wallCrashSpeed * tol;
    }

    private ClientConfig() {}
}
