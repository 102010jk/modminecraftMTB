package com.descentmtb.trick;

/** Time-based trick pose shared by the rider and bike. Spins finish after a flick. */
public final class TrickAnimation {
    public Trick trick = Trick.NONE;
    public double amount, progress;
    public int side = 1;
    private Trick previousRequest = Trick.NONE;

    public void tick(Trick request, int direction, boolean airborne, double dt) {
        if (!airborne) { reset(); return; }
        if (trick.kind == Trick.Kind.SPIN && trick != Trick.NONE) {
            progress = Math.min(1, progress + dt / trick.inTime);
            amount = Math.sin(Math.PI * progress);
            if (progress >= 1) { trick = Trick.NONE; amount = 0; }
        } else {
            if (trick == Trick.NONE && request != Trick.NONE
                    && (request.kind == Trick.Kind.HOLD || request != previousRequest)) {
                trick = request;
                side = direction < 0 ? -1 : 1;
                progress = 0;
            }
            if (trick != Trick.NONE) {
                if (trick.kind == Trick.Kind.HOLD) {
                    double target = request == trick ? 1 : 0;
                    if (target > amount) amount = Math.min(1, amount + dt / trick.inTime);
                    else if (target < amount) amount = Math.max(0, amount - dt / trick.outTime);
                    if (amount == 0 && target == 0) trick = Trick.NONE;
                }
            }
        }
        previousRequest = request;
    }

    public void reset() {
        trick = previousRequest = Trick.NONE;
        amount = progress = 0;
    }

    public static double ease(double t) { return t * t * (3 - 2 * t); }
}
