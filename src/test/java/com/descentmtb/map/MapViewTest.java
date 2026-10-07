package com.descentmtb.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapViewTest {
    private static MapView view(double w, double h) {
        MapView v = new MapView(256);
        v.setRect(20, 30, w, h);
        return v;
    }

    @Test
    void zoomOneShowsWholeTexture() {
        MapView v = view(300, 300);
        assertEquals(0, v.originX(), 1e-9);
        assertEquals(0, v.texX(20), 1e-9);
        assertEquals(256, v.texX(320), 1e-9);
        assertEquals(300.0 / 256, v.scale(), 1e-9);
    }

    @Test
    void screenAndTextureRoundTrip() {
        MapView v = view(300, 300);
        v.zoomAt(150, 140, 3);
        v.pan(-17, 9);
        for (double sx : new double[]{20, 77.5, 319}) {
            assertEquals(sx, v.screenX(v.texX(sx)), 1e-9);
            assertEquals(sx + 5, v.screenY(v.texY(sx + 5)), 1e-9);
        }
        for (double t : new double[]{0, 31.25, 255}) assertEquals(t, v.texX(v.screenX(t)), 1e-9);
    }

    @Test
    void zoomKeepsPointUnderCursorFixed() {
        MapView v = view(300, 300);
        double sx = 140, sy = 200, before = v.texX(sx), beforeY = v.texY(sy);
        v.zoomAt(sx, sy, 2);
        assertEquals(2, v.zoom(), 1e-9);
        assertEquals(before, v.texX(sx), 1e-9);
        assertEquals(beforeY, v.texY(sy), 1e-9);
        v.zoomAt(sx, sy, 1.5);
        assertEquals(before, v.texX(sx), 1e-9);
        assertEquals(beforeY, v.texY(sy), 1e-9);
    }

    @Test
    void zoomIsClamped() {
        MapView v = view(300, 300);
        v.zoomAt(100, 100, 100);
        assertEquals(MapView.MAX_ZOOM, v.zoom(), 1e-9);
        v.zoomAt(100, 100, 1e-6);
        assertEquals(MapView.MIN_ZOOM, v.zoom(), 1e-9);
        assertEquals(0, v.originX(), 1e-9);
    }

    @Test
    void panNeverLeavesTheMap() {
        MapView v = view(300, 300);
        v.pan(-500, -500);
        assertEquals(0, v.originX(), 1e-9); // whole map in view: nothing to pan
        v.zoomAt(170, 180, 4);
        v.pan(100000, 100000);
        assertEquals(0, v.originX(), 1e-9);
        assertEquals(0, v.originY(), 1e-9);
        v.pan(-100000, -100000);
        assertEquals(256 - 256 / 4.0, v.originX(), 1e-9);
        assertEquals(256 - 256 / 4.0, v.originY(), 1e-9);
        assertEquals(20 + 300, v.screenX(256), 1e-9); // far edge sits on the rectangle's edge
    }

    @Test
    void wideRectangleCentresTheTexture() {
        MapView v = view(400, 200);
        assertEquals(200.0 / 256, v.scale(), 1e-9);
        assertTrue(v.originX() < 0);
        assertEquals(20 + 100, v.screenX(0), 1e-9);
        assertEquals(30, v.screenY(0), 1e-9);
        v.pan(300, 0);
        assertEquals(20 + 100, v.screenX(0), 1e-9); // cannot be dragged off-centre on the long side
    }

    @Test
    void resizeKeepsViewInside() {
        MapView v = view(300, 300);
        v.zoomAt(300, 300, 8);
        v.pan(-1e6, -1e6);
        v.setRect(0, 0, 100, 100);
        assertEquals(256 - 256 / 8.0, v.originX(), 1e-9);
    }

    @Test
    void resetShowsEverything() {
        MapView v = view(300, 300);
        v.zoomAt(250, 250, 5);
        v.reset();
        assertEquals(1, v.zoom(), 1e-9);
        assertEquals(0, v.originX(), 1e-9);
        assertEquals(0, v.originY(), 1e-9);
    }

    @Test
    void fitCentresOnABox() {
        MapView v = view(300, 300);
        v.fit(100, 100, 120, 140, 4);
        assertTrue(v.zoom() > 1);
        assertEquals(110, v.texX(20 + 150), 1e-6);
        assertEquals(120, v.texY(30 + 150), 1e-6);
        assertTrue(v.texX(20) <= 96 && v.texX(320) >= 124 && v.texY(30) <= 96 && v.texY(330) >= 144);
    }

    @Test
    void nearestOnPolyline() {
        double[] xy = {0, 0, 10, 0, 10, 10};
        var n = MapView.nearest(xy, 4, 3);
        assertEquals(3, n.distance(), 1e-9);
        assertEquals(4, n.x(), 1e-9);
        assertEquals(0, n.y(), 1e-9);
        assertEquals(0.4, n.index(), 1e-9);
        var corner = MapView.nearest(xy, 14, -3); // beyond the corner: distance to the vertex
        assertEquals(5, corner.distance(), 1e-9);
        assertEquals(10, corner.x(), 1e-9);
        var onSecond = MapView.nearest(xy, 12, 5);
        assertEquals(2, onSecond.distance(), 1e-9);
        assertEquals(1.5, onSecond.index(), 1e-9);
        var single = MapView.nearest(new double[]{5, 5}, 8, 9);
        assertEquals(5, single.distance(), 1e-9);
        assertNull(MapView.nearest(new double[0], 0, 0));
    }

    @Test
    void nearestIgnoresDegenerateSegments() {
        var n = MapView.nearest(new double[]{3, 3, 3, 3, 6, 3}, 4, 5);
        assertEquals(2, n.distance(), 1e-9);
        assertEquals(4, n.x(), 1e-9);
    }

    @Test
    void pinKeepsInsidersAndClampsOutsiders() {
        double[] in = MapView.pin(0, 0, 100, 100, 30, 40, 5);
        assertEquals(30, in[0], 1e-9);
        assertEquals(40, in[1], 1e-9);
        double[] right = MapView.pin(0, 0, 100, 100, 300, 50, 6);
        assertEquals(94, right[0], 1e-9);
        assertEquals(50, right[1], 1e-9);
        assertEquals(Math.PI / 2, right[2], 1e-9);
        double[] up = MapView.pin(0, 0, 100, 100, 50, -400, 6);
        assertEquals(50, up[0], 1e-9);
        assertEquals(6, up[1], 1e-9);
        assertEquals(0, up[2], 1e-9);
        double[] diagonal = MapView.pin(0, 0, 100, 100, 250, 250, 0);
        assertEquals(100, diagonal[0], 1e-9);
        assertEquals(100, diagonal[1], 1e-9);
    }

    /** Straight track along +x: 0 m at height 50, 10 m at 40, 30 m at 40 (blocks). */
    private static int[] track() {
        return new int[]{0, 500, 0, 100, 400, 0, 300, 400, 0};
    }

    @Test
    void profileLookup() {
        var start = MapView.sampleAt(track(), 0);
        assertEquals(0, start.distance(), 1e-9);
        assertEquals(50, start.height(), 1e-9);
        var mid = MapView.sampleAt(track(), 1.0 / 6); // 5 m: halfway down the first drop
        assertEquals(5, mid.distance(), 1e-9);
        assertEquals(45, mid.height(), 1e-9);
        assertEquals(5, mid.x(), 1e-9);
        var flat = MapView.sampleAt(track(), 0.5);
        assertEquals(15, flat.distance(), 1e-9);
        assertEquals(40, flat.height(), 1e-9);
        var end = MapView.sampleAt(track(), 5);
        assertEquals(30, end.distance(), 1e-9);
        assertEquals(30, end.x(), 1e-9);
        assertEquals(50, MapView.sampleAt(track(), -1).height(), 1e-9);
        assertNull(MapView.sampleAt(new int[0], .5));
    }

    @Test
    void profileLookupSkipsStandingPoints() {
        int[] p = {0, 100, 0, 0, 0, 0, 100, 0, 0}; // a vertical drop of 10 blocks, then 10 m flat
        assertEquals(0, MapView.sampleAt(p, 0).distance(), 1e-9);
        assertEquals(5, MapView.sampleAt(p, .5).distance(), 1e-9);
        assertEquals(0, MapView.sampleAt(p, .5).height(), 1e-9);
    }
}
