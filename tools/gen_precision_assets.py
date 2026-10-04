from pathlib import Path
from PIL import Image,ImageDraw
import json
root=Path('src/main/resources/assets/descentmtb')
def write(path,data):
 p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
for name in ['trail_shovel','trail_hammer']:
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
 d.line((3,13,10,6),fill='#29363c',width=4);d.line((3,12,10,5),fill='#986c42',width=2);d.point((4,11),fill='#dfb65e')
 if name=='trail_shovel':
  d.polygon([(8,5),(9,1),(13,1),(15,3),(14,7),(11,8)],fill='#304750');d.polygon([(9,4),(10,2),(13,2),(14,3),(13,6),(11,7)],fill='#91bdb1');d.line((10,3,12,5),fill='#d3e1c8');d.point((9,6),fill='#dfb65e')
 else:
  d.polygon([(6,3),(9,0),(15,6),(12,9)],fill='#29363c');d.polygon([(7,3),(9,1),(14,6),(12,8)],fill='#ba8854');d.line((8,3,12,7),fill='#dfb65e',width=2);d.point((10,4),fill='#29363c')
 im.save(root/'textures/item'/f'{name}.png');write(Path('models/item')/f'{name}.json',{'parent':'minecraft:item/handheld','textures':{'layer0':f'descentmtb:item/{name}'}})
shape_cs=['Pod kurzorem','SZ roh','SV roh','JZ roh','JV roh','Sever','Východ','Jih','Západ','Celý blok','Prohnutí']
shape_en=['Cursor','NW corner','NE corner','SW corner','SE corner','North edge','East edge','South edge','West edge','Whole block','Takeoff curve']
keys=['auto','nw','ne','sw','se','north','east','south','west','whole','curve']
for locale,names in [('cs_cz',shape_cs),('en_us',shape_en)]:
 p=root/'lang'/f'{locale}.json';data=json.loads(p.read_text(encoding='utf-8-sig'));cs=locale=='cs_cz'
 data.update({f'descentmtb.shape.{key}':text for key,text in zip(keys,names)})
 data.update({'item.descentmtb.trail_shovel':'Trailová lopata' if cs else 'Trail shovel','item.descentmtb.trail_hammer':'Kladivo na lávky' if cs else 'Deck hammer','descentmtb.shape.title':'Jemné tvarování' if cs else 'Precision shaping','descentmtb.shape.hint':'G: výběr tvaru • Shift + kolečko: výška po 1/32 m' if cs else 'Menu key: pick shape • Shift + wheel: height in 1/32 m steps','descentmtb.shape.wheel':'Shift + kolečko • 1/32 m' if cs else 'Shift + wheel • 1/32 m','descentmtb.shape.group.whole':'Plocha' if cs else 'Surface','descentmtb.shape.group.corners':'Rohy' if cs else 'Corners','descentmtb.shape.group.edges':'Hrany' if cs else 'Edges','descentmtb.overlay.hint':'Klik: vtisknout do povrchu • Shift: odstranit' if cs else 'Click: add to the shaped surface • Shift: remove','descentmtb.shaping.hint.place':'Klik: navázat výšku a sklon • Shift: samostatný blok' if cs else 'Click: extend height and slope • Shift: standalone block','descentmtb.machete.hint':'Klik na strom: pokácet • Shift: lesní koridor • zem: ořezat a vyhladit' if cs else 'Tree: fell • Shift: forest corridor • ground: clear and smooth'})
 write(Path('lang')/f'{locale}.json',data)
for name,pattern,key in [('trail_shovel',[' I ',' CI',' S '],{'I':'iron_ingot','C':'copper_ingot','S':'stick'}),('trail_hammer',['CCC',' IS',' S '],{'I':'iron_ingot','C':'copper_ingot','S':'stick'})]:
 p=Path('src/main/resources/data/descentmtb/recipe')/f'{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':f'minecraft:{v}'} for k,v in key.items()},'result':{'id':f'descentmtb:{name}','count':1}},indent=2)+'\n')
