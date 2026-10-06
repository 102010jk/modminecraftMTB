package com.descentmtb.trail;

import com.descentmtb.trail.TreeScan.Kind;
import com.descentmtb.trail.TreeScan.Pos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Taking a whole tree: logs, its own leaves and vines, and nothing that only looks like a tree. */
class TreeScanTest {
    /** A small world kept in a map; the ground is the plane y = 0 and above it is air unless something is set. */
    private static final class Fake implements TreeScan.World {
        final Map<Pos, Kind> kinds = new HashMap<>();
        final Map<Pos, Integer> distance = new HashMap<>();
        final Set<Pos> data = new HashSet<>();

        @Override
        public Kind kind(Pos pos) {
            return kinds.getOrDefault(pos, pos.y() <= 0 ? Kind.GROUND : Kind.AIR);
        }

        @Override
        public int leafDistance(Pos pos) {
            return distance.getOrDefault(pos, 7);
        }

        @Override
        public boolean hasData(Pos pos) {
            return data.contains(pos);
        }

        void log(int x, int y, int z) {
            kinds.put(new Pos(x, y, z), Kind.LOG);
        }

        void leaf(int x, int y, int z, int d) {
            kinds.put(new Pos(x, y, z), Kind.LEAF);
            distance.put(new Pos(x, y, z), d);
        }

        /** A trunk of four logs at (x, 1..4, z) with a crown of leaves 3 away at most, each with the distance to the nearest log. */
        void tree(int x, int z) {
            for (int y = 1; y <= 4; y++) {
                log(x, y, z);
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int y = 3; y <= 7; y++) {
                        int d = Math.abs(dx) + Math.abs(dz) + Math.max(0, y - 4);
                        if (d >= 1 && d <= 3) {
                            leaf(x + dx, y, z + dz, d);
                        }
                    }
                }
            }
        }

        long count(Kind kind) {
            return kinds.values().stream().filter(k -> k == kind).count();
        }
    }

    @Test
    void aWholeTreeIsFoundFromAnyOfItsLogs() {
        Fake world = new Fake();
        world.tree(0, 0);
        long logs = world.count(Kind.LOG), leaves = world.count(Kind.LEAF);
        for (int y = 1; y <= 4; y++) {
            Set<Pos> tree = TreeScan.tree(world, new Pos(0, y, 0));
            assertEquals(logs + leaves, tree.size(), "log " + y);
            assertTrue(tree.contains(new Pos(3, 3, 0)), "a leaf at the edge of the crown belongs to it");
            assertTrue(tree.contains(new Pos(0, 5, 0)), "a leaf above the top log belongs to it");
        }
    }

    @Test
    void aTreeDoesNotTakeTheLeavesOfItsNeighbour() {
        Fake world = new Fake();
        world.tree(0, 0);
        world.tree(6, 0);   // the crowns touch; each leaf knows which trunk is nearer
        Set<Pos> first = TreeScan.tree(world, new Pos(0, 1, 0));
        assertTrue(first.contains(new Pos(0, 5, 0)) && first.contains(new Pos(2, 4, 0)));
        assertFalse(first.contains(new Pos(6, 1, 0)), "not the other trunk");
        assertFalse(first.contains(new Pos(5, 4, 0)), "not a leaf one step from the other trunk");
        Set<Pos> second = TreeScan.tree(world, new Pos(6, 1, 0));
        assertEquals(first.size(), second.size());
    }

    @Test
    void aBranchThatTouchesOnlyAtACornerIsPartOfTheTree() {
        Fake world = new Fake();
        world.tree(0, 0);
        world.log(1, 5, 1);   // an acacia-like branch
        world.log(2, 6, 2);
        Set<Pos> tree = TreeScan.tree(world, new Pos(0, 1, 0));
        assertTrue(tree.contains(new Pos(1, 5, 1)) && tree.contains(new Pos(2, 6, 2)));
    }

    @Test
    void aCabinOfLogsIsNotATree() {
        Fake world = new Fake();
        for (int x = 0; x < 4; x++) {
            for (int y = 1; y <= 3; y++) {
                world.log(x, y, 0);
            }
        }
        assertTrue(TreeScan.tree(world, new Pos(1, 2, 0)).isEmpty(), "logs without leaves");
        world.kinds.put(new Pos(1, 4, 0), Kind.PLACED_LEAF);
        assertTrue(TreeScan.tree(world, new Pos(1, 2, 0)).isEmpty(), "a hedge of placed leaves is not a natural crown");
    }

    @Test
    void aFloatingLogWithLeavesIsNotATree() {
        Fake world = new Fake();
        world.log(0, 10, 0);
        world.leaf(1, 10, 0, 1);
        assertTrue(TreeScan.tree(world, new Pos(0, 10, 0)).isEmpty());
    }

    @Test
    void vinesHangingFromATreeGoWithIt() {
        Fake world = new Fake();
        world.tree(0, 0);
        for (int y = 3; y >= 1; y--) {
            world.kinds.put(new Pos(1, y, 0), Kind.VINE);   // against the trunk, and hanging down
        }
        world.kinds.put(new Pos(5, 2, 5), Kind.VINE);       // a vine somewhere else
        Set<Pos> tree = TreeScan.tree(world, new Pos(0, 1, 0));
        assertTrue(tree.contains(new Pos(1, 1, 0)) && tree.contains(new Pos(1, 2, 0)));
        assertFalse(tree.contains(new Pos(5, 2, 5)));
    }

    @Test
    void aTreeWithABeeNestIsLeftAlone() {
        Fake world = new Fake();
        world.tree(0, 0);
        world.data.add(new Pos(1, 4, 0));
        assertTrue(TreeScan.tree(world, new Pos(0, 1, 0)).isEmpty());
    }

    @Test
    void aForestGrownTogetherIsNotOneTree() {
        Fake world = new Fake();
        for (int x = 0; x < 80; x++) {
            for (int y = 1; y <= 12; y++) {
                world.log(x, y, 0);
            }
        }
        world.leaf(0, 13, 0, 1);
        assertTrue(TreeScan.tree(world, new Pos(0, 1, 0)).isEmpty());
    }
}
