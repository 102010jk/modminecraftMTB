package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;

/**
 * Client brain for the bike the local player is riding: feeds controls into the
 * simulation, streams the result to the server, keeps respawn points, handles
 * bails (Descenders: crash → short pause → back on the trail a few seconds
 * earlier) and produces the HUD messages.
 */
public final class BikeClientController {
    private record SafePoint(double x, double y, double z, double yaw) {}

    private static MountainBikeEntity riding;
    private static final ArrayDeque<SafePoint> safe = new ArrayDeque<>();
    private static SafePoint start;
    private static int safeTimer, bailTicks;

    private static RiderPose.Trick trick = RiderPose.Trick.NONE;

    public static RiderPose.Trick trick() {
        return trick;
    }

    // ---- HUD feed ----
    public static String message = "";
    public static int messageTicks;
    public static int messageColor = 0xFFFFFF;

    /** Installed as {@link MountainBikeEntity#clientTicker}. */
    public static void tick(MountainBikeEntity bike) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !bike.hasPassenger(player)) {
            bike.remoteTick();
            return;
        }
        if (riding != bike) onMount(bike, player);

        BikeInputHandler.Frame in = (mc.isPaused() || mc.screen != null)
                ? BikeInputHandler.Frame.NONE : BikeInputHandler.poll();

        boolean teleport = false;
        BikeSim sim = bike.sim();
        if (sim != null && sim.bailed) {
            bailTicks++;
            if (bailTicks > 45 || in.respawn()) {
                respawn(bike, false);
                teleport = true;
            }
        } else if (in.respawn()) {
            respawn(bike, false);
            teleport = true;
        }
        if (in.respawnStart()) {
            respawn(bike, true);
            teleport = true;
        }

        bike.driveLocal(in.controls());
        sim = bike.sim();
        trick = (sim.airborne && !sim.bailed && in.controls().trickMod)
                ? RiderPose.trickFor(in.controls().trickX, in.controls().trickY) : RiderPose.Trick.NONE;
        handleEvents(sim);
        recordSafePoint(bike, sim);

        if (in.cycleCamera()) BikeCamera.cycle();
        if (in.resetCamera()) BikeCamera.snapBehind();

        PacketDistributor.sendToServer(bike.statePayload(teleport));
        if (messageTicks > 0) messageTicks--;
    }

    /** Called every client tick to notice dismounts. */
    public static void checkDismount() {
        Minecraft mc = Minecraft.getInstance();
        if (riding != null && (mc.player == null || mc.player.getVehicle() != riding)) {
            riding.stopSim();
            riding = null;
            BikeCamera.onDismount();
        }
    }

    public static MountainBikeEntity riding() {
        return riding;
    }

    private static void onMount(MountainBikeEntity bike, LocalPlayer player) {
        riding = bike;
        safe.clear();
        start = null;
        bailTicks = 0;
        BikeInputHandler.announce(player);
        BikeCamera.onMount();
    }

    private static void handleEvents(BikeSim sim) {
        for (BikeSim.Event e : sim.events) {
            switch (e.type()) {
                case BAIL -> {
                    bailTicks = 0;
                    show("BAIL! " + e.info(), 0xFF5555, 60);
                }
                case LAND -> {
                    if (!sim.bailed && sim.airTime > 0.45) {
                        String trick = trickName(sim);
                        show(String.format(java.util.Locale.ROOT, "%s%.1f s air", trick.isEmpty() ? "" : trick + "  ", sim.airTime),
                                0x55FFFF, 40);
                    }
                }
                default -> {}
            }
        }
        sim.events.clear();
    }

    /** Names the rotation of the jump that just ended (full trick system comes in P6). */
    private static String trickName(BikeSim sim) {
        int flips = (int) Math.round(sim.airPitchTravel / (2 * Math.PI));
        int spin = (int) Math.round(Math.abs(sim.airYawTravel) / Math.PI) * 180;
        StringBuilder sb = new StringBuilder();
        if (spin >= 360) sb.append(spin).append(' ');
        if (flips != 0) {
            int n = Math.abs(flips);
            if (n > 1) sb.append(n == 2 ? "Double " : n == 3 ? "Triple " : n + "x ");
            sb.append(flips > 0 ? "Backflip" : "Frontflip");
        }
        return sb.toString().trim();
    }

    private static void recordSafePoint(MountainBikeEntity bike, BikeSim sim) {
        if (++safeTimer < 10) return;
        safeTimer = 0;
        if (sim.bailed || sim.airborne || !sim.grounded()) return;
        SafePoint p = new SafePoint(bike.getX(), bike.getY(), bike.getZ(), sim.yaw);
        if (start == null) start = p;
        if (sim.speed() < 0.5 && !safe.isEmpty()) return;
        safe.addLast(p);
        while (safe.size() > 40) safe.removeFirst();
    }

    private static void respawn(MountainBikeEntity bike, boolean atStart) {
        SafePoint p = null;
        if (atStart) {
            p = start;
        } else {
            // a few seconds back so you do not re-crash immediately
            int back = Math.min(safe.size(), 6);
            for (int i = 0; i < back && !safe.isEmpty(); i++) p = safe.pollLast();
            if (p != null) safe.addLast(p);
        }
        if (p == null) p = start;
        if (p == null) p = new SafePoint(bike.getX(), bike.getY(), bike.getZ(), Math.toRadians(bike.getYRot()));
        bike.respawnAt(p.x, p.y, p.z, p.yaw);
        bailTicks = 0;
        BikeCamera.snapBehind();
        show(atStart ? "Back to the start" : "Respawned", 0xAAAAAA, 25);
    }

    private static void show(String text, int color, int ticks) {
        message = text;
        messageColor = color;
        messageTicks = ticks;
    }

    private BikeClientController() {}
}
