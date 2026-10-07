package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteMergeTest {
    @Test void keepsOrderAndDropsDuplicatesFirstWins() {
        var out = RouteMerge.merge(List.of(List.of("a1", "b1"), List.of("a2", "c1")),
                (x, y) -> x.charAt(0) == y.charAt(0), r -> true, 10);
        assertEquals(List.of("a1", "b1", "c1"), out);
    }

    @Test void preferredComeFirstThenTheRestInOrder() {
        var out = RouteMerge.merge(List.of(List.of("x1", "y1", "x2", "y2")), (a, b) -> false, r -> r.startsWith("y"), 10);
        assertEquals(List.of("y1", "y2", "x1", "x2"), out);
    }

    @Test void capKeepsTheFirstOnes() {
        var out = RouteMerge.merge(List.of(List.of("1", "2", "3", "4")), (a, b) -> false, r -> true, 2);
        assertEquals(List.of("1", "2"), out);
        assertEquals(List.of(), RouteMerge.merge(List.of(List.of("1")), (a, b) -> false, r -> true, 0));
    }

    @Test void emptyInputGivesEmpty() {
        assertTrue(RouteMerge.<String>merge(List.of(), (a, b) -> false, r -> true, 5).isEmpty());
    }
}
