"""Generates the dirt bike (motocross) model, texture, item icon and preview renders.

  python tools/gen_dirt_bike.py

Writes
  src/main/java/com/descentmtb/client/model/DirtBikeModel.java   (cube table between the GENERATED markers)
  src/main/resources/assets/descentmtb/textures/entity/dirt_bike.png
  src/main/resources/assets/descentmtb/textures/item/dirt_bike.png
  tools/preview/dirt_bike/*.png                                   (side, 3/4 front, 3/4 rear, top)

Geometry is written in metres in the bike's rest pose (steering straight, suspension fully extended): X = rider's
LEFT, Y = UP, Z = BACK (forward is -Z), origin on the ground below the centre of mass, midway between the axles.
The model is built at 32 units per metre (twice the bicycles' density, so the texture carries twice the detail);
the renderer scales it by 1/2. Every cube gets its own UV box; the painter shades each face by its direction
(light from the top left, warm highlights, cool shadows, ambient occlusion at the ends of tubes) the way the
Create mod / vanilla Jappa textures are shaded.
"""
import math, os, re, sys
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
S = 32.0                      # model units per metre
TEX_W, TEX_H = 256, 256

# ------------------------------------------------------------------ geometry constants (metres)
WHEEL_R = 0.355
HALF_WB = 0.74
FRONT_AXLE = np.array([0.0, WHEEL_R, -HALF_WB])
REAR_AXLE = np.array([0.0, WHEEL_R, HALF_WB])
HEAD = np.array([0.0, 1.0, -0.41])            # head tube centre = steering pivot
RAKE = 27.0                                   # steering axis from vertical, degrees
PIVOT = np.array([0.0, 0.42, 0.14])           # swingarm pivot
PEG = np.array([0.17, 0.355, 0.06])           # left footpeg (mirror for the right)
GRIP = np.array([0.40, 1.095, -0.37])         # left grip centre

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

# ------------------------------------------------------------------ bones (model space, rest pose)
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
        off = P.rot.T @ (self.origin - P.origin)
        return off, P.rot.T @ self.rot

AXIS_ROT = rx(math.radians(-RAKE))                    # model-space rotation of the steering axis bone
root = Bone("root", None, [0, 0, 0])
frame = Bone("frame", root, [0, 0, 0])
steer_axis = Bone("steer_axis", frame, HEAD, AXIS_ROT)
steer = Bone("steer", steer_axis, HEAD, AXIS_ROT)
fork_upper = Bone("fork_upper", steer, HEAD, AXIS_ROT)
fork_lower = Bone("fork_lower", steer, HEAD, AXIS_ROT)
front_wheel = Bone("front_wheel", fork_lower, FRONT_AXLE, AXIS_ROT)
swingarm = Bone("swingarm", frame, PIVOT)
rear_wheel = Bone("rear_wheel", swingarm, REAR_AXLE)
BONES = [frame, steer_axis, steer, fork_upper, fork_lower, front_wheel, swingarm, rear_wheel]

def axis_point(dist_down, fwd=0.0):
    """World point on the steering axis, dist_down metres below the head tube centre (+ fwd ahead of the axis)."""
    down = np.array([0, -math.cos(math.radians(RAKE)), -math.sin(math.radians(RAKE))])
    ahead = np.array([0, -math.sin(math.radians(RAKE)), math.cos(math.radians(RAKE))]) * -1  # perpendicular, forward
    return HEAD + down * dist_down + ahead * fwd

AXIS_WORLD_ROT = rx(math.radians(RAKE))   # world-space (Y up) rotation that tilts a box onto the steering axis

class Cube:
    def __init__(self, bone, name, mat, center_world, size_m, rot_world=np.eye(3)):
        self.bone, self.name, self.mat = bone, name, mat
        self.center = to_model(center_world)
        self.size = np.asarray(size_m, float) * S
        self.rot = FLIP @ rot_world @ FLIP            # model-space absolute rotation
        self.u = self.v = 0
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

def mirror(fn, name, *args):
    """Calls a part builder for the left (+X) side and its mirror."""
    fn(name + "_l", 1, *args); fn(name + "_r", -1, *args)

# ================================================================== FRAME (steel double cradle)
F = frame
spar_top = np.array([0.0, 0.95, -0.36])
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

# ================================================================== ENGINE 250 cc single (finned cylinder)
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

# ================================================================== EXHAUST (header right, muffler under the seat)
hdr = [cyl_base + np.array([0, 0.18, -0.10]), np.array([-0.04, 0.60, -0.30]), np.array([-0.12, 0.42, -0.30]),
       np.array([-0.15, 0.36, -0.12]), np.array([-0.15, 0.40, 0.10]), np.array([-0.15, 0.58, 0.30])]
for i in range(len(hdr) - 1):
    tube(F, f"header{i}", "header", hdr[i], hdr[i + 1], 0.04)
tube(F, "muffler", "muffler", [-0.15, 0.58, 0.30], [-0.16, 0.74, 0.72], 0.095, 0.10)
tube(F, "muffler_cap", "black", [-0.16, 0.74, 0.72], [-0.162, 0.755, 0.77], 0.085, 0.09)
tube(F, "muffler_tip", "silver", [-0.162, 0.755, 0.77], [-0.163, 0.762, 0.80], 0.035)
box(F, "muffler_band", "black", [-0.155, 0.66, 0.50], [0.10, 0.11, 0.02], (-22, 0, 0))

# ================================================================== BODYWORK
box(F, "tank", "plastic", [0, 0.87, -0.22], [0.28, 0.17, 0.32], (-6, 0, 0))
box(F, "tank_cap", "black", [0, 0.96, -0.24], [0.06, 0.02, 0.06])
for s, n in ((1, "l"), (-1, "r")):
    box(F, f"shroud_{n}", "shroud", [0.155 * s, 0.76, -0.30], [0.03, 0.26, 0.24], (-12, 0, 6 * s))
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

# ================================================================== FRONT END (USD fork, triple clamps, bars)
U = fork_upper
AW = AXIS_WORLD_ROT
for s, n in ((1, "l"), (-1, "r")):
    x = 0.095 * s
    def on_axis(d, fwd=0.0, side=x):
        p = axis_point(d, fwd); p = p.copy(); p[0] = side; return p
    Cube(U, f"outer_tube_{n}", "fork_outer", on_axis(0.20), [0.058, 0.50, 0.058], AW)        # fat upper tubes
    Cube(U, f"fork_cap_{n}", "silver", on_axis(-0.08), [0.045, 0.03, 0.045], AW)
    Cube(fork_lower, f"inner_tube_{n}", "kashima", on_axis(0.53), [0.044, 0.30, 0.044], AW) # gold stanchions below
    Cube(fork_lower, f"seal_{n}", "black", on_axis(0.455), [0.06, 0.03, 0.06], AW)
    Cube(fork_lower, f"axle_lug_{n}", "fork_lug", on_axis(0.70, 0.01), [0.05, 0.12, 0.07], AW)
    Cube(fork_lower, f"fork_guard_{n}", "plastic_dark", on_axis(0.56, 0.035), [0.05, 0.22, 0.02], AW)
Cube(U, "top_clamp", "triple", axis_point(-0.04), [0.27, 0.03, 0.09], AW)
Cube(U, "lower_clamp", "triple", axis_point(0.13), [0.27, 0.04, 0.10], AW)
Cube(U, "steerer", "silver", axis_point(0.05), [0.04, 0.18, 0.04], AW)
Cube(U, "front_fender", "fender", axis_point(0.17, 0.07), [0.14, 0.03, 0.46], rx(math.radians(-4)))
Cube(U, "front_fender_tip", "fender", axis_point(0.17, 0.07) + np.array([0, 0.03, -0.27]), [0.12, 0.03, 0.10], rx(math.radians(-22)))
Cube(U, "front_plate", "number_plate_front", axis_point(-0.02, 0.10), [0.24, 0.22, 0.02], AW @ rx(math.radians(-8)))
Cube(U, "headlight_mask", "plastic_dark", axis_point(0.06, 0.085), [0.16, 0.08, 0.03], AW)
clamp = axis_point(-0.07)
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
Cube(fork_lower, "front_caliper", "caliper", axis_point(0.66, -0.06) + np.array([0.12, 0, 0]), [0.04, 0.09, 0.07], AW)

# ================================================================== SWINGARM, chain, rear brake
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

# ================================================================== WHEELS
def wheel(bone, prefix, axle, rim_r, tyre_w, front):
    n = 32
    for i in range(n):
        a = 2 * math.pi * i / n
        R = rx(-a)
        d = np.array([0, math.cos(a), math.sin(a)])              # world: up and back around the axle
        seg = 2 * math.pi * WHEEL_R / n * 1.05
        Cube(bone, f"{prefix}tire{i}", "tire", axle + d * (WHEEL_R - 0.045), [tyre_w, 0.07, seg], R)
        Cube(bone, f"{prefix}wall{i}", "tire_wall", axle + d * (rim_r + 0.022), [tyre_w * 0.82, 0.05, 2 * math.pi * (rim_r + 0.02) / n * 1.06], R)
        Cube(bone, f"{prefix}rim{i}", "rim", axle + d * (rim_r - 0.004), [tyre_w * 0.42, 0.026, 2 * math.pi * rim_r / n * 1.08], R)
    for i in range(24):                                          # big square motocross knobs, staggered
        a = 2 * math.pi * (i + 0.5) / 24
        d = np.array([0, math.cos(a), math.sin(a)])
        for k, off in enumerate((-1, 1) if i % 2 else (0,)):
            x = off * tyre_w * 0.30
            Cube(bone, f"{prefix}knob{i}_{k}", "knob", axle + d * (WHEEL_R - 0.005) + np.array([x, 0, 0]),
                 [tyre_w * (0.30 if off else 0.40), 0.02, 0.032], rx(-a))
    for i in range(18):                                          # laced spokes, crossing
        a = 2 * math.pi * i / 18
        side = 1 if i % 2 else -1
        hub_p = axle + np.array([0.03 * side, math.cos(a) * 0.045, math.sin(a) * 0.045])
        rim_p = axle + np.array([0.004 * side, math.cos(a + 0.35 * side) * (rim_r - 0.015), math.sin(a + 0.35 * side) * (rim_r - 0.015)])
        tube(bone, f"{prefix}spoke{i}", "spoke", hub_p, rim_p, 0.007)
    box(bone, f"{prefix}hub", "hub", axle, [0.13 if front else 0.15, 0.08, 0.08])
    box(bone, f"{prefix}hub_l", "hub", axle + np.array([0.04, 0, 0]), [0.025, 0.11, 0.11])
    box(bone, f"{prefix}hub_r", "hub", axle - np.array([0.04, 0, 0]), [0.025, 0.11, 0.11])
    disc_r = 0.135 if front else 0.12
    for i in range(8):
        a = math.pi * i / 8
        Cube(bone, f"{prefix}disc{i}", "disc", axle + np.array([(0.07 if front else -0.07), 0, 0]), [0.006, 2 * disc_r * 0.98, 2 * disc_r * math.tan(math.pi / 16) * 1.06], rx(-a))
    if not front:
        for i in range(6):
            a = math.pi * i / 6
            Cube(bone, f"{prefix}sprocket{i}", "sprocket", axle + np.array([0.135, 0, 0]), [0.008, 0.24, 0.24 * math.tan(math.pi / 12) * 1.08], rx(-a))
wheel(front_wheel, "f_", FRONT_AXLE, 0.267, 0.085, True)
wheel(rear_wheel, "r_", REAR_AXLE, 0.242, 0.115, False)

# ------------------------------------------------------------------ UV packing (shelf packer, 1 texel margin)
CUBES = [c for b in BONES for c in b.cubes]
def uv_box(c):
    dx, dy, dz = c.size
    return int(math.ceil(2 * (dx + dz))) + 1, int(math.ceil(dz + dy)) + 1
order = sorted(CUBES, key=lambda c: -uv_box(c)[1])
u = v = shelf = 0
for c in order:
    w, h = uv_box(c)
    if u + w > TEX_W:
        u, v = 0, v + shelf; shelf = 0
    c.u, c.v = u, v
    u += w; shelf = max(shelf, h)
if v + shelf > TEX_H:
    sys.exit(f"texture too small: needs {v + shelf} rows")
print(f"{len(CUBES)} cubes, texture rows used {v + shelf}/{TEX_H}")

# ------------------------------------------------------------------ painter (hue-shifted ramps)
def hexrgb(h): return np.array([int(h[i:i + 2], 16) for i in (1, 3, 5)], float)
# each material: (shadow, base, highlight, extra) - shadows lean cool/purple, highlights warm/gold
PAL = {
    "frame": ("#3b3550", "#7a7f8f", "#d9d4c4"), "frame_dark": ("#25222f", "#3f4049", "#7d7b75"),
    "engine": ("#26252e", "#4a4c55", "#8e8c84"), "engine_cover": ("#4b4a5a", "#9aa0a6", "#efe7d2"),
    "fins": ("#3a3a48", "#868b92", "#f3ecd8"), "accent": ("#7a2316", "#e0561d", "#ffc35c"),
    "black": ("#141320", "#2a2a33", "#5b5853"), "silver": ("#545a6e", "#a9aeb5", "#fff6df"),
    "radiator": ("#1f2230", "#3a3e48", "#7c7a72"), "header": ("#2c2f63", "#8b5b9a", "#f0c070"),
    "muffler": ("#5d6378", "#b8bcc2", "#fffbea"), "plastic": ("#8c2a14", "#ef5a1c", "#ffb45a"),
    "plastic_dark": ("#16151f", "#2c2b33", "#5e5a52"), "shroud": ("#8c2a14", "#ef5a1c", "#ffb45a"),
    "number_plate": ("#9a9ab0", "#eeeee6", "#fffdf4"), "number_plate_front": ("#9a9ab0", "#eeeee6", "#fffdf4"),
    "fender": ("#a3a3b6", "#f1f0e8", "#fffef8"), "seat": ("#121119", "#25242c", "#57544e"),
    "peg": ("#565c70", "#a2a7ae", "#f8f0dc"), "peg_tooth": ("#3a3e50", "#c3c6ca", "#ffffff"),
    "shock": ("#3b3550", "#5e626c", "#b8b3a6"), "spring": ("#7a1d10", "#d9431b", "#ffad55"),
    "fork_outer": ("#25232e", "#3c3d45", "#8a867c"), "kashima": ("#6f4a17", "#c8962f", "#ffe7a3"),
    "fork_lug": ("#25232e", "#43444c", "#8f8a80"), "triple": ("#7a2316", "#e0561d", "#ffc35c"),
    "bar_pad": ("#141320", "#2b2a32", "#d6d0c0"), "bars": ("#5c6274", "#b5b9bf", "#fff6df"),
    "grip": ("#121119", "#26252d", "#55524c"), "caliper": ("#6f4a17", "#c8962f", "#ffe7a3"),
    "swingarm": ("#545a6e", "#a6abb2", "#fff4dc"), "chain": ("#5a3c18", "#b08840", "#f6d590"),
    "tire": ("#0e0d14", "#24232a", "#4a4842"), "tire_wall": ("#121118", "#2a2930", "#56534c"),
    "knob": ("#0f0e15", "#2b2a31", "#5d5a53"), "rim": ("#16151e", "#2e2e37", "#7f7d77"),
    "spoke": ("#5f6577", "#b7bbc1", "#fff6df"), "hub": ("#7a2316", "#e0561d", "#ffc35c"),
    "disc": ("#5a6072", "#aeb3b9", "#fff8e6"), "sprocket": ("#7a2316", "#e0561d", "#ffc35c"),
}
img = np.zeros((TEX_H, TEX_W, 4), np.uint8)
rng = np.random.default_rng(250)
DIGITS = {"7": ["###", "..#", ".#.", ".#.", ".#."], "2": ["##.", "..#", ".#.", "#..", "###"],
          "5": ["###", "#..", "##.", "..#", "##."]}

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
    # decals
    if mat in ("number_plate", "number_plate_front") and kind == "side" and W >= 7 and H >= 6:
        digits = "77"
        sx = int(x0) + max(0, (W - 7) // 2); sy = int(y0) + max(0, (H - 5) // 2)
        for k, ch in enumerate(digits):
            for r, row in enumerate(DIGITS[ch]):
                for q, cell in enumerate(row):
                    if cell == "#": put(sx + k * 4 + q, sy + r, hexrgb("#1b1a24") if r < 4 else hexrgb("#2d2c38"))
    if mat == "shroud" and kind == "side" and H >= 4:             # race stripes: white and black flash
        for i in range(W):
            j = int(H * 0.55 - i * 0.45)
            for dj, col in ((0, "#f6f1e3"), (1, "#f6f1e3"), (2, "#1b1a24")):
                if 0 <= j + dj < H: put(int(x0) + i, int(y0) + j + dj, hexrgb(col))
    if mat == "bar_pad" and kind == "side" and W >= 5:            # pad logo bar
        for i in range(1, W - 1):
            put(int(x0) + i, int(y0) + H // 2, hexrgb("#e0561d"))
    if mat in ("plastic",) and kind == "top" and W >= 6 and name == "tank":
        for j in range(H):                                       # tank centre stripe
            put(int(x0) + W // 2, int(y0) + j, hexrgb("#f6f1e3"))

for c in CUBES:
    dx, dy, dz = c.size; u0, v0 = c.u, c.v
    # vanilla layout: row0 DOWN(=visual top) at u+dz, UP(=visual bottom) at u+dz+dx; row1 WEST, NORTH, EAST, SOUTH
    paint_face(c.mat, u0 + dz, v0, dx, dz, "top", c.name)
    paint_face(c.mat, u0 + dz + dx, v0, dx, dz, "bottom", c.name)
    paint_face(c.mat, u0, v0 + dz, dz, dy, "side", c.name)
    paint_face(c.mat, u0 + dz, v0 + dz, dx, dy, "end", c.name)
    paint_face(c.mat, u0 + dz + dx, v0 + dz, dz, dy, "side", c.name)
    paint_face(c.mat, u0 + 2 * dz + dx, v0 + dz, dx, dy, "end", c.name)

tex_path = os.path.join(ROOT, "src/main/resources/assets/descentmtb/textures/entity/dirt_bike.png")
Image.fromarray(img, "RGBA").save(tex_path)

# ------------------------------------------------------------------ Java cube table
def f(v): return f"{v:.4f}f"
lines = []
def emit_bone(b):
    off, rot = b.local()
    a, bb, cc = euler_zyx(rot)
    parent = "root" if b.parent is root else b.parent.name
    if b is not frame:
        lines.append(f'        PartDefinition {b.name} = bone({parent}, "{b.name}", {f(off[0])}, {f(off[1])}, {f(off[2])}, '
                     f'{f(math.degrees(a))}, {f(math.degrees(bb))}, {f(math.degrees(cc))});')
    else:
        lines.append(f'        PartDefinition frame = bone(root, "frame", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);')
    for c in b.cubes:
        lp = b.rot.T @ (c.center - b.origin)
        lr = b.rot.T @ c.rot
        ex, ey, ez = euler_zyx(lr)
        lines.append(f'        cube({b.name}, "{c.name}", {c.u}, {c.v}, {f(lp[0])}, {f(lp[1])}, {f(lp[2])}, '
                     f'{f(c.size[0])}, {f(c.size[1])}, {f(c.size[2])}, {f(math.degrees(ex))}, {f(math.degrees(ey))}, {f(math.degrees(ez))});')
for b in BONES:
    emit_bone(b)

java_path = os.path.join(ROOT, "src/main/java/com/descentmtb/client/model/DirtBikeModel.java")
src = open(java_path, encoding="utf-8").read()
start, end = "        // <GENERATED>\n", "        // </GENERATED>\n"
i, j = src.index(start) + len(start), src.index(end)
src = src[:i] + "\n".join(lines) + "\n" + src[j:]
open(java_path, "w", encoding="utf-8").write(src)
print("wrote", os.path.relpath(java_path, ROOT), "and", os.path.relpath(tex_path, ROOT))

# ------------------------------------------------------------------ preview renderer (z-buffered, textured)
def faces_of(c):
    """World-space (metres, Y up) quads of a cube with their UV rects, in the rest pose."""
    dx, dy, dz = c.size; u0, v0 = c.u, c.v
    hx, hy, hz = dx / 2, dy / 2, dz / 2
    # model-space local corners -> absolute model space
    def P(x, y, z): return c.center + c.rot @ np.array([x, y, z])
    # (corner origin, edge u, edge v, uv rect (u, v, w, h)); model-space: -y = visual top
    fs = [
        (P(-hx, -hy, -hz), P(hx, -hy, -hz) - P(-hx, -hy, -hz), P(-hx, -hy, hz) - P(-hx, -hy, -hz), (u0 + dz, v0, dx, dz)),      # top (model DOWN)
        (P(-hx, hy, -hz), P(hx, hy, -hz) - P(-hx, hy, -hz), P(-hx, hy, hz) - P(-hx, hy, -hz), (u0 + dz + dx, v0, dx, dz)),       # bottom
        (P(-hx, -hy, hz), P(-hx, -hy, -hz) - P(-hx, -hy, hz), P(-hx, hy, hz) - P(-hx, -hy, hz), (u0, v0 + dz, dz, dy)),          # west (-x)
        (P(hx, -hy, -hz), P(-hx, -hy, -hz) - P(hx, -hy, -hz), P(hx, hy, -hz) - P(hx, -hy, -hz), (u0 + dz, v0 + dz, dx, dy)),     # north (-z)
        (P(hx, -hy, -hz), P(hx, -hy, hz) - P(hx, -hy, -hz), P(hx, hy, -hz) - P(hx, -hy, -hz), (u0 + dz + dx, v0 + dz, dz, dy)),  # east (+x)
        (P(-hx, -hy, hz), P(hx, -hy, hz) - P(-hx, -hy, hz), P(-hx, hy, hz) - P(-hx, -hy, hz), (u0 + 2 * dz + dx, v0 + dz, dx, dy)),  # south
    ]
    out = []
    for o, eu, ev, uv in fs:
        out.append((FLIP @ o / S, FLIP @ eu / S, FLIP @ ev / S, uv))
    return out

TEX = np.asarray(Image.open(tex_path).convert("RGBA")).astype(float)
LIGHT = np.array([-0.45, 0.8, -0.35]); LIGHT /= np.linalg.norm(LIGHT)

def render(yaw_deg, pitch_deg, size=(900, 560), scale=360, name="preview.png", bg=(222, 214, 196)):
    W, H = size
    R = rx(math.radians(pitch_deg)) @ ry(math.radians(yaw_deg))
    color = np.zeros((H, W, 3)); color[:] = bg
    depth = np.full((H, W), np.inf)
    center = np.array([0, 0.62, 0.0])
    for c in CUBES:
        for o, eu, ev, (tu, tv, tw, th) in faces_of(c):
            n = np.cross(eu, ev); nl = np.linalg.norm(n)
            if nl < 1e-9: continue
            n /= nl
            o2, u2, v2 = R @ (o - center), R @ eu, R @ ev
            n2 = R @ n
            if n2[2] > 0: n2 = -n2
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
            col = texel[..., :3] * shade
            zb[mask] = z[mask]
            color[y0:y1 + 1, x0:x1 + 1][mask] = col[mask]
    Image.fromarray(np.clip(color, 0, 255).astype(np.uint8)).save(os.path.join(ROOT, "tools/preview/dirt_bike", name))
    return color

if "--no-preview" not in sys.argv:
    render(-90, 0, name="side_left.png")
    render(90, 0, name="side_right.png")
    render(-135, -20, name="three_quarter_front.png")
    render(-45, -22, name="three_quarter_rear.png")
    render(0, 89, size=(900, 560), name="top.png")
    # item icon: a slightly turned side view, rendered at 8x and box-filtered down so thin parts blend instead of
    # aliasing, then a crisp one-pixel outline (vanilla item style)
    big = render(-100, -8, size=(256, 256), scale=112, name="icon_big.png", bg=(255, 0, 255))
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
    Image.fromarray(out, "RGBA").save(os.path.join(ROOT, "src/main/resources/assets/descentmtb/textures/item/dirt_bike.png"))
    print("previews in tools/preview/dirt_bike/")
