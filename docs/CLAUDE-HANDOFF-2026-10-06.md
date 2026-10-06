# Předání pro Claude — Customization Experience Update

Stav při ukončení Codex, 2026-10-06. Repo `C:/MTBMod`, větev `master`, Java 21, NeoForge 1.21.1. Limit Codex při poslední kontrole: 97 % spotřebováno z pětihodinového okna, bez placených kreditů. Úkol není celý hotový; níže rozlišuji implementaci od neověřeného výsledku a nedokončených požadavků.

## Aktuální pravidla od uživatele

- Žádné herní ani vizuální testy, autopilot ani generované testovací screenshoty. Poslední zákaz přebíjí původní přiložený prompt požadující `runClientAuto`. Číst uživatelovy obrázky a oficiální reference je žádoucí. Ověřovat jen kompilaci/sestavení; nepovažovat ji za potvrzení opravy ve hře.
- Pokračovat autonomně, stručně informovat česky, které části se mění. AgentBridge je povolen pro informování; uživatel nechce práci s tabulkou ani čekání na odpovědi ostatních.
- Postupovat po funkčních milnících: sestavit, vlastní explicitní `git add`, commit, nový číslovaný JAR v `dist`. Staré milníky nepřepisovat. Lze aktualizovat `descentmtb-latest.jar`. Předat uživateli odkaz a co může sám zkusit; nečekat na jeho zkoušku při nezávislé práci.
- Nezavádět opotřebení či servisní povinnosti. Nevydávat přejmenování nebo otočení tlumiče za skutečný model značkového kola.
- Vlastnictví fyziky, `client/BikeInputHandler.java`, `client/BikeCamera.java`, `client/CameraMath.java` a `trail/**` patří původně druhému agentovi; nezasahovat bez koordinace. Níže uvedené trail bonusy jsou schválené uživatelem, ale nejsou touto etapou implementované.

## Commitnuté milníky a artefakty

| Commit | JAR v `C:/MTBMod/dist/` | Implementováno |
|---|---|---|
| `74b680f` | základ před milníky | Materiály, modely komponent, náhled, stojan, doplňky, nálepky; doplnění původního customization stubu. |
| `c7ffc45` | `descentmtb-milestone-01-fixes.jar` | `HasBike` v update tagu stojanu i při prázdném stavu (oprava ghost kola), server ACK u uložení dílny, sedlovka v čelistech, skutečný dynamický item renderer, potlačení světelného kuželu v náhledech. |
| `779c5a0` | `descentmtb-milestone-02-designs.jar` | Název kola na itemu/entity/stojanu/GUI; pojmenovaná lokální knihovna sestav podle účtu, nezávislá na světě, bezpečný zápis JSON a informace o chybách. |
| `9009b45` | `descentmtb-milestone-03-stickers.jar` | Textové nálepky, posun podél/napříč trubkou, duplikace, kopie na protější stranu, zrcadlení. Původní přímé vykreslení textu mělo následně uživatelem prokázanou chybu; nahrazeno v 05. |
| `8951d7f` | `descentmtb-milestone-04-frames.jar` | Šest odlišných předních profilů značkových rámů, úpravy vizuálního tlumiče; oprava vysokého DJ_STRAIGHT spoje a plovoucího DJ seat_bridge; odstraněn světelný objem i ve světě. Značkové zadní stavby NEJSOU věrně dokončené — viz prioritní chyby. |
| `ac11e97` | `descentmtb-milestone-05-sticker-assets.jar` | Automatická PNG paleta z aktivních resource packů, uložené resource ID, fallback chybějícího PNG, volitelný wrap přes plochy trubky i sousední segmenty; text rasterizovaný na stejný decal jako obrázek; textový editor pouze pro textové nálepky. |

Každý číslovaný milník prošel `assemble`. Milník 05 finálně `BUILD SUCCESSFUL`, `build/milestone-05.log`. Žádná nová herní ověření v této navazující etapě. `dist/descentmtb-latest.jar` nyní odpovídá 05. Obecné pojmenování commitu 04 neznamená potvrzení věrnosti značkových rámů.

Knihovna sestav: `config/descentmtb/players/<Minecraft user profile UUID>/bike_designs.json` v klientské instanci, maximálně 100 sestav. Jde o přesun mezi světy stejné instalace/účtu, nikoli cloudovou synchronizaci; mezi instalacemi zkopírovat soubor.

## Priorita 1: skutečné značkové modely, ne pouhé variace

Uživatel odmítl nynější značkové modely. Výběr značky má změnit celý relevantní model rámu podle konkrétní skutečné generace: hlavní čepy, zadní stavbu, vahadla a její napojení, oba úchyty a orientaci tlumiče, řetězovou linku a případnou kladku POUZE pokud skutečný model kladku má. Stylizace do Minecraftu je povolená; rozpojené či levitující díly a falešná konstrukce nejsou.

Potvrzení na pěti uživatelských obrázcích (Codex všechny přečetl; toto není nový herní test): společný vysoký hlavní čep a téměř stejná zadní stavba, oddělené hranaté úchyty tlumiče, u Nomadu dole výrazně nejasné spojení. Posun/rotace tlumiče není změna kinematiky.

Adresář obrázků `C:/Users/jakub/OneDrive/Obrázky/Snímky obrazovky/`:

- `Snímek obrazovky 2026-10-06 175206.png`: Specialized Stumpjumper 15.
- `...175154.png`: YT Capra.
- `...175149.png`: Commencal META SX V5.
- `...175140.png`: Canyon Torque AL.
- `...175128.png`: Santa Cruz Nomad.
- CUBE Stereo ONE77 patří mezi šest požadovaných rámů; stejnou skutečnou geometrii je nutné dopracovat také pro něj.

Před úpravou použít oficiální BOČNÍ fotky a technické podklady správné generace; obecný marketing o VPP/VCS nestačí na souřadnice. Referenční odkazy jsou v `docs/frame-catalog.md`, ale to není hotová CAD analýza. Správná priorita je jeden přesně propojený model před šesti odhady. Dosavadní výběr v GUI nemaskuje tento nedostatek a žádná nová oprava značkových pivotů v 05 není.

Kód: `tools/gen_frame_catalog.py` generuje literal kuboidy ve `client/model/EnduroBikeModel.java` mezi BEGIN/END FRAME CATALOG. Současný společný bone zadní stavby a jeho původní pivot jsou právě problém. `EnduroBikeModel.renderCustomized` dočasně mění vizuální transformace tlumiče a vrací je ve finally. `PartTable` přiřazuje modelové názvy/materiály/kódy tvarů. `BikeParts.FrameShape` má původní ordinaly 0–5 a značky 6–11; zachovat kompatibilitu. `tools/gen_custom_assets.py` generuje `StickerAnchors` z skutečných trubek; po změně geometrie nutno regenerovat kotvy. Kontaktní body jezdce/kol a fyzika zůstaly sdílené, to není věrná kinematika značek.

## Priorita 2: poslední opravy potřebují uživatelovo ověření

- **Obří šedá plocha v náhledu**: screenshot `...171708.png`; odstranění světelného kuželu v GUI/stand/item v 01, celého světelného objemu v 04. Reálné light bloky a emissive čočky zůstávají. Není potvrzeno novým screenshotem po 04.
- **Stojan kreslí kolo, server říká prázdný**: `...171914.png`; 01 ukládá explicitně i `HasBike=false`, protože prázdný CompoundTag NeoForge neaplikoval. Uložení dílny čeká na serverový `WorkshopResultPayload`. Poslední nový výsledek ve hře není potvrzen.
- **Upnutí kola do stojanu**: transformace v `StandRenderer` explicitně vychází z centra sedlovky, samostatné DJ/enduro offsety. Ověření uživatelem případně potřeba.
- **DJ_STRAIGHT obří hranatá horní trubka / všechny DJ plovoucí červený spoj**: `...174256.png`, `...174300.png`, `...174304.png`; v 04 odstraněn `top_join__s`, nová souvislá šikmá `top_straight__s`, `seat_bridge` posunut mezi vzpěry pod sedlo. Zdroj opraven/sestaven, vzhled po změně není potvrzen.
- **Denní světlo dělá tmavou hmotu nad vodou**: `C:/Users/jakub/AppData/Local/Temp/codex-clipboard-adca8753-b7be-4254-97fc-c8aa384a4a5c.png` (Codex jej přímo nepřečetl); v 04 odstraněn kužel ve všech kontextech. Reálné osvětlení zachováno; vizuální výsledek neověřen.
- **Text nejdřív neviditelný, potom obří bílé písmo nad kolem při Both Sides**: `C:/Users/jakub/AppData/Local/Temp/codex-clipboard-eeea2a42-6b20-49cb-a3dc-5baf4ff3155d.png` přečtený, prokazuje chybu. Přesná příčina původního font pipeline nebyla určena. 05 odstranil `font.drawInBatch` pro textové nálepky, používá `TextStickerTextures` + identický surface quad jako PNG. Data rozlišují `is_text` i po vymazání textu. Kompiluje; nezaručovat úspěch ve hře bez uživatelovy zpětné vazby.
- **Obrázkové samolepky nesmí nabízet textový editor**: upraveno v 05 podmínkou `Sticker.isText()`. Stále mají transformace, barvu, wrap. Textové pole je jen u skutečného textového typu.

## Milník 05 — detaily a omezení

`StickerAssets` registruje reload listener, hledá oba přesné prefixy `textures/sticker` a `textures/stickers`, všechna PNG do 1024×1024 z modových i klientských packů. Paleta má skutečné obrázky a resource ID, aktualizuje se podle revision při reloadu. `BikeBuild.Sticker` ukládá `texture`, `wrap`, `is_text` jako optional codec fields; všechny změny/duplikace zachovávají nové údaje. ID omezeno na povolené cesty PNG, chybějící soubor se nemění na jiný design.

`TextStickerTextures`: lokální bílá ARGB rasterizace logickým Java monospace fontem, NativeImage ABGR, maximálně 128 cache položek, uvolnění při reloadu. Tint je společný s PNG. Drobný vzhled fontu se může lišit podle OS. Renderer běží pouze na klientu.

`WrappedDecal`: čtyři plochy kuboidu s malým offsetem, souvislé UV po obvodu, dělené TOP/DOWN s globální délkou, ořez na koncích a UV hranách, Sutherland–Hodgman polygon clipping; angular seam rozdělen před scale, aby větší decal nekreslil překryté kopie. Wrap celou trubku ignoruje stranu; u vzpěr/vidlice strana vybírá větev. Příčné posunutí posouvá obvodový spoj. Rotace/scale ořezané na dostupnou trubku. Vzhled na ohybech a extremních rotacích ještě není potvrzen ve hře.

Dokumentace použití: `docs/custom-stickers.md`. Zdrojová složka pro nové PNG `src/main/resources/assets/descentmtb/textures/stickers/`; úprava souboru nezmění starý JAR. Resource pack struktura, pack_format 34 a F3+T popsány. Multiplayer synchronizuje jen ID/text/nastavení, nikoli PNG bajty — ostatní klienti potřebují stejný resource pack. Nedělat nepravdivý slib automatického sdílení obrázků.

## Další schválené požadavky — dosud neimplementované

- Barevné kapátko a sladění barev doplňků/komponent; katalog skutečných komponent a barev již existuje, bonus UX ještě chybí.
- Porovnání původní a aktuální sestavy při podržení tlačítka.
- Poctivé tooltipy komponent a jejich účinků: nezobrazovat kosmetické volby jako změnu fyziky bez napojení. Dosavadní materiály a rámy převážně vizuální.
- Pamatovat nastavení stavebních nástrojů podle režimu; pojmenované vlastní trail šablony s náhledem a otočením; startovní marker pro trénink a návrat s kolem. Tato customization série do `trail/**` nesahala.
- Původní širší zadání smooth modu zůstává samostatné: wallride detection jen při airborne odrazu/vysoké rychlosti, stabilita na rovině, rychlé zatáčení, manuál bez zbytečného bailu, kolize země při pádu a zabránění nesmyslné rotaci ragdollu, zrychlení šlapání a config, pumptrack/povrchové štětce/klopenky/sharkfin/lávky/podpěry/obstacle overlays/mačeta, airbag tlumící bez trampolíny, radiální menu a trail cedule. Před pokračováním zjistit stav práce druhého agenta; netvrdit, že to tato customization série dokončila.
- Dřívější požadavek Sable podpory (Minecraft fyzikální entity), animace rukou/brzd, whip/table a dopaminové trick texty: nejsou touto etapou implementované, dohledat aktuální stav u fyziky. Uživatel žádal stavění ramp opět blíž staršímu samostatnému ovládání a stále hlásil špatnou wallride detekci.
- Původní přiložený customization prompt je `C:/Users/jakub/.codex/attachments/42539737-5393-4201-a5a1-fc4b6bac6231/Vložený text.txt`; případné resty zvonků, nočních světel, randomize a GUI ergonomie porovnat se současným kódem, nikoli slepě opakovat už implementované části. Jeho požadavek herních testů už neplatí.

## Working tree a bezpečné pokračování

Všechny vlastní Java/JSON/PNG/generátor změny 05 jsou commitnuté v `ac11e97`. Tento handoff bude mít samostatný dokumentační commit; nejsou žádné vlastní rozpracované nekompilovatelné zdroje. Cizí preview změny zachovat a necommitovat:

```text
 M tools/preview/v3/_sheet.png
?? tools/gen_texture_review.py
?? tools/preview/bike_layers.png
?? tools/preview/review_16x16/
?? tools/preview/stickers.png
?? tools/preview/v3/block/trail_sign.png
?? tools/preview/v3/block/trail_sign_post.png
?? tools/preview/v3/sign/
?? tools/preview/v4/
```

Příkaz pro sestavení (PowerShell): nastavit JAVA_HOME na `C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot`, spustit `.\gradlew.bat assemble --console=plain` s logem v `build/milestone-06.log`. JAR `build/libs/descentmtb-1.0.0-alpha.jar` kopírovat do nového milníku 06 a případně latest. Deprecated EventBusSubscriber warnings nejsou chyba sestavení. Žádné `git add -A`, žádné čekání na potvrzení od jiného agenta místo práce.
