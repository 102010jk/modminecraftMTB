package com.descentmtb.trick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered list of the tricks finished during one air session (take-off to landing). Pure Java, no Minecraft.
 * {@link TrickAnimation} owns one: it calls {@link #begin} at take-off, {@link #complete} whenever a trick is
 * finished (a spin that played its full rotation, a hold trick that was released and is back on the bars) and
 * {@link #finish} on landing, which produces the {@link Summary} the banner and the risk logic read.
 */
public final class ComboTracker {
    /**
     * What one air session came to.
     *
     * @param tricks         finished tricks in the order they were done (a trick may appear more than once)
     * @param unfinished     the trick that was still going at landing (limbs not back on the bike, spin not complete),
     *                       {@link Trick#NONE} if none
     * @param unfinishedPeak how far into the unfinished trick the rider got (0..1)
     */
    public record Summary(List<Trick> tricks, Trick unfinished, double unfinishedPeak) {
        public static final Summary EMPTY = new Summary(List.of(), Trick.NONE, 0);

        public int size() { return tricks.size(); }
        public boolean isEmpty() { return tricks.isEmpty() && unfinished == Trick.NONE; }
        public boolean hasUnfinished() { return unfinished != Trick.NONE; }
    }

    private final List<Trick> completed = new ArrayList<>();
    private boolean active;

    /** A new air session starts: forgets the previous one. */
    public void begin() {
        completed.clear();
        active = true;
    }

    /** A trick was finished. Ignored outside an air session and for {@link Trick#NONE}. */
    public void complete(Trick trick) {
        if (active && trick != Trick.NONE) completed.add(trick);
    }

    /** Ends the session (landing, bail, respawn). */
    public Summary finish(Trick unfinished, double unfinishedPeak) {
        Summary s = new Summary(List.copyOf(completed), unfinished == null ? Trick.NONE : unfinished, unfinishedPeak);
        completed.clear();
        active = false;
        return s;
    }

    /** Forgets everything without producing a summary. */
    public void clear() {
        completed.clear();
        active = false;
    }

    public boolean active() { return active; }
    public int size() { return completed.size(); }
    public boolean isEmpty() { return completed.isEmpty(); }
    /** Live, read-only view of the finished tricks so far. */
    public List<Trick> completed() { return Collections.unmodifiableList(completed); }
    public Trick last() { return completed.isEmpty() ? Trick.NONE : completed.get(completed.size() - 1); }
}
