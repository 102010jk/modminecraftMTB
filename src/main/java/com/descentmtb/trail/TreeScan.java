package com.descentmtb.trail;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds the blocks of a whole tree, the way a lumberjack takes it: the logs of one trunk (with its branches), the leaves
 * that belong to it, and the vines hanging from it. The search only needs to know what kind of block is where
 * ({@link World}), so it is tested on a small tree built in memory.
 *
 * <p>A cluster of logs is a <em>natural tree</em> only when it stands on the ground and has natural leaves against it (leaves
 * a player placed, and logs without leaves, such as a cabin, are not trees and are left alone). A leaf belongs to the tree when
 * it is reached from a log through leaves that get one step further away each time, which is exactly what the leaf's
 * {@code distance} property says: a tree does not take the leaves of another tree that are closer to that one.
 */
public final class TreeScan {
    /** What a block is, as far as clearing is concerned. */
    public enum Kind {
        AIR,
        /** Anything solid that is not vegetation: the ground a tree stands on, and what a clearing must not touch. */
        GROUND,
        LOG,
        /** Natural leaves: they decay when no log is near them. */
        LEAF,
        /** Leaves a player placed (they never decay). */
        PLACED_LEAF,
        VINE,
        /** Plants, snow layers and the like. */
        PLANT
    }

    /** A block position (Minecraft-free, so the search can be tested without the game). */
    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }

        Pos above() {
            return offset(0, 1, 0);
        }

        Pos below() {
            return offset(0, -1, 0);
        }

        Pos north() {
            return offset(0, 0, -1);
        }

        Pos south() {
            return offset(0, 0, 1);
        }

        Pos east() {
            return offset(1, 0, 0);
        }

        Pos west() {
            return offset(-1, 0, 0);
        }
    }

    /** The blocks to look at. */
    public interface World {
        Kind kind(Pos pos);

        /** The distance to the nearest log of a {@link Kind#LEAF} (1 = next to a log). */
        int leafDistance(Pos pos);

        /** True when the block holds data (a bee nest, say) that clearing must not lose: such a tree is left alone. */
        boolean hasData(Pos pos);
    }

    /** The most logs a tree may have; a bigger cluster is a forest grown together, not one tree. */
    public static final int MAX_LOGS = 700;
    /** The most blocks of one tree, vines included. */
    public static final int MAX_BLOCKS = 6000;

    /**
     * The blocks of the tree that has a log at {@code log}: its logs, leaves and vines.
     *
     * @return the blocks, or an empty set when that log is not part of a natural tree (or the tree holds data)
     */
    public static Set<Pos> tree(World world, Pos log) {
        Set<Pos> logs = new LinkedHashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        logs.add(log);
        queue.add(log);
        while (!queue.isEmpty()) {
            Pos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Pos next = at.offset(dx, dy, dz);
                        if ((dx != 0 || dy != 0 || dz != 0) && world.kind(next) == Kind.LOG && logs.add(next)) {
                            if (logs.size() > MAX_LOGS) {
                                return Collections.emptySet();
                            }
                            queue.add(next);
                        }
                    }
                }
            }
        }
        boolean rooted = false, leafy = false;
        for (Pos at : logs) {
            rooted |= world.kind(at.below()) == Kind.GROUND;
            for (Pos next : neighbours(at)) {
                leafy |= world.kind(next) == Kind.LEAF;
            }
        }
        if (!rooted || !leafy) {
            return Collections.emptySet();
        }

        Set<Pos> tree = new LinkedHashSet<>(logs);
        // leaves, each one step further from the logs than the one it is reached from
        ArrayDeque<Pos> frontier = new ArrayDeque<>(logs);
        java.util.Map<Pos, Integer> distance = new java.util.HashMap<>();
        logs.forEach(at -> distance.put(at, 0));
        while (!frontier.isEmpty()) {
            Pos at = frontier.poll();
            int from = distance.get(at);
            for (Pos next : neighbours(at)) {
                if (!distance.containsKey(next) && world.kind(next) == Kind.LEAF && world.leafDistance(next) == from + 1) {
                    distance.put(next, from + 1);
                    tree.add(next);
                    frontier.add(next);
                }
            }
        }
        // vines hanging from the logs and the leaves, and from each other
        ArrayDeque<Pos> vines = new ArrayDeque<>(tree);
        while (!vines.isEmpty() && tree.size() <= MAX_BLOCKS) {
            Pos at = vines.poll();
            for (Pos next : neighbours(at)) {
                if (world.kind(next) == Kind.VINE && tree.add(next)) {
                    vines.add(next);
                }
            }
        }
        for (Pos at : tree) {
            if (world.hasData(at)) {
                return Collections.emptySet();
            }
        }
        return tree;
    }

    private static Pos[] neighbours(Pos at) {
        return new Pos[]{at.above(), at.below(), at.north(), at.south(), at.east(), at.west()};
    }

    private TreeScan() {}
}
