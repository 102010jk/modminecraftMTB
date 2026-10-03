package com.descentmtb.trail;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure (Minecraft-free) vertical layering of a shaped surface column.
 *
 * <p>A shaped surface has four absolute corner heights {NW, NE, SW, SE}. When it crosses block boundaries it is
 * stored as a stack of blocks, each holding the same plane relative to its own Y. The physics clamps every layer
 * to 0..1, so the stack behaves like the single continuous plane - there is exactly one implementation of this
 * layering, used by the planners, by the hand sculpting and by the tests.
 */
public final class ColumnShaper {
    /** Thickness (blocks) of a wooden deck below its upper surface. */
    public static final double DECK_THICKNESS = .14;
    /** Safety limit: how many layers a single column may need. */
    public static final int MAX_LAYERS = 9;

    /** One block of the stack: its Y and the corner heights relative to that Y. */
    public record Layer(int y, double[] heights) {}

    public record Layers(int bottom, int top, List<Layer> layers) {}

    /**
     * @param abs  absolute heights of the four corners
     * @param deck true for a wooden deck (it also reaches {@link #DECK_THICKNESS} below its surface)
     * @throws IllegalArgumentException when the corners differ by more than {@link #MAX_LAYERS} blocks
     */
    public static Layers layers(double[] abs, boolean deck) {
        double min = Math.min(Math.min(abs[0], abs[1]), Math.min(abs[2], abs[3]));
        double max = Math.max(Math.max(abs[0], abs[1]), Math.max(abs[2], abs[3]));
        int bottom = (int) Math.floor(min - (deck ? DECK_THICKNESS : 0) - .001);
        int top = (int) Math.ceil(max) - 1;
        if (top - bottom + 1 > MAX_LAYERS) {
            throw new IllegalArgumentException("Příliš prudký přechod; zmenši sílu nebo nejprve vyhlaď terén");
        }
        List<Layer> list = new ArrayList<>();
        for (int y = bottom; y <= top; y++) {
            double[] local = new double[4];
            for (int i = 0; i < 4; i++) {
                local[i] = abs[i] - y;
            }
            list.add(new Layer(y, local));
        }
        return new Layers(bottom, top, list);
    }

    /**
     * Absolute corner heights stored in a layer: {@code y + local}. Every layer of the same column yields the
     * same corners, so reading any one of them (preferably the top one) is enough.
     */
    public static double[] absolute(int y, double[] local) {
        return new double[]{y + local[0], y + local[1], y + local[2], y + local[3]};
    }

    /** Bilinear height at in-block coordinates, as the physics sees it for the whole column (absolute). */
    public static double surfaceAt(double[] abs, double fx, double fz) {
        return (abs[0] * (1 - fx) + abs[1] * fx) * (1 - fz) + (abs[2] * (1 - fx) + abs[3] * fx) * fz;
    }

    /** The height the stacked layers produce at (fx, fz): the highest clamped layer top. */
    public static double stackedSurfaceAt(Layers layers, double fx, double fz) {
        double best = Double.NEGATIVE_INFINITY;
        for (Layer layer : layers.layers()) {
            double local = surfaceAt(layer.heights(), fx, fz);
            double clamped = Math.max(0, Math.min(1, local));
            if (local > 0) {
                best = Math.max(best, layer.y() + clamped);
            }
        }
        return best;
    }

    /** A vertex of the block grid (integer world coordinates of a block corner). */
    public record Vertex(int x, int z) {}

    /**
     * The vertices a click at in-block position (fx, fz) refers to: a corner zone picks that corner, an edge
     * zone picks both corners of the edge, the middle picks all four corners of the block.
     */
    public static List<Vertex> pickVertices(int bx, int bz, double fx, double fz) {
        int zoneX = zone(fx), zoneZ = zone(fz);
        List<Vertex> out = new ArrayList<>();
        for (int vx : zoneX == 1 ? new int[]{0, 1} : new int[]{zoneX == 2 ? 1 : 0}) {
            for (int vz : zoneZ == 1 ? new int[]{0, 1} : new int[]{zoneZ == 2 ? 1 : 0}) {
                out.add(new Vertex(bx + vx, bz + vz));
            }
        }
        return out;
    }

    /** 0 = near the low edge, 2 = near the high edge, 1 = middle. */
    private static int zone(double f) {
        return f < .3 ? 0 : f > .7 ? 2 : 1;
    }

    private ColumnShaper() {}
}
