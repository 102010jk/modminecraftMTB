package com.descentmtb.client;

/** Stateful button gestures, kept separate from device polling and Minecraft. */
final class InputTransitions {
    static final class AirPress {
        private boolean held = true, active;

        boolean update(boolean inAir, boolean down) {
            if (!inAir || !down) active = false;
            else if (!held) active = true;
            held = down;
            return active;
        }

        void reset() { held = true; active = false; }
    }

    static final class Hop {
        private boolean held = true, armed;
        private int remaining;

        float update(boolean down) {
            if (down && !held) armed = true;
            if (!down && held && armed) { remaining = 6; armed = false; }
            held = down;
            if (down) return -1;
            if (remaining > 0) { remaining--; return 1; }
            return 0;
        }

        void reset() { held = true; armed = false; remaining = 0; }
    }

    private InputTransitions() {}
}
