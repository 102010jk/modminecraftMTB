package com.descentmtb.client.audio;

import com.descentmtb.client.audio.win.Com;
import com.descentmtb.client.audio.win.ProcessLoopback;
import com.descentmtb.client.audio.win.WinAudioSessions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moves one desktop program's sound into the game. The program keeps playing, but its Windows mixer volume is
 * pulled down to {@link #PARKED} (inaudible on the speakers) while its process-loopback stream — which is taken
 * after the mixer volume — is captured and amplified back by the same factor, so it comes out of the boombox or the
 * headphones instead of twice. Measured: the capture is linear in the mixer volume down to 1e-5, so the round trip
 * is lossless in float.
 * <p>
 * Safety: the program's original volume is always given back — on stop, on world leave, on a normal JVM exit
 * (shutdown hook) and, after a hard crash, on the next game start from a small restore file in the config dir.
 * <p>
 * All Windows calls run on one daemon thread; nothing here blocks the render thread.
 */
public final class DesktopCapture {
    private DesktopCapture() {}

    public static final float PARKED = 1e-4f;

    /** One sink per consumer (local playback, network); called on the capture thread with stereo 48 kHz floats. */
    public interface Listener {
        void accept(float[] stereo, int frames);
    }

    private static final ExecutorService WIN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DescentMTB desktop audio");
        t.setDaemon(true);
        return t;
    });
    private static final ScheduledExecutorService WATCH = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DescentMTB desktop audio watchdog");
        t.setDaemon(true);
        return t;
    });

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static Path restoreFile;
    private static volatile ProcessLoopback capture;
    private static volatile int pid;
    private static volatile String exe = "";
    private static volatile float originalVolume = 1f;
    private static volatile float gain = 1f;
    private static volatile String lastError;
    private static boolean hooked;

    public static boolean supported() {
        return Com.AVAILABLE;
    }

    /** Call once at client start: remembers where the restore file lives and repairs a previous crash. */
    public static synchronized void init(Path configDir) {
        restoreFile = configDir.resolve("descentmtb-audio-restore.txt");
        if (!hooked) {
            hooked = true;
            Runtime.getRuntime().addShutdownHook(new Thread(DesktopCapture::stopNow, "DescentMTB audio restore"));
            WATCH.scheduleWithFixedDelay(DesktopCapture::watchdog, 2, 2, TimeUnit.SECONDS);
        }
        if (supported() && Files.exists(restoreFile)) WIN.execute(DesktopCapture::repairFromFile);
    }

    /** The programs that may be offered as a source, loudest first (voice/chat apps and the game itself are left out). */
    public static CompletableFuture<List<WinAudioSessions.Session>> listSources() {
        if (!supported()) return CompletableFuture.completedFuture(List.of());
        return CompletableFuture.supplyAsync(() -> {
            List<WinAudioSessions.Session> out = new ArrayList<>();
            long self = ProcessHandle.current().pid();
            for (WinAudioSessions.Session s : WinAudioSessions.list()) {
                if (s.rootPid() != self && AudioApps.allowed(s.exePath())) out.add(s);
            }
            // a program we are capturing shows the parked volume; report it at its real level
            return out;
        }, WIN);
    }

    public static void addListener(Listener l) { LISTENERS.add(l); }

    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    public static boolean active() {
        ProcessLoopback c = capture;
        return c != null && c.alive();
    }

    public static int pid() { return pid; }

    public static String exe() { return exe; }

    /** Last failure message (old Windows, program closed, ...) or null. */
    public static String lastError() { return lastError; }

    /** Starts capturing {@code rootPid} (stops a previous capture first). Completes with null or an error message. */
    public static CompletableFuture<String> start(int rootPid, String exePath) {
        if (!supported()) return CompletableFuture.completedFuture("Windows only");
        lastError=null;
        return CompletableFuture.supplyAsync(() -> {
            stopNow();
            try {
                float vol = WinAudioSessions.volume(rootPid);
                if (vol < 0) throw new IllegalStateException("The application no longer has an audio session");
                originalVolume = vol;
                pid = rootPid;
                exe = exePath == null ? "" : exePath;
                writeRestoreFile();
                // park first, then capture: everything captured is at the parked level, so the gain is exact
                gain = originalVolume / PARKED;
                WinAudioSessions.setVolume(rootPid, PARKED);
                capture = ProcessLoopback.start(rootPid, DesktopCapture::deliver);
                lastError = null;
                return null;
            } catch (Throwable t) {
                lastError = t.getMessage() == null ? t.toString() : t.getMessage();
                stopNow();
                return lastError;
            }
        }, WIN);
    }

    /** Stops capturing and gives the program its volume back. */
    public static void stop() {
        WIN.execute(DesktopCapture::stopNow);
    }

    private static void deliver(float[] buf, int frames) {
        // Parked, no raw sample can exceed PARKED. Anything louder means the program is not parked right now (the
        // user moved its mixer slider, a new session appeared): pass that block through unamplified rather than
        // blasting it x10000, and let the watchdog re-park the program.
        float peak = 0;
        for (int i = 0; i < frames * 2; i++) peak = Math.max(peak, Math.abs(buf[i]));
        float g = peak > PARKED * 2.5f ? 1f : gain;
        if (g != gain) WATCH.execute(DesktopCapture::watchdog);
        for (int i = 0; i < frames * 2; i++) buf[i] = AudioDsp.limit(buf[i] * g);
        for (Listener l : LISTENERS) l.accept(buf, frames);
    }

    private static synchronized void stopNow() {
        gain = 0;
        ProcessLoopback c = capture;
        capture = null;
        if (c != null) c.close();
        int p = pid;
        pid = 0;
        boolean restored=true;
        if (p != 0 && supported()) {
            try {
                WinAudioSessions.setVolume(p, originalVolume);
            } catch (Throwable ignored) {
                restored=false;
            }
        }
        if(restored)deleteRestoreFile();
    }

    /** Keeps the program parked (a browser may open a new audio session) and notices when it has gone away. */
    private static void watchdog() {
        int p = pid;
        if (p == 0) return;
        WIN.execute(() -> {
            if (pid != p) return;
            ProcessLoopback c = capture;
            if (c == null || !c.alive()) {
                lastError = c == null ? lastError : c.error();
                stopNow();
                return;
            }
            try {
                float v = WinAudioSessions.volume(p);
                if (v < 0) return;
                if (Math.abs(v - PARKED) > 1e-5f) {
                    // the user moved the program's slider in the Windows mixer: take that as the new level
                    if (v > 0.01f) originalVolume = v;
                    gain = originalVolume / PARKED;
                    WinAudioSessions.setVolume(p, PARKED);
                    writeRestoreFile();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // ------------------------------------------------------------------ crash safety

    private static void writeRestoreFile() throws IOException {
        if (restoreFile == null) throw new IOException("Audio restore path was not initialized");
        Files.createDirectories(restoreFile.getParent());
        Path temporary = restoreFile.resolveSibling(restoreFile.getFileName()+".tmp");
        Files.writeString(temporary, pid + "\t" + originalVolume + "\t" + exe + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary,restoreFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary,restoreFile,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRestoreFile() {
        if (restoreFile == null) return;
        try {
            Files.deleteIfExists(restoreFile);
        } catch (IOException ignored) {
        }
    }

    private static void repairFromFile() {
        try {
            for (String line : Files.readAllLines(restoreFile, StandardCharsets.UTF_8)) {
                String[] f = line.split("\t");
                if (f.length < 3) continue;
                int p = Integer.parseInt(f[0].trim());
                float v = Float.parseFloat(f[1].trim());
                String path = f[2].trim();
                for (WinAudioSessions.Session s : WinAudioSessions.list()) {
                    boolean samePid = s.rootPid() == p;
                    boolean sameExe = AudioApps.exeKey(s.exePath()).equals(AudioApps.exeKey(path));
                    if (sameExe && s.volume() < 0.01f) WinAudioSessions.setVolume(s.rootPid(), v);
                }
            }
        } catch (Throwable ignored) {
        }
        deleteRestoreFile();
    }
}
