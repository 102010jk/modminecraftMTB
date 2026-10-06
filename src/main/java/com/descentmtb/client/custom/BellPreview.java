package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.Bell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/** Plays a local preview of a bell: the same sounds {@code BikeBells} plays in the world. */
final class BellPreview {
    private static final class Note {
        int delay;
        final SoundEvent sound;
        final float pitch, volume;

        Note(int delay, SoundEvent sound, float pitch, float volume) {
            this.delay = delay;
            this.sound = sound;
            this.pitch = pitch;
            this.volume = volume;
        }
    }

    private final List<Note> queue = new ArrayList<>();

    void play(Bell bell) {
        queue.clear();
        // the same sounds BikeBells plays in the world
        SoundEvent chime = SoundEvents.NOTE_BLOCK_BELL.value();
        switch (bell) {
            case DING -> add(0, chime, 1.6f, 1f);
            case MINI -> add(0, chime, 2.0f, .55f);
            case CLASSIC -> {
                add(0, chime, 1.35f, 1f);
                add(3, chime, 1.35f, 1f);
            }
            case AIR_HORN -> add(0, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), 1.0f, 1f);
            case RUBBER_DUCK -> {
                add(0, SoundEvents.CHICKEN_HURT, 1.9f, .9f);
                add(2, SoundEvents.CHICKEN_HURT, 2.0f, .9f);
            }
            case NONE -> { }
        }
        tick(true);
    }

    private void add(int delay, SoundEvent sound, float pitch, float volume) {
        queue.add(new Note(delay, sound, pitch, volume));
    }

    void tick() {
        tick(false);
    }

    private void tick(boolean first) {
        for (int i = queue.size() - 1; i >= 0; i--) {
            Note n = queue.get(i);
            if (!first) {
                n.delay--;
            }
            if (n.delay <= 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(n.sound, n.pitch, n.volume));
                queue.remove(i);
            }
        }
    }

    void clear() {
        queue.clear();
    }
}
