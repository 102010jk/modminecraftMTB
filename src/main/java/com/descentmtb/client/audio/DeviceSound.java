package com.descentmtb.client.audio;

import com.descentmtb.audio.BoomboxState;
import com.descentmtb.audio.BoomboxServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.CompletableFuture;

public final class DeviceSound extends AbstractTickableSoundInstance {
    private final BoomboxState state;
    private final PcmRing ring;
    private final int rate;
    private final boolean headphones;
    private final boolean disc;
    public DeviceSound(BoomboxState state, PcmRing ring, int rate, boolean headphones, SoundEvent event) {
        super(event, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.state = state; this.ring = ring; this.rate = rate; this.headphones = headphones;
        disc = ring == null; looping = !disc; relative = headphones;
        attenuation = Attenuation.NONE; // custom radius in metres, independent of event volume
        tick();
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return ring == null ? super.getStream(library, sound, looping) : CompletableFuture.completedFuture(new PcmStream(ring, rate, headphones ? 2 : 1));
    }
    @Override public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { stop(); return; }
        Vec3 at = headphones ? mc.player.position() : BoomboxServer.position(mc.level, state.emitter());
        if (at == null) { stop(); return; }
        x = headphones ? 0 : at.x; y = headphones ? 0 : at.y; z = headphones ? 0 : at.z;
        double distance = headphones ? 0 : mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        double gain = Math.max(0, 1 - distance / state.radius());
        volume = (float) (state.volume() * AudioClient.musicVolume() * gain * gain);
    }
    public void finish() { stop(); }
}
