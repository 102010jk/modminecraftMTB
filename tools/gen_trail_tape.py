"""Draws the barrier post texture (16 x 16). The ribbon (entity/trail_tape.png) is made by gen_textures_v4.py and is not touched here."""
import os
import random
from PIL import Image

ASSETS = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'descentmtb', 'textures')

RED = (205, 38, 40)
WHITE = (242, 238, 230)
WOOD = [(88, 62, 36), (112, 80, 46), (138, 100, 58), (156, 116, 70)]
ORANGE = [(196, 78, 14), (240, 112, 22), (255, 146, 48)]


def shade(colour, factor):
    return tuple(max(0, min(255, round(c * factor))) for c in colour)


def post():
    """Left strip: wooden stake side (2 x 16) and its end (2 x 2). Right: the reflective cap (3 x 4 side, 3 x 3 top)."""
    rng = random.Random(11)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(2):
            tone = WOOD[(1 + x + (y // 3) % 2 + rng.randint(0, 1)) % 4]
            im.putpixel((x, y), tone + (255,))
    for y in range(2, 4):
        for x in range(2, 4):
            im.putpixel((x, y - 2), WOOD[1] + (255,))
    side_rows = [ORANGE[1], WHITE, WHITE, ORANGE[0]]
    for j, colour in enumerate(side_rows):
        for x in range(8, 11):
            im.putpixel((x, j), colour + (255,))
    for j in range(4, 7):
        for x in range(8, 11):
            im.putpixel((x, j), ORANGE[2 if (x + j) % 2 else 1] + (255,))
    return im


def main():
    block = os.path.join(ASSETS, 'block', 'barrier_post.png')
    post().save(block)
    print('wrote', block)


if __name__ == '__main__':
    main()
