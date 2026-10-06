package com.descentmtb.client.custom;

import com.descentmtb.client.ClientConfig;
import com.descentmtb.client.sound.BikeSoundMath;
import com.descentmtb.client.sound.FreewheelPlayer;
import com.descentmtb.custom.BikeParts.HubType;

/**
 * A short local preview of a freehub in the workshop: a made-up coast that starts at a walk (single clicks), rolls
 * up to ~30 km/h (the buzz takes over and rises in pitch), is "pedalled" for a moment in the middle (silent, as it
 * would be on the trail) and rolls out. Plays through the same {@link FreewheelPlayer} as a real bike.
 */
final class HubPreview {
    private static final int LENGTH = 40;
    private static final double WHEEL_RADIUS = 0.375;

    private HubType hub;
    private FreewheelPlayer player;
    private int tick;

    void play(HubType type) {
        clear();
        if (type.silent()) return;
        hub = type;
        player = new FreewheelPlayer(null);
        tick = 0;
        step();
    }

    void tick() {
        if (hub == null) return;
        tick++;
        if (tick > LENGTH) {
            clear();
            return;
        }
        step();
    }

    private void step() {
        double t = tick / (double) LENGTH;
        double kmh = 1.5 + 30 * BikeSoundMath.smooth(t * 1.15);
        double speed = kmh / 3.6;
        boolean pedalling = tick >= 22 && tick <= 25;
        boolean rollOut = tick >= LENGTH - 3;
        double master = ClientConfig.SPEC.isLoaded() ? ClientConfig.BIKE_SOUND_VOLUME.get() : 1.0;
        player.update(hub, speed / WHEEL_RADIUS, !pedalling && !rollOut, speed, Math.max(master, 0.5));
    }

    void clear() {
        if (player != null) player.stop();
        player = null;
        hub = null;
    }
}
