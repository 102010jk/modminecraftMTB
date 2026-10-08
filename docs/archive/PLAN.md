# Descent MTB v1.0 – plán „Descenders v Minecraftu“

Cíl: celoodpružené enduro/DH kolo v **reálné velikosti**, postavené na **skutečné fyzice**
(tuhé těleso, pružiny, pneumatiky), ale **naladěné tak, aby se ovládalo jako Descenders**.
NeoForge 1.21.1, projekt `C:\MTBMod` (mod id `descentmtb`).

---

## 0. Co je hlavní poučení z v0.1–v0.4

Dosavadní verze byly „arcade entita“: rychlost + yaw, gravitace z Minecraftu, skok = impuls.
Proto to nikdy nepůsobilo jako Descenders – kolo nemá kola, odpružení ani hmotnost, takže
nemůže existovat odhopsnutí z hrany, pumpování, dopad do kopce ani přirozený bunnyhop.

**v1.0 = nové fyzikální jádro**, zbytek (registrace, item, vstupy z ovladače, HUD) se znovu použije.

### Proč ne Sable / Valkyrien Skies
Sable je engine pro létající/plovoucí *bloková seskupení* (sub-levels). Na kolo, které jede
po terénu na dvou pneumatikách, je to špatný nástroj: těžká závislost, žádný model
pneumatiky ani odpružení a ladit „feel“ přes cizí solver je peklo. Descenders sám je
*vlastní* model vozidla (raycast kola + asistence) nad Unity fyzikou. Uděláme totéž – menší,
rychlejší a plně pod kontrolou.

---

## 1. Feel spec – co znamená „feeluje jako Descenders“

Tohle jsou měřitelné cíle; každý bod má v jádru svůj parametr a headless test.

| # | Pocit z Descenders | Jak ho dosáhneme fyzikou | Cílová hodnota (start ladění) |
|---|---|---|---|
| F1 | **Rychlost dělá kopec, ne šlapání.** Šlapání pomáhá jen do ~25 km/h. | Gravitace 9,81 m/s² po svahu, valivý odpor + odpor vzduchu, šlapací síla klesá s rychlostí | 30° svah → ~55 km/h za 5 s; šlapání po rovině max ~28 km/h |
| F2 | **Těžká hybnost**, kolo se „nese“, nezastaví se na kameni. | Hmotnost 16 kg kolo + 75 kg jezdec, setrvačnost kol | z 50 km/h dojezd po rovině bez brzd ~15 s |
| F3 | **Zatáčení náklonem**, kolo se samo položí do zatáčky. | Asistovaná rovnováha: PD regulátor drží náklon `atan(v²/(r·g))`, řidítka jen zadávají poloměr | plný rejd při 40 km/h = náklon ~40°, reakce < 0,25 s |
| F4 | **Postupná ztráta gripu**, smyk na volném povrchu, ne led. | Model pneumatiky se slip angle (zjednodušená Pacejka), grip podle bloku | hlína 1.0, tráva 0.85, štěrk 0.7, písek 0.55, bláto 0.5, led 0.15 |
| F5 | **Odpružení spolkne malé nerovnosti**, kolo se neodráží od schodu. | Vidlice 170 mm + tlumič 160 mm (pružina-tlumič, progrese), vyhlazený povrch terénu (viz §3) | 1-blokový schod při 30 km/h: žádný odskok, jen propružení |
| F6 | **Pre-jump / pop:** podržet A = přikrčit, pustit na hraně = vyšší skok. | Jezdec je samostatná hmota na pružině (ruce/nohy); pop = rychlé natažení nohou → reakční síla přes pneumatiky | pop na rovině 0,6 m, pop z hrany +40–60 % výšky |
| F7 | **Bunnyhop** – časování rozhoduje, perfektní = vyskočí na 1 blok. | Fáze: komprese → zvednutí předku (manual) → přenos dopředu → zvednutí zadku | max hop 1,05 m (= 1 blok + rezerva) jen při perfektním časování |
| F8 | **Vzduch: kontrolovaná rotace**, kolo se jemně srovná do dráhy letu. | Rotace řízené vstupem (zachovává moment hybnosti + „air assist“ moment k vektoru rychlosti) | backflip ~0,9 s, 360 ~0,8 s; asistence vypnutelná |
| F9 | **Dopad do kopce = zachovaná rychlost, na rovinu = ztráta / pád.** | Rozklad dopadové rychlosti do normály terénu; normálová složka → odpružení / bail | bail když úhel kolo↔terén > 35° nebo normálová rychlost > ~9 m/s |
| F10 | **Pump** – zatlačení na zádech terénních vln přidá rychlost. | Jezdec tlačí kolo do svahu (síla podél normály na odvrácené straně vlny) | rollery: +2–4 km/h na vlnu při dobrém časování |
| F11 | **Kamera** plynule za jezdcem, mírně zpožděná, FOV roste s rychlostí. | Pružinová chase kamera (§6) | zpoždění yaw ~0,15 s, FOV +12° při 60 km/h |

> Princip: **fyzika je základ, Descenders feel dělají „asistence“** (rovnováha, srovnání ve
> vzduchu, pop z hran, odpuštění dopadů). Každá asistence je jeden parametr 0–1, takže jde
> posunout od „arcade“ k „sim“ bez přepisování kódu.

---

## 2. Rozměry (1 blok = 1 m, hráč je 1,8 m – sedí to přesně)

Enduro/DH 29": kola Ø 0,75 m, rozvor 1,26 m, výška středu šlapání 0,35 m, šířka řidítek
0,80 m, úhel hlavy 63,5°. Model z `Downloads\enduro_bike.json` (77 elementů, Blockbench)
použijeme jako základ, ale rozdělíme do kostí a přeměříme na tyhle rozměry.

---

## 3. Fyzikální jádro (čistá Java, bez Minecraftu)

Balík `com.descentmtb.physics` – **žádný import z `net.minecraft`**, aby šel testovat headless.

- **`BikeBody`** – tuhé těleso: pozice, rychlost, orientace (kvaternion), úhlová rychlost,
  tenzor setrvačnosti. Pevný krok **240 Hz** (12 sub-kroků na tick), semi-implicitní Euler.
- **`Wheel` ×2** – „raycast/spherecast kolo“: paprsek od uchycení odpružení dolů podél osy
  vidlice/zadní stavby, kontakt → komprese → síla pružiny + tlumiče (zvlášť komprese/odskok,
  progresivní křivka, bottom-out doraz). Rotace kola, setrvačnost, brzdný moment.
- **`TireModel`** – podélný a boční skluz → síla, třecí elipsa (brzda v zatáčce ubírá
  boční grip), koeficient podle povrchu (`SurfaceTable`: blok/tag → grip, valivý odpor,
  částice, zvuk).
- **`Rider`** – hmota 75 kg spojená s rámem pružinou-tlumičem ve 2 osách (výška + dopředu/
  dozadu). Vstupy hýbou *cílovou* polohou jezdce → vzniká přikrčení, pop, manual, pump,
  vstřebání dopadu. Tohle je klíč k F6/F7/F10.
- **`BalanceAssist`** – PD regulátor náklonu + asistence proti převrácení dopředu/dozadu.
- **`AirControl`** – ve vzduchu: momenty od vstupu (flipy/spiny), air-assist srovnání.
- **`TerrainQuery`** (rozhraní) – jádro se ptá jen přes něj: `raycast`, `surfaceAt`,
  `smoothedHeight`. Implementace v MC i falešný terén pro testy.

### Problém kostičkového terénu (nejdůležitější technická věc)
Skutečné kolo nevyjede 1m schod. V Minecraftu jsou všechny kopce schody. Řešení:
1. **Vyhlazená kontaktní plocha**: pro kola počítáme výšku jako bilineární interpolaci
   horních hran sloupců v okolí 3×3 (+ zohlednění slabů/schodů přes jejich VoxelShape).
   Schodovitý kopec = hladký svah. Normála terénu = gradient téhle plochy.
2. **Skutečná geometrie** pro zdi a srázy: když je rozdíl sloupců ≥ 2 bloky nebo jde
   o zeď → kontakt s reálným tvarem (náraz / sjezd z hrany = skok).
3. **Vlastní trailové bloky** (§8) s přesně definovanými šikmými plochami – stavění tratí.

### Headless testy (JUnit) – protože hru nemůžu sám hrát
`./gradlew test` spustí scénáře na syntetickém terénu a ověří feel spec čísly:
sjezd svahem (F1), dojezd (F2), ustálený náklon v zatáčce (F3), schod (F5), pop a hop
výška (F6/F7), dopad do kopce vs. na rovinu (F9), pump na rollerech (F10).
Výstup i jako CSV telemetrie (rychlost, komprese, náklon) pro grafy při ladění.

---

## 4. Napojení na Minecraft

- **`MountainBikeEntity`** – nositel stavu. Když na kole někdo sedí, **simuluje klient
  jezdce** (jako loď) každý frame s pevným krokem; server dostává stav 20× za s
  (`BikeStatePayload`: pozice, kvaternion, komprese, rejd, otáčky kol, poloha jezdce,
  stav triku) a přeposílá ostatním. Server dělá sanity check (rychlost, teleport).
- Vzdálení hráči: interpolace s bufferem ~100 ms.
- Prázdné kolo: zjednodušená simulace na serveru (dojede, spadne na bok).
- Entita má hitbox 0,5×1,2 m, poškození pádem jezdce se ruší (nahrazeno bail systémem).
- Render se interpoluje mezi kroky fyziky podle `partialTick` → plynulé i při 144 FPS.

---

## 5. Model kola a jezdce

- **Kolo**: Blockbench „Modded Entity“ export → `ModelPart` s kostmi: rám, zadní stavba
  (otočná kolem čepu), tlumič (2 části, zasouvání), korunka + horní nohy vidlice, spodní
  nohy (posun podle komprese), řidítka, přední/zadní kolo, kliky, pedály. Všechno je
  **procedurální z fyziky** (komprese, rejd, otáčení) → GeckoLib není potřeba.
- **Jezdec**: knihovna **Player Animation Library** (NeoForge 1.21.1, načítá Blockbench/
  GeckoLib animace) – základní póza na kole (ruce na gripech, nohy na pedálech) se
  procedurálně míchá podle polohy `Rider` (přikrčení, předklon, záklon) + keyframe
  animace triků (superman, no-hander, tabletop…). Fallback bez knihovny: vlastní mixin
  do `HumanoidModel.setupAnim`.

---

## 6. Kamera (vlastní, ne Camera Overhaul – aby nebyly konflikty)

Přepínání `V`/Select:
1. **Chase cam (výchozí, Descenders)** – za a nad jezdcem, pružinově tlumená poloha +
   zpožděný yaw, výhled dopředu po směru rychlosti, mírný roll s náklonem, FOV podle
   rychlosti, raycast proti terénu aby necouvala do kopce, otřesy od odpružení.
2. **Helmet cam** – POV z hlavy, náklon s kolem, jemné vibrace podle povrchu.
3. **Cinematic** – boční pevné body podél trati (replay-like).
Implementace: `ViewportEvent.ComputeCameraAngles` (yaw/pitch/roll), `ComputeFov`,
mixin do `Camera.setup` pro vlastní pozici.

---

## 7. Ovládání (výchozí mapování ve stylu Descenders, vše přemapovatelné)

| Akce | Ovladač | Klávesnice |
|---|---|---|
| Řízení | levá páčka do stran | A / D |
| Předklon / záklon těla | levá páčka nahoru / dolů | šipka ↑ / ↓ |
| Šlapání | RT | W |
| Brzda (zadní / přední) | LT / LB | S / Ctrl |
| Přikrčení → pop / bunnyhop | A (držet, pustit) | Mezerník |
| Triky ve vzduchu (tweaky) | pravá páčka (směr = trik) | I J K L |
| Flip / spin ve vzduchu | levá páčka nahoru-dolů / do stran | ↑ ↓ / A D ve vzduchu |
| Manual | levá páčka dolů na zemi | ↓ na zemi |
| Kamera | Select / pravá páčka klik | V |
| Reset na kolo / checkpoint | Y | Backspace |

Vstup z ovladače: stávající GLFW čtení (funguje bez závislostí) + volitelná kompatibilita
s **Controllable**, aby se nebila s jeho ovládáním menu.

---

## 8. Hratelnost

- **Triky**: flipy, 360/720, whip, tabletop, superman, no-hander, tuck no-hander, manual,
  nose manual. Combo + body (rep) jako v Descenders, multiplikátor za čistý dopad.
- **Bail**: zlý dopad / náraz → jezdec letí jako ragdoll (jednoduchý Verlet skelet nad
  modelem hráče), kolo se kutálí samo, po 2 s respawn na kole na posledním checkpointu.
- **Zvuky**: řehtačka náboje, pneumatika podle povrchu, klepání odpružení, řetěz,
  vítr podle rychlosti, dopady. **Částice**: hlína/prach za zadním kolem při smyku.
- **Trailové bloky**: rampy (kicker 20°/30°/40°), dopady, berm (klopená zatáčka – rovná,
  vnitřní, vnější díl), roller, step-up, drop; hlína/štěrk/kořeny varianty. Start/cíl
  brána + stopky, checkpointy.
- **Nastavení kola** (GUI): tvrdost pružin, odskok, tlak v pneu, velikost kol, + presety
  DH / Enduro / Dirt jump. Konfig `config/descentmtb-physics.toml` s live reloadem pro ladění.

### Doporučený modpack kolem (nejsou to závislosti)
- **Tectonic** – obří pohoří, ideální na sjezdy.
- **Sodium + Distant Horizons** – výkon a dlouhý výhled do údolí (Descenders atmosféra).
- **Iris + shader** – volitelně.
- **WorldEdit / Axiom** – stavění tratí.

---

## 9. Fáze (každá končí hratelným buildem)

| Fáze | Obsah | Hotovo když |
|---|---|---|
| **P1 Jádro** | `physics` balík, testovací terén, JUnit feel testy F1–F5 | testy zelené, CSV telemetrie dává smysl |
| **P2 V Minecraftu** | entita, klientská simulace, síť, interpolace, vyhlazený terén | dá se sjet Tectonic kopec bez skákání po schodech |
| **P3 Model + jezdec** | nový model s kostmi, procedurální odpružení, póza jezdce | odpružení a rejd viditelně pracují |
| **P4 Kamera** | chase / helmet / cinematic | plynulá kamera bez trhání při 144 FPS |
| **P5 Pop, hop, pump, manual** | `Rider` vstupy, F6/F7/F10 testy | hop na 1 blok jen s dobrým časováním |
| **P6 Vzduch + triky + dopady** | air control, triky, skóre, bail | combo HUD, pád při špatném dopadu |
| **P7 Zvuk a částice** | | |
| **P8 Trailové bloky + závody** | rampy, bermy, start/cíl, stopky | postavená testovací trať |
| **P9 Ladění feel** | iterace s tvým playtestem, presety kol | „feeluje to jako Descenders“ |

**Ladění (P9) je nejdůležitější a stojí na tobě:** já nemůžu hrát, takže po každé fázi
pošleš krátký popis (případně video) a já upravím parametry. Konfig s live reloadem
znamená, že spoustu věcí doladíš i bez rebuildu.

---

## 10. Rizika

- **Výkon**: 240 Hz × raycasty – OK pro jedno kolo, ale vyhlazený terén cachovat po sloupcích.
- **Multiplayer desync** u klientské autority – stejný kompromis jako u lodí, přijatelné.
- **Player Animation Library** se může tlouct s jinými mody na animace hráče → fallback mixin.
- **Feel je subjektivní** – proto feel spec v číslech + asistence jako posuvníky.
