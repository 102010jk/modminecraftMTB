package com.descentmtb.trail;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.BlockTerrain;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.world.McColumns;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Development-only: rides a path with the real bike physics over the REAL blocks of a level (through the same
 * terrain adapter the game uses), with a path-following rider. It runs headless on the server thread, so a
 * whole track is checked in a moment without a client, a camera or a teleport.
 */
public final class DevRideSim {
    public record Result(boolean bailed, String why, double laps, double seconds, double minSpeed, double avgSpeed,
                         double maxOffLine, double maxLeanDeg, double airFraction) {
        public String summary() {
            return String.format(Locale.ROOT, "laps %.2f in %.0f s | speed min %.1f avg %.1f m/s | off the line max %.2f m | lean max %.0f° | air %.0f%% | bail=%s %s",
                    laps, seconds, minSpeed, avgSpeed, maxOffLine, maxLeanDeg, airFraction * 100, bailed, why);
        }
    }

    private static final double DT = 0.05;

    /**
     * @param path     dense polyline (x, z) of the centre line, at most about 0.5 m between points
     * @param closed   true for a loop (distance is counted in laps), false for a one-way path
     * @param groundY  height to start from (the ground search runs around it)
     * @param pump     optional body input (-1 bend .. +1 stretch) for pumping, may be null
     */
    public static Result ride(Level level, double[][] path, boolean closed, double groundY, double seconds, double target,
                              BikeType type, ToDoubleFunction<BikeSim> pump) {
        McColumns columns = new McColumns(level);
        BikeSim sim = new BikeSim(type.params(), new BlockTerrain(columns));
        double[] p0 = path[0], p1 = path[Math.min(path.length - 1, 4)];
        sim.place(p0[0], groundY, p0[1], Math.atan2(-(p1[0] - p0[0]), p1[1] - p0[1]));
        sim.vel = sim.forward().mul(target * .8);
        sim.riderVel = sim.vel;

        int n = path.length, index = 0, steps = (int) (seconds / DT);
        long travelled = 0;
        double minV = 1e9, sumV = 0, maxOff = 0, maxLean = 0;
        int air = 0, ticks = 0;
        for (int i = 0; i < steps && !sim.bailed; i++) {
            columns.newTick();
            // nearest path point in a window ahead of the last one
            double best = 1e18;
            int bestIndex = index;
            for (int k = -10; k <= 40; k++) {
                int idx = closed ? Math.floorMod(index + k, n) : Math.max(0, Math.min(n - 1, index + k));
                double dx = path[idx][0] - sim.pos.x, dz = path[idx][1] - sim.pos.z;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    bestIndex = idx;
                }
            }
            int advance = closed ? Math.floorMod(bestIndex - index + n / 2, n) - n / 2 : bestIndex - index;
            travelled += advance;
            index = bestIndex;

            double speed = sim.vel.horizontalLength();
            int ahead = (int) Math.max(6, (3.0 + .45 * speed) / .5);
            int idx = closed ? Math.floorMod(index + ahead, n) : Math.min(n - 1, index + ahead);
            V3 fwd = sim.forward().horizontal().normalize(), right = sim.rightAxis().horizontal().normalize();
            V3 want = new V3(path[idx][0] - sim.pos.x, 0, path[idx][1] - sim.pos.z).normalize();
            double err = Math.atan2(want.dot(right), want.dot(fwd));
            float steer = (float) Math.max(-1, Math.min(1, err * 2.2));
            float pedal = speed < target ? 1 : 0;
            float brake = speed > target + 2.5 ? .5f : 0;
            float body = pump == null ? 0 : (float) pump.applyAsDouble(sim);
            sim.tick(new Controls(steer, 0, pedal, brake, body, 0, false, 0, 0), DT);
            sim.events.clear();

            maxOff = Math.max(maxOff, Math.sqrt(best));
            maxLean = Math.max(maxLean, Math.abs(Math.toDegrees(sim.lean)));
            minV = Math.min(minV, sim.speed());
            sumV += sim.speed();
            if (sim.airborne) air++;
            ticks++;
            if (!closed && index >= n - 3) {
                break;   // reached the end of a one-way path
            }
        }
        double laps = closed ? travelled / (double) n : index / (double) Math.max(1, n - 1);
        return new Result(sim.bailed, sim.bailReason, laps, ticks * DT, minV, sumV / Math.max(1, ticks), maxOff, maxLean,
                air / (double) Math.max(1, ticks));
    }

    private DevRideSim() {}
}
