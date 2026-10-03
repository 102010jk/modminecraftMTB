package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.trail.DevPumpTrack;
import com.descentmtb.trail.PumpShapes;
import com.descentmtb.trail.TrailMath.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.Locale;

/**
 * Development autopilot stage: rides the large closed pumptrack in-game (real blocks, real physics,
 * real client) with a path-following rider and checks that it laps without bailing, staying on the
 * track through the banked turns. Screenshots a straight and a corner.
 */
final class DevPumpRide {
    private static final double TARGET = 7.5, PHASE = Math.toRadians(120);
    private static final int REQUIRED_LAPS = 2, TIMEOUT = 4200;

    private static int stage, wait, ticks;
    private static boolean done, failed, shotCorner, shotStraight;
    private static String why = "";
    private static PumpShapes.Oval oval;
    private static double last, unwrapped, startTheta, maxOff, maxLean;
    private static final int DIR = 1;

    static boolean finished() { return done; }
    static boolean failed() { return failed; }
    static String why() { return why; }
    static boolean riding() { return stage == 3 && !done; }

    static void tick(Minecraft mc, LocalPlayer p, MountainBikeEntity bike, BlockPos o) {
        int x0 = o.getX() - 10, z0 = o.getZ() + 96;
        switch (stage) {
            case 0 -> {
                p.connection.sendCommand("mtbdevpump " + x0 + " " + z0);
                oval = PumpShapes.oval(new Point(x0, DevPumpTrack.Y, z0),
                        new Point(x0 + DevPumpTrack.SIZE_X, DevPumpTrack.Y, z0 + DevPumpTrack.SIZE_Z), DevPumpTrack.PARAMS);
                stage = 1;
                wait = 0;
            }
            case 1 -> {
                if (DevPumpTrack.FAILED) { fail("pumptrack could not be built"); return; }
                if (++wait > 80 && DevPumpTrack.BUILT) stage = 2;
                if (wait > 600) fail("pumptrack build timed out");
            }
            case 2 -> {
                // put the bike on the middle of the first straight, rolling along the direction of travel
                startTheta = -Math.PI / 2;
                double sx = oval.cx() + oval.rx() * Math.cos(startTheta), sz = oval.cz() + oval.rz() * Math.sin(startTheta);
                double tx = -oval.rx() * Math.sin(startTheta) * DIR, tz = oval.rz() * Math.cos(startTheta) * DIR;
                p.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %d %.2f", sx, DevPumpTrack.Y + 2, sz));
                bike.respawnAt(sx, DevPumpTrack.Y + .3, sz, Math.atan2(-tx, tz));
                V3 f = bike.sim().forward();
                bike.sim().vel = bike.sim().riderVel = f.mul(6);
                last = unwrapped = startTheta;
                ticks = 0;
                mc.options.hideGui = true;
                BikeCamera.debugSide = 0;
                stage = 3;
            }
            case 3 -> ride(mc, bike);
            default -> {}
        }
    }

    private static void ride(Minecraft mc, MountainBikeEntity bike) {
        var sim = bike.sim();
        if (sim == null) return;
        ticks++;
        if (sim.bailed) { fail("bailed on the pumptrack: " + sim.bailReason); return; }
        if (ticks > TIMEOUT) { fail(String.format(Locale.ROOT, "only %.2f laps in %d s", Math.abs(unwrapped - startTheta) / (2 * Math.PI), TIMEOUT / 20)); return; }

        double theta = nearest(sim.pos.x, sim.pos.z);
        unwrapped += theta - last;
        last = theta;
        double off = distanceToCentreLine(sim.pos.x, sim.pos.z, theta);
        maxOff = Math.max(maxOff, off);
        maxLean = Math.max(maxLean, Math.abs(Math.toDegrees(sim.lean)));
        double laps = Math.abs(unwrapped - startTheta) / (2 * Math.PI);
        if (ticks % 20 == 0) {
            DescentMtb.LOG.info(String.format(Locale.ROOT, "[pumpride] t=%ds lap %.2f pos(%.1f, %.2f, %.1f) %.1f km/h off %.2f m lean %.0f° air=%s turn=%.2f type=%s",
                    ticks / 20, laps, sim.pos.x, sim.pos.y, sim.pos.z, sim.speed() * 3.6, off, Math.toDegrees(sim.lean), sim.airborne,
                    oval.turnAmount(sim.pos.x, sim.pos.z), bike.bikeType()));
        }
        if (!shotStraight && ticks > 60 && oval.turnAmount(sim.pos.x, sim.pos.z) < .1) {
            BikeCamera.debugSide = 1;
            if (ticks > 70) { shot(mc, "pump_straight"); shotStraight = true; BikeCamera.debugSide = 0; }
        }
        if (!shotCorner && laps > .2 && oval.turnAmount(sim.pos.x, sim.pos.z) > .9 && Math.abs(Math.toDegrees(sim.lean)) > 20) {
            BikeCamera.debugSide = 2;
            shot(mc, "pump_corner");
            shotCorner = true;
        } else if (shotCorner && BikeCamera.debugSide == 2 && oval.turnAmount(sim.pos.x, sim.pos.z) < .5) {
            BikeCamera.debugSide = 0;
        }
        if (laps >= REQUIRED_LAPS) {
            if (maxOff > oval.half() + 1.5) { fail(String.format(Locale.ROOT, "left the track by %.2f m", maxOff)); return; }
            DescentMtb.LOG.info(String.format(Locale.ROOT, "[autopilot] PASS: pumptrack %.1f laps in %d s, max %.2f m off the line, lean up to %.0f°, no bail",
                    laps, ticks / 20, maxOff, maxLean));
            BikeCamera.debugSide = 0;
            done = true;
        }
    }

    /** Scripted rider: pure-pursuit steering along the centre line, pedal to a target speed, pump the rollers. */
    static BikeInputHandler.Frame frame(MountainBikeEntity bike) {
        var sim = bike == null ? null : bike.sim();
        if (sim == null || oval == null) return BikeInputHandler.Frame.NONE;
        double theta = last;
        double speed = sim.vel.horizontalLength();
        double look = 3.0 + .45 * speed;
        double q = theta + look / (.5 * (oval.rx() + oval.rz())) * DIR;
        double px = oval.cx() + oval.rx() * Math.cos(q), pz = oval.cz() + oval.rz() * Math.sin(q);
        V3 fwd = sim.forward().horizontal().normalize(), right = sim.rightAxis().horizontal().normalize();
        V3 d = new V3(px - sim.pos.x, 0, pz - sim.pos.z).normalize();
        double err = Math.atan2(d.dot(right), d.dot(fwd));
        float steer = (float) Math.max(-1, Math.min(1, err * 2.2));
        float pedal = speed < TARGET ? 1 : 0;
        float brake = speed > TARGET + 2.5 ? .5f : 0;
        double spacing = DevPumpTrack.PARAMS.spacing();
        float body = (float) (Math.cos(2 * Math.PI * (oval.distance(sim.pos.x, sim.pos.z) - spacing / 2) / spacing * DIR + PHASE)
                * (1 - oval.turnAmount(sim.pos.x, sim.pos.z)));
        return new BikeInputHandler.Frame(new Controls(steer, 0, pedal, brake, body, 0, false, 0, 0), false, false, false, false);
    }

    private static double nearest(double x, double z) {
        double best = 1e18, th = last;
        for (int k = -40; k <= 40; k++) {
            double qq = last + k * .0045;
            double ex = oval.cx() + oval.rx() * Math.cos(qq) - x, ez = oval.cz() + oval.rz() * Math.sin(qq) - z;
            double dd = ex * ex + ez * ez;
            if (dd < best) { best = dd; th = qq; }
        }
        return th;
    }

    private static double distanceToCentreLine(double x, double z, double theta) {
        return Math.hypot(oval.cx() + oval.rx() * Math.cos(theta) - x, oval.cz() + oval.rz() * Math.sin(theta) - z);
    }

    private static void fail(String reason) { failed = true; why = reason; }

    private static void shot(Minecraft mc, String name) {
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mtb_" + name + ".png", mc.getMainRenderTarget(),
                msg -> DescentMtb.LOG.info("[autopilot] {}", msg.getString()));
    }

    private DevPumpRide() {}
}
