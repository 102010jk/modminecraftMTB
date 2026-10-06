package com.descentmtb.custom;

import com.descentmtb.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import java.util.List;

/** One sound recipe for the workshop preview and the server's positional bell. */
public final class BellSounds {
    public record Note(int delay, SoundEvent sound, float volume, float pitch) {}

    public static List<Note> notes(BikeParts.Bell bell) {
        SoundEvent event = switch (bell) {
            case NONE -> null;
            case DING -> ModSounds.BELL_DING.get();
            case MINI -> ModSounds.BELL_MINI.get();
            case CLASSIC -> ModSounds.BELL_CLASSIC.get();
            case AIR_HORN -> ModSounds.BELL_HORN.get();
            case RUBBER_DUCK -> ModSounds.BELL_DUCK.get();
        };
        return event == null ? List.of() : List.of(new Note(0, event, 1.0f, 1.0f));
    }

    private BellSounds() {}
}
