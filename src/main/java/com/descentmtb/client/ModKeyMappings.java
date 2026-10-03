package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard bindings. Steering / pedalling reuse the vanilla movement input
 * (WASD) so they conflict with nothing; this adds the one control Minecraft
 * lacks - a dedicated brake. All bindings are rebindable in Options > Controls.
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.descentmtb";

    public static final KeyMapping BRAKE = new KeyMapping(
            "key.descentmtb.brake", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);

    // body lean ("zklápění") - the keyboard equivalent of the right stick
    public static final KeyMapping LEAN_LEFT = new KeyMapping(
            "key.descentmtb.lean_left", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping LEAN_RIGHT = new KeyMapping(
            "key.descentmtb.lean_right", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    private ModKeyMappings() {}
}
