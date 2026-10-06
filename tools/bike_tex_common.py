#!/usr/bin/env python3
"""Shared core of tools/gen_enduro_texture.py and tools/gen_hardtail_texture.py.

Every `cube(bone, "name", "material", u, v, cx,cy,cz, sx,sy,sz, rx,ry,rz)` line of a bike model owns the UV rectangle
[u, u+2*(sz+sx)+1) x [v, v+sz+sy+1)  (vanilla box layout + 1px bleed padding).  The scripts parse those lines, check
that no two rectangles overlap and paint each rectangle according to its material tag.

CUSTOMISATION (see client/model/BikeMat.java): the renderer tints materials per bike build by multiplying the vertex
colour with the texture, so the TINTABLE materials here are painted as light, shaded greys; the shading (face
brightness, specular streaks, rubber ribs ...) is what you see through the colour.  FIXED materials keep their colour.

Cube-name conventions (parsed by PartTable.java as well):
  name__codes   the cube is only drawn for frame shapes whose code letter is in `codes`
                (enduro: c = CLASSIC, h = HIGH_PIVOT, l = LOW_SLUNG;  hardtail: c = CLASSIC, s = STRAIGHT, v = CURVED)
  acc_<kind>_*  accessory part, only drawn when the build asks for it (kinds: ding mini classic horn duck flight rlight)

Output files per bike (entity texture folder):  <bike>.png  (gloss frame + everything)  and  <bike>_matte.png,
<bike>_metallic.png, <bike>_raw.png  (ONLY the frame / accent cubes, repainted with that finish).
"""
import math
import os
import random
import re
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODEL_DIR = os.path.join(ROOT, "src", "main", "java", "com", "descentmtb", "client", "model")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "descentmtb", "textures", "entity")

# tags the Java side tints per build (everything else is painted in its own colour)
TINTABLE = {"frame", "accent", "tire", "rim", "hub", "stanchion", "lower", "shockbody", "aircan", "shockres", "spring",
            "bars", "grip", "saddle", "pedal", "brake", "lens"}

# colours of the FIXED materials, and the default-build colour of the tintable ones (only used by the previews)
COMMON = {
    "spoke": (196, 199, 205), "black": (30, 30, 34), "silver": (178, 182, 189), "rotor": (152, 156, 162),
    "chain": (74, 70, 66), "tread": (24, 24, 26), "chrome": (214, 218, 224), "duck": (250, 208, 46),
    "beak": (240, 118, 22), "eye": (14, 14, 16), "horn": (206, 44, 40), "lightbody": (44, 46, 50),
    "lens": (244, 242, 232), "accent": (242, 240, 234), "aircan": (22, 23, 26), "shockres": (22, 23, 26),
    "lower": (22, 23, 26), "brake": (42, 44, 48), "bars": (38, 39, 43), "pedal": (30, 30, 34),
    "grip": (26, 26, 28), "saddle": (26, 26, 28), "tire": (28, 28, 31), "hub": (30, 30, 34), "rim": (30, 30, 34),
    "stanchion": (201, 162, 75), "shockbody": (201, 162, 75), "spring": (232, 101, 42), "frame": (31, 138, 143),
}

NUM = r"(-?\d+(?:\.\d+)?)f"
BONE_RE = re.compile(r'PartDefinition (\w+) = bone\((\w+), "(\w+)", ' + ", ".join([NUM] * 6) + r"\);")
CUBE_RE = re.compile(r'cube\((\w+), "(\w+)", "(\w+)", (\d+), (\d+), ' + ", ".join([NUM] * 9) + r"\);")
TEX_RE = re.compile(r"LayerDefinition\.create\(mesh, (\d+), (\d+)\)")


def parse_java(path):
    """-> (bones dict name -> dict(parent,pivot,rot_deg), cubes list, (texW, texH))"""
    src = open(path, encoding="utf-8").read()
    bones = {"root": dict(parent=None, pivot=(0, 0, 0), rot=(0, 0, 0), var="root")}
    var2name = {"root": "root"}
    for m in BONE_RE.finditer(src):
        var, par, name = m.group(1), m.group(2), m.group(3)
        vals = [float(m.group(i)) for i in range(4, 10)]
        bones[name] = dict(parent=var2name[par], pivot=tuple(vals[:3]), rot=tuple(vals[3:]), var=var)
        var2name[var] = name
    cubes = []
    for m in CUBE_RE.finditer(src):
        v = [float(m.group(i)) for i in range(6, 15)]
        full = m.group(2)
        base, _, codes = full.partition("__")
        cubes.append(dict(bone=var2name[m.group(1)], name=full, base=base, codes=codes, mat=m.group(3),
                          u=int(m.group(4)), v=int(m.group(5)),
                          c=tuple(v[0:3]), s=tuple(v[3:6]), r=tuple(v[6:9])))
    tm = TEX_RE.search(src)
    return bones, cubes, (int(tm.group(1)), int(tm.group(2)))


def region(c):
    sx, sy, sz = c["s"]
    w = int(math.ceil(round(2 * (sz + sx), 4))) + 1
    h = int(math.ceil(round(sz + sy, 4))) + 1
    return c["u"], c["v"], w, h


def faces(c):
    """vanilla box-UV face rectangles (x0,y0,x1,y1 floats) keyed by model-space face normal"""
    sx, sy, sz = c["s"]
    u, v = c["u"], c["v"]
    d, w, h = sz, sx, sy
    return {
        "-y": (u + d, v, u + d + w, v + d),
        "+y": (u + d + w, v, u + d + 2 * w, v + d),
        "-x": (u, v + d, u + d, v + d + h),
        "-z": (u + d, v + d, u + d + w, v + d + h),
        "+x": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "+z": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def clampc(v):
    return max(0, min(255, int(round(v))))


def shade(col, k):
    return tuple(clampc(ch * k) for ch in col)


def check_overlaps(cubes, tw, th):
    owner = {}
    for i, c in enumerate(cubes):
        u, v, w, h = region(c)
        if u < 0 or v < 0 or u + w > tw or v + h > th:
            raise SystemExit(f"cube {c['bone']}/{c['name']} region {u},{v},{w},{h} leaves the {tw}x{th} texture")
        for y in range(v, v + h):
            for x in range(u, u + w):
                if (x, y) in owner:
                    j = owner[(x, y)]
                    raise SystemExit(f"UV overlap: {cubes[j]['bone']}/{cubes[j]['name']} and "
                                     f"{c['bone']}/{c['name']} at pixel {x},{y}")
                owner[(x, y)] = i


NEUTRAL = 236     # albedo of the tintable materials: tint colour * ~0.93 (top faces clamp at 255)


def face_of(c, x, y):
    for key, (x0, y0, x1, y1) in faces(c).items():
        if x0 - 0.001 <= x + 0.5 <= x1 + 0.001 and y0 - 0.001 <= y + 0.5 <= y1 + 0.001:
            return key
    return None


def paint_cube(c, x, y, rnd, finish, fixed, bike):
    """-> (r,g,b) of texel x,y of cube c.  fixed = per-bike dict of fixed-material colours."""
    u, v, _, _ = region(c)
    face = face_of(c, x, y)
    k = 1.0
    if face == "-y": k = 1.10
    elif face == "+y": k = 0.78
    elif face in ("-x", "+x"): k = 0.94
    elif face == "+z": k = 0.88
    n = rnd.uniform(-1, 1)
    mat = c["mat"]
    nz = 3.0
    grey = (NEUTRAL, NEUTRAL, NEUTRAL)
    col = fixed.get(mat, COMMON.get(mat, (128, 128, 128)))
    if mat in TINTABLE:
        col = grey
    if mat in ("frame", "accent"):
        nz = 2.0
        if finish == "gloss":
            # clear coat: bright specular streak on the outer faces + faint speckle
            if face in ("-y", "-x", "+x"):
                k *= 1.0 + 0.09 * math.cos((x - u) * 1.1 + (y - v) * 0.4)
            if face == "-y": k *= 1.10
            if (x * 7 + y * 13) % 31 == 0: k *= 1.14
        elif finish == "matte":
            k = 1.0 + (k - 1.0) * 0.45          # flat powder coat: little face contrast, no streak
            k *= 0.97
            nz = 3.2
            if (x * 5 + y * 11) % 17 == 0: k *= 1.04
        elif finish == "metallic":
            nz = 0.0
            k = 1.0 + (k - 1.0) * 1.2
            k *= 1.0 + 0.16 * math.cos((x - u) * 1.3 + (y - v) * 0.5)
            k *= 0.88 + 0.26 * ((x * 73856093 ^ y * 19349663) % 100) / 100.0     # metal flake
            if face == "-y": k *= 1.08
        elif finish == "raw":
            # brushed aluminium: fixed grey, long horizontal grain; the build colour is NOT applied (tint = white)
            base = 176 + 22 * math.sin((y * 3.7 + (x // 5) * 1.3)) + rnd.uniform(-6, 6)
            col = (clampc(base), clampc(base + 3), clampc(base + 8))
            nz = 1.0
    elif mat == "tire":
        nz = 2.0
        if face in ("-x", "+x"):
            # sidewall (this is what the wall colour shows on): faint inner ring
            k = 1.0 if (y - v) % 5 else 0.9
            col = (250, 250, 250)
        else:
            # tread / inner / end faces stay dark whatever the wall colour is
            if face == "+y":
                kx, ky = (x - u) % 3, (y - v) % 3
                col = (96, 96, 98) if (kx != 2 and ky != 2) else (34, 34, 36)
            else:
                col = (70, 70, 72)
            k = 1.0
    elif mat == "tread":
        nz = 2.0
        if face == "+y":
            kx, ky = (x - u) % 3, (y - v) % 3
            col = (46, 46, 49) if (kx != 2 and ky != 2) else (16, 16, 17)
            k = 1.0
    elif mat == "rim":
        if face in ("-x", "+x", "-z", "+z"):
            k *= 1.12 if (y - v) % 4 == 0 else 1.0
    elif mat in ("stanchion", "lower", "shockbody", "aircan"):
        nz = 2.0
        k *= 1.0 + 0.14 * math.cos((x - u) * 1.3)      # specular streak along the length
        if mat == "shockres" or mat == "lower":
            k = max(k, 0.8)
    elif mat == "grip":
        k *= 0.8 if ((x + y) % 3 == 0) else 1.1          # rubber ribs
    elif mat == "saddle":
        if face == "-y" and (x - u) % 4 == 1: k *= 1.3
    elif mat == "spring":
        k *= 1.0 + 0.12 * math.sin((x - u) * 0.9)
    elif mat == "chain":
        k *= 1.25 if ((x + y) % 2 == 0) else 0.8
    elif mat == "pedal":
        k *= 1.12 if ((x + y) % 3 == 0) else 1.0
    elif mat in ("silver", "rotor", "spoke", "hub", "chrome"):
        nz = 2.0
        k *= 1.0 + 0.08 * math.sin((x * 3 + y) * 0.7)
    elif mat == "lens":
        k = 1.0
        nz = 0.0
        col = (255, 255, 255)
    return tuple(clampc(ch * k + n * nz) for ch in col)


def paint(cubes, tw, th, finish="gloss", fixed=None, seed=7, only_frame=False):
    fixed = fixed or {}
    rnd = random.Random(seed)
    img = [[(0, 0, 0, 0)] * tw for _ in range(th)]
    for c in cubes:
        if only_frame and c["mat"] not in ("frame", "accent"):
            continue
        u, v, w, h = region(c)
        for y in range(v, v + h):
            for x in range(u, u + w):
                r, g, b = paint_cube(c, x, y, rnd, finish, fixed, None)
                img[y][x] = (r, g, b, 255)
    return img


def write_png(img, path):
    th, tw = len(img), len(img[0])
    os.makedirs(os.path.dirname(path), exist_ok=True)
    try:
        from PIL import Image
        im = Image.new("RGBA", (tw, th))
        im.putdata([px for row in img for px in row])
        im.save(path)
        return
    except ImportError:
        pass
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in img)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", tw, th, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(png)


def run(java, name, fixed, seed):
    """parse, validate, write <name>.png and the three finish variants"""
    bones, cubes, (tw, th) = parse_java(java)
    unknown = {c["mat"] for c in cubes} - TINTABLE - set(COMMON) - set(fixed)
    if unknown:
        raise SystemExit(f"unknown material tags: {unknown}")
    check_overlaps(cubes, tw, th)
    img = paint(cubes, tw, th, "gloss", fixed, seed)
    holes = 0
    for c in cubes:
        for (x0, y0, x1, y1) in faces(c).values():
            for y in range(int(math.floor(y0)), int(math.ceil(y1)) + 1):
                for x in range(int(math.floor(x0)), int(math.ceil(x1)) + 1):
                    if 0 <= x < tw and 0 <= y < th and img[y][x][3] == 0:
                        holes += 1
    if holes:
        raise SystemExit(f"{holes} unpainted texels referenced by cubes")
    write_png(img, os.path.join(TEX_DIR, name + ".png"))
    for finish in ("matte", "metallic", "raw"):
        write_png(paint(cubes, tw, th, finish, fixed, seed + 1, only_frame=True), os.path.join(TEX_DIR, f"{name}_{finish}.png"))
    used = sum(region(c)[2] * region(c)[3] for c in cubes)
    print(f"wrote {name}.png (+ matte/metallic/raw)  ({tw}x{th}, {len(cubes)} cubes, "
          f"{100.0 * used / (tw * th):.1f}% of texels used, no overlaps, no holes)")
