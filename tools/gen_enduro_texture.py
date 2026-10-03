#!/usr/bin/env python3
"""Generate the enduro bike texture from the cube table in EnduroBikeModel.java.

Every `cube(bone, "name", "material", u, v, cx,cy,cz, sx,sy,sz, rx,ry,rz)` line of the Java model owns the
UV rectangle  [u, u+2*(sz+sx)+1) x [v, v+sz+sy+1)  (vanilla box layout + 1px bleed padding).  This script parses
those lines, checks that no two rectangles overlap, and paints each rectangle according to its material tag.

    python tools/gen_enduro_texture.py            # writes src/main/resources/assets/descentmtb/textures/entity/enduro_bike.png

Needs Pillow; falls back to a pure zlib/struct PNG writer if Pillow is missing.
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
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "descentmtb", "client", "model", "EnduroBikeModel.java")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "descentmtb", "textures", "entity", "enduro_bike.png")

# base colours per material tag (shared with preview_enduro.py)
MATERIALS = {
    "frame":     (16, 94, 100),     # matte deep teal
    "tire":      (24, 24, 26),
    "rim":       (62, 64, 70),
    "spoke":     (196, 199, 205),
    "hub":       (92, 95, 102),
    "stanchion": (214, 170, 52),    # Kashima gold
    "black":     (30, 30, 34),
    "spring":    (238, 124, 22),    # orange coil
    "saddle":    (26, 26, 28),
    "grip":      (20, 20, 22),
    "silver":    (178, 182, 189),
    "rotor":     (152, 156, 162),
    "chain":     (74, 70, 66),
    "pedal":     (34, 34, 38),
}

NUM = r"(-?\d+(?:\.\d+)?)f"
BONE_RE = re.compile(r'PartDefinition (\w+) = bone\((\w+), "(\w+)", ' + ", ".join([NUM] * 6) + r"\);")
CUBE_RE = re.compile(r'cube\((\w+), "(\w+)", "(\w+)", (\d+), (\d+), ' + ", ".join([NUM] * 9) + r"\);")
TEX_RE = re.compile(r"LayerDefinition\.create\(mesh, (\d+), (\d+)\)")


def parse_java(path=JAVA):
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
        cubes.append(dict(bone=var2name[m.group(1)], name=m.group(2), mat=m.group(3),
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


def make_canvas(tw, th):
    return [[(0, 0, 0, 0)] * tw for _ in range(th)]


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


def paint(cubes, tw, th, seed=7):
    rnd = random.Random(seed)
    img = make_canvas(tw, th)
    for c in cubes:
        base = MATERIALS[c["mat"]]
        u, v, w, h = region(c)
        fr = faces(c)
        # face lookup per pixel (padding pixels take the nearest face by clamping into the layout)
        for y in range(v, v + h):
            for x in range(u, u + w):
                face = None
                for key, (x0, y0, x1, y1) in fr.items():
                    if x0 - 0.001 <= x + 0.5 <= x1 + 0.001 and y0 - 0.001 <= y + 0.5 <= y1 + 0.001:
                        face = key
                        break
                k = 1.0
                if face == "-y": k = 1.10
                elif face == "+y": k = 0.78
                elif face in ("-x", "+x"): k = 0.94
                elif face == "+z": k = 0.88
                n = rnd.uniform(-1, 1)
                mat = c["mat"]
                col = base
                nz = 3.0
                if mat == "frame":
                    nz = 2.5
                    # slow vertical gradient + faint speckle (matte powder coat)
                    k *= 1.0 + 0.04 * math.sin((x + y) * 0.9)
                    if (x * 7 + y * 13) % 29 == 0: k *= 1.12
                elif mat == "tire":
                    nz = 2.0
                    if face == "+y":
                        # tread: alternating knob blocks and gaps
                        kx = (x - u) % 3
                        ky = (y - v) % 3
                        col = (46, 46, 49) if (kx != 2 and ky != 2) else (14, 14, 15)
                        k = 1.0
                    elif face in ("-x", "+x"):
                        col = (30, 30, 33) if (y - v) % 5 else (40, 40, 44)
                elif mat == "rim":
                    if face in ("-x", "+x", "-z", "+z"):
                        k *= 1.1 if (y - v) % 4 == 0 else 1.0
                elif mat == "stanchion":
                    nz = 2.0
                    # specular streak along the length (x within the face)
                    k *= 1.0 + 0.16 * math.cos((x - u) * 1.3)
                elif mat == "grip":
                    k *= 0.8 if ((x + y) % 3 == 0) else 1.15   # rubber ribs
                elif mat == "saddle":
                    if face == "-y" and (x - u) % 4 == 1: k *= 1.35
                elif mat == "spring":
                    k *= 1.0 + 0.12 * math.sin((x - u) * 0.9)
                elif mat == "chain":
                    k *= 1.25 if ((x + y) % 2 == 0) else 0.8
                elif mat in ("silver", "rotor", "spoke", "hub"):
                    nz = 2.0
                    k *= 1.0 + 0.08 * math.sin((x * 3 + y) * 0.7)
                r_, g_, b_ = (clampc(ch * k + n * nz) for ch in col)
                img[y][x] = (r_, g_, b_, 255)
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


def main():
    bones, cubes, (tw, th) = parse_java()
    unknown = {c["mat"] for c in cubes} - set(MATERIALS)
    if unknown:
        raise SystemExit(f"unknown material tags: {unknown}")
    check_overlaps(cubes, tw, th)
    img = paint(cubes, tw, th)
    # every referenced face texel must be painted
    holes = 0
    for c in cubes:
        for (x0, y0, x1, y1) in faces(c).values():
            for y in range(int(math.floor(y0)), int(math.ceil(y1)) + 1):
                for x in range(int(math.floor(x0)), int(math.ceil(x1)) + 1):
                    if 0 <= x < tw and 0 <= y < th and img[y][x][3] == 0:
                        holes += 1
    if holes:
        raise SystemExit(f"{holes} unpainted texels referenced by cubes")
    write_png(img, OUT)
    used = sum(region(c)[2] * region(c)[3] for c in cubes)
    print(f"wrote {OUT}  ({tw}x{th}, {len(cubes)} cubes, {100.0 * used / (tw * th):.1f}% of texels used, no overlaps, no holes)")


if __name__ == "__main__":
    main()
