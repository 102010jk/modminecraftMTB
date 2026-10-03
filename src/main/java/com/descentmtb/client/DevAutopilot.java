package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Controls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;

/**
 * Development-only test pilot ({@code -Ddescentmtb.autopilot=true}, see the
 * {@code runClientAuto} Gradle run): mounts a bike in the test world, rides a
 * short script and saves screenshots of each camera to {@code run/screenshots},
 * then quits. Lets the developer check visuals without taking over the screen.
 * Inert in normal play.
 */
public final class DevAutopilot {
    public static final boolean ENABLED = Boolean.getBoolean("descentmtb.autopilot");

    private static int tick;
    private static int rideTick = -1;

    static boolean active() {
        return ENABLED && rideTick >= 0;
    }

    /** Called every client tick. */
    static void clientTick() {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        tick++;
        if (tick == 40) {
            p.connection.sendCommand("time set 6000");
            p.connection.sendCommand("weather clear");
            if (!(p.getVehicle() instanceof MountainBikeEntity)) {
                p.connection.sendCommand("summon descentmtb:mountain_bike ~2 ~1 ~ {Rotation:[" + p.getYRot() + "f,0f]}");
            }
        }
        if (tick == 70 && !(p.getVehicle() instanceof MountainBikeEntity)) {
            p.connection.sendCommand("ride @s mount @e[type=descentmtb:mountain_bike,limit=1,sort=nearest]");
        }
        if (p.getVehicle() instanceof MountainBikeEntity) {
            if (rideTick < 0) rideTick = 0;
            rideTick++;
            script(mc, rideTick);
        }
    }

    /** Scripted controls (replaces the gamepad / keyboard while active). */
    static BikeInputHandler.Frame frame() {
        int t = rideTick;
        float pedal = t < 140 ? 0.8f : 0.3f;
        float steer = (float) Math.sin(t * 0.03) * 0.25f;
        float body = 0;
        if (t >= 200 && t < 208) body = -1;          // bend ...
        if (t >= 208 && t < 214) body = 1;           // ... and pop
        if (t >= 300 && t < 340) body = -1;          // crouched screenshot
        return new BikeInputHandler.Frame(new Controls(steer, 0, pedal, 0, body, 0, false, 0, 0),
                false, false, false, false);
    }

    private static void script(Minecraft mc, int t) {
        switch (t) {
            case 50 -> mc.options.hideGui = true;
            case 60 -> shot(mc, "fp_standing");
            case 70 -> { BikeCamera.debugSide = 1; }
            case 80 -> shot(mc, "side_right");
            case 85 -> { BikeCamera.debugSide = 2; }
            case 95 -> shot(mc, "front");
            case 100 -> { BikeCamera.debugSide = -1; }
            case 110 -> { shot(mc, "side_left"); BikeCamera.debugSide = 0; }
            case 302 -> BikeCamera.debugSide = 1;
            case 315 -> { shot(mc, "side_crouch"); BikeCamera.debugSide = 0; }
            case 120 -> shot(mc, "fp_riding");
            case 205 -> shot(mc, "fp_bend");
            case 230 -> {
                BikeCamera.cycle();                // -> third person
            }
            case 250 -> shot(mc, "tp_chase");
            case 270 -> BikeCamera.cycle();        // -> far
            case 290 -> shot(mc, "tp_far");
            case 295 -> BikeCamera.cycle();        // -> helmet again
            case 330 -> shot(mc, "fp_crouch");
            case 360 -> {
                DescentMtb.LOG.info("[autopilot] done");
                mc.stop();
            }
            default -> {}
        }
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "mtb_" + name + ".png", mc.getMainRenderTarget(),
                msg -> DescentMtb.LOG.info("[autopilot] {}", msg.getString()));
    }

    private DevAutopilot() {}
}
