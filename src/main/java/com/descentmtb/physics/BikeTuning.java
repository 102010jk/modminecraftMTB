package com.descentmtb.physics;

/** Predictable, gradual tyre/fork tuning. Low pressure scrubs speed, never switches drive off. */
public final class BikeTuning {
    public static void apply(BikeParams p, BikeParams base, double front, double rear, double fork, double strength) {
        double sf = Math.max(0, (28-front)/23), sr = Math.max(0, (28-rear)/23);
        double soft = (sf+sr)*.5;
        double hard = (Math.max(0,(front-32)/35)+Math.max(0,(rear-32)/35))*.5;
        p.tyreRolling = 1 + strength * ((sf*sf+sr*sr)*2 + hard * .12);
        p.tyreGrip = Math.max(.65, 1 + strength * (soft * .07 - hard * .22));
        double ratio = Math.max(.35, Math.min(2.2, fork / 80));
        p.forkRate = base.forkRate * ratio;
        p.forkProgression = base.forkProgression * Math.sqrt(ratio);
        p.forkCompDamp = base.forkCompDamp * Math.sqrt(ratio);
        p.forkRebDamp = base.forkRebDamp * Math.sqrt(ratio);
    }
    private BikeTuning() {}
}
