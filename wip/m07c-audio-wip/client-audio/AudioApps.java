package com.descentmtb.client.audio;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which desktop programs may be used as a music source for the boombox / headphones, and how they are named.
 * Voice and chat programs are never offered (a boombox must never broadcast somebody's call), and neither are the
 * game itself or Windows system sounds. Pure: no Minecraft or JNA classes, unit-tested.
 */
public final class AudioApps {
    private AudioApps() {}

    /** Executables (lower case, without ".exe") that are never a music source. */
    private static final Set<String> BLOCKED = Set.of(
            "discord", "discordptb", "discordcanary", "discorddevelopment", "update",
            "teamspeak", "ts3client_win64", "ts3client_win32", "teamspeak3",
            "zoom", "zoomit", "cpthost",
            "whatsapp", "whatsapp.root", "teams", "ms-teams", "msteams", "msedgewebview2",
            "skype", "skypeapp", "lync", "slack", "signal", "telegram", "viber", "mumble",
            "element", "guilded", "revolt", "webex", "ciscowebexstart", "googlemeet", "facetime",
            "nvcontainer", "nvidia broadcast", "voicemeeter", "voicemod", "obs64", "obs32",
            "audiodg", "explorer", "systemsettings", "shellexperiencehost", "searchhost",
            "javaw", "java", "minecraft", "minecraftlauncher");

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("chrome", "Google Chrome"),
            Map.entry("msedge", "Microsoft Edge"),
            Map.entry("firefox", "Firefox"),
            Map.entry("opera", "Opera"),
            Map.entry("opera_gx", "Opera GX"),
            Map.entry("brave", "Brave"),
            Map.entry("vivaldi", "Vivaldi"),
            Map.entry("spotify", "Spotify"),
            Map.entry("applemusic", "Apple Music"),
            Map.entry("itunes", "iTunes"),
            Map.entry("tidal", "TIDAL"),
            Map.entry("deezer", "Deezer"),
            Map.entry("amazon music", "Amazon Music"),
            Map.entry("amazonmusic", "Amazon Music"),
            Map.entry("vlc", "VLC"),
            Map.entry("foobar2000", "foobar2000"),
            Map.entry("aimp", "AIMP"),
            Map.entry("winamp", "Winamp"),
            Map.entry("musicbee", "MusicBee"),
            Map.entry("mpc-hc64", "MPC-HC"),
            Map.entry("mpc-be64", "MPC-BE"),
            Map.entry("potplayermini64", "PotPlayer"),
            Map.entry("wmplayer", "Windows Media Player"),
            Map.entry("microsoft.media.player", "Media Player"),
            Map.entry("youtube music", "YouTube Music"),
            Map.entry("soundcloud", "SoundCloud"));

    /** "C:\\Program Files\\Google\\Chrome\\chrome.exe" → "chrome". */
    public static String exeKey(String pathOrName) {
        if (pathOrName == null) return "";
        String s = pathOrName.replace('/', '\\');
        int slash = s.lastIndexOf('\\');
        if (slash >= 0) s = s.substring(slash + 1);
        s = s.toLowerCase(Locale.ROOT);
        if (s.endsWith(".exe")) s = s.substring(0, s.length() - 4);
        return s.trim();
    }

    public static boolean allowed(String pathOrName) {
        String k = exeKey(pathOrName);
        return !k.isEmpty() && !BLOCKED.contains(k) && !k.startsWith("discord") && !k.contains("teamspeak");
    }

    /** A friendly name for the picker: a known player name, else the executable name with a capital letter. */
    public static String displayName(String pathOrName) {
        String k = exeKey(pathOrName);
        String known = NAMES.get(k);
        if (known != null) return known;
        if (k.isEmpty()) return "?";
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    /** Browsers show the page title next to their name ("Google Chrome — Lofi beats - YouTube"). */
    public static boolean isBrowser(String pathOrName) {
        return switch (exeKey(pathOrName)) {
            case "chrome", "msedge", "firefox", "opera", "opera_gx", "brave", "vivaldi" -> true;
            default -> false;
        };
    }

    /** Trims a browser window title to the media part: drops " - Google Chrome" and similar suffixes. */
    public static String cleanTitle(String title) {
        if (title == null) return "";
        String t = title.trim();
        for (String suffix : new String[]{" - Google Chrome", " - Microsoft\u200b Edge", " - Microsoft Edge",
                " — Mozilla Firefox", " - Mozilla Firefox", " - Opera", " - Brave", " - Vivaldi"}) {
            if (t.endsWith(suffix)) t = t.substring(0, t.length() - suffix.length()).trim();
        }
        if (t.length() > 60) t = t.substring(0, 57) + "...";
        return t;
    }
}
