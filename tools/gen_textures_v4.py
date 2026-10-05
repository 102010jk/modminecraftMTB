"""
Descent MTB textures v4 - rough, vanilla-Minecraft look.

The colour ramps are measured from the vanilla textures in the NeoForge resources jar (only the colours are
used, no pixels are copied), fills are dithered with clustered noise instead of smooth gradients, edges are
slightly irregular and every object is outlined with the darkest shade of its own material - the way vanilla
items and blocks are painted.

    python tools/gen_textures_v4.py [--install]
"""
import glob
import io
import math
import os
import sys
import zipfile
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures_v3 as v3          # Canvas, iso(), line(), noise() and the GUI glyphs

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'textures')
STAGE = os.path.join(HERE, 'preview', 'v4')
JAR = sorted(glob.glob(os.path.join(HERE, '..', 'build', 'moddev', 'artifacts', '*client-extra*.jar')))[-1]

noise = v3.noise
Canvas = v3.Canvas


# ------------------------------------------------------------------ palettes measured from vanilla

def _vanilla(path):
    with zipfile.ZipFile(JAR) as z:
        return Image.open(io.BytesIO(z.read('assets/minecraft/textures/' + path + '.png'))).convert('RGBA')


def measure(path, n=5, tint=None, crop=None):
    """n colours from dark to light (luminance quantiles) of a vanilla texture."""
    im = _vanilla(path)
    if crop:
        im = im.crop(crop)
    px = [im.getpixel((x, y)) for y in range(im.size[1]) for x in range(im.size[0]) if im.getpixel((x, y))[3] > 200]
    if tint:
        px = [(p[0] * tint[0] // 255, p[1] * tint[1] // 255, p[2] * tint[2] // 255, 255) for p in px]
    px.sort(key=lambda p: .3 * p[0] + .59 * p[1] + .11 * p[2])
    qs = [.03, .22, .5, .78, .97] if n == 5 else [i / (n - 1) * .94 + .03 for i in range(n)]
    return [px[int(q * (len(px) - 1))][:3] + (255,) for q in qs]


def darker(c, f=.55):
    return tuple(int(v * f) for v in c[:3]) + (255,)


PAL = {
    'oak': measure('block/oak_planks'),
    'stripped': measure('block/stripped_oak_log'),
    'bark': measure('block/oak_log'),
    'stick': measure('item/stick'),
    'stone': measure('block/andesite'),
    'cobble': measure('block/cobblestone'),
    'dirt': measure('block/dirt'),
    'coarse': measure('block/coarse_dirt'),
    'moss': measure('block/moss_block'),
    'grass': measure('block/grass_block_top', tint=(124, 189, 107)),
    'red': measure('block/red_wool'),
    'white': measure('block/white_wool'),
    'blue': measure('block/blue_wool'),
    'lblue': measure('block/light_blue_wool'),
    'iron': measure('item/iron_ingot'),
    'gold': measure('item/gold_ingot'),
    'black': measure('block/black_wool'),
    'cyan': measure('block/cyan_wool'),
    'orange': measure('block/orange_wool'),
    'yellow': measure('block/yellow_wool'),
}
for k in list(PAL):
    PAL[k] = [PAL[k][0] if i == 0 else c for i, c in enumerate(PAL[k])]
    PAL[k].insert(0, darker(PAL[k][0], .6))          # index 0 = outline shade


def col(mat, i):
    r = PAL[mat]
    return r[max(0, min(len(r) - 1, i))]


def rough(x, y, seed, amount=1):
    """-amount..+amount, clustered like vanilla noise (2x2 blobs mixed with single pixels)."""
    a = noise(x // 2, y // 2, seed)
    b = noise(x, y, seed + 101)
    v = a * .6 + b * .4
    return -amount if v < .3 else (amount if v > .72 else 0)


def put(c, x, y, mat, i, seed=0, amount=1):
    c.set(x, y, col(mat, i + rough(x, y, seed, amount)), mat)


def outline(c):
    """Vanilla items: a dark rim in the darkest shade of the touching material."""
    add = {}
    for y in range(16):
        for x in range(16):
            if c.get(x, y) is not None:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and c.get(nx, ny) is not None and c.mat[ny][nx] in PAL:
                    add[(x, y)] = c.mat[ny][nx]
                    break
    for (x, y), m in add.items():
        c.set(x, y, col(m, 0), m)


def iso_rough(c, heights, shades, mat, seed, cy=8.0, base=0.0, top_detail=None, edges=True):
    """Isometric block with flat face shades (top / south / east) roughened per pixel."""
    def colour(face, u, v):
        if face == 'top' and top_detail:
            r = top_detail(u, v)
            if r is not None:
                return r
        i = shades[face]
        px = int(u * 13), int(v * 13)
        # only the top is noisy; the sides stay two clean shades so the block keeps its edges
        jitter = rough(px[0], px[1], seed) if face == 'top' else (rough(px[0], px[1] + 40, seed) if face == 'south' and v > .3 else 0)
        return col(mat, i + jitter)
    v3.iso(c, heights, colour, mat, cy=cy, base=base, edges=(col(mat, 5), col(mat, 3)) if edges else None)


# ================================================================== items

def item_shovel():
    """Trail Shaper: an iron spade on an oak shaft with a gold ferrule (vanilla tool proportions)."""
    c = Canvas()
    rows = [
        "..........ii....",
        ".........iIIi...",
        "........iIWIIi..",
        ".......iIWIIIi..",
        "......iIIIIIIi..",
        "......iIIIIIi...",
        ".......iIIIi....",
        "........gGi.....",
        ".......sGg......",
        "......sS........",
        ".....sS.........",
        "....sS..........",
        "...sS...........",
        "..sS............",
        ".gG.............",
        "..g.............",
    ]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            if ch in 'iIW':
                i = {'i': 2, 'I': 4, 'W': 5}[ch]
                put(c, x, y, 'iron', i, 3)
            elif ch in 'gG':
                put(c, x, y, 'gold', 3 if ch == 'g' else 4, 4)
            else:
                put(c, x, y, 'stick', 2 if ch == 's' else 4, 5)
    outline(c)
    return c


def item_dirt():
    c = Canvas()

    def top(u, v):
        if v < .2:
            return col('grass', 3 + rough(int(u * 13), int(v * 13), 9))
        return None
    iso_rough(c, [.95, .95, .5, .5], {'top': 4, 'south': 2, 'east': 1}, 'dirt', 11, cy=8.5, top_detail=top)
    outline(c)
    return c


def item_deck():
    """Shaping Deck: three oak planks in a slanted 3/4 view, seams between them, front and side edges."""
    c = Canvas()
    for y in range(3, 11):
        x0 = 10 - (y - 3)
        for x in range(x0, x0 + 8):
            seam = y in (5, 8)
            put(c, x, y, 'oak', 2 if seam else (5 if y == 3 else 4), 12, 0 if seam else 1)
        put(c, x0 + 8, y, 'oak', 2, 13)                 # right side
        put(c, x0 + 9, y + 1, 'oak', 1, 13) if y < 10 else None
    for y in (11, 12):
        for x in range(3, 12 - (y - 11)):
            put(c, x, y, 'oak', 3 if y == 11 else 2, 14)   # front edge (plank ends)
    for y in (11, 12):
        c.set(5, y, col('oak', 1), 'oak')
        c.set(8, y, col('oak', 1), 'oak')
    outline(c)
    return c


def item_airbag():
    c = Canvas()

    def top(u, v):
        if (u * 2) % 1 < .1 or (v * 2) % 1 < .1:
            return col('blue', 2)
        return None
    iso_rough(c, [.95, .95, .95, .95], {'top': 4, 'south': 2, 'east': 1}, 'blue', 13, cy=8, top_detail=top)
    c.set(11, 11, col('gold', 4), 'gold')
    c.set(12, 11, col('gold', 3), 'gold')
    outline(c)
    return c


def item_tape():
    """Roll of trail tape seen at an angle: striped red/white side band, cardboard core, a loose end."""
    c = Canvas()
    cx, top, bot, rx, ry = 7.5, 6.0, 8.5, 6.5, 3.2
    for y in range(16):
        for x in range(16):
            dx = (x + .5 - cx) / rx
            if abs(dx) > 1:
                continue
            e = ry * math.sqrt(max(0, 1 - dx * dx))
            if top <= y + .5 <= bot + e:                       # side band
                red = ((x + int(y * .6)) // 2) % 2 == 0
                lit = dx < -.2
                put(c, x, y, 'red' if red else 'white', (3 if lit else 2), 21)
    for y in range(16):
        for x in range(16):
            dx, dy = (x + .5 - cx) / rx, (y + .5 - top) / ry
            if dx * dx + dy * dy <= 1:                         # top face: wound layers
                r = math.hypot(dx, dy)
                if r < .28:
                    put(c, x, y, 'oak', 1, 23)                 # inside of the cardboard core
                elif r < .42:
                    put(c, x, y, 'oak', 4, 23)
                else:
                    put(c, x, y, 'red' if int(r * 9) % 2 else 'white', 4, 24)
    for x in range(12, 16):                                    # loose end
        for y in (11, 12):
            put(c, x, y + (x - 12) // 2, 'red' if (x // 2) % 2 == 0 else 'white', 3, 25)
    outline(c)
    return c


def item_sign():
    c = Canvas()
    for y in range(2, 10):
        for x in range(1, 15):
            plank = (y - 2) // 4
            i = 3 if (y - 2) % 4 != 3 else 2
            if y == 2:
                i = 4
            put(c, x, y, 'oak', i, 31 + plank)
    for x, y in ((4, 5), (5, 4), (5, 5), (5, 6), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5)):
        c.set(x, y, col('white', 4), 'white')          # arrow
    for y in range(10, 16):
        put(c, 7, y, 'stripped', 3, 33)
        put(c, 8, y, 'stripped', 2, 34)
    outline(c)
    return c


def item_rock():
    c = Canvas()
    cx, cy = 8.0, 9.5
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            ang = math.atan2(dy, dx)
            r = 6.0 + .9 * math.sin(ang * 3 + .7) + .6 * math.sin(ang * 5 + 2)
            if (dx / r) ** 2 + (dy / (r * .78)) ** 2 > 1:
                continue
            light = -(dx + dy * 1.3) / 6
            i = 4 if light > .5 else 3 if light > -.3 else 2
            put(c, x, y, 'stone', i, 41)
    for x, y in ((6, 5), (7, 5), (8, 5), (7, 4), (9, 6), (5, 6), (8, 6)):
        if c.get(x, y):
            put(c, x, y, 'moss', 3, 42)
    outline(c)
    return c


def item_roots():
    """A gnarled root lifted out of the dirt: thick trunk end at the left, branching thinner to the right."""
    c = Canvas()
    for x in range(1, 15):
        y = int(round(9 + 2.2 * math.sin(x / 2.4)))
        w = 3 if x < 6 else 2 if x < 11 else 1
        for k in range(w):
            put(c, x, y - k, 'bark', 4 if k == w - 1 else 2, 53)
    for x0, y0, dx, dy, n in ((5, 8, 1, -1, 4), (9, 10, 1, 1, 3), (3, 10, -1, 1, 2)):
        for k in range(n):
            put(c, x0 + dx * k, y0 + dy * k, 'bark', 3, 54)
    for x in range(2, 7):                                      # clump of dirt on the thick end
        for y in range(11, 14):
            if (x - 4) ** 2 + (y - 12) ** 2 <= 4:
                put(c, x, y, 'dirt', 3, 55)
    outline(c)
    return c


def item_support():
    c = Canvas()
    for y in range(1, 16):
        for x in range(5, 11):
            i = 4 if x < 7 else 3 if x < 9 else 2
            put(c, x, y, 'stripped', i, 61)
    for y0 in (3, 12):
        for x in range(4, 12):
            c.set(x, y0, col('iron', 4 if x < 8 else 3), 'iron')
    for x in range(4, 12):
        put(c, x, 1, 'stripped', 5, 62)
    outline(c)
    return c


def item_pump():
    c = Canvas()
    for x in range(3, 12):
        put(c, x, 1, 'oak', 3, 71)                      # T-handle
    for y in range(2, 4):
        c.set(7, y, col('iron', 4), 'iron')
        c.set(8, y, col('iron', 3), 'iron')
    for y in range(4, 14):
        for x in range(6, 10):
            put(c, x, y, 'gold', 5 if x == 6 else 4 if x == 7 else 3, 72)
    for x, y in ((7, 9), (8, 9), (7, 10), (8, 10)):
        c.set(x, y, col('white', 4), 'white')           # gauge
    c.set(8, 9, col('red', 3), 'red')
    for y in range(14, 16):
        for x in range(3, 13):
            put(c, x, y, 'stone', 3 if y == 14 else 2, 73)
    for x, y in ((10, 12), (11, 12), (12, 11), (13, 10), (13, 9), (13, 8), (13, 7)):
        c.set(x, y, col('black', 2), 'black')           # hose
    c.set(13, 6, col('red', 3), 'red')
    outline(c)
    return c


def item_bike(frame, hardtail):
    c = Canvas()
    wy = 11.5
    for y in range(16):
        for x in range(16):
            for cx in (3.5, 12.5):
                d = math.hypot(x + .5 - cx, y + .5 - wy)
                if 2.6 <= d <= 3.6:
                    put(c, x, y, 'black', 3 if y < wy else 2, 81)
                elif 1.9 <= d < 2.6:
                    c.set(x, y, col('iron', 3), 'iron')
                elif d < .8:
                    c.set(x, y, col('iron', 4), 'iron')
    for p0, p1, i in (((3, 11), (7, 12), 2), ((7, 12), (6, 6), 3), ((6, 6), (11, 6), 4),
                      ((8, 11), (11, 7), 3), ((4, 10), (6, 7), 2)):
        v3.line(c, p0, p1, col(frame, i), frame)
    fork = 'iron' if hardtail else 'gold'
    v3.line(c, (11, 7), (12, 11), col(fork, 4), fork)
    if not hardtail:
        c.set(7, 8, col('gold', 4), 'gold')
        c.set(8, 8, col('gold', 3), 'gold')
    for x, y in ((11, 5), (11, 4), (12, 4)):
        c.set(x, y, col('black', 3), 'black')
    sy = 5 if hardtail else 4
    for x in (4, 5, 6):
        c.set(x, sy, col('black', 3 if x == 4 else 2), 'black')
    c.set(6, sy + 1, col('iron', 3), 'iron')
    outline(c)
    return c


# ================================================================== blocks (tileable)

def block_fill(mat, seed, base=3, amount=1):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            put(c, x, y, mat, base, seed, amount)
    return c


def block_airbag():
    c = block_fill('blue', 91, 3)
    for k in range(16):
        for a in (0, 8):
            c.set(k, a, col('blue', 1), 'blue')
            c.set(a, k, col('blue', 1), 'blue')
    for base in (0, 8):
        for k in range(1, 7):
            c.set(base + k, base + 1, col('blue', 4), 'blue')     # puff catches the light
            c.set(base + 1, base + k, col('blue', 4), 'blue')
            c.set(base + k, base + 7, col('blue', 2), 'blue')
            c.set(base + 7, base + k, col('blue', 2), 'blue')
    c.set(12, 12, col('gold', 4), 'gold')
    c.set(13, 12, col('gold', 3), 'gold')
    c.set(12, 13, col('gold', 3), 'gold')
    c.set(13, 13, col('gold', 2), 'gold')
    return c


def block_rock_side():
    c = block_fill('stone', 101, 3)
    for p0, p1 in (((3, 3), (6, 8)), ((6, 8), (5, 12)), ((11, 1), (13, 6)), ((12, 9), (14, 13))):
        v3.line(c, p0, p1, col('stone', 1), 'stone')
    return c


def block_rock_top():
    c = block_rock_side()
    for y in range(16):
        for x in range(16):
            m = noise(x // 3, y // 3, 102) * .7 + noise(x, y, 103) * .3
            if m > .6:
                put(c, x, y, 'moss', 3, 104)
    return c


def block_support_side():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            i = 4 if x < 4 else 3
            if x in (0, 15):
                i = 2
            c.set(x, y, col('stripped', i + rough(x, y // 3, 111)), 'stripped')
    for y0 in (2, 12):
        for x in range(16):
            c.set(x, y0, col('iron', 4 + rough(x, y0, 112)), 'iron')
            c.set(x, y0 + 1, col('iron', 2), 'iron')
        for x in (3, 12):
            c.set(x, y0, col('iron', 5), 'iron')
    return c


def block_support_top():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            ring = int(math.hypot(x + .5 - 7, y + .5 - 9) * 1.2 + noise(x, y, 121) * .6) % 2
            i = 4 if ring else 3
            if x in (0, 15) or y in (0, 15):
                i = 2
            c.set(x, y, col('stripped', i), 'stripped')
    return c


def block_roots():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            streak = noise(x, y // 4, 131)
            i = 4 if streak > .75 else 3 if streak > .35 else 2
            c.set(x, y, col('bark', i + rough(x, y, 132)), 'bark')
    return c


def block_sign_board():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c.set(x, y, col('oak', 1), 'oak')
                continue
            seam = (y - 1) % 5 == 4
            i = 2 if seam else 3
            c.set(x, y, col('oak', i + (0 if seam else rough(x, y + (y - 1) // 5 * 9, 141))), 'oak')
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(x, y, col('iron', 3), 'iron')
    return c


def block_sign_post():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            i = [4, 3, 3, 2][x // 4]
            c.set(x, y, col('stripped', i + rough(x, y // 3, 151)), 'stripped')
    return c


def tape():
    im = Image.new('RGBA', (16, 4))
    for y in range(4):
        for x in range(16):
            red = ((x + y) // 4) % 2 == 0
            i = 4 if y == 0 else 3 if y < 3 else 2
            im.putpixel((x, y), col('red' if red else 'white', i + rough(x, y, 161)))
    return im


# ================================================================== write

def outputs():
    out = {
        'item/trail_shovel.png': item_shovel(),
        'item/trail_dirt.png': item_dirt(),
        'item/trail_deck.png': item_deck(),
        'item/airbag.png': item_airbag(),
        'item/cloth_barrier.png': item_tape(),
        'item/trail_sign.png': item_sign(),
        'item/trail_rock.png': item_rock(),
        'item/trail_roots.png': item_roots(),
        'item/wood_support.png': item_support(),
        'item/bike_pump.png': item_pump(),
        'item/mountain_bike.png': item_bike('cyan', False),
        'item/hardtail_bike.png': item_bike('red', True),
        'block/airbag.png': block_airbag(),
        'block/trail_rock.png': block_rock_side(),
        'block/trail_rock_top.png': block_rock_top(),
        'block/wood_support.png': block_support_side(),
        'block/wood_support_top.png': block_support_top(),
        'block/trail_roots.png': block_roots(),
        'block/trail_sign.png': block_sign_board(),
        'block/trail_sign_post.png': block_sign_post(),
    }
    files = {k: v.image() for k, v in out.items()}
    files['entity/trail_tape.png'] = tape()
    return files


def main():
    install = '--install' in sys.argv
    root = ASSETS if install else STAGE
    files = outputs()
    for rel, im in files.items():
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    cols, cell = 7, 136
    sheet = Image.new('RGBA', (cols * cell, ((len(files) + cols - 1) // cols) * cell), (139, 139, 139, 255))
    for k, (rel, im) in enumerate(files.items()):
        big = im.resize((128, int(128 * im.size[1] / im.size[0])), Image.NEAREST)
        sheet.alpha_composite(big, ((k % cols) * cell + 4, (k // cols) * cell + 4))
    os.makedirs(STAGE, exist_ok=True)
    sheet.save(os.path.join(STAGE, '_sheet.png'))
    print('wrote', len(files), 'files to', os.path.abspath(root))


if __name__ == '__main__':
    main()
