"""Style metrics for 16x16 Minecraft-style textures. Compare generated sets to reference sets."""
import colorsys, glob, os, sys, json
import numpy as np
from PIL import Image

def load(path):
    im = Image.open(path).convert("RGBA")
    return np.asarray(im).astype(np.float32)

def lum(rgb):  # perceptual-ish luminance 0..1
    return (0.299*rgb[...,0] + 0.587*rgb[...,1] + 0.114*rgb[...,2]) / 255.0

def is_full_block(a):
    return a.shape[0] == 16 and a.shape[1] == 16 and (a[..., 3] == 255).all()

def is_item(a):
    return a.shape[0] == 16 and a.shape[1] == 16 and (a[..., 3] == 0).mean() > 0.25 and (a[..., 3] > 0).sum() >= 12

def _hsv(rgb):
    flat = rgb.reshape(-1, 3) / 255.0
    return np.array([colorsys.rgb_to_hsv(*p) for p in flat])

FEATURES = ["n_colors", "lum_mean", "lum_std", "lum_range", "sat_mean", "local_contrast",
            "orphan_ratio", "cluster_size", "hue_shift", "sat_shift", "border_delta",
            "ramp_step", "h_v_ratio", "outline_dark"]

def features(a):
    """a: HxWx4 float array. Uses only opaque pixels."""
    mask = a[..., 3] > 0
    rgb = a[..., :3]
    L = lum(rgb)
    px = rgb[mask]
    cols = np.unique(px.astype(np.uint8), axis=0)
    hsv = _hsv(px)
    Lm = L[mask]
    f = {}
    f["n_colors"] = len(cols)
    f["lum_mean"] = float(Lm.mean())
    f["lum_std"] = float(Lm.std())
    f["lum_range"] = float(np.percentile(Lm, 95) - np.percentile(Lm, 5))
    f["sat_mean"] = float(hsv[:, 1].mean())
    # local contrast: mean |dL| between opaque horizontal & vertical neighbours
    dh = np.abs(np.diff(L, axis=1))[mask[:, 1:] & mask[:, :-1]]
    dv = np.abs(np.diff(L, axis=0))[mask[1:, :] & mask[:-1, :]]
    f["local_contrast"] = float(np.concatenate([dh, dv]).mean()) if len(dh) + len(dv) else 0.0
    f["h_v_ratio"] = float((dh.mean() + 1e-4) / (dv.mean() + 1e-4)) if len(dh) and len(dv) else 1.0
    # orphan pixels: colour differs from all 4 neighbours (counts single-pixel noise)
    key = (rgb[..., 0].astype(np.int64) << 16) | (rgb[..., 1].astype(np.int64) << 8) | rgb[..., 2].astype(np.int64)
    H, W = key.shape
    orph = 0; tot = 0
    for y in range(H):
        for x in range(W):
            if not mask[y, x]: continue
            tot += 1
            same = False
            for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                yy, xx = y+dy, x+dx
                if 0 <= yy < H and 0 <= xx < W and mask[yy, xx] and key[yy, xx] == key[y, x]:
                    same = True; break
            orph += (not same)
    f["orphan_ratio"] = orph / max(tot, 1)
    # mean connected same-colour cluster size (4-conn)
    seen = np.zeros_like(mask); sizes = []
    for y in range(H):
        for x in range(W):
            if not mask[y, x] or seen[y, x]: continue
            stack = [(y, x)]; seen[y, x] = True; n = 0
            while stack:
                cy, cx = stack.pop(); n += 1
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    yy, xx = cy+dy, cx+dx
                    if 0 <= yy < H and 0 <= xx < W and mask[yy, xx] and not seen[yy, xx] and key[yy, xx] == key[cy, cx]:
                        seen[yy, xx] = True; stack.append((yy, xx))
            sizes.append(n)
    f["cluster_size"] = float(np.mean(sizes))
    # hue shift between darkest and brightest quartile (signed, wrapped, in degrees)
    q1, q3 = np.percentile(Lm, 25), np.percentile(Lm, 75)
    dark, bright = hsv[Lm <= q1], hsv[Lm >= q3]
    def circmean(h):
        ang = h * 2 * np.pi
        return np.arctan2(np.sin(ang).mean(), np.cos(ang).mean())
    if len(dark) and len(bright) and hsv[:, 1].mean() > 0.08:
        d = circmean(bright[:, 0]) - circmean(dark[:, 0])
        d = (d + np.pi) % (2 * np.pi) - np.pi
        f["hue_shift"] = float(np.degrees(d))
    else:
        f["hue_shift"] = 0.0
    f["sat_shift"] = float(bright[:, 1].mean() - dark[:, 1].mean()) if len(dark) and len(bright) else 0.0
    # border vs interior luminance (frames / bevels)
    if mask.all():
        b = np.ones((H, W), bool); b[1:-1, 1:-1] = False
        f["border_delta"] = float(L[b].mean() - L[~b].mean())
    else:
        # for items: outline pixels = opaque pixels touching transparency
        f["border_delta"] = 0.0
    # mean luminance gap between adjacent distinct palette levels
    ls = np.sort(np.unique(np.round(lum(cols.astype(np.float32)), 3)))
    f["ramp_step"] = float(np.diff(ls).mean()) if len(ls) > 1 else 0.0
    # items: how dark the outline is relative to interior
    if not mask.all():
        pad = np.pad(mask, 1)
        edge = mask & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
        inner = mask & ~edge
        f["outline_dark"] = float(L[inner].mean() - L[edge].mean()) if inner.any() and edge.any() else 0.0
    else:
        f["outline_dark"] = 0.0
    return f

def load_set(paths, kind="block"):
    out = []
    for p in paths:
        try:
            a = load(p)
        except Exception:
            continue
        if kind == "block" and is_full_block(a): out.append((p, a))
        if kind == "item" and is_item(a): out.append((p, a))
    return out

def feat_matrix(items):
    return np.array([[features(a)[k] for k in FEATURES] for _, a in items])

def compare(gen, ref, label=""):
    """Print per-feature z-scores of generated set vs reference distribution + classifier test."""
    G, R = feat_matrix(gen), feat_matrix(ref)
    mu, sd = R.mean(0), R.std(0) + 1e-6
    print(f"\n== {label}: {len(gen)} generated vs {len(ref)} reference")
    print(f"{'feature':16s} {'ref mean':>9s} {'ref sd':>8s} {'gen mean':>9s} {'z':>6s}")
    zs = {}
    for i, k in enumerate(FEATURES):
        z = (G[:, i].mean() - mu[i]) / sd[i]
        zs[k] = z
        flag = "  <-- off" if abs(z) > 1.0 else ""
        print(f"{k:16s} {mu[i]:9.3f} {sd[i]:8.3f} {G[:, i].mean():9.3f} {z:6.2f}{flag}")
    acc = classifier_test(G, R)
    print(f"classifier real-vs-generated balanced accuracy: {acc:.2f} (0.50 = indistinguishable)")
    return zs, acc

def classifier_test(G, R, seeds=10):
    """Balanced accuracy of a small random forest telling generated from real (subsampled to equal size)."""
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.model_selection import cross_val_score, StratifiedKFold
    rng = np.random.default_rng(0)
    accs = []
    n = min(len(G), len(R))
    for s in range(seeds):
        ri = rng.choice(len(R), n, replace=False)
        gi = rng.choice(len(G), n, replace=False)
        X = np.vstack([G[gi], R[ri]]); y = np.r_[np.ones(n), np.zeros(n)]
        k = min(5, n)
        cv = StratifiedKFold(k, shuffle=True, random_state=s)
        clf = RandomForestClassifier(200, max_depth=4, random_state=s)
        accs.append(cross_val_score(clf, X, y, cv=cv, scoring="balanced_accuracy").mean())
    return float(np.mean(accs))

def noise_floor(ref, n, rounds=12):
    """Classifier accuracy for n REAL textures vs the rest: what 'indistinguishable' scores at this sample size."""
    M = feat_matrix(ref); rng = np.random.default_rng(1); accs = []
    for _ in range(rounds):
        idx = rng.permutation(len(M)); accs.append(classifier_test(M[idx[:n]], M[idx[n:]], seeds=4))
    return float(np.mean(accs)), float(np.percentile(accs, 90))

if __name__ == "__main__":
    # usage: python mcpx_metrics.py <generated_dir> <reference_dir> [block|item] [name_regex]
    #   name_regex: category-match the reference set, e.g. "(stone|andesite|tuff|deepslate)" or "casing"
    import re
    gen_dir, ref_dir = sys.argv[1], sys.argv[2]
    kind = sys.argv[3] if len(sys.argv) > 3 else "block"
    pat = re.compile(sys.argv[4]) if len(sys.argv) > 4 else None
    gen = load_set(sorted(glob.glob(os.path.join(gen_dir, "**/*.png"), recursive=True)), kind)
    ref = load_set(sorted(glob.glob(os.path.join(ref_dir, "**/*.png"), recursive=True)), kind)
    if pat: ref = [t for t in ref if pat.search(os.path.basename(t[0]))]
    compare(gen, ref, f"{kind}")
    m, p90 = noise_floor(ref, len(gen))
    print(f"noise floor at n={len(gen)}: real-vs-real mean {m:.2f}, 90th pct {p90:.2f} -> aim for <= {p90:.2f}")
