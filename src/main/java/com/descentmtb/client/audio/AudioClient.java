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
import net.minecraft.world.phys.Vec3;
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
    private static final class Playback {
        final PcmRing ring;
        final DeviceSound sound;
        boolean started, received;
        int grace, lastSequence;
        Playback(PcmRing ring, DeviceSound sound) { this.ring = ring; this.sound = sound; }
    }
    private record Outgoing(int sequence, byte[] data) {}
    private static final PcmRing local = new PcmRing(48000 * 3 / 4);
    private static final PcmRing localStereo = new PcmRing(48000 * 2 * 3 / 4);
    private static final ConcurrentLinkedQueue<Outgoing> outgoing = new ConcurrentLinkedQueue<>();
    private static volatile Emitter sending;
    private static Emitter pendingEmitter;
    private static int pendingTicks;
    private static float personalVolume=1;
    private static boolean personal, requested;
    private static int generation, sequence;
    private static DeviceSound localSound;
    private static boolean localStarted;
    private static int localGrace;
    private static Level world;
    private static AudioDsp.Decimator3 decimator = new AudioDsp.Decimator3();
    private static byte[] packet = new byte[640];
    private static int filled;
    // Headphones off the head while still playing: they hiss on from where they lie (or from the pocket), and the
    // desktop program stays parked so it never jumps back onto the speakers mid-crash.
    private static final PcmRing tinny = new PcmRing(48000 * 3 / 4);
    private static final AudioDsp.NeckFilter tinnyFilter = new AudioDsp.NeckFilter(1, 48000);
    private static volatile boolean outOfEars;
    private static volatile Vec3 droppedAt = Vec3.ZERO;
    private static int droppedEntity = -1, lostTicks;
    /** How long the headphones may be unaccounted for (drop packet in flight) before the program gets its volume back. */
    static final int LOST_GRACE_TICKS = 60;
    /** Dropped headphones are audible this far, in metres. */
    static final int DROPPED_RADIUS = 5;
    /** World sound factor, computed on the client thread; the sound thread only reads it. */
    private static volatile float worldGain = 1;
    public static void setup() {
        DesktopCapture.init(FMLPaths.CONFIGDIR.get());
        DesktopCapture.addListener(AudioClient::capture);
        AudioNet.onState = AudioClient::state;
        AudioNet.onChunk = c -> {
            Playback p = players.get(c.emitter());
            if (p == null || p.ring == null) return;
            int gap = c.seq() - p.lastSequence;
            if (p.received && gap <= 0) return;
            if (p.received && gap > 1) {
                // Preserve elapsed time after sender/rate-limit drops instead of compressing the song.
                if (gap <= 12) p.ring.write(new float[(gap - 1) * 640], 0, (gap - 1) * 640);
                else p.ring.clear();
            }
            p.lastSequence = c.seq(); p.received = true;
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
    public static double musicGain() { return ClientConfig.SPEC.isLoaded() ? ClientConfig.MUSIC_GAIN.get() : 3; }
    public static float personalVolume() { return personalVolume; }
    public static boolean wearing() { return MC.player != null && BoomboxServer.isHeadphones(MC.player.getItemBySlot(EquipmentSlot.HEAD)); }
    /** Factor for every world sound (read by the sound engine thread, so no player access here). */
    public static float worldVolume() { return worldGain; }
    /** Own-bike sounds and the rider's voice stay clearly audible under the headphones (never muted with the world). */
    public static float playerVolume() { float w = worldGain; return w >= 1 ? 1 : HeadphoneMix.playerGain(w); }
    public static BoomboxState stateOf(Emitter e) { return states.get(e); }
    public static boolean personal() { return personal; }
    public static void start(Emitter e, WinAudioSessions.Session source, int radius, float volume) {
        stopCapture();
        if (MC.player == null || MC.level == null || (e == null && !wearing())) return;
        int request = ++generation;
        requested = true; personal = e == null;
        personalVolume=volume; pendingEmitter=e; pendingTicks=0;
        synchronized (AudioClient.class) { decimator = new AudioDsp.Decimator3(); filled = 0; }
        DesktopCapture.start(source.rootPid(), source.exePath()).thenAccept(error -> MC.execute(() -> {
            if (request != generation) return;
            if (MC.level == null) { DesktopCapture.stop(); return; }
            if (error != null) { stopCapture(); MC.player.displayClientMessage(net.minecraft.network.chat.Component.literal(error), false); return; }
            if (e != null) {
                pendingTicks=60;
                String label=source.title().length()>64?source.title().substring(0,64):source.title();
                PacketDistributor.sendToServer(new AudioNet.ControlC2S(e,true,BoomboxState.Mode.APP,Optional.empty(),radius,volume,label));
            }
            else playLocal(null, true);
        }));
    }
    public static void stop(Emitter e) {
        if (e != null && MC.player != null) PacketDistributor.sendToServer(new AudioNet.ControlC2S(e,false,BoomboxState.Mode.APP,Optional.empty(),20,1,""));
        if (e == null || e.equals(sending) || e.equals(pendingEmitter)) stopCapture();
    }
    private static synchronized void stopCapture() {
        generation++; requested=false; sending=null; pendingEmitter=null;pendingTicks=0; personal=false; outgoing.clear(); local.clear(); localStereo.clear(); tinny.clear();
        outOfEars=false; droppedEntity=-1; lostTicks=0;
        DesktopCapture.stop();
        if (localSound != null) { localSound.finish(); MC.getSoundManager().stop(localSound); localSound=null; }
    }
    private static void playLocal(BoomboxState s, boolean headphones) {
        if (localSound != null) { localSound.finish(); MC.getSoundManager().stop(localSound); }
        if (headphones && outOfEars) {
            BoomboxState here = new BoomboxState(Emitter.bike(0),true,MC.player.getUUID(),"",BoomboxState.Mode.APP,Optional.empty(),DROPPED_RADIUS,personalVolume,"");
            localSound = DeviceSound.at(here,tinny,48000,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("descentmtb","audio.live")),() -> droppedAt);
            localStarted = false; localGrace = 0;
            return;
        }
        if (s == null) s = new BoomboxState(Emitter.bike(0),true,MC.player.getUUID(),"",BoomboxState.Mode.APP,Optional.empty(),20,personalVolume,"");
        localSound = new DeviceSound(s,headphones ? localStereo : local,48000,headphones,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("descentmtb","audio.live")));
        localStarted = false; localGrace = 0;
    }
    private static void state(BoomboxState s) {
        if (MC.level != world) { world=MC.level;stopCapture(); states.clear(); players.values().forEach(p -> {p.sound.finish();MC.getSoundManager().stop(p.sound);}); players.clear(); }
        Playback old = players.remove(s.emitter());
        if (old != null) { old.sound.finish(); MC.getSoundManager().stop(old.sound); }
        states.remove(s.emitter());
        if (s.emitter().equals(sending)) stopCapture();
        if(s.emitter().equals(pendingEmitter) && (!s.active() || MC.player==null || !s.owner().equals(MC.player.getUUID())))stopCapture();
        if (!s.active() || MC.player == null || MC.level == null) return;
        states.put(s.emitter(),s);
        if (s.mode() == BoomboxState.Mode.APP && s.owner().equals(MC.player.getUUID())) {
            if (!requested || !DesktopCapture.active()) { stop(s.emitter()); return; }
            sending = s.emitter();pendingEmitter=null;pendingTicks=0; playLocal(s,false); return;
        }
    }
    private static void playRemote(BoomboxState s) {
        PcmRing ring = s.mode() == BoomboxState.Mode.APP ? new PcmRing(16000) : null;
        SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("descentmtb","audio.live"));
        if (ring == null) {
            var song = s.disc().map(id -> MC.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).get(id)).orElse(null);
            if (song == null) return;
            event = song.soundEvent().value();
        }
        DeviceSound sound = new DeviceSound(s,ring,16000,false,event);
        Playback playback = new Playback(ring,sound);
        players.put(s.emitter(),playback);
        if (ring == null) { MC.getSoundManager().play(sound); playback.started = true; playback.grace = 20; }
    }
    private static synchronized void capture(float[] stereo,int frames) {
        float[] mono = new float[frames]; AudioDsp.downmix(stereo,frames,mono); local.write(mono,0,frames);
        localStereo.write(stereo,0,frames*2);
        if (outOfEars) {
            float[] thin = mono.clone(); tinnyFilter.process(thin,frames,1f); tinny.write(thin,0,frames);
        }
        if (sending == null) return;
        float[] low = new float[frames/3+2]; int n=decimator.process(mono,frames,low);
        for(int i=0;i<n;i++) {
            packet[filled++]=MuLaw.encode(low[i]);
            if(filled==packet.length) {
                if(outgoing.size()>=8) outgoing.poll(); // discard the oldest backlog, preserve live latency
                outgoing.offer(new Outgoing(sequence++,packet)); packet=new byte[640]; filled=0;
            }
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
        if (personal) followHeadphones();
        if (requested && DesktopCapture.lastError()!=null && !DesktopCapture.active()) stop(sending);
        updateWorldGain();
        if(personal && localSound!=null && !DesktopCapture.active())stopCapture();
        if(pendingTicks>0 && --pendingTicks==0)stop(pendingEmitter);
        if (sending != null) for(int i=0;i<3;i++) { Outgoing chunk=outgoing.poll(); if(chunk==null) break; PacketDistributor.sendToServer(new AudioNet.ChunkC2S(sending,chunk.sequence(),chunk.data())); }
        // Restart a live channel after an audio device/resource reload. Disc playback stays finite.
        if (localSound != null && DesktopCapture.active()) {
            if (localGrace > 0) localGrace--;
            if (localStarted && localGrace == 0 && !MC.getSoundManager().isActive(localSound)) playLocal(sending==null?null:states.get(sending),personal);
            PcmRing queue = personal ? (outOfEars ? tinny : localStereo) : local;
            if (!localStarted && queue.available() >= PcmStream.prefillSamples(48000,personal && !outOfEars ? 2 : 1)) {
                MC.getSoundManager().play(localSound); localStarted = true; localGrace = 20;
            }
        }
        for(var s:states.values()){
            if(s.emitter().equals(sending))continue;
            var at=BoomboxServer.position(MC.level,s.emitter());
            boolean near=at!=null && MC.player.position().distanceToSqr(at)<(s.radius()+4.0)*(s.radius()+4.0);
            var p=players.get(s.emitter());
            if(!near){if(p!=null){p.sound.finish();MC.getSoundManager().stop(p.sound);players.remove(s.emitter());}continue;}
            if(p==null){playRemote(s);continue;}
            if (p.grace > 0) p.grace--;
            if (p.ring != null && !p.started && p.ring.available() >= PcmStream.prefillSamples(16000,1)) {
                MC.getSoundManager().play(p.sound); p.started = true; p.grace = 20;
            } else if(p.ring!=null && p.started && p.grace == 0 && !MC.getSoundManager().isActive(p.sound)) {
                p.sound.finish();playRemote(s);
            }
        }
    }

    /**
     * Personal listening with the headphones off the head. Worn: music in the ears. Lying in the world or in the
     * inventory: the program stays parked and the music plays thin and quiet from the headphones themselves. Only
     * when they are really gone (despawned, burnt, taken) does the program get its Windows volume back.
     */
    private static void followHeadphones() {
        if (wearing()) {
            lostTicks = 0; droppedEntity = -1;
            if (outOfEars) { outOfEars = false; playLocal(null, true); }
            return;
        }
        Vec3 where = locateHeadphones();
        if (where != null) lostTicks = 0;
        else if (droppedEntity >= 0 && MC.player.position().distanceToSqr(droppedAt) > 80 * 80) {
            lostTicks = 0; // out of tracking range: they still lie there, we just cannot see them — stay parked
        } else if (++lostTicks > LOST_GRACE_TICKS) { stop(sending); return; }
        if (where == null && droppedEntity < 0) where = MC.player.getEyePosition(); // drop packet in flight
        if (where != null) droppedAt = where;
        if (!outOfEars) { outOfEars = true; tinny.clear(); playLocal(null, true); }
    }

    /** The worn-off headphones: the dropped item entity, or the player's own inventory. Null = not found. */
    private static Vec3 locateHeadphones() {
        if (droppedEntity >= 0) {
            var e = MC.level.getEntity(droppedEntity);
            if (e instanceof net.minecraft.world.entity.item.ItemEntity item && item.isAlive() && BoomboxServer.isHeadphones(item.getItem()))
                return item.position().add(0, 0.15, 0);
            droppedEntity = -1;
        }
        for (var stack : MC.player.getInventory().items)
            if (BoomboxServer.isHeadphones(stack)) return MC.player.position().add(0, 1.0, 0);
        net.minecraft.world.entity.item.ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (var item : MC.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, MC.player.getBoundingBox().inflate(48),
                i -> i.isAlive() && BoomboxServer.isHeadphones(i.getItem()))) {
            double d = item.distanceToSqr(MC.player);
            if (d < bestD) { bestD = d; best = item; }
        }
        if (best == null) return null;
        droppedEntity = best.getId();
        return best.position().add(0, 0.15, 0);
    }

    /** Recomputes the world-sound factor and re-applies it to already playing sounds (records, ambience) when it changes. */
    private static void updateWorldGain() {
        float target = personal && !outOfEars && DesktopCapture.active() && wearing()
                ? (float)(ClientConfig.SPEC.isLoaded() ? ClientConfig.HEADPHONE_WORLD_VOLUME.get() : HeadphoneMix.DEFAULT_WORLD) : 1;
        if (target == worldGain) return;
        worldGain = target;
        // Volumes are otherwise only computed when a sound starts: a jukebox song begun under the headphones would
        // stay muffled after taking them off. Re-applying a category volume recomputes every playing instance.
        for (net.minecraft.sounds.SoundSource source : net.minecraft.sounds.SoundSource.values())
            if (source != net.minecraft.sounds.SoundSource.MASTER) MC.getSoundManager().updateSourceVolume(source, MC.options.getSoundSourceVolume(source));
    }
}
