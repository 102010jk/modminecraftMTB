import sys,math,json
from pathlib import Path
sys.path.insert(0,'tools')
import bike_tex_common as tex
from PIL import Image
def rotate(p,r):
 x,y,z=p;rx,ry,rz=[math.radians(a) for a in r]
 y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
 x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
 x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
 return x,y,z
arrays=[]
for full,code in [(True,'c'),(True,'h'),(True,'l'),(False,'c'),(False,'s'),(False,'v')]:
 bones,cubes,size=tex.parse_java('src/main/java/com/descentmtb/client/model/'+('EnduroBikeModel' if full else 'HardtailBikeModel')+'.java')
 selected={c['base']:c for c in cubes if not c['codes'] or code in c['codes']}
 def point(c,positive,axis=2):
  p=[0,0,0];p[axis]=(1 if positive else -1)*c['s'][axis]/2
  p=[a+b for a,b in zip(rotate(p,c['r']),c['c'])];b=c['bone']
  while b!='root':
   spec=bones[b];p=[a+b for a,b in zip(rotate(p,spec['rot']),spec['pivot'])];b=spec['parent']
  return [-p[2]/16,-p[1]/16]
 topname='top_tube' if code not in ('l','s','v') else 'top_straight' if code=='s' else 'top_front'
 if code in ('l','v'):
  points=[point(selected['top_rear'],True),point(selected['top_front'],True),point(selected['top_front'],False)]
 else:points=[point(selected[topname],True),point(selected[topname],False)]
 tubes=[points]
 for name in ['down_tube','seat_tube','seatstay_l','chainstay_l','head_tube','leg_up_l']:
  c=selected[name];tubes.append([point(c,False,1 if name=='leg_up_l' else 2),point(c,True,1 if name=='leg_up_l' else 2)])
 arrays.append(tubes)
 literal='{\n'+',\n'.join('        {'+', '.join('{'+', '.join('{'+','.join(f'{v:.6f}f' for v in point)+'}' for point in tube)+'}' for tube in shape)+'}' for shape in arrays)+'\n    }'
 path=Path('src/main/java/com/descentmtb/client/custom/StickerAnchors.java')
 source='''package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.Tube;
import com.descentmtb.entity.BikeType;

/** Side-view anchors derived from the literal model tube geometry, in metres (forward, up). */
public final class StickerAnchors {
    public record Pick(Tube tube,float t,float distance) {}
    private static final float[][][][] PATHS = TABLE;
    public static Pick pick(BikeType type,FrameShape shape,float x,float y) { return pick(type==BikeType.ENDURO,shape,x,y); }
    public static Pick pick(boolean full,FrameShape shape,float x,float y) {
        Pick pick=nearest(full,shape,x,y);
        return pick.distance()<.12f ? pick : null;
    }
    public static Pick nearest(boolean full,FrameShape shape,float x,float y) {
        Pick best=null;
        for(Tube tube:Tube.values()) {
            float[][] path=PATHS[shape.ordinal()][tube.ordinal()];
            for(int i=0;i<path.length-1;i++) {
                float[] a=path[i],b=path[i+1];float dx=b[0]-a[0],dy=b[1]-a[1];
                float t=Math.max(0,Math.min(1,((x-a[0])*dx+(y-a[1])*dy)/(dx*dx+dy*dy)));
                float d=(float)Math.hypot(x-a[0]-dx*t,y-a[1]-dy*t);
                if(best==null || d<best.distance()) best=new Pick(tube,(i+t)/(path.length-1),d);
            }
        }
        return best;
    }
    public static float[] positionOf(BikeType type,FrameShape shape,Tube tube,float t) { return pointOn(type==BikeType.ENDURO,shape,tube,t); }
    public static float[] pointOn(boolean full,FrameShape shape,Tube tube,float t) {
        float[][] path=PATHS[shape.ordinal()][tube.ordinal()];
        float u=Math.max(0,Math.min(1,t))*(path.length-1);int i=Math.min(path.length-2,(int)u);u-=i;
        return new float[]{path[i][0]+(path[i+1][0]-path[i][0])*u,path[i][1]+(path[i+1][1]-path[i][1])*u};
    }
    public static float[] segment(boolean full,FrameShape shape,Tube tube) {
        float[] a=pointOn(full,shape,tube,0),b=pointOn(full,shape,tube,1);
        return new float[]{a[0],a[1],b[0],b[1]};
    }
    private StickerAnchors() {}
}
'''.replace('TABLE',literal)
path.write_text(source,encoding='utf-8')
models=Path('src/main/resources/assets/descentmtb/models/item')
shapes=['enduro_classic','enduro_high_pivot','enduro_low_slung','dj_classic','dj_straight','dj_curved']
names=['outline','tyres','rims','frame','fork','cockpit']
for shape in shapes:
 data={'parent':'minecraft:item/generated','loader':'neoforge:item_layers','textures':{f'layer{i}':f'descentmtb:item/bike_layers/{shape}_{name}' for i,name in enumerate(names)}}
 for i,name in enumerate(['bell','duck','front_light','rear_light'],6):data['textures'][f'layer{i}']=f'descentmtb:item/bike_layers/acc_{name}'
 (models/f'bike_{shape}.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
for item,default in [('mountain_bike',0),('hardtail_bike',3)]:
 data=json.loads((models/f'bike_{shapes[default]}.json').read_text())
 data['overrides']=[{'predicate':{'descentmtb:shape':i},'model':f'descentmtb:item/bike_{shape}'} for i,shape in enumerate(shapes)]
 (models/f'{item}.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

# Powder-coated repair stand: metal edges, rubber pads and a brass clamp adjuster.
assets=Path('src/main/resources/assets/descentmtb')
for name,base in [('stand_metal',(55,67,72)),('stand_rubber',(27,30,31)),('stand_brass',(176,135,64))]:
 im=Image.new('RGB',(16,16));p=im.load()
 for y in range(16):
  for x in range(16):
   shade=(12 if x in (0,15) else 0)+(-8 if y in (0,15) else 0)+((x*17+y*11)%5-2)
   if name=='stand_brass':shade+=10 if y%4==0 else 0
   p[x,y]=tuple(max(0,min(255,v+shade)) for v in base)
 im.save(assets/'textures/block'/f'{name}.png')
elements=[]
def box(a,b,material):
 elements.append({'from':a,'to':b,'faces':{f:{'texture':'#'+material} for f in ['up','down','north','south','east','west']}})
box([1,0,6],[15,2,10],'metal');box([6,0,1],[10,2,15],'metal')
box([1,0,6],[3,1,10],'rubber');box([13,0,6],[15,1,10],'rubber')
box([6,0,1],[10,1,3],'rubber');box([6,0,13],[10,1,15],'rubber')
box([7,2,7],[9,17,9],'metal');box([6.5,10,6.5],[9.5,12,9.5],'brass')
box([7,14.5,8],[9,16.5,14],'metal')
# North-facing clamp grips the seatpost; open middle, padded jaws.
box([6,14.5,12],[7,18.5,14],'metal');box([9,14.5,12],[10,18.5,14],'metal')
box([7,14.5,12],[9,15.5,14],'rubber');box([7,17.5,12],[9,18.5,14],'rubber')
box([10,15.5,12],[12,17.5,14],'brass')
(assets/'models/block/bike_stand.json').write_text(json.dumps({'textures':{'particle':'descentmtb:block/stand_metal',
 **{m:f'descentmtb:block/stand_{m}' for m in ['metal','rubber','brass']}},'elements':elements},indent=2)+'\n')
