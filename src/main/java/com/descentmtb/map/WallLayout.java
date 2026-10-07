package com.descentmtb.map;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Which item frames of a wall show one big trail map between them (pure, unit-tested; no Minecraft types). Frames are
 * cells of a flat grid seen from the front of the wall: {@code u} grows to the viewer's right, {@code v} grows upwards.
 * A frame's tile is the largest filled rectangle that contains it; where a group is not itself a rectangle the group is
 * cut up greedily (largest rectangle first, ties to the lowest then leftmost one), the same way for every frame of the
 * group, so the tiles never overlap and every frame agrees on which rectangle it belongs to.
 */
public final class WallLayout {
    /** Frames per side of one big map: 8 frames of 128 pixels is a 1024 px texture. */
    public static final int MAX_SIDE = 8;
    /** Pixels the big map gets per frame (the vanilla map resolution), and the most one side of it may have. */
    public static final int PIXELS_PER_FRAME = 128, MAX_TEXTURE = 1024;

    public record Cell(int u, int v) {}

    /** A filled rectangle of frames: lower left cell (u0, v0), {@code w} wide, {@code h} tall. */
    public record Rect(int u0, int v0, int w, int h) {
        public boolean contains(int u, int v) { return u >= u0 && u < u0 + w && v >= v0 && v < v0 + h; }
        /** Column of a frame counted from the viewer's left. */
        public int column(int u) { return u - u0; }
        /** Row of a frame counted from the top. */
        public int rowFromTop(int v) { return v0 + h - 1 - v; }
        public int count() { return w * h; }
    }

    private WallLayout() {}

    /** Pixels along one side of the big map: a frame's worth per frame, never more than {@link #MAX_TEXTURE}. */
    public static int textureSide(int frames) {
        return Math.max(1, Math.min(MAX_TEXTURE, PIXELS_PER_FRAME * frames));
    }

    /**
     * The rectangle {@code start} belongs to among {@code cells} (frames holding a trail map): at most
     * {@code maxW} by {@code maxH} cells, always containing {@code start}. Only cells connected to {@code start} within
     * {@link #MAX_SIDE} cells of it count. A start that is not itself in {@code cells} is a rectangle of one.
     */
    public static Rect around(Set<Cell> cells, Cell start, int maxW, int maxH) {
        maxW = Math.max(1, maxW);
        maxH = Math.max(1, maxH);
        Rect alone = new Rect(start.u(), start.v(), 1, 1);
        if (!cells.contains(start)) return alone;
        Set<Cell> remaining = component(cells, start);
        while (!remaining.isEmpty()) {
            Rect best = largest(remaining, maxW, maxH);
            if (best == null) return alone;
            if (best.contains(start.u(), start.v())) return best;
            for (int u = best.u0(); u < best.u0() + best.w(); u++)
                for (int v = best.v0(); v < best.v0() + best.h(); v++) remaining.remove(new Cell(u, v));
        }
        return alone;
    }

    /** The cells edge-connected to {@code start}, no further than {@link #MAX_SIDE} cells away from it on either axis. */
    static Set<Cell> component(Set<Cell> cells, Cell start) {
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int[][] step = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (int[] s : step) {
                Cell n = new Cell(c.u() + s[0], c.v() + s[1]);
                if (Math.abs(n.u() - start.u()) > MAX_SIDE || Math.abs(n.v() - start.v()) > MAX_SIDE) continue;
                if (cells.contains(n) && seen.add(n)) queue.add(n);
            }
        }
        return seen;
    }

    /** Largest rectangle within {@code cells}: by area, then lowest, then leftmost, then widest. {@code null} when empty. */
    static Rect largest(Set<Cell> cells, int maxW, int maxH) {
        Rect best = null;
        for (Cell origin : cells) {
            int width = maxW;
            for (int h = 1; h <= maxH; h++) {
                width = Math.min(width, run(cells, origin.u(), origin.v() + h - 1, width));
                if (width == 0) break;
                Rect candidate = new Rect(origin.u(), origin.v(), width, h);
                if (better(candidate, best)) best = candidate;
            }
        }
        return best;
    }

    private static int run(Set<Cell> cells, int u, int v, int limit) {
        int n = 0;
        while (n < limit && cells.contains(new Cell(u + n, v))) n++;
        return n;
    }

    private static boolean better(Rect a, Rect b) {
        if (b == null) return true;
        if (a.count() != b.count()) return a.count() > b.count();
        if (a.v0() != b.v0()) return a.v0() < b.v0();
        if (a.u0() != b.u0()) return a.u0() < b.u0();
        return a.w() > b.w();
    }
}
