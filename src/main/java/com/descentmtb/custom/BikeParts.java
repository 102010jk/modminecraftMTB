package com.descentmtb.custom;

import java.util.List;
import java.util.Locale;

/**
 * The catalogue of everything a bike can be built from. Real-world inspired parts: the fork / shock models and
 * their stock finishes follow the actual 2024-26 product lines (Kashima-coated "Factory" vs black "Performance"
 * stanchions, RockShox "Ultimate" colours, coil shocks with coloured springs, anodised small parts, tan-wall
 * tyres). Pure Java - shared by server validation, rendering and the workshop screen.
 *
 * <p>Every enum is saved BY NAME, so new entries can be added at any position later.
 */
public final class BikeParts {

    // ------------------------------------------------------------------ colours

    /** Colours of anodised aluminium parts (rims, hubs, pedals, stems, brake levers, bells), as sold. */
    public enum Anodized {
        BLACK(0x1E1F22), SILVER(0xC9CDD2), GUNMETAL(0x4A4E55), GOLD(0xD9A93A), RED(0xC8312B), ORANGE(0xE8752A),
        BLUE(0x2F6FD0), TURQUOISE(0x23B3B0), PURPLE(0x7B49B8), PINK(0xE07BA6), GREEN(0x4E9B3C), BRONZE(0x9C6B3A);

        public final int rgb;

        Anodized(int rgb) {
            this.rgb = rgb;
        }

        public String key() {
            return "descentmtb.custom.anodized." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Ready-made frame paints (the workshop also offers a free colour picker). */
    public static final List<Integer> FRAME_PRESETS = List.of(
            0x1F8A8F, 0xC73A2E, 0x23262B, 0xF2F0EA, 0x2F6FD0, 0xE8752A, 0x5C8A3A, 0x7B49B8, 0xD9A93A, 0x8A8F96,
            0xE85D9B, 0x3B3F2E);

    /** Frame paint finish - how the colour is shaded on the model and icon. */
    public enum Finish {
        GLOSS, MATTE, METALLIC, RAW;

        public String key() {
            return "descentmtb.custom.finish." + name().toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------ frame

    /** Frame silhouettes; each bike type has its own set (see {@link #shapesFor}). */
    public enum FrameShape {
        // full suspension (enduro)
        ENDURO_CLASSIC(true), ENDURO_HIGH_PIVOT(true), ENDURO_LOW_SLUNG(true),
        // hardtail (dirt jump)
        DJ_CLASSIC(false), DJ_STRAIGHT(false), DJ_CURVED(false);

        public final boolean fullSuspension;

        FrameShape(boolean fullSuspension) {
            this.fullSuspension = fullSuspension;
        }

        public String key() {
            return "descentmtb.custom.shape." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static List<FrameShape> shapesFor(boolean fullSuspension) {
        return java.util.Arrays.stream(FrameShape.values()).filter(s -> s.fullSuspension == fullSuspension).toList();
    }

    // ------------------------------------------------------------------ suspension

    /** Stanchion / shock-body coating. */
    public enum Coating {
        KASHIMA(0xC9A24B), BLACK(0x202226), SILVER(0xB8BEC6), GOLD_TI(0xD8B45A);

        public final int rgb;

        Coating(int rgb) {
            this.rgb = rgb;
        }
    }

    /** Forks. lowerColors = the stock colours of the lower legs for that model. */
    public enum Fork {
        FOX_38_FACTORY("Fox 38 Factory", Coating.KASHIMA, 170, true, 0x16171A, 0xE8652A, 0x2A2C30),
        FOX_36_FACTORY("Fox 36 Factory", Coating.KASHIMA, 160, true, 0x16171A, 0xE8652A),
        FOX_38_PERFORMANCE("Fox 38 Performance Elite", Coating.BLACK, 170, true, 0x16171A, 0x2A2C30),
        FOX_36_RHYTHM("Fox 36 Rhythm", Coating.BLACK, 150, true, 0x2A2C30),
        ZEB_ULTIMATE("RockShox ZEB Ultimate", Coating.BLACK, 170, true, 0x15161A, 0xC0242C, 0x8E949C),
        LYRIK_ULTIMATE("RockShox Lyrik Ultimate", Coating.BLACK, 160, true, 0x15161A, 0xC0242C, 0x8E949C),
        BOMBER_Z1("Marzocchi Bomber Z1", Coating.BLACK, 170, true, 0xB3202A, 0x15161A),
        OHLINS_RXF38("Öhlins RXF38", Coating.GOLD_TI, 170, true, 0x15161A),
        DVO_ONYX("DVO Onyx D1", Coating.SILVER, 170, true, 0x3E8E3A),
        DJ_PIKE("RockShox Pike DJ", Coating.BLACK, 100, false, 0x15161A, 0xC0242C),
        DJ_RIGID("Rigid steel DJ fork", Coating.BLACK, 0, false, 0x23262B, 0xC9CDD2);

        public final String displayName;
        public final Coating stanchions;
        public final int travelMm;
        /** False = only for the dirt-jump hardtail (short travel). */
        public final boolean enduro;
        public final int[] lowerColors;

        Fork(String displayName, Coating stanchions, int travelMm, boolean enduro, int... lowerColors) {
            this.displayName = displayName;
            this.stanchions = stanchions;
            this.travelMm = travelMm;
            this.enduro = enduro;
            this.lowerColors = lowerColors;
        }
    }

    /** Rear shocks (full-suspension frames only). springColor = coil colour, -1 for air shocks. */
    public enum Shock {
        FLOAT_X2_FACTORY("Fox Float X2 Factory", Coating.KASHIMA, -1, 0x16171A),
        FLOAT_X_PERFORMANCE("Fox Float X Performance", Coating.BLACK, -1, 0x16171A),
        DHX2_FACTORY("Fox DHX2 Factory (coil)", Coating.KASHIMA, 0xE8652A, 0x16171A),
        SUPER_DELUXE_ULTIMATE("RockShox Super Deluxe Ultimate", Coating.BLACK, -1, 0x15161A),
        VIVID_COIL("RockShox Vivid Coil", Coating.BLACK, 0xC0242C, 0x15161A),
        TTX22("Öhlins TTX22", Coating.GOLD_TI, 0xE3C23A, 0x15161A);

        public final String displayName;
        public final Coating body;
        public final int springColor;
        public final int reservoirColor;

        Shock(String displayName, Coating body, int springColor, int reservoirColor) {
            this.displayName = displayName;
            this.body = body;
            this.springColor = springColor;
            this.reservoirColor = reservoirColor;
        }

        public boolean coil() {
            return springColor >= 0;
        }
    }

    // ------------------------------------------------------------------ wheels and cockpit

    public enum TyreWall {
        BLACK(0x1C1C1F), TAN(0xB7884E), GUM(0x8A5A2E);

        public final int rgb;

        TyreWall(int rgb) {
            this.rgb = rgb;
        }

        public String key() {
            return "descentmtb.custom.tyre." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Saddle / grip colours (rubber, leather, fabric). */
    public enum Soft {
        BLACK(0x1A1A1D), GREY(0x5A5D63), WHITE(0xE9E7E1), BROWN(0x6B4426), RED(0xB5302A), BLUE(0x2A5FB0),
        GREEN(0x3D7A34), ORANGE(0xD9662A), PINK(0xD86C98), YELLOW(0xE3C23A);

        public final int rgb;

        Soft(int rgb) {
            this.rgb = rgb;
        }

        public String key() {
            return "descentmtb.custom.soft." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Brakes {
        SHIMANO_SAINT("Shimano Saint", 0x2A2C30), SHIMANO_XT("Shimano XT", 0x7C8088), SRAM_CODE("SRAM Code Silver", 0xC9CDD2),
        SRAM_MAVEN("SRAM Maven Ultimate", 0x1E1F22), HOPE_TECH4("Hope Tech 4 (anodised)", -1);

        public final String displayName;
        /** Fixed colour, or -1 = uses the anodised colour chosen in the build. */
        public final int rgb;

        Brakes(String displayName, int rgb) {
            this.displayName = displayName;
            this.rgb = rgb;
        }
    }

    public enum Bars {
        CARBON(0x26272B), BLACK_ALLOY(0x1E1F22), RAW_ALLOY(0xB9BEC4), GOLD(0xD9A93A), RED(0xC8312B), BLUE(0x2F6FD0);

        public final int rgb;

        Bars(int rgb) {
            this.rgb = rgb;
        }

        public String key() {
            return "descentmtb.custom.bars." + name().toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------ accessories

    /** Bell types; each plays its own sound (key: bell). */
    public enum Bell {
        NONE, DING, MINI, CLASSIC, AIR_HORN, RUBBER_DUCK;

        public String key() {
            return "descentmtb.custom.bell." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum LightColor {
        WHITE(0xF4F2E8), WARM(0xFFD08A), BLUE(0x9CC8FF), RED(0xFF3B30), GREEN(0x5BE37D);

        public final int rgb;

        LightColor(int rgb) {
            this.rgb = rgb;
        }

        public String key() {
            return "descentmtb.custom.light." + name().toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------ stickers

    /** Frame tubes a sticker can sit on (positions are along the tube, 0 = start, 1 = end). */
    public enum Tube {
        TOP, DOWN, SEAT, SEAT_STAY, CHAIN_STAY, HEAD, FORK_LEG;

        public String key() {
            return "descentmtb.custom.tube." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Sticker designs; the art lives at textures/sticker/&lt;lowercase name&gt;.png (32x16 or 16x16). */
    public enum StickerDesign {
        LOGO_DESCENT, FLAME, LIGHTNING, STRIPES, STAR, SKULL, HEART, CHECKER, NUMBER_PLATE, MOUNTAIN, PAW, RACING_7;

        public String texture() {
            return "textures/sticker/" + name().toLowerCase(Locale.ROOT) + ".png";
        }

        public String key() {
            return "descentmtb.custom.sticker." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static <E extends Enum<E>> E byName(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return fallback;
    }

    private BikeParts() {}
}
