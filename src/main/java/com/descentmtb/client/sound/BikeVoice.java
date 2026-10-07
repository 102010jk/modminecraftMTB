package com.descentmtb.client.sound;

import com.descentmtb.client.ClientConfig;
import com.descentmtb.client.sound.BikeSoundMath.RollFamily;
import com.descentmtb.custom.BikeParts.HubType;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModSounds;
import com.descentmtb.trick.Trick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;

/**
 * Everything one bike is heard doing: the freehub, wind, tyres on the ground, landings, crash impacts,
 * the rider's voice (screams, bails, cheers) and trick sounds. One voice per audible bike; {@link BikeSoundController}
 * feeds it a {@link BikeAudioFrame} every tick. The rider's own bike is heard directly (no distance), others from
 * where they are.
 */
final class BikeVoice {
    /** What the controller reads from the config for one tick. */
    record Settings(double master, boolean hub, boolean wind, boolean scream, ClientConfig.RiderVoice voice) {}

    final MountainBikeEntity bike;
    final boolean local;
    private final MountainBikeEntity follow;
    private final FreewheelPlayer freewheel;
    private final EnumMap<RollFamily, LoopSound> roll = new EnumMap<>(RollFamily.class);
    private final EnumMap<RollFamily, Integer> rollLevels = new EnumMap<>(RollFamily.class);
    private final ScreamTrigger screamTrigger = new ScreamTrigger();
    private final RandomSource random = RandomSource.create();
    private LoopSound wind, scream, skid;
    private SoundEvent skidEvent;
    private Trick lastTrick = Trick.NONE;
    private boolean prevAirborne, prevBailed, prevRidden;
    /** Ticks in the air so far (for the "just left the ground" rule of the crash predictor). */
    int airTicks;

    BikeVoice(MountainBikeEntity bike, boolean local) {
        this.bike = bike;
        this.local = local;
        this.follow = local ? null : bike;
        this.freewheel = new FreewheelPlayer(follow);
    }

    void update(BikeAudioFrame f, HubType hub, Settings cfg) {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        // others are a little quieter than your own bike; the engine adds the distance falloff on top
        double master = cfg.master() * (local ? 1.0 : 0.8);

        // ---- freehub: only while coasting; the pawls engage the instant you pedal ----
        boolean hubOn = cfg.hub() && BikeSoundMath.freewheelAudible(hub, f.rearOmega, f.pedalling, f.bailed);
        freewheel.update(hub, f.rearOmega, hubOn, f.speed, master);

        // ---- wind ----
        double windVol = cfg.wind() ? BikeSoundMath.windVolume(f.speed, f.airborne) * master : 0;
        wind = drive(manager, wind, ModSounds.WIND.get(), windVol, BikeSoundMath.windPitch(f.speed), 0.25f);

        // ---- tyres: only the surface under the wheels is audible, fading out in the air ----
        var surface = BikeSoundMath.dominantSurface(f);
        RollFamily family = BikeSoundMath.family(surface);
        double rollVol = BikeSoundMath.rollVolume(f.speed, BikeSoundMath.contactFraction(f), surface) * master;
        double rollPitch = BikeSoundMath.rollPitch(f.speed);
        for (RollFamily fam : RollFamily.values()) {
            int curLevel = rollLevels.getOrDefault(fam, 0);
            int newLevel = BikeSoundMath.rollLevel(f.speed, curLevel);
            LoopSound sound = roll.get(fam);
            if (sound != null && curLevel != newLevel && (fam == family && rollVol > 0.012)) {
                // crossfade to new speed ladder sample
                sound.fadeOutFast();
                sound = null;
                roll.remove(fam);
            }
            SoundEvent event = ModSounds.rollEvent(fam, newLevel);
            LoopSound current = drive(manager, sound, event, fam == family ? rollVol : 0, rollPitch, 0.18f);
            if (current == null) {
                roll.remove(fam);
                rollLevels.remove(fam);
            } else {
                roll.put(fam, current);
                rollLevels.put(fam, newLevel);
            }
        }

        // ---- skid: rear tyre scrubbing in a power slide or locked under the brake ----
        double skidVol = f.skid * master * 0.85;
        SoundEvent wantedSkid = ModSounds.slideEvent(family, f.skidLocked);
        if (skid != null && skidEvent != wantedSkid && skidVol > 0.012) {
            skid.fadeOutFast();
            skid = null;
        }
        skid = drive(manager, skid, wantedSkid, skidVol, 0.9 + Math.min(0.25, f.speed * 0.012), 0.3f);
        if (skid != null) skidEvent = wantedSkid;

        // ---- landings ----
        if (prevAirborne && !f.airborne && !f.bailed && airTicks >= 4) {
            playLanding(f, master, f.ridden ? cfg.voice() : ClientConfig.RiderVoice.OFF);
        }

        // ---- crash / bail ----
        if (!prevBailed && f.bailed) {
            // The rider can be ejected on this tick; retain the actual bail, not a voice on an empty bike.
            playCrash(f, master, f.ridden || prevRidden ? cfg.voice() : ClientConfig.RiderVoice.OFF);
        }

        // ---- the scream ----
        if (f.airborne) airTicks++;
        else airTicks = 0;
        f.airTime = airTicks * 0.05;
        if (f.ridden && cfg.scream() && cfg.voice() != ClientConfig.RiderVoice.OFF) {
            switch (screamTrigger.update(f.airborne, CrashPredictor.unavoidable(f), f.bailed)) {
                case START -> startScream(manager, master, cfg.voice());
                case STOP -> {
                    if (scream != null) scream.fadeOutFast();
                    scream = null;
                }
                default -> {}
            }
        } else {
            if (scream != null) {
                if (!f.ridden) manager.stop(scream);
                else scream.fadeOutFast();
            }
            scream = null;
            screamTrigger.reset();
        }
        if (scream != null && scream.finished(manager)) scream = null;

        // ---- trick sounds on the start of a trick ----
        Trick trick = bike.rsCur.trick;
        if (trick != lastTrick) {
            if (trick != Trick.NONE) playTrick(trick, master);
            lastTrick = trick;
        }

        prevAirborne = f.airborne;
        prevBailed = f.bailed;
        prevRidden = f.ridden;
    }

    private void startScream(SoundManager manager, double master, ClientConfig.RiderVoice voice) {
        if (master <= 0.004 || voice == ClientConfig.RiderVoice.OFF) return;
        if (scream != null) scream.fadeOutFast();
        SoundEvent screamEvent = voice == ClientConfig.RiderVoice.FEMALE
                ? ModSounds.RIDER_FEMALE_SCREAM.get()
                : ModSounds.RIDER_MALE_SCREAM.get();
        scream = new LoopSound(screamEvent, follow, false, (float) Math.min(1.0, 0.95 * master),
                (float) (0.94 + 0.16 * random.nextDouble()));
        manager.play(scream);
    }

    private void playLanding(BikeAudioFrame f, double master, ClientConfig.RiderVoice voice) {
        if (master <= 0.004) return;
        boolean hard = f.forkVel > 2.0 || f.shockVel > 2.0 || f.vel.y < -7;
        boolean med = f.forkVel > 1.0 || f.shockVel > 1.0 || f.vel.y < -4;

        SoundEvent landSound;
        if (f.vel.y < -12 || airTicks > 30) {
            landSound = ModSounds.LAND_BIGDROP.get();
        } else if (f.frontContact && !f.rearContact) {
            landSound = hard ? ModSounds.LAND_FRONT_HARD.get() : (med ? ModSounds.LAND_FRONT_MED.get() : ModSounds.LAND_FRONT_SOFT.get());
        } else {
            landSound = hard ? ModSounds.LAND_BACK_HARD.get() : (med ? ModSounds.LAND_BACK_MED.get() : ModSounds.LAND_BACK_SOFT.get());
        }
        Sfx.play(landSound, follow, 0.9 * master, 0.95 + 0.1 * random.nextDouble());

        // rider landing cheer for big air or landed trick
        if (voice != ClientConfig.RiderVoice.OFF) {
            SoundEvent cheer = null;
            if (airTicks > 35 || (airTicks > 20 && f.speed > 14)) {
                cheer = voice == ClientConfig.RiderVoice.FEMALE
                        ? ModSounds.RIDER_FEMALE_LANDED_HUGE.get()
                        : ModSounds.RIDER_MALE_LANDED_HUGE.get();
            } else if (airTicks > 22 || lastTrick != Trick.NONE) {
                cheer = voice == ClientConfig.RiderVoice.FEMALE
                        ? ModSounds.RIDER_FEMALE_LANDED_BIG.get()
                        : ModSounds.RIDER_MALE_LANDED_BIG.get();
            } else if (airTicks > 12) {
                cheer = voice == ClientConfig.RiderVoice.FEMALE
                        ? ModSounds.RIDER_FEMALE_LANDED_NORMAL.get()
                        : ModSounds.RIDER_MALE_LANDED_NORMAL.get();
            }
            if (cheer != null) {
                Sfx.play(cheer, follow, 0.85 * master, 0.96 + 0.08 * random.nextDouble());
            }
        }
    }

    private void playCrash(BikeAudioFrame f, double master, ClientConfig.RiderVoice voice) {
        if (master <= 0.004) return;
        if (voice != ClientConfig.RiderVoice.OFF) {
            SoundEvent bailVoice = voice == ClientConfig.RiderVoice.FEMALE
                    ? ModSounds.RIDER_FEMALE_BAIL.get()
                    : ModSounds.RIDER_MALE_BAIL.get();
            Sfx.play(bailVoice, follow, 0.95 * master, 0.95 + 0.1 * random.nextDouble());
        }
        SoundEvent crashBike = f.speed > 10
                ? ModSounds.CRASH_BIKE_HARD.get()
                : (f.speed > 5 ? ModSounds.CRASH_BIKE_MED.get() : ModSounds.CRASH_BIKE_SOFT.get());
        Sfx.play(crashBike, follow, 0.9 * master, 0.95 + 0.1 * random.nextDouble());
    }

    private void playTrick(Trick trick, double master) {
        TrickSounds.Kind kind = TrickSounds.of(trick.name());
        if (kind == null) return;
        SoundEvent event = switch (kind) {
            case HEEL_CLICK -> ModSounds.HEEL_CLICK.get();
            case BARSPIN, TAILWHIP -> ModSounds.TRICK_FLICK.get();
        };
        Sfx.play(event, follow, 0.8 * master, 0.97 + 0.06 * random.nextDouble());
    }

    /**
     * Keeps one loop alive at a target: starts it when it is needed, steers it, and lets it go (it stops itself
     * after being silent for a while). Returns the instance to keep, or null.
     */
    private LoopSound drive(SoundManager manager, LoopSound sound, SoundEvent event, double volume, double pitch, float release) {
        if (sound != null && sound.finished(manager)) sound = null;
        if (volume < 0.012) {
            if (sound != null) sound.setTarget(0, (float) pitch);
            return sound;
        }
        if (sound == null) {
            sound = new LoopSound(event, follow, true, (float) volume, (float) pitch);
            sound.fadeRates(0.25f, release);
            manager.play(sound);
        }
        sound.setTarget((float) volume, (float) pitch);
        return sound;
    }

    /** Stops everything this bike makes (dismount, out of range, level change). */
    void shutdown() {
        freewheel.stop();
        if (wind != null) wind.fadeOut();
        wind = null;
        if (skid != null) skid.fadeOut();
        skid = null;
        roll.values().forEach(LoopSound::fadeOut);
        roll.clear();
        rollLevels.clear();
        if (scream != null) scream.fadeOutFast();
        scream = null;
        screamTrigger.reset();
        prevAirborne = false;
        prevBailed = false;
        prevRidden = false;
    }
}
