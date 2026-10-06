package com.descentmtb.client.audio;

import com.descentmtb.audio.*;
import com.descentmtb.client.ClientConfig;
import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.client.audio.win.WinAudioSessions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Native capture only starts after an explicit choice in the audio screen. */
public final class AudioClient {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<Emitter, BoomboxState> states = new HashMap<>();
    private static final Map<Emitter, Playback> players = new HashMap<>();
    private record Playback(PcmRing ring, DeviceSound sound) {}
    private static final PcmRing local = new PcmRing(48000 / 4);
    private static final ConcurrentLinkedQueue<byte[]> outgoing = new ConcurrentLinkedQueue<>();
    private static volatile Emitter sending;
    private static boolean personal, requested;
    private static int generation, sequence;
    private static DeviceSound localSound;
    private static Level world;
    private static AudioDsp.Decimator3 decimator = new AudioDsp.Decimator3();
    private static byte[] packet = new byte[640];
    private static int filled;
    public static void setup() {
        DesktopCapture.init(FMLPaths.CONFIGDIR.get());
        DesktopCapture.addListener(AudioClient::capture);
        AudioNet.onState = AudioClient::state;
        AudioNet.onChunk = c -> {
            Playback p = players.get(c.emitter());
            if (p == null || p.ring == null) return;
            float[] samples = new float[c.data().length];
            for (int i=0;i<samples.length;i++) samples[i]=MuLaw.decode(c.data()[i]);
            p.ring.write(samples,0,samples.length);
        };
        BoomboxBlock.editor = e -> MC.setScreen(new AudioSettingsScreen(e));
        com.descentmtb.entity.MountainBikeEntity.audioEditor = BoomboxBlock.editor;
        HeadphonesItem.editor = () -> MC.setScreen(new AudioSettingsScreen(null));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }
    public static double musicVolume() { return ClientConfig.SPEC.isLoaded() ? ClientConfig.MUSIC_VOLUME.get() : .8; }
    public static boolean wearing() { return MC.player != null && BoomboxServer.isHeadphones(MC.player.getItemBySlot(EquipmentSlot.HEAD)); }
    public static float worldVolume() { return personal && DesktopCapture.active() && wearing() ? (float)(ClientConfig.SPEC.isLoaded() ? ClientConfig.HEADPHONE_WORLD_VOLUME.get() : .25) : 1; }
    public static BoomboxState stateOf(Emitter e) { return states.get(e); }
    public static boolean personal() { return personal; }
    public static void start(Emitter e, WinAudioSessions.Session source, int radius, float volume) {
        stopCapture();
        if (MC.player == null || MC.level == null || (e == null && !wearing())) return;
        int request = ++generation;
        requested = true; personal = e == null;
        synchronized (AudioClient.class) { decimator = new AudioDsp.Decimator3(); filled = 0; }
        DesktopCapture.start(source.rootPid(), source.exePath()).thenAccept(error -> MC.execute(() -> {
            if (request != generation) return;
            if (MC.level == null) { DesktopCapture.stop(); return; }
            if (error != null) { stopCapture(); MC.player.displayClientMessage(net.minecraft.network.chat.Component.literal(error), false); return; }
            if (e != null) PacketDistributor.sendToServer(new AudioNet.ControlC2S(e,true,BoomboxState.Mode.APP,Optional.empty(),radius,volume,source.title()));
            else playLocal(null, true);
        }));
    }
    public static void stop(Emitter e) {
        if (e != null && MC.player != null) PacketDistributor.sendToServer(new AudioNet.ControlC2S(e,false,BoomboxState.Mode.APP,Optional.empty(),20,1,""));
        if (e == null || e.equals(sending)) stopCapture();
    }
    private static void stopCapture() {
        generation++; requested=false; sending=null; personal=false; outgoing.clear(); local.clear();
        DesktopCapture.stop();
        if (localSound != null) { localSound.finish(); MC.getSoundManager().stop(localSound); localSound=null; }
    }
    private static void playLocal(BoomboxState s, boolean headphones) {
        if (localSound != null) { localSound.finish(); MC.getSoundManager().stop(localSound); }
        if (s == null) s = new BoomboxState(Emitter.bike(0),true,MC.player.getUUID(),"",BoomboxState.Mode.APP,Optional.empty(),20,1,"");
        localSound = new DeviceSound(s,local,48000,headphones,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("descentmtb","audio.live")));
        MC.getSoundManager().play(localSound);
    }
    private static void state(BoomboxState s) {
        if (MC.level != world) { world=MC.level; states.clear(); players.values().forEach(p -> p.sound.finish()); players.clear(); }
        Playback old = players.remove(s.emitter());
        if (old != null) { old.sound.finish(); MC.getSoundManager().stop(old.sound); }
        states.remove(s.emitter());
        if (s.emitter().equals(sending)) stopCapture();
        if (!s.active() || MC.player == null || MC.level == null) return;
        states.put(s.emitter(),s);
        if (s.mode() == BoomboxState.Mode.APP && s.owner().equals(MC.player.getUUID())) {
            if (!requested || !DesktopCapture.active()) { stop(s.emitter()); return; }
            sending = s.emitter(); playLocal(s,false); return;
        }
        PcmRing ring = s.mode() == BoomboxState.Mode.APP ? new PcmRing(4000) : null;
        SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("descentmtb","audio.live"));
        if (ring == null) {
            var song = s.disc().map(id -> MC.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).get(id)).orElse(null);
            if (song == null) return;
            event = song.soundEvent().value();
        }
        DeviceSound sound = new DeviceSound(s,ring,16000,false,event);
        players.put(s.emitter(),new Playback(ring,sound)); MC.getSoundManager().play(sound);
    }
    private static synchronized void capture(float[] stereo,int frames) {
        float[] mono = new float[frames]; AudioDsp.downmix(stereo,frames,mono); local.write(mono,0,frames);
        if (sending == null) return;
        float[] low = new float[frames/3+2]; int n=decimator.process(mono,frames,low);
        for(int i=0;i<n;i++) {
            packet[filled++]=MuLaw.encode(low[i]);
            if(filled==packet.length) { if(outgoing.size()<4) outgoing.offer(packet); packet=new byte[640]; filled=0; }
        }
    }
    private static void tick() {
        if (MC.level != world) {
            world=MC.level; stopCapture();
            players.values().forEach(p -> { p.sound.finish(); MC.getSoundManager().stop(p.sound); }); players.clear(); states.clear();
        }
        if (MC.player == null) return;
        while (ModKeyMappings.AUDIO_SETTINGS.consumeClick()) {
            Emitter e = MC.player.getVehicle() instanceof BoomboxHolder h && h.hasBoombox() ? Emitter.bike(MC.player.getVehicle().getId()) : null;
            MC.setScreen(new AudioSettingsScreen(e));
        }
        if (personal && !wearing() || requested && DesktopCapture.lastError()!=null && !DesktopCapture.active()) stop(sending);
        if (sending != null) for(int i=0;i<3;i++) { byte[] data=outgoing.poll(); if(data==null) break; PacketDistributor.sendToServer(new AudioNet.ChunkC2S(sending,sequence++,data)); }
        // Restart a live channel after an audio device/resource reload. Disc playback stays finite.
        if (localSound != null && !MC.getSoundManager().isActive(localSound) && DesktopCapture.active()) playLocal(sending==null?null:states.get(sending),personal);
        for(var p:players.values()) if(p.ring!=null && !MC.getSoundManager().isActive(p.sound)) MC.getSoundManager().play(p.sound);
    }
}
