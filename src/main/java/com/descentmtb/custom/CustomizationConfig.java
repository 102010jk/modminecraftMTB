package com.descentmtb.custom;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side switches of the bike customisation. */
public final class CustomizationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DYNAMIC_BIKE_LIGHTS;

    static {
        var b = new ModConfigSpec.Builder();
        b.push("customization");
        DYNAMIC_BIKE_LIGHTS = b.comment("A ridden bike with a front / rear light really lights up its surroundings in the dark "
                        + "(it moves invisible light blocks through empty air next to the bike).")
                .define("dynamicBikeLights", true);
        b.pop();
        SPEC = b.build();
    }

    /** The switch; true while the config is not loaded yet (and in dev tests that run before it). */
    public static boolean dynamicBikeLights() {
        try {
            return DYNAMIC_BIKE_LIGHTS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private CustomizationConfig() {}
}
