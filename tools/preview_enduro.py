#!/usr/bin/env python3
"""Orthographic preview of the enduro bike model (side / top / front) straight from EnduroBikeModel.java.

The bone/cube table and the pose constants are parsed from the Java source (single source of truth);
`pose()` below is a line-by-line port of EnduroBikeModel.setupPose().  Renders to tools/preview/*.png.

    python tools/preview_enduro.py
"""
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_enduro_texture import JAVA, MATERIALS, parse_java  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")
PX = 16.0


# ------------------------------------------------------------------ java constants
def java_consts():
    src = open(JAVA, encoding="utf-8").read()
    k = {m.group(1): float(m.group(2)) for m in re.finditer(r"\b([A-Z][A-Z0-9_]+) = (-?\d+(?:\.\d+)?)f", src)}
    anchors = {}
    for m in re.finditer(r"(GRIP_LEFT|GRIP_RIGHT|SADDLE_TOP|PEDAL_AXIS) = new Vector3f\(([^)]*)\)", src):
        anchors[m.group(1)] = tuple(float(v.strip().rstrip("f")) for v in m.group(2).split(","))
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
    PIV_Y, PIV_Z, AXLE_Y, AXLE_Z = K["PIV_Y"], K["PIV_Z"], K["AXLE_Y"], K["AXLE_Z"]
    SF_Y, SF_Z, SA_Y, SA_Z = K["SHOCK_FRAME_Y"], K["SHOCK_FRAME_Z"], K["SHOCK_ARM_Y"], K["SHOCK_ARM_Z"]
    BB_Y, BB_Z = K["BB_Y"], K["BB_Z"]
    SW_LEN = math.hypot(AXLE_Z - PIV_Z, AXLE_Y - PIV_Y)
    SW_PHI = math.atan2(AXLE_Y - PIV_Y, AXLE_Z - PIV_Z)
    fork = clamp(fork_m, 0, 0.17) * PX
    rise = clamp(rear_m, 0, 0.16) * PX
    P["steer"]["ry"] = steer
    P["fork_lower"]["y"] = -fork
    P["front_wheel"]["rx"] = fwheel
    s = clamp(rise / SW_LEN - math.sin(SW_PHI), -1, 1)
    alpha = SW_PHI + math.asin(s)
    P["swingarm"]["rx"] = alpha
    P["rear_wheel"]["rx"] = rwheel
    cs, sn = math.cos(alpha), math.sin(alpha)
    ry_, rz_ = SA_Y - PIV_Y, SA_Z - PIV_Z
    eyeY = PIV_Y + ry_ * cs - rz_ * sn
    eyeZ = PIV_Z + ry_ * sn + rz_ * cs
    dy, dz = eyeY - SF_Y, eyeZ - SF_Z
    ln = math.hypot(dy, dz)
    P["shock_body"]["rx"] = math.atan2(-dy, dz)
    P["shock_shaft"]["rx"] = math.atan2(dy, -dz) - alpha
    pad, l0 = K["SPRING_PAD"], K["SHOCK_LEN0"]
    P["shock_spring"]["zs"] = max(0.2, (ln - 2 * pad) / (l0 - 2 * pad))
    P["cranks"]["rx"] = crank
    P["pedal_left"]["rx"] = -crank
    P["pedal_right"]["rx"] = -crank
    ay = PIV_Y + (AXLE_Y - PIV_Y) * cs - (AXLE_Z - PIV_Z) * sn
    az = PIV_Z + (AXLE_Y - PIV_Y) * sn + (AXLE_Z - PIV_Z) * cs

    def aim(name, y0, z0, y1, z1):
        d_y, d_z = y1 - y0, z1 - z0
        P[name]["y"] = (y0 + y1) / 2
        P[name]["z"] = (z0 + z1) / 2
        P[name]["rx"] = math.atan2(-d_y, d_z)
        P[name]["zs"] = math.hypot(d_y, d_z) / K["CHAIN_BASE"]

    aim("chain_top", BB_Y - K["CHAINRING_R"], BB_Z, ay - K["COG_R"], az)
    aim("chain_bottom", BB_Y + K["CHAINRING_R"], BB_Z, ay + K["COG_R"], az)
    info = dict(alpha=alpha, shock_len=ln, axle=(ay, az))
    return P, info


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


def render(cubes_w, view, W, H, scale, origin, supers=2, title="", anchors=None, human_m=None):
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
    for cw in cubes_w:
        pts2 = [(tx(sxf(x, hh, z) / PX), ty(syf(x, hh, z) / PX)) for (x, hh, z) in cw["corners"]]
        items.append((max(dpf(*p) for p in cw["corners"]), cw, pts2))
    items.sort(key=lambda t: t[0])
    dmin = min(i[0] for i in items); dmax = max(i[0] for i in items)
    for dep, cw, pts2 in items:
        base = MATERIALS[cw["c"]["mat"]]
        k = 0.72 + 0.4 * (dep - dmin) / (dmax - dmin + 1e-6)
        col = tuple(min(255, int(ch * k)) for ch in base)
        hp = hull(pts2)
        if len(hp) >= 3:
            d.polygon(hp, fill=col + (255,), outline=tuple(int(ch * 0.45) for ch in col) + (255,))
        else:
            d.line(pts2, fill=col + (255,))
    f = ImageFont.load_default()
    if anchors:
        for nm, (ax, ay, az) in anchors.items():          # metres, y up
            px_, py_ = {"side": (-az, ay), "front": (ax, ay), "top": (-ax, -az)}[view]
            X, Y = tx(px_), ty(py_)
            r = 5 * supers
            d.line([X - r, Y, X + r, Y], fill=(220, 30, 30, 255), width=2)
            d.line([X, Y - r, X, Y + r], fill=(220, 30, 30, 255), width=2)
            if view == "side":
                d.text((X + r + 2, Y - 14), nm, fill=(180, 20, 20, 255), font=f)
    d.text((12 * supers, 8 * supers), title, fill=(20, 20, 30, 255), font=f)
    return img.resize((W, H), Image.LANCZOS)


def main():
    bones, cubes, _ = parse_java()
    K, anchors = java_consts()
    os.makedirs(OUT_DIR, exist_ok=True)
    poses = {
        "rest": dict(steer=0.0, fork=0.0, rear=0.0, fw=0.0, rw=0.0, crank=0.0),
        "compressed": dict(steer=0.4, fork=0.17, rear=0.16, fw=0.0, rw=0.0, crank=0.0),
        "crank45": dict(steer=0.0, fork=0.05, rear=0.05, fw=0.7, rw=0.3, crank=math.radians(50)),
    }
    for name, q in poses.items():
        P, info = pose(bones, K, q["steer"], q["fork"], q["rear"], q["fw"], q["rw"], q["crank"])
        cw = world_cubes(bones, cubes, P)
        # report
        ys = [pt[1] for c in cw for pt in c["corners"]]
        zs = [pt[2] for c in cw for pt in c["corners"]]
        xs = [pt[0] for c in cw for pt in c["corners"]]
        print(f"[{name}] extents x {min(xs)/PX:+.3f}..{max(xs)/PX:+.3f}  y {min(ys)/PX:+.3f}..{max(ys)/PX:+.3f}  "
              f"z {min(zs)/PX:+.3f}..{max(zs)/PX:+.3f} m;  shock {info['shock_len']:.3f}px  swingarm {math.degrees(info['alpha']):.2f} deg  "
              f"rear axle (y,z) = ({-info['axle'][0]:.3f}, {info['axle'][1]:.3f}) px")

        sc = 21.0
        w3 = world_cubes(bones, cubes, P)
        render(w3, "side", 1150, 780, sc, (640, 730), anchors=anchors, human_m=-1.15,
               title=f"{name}: side (rider's left; forward = right), steer={q['steer']} fork={q['fork']} rear={q['rear']}"
               ).save(os.path.join(OUT_DIR, f"side_{name}.png"))
        render(w3, "side", 1400, 820, 40.0, (700, 780), anchors=anchors,
               title=f"{name}: side close-up").save(os.path.join(OUT_DIR, f"side_zoom_{name}.png"))
        render(w3, "top", 560, 900, sc, (280, 450), anchors=anchors,
               title=f"{name}: top (forward = up)").save(os.path.join(OUT_DIR, f"top_{name}.png"))
        render(w3, "front", 700, 780, sc, (350, 730), anchors=anchors, human_m=-0.9,
               title=f"{name}: front").save(os.path.join(OUT_DIR, f"front_{name}.png"))
    print("wrote previews to", OUT_DIR)


if __name__ == "__main__":
    main()
