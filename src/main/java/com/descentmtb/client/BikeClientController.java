package com.descentmtb.client;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.descentmtb.network.BikeBailPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;

/**
 * Client brain for the bike the local player is riding: feeds controls into the
 * simulation, streams the result to the server, keeps manual reset points,
 * starts a ragdoll after a crash and produces the HUD messages.
 */
public final class BikeClientController {
    private record SafePoint(double x, double y, double z, double yaw) {}

    private static MountainBikeEntity riding;
    private static final ArrayDeque<SafePoint> safe = new ArrayDeque<>();
    private static SafePoint start;
    private static int safeTimer, bailTicks, idleTicks;
    private static String airLabel = "";

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
        ClientConfig.apply(bike);

        BikeInputHandler.Frame in = DevAutopilot.active() ? DevAutopilot.frame()
                : (mc.isPaused() || mc.screen != null) ? BikeInputHandler.Frame.NONE : BikeInputHandler.poll();

        boolean teleport = false;
        BikeSim sim = bike.sim();
        if (sim != null && sim.bailed) {
            // crashed: the server throws us off (ragdoll); just wait for the dismount
            bailTicks++;
            return;
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
        // The server needs the crash frame before the bail detaches the rider.
        // a parked bike changes nothing: a few updates a second are plenty
        boolean resting = sim.speed() < .05 && !sim.airborne && sim.grounded() && !teleport;
        idleTicks = resting ? idleTicks + 1 : 0;
        if (!resting || idleTicks % 10 == 1) {
            PacketDistributor.sendToServer(bike.statePayload(teleport));
        }
        handleEvents(sim);
        recordSafePoint(bike, sim);

        if (in.cycleCamera()) BikeCamera.cycle();
        if (in.resetCamera()) BikeCamera.snapBehind();

        if (messageTicks > 0) messageTicks--;
    }

    /** Called every client tick to notice dismounts. */
    public static void checkDismount() {
        DevAutopilot.clientTick();
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
        net.minecraft.client.Minecraft.getInstance().gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(),false);
        BikeCamera.onMount();
        DevAutopilot.prepareBike(bike);
    }

    private static void handleEvents(BikeSim sim) {
        for (BikeSim.Event e : sim.events) {
            switch (e.type()) {
                case BAIL -> {
                    bailTicks = 0;
                    com.descentmtb.DescentMtb.LOG.info("[bike] bail: {} at {} speed {} m/s", e.info(), sim.pos, String.format("%.1f", e.value()));
                    show("BAIL! " + e.info(), 0xFF5555, 60);
                    PacketDistributor.sendToServer(new BikeBailPayload(riding.getId(),
                            sim.crashRiderPos.x, sim.crashRiderPos.y, sim.crashRiderPos.z,
                            (float) sim.crashRiderVel.x, (float) sim.crashRiderVel.y, (float) sim.crashRiderVel.z));
                }
                case LAND -> {
                    if (!sim.bailed && sim.airTime > 0.45) {
                        String trick = trickName(sim);
                        show(String.format(java.util.Locale.ROOT, "%s%.1f s air", trick.isEmpty() ? "" : trick + "  ", sim.airTime),
                                0x55FFFF, 40);
                        if (!trick.isEmpty()) TrickToast.show(trick, "LANDED  •  " + String.format(java.util.Locale.ROOT, "%.1f s AIR", sim.airTime));
                    }
                }
                case HIT -> {
                    if (DevAutopilot.ENABLED) com.descentmtb.DescentMtb.LOG.info("[bike] hit {} m/s: {}", String.format("%.1f", e.value()), e.info());
                }
                case TAKEOFF -> airLabel = "";
                default -> {}
            }
        }
        if (DevAutopilot.ENABLED && riding.tickCount % 10 == 0) {
            com.descentmtb.DescentMtb.LOG.info("[bike] pos {} v={} air={} F[{} {}] R[{} {}]", sim.pos, String.format("%.1f", sim.speed()), sim.airborne,
                    sim.front.contact, String.format("%.2f", sim.front.compression), sim.rear.contact, String.format("%.2f", sim.rear.compression));
        }
        sim.events.clear();
        if (sim.airborne && sim.airTime > .35) {
            String label = sim.wallRide ? "Wallride" : trickName(sim);
            if (!label.isEmpty() && !label.equals(airLabel)) { TrickToast.show(label, "IN THE AIR"); airLabel = label; }
        }
    }

    /** Names the flip/spin of the jump that just ended. */
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
        if (sim.maxWhip > .6 && spin < 180) appendTrick(sb, "Whip");
        if (sim.maxTable > .75 && (sim.trickMask & (1 << com.descentmtb.trick.Trick.TABLETOP.ordinal())) == 0) appendTrick(sb, "Tabletop");
        for (var trick : com.descentmtb.trick.Trick.values())
            if (trick != com.descentmtb.trick.Trick.NONE && (sim.trickMask & (1 << trick.ordinal())) != 0) appendTrick(sb, trick.displayName);
        return sb.toString().trim();
    }

    private static void appendTrick(StringBuilder sb, String name) { if (!sb.isEmpty()) sb.append(" + "); sb.append(name); }

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

    public static void toast(String text) {
        show(text, 0xFFFFFF, 30);
    }

    private static void show(String text, int color, int ticks) {
        message = text;
        messageColor = color;
        messageTicks = ticks;
    }

    private BikeClientController() {}
}
