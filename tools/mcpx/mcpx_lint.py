"""Lint textures (calibrated: each rule fires on <5% of real vanilla/Create textures) for the classic AI-pixel-art mistakes (each one found in real failed attempts).
usage: python mcpx_lint.py <png or dir> [...]"""
import glob, os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcpx_metrics import features, lum

def lint(path):
    im = Image.open(path); a = np.asarray(im.convert("RGBA")).astype(np.float32)
    H, W = a.shape[:2]; out = []
    kind = "item" if (a[..., 3] == 0).mean() > 0.25 else "block"
    if "/item/" in path.replace(os.sep, "/") and (H, W) != (16, 16) and not (W == 16 and H % 16 == 0):
        out.append(f"item is {W}x{H}: vanilla-style items are 16x16 (animated strips 16xN*16)")
    if a.shape[0] != 16 or a.shape[1] != 16: return out
    if (a[..., 3] > 0).sum() < 12: return out
    alpha = a[..., 3]
    if ((alpha > 0) & (alpha < 255)).any() and kind == "item":
        out.append("semi-transparent pixels in an item: vanilla items use alpha 0 or 255 only")
    f = features(a)
    if f["n_colors"] > 40 and f["orphan_ratio"] > 0.6:
        out.append(f"TV-static noise: {f['n_colors']} colours, {f['orphan_ratio']:.0%} isolated pixels -> paint indices from a ramp, not random RGB")
    if f["lum_std"] < 0.03 and f["n_colors"] > 10:
        out.append("many colours but almost no value contrast -> reads as flat grey mush; widen the ramp")
    if f["cluster_size"] > 8:
        out.append(f"flat fills (mean cluster {f['cluster_size']:.1f}px) -> add 2-4 shades of texture/shading")
    if f["n_colors"] <= 4 and kind == "block":
        out.append(f"only {f['n_colors']} colours -> vanilla blocks use ~8-16")
    if f["ramp_step"] > 0.11:
        out.append(f"big jumps between shades ({f['ramp_step']:.3f}) -> insert intermediate shades")
    if kind == "item":
        rgb = a[..., :3][alpha > 0]
        if (rgb.max(1) < 12).mean() > 0.05:
            out.append("pure-black pixels: outlines should be a dark TINT of the material, not #000")
        if f["outline_dark"] > 0.40:
            out.append(f"outline too dark vs body ({f['outline_dark']:.2f}); vanilla median ~0.19")
    if kind == "block" and f["border_delta"] > 0.12:
        out.append("bright border all around: looks like a framed tile/button; ok only for casings/frames")
    return out

if __name__ == "__main__":
    paths = []
    for p in sys.argv[1:]:
        paths += sorted(glob.glob(os.path.join(p, "**/*.png"), recursive=True)) if os.path.isdir(p) else [p]
    bad = 0
    for p in paths:
        issues = lint(p)
        if issues:
            bad += 1; print(os.path.relpath(p)); [print("   -", i) for i in issues]
    print(f"{bad}/{len(paths)} textures with issues")
