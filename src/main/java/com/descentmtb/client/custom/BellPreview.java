package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.Bell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

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
        for (var note : com.descentmtb.custom.BellSounds.notes(bell)) {
            add(note.delay(), note.sound(), note.pitch(), note.volume());
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
