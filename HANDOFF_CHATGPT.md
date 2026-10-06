# Předání pro ChatGPT – Descent MTB (stav 2026-10-06)

## Aktuální stav po milníku 12

Aktuální JAR: `dist/descentmtb-milestone-12-immersion-complete.jar`; `dist/descentmtb-latest.jar` je jeho shodná kopie. Milníky 10–12 dokončují audio zařízení, GPS mapy/cedule/rámečky a bláto, trhací fólie, roost částice, speed lines a nastavitelné FOV. Ovládání a technická omezení: `docs/milestones-10-12.md`.

Ověření tohoto dokončení: pouze `assemble`, úspěšné. Nebyla spuštěna hra, screenshoty ani runtime/multiplayer testy. Historické tvrzení o unit testech níže se týká staršího předání, nikoli této práce.

Milník 08 odstranil hlas jezdce z pádů prázdného kola. Milník 09 opravil spoje rámů a sedel; checker byl tehdy na 0 chyb. Zbývá samostatná práce na věrné geometrii zadních staveb jednotlivých značek. Audio WIP ve `wip/` už není aktivní kód; jeho dokončená verze je v `src/main/java/com/descentmtb/audio` a `client/audio`.

## Historické předání před milníky 08–12

Repo `C:/MTBMod`, NeoForge 1.21.1, Java 21. Build (PowerShell): `$env:JAVA_HOME='C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot'; .\gradlew.bat assemble --console=plain`. Pravidla: žádné herní testy/screenshoty, jen build; nikdy `git add -A`; JAR po milníku do `dist/` + `descentmtb-latest.jar`. Poslední JAR: `dist/descentmtb-milestone-07-sound-update.jar`.

## PRIORITA 1 – Zvuky (HOTOVO)
- Descenders zvuky plně integrovány: `sounds.json` s 75 eventy a 201 OGG vzorky vygenerován z `tools/import_descenders_sounds.py`.
- `ModSounds.java` a `client/sound/*` (`BikeSoundController`, `BikeVoice`, `FreewheelPlayer`, `TrickSounds`, scream, landing, crash/bail) kompletně přepojeny.
- Zvonky (`custom/BellSounds`, `BikeBells`, `client/custom/BellPreview`) přepojeny na Descenders vzorky (`ding`, `mini`, `classic`, `horn`, `duck`).
- Config `riderVoice` (MALE, FEMALE, OFF) přidán do `ClientConfig.java`, default `bikeSoundVolume` nastaven na 0.6.
- `tools/gen_sounds.py` a staré syntetické `.ogg` smazány.
- Unit testy (`BikeSoundMathTest`, `ScreamTest` a celý test suite) 100% zelené.

## PRIORITA 2 – Rámy kol (uživatel: všechna kola mají chyby, hlavně sedlo; dirt kolo sedlo)
- `python tools/check_frames.py` vypíše 40 chyb (commit 831a251). Opravit v `client/model/EnduroBikeModel.java`, `HardtailBikeModel.java`, `tools/gen_frame_catalog.py`, pak `gen_enduro_texture.py`, `gen_hardtail_texture.py`, `gen_custom_assets.py`, dokud checker nehlásí 0 chyb:
  - všechna enduro: sedlovka málo zasunutá do sedlové trubky;
  - značky n,t,u,m,g,j: oka tlumiče ~0.4 mimo úchyty; m,g,j volné konce vahadel/výztuh (spojit nebo smazat);
  - DJ c,s,v: plovoucí `seat_bridge`, zámek sedla mimo sedlovku, vzpěry nedosahují k sedlové trubce, sedlovka nezasunutá. DJ sedlo nízko na krátké sedlovce.
- Značkové rámy (Nomad VPP, Torque, Cube, Commencal, YT, Stumpjumper) sdílí jednu zadní stavbu = nejsou věrné; ideálně per-značka čepy/vahadla podle oficiálních bočních fotek (Nomad 6: tlumič vodorovně v předním trojúhelníku, uchycen na spodní trubce, poháněn spodním VPP linkem).

## Rozdělané / odložené
- **Boombox + sluchátka (Milník 07c):** kód ve `wip/m07c-audio-wip/` (MIMO build). Funguje (ověřeno standalone): výpis audio aplikací Windows (JNA, filtr Discord/Teams…), process-loopback zachytávání aplikace, ztlumení aplikace na 1e-4 a zesílení zpět (bezztrátové), síťové payloady/server relay (mu-law 16 kHz). Chybí: přehrávání v MC (streaming SoundInstance s `getStream`), mixin útlumu okolí (SoundEngine.calculateVolume), bloky/itemy boombox+sluchátka, GUI `AudioSettingsScreen`, vypadnutí sluchátek při pádu.
- **GPS tracker (07b):** jen nahrávání trasy (commit 4caefde); chybí textura, propojení s `trail_sign`, `trail_map` item + `TrailMapScreen`, mapa v item framu.
- **07a zbytek:** bláto na kole, trhací páska, roost částice, speed lines + FOV (hotovo: airbag bezpečí, pád při zvonění jednou rukou, kutálení kola po pádu).
