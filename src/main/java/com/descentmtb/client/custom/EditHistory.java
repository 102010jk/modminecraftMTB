package com.descentmtb.client.custom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Undo / redo of the value edited in the workshop screen. Keeps the current value; {@link #record} replaces it and
 * remembers the old one. Edits that come in a stream (dragging a colour, typing a hex code, a slider) pass the same
 * non-null gesture key and collapse into ONE undo step until {@link #endGesture()} (mouse released, field left).
 * Pure Java, unit tested.
 */
public final class EditHistory<T> {
    private final Deque<T> undo = new ArrayDeque<>();
    private final Deque<T> redo = new ArrayDeque<>();
    private final int limit;
    private T current;
    private Object gesture;

    public EditHistory(T initial, int limit) {
        this.current = initial;
        this.limit = Math.max(1, limit);
    }

    public T current() {
        return current;
    }

    /**
     * Makes {@code next} the current value; returns false (and does nothing) when it equals the current one. With a
     * non-null {@code gestureKey} equal to the one of the previous call (and no {@link #endGesture} in between) the
     * old value is not stacked again.
     */
    public boolean record(T next, Object gestureKey) {
        if (Objects.equals(next, current)) {
            return false;
        }
        boolean merge = gestureKey != null && gestureKey.equals(gesture) && !undo.isEmpty();
        if (!merge) {
            undo.push(current);
            while (undo.size() > limit) {
                undo.removeLast();
            }
        }
        gesture = gestureKey;
        redo.clear();
        current = next;
        return true;
    }

    public boolean record(T next) {
        return record(next, null);
    }

    public void endGesture() {
        gesture = null;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Steps back; returns the new current value, or the unchanged one when there is nothing to undo. */
    public T undo() {
        gesture = null;
        if (!undo.isEmpty()) {
            redo.push(current);
            current = undo.pop();
        }
        return current;
    }

    public T redo() {
        gesture = null;
        if (!redo.isEmpty()) {
            undo.push(current);
            current = redo.pop();
        }
        return current;
    }

    /** Forgets all steps and starts again from {@code value}. */
    public void reset(T value) {
        undo.clear();
        redo.clear();
        gesture = null;
        current = value;
    }
}
