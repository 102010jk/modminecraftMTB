"""Generates the motorbike models (dirt bike + pit bike): cube tables, textures, item icons and preview renders.

  python tools/gen_dirt_bike.py [--no-preview]

Writes, per bike (dirt_bike / pit_bike)
  src/main/java/com/descentmtb/client/model/{DirtBikeModel,PitBikeModel}.java   (cube table + paint-group table
                                                                                 between the GENERATED / GROUPS markers)
  src/main/java/com/descentmtb/client/model/MotoModel.java                      (stock group colours, GENERATED block)
  src/main/resources/assets/descentmtb/textures/entity/<bike>.png
  src/main/resources/assets/descentmtb/textures/item/<bike>.png
  tools/preview/<bike>/*.png                                                    (side, 3/4 front, 3/4 rear, top,
                                                                                 and a re-painted 3/4 view)

Geometry is written in metres in the bike's rest pose (steering straight, suspension fully extended): X = rider's
LEFT, Y = UP, Z = BACK (forward is -Z), origin on the ground below the centre of mass, midway between the axles.
The models are built at 32 units per metre (twice the bicycles' texel density, so the texture carries twice the
detail); the renderer scales them by 1/2. Every cube gets its own UV box; the painter shades each face by its
direction (light from the top left, warm highlights, cool shadows, ambient occlusion at the ends of tubes) the way
the Create mod / vanilla Jappa textures are shaded.

PAINTABLE GROUPS. The materials of the tintable groups (see MAT_GROUP) are painted in a NEUTRAL grey ramp (same
shading, no hue): the game multiplies them by the group's colour, so the stock colours (DEFAULT_COLOR, = today's
look divided by the ramp's base value) and any player paint keep the shading. Decals that must keep their own
colour (race stripes, the tank stripe) are separate thin cubes of the untinted "decal_*" materials.
"""
import math, os, sys
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 32.0                      # model units per metre
TEX_W, TEX_H = 256, 256
RES = os.path.join(ROOT, "src/main/resources/assets/descentmtb/textures")
JAVA = os.path.join(ROOT, "src/main/java/com/descentmtb/client/model")

# ------------------------------------------------------------------ paint groups
GROUPS = ["plastic", "fender", "frame", "seat", "rim", "spring", "anodized"]
MAT_GROUP = {
    "plastic": "plastic", "shroud": "plastic",                                  # tank, shrouds, scoops, airbox covers
    "fender": "fender", "number_plate": "fender", "number_plate_front": "fender",
    "frame": "frame", "frame_dark": "frame",                                    # tubes, spars, cradle, subframe, mounts
    "seat": "seat", "rim": "rim", "spring": "spring",
    "triple": "anodized", "hub": "anodized", "sprocket": "anodized", "accent": "anodized",
}
# neutral ramp per tintable material: (shadow value, base value, highlight value), 0..1 grey
NEUTRAL = {
    "plastic": (.45, .90, 1.0), "shroud": (.45, .90, 1.0),
    "fender": (.62, .95, 1.0), "number_plate": (.62, .95, 1.0), "number_plate_front": (.62, .95, 1.0),
    "frame": (.45, .92, 1.0), "frame_dark": (.22, .47, .90),
    "seat": (.34, .75, 1.0), "rim": (.34, .75, 1.0), "spring": (.50, .90, 1.0),
    "triple": (.45, .90, 1.0), "hub": (.45, .90, 1.0), "sprocket": (.45, .90, 1.0), "accent": (.45, .90, 1.0),
}
# today's look of each group (the colour the stock bike must show) ...
TARGET_COLOR = {"plastic": 0xef5a1c, "fender": 0xf1f0e8, "frame": 0x7a7f8f, "seat": 0x25242c, "rim": 0x2e2e37,
                "spring": 0xd9431b, "anodized": 0xe0561d}
GROUP_BASE = {"plastic": .90, "fender": .95, "frame": .92, "seat": .75, "rim": .75, "spring": .90, "anodized": .90}
# ... and the tint that reproduces it through the neutral ramp's base value
DEFAULT_COLOR = {g: sum(min(255, round(((TARGET_COLOR[g] >> s) & 255) / GROUP_BASE[g])) << s for s in (16, 8, 0))
                 for g in GROUPS}

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
def to_model(p):           # world metres -> model units (Y down)
    return FLIP @ (np.asarray(p, float) * S)

# ------------------------------------------------------------------ rig: bones + cubes
class Bone:
    def __init__(self, name, parent, origin_world, rot_model=np.eye(3)):
        self.name, self.parent = name, parent
        self.origin = to_model(origin_world)          # absolute model-space position of the pivot
        self.rot = rot_model                          # absolute model-space rotation
        self.cubes = []
    def local(self):
        if self.parent is None:
            return self.origin, self.rot
        P = self.parent
        return P.rot.T @ (self.origin - P.origin), P.rot.T @ self.rot
    def path(self):
        return self.name if self.parent.parent is None else self.parent.path() + "/" + self.name

class Cube:
    def __init__(self, bone, name, mat, center_world, size_m, rot_world=np.eye(3)):
        self.bone, self.name, self.mat = bone, name, mat
        self.center = to_model(center_world)
        self.size = np.asarray(size_m, float) * S
        self.rot = FLIP @ rot_world @ FLIP            # model-space absolute rotation
        self.u = self.v = 0
        self.group = MAT_GROUP.get(mat)
        bone.cubes.append(self)

def box(bone, name, mat, c, size, rot=(0, 0, 0)):
    return Cube(bone, name, mat, c, size, zyx(*[math.radians(v) for v in rot]))

def tube(bone, name, mat, a, b, d, d2=None):
    """A square tube of thickness d from world point a to b (length along the cube's Z)."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    v = b - a; L = np.linalg.norm(v); n = v / L
    yaw = math.atan2(n[0], n[2]); pitch = math.asin(max(-1, min(1, n[1])))
    R = ry(yaw) @ rx(-pitch)
    return Cube(bone, name, mat, (a + b) / 2, (d, d2 if d2 else d, L), R)

def A(*v):
    return np.array(v, float)

class Rig:
    """The bone chain every motorbike shares (names are what MotoModel looks up)."""
    def __init__(self, name, head, rake, pivot, front_axle, rear_axle, center):
        self.name, self.head, self.rake = name, A(*head), rake
        self.pivot, self.front_axle, self.rear_axle = A(*pivot), A(*front_axle), A(*rear_axle)
        self.center = A(*center)                       # preview camera target
        self.axis_rot = rx(math.radians(-rake))        # model-space rotation of the steering axis bone
        self.axis_world_rot = rx(math.radians(rake))   # world-space (Y up) rotation tilting a box onto the axis
        self.root = Bone("root", None, [0, 0, 0])
        self.frame = Bone("frame", self.root, [0, 0, 0])
        self.steer_axis = Bone("steer_axis", self.frame, self.head, self.axis_rot)
        self.steer = Bone("steer", self.steer_axis, self.head, self.axis_rot)
        self.fork_upper = Bone("fork_upper", self.steer, self.head, self.axis_rot)
        self.fork_lower = Bone("fork_lower", self.steer, self.head, self.axis_rot)
        self.front_wheel = Bone("front_wheel", self.fork_lower, self.front_axle, self.axis_rot)
        self.swingarm = Bone("swingarm", self.frame, self.pivot)
        self.rear_wheel = Bone("rear_wheel", self.swingarm, self.rear_axle)
        self.bones = [self.frame, self.steer_axis, self.steer, self.fork_upper, self.fork_lower, self.front_wheel,
                      self.swingarm, self.rear_wheel]
    @property
    def cubes(self):
        return [c for b in self.bones for c in b.cubes]
    def axis_point(self, dist_down, fwd=0.0):
        """World point on the steering axis, dist_down metres below the head tube centre (+ fwd ahead of the axis)."""
        r = math.radians(self.rake)
        down = np.array([0, -math.cos(r), -math.sin(r)])
        ahead = np.array([0, math.sin(r), -math.cos(r)])
        return self.head + down * dist_down + ahead * fwd
    def axis_coords(self, p):
        """(distance down the axis, distance ahead of it) of a world point."""
        r = math.radians(self.rake)
        down = np.array([0, -math.cos(r), -math.sin(r)]); ahead = np.array([0, math.sin(r), -math.cos(r)])
        d = np.asarray(p, float) - self.head
        return float(d @ down), float(d @ ahead)

def wheel(bone, prefix, axle, tyre_r, rim_r, tyre_w, front, p):
    """Segmented tyre, sidewall and rim ring, staggered knobs, laced spokes, hub, brake disc (+ sprocket at the rear).
    p: tuning dict (segs, knobs, spokes, tire_off, tire_th, knob_off, knob_len, spoke_hub, hub_k, disc_r, spr_d, spr_x)."""
    n = p["segs"]
    for i in range(n):
        a = 2 * math.pi * i / n
        R = rx(-a)
        d = np.array([0, math.cos(a), math.sin(a)])              # world: up and back around the axle
        seg = 2 * math.pi * tyre_r / n * 1.05
        Cube(bone, f"{prefix}tire{i}", "tire", axle + d * (tyre_r - p["tire_off"]), [tyre_w, p["tire_th"], seg], R)
        Cube(bone, f"{prefix}wall{i}", "tire_wall", axle + d * (rim_r + 0.022), [tyre_w * 0.82, 0.05, 2 * math.pi * (rim_r + 0.02) / n * 1.06], R)
        Cube(bone, f"{prefix}rim{i}", "rim", axle + d * (rim_r - 0.004), [tyre_w * 0.42, 0.026, 2 * math.pi * rim_r / n * 1.08], R)
    kn = p["knobs"]
    for i in range(kn):                                          # big square knobs, staggered
        a = 2 * math.pi * (i + 0.5) / kn
        d = np.array([0, math.cos(a), math.sin(a)])
        for k, off in enumerate((-1, 1) if i % 2 else (0,)):
            x = off * tyre_w * 0.30
            Cube(bone, f"{prefix}knob{i}_{k}", "knob", axle + d * (tyre_r - p["knob_off"]) + np.array([x, 0, 0]),
                 [tyre_w * (0.30 if off else 0.40), 0.02, p["knob_len"]], rx(-a))
    ns = p["spokes"]
    for i in range(ns):                                          # laced spokes, crossing
        a = 2 * math.pi * i / ns
        side = 1 if i % 2 else -1
        hk = p["hub_k"]
        hub_p = axle + np.array([0.03 * hk * side, math.cos(a) * p["spoke_hub"], math.sin(a) * p["spoke_hub"]])
        rim_p = axle + np.array([0.004 * side, math.cos(a + 0.35 * side) * (rim_r - 0.015), math.sin(a + 0.35 * side) * (rim_r - 0.015)])
        tube(bone, f"{prefix}spoke{i}", "spoke", hub_p, rim_p, 0.007)
    hk = p["hub_k"]
    box(bone, f"{prefix}hub", "hub", axle, [(0.13 if front else 0.15) * hk, 0.08 * hk, 0.08 * hk])
    box(bone, f"{prefix}hub_l", "hub", axle + np.array([0.04 * hk, 0, 0]), [0.025 * hk, 0.11 * hk, 0.11 * hk])
    box(bone, f"{prefix}hub_r", "hub", axle - np.array([0.04 * hk, 0, 0]), [0.025 * hk, 0.11 * hk, 0.11 * hk])
    disc_r = p["disc_r"][0 if front else 1]
    for i in range(8):
        a = math.pi * i / 8
        Cube(bone, f"{prefix}disc{i}", "disc", axle + np.array([(0.07 if front else -0.07) * hk, 0, 0]), [0.006, 2 * disc_r * 0.98, 2 * disc_r * math.tan(math.pi / 16) * 1.06], rx(-a))
    if not front:
        for i in range(6):
            a = math.pi * i / 6
            Cube(bone, f"{prefix}sprocket{i}", "sprocket", axle + np.array([p["spr_x"], 0, 0]), [0.008, p["spr_d"], p["spr_d"] * math.tan(math.pi / 12) * 1.08], rx(-a))

def stripes(F, base_name, c, size, rot, phi, white_w=0.045, black_w=0.022, length=0.27, lift=0.006):
    """Diagonal race flash (white + black band) laid over a shroud: thin untinted decal cubes, just proud of its faces.
    c, size, rot (deg x/y/z) describe the shroud; phi (deg) turns the band about the shroud's thin local X axis."""
    R = zyx(*[math.radians(v) for v in rot]) @ rx(math.radians(phi))
    t = size[0] + 2 * lift
    Cube(F, base_name + "_w", "decal_white", c, [t, white_w, length], R)
    Cube(F, base_name + "_k", "decal_black", np.asarray(c, float) + R @ np.array([0, -(white_w + black_w) / 2, 0]), [t, black_w, length], R)

# ================================================================== DIRT BIKE (250 cc motocross)
def build_dirt():
    r = Rig("dirt_bike", head=[0, 1.0, -0.41], rake=27, pivot=[0, 0.42, 0.14], front_axle=[0, 0.355, -0.74],
            rear_axle=[0, 0.355, 0.74], center=[0, 0.62, 0])
    r.cls, r.preview_scale, r.icon_scale = "DirtBikeModel", 360, 112
    PEG = np.array([0.17, 0.355, 0.06]); GRIP = np.array([0.40, 1.095, -0.37])
    HEAD, RAKE, PIVOT, FRONT_AXLE, REAR_AXLE = r.head, r.rake, r.pivot, r.front_axle, r.rear_axle
    swingarm, fork_lower, fork_upper = r.swingarm, r.fork_lower, r.fork_upper

    # ============================================================== FRAME (steel double cradle)
    F = r.frame
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.085 * s
        tube(F, f"spar_{n}", "frame", [x, 0.95, -0.36], [x * 1.4, 0.64, 0.10], 0.055, 0.07)          # main spar head -> pivot
        tube(F, f"spar_drop_{n}", "frame", [x * 1.4, 0.64, 0.10], [x * 1.4, 0.36, 0.13], 0.055, 0.065)
        tube(F, f"cradle_{n}", "frame", [x * 0.7, 0.32, -0.22], [x * 1.25, 0.20, 0.06], 0.042)  # under the engine
        tube(F, f"cradle_rear_{n}", "frame", [x * 1.25, 0.20, 0.06], [x * 1.4, 0.36, 0.13], 0.035)
        tube(F, f"subframe_{n}", "frame_dark", [x * 1.3, 0.70, 0.08], [x * 1.25, 0.86, 0.66], 0.03)
        tube(F, f"subframe_strut_{n}", "frame_dark", [x * 1.4, 0.50, 0.14], [x * 1.25, 0.84, 0.55], 0.025)
        box(F, f"pivot_plate_{n}", "frame", [x * 1.45, 0.42, 0.13], [0.03, 0.14, 0.10])
    tube(F, "down_tube", "frame", [0, 0.93, -0.43], [0, 0.34, -0.25], 0.05)
    for s, n in ((1, "l"), (-1, "r")):
        tube(F, f"down_split_{n}", "frame", [0, 0.36, -0.25], [0.06 * s, 0.30, -0.22], 0.035)
    box(F, "head_tube", "frame", HEAD, [0.07, 0.16, 0.07], (RAKE, 0, 0))
    box(F, "head_gusset", "frame", [0, 0.92, -0.36], [0.06, 0.08, 0.10], (20, 0, 0))
    box(F, "pivot_bolt", "silver", PIVOT, [0.26, 0.03, 0.03])

    # ============================================================== ENGINE 250 cc single (finned cylinder)
    box(F, "crankcase", "engine", [0, 0.40, -0.04], [0.23, 0.27, 0.38])
    box(F, "case_cover_l", "engine_cover", [0.12, 0.40, -0.02], [0.03, 0.19, 0.26])
    box(F, "case_cover_r", "engine_cover", [-0.12, 0.39, -0.08], [0.03, 0.17, 0.18])
    box(F, "clutch_cover", "engine_cover", [-0.135, 0.38, -0.04], [0.02, 0.12, 0.12])
    box(F, "ignition_cover", "accent", [0.137, 0.41, -0.10], [0.012, 0.09, 0.09])
    box(F, "sump", "engine", [0, 0.27, -0.06], [0.16, 0.05, 0.26])
    cyl_base = np.array([0, 0.52, -0.12]); cyl_tilt = 18
    for i in range(6):                                  # cooling fins up the forward-tilted cylinder
        h = 0.035 * i
        c = cyl_base + np.array([0, h * math.cos(math.radians(cyl_tilt)), -h * math.sin(math.radians(cyl_tilt))])
        box(F, f"fin{i}", "fins", c, [0.20 - 0.008 * i, 0.012, 0.17 - 0.006 * i], (cyl_tilt, 0, 0))
    box(F, "cylinder", "engine", cyl_base + np.array([0, 0.09, -0.03]), [0.12, 0.20, 0.11], (cyl_tilt, 0, 0))
    box(F, "head", "engine_cover", cyl_base + np.array([0, 0.22, -0.07]), [0.16, 0.06, 0.14], (cyl_tilt, 0, 0))
    box(F, "head_cap", "accent", cyl_base + np.array([0, 0.255, -0.08]), [0.10, 0.02, 0.09], (cyl_tilt, 0, 0))
    box(F, "spark_cap", "black", cyl_base + np.array([0.06, 0.20, -0.04]), [0.03, 0.04, 0.03])
    box(F, "carb", "silver", [0, 0.60, 0.05], [0.08, 0.07, 0.10])
    box(F, "airbox", "black", [0, 0.70, 0.27], [0.18, 0.14, 0.22])
    box(F, "countershaft", "accent", [0.135, 0.38, 0.05], [0.02, 0.09, 0.09])
    box(F, "kick_lever", "silver", [-0.15, 0.36, 0.10], [0.015, 0.03, 0.16], (-35, 0, 0))
    box(F, "shift_lever", "silver", [0.16, 0.33, 0.02], [0.015, 0.02, 0.12], (8, 0, 0))
    box(F, "brake_pedal", "silver", [-0.16, 0.32, 0.00], [0.015, 0.02, 0.14], (8, 0, 0))
    box(F, "radiator_l", "radiator", [0.13, 0.66, -0.30], [0.04, 0.24, 0.12])
    box(F, "radiator_r", "radiator", [-0.13, 0.66, -0.30], [0.04, 0.24, 0.12])
    box(F, "coolant_hose", "black", [0, 0.56, -0.24], [0.20, 0.03, 0.03])

    # ============================================================== EXHAUST (header right, muffler under the seat)
    hdr = [cyl_base + np.array([0, 0.18, -0.10]), np.array([-0.04, 0.60, -0.30]), np.array([-0.12, 0.42, -0.30]),
           np.array([-0.15, 0.36, -0.12]), np.array([-0.15, 0.40, 0.10]), np.array([-0.15, 0.58, 0.30])]
    for i in range(len(hdr) - 1):
        tube(F, f"header{i}", "header", hdr[i], hdr[i + 1], 0.04)
    tube(F, "muffler", "muffler", [-0.15, 0.58, 0.30], [-0.16, 0.74, 0.72], 0.095, 0.10)
    tube(F, "muffler_cap", "black", [-0.16, 0.74, 0.72], [-0.162, 0.755, 0.77], 0.085, 0.09)
    tube(F, "muffler_tip", "silver", [-0.162, 0.755, 0.77], [-0.163, 0.762, 0.80], 0.035)
    box(F, "muffler_band", "black", [-0.155, 0.66, 0.50], [0.10, 0.11, 0.02], (-22, 0, 0))

    # ============================================================== BODYWORK
    box(F, "tank", "plastic", [0, 0.87, -0.22], [0.28, 0.17, 0.32], (-6, 0, 0))
    tank_r = zyx(math.radians(-6), 0, 0)
    Cube(F, "tank_stripe", "decal_white", np.array([0, 0.87, -0.22]) + tank_r @ np.array([0, 0.085 + 0.004, 0]),
         [0.03, 0.006, 0.30], tank_r)
    box(F, "tank_cap", "black", [0, 0.96, -0.24], [0.06, 0.02, 0.06])
    for s, n in ((1, "l"), (-1, "r")):
        box(F, f"shroud_{n}", "shroud", [0.155 * s, 0.76, -0.30], [0.03, 0.26, 0.24], (-12, 0, 6 * s))
        stripes(F, f"shroud_stripe_{n}", [0.155 * s, 0.76, -0.30], [0.03, 0.26, 0.24], (-12, 0, 6 * s), -35)
        box(F, f"shroud_scoop_{n}", "plastic", [0.165 * s, 0.86, -0.42], [0.025, 0.10, 0.07], (-30, 0, 6 * s))
        box(F, f"side_plate_{n}", "number_plate", [0.165 * s, 0.77, 0.50], [0.025, 0.22, 0.30], (8, 0, 0))
        box(F, f"airbox_cover_{n}", "plastic", [0.15 * s, 0.76, 0.17], [0.025, 0.20, 0.30], (4, 0, 0))
        box(F, f"peg_{n}", "peg", [PEG[0] * s, PEG[1], PEG[2]], [0.10, 0.025, 0.05])
        for k in range(4):                                # toothed, self-cleaning steel pegs
            box(F, f"peg_tooth_{n}{k}", "peg_tooth", [(0.135 + 0.022 * k) * s, PEG[1] + 0.018, PEG[2]], [0.008, 0.012, 0.05])
        box(F, f"peg_mount_{n}", "frame_dark", [0.10 * s, 0.36, 0.06], [0.05, 0.05, 0.05])
    box(F, "seat", "seat", [0, 0.935, 0.18], [0.26, 0.07, 0.74], (2.5, 0, 0))
    box(F, "seat_front", "seat", [0, 0.92, -0.20], [0.22, 0.06, 0.08], (-12, 0, 0))
    box(F, "seat_base", "black", [0, 0.885, 0.20], [0.24, 0.03, 0.66], (2.5, 0, 0))
    box(F, "rear_fender", "fender", [0, 0.905, 0.80], [0.21, 0.03, 0.42], (-9, 0, 0))
    box(F, "rear_fender_tip", "fender", [0, 0.95, 1.03], [0.17, 0.02, 0.10], (-18, 0, 0))
    box(F, "shock_body", "shock", [0, 0.66, 0.24], [0.06, 0.24, 0.06], (-25, 0, 0))
    box(F, "shock_spring", "spring", [0, 0.58, 0.27], [0.085, 0.14, 0.085], (-25, 0, 0))
    box(F, "shock_reservoir", "accent", [-0.05, 0.71, 0.22], [0.035, 0.10, 0.035], (-25, 0, 0))
    box(F, "skid_plate", "frame_dark", [0, 0.185, -0.08], [0.20, 0.015, 0.38])

    # ============================================================== FRONT END (USD fork, triple clamps, bars)
    AW = r.axis_world_rot
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.095 * s
        def on_axis(d, fwd=0.0, side=x):
            p = r.axis_point(d, fwd).copy(); p[0] = side; return p
        Cube(fork_upper, f"outer_tube_{n}", "fork_outer", on_axis(0.20), [0.058, 0.50, 0.058], AW)        # fat upper tubes
        Cube(fork_upper, f"fork_cap_{n}", "silver", on_axis(-0.08), [0.045, 0.03, 0.045], AW)
        Cube(fork_lower, f"inner_tube_{n}", "kashima", on_axis(0.53), [0.044, 0.30, 0.044], AW)           # gold stanchions below
        Cube(fork_lower, f"seal_{n}", "black", on_axis(0.455), [0.06, 0.03, 0.06], AW)
        Cube(fork_lower, f"axle_lug_{n}", "fork_lug", on_axis(0.70, 0.01), [0.05, 0.12, 0.07], AW)
        Cube(fork_lower, f"fork_guard_{n}", "plastic_dark", on_axis(0.56, 0.035), [0.05, 0.22, 0.02], AW)
    U = fork_upper
    Cube(U, "top_clamp", "triple", r.axis_point(-0.04), [0.27, 0.03, 0.09], AW)
    Cube(U, "lower_clamp", "triple", r.axis_point(0.13), [0.27, 0.04, 0.10], AW)
    Cube(U, "steerer", "silver", r.axis_point(0.05), [0.04, 0.18, 0.04], AW)
    Cube(U, "front_fender", "fender", r.axis_point(0.17, 0.07), [0.14, 0.03, 0.46], rx(math.radians(-4)))
    Cube(U, "front_fender_tip", "fender", r.axis_point(0.17, 0.07) + np.array([0, 0.03, -0.27]), [0.12, 0.03, 0.10], rx(math.radians(-22)))
    Cube(U, "front_plate", "number_plate_front", r.axis_point(-0.02, 0.10), [0.24, 0.22, 0.02], AW @ rx(math.radians(-8)))
    Cube(U, "headlight_mask", "plastic_dark", r.axis_point(0.06, 0.085), [0.16, 0.08, 0.03], AW)
    clamp = r.axis_point(-0.07)
    Cube(U, "bar_mount", "black", clamp + np.array([0, 0.03, 0]), [0.08, 0.05, 0.05])
    Cube(U, "bar_pad", "bar_pad", clamp + np.array([0, 0.075, 0.0]), [0.20, 0.05, 0.05])
    bar_mid = np.array([0.0, 1.075, -0.38])
    tube(U, "bar_l", "bars", bar_mid, GRIP - np.array([0.07, 0, 0]), 0.026)
    tube(U, "bar_r", "bars", bar_mid, GRIP * np.array([-1, 1, 1]) + np.array([0.07, 0, 0]), 0.026)
    tube(U, "crossbar", "bars", [-0.12, 1.11, -0.38], [0.12, 1.11, -0.38], 0.02)
    tube(U, "grip_l", "grip", GRIP - np.array([0.07, 0, 0]), GRIP + np.array([0.06, 0, 0]), 0.036)
    tube(U, "grip_r", "grip", GRIP * np.array([-1, 1, 1]) + np.array([0.07, 0, 0]), GRIP * np.array([-1, 1, 1]) - np.array([0.06, 0, 0]), 0.036)
    box(U, "clutch_perch", "black", GRIP - np.array([0.10, 0, 0]), [0.035, 0.04, 0.04])
    box(U, "clutch_lever", "silver", GRIP + np.array([-0.02, -0.005, -0.06]), [0.15, 0.012, 0.02], (0, -14, 0))
    box(U, "brake_res", "black", GRIP * np.array([-1, 1, 1]) + np.array([0.11, 0.035, 0]), [0.04, 0.035, 0.035])
    box(U, "brake_lever", "silver", GRIP * np.array([-1, 1, 1]) + np.array([0.02, -0.005, -0.06]), [0.15, 0.012, 0.02], (0, 14, 0))
    box(U, "throttle", "black", GRIP * np.array([-1, 1, 1]) + np.array([0.075, 0, 0]), [0.025, 0.045, 0.045])
    Cube(fork_lower, "front_caliper", "caliper", r.axis_point(0.66, -0.06) + np.array([0.12, 0, 0]), [0.04, 0.09, 0.07], AW)

    # ============================================================== SWINGARM, chain, rear brake
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.105 * s
        tube(swingarm, f"arm_{n}", "swingarm", [x, PIVOT[1], PIVOT[2] + 0.03], [x, REAR_AXLE[1] + 0.01, REAR_AXLE[2] - 0.03], 0.05, 0.065)
        box(swingarm, f"chain_adjuster_{n}", "silver", [x, REAR_AXLE[1], REAR_AXLE[2] + 0.02], [0.03, 0.04, 0.06])
    box(swingarm, "arm_brace", "swingarm", [0, 0.40, 0.30], [0.20, 0.04, 0.05])
    box(swingarm, "linkage", "frame_dark", [0, 0.33, 0.24], [0.06, 0.05, 0.12], (-15, 0, 0))
    box(swingarm, "chain_slider", "black", [0.11, 0.425, 0.24], [0.02, 0.025, 0.20])
    cs = np.array([0.135, 0.38, 0.05]); rs = REAR_AXLE + np.array([0.135, 0, 0])
    tube(swingarm, "chain_top", "chain", cs + np.array([0, 0.045, 0]), rs + np.array([0, 0.12, 0]), 0.012, 0.016)
    tube(swingarm, "chain_bot", "chain", cs + np.array([0, -0.045, 0]), rs + np.array([0, -0.12, 0]), 0.012, 0.016)
    box(swingarm, "rear_caliper", "caliper", REAR_AXLE + np.array([-0.12, 0.10, -0.08]), [0.035, 0.07, 0.07])

    # ============================================================== WHEELS (21"/19" laced, same overall radius)
    dirt = dict(segs=32, knobs=24, spokes=18, tire_off=0.045, tire_th=0.07, knob_off=0.005, knob_len=0.032,
                spoke_hub=0.045, hub_k=1.0, disc_r=(0.135, 0.12), spr_d=0.24, spr_x=0.135)
    wheel(r.front_wheel, "f_", FRONT_AXLE, 0.355, 0.267, 0.085, True, dirt)
    wheel(r.rear_wheel, "r_", REAR_AXLE, 0.355, 0.242, 0.115, False, dirt)
    return r

# ================================================================== PIT BIKE (125 cc, Kayo / CRF125 style)
def build_pit():
    r = Rig("pit_bike", head=[0, 0.78, -0.30], rake=25, pivot=[0, 0.33, 0.10], front_axle=[0, 0.28, -0.56],
            rear_axle=[0, 0.28, 0.56], center=[0, 0.46, 0])
    r.cls, r.preview_scale, r.icon_scale = "PitBikeModel", 500, 150
    PEG = np.array([0.15, 0.27, 0.04]); GRIP = np.array([0.33, 0.86, -0.28])
    HEAD, RAKE, PIVOT, FRONT_AXLE, REAR_AXLE = r.head, r.rake, r.pivot, r.front_axle, r.rear_axle
    swingarm, fork_lower, fork_upper = r.swingarm, r.fork_lower, r.fork_upper

    # ============================================================== FRAME (steel backbone + cradle)
    F = r.frame
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.045 * s
        tube(F, f"spar_{n}", "frame", [x, 0.735, -0.285], [x * 1.35, 0.64, -0.02], 0.032, 0.04)       # top tube under the tank
        tube(F, f"spar_drop_{n}", "frame", [x * 1.35, 0.64, -0.02], [x * 1.7, 0.40, 0.10], 0.03)      # down to the pivot plate
        tube(F, f"cradle_{n}", "frame", [x * 0.9, 0.30, -0.19], [x * 1.6, 0.235, 0.085], 0.03)       # under the engine
        tube(F, f"subframe_{n}", "frame_dark", [x * 1.35, 0.61, 0.0], [x * 1.1, 0.60, 0.50], 0.025)   # seat rail
        tube(F, f"subframe_strut_{n}", "frame_dark", [x * 1.7, 0.40, 0.11], [x * 1.1, 0.59, 0.44], 0.02)
        box(F, f"pivot_plate_{n}", "frame", [x * 1.7, 0.36, 0.10], [0.02, 0.26, 0.07])
    tube(F, "down_tube", "frame", [0, 0.72, -0.305], [0, 0.31, -0.19], 0.04)
    box(F, "head_tube", "frame", HEAD, [0.05, 0.10, 0.05], (RAKE, 0, 0))
    box(F, "head_gusset", "frame", [0, 0.735, -0.26], [0.045, 0.05, 0.08], (20, 0, 0))
    box(F, "pivot_bolt", "silver", PIVOT, [0.19, 0.025, 0.025])

    # ============================================================== ENGINE 125 cc (horizontal finned cylinder)
    box(F, "crankcase", "engine", [0, 0.33, -0.02], [0.17, 0.20, 0.20])
    box(F, "case_cover_l", "engine_cover", [0.095, 0.33, 0.0], [0.02, 0.15, 0.17])
    box(F, "case_cover_r", "engine_cover", [-0.095, 0.34, -0.04], [0.02, 0.12, 0.12])
    box(F, "clutch_cover", "engine_cover", [-0.108, 0.335, -0.04], [0.016, 0.08, 0.08])
    box(F, "ignition_cover", "accent", [0.108, 0.37, -0.07], [0.012, 0.065, 0.065])
    box(F, "sump", "engine", [0, 0.235, -0.01], [0.12, 0.04, 0.17])
    tilt = 80                                          # cylinder axis: 80 deg from vertical, leaning forward
    ax = np.array([0, math.cos(math.radians(tilt)), -math.sin(math.radians(tilt))])
    cb = np.array([0, 0.385, -0.115])
    for i in range(5):                                 # cooling fins stacked along the axis
        box(F, f"fin{i}", "fins", cb + ax * (0.02 + 0.021 * i), [0.125 - 0.004 * i, 0.012, 0.125 - 0.004 * i], (-tilt, 0, 0))
    box(F, "cylinder", "engine", cb + ax * 0.06, [0.085, 0.10, 0.085], (-tilt, 0, 0))
    box(F, "head", "engine_cover", cb + ax * 0.125, [0.105, 0.05, 0.105], (-tilt, 0, 0))
    box(F, "head_cap", "accent", cb + ax * 0.155, [0.07, 0.012, 0.07], (-tilt, 0, 0))
    box(F, "spark_cap", "black", cb + ax * 0.13 + np.array([0.0, 0.065, 0.0]), [0.025, 0.035, 0.025])
    box(F, "carb", "silver", [0, 0.45, -0.045], [0.05, 0.055, 0.07])
    box(F, "airbox", "black", [0, 0.565, 0.30], [0.12, 0.10, 0.17])
    box(F, "airbox_hose", "black", [0, 0.51, 0.14], [0.04, 0.04, 0.22])
    box(F, "countershaft", "accent", [0.108, 0.30, 0.03], [0.014, 0.05, 0.05])
    box(F, "kick_lever", "silver", [-0.12, 0.28, 0.06], [0.012, 0.025, 0.12], (-35, 0, 0))
    box(F, "shift_lever", "silver", [0.125, 0.26, 0.03], [0.012, 0.016, 0.09], (8, 0, 0))
    box(F, "brake_pedal", "silver", [-0.13, 0.265, 0.0], [0.012, 0.016, 0.10], (8, 0, 0))

    # ============================================================== EXHAUST (header under the engine, small muffler right)
    hdr = [np.array([-0.035, 0.405, -0.245]), np.array([-0.085, 0.37, -0.245]), np.array([-0.105, 0.27, -0.20]),
           np.array([-0.115, 0.215, -0.10]), np.array([-0.12, 0.215, 0.04]), np.array([-0.135, 0.30, 0.13])]
    for i in range(len(hdr) - 1):
        tube(F, f"header{i}", "header", hdr[i], hdr[i + 1], 0.03)
    tube(F, "muffler", "muffler", [-0.135, 0.30, 0.13], [-0.145, 0.43, 0.43], 0.06, 0.065)
    tube(F, "muffler_cap", "black", [-0.145, 0.43, 0.43], [-0.146, 0.445, 0.465], 0.052, 0.056)
    tube(F, "muffler_tip", "silver", [-0.146, 0.445, 0.465], [-0.147, 0.452, 0.485], 0.024)
    box(F, "muffler_band", "black", [-0.14, 0.36, 0.28], [0.065, 0.07, 0.014], (-20, 0, 0))

    # ============================================================== BODYWORK
    tank_c = np.array([0, 0.755, -0.145]); tank_rot = (-6, 0, 0)
    box(F, "tank", "plastic", tank_c, [0.17, 0.105, 0.22], tank_rot)
    tank_r = zyx(math.radians(-6), 0, 0)
    Cube(F, "tank_stripe", "decal_white", tank_c + tank_r @ np.array([0, 0.0525 + 0.004, 0]), [0.025, 0.006, 0.20], tank_r)
    box(F, "tank_cap", "black", [0, 0.812, -0.16], [0.045, 0.014, 0.045])
    for s, n in ((1, "l"), (-1, "r")):
        box(F, f"shroud_{n}", "shroud", [0.098 * s, 0.725, -0.145], [0.022, 0.14, 0.20], (-6, 0, 4 * s))
        stripes(F, f"shroud_stripe_{n}", [0.098 * s, 0.725, -0.145], [0.022, 0.14, 0.20], (-6, 0, 4 * s), -30,
                white_w=0.03, black_w=0.015, length=0.19, lift=0.004)
        box(F, f"shroud_scoop_{n}", "plastic", [0.1 * s, 0.78, -0.255], [0.02, 0.06, 0.05], (-30, 0, 4 * s))
        box(F, f"side_plate_{n}", "number_plate", [0.1 * s, 0.585, 0.44], [0.016, 0.12, 0.16], (6, 0, 0))
        box(F, f"airbox_cover_{n}", "plastic", [0.072 * s, 0.57, 0.30], [0.016, 0.11, 0.17])
        box(F, f"peg_{n}", "peg", [PEG[0] * s, PEG[1], PEG[2]], [0.08, 0.02, 0.045])
        for k in range(3):                              # toothed steel pegs
            box(F, f"peg_tooth_{n}{k}", "peg_tooth", [(0.125 + 0.02 * k) * s, PEG[1] + 0.014, PEG[2]], [0.007, 0.010, 0.045])
        box(F, f"peg_mount_{n}", "frame_dark", [0.095 * s, 0.27, 0.04], [0.04, 0.04, 0.04])
    box(F, "seat", "seat", [0, 0.675, 0.21], [0.19, 0.055, 0.36], (1.5, 0, 0))
    box(F, "seat_front", "seat", [0, 0.67, -0.0], [0.15, 0.05, 0.08], (-10, 0, 0))
    box(F, "seat_base", "black", [0, 0.64, 0.21], [0.17, 0.025, 0.40], (1.5, 0, 0))
    box(F, "rear_fender", "fender", [0, 0.625, 0.62], [0.10, 0.014, 0.26], (-6, 0, 0))
    box(F, "rear_fender_tip", "fender", [0, 0.625, 0.775], [0.08, 0.014, 0.07], (-26, 0, 0))
    tube(F, "shock_body", "shock", [0, 0.375, 0.19], [0, 0.50, 0.15], 0.032)
    tube(F, "shock_spring", "spring", [0, 0.50, 0.15], [0, 0.61, 0.115], 0.055)
    box(F, "shock_mount", "silver", [0, 0.615, 0.11], [0.05, 0.016, 0.016])
    box(F, "skid_plate", "frame_dark", [0, 0.205, -0.07], [0.13, 0.010, 0.22])

    # ============================================================== FRONT END (short USD fork)
    AW = r.axis_world_rot
    d_ax, f_ax = r.axis_coords(FRONT_AXLE)             # the axle's place relative to the steering axis
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.07 * s
        def on_axis(d, fwd=0.0, side=x):
            p = r.axis_point(d, fwd).copy(); p[0] = side; return p
        Cube(fork_upper, f"outer_tube_{n}", "fork_outer", on_axis(0.10), [0.042, 0.32, 0.042], AW)
        Cube(fork_upper, f"fork_cap_{n}", "silver", on_axis(-0.07), [0.032, 0.02, 0.032], AW)
        Cube(fork_lower, f"inner_tube_{n}", "kashima", on_axis(0.375), [0.032, 0.31, 0.032], AW)
        Cube(fork_lower, f"seal_{n}", "black", on_axis(0.275), [0.046, 0.02, 0.046], AW)
        Cube(fork_lower, f"axle_lug_{n}", "fork_lug", on_axis(d_ax, f_ax, x), [0.04, 0.09, 0.05], AW)
        Cube(fork_lower, f"fork_guard_{n}", "plastic_dark", on_axis(0.40, 0.03), [0.04, 0.14, 0.014], AW)
    U = fork_upper
    Cube(U, "top_clamp", "triple", r.axis_point(-0.07), [0.19, 0.022, 0.07], AW)
    Cube(U, "lower_clamp", "triple", r.axis_point(0.075), [0.19, 0.03, 0.075], AW)
    Cube(U, "steerer", "silver", r.axis_point(0.0), [0.03, 0.15, 0.03], AW)
    Cube(U, "front_plate", "number_plate_front", r.axis_point(-0.015, 0.075), [0.17, 0.15, 0.014], AW @ rx(math.radians(-8)))
    # the front fender rides on the lower legs, over the tyre (tyre outer radius 0.29)
    fa = FRONT_AXLE
    Cube(fork_lower, "front_fender", "fender", fa + np.array([0, 0.318, -0.01]), [0.095, 0.014, 0.20], rx(math.radians(-4)))
    Cube(fork_lower, "front_fender_tip", "fender", fa + np.array([0, 0.30, -0.125]), [0.08, 0.014, 0.08], rx(math.radians(26)))
    Cube(fork_lower, "front_fender_rear", "fender", fa + np.array([0, 0.30, 0.115]), [0.08, 0.014, 0.07], rx(math.radians(-24)))
    box(fork_lower, "fender_stay_l", "frame_dark", fa + np.array([0.06, 0.16, 0.0]), [0.014, 0.30, 0.014])
    box(fork_lower, "fender_stay_r", "frame_dark", fa + np.array([-0.06, 0.16, 0.0]), [0.014, 0.30, 0.014])
    clamp = r.axis_point(-0.075)
    Cube(U, "bar_mount", "black", clamp + np.array([0, 0.022, 0]), [0.06, 0.04, 0.04])
    Cube(U, "bar_pad", "bar_pad", clamp + np.array([0, 0.055, 0.0]), [0.15, 0.04, 0.04])
    bar_mid = np.array([0.0, 0.855, -0.275])
    tube(U, "bar_l", "bars", bar_mid, GRIP - np.array([0.055, 0, 0]), 0.022)
    tube(U, "bar_r", "bars", bar_mid, GRIP * np.array([-1, 1, 1]) + np.array([0.055, 0, 0]), 0.022)
    tube(U, "crossbar", "bars", [-0.10, 0.885, -0.28], [0.10, 0.885, -0.28], 0.016)
    tube(U, "grip_l", "grip", GRIP - np.array([0.055, 0, 0]), GRIP + np.array([0.055, 0, 0]), 0.03)
    tube(U, "grip_r", "grip", GRIP * np.array([-1, 1, 1]) + np.array([0.055, 0, 0]), GRIP * np.array([-1, 1, 1]) - np.array([0.055, 0, 0]), 0.03)
    box(U, "clutch_perch", "black", GRIP - np.array([0.075, 0, 0]), [0.03, 0.034, 0.034])
    box(U, "clutch_lever", "silver", GRIP + np.array([-0.02, -0.004, -0.05]), [0.12, 0.010, 0.016], (0, -14, 0))
    box(U, "brake_res", "black", GRIP * np.array([-1, 1, 1]) + np.array([0.085, 0.03, 0]), [0.034, 0.03, 0.03])
    box(U, "brake_lever", "silver", GRIP * np.array([-1, 1, 1]) + np.array([0.02, -0.004, -0.05]), [0.12, 0.010, 0.016], (0, 14, 0))
    box(U, "throttle", "black", GRIP * np.array([-1, 1, 1]) + np.array([0.06, 0, 0]), [0.022, 0.038, 0.038])
    Cube(fork_lower, "front_caliper", "caliper", r.axis_point(d_ax - 0.01, f_ax - 0.05) + np.array([0.075, 0, 0]), [0.03, 0.07, 0.055], AW)

    # ============================================================== SWINGARM, chain, rear brake
    for s, n in ((1, "l"), (-1, "r")):
        x = 0.075 * s
        tube(swingarm, f"arm_{n}", "swingarm", [x, PIVOT[1], PIVOT[2] + 0.025], [x, REAR_AXLE[1] + 0.008, REAR_AXLE[2] - 0.025], 0.04, 0.05)
        box(swingarm, f"chain_adjuster_{n}", "silver", [x, REAR_AXLE[1], REAR_AXLE[2] + 0.015], [0.024, 0.035, 0.05])
    box(swingarm, "arm_brace", "swingarm", [0, 0.31, 0.34], [0.15, 0.03, 0.04])
    box(swingarm, "linkage", "frame_dark", [0, 0.31, 0.19], [0.05, 0.04, 0.09], (-15, 0, 0))
    box(swingarm, "chain_slider", "black", [0.105, 0.34, 0.30], [0.016, 0.02, 0.16])
    cs = np.array([0.108, 0.30, 0.03]); rs = REAR_AXLE + np.array([0.105, 0, 0])
    tube(swingarm, "chain_top", "chain", cs + np.array([0, 0.03, 0]), rs + np.array([0, 0.082, 0]), 0.010, 0.014)
    tube(swingarm, "chain_bot", "chain", cs + np.array([0, -0.03, 0]), rs + np.array([0, -0.082, 0]), 0.010, 0.014)
    box(swingarm, "rear_caliper", "caliper", REAR_AXLE + np.array([-0.085, 0.07, -0.06]), [0.028, 0.05, 0.05])

    # ============================================================== WHEELS (14" front / 12" rear, spoked, knobbies)
    pit = dict(segs=28, knobs=20, spokes=16, tire_off=0.04, tire_th=0.06, knob_off=0.01, knob_len=0.026,
               spoke_hub=0.035, hub_k=0.7, disc_r=(0.095, 0.085), spr_d=0.17, spr_x=0.105)
    wheel(r.front_wheel, "f_", FRONT_AXLE, 0.29, 0.18, 0.075, True, pit)
    wheel(r.rear_wheel, "r_", REAR_AXLE, 0.27, 0.155, 0.09, False, pit)
    return r

# ------------------------------------------------------------------ UV packing (shelf packer, 1 texel margin)
def pack(rig):
    def uv_box(c):
        dx, dy, dz = c.size
        return int(math.ceil(2 * (dx + dz))) + 1, int(math.ceil(dz + dy)) + 1
    u = v = shelf = 0
    for c in sorted(rig.cubes, key=lambda c: -uv_box(c)[1]):
        w, h = uv_box(c)
        if u + w > TEX_W:
            u, v = 0, v + shelf; shelf = 0
        c.u, c.v = u, v
        u += w; shelf = max(shelf, h)
    if v + shelf > TEX_H:
        sys.exit(f"{rig.name}: texture too small: needs {v + shelf} rows")
    print(f"{rig.name}: {len(rig.cubes)} cubes, texture rows used {v + shelf}/{TEX_H}")

# ------------------------------------------------------------------ painter (hue-shifted ramps; tintable materials neutral)
def hexrgb(h): return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], float)
def grey_ramp(lo, base, hi):
    """Near-grey ramp: cool shadow, neutral base, warm highlight (values 0..1)."""
    f = lambda c: "#%02x%02x%02x" % tuple(int(round(min(1, max(0, x)) * 255)) for x in c)
    return (f((lo * .95, lo * .96, lo * 1.08)), f((base, base, base)), f((hi, hi * .985, hi * .94)))

# each material: (shadow, base, highlight) - shadows lean cool/purple, highlights warm/gold
PAL = {
    "frame": None, "frame_dark": None,
    "engine": ("#26252e", "#4a4c55", "#8e8c84"), "engine_cover": ("#4b4a5a", "#9aa0a6", "#efe7d2"),
    "fins": ("#3a3a48", "#868b92", "#f3ecd8"), "accent": None,
    "black": ("#141320", "#2a2a33", "#5b5853"), "silver": ("#545a6e", "#a9aeb5", "#fff6df"),
    "radiator": ("#1f2230", "#3a3e48", "#7c7a72"), "header": ("#2c2f63", "#8b5b9a", "#f0c070"),
    "muffler": ("#5d6378", "#b8bcc2", "#fffbea"), "plastic": None,
    "plastic_dark": ("#16151f", "#2c2b33", "#5e5a52"), "shroud": None,
    "number_plate": None, "number_plate_front": None, "fender": None, "seat": None,
    "peg": ("#565c70", "#a2a7ae", "#f8f0dc"), "peg_tooth": ("#3a3e50", "#c3c6ca", "#ffffff"),
    "shock": ("#3b3550", "#5e626c", "#b8b3a6"), "spring": None,
    "fork_outer": ("#25232e", "#3c3d45", "#8a867c"), "kashima": ("#6f4a17", "#c8962f", "#ffe7a3"),
    "fork_lug": ("#25232e", "#43444c", "#8f8a80"), "triple": None,
    "bar_pad": ("#141320", "#2b2a32", "#d6d0c0"), "bars": ("#5c6274", "#b5b9bf", "#fff6df"),
    "grip": ("#121119", "#26252d", "#55524c"), "caliper": ("#6f4a17", "#c8962f", "#ffe7a3"),
    "swingarm": ("#545a6e", "#a6abb2", "#fff4dc"), "chain": ("#5a3c18", "#b08840", "#f6d590"),
    "tire": ("#0e0d14", "#24232a", "#4a4842"), "tire_wall": ("#121118", "#2a2930", "#56534c"),
    "knob": ("#0f0e15", "#2b2a31", "#5d5a53"), "rim": None,
    "spoke": ("#5f6577", "#b7bbc1", "#fff6df"), "hub": None,
    "disc": ("#5a6072", "#aeb3b9", "#fff8e6"), "sprocket": None,
    "decal_white": ("#a3a3b6", "#f6f1e3", "#fffef8"), "decal_black": ("#0c0b12", "#1b1a24", "#3a3844"),
}
for _m, (_lo, _ba, _hi) in NEUTRAL.items():
    PAL[_m] = grey_ramp(_lo, _ba, _hi)
DIGITS = {"7": ["###", "..#", ".#.", ".#.", ".#."]}

def paint(rig, seed):
    img = np.zeros((TEX_H, TEX_W, 4), np.uint8)
    rng = np.random.default_rng(seed)
    def ramp(mat, t):
        """t in [-1, 1]: -1 shadow, 0 base, 1 highlight (piecewise linear between hue-shifted stops)."""
        sh, ba, hi = (hexrgb(x) for x in PAL[mat])
        return ba + (hi - ba) * t if t >= 0 else ba + (ba - sh) * t
    def put(x, y, rgb):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            img[y, x, :3] = np.clip(rgb, 0, 255); img[y, x, 3] = 255
    def paint_face(mat, x0, y0, w, h, kind, name):
        """kind: top / bottom / side / end. Directional shading + material detail."""
        W, H = max(1, int(math.ceil(w))), max(1, int(math.ceil(h)))
        base = {"top": 0.45, "bottom": -0.75, "side": 0.0, "end": -0.25}[kind]
        for j in range(H):
            for i in range(W):
                t = base
                if kind in ("side", "end") and H > 2:              # light from above: top lighter, bottom darker
                    t += 0.35 - 0.7 * j / (H - 1)
                if j == 0 and kind != "bottom": t += 0.35            # 1 px rim light on the top edge
                if j == H - 1 and H > 2: t -= 0.35                   # contact shadow
                if i == W - 1 and W > 2 and kind != "top": t -= 0.15 # right edge falls away from the light
                n = rng.random()
                if mat in ("tire", "knob", "tire_wall", "seat", "grip", "bar_pad"):
                    t += (n - 0.5) * 0.25                            # rubber / foam grain
                elif mat in ("engine", "frame_dark", "fork_outer", "fork_lug", "plastic_dark"):
                    t += (n - 0.5) * 0.12
                elif mat in ("muffler", "silver", "bars", "swingarm", "disc"):
                    t += 0.12 * math.sin(i * 1.7 + j * 0.3)          # brushed streaks
                elif mat in ("plastic", "shroud", "fender", "triple", "number_plate", "number_plate_front"):
                    if kind == "side" and i == j % max(2, W) and W > 3: t += 0.3   # specular streak on gloss plastic
                if mat == "header":                                  # heat tint: gold at the port, blue-violet further on
                    t = max(-1, min(1, 0.8 - 1.6 * (i / max(1, W - 1))))
                if mat == "fins" and kind == "side":
                    t += 0.25 if j % 2 == 0 else -0.25
                if mat == "chain" and kind == "side":
                    t += 0.4 if i % 2 == 0 else -0.5
                if mat == "seat" and kind == "top":
                    if i % 3 == 0 and j % 2 == 0: t += 0.35          # gripper pattern
                    if j == 1 or j == H - 2: t += 0.45               # stitched seams
                if mat == "frame" and kind == "side" and (i <= 1 or i >= W - 2):
                    t -= 0.4                                         # AO where the tube meets its neighbours / weld bead
                    if i == 1: t += 0.7
                if mat == "radiator" and kind == "side":
                    t += 0.3 if (i + j) % 2 == 0 else -0.3           # louvres
                if mat == "disc" and kind == "side" and (i + 2 * j) % 4 == 0:
                    t -= 0.8                                         # drilled holes
                if mat == "kashima" and kind == "side" and j == int(H * 0.35):
                    t = -0.7                                         # o-ring sag indicator
                put(int(x0) + i, int(y0) + j, ramp(mat, max(-1, min(1, t))))
        # decals (the numbers are dark: they stay dark under any tint of the fender group)
        if mat in ("number_plate", "number_plate_front") and kind == "side" and W >= 7 and H >= 6:
            sx = int(x0) + max(0, (W - 7) // 2); sy = int(y0) + max(0, (H - 5) // 2)
            for k in range(2):
                for rr, row in enumerate(DIGITS["7"]):
                    for q, cell in enumerate(row):
                        if cell == "#": put(sx + k * 4 + q, sy + rr, hexrgb("#1b1a24") if rr < 4 else hexrgb("#2d2c38"))
        if mat == "bar_pad" and kind == "side" and W >= 5:            # pad logo bar
            for i in range(1, W - 1):
                put(int(x0) + i, int(y0) + H // 2, hexrgb("#e0561d"))
    for c in rig.cubes:
        dx, dy, dz = c.size; u0, v0 = c.u, c.v
        # vanilla layout: row0 DOWN(=visual top) at u+dz, UP(=visual bottom) at u+dz+dx; row1 WEST, NORTH, EAST, SOUTH
        paint_face(c.mat, u0 + dz, v0, dx, dz, "top", c.name)
        paint_face(c.mat, u0 + dz + dx, v0, dx, dz, "bottom", c.name)
        paint_face(c.mat, u0, v0 + dz, dz, dy, "side", c.name)
        paint_face(c.mat, u0 + dz, v0 + dz, dx, dy, "end", c.name)
        paint_face(c.mat, u0 + dz + dx, v0 + dz, dz, dy, "side", c.name)
        paint_face(c.mat, u0 + 2 * dz + dx, v0 + dz, dx, dy, "end", c.name)
    return img

# ------------------------------------------------------------------ Java output
def f(v): return f"{v:.4f}f"

def splice(path, start, end, text):
    src = open(path, encoding="utf-8").read()
    i, j = src.index(start) + len(start), src.index(end)
    open(path, "w", encoding="utf-8").write(src[:i] + text + src[j:])

def write_java(rig):
    lines = []
    for b in rig.bones:
        off, rot = b.local()
        a, bb, cc = euler_zyx(rot)
        if b is not rig.frame:
            parent = "root" if b.parent is rig.root else b.parent.name
            lines.append(f'        PartDefinition {b.name} = bone({parent}, "{b.name}", {f(off[0])}, {f(off[1])}, {f(off[2])}, '
                         f'{f(math.degrees(a))}, {f(math.degrees(bb))}, {f(math.degrees(cc))});')
        else:
            lines.append('        PartDefinition frame = bone(root, "frame", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);')
        for c in b.cubes:
            lp = b.rot.T @ (c.center - b.origin)
            ex, ey, ez = euler_zyx(b.rot.T @ c.rot)
            lines.append(f'        cube({b.name}, "{c.name}", {c.u}, {c.v}, {f(lp[0])}, {f(lp[1])}, {f(lp[2])}, '
                         f'{f(c.size[0])}, {f(c.size[1])}, {f(c.size[2])}, {f(math.degrees(ex))}, {f(math.degrees(ey))}, {f(math.degrees(ez))});')
    path = os.path.join(JAVA, rig.cls + ".java")
    splice(path, "        // <GENERATED>\n", "        // </GENERATED>\n", "\n".join(lines) + "\n")
    # paint-group table: cube paths from the model root, one row per group
    out = ["    private static final String[][] GROUP_CUBES = {\n"]
    for g in GROUPS:
        names = [f'"{c.bone.path()}/{c.name}"' for c in rig.cubes if c.group == g]
        out.append(f"        // {g}\n        {{")
        row, ln = [], 0
        for n_ in names:
            if ln + len(n_) > 100:
                out.append(", ".join(row) + ",\n         "); row, ln = [], 0
            row.append(n_); ln += len(n_) + 2
        out.append(", ".join(row) + "},\n")
    out.append("    };\n")
    splice(path, "    // <GROUPS>\n", "    // </GROUPS>\n", "".join(out))
    print("wrote", os.path.relpath(path, ROOT))

def write_moto_defaults():
    txt = "    /** Tint (0xRRGGBB) that reproduces the stock look of each group through the neutral paint ramp (generated). */\n"
    txt += "    private static final int[] DEFAULTS = {" + ", ".join(f"0x{DEFAULT_COLOR[g]:06x}" for g in GROUPS) + "};\n"
    splice(os.path.join(JAVA, "MotoModel.java"), "    // <GENERATED>\n", "    // </GENERATED>\n", txt)

# ------------------------------------------------------------------ preview renderer (z-buffered, textured, tinted)
def faces_of(c):
    """World-space (metres, Y up) quads of a cube with their UV rects, in the rest pose."""
    dx, dy, dz = c.size; u0, v0 = c.u, c.v
    hx, hy, hz = dx / 2, dy / 2, dz / 2
    def P(x, y, z): return c.center + c.rot @ np.array([x, y, z])      # model-space local corner -> absolute model space
    fs = [
        (P(-hx, -hy, -hz), P(hx, -hy, -hz) - P(-hx, -hy, -hz), P(-hx, -hy, hz) - P(-hx, -hy, -hz), (u0 + dz, v0, dx, dz)),      # top (model DOWN)
        (P(-hx, hy, -hz), P(hx, hy, -hz) - P(-hx, hy, -hz), P(-hx, hy, hz) - P(-hx, hy, -hz), (u0 + dz + dx, v0, dx, dz)),       # bottom
        (P(-hx, -hy, hz), P(-hx, -hy, -hz) - P(-hx, -hy, hz), P(-hx, hy, hz) - P(-hx, -hy, hz), (u0, v0 + dz, dz, dy)),          # west (-x)
        (P(hx, -hy, -hz), P(-hx, -hy, -hz) - P(hx, -hy, -hz), P(hx, hy, -hz) - P(hx, -hy, -hz), (u0 + dz, v0 + dz, dx, dy)),     # north (-z)
        (P(hx, -hy, -hz), P(hx, -hy, hz) - P(hx, -hy, -hz), P(hx, hy, -hz) - P(hx, -hy, -hz), (u0 + dz + dx, v0 + dz, dz, dy)),  # east (+x)
        (P(-hx, -hy, hz), P(hx, -hy, hz) - P(-hx, -hy, hz), P(-hx, hy, hz) - P(-hx, -hy, hz), (u0 + 2 * dz + dx, v0 + dz, dx, dy)),  # south
    ]
    return [(FLIP @ o / S, FLIP @ eu / S, FLIP @ ev / S, uv) for o, eu, ev, uv in fs]

LIGHT = np.array([-0.45, 0.8, -0.35]); LIGHT /= np.linalg.norm(LIGHT)

def tint_of(group, colors):
    """The multiplicative tint the game applies to a group's cubes (stock colour when not overridden)."""
    if group is None: return np.ones(3)
    rgb = (colors or {}).get(group, DEFAULT_COLOR[group])
    return np.array([(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255]) / 255.0

def render(rig, tex, yaw_deg, pitch_deg, size=(900, 560), scale=None, name="preview.png", bg=(222, 214, 196), colors=None):
    W, H = size
    scale = scale or rig.preview_scale
    R = rx(math.radians(pitch_deg)) @ ry(math.radians(yaw_deg))
    color = np.zeros((H, W, 3)); color[:] = bg
    depth = np.full((H, W), np.inf)
    TEX = tex.astype(float)
    for c in rig.cubes:
        tint = tint_of(c.group, colors)
        for o, eu, ev, (tu, tv, tw, th) in faces_of(c):
            n = np.cross(eu, ev); nl = np.linalg.norm(n)
            if nl < 1e-9: continue
            n /= nl
            o2, u2, v2 = R @ (o - rig.center), R @ eu, R @ ev
            lam = abs(float(n @ LIGHT)); shade = 0.55 + 0.45 * lam
            p0 = np.array([W / 2 + o2[0] * scale, H / 2 - o2[1] * scale])
            pu = np.array([u2[0] * scale, -u2[1] * scale]); pv = np.array([v2[0] * scale, -v2[1] * scale])
            corners = np.array([p0, p0 + pu, p0 + pv, p0 + pu + pv])
            x0, y0 = np.floor(corners.min(0)).astype(int); x1, y1 = np.ceil(corners.max(0)).astype(int)
            x0, y0 = max(0, x0), max(0, y0); x1, y1 = min(W - 1, x1), min(H - 1, y1)
            if x1 < x0 or y1 < y0: continue
            M = np.array([pu, pv]).T
            if abs(np.linalg.det(M)) < 1e-6: continue
            Mi = np.linalg.inv(M)
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
            rel = np.stack([xs + 0.5 - p0[0], ys + 0.5 - p0[1]], -1) @ Mi.T
            s, t = rel[..., 0], rel[..., 1]
            mask = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
            if not mask.any(): continue
            z = o2[2] + s * u2[2] + t * v2[2]
            zb = depth[y0:y1 + 1, x0:x1 + 1]
            mask &= z < zb
            if not mask.any(): continue
            tx = np.clip((tu + s * tw).astype(int), 0, TEX_W - 1); ty = np.clip((tv + t * th).astype(int), 0, TEX_H - 1)
            texel = TEX[ty, tx]
            mask &= texel[..., 3] > 0
            col = texel[..., :3] * shade * tint
            zb[mask] = z[mask]
            color[y0:y1 + 1, x0:x1 + 1][mask] = col[mask]
    out_dir = os.path.join(ROOT, "tools/preview", rig.name)
    os.makedirs(out_dir, exist_ok=True)
    Image.fromarray(np.clip(color, 0, 255).astype(np.uint8)).save(os.path.join(out_dir, name))
    return color

# a clearly non-stock livery used to prove the tinting
REPAINT = {"plastic": 0x1e5bd8, "fender": 0xfafafa, "frame": 0x1c1c22, "seat": 0x7a1d1d, "rim": 0xd4a017,
           "spring": 0x1e5bd8, "anodized": 0xd4a017}

def make_icon(rig, tex):
    """Item icon: a slightly turned side view rendered at 8x and box-filtered down so thin parts blend instead of
    aliasing, then a crisp one-pixel outline (vanilla item style)."""
    big = render(rig, tex, -100, -8, size=(256, 256), scale=rig.icon_scale, name="icon_big.png", bg=(255, 0, 255))
    key = (big[..., 0] > 250) & (big[..., 1] < 5) & (big[..., 2] > 250)
    rgba = np.zeros((256, 256, 4)); rgba[..., :3] = big; rgba[..., 3] = np.where(key, 0, 255)
    rgba[..., :3] *= (rgba[..., 3:4] / 255)                     # premultiply so the background never bleeds in
    small = rgba.reshape(32, 8, 32, 8, 4).mean(axis=(1, 3))
    alpha = small[..., 3]
    col = np.where(alpha[..., None] > 0, small[..., :3] / np.maximum(alpha[..., None], 1e-6) * 255, 0)
    solid = alpha > 255 * 0.42
    out = np.zeros((32, 32, 4), np.uint8)
    out[solid, :3] = np.clip(col[solid] * 1.08, 0, 255).astype(np.uint8); out[solid, 3] = 255
    outline = np.zeros_like(solid)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        outline |= np.roll(np.roll(solid, dy, 0), dx, 1)
    outline &= ~solid
    out[outline] = (34, 28, 44, 255)
    Image.fromarray(out, "RGBA").save(os.path.join(RES, "item", rig.name + ".png"))

def main():
    write_moto_defaults()
    for rig, seed in ((build_dirt(), 250), (build_pit(), 125)):
        pack(rig)
        img = paint(rig, seed)
        tex_path = os.path.join(RES, "entity", rig.name + ".png")
        Image.fromarray(img, "RGBA").save(tex_path)
        write_java(rig)
        print("wrote", os.path.relpath(tex_path, ROOT))
        if "--no-preview" in sys.argv:
            continue
        tex = np.asarray(Image.open(tex_path).convert("RGBA"))
        render(rig, tex, -90, 0, name="side_left.png")
        render(rig, tex, 90, 0, name="side_right.png")
        render(rig, tex, -135, -20, name="three_quarter_front.png")
        render(rig, tex, -45, -22, name="three_quarter_rear.png")
        render(rig, tex, 0, 89, name="top.png")
        render(rig, tex, -135, -20, name="repainted_three_quarter.png", colors=REPAINT)
        render(rig, tex, -90, 0, name="repainted_side.png", colors=REPAINT)
        make_icon(rig, tex)
        print(f"previews in tools/preview/{rig.name}/")

if __name__ == "__main__":
    main()
