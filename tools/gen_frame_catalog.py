"""Generate brand geometry and Java linkage data from frame_profiles.json.

Short links, pivots, shock mounts and rear triangles share animator rest points.
Stylised proportions, not manufacturer CAD; references in docs/frame-catalog.md.
"""
from pathlib import Path
import json
import math
import re

ROOT = Path(__file__).resolve().parent.parent
FILE = ROOT / 'src/main/java/com/descentmtb/client/model/EnduroBikeModel.java'
FRAMES = json.loads((ROOT / 'tools/frame_profiles.json').read_text())
PIVOT, AXLE = (-8.3, 4.2), (-6.0, 10.08)
source = FILE.read_text(encoding='utf-8')
source = re.sub(r'\n        // BEGIN FRAME CATALOG.*?// END FRAME CATALOG\n', '\n', source, flags=re.S)
stock = ['down_tube', 'shock_mount_l', 'shock_mount_r', 'shock_mount_web', 'shock_mount_bolt', 'pivot_axle']
stock += ['yoke_l', 'chainstay_l', 'seatstay_l', 'rocker_l', 'dropout_l', 'pivot_cap_l', 'rocker_bolt_l',
          'yoke_r', 'chainstay_r', 'seatstay_r', 'rocker_r', 'dropout_r', 'pivot_cap_r', 'rocker_bolt_r',
          'rocker_bar', 'stay_brace', 'caliper_r']
for name in stock:
    source = source.replace('"' + name + '",', '"' + name + '__chl",')
lines = [f'        PartDefinition {name} = bone(frame, "{name}", 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);'
         for name in ['catalog_upper', 'catalog_lower', 'catalog_stays']]
u, v, row = 0, 160, 0


def cube(bone, name, code, mat, centre, size, rot=(0, 0, 0)):
    global u, v, row
    size = tuple(round(x, 4) for x in size)
    w = math.ceil(round(2 * (size[2] + size[0]), 4)) + 1
    h = math.ceil(round(size[2] + size[1], 4)) + 1
    if u + w > 128:
        u, v, row = 0, v + row, 0
    if v + h > 512:
        raise ValueError('Catalog UV overflow')
    values = tuple(centre) + size + tuple(rot)
    lines.append(f'        cube({bone}, "{name}__{code}", "{mat}", {u}, {v}, ' +
                 ', '.join(f'{n:.4f}f' for n in values) + ');')
    u += w
    row = max(row, h)


def local(bone, point):
    return (point[0] - PIVOT[0], point[1] - PIVOT[1]) if bone == 'swingarm' else point


def tube(bone, name, code, a, b, width, height=None, x=0):
    a, b = local(bone, a), local(bone, b)
    dy, dz = b[0] - a[0], b[1] - a[1]
    cube(bone, name, code, 'frame', (x, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2),
         (width, height or width, math.hypot(dy, dz)), (math.degrees(math.atan2(-dy, dz)), 0, 0))


def bolt(bone, name, code, point, width=1.7):
    y, z = local(bone, point)
    cube(bone, name, code, 'silver', (0, y, z), (width, .25, .25))


def paired(bone, name, code, a, b, x=.75, thickness=.32, height=.5):
    for suffix, sign in [('l', 1), ('r', -1)]:
        tube(bone, name + '_' + suffix, code, a, b, thickness, height, sign * x)


def closest(point, segments):
    best, distance = None, float('inf')
    for a, b in segments:
        dy, dz = b[0] - a[0], b[1] - a[1]
        t = max(0, min(1, ((point[0] - a[0]) * dy + (point[1] - a[1]) * dz) / (dy * dy + dz * dz)))
        p = (a[0] + t * dy, a[1] + t * dz)
        if math.dist(point, p) < distance:
            best, distance = p, math.dist(point, p)
    return best


def validate_travel(code, f):
    """Reject an outline whose four-bar would toggle before the model's 160 mm travel."""
    a, b, c, d = [f[key] for key in ['a', 'b', 'c', 'd']]
    bc, dc = math.dist(b, c), math.dist(d, c)
    branch = 1 if (d[0]-b[0])*(c[1]-b[1])-(d[1]-b[1])*(c[0]-b[0]) > 0 else -1

    def rotate(p, angle):
        cs, sn = math.cos(angle), math.sin(angle)
        return p[0]*cs-p[1]*sn, p[0]*sn+p[1]*cs

    def rise(angle):
        offset = rotate((b[0]-a[0], b[1]-a[1]), angle)
        mb = (a[0]+offset[0], a[1]+offset[1])
        delta = (d[0]-mb[0], d[1]-mb[1])
        span = math.hypot(*delta)
        if not abs(bc-dc)+.001 < span < bc+dc-.001:
            return None
        along = (bc*bc-dc*dc+span*span)/(2*span)
        h = math.sqrt(max(0, bc*bc-along*along))
        uy, uz = delta[0]/span, delta[1]/span
        mc = (mb[0]+uy*along-uz*h*branch, mb[1]+uz*along+uy*h*branch)
        rest, moved = (c[0]-b[0], c[1]-b[1]), (mc[0]-mb[0], mc[1]-mb[1])
        angle = math.atan2(rest[0]*moved[1]-rest[1]*moved[0], rest[0]*moved[0]+rest[1]*moved[1])
        axle = rotate((AXLE[0]-b[0], AXLE[1]-b[1]), angle)
        return AXLE[0]-mb[0]-axle[0]

    initial = rise(.001)
    if initial is None:
        raise ValueError(f'{code}: degenerate rest linkage')
    sign = 1 if initial > 0 else -1
    previous = 0
    for i in range(1, 321):
        current = rise(sign*i*.005)
        if current is None or current <= previous:
            break
        previous = current
        if current >= 2.56:
            return
    raise ValueError(f'{code}: linkage can only reach {previous/16:.3f} m before a toggle; revise its pivots')


for code, f in FRAMES.items():
    validate_travel(code, f)
    mid, seat = f['top']
    down, (dw, tw) = f['down'], f['width']
    a, b, c, d = [f[key] for key in ['a', 'b', 'c', 'd']]
    eye, arm = f['fixedEye'], f['movingEye']
    head, down_head, bb = (-15.9, -4.17), (-15.31, -4.55), (-5.6, 3.04)
    tube('frame', 'top_front', code, head, mid, tw)
    tube('frame', 'top_rear', code, mid, seat, tw)
    tube('frame', 'down_front', code, down_head, down, dw, dw * 1.07)
    tube('frame', 'down_rear', code, down, bb, dw, dw * 1.07)
    # Mount ears reach the supporting tube; do not float at the shock eye.
    mount = closest(eye, [(head, mid), (mid, seat), (down_head, down), (down, bb)])
    paired('frame', 'shock_mount', code, mount, eye, .48, .25, .5)
    bolt('frame', 'shock_mount_bolt', code, eye, 1.3)
    for name, point in [('lower_pivot', a), ('upper_pivot', d)]:
        if name == 'lower_pivot' and not f['dual']:
            seat_axis = closest(point, [((-13.58, 4.95), bb)])
            tube('frame', name + '_boss', code, seat_axis, point, 1.6, .7)
        else:
            cube('frame', name + '_boss', code, 'frame', (0, *point), (1.75, .65, .65))
        bolt('frame', name, code, point, 2.05)
    paired('catalog_upper', 'upper_link', code, d, c)
    bolt('catalog_upper', 'upper_rear_pivot', code, c)
    shock_bone = 'catalog_lower' if f['dual'] else 'catalog_upper'
    paired(shock_bone, 'shock_lever_front', code, a if f['dual'] else d, arm, .48, .28, .45)
    paired(shock_bone, 'shock_lever_rear', code, arm, b if f['dual'] else c, .48, .28, .45)
    bolt(shock_bone, 'shock_arm_bolt', code, arm, 1.3)
    if f['dual']:
        paired('catalog_lower', 'lower_link', code, a, b)
        bolt('catalog_lower', 'lower_rear_pivot', code, b)
        paired('swingarm', 'chainstay', code, b, AXLE, 1.0, .4, .58)
        paired('swingarm', 'seatstay', code, c, AXLE, 1.0, .38, .5)
        paired('swingarm', 'rear_upright', code, b, c, 1.0, .38, .52)
    else:
        paired('swingarm', 'chainstay', code, a, b, 1.0, .4, .58)
        paired('catalog_stays', 'seatstay', code, c, AXLE, 1.0, .38, .5)
        paired('catalog_stays', 'horst_rear', code, AXLE, b, 1.0, .38, .5)
        bolt('swingarm', 'horst_pivot', code, b, 2.2)
    for suffix, sign in [('l', 1), ('r', -1)]:
        bone = 'swingarm' if f['dual'] else 'catalog_stays'
        y, z = local(bone, AXLE)
        cube(bone, 'dropout_' + suffix, code, 'black', (sign * 1.0, y, z), (.4, .75, .65))
    bone = 'swingarm' if f['dual'] else 'catalog_stays'
    y, z = local(bone, (-7.25, 9.1))
    cube(bone, 'caliper_r', code, 'brake', (-.85, y, z), (.4, 1.15, .9))

catalog = '\n        // BEGIN FRAME CATALOG\n' + '\n'.join(lines) + '\n        // END FRAME CATALOG\n'
source = re.sub(r'        return LayerDefinition.create\(mesh, 128, (?:256|512)\);',
                catalog + '        return LayerDefinition.create(mesh, 128, 512);', source)
FILE.write_text(source, encoding='utf-8')
cases = []
for code, f in FRAMES.items():
    points = ', '.join('new EnduroLinkage.Point(' + ', '.join(f'{v:.4f}f' for v in f[key]) + ')'
                       for key in ['a', 'b', 'c', 'd', 'fixedEye', 'movingEye'])
    cases.append(f'    private static final EnduroLinkage {code.upper()} = new EnduroLinkage({str(f["dual"]).lower()}, {points});')
java = '''package com.descentmtb.client.model;

import com.descentmtb.custom.BikeParts.FrameShape;

/** Generated by tools/gen_frame_catalog.py; edit tools/frame_profiles.json. */
final class FrameLayouts {
''' + '\n'.join(cases) + '''
    static EnduroLinkage forShape(FrameShape shape) {
        return switch (shape) {
''' + '\n'.join(f'            case {f["shape"]} -> {code.upper()};' for code, f in FRAMES.items()) + '''
            default -> null;
        };
    }
    private FrameLayouts() {}
}
'''
(FILE.parent / 'FrameLayouts.java').write_text(java, encoding='utf-8')
print('Wrote', len(lines) - 3, 'catalog cubes; UV end row', v + row)
