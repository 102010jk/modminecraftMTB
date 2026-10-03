"""Small original pixel tool icons and ordinary JSON block models; no external art dependencies."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/descentmtb'
def save(path,data):
 p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
colors={'berm_tool':'#45c995','route_tool':'#49b4e6','boardwalk_tool':'#d99847','roller_tool':'#ddaa42','undo_tool':'#e97567','clone_tool':'#aa86e8','obstacle_tool':'#93a0a5','measure_tool':'#efcc48','bike_pump':'#f5c439'}
for name,color in colors.items():
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
 if name=='bike_pump':
  d.rectangle((6,3,8,12),fill=color);d.rectangle((4,2,10,3),fill='#28303c');d.rectangle((5,12,10,13),fill='#28303c');d.line((9,6,12,6,13,10,10,12),fill='#596777',width=1)
 else:
  d.line((3,13,11,5),fill='#4e3427',width=3);d.line((3,12,10,5),fill='#af8557',width=1)
  if name=='clone_tool': d.rectangle((7,1,12,6),outline='#263142',fill=color);d.rectangle((10,3,14,8),outline='#263142',fill=color)
  elif name=='undo_tool':d.arc((7,1,14,8),190,490,fill=color,width=2);d.polygon([(7,1),(7,5),(11,4)],fill=color)
  elif name=='measure_tool':d.rectangle((5,1,14,4),fill=color);[d.point((x,3),fill='#273445') for x in range(6,14,2)]
  elif name=='obstacle_tool':d.polygon([(8,6),(9,2),(13,1),(15,5),(12,8)],fill=color)
  else:d.polygon([(8,2),(12,1),(15,4),(13,8),(10,7)],fill=color);d.line((9,4,12,6),fill='#253549',width=1)
 out=root/'textures/item'/f'{name}.png';out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
 save(Path('models/item')/f'{name}.json',{'parent':'minecraft:item/handheld','textures':{'layer0':f'descentmtb:item/{name}'}})
def block_model(name,texture,elements):
 save(Path('models/block')/f'{name}.json',{'textures':{'all':texture,'particle':texture},'elements':[{'from':a,'to':b,'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}} for a,b in elements]})
 save(Path('models/item')/f'{name}.json',{'parent':f'descentmtb:block/{name}'})
block_model('trail_roots','minecraft:block/oak_log', [([0,0,3],[16,3,6]),([0,0,10],[16,2.5,13]),([2,0,2],[5,2,15])])
block_model('trail_rock','minecraft:block/stone',[([2,0,2],[14,2,14]),([4,2,3],[13,4,12]),([5,4,5],[10,6,10])])
for name in ['trail_roots','trail_rock']:
 save(Path('blockstates')/f'{name}.json',{'variants':{f'facing={face}':{'model':f'descentmtb:block/{name}','y':yaw} for face,yaw in [('north',0),('east',90),('south',180),('west',270)]}})
save(Path('models/block/trail_stake.json'),{'textures':{'pole':'minecraft:block/oak_log','flag':'minecraft:block/orange_concrete','particle':'minecraft:block/oak_log'},'elements':[{'from':[7,0,7],'to':[9,16,9],'faces':{f:{'texture':'#pole'} for f in ['up','down','north','south','east','west']}},{'from':[9,12,7.5],'to':[15,16,8.5],'faces':{f:{'texture':'#flag'} for f in ['up','down','north','south','east','west']}}]})
save(Path('blockstates/trail_stake.json'),{'variants':{'':{'model':'descentmtb:block/trail_stake'}}})
save(Path('models/item/trail_stake.json'),{'parent':'descentmtb:block/trail_stake'})
languages={
 'item.descentmtb.berm_tool':('Kolíky na klopenky','Berm guide'), 'item.descentmtb.route_tool':('Lopatka na plynulou cestu','Flow trail shaper'),
 'item.descentmtb.boardwalk_tool':('Stavitel dřevěných lávek','Boardwalk builder'),'item.descentmtb.roller_tool':('Stavitel rollerů','Pumptrack roller builder'),
 'item.descentmtb.clone_tool':('Kopírka tratí','Trail clone tool'),'item.descentmtb.undo_tool':('Vrátit úpravu tratě','Trail undo tool'),
 'item.descentmtb.obstacle_tool':('Kořeny a kamenné pasáže','Roots and rock garden tool'),'item.descentmtb.measure_tool':('Měřidlo délky a sklonu','Trail tape and clinometer'),
 'item.descentmtb.bike_pump':('Pumpička na kolo a vidlici','Tyre and fork pump'),'block.descentmtb.trail_surface':('Tvarovaný povrch tratě','Sculpted trail surface'),
 'block.descentmtb.trail_stake':('Vytyčovací kolík','Trail guide stake'),'block.descentmtb.trail_roots':('Kořeny','Trail roots'),'block.descentmtb.trail_rock':('Kámen na trať','Trail rock'),
 'descentmtb.builder.creative':('Stavební nástroje vyžadují creative (lze změnit v configu).','Construction tools require creative (configurable).'),
 'descentmtb.builder.cleared':('Vytyčení zrušeno','Guides cleared'),'descentmtb.builder.guide':('Bod %s/%s vytyčen','Guide %s/%s set'),
 'descentmtb.builder.done':('Upraveno %s bloků • nástrojem zpět lze změnu vrátit','Edited %s blocks • use undo to restore'),
 'descentmtb.builder.undone':('Vráceno %s bloků','Restored %s blocks'),
 'descentmtb.builder.hint':('Klikni na terén nebo kolíky; Shift+klik do vzduchu mění režim, na blok ruší vytyčení.','Click terrain/stakes; sneak-use in air cycles mode, on block clears guides.'),
 'descentmtb.clone.first':('První roh vybraný; klikni na druhý','First corner set; click the second'),
 'descentmtb.clone.copied':('Zkopírováno %s bloků; klikni pro vložení','Copied %s blocks; click to paste'),
 'descentmtb.clone.pasted':('Vloženo %s bloků','Pasted %s blocks'),'descentmtb.clone.rotated':('Kopie otočena o 90°','Copy rotated 90°'),
 'descentmtb.clone.hint':('Dva rohy: výběr; další kliky: kopie; Shift+vzduch: otočení; Shift+blok: nový výběr.','Two corners: capture; next clicks: paste; sneak-air: rotate; sneak-block: new capture.'),
 'descentmtb.pump.valve.0':('Přední pneumatika','Front tyre'),'descentmtb.pump.valve.1':('Zadní pneumatika','Rear tyre'),'descentmtb.pump.valve.2':('Vzduchová vidlice','Air fork'),
 'descentmtb.pump.hint':('Shift+vzduch: ventil; klik na odstavené kolo: dofouknout; Shift+klik: upustit.','Sneak-air: valve; click parked bike: inflate; sneak-click: bleed.'),
 'descentmtb.pump.pressure':('%s: %s PSI','%s: %s PSI'),
 'descentmtb.obstacle.mode.0':('Kořeny napříč tratí','Cross-trail roots'),'descentmtb.obstacle.mode.1':('Kameny','Rocks'),'descentmtb.obstacle.mode.2':('Rock garden','Rock garden'),
}
for key,cs,en in [('flow','Plynulá cesta (3 body)','Smooth flow trail (3 guides)'),('berm','Klopenka (3 body)','Berm (3 guides)'),('enduro','Široká enduro klopenka','Gentle enduro berm'),('sharkfin','Sharkfin s odrazem','Sharkfin with an exit lip'),('boardwalk','Dřevěná lávka (2 body)','Elevated boardwalk (2 guides)'),('kicker','Dřevěný kicker','Wooden kicker'),('drop','Dřevěný drop','Wooden drop'),('rollers','Pumptrack rollery (2 body)','Pumptrack rollers (2 guides)'),('measure','Délka, převýšení a sklon','Length, elevation and grade')]:languages[f'descentmtb.builder.shape.{key}']=(cs,en)
for key,cs,en in [('pedalPower','Síla sprintu (W)','Pedal sprint power (W)'),('pedalForce','Síla rozjezdu (N)','Low-speed pedal force (N)'),('pedalSpeedKmh','Rychlost šlapání (km/h)','Pedalling speed (km/h)'),('brakeStrength','Síla brzd','Brake strength'),('manualAngleDegrees','Úhel manualu (°)','Manual balance angle (°)'),('airControl','Ovládání ve vzduchu','Air control'),('airRecovery','Srovnání do dopadu','Landing alignment'),('flipRate','Rychlost flipů','Flip speed'),('spinRate','Rychlost otoček','Spin speed'),('pressureEffect','Vliv tlaku pneumatik','Tyre pressure effect'),('wallRides','Wallride','Wall rides'),('trickBanner','Výrazný text triků','Trick banner')]:languages[f'descentmtb.config.{key}']=(cs,en)
for filename,index in [('cs_cz.json',0),('en_us.json',1)]:
 p=root/'lang'/filename;data=json.loads(p.read_text(encoding='utf-8'));data.update({key:values[index] for key,values in languages.items()});p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated 9 tool icons, obstacle/stake models and translations.')
