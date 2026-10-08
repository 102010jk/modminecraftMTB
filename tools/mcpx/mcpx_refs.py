"""Reference indexer: learn style numbers + material palettes from the user's OWN game/mod files.

Never copy reference PNGs into outputs. Use them only to measure style and pull colour ramps.

usage:
  python mcpx_refs.py index  <out.json> <jar|zip|dir> [<jar|zip|dir> ...]
        -> per-namespace stats (blocks/items) + list of textures (for compare)
  python mcpx_refs.py palette <texture.png> [n]
        -> hue-ordered colour ramp (hex, dark->light) of one reference texture
  python mcpx_refs.py extract <jar|zip> <dest_dir>
        -> unpack only assets/*/textures and assets/*/models (handles nested jar-in-jar like Aeronautics)
"""
import io, json, os, sys, zipfile, glob
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcpx_metrics import features, FEATURES, is_full_block, is_item

def iter_pngs(src):
    """Yield (namespace, kind, name, rgba_array) for every 16x16 texture in a jar/zip/dir (recurses into nested jars)."""
    def handle(path, data):
        parts = path.split("/")
        if len(parts) < 5 or parts[0] != "assets" or parts[2] != "textures": return None
        ns, kind = parts[1], parts[3]
        try: a = np.asarray(Image.open(io.BytesIO(data)).convert("RGBA")).astype(np.float32)
        except Exception: return None
        return ns, kind, "/".join(parts[3:]), a
    if os.path.isdir(src):
        for p in glob.glob(os.path.join(src, "**/*.png"), recursive=True):
            rel = os.path.relpath(p, src).replace(os.sep, "/")
            i = rel.find("assets/")
            if i >= 0:
                r = handle(rel[i:], open(p, "rb").read())
                if r: yield r
        return
    def walk_zip(zf):
        for n in zf.namelist():
            if n.endswith(".jar"):
                yield from walk_zip(zipfile.ZipFile(io.BytesIO(zf.read(n))))
            elif n.endswith(".png"):
                r = handle(n, zf.read(n))
                if r: yield r
    yield from walk_zip(zipfile.ZipFile(src))

def index(out, sources):
    stats = {}
    for src in sources:
        for ns, kind, name, a in iter_pngs(src):
            if a.shape[:2] != (16, 16): continue
            cls = "block" if kind == "block" and is_full_block(a) else "item" if kind == "item" and is_item(a) else None
            if not cls: continue
            stats.setdefault(f"{ns}:{cls}", []).append([features(a)[k] for k in FEATURES])
    res = {}
    for k, rows in stats.items():
        M = np.array(rows)
        res[k] = {"n": len(rows), **{f: {"median": round(float(np.median(M[:, i])), 4),
                                           "p10": round(float(np.percentile(M[:, i], 10)), 4),
                                           "p90": round(float(np.percentile(M[:, i], 90)), 4)}
                                       for i, f in enumerate(FEATURES)}}
    json.dump(res, open(out, "w"), indent=1)
    for k, v in res.items(): print(k, v["n"])

def palette(path, n=None):
    a = np.asarray(Image.open(path).convert("RGBA"))
    px = a[a[..., 3] > 0][:, :3]
    cols, counts = np.unique(px, axis=0, return_counts=True)
    if n and len(cols) > n:                      # simple k-means down to n colours
        rng = np.random.default_rng(0)
        c = cols[rng.choice(len(cols), n, replace=False)].astype(float)
        for _ in range(20):
            d = ((cols[:, None, :] - c[None]) ** 2).sum(-1); lab = d.argmin(1)
            for j in range(n):
                if (lab == j).any(): c[j] = np.average(cols[lab == j], axis=0, weights=counts[lab == j])
        cols = np.round(c).astype(int)
    L = 0.299 * cols[:, 0] + 0.587 * cols[:, 1] + 0.114 * cols[:, 2]
    return ["#%02x%02x%02x" % tuple(c) for c in cols[np.argsort(L)]]

def extract(src, dest):
    def walk(zf, pre=""):
        for n in zf.namelist():
            if n.endswith(".jar"): walk(zipfile.ZipFile(io.BytesIO(zf.read(n))))
            elif n.startswith("assets/") and ("/textures/" in n or "/models/" in n) and not n.endswith("/"):
                p = os.path.join(dest, n); os.makedirs(os.path.dirname(p), exist_ok=True)
                open(p, "wb").write(zf.read(n))
    walk(zipfile.ZipFile(src))

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "index": index(sys.argv[2], sys.argv[3:])
    elif cmd == "palette": print(" ".join(palette(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else None)))
    elif cmd == "extract": extract(sys.argv[2], sys.argv[3])

def get(src, inner_path, dest):
    """Copy ONE file (e.g. assets/minecraft/textures/block/stone.png) out of the player's jar/zip
    for build-time composition (ore overlay on stone, new tool head on the vanilla stick handle)."""
    def walk(zf):
        if inner_path in zf.namelist(): return zf.read(inner_path)
        for n in zf.namelist():
            if n.endswith(".jar"):
                r = walk(zipfile.ZipFile(io.BytesIO(zf.read(n))))
                if r: return r
    data = walk(zipfile.ZipFile(src))
    if data is None: raise SystemExit(f"{inner_path} not found in {src}")
    os.makedirs(os.path.dirname(dest) or ".", exist_ok=True); open(dest, "wb").write(data); return dest

if __name__ == "__main__" and sys.argv[1] == "get":
    print(get(sys.argv[2], sys.argv[3], sys.argv[4]))
