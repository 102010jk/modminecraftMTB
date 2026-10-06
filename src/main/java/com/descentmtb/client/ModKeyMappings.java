package com.descentmtb.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Keyboard bindings, defaulting to the Descenders PC layout (all rebindable in
 * Options → Controls → Descent MTB). The keys are read straight from the
 * window while riding, so it does not matter that S / D / Space also have a
 * vanilla meaning.
 *
 * <p>All riding bindings live in a {@link RidingContext} that is only active while the local player sits on a bike,
 * so the Controls screen does not flag them as conflicting with the vanilla walking keys (S, D, Space, ...).
 *
 * <p>One deliberate difference: Descenders uses Shift for "tweak", but Shift is
 * Minecraft's dismount key, so tweak defaults to Space (the brake on the ground, the table in the air).
 */
public final class ModKeyMappings {
    public static final String CATEGORY = "key.categories.descentmtb";

    /**
     * Conflict context of the riding keys. Two riding bindings only conflict when they are in the same group; the
     * brake and the tweak share Space on purpose (ground / air), so they sit in different groups.
     */
    public static final class RidingContext implements IKeyConflictContext {
        /** Most riding keys. */
        public static final RidingContext RIDING = new RidingContext();
        /** The tweak, which shares its default key with the brake. */
        public static final RidingContext RIDING_AIR = new RidingContext();

        private RidingContext() {}

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && BikeClientController.riding() != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    }

    public static final KeyMapping TRAIL_MENU = key("trail_menu", KeyConflictContext.IN_GAME, KeyModifier.NONE, GLFW.GLFW_KEY_G);
    public static final KeyMapping TRAIL_UNDO = key("trail_undo", KeyConflictContext.IN_GAME, KeyModifier.CONTROL, GLFW.GLFW_KEY_Z);
    public static final KeyMapping ACCELERATE = riding("accelerate", GLFW.GLFW_KEY_Z);
    public static final KeyMapping BRAKE = riding("brake", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping TWEAK = key("tweak", RidingContext.RIDING_AIR, KeyModifier.NONE, GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping STEER_LEFT = riding("steer_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping STEER_RIGHT = riding("steer_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping LEAN_FORWARD = riding("lean_forward", GLFW.GLFW_KEY_UP);
    public static final KeyMapping LEAN_BACK = riding("lean_back", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping BUNNY_HOP = riding("bunny_hop", GLFW.GLFW_KEY_X);
    public static final KeyMapping BEND = riding("bend", GLFW.GLFW_KEY_S);
    public static final KeyMapping STRETCH = riding("stretch", GLFW.GLFW_KEY_D);
    public static final KeyMapping TRICK = riding("trick", GLFW.GLFW_KEY_C);
    /**
     * Independent trick keys (scheme INDEPENDENT), one per trick slot, all on the right hand so the left keeps the arrows:
     * <pre>
     *   U Heelclicker   I up            O up+side
     *                   J side          L down+side      K down
     * </pre>
     * The slot's trick depends on the bike (see {@code BikeType}): up = No Hander / Tuck No Hander, up+side = Tabletop,
     * side = Nac Nac / Barspin, down+side = Can Can / Tailwhip, down = Superman / Superman Seatgrab.
     * In the CLASSIC scheme only the Heelclicker key is used (the rest is C + arrows).
     */
    public static final KeyMapping TRICK_UP = riding("trick_up", GLFW.GLFW_KEY_I);
    public static final KeyMapping TRICK_UP_SIDE = riding("trick_up_side", GLFW.GLFW_KEY_O);
    public static final KeyMapping TRICK_SIDE = riding("trick_side", GLFW.GLFW_KEY_J);
    public static final KeyMapping TRICK_DOWN_SIDE = riding("trick_down_side", GLFW.GLFW_KEY_L);
    public static final KeyMapping TRICK_DOWN = riding("trick_down", GLFW.GLFW_KEY_K);
    public static final KeyMapping TRICK_HEEL = riding("trick_heel", GLFW.GLFW_KEY_U);
    /** In slot order of {@code BikeType.trickAt}. */
    public static final List<KeyMapping> TRICK_KEYS = List.of(TRICK_UP, TRICK_UP_SIDE, TRICK_SIDE, TRICK_DOWN_SIDE, TRICK_DOWN, TRICK_HEEL);
    public static final KeyMapping RESPAWN = riding("respawn", GLFW.GLFW_KEY_R);
    public static final KeyMapping RESPAWN_START = riding("respawn_start", GLFW.GLFW_KEY_BACKSPACE);
    public static final KeyMapping CAMERA = riding("camera", GLFW.GLFW_KEY_V);
    public static final KeyMapping RESET_CAMERA = riding("reset_camera", GLFW.GLFW_KEY_B);
    /** Rings the bell of the bike's build (customisation); see client.custom.BikeBellClient. */
    public static final KeyMapping BELL = riding("bell", GLFW.GLFW_KEY_H);

    public static final List<KeyMapping> ALL = List.of(ACCELERATE, BRAKE, TWEAK, STEER_LEFT, STEER_RIGHT,
            LEAN_FORWARD, LEAN_BACK, BUNNY_HOP, BEND, STRETCH, TRICK, TRICK_UP, TRICK_UP_SIDE, TRICK_SIDE, TRICK_DOWN_SIDE, TRICK_DOWN, TRICK_HEEL, RESPAWN, RESPAWN_START, CAMERA, RESET_CAMERA, BELL, TRAIL_MENU,
            TRAIL_UNDO);

    private static KeyMapping riding(String name, int glfwKey) {
        return key(name, RidingContext.RIDING, KeyModifier.NONE, glfwKey);
    }

    private static KeyMapping key(String name, IKeyConflictContext context, KeyModifier modifier, int glfwKey) {
        return new KeyMapping("key.descentmtb." + name, context, modifier, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    private ModKeyMappings() {}
}
