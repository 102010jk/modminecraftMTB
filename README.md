# Descent MTB

Kola, motorky a stavění tratí pro **Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21**.

## Stažení

- [Aktuální JAR](dist/descentmtb-latest.jar)
- [GitHub Releases](https://github.com/102010jk/modminecraftMTB/releases)
- [Přehled milníků](releases/README.md)

JAR vlož do složky `mods` profilu s NeoForge. Při aktualizaci nahraď původní Descent MTB JAR; ve složce má být jedna jeho verze.

## Jak začít

Kreativní inventář má tři kategorie:

- **Kola:** enduro, hardtail, motokrosová motorka, pitbike a servisní stojan.
- **Stavění tratí:** trailová lopata, tvarovaná hlína a lávky, podpěry, kořeny, kameny, airbag, bariéry a cedule.
- **Vybavení:** pumpa, GPS, mapa, sluchátka, reproduktor a trhací fólie.

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
| Audio nastavení | P |
| Stržení fólie | Y |
| Nabídka trailové lopaty | G |
| Vrácení změny trati | Ctrl + Z |

Triky závisí na typu kola. Nastavení tricks.trickKeyScheme = CLASSIC používá C + šipky místo samostatných trikových kláves. Ovladač používá RT/LT pro plyn/brzdu, páčky pro náklon a pumpování a LB + pravou páčku pro triky.

## Stavění a orientace

Trailová lopata má režimy pro skoky, klopenky, ruční úpravy, rampy a celé linie. Podrž G, najeď na režim a pusť; krátké stisknutí otevře klikací nabídku. V ní funguje i Tab pro kategorii, šipky pro režim a Enter pro potvrzení. Shift + kolečko mění režim nebo jeho nastavení. Ctrl + pravý klik na tvarovaný blok otevře detailní editor. Nápověda nad hotbarem běžně ukazuje jen režim a nastavení; při držení Shift se rozbalí.

GPS zaznamenává jízdu. Trasy lze propojit s cedulemi a zobrazit v mapě; mapa podporuje posun, zoom a výškový profil. Více mapových rámečků vedle sebe může tvořit mapu na zdi.

## Nastavení

Client config config/descentmtb-client.toml obsahuje jízdu, kamery, triky, zvuk, audio a vizuální efekty. V části hud lze samostatně vypnout rychlost, čas ve vzduchu a nápovědu nástrojů; compactTrailHints ovládá kompaktní nápovědu. audioDevices.musicGain zesiluje PCM hudbu před limiterem. Posuvníky v audio nabídce se použijí při výběru aplikace nebo desky.

Trailové nástroje mají společný config; limity úprav komponentů spravuje serverový config. Integrace pohyblivých staveb je volitelná: samostatný mod Sable není potřeba pro běžnou jízdu.

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
