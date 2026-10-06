package com.descentmtb.map;

import java.util.Arrays;

/**
 * Collects the positions of a ride into a track. A point is only kept once the rider has moved {@link #SPACING}
 * blocks from the last kept point; if the buffer fills up it is simplified in place, so a long ride never runs out of
 * room. {@link #finish()} gives the final, simplified, size-limited track. Pure and unit-tested.
 */
public final class TrackRecorder {
    /** Distance between two sampled points (blocks). */
    public static final double SPACING = 0.5;
    /** Points the buffer holds before it is simplified. */
    public static final int BUFFER_CAP = 4000;
    /** Simplification tolerance of the finished track (blocks). */
    public static final double TOLERANCE = 0.3;

    private int[] buf = new int[256 * 3];
    private int n;
    /** Exact (not rounded) last kept position, so slow rounding drift cannot skip points. */
    private double lastX, lastY, lastZ;
    private double travelled;

    /** Offers the current position; returns true if it was kept as a new point. */
    public boolean offer(double x, double y, double z) {
        if (n > 0) {
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < SPACING) {
                return false;
            }
            travelled += Math.sqrt(dx * dx + dz * dz);
        }
        if (n == BUFFER_CAP) {
            compress();
        }
        if (n * 3 + 3 > buf.length) {
            buf = Arrays.copyOf(buf, buf.length * 2);
        }
        buf[n * 3] = TrackGeometry.pack(x);
        buf[n * 3 + 1] = TrackGeometry.pack(y);
        buf[n * 3 + 2] = TrackGeometry.pack(z);
        n++;
        lastX = x;
        lastY = y;
        lastZ = z;
        return true;
    }

    public int size() {
        return n;
    }

    /** Horizontal distance recorded so far (blocks). */
    public double travelled() {
        return travelled;
    }

    private void compress() {
        int[] out = TrackGeometry.simplifyTo(Arrays.copyOf(buf, n * 3), TOLERANCE, BUFFER_CAP * 3 / 4);
        System.arraycopy(out, 0, buf, 0, out.length);
        n = out.length / 3;
    }

    /** The finished track: simplified and at most {@link TrackGeometry#MAX_POINTS} points; empty if too short. */
    public int[] finish() {
        if (n < TrackGeometry.MIN_POINTS) {
            return new int[0];
        }
        int[] out = TrackGeometry.simplifyTo(Arrays.copyOf(buf, n * 3), TOLERANCE, TrackGeometry.MAX_POINTS);
        return TrackGeometry.stats(out).length() < TrackGeometry.MIN_LENGTH ? new int[0] : out;
    }
}
