# Descent MTB

Kola, motorky, lyže a stavění tratí pro **Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21**.

## Stažení

- [Aktuální JAR](dist/descentmtb-latest.jar)
- [GitHub Releases](https://github.com/102010jk/modminecraftMTB/releases)
- [Přehled milníků](releases/README.md)

JAR vlož do složky `mods` profilu s NeoForge. Při aktualizaci nahraď původní Descent MTB JAR; ve složce má být jedna jeho verze.

## Jak začít

V jedné záložce **Descent MTB** najdeš kola, motorky, lyže, servisní stojan, pumpu, GPS, mapu i všechny stavební nástroje a bloky.

Pravým klikem na blok polož kolo, pravým klikem na kolo nasedni. Shift slouží k sesednutí; **Shift + pravý klik na prázdné zaparkované kolo** ho vrátí do inventáře s jeho úpravami. Ve stojanu upravíš komponenty, barvy a samolepky; obrazovka ukazuje náhled a stav neuložených změn.

## Ovládání

Výchozí klávesy odpovídají současnému kódu. Modové vazby lze změnit v **Nastavení → Ovládání → Descent MTB**.

| Akce | Klávesa |
|---|---|
| Šlapání / plyn | Z |
| Brzda; ve vzduchu tweak/table | Space |
| Zatáčení / rotace do stran | ← / → |
| Náklon dopředu / dozadu | ↑ / ↓ |
| Skrčení / protažení | S / D |
| Bunny hop | podrž X, pak pusť |
| Jednotlivé triky | I, O, J, L, K, U |
| Kamera / reset kamery | V / B |
| Návrat / návrat na start | R / Backspace |
| Zvonek | H |
| Nabídka trailové lopaty | G |
| Vrácení změny trati | Ctrl + Z |

Triky závisí na typu kola. Nastavení tricks.trickKeyScheme = CLASSIC používá C + šipky místo samostatných trikových kláves. Ovladač používá RT/LT pro plyn/brzdu, páčky pro náklon a pumpování a LB + pravou páčku pro triky.

## Lyže

V záložce **Descent MTB** je za pit bikem šest skutečných párů lyží. Závodní: Atomic Redster G9 FIS (obří slalom), Rossignol Hero Elite ST Ti (slalom) a Fischer RC4 Worldcup RC. Freestylové twin tipy: Armada ARV 96, Line Chronic 101 a Faction Prodigy 2. Popisek předmětu ukazuje délku, poloměr krojení a šířku pasu; podle nich se pár i chová. Dlouhá obřačka drží rychlost a široký oblouk, slalomka zatáčí ostře, freestylové lyže jsou hravější, rychleji se točí ve vzduchu a dají se odjet i pozpátku (switch). Lyže se pokládají, nasedá se na ně a vrací do inventáře stejně jako kolo.

Ovládání používá stejné klávesy i ovladač jako kolo:

| Akce na lyžích | Klávesa | Ovladač |
|---|---|---|
| Odpich hůlkami (jen v pomalé jízdě); na holé zemi chůze | Z | RT |
| Brzda: pluh / smyk napříč (hockey stop) | Space | LT |
| Oblouky: krojení, pomalu smýkaný oblouk | ← / → | levá páčka do stran |
| Náklon dopředu / dozadu | ↑ / ↓ | levá páčka nahoru / dolů |
| Podřep; s náklonem dopředu sjezdový posed (tuck) | S | pravá páčka dolů (+ levá nahoru) |
| Protažení | D | pravá páčka nahoru |
| Odraz | podrž X, pak pusť | pravá páčka dolů → nahoru |
| Triky (závodní: Spread Eagle, Daffy, Iron Cross…; freestyle: grab triky) | I, O, J, L, K, U | LB + pravá páčka; LB + klik pravé páčky |

Lyže jedou jen po sněhu (sněhový blok, vrstva sněhu, prašan) a po ledu, kde kloužou rychle, ale hrany skoro nedrží. Po dřevě (box, rail) se klouže. Na kameni, hlíně, trávě, štěrku a jiné holé zemi skluznice **drhnou**: lyže prudce brzdí, letí prach a jiskry, ozve se skřípění a na HUD se plní ukazatel „Kameny!“. Krátký přejezd se dá ustát, ale když se ukazatel naplní, lyže se o kameny zaseknou a jezdec spadne. Na sněhu se ukazatel zase vyprázdní. Na sněhu lyže při ostrém oblouku, brzdění a dopadu víří sníh.

Pumpa ani servisní stojan s lyžemi nepracují a lyže se nešpiní blátem.

## Stavění a orientace

Trailová lopata má režimy pro skoky, klopenky, ruční úpravy, rampy a celé linie. Podrž G, najeď na režim a pusť; krátké stisknutí otevře klikací nabídku. V ní funguje i Tab pro kategorii, šipky pro režim a Enter pro potvrzení. Shift + kolečko mění režim nebo jeho nastavení. Ctrl + pravý klik na tvarovaný blok otevře detailní editor. Nápověda nad hotbarem běžně ukazuje jen režim a nastavení; při držení Shift se rozbalí.

Generátor skoků nemá původní stropy 12 m délky, 7 m šířky a 4 m výšky. Délku, šířku, výšku, úhel odrazu, plošinu a dopad můžeš přímo napsat do číselných polí; Enter potvrdí hodnotu. Propojení ramp nemá limit 32 m / 8 m převýšení. Stavba musí být uvnitř hranic a výšky světa, v načtených chunkech a na povolených blocích; převislá stěna není výškový profil a odraz má úhel pod 90°. Tyto stavby nadále podporují vrácení změn a spotřebu materiálu v survivalu.

Boombox, sluchátka a trhací fólie jsou odstraněné. Bláto zůstává pouze na samotném kole; výhled se nešpiní. Předměty odstraněných zařízení ze starých světů již tato verze neregistruje.

GPS zaznamenává jízdu. Trasy lze propojit s cedulemi a zobrazit v mapě; mapa podporuje posun, zoom a výškový profil. Více mapových rámečků vedle sebe může tvořit mapu na zdi.

## Nastavení

Client config config/descentmtb-client.toml obsahuje jízdu, kamery, triky, zvuk a vizuální efekty. V části hud lze samostatně vypnout rychlost, čas ve vzduchu a nápovědu nástrojů; compactTrailHints ovládá kompaktní nápovědu.

Trailové nástroje mají společný config; `maxBlocks` dál omezuje běžné terénní úpravy a kopírování, ale ne generátor skoků a propojení ramp. Limity úprav komponentů spravuje serverový config. Integrace pohyblivých staveb je volitelná: samostatný mod Sable není potřeba pro běžnou jízdu.

## Sestavení

Nastav JAVA_HOME na Java 21 a z kořene naklonovaného projektu spusť:

```powershell
.\gradlew.bat assemble --console=plain
```

Výsledek: build/libs/descentmtb-1.0.0-alpha.jar.

## Struktura projektu

- src/main: aktivní mod a jeho assety; src/test: existující automatické testy.
- tools: generátory a pomocné nástroje; tools/preview: kontrolní podklady.
- docs: technická dokumentace; docs/archive: historické plány a předání.
- releases a dist: hotové a archivované JARy.
- wip: historický rozpracovaný kód mimo build.
- addons: samostatné projekty, které hlavní build nezahrnuje.

[Aktuální předání a stav ověření](HANDOFF_CHATGPT.md). Starší tvrzení v archivech popisují tehdejší stav a nenahrazují ověření aktuální verze.
