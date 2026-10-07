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
    /** Where a free-standing source is (dropped headphones); null = the boombox of {@code state}. */
    private final java.util.function.Supplier<Vec3> where;
    public DeviceSound(BoomboxState state, PcmRing ring, int rate, boolean headphones, SoundEvent event) {
        this(state, ring, rate, headphones, event, null);
    }
    /** A mono live source at {@code where} — headphones lying in the grass, still playing into the air. */
    public static DeviceSound at(BoomboxState state, PcmRing ring, int rate, SoundEvent event, java.util.function.Supplier<Vec3> where) {
        return new DeviceSound(state, ring, rate, false, event, where);
    }
    private DeviceSound(BoomboxState state, PcmRing ring, int rate, boolean headphones, SoundEvent event, java.util.function.Supplier<Vec3> where) {
        super(event, ring == null || where != null ? SoundSource.RECORDS : SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.state = state; this.ring = ring; this.rate = rate; this.headphones = headphones; this.where = where;
        disc = ring == null; looping = !disc; relative = headphones;
        attenuation = Attenuation.NONE; // custom radius in metres, independent of event volume
        tick();
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        CompletableFuture<AudioStream> stream = ring == null ? super.getStream(library, sound, looping)
                : CompletableFuture.completedFuture(new PcmStream(ring, rate, headphones ? 2 : 1));
        return stream.thenApply(source -> new GainAudioStream(source, () -> state.volume() * AudioClient.musicGain()));
    }
    @Override public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { stop(); return; }
        Vec3 at = headphones ? mc.player.position() : where != null ? where.get() : BoomboxServer.position(mc.level, state.emitter());
        if (at == null) { stop(); return; }
        x = headphones ? 0 : at.x; y = headphones ? 0 : at.y; z = headphones ? 0 : at.z;
        double distance = headphones ? 0 : mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        // Full level beside the device; a linear fade avoids the old extra squared attenuation.
        double gain = Math.max(0, Math.min(1, (state.radius() - distance) / Math.max(1, state.radius() - 2)));
        volume = (float) (AudioClient.musicVolume() * gain);
    }
    public void finish() { stop(); }
}
