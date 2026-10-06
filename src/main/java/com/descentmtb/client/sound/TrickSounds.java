package com.descentmtb.client.sound;

import java.util.Locale;

/** Which trick makes which sound. Matches on the trick's enum name so tricks added later need no change here. */
public final class TrickSounds {
    public enum Kind { HEEL_CLICK, BARSPIN, TAILWHIP }

    /** The sound of a trick (by its {@code Trick} enum name), or null when it is silent. */
    public static Kind of(String trickName) {
        if (trickName == null) return null;
        String n = trickName.toUpperCase(Locale.ROOT).replace("_", "").replace(" ", "");
        if (n.contains("HEELCLICK")) return Kind.HEEL_CLICK;
        if (n.equals("BARSPIN")) return Kind.BARSPIN;
        if (n.equals("TAILWHIP")) return Kind.TAILWHIP;
        return null;
    }

    private TrickSounds() {}
}
