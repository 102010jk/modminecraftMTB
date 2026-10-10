package com.descentmtb.client.sound;

import com.descentmtb.client.ClientConfig;
import com.descentmtb.client.sound.BikeSoundMath.RollFamily;
import com.descentmtb.custom.BikeParts.HubType;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Terrain;
import com.descentmtb.registry.ModSounds;
import com.descentmtb.trick.Trick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
    /** Skis: the glide hiss on snow / ice and the grind of the bases on rock. */
    private LoopSound glide, scrape;
    private int glideLevel;
    private SoundEvent glideEvent;
    /** The dirt bike's engine (null on a bicycle). */
    private MotoSoundController engine;
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
        boolean hubOn = !f.motor && !f.ski && cfg.hub() && BikeSoundMath.freewheelAudible(hub, f.rearOmega, f.pedalling, f.bailed);
        if (f.motor) {
            if (engine == null) engine = new MotoSoundController(follow);
            engine.update(f, master);
        }
        freewheel.update(hub, f.rearOmega, hubOn, f.speed, master);

        // ---- wind ----
        double windVol = cfg.wind() ? BikeSoundMath.windVolume(f.speed, f.airborne) * master : 0;
        wind = drive(manager, wind, ModSounds.WIND.get(), windVol, BikeSoundMath.windPitch(f.speed), 0.25f);

        // ---- tyres: only the surface under the wheels is audible, fading out in the air ----
        var surface = BikeSoundMath.dominantSurface(f);
        RollFamily family = BikeSoundMath.family(surface);
        double rollVol = f.ski ? 0 : BikeSoundMath.rollVolume(f.speed, BikeSoundMath.contactFraction(f), surface) * master;
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

        // ---- skis: glide on snow, grind on rock ----
        updateSki(manager, f, surface, master);

        // ---- skid: rear tyre scrubbing in a power slide or locked under the brake; skis spraying snow ----
        double skidVol = f.skid * master * 0.85;
        SoundEvent wantedSkid = ModSounds.slideEvent(family, f.skidLocked);
        if (f.ski) {
            wantedSkid = ModSounds.SLIDE_SNOW.get();
            if (!BikeSoundMath.skiGlides(surface)) skidVol = 0;      // on rock the grind says it all, a box slides
        }
        if (skid != null && skidEvent != wantedSkid && skidVol > 0.012) {
            skid.fadeOutFast();
            skid = null;
        }
        skid = drive(manager, skid, wantedSkid, skidVol, 0.9 + Math.min(0.25, f.speed * 0.012), 0.3f);
        if (skid != null) skidEvent = wantedSkid;

        // ---- landings ----
        if (prevAirborne && !f.airborne && !f.bailed && airTicks >= 4) {
            if (f.ski) playSkiLanding(f, surface, master, f.ridden ? cfg.voice() : ClientConfig.RiderVoice.OFF);
            else playLanding(f, master, f.ridden ? cfg.voice() : ClientConfig.RiderVoice.OFF);
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
        playCheer(f, master, voice);
    }

    /** The rider's cheer after big air or a landed trick. */
    private void playCheer(BikeAudioFrame f, double master, ClientConfig.RiderVoice voice) {
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
        if (f.ski) {
            playSkiCrash(f, master);
            return;
        }
        SoundEvent crashBike = f.speed > 10
                ? ModSounds.CRASH_BIKE_HARD.get()
                : (f.speed > 5 ? ModSounds.CRASH_BIKE_MED.get() : ModSounds.CRASH_BIKE_SOFT.get());
        Sfx.play(crashBike, follow, 0.9 * master, 0.95 + 0.1 * random.nextDouble());
    }

    /**
     * Skis on the ground: the glide hiss on snow (four speed samples like the tyres, pitched up and thinner on ice)
     * and, where the bases scrape over rock, a low harsh grind with the odd knock of a stone.
     */
    private void updateSki(SoundManager manager, BikeAudioFrame f, Terrain.Surface surface, double master) {
        if (!f.ski) return;
        boolean onGround = !f.airborne && !f.bailed;
        double contact = onGround ? BikeSoundMath.contactFraction(f) : 0;

        // glide: the speed sample ladder with the tyres' hysteresis, crossfaded on a change of level
        int level = BikeSoundMath.rollLevel(f.speed, glideLevel);
        double glideVol = BikeSoundMath.glideVolume(f.speed, contact, surface) * master;
        SoundEvent wanted = surface == Terrain.Surface.WOOD ? ModSounds.SLIDE_WOOD.get() : ModSounds.glideEvent(level);
        if (glide != null && glideEvent != wanted && glideVol > 0.012) {
            glide.fadeOutFast();
            glide = null;
        }
        glide = drive(manager, glide, wanted, glideVol, BikeSoundMath.glidePitch(f.speed, surface), 0.2f);
        if (glide != null) glideEvent = wanted;
        glideLevel = level;

        // grind: bases over stone, harsh and low, with a stone knocking against the edges now and then
        double scrapeAmount = onGround ? f.skiScrape : 0;
        double scrapeVol = BikeSoundMath.scrapeVolume(scrapeAmount) * master;
        double jitter = 0.94 + 0.12 * random.nextDouble();
        scrape = drive(manager, scrape, ModSounds.SLIDE_HARD.get(), scrapeVol, BikeSoundMath.scrapePitch(f.speed) * jitter, 0.35f);
        if (scrapeAmount > 0.1 && random.nextDouble() < 0.10 * scrapeAmount) {
            Sfx.play(ModSounds.CRASH_STONE_SMALL.get(), follow, (0.25 + 0.35 * scrapeAmount) * master, 1.1 + 0.3 * random.nextDouble());
        }
    }

    /** Skis touching down: a soft thump into snow (a hard crack on ice / rock), the big-drop slam, then the cheer. */
    private void playSkiLanding(BikeAudioFrame f, Terrain.Surface surface, double master, ClientConfig.RiderVoice voice) {
        if (master <= 0.004) return;
        double hit = BikeSoundMath.clamp(-f.vel.y / 10.0, 0.25, 1.0);
        if (f.vel.y < -12 || airTicks > 30) Sfx.play(ModSounds.LAND_BIGDROP.get(), follow, 0.8 * master, 0.9 + 0.1 * random.nextDouble());
        if (surface == Terrain.Surface.SNOW || surface == Terrain.Surface.AIRBAG) {
            Sfx.play(SoundEvents.POWDER_SNOW_FALL, follow, (0.5 + 0.5 * hit) * master, 0.8 + 0.15 * random.nextDouble());
            Sfx.play(SoundEvents.SNOW_BREAK, follow, 0.6 * hit * master, 0.7 + 0.1 * random.nextDouble());
            Sfx.play(ModSounds.LAND_BACK_SOFT.get(), follow, 0.35 * hit * master, 0.75 + 0.1 * random.nextDouble());
        } else {
            // ice or rock: the edges and bases clack down hard
            Sfx.play(hit > 0.6 ? ModSounds.CRASH_STONE_MED.get() : ModSounds.CRASH_STONE_SMALL.get(), follow,
                    (0.45 + 0.4 * hit) * master, 1.0 + 0.15 * random.nextDouble());
        }
        playCheer(f, master, voice);
    }

    /** Skis bailing: the body into the snow (powder thump) or onto rock (stone impacts), skis clattering away. */
    private void playSkiCrash(BikeAudioFrame f, double master) {
        Terrain.Surface surface = BikeSoundMath.dominantSurface(f);
        if (surface == Terrain.Surface.SNOW || surface == Terrain.Surface.AIRBAG) {
            Sfx.play(SoundEvents.POWDER_SNOW_FALL, follow, 0.9 * master, 0.75 + 0.1 * random.nextDouble());
            Sfx.play(SoundEvents.SNOW_BREAK, follow, 0.8 * master, 0.6 + 0.1 * random.nextDouble());
        }
        SoundEvent impact = f.speed > 10 ? ModSounds.CRASH_STONE_HARD.get()
                : f.speed > 5 ? ModSounds.CRASH_STONE_MED.get() : ModSounds.CRASH_STONE_SMALL.get();
        boolean soft = surface == Terrain.Surface.SNOW || surface == Terrain.Surface.AIRBAG;
        Sfx.play(impact, follow, (soft ? 0.35 : 0.9) * master, 0.95 + 0.1 * random.nextDouble());
    }

    private void playTrick(Trick trick, double master) {
        TrickSounds.Kind kind = TrickSounds.of(trick.name());
        if (kind == null) return;
        if (kind == TrickSounds.Kind.HEEL_CLICK) return; // no metallic clink
        SoundEvent event = ModSounds.TRICK_FLICK.get();
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
        if (glide != null) glide.fadeOut();
        glide = null;
        glideLevel = 0;
        if (scrape != null) scrape.fadeOut();
        scrape = null;
        if (engine != null) engine.shutdown();
        engine = null;
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
