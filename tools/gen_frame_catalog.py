"""Stylised frame silhouettes from manufacturer references in docs/frame-catalog.md.

Writes literal cube tables so the common UV painter and decal-anchor generator stay authoritative.
Model geometry only: shared contact points and riding parameters are unaffected.
"""
from pathlib import Path
import math,re

FILE=Path('src/main/java/com/descentmtb/client/model/EnduroBikeModel.java')
# code: (top mid, top seat end, down mid, down width, top width, frame shock eye, rear shock eye)
FRAMES={
 'n':((-13.3,.4),(-13.3,4.95),(-8.6,.5),1.30,.82,(-7.2,-.8),(-7.6,3.4)),
 't':((-14.7,0),(-13.6,4.95),(-7.2,1.2),1.45,1.0,(-12.7,-.2),(-9.5,3.8)),
 'u':((-13.1,.4),(-12.4,4.95),(-8.4,.2),1.50,.95,(-11.7,-1.2),(-9.5,3.8)),
 'm':((-15.2,.2),(-13.6,4.95),(-10,.2),1.12,.90,(-9.8,-.7),(-7.8,3.6)),
 'g':((-14.4,0),(-12.5,4.95),(-8.2,.6),1.42,.90,(-12.4,-.2),(-8.8,3.7)),
 'j':((-13.5,.8),(-13.6,4.95),(-9.5,-1.0),1.18,.72,(-9.2,-.5),(-12.6,3.6)),
}
source=FILE.read_text(encoding='utf-8')
source=re.sub(r'\n        // BEGIN FRAME CATALOG.*?// END FRAME CATALOG\n','\n',source,flags=re.S)
for name in ['down_tube','shock_mount_l','shock_mount_r','shock_mount_web','shock_mount_bolt']:
 source=source.replace('"'+name+'",','"'+name+'__chl",')
lines=[];u=0;v=160;row=0
def cube(bone,name,mat,centre,size,rot=(0,0,0)):
 global u,v,row
 size=tuple(round(x,4) for x in size)
 w=math.ceil(round(2*(size[2]+size[0]),4))+1;h=math.ceil(round(size[2]+size[1],4))+1
 if u+w>128:u=0;v+=row;row=0
 if v+h>256:raise ValueError('Catalog UV overflow')
 values=centre+size+rot
 lines.append(f'        cube({bone}, "{name}", "{mat}", {u}, {v}, '+', '.join(f'{n:.4f}f' for n in values)+');')
 u+=w;row=max(row,h)
def tube(bone,name,code,a,b,width,height=None,x=0):
 dy,dz=b[0]-a[0],b[1]-a[1]
 cube(bone,name+'__'+code,'frame',(x,(a[0]+b[0])/2,(a[1]+b[1])/2),
      (width,height or width,math.hypot(dy,dz)),(math.degrees(math.atan2(-dy,dz)),0,0))
for code,(mid,seat,down,dw,tw,eye,arm) in FRAMES.items():
 seat=(seat[0],4.95-(seat[0]+13.58)*.24)
 tube('frame','top_front',code,(-15.9,-4.17),mid,tw)
 tube('frame','top_rear',code,mid,seat,tw)
 tube('frame','down_front',code,(-15.31,-4.55),down,dw,dw*1.07)
 tube('frame','down_rear',code,down,(-5.6,3.04),dw,dw*1.07)

 for side in [-1,1]:
  cube('frame','brand_shock_mount_'+str(side).replace('-','l')+'__'+code,'frame',
       (side*.55,eye[0],eye[1]),(.3,.9,.9))
  cube('swingarm','brand_arm_eye_'+str(side).replace('-','l')+'__'+code,'frame',
       (side*.55,arm[0]+8.3,arm[1]-4.2),(.3,.9,.9))
 if code=='n':
  tube('frame','lower_vpp_link',code,(-6.2,2.6),(-8.1,4.2),1.4,.45)
  cube('frame','shock_tunnel__n','frame',(0,-7.4,4.0),(1.7,.55,1.5))
 elif code=='t':
  cube('frame','alloy_head_gusset__t','frame',(0,-14.8,-3.6),(1.25,1.7,1.5),(-25,0,0))
 elif code=='u':
  tube('frame','one77_rocker',code,(-10.8,4.7),(-9.1,2.7),1.4,.6)
 elif code=='m':
  tube('frame','vcs_upper',code,(-10.1,4.6),(-9.3,2.9),1.4,.5)
  tube('frame','vcs_lower',code,(-6.5,4.0),(-7.1,2.8),1.35,.5)
 elif code=='g':
  tube('frame','asymmetric_brace',code,seat,(-8.2,.6),.5,.55,-.8)
 elif code=='j':
  for side in [-1,1]:tube('frame','stumpy_rocker_'+('l' if side<0 else 'r'),code,(-12.3,4.7),(-10.2,2.7),.3,.55,side*.7)
catalog='\n        // BEGIN FRAME CATALOG\n'+'\n'.join(lines)+'\n        // END FRAME CATALOG\n'
source=source.replace('        return LayerDefinition.create(mesh, 128, 256);',catalog+'        return LayerDefinition.create(mesh, 128, 256);')
FILE.write_text(source,encoding='utf-8')
print('Wrote',len(lines),'catalog cubes')
