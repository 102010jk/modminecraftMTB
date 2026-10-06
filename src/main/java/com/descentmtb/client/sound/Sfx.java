package com.descentmtb.client.sound;

import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** One-shot bike sounds: single hub clicks, suspension hiss, trick sounds. */
public final class Sfx {
    private static final class OneShot extends AbstractSoundInstance {
        OneShot(SoundEvent event, MountainBikeEntity at, float volume, float pitch) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            if (at == null) {
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
            } else {
                this.attenuation = SoundInstance.Attenuation.LINEAR;
                this.x = at.getX();
                this.y = at.getY() + 0.6;
                this.z = at.getZ();
            }
        }
    }

    /** @param at the bike the sound comes from; null = heard directly (own bike, previews) */
    public static void play(SoundEvent event, MountainBikeEntity at, double volume, double pitch) {
        if (event == null || volume <= 0.004) return;
        Minecraft.getInstance().getSoundManager().play(new OneShot(event, at, (float) volume, (float) pitch));
    }

    private Sfx() {}
}
