"""Six real 16x16 textures and four JSON model variants, preview only.

Run: python tools/gen_texture_review.py
Reuses the project's v4 generator palette and deterministic noise.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import gen_textures_v4 as v4

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/descentmtb'
OUT = ROOT / 'tools/preview/review_16x16'


def texture(name):
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            n = v4.noise(x // 2, y // 2, 701)
            if name.startswith('trail_rock'):
                mat, tone = 'stone', 3 + (n > .73) - (n < .2)
                # Broken mineral seams, not uniform salt-and-pepper noise.
                seam = (x + (y // 3) * 2) % 13
                if seam == 0:
                    tone = 1
                elif seam == 1:
                    tone = 4
                if name.endswith('_top') and v4.noise(x // 4, y // 3, 92) > .78:
                    mat, tone = 'moss', 2 + int(n > .5)
            elif name == 'trail_roots':
                mat = 'bark'
                grain = (x + (y // 6) % 2) % 6
                tone = [1, 3, 4, 3, 2, 2][grain]
                if y in (5, 12) and grain in (0, 1):
                    tone = 1
            elif name == 'wood_support_top':
                mat = 'stripped'
                radius = max(abs(x - 7.5), abs(y - 7.5))
                tone = 2 if int(radius) % 3 == 0 else 4
                if x == 8 and y < 7:
                    tone = 1
            elif name == 'wood_support':
                mat = 'stripped'
                tone = [2, 3, 4, 3, 3, 2, 3, 4][(x + int(y > 9)) % 8]
                if (x - 8) ** 2 + (y - 8) ** 2 < 5:
                    tone = 1 if x == 8 else 2
                if y in (2, 3, 12, 13):
                    mat, tone = 'iron', 4 if y in (2, 12) else 2
                    if x in (6, 10):
                        tone = 1
            else:
                mat, tone = 'blue', 3 + int(n > .88)
                if x in (0, 8) or y in (0, 8):
                    tone = 1
                elif x in (1, 9) or y in (1, 9):
                    tone = 4
                if y == 15:
                    tone = 1
                if x in (12, 13) and y in (12, 13):
                    mat, tone = 'gold', 4 if y == 12 else 2
            im.putpixel((x, y), v4.col(mat, tone))
    return im


def cube(a, b, side='#all', top=None):
    return {'from': a, 'to': b, 'faces': {
        face: {'texture': (top or side) if face == 'up' else side}
        for face in ('north', 'south', 'east', 'west', 'up', 'down')}}


def variant(name, old):
    model = json.loads(json.dumps(old))
    if name == 'trail_roots':
        boxes = [([0,0,3],[6,3,6]), ([6,0,4],[11,2.5,7]),
                 ([11,0,5],[16,2,7]), ([1,0,10],[8,2.5,13]),
                 ([8,0,9],[16,2,12]), ([3,0,5],[5,2,11]),
                 ([4,0,1],[6,1.5,4]), ([11,0,11],[13,1.5,15])]
        model['elements'] = [cube(a,b) for a,b in boxes]
    elif name == 'trail_rock':
        boxes = [([2,0,3],[9,3,10]), ([2,3,4],[8,5,9]),
                 ([3,5,5],[7,6,8]), ([9,0,7],[14,2.5,13]),
                 ([10,2.5,8],[14,3.5,11]), ([4,0,11],[7,1.5,14])]
        model['elements'] = [cube(a,b,'#side','#top') for a,b in boxes]
    elif name == 'wood_support':
        for lo, hi in ((2,4),(12,14)):
            e = cube([4.7,lo,4.7],[11.3,hi,11.3],'#side')
            for f in e['faces'].values():
                f['uv'] = [5,2,11,4]
            model['elements'].append(e)
    else:
        model['parent'] = 'minecraft:block/block'
        model['elements'] = [cube([0,0,0],[16,12,16]),
                             cube([.5,12,.5],[15.5,15,15.5]),
                             cube([1,15,1],[15,16,15])]
    return model


def render(model, textures):
    """Orthographic raster of actual JSON cuboids and UVs; nearest texel sampling."""
    im = Image.new('RGB', (340,300), '#202731')
    d = ImageDraw.Draw(im)
    def project(p):
        x,y,z = p
        return (170+(x-z)*9, 172+(x+z-16)*4.5-(y-8)*9)
    elements = model.get('elements', [cube([0,0,0],[16,16,16])])
    faces=[]
    for e in elements:
        x0,y0,z0=e['from']; x1,y1,z1=e['to']
        for face,pts,uv,shade in (
            ('up',[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)], [x0,z0,x1,z1],1),
            ('south',[(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)], [x0,16-y1,x1,16-y0],.78),
            ('east',[(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)], [16-z1,16-y1,16-z0,16-y0],.62)):
            spec=e['faces'].get(face)
            if not spec: continue
            ref=model['textures'][spec['texture'].lstrip('#')].split('/')[-1]
            faces.append((sum(sum(p) for p in pts)/4,pts,spec.get('uv',uv),textures[ref],shade))
    for _,pts,uv,tex,shade in sorted(faces,key=lambda f:f[0]):
        # Geometry subdivisions follow the texture pixel grid, including explicit UV crops.
        nu=max(1,math.ceil(abs(uv[2]-uv[0]))); nv=max(1,math.ceil(abs(uv[3]-uv[1])))
        def point(u,v):
            return project(tuple(pts[0][i]+u*(pts[1][i]-pts[0][i])+v*(pts[3][i]-pts[0][i]) for i in range(3)))
        for j in range(nv):
            for i in range(nu):
                u,v=(i+.5)/nu,(j+.5)/nv
                tx=min(15,max(0,int(uv[0]+u*(uv[2]-uv[0]))))
                ty=min(15,max(0,int(uv[1]+v*(uv[3]-uv[1]))))
                color=tuple(int(c*shade) for c in tex.getpixel((tx,ty))[:3])
                d.polygon([point(i/nu,j/nv),point((i+1)/nu,j/nv),point((i+1)/nu,(j+1)/nv),point(i/nu,(j+1)/nv)],fill=color)
    return im


def main():
    names=['trail_rock','trail_rock_top','trail_roots','wood_support','wood_support_top','airbag']
    new={n:texture(n) for n in names}
    old={n:Image.open(ASSETS/f'textures/block/{n}.png').convert('RGBA') for n in names}
    (OUT/'textures/block').mkdir(parents=True,exist_ok=True)
    (OUT/'models/block').mkdir(parents=True,exist_ok=True)
    for n,im in new.items():
        assert im.size == (16,16)
        assert im.tobytes() != old[n].tobytes()
        im.save(OUT/f'textures/block/{n}.png')
    font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
    small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',17)
    sheet=Image.new('RGB',(1120,1440),'#151b23')
    labels=[('trail_rock','1  KAMEN', 'trail_rock_top'),('trail_roots','2  KORENY',None),
            ('wood_support','3  DREVENA PODPERA','wood_support_top'),('airbag','4  AIRBAG',None)]
    for row,(name,label,extra) in enumerate(labels):
        card=Image.new('RGB',(1120,360),'#202731'); d=ImageDraw.Draw(card)
        d.text((20,12),label,font=font,fill='#e9eef3')
        d.text((20,48),'TEXTURY 16 x 16',font=small,fill='#9aafbf')
        d.text((24,78),'PRED',font=small,fill='#a3aebd'); d.text((208,78),'PO',font=small,fill='#91d6b2')
        for k,n in enumerate([name]+([extra] if extra else [])):
            size=112 if extra else 160; y=106+k*124
            for x,images in ((24,old),(208,new)):
                card.paste(images[n].resize((size,size),Image.Resampling.NEAREST),(x,y))
        before=json.loads((ASSETS/f'models/block/{name}.json').read_text())
        after=variant(name,before)
        (OUT/f'models/block/{name}.json').write_text(json.dumps(after,indent=2)+'\n')
        for x,m,tx in ((410,before,old),(760,after,new)):
            card.paste(render(m,tx),(x,60))
        d.text((424,40),'MODEL PRED',font=small,fill='#a3aebd')
        d.text((774,40),'MODEL PO',font=small,fill='#91d6b2')
        card.save(OUT/f'{name}_comparison.png')
        sheet.paste(card,(0,row*360))
    sheet.save(OUT/'comparison.png')
    print(f'Generated 6 textures (16x16), 4 JSON models and comparison: {OUT}')


if __name__ == '__main__':
    main()
