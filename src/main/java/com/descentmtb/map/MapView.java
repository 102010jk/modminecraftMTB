package com.descentmtb.map;

/**
 * The pan and zoom of the trail map: which part of a square map texture shows in a screen rectangle. Pure maths, no
 * Minecraft types, unit-tested. The texture fits the rectangle's shorter side at zoom 1; zooming stays within
 * {@link #MIN_ZOOM}..{@link #MAX_ZOOM}, and panning never leaves the texture (a view larger than the texture, on the
 * rectangle's longer side, is centred). Texture coordinates are pixels, x east and y south; screen coordinates are
 * GUI pixels. The view origin ({@link #originX()}, {@link #originY()}) is the texture point at the rectangle's top left.
 */
public final class MapView {
    public static final double MIN_ZOOM = 1, MAX_ZOOM = 8;
    private final double tex;
    private double rx, ry, rw = 1, rh = 1, zoom = MIN_ZOOM, ox, oy;

    /** @param textureSize side of the square map texture, in texture pixels */
    public MapView(double textureSize) {
        this.tex = textureSize;
        clamp();
    }

    /** Where on the screen the map is drawn; keeps the current zoom and re-clamps the pan. */
    public void setRect(double x, double y, double w, double h) {
        rx = x;
        ry = y;
        rw = Math.max(1, w);
        rh = Math.max(1, h);
        clamp();
    }

    public double zoom() { return zoom; }
    public double originX() { return ox; }
    public double originY() { return oy; }
    public double textureSize() { return tex; }

    /** Screen pixels per texture pixel. */
    public double scale() { return Math.min(rw, rh) / tex * zoom; }

    public double screenX(double tx) { return rx + (tx - ox) * scale(); }
    public double screenY(double ty) { return ry + (ty - oy) * scale(); }
    public double texX(double sx) { return ox + (sx - rx) / scale(); }
    public double texY(double sy) { return oy + (sy - ry) / scale(); }

    public boolean contains(double sx, double sy) { return sx >= rx && sy >= ry && sx < rx + rw && sy < ry + rh; }

    /** Zooms by {@code factor} keeping the texture point under the screen point (sx, sy) fixed, where the edges allow. */
    public void zoomAt(double sx, double sy, double factor) {
        double tx = texX(sx), ty = texY(sy);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        double s = scale();
        ox = tx - (sx - rx) / s;
        oy = ty - (sy - ry) / s;
        clamp();
    }

    /** Sets the zoom around the centre of the rectangle. */
    public void zoomCentre(double factor) { zoomAt(rx + rw / 2, ry + rh / 2, factor); }

    /** Drags the map by a screen-pixel delta (the content follows the cursor). */
    public void pan(double dxScreen, double dyScreen) {
        double s = scale();
        ox -= dxScreen / s;
        oy -= dyScreen / s;
        clamp();
    }

    /** Whole map in view. */
    public void reset() {
        zoom = MIN_ZOOM;
        clamp();
    }

    /** Zooms and centres on a texture rectangle (plus {@code pad} texture pixels each side), as far as the limits allow. */
    public void fit(double minX, double minY, double maxX, double maxY, double pad) {
        double w = Math.max(1e-6, maxX - minX + 2 * pad), h = Math.max(1e-6, maxY - minY + 2 * pad);
        double base = Math.min(rw, rh) / tex;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(rw / (base * w), rh / (base * h))));
        double s = scale();
        ox = (minX + maxX) / 2 - rw / s / 2;
        oy = (minY + maxY) / 2 - rh / s / 2;
        clamp();
    }

    private void clamp() {
        double s = Math.min(rw, rh) / tex * zoom, vw = rw / s, vh = rh / s;
        ox = vw >= tex ? (tex - vw) / 2 : Math.max(0, Math.min(tex - vw, ox));
        oy = vh >= tex ? (tex - vh) / 2 : Math.max(0, Math.min(tex - vh, oy));
    }

    // ---- hit-testing ----

    /**
     * The closest point of a polyline to a position.
     *
     * @param distance distance from the position to the polyline
     * @param x        closest point
     * @param y        closest point
     * @param index    where along the polyline: segment number plus the fraction within it
     */
    public record Nearest(double distance, double x, double y, double index) {}

    /** Closest point on a polyline ({@code xy} = x, y pairs) to (px, py); {@code null} for an empty polyline. */
    public static Nearest nearest(double[] xy, double px, double py) {
        int n = xy.length / 2;
        if (n == 0) return null;
        Nearest best = new Nearest(Math.hypot(px - xy[0], py - xy[1]), xy[0], xy[1], 0);
        for (int i = 1; i < n; i++) {
            double ax = xy[i * 2 - 2], ay = xy[i * 2 - 1], dx = xy[i * 2] - ax, dy = xy[i * 2 + 1] - ay;
            double len2 = dx * dx + dy * dy;
            double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
            double cx = ax + dx * t, cy = ay + dy * t, d = Math.hypot(px - cx, py - cy);
            if (d < best.distance) best = new Nearest(d, cx, cy, i - 1 + t);
        }
        return best;
    }

    /**
     * Where a marker at (px, py) shows when the rectangle (x, y, w, h) does not contain it: on the rectangle's edge
     * (inset by {@code inset}) along the line from the centre to the marker. Returns x, y and the angle (radians,
     * 0 = straight up, clockwise positive) pointing from the centre towards the marker; a marker inside comes back
     * unchanged.
     */
    public static double[] pin(double x, double y, double w, double h, double px, double py, double inset) {
        double cx = x + w / 2, cy = y + h / 2, dx = px - cx, dy = py - cy;
        double hw = Math.max(1, w / 2 - inset), hh = Math.max(1, h / 2 - inset);
        double angle = Math.atan2(dx, -dy);
        double t = Math.max(Math.abs(dx) / hw, Math.abs(dy) / hh);
        if (t <= 1) return new double[]{px, py, angle};
        return new double[]{cx + dx / t, cy + dy / t, angle};
    }

    // ---- elevation profile ----

    /**
     * A point along a track.
     *
     * @param distance horizontal distance from the start (blocks)
     * @param height   height of the track there (blocks)
     * @param x        world position (blocks)
     * @param z        world position (blocks)
     */
    public record Sample(double distance, double height, double x, double z) {}

    /** The track at {@code fraction} (0..1, clamped) of its horizontal length, interpolated between points. */
    public static Sample sampleAt(int[] pts, double fraction) {
        int n = TrackGeometry.count(pts);
        if (n == 0) return null;
        double u = TrackGeometry.UNIT;
        if (n == 1) return new Sample(0, pts[1] / u, pts[0] / u, pts[2] / u);
        double[] dist = TrackGeometry.distances(pts);
        double d = Math.max(0, Math.min(1, fraction)) * dist[n - 1];
        int i = 1;
        while (i < n - 1 && dist[i] < d) i++;
        double span = dist[i] - dist[i - 1], t = span <= 1e-9 ? 1 : Math.max(0, Math.min(1, (d - dist[i - 1]) / span));
        int a = (i - 1) * 3, b = i * 3;
        return new Sample(d, (pts[a + 1] + (pts[b + 1] - pts[a + 1]) * t) / u,
                (pts[a] + (pts[b] - pts[a]) * t) / u, (pts[a + 2] + (pts[b + 2] - pts[a + 2]) * t) / u);
    }
}
