package com.descentmtb.ski;

import com.descentmtb.physics.Terrain;

/**
 * How a ski base and its steel edges behave on each {@link Terrain.Surface}: this replaces the tyre grip and rolling
 * resistance when {@link com.descentmtb.physics.BikeParams#ski} is set.
 *
 * <ul>
 *   <li><b>glide</b> - longitudinal resistance coefficient μ of a gliding ski (snow 0.03-0.05 for a waxed base).</li>
 *   <li><b>edge</b> - lateral force / normal load the edges hold before they skid. On snow the edge cuts a platform,
 *       so it holds far more than Coulomb friction would: tan(edge angle) of a hard carve, ≈2.2. Ice barely holds.</li>
 *   <li><b>brake</b> - μ of a hockey stop / snowplough (skis across the direction of travel).</li>
 *   <li><b>grinds</b> - bare ground (rock, dirt, grass ...): the base scrapes, it brakes very hard and the skier
 *       falls after a while (see the scrape meter in {@link com.descentmtb.physics.BikeSim}).</li>
 * </ul>
 * Wood is a park box / rail: a low-friction slide that does not scrape.
 */
public final class SkiSurface {
    /** μ of a gliding ski. */
    public static double glide(Terrain.Surface s) {
        return switch (s) {
            case SNOW -> 0.045;
            case ICE -> 0.025;
            case AIRBAG -> 0.10;
            case WOOD -> 0.07;
            case GRASS -> 0.38;
            case TRAIL -> 0.45;
            case DIRT -> 0.50;
            case ROCK -> 0.55;
            case MUD -> 0.55;
            case GRAVEL -> 0.60;
            case SAND -> 0.65;
        };
    }

    /** Lateral hold of the edges (lateral force per unit normal load). */
    public static double edge(Terrain.Surface s) {
        return switch (s) {
            case SNOW -> 2.2;
            case ICE -> 0.55;
            case AIRBAG -> 1.0;
            case WOOD -> 0.5;
            case ROCK, DIRT, TRAIL -> 0.8;
            case GRASS, GRAVEL, SAND -> 0.7;
            case MUD -> 0.6;
        };
    }

    /** μ of a hockey stop / snowplough on top of the glide. */
    public static double brake(Terrain.Surface s) {
        return switch (s) {
            case SNOW -> 0.70;
            case ICE -> 0.20;
            case AIRBAG -> 0.50;
            case WOOD -> 0.25;
            default -> 0.20;          // already grinding
        };
    }

    /** True for bare ground: the base scrapes over it. */
    public static boolean grinds(Terrain.Surface s) {
        return switch (s) {
            case SNOW, ICE, AIRBAG, WOOD -> false;
            default -> true;
        };
    }

    private SkiSurface() {}
}
