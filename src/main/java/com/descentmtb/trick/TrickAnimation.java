package com.descentmtb.trick;

import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Time-based trick pose shared by the rider and bike. One trick plays at a time, but nothing locks the input:
 * a trick requested while another is going is queued and starts the moment the current one has finished, so
 * any number of tricks can be chained in one jump (Tailwhip, Barspin, No Hander, ...). Spins finish after a flick;
 * hold tricks blend in while requested and out when released (after a short minimum, so a quick tap still plays).
 *
 * <p>Every finished trick is recorded in {@link #combo}; landing (or a bail / respawn) ends the session and leaves a
 * {@link ComboTracker.Summary} to be picked up with {@link #takeSummary()}.
 */
public final class TrickAnimation {
    /** A hold trick must have reached this amount to count as done once it is released. */
    public static final double COMPLETE_AMOUNT = 0.6;
    /** {@link #unfinished()}: limbs are off the bike above this amount. */
    public static final double UNFINISHED_AMOUNT = 0.15;
    private static final int QUEUE_CAP = 3;

    private static final CopyOnWriteArrayList<TrickListener> LISTENERS = new CopyOnWriteArrayList<>();

    public Trick trick = Trick.NONE;
    public double amount, progress;
    public int side = 1;
    /** The tricks finished in the current air session, in order. */
    public final ComboTracker combo = new ComboTracker();

    private Trick previousRequest = Trick.NONE;
    private double peak, holdTimer;
    private boolean inSession;
    private ComboTracker.Summary lastSummary;
    private final ArrayDeque<Pending> queue = new ArrayDeque<>();

    private record Pending(Trick trick, int side) {}

    public static void addListener(TrickListener l) { LISTENERS.addIfAbsent(l); }
    public static void removeListener(TrickListener l) { LISTENERS.remove(l); }

    public void tick(Trick request, int direction, boolean airborne, double dt) {
        if (!airborne) { reset(); return; }
        if (!inSession) {
            inSession = true;
            lastSummary = null;
            combo.begin();
        }
        int dir = direction < 0 ? -1 : 1;
        boolean rising = request != Trick.NONE && request != previousRequest;
        // A new request while something is going is not lost: it waits in line (never the trick that is going
        // already, nor the same one twice in a row, so holding or key-repeat cannot stack spins)
        if (rising && trick != Trick.NONE && request != trick
                && (queue.isEmpty() || queue.peekLast().trick != request) && queue.size() < QUEUE_CAP) {
            queue.add(new Pending(request, dir));
        }
        advance(request, dt);
        if (trick == Trick.NONE) {
            Pending next = queue.poll();
            if (next != null) start(next.trick, next.side, request, dt);
            else if (request != Trick.NONE && (request.kind == Trick.Kind.HOLD || rising)) start(request, dir, request, dt);
        }
        previousRequest = request;
    }

    private void advance(Trick request, double dt) {
        if (trick == Trick.NONE) return;
        if (trick.kind == Trick.Kind.SPIN) {
            progress = Math.min(1, progress + dt / trick.inTime);
            amount = Math.sin(Math.PI * progress);
            if (progress >= 1) finish(true);
            return;
        }
        holdTimer -= dt;
        boolean want = (request == trick && queue.isEmpty()) || holdTimer > 0;
        double target = want ? 1 : 0;
        if (target > amount) amount = Math.min(1, amount + dt / trick.inTime);
        else if (target < amount) amount = Math.max(0, amount - dt / (queue.isEmpty() ? trick.outTime : trick.outTime * 0.6));
        peak = Math.max(peak, amount);
        if (amount == 0 && target == 0) finish(peak >= COMPLETE_AMOUNT);
    }

    private void start(Trick t, int dir, Trick request, double dt) {
        trick = t;
        side = dir;
        progress = 0;
        amount = 0;
        peak = 0;
        // a tapped hold trick still plays: it stays in until it has fully blended in
        holdTimer = t.kind == Trick.Kind.HOLD ? t.inTime : 0;
        for (TrickListener l : LISTENERS) l.onTrickStart(t, dir);
        if (t.kind == Trick.Kind.HOLD) advance(request, dt);
    }

    private void finish(boolean completed) {
        Trick t = trick;
        trick = Trick.NONE;
        amount = 0;
        progress = 0;
        peak = 0;
        holdTimer = 0;
        if (completed) combo.complete(t);
        for (TrickListener l : LISTENERS) l.onTrickEnd(t, completed);
    }

    /**
     * True while a trick is not over: it is mid-animation, i.e. a spin that has not completed its rotation or a hold
     * trick whose limbs are off the bike ({@code amount > 0.15}). Landing in this state is what a rider should be
     * punished for; a queued (not yet started) trick does not count.
     */
    public boolean unfinished() {
        return trick != Trick.NONE
                && (amount > UNFINISHED_AMOUNT || (trick.kind == Trick.Kind.SPIN && progress < 1));
    }

    /**
     * The result of the air session that ended last (landing, bail or respawn) or {@code null} when there is none
     * (nothing was done since it was last taken, or a new session has begun). Cleared by taking it.
     */
    public ComboTracker.Summary takeSummary() {
        ComboTracker.Summary s = lastSummary;
        lastSummary = null;
        return s;
    }

    /** Ends the air session (if one was open) and puts the rider back on the bike. Called every tick on the ground. */
    public void reset() {
        if (inSession) {
            Trick unfinishedTrick = unfinished() ? trick : Trick.NONE;
            double unfinishedPeak = unfinishedTrick == Trick.NONE ? 0
                    : trick.kind == Trick.Kind.SPIN ? Math.max(progress, amount) : Math.max(peak, amount);
            Trick cancelled = trick;
            lastSummary = combo.finish(unfinishedTrick, unfinishedPeak);
            if (cancelled != Trick.NONE) for (TrickListener l : LISTENERS) l.onTrickEnd(cancelled, false);
        }
        inSession = false;
        trick = previousRequest = Trick.NONE;
        amount = progress = peak = holdTimer = 0;
        queue.clear();
    }

    public static double ease(double t) { return t * t * (3 - 2 * t); }

    /**
     * Rotation of a spin trick (tailwhip, barspin) over its progress: thrown hard (fastest a quarter of the way in) and
     * slowing into the catch, instead of a constant-speed turn. 0 → 0, 1 → 1, monotonic; {@code f(p) = 1 - (1-p)^4 (1+4p)}.
     */
    public static double spinCurve(double progress) {
        double p = Math.max(0, Math.min(1, progress)), q = 1 - p;
        return 1 - q * q * q * q * (1 + 4 * p);
    }
}
