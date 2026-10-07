package com.descentmtb.client.sound;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;

/**
 * The engine of one dirt bike: four looping layers recorded at idle / low / mid / high revs, crossfaded and pitched
 * to the engine's rpm ({@link MotoSoundMath}), louder under load; the stuttering limiter loop on the rev limiter and
 * a backfire pop when the throttle snaps shut at high revs. The rider hears their own engine directly, everyone else
 * from where the bike is. Driven once per client tick by {@link BikeVoice}.
 */
final class MotoSoundController {
    private final MountainBikeEntity follow;
    private final LoopSound[] layers = new LoopSound[4];
    private LoopSound limiter;
    private boolean throttleWasOpen;
    private double peakRpm;

    MotoSoundController(MountainBikeEntity follow) {
        this.follow = follow;
    }

    private static SoundEvent layer(int i) {
        return switch (i) {
            case 0 -> ModSounds.MOTO_IDLE.get();
            case 1 -> ModSounds.MOTO_LOW.get();
            case 2 -> ModSounds.MOTO_MID.get();
            default -> ModSounds.MOTO_HIGH.get();
        };
    }

    void update(BikeAudioFrame f, double master) {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        // an empty bike lying in the dirt has stalled
        double rpm = f.ridden && !f.bailed ? f.engineRpm : 0;
        double loud = rpm > 0 ? MotoSoundMath.loudness(rpm, f.throttle) * master : 0;
        double[] w = MotoSoundMath.weights(rpm);
        for (int i = 0; i < layers.length; i++) {
            double vol = loud * w[i];
            layers[i] = drive(manager, layers[i], layer(i), vol, MotoSoundMath.pitch(i, Math.max(rpm, 1)), 0.25f);
        }
        limiter = drive(manager, limiter, ModSounds.MOTO_LIMITER.get(), f.limiting && rpm > 0 ? 0.8 * master : 0,
                1.0, 0.4f);

        // backfire: the local engine reports it; for other riders it is the throttle snapping shut at high revs
        boolean open = f.throttle > 0.3;
        peakRpm = open ? Math.max(rpm, peakRpm * 0.9) : peakRpm * 0.8;
        boolean pop = f.backfire || (follow != null && throttleWasOpen && !open && peakRpm > 7500 && Math.random() < 0.6);
        if (pop && rpm > 0) Sfx.play(ModSounds.MOTO_BACKFIRE.get(), follow, 0.9 * master, 0.9 + 0.2 * Math.random());
        throttleWasOpen = open;
    }

    private LoopSound drive(SoundManager manager, LoopSound sound, SoundEvent event, double volume, double pitch, float release) {
        if (sound != null && sound.finished(manager)) sound = null;
        if (volume < 0.008) {
            if (sound != null) sound.setTarget(0, (float) pitch);
            return sound;
        }
        if (sound == null) {
            sound = new LoopSound(event, follow, true, (float) Math.min(1, volume), (float) pitch);
            sound.fadeRates(0.3f, release);
            manager.play(sound);
        }
        sound.setTarget((float) Math.min(1, volume), (float) pitch);
        return sound;
    }

    void shutdown() {
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] != null) layers[i].fadeOut();
            layers[i] = null;
        }
        if (limiter != null) limiter.fadeOut();
        limiter = null;
    }
}
