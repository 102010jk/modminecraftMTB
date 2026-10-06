"""Small pixel assets and vanilla models/recipes for audio, GPS and riding consumables."""
from pathlib import Path
import json
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
A=ROOT/'assets/descentmtb'
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
def image(name,paint):
 im=Image.new('RGBA',(16,16));paint(ImageDraw.Draw(im));p=A/f'textures/item/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
def gps(d):
 d.rectangle((4,2,11,14),fill='#263840',outline='#111820');d.rectangle((5,4,10,9),fill='#80bd94');d.line([(6,8),(7,6),(9,7),(10,5)],fill='#e1f0b8');d.rectangle((7,11,8,12),fill='#eea044');d.line((10,0,10,2),fill='#64787d')
def paper(d):
 d.rectangle((2,2,13,13),fill='#e6dcc1',outline='#605b50');d.line([(3,11),(5,9),(6,10),(8,6),(10,7),(12,4)],fill='#407e65',width=2);d.rectangle((3,10,4,11),fill='#d38737');d.rectangle((11,3,12,4),fill='#ce5445')
def phones(d):
 d.arc((2,1,13,14),180,360,fill='#536370',width=3);d.rectangle((1,7,4,13),fill='#252d38',outline='#a7b7bd');d.rectangle((11,7,14,13),fill='#252d38',outline='#a7b7bd');d.rectangle((2,8,3,11),fill='#d29b48');d.rectangle((12,8,13,11),fill='#d29b48')
def strip(d):
 d.polygon([(1,6),(12,4),(14,7),(3,10)],fill='#b9dadd',outline='#537a86');d.line((3,7,10,6),fill='#f6ffff');d.rectangle((13,8,15,10),fill='#e9a94d')
for name,paint in [('trail_gps',gps),('trail_map',paper),('headphones',phones),('tear_off',strip)]:
 image(name,paint);write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'descentmtb:item/{name}'}})
im=Image.new('RGBA',(16,16),'#243039');d=ImageDraw.Draw(im);d.rectangle((1,3,6,12),fill='#11191f',outline='#64737b');d.rectangle((9,3,14,12),fill='#11191f',outline='#64737b');d.rectangle((5,0,10,2),fill='#d39841');d.rectangle((7,5,8,7),fill='#a5ddb1');p=A/'textures/block/boombox.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
write(A/'blockstates/boombox.json',{'variants':{'':{'model':'descentmtb:block/boombox'}}})
write(A/'models/block/boombox.json',{'textures':{'all':'descentmtb:block/boombox','particle':'descentmtb:block/boombox'},'elements':[{'from':[0,0,4],'to':[16,10,12],'faces':{face:{'texture':'#all'} for face in ['up','down','north','south','east','west']}}]})
write(A/'models/item/boombox.json',{'parent':'descentmtb:block/boombox'})
# Worn by vanilla's head item layer: a band and two ear cups rather than a floating flat inventory icon.
write(A/'models/item/headphones.json',{'textures':{'all':'descentmtb:item/headphones','particle':'descentmtb:item/headphones'},'elements':[{'from':lo,'to':hi,'faces':{face:{'texture':'#all','uv':[1,7,4,12]} for face in ['up','down','north','south','east','west']}} for lo,hi in [([0,12,3],[16,15,13]),([0,3,3],[3,12,13]),([13,3,3],[16,12,13])]],'display':{'head':{'translation':[0,-2,0],'scale':[1.1,1.1,1.1]},'gui':{'rotation':[25,40,0],'scale':[.85,.85,.85]},'thirdperson_righthand':{'rotation':[0,90,0],'scale':[.5,.5,.5]},'firstperson_righthand':{'rotation':[0,90,0],'scale':[.6,.6,.6]}}})
def recipe(name,pattern,key,resultcount=1):
 write(ROOT/f'data/descentmtb/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':'minecraft:'+v} for k,v in key.items()},'result':{'id':'descentmtb:'+name,'count':resultcount}})
recipe('boombox',['III','RJR','III'],{'I':'iron_ingot','R':'redstone','J':'jukebox'})
recipe('headphones',['III','W W','R R'],{'I':'iron_ingot','W':'black_wool','R':'redstone'})
recipe('trail_gps',['IRI','ICI',' I '],{'I':'iron_ingot','R':'redstone','C':'compass'})
recipe('trail_map',['PPP','PCP','PPP'],{'P':'paper','C':'compass'})
recipe('tear_off',['GGG',' P '],{'G':'glass_pane','P':'paper'},4)
write(ROOT/'data/descentmtb/loot_table/blocks/boombox.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'descentmtb:boombox'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
