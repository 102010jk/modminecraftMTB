package com.descentmtb.client.audio.win;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The audio sessions of the default playback device (what the Windows volume mixer shows): which programs make
 * sound, how loud they are right now, and their mixer volume. Grouped by the program's root process, so a browser's
 * audio-service child process shows up as the browser. Windows only; call from a thread that may block briefly.
 */
public final class WinAudioSessions {
    private WinAudioSessions() {}

    private static final String CLSID_MMDeviceEnumerator = "{BCDE0395-E52F-467C-8E3D-C4579291692E}";
    private static final String IID_IMMDeviceEnumerator = "{A95664D2-9614-4F35-A746-DE8DB63617E6}";
    private static final String IID_IAudioSessionManager2 = "{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}";
    private static final String IID_IAudioSessionControl2 = "{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}";
    private static final String IID_ISimpleAudioVolume = "{87CE5498-68D6-44E5-9215-6DA47EF883D8}";
    private static final String IID_IAudioMeterInformation = "{C02216F6-8C67-4B5B-9D00-D008E73E0064}";

    /** One program that has an audio session. {@code rootPid} is the process to capture (with its tree). */
    public record Session(int rootPid, String exePath, String title, float peak, float volume, boolean muted, boolean active) {}

    /** All programs with an audio session on the default output, loudest first. */
    public static List<Session> list() {
        Map<Integer, Session> byRoot = new LinkedHashMap<>();
        Procs procs = Procs.snapshot();
        Map<Integer, String> titles = windowTitles();
        forEachSession((ctl, vol) -> {
            int pid = pid(ctl);
            if (pid == 0) return; // system sounds
            int root = procs.root(pid);
            String exe = procs.path(root);
            float peak = peak(ctl);
            FloatByReference v = new FloatByReference();
            IntByReference m = new IntByReference();
            if (vol != null) {
                Com.call(vol, 4, v);
                Com.call(vol, 6, m);
            }
            IntByReference state = new IntByReference();
            Com.call(ctl, 3, state);
            Session prev = byRoot.get(root);
            Session s = new Session(root, exe, titles.getOrDefault(root, ""), peak,
                    vol == null ? 1 : v.getValue(), m.getValue() != 0, state.getValue() == 1);
            if (prev == null) byRoot.put(root, s);
            else byRoot.put(root, new Session(root, exe, prev.title(), Math.max(prev.peak(), peak),
                    prev.volume(), prev.muted(), prev.active() || s.active()));
        });
        List<Session> out = new ArrayList<>(byRoot.values());
        out.sort((a, b) -> Float.compare(b.peak(), a.peak()));
        return out;
    }

    /** Sets the mixer volume (0..1) of every session that belongs to {@code rootPid}'s process tree. */
    public static void setVolume(int rootPid, float volume) {
        Procs procs = Procs.snapshot();
        float v = Math.max(0, Math.min(1, volume));
        forEachSession((ctl, vol) -> {
            if (vol != null && procs.root(pid(ctl)) == rootPid) Com.call(vol, 3, v, Pointer.NULL);
        });
    }

    /** Current mixer volume of {@code rootPid}'s first session, or -1 when it has none. */
    public static float volume(int rootPid) {
        Procs procs = Procs.snapshot();
        float[] out = {-1};
        forEachSession((ctl, vol) -> {
            if (out[0] < 0 && vol != null && procs.root(pid(ctl)) == rootPid) {
                FloatByReference v = new FloatByReference();
                Com.call(vol, 4, v);
                out[0] = v.getValue();
            }
        });
        return out[0];
    }

    private static int pid(Pointer ctl) {
        Pointer c2 = Com.query(ctl, IID_IAudioSessionControl2);
        if (c2 == null) return 0;
        try {
            if (Com.call(c2, 15) == Com.S_OK) return 0; // IsSystemSoundsSession
            IntByReference pid = new IntByReference();
            Com.call(c2, 14, pid);
            return pid.getValue();
        } finally {
            Com.release(c2);
        }
    }

    private static float peak(Pointer ctl) {
        Pointer meter = Com.query(ctl, IID_IAudioMeterInformation);
        if (meter == null) return 0;
        try {
            FloatByReference f = new FloatByReference();
            Com.call(meter, 3, f);
            return f.getValue();
        } finally {
            Com.release(meter);
        }
    }

    /** Calls {@code action(sessionControl, simpleVolumeOrNull)} for every session of the default render device. */
    private static void forEachSession(BiConsumer<Pointer, Pointer> action) {
        Com.initThread();
        Pointer en = Com.create(CLSID_MMDeviceEnumerator, IID_IMMDeviceEnumerator);
        Pointer dev = null, mgr = null, list = null;
        try {
            PointerByReference r = new PointerByReference();
            Com.check("GetDefaultAudioEndpoint", Com.call(en, 4, 0 /*eRender*/, 1 /*eMultimedia*/, r));
            dev = r.getValue();
            r = new PointerByReference();
            Com.check("Activate(IAudioSessionManager2)", Com.call(dev, 3, Com.guid(IID_IAudioSessionManager2).getPointer(),
                    Com.CLSCTX_ALL, Pointer.NULL, r));
            mgr = r.getValue();
            r = new PointerByReference();
            Com.check("GetSessionEnumerator", Com.call(mgr, 5, r));
            list = r.getValue();
            IntByReference count = new IntByReference();
            Com.call(list, 3, count);
            for (int i = 0; i < count.getValue(); i++) {
                PointerByReference c = new PointerByReference();
                if (Com.call(list, 4, i, c) < 0) continue;
                Pointer ctl = c.getValue();
                Pointer vol = Com.query(ctl, IID_ISimpleAudioVolume);
                try {
                    action.accept(ctl, vol);
                } finally {
                    Com.release(vol);
                    Com.release(ctl);
                }
            }
        } finally {
            Com.release(list);
            Com.release(mgr);
            Com.release(dev);
            Com.release(en);
        }
    }

    /** Titles of visible top-level windows, by owning process (the most media-looking title wins). */
    private static Map<Integer, String> windowTitles() {
        Map<Integer, String> out = new HashMap<>();
        char[] buf = new char[512];
        User32.INSTANCE.EnumWindows((WinDef.HWND hwnd, Pointer data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true;
            int n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
            if (n <= 0) return true;
            String title = new String(buf, 0, n);
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            String prev = out.get(pid.getValue());
            if (prev == null || mediaScore(title) > mediaScore(prev)) out.put(pid.getValue(), title);
            return true;
        }, Pointer.NULL);
        return out;
    }

    private static int mediaScore(String t) {
        String s = t.toLowerCase(java.util.Locale.ROOT);
        return s.contains("youtube") || s.contains("soundcloud") || s.contains("spotify") || s.contains("music")
                || s.contains("radio") || s.contains("deezer") || s.contains("tidal") ? 2 : 1;
    }

    /** Process table: parent and executable path of every process. */
    private record Procs(Map<Integer, Integer> parent, Map<Integer, String> exe) {
        static Procs snapshot() {
            Map<Integer, Integer> parent = new HashMap<>();
            Map<Integer, String> exe = new HashMap<>();
            WinNT.HANDLE snap = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));
            try {
                Tlhelp32.PROCESSENTRY32.ByReference e = new Tlhelp32.PROCESSENTRY32.ByReference();
                if (Kernel32.INSTANCE.Process32First(snap, e)) {
                    do {
                        int pid = e.th32ProcessID.intValue();
                        parent.put(pid, e.th32ParentProcessID.intValue());
                        exe.put(pid, com.sun.jna.Native.toString(e.szExeFile));
                    } while (Kernel32.INSTANCE.Process32Next(snap, e));
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(snap);
            }
            return new Procs(parent, exe);
        }

        /** The topmost ancestor running the same executable (chrome's audio service → the browser process). */
        int root(int pid) {
            String name = exe.get(pid);
            int cur = pid;
            for (int guard = 0; guard < 16 && name != null; guard++) {
                Integer p = parent.get(cur);
                if (p == null || p == cur || !name.equalsIgnoreCase(exe.get(p))) break;
                cur = p;
            }
            return cur;
        }

        String path(int pid) {
            WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(0x1000 /*QUERY_LIMITED_INFORMATION*/, false, pid);
            if (h != null) {
                try {
                    char[] buf = new char[1024];
                    IntByReference len = new IntByReference(buf.length);
                    if (Kernel32.INSTANCE.QueryFullProcessImageName(h, 0, buf, len)) return new String(buf, 0, len.getValue());
                } finally {
                    Kernel32.INSTANCE.CloseHandle(h);
                }
            }
            return exe.getOrDefault(pid, "");
        }
    }

    @SuppressWarnings("unused")
    private static Memory unused() { return null; }
}
