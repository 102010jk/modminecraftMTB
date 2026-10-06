package com.descentmtb.client;

/** How the keyboard fires tricks (see {@code ClientConfig.TRICK_KEY_SCHEME}). */
public enum TrickKeyScheme {
    /** C held + arrow keys pick the trick, like LB + right stick; the Heelclicker has its own key. */
    CLASSIC,
    /** One key per trick (default I J K L U O): the left hand keeps the arrows for rotation and lean. */
    INDEPENDENT
}
