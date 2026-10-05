"""
Radial-menu icons of the Trail Shaper, vanilla style (rough fills, material outlines), one distinct picture per
mode so the menu can be read at a glance:

* jumps / ramps  - side profile of the block (dirt or planks), the height tells which preset; the jump builder is a
                   kicker over three blocks with a settings gear
* berms          - view along the trail: the tilted surface, arrow to the high side
* manual         - the block with the action drawn on it (cursor, arrows, level, reset, copy); the cursor's sub-types
                   (cursor_*) mark the corners a click picks on the top in gold
* lines          - a hillside with a trail and a flag

    python tools/gen_icons_v4.py [--install]
"""
import math
import os
import sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures_v4 as t4

Canvas, col, put, rough, outline = t4.Canvas, t4.col, t4.put, t4.rough, t4.outline
HERE = os.path.dirname(os.path.abspath(__file__))
WHITE = (250, 250, 245, 255)
INK = (40, 40, 44, 255)
ACCENT = t4.col('gold', 4)


def side(c, heights, mat='dirt', grass=True, x0=1, base=14, seed=1):
    """Side profile: column x0+i is filled from `base` up by heights[i] pixels."""
    for i, h in enumerate(heights):
        x = x0 + i
        top = base - int(round(h)) + 1
        for y in range(top, base + 1):
            if grass and y == top and mat == 'dirt':
                put(c, x, y, 'grass', 3, seed)
            elif mat == 'oak' and y == top:
                put(c, x, y, 'oak', 5, seed, 0)
            else:
                put(c, x, y, mat, 3 if y < top + 2 else 2, seed)


def glyph(c, pts, color=WHITE):
    for x, y in pts:
        c.set(x, y, color, None)


def ring_outline(c, color=INK):
    """Dark 1 px rim around white glyph pixels (material-less pixels)."""
    add = []
    for y in range(16):
        for x in range(16):
            if c.get(x, y) is not None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and c.get(nx, ny) is not None and c.mat[ny][nx] is None:
                    add.append((x, y))
                    break
    for x, y in add:
        c.set(x, y, color, None)


def arrow(c, x, y, dx, dy, length=3, color=WHITE):
    for k in range(length + 1):
        c.set(x - dx * k, y - dy * k, color, None)
    if dx and dy:
        for k in (1, 2):
            c.set(x - dx * k, y, color, None)
            c.set(x, y - dy * k, color, None)
    else:
        px, py = -dy, dx
        for k in (1, 2):
            c.set(x - dx * k + px * k, y - dy * k + py * k, color, None)
            c.set(x - dx * k - px * k, y - dy * k - py * k, color, None)


def finish(c):
    outline(c)          # material pixels: rim in their darkest shade
    ring_outline(c)     # glyph pixels: ink rim
    return c.image()


def ramp_profile(rise, curve=0.0):
    """Heights (px) of a 14 px wide ramp rising from 1 px to rise px; curve > 0 = kicker (concave)."""
    out = []
    for i in range(14):
        t = i / 13
        shaped = t ** (1 + curve) if curve >= 0 else 1 - (1 - t) ** (1 - curve)
        out.append(1 + (rise - 1) * shaped)
    return out


def icons():
    out = {}

    # ---------------------------------------------------------------- jumps (dirt side profiles)
    for name, rise in (('ramp_quarter', 4), ('ramp_half', 7.5), ('ramp_full', 13)):
        c = Canvas()
        side(c, ramp_profile(rise, .6), seed=len(name))
        out[name] = finish(c)
    c = Canvas()
    side(c, [9] * 6 + [8, 6, 4.5, 3.5, 3, 2.5, 2, 2], seed=7)
    arrow(c, 11, 5, 0, 1, 3)
    out['drop_half'] = finish(c)

    c = Canvas()                                        # jump builder: kicker, gap and landing + a settings gear
    side(c, ramp_profile(9, 1.3)[::2][:7], x0=0, seed=8)
    side(c, [8, 7, 5, 3, 2], x0=11, seed=9)
    glyph(c, [(2, 0), (1, 1), (2, 1), (3, 1), (0, 2), (1, 2), (3, 2), (4, 2), (1, 3), (2, 3), (3, 3), (2, 4)])
    out['jump_build'] = finish(c)

    # ---------------------------------------------------------------- berms (view along the trail)
    def bank(name, rise, left):
        c = Canvas()
        h = [2 + (rise - 2) * (i / 13) for i in range(14)]
        if left:
            h = h[::-1]
        side(c, h, seed=11 + rise)
        if left:
            arrow(c, 2, 2, -1, -1, 2)
        else:
            arrow(c, 13, 2, 1, -1, 2)
        return finish(c)
    out['bank_left_half'] = bank('l', 7, True)
    out['bank_right_half'] = bank('r', 7, False)
    out['bank_left_full'] = bank('l', 12, True)
    out['bank_right_full'] = bank('r', 12, False)

    c = Canvas()                                        # corner bank: iso block, one corner up
    t4.iso_rough(c, [.95, .5, .5, .1], {'top': 4, 'south': 2, 'east': 1}, 'dirt', 21, cy=9)
    arrow(c, 8, 0, 0, -1, 1)
    out['corner_bank'] = finish(c)

    c = Canvas()                                        # berm builder: curved band seen from above + 3 points
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + .5 - 2, y + .5 - 14)
            if 6.5 <= d <= 11.5 and x >= 1 and y <= 14:
                put(c, x, y, 'dirt', 4 if d > 9.8 else 3, 22)
    for x, y in ((3, 7), (8, 3), (12, 8)):
        c.set(x, y, ACCENT, None)
        c.set(x + 1, y, ACCENT, None)
        c.set(x, y + 1, ACCENT, None)
        c.set(x + 1, y + 1, ACCENT, None)
    out['berm_build'] = finish(c)

    # ---------------------------------------------------------------- manual
    def block(level=.55):
        c = Canvas()
        t4.iso_rough(c, [level] * 4, {'top': 4, 'south': 2, 'east': 1}, 'dirt', 31, cy=10)
        return c

    c = block()
    glyph(c, [(9, 2), (9, 3), (10, 3), (9, 4), (10, 4), (11, 4), (9, 5), (10, 5), (11, 5), (12, 5),
              (9, 6), (10, 6), (11, 7)])           # mouse cursor
    out['auto'] = finish(c)

    c = block(.45)
    arrow(c, 3, 0, 0, -1, 2)
    arrow(c, 3, 6, 0, 1, 2)
    out['whole'] = finish(c)

    # cursor sub-types: the top of the block with the picked corners in gold
    def picked_top(test, seed):
        def detail(u, v):
            return ACCENT if test(u, v) else None
        c = Canvas()
        t4.iso_rough(c, [.55] * 4, {'top': 4, 'south': 2, 'east': 1}, 'dirt', seed, cy=10, top_detail=detail)
        return c

    def zones(u, v):
        return any(abs(u - k) < .06 for k in (.3, .7)) or any(abs(v - k) < .06 for k in (.3, .7))
    c = Canvas()
    t4.iso_rough(c, [.55] * 4, {'top': 4, 'south': 2, 'east': 1}, 'dirt', 34, cy=10,
                 top_detail=lambda u, v: col('dirt', 1) if zones(u, v) else (ACCENT if u > .76 and v > .76 else None))
    glyph(c, [(11, 0), (11, 1), (12, 1), (11, 2), (12, 2), (13, 2), (11, 3), (12, 3), (13, 3), (14, 3), (12, 4)])
    out['cursor_auto_zone'] = finish(c)
    out['cursor_corner'] = finish(picked_top(lambda u, v: u > .55 and v > .55, 35))
    out['cursor_edge'] = finish(picked_top(lambda u, v: u > .62, 36))
    out['cursor_whole'] = finish(picked_top(lambda u, v: (u < .38 or u > .62) and (v < .38 or v > .62), 37))

    c = block(.45)
    for x in range(3, 13):                              # spirit level
        c.set(x, 3, (110, 190, 90, 255) if 6 <= x <= 9 else WHITE, None)
    c.set(7, 3, WHITE, None)
    out['flatten'] = finish(c)

    c = Canvas()
    t4.iso_rough(c, [1, 1, 1, 1], {'top': 4, 'south': 2, 'east': 1}, 'dirt', 33, cy=8.5)
    for k in range(0, 300, 12):
        a = math.radians(k - 220)
        c.set(int(round(12 + 2.6 * math.cos(a))), int(round(3 + 2.6 * math.sin(a))), WHITE, None)
    arrow(c, 10, 1, -1, 0, 1)
    out['reset'] = finish(c)

    c = Canvas()                                        # copy: two overlapping blocks of dirt
    for ox, oy, seed in ((1, 1, 41), (6, 6, 42)):
        for y in range(oy, oy + 9):
            for x in range(ox, ox + 9):
                put(c, x, y, 'grass' if y == oy else 'dirt', 3, seed)
    glyph(c, [(11, 1), (12, 1), (13, 1), (12, 0), (12, 2)])   # plus
    out['copy'] = finish(c)

    # ---------------------------------------------------------------- copycat ramps (planks)
    c = Canvas()
    side(c, ramp_profile(10, .6), 'oak', seed=51)
    glyph(c, [(3, 2), (2, 3), (3, 3), (4, 3), (3, 4)])
    out['ramp_make'] = finish(c)

    c = Canvas()
    side(c, ramp_profile(9, .6), 'oak', seed=52)
    arrow(c, 13, 1, 0, -1, 2)
    out['ramp_steepness'] = finish(c)

    c = Canvas()
    side(c, [5 + (12 - 5) * (i / 13) for i in range(14)], 'oak', seed=53)
    arrow(c, 2, 2, 0, -1, 2)
    out['ramp_start'] = finish(c)

    c = Canvas()
    side(c, ramp_profile(11, 1.6), 'oak', seed=54)
    for i in range(14):                                 # dotted straight line = the other profile
        if i % 2 == 0:
            c.set(1 + i, int(round(14 - (1 + 10 * i / 13))) - 1, WHITE, None)
    out['ramp_profile'] = finish(c)

    c = Canvas()
    side(c, ramp_profile(8, .6), 'oak', x0=1, base=14, seed=55)
    for k in range(0, 270, 12):
        a = math.radians(k - 180)
        c.set(int(round(8 + 4.5 * math.cos(a))), int(round(5 + 3 * math.sin(a))), WHITE, None)
    arrow(c, 12, 6, 0, 1, 1)
    out['ramp_rotate'] = finish(c)

    c = Canvas()
    side(c, [1 + 4 * (i / 6) for i in range(7)], 'oak', x0=1, seed=56)
    side(c, [6 + 6 * (i / 6) for i in range(7)], 'oak', x0=9, seed=57)
    glyph(c, [(6, 6), (7, 5), (8, 5), (9, 6), (8, 7), (7, 7)], (190, 196, 204, 255))   # chain link
    out['ramp_link'] = finish(c)

    # ---------------------------------------------------------------- lines
    c = Canvas()
    side(c, [13 - i * .8 for i in range(15)], seed=61, x0=0)
    for x, y in ((2, 4), (3, 5), (5, 6), (6, 7), (8, 8), (9, 9), (11, 10), (12, 11)):
        c.set(x, y, col('dirt', 5), 'dirt')             # the trail snaking down
    for y in range(0, 5):
        c.set(1, y, (200, 200, 200, 255), None)         # flag pole + flag
    glyph(c, [(2, 0), (3, 0), (2, 1), (3, 1), (4, 1)], (220, 60, 50, 255))
    out['downhill'] = finish(c)
    return out


def main():
    install = '--install' in sys.argv
    root = t4.ASSETS if install else t4.STAGE
    files = icons()
    for name, im in files.items():
        path = os.path.join(root, 'gui', 'shape', name + '.png')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    cols, cell = 8, 112
    sheet = Image.new('RGBA', (cols * cell, ((len(files) + cols - 1) // cols) * cell), (37, 52, 61, 255))
    for k, im in enumerate(files.values()):
        sheet.alpha_composite(im.resize((96, 96), Image.NEAREST), ((k % cols) * cell + 8, (k // cols) * cell + 8))
    os.makedirs(t4.STAGE, exist_ok=True)
    sheet.save(os.path.join(t4.STAGE, '_icons.png'))
    print('wrote', len(files), 'icons to', os.path.abspath(root))


if __name__ == '__main__':
    main()
