"""mcpx — toolkit for Minecraft-style 16x16 pixel-art textures.

Core idea: every texture is an INDEX MAP (ints 0..n-1) painted with a hand-tuned
COLOUR RAMP. You never paint raw RGB. Procedural noise is only for surfaces
(stone, wood grain, fabric); shapes (items, bolts, holes, frames) are placed by
hand with ASCII pixel maps or primitives.

    from mcpx import *
    r = Ramp("#8a8f94", n=6, spread=0.42, warm_hi=8, shadow_hue=270, cool_lo=10)
    idx = quantize(noise(seed=3, cells=(8, 4)), weights=[.06,.16,.3,.28,.14,.06])
    idx = cleanup(idx, max_orphans=0.30)
    save(r.paint(idx), "out/my_stone.png")
"""
import math, os, random
import numpy as np
from PIL import Image

# ----------------------------------------------------------------- colour ---
def hex2rgb(h):
    h = h.lstrip("#"); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

def _srgb_to_lin(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)

def _lin_to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055) * 255.0

def rgb2oklch(rgb):
    r, g, b = _srgb_to_lin(np.array(rgb, float))
    l = 0.4122214708*r + 0.5363325363*g + 0.0514459929*b
    m = 0.2119034982*r + 0.6806995451*g + 0.1073969566*b
    s = 0.0883024619*r + 0.2817188376*g + 0.6299787005*b
    l, m, s = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    L = 0.2104542553*l + 0.7936177850*m - 0.0040720468*s
    a = 1.9779984951*l - 2.4285922050*m + 0.4505937099*s
    bb = 0.0259040371*l + 0.7827717662*m - 0.8086757660*s
    return float(L), float(math.hypot(a, bb)), float(math.degrees(math.atan2(bb, a)) % 360)

def oklch2rgb(L, C, h):
    a, b = C * math.cos(math.radians(h)), C * math.sin(math.radians(h))
    l = (L + 0.3963377774*a + 0.2158037573*b) ** 3
    m = (L - 0.1055613458*a - 0.0638541728*b) ** 3
    s = (L - 0.0894841775*a - 1.2914855480*b) ** 3
    r = 4.0767416621*l - 3.3077115913*m + 0.2309699292*s
    g = -1.2684380046*l + 2.6097574011*m - 0.3413193965*s
    bl = -0.0041960863*l - 0.7034186147*m + 1.7076147010*s
    return tuple(int(round(v)) for v in _lin_to_srgb(np.array([r, g, bl])))

def _toward(h, target, amt):
    d = (target - h + 540) % 360 - 180
    return (h + max(-abs(amt), min(abs(amt), d))) % 360

class Ramp:
    """Hue-shifted colour ramp, index 0 = darkest.

    base       : mid colour (hex)
    n          : number of shades (4-8; vanilla blocks ~5-7, Create metals ~6-8)
    spread     : total OKLab lightness range (0.25 subtle stone .. 0.6 punchy metal)
    warm_hi    : degrees the brightest shade rotates toward yellow (hue 95)
    shadow_hue : hue the darkest shade rotates toward (270 cool/blue, 30 red-brown for warm metals)
    cool_lo    : degrees the darkest shade rotates toward shadow_hue
    hi_desat   : chroma multiplier at the top (vanilla ~0.75, Create brass ~0.6)
    lo_chroma  : chroma multiplier at the bottom (shadows slightly less saturated, 0.8-1.1)
    mid_bias   : shifts the ramp centre lighter(+)/darker(-), in L units
    """
    def __init__(self, base, n=6, spread=0.4, warm_hi=8, shadow_hue=270, cool_lo=8,
                 hi_desat=0.75, lo_chroma=0.95, mid_bias=0.0, chroma_mul=1.0):
        L0, C0, h0 = rgb2oklch(hex2rgb(base))
        C0 *= chroma_mul
        self.colors = []
        for i in range(n):
            t = (i / (n - 1)) * 2 - 1 if n > 1 else 0.0          # -1 .. 1
            L = L0 + mid_bias + t * spread / 2
            if t >= 0:
                C = C0 * (1 - (1 - hi_desat) * t ** 1.5)
                h = _toward(h0, 95, warm_hi * t)
            else:
                C = C0 * (1 + (lo_chroma - 1) * (-t))
                h = _toward(h0, shadow_hue, cool_lo * -t)
            self.colors.append(oklch2rgb(max(0.02, min(0.99, L)), max(0, C), h))
        self.n = n

    def __getitem__(self, i): return self.colors[max(0, min(self.n - 1, i))]
    def __len__(self): return self.n

    def paint(self, idx, alpha=None):
        idx = np.clip(np.asarray(idx), 0, self.n - 1)
        pal = np.array(self.colors, np.uint8)
        rgb = pal[idx]
        a = np.full(idx.shape, 255, np.uint8) if alpha is None else alpha.astype(np.uint8)
        return np.dstack([rgb, a])

    def swatch(self):
        return np.array(self.colors, np.uint8)[None, :, :]

def composite(*layers):
    """Paint several (Ramp, idx, mask) layers onto one RGBA canvas, later layers on top."""
    H, W = layers[0][1].shape
    out = np.zeros((H, W, 4), np.uint8)
    for ramp, idx, mask in layers:
        img = ramp.paint(idx)
        m = np.ones((H, W), bool) if mask is None else mask.astype(bool)
        out[m] = img[m]
    return out

# ------------------------------------------------------------------ noise ---
def noise(seed=0, size=16, cells=(4, 4), octaves=3, persistence=0.5, tile=True):
    """Tileable value noise in [0,1]. cells=(cx, cy): lattice cells across.
    cells=(8,3) -> horizontally streaky (stone strata, planks), (3,8) vertical (bark, casing wood)."""
    rng = np.random.default_rng(seed)
    out = np.zeros((size, size)); amp = 1.0; tot = 0.0
    cx, cy = cells
    for _ in range(octaves):
        g = rng.random((cy, cx))
        ys = np.arange(size) * cy / size; xs = np.arange(size) * cx / size
        y0 = np.floor(ys).astype(int); x0 = np.floor(xs).astype(int)
        fy = ys - y0; fx = xs - x0
        fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
        y1 = (y0 + 1) % cy if tile else np.minimum(y0 + 1, cy - 1)
        x1 = (x0 + 1) % cx if tile else np.minimum(x0 + 1, cx - 1)
        a = g[y0][:, x0]; b = g[y0][:, x1]; c = g[y1][:, x0]; d = g[y1][:, x1]
        top = a + (b - a) * fx[None, :]; bot = c + (d - c) * fx[None, :]
        out += amp * (top + (bot - top) * fy[:, None]); tot += amp
        amp *= persistence; cx *= 2; cy *= 2
    out /= tot
    return (out - out.min()) / (out.max() - out.min() + 1e-9)

def white(seed=0, size=16):
    return np.random.default_rng(seed).random((size, size))

def voronoi(seed=0, size=16, points=7, tile=True):
    """Returns (cell_id, dist_to_edge) — use for cobble, gravel, ore blobs."""
    rng = np.random.default_rng(seed)
    pts = rng.random((points, 2)) * size
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    ds = []
    for py, px in pts:
        dy = np.abs(yy - py); dx = np.abs(xx - px)
        if tile: dy = np.minimum(dy, size - dy); dx = np.minimum(dx, size - dx)
        ds.append(np.sqrt(dy * dy + dx * dx))
    ds = np.array(ds); order = np.sort(ds, axis=0)
    return np.argmin(ds, axis=0), order[1] - order[0]

def quantize(field, weights):
    """Map a float field to ramp indices so that index i covers `weights[i]` of the pixels."""
    w = np.clip(np.cumsum(weights) / np.sum(weights), 0, 1)
    qs = np.quantile(field, w[:-1])
    return np.searchsorted(qs, field, side="right")

def cleanup(idx, max_orphans=0.30, seed=0, keep=None):
    """Merge isolated single pixels into a neighbour until orphan ratio <= max_orphans.
    keep: boolean mask of pixels never to touch (hand-placed detail)."""
    idx = idx.copy(); H, W = idx.shape
    rng = random.Random(seed)
    def orphans():
        res = []
        for y in range(H):
            for x in range(W):
                if keep is not None and keep[y, x]: continue
                v = idx[y, x]
                if all(idx[(y+dy) % H, (x+dx) % W] != v for dy, dx in ((1,0),(-1,0),(0,1),(0,-1))):
                    res.append((y, x))
        return res
    o = orphans()
    while len(o) / (H * W) > max_orphans:
        rng.shuffle(o)
        for y, x in o[: max(1, len(o) // 4)]:
            nb = [idx[(y+dy) % H, (x+dx) % W] for dy, dx in ((1,0),(-1,0),(0,1),(0,-1))]
            idx[y, x] = min(nb, key=lambda v: abs(int(v) - int(idx[y, x])))
        o = orphans()
    return idx

def limit_steps(idx, max_step=1):
    """Soften: neighbouring pixels may differ by at most max_step ramp indices (removes harsh speckle)."""
    idx = idx.copy().astype(int)
    for _ in range(3):
        for axis in (0, 1):
            nb = np.roll(idx, 1, axis)
            d = idx - nb
            idx = np.where(d > max_step, nb + max_step, np.where(d < -max_step, nb - max_step, idx))
    return idx

# -------------------------------------------------------------- primitives ---
def bevel(idx, width=1, hi=+1, lo=-1, light="tl", corners=True):
    """Raised-edge bevel: top/left get +hi, bottom/right get lo (light from top-left)."""
    o = idx.copy(); H, W = idx.shape
    for k in range(width):
        o[k, k:W-k] += hi; o[k:H-k, k] += hi
        o[H-1-k, k:W-k] += lo; o[k:H-k, W-1-k] += lo
    if corners:   # the two ambiguous corners sit at the mid value
        o[H-1, 0] = idx[H-1, 0]; o[0, W-1] = idx[0, W-1]
    return o

def frame_mask(size=16, width=2):
    m = np.zeros((size, size), bool)
    m[:width, :] = m[-width:, :] = m[:, :width] = m[:, -width:] = True
    return m

def rect(idx, y0, x0, y1, x1, v):
    idx[y0:y1+1, x0:x1+1] = v; return idx

def rivet(idx, y, x, hi, lo):
    """2-px rivet/bolt: highlight top-left, shadow bottom-right."""
    idx[y, x] = hi; idx[y+1, x+1] = lo; return idx

def ring(size, cy, cx, r_out, r_in):
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    d = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
    return (d < r_out) & (d >= r_in)

def disc(size, cy, cx, r):
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    return np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2) < r

def planks(seed, size=16, boards=4, vertical=False, grain=(8, 2)):
    """Index-field helper for board textures: returns (field 0..1, seam mask)."""
    f = noise(seed, size, cells=(grain[1], grain[0]) if vertical else grain, octaves=2)
    seam = np.zeros((size, size), bool)
    step = size // boards
    rng = np.random.default_rng(seed)
    for b in range(boards):
        p = b * step + step - 1
        if vertical: seam[:, p] = True
        else: seam[p, :] = True
        # per-board brightness offset
        sl = (slice(None), slice(b*step, (b+1)*step)) if vertical else (slice(b*step, (b+1)*step), slice(None))
        f[sl] = f[sl] * 0.8 + rng.random() * 0.2
        # one end-joint per board (offset seams like real planks)
        j = rng.integers(2, size - 2)
        if vertical: seam[j, b*step:(b+1)*step-1] = True if rng.random() < .5 else seam[j, b*step:(b+1)*step-1]
        else: seam[b*step:(b+1)*step-1, j] = True
    return f, seam

# ------------------------------------------------------------ pixel maps ---
def from_ascii(art, legend):
    """Hand-placed pixels. art: 16 lines of 16 chars. legend: char -> (ramp_key, index) or None (transparent).
    Returns dict ramp_key -> (idx_array, mask) for composite()."""
    rows = [r for r in art.strip("\n").split("\n")]
    rows = [r.ljust(16, ".")[:16] for r in rows]
    H = len(rows)
    layers = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in legend or legend[ch] is None: continue
            key, v = legend[ch]
            if key not in layers: layers[key] = [np.zeros((H, 16), int), np.zeros((H, 16), bool)]
            layers[key][0][y, x] = v; layers[key][1][y, x] = True
    return layers

def shade_mask(mask, n, light=(-1, -1), base=None, rim=True):
    """Auto-shade a silhouette: lighter toward the light (top-left), darker away; outline = 0.
    Good starting point for items; ALWAYS hand-fix afterwards."""
    H, W = mask.shape
    from collections import deque
    dist = np.full((H, W), 99); q = deque()
    for y in range(H):
        for x in range(W):
            if mask[y, x] and any(not (0 <= y+dy < H and 0 <= x+dx < W) or not mask[y+dy, x+dx]
                                  for dy, dx in ((1,0),(-1,0),(0,1),(0,-1))):
                dist[y, x] = 0; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
            yy, xx = y+dy, x+dx
            if 0 <= yy < H and 0 <= xx < W and mask[yy, xx] and dist[yy, xx] > dist[y, x] + 1:
                dist[yy, xx] = dist[y, x] + 1; q.append((yy, xx))
    idx = np.zeros((H, W), int)
    ys, xs = np.nonzero(mask)
    cy, cx = ys.mean(), xs.mean()
    for y, x in zip(ys, xs):
        if dist[y, x] == 0 and rim: idx[y, x] = 0; continue
        lit = -((y - cy) * light[0] + (x - cx) * light[1]) / 8.0   # >0 toward light... sign handled below
        lit = ((cy - y) + (cx - x)) / 10.0
        v = (n - 1) * 0.55 + lit * (n - 1) * 0.5
        # pixels next to the outline on the lit side get the highlight
        if dist[y, x] == 1:
            if (y > 0 and not mask[y-1, x]) or (x > 0 and not mask[y, x-1]): v += 1.2
            if (y < H-1 and not mask[y+1, x]) or (x < W-1 and not mask[y, x+1]): v -= 1.2
        idx[y, x] = int(round(max(1, min(n - 1, v))))
    return idx

# ------------------------------------------------------------------- output ---
def save(img, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    Image.fromarray(np.asarray(img, np.uint8), "RGBA").save(path)
    return path

def upscale(img, k=8):
    im = img if isinstance(img, Image.Image) else Image.fromarray(np.asarray(img, np.uint8), "RGBA")
    return im.resize((im.width * k, im.height * k), Image.NEAREST)

def tile_preview(img, n=3, k=6):
    """n x n tiling — check seams and repetition."""
    a = np.asarray(img, np.uint8)
    return upscale(np.tile(a, (n, n, 1)), k)

def iso_block(top, side, front=None, k=6):
    """Tiny isometric cube render (MC-like face shading: top 100%, left 80%, right 60%)."""
    front = side if front is None else front
    def face(a, mul):
        a = np.asarray(a, np.float32).copy(); a[..., :3] *= mul
        return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA").resize((16*k, 16*k), Image.NEAREST)
    T, L, R = face(top, 1.0), face(side, 0.8), face(front, 0.6)
    s = 16 * k; W = 2 * s; H = 2 * s
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # top: map unit square -> rhombus
    def affine(img, a, b, c, d, e, f):  # output->input
        return img.transform((W, H), Image.AFFINE, (a, b, c, d, e, f), Image.NEAREST)
    # top face: corners (s,0) (2s,s/2) (s,s) (0,s/2)
    # input(u,v): x = s + (u - v), y = (u + v)/2  => u = y + (x - s)/2, v = y - (x - s)/2
    canvas.alpha_composite(affine(T, 0.5, 1, -s/2, -0.5, 1, s/2))
    # left face: x = u, y = s/2 + u/2 + v  => u = x, v = y - s/2 - x/2
    canvas.alpha_composite(affine(L, 1, 0, 0, -0.5, 1, -s/2))
    # right face: x = s + u, y = s - u/2 + v => u = x - s, v = y - s + (x - s)/2
    canvas.alpha_composite(affine(R, 1, 0, -s, 0.5, 1, -s - s/2))
    return canvas

def contact_sheet(paths_or_imgs, out, cols=8, k=8, labels=None, bg=(38, 38, 46)):
    from PIL import ImageDraw
    ims = [Image.open(p).convert("RGBA") if isinstance(p, str) else Image.fromarray(np.asarray(p, np.uint8), "RGBA")
           for p in paths_or_imgs]
    S = 16 * k; pad = 6; lab = 14 if labels else 0
    rows = (len(ims) + cols - 1) // cols
    canvas = Image.new("RGBA", (cols * (S + pad) + pad, rows * (S + pad + lab) + pad), bg + (255,))
    d = ImageDraw.Draw(canvas)
    for i, im in enumerate(ims):
        x = pad + (i % cols) * (S + pad); y = pad + (i // cols) * (S + pad + lab)
        canvas.alpha_composite(im.crop((0, 0, 16, 16)).resize((S, S), Image.NEAREST), (x, y))
        if labels: d.text((x, y + S + 1), labels[i][:18], fill=(200, 200, 210, 255))
    canvas.save(out); return out

# ------------------------------------------------------- richness helpers ---
def gauss_weights(n, mu=0.5, sigma=0.22):
    """Level distribution for quantize(): bell curve centred at mu (0=dark .. 1=light)."""
    x = np.linspace(0, 1, n)
    w = np.exp(-((x - mu) ** 2) / (2 * max(sigma, 1e-3) ** 2)) + 1e-6
    return w / w.sum()

def enrich(img, seed=0, frac=0.35, hue=5.0, dl=0.012, dc=0.12, cells=(4, 4), mask=None):
    """Hand-painted colour variety: real MC textures use 12-30 colours where a naive
    ramp gives 6. Nudges hue/chroma (and very slightly lightness) of low-frequency
    patches WITHOUT changing the value structure. Apply as the LAST step."""
    a = np.asarray(img, np.uint8).copy()
    H, W = a.shape[:2]
    n1 = noise(seed + 7001, H, cells=cells, octaves=2)
    n2 = noise(seed + 7002, H, cells=cells, octaves=1)
    sel = n1 > np.quantile(n1, 1 - frac)
    if mask is not None: sel &= mask
    sel &= a[..., 3] > 0
    cache = {}
    for y, x in zip(*np.nonzero(sel)):
        s = 1 if n2[y, x] > 0.5 else -1
        key = (tuple(a[y, x, :3]), s)
        if key not in cache:
            L, C, h = rgb2oklch(a[y, x, :3])
            cache[key] = oklch2rgb(L + s * dl, C * (1 + s * dc), (h + s * hue) % 360)
        a[y, x, :3] = cache[key]
    return a

def grow_clusters(seed, size=16, n=5, sizes=(3, 6), avoid=None, margin=1):
    """Ore-style clusters: n blobs of 3-6 orthogonally connected pixels. Returns label map (0=none)."""
    rng = np.random.default_rng(seed)
    lab = np.zeros((size, size), int)
    tries = 0
    k = 0
    while k < n and tries < 400:
        tries += 1
        y, x = rng.integers(margin, size - margin, 2)
        if lab[max(0, y-2):y+3, max(0, x-2):x+3].any(): continue
        if avoid is not None and avoid[y, x]: continue
        k += 1; target = rng.integers(sizes[0], sizes[1] + 1)
        cells = [(y, x)]; lab[y, x] = k
        while len(cells) < target:
            cy, cx = cells[rng.integers(len(cells))]
            dy, dx = [(1,0),(-1,0),(0,1),(0,-1)][rng.integers(4)]
            yy, xx = cy + dy, cx + dx
            if margin <= yy < size - margin and margin <= xx < size - margin and lab[yy, xx] == 0:
                lab[yy, xx] = k; cells.append((yy, xx))
    return lab

def lit_edges(region):
    """For a boolean region: (top_left_edge, bottom_right_edge) masks for bevel-style shading."""
    up = np.roll(region, 1, 0); left = np.roll(region, 1, 1)
    dn = np.roll(region, -1, 0); right = np.roll(region, -1, 1)
    tl = region & (~up | ~left)
    br = region & (~dn | ~right) & ~tl
    return tl, br

# ------------------------------------------------------------ ores / bases ---
def ore_overlay(ramp, seed=0, n=6, sizes=(3, 5), size=16):
    """Ore blobs on a TRANSPARENT background (compose onto stone/deepslate later).
    ramp: 5-7 shade ramp of the ore mineral. Each blob: 1-2 highlight px (top-left),
    mids, a darker bottom-right side, plus a 1px dark rim outside its bottom/right."""
    lab = grow_clusters(seed, size, n=n, sizes=sizes, margin=2)
    blob = lab > 0
    top = len(ramp) - 1
    idx = np.zeros((size, size), int); a = np.zeros((size, size), np.uint8)
    for k in range(1, lab.max() + 1):
        ys, xs = np.nonzero(lab == k)
        s = ys + xs; lo, hi = s.min(), s.max()
        for y, x in zip(ys, xs):
            t = (s[(ys == y) & (xs == x)][0] - lo) / max(1, hi - lo)
            idx[y, x] = int(round(top - 1 - t * (top - 2)))   # top-1 .. 1
        h = np.argmin(s); idx[ys[h], xs[h]] = top                 # one bright glint
    rim = (np.roll(blob, 1, 0) | np.roll(blob, 1, 1)) & ~blob
    idx[rim] = 0
    a[blob | rim] = 255
    return ramp.paint(idx, alpha=a)

def compose(base, *overlays):
    """Alpha-compose overlays (RGBA arrays or PNG paths) onto a base texture (array or path).
    Use it to put ore overlays on the player's own vanilla stone.png / deepslate.png at build time."""
    def arr(x): return np.asarray(Image.open(x).convert("RGBA")) if isinstance(x, str) else np.asarray(x, np.uint8)
    out = Image.fromarray(arr(base).copy(), "RGBA")
    for o in overlays: out.alpha_composite(Image.fromarray(arr(o), "RGBA"))
    return np.asarray(out)

def stone_like(seed=0, base="#7f7f7f", n=7, spread=0.20):
    """Neutral vanilla-stone-like base (low contrast, horizontally stretched blotches)."""
    r = Ramp(base, n, spread=spread, warm_hi=2, shadow_hue=260, cool_lo=4)
    f = 0.55 * noise(seed, cells=(3, 8), octaves=3) + 0.45 * white(seed)
    return r.paint(cleanup(quantize(f, gauss_weights(n, .5, .24)), 0.3))

# ------------------------------------------------------------------ items ---
def mask_from_ascii(art, on="#*-+"):
    """16 lines of 16 chars. Any char in `on` is opaque. Returns (mask, chars array)."""
    rows = [r.ljust(16, ".")[:16] for r in art.strip("\n").split("\n")]
    rows += ["." * 16] * (16 - len(rows))          # pad to 16 rows
    ch = np.array([list(r) for r in rows[:16]])
    return np.isin(ch, list(on)), ch

def shade_item(mask, n, ch=None, outline=True, rim_light=1, rim_dark=1):
    """Vanilla item shading on a silhouette:
       outermost pixels -> 0 (tinted dark outline, never pure black),
       pixels just inside on the top/left -> highlight, bottom/right -> shadow,
       body -> diagonal gradient (light from top-left).
       ASCII overrides: '*' = brightest, '+' = highlight-1, '-' = dark accent."""
    H, W = mask.shape
    pad = np.pad(mask, 1)
    edge = mask & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    inner = mask & ~edge if outline else mask
    pi = np.pad(inner, 1)
    tl = inner & (~pi[:-2, 1:-1] | ~pi[1:-1, :-2])
    br = inner & (~pi[2:, 1:-1] | ~pi[1:-1, 2:]) & ~tl
    ys, xs = np.nonzero(inner)
    idx = np.zeros((H, W), int)
    if len(ys):
        d = (ys + xs).astype(float); d = (d - d.min()) / max(1, d.max() - d.min())
        top, mid = n - 1, (n - 1) * 0.55
        for (y, x), t in zip(zip(ys, xs), d):
            idx[y, x] = int(round(mid + (0.5 - t) * (n - 1) * 0.35))
        idx[tl] = np.maximum(idx[tl] + rim_light, n - 2)
        idx[br] = np.minimum(idx[br] - rim_dark, max(2, (n - 1) // 3 + 1))
    idx[edge] = 0
    if ch is not None:
        idx[ch == "*"] = n - 1; idx[ch == "+"] = n - 2; idx[ch == "-"] = 1
    return np.clip(idx, 0, n - 1)

STICK_PATH = [(14, 2), (13, 3), (12, 4), (11, 5), (10, 6), (9, 7), (8, 8), (7, 9), (6, 10), (5, 11), (4, 12), (3, 13)]

def stick_handle(length=12, path=STICK_PATH):
    """Stand-in for the vanilla stick handle (2 px diagonal, light upper-left / dark lower-right side).
    In a real pack REPLACE this with extract_handle() from the player's own vanilla tool texture."""
    r = Ramp("#7a5a32", 4, spread=0.38, warm_hi=8, shadow_hue=25, cool_lo=10)
    idx = np.zeros((16, 16), int); m = np.zeros((16, 16), bool)
    for i, (y, x) in enumerate(path[:length]):
        m[y, x] = True; idx[y, x] = 2 if i % 3 else 3
        if x - 1 >= 0 and y + 1 < 16 and not m[y, x - 1]:
            pass
        m[y + 1, x] = True; idx[y + 1, x] = 1     # shadow side under the core
    y, x = path[0]; idx[y + 1, x] = 0; m[y + 1, x - 1] = True; idx[y + 1, x - 1] = 0
    return r, idx, m

def extract_handle(tool_png, stick_png):
    """Build-time helper: pull the shared handle pixels (all pixels whose colour occurs in
    stick.png) out of the player's own vanilla tool texture. Returns an RGBA layer."""
    a = np.asarray(Image.open(tool_png).convert("RGBA")).copy()
    s = np.asarray(Image.open(stick_png).convert("RGBA"))
    pal = {tuple(c) for c in s.reshape(-1, 4) if c[3] > 0}
    keep = np.array([[tuple(a[y, x]) in pal for x in range(a.shape[1])] for y in range(a.shape[0])])
    a[~keep] = 0
    return a

def mirror_diag(mask):
    """Mirror across the tool-handle axis (the anti-diagonal y + x = 15): draw ONE arm of a
    pickaxe/hoe head and get the other arm for free, perfectly symmetric about the handle."""
    m = np.asarray(mask)
    return m | m[::-1, ::-1].T

def item_ramp(base, n=7, outline_drop=0.06, **kw):
    """Item ramp: like Ramp but index 0 is a dedicated DARK TINTED OUTLINE colour
    (measured: vanilla outlines sit ~0.19 lum below the body MEAN, i.e. only a little below the darkest body shade; never pure black).
    Use spread ~0.4-0.45 for items (examples use 0.55*0.7)."""
    kw.setdefault("spread", 0.55)
    body = Ramp(base, n - 1, **kw)
    L, C, h = rgb2oklch(body[0])
    outline = oklch2rgb(max(0.08, L - outline_drop), C * 0.9, _toward(h, kw.get("shadow_hue", 270), 8))
    body.colors = [outline] + body.colors
    body.n = n
    return body
