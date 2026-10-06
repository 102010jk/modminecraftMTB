"""
Layered, tintable sprites for the customisable bike item icon (vanilla layered item model + ItemColor tints).

For every frame shape <shape> (enduro_classic, enduro_high_pivot, enduro_low_slung, dj_classic, dj_straight,
dj_curved) it writes textures/item/bike_layers/<shape>_<layer>.png with the layers, bottom to top:

    0 outline  - dark silhouette (NOT tinted)
    1 tyres    - light grey, tinted with the tyre sidewall colour
    2 rims     - light grey, tinted with the rim colour
    3 frame    - greyscale shading, tinted with the frame colour
    4 fork     - greyscale, tinted with the fork stanchion / lower colour (also the rear shock on full suspension)
    5 cockpit  - greyscale, tinted with the saddle / grip colour (saddle, bars, grips)

plus accessory overlays (not tinted) textures/item/bike_layers/acc_<bell|duck|front_light|rear_light>.png.

    python tools/gen_bike_layers.py
"""
import math
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'descentmtb', 'textures', 'item', 'bike_layers')

G = {1: (110, 110, 110, 255), 2: (150, 150, 150, 255), 3: (190, 190, 190, 255), 4: (225, 225, 225, 255),
     5: (255, 255, 255, 255)}
OUTLINE = (28, 26, 30, 255)


class Layer:
    def __init__(self):
        self.px = {}

    def set(self, x, y, shade):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(x, y)] = G[shade] if isinstance(shade, int) else shade

    def line(self, p0, p1, shade):
        (x0, y0), (x1, y1) = p0, p1
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, shade)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def curve(self, pts, shade):
        for a, b in zip(pts, pts[1:]):
            self.line(a, b, shade)

    def image(self):
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            im.putpixel((x, y), c)
        return im


def wheels(rim_r=(1.9, 2.6), tyre_r=(2.6, 3.6), centres=(3.5, 12.5), wy=11.5):
    tyres, rims = Layer(), Layer()
    for y in range(16):
        for x in range(16):
            for cx in centres:
                d = math.hypot(x + .5 - cx, y + .5 - wy)
                if tyre_r[0] <= d <= tyre_r[1]:
                    tyres.set(x, y, 4 if y + .5 < wy else 3)
                elif rim_r[0] <= d < rim_r[1]:
                    rims.set(x, y, 5 if y + .5 < wy else 4)
                elif d < .8:
                    rims.set(x, y, 3)
    return tyres, rims


def build(shape):
    full = shape.startswith('enduro')
    tyres, rims = wheels()
    frame, fork, cockpit = Layer(), Layer(), Layer()
    bb, seat_top, head_top, head_bottom = (7, 12), (6, 6), (11, 6), (11, 7)
    if shape == 'enduro_classic':
        frame.line((3, 11), bb, 3)                 # chainstay
        frame.line(bb, seat_top, 4)                # seat tube
        frame.line(seat_top, head_top, 5)          # top tube
        frame.line((8, 11), head_bottom, 4)        # down tube
        frame.line((4, 10), (6, 7), 3)             # seat stay
    elif shape == 'enduro_high_pivot':
        frame.line((3, 11), (6, 9), 3)             # stays rise to a high pivot
        frame.line((6, 9), bb, 3)
        frame.line(bb, seat_top, 4)
        frame.line(seat_top, head_top, 5)
        frame.line((8, 11), head_bottom, 4)
        frame.line((4, 10), (6, 7), 3)
        frame.set(7, 10, 2)                        # idler pulley
    elif shape == 'enduro_low_slung':
        frame.line((3, 11), bb, 3)
        frame.line(bb, seat_top, 4)
        frame.curve([(6, 7), (8, 8), (10, 7), (11, 6)], 5)   # low, curved top tube
        frame.line((8, 11), head_bottom, 4)
        frame.line((4, 10), (6, 7), 3)
    elif shape == 'dj_classic':
        frame.line((3, 11), bb, 3)
        frame.line(bb, (6, 7), 4)
        frame.line((6, 7), head_top, 5)
        frame.line((8, 11), head_bottom, 4)
        frame.line((4, 10), (6, 8), 3)
    elif shape == 'dj_straight':
        frame.line((3, 11), bb, 3)
        frame.line(bb, (6, 7), 4)
        frame.line((6, 7), (11, 7), 5)             # dead straight top tube
        frame.line((8, 11), head_bottom, 4)
        frame.line((4, 10), (6, 8), 3)
    elif shape == 'dj_curved':
        frame.line((3, 11), bb, 3)
        frame.line(bb, (6, 8), 4)
        frame.curve([(6, 8), (8, 9), (10, 8), (11, 6)], 5)   # curved, very low
        frame.line((8, 11), head_bottom, 4)
        frame.line((4, 10), (6, 8), 3)
    # fork (+ rear shock on full suspension)
    fork.line((11, 7), (12, 11), 4)
    fork.set(12, 8, 5)
    if full:
        fork.set(7, 8, 5)
        fork.set(8, 8, 4)
    # cockpit: stem, bars, saddle
    sy = 4 if full else 5
    cockpit.set(11, 5, 3)
    cockpit.set(11, 4, 4)
    cockpit.set(12, 4, 5)
    for x in (4, 5, 6):
        cockpit.set(x, sy, 5 if x == 4 else 4)
    cockpit.set(6, sy + 1, 3)
    layers = [tyres, rims, frame, fork, cockpit]
    # outline: 1 px around the union of all layers
    union = set()
    for l in layers:
        union |= set(l.px)
    outline = Layer()
    for (x, y) in union:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in union:
                outline.set(q[0], q[1], OUTLINE)
    return [outline] + layers


def accessories():
    acc = {}
    bell = Layer()
    for x, y, c in ((12, 3, (240, 200, 70, 255)), (13, 3, (200, 160, 40, 255)), (12, 2, (255, 230, 120, 255))):
        bell.set(x, y, c)
    acc['bell'] = bell
    duck = Layer()
    for x, y in ((12, 2), (13, 2), (12, 3), (13, 3), (14, 3)):
        duck.set(x, y, (250, 214, 64, 255))
    duck.set(14, 2, (240, 140, 40, 255))           # beak
    duck.set(13, 1, (250, 214, 64, 255))
    duck.set(12, 1, (30, 30, 30, 255) if False else (250, 214, 64, 255))
    duck.set(13, 2, (30, 30, 34, 255))             # eye
    acc['duck'] = duck
    front = Layer()
    front.set(13, 5, (255, 250, 220, 255))
    front.set(14, 5, (255, 255, 200, 255))
    front.set(15, 5, (255, 255, 180, 160))
    acc['front_light'] = front
    rear = Layer()
    rear.set(5, 6, (255, 60, 50, 255))
    rear.set(4, 6, (200, 40, 40, 255))
    acc['rear_light'] = rear
    return acc


def main():
    os.makedirs(OUT, exist_ok=True)
    names = ['outline', 'tyres', 'rims', 'frame', 'fork', 'cockpit']
    shapes = ['enduro_classic', 'enduro_high_pivot', 'enduro_low_slung', 'dj_classic', 'dj_straight', 'dj_curved']
    sheet = Image.new('RGBA', (6 * 72, 2 * 72), (139, 139, 139, 255))
    tints = [None, (40, 40, 44), (200, 200, 205), (31, 138, 143), (201, 162, 75), (30, 30, 34)]
    for k, shape in enumerate(shapes):
        layers = build(shape)
        composite = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for name, layer, tint in zip(names, layers, tints):
            im = layer.image()
            im.save(os.path.join(OUT, f'{shape}_{name}.png'))
            if tint:
                px = im.load()
                for y in range(16):
                    for x in range(16):
                        r, g, b, a = px[x, y]
                        px[x, y] = (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a)
            composite.alpha_composite(im)
        sheet.alpha_composite(composite.resize((64, 64), Image.NEAREST), ((k % 6) * 72 + 4, 4))
    for i, (name, layer) in enumerate(accessories().items()):
        im = layer.image()
        im.save(os.path.join(OUT, f'acc_{name}.png'))
        sheet.alpha_composite(im.resize((64, 64), Image.NEAREST), (i * 72 + 4, 76))
    sheet.save(os.path.join(os.path.dirname(OUT), '..', '..', '..', '..', '..', '..', '..', 'tools', 'preview', 'bike_layers.png'))
    print('wrote', len(shapes) * len(names) + 4, 'sprites to', os.path.abspath(OUT))


if __name__ == '__main__':
    main()
