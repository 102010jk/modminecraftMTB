package com.descentmtb.client.sound;

import com.descentmtb.client.BikeClientController;
import com.descentmtb.client.ClientConfig;
import com.descentmtb.entity.BikeRenderState;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;
import com.descentmtb.world.McColumns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Drives the sounds of the bikes around the player once per client tick: the bike being ridden (heard directly)
 * and up to {@link #MAX_REMOTE} other bikes within {@link #HEAR_RANGE} blocks (heard from where they are). Each
 * bike gets a {@link BikeVoice}; this class gathers its {@link BikeAudioFrame} - from the physics on the riding
 * client, from the interpolated render state for everyone else - and hands it over.
 *
 * <p>Rules (details in {@link BikeSoundMath}, {@link CrashPredictor}, {@link ScreamTrigger}):
 * <ul>
 *   <li>freehub: coasting only (silent the moment you pedal, at a crash, with the Onyx sprag clutch); single clicks
 *       at walking speed, the buzz loop above that, pitched by the hub's engagement points;</li>
 *   <li>wind from speed (strong above ~35 km/h) and a rush in the air;</li>
 *   <li>tyre roll per surface family, only while a tyre touches the ground, fading out in the air;</li>
 *   <li>suspension hiss on fork / shock compression spikes (landings) and the snap back on take-off;</li>
 *   <li>the rider's scream 0.5-1 s before a crash that cannot be avoided, cut by a clean landing;</li>
 *   <li>heel click / barspin / tailwhip sounds when the trick starts.</li>
 * </ul>
 */
public final class BikeSoundController {
    /** Other bikes are only heard (and simulated for sound) inside this distance, blocks. */
    public static final double HEAR_RANGE = 18;
    /** At most this many other bikes make sound at once: each voice is up to five sound instances. */
    public static final int MAX_REMOTE = 3;

    private static final Map<Integer, BikeVoice> VOICES = new HashMap<>();
    private static final BikeAudioFrame FRAME = new BikeAudioFrame();
    private static McColumns columns;
    private static ClientLevel columnsLevel;

    /** Called at the end of every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !ClientConfig.SPEC.isLoaded()) {
            shutdown();
            return;
        }
        if (mc.isPaused()) return;
        if (columns == null || columnsLevel != level) {
            columns = new McColumns(level);
            columnsLevel = level;
        }
        columns.newTick();

        BikeVoice.Settings settings = new BikeVoice.Settings(ClientConfig.BIKE_SOUND_VOLUME.get(), ClientConfig.HUB_SOUND.get(),
                ClientConfig.WIND_SOUND.get(), ClientConfig.SCREAM_SOUND.get(), ClientConfig.RIDER_VOICE.get());

        MountainBikeEntity riding = BikeClientController.riding();
        List<MountainBikeEntity> chosen = new ArrayList<>(MAX_REMOTE + 1);
        if (riding != null && riding.isSimulating() && !riding.isRemoved()) chosen.add(riding);
        else riding = null;
        List<MountainBikeEntity> near = level.getEntitiesOfClass(MountainBikeEntity.class,
                mc.player.getBoundingBox().inflate(HEAR_RANGE), b -> !b.isRemoved() && b != BikeClientController.riding());
        near.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        for (int i = 0; i < near.size() && i < MAX_REMOTE; i++) chosen.add(near.get(i));

        // voices of bikes no longer chosen (dismounted, out of range, removed) are shut down
        for (Iterator<Map.Entry<Integer, BikeVoice>> it = VOICES.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            BikeVoice voice = entry.getValue();
            boolean keep = false;
            for (MountainBikeEntity b : chosen) {
                if (b == voice.bike && (b == riding) == voice.local) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                voice.shutdown();
                it.remove();
            }
        }

        for (MountainBikeEntity bike : chosen) {
            boolean local = bike == riding;
            BikeVoice voice = VOICES.get(bike.getId());
            if (voice == null) {
                voice = new BikeVoice(bike, local);
                VOICES.put(bike.getId(), voice);
            }
            collect(bike, local, voice.airTicks, FRAME);
            voice.update(FRAME, bike.build().hub(), settings);
        }
    }

    /** Stops every sound of every bike (left the world, closed the game). */
    public static void shutdown() {
        if (VOICES.isEmpty()) return;
        VOICES.values().forEach(BikeVoice::shutdown);
        VOICES.clear();
        columns = null;
        columnsLevel = null;
    }

    // ------------------------------------------------------------------ frame

    /** Fills the frame of a bike for this tick; the rider's own bike reads the simulation, others the render state. */
    private static void collect(MountainBikeEntity bike, boolean local, int airTicks, BikeAudioFrame f) {
        BikeRenderState cur = bike.rsCur, prev = bike.rsPrev;
        BikeParams p = bike.params();
        BikeSim sim = local ? bike.sim() : null;

        f.fullSuspension = bike.build().shape().fullSuspension;
        f.ridden = bike.getControllingPassenger() != null;
        f.airborne = cur.airborne;
        f.bailed = cur.bailed;
        f.vel = sim != null ? sim.vel : cur.vel;
        f.speed = f.vel.length();
        f.airTime = airTicks * 0.05;
        f.bailPitch = p.bailPitchError;
        f.bailYaw = p.bailYawError;
        f.bailImpact = p.bailImpactSpeed;

        // wheel and crank, suspension: render-state deltas work for every bike, the sim is sharper for the rider's
        double spinDelta = (cur.spinR - prev.spinR) * 20;
        double crankDelta = wrap(cur.crank - prev.crank);
        double forkVel = (cur.compF - prev.compF) * 20, shockVel = (cur.compR - prev.compR) * 20;
        if (sim != null) {
            f.rearOmega = sim.rear.spinRate;
            f.pedalling = bike.lastControls.pedal > 0.05f;
            f.frontContact = sim.front.contact;
            f.rearContact = sim.rear.contact;
            f.frontSurface = sim.front.surface;
            f.rearSurface = sim.rear.surface;
            forkVel = strongest(forkVel, sim.front.compVel);
            shockVel = strongest(shockVel, sim.rear.compVel);
        } else {
            f.rearOmega = spinDelta;
            double coupled = Math.abs(spinDelta) / p.gearRatio * 0.05;   // crank turn per tick if the rider was pedalling
            f.pedalling = crankDelta > 0.02 && crankDelta > 0.35 * coupled;
            f.frontContact = f.rearContact = !cur.airborne && !cur.bailed;
            if (f.rearContact && f.speed > 0.3) {
                Terrain.GroundHit hit = new Terrain.GroundHit();
                if (columns.terrain().ground(cur.com.x, cur.com.z, cur.com.y + 1, cur.com.y - 3, hit)) {
                    f.frontSurface = f.rearSurface = hit.surface;
                }
            }
        }
        // rear tyre scrub: how far the travel direction is off the bike's heading, or a wheel locked by the brake
        double lateral = Math.abs(f.vel.x * Math.cos(cur.yaw) + f.vel.z * Math.sin(cur.yaw));
        double slip = f.speed > 1.5 ? lateral / f.speed : 0;
        boolean locked = sim != null ? sim.rear.contact && sim.rear.sliding && sim.brake > 0.8 : cur.brake > 0.85 && f.speed > 2.5;
        boolean scrub = sim != null ? sim.rear.contact && (sim.rear.sliding || sim.rear.latSaturated) : f.rearContact;
        f.skidLocked = locked;
        f.skid = !scrub || cur.airborne || cur.bailed ? 0 : BikeSoundMath.skidAmount(slip, locked, f.speed);
        f.forkVel = Double.isFinite(forkVel) ? forkVel : 0;
        // engine: the riding client reads it, everyone else unpacks it from the synced crank slot
        f.motor = bike.bikeType().motor();
        f.engineRpm = f.throttle = 0;
        f.limiting = f.backfire = false;
        f.enginePitch = f.engineLoud = 1;
        if (f.motor) {
            if (bike.bikeType() == com.descentmtb.entity.BikeType.PIT_BIKE) f.enginePitch = MotoSoundMath.SMALL_ENGINE_PITCH;
            if (bike.moto().exhaust() == com.descentmtb.custom.MotoBuild.Exhaust.RACE) f.engineLoud = MotoSoundMath.RACE_EXHAUST_LOUDNESS;
            if (sim != null && sim.engine != null) {
                f.engineRpm = sim.engine.rpm;
                f.throttle = sim.engine.throttle;
                f.limiting = sim.engine.limiting;
                f.backfire = sim.engine.takeBackfire();
            } else {
                f.engineRpm = com.descentmtb.physics.Engine.decodeRpm(cur.crank);
                f.throttle = com.descentmtb.physics.Engine.decodeThrottle(cur.crank) ? 1 : 0;
                f.limiting = f.throttle > 0 && f.engineRpm > p.limitRpm - 250;
            }
            f.pedalling = true;           // no freewheel on a motorbike: keeps the hub silent
        }
        f.shockVel = Double.isFinite(shockVel) ? shockVel : 0;

        // where and how it will land, while it is in the air
        f.timeToGround = Double.POSITIVE_INFINITY;
        f.pitchError = f.yawError = f.pitchRate = f.yawRate = 0;
        if (f.ridden && cur.airborne && !cur.bailed) {
            V3 com = cur.com;
            LandingPredictor.Landing landing = LandingPredictor.predict(columns.terrain(), com, f.vel,
                    p.wheelRadius - p.axleDrop, cur.yaw, p.gravity);
            f.timeToGround = landing.time();
            if (landing.known()) f.pitchError = wrap(cur.pitch - landing.groundPitch());
            f.yawError = f.vel.horizontalLength() > 3 ? wrap(Math.atan2(-f.vel.x, f.vel.z) - cur.yaw) : 0;
            f.pitchRate = wrap(cur.pitch - prev.pitch) * 20;
            f.yawRate = -wrap(cur.yaw - prev.yaw) * 20;       // the yaw error grows as the yaw falls
        }
    }

    private static double strongest(double a, double b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    private static double wrap(double a) {
        if (!Double.isFinite(a)) return 0;
        double r = Math.IEEEremainder(a, 2 * Math.PI);
        return r <= -Math.PI ? r + 2 * Math.PI : r;
    }

    private BikeSoundController() {}
}
