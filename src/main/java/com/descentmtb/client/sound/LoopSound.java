package com.descentmtb.client.sound;

import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A sound the controller steers every tick: a loop (hub buzz, wind, tyres) or a long one-shot (the scream) whose
 * volume and pitch ease toward a target, which follows a bike (or sits "in the rider's ears" when it is the
 * rider's own bike) and which stops itself once it has been silent for a while or after a fade-out.
 */
public final class LoopSound extends AbstractTickableSoundInstance {
    /** A silent sound is dropped after this many ticks (the controller starts a new one when it is needed again). */
    private static final int IDLE_STOP_TICKS = 30;

    private final MountainBikeEntity follow;
    private float targetVolume, targetPitch;
    /** Volume change per tick when getting louder / quieter (a fade-out replaces the latter). */
    private float attack = 0.5f, release = 0.2f;
    private boolean ending;
    private int age, idle;

    /** @param follow the bike the sound comes from; null = heard directly (the rider's own bike, UI previews). */
    public LoopSound(SoundEvent event, MountainBikeEntity follow, boolean looping, float volume, float pitch) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.follow = follow;
        this.looping = looping;
        this.delay = 0;
        this.volume = volume;
        this.pitch = pitch;
        this.targetVolume = volume;
        this.targetPitch = pitch;
        if (follow == null) {
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        } else {
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            place();
        }
    }

    private void place() {
        this.x = follow.getX();
        this.y = follow.getY() + 0.6;
        this.z = follow.getZ();
    }

    @Override
    public void tick() {
        age++;
        if (follow != null) {
            if (follow.isRemoved()) {
                stop();
                return;
            }
            place();
        }
        float goal = ending ? 0 : targetVolume;
        float delta = goal - volume;
        volume += Math.max(-release, Math.min(attack, delta));
        pitch += (targetPitch - pitch) * 0.5f;
        if (goal <= 0.002f && volume <= 0.003f) {
            idle++;
            if (ending || idle > IDLE_STOP_TICKS) stop();
        } else {
            idle = 0;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void setTarget(float volume, float pitch) {
        if (ending) return;
        this.targetVolume = volume;
        this.targetPitch = pitch;
    }

    /** Fades out over a few ticks and stops. */
    public void fadeOut() {
        ending = true;
    }

    /** Fades out faster than {@link #fadeOut()} (a scream cut by a clean landing). */
    public void fadeOutFast() {
        ending = true;
        release = Math.max(release, 0.35f);
    }

    /** Silent right now - the pawls of a freehub engage the instant the rider pedals. */
    public void cut() {
        ending = true;
        volume = 0;
    }

    public void fadeRates(float attackPerTick, float releasePerTick) {
        attack = attackPerTick;
        release = releasePerTick;
    }

    /** True once the instance is no longer playing: finished by itself, stopped here or evicted by the engine. */
    public boolean finished(SoundManager manager) {
        return isStopped() || (age > 3 && !manager.isActive(this));
    }

    public boolean ending() {
        return ending;
    }

    public int age() {
        return age;
    }
}
