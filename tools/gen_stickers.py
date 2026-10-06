"""
Frame stickers (decals) for the bike workshop: textures/sticker/<design>.png.
Long designs are 32x16 (logo_descent, flame, stripes, checker), the others 16x16. Bold, outlined, vanilla-pixel style.

    python tools/gen_stickers.py
"""
import math
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'descentmtb', 'textures', 'sticker')
INK = (24, 22, 26, 255)
WHITE = (248, 247, 240, 255)


def canvas(w=16, h=16):
    return Image.new('RGBA', (w, h), (0, 0, 0, 0))


def outline(im):
    w, h = im.size
    px = im.load()
    add = []
    for y in range(h):
        for x in range(w):
            if px[x, y][3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and px[nx, ny][3] and px[nx, ny] != INK:
                    add.append((x, y))
                    break
    for x, y in add:
        px[x, y] = INK
    return im


def text(im, x0, y0, rows, color):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '#':
                im.putpixel((x0 + i, y0 + j), color)


def logo():
    im = canvas(32, 16)
    for y in range(4, 12):
        for x in range(1, 31):
            im.putpixel((x, y), (31, 138, 143, 255) if y < 11 else (20, 100, 104, 255))
    text(im, 1, 5, [
        "###  ### ###  ### ### #  # ###",
        "#  # #   #   #    #   ## #  # ",
        "#  # ##  ### #    ##  # ##  # ",
        "#  # #     # #    #   #  #  # ",
        "###  ### ###  ### ### #  #  # ",
    ], WHITE)
    return outline(im)


def flame():
    im = canvas(32, 16)
    for x in range(32):
        top = 8 - 5 * math.exp(-((x % 9) - 4) ** 2 / 6) * (x / 31) - 1 * math.sin(x / 2.0)
        for y in range(16):
            if top <= y <= 12 and x >= 1:
                t = (y - top) / max(1, 12 - top)
                c = (255, 230, 90, 255) if t > .6 else (245, 140, 40, 255) if t > .25 else (210, 50, 30, 255)
                im.putpixel((x, y), c)
    return outline(im)


def lightning():
    im = canvas()
    pts = [(9, 1), (4, 8), (8, 8), (5, 15), (12, 6), (8, 6), (11, 1)]
    from PIL import ImageDraw
    ImageDraw.Draw(im).polygon(pts, fill=(250, 214, 64, 255))
    return outline(im)


def stripes():
    im = canvas(32, 16)
    for x in range(1, 31):
        for y in range(5, 11):
            band = (x - y) // 3 % 3
            im.putpixel((x, y), [(200, 50, 40, 255), WHITE, (40, 90, 190, 255)][band])
    return outline(im)


def star():
    im = canvas()
    from PIL import ImageDraw
    pts = []
    for k in range(10):
        r = 7 if k % 2 == 0 else 3
        a = math.pi / 2 + k * math.pi / 5
        pts.append((7.5 + r * math.cos(a), 7.5 - r * math.sin(a)))
    ImageDraw.Draw(im).polygon(pts, fill=(250, 214, 64, 255))
    return outline(im)


def skull():
    im = canvas()
    text(im, 3, 2, [
        " ######   ",
        "########  ",
        "##..##..# ",
        "##..##..# ",
        "##########",
        " ###..### ",
        "  ######  ",
        "  # ## #  ",
        "  # ## #  ",
    ], WHITE)
    px = im.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and 3 <= x <= 12 and 2 <= y <= 10:
                pass
    # eyes and nose (the '.' cells) in ink
    for x, y in ((5, 4), (6, 4), (5, 5), (6, 5), (9, 4), (10, 4), (9, 5), (10, 5), (7, 7), (8, 7)):
        px[x, y] = INK
    return outline(im)


def heart():
    im = canvas()
    for y in range(16):
        for x in range(16):
            X, Y = (x - 7.5) / 6, (7 - y) / 6
            if (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0:
                im.putpixel((x, y), (215, 45, 70, 255))
    im.putpixel((5, 5), (255, 160, 170, 255))
    return outline(im)


def checker():
    im = canvas(32, 16)
    for x in range(1, 31):
        for y in range(4, 12):
            im.putpixel((x, y), INK if ((x - 1) // 2 + (y - 4) // 2) % 2 == 0 else WHITE)
    return outline(im)


def number_plate():
    im = canvas()
    for y in range(2, 14):
        for x in range(2, 14):
            im.putpixel((x, y), WHITE)
    text(im, 4, 4, [
        "## ###",
        " #   #",
        " #  # ",
        " #  # ",
        "### # ",
    ], INK)
    for x in range(2, 14):
        im.putpixel((x, 11), (200, 50, 40, 255))
        im.putpixel((x, 12), (200, 50, 40, 255))
    return outline(im)


def mountain():
    im = canvas()
    for x in range(1, 15):
        for y in range(16):
            h1 = 13 - (6 - abs(x - 6)) * 1.6
            h2 = 13 - (5 - abs(x - 11)) * 1.4
            top = min(h1, h2)
            if top <= y <= 13:
                snow = y < top + 2 and top < 7
                im.putpixel((x, y), WHITE if snow else (70, 110, 150, 255))
    return outline(im)


def paw():
    im = canvas()
    col = (120, 80, 50, 255)
    for cx, cy, r in ((8, 11, 3.0), (3.5, 6.5, 1.5), (6.5, 3.5, 1.5), (10.5, 3.5, 1.5), (13, 6.5, 1.5)):
        for y in range(16):
            for x in range(16):
                if math.hypot(x + .5 - cx, y + .5 - cy) <= r:
                    im.putpixel((x, y), col)
    return outline(im)


def racing_7():
    im = canvas()
    for y in range(16):
        for x in range(16):
            if math.hypot(x + .5 - 8, y + .5 - 8) <= 7:
                im.putpixel((x, y), WHITE)
    text(im, 5, 4, [
        "######",
        "    ##",
        "   ## ",
        "  ##  ",
        "  ##  ",
        "  ##  ",
        "  ##  ",
    ], INK)
    return outline(im)


DESIGNS = {
    'logo_descent': logo, 'flame': flame, 'lightning': lightning, 'stripes': stripes, 'star': star,
    'skull': skull, 'heart': heart, 'checker': checker, 'number_plate': number_plate, 'mountain': mountain,
    'paw': paw, 'racing_7': racing_7,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new('RGBA', (6 * 140, 2 * 76), (90, 110, 120, 255))
    for k, (name, fn) in enumerate(DESIGNS.items()):
        im = fn()
        im.save(os.path.join(OUT, name + '.png'))
        big = im.resize((im.size[0] * 4, im.size[1] * 4), Image.NEAREST)
        sheet.alpha_composite(big, ((k % 6) * 140 + 4, (k // 6) * 76 + 6))
    sheet.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview', 'stickers.png'))
    print('wrote', len(DESIGNS), 'stickers')


if __name__ == '__main__':
    main()
