# Descent MTB — aktuální předání (2026-10-08)

Repozitář: `C:/MTBMod`, GitHub `102010jk/modminecraftMTB`, větev `master`.
Minecraft 1.21.1, NeoForge 21.1.250, Java 21.

## Milník 25: úklid rozhraní a projektu

JAR: `dist/descentmtb-milestone-25-clean-ui.jar`; jeho kopie je `dist/descentmtb-latest.jar` a `releases/descentmtb-1.0.0-alpha-m25.jar`.

- Sdílené pozadí devíti editorů přes `client/ui/DescentScreen`; společné barvy a omezený text přes `UiTheme`.
- Adaptivní kategorie hůlky, popisy mimo prstenec, klávesnicová volba a opravené krátké otevření nabídky.
- Audio: pojmenované posuvníky, stránkování, tooltipy aplikací, zahazování zastaralých výsledků načítání.
- Aktualizace: zalomený text, přehledná karta a ukazatel stahování. Workshop: nadpis nezasahuje do stavu uložení.
- Menší nápověda nad hotbarem; Shift zobrazí podrobnosti. Časomíra nepřekrývá banner triku. HUD se nekreslí pod otevřeným menu.
- Kreativní inventář rozdělený na kola, stavění tratí a vybavení; registry předmětů a bloků zůstávají zachované.
- README odpovídá aktuálním klávesám a funkcím. Staré plány a kontrolní obrázky jsou archivované, nikoli smazané.

Podrobnosti: [milník 25](docs/milestone-25-clean-ui.md). Starší aktuální změny: [milníky 20–24](releases/README.md). Historické předání do milníku 17: [archiv](docs/archive/handoff-through-m17.md).

## Pravidla práce

- Pouze build; nespouštět hru, autopilota, screenshoty ani herní testy.
- Nikdy `git add -A`; přidávat explicitně jen soubory dané práce.
- Zachovat JAR každého milníku a aktualizovat `dist/descentmtb-latest.jar`.
- Nepoužívat AgentBridge. Ostatní rozpracované projekty neměnit.
- Rozpracovaný addon `addons/mtb-ropeways` je samostatný a nebyl součástí tohoto úklidu.

Build v PowerShellu:

```powershell
$env:JAVA_HOME='C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot'
.\gradlew.bat assemble --console=plain
```

Ověření milníku 25: `assemble` úspěšný; bez herního, vizuálního, poslechového či multiplayer ověření a bez nezávislé agentové review. Jde o úklid rozhraní a projektu, nikoli potvrzení, že všechny zbývající chyby fyziky či vzhledu jsou odstraněné.
