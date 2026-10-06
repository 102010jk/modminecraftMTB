#!/usr/bin/env python3
"""Offline geometry check of every bike frame shape (enduro c,h,l,n,t,u,m,g,j; hardtail c,s,v).

Parses the literal cube tables of EnduroBikeModel.java / HardtailBikeModel.java, poses them at rest, and reports per shape:
  * TUBE-END   tube ends that do not touch a joint / another frame part (gap > 0.25 units)
  * FLOAT      frame/swingarm/shock cubes that are not connected (gap <= 0.15) to the head tube's cluster
  * SEATPOST   seat post / collar / dropper not coaxial with the seat tube (angle > 2 deg or offset > 0.2) or not inserted
  * SADDLE     clamp not on the post top, rails/saddle not touching, saddle not level, saddle top away from SADDLE_TOP
  * SHOCK-EYE  shock eyes (frame side + swingarm side, incl. renderCustomized overrides) not on a mount cube
  * HARDTAIL   seat stays not meeting the seat tube just below the top-tube junction, post too long

Model units: 16 = 1 m, +Y down, forward -Z.  Exit code 1 if any issue is found.

    python tools/check_frames.py [--png]      (--png also renders a side view per shape into tools/preview/frames_<bike>_<code>.png)
"""
import math
import json
import os
import re
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bike_tex_common as T  # noqa: E402

ROOT = os.path.dirname(HERE)
MODEL_DIR = os.path.join(ROOT, "src", "main", "java", "com", "descentmtb", "client", "model")
ENDURO = os.path.join(MODEL_DIR, "EnduroBikeModel.java")
HARDTAIL = os.path.join(MODEL_DIR, "HardtailBikeModel.java")

END_GAP = 0.25        # tube end must be within this of its joint
CONTACT = 0.15        # cubes closer than this count as connected
EYE_GAP = 0.30        # shock eye centre to mount cube
AXIS_DEG, AXIS_OFF = 2.0, 0.2
ENDURO_CODES = "chlntumgj"
HARDTAIL_CODES = "csv"


# ------------------------------------------------------------------ maths
def rot_zyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    return np.array([[cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
                     [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
                     [-sy, cy * sx, cy * cx]])


def consts(path):
    src = open(path, encoding="utf-8").read()
    k = {m.group(1): float(m.group(2)) for m in re.finditer(r"\b([A-Z][A-Z0-9_]+) = (-?\d+(?:\.\d+)?)f", src)}
    m = re.search(r"SADDLE_TOP = new Vector3f\(([^)]*)\)", src)
    st = [float(v.strip().rstrip("f")) for v in m.group(1).split(",")]
    return src, k, st


class Box:
    def __init__(self, cube, W, scale=1.0):
        R, t = W[cube["bone"]]
        M = R @ rot_zyx(*(math.radians(a) for a in cube["r"]))
        self.cube = cube
        self.name = cube["name"]
        self.base = cube["base"]
        self.bone = cube["bone"]
        self.c = t + R @ np.array(cube["c"])
        lens = np.linalg.norm(M, axis=0)
        self.u = M / lens                      # columns = unit axes
        self.h = np.array(cube["s"]) / 2 * lens
        self.dir = self.u[:, 2]               # long axis (local z)

    def local(self, p):
        return (p - self.c) @ self.u

    def dist(self, p):
        """distance from point(s) p (N,3) to the solid box"""
        q = np.abs((np.atleast_2d(p) - self.c) @ self.u) - self.h
        return np.linalg.norm(np.maximum(q, 0), axis=1)

    def inside(self, p, pad=0.0):
        q = np.abs((np.atleast_2d(p) - self.c) @ self.u) - self.h - pad
        return np.all(q <= 0, axis=1)

    def end(self, sign):
        return self.c + sign * self.dir * self.h[2]

    def samples(self, step=0.12):
        pts = []
        axes = [np.linspace(-h, h, max(2, int(math.ceil(2 * h / step)) + 1)) for h in self.h]
        for k in range(3):
            a, b = [i for i in range(3) if i != k]
            for s in (-1, 1):
                g1, g2 = np.meshgrid(axes[a], axes[b], indexing="ij")
                loc = np.zeros((g1.size, 3))
                loc[:, k] = s * self.h[k]
                loc[:, a] = g1.ravel()
                loc[:, b] = g2.ravel()
                pts.append(loc)
        loc = np.vstack(pts)
        return self.c + loc @ self.u.T

    def corners(self):
        out = []
        for sx in (-1, 1):
            for sy in (-1, 1):
                for sz in (-1, 1):
                    out.append(self.c + self.u @ (np.array([sx, sy, sz]) * self.h))
        return np.array(out)


def gap(a, b):
    return min(b.dist(a.samples()).min(), a.dist(b.samples()).min())


def world(bones, P):
    W = {}

    def get(n):
        if n in W:
            return W[n]
        b = bones[n]
        if b["parent"] is None:
            W[n] = (np.eye(3), np.zeros(3))
            return W[n]
        pr, pt = get(b["parent"])
        p = P[n]
        R = rot_zyx(p["rx"], p["ry"], p["rz"]) * np.array([1, 1, p["zs"]])
        W[n] = (pr @ R, pt + pr @ np.array([p["x"], p["y"], p["z"]]))
        return W[n]

    for n in bones:
        get(n)
    return W


# ------------------------------------------------------------------ per-shape analysis
def analyse(full, code):
    path = ENDURO if full else HARDTAIL
    src, K, saddle_top = consts(path)
    bones, cubes, _ = T.parse_java(path)
    vis = [c for c in cubes if not c["codes"] or code in c["codes"]]

    # --- shock eyes (model units, frame space, rest pose)
    eyeF = eyeA = None
    if full:
        eyeF, eyeA = (K["SHOCK_FRAME_Y"], K["SHOCK_FRAME_Z"]), (K["SHOCK_ARM_Y"], K["SHOCK_ARM_Z"])
        profile = json.loads(open(os.path.join(HERE, 'frame_profiles.json'), encoding='utf-8').read()).get(code)
        if profile:
            eyeF, eyeA = profile['fixedEye'], profile['movingEye']
    P = {}
    for n, b in bones.items():
        px, py, pz = b["pivot"]
        rx, ry, rz = (math.radians(a) for a in b["rot"])
        P[n] = dict(x=px, y=py, z=pz, rx=rx, ry=ry, rz=rz, zs=1.0)
    if full:
        piv = np.array([bones["swingarm"]["pivot"][1], bones["swingarm"]["pivot"][2]])
        dy, dz = eyeA[0] - eyeF[0], eyeA[1] - eyeF[1]
        ln = math.hypot(dy, dz)
        P["shock_body"].update(y=eyeF[0], z=eyeF[1], rx=math.atan2(-dy, dz))
        P["shock_shaft"].update(y=eyeA[0] - piv[0], z=eyeA[1] - piv[1], rx=math.atan2(dy, -dz))
        if code in "ntumgj":
            P["shock_body"]["zs"] = P["shock_shaft"]["zs"] = ln / K["SHOCK_LEN0"]
            P["shock_spring"]["zs"] = 1.0
        else:
            P["shock_spring"]["zs"] = max(0.2, (ln - 2 * K["SPRING_PAD"]) / (K["SHOCK_LEN0"] - 2 * K["SPRING_PAD"]))
    W = world(bones, P)

    structural = {"frame", "swingarm", "shock_body", "shock_shaft", "catalog_upper", "catalog_lower", "catalog_stays"} if full else {"frame", "saddle"}
    boxes = [Box(c, W) for c in vis]
    by = {b.name: b for b in boxes}
    base = {}
    for b in boxes:
        base.setdefault(b.base, []).append(b)

    def get(n):
        return next((b for b in boxes if b.base == n and b.bone in structural), None)

    chk = [b for b in boxes if b.bone in structural and not b.base.startswith(("acc_", "chain_"))]
    issues = []
    add = lambda kind, msg: issues.append(f"{kind:9s} {msg}")  # noqa: E731

    # --- connectivity (FLOAT)
    n = len(chk)
    adj = [[] for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            if np.linalg.norm(chk[i].c - chk[j].c) > chk[i].h.max() * 1.8 + chk[j].h.max() * 1.8 + CONTACT * 4:
                continue
            if gap(chk[i], chk[j]) <= CONTACT:
                adj[i].append(j)
                adj[j].append(i)
    head = next(i for i, b in enumerate(chk) if b.base == "head_tube")
    seen, stack = {head}, [head]
    while stack:
        for j in adj[stack.pop()]:
            if j not in seen:
                seen.add(j)
                stack.append(j)
    for i, b in enumerate(chk):
        if i not in seen:
            nearest = min((gap(b, o), o.name) for k, o in enumerate(chk) if k != i)
            add("FLOAT", f"{b.name} ({b.bone}) not connected to frame; nearest {nearest[1]} gap {nearest[0]:.2f}")

    # --- tube ends
    fam = lambda b: "top" if b.base.startswith("top_") else "down" if b.base.startswith("down_") else None  # noqa: E731
    tubes = [b for b in chk if re.match(r"(top_|down_|seat_tube|chainstay|seatstay|yoke|rocker_[lr]$|lower_vpp|one77|vcs_|"
                                         r"stumpy|asymmetric|seat_bridge|pivot_strut)", b.base)]
    for b in tubes:
        if b.base == "seat_tube":
            ends = [("bottom", max((b.end(-1), b.end(1)), key=lambda p: p[1]))]
        else:
            ends = [("front", b.end(-1)), ("rear", b.end(1))]
        for label, p in ends:
            others = [o for o in chk if o is not b and o.base not in ("bb_cup_l", "bb_cup_r")]
            d_any = min(o.dist(p)[0] for o in others)
            if d_any > END_GAP:
                add("TUBE-END", f"{b.name} {label} end {tuple(np.round(p[1:], 2))} floats (nearest gap {d_any:.2f})")
                continue
            f = fam(b)
            if f:
                joint = ("head_tube", "seat_tube") if f == "top" else ("head_tube", "bb_shell")
                same = [o for o in chk if o is not b and fam(o) == f]
                near_same = min((o.dist(p)[0] for o in same), default=9)
                near_joint = min((o.dist(p)[0] for o in others if o.base in joint), default=9)
                if near_same > END_GAP and near_joint > END_GAP:
                    add("TUBE-END", f"{b.name} {label} end {tuple(np.round(p[1:], 2))} meets no {'/'.join(joint)} "
                                    f"(gap {near_joint:.2f})")

    # --- seat post
    st = get("seat_tube")
    a0, a1 = st.end(-1), st.end(1)
    top, bot = (a0, a1) if a0[1] < a1[1] else (a1, a0)           # y down: top has smaller y
    axis = (top - bot) / np.linalg.norm(top - bot)
    tparam = lambda p: float((p - bot) @ axis)  # noqa: E731
    seatpost_top = None
    for nm in ("seatpost", "dropper_lower", "seat_collar"):
        b = get(nm)
        if b is None:
            continue
        d = b.dir if b.dir @ axis > 0 else -b.dir
        ang = math.degrees(math.acos(max(-1, min(1, float(d @ axis)))))
        v = b.c - bot
        off = float(np.linalg.norm(v - (v @ axis) * axis))
        if ang > AXIS_DEG or off > AXIS_OFF:
            add("SEATPOST", f"{nm} not on seat-tube axis (angle {ang:.1f} deg, offset {off:.2f})")
    post = get("seatpost")
    tt = tparam(top)
    if post is not None:
        p_lo = min(tparam(post.end(-1)), tparam(post.end(1)))
        p_hi = max(tparam(post.end(-1)), tparam(post.end(1)))
        seatpost_top = bot + axis * p_hi
        if p_lo > tt - 0.4:
            add("SEATPOST", f"post not inserted in seat tube (bottom {tt - p_lo:.2f} below tube top)")
        if p_hi < tt + 0.3:
            add("SEATPOST", "post does not stick out of the seat tube")
        if not full and p_hi - tt > 2.0:
            add("HARDTAIL", f"dirt-jump post sticks out {p_hi - tt:.2f} units (> 2.0)")
        if full:
            dl = get("dropper_lower")
            if dl is not None:
                dhi = max(tparam(dl.end(-1)), tparam(dl.end(1)))
                if dhi > p_hi + 0.1 or min(tparam(dl.end(-1)), tparam(dl.end(1))) < tt - 1.5:
                    add("SEATPOST", "dropper lower tube does not sit on the seat-tube top")
    col = get("seat_collar")
    if col is not None and abs(tparam(col.c) - tt) > 0.9:
        add("SEATPOST", f"seat collar {tparam(col.c) - tt:+.2f} from seat-tube top")

    # --- saddle
    sad = [b for b in boxes if b.base.startswith("saddle_") and b.base not in ("saddle_clamp",) or b.base == "saddle_under"]
    sad = [b for b in boxes if b.base in ("saddle_nose", "saddle_mid", "saddle_rear", "saddle_under")]
    clamp = get("saddle_clamp")
    rails = [b for b in boxes if b.base in ("saddle_rail_l", "saddle_rail_r")]
    if clamp is not None and seatpost_top is not None:
        dtop = float(np.linalg.norm(clamp.c - seatpost_top))
        if dtop > 0.4 or clamp.dist(seatpost_top)[0] > 0.1:
            add("SADDLE", f"saddle clamp not on post top (centre {dtop:.2f} from post-top end)")
        for r in rails:
            if gap(clamp, r) > 0.1:
                add("SADDLE", f"{r.name} does not touch the clamp (gap {gap(clamp, r):.2f})")
        under = next(b for b in sad if b.base == "saddle_under")
        for r in rails:
            if gap(under, r) > 0.1:
                add("SADDLE", f"{r.name} does not touch the saddle shell (gap {gap(under, r):.2f})")
        for b in sad:
            if gap(under, b) > 0.1:
                add("SADDLE", f"{b.name} detached from saddle shell (gap {gap(under, b):.2f})")
    tilt = [abs(b.cube["r"][0]) for b in sad]
    if full and max(tilt) > 6:
        add("SADDLE", f"saddle not level ({max(tilt):.1f} deg)")
    if not full and max(tilt) > 12:
        add("SADDLE", f"saddle tilted {max(tilt):.1f} deg")
    # saddle top surface at SADDLE_TOP.z
    z0 = -saddle_top[2] * -16.0 if False else saddle_top[2] * 16.0
    ys = np.arange(-30.0, 0.0, 0.01)
    col_pts = np.stack([np.zeros_like(ys), ys, np.full_like(ys, z0)], 1)
    hit = np.zeros(len(ys), bool)
    for b in sad:
        hit |= b.inside(col_pts)
    if not hit.any():
        add("SADDLE", "no saddle surface under SADDLE_TOP.z")
    else:
        topy = ys[hit.argmax()]
        want = -saddle_top[1] * 16.0
        if abs(topy - want) > 0.5:
            add("SADDLE", f"saddle top y {topy:.2f} vs SADDLE_TOP {want:.2f} (> 0.5)")

    # --- hardtail: seat stays vs seat tube
    if not full:
        tops = [b for b in chk if b.base.startswith("top_")]
        tpar = []
        for b in tops:
            for s in (-1, 1):
                p = b.end(s)
                if st.dist(p)[0] < 0.4:
                    tpar.append(tparam(p))
        if tpar:
            junction = max(tpar)
            for nm in ("seatstay_l", "seatstay_r"):
                s = get(nm)
                ends = [s.end(-1), s.end(1)]
                p = min(ends, key=lambda q: q[1])               # upper end
                d = st.dist(p)[0]
                v = p - bot
                tp = float(v @ axis)
                if d > END_GAP or not (junction - 2.2 <= tp <= junction + 0.05):
                    add("HARDTAIL", f"{nm} top end must meet seat tube just below the top-tube junction "
                                    f"(gap {d:.2f}, {junction - tp:+.2f} below junction)")

    # --- shock eyes
    if full:
        piv = np.array(bones["swingarm"]["pivot"])
        pf = np.array([0.0, eyeF[0], eyeF[1]])
        pa = np.array([0.0, eyeA[0], eyeA[1]])
        arm_bone = ('catalog_lower' if profile['dual'] else 'catalog_upper') if profile else 'swingarm'
        for lab, p, bone in (("frame", pf, "frame"), ("rear", pa, arm_bone)):
            mounts = [b for b in chk if b.bone == bone and b.base not in ("saddle_clamp",) and not b.base.startswith(("saddle", "seatpost", "dropper", "seat_collar", "bb_", "head", "pivot_axle"))]
            ds = sorted((b.dist(p)[0], b.name) for b in mounts)
            # eye must sit on a cube that is not a long main tube only: mount/boss/rocker/bar cubes count, tubes count too
            if ds[0][0] > EYE_GAP:
                add("SHOCK-EYE", f"{lab} eye ({eyeF if lab == 'frame' else eyeA}) not on any {bone} mount (nearest {ds[0][1]} gap {ds[0][0]:.2f})")
            else:
                on = [b for b in mounts if b.dist(p)[0] <= EYE_GAP]
                ok = False
                for b in on:
                    i = chk.index(b)
                    ok |= i in seen
                if not ok:
                    add("SHOCK-EYE", f"{lab} eye mount ({', '.join(b.name for b in on)}) is not attached to the frame")
        # shock eye cubes themselves attach
        for nm in ("eye",):
            pass
    return issues, boxes


def render(full, code, boxes):
    from PIL import Image, ImageDraw
    S, ox, oy = 52.0, 60.0, 560.0
    img = Image.new("RGB", (900, 640), (236, 238, 242))
    d = ImageDraw.Draw(img)
    cols = {"frame": (16, 94, 100), "black": (40, 40, 44), "silver": (170, 175, 180), "saddle": (30, 30, 30)}
    keep = [b for b in boxes if b.bone in (("frame", "swingarm", "shock_body", "shock_shaft", "catalog_upper", "catalog_lower", "catalog_stays") if full else ("frame", "saddle"))
            and not b.base.startswith(("acc_", "chain_"))]
    keep.sort(key=lambda b: b.c[0])
    for b in keep:
        pts = [(ox + (-(p[2])) * S + 330, oy + p[1] * S) for p in b.corners()]
        hull = T_hull(pts)
        col = cols.get(b.cube["mat"], (150, 100, 40))
        d.polygon(hull, fill=col, outline=(0, 0, 0))
    img.save(os.path.join(HERE, "preview", f"frames_{'enduro' if full else 'hardtail'}_{code}.png"))


def T_hull(pts):
    pts = sorted(set(pts))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lo, up = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0:
            lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(up) >= 2 and cross(up[-2], up[-1], p) <= 0:
            up.pop()
        up.append(p)
    return lo[:-1] + up[:-1]


def main():
    png = "--png" in sys.argv
    total = 0
    for full, codes in ((True, ENDURO_CODES), (False, HARDTAIL_CODES)):
        for code in codes:
            issues, boxes = analyse(full, code)
            total += len(issues)
            print(f"[{'enduro' if full else 'hardtail'} {code}] " + ("OK" if not issues else f"{len(issues)} issue(s)"))
            for i in issues:
                print("   " + i)
            if png:
                os.makedirs(os.path.join(HERE, "preview"), exist_ok=True)
                render(full, code, boxes)
    print("TOTAL issues:", total)
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
