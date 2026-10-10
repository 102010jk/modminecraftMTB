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
    public static final ModConfigSpec.DoubleValue WALL_RIDE_SPEED;
    public static final ModConfigSpec.DoubleValue STEERING_GRIP, KEYBOARD_STEER_RAMP;
    public static final ModConfigSpec.DoubleValue AIR_ROTATION_SENSITIVITY, AIR_ROTATION_DEADZONE;
    public static final ModConfigSpec.BooleanValue RISK_REWARD;

    // ---- camera (BikeCamera) ----
    public static final ModConfigSpec.EnumValue<BikeCamera.Mode> CAMERA_MODE;
    public static final ModConfigSpec.DoubleValue CAMERA_SMOOTHING, CAMERA_ORBIT_RETURN;
    public static final ModConfigSpec.DoubleValue CAM_FIRST_SMOOTH;
    public static final ModConfigSpec.DoubleValue CAM_SECOND_DIST, CAM_SECOND_HEIGHT, CAM_SECOND_SMOOTH;
    public static final ModConfigSpec.DoubleValue CAM_THIRD_DIST, CAM_THIRD_HEIGHT, CAM_THIRD_SMOOTH;
    public static final ModConfigSpec.DoubleValue CAM_DRONE_DIST, CAM_DRONE_HEIGHT, CAM_DRONE_SMOOTH;

    // ---- tricks (TrickInput / BikeInputHandler) ----
    public static final ModConfigSpec.EnumValue<TrickKeyScheme> TRICK_KEY_SCHEME;

    public enum RiderVoice {
        MALE, FEMALE, OFF
    }

    // ---- sound (client.sound.BikeSoundController) ----
    public static final ModConfigSpec.DoubleValue BIKE_SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue SCREAM_SOUND, WIND_SOUND, HUB_SOUND;
    public static final ModConfigSpec.EnumValue<RiderVoice> RIDER_VOICE;

    public static final ModConfigSpec.BooleanValue SHOW_SPEED_HUD, SHOW_AIR_TIME, SHOW_TRAIL_HINTS, COMPACT_TRAIL_HINTS;
    public static final ModConfigSpec.BooleanValue MUD_EFFECTS, ROOST_PARTICLES, SPEED_LINES;
    public static final ModConfigSpec.DoubleValue ROOST_DENSITY, SPEED_LINE_STRENGTH, SPEED_FOV_STRENGTH;
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
        STEERING_GRIP = number(b, "steeringGripDemand", "How much of the cornering grip full steering asks for (0.78 = planted; above ~0.95 the rear starts sliding by itself).", 0.78, 0.5, 1.1);
        KEYBOARD_STEER_RAMP = number(b, "keyboardSteerRamp", "Optional keyboard steering ramp in seconds. Default 0 reacts immediately.", 0, 0, 0.5);
        AIR_ROTATION_SENSITIVITY = number(b, "airRotationSensitivity", "How fast the bike spins in the air at full stick (1 = default).", 1, .3, 1.6);
        AIR_ROTATION_DEADZONE = number(b, "airRotationDeadzone", "Stick deflection that is ignored for spins in the air (a brushed stick must not turn the bike).", .35, .1, .7);
        RISK_REWARD = b.comment("Experimental risk & reward: landing mid-trick or clearly sideways is a crash. Off = casual, forgiving landings.")
                .translation("descentmtb.config.experimentalRiskReward").define("experimentalRiskReward", true);
        WALL_RIDES = b.translation("descentmtb.config.wallRides").define("wallRides", true);
        WALL_RIDE_SPEED = number(b, "wallRideMinSpeedKmh", "Minimum speed along the wall needed to start a wallride after a jump.", 28.8, 18, 60);
        TRICK_BANNER = b.translation("descentmtb.config.trickBanner").define("trickBanner", true);
        b.pop();

        b.push("camera");
        CAMERA_MODE = b.comment("Riding camera (V cycles it): FIRST_PERSON helmet, SECOND_PERSON from ahead, THIRD_PERSON chase, DRONE far chase.")
                .translation("descentmtb.config.cameraMode")
                .defineEnum("mode", BikeCamera.Mode.FIRST_PERSON);
        CAMERA_SMOOTHING = number(b, "cameraSmoothing", "Camera lag multiplier for every mode: 0 = rigidly attached, 1 = the old lag, lower feels more responsive.", 0.6, 0, 2);
        CAMERA_ORBIT_RETURN = number(b, "cameraOrbitReturn", "Seconds after the last mouse movement before an orbited camera springs back behind the rider.", 1.5, 0.2, 5);
        CAM_FIRST_SMOOTH = number(b, "camFirstSmooth", "Helmet camera: how much the view lags the head, in seconds (before the multiplier).", 0.05, 0, 0.2);
        CAM_SECOND_DIST = number(b, "camSecondDistance", "Second person camera: metres in front of the rider.", 3.5, 1.5, 10);
        CAM_SECOND_HEIGHT = number(b, "camSecondHeight", "Second person camera: height above the rider, metres.", 1.4, 0, 5);
        CAM_SECOND_SMOOTH = number(b, "camSecondSmooth", "Second person camera: seconds it takes to swing in front of the direction of travel (before the multiplier).", 0.25, 0, 2);
        CAM_THIRD_DIST = number(b, "camThirdDistance", "Third person camera: metres behind the rider.", 3.5, 1.5, 10);
        CAM_THIRD_HEIGHT = number(b, "camThirdHeight", "Third person camera: height above the rider, metres.", 1.6, 0, 5);
        CAM_THIRD_SMOOTH = number(b, "camThirdSmooth", "Third person camera: seconds it takes to follow the direction of travel (before the multiplier).", 0.16, 0, 2);
        CAM_DRONE_DIST = number(b, "camDroneDistance", "Drone camera: metres behind the rider.", 8, 3, 25);
        CAM_DRONE_HEIGHT = number(b, "camDroneHeight", "Drone camera: height above the rider, metres.", 4.5, 1, 15);
        CAM_DRONE_SMOOTH = number(b, "camDroneSmooth", "Drone camera: seconds it takes to follow the direction of travel (before the multiplier).", 0.5, 0, 3);
        b.pop();

        // ---- tricks (keyboard scheme; tricks milestone) ----
        b.push("tricks");
        TRICK_KEY_SCHEME = b.comment("Keyboard trick keys. INDEPENDENT: one key per trick (I J K L O U by default, see Controls), so the left hand keeps the arrows for rotation and lean and tricks chain freely. CLASSIC: hold C and press arrows.")
                .translation("descentmtb.config.trickKeyScheme")
                .defineEnum("trickKeyScheme", TrickKeyScheme.INDEPENDENT);
        b.pop();
        b.push("sound");
        BIKE_SOUND_VOLUME = number(b, "bikeSoundVolume", "Overall volume of the bike sounds (freehub, wind, tyres, suspension, scream, tricks). 0.6 = default.", 0.6, 0.0, 1.5);
        HUB_SOUND = b.comment("Freehub clicks and buzz while coasting.").translation("descentmtb.config.hubSound").define("hubSound", true);
        WIND_SOUND = b.comment("Wind rushing past the rider (speed and air).").translation("descentmtb.config.windSound").define("windSound", true);
        SCREAM_SOUND = b.comment("The rider screams before a crash that cannot be avoided.").translation("descentmtb.config.screamSound").define("screamSound", true);
        RIDER_VOICE = b.comment("Rider voice sounds (screams, bails, landings): MALE, FEMALE or OFF.")
                .translation("descentmtb.config.riderVoice")
                .defineEnum("riderVoice", RiderVoice.MALE);
        b.pop();
        b.push("hud");
        SHOW_SPEED_HUD = b.comment("Show speed and motorbike revs while riding.").define("showSpeed", true);
        SHOW_AIR_TIME = b.comment("Show the air-time line above the speed display.").define("showAirTime", true);
        SHOW_TRAIL_HINTS = b.comment("Show the active trail-tool mode and settings above the hotbar.").define("showTrailHints", true);
        COMPACT_TRAIL_HINTS = b.comment("Keep trail hints compact; hold Shift for the full instructions.").define("compactTrailHints", true);
        b.pop();
        b.push("immersion");
        MUD_EFFECTS = b.comment("Mud accumulation on the bike only; the rider view stays clear.").define("mudEffects",true);
        ROOST_PARTICLES = b.comment("Dirt spray when braking or sliding on loose surfaces.").define("roostParticles",true);
        SPEED_LINES = b.comment("Peripheral helmet camera streaks above 28 km/h.").define("speedLines",true);
        ROOST_DENSITY = number(b,"roostDensity","Dirt spray particle density. 0 disables particles.",1,0,2);
        SPEED_LINE_STRENGTH = number(b,"speedLineStrength","Opacity of the peripheral speed effect.",.5,0,1);
        SPEED_FOV_STRENGTH = number(b,"speedFovStrength","Speed-dependent FOV change; helmet camera base angle is preserved.",1,0,2);
        b.pop();
        SPEC = b.build();
    }

    private static ModConfigSpec.DoubleValue number(ModConfigSpec.Builder b, String key, String comment, double value, double min, double max) {
        return b.comment(comment).translation("descentmtb.config."+key).defineInRange(key, value, min, max);
    }

    /** Copies the settings into the shared physics parameters (cheap; done every tick). */
    static void apply(MountainBikeEntity bike) {
        if (!SPEC.isLoaded()) return;
        BikeParams p = bike.params();
        boolean ski = bike.bikeType().ski();
        // skis: the pair's own length / sidecut / weight are the defaults (SkiPhysics.tune)
        BikeParams defaults = ski ? bike.defaultParams() : bike.bikeType().params();
        p.manualAssist = MANUAL_ASSIST.get() ? defaults.manualAssist * MANUAL_ASSIST_STRENGTH.get() : 0;
        p.landingAssistRate = LANDING_ASSIST.get() ? defaults.landingAssistRate : 0;
        double tol = BAIL_TOLERANCE.get();
        p.bailPitchError = Math.min(Math.toRadians(175), defaults.bailPitchError * tol);
        p.bailYawError = Math.min(Math.toRadians(175), defaults.bailYawError * tol);
        p.bailImpactSpeed = defaults.bailImpactSpeed * tol;
        p.crashSpeed = defaults.crashSpeed * tol;
        p.wallCrashSpeed = defaults.wallCrashSpeed * tol;
        p.pedalPower = PEDAL_POWER.get(); p.pedalMaxForce = PEDAL_FORCE.get();
        p.pedalSpinOut = PEDAL_SPEED.get() / 3.6;
        p.brakeForce = defaults.brakeForce * BRAKE_STRENGTH.get();
        p.manualTargetPitch = Math.toRadians(MANUAL_ANGLE.get());
        p.airControlResponse = defaults.airControlResponse / AIR_CONTROL.get();
        p.airAlignAssist = AIR_RECOVERY.get();
        p.flipRate = defaults.flipRate * FLIP_RATE.get(); p.spinRate = defaults.spinRate * SPIN_RATE.get();
        p.wallRides = WALL_RIDES.get();
        p.wallRideMinSpeed = WALL_RIDE_SPEED.get() / 3.6;
        p.steerGripDemand = STEERING_GRIP.get();
        p.airSpinSensitivity = AIR_ROTATION_SENSITIVITY.get();
        p.airSpinDeadzone = AIR_ROTATION_DEADZONE.get();
        p.riskReward = RISK_REWARD.get();
        if (ski) {
            // no tyres, pedals, brakes or wallrides on skis: the pole push, hockey stop and edges are SkiPhysics'
            p.pedalPower = defaults.pedalPower; p.pedalMaxForce = defaults.pedalMaxForce;
            p.pedalSpinOut = defaults.pedalSpinOut;
            p.brakeForce = defaults.brakeForce;
            p.wallRides = false;
            return;
        }
        com.descentmtb.physics.BikeTuning.apply(p, defaults, bike.frontPsi(), bike.rearPsi(), bike.forkPsi(), PRESSURE_EFFECT.get());
        if (bike.bikeType().motor()) com.descentmtb.custom.MotoTuning.apply(p, defaults, bike.moto());
    }

    private ClientConfig() {}
}
