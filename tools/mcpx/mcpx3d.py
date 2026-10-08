"""mcpx3d — Minecraft 3D models: Create-style multi-element block models, box-UV entity
models, auto-painted texture atlases, and a software preview renderer.

Block model (Java JSON, Blockbench-compatible):
    m = BlockModel("bronze_valve", atlas=64)
    m.box("pipe", (4, 4, 0), (12, 12, 16), "copper")          # material name -> ramp + painter
    m.box("blade", (7.5, 2, 7), (8.5, 14, 9), "wood", rot=("z", 22.5, (8, 8, 8)))
    m.paint(MATERIALS); m.save("out/models", namespace="mymod")  # writes models/block/*.json + textures/block/*.png
    render_block_model(m).save("preview.png")

Entity model (Java LayerDefinition + Bedrock geo for Blockbench, box UV):
    e = EntityModel("moss_beetle", 64, 32)
    body = e.bone("body", pivot=(0, 19, 0))
    body.cube((-4, -3, -5), (8, 5, 10), uv=(0, 0), material="shell")
    ...
    e.paint(MATERIALS, features=callback); e.save(...)

Rules baked in (measured from Create/Aeronautics/vanilla models):
  * 1 texel per model unit (16 px per block) - never stretch a small texture over a big face
  * element rotation only 0, +-22.5, +-45 on ONE axis (JSON limitation)
  * median 3-8 elements per Create/Aeronautics block model, max ~25
  * ~25-45% of elements are thin (<=1 px) details: rims, bolts, handles, blade edges
  * every face rect is shaded: lit top/left edge, darker bottom/right edge, material noise
"""
import json, math, os
import numpy as np
from PIL import Image
from mcpx import Ramp, noise, white, quantize, gauss_weights, save, enrich

FACES = ("north", "south", "east", "west", "up", "down")
FACE_LIGHT = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}

# ---------------------------------------------------------------- painting ---
class Material:
    """How one material paints a face rect: ramp + noise + bevel. `streak` = grain direction."""
    def __init__(self, ramp, noise_cells=(4, 4), white_amt=0.3, bevel=True, streak=None,
                 base=0.55, sigma=0.16, edge_dark=False, variety=0.2, detail=None):
        self.ramp, self.noise_cells, self.white_amt = ramp, noise_cells, white_amt
        self.bevel, self.streak, self.base, self.sigma, self.edge_dark, self.variety = bevel, streak, base, sigma, edge_dark, variety
        self.detail = detail or []

    def paint(self, w, h, seed, face="north"):
        n = len(self.ramp)
        S = max(w, h, 4)
        cells = self.noise_cells
        if self.streak == "h": cells = (max(1, cells[0] // 2), cells[1] * 2)
        if self.streak == "v": cells = (cells[0] * 2, max(1, cells[1] // 2))
        # real model faces range from almost flat to busy: vary intensity per face
        k = np.random.default_rng(seed + 31).choice([0.35, 1.0, 2.0], p=[0.3, 0.45, 0.25])
        wa = min(0.6, self.white_amt * (k if k > 1 else 1))
        f = (1 - wa) * noise(seed, S, cells=cells, octaves=2)[:h, :w] + wa * white(seed, S)[:h, :w]
        mu = self.base + {"up": 0.12, "down": -0.18, "east": -0.06, "west": -0.06}.get(face, 0)
        idx = quantize(f, gauss_weights(n, min(.9, max(.1, mu)), self.sigma * k)) if w * h > 1 else np.full((h, w), int(mu * (n - 1)))
        # Measured on 1094 real Create/Aeronautics model faces: faces are almost FLAT
        # (top-left vs bottom-right edge difference ~0.01). Only a faint lit top row.
        if self.bevel and w >= 4 and h >= 4:
            idx[0, :] = np.maximum(idx[0, :], idx[0, :] + 1)
            if self.edge_dark: idx[-1, :] -= 1
        # hand-made model faces carry drawn detail, not just noise (measured: wide spread of
        # per-face contrast in real Create faces). Large faces get one motif chosen by seed.
        if self.detail and w >= 6 and h >= 6:
            rng = np.random.default_rng(seed + 991)
            motif = self.detail[rng.integers(len(self.detail))]
            if motif == "rivets":
                for (yy, xx) in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
                    idx[yy, xx] = n - 1; idx[min(h - 1, yy + 1), min(w - 1, xx + 1)] = 1
            elif motif == "panel":
                y = h // 2 if rng.random() < .5 else max(2, h // 3)
                idx[y, 1:w - 1] = 1; idx[y + 1, 1:w - 1] = np.minimum(idx[y + 1, 1:w - 1] + 1, n - 1)
            elif motif == "planks":
                for y in range(3, h - 1, 4): idx[y, :] = 1
            elif motif == "scratch":
                for k in range(min(w, h) - 3): idx[1 + k, 2 + k] = min(n - 1, idx[1 + k, 2 + k] + 2)
        img = self.ramp.paint(np.clip(idx, 0, n - 1))
        if self.variety and w * h >= 4 and k > 0.5:      # real model faces: ~0.19 distinct colours per pixel
            pad = np.zeros((S, S, 4), np.uint8); pad[:h, :w] = img
            img = enrich(pad, seed, frac=self.variety, hue=3.0, dl=0.006, dc=0.10, cells=(4, 4))[:h, :w]
        return img

def default_materials():
    """A starter set tuned on Create/Aeronautics palettes. Extend per project."""
    R = lambda *a, **k: Ramp(*a, **{**k, "spread": k.get("spread", .4) * 0.62, "chroma_mul": k.get("chroma_mul", 1) * 0.85, "mid_bias": k.get("mid_bias", 0) - 0.06})
    return {
        # metals: smooth faces (low white noise, broad noise cells) + crisp bevel = the Create look
        "andesite": Material(R("#a3a69c", 7, spread=0.40, warm_hi=4, shadow_hue=260, cool_lo=8), white_amt=0.2, sigma=0.16, detail=["rivets", "none", "panel"]),
        "brass": Material(R("#d0a04a", 8, spread=0.55, warm_hi=18, shadow_hue=25, cool_lo=24, hi_desat=1.05), noise_cells=(2, 2), white_amt=0.06, sigma=0.13, detail=["rivets", "panel", "none", "scratch"]),
        "bronze": Material(R("#b8763c", 8, spread=0.52, warm_hi=18, shadow_hue=20, cool_lo=22, hi_desat=1.0), noise_cells=(2, 2), white_amt=0.06, sigma=0.13, detail=["rivets", "panel", "none", "scratch"]),
        "copper": Material(R("#c46a48", 8, spread=0.50, warm_hi=16, shadow_hue=15, cool_lo=14, hi_desat=1.0), noise_cells=(2, 2), white_amt=0.08, sigma=0.14, detail=["panel", "none", "rivets"]),
        "iron": Material(R("#8f9499", 7, spread=0.45, warm_hi=2, shadow_hue=260, cool_lo=12), noise_cells=(2, 2), white_amt=0.08, sigma=0.15, detail=["rivets", "none", "panel"]),
        "dark_iron": Material(R("#4d5157", 6, spread=0.32, warm_hi=2, shadow_hue=265, cool_lo=10), white_amt=0.15),
        "wood": Material(R("#8a6238", 7, spread=0.42, warm_hi=10, shadow_hue=25, cool_lo=12), streak="h", white_amt=0.25, detail=["planks", "none"]),
        "dark_wood": Material(R("#5a3d24", 7, spread=0.38, warm_hi=10, shadow_hue=25, cool_lo=12), streak="h", white_amt=0.25, detail=["planks", "none"]),
        "canvas": Material(R("#d6cdb8", 6, spread=0.2, warm_hi=4, shadow_hue=300, cool_lo=8), bevel=False, white_amt=0.4),
        "red_paint": Material(R("#a8332e", 7, spread=0.42, warm_hi=12, shadow_hue=10, cool_lo=10), white_amt=0.1, sigma=0.15),
        "rubber": Material(R("#2f2c2b", 5, spread=0.22), bevel=False),
        "glow": Material(R("#e89a3a", 5, spread=0.4, warm_hi=20, hi_desat=1.0), bevel=False, white_amt=0.5),
    }

# ------------------------------------------------------------------ packing ---
class Shelf:
    def __init__(self, W, H): self.W, self.H, self.x, self.y, self.row_h = W, H, 0, 0, 0
    def alloc(self, w, h):
        if self.x + w > self.W: self.x, self.y, self.row_h = 0, self.y + self.row_h, 0
        if self.y + h > self.H: raise ValueError(f"atlas {self.W}x{self.H} full - raise atlas size or share faces")
        r = (self.x, self.y); self.x += w; self.row_h = max(self.row_h, h); return r

# ------------------------------------------------------------ block models ---
def _face_dims(f, t, face):
    dx, dy, dz = (abs(t[i] - f[i]) for i in range(3))
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]

class BlockModel:
    def __init__(self, name, atlas=32):
        self.name, self.atlas, self.elements = name, atlas, []

    def box(self, name, frm, to, material, rot=None, faces=FACES, shade=True):
        """rot = (axis, angle in {-45,-22.5,0,22.5,45}, origin)"""
        if rot: assert rot[1] in (-45, -22.5, 0, 22.5, 45), "JSON models only allow 0/±22.5/±45"
        self.elements.append(dict(name=name, frm=tuple(frm), to=tuple(to), mat=material, rot=rot, faces=faces, shade=shade))
        return self

    def paint(self, materials, seed=0):
        """Pack every face into the atlas (identical material+size faces share one rect) and paint it."""
        A = self.atlas
        img = np.zeros((A, A, 4), np.uint8)
        shelf, cache = Shelf(A, A), {}
        items = []
        for ei, e in enumerate(self.elements):
            for face in e["faces"]:
                w, h = _face_dims(e["frm"], e["to"], face)
                items.append((ei, face, max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))))
        items.sort(key=lambda t: -t[3])                       # tall first: better shelf packing
        for ei, face, w, h in items:
            e = self.elements[ei]
            key = (e["mat"], w, h, face in ("up", "down"))
            if key not in cache:
                x, y = shelf.alloc(w, h)
                img[y:y+h, x:x+w] = materials[e["mat"]].paint(w, h, seed + len(cache) * 17, face)
                cache[key] = (x, y, w, h)
            e.setdefault("uv", {})[face] = cache[key]
        self.texture = img
        return img

    def to_json(self, tex_ref):
        k = 16 / self.atlas
        els = []
        for e in self.elements:
            faces = {}
            for face, (x, y, w, h) in e["uv"].items():
                faces[face] = {"uv": [round(x * k, 4), round(y * k, 4), round((x + w) * k, 4), round((y + h) * k, 4)], "texture": "#0"}
            d = {"name": e["name"], "from": list(e["frm"]), "to": list(e["to"]), "faces": faces}
            if e["rot"]:
                ax, ang, org = e["rot"]; d["rotation"] = {"angle": ang, "axis": ax, "origin": list(org)}
            if not e["shade"]: d["shade"] = False
            els.append(d)
        return {"credit": "Made with mcpx3d", "parent": "block/block", "texture_size": [self.atlas, self.atlas],
                "textures": {"0": tex_ref, "particle": tex_ref}, "elements": els,
                "display": {"gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
                            "fixed": {"scale": [0.5, 0.5, 0.5]}}}

    def save(self, root, namespace="mymod"):
        tex = f"{namespace}:block/{self.name}"
        save(self.texture, os.path.join(root, f"assets/{namespace}/textures/block/{self.name}.png"))
        p = os.path.join(root, f"assets/{namespace}/models/block/{self.name}.json")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump(self.to_json(tex), open(p, "w"), indent=1)
        return p

# geometry for the renderer: (p0, pu, pv) corners per face in block space (y up)
def _face_corners(f, t, face):
    x1, y1, z1 = f; x2, y2, z2 = t
    return {
        "north": ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        "south": ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        "east":  ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
        "west":  ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        "up":    ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        "down":  ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
    }[face]

def _rotate(pts, rot):
    if not rot: return np.asarray(pts, float)
    ax, ang, org = rot
    a = math.radians(ang); c, s = math.cos(a), math.sin(a)
    P = np.asarray(pts, float) - np.asarray(org, float)
    if ax == "x": M = np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    elif ax == "y": M = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    else: M = np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return P @ M.T + np.asarray(org, float)

# ------------------------------------------------------------------ render ---
def render_faces(quads, scale=14, view=(1, 1, 1), bg=(0, 0, 0, 0), size=None):
    """quads: list of (p0, pu, pv, tex_rgba_patch, light). Orthographic iso, z-buffered, nearest sampling."""
    v = np.asarray(view, float); v /= np.linalg.norm(v)
    # screen basis: right = up x view, upv = view x right
    upw = np.array([0, 1, 0.0]); right = np.cross(upw, v); right /= np.linalg.norm(right); up2 = np.cross(v, right)
    proj = lambda P: np.stack([P @ right, -(P @ up2)], -1) * scale
    allp = np.concatenate([np.asarray(q[:3]) for q in quads])
    S2 = proj(allp); mn, mx = S2.min(0) - 4, S2.max(0) + 4
    W, H = (int(mx[0] - mn[0]) + 1, int(mx[1] - mn[1]) + 1) if size is None else size
    img = np.zeros((H, W, 4), np.float32); img[:] = bg; zb = np.full((H, W), -1e9)
    for p0, pu, pv, tex, light in quads:
        p0, pu, pv = map(lambda a: np.asarray(a, float), (p0, pu, pv))
        U, V = pu - p0, pv - p0
        nrm = np.cross(U, V)
        if np.dot(nrm, v) >= -1e-9: continue             # back-face (face normals from U x V point inward)
        a0 = proj(p0[None])[0] - mn; au = proj(pu[None])[0] - mn - a0; av = proj(pv[None])[0] - mn - a0
        M = np.array([[au[0], av[0]], [au[1], av[1]]])
        if abs(np.linalg.det(M)) < 1e-6: continue
        Mi = np.linalg.inv(M)
        corners = np.array([a0, a0 + au, a0 + av, a0 + au + av])
        x0, y0 = np.floor(corners.min(0)).astype(int); x1, y1 = np.ceil(corners.max(0)).astype(int)
        ys, xs = np.mgrid[max(0, y0):min(H, y1 + 1), max(0, x0):min(W, x1 + 1)]
        P = np.stack([xs + 0.5 - a0[0], ys + 0.5 - a0[1]], -1)
        st = P @ Mi.T
        s, t = st[..., 0], st[..., 1]
        ok = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not ok.any(): continue
        depth = (p0 @ v) + s * (U @ v) + t * (V @ v)
        th, tw = tex.shape[:2]
        tx = np.clip((s * tw).astype(int), 0, tw - 1); ty = np.clip((t * th).astype(int), 0, th - 1)
        col = tex[ty, tx].astype(np.float32)
        ok &= col[..., 3] > 0
        yy, xx = ys[ok], xs[ok]
        closer = depth[ok] > zb[yy, xx]
        yy, xx = yy[closer], xx[closer]
        c = col[ok][closer]; c[:, :3] *= light
        img[yy, xx] = c; zb[yy, xx] = depth[ok][closer]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")

def render_block_model(m, scale=14, view=(1, 1, 1), bg=(0, 0, 0, 0)):
    quads = []
    for e in m.elements:
        for face, (x, y, w, h) in e["uv"].items():
            p0, pu, pv = _rotate(_face_corners(e["frm"], e["to"], face), e["rot"])
            quads.append((p0, pu, pv, m.texture[y:y+h, x:x+w], FACE_LIGHT[face] if e["shade"] else 1.0))
    return render_faces(quads, scale, view, bg)

def render_vanilla_cube(tex_top, tex_side, scale=14, view=(1, 1, 1)):
    """Reference cube with given textures - for side-by-side scale/brightness comparisons."""
    quads = []
    for face in FACES:
        t = tex_top if face in ("up", "down") else tex_side
        p0, pu, pv = _face_corners((0, 0, 0), (16, 16, 16), face)
        quads.append((p0, pu, pv, np.asarray(t), FACE_LIGHT[face]))
    return render_faces(quads, scale, view)

# ------------------------------------------------------------------ entities ---
class Cube:
    def __init__(self, origin, size, uv, material, mirror=False, inflate=0.0):
        self.origin, self.size, self.uv, self.material, self.mirror, self.inflate = origin, size, uv, material, mirror, inflate

class Bone:
    def __init__(self, name, pivot, rotation=(0, 0, 0), parent=None):
        self.name, self.pivot, self.rotation, self.parent, self.cubes = name, pivot, rotation, parent, []
    def cube(self, origin, size, uv, material, **kw):
        """Java ModelPart convention: origin/size in px relative to the bone pivot, y DOWN, box UV at uv=(u, v)."""
        self.cubes.append(Cube(origin, size, uv, material, **kw)); return self

def box_uv_rects(u, v, w, h, d):
    """Vanilla/Blockbench box-UV layout. Returns face -> (x, y, w, h) in texture px."""
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
            "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}

class EntityModel:
    def __init__(self, name, tw=64, th=32):
        self.name, self.tw, self.th, self.bones = name, tw, th, []

    def bone(self, name, pivot, rotation=(0, 0, 0), parent=None):
        b = Bone(name, pivot, rotation, parent); self.bones.append(b); return b

    def check_uv(self):
        """Overlapping box-UV islands are the #1 entity-texture bug. Raises with the offending cubes."""
        occ = {}
        for b in self.bones:
            for c in b.cubes:
                w, h, d = (math.ceil(s) for s in c.size)
                for face, (x, y, fw, fh) in box_uv_rects(*c.uv, w, h, d).items():
                    if x + fw > self.tw or y + fh > self.th:
                        raise ValueError(f"{b.name} cube uv {c.uv} {face} runs off the {self.tw}x{self.th} texture")
                    for yy in range(y, y + fh):
                        for xx in range(x, x + fw):
                            if (xx, yy) in occ and occ[(xx, yy)] != id(c):
                                raise ValueError(f"UV overlap at {xx},{yy}: {b.name} {c.uv}")
                            occ[(xx, yy)] = id(c)
        return True

    def paint(self, materials, seed=0, features=None):
        img = np.zeros((self.th, self.tw, 4), np.uint8)
        for bi, b in enumerate(self.bones):
            for ci, c in enumerate(b.cubes):
                w, h, d = (max(1, math.ceil(s)) for s in c.size)
                for fi, (face, (x, y, fw, fh)) in enumerate(box_uv_rects(*c.uv, w, h, d).items()):
                    img[y:y+fh, x:x+fw] = materials[c.material].paint(fw, fh, seed + bi * 101 + ci * 13 + fi, face)
        if features: features(img, self)
        self.texture = img
        return img

    # --- world transform (y up, for rendering / bedrock) ---
    def _bone_matrix(self, b):
        def rot(rx, ry, rz):
            cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
            Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
            return Rz @ Ry @ Rx                                    # vanilla ModelPart order: z, y, x
        chain = []; cur = b
        while cur is not None:
            chain.append(cur); cur = next((x for x in self.bones if x.name == cur.parent), None) if cur.parent else None
        def apply(P):
            for bb in chain:
                P = P @ rot(*[math.radians(a) for a in bb.rotation]).T + np.asarray(bb.pivot, float)
            return P
        return apply

    def quads(self):
        out = []
        for b in self.bones:
            apply = self._bone_matrix(b)
            for c in b.cubes:
                x, y, z = c.origin; w, h, d = c.size
                f, t = (x - c.inflate, y - c.inflate, z - c.inflate), (x + w + c.inflate, y + h + c.inflate, z + d + c.inflate)
                rects = box_uv_rects(*c.uv, *(max(1, math.ceil(s)) for s in c.size))
                # Java space is y-down with -z = front ("north"). Map Java faces onto the block-face corner helper:
                # flip y so up is up, then reuse _face_corners (java "up" = smaller y).
                for face, (tx, ty, tw_, th_) in rects.items():
                    jf = {"up": "up", "down": "down", "north": "north", "south": "south", "east": "west", "west": "east"}[face]
                    p = np.array(_face_corners((f[0], -t[1], f[2]), (t[0], -f[1], t[2]), jf), float)
                    p[:, 1] *= -1                                    # back to java y-down
                    p = apply(p); p[:, 1] = 24 - p[:, 1]             # world y-up, ground at 0
                    p[:, 0] *= -1                                    # java +x is the model's left
                    out.append((p[0], p[1], p[2], self.texture[ty:ty+th_, tx:tx+tw_], FACE_LIGHT[face]))
        return out

    def to_java(self, cls=None):
        cls = cls or "".join(p.capitalize() for p in self.name.split("_")) + "Model"
        L = [f"// {cls} - generated by mcpx3d (Mojang mappings, NeoForge/Fabric 1.21.x)",
             "public static LayerDefinition createBodyLayer() {",
             "    MeshDefinition mesh = new MeshDefinition();",
             "    PartDefinition root = mesh.getRoot();"]
        for b in self.bones:
            par = "root" if not b.parent else b.parent
            cl = "CubeListBuilder.create()"
            for c in b.cubes:
                cl += f".texOffs({c.uv[0]}, {c.uv[1]})" + (".mirror()" if c.mirror else "") + \
                      f".addBox({c.origin[0]}F, {c.origin[1]}F, {c.origin[2]}F, {c.size[0]}F, {c.size[1]}F, {c.size[2]}F" + \
                      (f", new CubeDeformation({c.inflate}F))" if c.inflate else ")")
            rx, ry, rz = (round(math.radians(a), 4) for a in b.rotation)
            pose = f"PartPose.offsetAndRotation({b.pivot[0]}F, {b.pivot[1]}F, {b.pivot[2]}F, {rx}F, {ry}F, {rz}F)" if any(b.rotation) \
                else f"PartPose.offset({b.pivot[0]}F, {b.pivot[1]}F, {b.pivot[2]}F)"
            L.append(f"    PartDefinition {b.name} = {par}.addOrReplaceChild(\"{b.name}\", {cl}, {pose});")
        L += [f"    return LayerDefinition.create(mesh, {self.tw}, {self.th});", "}"]
        return "\n".join(L)

    def to_bedrock(self):
        """Bedrock geometry (format 1.12.0) - opens in Blockbench (File > Open), handy for editing/animating."""
        bones = []
        for b in self.bones:
            parent_pivot = np.zeros(3)
            # absolute pivot in java space
            cur, piv = b, np.array(b.pivot, float)
            while cur.parent:
                cur = next(x for x in self.bones if x.name == cur.parent); piv = piv + np.array(cur.pivot, float)
            bp = [-piv[0], 24 - piv[1], piv[2]]
            cubes = []
            for c in b.cubes:
                ox = -(piv[0] + c.origin[0] + c.size[0]); oy = 24 - (piv[1] + c.origin[1] + c.size[1]); oz = piv[2] + c.origin[2]
                cu = {"origin": [ox, oy, oz], "size": list(c.size), "uv": list(c.uv)}
                if c.inflate: cu["inflate"] = c.inflate
                if c.mirror: cu["mirror"] = True
                cubes.append(cu)
            bd = {"name": b.name, "pivot": bp, "cubes": cubes}
            if any(b.rotation): bd["rotation"] = [-b.rotation[0], -b.rotation[1], b.rotation[2]]
            if b.parent: bd["parent"] = b.parent
            bones.append(bd)
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": bones}]}

    def save(self, root, namespace="mymod"):
        save(self.texture, os.path.join(root, f"assets/{namespace}/textures/entity/{self.name}.png"))
        os.makedirs(os.path.join(root, "models_src"), exist_ok=True)
        json.dump(self.to_bedrock(), open(os.path.join(root, f"models_src/{self.name}.geo.json"), "w"), indent=1)
        open(os.path.join(root, f"models_src/{self.name}_LayerDefinition.java"), "w").write(self.to_java())

def render_entity(e, scale=12, view=(1, 0.8, -1.2), bg=(0, 0, 0, 0)):
    return render_faces(e.quads(), scale, view, bg)

def texture_preview(img, k=6, bg=(40, 40, 48)):
    a = np.asarray(img, np.uint8)
    b = Image.new("RGBA", (a.shape[1], a.shape[0]), bg + (255,)); b.alpha_composite(Image.fromarray(a, "RGBA"))
    return b.resize((a.shape[1] * k, a.shape[0] * k), Image.NEAREST)

# ------------------------------------------------------- load real JSON models ---
def load_json_model(path, resolve_texture):
    """Load a Java block-model JSON (with elements) for preview. resolve_texture('ns:block/x') -> PNG path.
    Use it to (a) look at reference models from the player's own mod jars, (b) verify exported JSON
    renders exactly like the in-memory model (round-trip test)."""
    m = json.load(open(path))
    ts = m.get("texture_size", [16, 16])
    texs = {}
    def tex(ref):
        while ref.startswith("#"): ref = m["textures"].get(ref[1:], "")
        if ref not in texs:
            p = resolve_texture(ref)
            texs[ref] = np.asarray(Image.open(p).convert("RGBA")) if p and os.path.exists(p) else np.full((16, 16, 4), 255, np.uint8)
        return texs[ref]
    quads = []
    for e in m.get("elements", []):
        rot = None
        if "rotation" in e: rot = (e["rotation"]["axis"], e["rotation"]["angle"], e["rotation"]["origin"])
        for face, fd in e.get("faces", {}).items():
            t = tex(fd.get("texture", ""))
            H, W = t.shape[:2]
            sx, sy = W / 16 if "texture_size" not in m else W / ts[0] * ts[0] / 16, H / 16 if "texture_size" not in m else H / ts[1] * ts[1] / 16
            u1, v1, u2, v2 = fd.get("uv", [0, 0, 16, 16])
            x1, x2 = sorted((u1 * sx, u2 * sx)); y1, y2 = sorted((v1 * sy, v2 * sy))
            patch = t[int(math.floor(y1)):max(int(math.ceil(y2)), int(math.floor(y1)) + 1), int(math.floor(x1)):max(int(math.ceil(x2)), int(math.floor(x1)) + 1)]
            if u2 < u1: patch = patch[:, ::-1]
            if v2 < v1: patch = patch[::-1]
            for _ in range(int(fd.get("rotation", 0)) // 90): patch = np.rot90(patch, -1)
            p0, pu, pv = _rotate(_face_corners(e["from"], e["to"], face), rot)
            quads.append((p0, pu, pv, patch, FACE_LIGHT[face] if e.get("shade", True) else 1.0))
    return quads
