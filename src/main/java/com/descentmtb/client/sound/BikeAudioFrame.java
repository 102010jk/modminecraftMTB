package com.descentmtb.client.sound;

import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;

/**
 * Everything the bike sounds need to know about one bike at one tick - gathered from the rider's simulation on the
 * riding client and from the interpolated render state on every other client. Plain data, no Minecraft classes.
 */
public final class BikeAudioFrame {
    /** Speed over the ground (m/s). */
    public double speed;
    /** Rear wheel angular speed (rad/s, + = rolling forward). */
    public double rearOmega;
    /** The rider is pedalling right now (the freehub is engaged, so it cannot click). */
    public boolean pedalling;
    public boolean airborne, bailed;
    /** A living rider is currently controlling this bike; an empty falling bike has no rider voice. */
    public boolean ridden;
    public boolean frontContact, rearContact;
    /** Surface under each tyre (only meaningful while that tyre touches the ground). */
    public Terrain.Surface frontSurface = Terrain.Surface.DIRT, rearSurface = Terrain.Surface.DIRT;
    /** Fork / rear shock compression speed (m/s, + = compressing). */
    public double forkVel, shockVel;
    public boolean fullSuspension;
    /** Dirt bike engine: revs, throttle (0..1), on the rev limiter, a backfire this tick. Zero on a bicycle. */
    public double engineRpm, throttle;
    public boolean limiting, backfire, motor;
    /** How much higher (small engine) and louder (race exhaust) this engine sounds than the stock dirt bike. */
    public double enginePitch = 1, engineLoud = 1;
    /** Rear tyre scrubbing: 0 gripping … 1 full slide; {@code skidLocked} = the brake has stopped the wheel turning. */
    public double skid;
    public boolean skidLocked;
    /** A pair of skis instead of a bike: no hub, tyres or engine; the bases glide on snow and grind on rock. */
    public boolean ski;
    /** Skis: how hard the bases grind on a surface that is not snow or ice right now (0..1). */
    public double skiScrape;
    // ---- crash prediction inputs ----
    public V3 vel = V3.ZERO;
    /** Seconds until the wheels touch down (infinity = not within the prediction window). */
    public double timeToGround = Double.POSITIVE_INFINITY;
    /** Signed pitch / yaw error against the landing slope / direction of travel (rad), and their rates (rad/s). */
    public double pitchError, yawError, pitchRate, yawRate;
    public double airTime;
    /** Bail thresholds of this bike (from its parameters). */
    public double bailPitch = Math.toRadians(100), bailYaw = Math.toRadians(115), bailImpact = 13.5;
}
