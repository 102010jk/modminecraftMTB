package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Controls;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Reads the controller (GLFW, no extra mods needed) and keyboard once per
 * client tick and turns them into {@link Controls}, following the official
 * Descenders bindings:
 *
 * <pre>
 *                      Controller            Keyboard
 *  Accelerate          RT                    Z
 *  Brake               LT                    Space
 *  Steer / lean        Left stick            Arrow keys
 *  Bend (attack/pump)  Right stick down      S
 *  Stretch (counter)   Right stick up        D
 *  Bunny hop           R down → R up         X (hold, release)
 *  Body lean (ground)  Right stick ← →       -   (into the turn = carve, out = drift)
 *  Tweak / table (air) Right stick ← →       Space (+ ← → picks the side)
 *  Tricks              LB + right stick      C + arrows
 *  Respawn             B                     R
 *  Respawn at start    Back / View           Backspace
 *  Switch camera       Y                     V
 *  Reset camera        X                     B
 * </pre>
 */
public final class BikeInputHandler {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float STICK_DEADZONE = 0.14f;

    /** What the player asked for this tick. Button actions are edge-triggered. */
    public record Frame(Controls controls, boolean respawn, boolean respawnStart, boolean cycleCamera,
                        boolean resetCamera) {
        public static final Frame NONE = new Frame(Controls.NONE, false, false, false, false);
    }

    public static volatile String controllerStatus = "none";
    public static boolean instantKeyboardSteering;

    /** Edge detectors; they start "held" so a key already down when riding begins is not a fresh press. */
    private static boolean prevRespawn = true, prevRespawnStart = true, prevCamera = true, prevResetCamera = true;
    private static boolean prevHopKey;
    private static float tableSide = 1;
    private static boolean tweakArmed;
    private static int hopStretchTicks;
    /** The bike seen riding on the last client tick, to notice a (re)mount. */
    private static MountainBikeEntity lastRiding;

    /**
     * Called every client tick (after the bike's own tick). Notices mounting, announces the detected controller
     * once, and forgets all held / edge state while the player is not riding, a screen is open or the game is
     * paused, so nothing fires on the first tick after a remount.
     */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        MountainBikeEntity riding = BikeClientController.riding();
        if (riding != lastRiding) {
            lastRiding = riding;
            if (riding != null && mc.player != null) {
                resetState();
                announce(mc.player);
            }
        }
        if (riding == null || mc.screen != null || mc.isPaused()) {
            resetState();
        }
    }

    /**
     * Clears keyboard ramps, bunny-hop and tweak state and the pad helpers. The edge detectors start out "held" so a
     * key that is still down when riding resumes does not count as a fresh press (it must be released first).
     */
    private static void resetState() {
        keyboardSteer = 0;
        instantKeyboardSteering = false;
        prevHopKey = false;
        hopStretchTicks = 0;
        tweakArmed = false;
        tableSide = 1;
        padCrouchTicks = 0;
        padPopTicks = 0;
        prevRespawn = prevRespawnStart = prevCamera = prevResetCamera = true;
    }

    public static Frame poll() {
        long win = Minecraft.getInstance().getWindow().getWindow();

        // ---------------- keyboard ----------------
        boolean trickKey = down(win, ModKeyMappings.TRICK);
        var ridden = BikeClientController.riding();
        boolean inAir = ridden != null && ridden.sim() != null && ridden.sim().airborne;
        // Space is the brake on the ground and the tweak (table) in the air, as in Descenders
        // ... but only a fresh press in the air: Space still held from braking before the lip must not table
        boolean spaceDown = down(win, ModKeyMappings.TWEAK);
        if (!inAir || !spaceDown) {
            tweakArmed = inAir && !spaceDown;
        }
        boolean tweakKey = inAir && spaceDown && tweakArmed;
        float arrowX = (down(win, ModKeyMappings.STEER_RIGHT) ? 1 : 0) - (down(win, ModKeyMappings.STEER_LEFT) ? 1 : 0);
        float arrowY = (down(win, ModKeyMappings.LEAN_FORWARD) ? 1 : 0) - (down(win, ModKeyMappings.LEAN_BACK) ? 1 : 0);
        float kSteer = 0, kLean = 0, kTweak = 0, kTrickX = 0, kTrickY = 0;
        if (trickKey) {
            kTrickX = arrowX;
            kTrickY = arrowY;
        } else if (tweakKey) {
            if (arrowX != 0) {
                tableSide = arrowX;
            }
            kTweak = tableSide;          // Space alone = table to the last chosen side
            kLean = arrowY;
        } else {
            kSteer = arrowX;
            kLean = arrowY;
        }
        kSteer = rampKeyboardSteer(kSteer);
        float kPedal = down(win, ModKeyMappings.ACCELERATE) ? 1 : 0;
        float kBrake = !tweakKey && down(win, ModKeyMappings.BRAKE) ? 1 : 0;
        float kBody = (down(win, ModKeyMappings.STRETCH) ? 1 : 0) - (down(win, ModKeyMappings.BEND) ? 1 : 0);
        // X = bunny-hop macro: hold to bend, release to spring up
        boolean hopKey = down(win, ModKeyMappings.BUNNY_HOP);
        if (hopKey) kBody = -1;
        else if (prevHopKey) hopStretchTicks = 6;
        prevHopKey = hopKey;
        if (!hopKey && hopStretchTicks > 0) {
            hopStretchTicks--;
            kBody = 1;
        }
        boolean respawn = down(win, ModKeyMappings.RESPAWN);
        boolean respawnStart = down(win, ModKeyMappings.RESPAWN_START);
        boolean camera = down(win, ModKeyMappings.CAMERA);
        boolean resetCamera = down(win, ModKeyMappings.RESET_CAMERA);

        // ---------------- controller ----------------
        Pad pad = pollPad();
        // keys steer instantly, but only while a steering key is actually held: releasing (a key or the stick)
        // always lets the bars return smoothly instead of snapping to centre in one frame
        instantKeyboardSteering = ClientConfig.KEYBOARD_STEER_RAMP.get() < .001 && kSteer != 0
                && (pad == null || Math.abs(kSteer) >= Math.abs(pad.lx));
        float steer = kSteer, lean = kLean, pedal = kPedal, brake = kBrake, body = kBody, tweak = kTweak;
        boolean trick = trickKey;
        float trickX = kTrickX, trickY = kTrickY;
        if (pad != null) {
            steer = bigger(steer, pad.lx);
            // the left stick is mostly a steering stick: a little up/down while steering must not move the rider
            // forward (front glued to the ground, accidental drifts) - lean only past a 35 % axial dead zone
            lean = bigger(lean, axial(-pad.ly, 0.35f));
            pedal = Math.max(pedal, pad.rt);
            brake = Math.max(brake, pad.lt);
            if (pad.lb) {
                trick = true;
                trickX = bigger(trickX, pad.rx);
                trickY = bigger(trickY, -pad.ry);
            } else {
                body = bigger(body, padHop(-pad.ry));
                tweak = bigger(tweak, axial(pad.rx, 0.15f));
            }
            respawn |= pad.b;
            respawnStart |= pad.back;
            camera |= pad.y;
            resetCamera |= pad.x;
        }

        Frame f = new Frame(new Controls(steer, lean, pedal, brake, body, tweak, trick, trickX, trickY),
                respawn && !prevRespawn, respawnStart && !prevRespawnStart,
                camera && !prevCamera, resetCamera && !prevResetCamera);
        prevRespawn = respawn;
        prevRespawnStart = respawnStart;
        prevCamera = camera;
        prevResetCamera = resetCamera;
        return f;
    }

    private static float keyboardSteer;

    /**
     * A key is either 0 or full lock. Easing it in over {@code keyboardSteerRamp} seconds (a bit slower at
     * speed, quicker on release) is what makes keyboard steering feel like a stick instead of a switch.
     */
    private static float rampKeyboardSteer(float target) {
        double ramp = ClientConfig.KEYBOARD_STEER_RAMP.get();
        if (ramp < .001) {
            return keyboardSteer = target;
        }
        var bike = BikeClientController.riding();
        double speed = bike != null && bike.sim() != null ? bike.sim().speed() : 0;
        double rate = .05 / ramp * (speed > 8 ? .75 : 1) * (target == 0 ? 1.6 : 1);
        keyboardSteer += (float) Math.max(-rate, Math.min(rate, target - keyboardSteer));
        return keyboardSteer;
    }

    private static float axial(float v, float deadZone) {
        float a = Math.abs(v);
        return a <= deadZone ? 0f : Math.signum(v) * (a - deadZone) / (1f - deadZone);
    }

    private static int padCrouchTicks, padPopTicks;

    /**
     * Right stick Y with a bunny-hop helper: a quick flick down then up (within 0.4 s) always gives the full
     * 0.3 s stretch, like the X key, even if the stick springs back to the centre straight away.
     */
    private static float padHop(float body) {
        if (body < -0.5f) {
            padCrouchTicks = 8;
        } else if (padCrouchTicks > 0) {
            padCrouchTicks--;
            if (body > 0.5f) {
                padPopTicks = 6;
                padCrouchTicks = 0;
            }
        }
        if (padPopTicks > 0) {
            padPopTicks--;
            return Math.max(body, 1f);
        }
        return body;
    }

    /** True while the binding's key (or mouse button) is held and, for a Ctrl / Shift / Alt binding, its modifier too. */
    private static boolean down(long win, KeyMapping k) {
        InputConstants.Key key = k.getKey();
        boolean pressed;
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            pressed = InputConstants.isKeyDown(win, key.getValue());
        } else if (key.getType() == InputConstants.Type.MOUSE) {
            pressed = GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        } else {
            return false;
        }
        KeyModifier modifier = k.getKeyModifier();
        return pressed && (modifier == KeyModifier.NONE || modifier.isActive(k.getKeyConflictContext()));
    }

    private static float bigger(float a, float b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    // ------------------------------------------------------------------ gamepad

    private static final class Pad {
        float lx, ly, rx, ry, lt, rt;
        boolean a, b, x, y, lb, back;
    }

    /** Per joystick and trigger (LT, RT): the first value seen and whether the trigger ever left it. */
    private static final float[][] triggerFirst = new float[GLFW.GLFW_JOYSTICK_LAST + 1][2];
    private static final boolean[][] triggerMoved = new boolean[GLFW.GLFW_JOYSTICK_LAST + 1][2];

    static {
        for (float[] first : triggerFirst) {
            Arrays.fill(first, Float.NaN);
        }
    }

    /**
     * Reads the first usable controller: a device GLFW knows as a gamepad wins; the raw joystick layout (XInput
     * order) is only a fallback when no gamepad is connected. Nothing is read while the window is not focused.
     */
    private static Pad pollPad() {
        if (!Minecraft.getInstance().isWindowActive()) {
            return null;
        }
        boolean gamepadPresent = false;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) {
                forgetTriggers(jid);
            } else if (GLFW.glfwJoystickIsGamepad(jid)) {
                gamepadPresent = true;
            }
        }
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid) && GLFW.glfwJoystickIsGamepad(jid)) {
                Pad p = readGamepad(jid);
                if (p != null) {
                    return p;
                }
            }
        }
        if (!gamepadPresent) {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (GLFW.glfwJoystickPresent(jid)) {
                    Pad p = readRaw(jid);
                    if (p != null) {
                        return p;
                    }
                }
            }
        }
        controllerStatus = "none";
        return null;
    }

    private static Pad readGamepad(int jid) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState gp = GLFWGamepadState.malloc(stack);
            if (!GLFW.glfwGetGamepadState(jid, gp)) {
                return null;
            }
            Pad p = new Pad();
            float[] l = radial(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y));
            float[] r = radial(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y));
            p.lx = l[0];
            p.ly = l[1];
            p.rx = r[0];
            p.ry = r[1];
            p.lt = trigger(jid, 0, gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
            p.rt = trigger(jid, 1, gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));
            p.a = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS;
            p.b = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_B) == GLFW.GLFW_PRESS;
            p.x = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_X) == GLFW.GLFW_PRESS;
            p.y = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_Y) == GLFW.GLFW_PRESS;
            p.lb = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) == GLFW.GLFW_PRESS;
            p.back = gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_BACK) == GLFW.GLFW_PRESS;
            controllerStatus = "gamepad: " + name(GLFW.glfwGetGamepadName(jid));
            return p;
        }
    }

    /** Raw joystick fallback (XInput order on Windows). */
    private static Pad readRaw(int jid) {
        FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer btn = GLFW.glfwGetJoystickButtons(jid);
        if (axes == null || axes.limit() < 4) {
            return null;
        }
        Pad p = new Pad();
        float[] l = radial(axes.get(0), axes.get(1));
        float[] r = radial(axes.get(2), axes.get(3));
        p.lx = l[0];
        p.ly = l[1];
        p.rx = r[0];
        p.ry = r[1];
        p.lt = axes.limit() > 4 ? trigger(jid, 0, axes.get(4)) : 0;
        p.rt = axes.limit() > 5 ? trigger(jid, 1, axes.get(5)) : 0;
        if (btn != null) {
            p.a = pressed(btn, 0);
            p.b = pressed(btn, 1);
            p.x = pressed(btn, 2);
            p.y = pressed(btn, 3);
            p.lb = pressed(btn, 4);
            p.back = pressed(btn, 6);
        }
        controllerStatus = "joystick (raw): " + name(GLFW.glfwGetJoystickName(jid));
        return p;
    }

    private static boolean pressed(ByteBuffer b, int i) {
        return b.limit() > i && b.get(i) == GLFW.GLFW_PRESS;
    }

    /** Radial dead zone, rescaled so the stick still reaches 1. */
    private static float[] radial(float x, float y) {
        float m = (float) Math.sqrt(x * x + y * y);
        if (m < STICK_DEADZONE) return new float[]{0, 0};
        float s = Math.min(1f, (m - STICK_DEADZONE) / (1f - STICK_DEADZONE)) / m;
        return new float[]{x * s, y * s};
    }

    /**
     * A trigger as 0..1. GLFW triggers rest at -1, but a pad that has not reported a trigger yet reads 0 (= half
     * pressed), so a trigger counts as 0 until its value has changed once since the controller was connected.
     */
    private static float trigger(int jid, int which, float v) {
        if (Float.isNaN(triggerFirst[jid][which])) {
            triggerFirst[jid][which] = v;
        } else if (Math.abs(v - triggerFirst[jid][which]) > 0.05f) {
            triggerMoved[jid][which] = true;
        }
        if (!triggerMoved[jid][which]) {
            return 0f;
        }
        float t = (v + 1f) * 0.5f;
        return t < 0.05f ? 0f : Math.min(1f, t);
    }

    private static void forgetTriggers(int jid) {
        Arrays.fill(triggerFirst[jid], Float.NaN);
        Arrays.fill(triggerMoved[jid], false);
    }

    private static String name(String n) {
        return n == null ? "?" : n;
    }

    /** One chat line on mounting so the player can see what was detected (and which keys to use without a pad). */
    private static void announce(LocalPlayer player) {
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) {
                found++;
                boolean pad = GLFW.glfwJoystickIsGamepad(jid);
                String device = name(pad ? GLFW.glfwGetGamepadName(jid) : GLFW.glfwGetJoystickName(jid));
                sb.append(Component.translatable(pad ? "descentmtb.input.gamepad" : "descentmtb.input.joystick", device)
                        .getString()).append("  ");
            }
        }
        String devices = sb.toString().trim();
        player.displayClientMessage(found == 0
                ? Component.translatable("descentmtb.input.no_controller",
                        ModKeyMappings.ACCELERATE.getTranslatedKeyMessage(), ModKeyMappings.BRAKE.getTranslatedKeyMessage(),
                        ModKeyMappings.STEER_LEFT.getTranslatedKeyMessage(), ModKeyMappings.STEER_RIGHT.getTranslatedKeyMessage(),
                        ModKeyMappings.BUNNY_HOP.getTranslatedKeyMessage(), ModKeyMappings.CAMERA.getTranslatedKeyMessage())
                : Component.translatable("descentmtb.input.controller", devices), false);
        LOG.info("[Descent MTB] controllers: {}", found == 0 ? "none" : devices);
    }

    private BikeInputHandler() {}
}
