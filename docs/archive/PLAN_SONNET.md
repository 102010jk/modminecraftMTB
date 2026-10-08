# Descent MTB – plán v1.1 pro Sonnet 5.5: nástroje, ruční stavba, drift, optimalizace

> **Pro koho:** implementátor (Claude Sonnet 5.5). Plán napsal Opus 5.5 podle zpětné vazby hráče.
> **Textury už jsou hotové** (viz §6). Nekresli nové, jen je zapoj.
> Projekt: `C:\MTBMod`, NeoForge **21.1.250**, MC 1.21.1, Java 21, mod id `descentmtb`.
> Build/testy: `.\gradlew.bat build` (spustí i headless testy), in-game: `.\gradlew.bat runClientAuto` (autopilot, viz §7).

---

## 0. Co hráč řekl (zdroj pravdy)

1. **Klopenky ani pumptrack nejde postavit ručně.** Všechno jde jen přes „divnou“ hůlku, která je matoucí a špatně se používá.
2. **Kolo strašně driftuje v zatáčkách.**
3. Radiální menu mělo být u **klikacího stavitele tratí** (vytyčíš body → postaví se klopenka/pumptrack/skok…), ne u terénního štětce.
4. **Nechce „smooth“ hůlku** (zvednout/snížit/vyhladit/srovnat štětcem). Představa:
   - **Nástroj 1** = jeden stavitel, všechny tvary tratí (klopenky, pumptrack, skoky, dřevo, vybavení) přes radiální menu.
   - **Nástroj 2** = jednoduchý: klikneš → **pokácí strom**, **trochu uhladí terén** (uklidí trávu, srovná schůdky). **Nikdy** nestaví klopenky.
5. **Všechno, co postaví nástroj 1, musí jít postavit i ručně bloky** (bez jakékoli hůlky).
6. Textury jsou příšerné → nové (hotovo, §6).
7. **Čas optimalizovat.** Velký pumptrack musí jet plynule.
8. Prověřit nástroje, zbytečné odstranit/sloučit.

### Definition of Done
- [ ] V kreativní záložce jsou jen itemy z tabulky §2.1. Staré nástroje zmizely, staré světy se načtou bez pádu (aliasy §2.5).
- [ ] Klopenku, válce pumptracku i kicker postavíš **jen bloky Tvarovací hlíny / Dřevěné desky / Rampy** (§3), bez nástroje 1 i 2.
- [ ] Nástroj 1 má radiální menu (podržet G), všechny stavební režimy, náhled → potvrzení. Žádné štětce terénu.
- [ ] Nástroj 2 kácí stromy a lehce uhlazuje, nic víc.
- [ ] Drift: testy v §4.4 prochází. Na klávesnici při plném rejdu na hlíně kolo neklouže.
- [ ] Výkon: scéna s pumptrackem 64×44 m má ≥ 90 % FPS stejné scény z vanilla bloků (měří autopilot, §5.7).
- [ ] `.\gradlew.bat build` zelený. `runClientAuto` končí `[autopilot] PASS` včetně nových stupňů (§7).
- [ ] Kód je čitelný (§8). Žádné 400znakové jednořádkové metody.

---

## 0.1 Stav předání (co už je hotové v posledním commitu)
- **Pumptrack jako čistá funkce:** `trail/PumpShapes` (`Params`, `Oval`, `loop`, `line`). `SurfacePlans.pump` ji volá přes `WandSettings.pump()`. Okruh má nově klopenky jen v obloucích (`Oval.turnAmount`) a válce jen na rovinkách. Původní tvar zůstal pro srovnání v `PumpTrackRideTest.legacyLoop` (v obloucích 4,2 g, vyjíždí z trati).
- **`trail/PumpTrackRideTest`** (headless): okruh 64×44 m, pure-pursuit jezdec, obě strany, enduro i hardtail, srovnání klopenky s rovinou. `phaseSweepExperiment` jen tiskne. **Přepiš ho na assert `pumpingBeatsCoasting`**: fáze 120° ujede ≥ 2× dál než pasivní jízda bez šlapání (dnes 1,18 vs 0,33 kola za 40 s).
- **Sable test opraven:** deska teď leží na kamenné podlaze (dřív padala volným pádem a kolo na ní nemělo tření). `[sabletest] ALL PASSED`. Autopilot projde všechny fáze 0–8 + ragdoll + Sable.
- **`trail/DevPumpTrack` (`/mtbdevpump x z`) + `client/DevPumpRide`:** stavba velkého okruhu v hře funguje (1 969 bloků). **Jízda padá na bugu z §1 (`/tp` sesadí z kola)**, oprava §7.1.
- **Textury v1.1** (§6), generátor `tools/gen_textures_v2.py`, náhled `tools/preview/textures_v2.png`.

---

## 1. Inventura současného stavu (ověřeno v kódu)

| Registrovaný item (id) | Třída | Co dělá | Verdikt |
|---|---|---|---|
| `trail_wand` | `trail.TrailWandItem` | 26 režimů `WandMode` (štětce RAISE/LOWER/SMOOTH/FLATTEN, pumptrack, klopenky, dřevo, vybavení, klon, šablony, měření, undo), radiální menu G, náhled `TrailDraft` | **Ponechat jako Nástroj 1** (id zůstává). Odebrat štětce. Přidat režimy z klonu, překážek a ramp. |
| `berm_tool`, `route_tool`, `boardwalk_tool`, `roller_tool`, `undo_tool`, `measure_tool` | `trail.TrackBuilderItem` (6 variant) | Starší duplikáty režimů hůlky. Nejsou v creative tabu, ale jsou registrované. | **Smazat** (alias → `trail_wand`) |
| `clone_tool` | `trail.TrailCloneItem` | Klon výběru | **Smazat jako item**, logika zůstává jako režim Nástroje 1 |
| `obstacle_tool` | `trail.ObstacleToolItem` | Kořeny/kameny/kamenné pole | **Smazat jako item** (režimy už má hůlka) |
| `trail_tool` | `ramp.TrailToolItem` | Ladění ramp: sklon/start/profil/otočení/LINK | **Smazat jako item**, funkce přesunout do Nástroje 1 kategorie „Ruční“ (§2.2) a do ruční stavby (§3.6) |
| `ramp` | `ramp.RampBlock` + BE | Copycat rampa | **Ponechat** |
| `trail_surface` (blok bez itemu) | `trail.TrailSurfaceBlock` + `TrailSurfaceEntity` | Povrch se 4 výškami rohů (bilineární), copycat, `deck` | **Ponechat**. Stává se ručně stavitelnou „Tvarovací hlínou“ (§3) |
| `wood_support`, `trail_roots`, `trail_rock`, `trail_stake`, `airbag`, `cloth_barrier`, `trail_sign` | bloky | Vybavení | **Ponechat** (nové textury) |
| `bike_pump`, `mountain_bike`, `hardtail_bike` | itemy | Pumpička, kola | **Ponechat** (nové textury) |

Další zjištění, ze kterých vychází §4 a §5:
- **Rendering:** `client/ramp/RampClient` registruje **BlockEntityRenderer** pro `RAMP_BE` i `TRAIL_BE`. `RampRenderer.renderShaped` kreslí až **8×8 dílků + boky každý snímek, pro každý blok**. Pumptrack 64×44 = 1 969 bloků = statisíce quadů za snímek na CPU. **Hlavní zabiják FPS.**
- **Ghost náhled** (`client/trail/TrailClient.renderGhost`) kreslí každou buňku každý snímek okamžitým režimem. U každé buňky volá `SableCompanion.getContainingClient` a kreslí 12 hran boxu.
- **`world/SableTerrain.nearby()`** skládá klíč jako `String` `cx+":"+cy+":"+cz` při **každém** dotazu fyziky (240 Hz × kola × sondy). Alokace a hashování řetězců.
- **`TrailSurfaceEntity.shape()`** skládá 16 boxů přes `Shapes.or` v cyklu (O(n²) merge). Cache je jen per instance.
- **Drift:** `BikeParams.steerGripDemand = 1.12`, takže plný rejd vždy žádá 112 % gripu. Klávesnice dává jen 0/±1, tedy vždy plný rejd. `slideFriction = 0.82`: po prvním prokluzu spadne grip na 82 % a smyk se drží (hystereze). Viz §4.
- **Autopilot pumptrack stage** (`client/DevPumpRide`) v kroku 2 posílá `/tp @s`. Vanilla `/tp` **sesadí hráče z kola** → stage nikdy nedojede („timed out in phase 8“). Navíc `respawnAt` o ~100 bloků musí poslat payload s `TELEPORT` flagem, jinak ho server (`BikeStatePayload.handle`, limit 12 bloků/tick) zahodí. Viz §7.1.

---

## 2. Cílová sada itemů

### 2.1 Creative tab (v tomto pořadí)

| id | Název CZ / EN | Typ | Textura (hotová) |
|---|---|---|---|
| `mountain_bike` | Enduro kolo / Enduro Bike | item | `textures/item/mountain_bike.png` |
| `hardtail_bike` | Dirt hardtail / Dirt Jump Hardtail | item | `textures/item/hardtail_bike.png` |
| `bike_pump` | Pumpička / Shock & Tyre Pump | item | `textures/item/bike_pump.png` |
| `trail_wand` | **Stavitel tratí** / Trail Builder (Nástroj 1) | item | `textures/item/trail_wand.png` |
| `clearing_tool` | **Mačeta** / Trail Machete (Nástroj 2) – **nový** | item | `textures/item/clearing_tool.png` |
| `trail_dirt` | **Tvarovací hlína** / Shaping Dirt – **nový BlockItem** pro `trail_surface` | blok | `textures/item/trail_dirt.png` (item) |
| `trail_deck` | **Dřevěná deska** / Shaping Deck – **nový BlockItem** (`trail_surface` s `deck=true`) | blok | `textures/item/trail_deck.png` (item) |
| `ramp` | Rampa / Ramp | blok | `textures/item/ramp.png` (item sprite místo 3D modelu) |
| `wood_support` | Dřevěná podpěra | blok | `textures/block/wood_support.png` |
| `trail_roots` | Kořeny | blok | `textures/block/trail_roots.png` |
| `trail_rock` | Kámen na trať | blok | `textures/block/trail_rock.png` |
| `trail_stake` | Vytyčovací kolík | blok | `textures/block/trail_stake.png` |
| `cloth_barrier` | Páska | blok | `textures/block/cloth_barrier.png` |
| `airbag` | Airbag | blok | `textures/block/airbag.png` |
| `trail_sign` | Cedule | blok | `textures/block/trail_sign.png` |

### 2.2 Nástroj 1 – Stavitel tratí (`trail_wand`, `TrailWandItem`)

**Ovládání (nové, jednotné):**

| Akce | Vstup |
|---|---|
| Otevřít radiální menu | **Podržet G** (`ModKeyMappings.TRAIL_MENU`, přenastavitelné). Pohyb myší vybírá výseč, **puštění G potvrdí**. Klik = potvrdit, Esc = zrušit. `TrailRadialScreen` už `keyReleased` řeší, zachovat. |
| Vytyčit bod | Pravý klik na blok (na `trail_stake` se přichytí na střed kolíku, už funguje). Počet bodů podle režimu (tabulka). |
| Zrušit rozdělané body / náhled | Shift + pravý klik do vzduchu, nebo **Backspace** |
| Potvrdit náhled | **Enter** nebo pravý klik **do náhledu** (raycast proti ghost buňkám) |
| Posunout / otočit náhled | **šipky** (X/Z), **PgUp/PgDn** (Y), **R** otočit o 90°. Jen když je aktivní náhled. Posílá `TrailActionPayload.MOVE/ROTATE`, logika existuje v `TrailDraft.action`. |
| Nastavení režimu | Ve středu radiálu tlačítko ⚙ → `TrailSettingsScreen` (šířka, výška, rozestup, počet opakování) |

Pro nové klávesy (Enter, Backspace, šipky, PgUp/PgDn, R) přidej `KeyMapping` s `KeyConflictContext.IN_GAME` a **aktivní jen když držíš `trail_wand` a existuje náhled**. Stav náhledu na klientu = `TrailClient.ghost` není prázdný.

**HUD** (už existuje `TrailClient.hud`): vlevo dole ikona režimu (16×16 z §6) + název + 2–3 klíčové hodnoty + „Body: 1/3“ + nápověda kláves. Přepsat čitelně (bez magických konstant v jednom řádku).

**Radiální menu: 5 kategorií (vnitřní kruh) → režimy (vnější kruh).** Ikony `textures/gui/trail/<icon>.png` (hotové):

| Kategorie | Režim (`WandMode`) | Body | Co staví | Implementace (existuje) | Ikona |
|---|---|---|---|---|---|
| **Linie** | `FLOW` | 3 | plynulá trať (úsek) | `TrailBuilder.plan(Shape.FLOW)` | `flow` |
| | `PUMP_LINE` | 3 | rovná/křivá řada válců | `SurfacePlans.pump` → `PumpShapes.line` | `pump_line` |
| | `PUMP_LOOP` | 2 (rohy oblasti) | uzavřený okruh, klopenky v obloucích | `SurfacePlans.pump` → `PumpShapes.loop` | `pump_loop` |
| **Skoky** | `DIRT_JUMP` | 3 | hliněný kicker/table | `TrailBuilder` + výplň hlínou | `dirt_jump` |
| | `WOOD_KICKER` | 2 | dřevěný kicker | `TrailBuilder(Shape.KICKER)` | `wood_kicker` |
| | `WOOD_DROP` | 2 | dřevěný drop | `TrailBuilder(Shape.DROP)` | `wood_drop` |
| | `DROP_EDGE` | 2 | hrana dropu | `EquipmentPlans` | `drop_edge` |
| **Klopenky** | `BERM` | 3 | klopená zatáčka | `TrailBuilder(Shape.BERM)` | `berm` |
| | `ENDURO` | 3 | plošší enduro zatáčka | `TrailBuilder(Shape.ENDURO)` | `enduro` |
| | `SHARKFIN` | 3 | sharkfin | `TrailBuilder(Shape.SHARKFIN)` | `sharkfin` |
| **Dřevo a konstrukce** | `BOARDWALK` | 2 | lávka | `TrailBuilder(Shape.BOARDWALK)` | `boardwalk` |
| | `SUPPORT` | 1 | podpěry pod deskou | `EquipmentPlans` | `support` |
| | `CLONE` | 2+1 | kopírovat → vložit | `TrailCloneItem.handleClone` (statická logika zůstane) | `clone` |
| | `TEMPLATE` | 1 | vložit uloženou šablonu | `TrailLibrary` | `template` |
| **Vybavení a ruční** | `ROOTS` / `ROCKS` / `ROCK_GARDEN` | 1 | překážky | `EquipmentPlans` | `roots` / `rocks` / `rock_garden` |
| | `BARRIER` | 2 | páska | `EquipmentPlans` | `barrier` |
| | `AIRBAG` | 1 | airbag | `EquipmentPlans` | `airbag` |
| | `SIGN` | 1 | cedule | `EquipmentPlans` | `sign` |
| | **`RAMP_TUNE`** (nový) | 0 | ladění rampy pod kurzorem (viz níže) | logika z `ramp.TrailToolItem` | `ramp_tune` |
| | `MEASURE` | 2 | měření | existuje | `measure` |
| | `UNDO` | 0 | vrátit poslední úpravu | `TrailEdit.undo` | `undo` |

**Odebrat z `WandMode`:** `RAISE`, `LOWER`, `SMOOTH`, `FLATTEN` (štětce). Jejich logika `SurfacePlans.brush` zůstane, používá ji Nástroj 2 (§2.3). Pozor na **pořadí enumu**: `WandSettings` ukládá `mode.ordinal()` do NBT. Ukládej raději `mode.name()` a při čtení fallback přes ordinal pro staré itemy.

**`RAMP_TUNE`** (náhrada `TrailToolItem`): pravý klik na rampu = +2/16 sklonu (END), Shift = −2/16. **Kolečko myši se Shiftem** přepíná pod-akci SKLON → START → PROFIL → OTOČIT → SPOJIT (LINK). Pod-akce se ukazuje v HUD. Kód přesunout z `ramp/TrailToolItem.java` do `trail/RampTuning.java` (statické metody `tune(level, pos, state, subAction, sign, player)`). Scroll přes `InputEvent.MouseScrollingEvent` (client, cancel když držíš wand v RAMP_TUNE se Shiftem) → payload `TrailActionPayload` s novou akcí `TUNE_SUB`.

### 2.3 Nástroj 2 – Mačeta (`clearing_tool`, nová třída `trail.ClearingToolItem`)

Jednoduchý nástroj **bez menu, bez náhledu**. Při stavbě: `TrackBuilderItem.allowed` (creative-only dle configu). **Kácení funguje i v survivalu** jako normální nástroj.

| Akce | Výsledek |
|---|---|
| **Pravý klik na kmen** (`BlockTags.LOGS`) | **Pokácí celý strom:** BFS přes 26 sousedů od kliknutého kmene. Kmeny stejného druhu (`state.is(clickedBlockLogTag)`, tj. `minecraft:oak_logs` atd., ne obecně `LOGS`, aby se nesmazal sousední dům) + listí (`BlockTags.LEAVES`, jen do vzdálenosti 6 od nejbližšího kmene a jen nepersistentní: `LeavesBlock.PERSISTENT == false`). Limit 512 bloků. Nesmí odbočit do bloků pod úrovní kliknutí − 1 (kořeny pod zemí nech). **Survival:** dropy přes `Block.dropResources`, opotřebení 1 za strom. **Creative:** bez dropů. Částice + zvuk `SoundEvents.AXE_STRIP` + pád listí. Zápis přes `TrailEdit.apply`, takže jde vrátit. |
| **Pravý klik na zem** | **Uklidí a lehce uhladí** kruh **r = 2,5 m** (Shift: r = 4): smaže rostliny (`BlockTags.REPLACEABLE`, `FLOWERS`, `SAPLINGS`, `minecraft:short_grass/tall_grass/fern/large_fern/dead_bush/sweet_berry_bush`, sněhovou vrstvu). Pak `SurfacePlans.brush` s režimem SMOOTH, **síla 0,35, měkkost 0,8**, a **ořezem změny výšky na ±1 blok**. Nikdy nevytváří klopenky (žádný bank, jen průměrování). |
| **Levý klik** | Normální kopání (vanilla). Mačeta je zároveň rychlá na listí a dřevo jako sekera: `getDestroySpeed` 6 pro LOGS/LEAVES. |

Zvuk a feedback: action bar „Pokáceno: 37 bloků“, „Uklizeno a uhlazeno“. Textura `textures/item/clearing_tool.png` (hotová), model `item/handheld`.

### 2.4 Shrnutí: co zmizí z kódu
- Smazat: `trail/TrackBuilderItem.java` (pokud `allowed()` používají jiné třídy, přesuň ho do `trail/TrailPermissions.java`), `trail/ObstacleToolItem.java`, `ramp/TrailToolItem.java` (logika → `RampTuning`), registrace `berm_tool…measure_tool`, `clone_tool`, `obstacle_tool`, `trail_tool`. **`TrailCloneItem` jako item smazat**, ale statickou `handleClone` ponech (přejmenuj třídu na `trail/TrailClone.java`).
- Smazat staré textury a modely: `textures/item/{berm_tool,boardwalk_tool,clone_tool,measure_tool,obstacle_tool,roller_tool,route_tool,trail_tool,undo_tool}.png` a odpovídající `models/item/*.json`.
- Smazat lang klíče, které nic nepoužívá (grep).

### 2.5 Migrace starých světů
NeoForge 21.1: `DeferredRegister#addAlias(ResourceLocation from, ResourceLocation to)`.
- `descentmtb:berm_tool|route_tool|boardwalk_tool|roller_tool|undo_tool|measure_tool|clone_tool|obstacle_tool|trail_tool` → `descentmtb:trail_wand`.
- Ověřit načtením `run/saves/New World`, kde tyto itemy jsou v inventáři.

---

## 3. Ruční stavba: Tvarovací hlína a Dřevěná deska (hlavní nová funkce)

Cíl: **klopenku, pumptrack i kicker postavíš rukama**, přímo bloky, bez Nástroje 1 i 2. Princip „sochaření vrcholů“: každý blok `trail_surface` má 4 výšky rohů (už existuje `TrailSurfaceEntity.h[4]`, bilineárně). Hráč kliká na rohy a zvedá/snižuje je. Sdílené rohy sousedních bloků se mění spolu, takže povrch je vždy spojitý.

### 3.1 Datový model (beze změny formátu BE)
- `TrailSurfaceEntity.h = {NW, NE, SW, SE}` v **lokálních jednotkách bloku** (0..1 viditelné, rozsah −16..16 kvůli vrstvení). **Absolutní výška vrcholu** = `blockY + h[i]`.
- **Vrchol světa** `V(vx, vz)` (celočíselné souřadnice rohu) sdílí až 4 sloupce: `(vx−1,vz−1)` SE roh, `(vx,vz−1)` SW, `(vx−1,vz)` NE, `(vx,vz)` NW.
- Sloupec může mít **víc vrstev** (`TrailBuilder`/`SurfacePlans.surface` už vrstvy vytváří: `for y in bottom..top` s lokálními výškami `abs − y`). Vrcholový editor používá **stejnou** vrstvicí funkci.

### 3.2 Nová pomocná třída `trail/ColumnShaper` (refaktor z `SurfacePlans.surface`)
```java
/** Absolute corner heights of the top shaped surface in column (x,z) near yHint, or null if the column is plain. */
static double[] readColumn(Level l, int x, int z, int yHint);
/** Rebuild column (x,z) so its top surface has absolute corners abs[4]: writes/clears trail_surface layers,
 *  keeps material+deck of the existing top layer, fills dirt below like surface() does. Returns the changes. */
static Map<BlockPos, TrailEdit.Change> rebuildColumn(Level l, int x, int z, double[] abs, BlockState material, boolean deck);
```
`SurfacePlans.surface(...)` přepiš tak, aby pro každý sloupec volal `rebuildColumn`. **Jedna implementace vrstvení, žádná kopie.**

### 3.3 Interakce bloku `trail_dirt` (BlockItem, `trail/ShapingBlockItem.java`)
- **Položení na libovolný blok** (normální place): vznikne `trail_surface` s materiálem `COARSE_DIRT` (u `trail_deck`: `OAK_PLANKS`, `deck=true`). **Výšky rohů se přizpůsobí okolí:** pro každý roh vezmi absolutní výšku stejného vrcholu ze sousedních tvarovaných sloupců (průměr). Když žádný není, použij **0,5** (půl bloku, jako slab). Položený blok tedy plynule navazuje.
- **Pravý klik itemem na existující `trail_surface` nebo na vrch obyčejného plného bloku:**
  - Vyber **nejbližší vrchol** k bodu kliknutí (z `hitLocation`).
  - **Klik = +2/16 bloku**, **Shift + klik = −2/16**.
  - **Ctrl/sprint + klik** (`player.isSprinting()` nebo modifikátor přes klientský payload) = **hrana**: když klikneš blíž ke středu hrany, zvedne oba vrcholy hrany. Nejrychlejší cesta ke klopence.
  - Obyčejný plný blok (hlína/tráva/kámen…) se nejdřív převede na `trail_surface` s rohy 1,0 a materiálem = původní blok (copycat), pak se teprve zvedá.
  - Pro všechny 4 sloupce kolem vrcholu: `readColumn` → nastav nový absolutní roh → `rebuildColumn`. Sloupec bez tvarovaného povrchu, který je plný blok s vrchem na stejné výšce, se taky převede. Jinak se jeho roh neupravuje (vzduch, voda, zdi).
  - Vše se zapíše jako jedna změna přes `TrailEdit.apply`. Rychlé kliky během 1 s slučuj do jednoho undo kroku (`TrailEdit` dostane volitelný `mergeKey`).
  - Limity: změna vrcholu max ±4 bloky od původní výšky sloupce. Výška vrcholu nad 16 vrstev zakázána.
- **Survival:** položení stojí 1 item. Sochaření stojí 1 item za každých +4/16 zvednutí (počítadlo v BE `pending`). Snížení itemy nevrací (jednoduché).
- **Klient:** když držíš `trail_dirt`/`trail_deck` a míříš na povrch, vykresli **zvýrazněný vrchol** (malý kosočtverec 0,15 m, nebo dva u hrany) a v action baru „Výška: 2 6/16 bl.“. `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`, jeden quad.

### 3.4 Jak se ručně postaví každý produkt Nástroje 1 (musí to jít, ověř testem §7.3)

| Produkt Nástroje 1 | Ruční postup |
|---|---|
| Klopenka / enduro zatáčka / sharkfin | Polož řadu `trail_dirt` do oblouku, pak Ctrl+klik na vnější hrany 4–8× (zvedne vnější okraj), vnitřek nech. Sharkfin = vyšší špička uprostřed. |
| Válce pumptracku / okruh | Řada `trail_dirt`, každý 3.–4. vrchol uprostřed řady zvedni 3–5× (vlna), sousední 1–2× (náběh). |
| Hliněný kicker / table | `ramp` (profil CONCAVE) + `trail_dirt` pro doskok, nebo jen sochařením. |
| Dřevěný kicker / drop / lávka | `trail_deck` (dřevěné desky, stejné sochaření) + `wood_support` pod nimi. |
| Hrana dropu | `ramp` CONVEX nebo `trail_dirt` se zvednutou hranou. |
| Kořeny, kameny, páska, airbag, cedule, kolík | Bloky `trail_roots`, `trail_rock`, `cloth_barrier`, `airbag`, `trail_sign`, `trail_stake`, ruční pokládání už funguje. |
| Klon / šablona | Jen nástrojem (není to blok, to je v pořádku). |

### 3.5 Copycat u ručních bloků
Pravý klik **jiným plným blokem** na `trail_surface` = změna materiálu (logika z `RampBlock.useItemOn`). Výjimka: drží-li hráč `trail_dirt`/`trail_deck`, je to sochaření, ne copycat.

### 3.6 Ladění ramp bez nástroje
Pravý klik **`trail_dirt` na rampu** = stejné sochaření: rampa se převede na `trail_surface` se stejnými rohy (`RampBlock.heightAt` v rozích) a pokračuje se. Rampy tím přestávají potřebovat vlastní nástroj. `RAMP_TUNE` v Nástroji 1 je jen pohodlnější zkratka.

---

## 4. Drift a zatáčení

### 4.1 Příčiny (ověřeno v `physics/BikeParams` + `BikeSim.substep/solveWheel`)
1. `steerGripDemand = 1.12`: `maxSteer = atan(2·halfWheelbase·demand/v²)`, takže plný rejd vždy žádá 112 % gripu. Přední guma se nasytí (`latSaturated`) a sklouzne. V headless testu `carvesAndLeansIntoTurns` je `sliding F = true` už při 36 km/h.
2. Klávesnice v `BikeInputHandler.poll()` dává `steer ∈ {−1,0,1}`, tedy pokaždé plný rejd.
3. `slideFriction = 0.82` + stav `w.sliding` z minulého substepu = **hystereze**. Po prvním nasycení drží 82 % gripu, takže smyk pokračuje i při menší poptávce.
4. Na `TRAIL` povrchu `tyreGrip` z pumpičky (`BikeTuning`) při výchozích 26/28 psi grip mírně zvyšuje. To není příčina, jen ověř.

### 4.2 Změny
- `BikeParams.steerGripDemand`: **1.12 → 0.88** (plný rejd = 88 % gripu, smyk jen na volném povrchu nebo s brzdou).
- **Drift jen záměrně:** když `brake > 0.5` a zároveň `|steer| > 0.8` → `steerGripDemand · 1.25` (kontrolovaný smyk zadní brzdou, jako v Descenders).
- `slideFriction`: **0.82 → 0.93**. Návrat z prokluzu: `w.sliding` se nastaví až po **3 po sobě jdoucích** nasycených substepech a zruší, když poptávka klesne pod `μ_k·N·0,9`.
- **Náběh rejdu na klávesnici:** v `BikeInputHandler` filtr pro digitální vstup `steerKb += clamp(target − steerKb, ±dt/0,22)`. Při rychlosti nad 8 m/s výsledek ×0,75. Ovladač (analog) beze změny.
- **Config** (`ClientConfig`): `steeringGrip` (0,6–1,2, výchozí 0,88 = `steerGripDemand`) a `keyboardSteerRamp` (0–0,5 s, výchozí 0,22). Lang CZ/EN.
- Hardtail (`BikeType.HARDTAIL.params()`): nic nepřepisuje, sdílí.

### 4.3 Na co si dát pozor
- `poppedKickerAllowsA360`, `carvesAndLeansIntoTurns` (lateral g 0,6–1,3 g, lean 28–55°), `bermsHoldSpeedBetterThanAFlatCorner` se nesmí rozbít. Upravit nejvýš meze, ne smysl testu.
- Bez změny `lateralStiffness`.

### 4.4 Nové testy (`src/test/java/com/descentmtb/physics/SteeringTest.java`)
1. **`keyboardFullLockDoesNotDriftOnDirt`**: hlína, 6 / 9 / 12 m/s, skok rejdu 0→1 s rampou jako klávesnice, 3 s. Asserty: `front.sliding` i `rear.sliding` false ve ≥ 95 % ticků. Úhel skluzu zadní gumy `atan2(v·tL, v·tF) < 6°`. Bike nebailuje.
2. **`gripLimitedCarveStaysUnderMu`**: ustálená zatáčka, `latG ≤ μ·1,02`.
3. **`brakeTurnDriftsOnPurpose`**: brzda 1 + plný rejd na trávě při 9 m/s → `rear.sliding` true ≥ 30 % času, bez bailu.
4. **`gravelSlidesMoreThanDirt`**: stejný manévr, štěrk má víc smyku než hlína.
5. Rozšířit `trail/PumpTrackRideTest`: assert, že na klopenkách `maxLatG < 1,6` a `offLine < 1,2 m` (dnes 2,0 g, 0,75 m).

---

## 5. Optimalizace

**Nejdřív měř, pak měň.** Benchmark viz §5.7. Každý bod commitni zvlášť s číslem před/po v commit message.

### 5.1 Ramp + Trail surface do chunk meshe (největší zisk)
Nahraď BER `RampRenderer` **dynamickým baked modelem**, vykresleným jednou při kompilaci chunku:
- `RampBlock.getRenderShape` → `RenderShape.MODEL`. Blockstate JSON pro `ramp` i `trail_surface` ukazuje na model s `"loader": "descentmtb:shaped"`.
- `ModelEvent.RegisterGeometryLoaders` → `IGeometryLoader<ShapedGeometry>`. `ShapedGeometry implements IUnbakedGeometry` → `bake()` vrací `ShapedBakedModel implements IDynamicBakedModel`.
- `RampBlockEntity` / `TrailSurfaceEntity` override `getModelData()` → `ModelData.builder().with(SHAPE, ShapeKey).build()`. `ShapeKey` = record (h0..h3 zaokrouhlené na 1/64, deck, profil/facing/start/end u ramp, `BlockState material`).
- Při změně tvaru/materiálu: `requestModelDataUpdate()` + `level.sendBlockUpdated(pos, s, s, 8)` (flag 8 = rerender main thread), aby se chunk přestavěl. Na klientu v `onDataPacket`/`handleUpdateTag` také `requestModelDataUpdate()`.
- `getQuads(state, side, rand, data, renderType)`: geometrie stejná jako dnes v `RampRenderer` (8 pásů nahoře pro profily, boky jako lichoběžníky, spodek). Sprite a **tint index** přeber z baked modelu materiálu: `Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getQuads(material, dir, rand, ModelData.EMPTY, null)`, první quad: `getSprite()`, `getTintIndex()`. Tint index předávej v `BakedQuad`, aby fungovala barva trávy (`BlockColors` řeší chunk renderer sám, ale **`trail_surface`/`ramp` musí v `RegisterColorHandlersEvent.Block` delegovat na barvu materiálu** z `ModelData`/BE).
- **Cache quadů:** `ConcurrentHashMap<ShapeKey, List<BakedQuad>[]>` s limitem (např. Caffeine-like LRU přes `LinkedHashMap` 4 096 položek, synchronizovaně). Chunk building běží ve více vláknech.
- `getRenderTypes(state, rand, data)` → render type materiálu (`ItemBlockRenderTypes.getRenderLayers(material)`), fallback `solid`.
- AO: `useAmbientOcclusion()` true, normály quadů nastav správně (`QuadBakingVertexConsumer` z NeoForge).
- Item model rampy/hlíny: sprite itemy (§6), takže 3D item model není potřeba.
- **Smazat** `RampRenderer` a registraci BER v `RampClient` (ponech třídu jen pro color handler + geometry loader).
- Kolize (`TrailSurfaceEntity.shape()`): globální `ConcurrentHashMap<ShapeKey, VoxelShape>`. Skládání přes `Shapes.or` nahraď **jedním `Shapes.join` z předpočtených 4×4 boxů** (sestav `List<VoxelShape>`, pak `Shapes.or(first, rest...)` jednou) + `optimize()`.

### 5.2 Ghost náhled do VBO
`TrailClient.renderGhost`: geometrii náhledu postav **jednou** při příchodu `TrailPreviewPayload` do `VertexBuffer` (`BufferBuilder` → `VertexBuffer.upload`). Kresli `vertexBuffer.drawWithShader(pose, proj, GameRenderer.getPositionColorShader())`. Sable `getContainingClient` volej jen jednou za buňku při stavbě (statické náhledy mimo Sable), u Sable sublevelů jen transform celého bufferu. Hrany boxů: jen obrys celého návrhu, ne každé buňky.

### 5.3 Fyzika
- `SableTerrain.nearby`: klíč `long` (`((long)cx & 0x1FFFFF) << 42 | ((long)cy & 0xFFFFF) << 21 | (cz & 0x1FFFFF)`) v `Long2BooleanOpenHashMap`. A **úplně přeskoč**, když `!ModList.get().isLoaded("sable")` (zjistit jednou ve statickém poli).
- `McColumns.exactSurface`: drží `getBlockEntity` per dotaz. Přidej per-tick cache `Long2ObjectOpenHashMap<double[]>` (klíč `BlockPos.asLong`) s rohy `TrailSurfaceEntity`, čistí se v `newTick()`.
- `BikeSim`: změř JFR (`-XX:StartFlightRecording`), jestli `V3` alokace dělají > 5 % CPU klienta. Jen pokud ano: mutable scratch vektory v `contact()`/`solveWheel()`. **Neměň chování** (feel testy musí dát identická čísla ±1e-9).

### 5.4 Síť
`BikeStatePayload` jde každý tick i když kolo stojí. Když `speed < 0.05` a nic se nemění, posílej max 2×/s. Server-side riderless sim už spí (`restTicks > 60`), ok.

### 5.5 Render kola a jezdce
`MountainBikeRenderer` volá `bike.params()` každý snímek. Ověř, že nealokuje (cache v entitě). `RiderPose.apply` alokuje `Stance` record, to je v pořádku.

### 5.6 Co NEoptimalizovat
`RampMath`, `PumpMath`, `TrailMath` (čisté funkce, levné). Nesahat na substep 240 Hz.

### 5.7 Benchmark (autopilot stage, §7.2)
Postav pumptrack 64×44 (`/mtbdevpump`) a vedle stejný obdélník z vanilla `coarse_dirt`. Postav hráče tak, aby viděl celý pumptrack, 10 s měř `mc.getFps()` (průměr, 1% low z `mc.getFrameTimeNs()` historie). Pak totéž u vanilla obdélníku. Loguj `[bench] shaped=xx fps, vanilla=yy fps, ratio=zz`. **Stage selže, když ratio < 0,9.**

---

## 6. Textury (hotové, nekreslit znovu)

Generuje je `tools/gen_textures_v2.py` (Opus). Styl: mechanický pixel-art inspirovaný Create (mosaz, andezit, dub, tmavý obrys, 16×16, světlo zleva nahoře). **Nepřegenerovávat.** Když chybí, řekni.

| Soubor | Použití | Model JSON (vytvoř/ponech) |
|---|---|---|
| `textures/item/trail_wand.png` | Stavitel tratí | `item/handheld` |
| `textures/item/clearing_tool.png` | Mačeta | `item/handheld` |
| `textures/item/trail_dirt.png` | Tvarovací hlína (item) | `item/generated` (block item s vlastním item modelem) |
| `textures/item/trail_deck.png` | Dřevěná deska (item) | `item/generated` |
| `textures/item/ramp.png` | Rampa (item) | `item/generated` (nahradit dnešní 3D `models/item/ramp.json`) |
| `textures/item/bike_pump.png` | Pumpička | `item/generated` |
| `textures/item/mountain_bike.png`, `hardtail_bike.png` | kola | `item/generated` |
| `textures/block/trail_rock.png`, `trail_roots.png`, `trail_stake.png`, `wood_support.png`, `airbag.png`, `cloth_barrier.png`, `trail_sign.png` | bloky | uprav blokové modely, aby je používaly (dnes často vanilla textury). `trail_stake` + `cloth_barrier` = `cross`/plochý model s cutout. |
| `textures/gui/trail/<icon>.png` (16×16) | ikony radiálu + HUD | ikony z tabulky §2.2 + `category_lines`, `category_jumps`, `category_turns`, `category_wood`, `category_gear`, `settings` |
| `textures/gui/trail/radial_ring.png` | podklad výseče (rám z mosazi) | volitelné, `TrailRadialScreen` |

Staré ikony `raise`, `lower`, `smooth`, `flatten` smaž. Mačeta je nemá.

---

## 7. Autopilot a testy

### 7.1 Opravit pumptrack stage (`client/DevPumpRide`)
- Odstranit `p.connection.sendCommand("tp …")`. **Vanilla /tp sesadí z kola.**
- Přidat `BikeClientController.forceTeleportNextPayload()` (statický flag spotřebovaný v `tick()` při stavbě `statePayload(teleport)`). `DevPumpRide` ho zavolá hned po `bike.respawnAt(...)`.
- `respawnAt` musí také posunout hráče: server přesune pasažéra sám přes `positionRider`, ověř.
- Timeout: `DevAutopilot` vrací v bloku `remountSent` dřív, než dojde globální `tick > 1800`. Pumptrack stage má vlastní limit `TIMEOUT = 4200`. Ponechat.
- Pass: ≥ 2 kola, bez bailu, `maxOff < half + 1,5`, a nově **`maxLatG < 1,8`** (nový drift fix).

### 7.2 Nové autopilot stage (pořadí po Sable testu)
1. `pump_ride` (opravený, §7.1) + `bench` (§5.7).
2. **`manual_berm`**: server command `/mtbdevmanual <x> <z>` postaví ručně klopenku **simulací kliků** `ShapingBlockItem.useOn` (stejná cesta jako hráč, viz `DevTrailTests.click`): 6 bloků do oblouku + 5× Ctrl-zvednutí vnějších hran. Autopilot ji projede (pure pursuit jako `DevPumpRide`), assert bez bailu a `lean > 25°`.
3. **`clearing`**: zasaď dub (`/place feature minecraft:oak` na připravené ploše), klikni `ClearingToolItem` na kmen → v okolí 5 bloků nesmí zbýt `LOGS` ani nepersistentní `LEAVES`. Klik na zem s trávou → žádná `short_grass` v r = 2.
4. Screenshoty: `pump_corner`, `manual_berm_side`, `clearing_before/after`, `radial_menu` (otevři `TrailRadialScreen` programově a vyfoť).

### 7.3 Headless testy (nové)
- `trail/ColumnShaperTest`: zvednutí vrcholu změní přesně 4 rohy 4 sloupců. Přechod přes celou výšku bloku vytvoří 2. vrstvu bez skoku (spojitost ±1e-9). Snížení pod 0 smaže horní vrstvu.
  *(Vrstvení musí jít testovat bez `Level`, proto `ColumnShaper` rozděl na čistou část `layers(double[] abs) → List<Layer(y, double[] local)>` a světovou část.)*
- `trail/ManualBermRideTest`: z čisté funkce „klikací sekvence → výškové pole“ postav klopenku a projeď ji `PumpTrackRideTest.GridTerrain` + pure pursuit. Stejné asserty jako `bermsHold…`.
- `physics/SteeringTest` (§4.4).

### 7.4 Regrese
Všech **56** stávajících testů zelených (počet ověř `python tools/test_summary.py | grep -c ^ok`).

---

## 8. Pravidla kódu (povinná)
- **Žádné husté jednořádkové metody.** Max ~120 znaků na řádek, jedna věc na řádek, smysluplné názvy. Soubory, které upravuješ a jsou dnes nečitelné (`trail/*.java` od minulého modelu, `Ragdolls`, `DevSableTests`), při úpravě přeformátuj.
- Komentáře jen tam, kde je důvod neočividný (styl jako `physics/BikeSim.java`).
- Lang: **vždy obojí** `en_us.json` i `cs_cz.json`. Při editaci soubory nejdřív načti a jen přidávej/mažeš konkrétní klíče.
- Fyzikální jádro `physics/*` dál **bez importů z `net.minecraft`**.
- Žádné nové závislosti.
- Commit po každém milníku §9, zpráva anglicky, končí řádkem `Co-Authored-By` podle instrukcí prostředí.

## 9. Pořadí prací (milníky)
1. **M1 – Úklid nástrojů:** aliasy, smazání itemů, `WandMode` bez štětců + ukládání jménem, `RAMP_TUNE`, `TrailClone`/`TrailPermissions`. Build zelený.
2. **M2 – Mačeta** (`ClearingToolItem`) + modely/textury + lang.
3. **M3 – `ColumnShaper` refaktor** (čistá vrstvicí funkce + test). `SurfacePlans.surface` přes něj. Všechny stávající testy zelené.
4. **M4 – Ruční stavba** `trail_dirt`/`trail_deck` (`ShapingBlockItem`, vrcholový editor, klientské zvýraznění, survival cena, undo merge).
5. **M5 – Ovládání Nástroje 1:** držené G, klávesy náhledu, HUD, ikony kategorií.
6. **M6 – Drift fix** + `SteeringTest` + config.
7. **M7 – Optimalizace** 5.1 → 5.2 → 5.3 → 5.4, každá s benchmarkem.
8. **M8 – Autopilot stage** §7 + závěrečný běh, screenshoty, souhrn čísel do `docs/v1.1-report.md`.

## 10. Rizika
- **Dynamický model + copycat tint:** tráva musí mít barvu biomu. Otestuj materiál `grass_block` na rampě (screenshot).
- **Chunk rebuild při sochaření:** každý klik přestaví až 4 sloupce = 1 chunk section. Spamování je ok, ale neposílej `sendBlockUpdated` víckrát na stejný blok v jednom ticku.
- **`WandMode` ordinal:** staré itemy mají v NBT ordinal, viz fallback v §2.2.
- **Sable:** ghost/sochaření na Sable sublevelech nepodporovat (vrátit hlášku „Na pohyblivé konstrukci nelze tvarovat“), jen jízda.
