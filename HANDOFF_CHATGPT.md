# Descent MTB — aktuální předání (2026-10-10)

Repozitář `C:/MTBMod`; GitHub `102010jk/modminecraftMTB`; publikovaná větev `master`.
Minecraft 1.21.1, NeoForge 21.1.250, Java 21.

## Milník 27 — lyže

JAR: `dist/descentmtb-milestone-27-skis.jar`, kopie `dist/descentmtb-latest.jar` a `releases/descentmtb-1.0.0-alpha-m27.jar` (SHA-256 `460c7c5bd5c126a3…`).

- Lyže jako nové typy `SKI_RACE` / `SKI_FREESTYLE` na entitě kola; šest párů v `ski/SkiBrand.java` (jen přidávat na konec). Fyzika v `ski/SkiPhysics.java`, `ski/SkiSurface.java` a větvích `p.ski` v `BikeSim`; kola beze změny.
- Na holé zemi drhnutí → měřič → pád; sníh rozpoznán i pod vrstvou sněhu (`McColumns`).
- Model / textury z `tools/gen_ski_assets.py`; póza `client/ski/SkierPose`, `SkiStance`, hůlky `SkiPoleLayer`.
- Síťový protokol 12.

Ověření: `assemble` + celá sada unit testů prošla. Hra nespuštěna; vzhled a chování ve hře čeká na test uživatele. Podrobnosti: [milník 27](docs/milestone-27-skis.md).

## Milník 26 (předchozí)

JAR: `dist/descentmtb-milestone-26-construction-freedom.jar`, kopie `dist/descentmtb-latest.jar` a `releases/descentmtb-1.0.0-alpha-m26.jar`.

- Jedna kreativní záložka Descent MTB. Boombox, sluchátka a trhací fólie kompletně odstraněné z aktivního buildu; výhled se nešpiní. Bláto na kole, ukládání jeho stavu a mytí zachované.
- Skoky a propojené rampy nemají staré malé rozměrové stropy ani limit počtu bloků. Zachované světové hranice, výška dimenze, načtené chunky, práva, ochrany, survival materiál a undo.
- Přímé zadání čísel v generátoru skoků; omezený počet bodů náhledu a přesná maximální strmost bez délkového skenování.
- Vyšší uložené rohy strmých profilů se neřežou na +/-16 bloků. Ostatní terénní editory a klonování mají původní konfigurovatelné limity.
- Síťový protokol 11 (v m27 zvýšen na 12). Staré odstraněné předměty/zařízení již nejsou registrované.

Podrobnosti: [milník 26](docs/milestone-26-construction-freedom.md); historie: [releases](releases/README.md).

## Pravidla práce

- Pouze build; nespouštět hru, autopilota, screenshoty ani herní testy.
- Nikdy `git add -A`; přidávat explicitně jen soubory dané práce.
- Zachovat JAR každého milníku a aktualizovat `dist/descentmtb-latest.jar`.
- Nepoužívat AgentBridge. Samostatný rozpracovaný addon `addons/mtb-ropeways` neměnit.

```powershell
$env:JAVA_HOME='C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot'
.\gradlew.bat assemble --console=plain
```

Ověření milníku 26: úspěšný `assemble`, kontrola obsahu JARu a shodných kopií. Žádná hra, vizuální/poslechové/multiplayer ověření, unit testy ani nezávislá agentová review. Změněny jen očekávané hodnoty existujícího testu nastavení skoků, test nebyl spuštěn.
