package com.descentmtb.trick;

/**
 * Descenders tweak tricks (LB + right stick in the air). Enduro / downhill bikes
 * and the dirt-jump hardtail have different sets, exactly like Descenders:
 *
 * <pre>
 *                 Up              Up+Side     Side       Down+Side   Down
 *  Enduro/DH      No Hander       Tabletop    Nac Nac    Can Can     Superman
 *  Hardtail       Tuck No Hander  Tabletop    Barspin    Tailwhip    Superman Seatgrab
 * </pre>
 *
 * HOLD tricks blend in while the stick is held and out when released; SPIN tricks
 * (barspin, tailwhip) play one full rotation per flick and must finish before landing.
 * Every bike also has the Heelclicker (a HOLD trick) as a sixth, extra slot: see {@link com.descentmtb.entity.BikeType}.
 */
public enum Trick {
    NONE("", Kind.HOLD, 0, 0),
    NO_HANDER("No Hander", Kind.HOLD, 0.16f, 0.14f),
    TUCK_NO_HANDER("Tuck No Hander", Kind.HOLD, 0.14f, 0.12f),
    TABLETOP("Tabletop", Kind.HOLD, 0.20f, 0.16f),
    NAC_NAC("Nac Nac", Kind.HOLD, 0.16f, 0.14f),
    CAN_CAN("Can Can", Kind.HOLD, 0.16f, 0.14f),
    SUPERMAN("Superman", Kind.HOLD, 0.24f, 0.18f),
    SUPERMAN_SEATGRAB("Superman Seatgrab", Kind.HOLD, 0.24f, 0.18f),
    BARSPIN("Barspin", Kind.SPIN, 0.42f, 0),
    TAILWHIP("Tailwhip", Kind.SPIN, 0.58f, 0),
    /** Both legs thrown forward over the bars, heels together. On every bike (extra mapping, not a stick direction). */
    HEELCLICKER("Heelclicker", Kind.HOLD, 0.18f, 0.16f),
    // ---- skis (appended: tricks are synced by ordinal) ----
    /** Race skis, old-school aerials: arms and legs thrown wide, skis spread in a V. */
    SPREAD_EAGLE("Spread Eagle", Kind.HOLD, 0.18f, 0.15f),
    /** One ski kicked forward, the other back: a running split in the air. */
    DAFFY("Daffy", Kind.HOLD, 0.18f, 0.15f),
    /** Ski tails together, tips crossed in an X in front of the body. */
    IRON_CROSS("Iron Cross", Kind.HOLD, 0.20f, 0.16f),
    /** Knees bent hard, both ski tails kicked up behind until they nearly touch the back. */
    BACK_SCRATCHER("Back Scratcher", Kind.HOLD, 0.20f, 0.16f),
    /** Folded forward, one hand grabbing a ski tip. */
    TIP_GRAB("Tip Grab", Kind.HOLD, 0.20f, 0.16f),
    /** Freestyle: leading hand grabs the toe edge of the opposite ski between the feet. */
    MUTE_GRAB("Mute Grab", Kind.HOLD, 0.18f, 0.15f),
    /** Freestyle: mute grab with the ski tucked behind and tweaked up, knees driven forward. */
    JAPAN_GRAB("Japan Grab", Kind.HOLD, 0.22f, 0.17f),
    /** Freestyle: the hand grabs the outside edge of the same-side ski under the boot. */
    SAFETY_GRAB("Safety Grab", Kind.HOLD, 0.16f, 0.14f),
    /** Freestyle: reaching back to grab a ski's tail. */
    TAIL_GRAB("Tail Grab", Kind.HOLD, 0.20f, 0.16f),
    /** Freestyle: both hands grab both tips in front, like holding a steering wheel. */
    TRUCK_DRIVER("Truck Driver", Kind.HOLD, 0.22f, 0.17f);

    public enum Kind { HOLD, SPIN }

    public final String displayName;
    public final Kind kind;
    /** HOLD: seconds to blend fully in; SPIN: seconds for one full rotation. */
    public final float inTime;
    /** HOLD: seconds to blend out. */
    public final float outTime;

    Trick(String displayName, Kind kind, float inTime, float outTime) {
        this.displayName = displayName;
        this.kind = kind;
        this.inTime = inTime;
        this.outTime = outTime;
    }

    public static Trick byId(int id) {
        Trick[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
