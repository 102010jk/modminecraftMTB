package com.descentmtb.client.audio;

/**
 * A bounded, thread-safe ring of float samples between a producer (the capture thread or the network) and the
 * sound thread. When full, the oldest samples are dropped so the latency never grows past the capacity.
 * Pure (no Minecraft classes), unit-tested.
 */
public final class PcmRing {
    private final float[] data;
    private int read, size;
    private long dropped;

    public PcmRing(int capacitySamples) {
        data = new float[Math.max(16, capacitySamples)];
    }

    public synchronized void write(float[] src, int off, int len) {
        if (len >= data.length) { // keep only the newest
            off += len - data.length;
            dropped += size + len - data.length;
            len = data.length;
            read = 0;
            size = 0;
        }
        int overflow = size + len - data.length;
        if (overflow > 0) {
            read = (read + overflow) % data.length;
            size -= overflow;
            dropped += overflow;
        }
        int w = (read + size) % data.length;
        int first = Math.min(len, data.length - w);
        System.arraycopy(src, off, data, w, first);
        System.arraycopy(src, off + first, data, 0, len - first);
        size += len;
    }

    /** Reads up to {@code len} samples; returns how many were read. */
    public synchronized int read(float[] dst, int off, int len) {
        int n = Math.min(len, size);
        int first = Math.min(n, data.length - read);
        System.arraycopy(data, read, dst, off, first);
        System.arraycopy(data, 0, dst, off + first, n - first);
        read = (read + n) % data.length;
        size -= n;
        return n;
    }

    public synchronized int available() { return size; }

    public synchronized long dropped() { return dropped; }

    public synchronized void clear() {
        read = 0;
        size = 0;
    }

    public int capacity() { return data.length; }
}
