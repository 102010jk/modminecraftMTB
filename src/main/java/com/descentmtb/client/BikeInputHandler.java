package com.descentmtb.client;

import com.descentmtb.physics.Controls;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

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

    private static boolean prevRespawn, prevRespawnStart, prevCamera, prevResetCamera;
    private static boolean prevHopKey;
    private static float tableSide = 1;
    private static int hopStretchTicks;

    public static Frame poll() {
        long win = Minecraft.getInstance().getWindow().getWindow();

        // ---------------- keyboard ----------------
        boolean trickKey = down(win, ModKeyMappings.TRICK);
        var ridden = BikeClientController.riding();
        boolean inAir = ridden != null && ridden.sim() != null && ridden.sim().airborne;
        // Space is the brake on the ground and the tweak (table) in the air, as in Descenders
        boolean tweakKey = inAir && down(win, ModKeyMappings.TWEAK);
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
        instantKeyboardSteering=ClientConfig.KEYBOARD_STEER_RAMP.get()<.001 && (pad==null || Math.abs(kSteer)>=Math.abs(pad.lx));
        float steer = kSteer, lean = kLean, pedal = kPedal, brake = kBrake, body = kBody, tweak = kTweak;
        boolean trick = trickKey;
        float trickX = kTrickX, trickY = kTrickY;
        if (pad != null) {
            steer = bigger(steer, pad.lx);
            lean = bigger(lean, -pad.ly);
            pedal = Math.max(pedal, pad.rt);
            brake = Math.max(brake, pad.lt);
            if (pad.lb) {
                trick = true;
                trickX = bigger(trickX, pad.rx);
                trickY = bigger(trickY, -pad.ry);
            } else {
                body = bigger(body, -pad.ry);
                tweak = bigger(tweak, pad.rx);
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

    private static boolean down(long win, KeyMapping k) {
        InputConstants.Key key = k.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            return InputConstants.isKeyDown(win, key.getValue());
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return false;
    }

    private static float bigger(float a, float b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    // ------------------------------------------------------------------ gamepad

    private static final class Pad {
        float lx, ly, rx, ry, lt, rt;
        boolean a, b, x, y, lb, back;
    }

    private static Pad pollPad() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            if (GLFW.glfwJoystickIsGamepad(jid)) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    GLFWGamepadState gp = GLFWGamepadState.malloc(stack);
                    if (GLFW.glfwGetGamepadState(jid, gp)) {
                        Pad p = new Pad();
                        float[] l = radial(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X), gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y));
                        float[] r = radial(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y));
                        p.lx = l[0];
                        p.ly = l[1];
                        p.rx = r[0];
                        p.ry = r[1];
                        p.lt = trigger(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
                        p.rt = trigger(gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));
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
            }
            // raw fallback (XInput order on Windows)
            FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer btn = GLFW.glfwGetJoystickButtons(jid);
            if (axes == null || axes.limit() < 4) continue;
            Pad p = new Pad();
            float[] l = radial(axes.get(0), axes.get(1));
            float[] r = radial(axes.get(2), axes.get(3));
            p.lx = l[0];
            p.ly = l[1];
            p.rx = r[0];
            p.ry = r[1];
            p.lt = axes.limit() > 4 ? trigger(axes.get(4)) : 0;
            p.rt = axes.limit() > 5 ? trigger(axes.get(5)) : 0;
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
        controllerStatus = "none";
        return null;
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

    /** GLFW triggers rest at -1. */
    private static float trigger(float v) {
        float t = (v + 1f) * 0.5f;
        return t < 0.05f ? 0f : Math.min(1f, t);
    }

    private static String name(String n) {
        return n == null ? "?" : n;
    }

    /** One chat line on mounting so the player can see what was detected. */
    public static void announce(LocalPlayer player) {
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) {
                found++;
                boolean pad = GLFW.glfwJoystickIsGamepad(jid);
                sb.append(pad ? "gamepad \"" : "joystick \"")
                        .append(name(pad ? GLFW.glfwGetGamepadName(jid) : GLFW.glfwGetJoystickName(jid))).append("\"  ");
            }
        }
        player.displayClientMessage(found == 0
                ? Component.literal("§e[MTB] Ovladač nenalezen – klávesnice: Z plyn, mezerník brzda, šipky, X bunnyhop, V kamera.")
                : Component.literal("§a[MTB] Ovladač: §f" + sb.toString().trim() + " §7(RT plyn, LT brzda, R dolů→nahoru = hop, LB+R triky)"), false);
        LOG.info("[Descent MTB] controllers: {}", found == 0 ? "none" : sb.toString().trim());
    }

    private BikeInputHandler() {}
}
