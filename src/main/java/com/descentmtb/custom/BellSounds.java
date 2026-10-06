package com.descentmtb.custom;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import java.util.List;

/** One sound recipe for the workshop preview and the server's positional bell. */
public final class BellSounds {
    public record Note(int delay, SoundEvent sound, float volume, float pitch) {}
    private static final List<Note> DING = List.of(new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.6f));
    private static final List<Note> MINI = List.of(new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), .55f, 2f));
    private static final List<Note> CLASSIC = List.of(new Note(0, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.35f),
            new Note(3, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.35f));
    // Short honk; a goat horn's multi-second sample cannot behave like a handlebar horn.
    private static final List<Note> HORN = List.of(new Note(0, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.1f, .8f));
    private static final List<Note> DUCK = List.of(new Note(0, SoundEvents.CHICKEN_HURT, .75f, 1.9f),
            new Note(2, SoundEvents.CHICKEN_HURT, .6f, 2f));

    public static List<Note> notes(BikeParts.Bell bell) {
        return switch (bell) {
            case NONE -> List.of();
            case DING -> DING;
            case MINI -> MINI;
            case CLASSIC -> CLASSIC;
            case AIR_HORN -> HORN;
            case RUBBER_DUCK -> DUCK;
        };
    }
    private BellSounds() {}
}
