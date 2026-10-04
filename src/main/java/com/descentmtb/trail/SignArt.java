package com.descentmtb.trail;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * The 16 x 16 pixel canvas of a custom sign, with the editing operations of the sign editor.
 * Pure Java: undo snapshots are taken per stroke and the flood fill is bounded by the canvas size.
 */
public final class SignArt {
    public static final int SIZE = 16;

    /** ARGB colours; a pixel stores an index into this table. */
    public static final int[] PALETTE = {
            0xff26323b, 0xfff0ede2, 0xffefb84f, 0xffe36a4f, 0xff68b483, 0xff529bd0, 0xffaa78c9, 0xff4d4140,
            0xff84919a, 0xffba8e60, 0xffd9caab, 0xff152027, 0xff96d8ce, 0xffe8a4bf, 0xffc7dc65, 0xff707b8c};

    private static final int MAX_UNDO = 32;
    private static final int TEMPLATE_COUNT = 5;

    private byte[] pixels = new byte[SIZE * SIZE];
    private final ArrayDeque<byte[]> undo = new ArrayDeque<>();

    public SignArt(byte[] initial) {
        if (initial.length == pixels.length) {
            pixels = initial.clone();
        }
        sanitize();
    }

    private void sanitize() {
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (pixels[i] & 15);
        }
    }

    public byte[] pixels() {
        return pixels.clone();
    }

    public int colour(int x, int y) {
        return PALETTE[pixels[y * SIZE + x] & 15];
    }

    /** Remembers the canvas so the next change can be undone as one stroke. */
    public void beginStroke() {
        undo.addLast(pixels.clone());
        while (undo.size() > MAX_UNDO) {
            undo.removeFirst();
        }
    }

    public void paint(int x, int y, int colour) {
        if (inside(x, y)) {
            pixels[y * SIZE + x] = (byte) (colour & 15);
        }
    }

    /** Flood fill of the connected area with the same colour; one undo step. */
    public void fill(int x, int y, int colour) {
        if (!inside(x, y)) {
            return;
        }
        colour &= 15;
        int old = pixels[y * SIZE + x] & 15;
        if (old == colour) {
            return;
        }
        beginStroke();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(y * SIZE + x);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if ((pixels[index] & 15) != old) {
                continue;
            }
            pixels[index] = (byte) colour;
            int px = index % SIZE, py = index / SIZE;
            if (px > 0) {
                queue.add(index - 1);
            }
            if (px < SIZE - 1) {
                queue.add(index + 1);
            }
            if (py > 0) {
                queue.add(index - SIZE);
            }
            if (py < SIZE - 1) {
                queue.add(index + SIZE);
            }
        }
    }

    public void undo() {
        if (!undo.isEmpty()) {
            pixels = undo.removeLast();
        }
    }

    /** Replaces the whole canvas (paste); one undo step. */
    public void replace(byte[] other) {
        if (other.length != pixels.length) {
            return;
        }
        beginStroke();
        pixels = other.clone();
        sanitize();
    }

    /** Clears the canvas and draws one of the starting templates: arrow, difficulty, jump, drop, warning. */
    public void template(int mode) {
        beginStroke();
        Arrays.fill(pixels, (byte) 0);
        for (int y = 1; y < SIZE - 1; y++) {
            for (int x = 1; x < SIZE - 1; x++) {
                if (templateMark(mode, x, y)) {
                    paint(x, y, mode == 1 ? 4 : 2);
                }
            }
        }
        if (mode == TEMPLATE_COUNT - 1) {
            // the exclamation mark of the warning triangle
            for (int y = 6; y < 10; y++) {
                paint(7, y, 0);
            }
            paint(7, 11, 0);
        }
    }

    private static boolean templateMark(int mode, int x, int y) {
        return switch (mode) {
            case 0 -> (y >= 6 && y <= 9 && x > 3 && x < 13) || (x >= 8 && Math.abs(y - 7) < x - 7);
            case 1 -> Math.abs(x - 7) + Math.abs(y - 7) < 6;
            case 2 -> y == 12 || Math.abs(y - (13 - x / 2)) < 1 && x > 2 && x < 13;
            case 3 -> y == 5 && x < 9 || x == 8 && y >= 5 && y < 11 || y == 11 && x >= 8;
            default -> y >= 2 && y <= 13 && Math.abs(x - 7) < (y - 1) / 2;
        };
    }

    private static boolean inside(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }
}
