package com.descentmtb.client;

import java.util.ArrayDeque;

/**
 * Turns the dedicated trick keys into one trick slot per tick, without Minecraft classes. Never loses a tap:
 * the caller passes, per slot, whether the key is down now and how many presses the game latched since the last
 * tick (KeyMapping click counts), so a tap that comes and goes between two ticks still fires.
 *
 * <ul>
 *   <li>Hold slots: the most recently pressed key that is still down wins; when it is let go the earlier one that is
 *       still down returns. A tap shorter than a tick is requested for one tick (the animation holds it long enough).</li>
 *   <li>Spin slots: every press in the air is queued and delivered one per two ticks (a gap tick in between, so the
 *       animation sees each as a fresh press, even the same spin twice). Holding a spin key does not repeat it.</li>
 *   <li>Nothing is latched on the ground. A spin key held through take-off does not fire.</li>
 * </ul>
 */
final class TrickInput {
    static final int SLOTS = 6;
    private static final int QUEUE_CAP = 4;

    private final boolean[] prevDown = new boolean[SLOTS];
    private final long[] pressStamp = new long[SLOTS];
    private final ArrayDeque<Integer> spins = new ArrayDeque<>();
    private long clock;
    private boolean deliveredLastTick;
    private boolean fresh = true;

    /**
     * @param inAir  the bike is airborne
     * @param down   key held at this moment, per slot
     * @param clicks presses latched since the previous call, per slot
     * @param spin   the slot's trick is a spin (SPIN kind) on this bike
     * @return the slot to request this tick, or -1
     */
    int update(boolean inAir, boolean[] down, int[] clicks, boolean[] spin) {
        boolean first = fresh;
        fresh = false;
        int hold = -1;
        long best = Long.MIN_VALUE;
        for (int i = 0; i < SLOTS; i++) {
            boolean d = down[i];
            // Key repeat only happens while the key is down; a real press needs the key to have been up
            int presses = first || prevDown[i] ? 0 : Math.max(clicks[i], d ? 1 : 0);
            if (presses > 0) {
                pressStamp[i] = ++clock;
                if (inAir && spin[i]) {
                    for (int k = 0; k < Math.min(presses, 2) && spins.size() < QUEUE_CAP; k++) spins.add(i);
                }
            }
            if (!spin[i] && (d || (inAir && presses > 0)) && pressStamp[i] >= best) {
                best = pressStamp[i];
                hold = i;
            }
            prevDown[i] = d;
        }
        if (!inAir) {
            spins.clear();
            deliveredLastTick = false;
            return hold;
        }
        if (!spins.isEmpty() && !deliveredLastTick) {
            deliveredLastTick = true;
            return spins.poll();
        }
        deliveredLastTick = false;
        return hold;
    }

    /** Forgets all state; the first update after it only learns which keys are already down. */
    void reset() {
        fresh = true;
        spins.clear();
        deliveredLastTick = false;
        java.util.Arrays.fill(prevDown, false);
    }
}
