package com.descentmtb.client.audio.win;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;

/**
 * Minimal raw-vtable COM helper over JNA (bundled with Minecraft). Only what the Windows audio-session and
 * process-loopback code needs: create an object, call a vtable slot, query an interface and release it.
 * Everything here is Windows-only; callers check {@link #AVAILABLE} first.
 */
public final class Com {
    private Com() {}

    public static final boolean AVAILABLE = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

    public static final int CLSCTX_ALL = 0x17;
    public static final int COINIT_MULTITHREADED = 0x0;
    public static final int S_OK = 0, S_FALSE = 1;
    public static final int E_NOINTERFACE = 0x80004002;
    public static final int RPC_E_CHANGED_MODE = 0x80010106;

    public static Guid.GUID guid(String s) {
        return new Guid.GUID(s);
    }

    /** Joins the calling thread to the multithreaded apartment (idempotent; an STA thread is fine too). */
    public static void initThread() {
        int hr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, COINIT_MULTITHREADED).intValue();
        if (hr < 0 && hr != RPC_E_CHANGED_MODE) throw new ComException("CoInitializeEx", hr);
    }

    public static Pointer create(String clsid, String iid) {
        PointerByReference out = new PointerByReference();
        check("CoCreateInstance", Ole32.INSTANCE.CoCreateInstance(guid(clsid), Pointer.NULL, CLSCTX_ALL, guid(iid), out).intValue());
        return out.getValue();
    }

    private static Function slot(Pointer self, int index) {
        Pointer vtbl = self.getPointer(0);
        return Function.getFunction(vtbl.getPointer((long) index * Native.POINTER_SIZE), Function.ALT_CONVENTION);
    }

    /** Calls vtable slot {@code index} of {@code self} (self is passed as the implicit first argument). */
    public static int call(Pointer self, int index, Object... args) {
        Object[] a = new Object[args.length + 1];
        a[0] = self;
        System.arraycopy(args, 0, a, 1, args.length);
        return slot(self, index).invokeInt(a);
    }

    public static void check(String what, int hr) {
        if (hr < 0) throw new ComException(what, hr);
    }

    /** {@code IUnknown::QueryInterface}; null when the object does not implement it. */
    public static Pointer query(Pointer self, String iid) {
        PointerByReference out = new PointerByReference();
        int hr = call(self, 0, guid(iid).getPointer(), out);
        return hr < 0 ? null : out.getValue();
    }

    public static void release(Pointer p) {
        if (p != null) call(p, 2);
    }

    /** Reads and frees a {@code CoTaskMemAlloc}'d wide string out-parameter. */
    public static String takeString(PointerByReference ref) {
        Pointer p = ref.getValue();
        if (p == null) return "";
        try {
            return p.getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(p);
        }
    }

    /** A {@code PROPVARIANT} holding a {@code VT_BLOB} that points at {@code blob} (24 bytes on x64). */
    public static Memory blobVariant(Memory blob) {
        Memory v = new Memory(Native.POINTER_SIZE == 8 ? 24 : 16);
        v.clear();
        v.setShort(0, (short) 65); // VT_BLOB
        v.setInt(8, (int) blob.size());
        v.setPointer(Native.POINTER_SIZE == 8 ? 16 : 12, blob);
        return v;
    }

    public static final class ComException extends RuntimeException {
        public final int hr;

        public ComException(String what, int hr) {
            super(what + " failed: 0x" + Integer.toHexString(hr));
            this.hr = hr;
        }
    }
}
