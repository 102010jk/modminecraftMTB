package com.descentmtb.client.audio.win;

import com.sun.jna.Callback;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Function;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Captures what one program (and its child processes) plays, through Windows' process-loopback audio client
 * (Windows build 20348 and newer). Delivers interleaved stereo float frames at {@link #RATE} Hz to a
 * consumer on its own daemon thread. The program keeps playing on the speakers as well — callers decide what to do
 * with that (see the audio controller).
 */
public final class ProcessLoopback implements AutoCloseable {
    public static final int RATE = 48000;
    public static final int CHANNELS = 2;

    /** Receives {@code frames} interleaved stereo samples in {@code buf[0 .. frames*2)}; the array is reused. */
    public interface Sink {
        void accept(float[] buf, int frames);
    }

    private static final String IID_IAudioClient = "{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}";
    private static final String IID_IAudioCaptureClient = "{C8ADBD64-E71E-48A0-A4DE-185C395CD317}";
    private static final String IID_IUnknown = "{00000000-0000-0000-C000-000000000046}";
    private static final String IID_IAgileObject = "{94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90}";
    private static final String IID_Handler = "{41D949AB-9862-444A-80F6-C261334DA5EB}";

    private static final int LOOPBACK = 0x00020000, EVENTCALLBACK = 0x00040000, AUTOCONVERTPCM = 0x80000000,
            SRC_DEFAULT_QUALITY = 0x08000000;

    private final int pid;
    private final Sink sink;
    private volatile boolean running = true;
    private final Thread thread;
    private volatile String error;
    private final CountDownLatch started = new CountDownLatch(1);

    private ProcessLoopback(int pid, Sink sink) {
        this.pid = pid;
        this.sink = sink;
        this.thread = new Thread(this::run, "DescentMTB loopback " + pid);
        this.thread.setDaemon(true);
    }

    /**
     * Starts capturing {@code pid}'s process tree. Waits up to 3 s for the audio client to come up and throws when it
     * cannot (old Windows, the process is gone, access denied).
     */
    public static ProcessLoopback start(int pid, Sink sink) {
        if (!Com.AVAILABLE) throw new IllegalStateException("process loopback needs Windows");
        ProcessLoopback c = new ProcessLoopback(pid, sink);
        c.thread.start();
        try {
            if (!c.started.await(3, TimeUnit.SECONDS)) {
                c.close();
                throw new IllegalStateException("process loopback did not start in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            c.close();
            throw new IllegalStateException("interrupted");
        }
        if (c.error != null) throw new IllegalStateException(c.error);
        return c;
    }

    public int pid() { return pid; }

    public boolean alive() { return running && thread.isAlive(); }

    public String error() { return error; }

    @Override
    public void close() {
        running = false;
    }

    // ------------------------------------------------------------------ capture thread

    private void run() {
        Pointer client = null, capture = null;
        WinNT.HANDLE event = null;
        try {
            Com.initThread();
            client = activate(pid);
            Memory fmt = new Memory(18);
            fmt.clear();
            fmt.setShort(0, (short) 3);                 // WAVE_FORMAT_IEEE_FLOAT
            fmt.setShort(2, (short) CHANNELS);
            fmt.setInt(4, RATE);
            fmt.setInt(8, RATE * CHANNELS * 4);
            fmt.setShort(12, (short) (CHANNELS * 4));
            fmt.setShort(14, (short) 32);
            int hr = Com.call(client, 3, 0 /*shared*/, LOOPBACK | EVENTCALLBACK | AUTOCONVERTPCM | SRC_DEFAULT_QUALITY,
                    200_000L /*20 ms*/, 0L, fmt, Pointer.NULL);
            Com.check("IAudioClient.Initialize", hr);
            event = Kernel32.INSTANCE.CreateEvent(null, false, false, null);
            Com.check("SetEventHandle", Com.call(client, 13, event.getPointer()));
            PointerByReference r = new PointerByReference();
            Com.check("GetService(IAudioCaptureClient)", Com.call(client, 14, Com.guid(IID_IAudioCaptureClient).getPointer(), r));
            capture = r.getValue();
            Com.check("Start", Com.call(client, 10));
            started.countDown();

            float[] buf = new float[RATE / 10 * CHANNELS];
            PointerByReference data = new PointerByReference();
            IntByReference frames = new IntByReference(), flags = new IntByReference(), next = new IntByReference();
            while (running) {
                Kernel32.INSTANCE.WaitForSingleObject(event, 100);
                while (running) {
                    if (Com.call(capture, 5, next) < 0 || next.getValue() == 0) break;
                    if (Com.call(capture, 3, data, frames, flags, Pointer.NULL, Pointer.NULL) < 0) break;
                    int n = frames.getValue();
                    if (n * CHANNELS > buf.length) buf = new float[n * CHANNELS];
                    if ((flags.getValue() & 0x2) != 0 || data.getValue() == null) { // AUDCLNT_BUFFERFLAGS_SILENT
                        java.util.Arrays.fill(buf, 0, n * CHANNELS, 0f);
                    } else {
                        data.getValue().read(0, buf, 0, n * CHANNELS);
                    }
                    Com.call(capture, 4, n);
                    if (n > 0) sink.accept(buf, n);
                }
            }
            Com.call(client, 11);
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.toString() : t.getMessage();
            running = false;
        } finally {
            started.countDown();
            Com.release(capture);
            Com.release(client);
            if (event != null) Kernel32.INSTANCE.CloseHandle(event);
        }
    }

    // ------------------------------------------------------------------ ActivateAudioInterfaceAsync

    public interface QueryInterfaceFn extends Callback { int invoke(Pointer self, Pointer riid, Pointer ppv); }
    public interface RefFn extends Callback { int invoke(Pointer self); }
    public interface CompletedFn extends Callback { int invoke(Pointer self, Pointer op); }

    /** Activates an IAudioClient for {@code pid}'s process-loopback stream. */
    private static Pointer activate(int pid) throws InterruptedException {
        Memory params = new Memory(12);
        params.setInt(0, 1);   // AUDIOCLIENT_ACTIVATION_TYPE_PROCESS_LOOPBACK
        params.setInt(4, pid);
        params.setInt(8, 0);   // PROCESS_LOOPBACK_MODE_INCLUDE_TARGET_PROCESS_TREE
        Memory variant = Com.blobVariant(params);

        CountDownLatch done = new CountDownLatch(1);
        Pointer[] opHolder = new Pointer[1];
        Guid.GUID unknown = Com.guid(IID_IUnknown), agile = Com.guid(IID_IAgileObject), handlerIid = Com.guid(IID_Handler);
        Memory self = new Memory(Native.POINTER_SIZE);
        QueryInterfaceFn qi = (s, riid, ppv) -> {
            Guid.GUID g = new Guid.GUID(riid);
            if (g.equals(unknown) || g.equals(agile) || g.equals(handlerIid)) {
                ppv.setPointer(0, s);
                return 0;
            }
            ppv.setPointer(0, Pointer.NULL);
            return Com.E_NOINTERFACE;
        };
        RefFn ref = s -> 1;
        CompletedFn completed = (s, op) -> {
            opHolder[0] = op;
            done.countDown();
            return 0;
        };
        Memory vtbl = new Memory(4L * Native.POINTER_SIZE);
        vtbl.setPointer(0, com.sun.jna.CallbackReference.getFunctionPointer(qi));
        vtbl.setPointer(Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(ref));
        vtbl.setPointer(2L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(ref));
        vtbl.setPointer(3L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(completed));
        self.setPointer(0, vtbl);

        Function fn = NativeLibrary.getInstance("Mmdevapi").getFunction("ActivateAudioInterfaceAsync", Function.ALT_CONVENTION);
        PointerByReference asyncOp = new PointerByReference();
        Memory path = new Memory(("VAD\\Process_Loopback".length() + 1) * 2L);
        path.setWideString(0, "VAD\\Process_Loopback");
        int hr = fn.invokeInt(new Object[]{path, Com.guid(IID_IAudioClient).getPointer(), variant, self, asyncOp});
        Com.check("ActivateAudioInterfaceAsync", hr);
        try {
            if (!done.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("audio activation timed out");
            IntByReference activateHr = new IntByReference();
            PointerByReference unk = new PointerByReference();
            Com.check("GetActivateResult", Com.call(asyncOp.getValue(), 3, activateHr, unk));
            Com.check("process loopback activation", activateHr.getValue());
            Pointer client = Com.query(unk.getValue(), IID_IAudioClient);
            Com.release(unk.getValue());
            if (client == null) throw new IllegalStateException("no IAudioClient");
            return client;
        } finally {
            Com.release(asyncOp.getValue());
            // keep the callbacks reachable until the call has completed
            java.lang.ref.Reference.reachabilityFence(qi);
            java.lang.ref.Reference.reachabilityFence(ref);
            java.lang.ref.Reference.reachabilityFence(completed);
            java.lang.ref.Reference.reachabilityFence(vtbl);
            java.lang.ref.Reference.reachabilityFence(self);
            java.lang.ref.Reference.reachabilityFence(variant);
            java.lang.ref.Reference.reachabilityFence(params);
            java.lang.ref.Reference.reachabilityFence(opHolder);
            @SuppressWarnings("unused") LongByReference keep = null;
        }
    }
}
