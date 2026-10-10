# Milník 27 — lyže

## 1.0.0-alpha-m27 (milník 27)

Soubor: `descentmtb-1.0.0-alpha-m27.jar`
SHA-256: `460c7c5bd5c126a3c47e2c1147172a651f67f4eb33980151320cb9b49eb16c58`
Kopie: `dist/descentmtb-milestone-27-skis.jar`, `dist/descentmtb-latest.jar`.

## Změny

- Nové typy vozidla `SKI_RACE` a `SKI_FREESTYLE` na stejné entitě jako kola (`MountainBikeEntity` + `BikeSim`): kamera, ragdoll při pádu, návrat, časomíra, GPS i mapa fungují beze změny. Typ i pár se ukládají podle pořadí (`SkiBrand` se jen přidává na konec).
- Šest skutečných párů jako samostatné předměty `ski_<id>` v záložce **Descent MTB** za pit bikem. Závodní: Atomic Redster G9 FIS (188 cm, R 23 m), Rossignol Hero Elite ST Ti (167 cm, R 13 m), Fischer RC4 Worldcup RC (175 cm, R 17 m). Freestylové twin tipy: Armada ARV 96, Line Chronic 101, Faction Prodigy 2. Popisek ukazuje druh lyží, délku, poloměr krojení a šířku pasu.
- Fyzika (`SkiPhysics`, `SkiSurface`): klouzání po sněhu a ledu, krojení podle poloměru boční křivky, smýkaný oblouk v nízké rychlosti, pluh / hockey stop na brzdě, odpich hůlkami, sjezdový posed. Na holé zemi (kámen, hlína, tráva, štěrk…) skluznice drhnou, prudce brzdí a plní se měřič; plný měřič znamená pád („skis caught on rock“). Dřevo (box / rail) klouže bez drhnutí. Freestylové lyže jezdí i switch.
- Triky: závodní lyže Spread Eagle, Daffy, Iron Cross, Back Scratcher, Tip Grab; freestylové Mute, Japan, Safety, Tail grab, Truck Driver.
- Efekty: na sněhu víří sníh (částice sněhového bloku + vločky) při ostrém oblouku, brzdění pluhem / smykem a při dopadu, na ledu méně. Při drhnutí letí prach bloku pod lyžemi, občas jiskra a kouř. Kola mají efekty beze změny.
- Zvuky: lyže nehrají volnoběžku, odvalování pneumatik ani kovové dorazy tlumičů. Na sněhu syčení skluznic podle rychlosti (čtyři vzorky `bike.roll.snow.*`, už dřív obsažené v `sounds.json`), na ledu ostřejší a vyšší, na dřevě klouzání po dřevě. Pluh / hockey stop `bike.slide.snow`. Drhnutí po kamení: hluboké skřípění (`bike.slide.hard` s nízkou výškou) a občasné ťuknutí kamene. Dopad do sněhu vanilla prašan + sníh, na tvrdém kamenný úder. Pád na lyžích bez zvuku rámu kola.
- HUD: při drhnutí malý ukazatel „Kameny!“ pod rychloměrem (s `hud.showSpeed`); od 80 % bliká.
- Pumpa na lyžích jen oznámí, že nemají pneumatiky; servisní stojan lyže nepřijme; lyže se nešpiní blátem.
- Překlady en_us / cs_cz pro lyže, hlášky a dříve chybějící titulky zvuků (pád, dopad, smyk, zvonek…).
- Sníh se rozpozná i pod jednou vrstvou sněhu (tráva / hlína / kámen pod `snow` nebo se stavem `snowy`); rampy ze sněhu nebo ledu jsou pro lyže sníh / led. Platí to i pro kola (na zasněžené trávě mají přilnavost sněhu).
- Model lyží (`SkiModel`, generuje `tools/gen_ski_assets.py`): skutečné délky a boční křivka, závodní deska a hranatá patka, freestyle twin tip, vázání s ski-stopy, lyžáky, grafika značek; hůlky v rukou (závodní GS hůlky prohnuté). Ikony 32×32. Náhledy v `tools/preview/ski/`.
- Póza lyžaře (`SkierPose`, `SkiPoleLayer`): postoj na šířku boků, pokrčená kolena, sjezdový posed s hůlkami pod pažemi, zapíchnutí hůlky v obloucích, pluh a hockey stop, pózy všech deseti triků s úchopem lyže.
- Síťový protokol 12 (nová synchronizovaná značka lyží): server a klient musí mít stejnou verzi.

Nové soubory: `ski/SkiBrand.java`, `ski/SkiItem.java`, `ski/SkiPhysics.java`, `ski/SkiSurface.java`, `client/ski/SkiStance.java` a assety lyží.

Ostatní hráči: jejich lyže se počítají z poloh synchronizovaných serverem. Drhnutí, víření sněhu a zvuky se u nich odhadují z bloku pod lyžemi a rychlosti (měřič drhnutí se nesynchronizuje).

## Ověření a omezení

- Úspěšný `assemble` a celá sada `test` (včetně nových `SkiPhysicsTest` a `SkierPoseTest`; mimo jiné plynulý sjezd po schodech z bloků i diagonálně bez odlepení, drhnutí a pád na kameni, krátký kámen v rychlosti bez pádu, led vs. sníh, pluh, odpich, tuck).
- Vzhled ověřen jen na náhledových renderech generátoru. Hra nebyla spuštěna: vzhled ve hře, dosednutí nohou do bot, zvuky, efekty a multiplayer nejsou ověřené.
- Známá omezení: boty jsou kvůli velikosti nohy hráče hranaté a lehce se v prostředku překrývají; kamera helmy v ostrém oblouku nesedí přesně na vykreslené hlavě (stejně jako u kol); důvod pádu je v angličtině jako u kol.
