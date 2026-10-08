"""Hand-shaded (Create mod / vanilla "Jappa" style) block textures for Descent MTB.

  python tools/gen_trail_textures.py

* New trail materials, drawn from scratch, tileable:
    packed_trail_dirt_top / _side   - tamped trail dirt: tyre-polished lanes, embedded pebbles, crumbs, cracks
    trail_boards                    - nailed deck boards: grain, knots, gaps with ambient occlusion, nail heads
* Re-shading of the older flat-filled textures (airbag, barrier post, cloth barrier, stand materials):
  every flat colour region gets light from the top left (warm 1 px rim highlight, cool shadow on the bottom/right
  edges), a soft occlusion where it meets another region, and a fine material grain - the drawn design stays.

Rules (see the handover): light from the top left, never shade by adding black/white - highlights move towards
yellow/gold, shadows towards blue/violet/deep brown; occlusion in crevices; no flat synthetic fills.
"""
import colorsys, os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK = os.path.join(ROOT, "src/main/resources/assets/descentmtb/textures/block")
rng = np.random.default_rng(77)

def hsv(h, s, v):
    return np.array(colorsys.hsv_to_rgb(h / 360, s, v)) * 255

def shift(rgb, t):
    """Hue-shifted shade: t > 0 lighter and warmer (towards gold), t < 0 darker and cooler (towards violet)."""
    r, g, b = np.clip(np.asarray(rgb, float), 0, 255) / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    target = 50 / 360 if t > 0 else 260 / 360            # gold / violet
    d = ((target - h + 0.5) % 1.0) - 0.5
    h = (h + d * min(1, abs(t)) * 0.22) % 1.0
    v = min(1, max(0, v * (1 + 0.55 * t)))
    s = min(1, max(0, s * (1 - 0.25 * t) if t > 0 else s * (1 + 0.15 * -t)))
    return np.array(colorsys.hsv_to_rgb(h, s, v)) * 255

def noise(n, scale, seed):
    """Tileable value noise on an n x n torus."""
    r = np.random.default_rng(seed)
    g = r.random((scale, scale))
    out = np.zeros((n, n))
    for y in range(n):
        for x in range(n):
            fx, fy = x * scale / n, y * scale / n
            x0, y0 = int(fx) % scale, int(fy) % scale; x1, y1 = (x0 + 1) % scale, (y0 + 1) % scale
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0, x0] + (g[y0, x1] - g[y0, x0]) * tx; b = g[y1, x0] + (g[y1, x1] - g[y1, x0]) * tx
            out[y, x] = a + (b - a) * ty
    return out

def save(name, rgb):
    a = np.clip(rgb, 0, 255).astype(np.uint8)
    if a.shape[2] == 3: a = np.concatenate([a, np.full(a.shape[:2] + (1,), 255, np.uint8)], 2)
    Image.fromarray(a, "RGBA").save(os.path.join(BLOCK, name + ".png"))

# ------------------------------------------------------------------ packed trail dirt (top)
def packed_dirt_top():
    n = 16
    base = hsv(28, 0.52, 0.50)
    img = np.zeros((n, n, 3))
    lo, hi = noise(n, 4, 1), noise(n, 8, 2)
    for y in range(n):
        for x in range(n):
            t = (lo[y, x] - 0.5) * 0.55 + (hi[y, x] - 0.5) * 0.35
            # two tyre-polished lanes (slightly lighter, smoother) running along the trail
            if x in (4, 5, 11, 12): t = t * 0.45 + 0.16
            img[y, x] = shift(base, t)
    # crumbs: single darker / lighter grains
    for _ in range(14):
        x, y = rng.integers(0, n, 2)
        img[y, x] = shift(base, rng.choice([-0.45, 0.3]))
    # a hairline crack with occlusion
    for x, y in ((1, 9), (2, 9), (2, 10), (3, 11), (13, 2), (14, 3), (14, 4)):
        img[y, x] = shift(base, -0.6)
    # embedded pebbles: cool grey stones, lit top-left, contact shadow bottom-right
    stone = hsv(35, 0.14, 0.58)
    for (px, py, w, h) in ((7, 3, 2, 2), (2, 13, 2, 1), (13, 9, 2, 2), (9, 13, 1, 1), (0, 5, 1, 1)):
        for j in range(h):
            for i in range(w):
                t = 0.3 if (i == 0 and j == 0) else (-0.15 if (i == w - 1 or j == h - 1) else 0.05)
                img[(py + j) % n, (px + i) % n] = shift(stone, t)
        img[(py + h) % n, (px + w) % n] = shift(base, -0.55)          # shadow cast down-right
        img[(py + h) % n, (px + w - 1) % n] = shift(base, -0.35)
    save("packed_trail_dirt_top", img)

def packed_dirt_side():
    n = 16
    top, mid, deep = hsv(28, 0.52, 0.50), hsv(24, 0.55, 0.40), hsv(20, 0.50, 0.32)
    img = np.zeros((n, n, 3))
    nz = noise(n, 8, 5)
    for y in range(n):
        for x in range(n):
            wave = 1.5 * np.sin((x / n) * 2 * np.pi * 2 + 1.3)
            layer = top if y < 4 + wave * 0.4 else (mid if y < 10 + wave else deep)
            t = (nz[y, x] - 0.5) * 0.5
            if y == 0: t += 0.35                                      # lit rim of the packed crust
            img[y, x] = shift(layer, t)
    for _ in range(9):                                                # stones in the soil
        x, y = rng.integers(0, n), rng.integers(5, n)
        img[y, x] = shift(hsv(35, 0.14, 0.50), 0.1)
        img[(y + 1) % n, x] = shift(deep, -0.5)
    for x in (3, 4, 9, 13):                                           # root hairs
        y = rng.integers(6, 14)
        img[y, x] = shift(hsv(30, 0.35, 0.55), 0.1)
    save("packed_trail_dirt_side", img)

# ------------------------------------------------------------------ nailed deck boards
def trail_boards():
    n = 16
    wood = hsv(30, 0.55, 0.70)
    img = np.zeros((n, n, 3))
    tones = [0.0, -0.08, 0.06, -0.03]
    for y in range(n):
        board = y // 4
        for x in range(n):
            grain = 0.10 * np.sin((x + board * 5) * 0.9 + np.sin(y * 1.7 + board) * 1.4)
            t = tones[board] + grain + (rng.random() - 0.5) * 0.06
            if y % 4 == 0: t += 0.28                                  # lit top edge of each board
            if y % 4 == 3: t -= 0.55                                  # gap / occlusion under the board
            if y % 4 == 2: t -= 0.10
            img[y, x] = shift(wood, t)
    # board ends staggered, with end-grain shadow
    for board, x in ((0, 6), (1, 12), (2, 3), (3, 10)):
        for j in range(3):
            img[board * 4 + j, x] = shift(wood, -0.5)
            img[board * 4 + j, (x + 1) % n] = shift(wood, 0.18)
    # a knot
    img[9, 7], img[9, 8], img[10, 8] = shift(wood, -0.35), shift(wood, -0.45), shift(wood, -0.3)
    # nail heads beside every board end: steel, lit top-left, rust-dark shadow
    steel = hsv(220, 0.10, 0.70)
    for board, x in ((0, 6), (1, 12), (2, 3), (3, 10)):
        for nx in (x - 1, x + 2):
            img[board * 4 + 1, nx % n] = shift(steel, 0.35)
            img[board * 4 + 2, nx % n] = shift(hsv(20, 0.45, 0.35), -0.3)
    save("trail_boards", img)

# ------------------------------------------------------------------ re-shade the flat textures
def reshade(name, grain=0.06, strength=1.0):
    path = os.path.join(BLOCK, name + ".png")
    src = np.asarray(Image.open(path).convert("RGBA")).astype(float)
    H, W = src.shape[:2]
    rgb, a = src[..., :3], src[..., 3]
    out = rgb.copy()
    clustered = noise(max(H, W), 4, hash(name) % 1000)              # soft blotches, not per-pixel dither
    def same(y, x, yy, xx):
        """Same painted region: close in colour (the old textures carry a little noise inside a flat fill)."""
        if not (0 <= yy < H and 0 <= xx < W): return False
        return a[yy, xx] > 0 and np.abs(rgb[y, x] - rgb[yy, xx]).sum() < 60
    for y in range(H):
        for x in range(W):
            if a[y, x] == 0: continue
            t = (clustered[y % clustered.shape[0], x % clustered.shape[1]] - 0.5) * grain * 3
            if rgb[y, x].min() > 235: t -= 0.10                       # pure white -> warm off-white, room for highlights
            up, left = same(y, x, y - 1, x), same(y, x, y, x - 1)
            down, right = same(y, x, y + 1, x), same(y, x, y, x + 1)
            if not up: t += 0.30                                      # rim light on the upper edge of the region
            elif not left: t += 0.15
            if not down: t -= 0.32                                    # contact shadow along the lower edge
            elif not right: t -= 0.15
            # soft occlusion one pixel in from a boundary with a darker neighbour region
            for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= yy < H and 0 <= xx < W and a[yy, xx] > 0 and not same(y, x, yy, xx):
                    if rgb[yy, xx].sum() < rgb[y, x].sum(): t -= 0.06
            # a gentle top-left to bottom-right light gradient across the whole tile
            t += 0.10 * (0.5 - (x + y) / (W + H - 2)) * 2
            out[y, x] = shift(rgb[y, x], t * strength)
    Image.fromarray(np.concatenate([np.clip(out, 0, 255), a[..., None]], 2).astype(np.uint8), "RGBA").save(path)

if __name__ == "__main__":
    packed_dirt_top(); packed_dirt_side(); trail_boards()
    # only the truly flat fills; the stand materials already carry their own shading
    for name, g in (("airbag", 0.10), ("barrier_post", 0.06), ("cloth_barrier", 0.05)):
        reshade(name, g)
    print("textures written to", os.path.relpath(BLOCK, ROOT))
