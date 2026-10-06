package com.descentmtb.client.custom;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The scrolling list on the right of the workshop screen. A vertical stack of {@link Item}s (headers, swatch grids,
 * option rows, chips, sliders, a colour picker ...) drawn immediate-mode inside a scissor, with a draggable scroll
 * bar and mouse-wheel scrolling. The screen rebuilds the item list whenever the build changes; items that are
 * dragged at that moment are re-linked by {@link Item#key}.
 */
final class WorkshopPanel {
    static final int GOLD = 0xffe2c48a, LABEL = 0xffa9b4b8, VALUE = 0xffeee6d2, DIM = 0xff5f6a6e;
    static final int BG = 0xff1d2a31, ROW_HOVER = 0xff26363e, ROW_SELECTED = 0xff2f4a54, EDGE = 0xff3a4d57;
    private static final int SCROLL_W = 4, PAD = 3, GAP = 3;

    int x, y, w, h;
    private final Font font;
    private List<Item> items = List.of();
    private int contentH;
    private double scroll;
    private Item dragging;
    private boolean draggingBar;

    WorkshopPanel(Font font) {
        this.font = font;
    }

    // ------------------------------------------------------------------ layout

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        relayout();
    }

    void setItems(List<Item> list) {
        if (dragging != null) {
            Item linked = null;
            if (dragging.key != null) {
                for (Item item : list) {
                    if (dragging.key.equals(item.key)) {
                        linked = item;
                    }
                }
            }
            dragging = linked;
        }
        items = list;
        relayout();
    }

    void unfocusAll() {
        for (Item item : items) {
            item.unfocus();
        }
    }

    void resetScroll() {
        scroll = 0;
    }

    private void relayout() {
        int innerW = w - 2 * PAD - SCROLL_W;
        int off = 0;
        for (Item item : items) {
            item.w = innerW;
            item.h = item.layout(font, innerW);
            item.off = off;
            off += item.h + GAP;
        }
        contentH = Math.max(0, off - GAP) + 2 * PAD;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - h)));
    }

    private void position() {
        for (Item item : items) {
            item.ax = x + PAD;
            item.ay = y + PAD + item.off - (int) scroll;
        }
    }

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ drawing

    void render(GuiGraphics g, int mx, int my) {
        position();
        g.fill(x, y, x + w, y + h, BG);
        boolean hover = inside(mx, my);
        int hx = hover ? mx : -9999, hy = hover ? my : -9999;
        g.enableScissor(x, y, x + w, y + h);
        for (Item item : items) {
            if (item.ay + item.h >= y && item.ay <= y + h) {
                item.render(g, font, hx, hy);
            }
        }
        g.disableScissor();
        if (contentH > h) {
            int trackX = x + w - SCROLL_W - 1;
            g.fill(trackX, y + 1, trackX + SCROLL_W, y + h - 1, 0xff10181d);
            int thumbH = Math.max(12, (int) ((long) h * h / contentH));
            int thumbY = y + 1 + (int) ((h - 2 - thumbH) * (scroll / (contentH - h)));
            g.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, draggingBar ? GOLD : 0xff7d8f98);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, EDGE);
    }

    Component tooltip(int mx, int my) {
        if (!inside(mx, my) || dragging != null) {
            return null;
        }
        position();
        for (Item item : items) {
            if (item.contains(mx, my)) {
                return item.tooltip(mx, my);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(double mx, double my, int button) {
        if (!inside(mx, my)) {
            for (Item item : items) {
                item.unfocus();
            }
            return false;
        }
        position();
        if (button == 0 && contentH > h && mx >= x + w - SCROLL_W - 3) {
            draggingBar = true;
            scrollToMouse(my);
            return true;
        }
        Item hit = null;
        for (Item item : items) {
            if (item.contains(mx, my)) {
                hit = item;
            }
        }
        for (Item item : items) {
            if (item != hit) {
                item.unfocus();
            }
        }
        if (hit != null && button == 0 && hit.click(mx, my) && hit.wantsDrag()) {
            // the click may have rebuilt the list: keep dragging the item that is in it now
            Item current = hit;
            if (hit.key != null) {
                for (Item item : items) {
                    if (hit.key.equals(item.key)) {
                        current = item;
                    }
                }
            }
            dragging = current;
        }
        return true;
    }

    boolean mouseDragged(double mx, double my) {
        if (draggingBar) {
            scrollToMouse(my);
            return true;
        }
        if (dragging != null) {
            position();
            dragging.drag(mx, my);
            return true;
        }
        return false;
    }

    boolean mouseReleased() {
        boolean was = draggingBar || dragging != null;
        if (dragging != null) {
            dragging.release();
            dragging = null;
        }
        draggingBar = false;
        return was;
    }

    boolean mouseScrolled(double mx, double my, double dy) {
        if (!inside(mx, my)) {
            return false;
        }
        scroll = Math.max(0, Math.min(Math.max(0, contentH - h), scroll - dy * 16));
        return true;
    }

    boolean keyPressed(int key, int scan, int mods) {
        for (Item item : items) {
            if (item.focused() && item.key(key, scan, mods)) {
                return true;
            }
        }
        return false;
    }

    boolean charTyped(char c, int mods) {
        for (Item item : items) {
            if (item.focused() && item.typed(c, mods)) {
                return true;
            }
        }
        return false;
    }

    boolean anyFocused() {
        for (Item item : items) {
            if (item.focused()) {
                return true;
            }
        }
        return false;
    }

    private void scrollToMouse(double my) {
        int thumbH = Math.max(12, (int) ((long) h * h / contentH));
        double f = (my - y - thumbH / 2.0) / Math.max(1, h - thumbH);
        scroll = Math.max(0, Math.min(contentH - h, f * (contentH - h)));
    }

    // ================================================================== items

    abstract static class Item {
        /** Identity across rebuilds for items that are being dragged. */
        String key;
        int ax, ay, w, h, off;

        abstract int layout(Font font, int width);

        abstract void render(GuiGraphics g, Font font, int mx, int my);

        boolean click(double mx, double my) {
            return false;
        }

        boolean wantsDrag() {
            return false;
        }

        void drag(double mx, double my) {}

        void release() {}

        Component tooltip(int mx, int my) {
            return null;
        }

        boolean key(int key, int scan, int mods) {
            return false;
        }

        boolean typed(char c, int mods) {
            return false;
        }

        void unfocus() {}

        boolean focused() {
            return false;
        }

        final boolean contains(double mx, double my) {
            return mx >= ax && mx < ax + w && my >= ay && my < ay + h;
        }

        final boolean hovered(int mx, int my) {
            return contains(mx, my);
        }
    }

    // ---- header / note ----

    static final class Header extends Item {
        private final Component text;

        Header(Component text) {
            this.text = text;
        }

        @Override
        int layout(Font font, int width) {
            return 12;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            g.drawString(font, text, ax, ay + 2, GOLD, false);
            int tw = font.width(text);
            g.fill(ax + tw + 4, ay + 6, ax + w, ay + 7, EDGE);
        }
    }

    static final class Note extends Item {
        private final Component text;
        private final int color;
        private List<FormattedCharSequence> lines = List.of();

        Note(Component text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        int layout(Font font, int width) {
            lines = font.split(text, width);
            return Math.max(1, lines.size()) * 10;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            int yy = ay;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, ax, yy, color, false);
                yy += 10;
            }
        }
    }

    // ---- swatches ----

    interface Painter {
        void paint(GuiGraphics g, int x, int y, int size);
    }

    record Swatch(int rgb, Component tip, boolean selected, Runnable onClick, Painter painter) {
        static Swatch of(int rgb, Component tip, boolean selected, Runnable onClick) {
            return new Swatch(rgb, tip, selected, onClick, null);
        }
    }

    static final class Swatches extends Item {
        private final List<Swatch> cells;
        private final int size;
        private int cols = 1;

        Swatches(List<Swatch> cells, int size) {
            this.cells = cells;
            this.size = size;
        }

        @Override
        int layout(Font font, int width) {
            cols = Math.max(1, (width + GAP) / (size + GAP));
            int rows = (cells.size() + cols - 1) / cols;
            return rows == 0 ? 0 : rows * (size + GAP) - GAP;
        }

        private int cellAt(double mx, double my) {
            int lx = (int) mx - ax, ly = (int) my - ay;
            if (lx < 0 || ly < 0) {
                return -1;
            }
            int c = lx / (size + GAP), r = ly / (size + GAP);
            if (lx % (size + GAP) >= size || ly % (size + GAP) >= size || c >= cols) {
                return -1;
            }
            int i = r * cols + c;
            return i < cells.size() ? i : -1;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            int hover = cellAt(mx, my);
            for (int i = 0; i < cells.size(); i++) {
                Swatch s = cells.get(i);
                int cx = ax + (i % cols) * (size + GAP), cy = ay + (i / cols) * (size + GAP);
                if (s.painter != null) {
                    g.fill(cx, cy, cx + size, cy + size, 0xff141d22);
                    s.painter.paint(g, cx, cy, size);
                } else {
                    g.fill(cx - 1, cy - 1, cx + size + 1, cy + size + 1, 0xff0c1114);
                    g.fill(cx, cy, cx + size, cy + size, 0xff000000 | s.rgb);
                }
                if (s.selected) {
                    g.renderOutline(cx - 2, cy - 2, size + 4, size + 4, GOLD);
                } else if (i == hover) {
                    g.renderOutline(cx - 1, cy - 1, size + 2, size + 2, 0xffffffff);
                }
            }
        }

        @Override
        boolean click(double mx, double my) {
            int i = cellAt(mx, my);
            if (i >= 0) {
                cells.get(i).onClick.run();
                return true;
            }
            return false;
        }

        @Override
        Component tooltip(int mx, int my) {
            int i = cellAt(mx, my);
            return i >= 0 ? cells.get(i).tip : null;
        }
    }

    // ---- option rows ----

    record Option(Component title, Component subtitle, Component tag, int tagColor, int dot, boolean selected,
                  boolean enabled, Runnable onClick) {
        static Option simple(Component title, int dot, boolean selected, Runnable onClick) {
            return new Option(title, null, null, 0, dot, selected, true, onClick);
        }
    }

    static final class Options extends Item {
        private final List<Option> rows;
        private int[] top = new int[0];

        Options(List<Option> rows) {
            this.rows = rows;
        }

        private static int rowHeight(Option o) {
            return o.subtitle != null || o.tag != null ? 22 : 14;
        }

        @Override
        int layout(Font font, int width) {
            top = new int[rows.size() + 1];
            int yy = 0;
            for (int i = 0; i < rows.size(); i++) {
                top[i] = yy;
                yy += rowHeight(rows.get(i)) + 1;
            }
            top[rows.size()] = yy;
            return Math.max(0, yy - 1);
        }

        private int rowAt(double my) {
            int ly = (int) my - ay;
            for (int i = 0; i < rows.size(); i++) {
                if (ly >= top[i] && ly < top[i] + rowHeight(rows.get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            int hover = contains(mx, my) ? rowAt(my) : -1;
            for (int i = 0; i < rows.size(); i++) {
                Option o = rows.get(i);
                int rh = rowHeight(o), ry = ay + top[i];
                if (o.selected) {
                    g.fill(ax, ry, ax + w, ry + rh, ROW_SELECTED);
                    g.fill(ax, ry, ax + 2, ry + rh, GOLD);
                } else if (i == hover && o.enabled) {
                    g.fill(ax, ry, ax + w, ry + rh, ROW_HOVER);
                }
                int textX = ax + 6;
                int centre = ry + (rh == 14 ? 3 : 2);
                if (o.dot >= 0) {
                    int dy = ry + (rh == 14 ? 3 : 2);
                    g.fill(textX - 1, dy - 1, textX + 9, dy + 9, 0xff0c1114);
                    g.fill(textX, dy, textX + 8, dy + 8, 0xff000000 | o.dot);
                    textX += 13;
                }
                int avail = ax + w - textX - 3;
                int color = !o.enabled ? DIM : o.selected ? VALUE : LABEL;
                g.drawString(font, font.plainSubstrByWidth(o.title.getString(), avail), textX, centre, color, false);
                if (rh > 14) {
                    int tagW = o.tag != null ? font.width(o.tag) + 4 : 0;
                    if (o.subtitle != null) {
                        g.drawString(font, font.plainSubstrByWidth(o.subtitle.getString(), avail - tagW), textX, ry + 12, DIM, false);
                    }
                    if (o.tag != null) {
                        g.drawString(font, o.tag, ax + w - font.width(o.tag) - 3, ry + 12, 0xff000000 | o.tagColor, false);
                    }
                }
            }
        }

        @Override
        boolean click(double mx, double my) {
            int i = rowAt(my);
            if (i >= 0 && rows.get(i).enabled) {
                rows.get(i).onClick.run();
                return true;
            }
            return false;
        }

        @Override
        Component tooltip(int mx, int my) {
            int i = rowAt(my);
            return i >= 0 && rows.get(i).title.getString().length() > 24 ? rows.get(i).title : null;
        }
    }

    // ---- chips (small text buttons in a flow) ----

    record Chip(Component label, boolean selected, boolean enabled, int dot, Runnable onClick) {
        static Chip of(Component label, boolean selected, Runnable onClick) {
            return new Chip(label, selected, true, -1, onClick);
        }
    }

    static final class Chips extends Item {
        private final List<Chip> chips;
        private int[] cx, cy, cw;

        Chips(List<Chip> chips) {
            this.chips = chips;
        }

        @Override
        int layout(Font font, int width) {
            int n = chips.size();
            cx = new int[n];
            cy = new int[n];
            cw = new int[n];
            int xx = 0, line = 0;
            for (int i = 0; i < n; i++) {
                Chip c = chips.get(i);
                int cwi = Math.max(22, font.width(c.label) + 10 + (c.dot >= 0 ? 10 : 0));
                cwi = Math.min(cwi, width);
                if (xx > 0 && xx + cwi > width) {
                    xx = 0;
                    line++;
                }
                cx[i] = xx;
                cy[i] = line * 16;
                cw[i] = cwi;
                xx += cwi + 3;
            }
            return n == 0 ? 0 : (line + 1) * 16 - 2;
        }

        private int chipAt(double mx, double my) {
            int lx = (int) mx - ax, ly = (int) my - ay;
            for (int i = 0; i < chips.size(); i++) {
                if (lx >= cx[i] && lx < cx[i] + cw[i] && ly >= cy[i] && ly < cy[i] + 14) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            int hover = contains(mx, my) ? chipAt(mx, my) : -1;
            for (int i = 0; i < chips.size(); i++) {
                Chip c = chips.get(i);
                int x0 = ax + cx[i], y0 = ay + cy[i], x1 = x0 + cw[i], y1 = y0 + 14;
                int bg = c.selected ? ROW_SELECTED : i == hover && c.enabled ? 0xff33454e : 0xff24323a;
                g.fill(x0, y0, x1, y1, bg);
                g.renderOutline(x0, y0, cw[i], 14, c.selected ? GOLD : EDGE);
                int tx = x0 + 5;
                if (c.dot >= 0) {
                    g.fill(tx, y0 + 3, tx + 8, y0 + 11, 0xff000000 | c.dot);
                    g.renderOutline(tx, y0 + 3, 8, 8, 0xff0c1114);
                    tx += 10;
                }
                int color = !c.enabled ? DIM : c.selected ? VALUE : LABEL;
                g.drawString(font, font.plainSubstrByWidth(c.label.getString(), x1 - tx - 3), tx, y0 + 3, color, false);
            }
        }

        @Override
        boolean click(double mx, double my) {
            int i = chipAt(mx, my);
            if (i >= 0 && chips.get(i).enabled) {
                chips.get(i).onClick.run();
                return true;
            }
            return false;
        }
    }

    // ---- toggle ----

    static final class Toggle extends Item {
        private final Component label;
        private final boolean on;
        private final Runnable onClick;

        Toggle(Component label, boolean on, Runnable onClick) {
            this.label = label;
            this.on = on;
            this.onClick = onClick;
        }

        @Override
        int layout(Font font, int width) {
            return 14;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            if (hovered(mx, my)) {
                g.fill(ax, ay, ax + w, ay + h, ROW_HOVER);
            }
            g.fill(ax + 3, ay + 2, ax + 13, ay + 12, 0xff0c1114);
            g.fill(ax + 4, ay + 3, ax + 12, ay + 11, 0xff24323a);
            if (on) {
                g.fill(ax + 5, ay + 4, ax + 11, ay + 10, GOLD);
            }
            g.drawString(font, label, ax + 18, ay + 3, on ? VALUE : LABEL, false);
        }

        @Override
        boolean click(double mx, double my) {
            onClick.run();
            return true;
        }
    }

    // ---- slider ----

    static final class Slider extends Item {
        private final Component label;
        private final double min, max, step;
        private final DoubleSupplier value;
        private final DoubleConsumer onChange;
        private final DoubleFunction<String> format;
        private final Runnable onEnd;
        private boolean active;

        Slider(String key, Component label, double min, double max, double step, DoubleSupplier value,
               DoubleFunction<String> format, DoubleConsumer onChange, Runnable onEnd) {
            this.key = key;
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.value = value;
            this.format = format;
            this.onChange = onChange;
            this.onEnd = onEnd;
        }

        @Override
        int layout(Font font, int width) {
            return 22;
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            double v = value.getAsDouble();
            g.drawString(font, label, ax, ay, LABEL, false);
            String text = format.apply(v);
            g.drawString(font, text, ax + w - font.width(text), ay, VALUE, false);
            int ty = ay + 13, tw = w - 6;
            g.fill(ax + 3, ty + 1, ax + 3 + tw, ty + 5, 0xff0c1114);
            double f = (v - min) / (max - min);
            int fx = ax + 3 + (int) Math.round(Math.max(0, Math.min(1, f)) * tw);
            g.fill(ax + 3, ty + 2, fx, ty + 4, 0xffb59a5f);
            boolean hot = active || hovered(mx, my);
            g.fill(fx - 2, ty - 1, fx + 2, ty + 7, hot ? 0xffffffff : GOLD);
        }

        private void setFromMouse(double mx) {
            double f = (mx - (ax + 3)) / Math.max(1, w - 6);
            double v = min + Math.max(0, Math.min(1, f)) * (max - min);
            v = Math.round(v / step) * step;
            onChange.accept(Math.max(min, Math.min(max, v)));
        }

        @Override
        boolean click(double mx, double my) {
            active = true;
            setFromMouse(mx);
            return true;
        }

        @Override
        boolean wantsDrag() {
            return true;
        }

        @Override
        void drag(double mx, double my) {
            setFromMouse(mx);
        }

        @Override
        void release() {
            active = false;
            onEnd.run();
        }
    }

    // ---- colour picker ----

    /** Saturation / value square, hue bar and a hex field for one colour (the value is read through a supplier). */
    static final class ColorPicker extends Item {
        private final Font font;
        private final EditBox box;
        private final Runnable onEnd;
        private IntSupplier source = () -> 0;
        private IntConsumer sink = c -> {};
        private float hue, sat, val = 1f;
        private int mode;            // 1 = dragging the square, 2 = dragging the hue bar
        private boolean syncing;
        private int sq = 80;

        ColorPicker(String key, Font font, Runnable onEnd) {
            this.key = key;
            this.font = font;
            this.onEnd = onEnd;
            box = new EditBox(font, 0, 0, 70, 14, Component.translatable("descentmtb.workshop.hex"));
            box.setMaxLength(7);
            box.setFilter(s -> s.chars().allMatch(ch -> ColorMath.isHexChar((char) ch)));
            box.setResponder(text -> {
                if (syncing) {
                    return;
                }
                int c = ColorMath.parseHex(text);
                boolean full = text.startsWith("#") ? text.length() == 7 : text.length() == 6;
                box.setTextColor(c >= 0 || text.length() < 7 ? 0xffe0e0e0 : 0xffff6b6b);
                if (c >= 0 && full) {
                    sink.accept(c);
                }
            });
        }

        void bind(IntSupplier source, IntConsumer sink) {
            this.source = source;
            this.sink = sink;
        }

        @Override
        int layout(Font font, int width) {
            sq = Math.max(48, Math.min(width - 18, 84));
            return sq + 4 + 14;
        }

        private void syncFromColor(int rgb) {
            if (ColorMath.hsvToRgb(hue, sat, val) != rgb) {
                float[] hsv = ColorMath.rgbToHsv(rgb);
                // a grey or black colour has no hue of its own: keep the one the user was on
                if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
                    hue = hsv[0];
                }
                sat = hsv[1];
                val = hsv[2];
            }
        }

        private void push() {
            sink.accept(ColorMath.hsvToRgb(hue, sat, val));
        }

        @Override
        void render(GuiGraphics g, Font font, int mx, int my) {
            int cur = source.getAsInt() & 0xFFFFFF;
            if (mode == 0) {
                syncFromColor(cur);
            }
            // saturation / value square
            g.fill(ax - 1, ay - 1, ax + sq + 1, ay + sq + 1, 0xff0c1114);
            for (int i = 0; i < sq; i++) {
                float s = sq <= 1 ? 0 : i / (float) (sq - 1);
                g.fillGradient(ax + i, ay, ax + i + 1, ay + sq, 0xff000000 | ColorMath.hsvToRgb(hue, s, 1f), 0xff000000);
            }
            int px = ax + Math.round(sat * (sq - 1)), py = ay + Math.round((1 - val) * (sq - 1));
            g.renderOutline(px - 3, py - 3, 7, 7, 0xff000000);
            g.renderOutline(px - 2, py - 2, 5, 5, 0xffffffff);
            // hue bar
            int bx = ax + sq + 6;
            g.fill(bx - 1, ay - 1, bx + 11, ay + sq + 1, 0xff0c1114);
            for (int k = 0; k < 6; k++) {
                int y0 = ay + k * sq / 6, y1 = ay + (k + 1) * sq / 6;
                g.fillGradient(bx, y0, bx + 10, y1, 0xff000000 | ColorMath.hsvToRgb(k * 60f, 1, 1),
                        0xff000000 | ColorMath.hsvToRgb((k + 1) * 60f, 1, 1));
            }
            int hy = ay + Math.round(hue / 360f * (sq - 1));
            g.fill(bx - 2, hy - 1, bx + 12, hy + 2, 0xff000000);
            g.fill(bx - 1, hy, bx + 11, hy + 1, 0xffffffff);
            // hex field and the swatch of the colour
            int ey = ay + sq + 4;
            box.setX(ax);
            box.setY(ey);
            box.setWidth(Math.min(w - 20, 74));
            if (!box.isFocused()) {
                String want = ColorMath.toHex(cur);
                if (!box.getValue().equals(want)) {
                    syncing = true;
                    box.setValue(want);
                    syncing = false;
                    box.setTextColor(0xffe0e0e0);
                }
            }
            box.render(g, mx, my, 0f);
            int sx = ax + box.getWidth() + 4;
            g.fill(sx - 1, ey - 1, sx + 15, ey + 15, 0xff0c1114);
            g.fill(sx, ey, sx + 14, ey + 14, 0xff000000 | cur);
        }

        @Override
        boolean click(double mx, double my) {
            int bx = ax + sq + 6, ey = ay + sq + 4;
            if (mx >= ax && mx < ax + sq && my >= ay && my < ay + sq) {
                mode = 1;
                drag(mx, my);
                return true;
            }
            if (mx >= bx - 2 && mx < bx + 12 && my >= ay && my < ay + sq) {
                mode = 2;
                drag(mx, my);
                return true;
            }
            if (mx >= box.getX() && mx < box.getX() + box.getWidth() && my >= ey && my < ey + 14) {
                box.setFocused(true);
                box.mouseClicked(mx, my, 0);
                return true;
            }
            return false;
        }

        @Override
        boolean wantsDrag() {
            return mode != 0;
        }

        @Override
        void drag(double mx, double my) {
            if (mode == 1) {
                sat = (float) Math.max(0, Math.min(1, (mx - ax) / Math.max(1, sq - 1)));
                val = 1f - (float) Math.max(0, Math.min(1, (my - ay) / Math.max(1, sq - 1)));
                push();
            } else if (mode == 2) {
                hue = (float) Math.max(0, Math.min(359.99, (my - ay) / Math.max(1, sq - 1) * 360.0));
                push();
            }
        }

        @Override
        void release() {
            mode = 0;
            onEnd.run();
        }

        @Override
        boolean key(int key, int scan, int mods) {
            if (key == 257 || key == 335) {           // Enter: accept shorthand like #f80 too
                int c = ColorMath.parseHex(box.getValue());
                if (c >= 0) {
                    sink.accept(c);
                }
                unfocus();
                return true;
            }
            if (key == 256) {                          // Esc: leave the field, keep the screen open
                unfocus();
                return true;
            }
            return box.keyPressed(key, scan, mods);
        }

        @Override
        boolean typed(char c, int mods) {
            return box.charTyped(c, mods);
        }

        @Override
        void unfocus() {
            if (box.isFocused()) {
                box.setFocused(false);
                onEnd.run();
            }
        }

        @Override
        boolean focused() {
            return box.isFocused();
        }
    }

    /** Helper to build lists without a generic dance at the call site. */
    static List<Item> list(Item... items) {
        return new ArrayList<>(List.of(items));
    }
}
