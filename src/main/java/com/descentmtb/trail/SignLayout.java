package com.descentmtb.trail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Where everything goes on the front of a sign. Pure geometry shared by the in-world renderer and the editor
 * preview, so both always look the same.
 *
 * <p>Coordinates are in "font units" (1/96 of a block, the size of one font pixel on a vanilla sign). The board
 * face is {@value #WIDTH} x {@value #HEIGHT} units, origin top left, with a {@value #FRAME} unit frame around it.
 */
public final class SignLayout {
    public static final double WIDTH = 84;
    public static final double HEIGHT = 60;
    public static final double FRAME = 6;

    /** Height of one text line at scale 1 (an 8 pixel font plus one pixel of spacing). */
    public static final double LINE = 9;
    private static final double MIN_SCALE = 0.4;

    private static final double ICON_SIZE = 28;
    private static final double ICON_X = 9;
    private static final double TEXT_LEFT = 42;
    private static final double TEXT_RIGHT = 78;
    private static final double TEXT_CENTRE = (TEXT_LEFT + TEXT_RIGHT) / 2;
    private static final double INNER_TOP = FRAME;
    private static final double INNER_HEIGHT = HEIGHT - 2 * FRAME;

    /** One thing to draw. */
    public sealed interface Item permits Icon, Label, Art {}

    /** A square texture from {@code textures/sign/<name>.png}. */
    public record Icon(String name, double x, double y, double size) implements Item {}

    /** One line of text, centred on {@code centreX}, glyphs scaled by {@code scale}. */
    public record Label(String text, double centreX, double top, double scale) implements Item {}

    /** The 16 x 16 pixel art, {@code cell} units per pixel. */
    public record Art(double x, double y, double cell) implements Item {}

    /** Text broken into lines, all drawn at one scale. */
    public record Fit(List<String> lines, double scale) {}

    /**
     * Lays out one sign.
     *
     * @param width        measures a string in font units at scale 1
     * @param startLabel   the word printed on START signs
     * @param finishLabel  the word printed on FINISH signs
     */
    public static List<Item> build(SignContent sign, String startLabel, String finishLabel, ToDoubleFunction<String> width) {
        List<Item> items = new ArrayList<>();
        switch (sign.type()) {
            case TRAIL -> trail(sign, items, width);
            case START -> gate(sign, sign.difficulty().icon(), startLabel, items, width);
            case FINISH -> gate(sign, "finish", finishLabel, items, width);
            case WARNING -> warning(sign, items, width);
            case CUSTOM -> items.add(new Art((WIDTH - 16 * 3) / 2, INNER_TOP, 3));
        }
        return items;
    }

    private static void trail(SignContent sign, List<Item> items, ToDoubleFunction<String> width) {
        items.add(new Icon(sign.difficulty().icon(), ICON_X, iconTop(), ICON_SIZE));
        if (sign.arrow() == SignContent.Arrow.NONE) {
            textBlock(items, sign.name(), INNER_TOP, INNER_HEIGHT, width);
        } else {
            textBlock(items, sign.name(), INNER_TOP, 31, width);
            items.add(new Icon(sign.arrow().icon(), TEXT_CENTRE - 7, 39, 14));
        }
    }

    /** START and FINISH: marker on the left, name on top, the word START / FINISH underneath. */
    private static void gate(SignContent sign, String icon, String label, List<Item> items, ToDoubleFunction<String> width) {
        items.add(new Icon(icon, ICON_X, iconTop(), ICON_SIZE));
        textBlock(items, sign.name(), 7, 32, width);
        items.add(new Label(label, TEXT_CENTRE, 42, 1));
    }

    private static void warning(SignContent sign, List<Item> items, ToDoubleFunction<String> width) {
        String icon = sign.warning().icon();
        if (sign.text().isEmpty()) {
            double size = 40;
            items.add(new Icon(icon, (WIDTH - size) / 2, (HEIGHT - size) / 2, size));
        } else {
            items.add(new Icon(icon, ICON_X, iconTop(), ICON_SIZE));
            textBlock(items, sign.text(), INNER_TOP, INNER_HEIGHT, width);
        }
    }

    private static double iconTop() {
        return (HEIGHT - ICON_SIZE) / 2;
    }

    /** Fits {@code text} into the right-hand text column between {@code top} and {@code top + height}, vertically centred. */
    private static void textBlock(List<Item> items, String text, double top, double height, ToDoubleFunction<String> width) {
        if (text.isEmpty()) {
            return;
        }
        Fit fit = fitText(text, TEXT_RIGHT - TEXT_LEFT, height, 2, width);
        double blockHeight = fit.lines().size() * LINE * fit.scale();
        double y = top + (height - blockHeight) / 2;
        for (String line : fit.lines()) {
            items.add(new Label(line, TEXT_CENTRE, y, fit.scale()));
            y += LINE * fit.scale();
        }
    }

    /**
     * Picks the largest scale at which the text fits in {@code maxLines} lines inside the box. Words are never
     * split unless even the smallest scale is not enough.
     */
    public static Fit fitText(String text, double maxWidth, double maxHeight, int maxLines, ToDoubleFunction<String> width) {
        for (double scale = 1.0; scale >= MIN_SCALE - 1e-9; scale -= 0.05) {
            List<String> lines = wrap(text, maxWidth / scale, false, width);
            if (lines != null && lines.size() <= maxLines && lines.size() * LINE * scale <= maxHeight + 1e-9) {
                return new Fit(lines, scale);
            }
        }
        List<String> lines = wrap(text, maxWidth / MIN_SCALE, true, width);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, maxLines));
        }
        return new Fit(lines, MIN_SCALE);
    }

    /** Greedy word wrap; returns null if a word is too wide and {@code splitWords} is off. */
    private static List<String> wrap(String text, double limit, boolean splitWords, ToDoubleFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String joined = line.isEmpty() ? word : line + " " + word;
            if (width.applyAsDouble(joined) <= limit) {
                line.setLength(0);
                line.append(joined);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (width.applyAsDouble(word) <= limit) {
                line.append(word);
            } else if (!splitWords) {
                return null;
            } else {
                for (char c : word.toCharArray()) {
                    if (!line.isEmpty() && width.applyAsDouble(line.toString() + c) > limit) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(c);
                }
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private SignLayout() {}
}
