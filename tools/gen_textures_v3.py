"""
Descent MTB textures v3 - in the style of the Create mod.

* Items: painted 3/4 view, hue-shifted shading ramps (warm lights, cool darks), the outline is the darkest
  shade of the object's own material (never a flat black line), light from the top-left.
* GUI icons (radial menu / HUD): Create-like light glyphs - a small isometric block in near-white greys that shows
  the shape a mode makes, brass accents for direction, a dark rim and a soft drop shadow, no plate.

    python tools/gen_textures_v3.py [--install]

Without --install everything goes to tools/preview/v3/ (plus a contact sheet); with it the files are written into
src/main/resources/assets/descentmtb/textures.
"""
import math
import os
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'textures')
STAGE = os.path.join(HERE, 'preview', 'v3')

# ------------------------------------------------------------------ hue-shifted ramps (dark -> light)
RAMP = {
    'steel': ['#2a2e36', '#4b525d', '#76808a', '#a6afb6', '#d5dade', '#f4f6f6'],
    'brass': ['#4e2a14', '#87501f', '#bb8233', '#e0b252', '#f6dc8a', '#fff4c8'],
    'wood': ['#36200f', '#5f3c1f', '#8a5d33', '#b0834e', '#d0a874', '#e8cc9c'],
    'dirt': ['#2a1a10', '#4d3220', '#714a2f', '#946644', '#b0835a', '#c8a07a'],
    'grass': ['#24401b', '#3d6527', '#5a8a35', '#7eae47', '#a6cc65'],
    'stone': ['#2f3033', '#4c4e50', '#6e706f', '#939491', '#b8b8b2', '#d8d7cf'],
    'blue': ['#18224a', '#263f86', '#3563b8', '#4f8ad8', '#7cb2ee', '#b5d8fb'],
    'red': ['#3e1010', '#7a1f1b', '#ad3226', '#d4533a', '#ef8565'],
    'cloth': ['#5e5a52', '#9a958a', '#c7c2b5', '#e6e2d6', '#fbf9f1'],
    'yellow': ['#5a3a08', '#a3701a', '#d9a62c', '#f2d052', '#fff09a'],
    'rubber': ['#141417', '#24252a', '#383a42', '#50535d'],
    'glyph': ['#30333a'],
}


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(material, i):
    r = RAMP[material]
    return rgb(r[max(0, min(len(r) - 1, i))])


class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]
        self.mat = [[None] * w for _ in range(h)]   # material per pixel (for the outline)

    def set(self, x, y, c, material=None):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[y][x] = c
            self.mat[y][x] = material

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = None
            self.mat[y][x] = None

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else None

    def paint(self, rows, legend, x0=0, y0=0):
        """ASCII painting: each character maps to (material, ramp index) or an explicit colour."""
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch in '. ':
                    continue
                spec = legend[ch]
                if isinstance(spec, tuple):
                    self.set(x0 + i, y0 + j, shade(*spec), spec[0])
                else:
                    self.set(x0 + i, y0 + j, rgb(spec), None)

    def material_outline(self, fixed=None):
        """Create-style edge: transparent pixels next to the object become its material's darkest shade."""
        add = {}
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and self.px[ny][nx] is not None:
                        add[(x, y)] = self.mat[ny][nx]
                        break
        for (x, y), m in add.items():
            if fixed is not None:
                self.set(x, y, fixed, m)
            else:
                self.set(x, y, shade(m or 'glyph', 0), m)

    def image(self):
        im = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is not None:
                    im.putpixel((x, y), self.px[y][x])
        return im


def noise(x, y, seed=0):
    v = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return (v ^ (v >> 16)) / 0xFFFFFFFF


# ================================================================== isometric block renderer
# corners NW(0) NE(1) SW(2) SE(3), x east, z south.
# Screen: x_s = cx + (x - z) * HW,  y_s = cy + (x + z) * HH - y * VS

HW, HH, VS = 6.5, 3.25, 6.5


def inside(poly, x, y):
    hit = False
    for k in range(len(poly)):
        (x0, y0), (x1, y1) = poly[k], poly[(k + 1) % len(poly)]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            hit = not hit
    return hit


def line(c, p0, p1, col, material=None, only_on_object=False):
    x0, y0 = int(math.floor(p0[0])), int(math.floor(p0[1]))
    x1, y1 = int(math.floor(p1[0])), int(math.floor(p1[1]))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        if not only_on_object or c.get(x0, y0) is not None:
            c.set(x0, y0, col, material)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def iso(c, heights, face_color, material=None, cx=8.0, cy=8.0, base=0.0, edges=None):
    """
    Draws a block whose top has the four corner heights. face_color(face, u, v) gives the colour of a pixel on
    face 'top' / 'south' / 'east' with (u, v) its position on that face (top: u = x, v = z; sides: u along the
    face from the front corner, v = 0 at the top edge .. 1 at the bottom). edges = (light, mid) colours.
    """
    def P(x, z, y):
        return (cx + (x - z) * HW, cy + (x + z) * HH - y * VS)

    h = heights
    nw, ne, sw, se = P(0, 0, h[0]), P(1, 0, h[1]), P(0, 1, h[2]), P(1, 1, h[3])
    swb, seb, neb = P(0, 1, base), P(1, 1, base), P(1, 0, base)
    faces = {'south': [sw, se, seb, swb], 'east': [se, ne, neb, seb], 'top': [nw, ne, se, sw]}
    hm = sum(h) / 4
    for face in ('south', 'east', 'top'):
        poly = faces[face]
        for py in range(16):
            for px in range(16):
                X, Y = px + .5, py + .5
                if not inside(poly, X, Y):
                    continue
                if face == 'top':
                    a = (X - cx) / HW                 # x - z
                    s_ = (Y - cy + VS * hm) / HH      # x + z (planar approximation)
                    u = max(0, min(1, (a + s_) / 2))
                    v = max(0, min(1, (s_ - a) / 2))
                elif face == 'south':
                    u = max(0, min(1, (X - sw[0]) / (se[0] - sw[0])))
                    top = sw[1] + (se[1] - sw[1]) * u
                    bot = swb[1] + (seb[1] - swb[1]) * u
                    v = max(0, min(1, (Y - top) / max(.01, bot - top)))
                else:
                    u = max(0, min(1, (X - se[0]) / (ne[0] - se[0])))
                    top = se[1] + (ne[1] - se[1]) * u
                    bot = seb[1] + (neb[1] - seb[1]) * u
                    v = max(0, min(1, (Y - top) / max(.01, bot - top)))
                col = face_color(face, u, v)
                if col is not None:
                    c.set(px, py, col, material)
    if edges:
        light, mid = edges
        line(c, sw, se, light, material, True)      # front edges of the top face catch the light
        line(c, se, ne, light, material, True)
        line(c, se, seb, mid, material, True)       # the vertical front corner
    return {'nw': nw, 'ne': ne, 'sw': sw, 'se': se}


# ================================================================== GUI icons (Create-like light glyphs)
GLYPH = {'top': rgb('#f4f3ec'), 'south': rgb('#b9bab2'), 'east': rgb('#83847e')}
EDGE_LIGHT, EDGE_MID = rgb('#ffffff'), rgb('#d6d7cf')
OUTLINE = rgb('#30333a')
ACCENT, ACCENT_DARK = rgb('#f0c45a'), rgb('#b8862e')
SHADOW = (16, 18, 22, 130)


def glyph_block(heights, base=0.0, cy=8.0):
    c = Canvas()
    iso(c, heights, lambda face, u, v: GLYPH[face], 'glyph', cy=cy, base=base, edges=(EDGE_LIGHT, EDGE_MID))
    return c


def glyph_finish(c):
    """Dark rim around everything, then a soft drop shadow 1 px down-right."""
    c.material_outline(fixed=OUTLINE)
    im = c.image()
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    for y in range(15):
        for x in range(15):
            if im.getpixel((x, y))[3]:
                out.putpixel((x + 1, y + 1), SHADOW)
    out.alpha_composite(im)
    return out


def arrow(c, x, y, dx, dy, length=3, col=ACCENT):
    """Arrow with its tip at (x, y) pointing along (dx, dy) (axis or diagonal unit steps)."""
    for k in range(length + 1):
        c.set(x - dx * k, y - dy * k, col)
    if dx and dy:
        for k in (1, 2):
            c.set(x - dx * k, y, col)
            c.set(x, y - dy * k, col)
    else:
        px, py = -dy, dx
        for k in (1, 2):
            c.set(x - dx * k + px * k, y - dy * k + py * k, col)
            c.set(x - dx * k - px * k, y - dy * k - py * k, col)


def shape_icons():
    lo = .08
    icons = {}

    # ramps rise "forward" = north = towards the upper right on screen
    for name, rise in (('ramp_quarter', .3), ('ramp_half', .55), ('ramp_full', .92)):
        c = glyph_block([lo + rise, lo + rise, lo, lo], cy=9)
        arrow(c, 14, 1, 1, -1, length=2)
        icons[name] = c

    drop = glyph_block([.7, .7, .12, .12], cy=9)
    arrow(drop, 2, 14, -1, 1, length=2)
    icons['drop_half'] = drop

    # banks rise to the left (west, upper-left edge) or to the right (east, lower-right edge)
    for name, h in (('bank_left_half', [.6, lo, .6, lo]), ('bank_left_full', [.95, lo, .95, lo])):
        c = glyph_block(h, cy=9)
        arrow(c, 1, 1, -1, -1, length=2)
        icons[name] = c
    for name, h in (('bank_right_half', [lo, .6, lo, .6]), ('bank_right_full', [lo, .95, lo, .95])):
        c = glyph_block(h, cy=9)
        arrow(c, 14, 1, 1, -1, length=2)
        icons[name] = c

    corner = glyph_block([.95, .5, .5, .08], cy=9)
    for x, y in ((8, 0), (7, 1), (8, 1), (9, 1)):
        corner.set(x, y, ACCENT)
    icons['corner_bank'] = corner

    auto = glyph_block([.5, .82, .5, .5], cy=9.5)
    arrow(auto, 14, 0, 0, -1, length=2)
    icons['auto'] = auto

    whole = glyph_block([.5, .5, .5, .5], cy=10)
    arrow(whole, 1, 0, 0, -1, length=2)
    arrow(whole, 1, 6, 0, 1, length=2)
    icons['whole'] = whole

    flat = glyph_block([.42, .42, .42, .42], cy=10)
    for x in range(2, 15):
        flat.set(x, 2, ACCENT if x % 4 else ACCENT_DARK)
    icons['flatten'] = flat

    reset = glyph_block([1, 1, 1, 1], cy=8.5)
    for k in range(0, 280, 15):
        a = math.radians(k - 210)
        reset.set(int(round(12.5 + 2.4 * math.cos(a))), int(round(3 + 2.4 * math.sin(a))), ACCENT)
    reset.set(10, 0, ACCENT)
    reset.set(11, 1, ACCENT)
    icons['reset'] = reset

    icons['category_jumps'] = icons['ramp_full']
    icons['category_berms'] = icons['bank_right_full']
    icons['category_manual'] = icons['auto']
    return {k: glyph_finish(v) for k, v in icons.items()}


# ================================================================== items

def item_shovel():
    """Trail Shaper: a short spade - steel blade with a brass collar on an oak shaft with a brass grip."""
    c = Canvas()
    legend = {
        '1': ('steel', 1), '2': ('steel', 2), '3': ('steel', 3), '4': ('steel', 4), '5': ('steel', 5),
        'b': ('brass', 1), 'B': ('brass', 2), 'y': ('brass', 3), 'Y': ('brass', 4),
        'h': ('wood', 1), 'H': ('wood', 2), 'j': ('wood', 3), 'J': ('wood', 4),
    }
    c.paint([
        "..........2.....",
        ".........3442...",
        "........345432..",
        ".......3455432..",
        "......24544321..",
        "......2344332...",
        ".......233221...",
        "........23221...",
        ".......yYb11....",
        "......jyBb......",
        ".....jHh........",
        "....jHh.........",
        "...JHh..........",
        "..yJh...........",
        ".yYBb...........",
        "..Bb............",
    ], legend)
    c.material_outline()
    return c


def item_dirt():
    """Shaping Dirt: a chunk of packed trail dirt with a smooth sloped top and a grassy back edge."""
    def colour(face, u, v):
        n = noise(int(u * 8), int(v * 8), 3 if face == 'top' else 5)
        if face == 'top':
            if v < .16:
                return shade('grass', 3 if n > .4 else 2)
            return shade('dirt', 5 if n > .8 else 4)
        if face == 'south':
            if v < .12:
                return shade('dirt', 4)
            return shade('dirt', 3 if n > .65 else 2)
        return shade('dirt', 2 if n > .65 else 1)

    c = Canvas()
    iso(c, [.95, .95, .5, .5], colour, 'dirt', cy=8.5, edges=(shade('dirt', 5), shade('dirt', 3)))
    for x, y in ((5, 11), (11, 12), (9, 9)):
        if c.get(x, y):
            c.set(x, y, shade('stone', 3), 'dirt')
    c.material_outline()
    return c


def item_deck():
    """Shaping Deck: a short run of oak planks (seams across), with a visible plank end grain."""
    def colour(face, u, v):
        if face == 'top':
            k = v * 4
            if k % 1 < .2:
                return shade('wood', 1)
            grain = noise(int(u * 10), int(k), 7) > .72
            return shade('wood', 3 if grain else 4)
        if face == 'south':
            return shade('wood', 3 if v < .5 else 2)
        k = u * 4
        return shade('wood', 0 if k % 1 < .2 else 2)

    c = Canvas()
    iso(c, [.42, .42, .42, .42], colour, 'wood', cy=10, edges=(shade('wood', 5), shade('wood', 3)))
    c.material_outline()
    return c


def item_airbag():
    """Airbag: a puffy quilted blue landing bag with a yellow valve."""
    def colour(face, u, v):
        if face == 'top':
            qu, qv = (u * 2) % 1, (v * 2) % 1
            if qu < .12 or qv < .12:
                return shade('blue', 2)
            light = (1 - qu) + (1 - qv)
            return shade('blue', 5 if light > 1.25 else 4 if light > .7 else 3)
        if face == 'south':
            return shade('blue', 3 if v < .5 else 2)
        return shade('blue', 2 if v < .5 else 1)

    c = Canvas()
    iso(c, [.66, .66, .66, .66], colour, 'blue', cy=9, edges=(shade('blue', 5), shade('blue', 3)))
    c.set(11, 11, shade('yellow', 4), 'yellow')
    c.set(12, 11, shade('yellow', 3), 'yellow')
    c.set(11, 12, shade('yellow', 2), 'yellow')
    c.material_outline()
    return c


def item_barrier():
    """Cloth barrier: a roll of red-white trail tape with its loose end."""
    c = Canvas()
    cx, cy, r = 6.0, 7.5, 5.2
    # loose end of the tape first (behind the roll), diagonal stripes
    for x in range(8, 16):
        for y in range(9, 13):
            t = y - 9 - (x - 8) * .25
            if 0 <= t < 2.6:
                stripe = ((x + y) // 2) % 2 == 0
                c.set(x, y, shade('red', 3 if t < 1.2 else 2) if stripe else shade('cloth', 4 if t < 1.2 else 2),
                      'red' if stripe else 'cloth')
    # side of the roll (depth), then its face with alternating windings
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - cx - 1.3, y + .5 - cy - .6)
            if d <= r:
                c.set(x, y, shade('red', 1), 'red')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            if d <= 1.5:
                c.set(x, y, shade('rubber', 1), 'rubber')
            elif d <= 2.3:
                c.set(x, y, shade('cloth', 1), 'cloth')
            elif d <= r:
                lit = (x + .5 - cx) + (y + .5 - cy) < 0
                stripe = int(d * 1.15) % 2 == 0
                if stripe:
                    c.set(x, y, shade('red', 3 if lit else 2), 'red')
                else:
                    c.set(x, y, shade('cloth', 4 if lit else 3), 'cloth')
    c.material_outline()
    return c


def item_sign():
    """Trail sign: a small oak signboard on a post with a white arrow."""
    c = Canvas()
    legend = {
        'h': ('wood', 1), 'H': ('wood', 2), 'j': ('wood', 3), 'J': ('wood', 4), 'K': ('wood', 5),
        'w': ('cloth', 3), 'W': ('cloth', 4),
    }
    c.paint([
        "................",
        ".KJJJJJJJJJJJJ..",
        ".JjjjjjjjjjjjH..",
        ".JjjjWjjjjjjjH..",
        ".JjjWWWWWWWjjH..",
        ".JjWWWWWWWWjjH..",
        ".JjjWWWWWWWjjH..",
        ".JjjjWjjjjjjjH..",
        ".JHHHHHHHHHHHh..",
        "......jH........",
        "......jH........",
        "......jH........",
        "......jH........",
        "......jh........",
        ".....jjhh.......",
    ], legend, 0, 1)
    c.material_outline()
    return c


def block_airbag():
    """Tileable airbag fabric: 2x2 quilted panels lit from the top-left, double seams with stitching, a valve."""
    c = Canvas()
    for y in range(16):
        for x in range(16):
            qx, qy = x % 8, y % 8
            if qx == 0 or qy == 0:
                c.set(x, y, shade('blue', 1), 'blue')
                continue
            if qx == 7 or qy == 7:
                c.set(x, y, shade('blue', 2), 'blue')        # shadowed side of the seam
                continue
            light = (6 - qx) + (6 - qy)                     # 0 (bottom-right) .. 10 (top-left)
            edge = min(qx, qy, 7 - qx, 7 - qy)
            i = 3
            if light >= 7 and edge >= 1:
                i = 4
            if light >= 9:
                i = 5
            if light <= 2:
                i = 2
            c.set(x, y, shade('blue', i), 'blue')
    for base in (0, 8):                                     # stitching just inside the seams
        for k in range(2, 7, 2):
            c.set(base + k, base + 1, shade('blue', 2), 'blue')
            c.set(base + 1, base + k, shade('blue', 2), 'blue')
    c.set(12, 12, shade('yellow', 4), 'yellow')
    c.set(13, 12, shade('yellow', 3), 'yellow')
    c.set(12, 13, shade('yellow', 2), 'yellow')
    c.set(13, 13, shade('yellow', 1), 'yellow')
    return c


def item_rock():
    """Trail rock: a faceted granite boulder with a little moss on top."""
    c = Canvas()
    cx, cy = 8.0, 9.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            ang = math.atan2(dy, dx)
            r = 6.2 + .8 * math.sin(ang * 3 + .7) + .5 * math.sin(ang * 5)
            ry = r * .78
            if (dx / r) ** 2 + (dy / ry) ** 2 > 1:
                continue
            facet = round(ang / (math.pi / 3))      # quantised angle: flat faces
            fa = facet * math.pi / 3
            nz = 1 - ((dx / r) ** 2 + (dy / ry) ** 2)
            light = -math.cos(fa) * .5 - math.sin(fa) * .8 + nz * 1.2
            i = 4 if light > 1.0 else 3 if light > .4 else 2 if light > -.3 else 1
            if noise(x, y, 21) > .86:
                i = min(5, i + 1)
            c.set(x, y, shade('stone', i), 'stone')
    for x, y in ((6, 5), (7, 5), (8, 5), (7, 4), (9, 6), (5, 6)):
        if c.get(x, y):
            c.set(x, y, shade('grass', 3 if (x + y) % 2 else 2), 'stone')
    c.material_outline()
    return c


def item_roots():
    """Trail roots: two gnarled roots arching out of a clump of dirt."""
    c = Canvas()
    legend = {
        'd': ('dirt', 2), 'D': ('dirt', 3), 'e': ('dirt', 4), 'q': ('dirt', 1),
        'h': ('wood', 1), 'H': ('wood', 2), 'j': ('wood', 3), 'J': ('wood', 4),
    }
    c.paint([
        "................",
        "......jJJj......",
        ".....jHhhHj.....",
        "....jH....Hj....",
        "...jH..JJj.Hj...",
        "...Jh.jHhHj.h...",
        "..jH.jH...Hjh...",
        "..Jh.Jh....Hh...",
        ".eDDDJhDDeDDhDe.",
        "eDdDdhdDdDdDhdDe",
        "DdddqddDdqdddddD",
        ".qdddqddqdddddq.",
        "..qqqdqqdqqqqq..",
    ], legend, 0, 2)
    c.material_outline()
    return c


def item_support():
    """Wood support: a squared oak post with two iron bands and a bolted cap."""
    c = Canvas()
    legend = {
        'h': ('wood', 1), 'H': ('wood', 2), 'j': ('wood', 3), 'J': ('wood', 4), 'K': ('wood', 5),
        's': ('steel', 1), 'S': ('steel', 2), 't': ('steel', 3), 'T': ('steel', 4),
    }
    c.paint([
        "....KJJJJJJh....",
        "....JjjjjjHh....",
        "....TtttttSs....",
        ".....JjjjHh.....",
        ".....JjjjHh.....",
        ".....JjjjHh.....",
        ".....JjHjHh.....",
        ".....JjjjHh.....",
        ".....JjjjHh.....",
        ".....TttSSs.....",
        ".....JjjjHh.....",
        ".....JjjHHh.....",
        ".....JjjjHh.....",
        "....TtttttSs....",
        "....JjjjjjHh....",
    ], legend, 0, 1)
    c.material_outline()
    return c


def item_pump():
    """Floor pump in brass: T-handle, barrel, gauge, black hose with a red chuck, andesite foot."""
    c = Canvas()
    legend = {
        'h': ('wood', 2), 'H': ('wood', 3), 'J': ('wood', 4),
        'b': ('brass', 1), 'B': ('brass', 2), 'y': ('brass', 3), 'Y': ('brass', 4), 'Z': ('brass', 5),
        's': ('steel', 2), 'S': ('steel', 3), 'T': ('steel', 4),
        'k': ('rubber', 1), 'K': ('rubber', 2),
        'w': ('cloth', 4), 'r': ('red', 2), 'R': ('red', 3),
        'a': ('stone', 2), 'A': ('stone', 3), 'z': ('stone', 4),
    }
    c.paint([
        "...JJHHHHHh.....",
        "......TS........",
        "......TS........",
        ".....ZYyb.......",
        ".....YyBb...R...",
        ".....YyBb...r...",
        ".....YyBb...K...",
        ".....YyBb...K...",
        ".....wwwb...K...",
        ".....wrwb..K....",
        ".....wwwbKKk....",
        ".....YyBb.......",
        ".....YyBb.......",
        "..zAAAAAAAAa....",
        "..AaaaaaaaaA....",
    ], legend, 0, 1)
    c.material_outline()
    return c


RAMP['teal'] = ['#0f2f33', '#17545a', '#22818a', '#3fb3b4', '#86e0d6']


def item_bike(frame, hardtail):
    """Side view: two wheels, frame in the given ramp, gold / steel fork, black saddle and bars."""
    c = Canvas()
    wy = 11.5
    for y in range(16):
        for x in range(16):
            for cx in (3.5, 12.5):
                d = math.hypot(x + .5 - cx, y + .5 - wy)
                if d <= 3.6:
                    if d >= 2.6:
                        lit = (y + .5 - wy) < -.5
                        c.set(x, y, shade('rubber', 3 if lit else 2), 'rubber')
                    elif d >= 2.0:
                        c.set(x, y, shade('steel', 3), 'steel')
                    elif d < .8:
                        c.set(x, y, shade('steel', 4), 'steel')
    light, mid, dark = shade(frame, 4), shade(frame, 3), shade(frame, 2)
    line(c, (3, 11), (7, 12), dark, frame)          # chainstay
    line(c, (7, 12), (6, 6), mid, frame)            # seat tube
    line(c, (6, 6), (11, 6), light, frame)          # top tube
    line(c, (8, 11), (11, 7), mid, frame)           # down tube
    line(c, (4, 10), (6, 7), dark, frame)           # seat stay
    fork = 'steel' if hardtail else 'yellow'
    line(c, (11, 7), (12, 11), shade(fork, 3), fork)
    c.set(12, 8, shade(fork, 4), fork)
    if not hardtail:                                # rear shock
        c.set(7, 8, shade('yellow', 4), 'yellow')
        c.set(8, 8, shade('yellow', 3), 'yellow')
    c.set(11, 5, shade('rubber', 3), 'rubber')      # stem + bars
    c.set(11, 4, shade('rubber', 2), 'rubber')
    c.set(12, 4, shade('rubber', 3), 'rubber')
    sy = 5 if hardtail else 4
    for x in (4, 5, 6):                             # saddle
        c.set(x, sy, shade('rubber', 3 if x == 4 else 2), 'rubber')
    c.set(6, sy + 1, shade('steel', 3), 'steel')
    c.set(7, 12, shade('steel', 4), 'steel')        # crank
    c.material_outline()
    return c


# ================================================================== write

def outputs():
    out = {
        'item/trail_shovel.png': item_shovel().image(),
        'item/trail_dirt.png': item_dirt().image(),
        'item/trail_deck.png': item_deck().image(),
        'item/airbag.png': item_airbag().image(),
        'item/cloth_barrier.png': item_barrier().image(),
        'item/trail_sign.png': item_sign().image(),
        'block/airbag.png': block_airbag().image(),
        'item/trail_rock.png': item_rock().image(),
        'item/trail_roots.png': item_roots().image(),
        'item/wood_support.png': item_support().image(),
        'item/bike_pump.png': item_pump().image(),
        'item/mountain_bike.png': item_bike('teal', False).image(),
        'item/hardtail_bike.png': item_bike('red', True).image(),
    }
    for name, im in shape_icons().items():
        out[f'gui/shape/{name}.png'] = im
    return out


def contact_sheet(files, path):
    cols, cell = 8, 112
    rows = (len(files) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * cell), (40, 52, 61, 255))   # radial-menu background
    for k, (rel, im) in enumerate(files.items()):
        big = im.resize((96, 96), Image.NEAREST)
        sheet.alpha_composite(big, ((k % cols) * cell + 8, (k // cols) * cell + 8))
    sheet.save(path)


def main():
    install = '--install' in sys.argv
    root = ASSETS if install else STAGE
    files = outputs()
    for rel, im in files.items():
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    os.makedirs(STAGE, exist_ok=True)
    contact_sheet(files, os.path.join(STAGE, '_sheet.png'))
    print('wrote', len(files), 'files to', os.path.abspath(root))


if __name__ == '__main__':
    main()
