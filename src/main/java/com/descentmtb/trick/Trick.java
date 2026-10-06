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
    HEELCLICKER("Heelclicker", Kind.HOLD, 0.18f, 0.16f);

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
