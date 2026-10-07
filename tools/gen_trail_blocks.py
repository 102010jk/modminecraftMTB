"""Packed trail dirt and trail deck boards, in the vanilla style: clustered 1-3 px tone blobs from a 4-5 shade
palette measured from vanilla dirt / dirt path / spruce planks, no soft gradients, no salt-and-pepper noise.

    python tools/gen_trail_blocks.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'descentmtb', 'textures', 'block')

# vanilla dirt shades, darkest -> lightest
DIRT = [(89, 61, 41), (108, 75, 50), (121, 85, 58), (136, 97, 66), (150, 108, 74)]
# packed riding surface: a touch warmer and flatter than plain dirt (fewer extremes)
PACKED = [(97, 68, 45), (114, 80, 53), (126, 90, 60), (137, 99, 66), (148, 107, 72)]
PEBBLE = [(96, 92, 86), (122, 118, 110)]
# weathered spruce deck boards
WOOD = [(85, 58, 31), (101, 74, 42), (112, 82, 46), (122, 90, 52), (130, 97, 58), (138, 103, 62)]
NAIL = (60, 58, 56)


def blobs(seed, weights):
    """16x16 grid of shade indices made of small wrapped clusters (vanilla dirt look)."""
    r = random.Random(seed)
    grid = [[2] * 16 for _ in range(16)]
    shades = [i for i, w in enumerate(weights) for _ in range(w)]
    for _ in range(150):
        s = r.choice(shades)
        x, y = r.randrange(16), r.randrange(16)
        n = r.choice((1, 1, 2, 2, 3))
        for _ in range(n):
            grid[y % 16][x % 16] = s
            dx, dy = r.choice(((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)))
            x, y = x + dx, y + dy
    return grid


def packed_top():
    g = blobs(11, [1, 3, 1, 3, 2])
    im = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            im.putpixel((x, y), PACKED[g[y][x]])
    r = random.Random(5)
    for _ in range(3):  # a few embedded pebbles: dark pixel with a lit pixel above-left
        x, y = r.randrange(1, 15), r.randrange(1, 15)
        im.putpixel((x, y), PEBBLE[0])
        im.putpixel((x - 1, y - 1) if r.random() < .5 else (x, y - 1), PEBBLE[1])
    return im


def packed_side():
    g = blobs(23, [2, 2, 0, 2, 1])
    top = packed_top()
    im = Image.new('RGB', (16, 16))
    r = random.Random(9)
    depth = [3 + (1 if r.random() < .35 else 0) for _ in range(16)]
    for x in range(1, 15):  # keep the edge ragged but not noisy
        if depth[x - 1] == depth[x + 1]:
            depth[x] = depth[x - 1]
    for y in range(16):
        for x in range(16):
            if y < depth[x]:
                c = top.getpixel((x, y + 6))
                if y == depth[x] - 1:
                    c = PACKED[1]  # compacted crust line
            else:
                c = DIRT[g[y][x]]
            im.putpixel((x, y), c)
    return im


def boards():
    im = Image.new('RGB', (16, 16))
    r = random.Random(3)
    joints = [11, 4, 14, 7]  # staggered board ends
    for b in range(4):
        y0 = b * 4
        base = [3, 4, 3, 2][b]
        for y in range(y0, y0 + 4):
            for x in range(16):
                im.putpixel((x, y), WOOD[base])
        # grain: long horizontal streaks, mostly one shade darker (vanilla planks)
        for _ in range(4):
            y = y0 + r.randrange(0, 3)
            x = r.randrange(16)
            s = base - 1 if r.random() < .7 else base + 1
            for k in range(r.randrange(4, 9)):
                im.putpixel(((x + k) % 16, y), WOOD[max(1, min(5, s))])
        # dark seam under every board and a staggered end joint
        for x in range(16):
            im.putpixel((x, y0 + 3), WOOD[0])
        j = joints[b]
        for y in range(y0, y0 + 3):
            im.putpixel((j, y), WOOD[0])
        # one nail head on each board end, two pixels clear of the joint
        im.putpixel(((j - 2) % 16, y0 + 1), NAIL)
        im.putpixel(((j + 2) % 16, y0 + 1), NAIL)
    return im


def main():
    packed_top().save(os.path.join(OUT, 'packed_trail_dirt_top.png'))
    packed_side().save(os.path.join(OUT, 'packed_trail_dirt_side.png'))
    boards().save(os.path.join(OUT, 'trail_boards.png'))
    print('wrote packed_trail_dirt_top/side, trail_boards')


if __name__ == '__main__':
    main()
