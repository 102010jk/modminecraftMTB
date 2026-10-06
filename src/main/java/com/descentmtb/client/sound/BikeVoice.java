package com.descentmtb.client.sound;

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
 * Everything one bike is heard doing: the freehub, wind, tyres on the ground, suspension hiss, the rider's scream
 * and trick sounds. One voice per audible bike; {@link BikeSoundController} feeds it a {@link BikeAudioFrame}
 * every tick. The rider's own bike is heard directly (no distance), others from where they are.
 */
final class BikeVoice {
    /** What the controller reads from the config for one tick. */
    record Settings(double master, boolean hub, boolean wind, boolean scream) {}

    final MountainBikeEntity bike;
    final boolean local;
    private final MountainBikeEntity follow;
    private final FreewheelPlayer freewheel;
    private final EnumMap<RollFamily, LoopSound> roll = new EnumMap<>(RollFamily.class);
    private final SuspensionHiss forkHiss = new SuspensionHiss(), shockHiss = new SuspensionHiss();
    private final ScreamTrigger screamTrigger = new ScreamTrigger();
    private final RandomSource random = RandomSource.create();
    private LoopSound wind, scream;
    private Trick lastTrick = Trick.NONE;
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
            LoopSound current = drive(manager, roll.get(fam), rollEvent(fam), fam == family ? rollVol : 0, rollPitch, 0.18f);
            if (current == null) roll.remove(fam);
            else roll.put(fam, current);
        }

        // ---- suspension hiss on hard compressions / snaps back ----
        playHiss(forkHiss.tick(f.forkVel), master, 0.92);
        if (f.fullSuspension) playHiss(shockHiss.tick(f.shockVel), master, 1.1);
        else shockHiss.reset();

        // ---- the scream ----
        if (f.airborne) airTicks++;
        else airTicks = 0;
        f.airTime = airTicks * 0.05;
        if (cfg.scream()) {
            switch (screamTrigger.update(f.airborne, CrashPredictor.unavoidable(f), f.bailed)) {
                case START -> startScream(manager, master);
                case STOP -> {
                    if (scream != null) scream.fadeOutFast();
                    scream = null;
                }
                default -> {}
            }
        } else if (scream != null) {
            scream.fadeOutFast();
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
    }

    private void startScream(SoundManager manager, double master) {
        if (master <= 0.004) return;
        if (scream != null) scream.fadeOutFast();
        scream = new LoopSound(ModSounds.SCREAM.get(), follow, false, (float) Math.min(1.0, 0.95 * master),
                (float) (0.94 + 0.16 * random.nextDouble()));
        manager.play(scream);
    }

    private void playHiss(double volume, double master, double pitch) {
        if (volume > 0) {
            Sfx.play(ModSounds.SUSPENSION_HISS.get(), follow, volume * master, pitch * (0.95 + 0.1 * random.nextDouble()));
        }
    }

    private void playTrick(Trick trick, double master) {
        TrickSounds.Kind kind = TrickSounds.of(trick.name());
        if (kind == null) return;
        SoundEvent event = switch (kind) {
            case HEEL_CLICK -> ModSounds.HEEL_CLICK.get();
            case BARSPIN -> ModSounds.BARSPIN.get();
            case TAILWHIP -> ModSounds.TAILWHIP.get();
        };
        Sfx.play(event, follow, 0.8 * master, 0.97 + 0.06 * random.nextDouble());
    }

    private static SoundEvent rollEvent(RollFamily family) {
        return switch (family) {
            case SOFT -> ModSounds.ROLL_SOFT.get();
            case HARD -> ModSounds.ROLL_HARD.get();
            case WOOD -> ModSounds.ROLL_WOOD.get();
        };
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
        roll.values().forEach(LoopSound::fadeOut);
        roll.clear();
        if (scream != null) scream.fadeOutFast();
        scream = null;
        screamTrigger.reset();
        forkHiss.reset();
        shockHiss.reset();
    }
}
