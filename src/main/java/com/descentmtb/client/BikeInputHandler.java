package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.BikeInput;
import com.descentmtb.entity.MountainBikeEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Builds a {@link BikeInput} every client tick while the local player is riding
 * a bike, blending keyboard and controller. Pushed straight onto the bike the
 * player controls.
 *
 * <p><b>Descenders-style mapping</b><br>
 * Controller: Left stick X = steer (turns the bars), RT = pedal, LT = brake,
 * A = bunny hop (hold to charge, pull back to preload, release to pop),
 * <b>Right stick X = lean</b> (body tilt).<br>
 * Keyboard: W/S = pedal / lean back, A/D = steer, Space = bunny hop,
 * Left Ctrl = brake, Z/C = lean (all rebindable). Sneak dismounts.
 *
 * <p>Controller input is read straight from the OS via GLFW: first the mapped
 * "gamepad" API, then a raw-joystick fallback for pads GLFW has no mapping for.
 * On mounting, a one-off chat line reports what was detected.
 */
@EventBusSubscriber(modid = DescentMtb.MODID, value = Dist.CLIENT)
public final class BikeInputHandler {
    private static final Logger LOG = LogUtils.getLogger();
    private static final float DEADZONE = 0.18f;

    private static boolean wasRiding = false;
    /** Last-known controller status string (for HUD / debugging). */
    public static volatile String controllerStatus = "none";

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasRiding = false;
            return;
        }
        if (!(player.getVehicle() instanceof MountainBikeEntity bike)) {
            wasRiding = false;
            return;
        }
        if (mc.isPaused()) {
            bike.setControlInput(BikeInput.NONE);
            return;
        }

        // --- keyboard base (vanilla movement impulses still populate while riding) ---
        float throttle = player.input.forwardImpulse;          // W = +1, S = -1
        float steer = player.input.leftImpulse;                // A = +1, D = -1
        boolean hop = player.input.jumping;                    // Space
        float brake = ModKeyMappings.BRAKE.isDown() ? 1f : 0f; // Left Ctrl
        float lean = (ModKeyMappings.LEAN_LEFT.isDown() ? 1f : 0f)
                - (ModKeyMappings.LEAN_RIGHT.isDown() ? 1f : 0f);

        // --- controller overrides when actively used ---
        Ctl c = pollController();
        if (c != null && c.active) {
            steer = c.steer;
            throttle = c.throttle;
            brake = Math.max(brake, c.brake);
            lean = c.lean;
            hop = hop || c.hop;
        }

        // one-off diagnostic the moment we get on the bike
        if (!wasRiding) {
            announce(player);
        }
        wasRiding = true;

        bike.setControlInput(new BikeInput(
                Mth.clamp(throttle, -1f, 1f),
                Mth.clamp(steer, -1f, 1f),
                Mth.clamp(brake, 0f, 1f),
                Mth.clamp(lean, -1f, 1f),
                hop));
    }

    // ----------------------------------------------------------------
    //  Controller polling
    // ----------------------------------------------------------------
    private static final class Ctl {
        float throttle, steer, brake, lean;
        boolean hop, active;
    }

    private static Ctl pollController() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) {
                continue;
            }
            // 1) mapped gamepad API
            if (GLFW.glfwJoystickIsGamepad(jid)) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    GLFWGamepadState gp = GLFWGamepadState.malloc(stack);
                    if (GLFW.glfwGetGamepadState(jid, gp)) {
                        Ctl c = fill(
                                gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X),
                                gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y),
                                gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
                                (gp.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) + 1f) * 0.5f,
                                (gp.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) + 1f) * 0.5f,
                                gp.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS);
                        controllerStatus = "gamepad: " + safeName(GLFW.glfwGetGamepadName(jid));
                        return c;
                    }
                }
            }
            // 2) raw-joystick fallback (best-effort standard layout)
            FloatBuffer axes = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
            if (axes != null && axes.limit() >= 2) {
                float lx = axes.get(0);
                float ly = axes.get(1);
                float rx = axes.limit() > 2 ? axes.get(2) : 0f;
                float lt = axes.limit() > 4 ? (axes.get(4) + 1f) * 0.5f : 0f;
                float rt = axes.limit() > 5 ? (axes.get(5) + 1f) * 0.5f : 0f;
                boolean a = buttons != null && buttons.limit() > 0 && buttons.get(0) == GLFW.GLFW_PRESS;
                Ctl c = fill(lx, ly, rx, lt, rt, a);
                controllerStatus = "joystick(raw): " + safeName(GLFW.glfwGetJoystickName(jid));
                return c;
            }
        }
        controllerStatus = "none";
        return null;
    }

    private static Ctl fill(float lx, float ly, float rx, float lt, float rt, boolean aBtn) {
        Ctl c = new Ctl();
        float stickY = -dz(ly);                 // up = +1
        c.steer = -dz(lx);                      // left = +1
        c.lean = dz(rx);                        // right stick = body tilt
        c.brake = lt;
        c.throttle = stickY < 0 ? stickY : Math.max(stickY, rt); // RT pedals, stick-down leans back
        c.hop = aBtn;
        c.active = Math.abs(lx) > DEADZONE || Math.abs(ly) > DEADZONE || Math.abs(rx) > DEADZONE
                || lt > 0.1f || rt > 0.1f || aBtn;
        return c;
    }

    private static float dz(float v) {
        if (Math.abs(v) < DEADZONE) {
            return 0f;
        }
        return Math.signum(v) * (Math.abs(v) - DEADZONE) / (1f - DEADZONE);
    }

    private static String safeName(String n) {
        return n == null ? "?" : n;
    }

    /** One-off chat + log line on mount so the player can see what GLFW detected. */
    private static void announce(LocalPlayer player) {
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickPresent(jid)) {
                found++;
                boolean pad = GLFW.glfwJoystickIsGamepad(jid);
                String name = pad ? GLFW.glfwGetGamepadName(jid) : GLFW.glfwGetJoystickName(jid);
                sb.append("#").append(jid).append(' ')
                        .append(pad ? "gamepad" : "joystick").append(" \"").append(safeName(name)).append("\"  ");
            }
        }
        Component msg = found == 0
                ? Component.literal("§e[MTB] Žádný ovladač nenalezen – hraješ na klávesnici.")
                : Component.literal("§a[MTB] Ovladač: §f" + sb.toString().trim());
        player.displayClientMessage(msg, false);
        LOG.info("[Descent MTB] controllers detected: {}", found == 0 ? "none" : sb.toString().trim());
    }

    private BikeInputHandler() {}
}
