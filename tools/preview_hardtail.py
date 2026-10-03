#!/usr/bin/env python3
"""Orthographic preview of the hardtail bike model (side / top / front) straight from HardtailBikeModel.java.

The bone/cube table and the anchors are parsed from the Java source (single source of truth);
`pose()` below is a line-by-line port of HardtailBikeModel.setupPose().  Cube colours are sampled from the
generated texture (run tools/gen_hardtail_texture.py first), so what you see is what the texture paints.
Renders to tools/preview/hardtail_*.png, including a side-by-side with the enduro if its files exist.

    python tools/gen_hardtail_texture.py
    python tools/preview_hardtail.py
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_hardtail_texture import JAVA, MATERIALS, OUT as TEX, faces, parse_java  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")
PX = 16.0


# ------------------------------------------------------------------ java constants
def java_consts():
    src = open(JAVA, encoding="utf-8").read()
    k = {m.group(1): float(m.group(2)) for m in re.finditer(r"\b([A-Z][A-Z0-9_]+) = (-?\d+(?:\.\d+)?)f", src)}
    anchors = {}
    for m in re.finditer(r"(GRIP_LEFT|GRIP_RIGHT|SADDLE_TOP|PEDAL_AXIS|STEER_PIVOT_PX|STEER_AXIS_DOWN) = new Vector3f\(([^)]*)\)", src):
        vals = []
        for v in m.group(2).split(","):
            v = v.strip()
            neg = v.startswith("-")
            v = v.lstrip("-")
            try:
                x = float(v[:-1]) if v.endswith("f") else k[v]
            except (ValueError, KeyError):
                x = k.get(v, 0.0)
            vals.append(-x if neg else x)
        anchors[m.group(1)] = tuple(vals)
    return k, anchors


# ------------------------------------------------------------------ math helpers (pure python)
def rot_zyx(rx, ry, rz):
    """R = Rz * Ry * Rx  (vanilla Quaternionf.rotationZYX), rows"""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    return [
        [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
        [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
        [-sy, cy * sx, cy * cx],
    ]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


# ------------------------------------------------------------------ pose port of setupPose()
def pose(bones, K, steer, fork_m, rear_m, fwheel, rwheel, crank):
    """returns dict bone -> dict(x,y,z,rx,ry,rz,zs) = the ModelPart fields after setupPose()"""
    P = {}
    for n, b in bones.items():
        px, py, pz = b["pivot"]
        rx, ry, rz = (math.radians(a) for a in b["rot"])
        P[n] = dict(x=px, y=py, z=pz, rx=rx, ry=ry, rz=rz, zs=1.0)
    fork = clamp(fork_m, 0, K["FORK_TRAVEL_M"]) * PX
    P["steer"]["ry"] = steer
    P["fork_lower"]["y"] = -fork
    P["front_wheel"]["rx"] = fwheel
    P["rear_wheel"]["rx"] = rwheel
    P["cranks"]["rx"] = crank
    P["pedal_left"]["rx"] = -crank
    P["pedal_right"]["rx"] = -crank
    return P


# ------------------------------------------------------------------ forward kinematics
def world_mats(bones, P):
    W = {}

    def get(n):
        if n in W: return W[n]
        b = bones[n]
        if b["parent"] is None:
            W[n] = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))
            return W[n]
        pr, pt = get(b["parent"])
        p = P[n]
        R = rot_zyx(p["rx"], p["ry"], p["rz"])
        t = tuple(pt[i] + mv(pr, (p["x"], p["y"], p["z"]))[i] for i in range(3))
        Rs = [[R[i][0], R[i][1], R[i][2] * p["zs"]] for i in range(3)]       # scale local z
        W[n] = (mm(pr, Rs), t)
        return W[n]

    for n in bones:
        get(n)
    return W


def world_cubes(bones, cubes, P):
    W = world_mats(bones, P)
    out = []
    for c in cubes:
        R, t = W[c["bone"]]
        cr = rot_zyx(*(math.radians(a) for a in c["r"]))
        M = mm(R, cr)
        ct = tuple(t[i] + mv(R, c["c"])[i] for i in range(3))
        hx, hy, hz = (v / 2 for v in c["s"])
        corners = []
        for sx in (-1, 1):
            for sy in (-1, 1):
                for sz in (-1, 1):
                    p = mv(M, (sx * hx, sy * hy, sz * hz))
                    # to y-up world: (x, h, z)
                    corners.append((ct[0] + p[0], -(ct[1] + p[1]), ct[2] + p[2]))
        ctr = (ct[0], -ct[1], ct[2])
        out.append(dict(c=c, corners=corners, ctr=ctr))
    return out


# ------------------------------------------------------------------ rasterising
def hull(pts):
    pts = sorted(set(pts))
    if len(pts) <= 2: return pts

    def cross(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0: lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0: up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


VIEWS = {
    # name: (screen_x(wx,wh,wz), screen_y_up(wx,wh,wz) , depth (bigger = nearer = drawn later))
    "side": (lambda x, h, z: -z, lambda x, h, z: h, lambda x, h, z: x),
    "top": (lambda x, h, z: -x, lambda x, h, z: -z, lambda x, h, z: h),
    "front": (lambda x, h, z: x, lambda x, h, z: h, lambda x, h, z: -z),
}
VIEW_FACE = {"side": "+x", "top": "-y", "front": "-z"}      # model-space face that points at the camera


def human(d, tx, ty, cx, view):
    """1.8 m human silhouette for scale, centred on horizontal coordinate cx (metres)."""
    col = (168, 174, 192, 255)
    ol = (60, 62, 75, 255)
    X, Y = tx, ty

    def poly(pts):
        d.polygon([(X(a), Y(b)) for a, b in pts], fill=col, outline=ol)

    d.ellipse([X(cx - 0.095), Y(1.80), X(cx + 0.095), Y(1.61)], fill=col, outline=ol)         # head
    if view == "side":
        poly([(cx - 0.05, 1.62), (cx + 0.05, 1.62), (cx + 0.06, 1.54), (cx - 0.06, 1.54)])     # neck
        poly([(cx - 0.13, 1.54), (cx + 0.13, 1.54), (cx + 0.11, 0.95), (cx - 0.11, 0.95)])     # torso
        poly([(cx - 0.11, 0.95), (cx + 0.11, 0.95), (cx + 0.08, 0.50), (cx + 0.07, 0.04),
              (cx - 0.07, 0.04), (cx - 0.08, 0.50)])                                           # legs
        poly([(cx - 0.05, 1.50), (cx + 0.05, 1.50), (cx + 0.04, 0.80), (cx - 0.04, 0.80)])     # arm
        poly([(cx - 0.07, 0.04), (cx + 0.2, 0.04), (cx + 0.2, 0.0), (cx - 0.07, 0.0)])         # foot
    else:
        poly([(cx - 0.05, 1.62), (cx + 0.05, 1.62), (cx + 0.06, 1.54), (cx - 0.06, 1.54)])
        poly([(cx - 0.23, 1.54), (cx + 0.23, 1.54), (cx + 0.19, 0.95), (cx - 0.19, 0.95)])
        poly([(cx - 0.19, 0.95), (cx - 0.01, 0.95), (cx - 0.04, 0.0), (cx - 0.17, 0.0)])
        poly([(cx + 0.01, 0.95), (cx + 0.19, 0.95), (cx + 0.17, 0.0), (cx + 0.04, 0.0)])
        poly([(cx - 0.34, 1.50), (cx - 0.23, 1.50), (cx - 0.25, 0.80), (cx - 0.33, 0.80)])
        poly([(cx + 0.23, 1.50), (cx + 0.34, 1.50), (cx + 0.33, 0.80), (cx + 0.25, 0.80)])


_TEX = None


def tex_image():
    global _TEX
    if _TEX is None:
        _TEX = Image.open(TEX).convert("RGB")
    return _TEX


def face_colour(c, view):
    """mean texture colour of the face of cube c that points at the camera"""
    x0, y0, x1, y1 = faces(c)[VIEW_FACE[view]]
    im = tex_image()
    xs0, xs1 = int(math.floor(x0 + 0.001)), max(int(math.floor(x0 + 0.001)) + 1, int(math.ceil(x1 - 0.001)))
    ys0, ys1 = int(math.floor(y0 + 0.001)), max(int(math.floor(y0 + 0.001)) + 1, int(math.ceil(y1 - 0.001)))
    acc = [0, 0, 0]
    n = 0
    for yy in range(ys0, ys1):
        for xx in range(xs0, xs1):
            if 0 <= xx < im.width and 0 <= yy < im.height:
                p = im.getpixel((xx, yy))
                acc = [acc[i] + p[i] for i in range(3)]
                n += 1
    return tuple(a // max(n, 1) for a in acc) if n else MATERIALS[c["mat"]]


def render(groups, view, W, H, scale, origin, supers=2, title="", anchors=None, human_m=None, axis=None):
    """groups: list of (cubes_w, colour_fn(c, view) -> rgb, dx_metres)"""
    sxf, syf, dpf = VIEWS[view]
    w, h = W * supers, H * supers
    img = Image.new("RGBA", (w, h), (236, 238, 242, 255))
    d = ImageDraw.Draw(img, "RGBA")
    S = scale * PX * supers                                     # image px per metre
    ox, oy = origin[0] * supers, origin[1] * supers

    def tx(a): return ox + a * S
    def ty(b): return oy - b * S

    for i in range(-40, 41):
        d.line([tx(i * 0.25), 0, tx(i * 0.25), h], fill=(214, 216, 222, 255), width=1)
        if view == "top" or i >= 0:
            d.line([0, ty(i * 0.25), w, ty(i * 0.25)], fill=(214, 216, 222, 255), width=1)
    if view != "top":
        d.line([0, ty(0), w, ty(0)], fill=(90, 90, 100, 255), width=2 * supers)
    if human_m is not None and view != "top":
        human(d, tx, ty, human_m, view)
    items = []
    for cubes_w, colfn, dx in groups:
        for cw in cubes_w:
            pts2 = [(tx(sxf(x, hh, z) / PX + dx), ty(syf(x, hh, z) / PX)) for (x, hh, z) in cw["corners"]]
            items.append((max(dpf(*p) for p in cw["corners"]), cw, pts2, colfn))
    items.sort(key=lambda t: t[0])
    dmin = min(i[0] for i in items); dmax = max(i[0] for i in items)
    for dep, cw, pts2, colfn in items:
        base = colfn(cw["c"], view)
        k = 0.82 + 0.3 * (dep - dmin) / (dmax - dmin + 1e-6)
        col = tuple(min(255, int(ch * k)) for ch in base)
        hp = hull(pts2)
        if len(hp) >= 3:
            d.polygon(hp, fill=col + (255,), outline=tuple(int(ch * 0.5) for ch in col) + (255,))
        else:
            d.line(pts2, fill=col + (255,))
    f = ImageFont.load_default()
    if axis is not None and view == "side":                      # steering axis (blue): pivot + direction in px, y down
        (py, pz), (dy, dz) = axis
        a0 = (tx(-(pz + (-7) * dz) / PX), ty(-(py + (-7) * dy) / PX))
        a1 = (tx(-(pz + (13) * dz) / PX), ty(-(py + (13) * dy) / PX))
        d.line([a0, a1], fill=(30, 90, 230, 255), width=2)
    if anchors:
        for nm, (ax, ay, az) in anchors.items():          # metres, y up
            if nm.startswith("STEER"):
                continue
            px_, py_ = {"side": (-az, ay), "front": (ax, ay), "top": (-ax, -az)}[view]
            X, Y = tx(px_), ty(py_)
            r = 5 * supers
            d.line([X - r, Y, X + r, Y], fill=(220, 30, 30, 255), width=2)
            d.line([X, Y - r, X, Y + r], fill=(220, 30, 30, 255), width=2)
            if view == "side":
                d.text((X + r + 2, Y - 14), nm, fill=(180, 20, 20, 255), font=f)
    d.text((12 * supers, 8 * supers), title, fill=(20, 20, 30, 255), font=f)
    return img.resize((W, H), Image.LANCZOS)


def tex_colour(c, view):
    return face_colour(c, view)


def main():
    bones, cubes, _ = parse_java()
    K, anchors = java_consts()
    os.makedirs(OUT_DIR, exist_ok=True)
    poses = {
        "rest": dict(steer=0.0, fork=0.0, rear=0.0, fw=0.0, rw=0.0, crank=0.0),
        "steer": dict(steer=0.5, fork=0.10, rear=0.0, fw=0.0, rw=0.0, crank=0.0),
    }
    axis = ((anchors["STEER_PIVOT_PX"][1], anchors["STEER_PIVOT_PX"][2]),
            (anchors["STEER_AXIS_DOWN"][1], anchors["STEER_AXIS_DOWN"][2]))
    shown = {k: v for k, v in anchors.items() if not k.startswith("STEER")}
    for name, q in poses.items():
        P = pose(bones, K, q["steer"], q["fork"], q["rear"], q["fw"], q["rw"], q["crank"])
        cw = world_cubes(bones, cubes, P)
        ys = [pt[1] for c in cw for pt in c["corners"]]
        zs = [pt[2] for c in cw for pt in c["corners"]]
        xs = [pt[0] for c in cw for pt in c["corners"]]
        print(f"[{name}] extents x {min(xs)/PX:+.3f}..{max(xs)/PX:+.3f}  y {min(ys)/PX:+.3f}..{max(ys)/PX:+.3f}  "
              f"z {min(zs)/PX:+.3f}..{max(zs)/PX:+.3f} m")
        sc = 26.0
        grp = [(cw, tex_colour, 0.0)]
        t = f"hardtail {name}: steer={q['steer']} fork={q['fork']}"
        render(grp, "side", 1000, 760, sc, (520, 700), anchors=shown, human_m=-1.05, axis=axis,
               title=f"{t}  side (rider's left; forward = right)").save(os.path.join(OUT_DIR, f"hardtail_side_{name}.png"))
        render(grp, "side", 1400, 820, 50.0, (700, 780), anchors=shown, axis=axis,
               title=f"{t}  side close-up").save(os.path.join(OUT_DIR, f"hardtail_side_zoom_{name}.png"))
        render(grp, "top", 520, 900, 26.0, (260, 450), anchors=shown,
               title=f"{t}  top (forward = up)").save(os.path.join(OUT_DIR, f"hardtail_top_{name}.png"))
        render(grp, "front", 640, 780, 30.0, (320, 740), anchors=shown, human_m=-0.75,
               title=f"{t}  front").save(os.path.join(OUT_DIR, f"hardtail_front_{name}.png"))

    # side-by-side with the enduro (optional)
    try:
        import preview_enduro as pe
        eb, ec, _ = pe.parse_java()
        eK, _ea = pe.java_consts()
        EP, _ = pe.pose(eb, eK, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        ecw = pe.world_cubes(eb, ec, EP)
        HP = pose(bones, K, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        hcw = world_cubes(bones, cubes, HP)
        flat = lambda c, view: pe.MATERIALS[c["mat"]]
        render([(ecw, flat, -0.2), (hcw, tex_colour, 1.7)], "side", 1450, 760, 26.0, (560, 700), human_m=0.85,
               title="enduro (left, flat colours) vs hardtail (right, texture colours) + 1.8 m human"
               ).save(os.path.join(OUT_DIR, "hardtail_vs_enduro_side.png"))
    except Exception as ex:                                    # noqa: BLE001
        print("side-by-side skipped:", ex)
    print("wrote previews to", OUT_DIR)


if __name__ == "__main__":
    main()
