"""
Descent MTB v1.1 textures - mechanical, Create-inspired pixel art (16x16).

Writes items, blocks and radial-menu icons into src/main/resources/assets/descentmtb/textures
and a magnified contact sheet to tools/preview/textures_v2.png.

    python tools/gen_textures_v2.py

Style rules: dark warm outline, light from the top-left, brass + andesite + oak + iron
palette, one clear silhouette per item so tools are told apart at a glance.
"""
import math
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'textures')
PREVIEW = os.path.join(os.path.dirname(__file__), 'preview', 'textures_v2.png')

# ------------------------------------------------------------------ palette
O = (40, 31, 29, 255)          # outline
P = {
    'B3': (255, 232, 142), 'B2': (236, 190, 80), 'B1': (186, 131, 50), 'B0': (122, 79, 33),      # brass
    'A3': (200, 197, 188), 'A2': (161, 158, 150), 'A1': (120, 118, 112), 'A0': (82, 80, 76),    # andesite
    'S3': (238, 242, 246), 'S2': (190, 198, 206), 'S1': (136, 144, 154), 'S0': (88, 94, 102),   # iron / steel
    'W3': (219, 176, 116), 'W2': (180, 136, 82), 'W1': (136, 97, 55), 'W0': (90, 62, 34),       # oak
    'L1': (110, 70, 42), 'L0': (74, 46, 28),                                                    # leather
    'D3': (176, 126, 82), 'D2': (143, 99, 62), 'D1': (110, 74, 45), 'D0': (76, 50, 30),         # dirt
    'G2': (122, 180, 76), 'G1': (86, 140, 54), 'G0': (58, 100, 38),                              # grass
    'R2': (226, 78, 60), 'R1': (176, 48, 42), 'R0': (112, 30, 30),                               # red
    'C2': (242, 240, 232), 'C1': (204, 202, 194),                                                # cloth white
    'U2': (104, 166, 236), 'U1': (66, 118, 196), 'U0': (42, 74, 134),                            # airbag blue
    'T2': (64, 178, 176), 'T1': (36, 124, 126), 'T0': (22, 80, 84),                              # teal frame
    'E2': (140, 240, 176), 'E1': (62, 176, 112), 'E0': (30, 110, 72),                            # lens green
    'K1': (64, 64, 70), 'K0': (30, 30, 34),                                                      # tyre black
    'N2': (230, 196, 140), 'N1': (184, 150, 98),                                                 # gum wall / tan
    'Y2': (250, 214, 64), 'Y1': (200, 160, 30),                                                  # tape-measure yellow
    'Or': (244, 146, 40),
    'BG': (46, 50, 56), 'BG1': (58, 63, 70), 'BGd': (32, 35, 40),                               # icon plate
    'BP': (54, 92, 150), 'BP1': (88, 130, 190),                                                  # blueprint
}


def col(c):
    if c is None:
        return None
    if isinstance(c, tuple):
        return c if len(c) == 4 else c + (255,)
    return P[c] + (255,) if len(P[c]) == 3 else P[c]


class Img:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[y][x] = col(c)

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else None

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def poly(self, pts, c):
        """Scanline fill of a polygon given in pixel-centre coordinates."""
        ys = [p[1] for p in pts]
        for y in range(int(math.floor(min(ys))), int(math.ceil(max(ys))) + 1):
            yc = y + 0.5
            xs = []
            for i in range(len(pts)):
                (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % len(pts)]
                if (y0 <= yc < y1) or (y1 <= yc < y0):
                    xs.append(x0 + (yc - y0) * (x1 - x0) / (y1 - y0))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for x in range(int(math.ceil(xs[i] - 0.5)), int(math.floor(xs[i + 1] - 0.5)) + 1):
                    self.set(x, y, c)

    def disc(self, cx, cy, r, c):
        for y in range(self.h):
            for x in range(self.w):
                if (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= r * r:
                    self.set(x, y, c)

    def ring(self, cx, cy, r0, r1, c):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + .5 - cx, y + .5 - cy)
                if r0 <= d <= r1:
                    self.set(x, y, c)

    def ascii(self, rows, x0=0, y0=0, keys=None):
        keys = keys or {}
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in '. ':
                    continue
                self.set(x0 + i, y0 + j, keys.get(ch, ch))

    def outline(self, c=O):
        """MC item style: dark border around the silhouette (only on the outside, holes stay open)."""
        outside = set()
        stack = [(x, y) for x in range(self.w) for y in (0, self.h - 1)] + [(x, y) for y in range(self.h) for x in (0, self.w - 1)]
        while stack:
            x, y = stack.pop()
            if (x, y) in outside or not (0 <= x < self.w and 0 <= y < self.h) or self.px[y][x] is not None:
                continue
            outside.add((x, y))
            stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
        add = []
        for y in range(self.h):
            for x in range(self.w):
                if (x, y) in outside and any(self.get(x + dx, y + dy) is not None and self.get(x + dx, y + dy) != col(c)
                                                 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    add.append((x, y))
        for x, y in add:
            self.px[y][x] = col(c)

    def save(self, rel):
        path = os.path.join(ROOT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is not None:
                    im.putpixel((x, y), self.px[y][x])
        im.save(path)
        return im


def noise(x, y, seed=0):
    v = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v ^ (v >> 16)) / 0xFFFFFFFF


def handle(img, x0, y0, n, light='W2', dark='W1'):
    """Two-pixel diagonal handle going up-right from (x0,y0), n steps."""
    for t in range(n):
        x, y = x0 + t, y0 - t
        img.set(x, y, light)
        img.set(x + 1, y, dark)
        img.set(x, y + 1, dark)


# ================================================================== items

def trail_wand():
    """Trail Builder: oak staff + brass survey head with a green sighting lens and gear teeth."""
    i = Img()
    handle(i, 2, 13, 7)
    i.rect(8, 6, 9, 7, 'A2')            # andesite collar
    i.set(8, 6, 'A3')
    i.set(9, 7, 'A1')
    i.rect(9, 2, 13, 6, 'B2')           # brass housing
    i.rect(9, 2, 13, 2, 'B3')
    i.rect(9, 2, 9, 6, 'B3')
    i.rect(13, 3, 13, 6, 'B1')
    i.rect(10, 6, 13, 6, 'B1')
    for x in (10, 12):                  # gear teeth
        i.set(x, 1, 'B2')
    i.set(14, 3, 'B1')
    i.set(14, 5, 'B1')
    i.rect(10, 3, 12, 5, 'E1')          # lens
    i.set(10, 3, 'E2')
    i.set(11, 3, 'E2')
    i.set(10, 4, 'E2')
    i.set(12, 5, 'E0')
    i.set(11, 4, 'S3')                  # glint
    i.outline()
    return i


def clearing_tool():
    """Trail Machete: leather-wrapped oak grip, brass guard, long steel blade with a hooked tip."""
    i = Img()
    for t in range(4):                  # grip
        x, y = 2 + t, 13 - t
        i.set(x, y, 'L1' if t % 2 == 0 else 'W2')
        i.set(x + 1, y, 'L0' if t % 2 == 0 else 'W1')
        i.set(x, y + 1, 'L0')
    i.rect(5, 9, 6, 9, 'B2')            # guard
    i.set(4, 8, 'B3')
    i.set(7, 10, 'B1')
    for k in range(8):                  # blade widens toward the tip; spine up-left, edge down-right
        x, y = 7 + k, 8 - k
        w = 2 if k < 3 else 3
        i.set(x - 1, y - 1, 'S1')       # spine
        i.set(x, y, 'S2')
        i.set(x, y - 1, 'S2')
        i.set(x + 1, y, 'S3')           # sharpened edge
        if w == 3:
            i.set(x + 1, y + 1, 'S3')
            i.set(x + 2, y + 1, 'S2')
    i.set(15, 1, None)
    i.set(14, 0, 'S2')                  # rounded, slightly hooked tip
    i.set(15, 2, 'S1')
    i.outline()
    return i


def dirt_body(i, top, x0, x1, base=13, seed=1, grassless=True):
    """Fill a dirt profile: top(x) gives the surface row for column x."""
    for x in range(x0, x1 + 1):
        t = top(x)
        for y in range(t, base + 1):
            n = noise(x, y, seed)
            c = 'D3' if y == t else ('D2' if n > .3 else 'D1')
            if y > t + 1 and n > .9:
                c = 'A2'
            if y == base:
                c = 'D0'
            i.set(x, y, c)


def trail_dirt():
    """Shaping Dirt: a sculpted mound with a brass vertex handle on its crest."""
    i = Img()
    prof = lambda x: int(round(9 - 4.2 * math.exp(-((x - 8.5) / 3.4) ** 2)))
    dirt_body(i, prof, 1, 14, 13, seed=3)
    i.set(8, 3, 'B3')                   # brass vertex marker (the thing you click)
    i.set(9, 3, 'B2')
    i.set(8, 2, 'B2')
    i.set(9, 4, 'B1')
    i.set(8, 1, 'S3')                   # up arrow hint
    i.outline()
    return i


def trail_deck():
    """Shaping Deck: sloped oak deck on an iron-banded post."""
    i = Img()
    for t in range(14):                  # 3 px thick sloped deck, plank seams every 3 px
        x = 1 + t
        y = int(round(10 - t * 6 / 13))
        i.set(x, y, 'W3')
        i.set(x, y + 1, 'W2')
        i.set(x, y + 2, 'W1')
        if x % 3 == 0:
            i.set(x, y + 1, 'W0')
            i.set(x, y + 2, 'W0')
    for px in (4, 11):                    # posts with iron bands
        top = int(round(10 - (px - 1) * 6 / 13)) + 3
        i.rect(px, top, px + 1, 14, 'W1')
        i.set(px, top, 'W2')
        i.rect(px, 12, px + 1, 12, 'S1')
        i.set(px, 12, 'S2')
    i.outline()
    return i


def ramp():
    """Ramp: a concave kicker with a crisp lip and a brass edge strip."""
    i = Img()
    prof = lambda x: int(round(11 - 7.5 * (1 - math.sqrt(max(0, 1 - ((x - 1) / 12.5) ** 2))) ** .9)) if x <= 13 else 3
    for x in range(1, 14):
        t = max(3, min(13, prof(x)))
        for y in range(t, 14):
            n = noise(x, y, 7)
            i.set(x, y, 'D3' if y == t else ('D2' if n > .35 else 'D1'))
        if t < 13:
            i.set(x, t, 'B2' if x > 9 else 'D3')
    i.rect(13, 3, 13, 13, 'D1')          # lip face
    i.set(13, 3, 'B3')
    i.outline()
    return i


def bike_pump():
    """Floor pump: brass barrel, T-handle, gauge, hose with a red chuck, andesite foot."""
    i = Img()
    i.rect(4, 2, 11, 2, 'W2')            # T-handle
    i.rect(4, 2, 4, 2, 'W3')
    i.rect(7, 3, 8, 3, 'S2')             # shaft
    i.rect(6, 4, 9, 12, 'B2')            # barrel
    i.rect(6, 4, 6, 12, 'B3')
    i.rect(9, 4, 9, 12, 'B1')
    i.disc(8.0, 9.5, 1.6, 'C2')          # gauge
    i.set(8, 9, 'R1')
    i.set(7, 9, 'K0')
    i.rect(3, 13, 12, 13, 'A2')          # foot
    i.rect(3, 13, 12, 13, 'A2')
    i.set(3, 13, 'A3')
    i.set(12, 13, 'A1')
    hose = [(10, 11), (11, 11), (12, 10), (13, 9), (13, 8), (13, 7), (13, 6)]
    for x, y in hose:
        i.set(x, y, 'K1')
    i.set(13, 5, 'R2')
    i.set(13, 4, 'R1')
    i.outline()
    return i


def bike(frame, frame_dark, wall=None, fork='K1', seat_y=4, shock=True):
    i = Img()
    for cx in (4.0, 12.0):                # wheels
        i.ring(cx, 11.0, 2.3, 3.5, 'K0')
        if wall:
            i.ring(cx, 11.0, 2.3, 2.8, wall)
        i.set(int(cx), 11, 'S2')
    i.line(4, 11, 7, 11, frame_dark)      # chainstay
    i.line(7, 11, 6, 6, frame)            # seat tube
    i.line(6, 6, 10, 6, frame)            # top tube
    i.line(7, 11, 10, 7, frame)           # down tube
    i.line(4, 11, 6, 7, frame_dark)       # seat stay
    i.line(10, 6, 12, 11, fork)           # fork
    i.set(10, 5, 'K1')                    # stem
    i.rect(10, 4, 11, 4, 'K0')            # bars
    i.rect(5, seat_y, 7, seat_y, 'K0')    # saddle
    i.set(6, seat_y + 1, 'S1')
    if shock:
        i.set(7, 8, 'Or')
        i.set(8, 8, 'Or')
    i.set(7, 11, 'S1')                    # BB
    i.outline()
    return i


# ================================================================== blocks (tileable, no outline)

def block_rock():
    i = Img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 11)
            i.set(x, y, 'A3' if n > .82 else 'A2' if n > .38 else 'A1')
    for x0, y0, x1, y1 in ((2, 3, 7, 6), (7, 6, 6, 11), (10, 1, 13, 5), (12, 9, 15, 12), (1, 12, 4, 14)):
        i.line(x0, y0, x1, y1, 'A0')
    for x, y in ((3, 9), (9, 13), (14, 4), (5, 1)):
        i.set(x, y, 'G1')                 # moss specks
    return i


def block_roots():
    i = Img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 5)
            i.set(x, y, 'D2' if n > .4 else 'D1')
    for y0, amp, ph, w in ((3, 1.5, 0.0, 2), (9, 2.0, 1.7, 2), (13, 1.2, 3.1, 1)):
        for x in range(16):
            y = int(round(y0 + amp * math.sin(x / 2.6 + ph)))
            i.set(x, y, 'W1')
            if w > 1:
                i.set(x, y + 1, 'W0')
            i.set(x, y - 1, 'W2' if x % 3 else 'W1')
    return i


def block_stake():
    i = Img()
    i.rect(7, 4, 8, 14, 'W2')
    i.rect(8, 4, 8, 14, 'W1')
    i.set(7, 15, 'W1')                    # pointed foot
    i.poly([(9, 3), (14, 4.5), (9, 6)], 'Or')
    i.set(9, 3, 'R2')
    i.set(9, 5, 'R1')
    i.rect(7, 2, 8, 3, 'W3')
    i.outline()
    return i


def block_support():
    i = Img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 9)
            i.set(x, y, 'W2' if n > .25 else 'W1')
            if x in (0, 15):
                i.set(x, y, 'W0')
    for y in (3, 12):                     # iron bands with bolts
        i.rect(0, y, 15, y + 1, 'S1')
        i.rect(0, y, 15, y, 'S2')
        for x in (3, 12):
            i.set(x, y, 'S3')
            i.set(x, y + 1, 'S0')
    return i


def block_airbag():
    i = Img()
    for y in range(16):
        for x in range(16):
            cx, cy = x % 8 - 3.5, y % 8 - 3.5
            puff = 1 - (cx * cx + cy * cy) / 24
            i.set(x, y, 'U2' if puff > .55 else 'U1' if puff > .1 else 'U0')
    for k in range(16):
        i.set(k, 0, 'U0')
        i.set(0, k, 'U0')
        i.set(k, 8, 'U0')
        i.set(8, k, 'U0')
    i.rect(12, 12, 13, 13, 'Y2')           # valve
    i.set(13, 13, 'Y1')
    return i


def block_barrier():
    i = Img()
    for x in range(16):
        for y in range(6, 10):
            i.set(x, y, 'R2' if ((x + y) // 2) % 2 == 0 else 'C2')
        i.set(x, 6, 'C1' if (x // 2) % 2 else 'R1')
        i.set(x, 9, 'R0' if (x // 2) % 2 == 0 else 'C1')
    return i


def block_sign():
    i = Img()
    for y in range(16):
        for x in range(16):
            n = noise(x, y, 13)
            i.set(x, y, 'W3' if n > .78 else 'W2' if n > .25 else 'W1')
    for y in (5, 10):
        i.rect(0, y, 15, y, 'W0')
    i.rect(0, 0, 15, 0, 'W0')
    i.rect(0, 15, 15, 15, 'W0')
    i.rect(0, 0, 0, 15, 'W0')
    i.rect(15, 0, 15, 15, 'W0')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        i.set(x, y, 'S2')
    return i


# ================================================================== radial icons

def plate():
    i = Img()
    i.rect(0, 0, 15, 15, 'BG')
    i.rect(1, 1, 14, 1, 'BG1')
    i.rect(1, 1, 1, 14, 'BG1')
    i.rect(1, 14, 14, 14, 'BGd')
    i.rect(14, 1, 14, 14, 'BGd')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        i.set(x, y, None)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        i.set(x, y, 'B2')                  # brass bolts
    return i


def icon_profile(func, x0=2, x1=13, base=12, seed=1, top='D3', body='D2', dark='D1'):
    i = plate()
    for x in range(x0, x1 + 1):
        t = max(2, min(base, func(x)))
        for y in range(t, base + 1):
            i.set(x, y, top if y == t else (body if noise(x, y, seed) > .3 else dark))
    i.rect(x0, base + 1, x1, base + 1, 'G1')
    return i


def icon_arrow(i, x, y, dx, dy, c='B3', length=3):
    """Arrow whose tip is at (x, y), pointing along (dx, dy) (axis or diagonal unit steps)."""
    for k in range(length + 1):
        i.set(x - dx * k, y - dy * k, c)
    if dx != 0 and dy != 0:                # diagonal: head runs back along both axes
        for k in (1, 2):
            i.set(x - dx * k, y, c)
            i.set(x, y - dy * k, c)
        return
    px, py = -dy, dx                      # perpendicular
    for k in (1, 2):
        i.set(x - dx * k + px * k, y - dy * k + py * k, c)
        i.set(x - dx * k - px * k, y - dy * k - py * k, c)


def icon_band_arc(cx, cy, r0, r1, a0, a1, raised=True, bank=1.0):
    i = plate()
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            a = math.degrees(math.atan2(y + .5 - cy, x + .5 - cx)) % 360
            if r0 <= d <= r1 and a0 <= a <= a1:
                f = (d - r0) / (r1 - r0)
                if raised:
                    c = 'D3' if f > 1 - .35 * bank else 'D2' if f > .3 else 'D1'
                else:
                    c = 'D2'
                i.set(x, y, c)
    return i


def icons():
    out = {}
    out['flow'] = (lambda: _flow())()
    out['pump_line'] = icon_profile(lambda x: int(round(11.5 - 5.5 * math.sin(math.pi * (x - 1.5) / 6.0) ** 2)))
    out['pump_loop'] = _pump_loop()
    out['dirt_jump'] = icon_profile(lambda x: 13 if 7 <= x <= 8 else (int(round(12 - 6 * ((x - 2) / 4.5) ** 2)) if x < 7 else int(round(6 + (x - 9) * 1.4))))
    out['wood_kicker'] = _wood(lambda x: int(round(12 - 7 * ((x - 2) / 11) ** 1.6)), drop=False)
    out['wood_drop'] = _wood(lambda x: 5 if x <= 9 else 13, drop=True)
    out['drop_edge'] = _drop_edge()
    out['berm'] = icon_band_arc(3, 13, 4.5, 10.5, 270, 360, bank=1.0)
    out['enduro'] = icon_band_arc(3, 13, 6.0, 10.5, 270, 360, bank=.45)
    out['sharkfin'] = _sharkfin()
    out['boardwalk'] = _boardwalk()
    out['support'] = _support()
    out['clone'] = _clone()
    out['template'] = _template()
    out['roots'] = _roots()
    out['rocks'] = _rocks(False)
    out['rock_garden'] = _rocks(True)
    out['barrier'] = _barrier()
    out['airbag'] = _airbag()
    out['sign'] = _sign()
    out['ramp_tune'] = _ramp_tune()
    out['measure'] = _measure()
    out['undo'] = _undo()
    out['category_lines'] = out['pump_line']
    out['category_jumps'] = _jumps_cat()
    out['category_turns'] = out['berm']
    out['category_wood'] = out['boardwalk']
    out['category_gear'] = _gear_cat()
    out['settings'] = _settings()
    return out


def _flow():
    i = plate()
    for y in range(2, 14):
        cx = 8 + 3.2 * math.sin((y - 2) / 11 * math.pi * 1.6)
        for x in range(2, 14):
            d = abs(x + .5 - cx)
            if d < 2.2:
                i.set(x, y, 'D3' if d < .9 else 'D2')
            elif d < 3:
                i.set(x, y, 'D1')
    return i


def _pump_loop():
    i = plate()
    for y in range(2, 14):
        for x in range(2, 14):
            nx, ny = (x + .5 - 8) / 5.5, (y + .5 - 8) / 4.0
            r = math.hypot(nx, ny)
            if .62 <= r <= 1.0:
                a = math.atan2(ny, nx)
                bump = math.sin(a * 6) > .3 and abs(math.cos(a)) < .7
                i.set(x, y, 'D3' if bump else ('D2' if r < .85 else 'D1'))
    return i


def _wood(func, drop):
    i = plate()
    for x in range(2, 14):
        t = max(2, min(13, func(x)))
        if drop and x > 9:
            continue
        i.set(x, t, 'W3')
        i.set(x, t + 1, 'W1')
        if x % 3 == 0:
            i.set(x, t, 'W2')
    posts = (4, 9, 12) if not drop else (3, 8)
    for px in posts:
        t = func(px) + 2
        for y in range(t, 14):
            i.set(px, y, 'W1')
    i.rect(2, 13, 13, 13, 'G1')
    if drop:
        icon_arrow(i, 12, 11, 0, 1)
    return i


def _drop_edge():
    i = icon_profile(lambda x: 5 if x <= 7 else (6 if x == 8 else 12))
    i.set(7, 5, 'B2')
    i.set(8, 6, 'B2')
    icon_arrow(i, 11, 9, 1, 1)
    return i


def _sharkfin():
    i = plate()
    i.poly([(2, 13), (9, 3), (11, 6), (13, 13)], 'D2')
    i.line(2, 12, 9, 3, 'D3')
    i.line(9, 3, 12, 10, 'D1')
    i.rect(2, 13, 13, 13, 'G1')
    return i


def _boardwalk():
    i = plate()
    for y in range(3, 13):
        for x in range(4, 12):
            i.set(x, y, 'W2' if (y % 3) else 'W1')
            if x in (4, 11):
                i.set(x, y, 'W0')
    i.rect(3, 3, 3, 12, 'S1')
    i.rect(12, 3, 12, 12, 'S1')
    return i


def _support():
    i = plate()
    i.rect(2, 3, 13, 4, 'W2')
    i.rect(2, 3, 13, 3, 'W3')
    i.rect(4, 5, 5, 13, 'W1')
    i.rect(10, 5, 11, 13, 'W1')
    i.line(5, 6, 10, 12, 'W2')
    i.line(10, 6, 5, 12, 'W2')
    i.set(4, 5, 'S2')
    i.set(11, 5, 'S2')
    return i


def _clone():
    i = plate()
    i.rect(3, 3, 9, 9, 'B1')
    i.rect(4, 4, 8, 8, 'BG')
    i.rect(6, 6, 12, 12, 'B2')
    i.rect(7, 7, 11, 11, 'D2')
    i.rect(6, 6, 12, 6, 'B3')
    return i


def _template():
    i = plate()
    i.rect(3, 3, 12, 12, 'BP')
    for y in (5, 7, 9):
        i.rect(4, y, 11, y, 'BP1')
    i.line(4, 11, 10, 4, 'C2')
    i.rect(12, 3, 12, 12, 'B1')
    i.rect(3, 3, 3, 12, 'B2')
    return i


def _roots():
    i = plate()
    i.rect(2, 10, 13, 13, 'D2')
    for x in range(2, 14):
        if noise(x, 3, 4) > .6:
            i.set(x, 11, 'D1')
    for x0, x1, h in ((2, 9, 4.5), (6, 13, 3.0)):     # arches poking out of the ground
        for x in range(x0, x1 + 1):
            t = (x - x0) / (x1 - x0)
            y = int(round(10 - h * math.sin(math.pi * t)))
            i.set(x, y, 'W2')
            i.set(x, y + 1, 'W1')
    for x, y in ((4, 7), (11, 8), (8, 9)):            # tendrils
        i.set(x, y, 'W0')
    return i


def _rocks(many):
    i = plate()
    spots = ((5, 10, 3.0), (10, 10.5, 2.6)) if not many else ((4, 11, 1.8), (8, 10, 2.2), (12, 11, 1.7), (6, 7.5, 1.4), (11, 7, 1.5))
    for cx, cy, r in spots:
        i.disc(cx, cy, r, 'A1')
        i.disc(cx - .5, cy - .6, r * .7, 'A2')
        i.set(int(cx - r * .5), int(cy - r * .6), 'A3')
    i.rect(2, 13, 13, 13, 'G1')
    return i


def _barrier():
    i = plate()
    for px in (3, 12):
        i.rect(px, 4, px, 13, 'W1')
        i.set(px, 4, 'Or')
    for x in range(4, 12):
        for y in (6, 7):
            i.set(x, y, 'R2' if ((x + y) // 2) % 2 == 0 else 'C2')
    return i


def _airbag():
    i = plate()
    i.poly([(2, 12), (3, 6), (7, 4), (12, 5), (13, 12)], 'U1')
    i.poly([(4, 7), (7, 5), (11, 6), (11, 8), (5, 8)], 'U2')
    i.line(2, 12, 13, 12, 'U0')
    i.line(7, 5, 7, 12, 'U0')
    i.set(12, 11, 'Y2')
    return i


def _sign():
    i = plate()
    i.rect(7, 8, 8, 13, 'W1')
    i.rect(3, 3, 12, 8, 'W2')
    i.rect(3, 3, 12, 3, 'W3')
    i.rect(5, 5, 9, 6, 'C2')
    i.set(10, 5, 'C2')
    i.set(10, 6, 'C2')
    i.set(11, 5, 'C2')
    return i


def _ramp_tune():
    i = icon_profile(lambda x: int(round(12 - 7 * ((x - 2) / 9) ** 1.8)) if x <= 11 else 13, x1=11)
    icon_arrow(i, 13, 3, 0, -1, 'B3')
    i.rect(13, 4, 13, 8, 'B2')
    icon_arrow(i, 13, 9, 0, 1, 'B1')
    return i


def _measure():
    i = plate()
    i.disc(6, 9, 4.2, 'Y2')
    i.disc(6, 9, 2.2, 'Y1')
    i.disc(6, 9, 1.0, 'K0')
    i.rect(9, 11, 13, 12, 'Y2')
    for x in (10, 12):
        i.set(x, 11, 'K0')
    return i


def _undo():
    i = plate()
    for k in range(0, 200, 6):
        a = math.radians(30 + k)
        x, y = 8 + 4.2 * math.cos(a), 8.5 + 4.2 * math.sin(a)
        i.set(int(x), int(y), 'B2')
        i.set(int(x + .5), int(y), 'B1')
    icon_arrow(i, 4, 5, 0, -1, 'B3', length=2)
    return i


def _jumps_cat():
    i = icon_profile(lambda x: int(round(12 - 6 * ((x - 2) / 5) ** 2)) if x <= 7 else 13, x1=7)
    for k in range(8):                    # flight arc
        x = 7 + k
        y = int(round(5 - 3.2 * math.sin(k / 7 * math.pi * .9)))
        i.set(x, y, 'C2' if k % 2 == 0 else None)
    return i


def _gear_cat():
    i = _rocks(False)
    i.rect(12, 3, 12, 9, 'W1')
    i.poly([(13, 3), (14, 4), (13, 5)], 'Or')
    return i


def _settings():
    i = plate()
    i.disc(8, 8, 4.6, 'B2')
    for a in range(0, 360, 45):
        x, y = 8 + 5.2 * math.cos(math.radians(a)), 8 + 5.2 * math.sin(math.radians(a))
        i.set(int(x), int(y), 'B2')
    i.disc(8, 8, 3.0, 'B1')
    i.disc(8, 8, 1.6, 'BG')
    i.set(6, 6, 'B3')
    return i


# ================================================================== write everything

def main():
    sheet_items = []
    items = {
        'item/trail_wand.png': trail_wand(),
        'item/clearing_tool.png': clearing_tool(),
        'item/trail_dirt.png': trail_dirt(),
        'item/trail_deck.png': trail_deck(),
        'item/ramp.png': ramp(),
        'item/bike_pump.png': bike_pump(),
        'item/mountain_bike.png': bike('T2', 'T1'),
        'item/hardtail_bike.png': bike('R2', 'R1', wall='N1', fork='S1', seat_y=5, shock=False),
    }
    blocks = {
        'block/trail_rock.png': block_rock(),
        'block/trail_roots.png': block_roots(),
        'block/trail_stake.png': block_stake(),
        'block/wood_support.png': block_support(),
        'block/airbag.png': block_airbag(),
        'block/cloth_barrier.png': block_barrier(),
        'block/trail_sign.png': block_sign(),
    }
    gui = {f'gui/trail/{k}.png': v for k, v in icons().items()}
    for group in (items, blocks, gui):
        for rel, img in group.items():
            sheet_items.append((rel, img.save(rel)))
    for old in ('raise', 'lower', 'smooth', 'flatten'):
        p = os.path.join(ROOT, 'gui', 'trail', old + '.png')
        if os.path.exists(p):
            os.remove(p)

    # contact sheet (x6)
    cols, cell = 10, 112
    rows = (len(sheet_items) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * cell), (70, 72, 80, 255))
    for k, (rel, im) in enumerate(sheet_items):
        big = im.resize((96, 96), Image.NEAREST)
        x, y = (k % cols) * cell + 8, (k // cols) * cell + 8
        sheet.paste(big, (x, y), big)
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)
    print('wrote', len(sheet_items), 'textures; preview', os.path.abspath(PREVIEW))
    for rel, _ in sheet_items:
        print(' ', rel)


if __name__ == '__main__':
    main()
