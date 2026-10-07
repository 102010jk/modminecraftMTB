package com.descentmtb.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Combines lists of routes for display (pure, unit-tested): no duplicates, stable order, bounded. */
public final class RouteMerge {
    private RouteMerge() {}

    /**
     * Every element of the lists in order, each once ({@code duplicate} says when two are the same route: the first one
     * wins), those that are {@code preferred} moved to the front (otherwise keeping their order), at most {@code cap}.
     */
    public static <R> List<R> merge(List<? extends List<? extends R>> lists, BiPredicate<R, R> duplicate, Predicate<R> preferred, int cap) {
        List<R> unique = new ArrayList<>();
        for (List<? extends R> list : lists) {
            for (R route : list) {
                boolean seen = false;
                for (R other : unique) if (duplicate.test(other, route)) { seen = true; break; }
                if (!seen) unique.add(route);
            }
        }
        List<R> out = new ArrayList<>(unique.size());
        for (R route : unique) if (preferred.test(route)) out.add(route);
        for (R route : unique) if (!preferred.test(route)) out.add(route);
        return out.size() > cap ? new ArrayList<>(out.subList(0, Math.max(0, cap))) : out;
    }
}
