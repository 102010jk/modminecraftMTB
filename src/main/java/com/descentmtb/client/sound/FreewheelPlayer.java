package com.descentmtb.client.sound;

import com.descentmtb.custom.BikeParts.HubType;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/**
 * The sound of one freehub. Slow rolling is played as single clicks paced by the wheel speed and the hub's points
 * of engagement ({@link ClickPacer}); once the clicks come faster than a tick can carry they hand over to a
 * looping buzz whose pitch follows the click rate ({@link BikeSoundMath#buzzPitch}). The instant the rider
 * pedals the pawls engage and everything goes silent. Used by every bike's voice and by the workshop preview.
 */
public final class FreewheelPlayer {
    private final MountainBikeEntity follow;
    private final ClickPacer pacer = new ClickPacer();
    private final RandomSource random = RandomSource.create();
    private LoopSound buzz;
    private HubType buzzHub;

    /** @param follow the bike the sound comes from; null = heard directly */
    public FreewheelPlayer(MountainBikeEntity follow) {
        this.follow = follow;
    }

    /**
     * One tick.
     *
     * @param audible whether the hub is making noise at all ({@link BikeSoundMath#freewheelAudible})
     * @param master  overall volume (config master x distance rules of the caller), 0 = silent
     */
    public void update(HubType hub, double rearOmega, boolean audible, double speed, double master) {
        if (!audible || master <= 0.004 || hub.silent()) {
            silence();
            return;
        }
        double rate = BikeSoundMath.clickRate(hub, rearOmega);
        double volume = BikeSoundMath.freewheelVolume(hub, speed) * master;

        if (BikeSoundMath.singleClicks(rate)) {
            if (pacer.tick(rate)) {
                Sfx.play(ModSounds.hubClick(hub), follow, volume * (0.85 + 0.3 * random.nextDouble()),
                        0.95 + 0.1 * random.nextDouble());
            }
        } else {
            pacer.reset();
        }

        double buzzVolume = volume * BikeSoundMath.buzzBlend(rate);
        double pitch = BikeSoundMath.buzzPitch(hub, rate);
        if (buzz != null && (buzzHub != hub || buzz.finished(Minecraft.getInstance().getSoundManager()))) {
            if (buzzHub != hub && !buzz.finished(Minecraft.getInstance().getSoundManager())) buzz.fadeOutFast();
            buzz = null;
        }
        if (buzzVolume > 0.012) {
            if (buzz == null) {
                buzz = new LoopSound(ModSounds.hubBuzz(hub), follow, true, (float) buzzVolume, (float) pitch);
                buzzHub = hub;
                Minecraft.getInstance().getSoundManager().play(buzz);
            }
            buzz.setTarget((float) buzzVolume, (float) pitch);
        } else if (buzz != null) {
            buzz.setTarget(0, (float) pitch);
        }
    }

    /** Silent right now: pedalling, braking to a stop, a crash, a setting turned off. */
    public void silence() {
        pacer.reset();
        if (buzz != null) {
            buzz.cut();
            Minecraft.getInstance().getSoundManager().stop(buzz);   // now, not at the next sound tick
            buzz = null;
        }
    }

    public void stop() {
        silence();
    }
}
