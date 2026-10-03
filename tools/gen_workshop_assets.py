from pathlib import Path
import json
from PIL import Image,ImageDraw
base=Path('src/main/resources/assets/descentmtb'); data=Path('src/main/resources/data/descentmtb')
def jsonfile(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
names=['flow','raise','lower','smooth','flatten','pump_line','pump_loop','dirt_jump','wood_kicker','wood_drop','drop_edge','berm','enduro','sharkfin','boardwalk','support','clone','template','roots','rocks','rock_garden','barrier','airbag','sign','measure','undo']
cs=['Cesta','Zvedat','Snížit','Vyhladit','Srovnat','Pumptrack','Okruh','Dirt skok','Kicker','Wood drop','Hrana dropu','Klopenka','Enduro','Sharkfin','Lávka','Podpěra','Klonovat','Šablona','Kořeny','Kameny','Rock garden','Bariéra','Airbag','Cedule','Měřit','Vrátit']
en=['Flow trail','Raise','Lower','Smooth','Flatten','Pump line','Pump loop','Dirt jump','Wood kicker','Wood drop','Drop edge','Berm','Enduro berm','Sharkfin','Boardwalk','Support','Clone','Template','Roots','Rocks','Rock garden','Barrier','Airbag','Paint sign','Measure','Undo']
colors=['#96a875']*7+['#d7a455']*4+['#72b5a0']*3+['#b89a75']*4+['#8da2b3']*8
for n,color in zip(names,colors):
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.rectangle((1,1,14,14),fill='#28353c',outline='#6f6858');d.point([(2,2),(13,2),(2,13),(13,13)],fill='#dabd82')
 if n in ['raise','lower']:
  d.line((4,10,12,10),fill='#718078',width=2);d.line((8,3,8,9),fill=color,width=2);pts=[(4,6),(8,3),(12,6)] if n=='raise' else [(4,6),(8,9),(12,6)];d.line(pts,fill=color,width=2)
 elif n in ['flow','smooth','flatten']:
  d.line([(3,11),(5,10),(8,6 if n=='flow' else 10),(11,6 if n=='flow' else 10),(13,5 if n=='flow' else 10)],fill=color,width=2)
 elif n in ['pump_line','pump_loop']:
  if n=='pump_loop':d.ellipse((3,4,12,11),outline=color,width=2)
  else:d.line([(3,10),(5,5),(7,10),(9,5),(11,10),(13,5)],fill=color,width=2)
 elif n in ['dirt_jump','wood_kicker','wood_drop','drop_edge','sharkfin']:
  pts=[(3,12),(6,11),(9,8),(12,4),(12,12)] if n!='wood_drop' else [(3,5),(9,5),(9,12)];d.line(pts,fill=color,width=2);d.line((3,13,13,13),fill='#768176')
 elif n in ['berm','enduro']:
  d.arc((2,2,13,13),0,130,fill=color,width=3);d.arc((5,4,13,12),0,130,fill='#dfc791',width=1)
 elif n in ['boardwalk','support','barrier']:
  d.rectangle((3,4,12,6),fill=color);d.line((4,7,4,12),fill='#96714f',width=2);d.line((11,7,11,12),fill='#96714f',width=2)
  if n=='barrier':d.rectangle((5,7,10,9),fill='#d77d54')
 elif n in ['clone','template','undo']:
  d.rectangle((3,3,8,8),outline=color,width=2);d.rectangle((7,7,12,12),outline='#dac698',width=2)
  if n=='undo':d.line([(12,4),(5,4),(5,7)],fill='#76c8b7',width=2)
 elif n in ['roots','rocks','rock_garden']:
  if n=='roots':d.line([(3,9),(6,6),(8,10),(12,7)],fill='#b9905f',width=3)
  else:d.polygon([(3,11),(4,6),(8,4),(12,8),(12,11)],fill=color);d.line((5,6,8,5),fill='#dbe4d9',width=1)
 elif n=='airbag':d.rounded_rectangle((3,5,12,11),radius=2,fill='#72bca7',outline='#d7e1c1');d.line((4,8,11,8),fill='#417b78')
 elif n=='sign':d.rectangle((3,3,12,10),fill='#c4a779');d.line((7,11,7,13),fill='#a18668',width=2);d.line([(5,6),(10,6),(8,4)],fill='#2d4851',width=2)
 else:d.line((3,11,12,4),fill=color,width=3);d.point([(5,10),(7,8),(9,6)],fill='#25353c')
 path=base/'textures/gui/trail'/f'{n}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im);d.line((3,14,11,5),fill='#394c55',width=4);d.line((3,14,11,5),fill='#a78a5d',width=2);d.ellipse((7,1,14,8),fill='#3d575d',outline='#ddba6d',width=2);d.point([(10,2),(13,5)],fill='#80d6c0');im.save(base/'textures/item/trail_wand.png');jsonfile(base/'models/item/trail_wand.json',{'parent':'minecraft:item/handheld','textures':{'layer0':'descentmtb:item/trail_wand'}})
# Fabric cushion, stitching and mechanical safety edging are original pixel assets.
for n in ['airbag','cloth_barrier','trail_sign']:
 im=Image.new('RGB',(16,16),'#70a99e' if n=='airbag' else '#c7a278' if n=='trail_sign' else '#d89955');d=ImageDraw.Draw(im)
 if n=='airbag':
  for k in [0,7,15]:d.line((k,0,k,15),fill='#41766e');d.line((0,k,15,k),fill='#41766e')
  for x in range(2,15,2):d.point((x,1),fill='#c5dbc4');d.point((x,14),fill='#c5dbc4')
 elif n=='trail_sign':
  for y in [3,8,13]:d.line((0,y,15,y),fill='#94775c')
  d.point([(1,1),(14,1),(1,14),(14,14)],fill='#35464d')
 else:
  for x in range(-10,20,6):d.polygon([(x,0),(x+3,0),(x+15,15),(x+12,15)],fill='#4a5555')
 path=base/'textures/block'/f'{n}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
 jsonfile(base/'models/item'/f'{n}.json',{'parent':f'descentmtb:block/{n}'})
 if n=='airbag':jsonfile(base/'blockstates/airbag.json',{'variants':{'':{'model':'descentmtb:block/airbag'}}});jsonfile(base/'models/block/airbag.json',{'parent':'minecraft:block/cube_all','textures':{'all':'descentmtb:block/airbag'}})
 elif n=='cloth_barrier':
  jsonfile(base/'blockstates/cloth_barrier.json',{'variants':{'':{'model':'descentmtb:block/cloth_barrier'}}});jsonfile(base/'models/block/cloth_barrier.json',{'textures':{'fabric':'descentmtb:block/cloth_barrier','wood':'minecraft:block/oak_log','particle':'descentmtb:block/cloth_barrier'},'elements':[{'from':[0,0,7],'to':[2,16,9],'faces':{f:{'texture':'#wood'} for f in ['north','south','east','west','up','down']}},{'from':[2,5,7.8],'to':[16,14,8.2],'faces':{f:{'texture':'#fabric'} for f in ['north','south','east','west','up','down']}}]})
 else:
  jsonfile(base/'blockstates/trail_sign.json',{'variants':{f'facing={f}':{'model':'descentmtb:block/trail_sign','y':rot} for f,rot in [('north',0),('east',90),('south',180),('west',270)]}})
  jsonfile(base/'models/block/trail_sign.json',{'textures':{'board':'descentmtb:block/trail_sign','wood':'minecraft:block/oak_log','particle':'descentmtb:block/trail_sign'},'elements':[{'from':[1,4,4],'to':[15,16,5],'faces':{f:{'texture':'#board'} for f in ['north','south','east','west','up','down']}},{'from':[7,0,7],'to':[9,15,9],'faces':{f:{'texture':'#wood'} for f in ['north','south','east','west','up','down']}}]})
for n in ['trail_roots','trail_rock','trail_stake','airbag','cloth_barrier','trail_sign']:
 jsonfile(data/'loot_table/blocks'/f'{n}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'descentmtb:{n}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
jsonfile(data/'recipe/trail_wand.json',{'type':'minecraft:crafting_shaped','pattern':[' CI',' RS','S  '],'key':{'C':{'item':'minecraft:copper_ingot'},'I':{'item':'minecraft:iron_ingot'},'R':{'item':'minecraft:redstone'},'S':{'item':'minecraft:stick'}},'result':{'id':'descentmtb:trail_wand','count':1}})
for lang,labels,cats in [('cs_cz',cs,['Terén','Skoky','Klopenky','Konstrukce','Vybavení']),('en_us',en,['Terrain','Jumps','Berms','Structures','Equipment'])]:
 p=base/'lang'/f'{lang}.json';t=json.loads(p.read_text(encoding='utf-8-sig'));cz=lang=='cs_cz'
 t.update({f'descentmtb.wand.mode.{n}':v for n,v in zip(names,labels)});t.update({f'descentmtb.wand.category.{i}':v for i,v in enumerate(cats)})
 pairs={'title':('TRAIL WORKSHOP','TRAIL WORKSHOP'),'settings':('Nastavení a návrh','Settings & design'),'hint':('Podrž G: režimy • kliknutí: vodicí body • Shift: nový výběr','Hold G: modes • click: guides • Shift: new selection'),'preview':('Náhled: %s bloků • G → Nastavení → Potvrdit','Preview: %s blocks • G → Settings → Confirm'),'confirm':('Potvrdit návrh','Build design'),'cancel':('Zahodit návrh','Discard design'),'rotate':('Otočit o 90°','Rotate 90°'),'template':('Název šablony','Template name'),'saved':('Šablona „%s“ uložená','Template “%s” saved'),'save':('Uložit šablonu','Save template'),'load':('Načíst šablonu','Load template'),'undo':('Vrátit úpravu','Undo edit'),'apply':('Použít nastavení','Apply settings')}
 for k,v in pairs.items():t['descentmtb.wand.'+k]=v[0 if cz else 1]
 for k,cslabel,enlabel in [('width','Šířka: %s m','Width: %s m'),('height','Výška: %s m','Height: %s m'),('spacing','Rozestup: %s m','Spacing: %s m'),('repeats','Vlny: %s','Rollers: %s'),('radius','Poloměr: %s m','Radius: %s m'),('strength','Síla: %s','Strength: %s'),('softness','Měkkost: %s','Soft edge: %s')]:t['descentmtb.wand.setting.'+k]=cslabel if cz else enlabel
 t['key.descentmtb.trail_menu']='Radiální nabídka trailové hůlky' if cz else 'Trail wand radial menu';t['item.descentmtb.trail_wand']='Trailová hůlka' if cz else 'Trail workshop wand'
 for n,cv,ev in [('airbag','Dopadový airbag','Landing airbag'),('cloth_barrier','Látková bariéra','Fabric trail barrier'),('trail_sign','Kreslicí cedule','Paintable trail sign')]:t['block.descentmtb.'+n]=cv if cz else ev
 for k,cv,ev in [('editor','TRAIL CANVAS · 16 × 16','TRAIL CANVAS · 16 × 16'),('undo','Vrátit tah','Undo stroke'),('copy','Kopírovat','Copy'),('paste','Vložit obraz','Paste image'),('save','Uložit ceduli','Save canvas'),('cancel','Zavřít','Cancel')]:t['descentmtb.sign.'+k]=cv if cz else ev
 for i,(cv,ev) in enumerate([('Kreslit','Draw'),('Guma','Erase'),('Výplň','Fill')]):t[f'descentmtb.sign.tool.{i}']=cv if cz else ev
 for i,(cv,ev) in enumerate([('Šipka','Arrow'),('Obtížnost','Difficulty'),('Skok','Jump'),('Drop','Drop'),('Pozor','Warning')]):t[f'descentmtb.sign.template.{i}']=cv if cz else ev
 jsonfile(p,t)
print('26 original mode icons, wand, equipment textures and localized editor generated')
