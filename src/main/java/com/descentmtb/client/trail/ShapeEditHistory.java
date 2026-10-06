package com.descentmtb.client.trail;

import java.util.ArrayDeque;

/** Undo/redo of the editor's unpublished draft; applying still makes one server-side edit. */
final class ShapeEditHistory {
    record State(int nw, int ne, int sw, int se, boolean deck, boolean material) {
        State(int[] heights, boolean deck, boolean material) {
            this(heights[0], heights[1], heights[2], heights[3], deck, material);
        }
        int[] heights() { return new int[]{nw, ne, sw, se}; }
    }

    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private static final int LIMIT = 32;

    void record(State before, State after) {
        if (before.equals(after)) return;
        undo.addLast(before);
        if (undo.size() > LIMIT) undo.removeFirst();
        redo.clear();
    }

    State undo(State current) {
        if (undo.isEmpty()) return current;
        redo.addLast(current);
        return undo.removeLast();
    }

    State redo(State current) {
        if (redo.isEmpty()) return current;
        undo.addLast(current);
        return redo.removeLast();
    }
}
