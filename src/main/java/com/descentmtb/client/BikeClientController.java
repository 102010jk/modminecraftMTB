package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.trail.TrailTimer;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.BikeBailPayload;
import com.descentmtb.network.BikeRespawnPayload;
import com.descentmtb.network.BikeResyncPayload;
import com.descentmtb.network.BikeStartPointPayload;
import com.descentmtb.network.IdleSendThrottle;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.Controls;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.TrailSignEntity;
import com.descentmtb.trail.TrailSignRegistry;
import com.descentmtb.trick.Trick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Client brain for the bike the local player is riding: feeds controls into the simulation, streams the result
 * to the server and produces the HUD messages.
 *
 * <p>The server has the last word on where the bike is. Respawning (R: the last safe point, Backspace: the
 * start) is a request the server carries out; it answers with a {@link BikeResyncPayload}, as it does when it
 * rejects a state packet, and the simulation is put wherever that says. Every state packet carries the epoch of
 * the last such answer, so the server can drop packets that were already on their way.
 */
public final class BikeClientController {
    /** After a crash the rider may ask for a respawn after this many ticks, even if the server never threw them off. */
    private static final int BAIL_RESPAWN_TICKS = 40;
    /** Respawn requests closer together than this (ticks) are not sent; the server has a cooldown anyway. */
    private static final int RESPAWN_REQUEST_TICKS = 10;
    /** A START sign is looked for this far (blocks) around the start point the trail timer reports. */
    private static final double START_SIGN_SEARCH = 3;

    private static MountainBikeEntity riding;
    private static final IdleSendThrottle sendThrottle = new IdleSendThrottle();
    /** The epoch of the last {@link BikeResyncPayload} heard; echoed in every state and bail packet. */
    private static int epoch;
    private static int bailTicks, respawnCooldown;
    /** The bike was just repositioned: send the next state at once, even if it is standing still. */
    private static boolean repositioned;
    private static String airLabel = "";

    // ---- HUD feed ----
    public static String message = "";
    public static int messageTicks;
    public static int messageColor = 0xFFFFFF;

    static {
        // Installed here rather than in DescentMtbClient: this class is loaded on the first client tick
        // (checkDismount), long before the server can answer anything.
        BikeResyncPayload.clientHandler = BikeClientController::onResync;
    }

    /** Installed as {@link MountainBikeEntity#clientTicker}. */
    public static void tick(MountainBikeEntity bike) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !bike.hasPassenger(player)) {
            bike.remoteTick();
            return;
        }
        if (riding != bike) onMount(bike);
        ClientConfig.apply(bike);

        BikeInputHandler.Frame in = DevAutopilot.active() ? DevAutopilot.frame()
                : (mc.isPaused() || mc.screen != null) ? BikeInputHandler.Frame.NONE : BikeInputHandler.poll();
        bike.params().steerResponse = BikeInputHandler.instantKeyboardSteering ? 0 : bike.bikeType().params().steerResponse;
        if (respawnCooldown > 0) respawnCooldown--;

        BikeSim sim = bike.sim();
        if (sim != null && sim.bailed) {
            // Crashed: the server throws us off (ragdoll). Should that never happen (a lost or rejected bail),
            // the rider is not stuck: after a moment the respawn keys work again.
            bailTicks++;
            if (bailTicks >= BAIL_RESPAWN_TICKS) handleRespawnKeys(in);
            tickMessage();
            return;
        }
        handleRespawnKeys(in);

        bike.driveLocal(in.controls());
        sim = bike.sim();
        // The server needs the crash frame (BAILED flag) before the bail packet, so the state goes out first.
        sendState(bike, sim, in.controls());
        handleEvents(sim);

        if (in.cycleCamera()) BikeCamera.cycle();
        if (in.resetCamera()) BikeCamera.snapBehind();
        tickMessage();
    }

    private static void handleRespawnKeys(BikeInputHandler.Frame in) {
        if (in.respawnStart()) requestRespawn(true);
        else if (in.respawn()) requestRespawn(false);
    }

    /** Asks the server to put us back; the answer arrives as a {@link BikeResyncPayload}. */
    private static void requestRespawn(boolean atStart) {
        if (respawnCooldown > 0) return;
        respawnCooldown = RESPAWN_REQUEST_TICKS;
        PacketDistributor.sendToServer(new BikeRespawnPayload(atStart));
    }

    private static void sendState(MountainBikeEntity bike, BikeSim sim, Controls c) {
        boolean resting = sim.speed() < .05 && !sim.airborne && sim.grounded() && !repositioned;
        if (sendThrottle.shouldSend(resting, c.steer, c.lean, c.body, c.tweak)) {
            PacketDistributor.sendToServer(bike.statePayload(epoch));
            repositioned = false;
        }
    }

    private static void tickMessage() {
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

    private static void onMount(MountainBikeEntity bike) {
        riding = bike;
        epoch = 0;                          // the server starts a fresh session on every mount
        bailTicks = 0;
        respawnCooldown = 0;
        repositioned = false;
        sendThrottle.reset();
        Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
        BikeCamera.onMount();
        DevAutopilot.prepareBike(bike);
    }

    // ------------------------------------------------------------------ server answers

    /** The server moved our bike (respawn) or overruled a state packet (resync): follow it. */
    private static void onResync(BikeResyncPayload m) {
        MountainBikeEntity bike = riding;
        if (bike == null || bike.getId() != m.entityId()) return;   // we got off in the meantime
        epoch = m.epoch();
        bike.respawnAt(m.x(), m.y(), m.z(), m.yawRad());
        bailTicks = 0;
        repositioned = true;
        switch (m.kind()) {
            case BikeResyncPayload.RESPAWN -> respawned("descentmtb.msg.respawned");
            case BikeResyncPayload.RESPAWN_START -> {
                TrailTimer.onRespawnAtStart(false);
                respawned("descentmtb.msg.back_to_start");
            }
            case BikeResyncPayload.RESPAWN_SIGN -> {
                TrailTimer.onRespawnAtStart(true);
                respawned("descentmtb.msg.back_to_start");
            }
            default -> {}                   // a plain resync: no message, the bike just jumps back
        }
    }

    private static void respawned(String key) {
        BikeCamera.snapBehind();
        show(Component.translatable(key).getString(), 0xAAAAAA, 25);
    }

    // ------------------------------------------------------------------ sim events and HUD

    private static void handleEvents(BikeSim sim) {
        for (BikeSim.Event e : sim.events) {
            switch (e.type()) {
                case BAIL -> {
                    bailTicks = 0;
                    DescentMtb.LOG.info("[bike] bail: {} at {} speed {} m/s", e.info(), sim.pos, String.format(Locale.ROOT, "%.1f", e.value()));
                    show(Component.translatable("descentmtb.msg.bail", e.info()).getString(), 0xFF5555, 60);
                    PacketDistributor.sendToServer(new BikeBailPayload(riding.getId(),
                            sim.crashRiderPos.x, sim.crashRiderPos.y, sim.crashRiderPos.z,
                            (float) sim.crashRiderVel.x, (float) sim.crashRiderVel.y, (float) sim.crashRiderVel.z, epoch));
                }
                case LAND -> {
                    if (!sim.bailed && sim.airTime > 0.45) showLanding(sim);
                }
                case HIT -> {
                    if (DevAutopilot.ENABLED) DescentMtb.LOG.info("[bike] hit {} m/s: {}", String.format(Locale.ROOT, "%.1f", e.value()), e.info());
                }
                case TAKEOFF -> airLabel = "";
                default -> {}
            }
        }
        if (DevAutopilot.ENABLED && riding.tickCount % 10 == 0) {
            DescentMtb.LOG.info("[bike] pos {} v={} air={} F[{} {}] R[{} {}]", sim.pos, String.format(Locale.ROOT, "%.1f", sim.speed()), sim.airborne,
                    sim.front.contact, String.format(Locale.ROOT, "%.2f", sim.front.compression),
                    sim.rear.contact, String.format(Locale.ROOT, "%.2f", sim.rear.compression));
        }
        sim.events.clear();
        if (sim.airborne && sim.airTime > .35) {
            String label = sim.wallRide ? "Wallride" : trickName(sim);
            if (!label.isEmpty() && !label.equals(airLabel)) {
                TrickToast.show(label, Component.translatable("descentmtb.msg.in_the_air").getString());
                airLabel = label;
            }
        }
    }

    private static void showLanding(BikeSim sim) {
        String trick = trickName(sim);
        String air = String.format(Locale.ROOT, "%.1f", sim.airTime);
        show(trick.isEmpty() ? Component.translatable("descentmtb.msg.air", air).getString()
                : Component.translatable("descentmtb.msg.air_trick", trick, air).getString(), 0x55FFFF, 40);
        if (!trick.isEmpty()) TrickToast.show(trick, Component.translatable("descentmtb.msg.landed", air).getString());
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
        if (sim.maxTable > .75 && (sim.trickMask & (1 << Trick.TABLETOP.ordinal())) == 0) appendTrick(sb, "Tabletop");
        for (Trick trick : Trick.values())
            if (trick != Trick.NONE && (sim.trickMask & (1 << trick.ordinal())) != 0) appendTrick(sb, trick.displayName);
        return sb.toString().trim();
    }

    private static void appendTrick(StringBuilder sb, String name) {
        if (!sb.isEmpty()) sb.append(" + ");
        sb.append(name);
    }

    // ------------------------------------------------------------------ trail start

    /**
     * Called by the trail timer when the rider arms a START sign; {@code (x, y, z)} is the start point beside
     * it. Tells the server which sign it is, so that "respawn at start" (Backspace) can return there. The
     * server checks the sign itself and works out the spot on its own.
     */
    public static void setStartPoint(double x, double y, double z, double yaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos sign = null;
        double best = Double.MAX_VALUE;
        for (TrailSignEntity s : TrailSignRegistry.near(mc.level, x, y, z, START_SIGN_SEARCH, SignContent.Type.START)) {
            double d = s.getBlockPos().distToCenterSqr(x, y, z);
            if (d < best) {
                best = d;
                sign = s.getBlockPos();
            }
        }
        if (sign != null) PacketDistributor.sendToServer(new BikeStartPointPayload(sign.immutable()));
    }

    /**
     * Called by the trail timer when it forgets its start (new level). Nothing to do here: the server keeps the
     * armed sign per player and ignores it in another dimension or once the sign is gone.
     */
    public static void clearStartPoint() {}

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
