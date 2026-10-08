"""Small pixel assets and vanilla models/recipes for GPS, maps and bike mud."""
from pathlib import Path
import json
import random
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
for name,paint in [('trail_gps',gps),('trail_map',paper)]:
 image(name,paint);write(A/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'descentmtb:item/{name}'}})
write(A/'models/item/trail_map.json',{'parent':'builtin/entity','textures':{'particle':'descentmtb:item/trail_map'},'display':{'gui':{'scale':[.9,.9,.9]},'firstperson_righthand':{'rotation':[0,0,-12],'translation':[1,1,0],'scale':[.75,.75,.75]},'thirdperson_righthand':{'rotation':[0,90,0],'scale':[.65,.65,.65]},'fixed':{'scale':[.95,.95,.95]}}})
def recipe(name,pattern,key,resultcount=1):
 write(ROOT/f'data/descentmtb/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':{k:{'item':'minecraft:'+v} for k,v in key.items()},'result':{'id':'descentmtb:'+name,'count':resultcount}})
recipe('trail_gps',['IRI','ICI',' I '],{'I':'iron_ingot','R':'redstone','C':'compass'})
recipe('trail_map',['PPP','PCP','PPP'],{'P':'paper','C':'compass'})
for lang,labels in {
 'en_us':{'item.descentmtb.trail_map':'Trail map','descentmtb.map.title':'Bikepark routes','descentmtb.map.added':'Added route: %s','descentmtb.map.count':'%s saved routes','descentmtb.map.hint':'Use on a linked sign, or hold a recorded GPS / another map in the other hand to copy. Scroll in the map to select a route.','descentmtb.map.empty':'Record a GPS route and link a trail sign.','descentmtb.map.wrong_dimension':'This route was recorded in another dimension.','descentmtb.map.linked':'GPS route linked to sign.','descentmtb.map.copied':'Routes copied to this map.'},
 'cs_cz':{'item.descentmtb.trail_map':'Mapa trailů','descentmtb.map.title':'Trasy bikeparku','descentmtb.map.added':'Přidaná trasa: %s','descentmtb.map.count':'%s uložených tras','descentmtb.map.hint':'Klikni na ceduli s GPS trasou, nebo měj ve druhé ruce nahrané GPS / jinou mapu pro kopírování. Kolečkem v mapě vybíráš trasu.','descentmtb.map.empty':'Nahraj GPS trasu a připoj ji k ceduli.','descentmtb.map.wrong_dimension':'Tahle trasa vznikla v jiné dimenzi.','descentmtb.map.linked':'GPS trasa připojená k ceduli.','descentmtb.map.copied':'Trasy zkopírované na tuto mapu.'}
}.items():
 p=A/f'lang/{lang}.json';data=json.loads(p.read_text(encoding='utf-8'));data.update(labels);p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Independent transparent effect layers; original frame and terrain textures are preserved.
rng=random.Random(7026)
mud=Image.new('RGBA',(256,256));d=ImageDraw.Draw(mud)
for i in range(1700):
 x,y=rng.randrange(256),rng.randrange(256);radius=rng.choice([0,0,1,1,2]);shade=rng.choice([(85,63,41,180),(101,74,45,220),(65,50,33,190),(128,98,61,170)])
 d.rectangle((x,y,x+radius,y+radius),fill=shade)
mud.save(A/'textures/entity/bike_mud.png')
