package com.descentmtb.physics;

/**
 * One frame of rider intent, already mapped to the Descenders control scheme:
 *
 * <pre>
 *  Left stick X   steer (in the air: spin)          keyboard ← →
 *  Left stick Y   lean forward/back (air: flip)     keyboard ↑ ↓
 *  RT             accelerate / pedal                keyboard Z
 *  LT             brake                             keyboard Space
 *  Right stick Y  down = bend (attack/pump),        keyboard S / D
 *                 up = stretch (counter-pump);
 *                 down→up = bunny hop               keyboard X (macro)
 *  Right stick X  ground: lean the body sideways     keyboard: -
 *                 (into the turn = tighter, out = drift)
 *                 air: tweak / table                keyboard Space (+ ← →)
 *  LB + R stick   trick                             keyboard C + arrows
 * </pre>
 *
 * Sticks are -1..1 (up/right positive), triggers 0..1. Immutable.
 */
public final class Controls {
    public static final Controls NONE = new Controls(0, 0, 0, 0, 0, 0, false, 0, 0);

    /** Left stick X: +1 = steer right. */
    public final float steer;
    /** Left stick Y: +1 = lean forward (air: frontflip), -1 = lean back (manual / air: backflip). */
    public final float lean;
    /** RT: 0..1 pedal. */
    public final float pedal;
    /** LT: 0..1 brake. */
    public final float brake;
    /** Right stick Y: -1 = bend down (crouch / pump), +1 = stretch up (counter-pump / pop). */
    public final float body;
    /** Right stick X: tweak direction in the air (-1..1). */
    public final float tweak;
    /** LB held: right stick selects a trick instead of body/tweak. */
    public final boolean trickMod;
    /** Right stick while LB is held (trick direction). */
    public final float trickX, trickY;

    public Controls(float steer, float lean, float pedal, float brake, float body, float tweak,
                    boolean trickMod, float trickX, float trickY) {
        this.steer = clamp(steer, -1, 1);
        this.lean = clamp(lean, -1, 1);
        this.pedal = clamp(pedal, 0, 1);
        this.brake = clamp(brake, 0, 1);
        this.body = clamp(body, -1, 1);
        this.tweak = clamp(tweak, -1, 1);
        this.trickMod = trickMod;
        this.trickX = clamp(trickX, -1, 1);
        this.trickY = clamp(trickY, -1, 1);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? 0f : Math.max(lo, Math.min(hi, v));
    }
}
