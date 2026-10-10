"""Generates the skis: one pair per SkiBrand (cube tables, per-brand textures, item icons, preview renders).

  python tools/gen_ski_assets.py [--no-preview]

Writes
  src/main/java/com/descentmtb/client/model/SkiModel.java          (cube tables between the GENERATED markers)
  src/main/resources/assets/descentmtb/textures/entity/ski/<id>.png (one texture per brand, same size)
  src/main/resources/assets/descentmtb/textures/item/ski_<id>.png   (32x32 item icon: the pair, diagonal)
  src/main/resources/assets/descentmtb/models/item/ski_<id>.json    (item/generated)
  tools/preview/ski/*.png                                           (side / top / rear / 3/4 / first person /
                                                                     base / leg fit / poles / gallery)

ONE SKI = one "ski" bone (ski, race plate, bindings, brake bone) + one "boot" bone (shell, buckles and the "cuff"
bone, which pivots about the sole centre to follow the rider's shin) + one "pole" bone. The renderer draws the same
ski twice, each placed by SkiStance (so the boots follow the rider's feet through the tricks), and the rider layer
draws the pole in each hand.

Geometry is written in metres in the ski's local frame: X = the rider's LEFT, Y = UP, Z = BACK (the tip points to
-Z), origin at the boot-sole centre (SkiStance's foot point, BOOT_SOLE = 0.045 m above the snow). The models are
built at 64 units per metre (the skis are small, thin parts: 4x the bicycles' density so the top sheet carries the
maker's lettering) and drawn at 1/4 scale. The pole is written in its hand frame: origin at the grip centre, the
shaft hanging down (-Y here = +Y in the model: the model's Y points down the shaft toward the tip).

Realism: real lengths and sidecut radius (SkiBrand), the planform from the radius (half-width = waist/2 + d^2/2R from
the waist, a longer tail radius so the tail is narrower than the shovel), widths x WIDTH_SCALE because the Minecraft
leg the boot has to swallow is 0.23 m wide; race skis: low tip, flat square tail, a 15 mm binding plate; freestyle
twins: high tip AND turned-up tail, no plate, a turntable heel. Each flat section of ski is two stacked boxes: the
steel-edged base strip (grey side line, graphic base below) and the core with the sidewall and the top sheet.
"""
import math, os, sys, json
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 64.0                       # model units (= texels) per metre
TEX_W, TEX_H = 256, 256
WIDTH_SCALE = 1.3
BOOT_SOLE = 0.045              # SkiStance.BOOT_SOLE
HALF_STANCE = 0.11             # SkiStance.HALF_STANCE
RES = os.path.join(ROOT, "src/main/resources/assets/descentmtb")
JAVA = os.path.join(ROOT, "src/main/java/com/descentmtb/client/model/SkiModel.java")
PREVIEW = os.path.join(ROOT, "tools/preview/ski")

# ------------------------------------------------------------------ the pairs (mirrors ski/SkiBrand.java)
def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], float)

BRANDS = [
    dict(id="atomic_redster_g9", race=True, maker="ATOMIC", tail_word="REDSTER", L=188, R=23.0, waist=66,
         top=0xD0202A, second=0x141414, accent=0xF4F4F4, mount=0.075, pole="gs",
         sidewall=0x1A1A1C, base=0x161618, base_word=0xB01C24,
         plate=0x2B2C31, plate_accent=0xD0202A, bind=0xC81E26, bind2=0x1C1C1E,
         shell=0xC8202A, cuff=0xC8202A, buckle=0xD8D8DC, liner=0x1A1A1A, strap=0x1A1A1A,
         shaft=0x2A2A2E, shaft_band=0xD0202A, grip=0x1A1A1C, basket=0x1A1A1C),
    dict(id="rossignol_hero_st", race=True, maker="ROSSIGNOL", tail_word="HERO", L=167, R=13.0, waist=68,
         top=0x17181C, second=0xF2F2F2, accent=0xE2231A, mount=0.07, pole="sl",
         sidewall=0x101012, base=0x141416, base_word=0xC81E16,
         plate=0x1C1C20, plate_accent=0xE2231A, bind=0xEFEFEF, bind2=0x1D1D1F,
         shell=0xE9E9EA, cuff=0x1A1B1F, buckle=0xE2231A, liner=0x1A1A1A, strap=0x1A1A1A,
         shaft=0xC9CBCF, shaft_band=0xE2231A, grip=0x1A1A1C, basket=0x1A1A1C),
    dict(id="fischer_rc4_wc", race=True, maker="FISCHER", tail_word="RC4", L=175, R=17.0, waist=68,
         top=0x101010, second=0xF6D200, accent=0xE0301E, mount=0.07, pole="gs",
         sidewall=0x101010, base=0x141414, base_word=0xC9AC00,
         plate=0x18181A, plate_accent=0xF6D200, bind=0x1C1C1E, bind2=0xF6D200,
         shell=0xF2CC00, cuff=0x141414, buckle=0xD8D8DC, liner=0x1A1A1A, strap=0x141414,
         shaft=0x1C1C1E, shaft_band=0xF6D200, grip=0x1A1A1C, basket=0x1A1A1C),
    dict(id="armada_arv_96", race=False, maker="ARMADA", tail_word="ARV", L=180, R=19.0, waist=96,
         top=0x1FA7A0, second=0xF07A1A, accent=0xF3EBD6, mount=0.035, pole="park",
         sidewall=0x1C1C1E, base=0xF07A1A, base_word=0x16706B,
         plate=0x1A1A1C, plate_accent=0xF07A1A, bind=0x1C1C1E, bind2=0xF07A1A,
         shell=0x34373D, cuff=0x34373D, buckle=0xF07A1A, liner=0x1A1A1A, strap=0x1FA7A0,
         shaft=0x1FA7A0, shaft_band=0xF3EBD6, grip=0x1A1A1C, basket=0x1A1A1C),
    dict(id="line_chronic_101", race=False, maker="LINE", tail_word="CHRONIC", L=178, R=18.0, waist=101,
         top=0x5B2A86, second=0xF5C518, accent=0x161616, mount=0.03, pole="park",
         sidewall=0x161616, base=0xF5C518, base_word=0x4A1F70,
         plate=0x1A1A1C, plate_accent=0x5B2A86, bind=0xEDEDED, bind2=0x5B2A86,
         shell=0xEDEDEE, cuff=0x18181A, buckle=0xF5C518, liner=0x1A1A1A, strap=0x5B2A86,
         shaft=0x5B2A86, shaft_band=0xF5C518, grip=0x1A1A1C, basket=0x1A1A1C),
    dict(id="faction_prodigy_2", race=False, maker="FACTION", tail_word="PRODIGY", L=173, R=17.0, waist=98,
         top=0xEDEDED, second=0x1A1A1A, accent=0xD7262E, mount=0.025, pole="park",
         sidewall=0x1A1A1A, base=0xD7262E, base_word=0xF2F2F2,
         plate=0x1A1A1C, plate_accent=0xD7262E, bind=0x1A1A1C, bind2=0xD7262E,
         shell=0x1C1C1E, cuff=0x1C1C1E, buckle=0xD7262E, liner=0x101010, strap=0xD7262E,
         shaft=0xE6E6E6, shaft_band=0xD7262E, grip=0x1A1A1C, basket=0x1A1A1C),
]

# ------------------------------------------------------------------ maths
def rx(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def ry(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def rz(a):
    c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
def zyx(x, y, z):
    return rz(z) @ ry(y) @ rx(x)
def euler_zyx(R):
    b = math.asin(max(-1, min(1, -R[2][0])))
    a = math.atan2(R[2][1], R[2][2]); c = math.atan2(R[1][0], R[0][0])
    return a, b, c

FLIP = np.diag([1.0, -1.0, 1.0])
def to_model(p):               # local metres (Y up) -> model units (Y down)
    return FLIP @ (np.asarray(p, float) * S)
def A(*v):
    return np.array(v, float)

# ------------------------------------------------------------------ rig
class Bone:
    def __init__(self, name, parent, origin_world, rot_model=np.eye(3)):
        self.name, self.parent = name, parent
        self.origin = to_model(origin_world)
        self.rot = rot_model                        # absolute model-space rotation (rest pose)
        self.cubes, self.children = [], []
        if parent is not None: parent.children.append(self)
    def local(self):
        P = self.parent
        return P.rot.T @ (self.origin - P.origin), P.rot.T @ self.rot
    def walk(self):
        yield self
        for c in self.children: yield from c.walk()

class Cube:
    """mat: painting material; info: extra data for the painter (along-ski coordinates, width ...)."""
    def __init__(self, bone, name, mat, center_world, size_m, rot_world=np.eye(3), **info):
        self.bone, self.name, self.mat, self.info = bone, name, mat, info
        self.center = to_model(center_world)
        self.size = np.asarray(size_m, float) * S
        if info.pop("snap", True):
            # whole texels across X and Z (the box-UV face offsets are u + dz, u + dz + dx ...): a fractional size
            # there would make neighbouring faces share texels. Thin parts keep their size (they are one colour).
            for k in (0, 2):
                if self.size[k] > 2.5: self.size[k] = round(self.size[k])
        self.rot = FLIP @ rot_world @ FLIP
        self.u = self.v = 0
        bone.cubes.append(self)

def box(bone, name, mat, c, size, rot=(0, 0, 0), **info):
    return Cube(bone, name, mat, c, size, zyx(*[math.radians(v) for v in rot]), **info)

def span(bone, name, mat, x0, x1, y0, y1, z0, z1, rot=(0, 0, 0), **info):
    """Axis-aligned box from its extents (metres)."""
    return box(bone, name, mat, ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2), (x1 - x0, y1 - y0, z1 - z0), rot, **info)

def rod(bone, name, mat, a, b, w, h, **info):
    """A box from point a to point b (length along the cube's Z), section w (x) by h, no roll."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = b - a; L = np.linalg.norm(v); n = v / L
    yaw = math.atan2(n[0], n[2]); pitch = math.asin(max(-1, min(1, n[1])))
    return Cube(bone, name, mat, (a + b) / 2, (w, h, L), ry(yaw) @ rx(-pitch), **info)

def shaft(bone, name, mat, a, b, w, h, **info):
    """A box from a to b with the length along the cube's Y (poles: the model's Y runs down the shaft)."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = b - a; L = np.linalg.norm(v); n = v / L
    # rotate the box's -Y (world: the pole hangs down) onto n, about X only (the poles bend in the Y-Z plane)
    ang = math.atan2(n[2], -n[1])
    return Cube(bone, name, mat, (a + b) / 2, (w, L, h), rx(-ang), **info)

# ------------------------------------------------------------------ one ski unit
Y_BASE = -BOOT_SOLE
BASE_T = 0.004                # base + steel edge strip
CORE_UNDERFOOT = 0.013        # core + top sheet under the binding (total 17 mm)
CORE_END = 0.0085

class SkiGeo:
    pass

def planform(b):
    """Returns half-width(z) (m, scaled) for the flat running length and the key positions."""
    L = b["L"] / 100.0
    race = b["race"]
    g = SkiGeo()
    g.L, g.race = L, race
    g.tip_len = sum((TIP_RACE if race else TIP_FREE)["len"]) / S
    g.tail_len = 0.0 if race else sum(TAIL_FREE["len"]) / S
    zc = -b["mount"]
    snap = lambda z: round(z * S) / S
    g.z_tc = snap(zc - L / 2 + g.tip_len)    # tip contact (start of the shovel)
    g.z_tl = snap(zc + L / 2 - g.tail_len)   # tail contact (race: the tail end)
    g.L = g.tip_len + g.tail_len + (g.z_tl - g.z_tc)     # the length actually built (on the texel grid)
    g.a_tl = g.tail_len                      # along-ski coordinate (from the tail end) of the tail contact
    g.a_of = lambda z: g.a_tl + (g.z_tl - z)
    g.ab = g.a_of(0.0)                       # along-ski coordinate of the boot centre
    g.a_tc = g.a_of(g.z_tc)                  # ... and of the tip contact
    z_w = 0.02                               # waist just behind the boot centre
    R = b["R"]
    Rt = R * (1.35 if race else 1.12)       # the tail tapers less than the shovel flares
    w0 = b["waist"] / 1000.0
    def hw(z):
        d = z - z_w
        return WIDTH_SCALE * (w0 / 2 + d * d / (2 * (R if d < 0 else Rt)))
    g.hw = hw
    return g

TIP_RACE = dict(len=[3, 3, 3, 2, 1], frac=[0.99, 0.95, 0.85, 0.66, 0.40],
                ang=[4, 12, 21, 30, 36], t=[0.0095, 0.0085, 0.0075, 0.0068, 0.006])
TIP_FREE = dict(len=[4, 4, 3, 3, 2, 1], frac=[0.99, 0.96, 0.90, 0.80, 0.62, 0.38],
                ang=[4, 11, 19, 28, 36, 42], t=[0.0095, 0.009, 0.008, 0.0072, 0.0066, 0.006])
TAIL_FREE = dict(len=[3, 3, 3, 2, 2], frac=[0.99, 0.95, 0.86, 0.70, 0.45],
                 ang=[4, 11, 19, 27, 33], t=[0.0095, 0.0088, 0.0078, 0.007, 0.0064])

def build_ski(b, brand_bone):
    g = planform(b)
    ski = Bone("ski", brand_bone, [0, 0, 0])
    # ---- flat running length: segments where the (integer texel) width changes, plus forced cuts
    zs = np.linspace(g.z_tc, g.z_tl, 2000)
    wpx = [max(2, int(round(2 * g.hw(z) * S))) for z in zs]
    cuts = [g.z_tc]
    for i in range(1, len(zs)):
        if wpx[i] != wpx[i - 1]: cuts.append(float(zs[i]))
    cuts.append(g.z_tl)
    for f in (-0.34, 0.32):                  # constant thickness under the binding / plate
        if cuts[0] < f < cuts[-1]: cuts.append(f)
    cuts = sorted(set(round(c * S) / S for c in cuts))          # whole texels along the ski
    merged = [cuts[0]]                       # no slivers
    for c in cuts[1:]:
        if c - merged[-1] < 0.025 and c != cuts[-1]: continue
        merged.append(c)
    cuts = merged
    final = [cuts[0]]
    for c in cuts[1:]:                       # split long pieces so the thickness can taper
        n = max(1, int(math.ceil((c - final[-1]) / 0.30)))
        base0 = final[-1]
        step = (c - base0) / n
        for k in range(1, n + 1): final.append(round((base0 + step * k) * S) / S)
    cuts = final
    g.segments = []
    for i in range(len(cuts) - 1):
        z0, z1 = cuts[i], cuts[i + 1]
        zm = (z0 + z1) / 2
        w = max(2, int(round(2 * g.hw(zm) * S))) / S
        # thickness: full under the binding, tapering to the contact points
        if -0.34 <= zm <= 0.32:
            tc = CORE_UNDERFOOT
        else:
            ends = g.z_tc if zm < 0 else g.z_tl
            k = (abs(zm) - (0.34 if zm < 0 else 0.32)) / max(1e-6, abs(ends) - (0.34 if zm < 0 else 0.32))
            tc = CORE_UNDERFOOT + (CORE_END - CORE_UNDERFOOT) * min(1, max(0, k))
            tc = round(tc * 1000 / 0.5) * 0.5 / 1000
        g.segments.append((z0, z1, w, tc))
        info = dict(a0=g.a_of(z1), a1=g.a_of(z0), w=w * S, part="flat")
        span(ski, f"base{i}", "ski_base", -w / 2, w / 2, Y_BASE, Y_BASE + BASE_T, z0, z1, **info)
        span(ski, f"core{i}", "ski_core", -w / 2, w / 2, Y_BASE + BASE_T, Y_BASE + BASE_T + tc, z0, z1, **info)
    g.w_tc = g.segments[0][2]
    g.w_tl = g.segments[-1][2]
    g.w_waist = min(s[2] for s in g.segments)
    # ---- shovel (and the twin tip's tail): a chain of boxes pivoting on the base line
    def chain(spec, z_start, w_start, forward, prefix):
        p = np.array([Y_BASE, z_start])          # (y, z) on the base line
        a = g.a_tc if forward else g.a_tl
        for k, (ln, fr, ang, t) in enumerate(zip(spec["len"], spec["frac"], spec["ang"], spec["t"])):
            ln = ln / S                              # lengths are given in whole texels
            th = math.radians(ang)
            d = np.array([math.sin(th), -math.cos(th) if forward else math.cos(th)])
            nrm = np.array([math.cos(th), math.sin(th) if forward else -math.sin(th)])
            q = p + d * ln
            mid = (p + q) / 2 + nrm * t / 2
            w = max(2, int(round(fr * w_start * S))) / S
            rot = rx(th) if forward else rx(-th)
            a0, a1 = (a, a + ln) if forward else (a - ln, a)
            Cube(ski, f"{prefix}{k}", "ski_tip", (0, mid[0], mid[1]), (w, t, ln), rot,
                 a0=a0, a1=a1, w=w * S, part=prefix)
            a = a1 if forward else a0
            p = q
        return p
    g.tip_end = chain(TIP_RACE if g.race else TIP_FREE, g.z_tc, g.w_tc, True, "tip")
    if not g.race:
        g.tail_end = chain(TAIL_FREE, g.z_tl, g.w_tl, False, "tail")
    return ski, g

# ------------------------------------------------------------------ plate, bindings, brakes
def build_binding(b, ski, g):
    y_top = Y_BASE + BASE_T + CORE_UNDERFOOT            # top sheet under the boot
    if g.race:
        pw = min(g.w_waist, 0.075) - 0.006
        span(ski, "plate_lower", "plate", -pw / 2, pw / 2, y_top, y_top + 0.008, -0.335, 0.295)
        span(ski, "plate_upper", "plate_top", -pw / 2 + 0.003, pw / 2 - 0.003, y_top + 0.008, y_top + 0.015, -0.30, 0.265)
        y_m = y_top + 0.015
    else:
        y_m = y_top
        span(ski, "toe_track", "track", -0.03, 0.03, y_top, y_top + 0.006, -0.30, -0.13)
    # toe piece
    span(ski, "toe_afd", "afd", -0.033, 0.033, y_m, -0.0005, -0.205, -0.125)
    span(ski, "toe_body", "bind", -0.036, 0.036, y_m, 0.036, -0.282, -0.186)
    box(ski, "toe_nose", "bind", (0, 0.012, -0.288), (0.062, 0.03, 0.045), (-32, 0, 0))
    span(ski, "toe_wings", "bind2", -0.054, 0.054, 0.004, 0.030, -0.197, -0.169)
    # heel piece
    span(ski, "heel_track", "track", -0.031, 0.031, y_m, y_m + 0.008, 0.115, 0.30)
    span(ski, "heel_pad", "afd", -0.033, 0.033, y_m, -0.0005, 0.115, 0.165)
    if g.race:
        span(ski, "heel_body", "bind", -0.035, 0.035, y_m, 0.050, 0.188, 0.272)
    else:      # turntable heel (Look Pivot style): round base under the housing
        box(ski, "heel_turn_a", "bind2", (0, y_top + 0.011, 0.222), (0.078, 0.022, 0.078))
        box(ski, "heel_turn_b", "bind2", (0, y_top + 0.011, 0.222), (0.078, 0.022, 0.078), (0, 45, 0))
        span(ski, "heel_body", "bind", -0.033, 0.033, y_top + 0.02, 0.052, 0.186, 0.262)
    span(ski, "heel_cup", "bind2", -0.043, 0.043, 0.012, 0.044, 0.163, 0.206)
    rod(ski, "heel_lever", "bind2", (0, 0.046, 0.245), (0, 0.022, 0.325), 0.044, 0.012)
    # brakes (own bone: folded up beside the heel piece with a boot in, swung down without one)
    brake = Bone("brake", ski, [0, y_top + 0.004, 0.125])
    hx = g.w_waist / 2 + 0.0055
    for side, sx in (("l", 1), ("r", -1)):
        rod(brake, f"brake_arm_{side}", "brake_arm", (sx * hx, y_top + 0.005, 0.125), (sx * hx, y_top + 0.010, 0.262), 0.006, 0.006)
        span(brake, f"brake_pad_{side}", "brake_pad", sx * hx - 0.007, sx * hx + 0.007, y_top - 0.004, y_top + 0.016, 0.248, 0.29)
    return brake

# ------------------------------------------------------------------ boot
P = 1 / S                     # one texel in metres
BOOT_HALF = 8.5 * P           # cuff half width: the 0.234 m leg box plus ~16 mm (17 texels)
CUFF_Z0, CUFF_Z1 = -7.75 * P, 10.25 * P
CUFF_TOP = 0.31
CUFF_PIVOT = (0.0, 0.0, 0.0)  # the sole centre: the rider's leg ends there and leans about it

def build_boot(b, brand_bone):
    boot = Bone("boot", brand_bone, [0, 0, 0])
    span(boot, "sole_rear", "sole", -8 * P, 8 * P, -0.003, 0.022, -7.5 * P, 10.5 * P)
    span(boot, "sole_front", "sole", -6 * P, 6 * P, -0.003, 0.022, -11.5 * P, -7.5 * P)
    span(boot, "toe_lug", "lug", -3 * P, 3 * P, 0.0, 0.020, -12.25 * P, -11.5 * P)
    span(boot, "heel_lug", "lug", -3 * P, 3 * P, 0.0, 0.020, 10.5 * P, 11.25 * P)
    span(boot, "shell", "shell", -8 * P, 8 * P, 0.021, 0.13, -8.25 * P, 10.75 * P)
    span(boot, "toe_box", "shell", -6 * P, 6 * P, 0.021, 0.088, -11.25 * P, -7.25 * P)
    rod(boot, "instep", "shell", (0, 0.052, -0.160), (0, 0.112, -0.112), 13 * P, 0.07)
    rod(boot, "buckle_toe", "buckle", (0, 0.0905, -0.1615), (0, 0.1025, -0.1515), 13.4 * P, 0.010)
    rod(boot, "buckle_instep", "buckle", (0, 0.1215, -0.1375), (0, 0.1335, -0.1275), 13.4 * P, 0.010)
    cuff = Bone("cuff", boot, list(CUFF_PIVOT))
    span(cuff, "cuff", "cuff", -BOOT_HALF, BOOT_HALF, 0.112, CUFF_TOP, CUFF_Z0, CUFF_Z1)
    span(cuff, "tongue", "tongue", -5 * P, 5 * P, 0.098, CUFF_TOP + 0.012, CUFF_Z0 - 0.6 * P, CUFF_Z0 + 0.4 * P)
    span(cuff, "spoiler", "spoiler", -3.5 * P, 3.5 * P, 0.15, CUFF_TOP - 0.006, CUFF_Z1 - 0.25 * P, CUFF_Z1 + 0.4 * P)
    span(cuff, "strap", "strap", -9 * P, 9 * P, CUFF_TOP - 0.044, CUFF_TOP - 0.014, CUFF_Z0 - 1.0 * P, CUFF_Z1 + 0.5 * P)
    for k, y in enumerate((0.168, 0.218)):
        span(cuff, f"buckle_c{k}", "buckle", -8.2 * P, 8.2 * P, y, y + 0.016, CUFF_Z0 - 1.0 * P, CUFF_Z0 - 0.62 * P)
        span(cuff, f"lever_c{k}", "lever", BOOT_HALF - 0.1 * P, BOOT_HALF + 0.6 * P, y - 0.003, y + 0.019,
             CUFF_Z0 - 0.9 * P, CUFF_Z0 + 3.5 * P)
    return boot, cuff

# ------------------------------------------------------------------ pole (hand frame; world Y up = model -Y)
POLE_LEN = {"gs": 1.20, "sl": 1.20, "park": 1.10}
GRIP_TOP = 0.128              # the grip's knob, metres above the grip centre (the fist)

def build_pole(b, brand_bone):
    pole = Bone("pole", brand_bone, [0, 0, 0])
    kind = b["pole"]
    Ltot = POLE_LEN[kind]
    tip_y = -(Ltot - GRIP_TOP)                         # world Y of the tip (the pole hangs down)
    span(pole, "grip", "grip", -0.021, 0.021, -0.092, 0.108, -0.024, 0.024)
    span(pole, "grip_knob", "grip_top", -0.026, 0.026, 0.104, GRIP_TOP, -0.030, 0.026)
    rod(pole, "strap", "pole_strap", (0, 0.112, 0.024), (0, -0.03, 0.078), 0.024, 0.004)
    # shaft: GS poles are bent around the body (they trail back under the arms in a tuck); slalom / park straight
    if kind == "gs":
        pts = [A(0, -0.092, 0)]
        for ln, ang in ((0.20, 0), (0.40, 24), (10.0, 12)):
            th = math.radians(ang)
            pts.append(pts[-1] + A(0, -math.cos(th), math.sin(th)) * ln)
        # trim the last piece so the tip ends at the pole's length below the knob
        p2, th = pts[2], math.radians(12)
        ln = (p2[1] - tip_y) / math.cos(th)
        pts[3] = p2 + A(0, -math.cos(th), math.sin(th)) * ln
    else:
        pts = [A(0, -0.092, 0), A(0, tip_y, 0)]
    d = 0.019 if kind != "park" else 0.018
    end = pts[-1]
    last_dir = (pts[-1] - pts[-2]) / np.linalg.norm(pts[-1] - pts[-2])
    shaft_end = end - last_dir * 0.035
    seg_pts = pts[:-1] + [shaft_end]
    for k in range(len(seg_pts) - 1):
        shaft(pole, f"shaft{k}", "pole_shaft", seg_pts[k], seg_pts[k + 1], d, d, seg=k, nseg=len(seg_pts) - 1)
    shaft(pole, "ferrule", "pole_ferrule", shaft_end - last_dir * 0.002, end, 0.011, 0.011)
    # basket: race cone (small), park basket (bigger); two squares at 45 deg read as a disc
    bw = 0.05 if kind != "park" else 0.075
    bc = end - last_dir * 0.095
    ang = math.atan2(last_dir[2], -last_dir[1])
    Rb = rx(-ang)
    Cube(pole, "basket_a", "basket", bc, (bw, 0.008, bw), Rb)
    Cube(pole, "basket_b", "basket", bc, (bw, 0.008, bw), Rb @ ry(math.radians(45)))
    return pole, Ltot

# ------------------------------------------------------------------ rig per brand
class Rig:
    def __init__(self, b):
        self.b = b
        self.root = Bone("root", None, [0, 0, 0])
        self.brand = Bone(b["id"], self.root, [0, 0, 0])
        self.ski, self.g = build_ski(b, self.brand)
        self.brake = build_binding(b, self.ski, self.g)
        self.boot, self.cuff = build_boot(b, self.brand)
        self.pole, self.pole_len = build_pole(b, self.brand)
    def bones(self):
        return [x for x in self.brand.walk()]
    def cubes(self, which=None):
        out = []
        for bn in self.bones():
            sub = "pole" if self._under(bn, self.pole) else "boot" if self._under(bn, self.boot) else "ski"
            if which is None or sub in which: out += bn.cubes
        return out
    @staticmethod
    def _under(bn, top):
        while bn is not None:
            if bn is top: return True
            bn = bn.parent
        return False

# ------------------------------------------------------------------ UV packing (shelf packer, 1 texel margin)
def pack(rig):
    def uv_box(c):
        dx, dy, dz = c.size
        return int(math.ceil(2 * dz + 2 * dx)) + 1, int(math.ceil(dz + dy)) + 1
    u = v = shelf = 0
    for c in sorted(rig.cubes(), key=lambda c: (-uv_box(c)[1], -uv_box(c)[0])):
        w, h = uv_box(c)
        if u + w > TEX_W:
            u, v = 0, v + shelf; shelf = 0
        c.u, c.v = u, v
        u += w; shelf = max(shelf, h)
    rows = v + shelf
    if rows > TEX_H:
        sys.exit(f"{rig.b['id']}: texture too small: needs {rows} rows")
    return rows

# ------------------------------------------------------------------ font (3x5, a few wider glyphs)
FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "C": [".##", "#..", "#..", "#..", ".##"],
    "D": ["##.", "#.#", "#.#", "#.#", "##."], "E": ["###", "#..", "##.", "#..", "###"],
    "F": ["###", "#..", "##.", "#..", "#.."], "G": [".##", "#..", "#.#", "#.#", ".##"],
    "H": ["#.#", "#.#", "###", "#.#", "#.#"], "I": ["###", ".#.", ".#.", ".#.", "###"],
    "L": ["#..", "#..", "#..", "#..", "###"], "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
    "N": ["#..#", "##.#", "#.##", "#..#", "#..#"], "O": ["###", "#.#", "#.#", "#.#", "###"],
    "P": ["##.", "#.#", "##.", "#..", "#.."], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "V": ["#.#", "#.#", "#.#", "#.#", ".#."], "Y": ["#.#", "#.#", ".#.", ".#.", ".#."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "9": ["###", "#.#", "###", "..#", "##."],
    "2": ["##.", "..#", ".#.", "#..", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "6": [".##", "#..", "###", "#.#", "###"],
    " ": ["..", "..", "..", "..", ".."],
}

def text_len_px(word, stretch=1):
    return sum(len(FONT[ch][0]) * stretch for ch in word) + (len(word) - 1)

def text_hit(word, a0, a, u, stretch=1, rows=5):
    """Is the point (a metres along the ski, u texels across, + = left) on a letter of `word` starting at a0?
    Letters read from the tail toward the tip with their tops toward the ski's left edge, 5 texels tall."""
    k = int(math.floor((a - a0) * S))
    if k < 0: return False
    r = int(math.floor(rows / 2 - u))
    if r < 0 or r >= 5: return False
    for ch in word:
        g = FONT[ch]; w = len(g[0]) * stretch
        if k < w:
            return g[r][k // stretch] == "#"
        k -= w
        if k == 0: return False       # 1 texel gap between letters
        k -= 1
    return False

# ------------------------------------------------------------------ top sheet / base graphics per brand
def mix(c0, c1, t):
    return np.asarray(c0, float) * (1 - t) + np.asarray(c1, float) * t

def top_graphic(b, g, a, u, W):
    """Colour of the top sheet at (a, u). a: metres from the tail end toward the tip; u texels across (+ left)."""
    L, ab = g.L, g.ab
    top, sec, acc = rgb(b["top"]), rgb(b["second"]), rgb(b["accent"])
    fore = ab + 0.33                                   # first free texel in front of the toe piece
    i = b["id"]
    c = top
    if i == "atomic_redster_g9":
        if ab - 0.335 < a < ab + 0.30: c = sec          # black under the plate / binding
        if abs(u) > W / 2 - 1.2 and ab + 0.30 <= a < L - 0.07: c = sec   # black edge piping
        if L - 0.077 < a < L - 0.062: c = sec
        if a >= L - 0.062: c = acc                     # white tip
        if text_hit("ATOMIC", fore + 0.02, a, u): c = acc
        if text_hit("REDSTER", 0.06, a, u): c = sec
        if 0.50 < a < ab - 0.335 and abs(u) < 0.6: c = acc   # white pin line to the plate
        if a < 0.022: c = rgb(0x3A3B40)               # tail protector
    elif i == "rossignol_hero_st":
        if ab - 0.335 < a < ab + 0.30 and abs(u) < W / 2 - 1: c = acc * 0.92
        if L - 0.10 < a < L - 0.07: c = acc
        if a >= L - 0.045: c = sec
        if text_hit("ROSSIGNOL", fore - 0.03, a, u): c = sec
        if text_hit("HERO", 0.10, a, u): c = sec
        if 0.39 < a < 0.43: c = acc
        if a < 0.022: c = rgb(0x3A3B40)
    elif i == "fischer_rc4_wc":
        if a >= L - 0.085: c = sec                     # yellow tip
        if L - 0.10 < a < L - 0.085: c = acc
        if text_hit("FISCHER", fore, a, u): c = sec
        if 0.03 < a < 0.40:
            c = sec
            if text_hit("RC4", 0.06, a, u, stretch=2): c = top
        if 0.40 <= a < 0.42 or ab + 0.30 < a < ab + 0.315: c = acc
        if a < 0.022: c = rgb(0x3A3B40)
    elif i == "armada_arv_96":
        if a < 0.07 or a > L - 0.08: c = acc           # cream tips
        if (a < 0.075 and a > 0.065) or (L - 0.09 < a < L - 0.08): c = sec
        if 0.30 <= a < ab - 0.36 and int(math.floor(a * S + abs(u))) % 7 < 2: c = sec    # orange chevrons
        if ab - 0.34 <= a <= ab + 0.30 and abs(u) < W / 2 - 1.5: c = mix(top, sec, 0.0) * 0.8
        if text_hit("ARMADA", fore, a, u): c = acc
        if text_hit("ARV", 0.08, a, u, stretch=2): c = acc
    elif i == "line_chronic_101":
        if a < 0.06 or a > L - 0.06: c = acc           # black tips
        if 0.06 <= a < 0.075 or L - 0.075 < a <= L - 0.06: c = sec
        if ab - 0.34 <= a <= ab + 0.30 and int(math.floor(a * S)) % 6 < 2: c = top * 0.72  # binding-zone bars
        if text_hit("LINE", fore - 0.02, a, u, stretch=2): c = sec
        if text_hit("CHRONIC", 0.09, a, u): c = sec
    elif i == "faction_prodigy_2":
        if a < 0.05 or a > L - 0.05: c = sec           # black tips with a red ring
        if 0.05 <= a < 0.065 or L - 0.065 < a <= L - 0.05: c = acc
        if ab - 0.34 <= a <= ab + 0.30 and abs(u) < 1.5: c = acc
        if text_hit("FACTION", fore - 0.02, a, u): c = sec
        if text_hit("PRODIGY", 0.10, a, u): c = sec
        if 0.53 < a < 0.545: c = acc
    return np.asarray(c, float)

def base_graphic(b, g, a, u, W):
    """The base (seen from below; u is mirrored by the caller so the lettering reads from below)."""
    base, word = rgb(b["base"]), rgb(b["base_word"])
    c = base
    w = b["maker"]
    st = 1 if (b["race"] or len(w) > 5) else 2
    n = text_len_px(w, st) / S
    if text_hit(w, g.ab - n / 2, a, u, stretch=st): c = word
    if b["race"]:
        c = c * (0.94 if int(a * S * 2) % 2 else 1.0)      # stone-ground structure
    else:
        if a < 0.05 or a > g.L - 0.05: c = word
    return c

# ------------------------------------------------------------------ painter
def paint(rig, seed):
    b = rig.b; g = rig.g
    img = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    rng = np.random.default_rng(seed)
    def put(x, y, col):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            img[y, x, :3] = np.clip(np.round(col), 0, 255); img[y, x, 3] = 255
    def texels(p0, n):
        """Texels whose centre lies on [p0, p0 + n) (at least the one under the middle), with the face index."""
        N = max(1, int(math.ceil(n - 1e-6)))
        out = []
        for t in range(int(math.floor(p0)), int(math.ceil(p0 + n))):
            if p0 - 1e-6 <= t + 0.5 < p0 + n + 1e-6:
                out.append((t, min(N - 1, max(0, int(math.floor(t + 0.5 - p0))))))
        if not out:
            out.append((int(math.floor(p0 + n / 2)), 0))
        return out, N
    def fill(x0, y0, w, h, f):
        cols, W = texels(x0, w)
        rows, H = texels(y0, h)
        for ty, j in rows:
            for tx, i in cols:
                put(tx, ty, f(i, j, W, H))
    sheen = lambda col, k: np.asarray(col, float) * k
    steel = rgb(0x9DA3AC)

    for c in rig.cubes():
        dx, dy, dz = c.size
        u0, v0 = c.u, c.v
        x_top, x_bot = u0 + dz, u0 + dz + dx
        x_w, x_n, x_e, x_s = u0, u0 + dz, u0 + dz + dx, u0 + 2 * dz + dx
        y_tb, y_side = v0, v0 + dz
        m = c.mat
        info = c.info

        def ski_top(i, j, W, H):
            uu = -dx / 2 + i + 0.5
            a = info["a0"] + (H - (j + 0.5)) / S * ((info["a1"] - info["a0"]) * S / H)
            col = top_graphic(b, g, a, uu, dx)
            if i == 0 or i == W - 1: col = mix(col, rgb(b["sidewall"]), 0.35)      # cap bevel
            n = 1 + (rng.random() - 0.5) * 0.03
            return col * n
        def ski_base(i, j, W, H):
            uu = -(-dx / 2 + i + 0.5)                               # mirrored: reads from below
            a = info["a0"] + (H - (j + 0.5)) / S * ((info["a1"] - info["a0"]) * S / H)
            col = base_graphic(b, g, a, uu, dx)
            if i == 0 or i == W - 1: col = steel * 0.9             # steel edges seen from below
            return col
        side_wall = lambda i, j, W, H: rgb(b["sidewall"]) * (1.08 if j == 0 else 0.95)
        side_steel = lambda i, j, W, H: steel * (1.1 if (i % 5) else 0.95)

        if m in ("ski_core", "ski_base", "ski_tip"):
            if m == "ski_base":
                fill(x_top, y_tb, dx, dz, lambda i, j, W, H: rgb(b["sidewall"]))
                fill(x_bot, y_tb, dx, dz, ski_base)
                for x0, w in ((x_w, dz), (x_e, dz), (x_n, dx), (x_s, dx)):
                    fill(x0, y_side, w, dy, side_steel)
            else:
                fill(x_top, y_tb, dx, dz, ski_top)
                fill(x_bot, y_tb, dx, dz, ski_base if m == "ski_tip" else (lambda i, j, W, H: rgb(b["sidewall"])))
                sw = (lambda i, j, W, H: mix(rgb(b["sidewall"]), steel, 0.35)) if m == "ski_tip" else side_wall
                for x0, w in ((x_w, dz), (x_e, dz)):
                    fill(x0, y_side, w, dy, sw)
                # end faces: the race tail is a square cut (tail protector), everything else hidden or tip ends
                endc = rgb(0x3A3B40) if (g.race and c.name.startswith("core") and info["a0"] < 0.01) else rgb(b["sidewall"])
                fill(x_n, y_side, dx, dy, lambda i, j, W, H: endc)
                fill(x_s, y_side, dx, dy, lambda i, j, W, H: endc)
            continue

        # ---------------- everything else: colour per material, mild directional shading, details by name
        col = {
            "plate": rgb(b["plate"]), "plate_top": rgb(b["plate"]) * 1.15 + 6,
            "track": rgb(0x2A2B2F), "afd": rgb(0xB9BDC4),
            "bind": rgb(b["bind"]), "bind2": rgb(b["bind2"]),
            "brake_arm": rgb(0xA9AEB6), "brake_pad": rgb(b["bind2"]) if b["race"] else rgb(0x1E1E20),
            "sole": rgb(0x1B1B1D), "lug": rgb(0x2A2A2D),
            "shell": rgb(b["shell"]), "cuff": rgb(b["cuff"]), "tongue": rgb(b["cuff"]) * 0.82,
            "spoiler": rgb(b["cuff"]) * 0.75, "strap": rgb(b["strap"]),
            "buckle": rgb(b["buckle"]), "lever": rgb(b["buckle"]),
            "grip": rgb(b["grip"]), "grip_top": rgb(b["grip"]) * 1.3 + 10, "pole_strap": rgb(0x202022),
            "pole_shaft": rgb(b["shaft"]), "pole_ferrule": rgb(0x3A3C42), "basket": rgb(b["basket"]),
        }[m]
        def shade(kind):
            return {"top": 1.06, "bottom": 0.80, "side": 0.94, "end": 0.88}[kind]
        def plain(kind, extra=None):
            def f(i, j, W, H):
                k = shade(kind)
                if j == 0 and kind in ("side", "end") and H > 2: k *= 1.10      # lit top row
                if j == H - 1 and kind in ("side", "end") and H > 3: k *= 0.88  # contact shadow
                cc = col * k * (1 + (rng.random() - 0.5) * 0.04)
                if extra: cc = extra(i, j, W, H, cc, kind)
                return cc
            return f
        extra = None
        if m in ("shell", "cuff"):                          # gloss shell: a specular streak on the sides
            def extra(i, j, W, H, cc, kind):
                if kind == "side" and W > 6 and i in (2, 3): return cc * 1.16 + 10       # gloss line
                if kind == "end" and W > 6 and i == W - 3: return cc * 1.12 + 6
                if kind == "top" and m == "cuff": return rgb(b["liner"])       # the opening (leg inside)
                return cc
        elif m == "sole":
            def extra(i, j, W, H, cc, kind):
                if kind == "bottom" and (i + 2 * j) % 4 == 0: return cc * 0.7          # tread
                return cc
        elif m == "strap":
            def extra(i, j, W, H, cc, kind):
                if kind == "end" and W > 6 and abs(i - W / 2) < 2 and 0 < j < H - 1:   # logo patch at the front
                    return rgb(b["accent"]) if b["accent"] != b["strap"] else rgb(0xEEEEEE)
                return cc
        elif m in ("buckle", "lever"):
            def extra(i, j, W, H, cc, kind):
                if m == "buckle" and kind == "end" and (i % 4 == 0): return cc * 0.7   # ladder teeth
                if m == "lever" and kind == "side": return cc * (1.15 if j == 0 else 0.9)
                return cc
        elif m == "bind":
            def extra(i, j, W, H, cc, kind):
                if c.name == "toe_body" and kind == "top" and 1 <= i <= 2 and 1 <= j <= 2:
                    return rgb(0xF2F2F2)                    # DIN window
                if c.name == "heel_body" and kind == "end" and j == 1: return rgb(b["bind2"])
                return cc
        elif m == "plate":
            def extra(i, j, W, H, cc, kind):
                if kind == "side" and j == 0: return rgb(b["plate_accent"])
                return cc
        elif m == "plate_top":
            def extra(i, j, W, H, cc, kind):
                if kind == "top" and (i == 0 or i == W - 1): return rgb(b["plate_accent"])
                return cc
        elif m == "pole_shaft":
            def extra(i, j, W, H, cc, kind):
                # bands (brand colour) near the top of the uppermost piece; the face rows run down the shaft
                if info.get("seg") == 0 and kind == "side" and 3 <= j <= 6: return rgb(b["shaft_band"])
                if info.get("seg") == 0 and kind == "side" and j in (9, 10) : return rgb(b["shaft_band"]) * 0.8
                if kind == "side" and i == 0: return cc * 1.15                              # round-tube sheen
                return cc
        elif m == "grip":
            def extra(i, j, W, H, cc, kind):
                if kind == "side" and j % 3 == 0: return cc * 1.4 + 8                        # finger grooves
                return cc
        elif m == "brake_pad":
            def extra(i, j, W, H, cc, kind):
                return cc
        fill(x_w, y_side, dz, dy, plain("side", extra))
        fill(x_e, y_side, dz, dy, plain("side", extra))
        fill(x_n, y_side, dx, dy, plain("end", extra))
        fill(x_s, y_side, dx, dy, plain("end", extra))
        fill(x_top, y_tb, dx, dz, plain("top", extra))
        fill(x_bot, y_tb, dx, dz, plain("bottom", extra))
    return img

# ------------------------------------------------------------------ Java output
def fl(v): return f"{v:.4f}f"

def java_for(rig):
    b = rig.b
    mname = "brand_" + b["id"]
    lines = [f"    private static void {mname}(PartDefinition root) {{"]
    var = {rig.brand: "brand"}
    lines.append(f'        PartDefinition brand = bone(root, "{b["id"]}", 0f, 0f, 0f, 0f, 0f, 0f);')
    for bn in rig.bones():
        if bn is not rig.brand:
            off, rot = bn.local()
            a, bb, cc = euler_zyx(rot)
            var[bn] = bn.name
            lines.append(f'        PartDefinition {bn.name} = bone({var[bn.parent]}, "{bn.name}", {fl(off[0])}, {fl(off[1])}, {fl(off[2])}, '
                         f'{fl(math.degrees(a))}, {fl(math.degrees(bb))}, {fl(math.degrees(cc))});')
        for c in bn.cubes:
            lp = bn.rot.T @ (c.center - bn.origin)
            ex, ey, ez = euler_zyx(bn.rot.T @ c.rot)
            lines.append(f'        cube({var[bn]}, "{c.name}", {c.u}, {c.v}, {fl(lp[0])}, {fl(lp[1])}, {fl(lp[2])}, '
                         f'{fl(c.size[0])}, {fl(c.size[1])}, {fl(c.size[2])}, {fl(math.degrees(ex))}, {fl(math.degrees(ey))}, {fl(math.degrees(ez))});')
    lines.append("    }")
    return mname, lines

def splice(path, start, end, text):
    src = open(path, encoding="utf-8").read()
    i, j = src.index(start) + len(start), src.index(end)
    open(path, "w", encoding="utf-8", newline="\n").write(src[:i] + text + src[j:])

def write_java(rigs):
    methods, calls = [], []
    for rig in rigs:
        name, lines = java_for(rig)
        calls.append(f"        {name}(root);")
        methods += lines + [""]
    lens = ", ".join(fl(r.pole_len) for r in rigs)
    body = []
    body.append(f"    /** Texture size of every brand's texture (generated). */")
    body.append(f"    public static final int TEX_W = {TEX_W}, TEX_H = {TEX_H};")
    body.append(f"    /** Pole length (knob to tip, m) by {{@link SkiBrand#ordinal()}} (generated). */")
    body.append(f"    private static final float[] POLE_LENGTH = {{{lens}}};")
    body.append("")
    body.append("    public static LayerDefinition createLayer() {")
    body.append("        MeshDefinition mesh = new MeshDefinition();")
    body.append("        PartDefinition root = mesh.getRoot();")
    body += calls
    body.append("        return LayerDefinition.create(mesh, TEX_W, TEX_H);")
    body.append("    }")
    body.append("")
    body += methods
    splice(JAVA, "    // <GENERATED>\n", "    // </GENERATED>\n", "\n".join(body) + "\n")
    print("wrote", os.path.relpath(JAVA, ROOT))

# ------------------------------------------------------------------ preview renderer (ray-cast, vanilla box UVs)
LIGHT0 = np.array([0.2, 1.0, -0.7]); LIGHT0 /= np.linalg.norm(LIGHT0)
LIGHT1 = np.array([-0.2, 1.0, 0.7]); LIGHT1 /= np.linalg.norm(LIGHT1)

def cube_faces(c, M=np.eye(4)):
    """World-space faces (metres, Y up) as (origin, eu, ev, uv-rect), the texel (0, 0) corner at origin, following
    ModelPart.Cube's polygon UVs exactly. M: extra 4x4 transform applied in world space."""
    dx, dy, dz = c.size; u, v = c.u, c.v
    hx, hy, hz = dx / 2, dy / 2, dz / 2
    def P(x, y, z): return c.center + c.rot @ np.array([x, y, z], float)
    def E(x, y, z): return c.rot @ np.array([x, y, z], float)
    fs = [
        (P(-hx, -hy, hz), E(dx, 0, 0), E(0, 0, -dz), (u + dz, v, dx, dz)),               # DOWN (visual top)
        (P(-hx, hy, hz), E(dx, 0, 0), E(0, 0, -dz), (u + dz + dx, v, dx, dz)),           # UP (visual bottom)
        (P(-hx, -hy, hz), E(0, 0, -dz), E(0, dy, 0), (u, v + dz, dz, dy)),               # WEST (-x)
        (P(-hx, -hy, -hz), E(dx, 0, 0), E(0, dy, 0), (u + dz, v + dz, dx, dy)),          # NORTH (-z)
        (P(hx, -hy, -hz), E(0, 0, dz), E(0, dy, 0), (u + dz + dx, v + dz, dz, dy)),      # EAST (+x)
        (P(hx, -hy, hz), E(-dx, 0, 0), E(0, dy, 0), (u + 2 * dz + dx, v + dz, dx, dy)),  # SOUTH (+z)
    ]
    out = []
    for o, eu, ev, uv in fs:
        o_w, eu_w, ev_w = FLIP @ o / S, FLIP @ eu / S, FLIP @ ev / S
        o_w = M[:3, :3] @ o_w + M[:3, 3]
        out.append((o_w, M[:3, :3] @ eu_w, M[:3, :3] @ ev_w, uv))
    return out

def bone_motion(rig, extra):
    """{cube: 4x4 world transform} for runtime bone rotations {bone: (axis, rad)} (model-space rotations)."""
    res = {}
    for bn in rig.bones():
        M = np.eye(4)
        chain = []
        x = bn
        while x is not None:
            chain.append(x); x = x.parent
        for x in reversed(chain):
            if x in extra:
                ax, ang = extra[x]
                Rm = {"x": rx, "y": ry, "z": rz}[ax](ang)
                Rl = x.rot @ Rm @ x.rot.T                   # about the bone's own axes, absolute model space
                Rw = FLIP @ Rl @ FLIP
                p = FLIP @ x.origin / S
                T = np.eye(4); T[:3, :3] = Rw; T[:3, 3] = p - Rw @ p
                M = M @ T
        for c in bn.cubes: res[c] = M
    return res

class Scene:
    def __init__(self): self.items = []           # (cube, 4x4 world transform, texture, tint)
    def add_rig(self, rig, tex, M, which=("ski", "boot"), extra=None, skip=()):
        mot = bone_motion(rig, extra or {})
        for c in rig.cubes(which):
            if c.name in skip: continue
            self.items.append((c, M @ mot[c], tex))
    def add_box(self, center, size, R, color):
        self.items.append(("box", (np.asarray(center, float), np.asarray(size, float), R, np.asarray(color, float)), None))

def mat4(R=np.eye(3), t=(0, 0, 0)):
    M = np.eye(4); M[:3, :3] = R; M[:3, 3] = t; return M

def render(scene, cam, size=(900, 560), bg=(214, 222, 232), name=None, ssaa=1):
    """cam: dict(kind='ortho', yaw, pitch, center, scale) or dict(kind='persp', eye, target, fov)."""
    W, H = size[0] * ssaa, size[1] * ssaa
    color = np.zeros((H, W, 3)); color[:] = bg
    depth = np.full((H, W), np.inf)
    alpha = np.zeros((H, W))
    ys, xs = np.mgrid[0:H, 0:W]
    if cam["kind"] == "ortho":
        R = rx(math.radians(cam["pitch"])) @ ry(math.radians(cam["yaw"]))   # world -> view (view: x right, y up, z into screen)
        sc = cam["scale"] * ssaa
        right, up, fwd = R[0], R[1], R[2]
        ctr = np.asarray(cam["center"], float)
        def project(p):
            q = R @ (p - ctr); return np.array([W / 2 + q[0] * sc, H / 2 - q[1] * sc, q[2]])
        def rays(px, py):
            o = ctr[None, :] + ((px + 0.5 - W / 2) / sc)[:, None] * right[None, :] - ((py + 0.5 - H / 2) / sc)[:, None] * up[None, :] - 50 * fwd[None, :]
            d = np.repeat(fwd[None, :], len(px), 0)
            return o, d
    else:
        eye, tgt = np.asarray(cam["eye"], float), np.asarray(cam["target"], float)
        fwd = tgt - eye; fwd /= np.linalg.norm(fwd)
        right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
        up = np.cross(right, fwd)
        f = (H / 2) / math.tan(math.radians(cam["fov"]) / 2)
        def project(p):
            q = p - eye; z = q @ fwd
            z = max(z, 1e-3)
            return np.array([W / 2 + (q @ right) / z * f, H / 2 - (q @ up) / z * f, z])
        def rays(px, py):
            d = fwd[None, :] + ((px + 0.5 - W / 2) / f)[:, None] * right[None, :] - ((py + 0.5 - H / 2) / f)[:, None] * up[None, :]
            d /= np.linalg.norm(d, axis=1)[:, None]
            return np.repeat(eye[None, :], len(px), 0), d
    faces = []
    for c, M, tex in scene.items:
        if c == "box":
            ctr_, siz, R_, col = M
            hx, hy, hz = siz / 2
            for o, eu, ev in (((-hx, hy, -hz), (2 * hx, 0, 0), (0, 0, 2 * hz)), ((-hx, -hy, -hz), (2 * hx, 0, 0), (0, 0, 2 * hz)),
                              ((-hx, -hy, -hz), (0, 2 * hy, 0), (0, 0, 2 * hz)), ((hx, -hy, -hz), (0, 2 * hy, 0), (0, 0, 2 * hz)),
                              ((-hx, -hy, -hz), (2 * hx, 0, 0), (0, 2 * hy, 0)), ((-hx, -hy, hz), (2 * hx, 0, 0), (0, 2 * hy, 0))):
                faces.append((ctr_ + R_ @ np.array(o, float), R_ @ np.array(eu, float), R_ @ np.array(ev, float), None, col))
            continue
        for o, eu, ev, uv in cube_faces(c, M):
            faces.append((o, eu, ev, uv, tex))
    for o, eu, ev, uv, tex in faces:
        n = np.cross(eu, ev); nl = np.linalg.norm(n)
        if nl < 1e-12: continue
        n = n / nl
        corners = np.array([project(p) for p in (o, o + eu, o + ev, o + eu + ev)])
        if cam["kind"] == "persp" and (corners[:, 2] <= 1e-3).all(): continue
        x0, y0 = np.floor(corners[:, :2].min(0)).astype(int) - 1
        x1, y1 = np.ceil(corners[:, :2].max(0)).astype(int) + 1
        x0, y0 = max(0, x0), max(0, y0); x1, y1 = min(W - 1, x1), min(H - 1, y1)
        if x1 < x0 or y1 < y0: continue
        px = xs[y0:y1 + 1, x0:x1 + 1].ravel(); py = ys[y0:y1 + 1, x0:x1 + 1].ravel()
        ro, rd = rays(px, py)
        # solve o + s eu + t ev = ro + lam rd
        C = -rd
        rhs = ro - o
        BxC = np.cross(ev[None, :], C)
        det = BxC @ eu
        ok = np.abs(det) > 1e-12
        det = np.where(ok, det, 1)
        s = np.einsum("ij,ij->i", rhs, BxC) / det
        t = np.einsum("j,ij->i", eu, np.cross(rhs, C)) / det
        lam = np.einsum("j,ij->i", eu, np.cross(ev[None, :], rhs)) / det
        m = ok & (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1) & (lam > 0)
        if not m.any(): continue
        zb = depth[py, px]
        m &= lam < zb
        if not m.any(): continue
        # entity lighting (two directional lights + ambient); two-sided like entityCutoutNoCull
        facing = n if n @ (-rd[0]) > 0 else -n
        shade = min(1.0, 0.4 + 0.6 * (max(0, facing @ LIGHT0) + max(0, facing @ LIGHT1)))
        if uv is None:
            col = np.repeat(tex[None, :] * shade, m.sum(), 0)
            sel = m
        else:
            tu, tv, tw, th = uv
            tx = np.clip(np.floor(tu + s * tw).astype(int), 0, TEX_W - 1)
            ty = np.clip(np.floor(tv + t * th).astype(int), 0, TEX_H - 1)
            texel = tex[ty, tx]
            sel = m & (texel[:, 3] > 0)
            col = texel[sel][:, :3] * shade
        idx_y, idx_x = py[sel], px[sel]
        depth[idx_y, idx_x] = lam[sel]
        color[idx_y, idx_x] = col
        alpha[idx_y, idx_x] = 1
    if ssaa > 1:
        color = color.reshape(H // ssaa, ssaa, W // ssaa, ssaa, 3).mean(axis=(1, 3))
        alpha = alpha.reshape(H // ssaa, ssaa, W // ssaa, ssaa).mean(axis=(1, 3))
    if name:
        os.makedirs(PREVIEW, exist_ok=True)
        Image.fromarray(np.clip(color, 0, 255).astype(np.uint8)).save(os.path.join(PREVIEW, name))
    return color, alpha

# ------------------------------------------------------------------ scenes
def pair(scene, rig, tex, which=("ski", "boot"), x=HALF_STANCE, extra=None, skip=()):
    for sx in (1, -1):
        scene.add_rig(rig, tex, mat4(t=(sx * x, BOOT_SOLE, 0)), which, extra, skip)

def leg_boxes(scene, lean_deg=0.0):
    """The rider's legs as the player model draws them (4 px at 1 m = 17.07 px: 0.234 m), ending at the soles."""
    for sx in (1, -1):
        R = rx(math.radians(-lean_deg))
        piv = np.array([sx * HALF_STANCE, BOOT_SOLE, 0.0])
        c = piv + R @ np.array([0, 0.40, 0.0])
        scene.add_box(c, (0.234, 0.80, 0.234), R, (150, 120, 96))

def previews(rig, tex):
    b = rig.b; n = b["id"]
    L = rig.g.L
    sc = Scene(); pair(sc, rig, tex)
    ctr = (0, 0.12, -0.05)
    render(sc, dict(kind="ortho", yaw=-90, pitch=0, center=ctr, scale=420), size=(1000, 300), name=f"{n}_side.png")
    render(sc, dict(kind="ortho", yaw=-90, pitch=0, center=(0, 0.06, -L / 2 + 0.2), scale=1400), size=(900, 360), name=f"{n}_side_tip.png")
    render(sc, dict(kind="ortho", yaw=0, pitch=-89.9, center=ctr, scale=420), size=(420, 1000), name=f"{n}_top.png")
    render(sc, dict(kind="ortho", yaw=180, pitch=-12, center=(0, 0.15, 0), scale=900), size=(600, 460), name=f"{n}_rear.png")
    render(sc, dict(kind="ortho", yaw=-140, pitch=-25, center=ctr, scale=380), size=(1000, 640), name=f"{n}_three_quarter.png")
    render(sc, dict(kind="persp", eye=(0.0, 1.62, 0.12), target=(0.0, 0.0, -0.75), fov=70), size=(800, 600), name=f"{n}_first_person.png")
    render(sc, dict(kind="ortho", yaw=0, pitch=89.9, center=ctr, scale=420), size=(420, 1000), name=f"{n}_base.png")
    # parked: no boots, brakes down
    sp = Scene(); pair(sp, rig, tex, which=("ski",), extra={rig.brake: ("x", -math.radians(40))})
    render(sp, dict(kind="ortho", yaw=-120, pitch=-18, center=(0, 0.05, -0.05), scale=420), size=(1000, 480), name=f"{n}_parked.png")

def boot_previews(rig, tex):
    n = rig.b["id"]
    sc = Scene(); pair(sc, rig, tex); leg_boxes(sc)
    render(sc, dict(kind="ortho", yaw=-90, pitch=0, center=(0, 0.3, 0), scale=900), size=(800, 640), name=f"{n}_legfit_side.png")
    render(sc, dict(kind="ortho", yaw=180, pitch=0, center=(0, 0.3, 0), scale=900), size=(800, 640), name=f"{n}_legfit_rear.png")
    sb = Scene(); sb.add_rig(rig, tex, mat4(), ("ski", "boot"))
    render(sb, dict(kind="ortho", yaw=-125, pitch=-20, center=(0, 0.12, 0.0), scale=1400), size=(900, 760), name=f"{n}_boot.png")
    render(sb, dict(kind="ortho", yaw=-40, pitch=-20, center=(0, 0.12, -0.03), scale=1400), size=(900, 760), name=f"{n}_boot_front.png")

def pole_preview(rigs, texs):
    sc = Scene()
    for k, (rig, tex) in enumerate(zip(rigs, texs)):
        # the pole's frame: hanging from its grip at (x, 1.2, 0); Y up in the preview world
        sc.add_rig(rig, tex, mat4(t=((k - 2.5) * 0.25, 1.2, 0)), ("pole",))
    render(sc, dict(kind="ortho", yaw=-90, pitch=0, center=(0, 0.62, 0), scale=420), size=(700, 600), name="poles_side.png")
    render(sc, dict(kind="ortho", yaw=-20, pitch=-10, center=(0, 0.62, 0), scale=420), size=(900, 600), name="poles_front.png")
    render(sc, dict(kind="ortho", yaw=-30, pitch=-15, center=(0, 1.2, 0), scale=1800), size=(1100, 520), name="poles_grips.png")

def gallery(rigs, texs):
    sc = Scene()
    for k, (rig, tex) in enumerate(zip(rigs, texs)):
        sc.add_rig(rig, tex, mat4(t=((k - 2.5) * 0.22, BOOT_SOLE, 0)), ("ski",))
    render(sc, dict(kind="ortho", yaw=0, pitch=-89.9, center=(0, 0, -0.05), scale=480), size=(700, 1000), name="gallery_top.png")
    render(sc, dict(kind="ortho", yaw=-90, pitch=0, center=(0, 0.05, -0.05), scale=480), size=(1000, 220), name="gallery_side.png")
    render(sc, dict(kind="ortho", yaw=-150, pitch=-30, center=(0, 0.05, -0.1), scale=420), size=(1000, 700), name="gallery_three_quarter.png")

# ------------------------------------------------------------------ item icon: the pair, diagonal, tips top right
ICON_TEXT = {"atomic_redster_g9": 0xF4F4F4, "rossignol_hero_st": 0xF2F2F2, "fischer_rc4_wc": 0xF6D200,
             "armada_arv_96": 0xF3EBD6, "line_chronic_101": 0xF5C518, "faction_prodigy_2": 0x1A1A1A}

def make_icon(rig, tex):
    """32x32 pixel art: two skis lying side by side on the diagonal (tails bottom left, tips top right). Each ski is
    three anti-diagonal pixel lines (a clean 45 deg band, no checkerboard): the upper-left line lit, the middle line
    carrying the lettering stripe, the lower-right line in shadow; tips (and the twin tips' tails) rounded to the
    middle line, race tails square; both bindings; a one-pixel outline. Colours come from the brand's graphic."""
    b, g = rig.b, rig.g
    N = 32
    out = np.zeros((N, N, 4), np.uint8)
    text = rgb(ICON_TEXT[b["id"]])
    tipc = top_graphic(b, g, g.L - 0.02, 0, 6)
    tailc = top_graphic(b, g, 0.012, 0, 6)
    bind, bind2 = rgb(b["bind"]), rgb(b["bind2"])
    frac_b = g.ab / g.L
    Sx = 22                                            # half length along the diagonal, in x - y steps
    for c0 in (25, 31):                                # lines x + y = c0 .. c0 + 2; outline, gap, outline between
        for l in range(3):
            for x in range(N):
                y = c0 + l - x
                if not 0 <= y < N: continue
                s_ = x - y
                t = (s_ + Sx) / (2 * Sx)
                if not 0 <= t <= 1: continue
                if Sx - s_ < 2.5 and l != 1: continue                       # rounded tip
                if not b["race"] and s_ + Sx < 2.5 and l != 1: continue    # rounded twin tail
                col = rgb(b["top"])
                if t > 0.88: col = tipc
                elif t < 0.07: col = tailc
                elif frac_b - 0.13 < t < frac_b - 0.05: col = bind          # heel piece
                elif frac_b + 0.07 < t < frac_b + 0.15: col = bind          # toe piece
                elif frac_b - 0.05 <= t <= frac_b + 0.07: col = rgb(b["plate"]) if b["race"] else rgb(b["second"]) * 0.85
                elif l == 1 and (0.60 < t < 0.85 or 0.12 < t < frac_b - 0.17): col = text
                if l == 0: col = col * 1.08 + 8
                elif l == 2: col = col * 0.78
                out[y, x, :3] = np.clip(np.round(col), 0, 255); out[y, x, 3] = 255
    solid = out[..., 3] > 0
    pad = np.pad(solid, 1)
    outline = np.zeros_like(solid)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        outline |= pad[1 + dy:1 + dy + N, 1 + dx:1 + dx + N]
    outline &= ~solid
    out[outline] = (30, 26, 36, 255)
    p = os.path.join(RES, "textures/item", f"ski_{b['id']}.png")
    Image.fromarray(out, "RGBA").save(p)
    os.makedirs(PREVIEW, exist_ok=True)
    Image.fromarray(out, "RGBA").resize((256, 256), Image.NEAREST).save(os.path.join(PREVIEW, f"icon_{b['id']}.png"))
    return out

def write_item_model(b):
    p = os.path.join(RES, "models/item", f"ski_{b['id']}.json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"descentmtb:item/ski_{b['id']}"}}, f, indent=2)
        f.write("\n")

def main():
    rigs, texs = [], []
    os.makedirs(os.path.join(RES, "textures/entity/ski"), exist_ok=True)
    for k, b in enumerate(BRANDS):
        rig = Rig(b)
        rows = pack(rig)
        img = paint(rig, 400 + k)
        p = os.path.join(RES, "textures/entity/ski", b["id"] + ".png")
        Image.fromarray(img, "RGBA").save(p)
        n_ski = len(rig.cubes(("ski",))); n_boot = len(rig.cubes(("boot",))); n_pole = len(rig.cubes(("pole",)))
        g = rig.g
        print(f"{b['id']}: ski {n_ski} + boot {n_boot} cubes per foot (pair {2 * (n_ski + n_boot)}), pole {n_pole}; "
              f"rows {rows}/{TEX_H}; widths tip {g.w_tc * 1000:.0f} waist {g.w_waist * 1000:.0f} tail {g.w_tl * 1000:.0f} mm "
              f"(x{WIDTH_SCALE}); flat segments {len(g.segments)}")
        rigs.append(rig); texs.append(np.asarray(Image.open(p).convert("RGBA")))
        write_item_model(b)
    write_java(rigs)
    for rig, tex in zip(rigs, texs):
        make_icon(rig, tex)
    if "--no-preview" in sys.argv:
        return
    gallery(rigs, texs)
    pole_preview(rigs, texs)
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    for rig, tex in zip(rigs, texs):
        if only and rig.b["id"] not in only: continue
        previews(rig, tex)
        boot_previews(rig, tex)
    print("previews in tools/preview/ski/")

if __name__ == "__main__":
    main()
