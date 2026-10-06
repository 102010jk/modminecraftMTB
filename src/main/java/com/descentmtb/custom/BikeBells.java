package com.descentmtb.custom;

import com.descentmtb.custom.BikeParts.Bell;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bell of a ridden bike: the build's {@link Bell} plays as a world sound at the bike for everyone nearby. One
 * ring per {@link #COOLDOWN_TICKS} per player, only while the player rides a bike (the client sends a payload; the
 * server decides what plays).
 */
public final class BikeBells {
    public static final int COOLDOWN_TICKS = 10;

    /** A sound still to play (second note of a double ring). */
    private record Pending(long dueTick, ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {}

    private static final Map<UUID, Long> LAST_RING = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            long now = e.getServer().getTickCount();
            PENDING.removeIf(p -> {
                if (p.dueTick > now) {
                    return false;
                }
                p.level.playSound(null, p.x, p.y, p.z, p.sound, SoundSource.PLAYERS, p.volume, p.pitch);
                return true;
            });
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            LAST_RING.clear();
            PENDING.clear();
        });
    }

    /** The player pressed the bell key. Returns whether a bell rang. */
    public static boolean ring(ServerPlayer player) {
        if (!(player.getVehicle() instanceof MountainBikeEntity bike) || !(bike.level() instanceof ServerLevel level)) {
            return false;
        }
        Bell bell = bike.build().bell();
        if (bell == Bell.NONE) {
            return false;
        }
        long now = level.getServer().getTickCount();
        Long last = LAST_RING.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return false;
        }
        LAST_RING.put(player.getUUID(), now);
        play(level, bike.getX(), bike.getY() + 1.0, bike.getZ(), bell, now);
        return true;
    }

    /** Plays a bell at a spot (second notes are scheduled). */
    static void play(ServerLevel level, double x, double y, double z, Bell bell, long now) {
        for (var note : BellSounds.notes(bell)) {
            if (note.delay() == 0) sound(level, x, y, z, note.sound(), note.volume(), note.pitch());
            else soundLater(level, now + note.delay(), x, y, z, note.sound(), note.volume(), note.pitch());
        }
    }

    private static void sound(ServerLevel level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void soundLater(ServerLevel level, long due, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        PENDING.add(new Pending(due, level, x, y, z, sound, volume, pitch));
    }

    private BikeBells() {}
}
