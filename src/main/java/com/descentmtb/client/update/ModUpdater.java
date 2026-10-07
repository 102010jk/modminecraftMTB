package com.descentmtb.client.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * In-game updater: compares the running mod jar with {@code dist/latest.json} in the GitHub repository and, on
 * request, downloads {@code dist/descentmtb-latest.jar}. A loaded jar can't be replaced while the game runs (Windows
 * locks it), so the new jar is saved next to it as {@code .update} and swapped in by a tiny helper process right
 * after the game exits.
 */
public final class ModUpdater {
    private ModUpdater() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String RAW = "https://raw.githubusercontent.com/102010jk/modminecraftMTB/master/dist/";
    public static final String MANIFEST_URL = RAW + "latest.json";
    public static final String JAR_URL = RAW + "descentmtb-latest.jar";

    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, DEV, ERROR }

    private static volatile State state = State.IDLE;
    private static volatile String message = "";
    private static volatile String remoteVersion = "", remoteNotes = "", remoteSha = "";
    private static volatile long remoteSize;
    private static volatile double progress;
    private static boolean swapScheduled;

    public static State state() { return state; }
    public static String message() { return message; }
    public static String remoteVersion() { return remoteVersion; }
    public static String remoteNotes() { return remoteNotes; }
    public static long remoteSize() { return remoteSize; }
    public static double progress() { return progress; }

    /** The jar the mod was loaded from, or null in a dev environment (classes directory). */
    static Path currentJar() {
        try {
            Path p = ModList.get().getModFileById("descentmtb").getFile().getFilePath();
            return p != null && Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".jar") ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Checks GitHub once (again when called after a finished check). */
    public static synchronized void check() {
        if (state == State.CHECKING || state == State.DOWNLOADING || state == State.READY) return;
        Path jar = currentJar();
        if (jar == null) {
            state = State.DEV;
            return;
        }
        state = State.CHECKING;
        CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> r = client().send(HttpRequest.newBuilder(URI.create(MANIFEST_URL + "?t=" + System.currentTimeMillis()))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                remoteSha = o.get("sha256").getAsString().toLowerCase(Locale.ROOT);
                remoteSize = o.has("size") ? o.get("size").getAsLong() : 0;
                remoteVersion = o.has("version") ? o.get("version").getAsString() : "";
                remoteNotes = o.has("notes") ? o.get("notes").getAsString() : "";
                state = remoteSha.equals(sha256(jar)) ? State.UP_TO_DATE : State.AVAILABLE;
            } catch (Throwable t) {
                fail("check", t);
            }
        });
    }

    /** Downloads the new jar next to the current one and schedules the swap for when the game exits. */
    public static synchronized void download() {
        if (state != State.AVAILABLE) return;
        Path jar = currentJar();
        if (jar == null) return;
        state = State.DOWNLOADING;
        progress = 0;
        CompletableFuture.runAsync(() -> {
            Path tmp = jar.resolveSibling(jar.getFileName() + ".part");
            Path pending = jar.resolveSibling(jar.getFileName() + ".update");
            try {
                HttpResponse<InputStream> r = client().send(HttpRequest.newBuilder(URI.create(JAR_URL + "?t=" + System.currentTimeMillis()))
                        .timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
                long total = r.headers().firstValueAsLong("content-length").orElse(remoteSize);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = r.body(); var out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[1 << 16];
                    long done = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        md.update(buf, 0, n);
                        done += n;
                        if (total > 0) progress = Math.min(1, done / (double) total);
                    }
                }
                String sha = HexFormat.of().formatHex(md.digest());
                if (!sha.equals(remoteSha)) throw new IllegalStateException("checksum mismatch");
                Files.move(tmp, pending, StandardCopyOption.REPLACE_EXISTING);
                scheduleSwap(jar, pending);
                state = State.READY;
            } catch (Throwable t) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
                fail("download", t);
            }
        });
    }

    /** After the JVM exits, a detached helper waits a moment and moves the new jar over the old one. */
    private static synchronized void scheduleSwap(Path jar, Path pending) {
        if (swapScheduled) return;
        swapScheduled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
                ProcessBuilder pb = windows
                        ? new ProcessBuilder("cmd", "/c", "ping -n 5 127.0.0.1 >nul & move /y \"" + pending + "\" \"" + jar + "\"")
                        : new ProcessBuilder("sh", "-c", "sleep 4; mv -f '" + pending + "' '" + jar + "'");
                pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (Exception e) {
                LOG.error("[descentmtb] could not start the update helper", e);
            }
        }, "DescentMTB update swap"));
    }

    private static void fail(String what, Throwable t) {
        LOG.warn("[descentmtb] update {} failed: {}", what, t.toString());
        message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        state = State.ERROR;
    }

    static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private static HttpClient client;

    /** On Windows trust the OS certificate store too (antivirus / proxies that inspect HTTPS). */
    private static synchronized HttpClient client() {
        if (client != null) return client;
        HttpClient.Builder b = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10));
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            try {
                KeyStore ks = KeyStore.getInstance("Windows-ROOT");
                ks.load(null, null);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, tmf.getTrustManagers(), null);
                b.sslContext(ctx);
            } catch (Exception e) {
                LOG.debug("[descentmtb] Windows trust store unavailable, using the JVM default", e);
            }
        }
        return client = b.build();
    }
}
