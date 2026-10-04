package com.descentmtb.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Keyboard bindings, defaulting to the Descenders PC layout (all rebindable in
 * Options → Controls → Descent MTB). The keys are read straight from the
 * window while riding, so it does not matter that S / D / Space also have a
 * vanilla meaning.
 *
 * <p>One deliberate difference: Descenders uses Shift for "tweak", but Shift is
 * Minecraft's dismount key, so tweak defaults to Left Alt.
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.descentmtb";

    public static final KeyMapping TRAIL_MENU = key("trail_menu", GLFW.GLFW_KEY_G);
    public static final KeyMapping TRAIL_UNDO = key("trail_undo", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ACCELERATE = key("accelerate", GLFW.GLFW_KEY_Z);
    public static final KeyMapping BRAKE = key("brake", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping TWEAK = key("tweak", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping STEER_LEFT = key("steer_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping STEER_RIGHT = key("steer_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping LEAN_FORWARD = key("lean_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping LEAN_BACK = key("lean_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping BUNNY_HOP = key("bunny_hop", GLFW.GLFW_KEY_X);
    public static final KeyMapping BEND = key("bend", GLFW.GLFW_KEY_S);
    public static final KeyMapping STRETCH = key("stretch", GLFW.GLFW_KEY_D);
    public static final KeyMapping TRICK = key("trick", GLFW.GLFW_KEY_C);
    public static final KeyMapping RESPAWN = key("respawn", GLFW.GLFW_KEY_R);
    public static final KeyMapping RESPAWN_START = key("respawn_start", GLFW.GLFW_KEY_BACKSPACE);
    public static final KeyMapping CAMERA = key("camera", GLFW.GLFW_KEY_V);
    public static final KeyMapping RESET_CAMERA = key("reset_camera", GLFW.GLFW_KEY_B);

    public static final List<KeyMapping> ALL = List.of(ACCELERATE, BRAKE, TWEAK, STEER_LEFT, STEER_RIGHT,
            LEAN_FORWARD, LEAN_BACK, BUNNY_HOP, BEND, STRETCH, TRICK, RESPAWN, RESPAWN_START, CAMERA, RESET_CAMERA, TRAIL_MENU,
            TRAIL_UNDO);

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key.descentmtb." + name, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    private ModKeyMappings() {}
}
