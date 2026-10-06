# Předání a Zadání pro Claude: Descent MTB – Feel, Audio, Visuals & Dirt Moto

Ahoj Claude! Navazuješ na projekt **Descent MTB** pro **Minecraft NeoForge 1.21.1 (Java 21)** v repozitáři `C:/MTBMod`.

Tento update navazuje na dokončení Milníku 07 (zvuky z Descenders a oprava geometrie rámů) a je kompletně zaměřený na:
1. **Game Feel & Šťavnatost (Juice):** Radikální odstranění tuhosti, oprava kamer, plynulé řetězení triků, reálné smyky (power slide/drifting) a fyzikální odezva na dopady (kamera trauma / shake).
2. **Audio & Sluchátka / Boombox:** Fix kritických memory leaků, oprava odtlumení Windows aplikací (Spotify blasting bug), prostorový zvuk spadlých sluchátek a reálný akustický profil sluchátek (tlumení světa).
3. **Vizuální Styl & Textury (Create Mod & Jappa Vanilla):** Úplný odklon od syntetických plochých výplní; přechod na ručně stínovaný pixel-art s barevným posunem (hue-shifting) a ambientní okluzí. V repozitáři je připravena vzorová srovnávací vitrína v `tools/preview/texture_comparison/`.
4. **Katalog 60+ nalezených chyb:** Důkladný audit od 6 specializovaných subagentů pokrývající fyziku, síť, UI, mapu, renderování bloků a NBT perzistenci.
5. **NOVÝ SPORT: DIRT MOTORKA (Motocross / Pitbike):** Přidání plnohodnotného motorového stroje se spalovacím motorem, točivým momentem na zadním kole, wheelie a motokrosovým modelem.

---

## 0. Pravidla projektu a příkazy pro sestavení

* **Prostředí:** Windows PowerShell, Java 21:
  ```powershell
  $env:JAVA_HOME='C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot'
  ```
* **Build & Testy:**
  ```powershell
  $env:JAVA_HOME='C:/Users/jakub/AppData/Local/Programs/Eclipse Adoptium/jdk-21.0.11.10-hotspot'; .\gradlew.bat assemble --console=plain
  .\gradlew.bat test --console=plain
  ```
* **Git pravidla:** **NIKDY nespouštěj `git add -A`!** Vždy přidávej jen explicitní soubory (`git add src/...`).
* **Testování:** Žádné in-game testy s grafickým klientem (`runClientAuto` je zakázáno). Spoléhej na unit testy a offline kontrolery (`python tools/check_frames.py`).
* **Výstupní JAR:** Sestavený JAR se kopíruje do `dist/` a jako `dist/descentmtb-latest.jar`.

---

## 1. Stav po předchozích krocích (Co je již hotové)

1. **Priorita 1 (Zvuky) – HOTOVO (Commit `3eae6f7`):**
   - 201 reálných OGG vzorků z Descenders importováno do `assets/descentmtb/sounds/`.
   - `sounds.json` vygenerován se 75 herními událostmi (pláště na hlíně, kamenech, dřevě, trávě, řetěz, cvrček náboje, dopady odpružení, zvonky, scream jezdce).
   - `ModSounds.java`, `BikeSoundController.java`, `BikeVoice.java`, `BellSounds.java` plně přepojeny. Starý syntetický kód smazán.
   - Všechny zvukové unit testy procházejí (100% zelené).
   - Vytvořen JAR `dist/descentmtb-milestone-07-sound-update.jar`.
2. **Priorita 2 (Geometrie rámů) – HOTOVO (0 chyb):**
   - Všech 12 profilů rámů (Enduro: classic, high-pivot, low-pivot, nomad, torque, one77, meta, stumpy, sender; Hardtail: classic, straight, v-drop) prochází offline geometrií:
     ```powershell
     python tools/check_frames.py
     # TOTAL issues: 0
     ```
   - Opravena orientace příčné vzpěry `seat_bridge` u dirt jump hardtailu (`HardtailBikeModel.java`).
3. **Vzorové textury Create Mod / Vanilla – VYGENEROVÁNO:**
   - Vytvořen generátor a ukázky v `tools/preview/texture_comparison/`:
     - `porovnani_textur_create_style.png`: Velký srovnávací plakát (staré ploché barvy vs. nový stínovaný pixel-art).
     - `saddle_dirt_pivotal_32x32.png` & `8x`: Pivotal sedlo s prošíváním a reliéfem.
     - `tire_knobby_gumwall_32x32.png` & `8x`: Agresivní plášť s gumwall boky a sipingem.
     - `fork_kashima_gold_16x32.png`: Zlatá Kashima s odleskem a sag kroužkem.
     - `frame_welds_alloy_32x32.png`: Trubka rámu s housenkovými svary (TIG welds).
     - `stickers_showcase_64x32.png`: Retro závodní pruhy, moto štítek a plameny.

---

## 2. Textury & Vizuální Styl – Create Mod & Jappa Vanilla Standard

Uživatel vyžaduje, aby všechny textury v módu měly charakter a vizuální úroveň srovnatelnou s **Create módem** nebo moderním vanillovým Minecraftem (**Jappa styl**).

### Klíčové principy, které musí splňovat každá textura:
1. **Směrové osvětlení (Directional Shading):**
   - Světlo přichází ze shora zleva pod úhlem 45° (standard Minecraftu).
   - Horní a levé hrany prvků mají jemný 1px highlight (zvýraznění hrany).
   - Spodní a pravé hrany mají tmavý stín.
2. **Posun odstínu (Hue-Shifting):**
   - Nikdy nestínuj přidáváním černé nebo bílé!
   - Highlighty se posouvají do teplejších tónů (ke žluté/zlaté).
   - Stíny se posouvají do chladnějších tónů (k modré/fialové/hluboké hnědé).
3. **Ambientní Okluze (Crevice Shadowing):**
   - V místech spojů trubek, kolem misek hlavového složení a uložení tlumiče musí být ztmavující přechod (AO).
4. **Žádné ploché syntetické plochy:**
   - Dřevěné lávky musí mít léta a hřebíky.
   - Hliněné traily musí mít organickou texturu udusané hlíny s drobnými kamínky.
   - Dirt sedlo musí mít patrné švy a materiál (kůže/kevlar) s vlisovaným logem.
5. **Autentické nálepky (Stickers):**
   - Závodní tabulky s čísly, retro závodní pruhy, plameny, maskovací vzory, sponzorská loga ve vanillovém rozlišení.

---

## 3. Game Feel, Kamera, Fyzika a Řízení

Současná jízda působí na rovných úsecích v pořádku, ale v extrémech je tuhá a postrádá dynamiku.

### 3.1 Kamera z první osoby (Helmet Cam)
- **Volný rozhled:** Hráč musí mít možnost rozhlížet se i za jízdy (omezený kužel ±70° do stran, ±45° vertikálně).
- **Korekce sklonu:** Statický sklon 24° dolů způsobuje, že na prudkém trailu kamera míří kolmo do hlíny. Sklon kamery se musí adaptovat na sklon terénu a horizont.
- **Detekce kolize:** Kamera nesmí procházet stropem tunelů nebo převisy (doplnit raycast proti blokům).

### 3.2 Odezva na dopad a přetížení (Camera Shake / Juice)
- Při tvrdém dopadu nebo dorazu tlumičů (bottom-out) musí proběhnout krátké zatřesení kamery (trauma shake) a ozvat se kovové cvaknutí dorazu.
- Rychlostní linky (speed lines) a FOV rozšíření musí fungovat jak v 1. osobě, tak ve 3. osobě.

### 3.3 Řetězení triků (Flow & Trick Chaining)
- Při přechodu z jednoho triku do druhého (např. whip do barspinu) nesmí póza jezdce v jednom ticku poskočit zpět do neutrálu. Přechod musí být interpolován s lehkou setrvačností.
- Rotace u Tailwhipu a Barspinu nesmí být lineární – musí mít dynamický náběh (švihnutí) a zpomalení při chycení (catch).

### 3.4 Smyky a Driftování (Power Slide)
- Při zatažení zadní brzdy v náklonu musí zadní kolo ztratit adhezi a vybočit do kontrolovaného smyku.
- Vizuálně se zadní kolo zablokuje, ozve se zvuk hrabání pláště na hlíně (`skid_dirt`) a ze styčné plochy začnou létat částice hlíny (roost).

---

## 4. Sluchátka & Boombox – Audio Rework

### 4.1 Spadlá sluchátka (Fix Spotify Blasting Bug)
- **Chyba:** Při pádu hráče (bail) kód v `DesktopCapture.java:162` zavolá `WinAudioSessions.setVolume(p, originalVolume)` a okamžitě pustí hudbu z Windows na plné pecky do fyzických reproduktorů.
- **Řešení:**
  1. Windows aplikace **zůstane utlumená** po celou dobu, dokud je sluchátkový item na světě.
  2. Spadlá sluchátka se ve světě stanou prostorovým 3D emitorem (`SoundSource.RECORDS`). Zvuk hraje z místa ležících sluchátek na zemi, s omezeným poloměrem (~5 bloků) a plastovým, plechovým charakterem.
  3. Znovunasazením sluchátek se zvuk vrátí do uší.
  4. Hlasitost ve Windows se obnoví pouze tehdy, když hráč sluchátka vypne v GUI nebo se item zničí/despawne.

### 4.2 Akustika sluchátek
- Když má hráč sluchátka na uších a hraje hudba, **zvuky okolí Minecraftu musí být utlumeny o ~80–90 %** (muffled / low-pass filtr).
- Vlastní kolo a výkřiky jezdce však nesmí být utlumeny na nulu (opravit chybu v `SoundEngineMixin`, která tlumí i `SoundSource.PLAYERS`).

---

## 5. Kompletní Audit Chyb (Master Bug Catalog)

Následující seznam obsahuje konkrétní chyby nalezené v kódu, rozdělené do 5 kategorií:

### A. Audio & Windows Loopback Capture
1. **`DesktopCapture.java:162` – Spotify Blasting Bug:** `stopNow()` okamžitě obnoví hlasitost Windows aplikace při pádu z kola.
2. **`DesktopCapture.java:138` – Trvalé ztlumení Windows:** `start()` volá `stopNow()` a hned čte volume; kvůli asynchronnímu WASAPI přečte `1e-4f` a uloží ji jako `originalVolume`. Aplikace ve Windows zůstane navždy ztlumená na 0.0001.
3. **`ProcessLoopback.java:195` – Pád JVM (Access Violation):** Pokud `activate` vyprší (timeout 3s), GC uvolní lokální COM callbacky, ale vlákno Windows do nich zapíše – pád JVM.
4. **`SoundEngineMixin.java:18` – Závod vláken (Race Condition):** Mixin běží na zvukovém vlákně a čte `MC.player.getItemBySlot(EquipmentSlot.HEAD)` bez synchronizace s render vláknem.
5. **`calculateVolume` nezpracovává streamované zvuky:** Zvuky ze jukeboxu zůstávají permanentně ztlumené na 25 % i po sundání sluchátek.
6. **`PcmStream.read()` & `GainAudioStream.read()` – Native Memory Leak:** Alokují unpooled přímé ByteBuffery na každý read (12.5x za sekundu).
7. **`ProcessLoopback.java` – COM Leak:** Vlákna nikdy nevolají `CoUninitialize()`.

### B. Fyzika, Pohyby & Fyzikální Entity
8. **`SableTerrain.java:46` & `BikeSim.java:1066` – Fázování skrz zdi:** Raycast ignoruje kolize, pokud `hit.isInside() == true`. Při rychlostech nad 15 m/s kolo proletí skalní stěnou.
9. **`BikeSim.java:1056-1060` – Chybějící boční a zadní sondy:** Řídítka (±0.33 m), pedály a zadní stavba nemají sondy kolize; couvání do zdi projde skrz.
10. **`BikeSim.java:571` – Dělení nulou / NaN infekce:** `hit.set(... / bodyHit.normal.y)` při svislém nárazu dělí nulou, což otráví pozici kola hodnotou `NaN` a smaže entitu.
11. **`BikeSim.java:953, 967` – Falešný pád při jízdě na couvačku (Fakie):** Přistání otočené o 180° vyhodnotí `yawErr ≈ 180° > riskYawLimit (55°)` a okamžitě způsobí crash.
12. **Absence fyziky vody:** Kolo jezdí po dně oceánu 60 km/h bez odporu vody nebo vztlaku.
13. **`SafeDismount.java:12-25` – Masivní lagy při sesednutí:** Vykoná až 2 800 dotazů na kolize bloků na hlavním serverovém vlákně; při pádu do rokle vyhodnotí vzduch jako bezpečný a shodí hráče do voidu.
14. **`MountainBikeEntity.java:406-414` – Crash při běžném sesednutí za jízdy:** Sesednutí při rychlosti > 0.1 m/s okamžitě aktivuje kutálení a simulaci pádu.
15. **`McColumns.java:183` vs `SableTerrain.java:44` – Hranice chunků:** Nenačtené chunky způsobí pád kola do prázdna pod mapu.

### C. Bloky, Traily, Okluze & Osvětlení
16. **`ShapedBakedModel.java:75` & `ModBlocks.java:35` – Z-Fighting se solidními bloky:** Rampy a trailové bloky nemají okluzi proti plným vanillovým blokům (kámen, hlína), což způsobuje blikání překrývajících se polygonů.
17. **`ShapedQuads.java:143` – Černý stín pod stropy:** Šikmé povrchy ramp nastavují normálu `Direction.UP`, což vzorkuje světlo v pevném bloku stropu a způsobí, že rampa v tunelu zčerná jako uhel.
18. **`ShapedQuads.overlay()` – Alokační špička:** Generuje až 1 280 polygonů na blok pro kořeny/kameny, což způsobuje záseky GC.
19. **`TapeCurve.java:28-30` – Páska se prověšuje pod zem:** Při vzdálenosti sloupků 20–24 bloků průvěs pásky klesne 16 cm pod úroveň terénu.
20. **`BarrierPostRenderer.java:71` – Zmizení pásky při načtení:** Páska se vykresluje jen ze sloupku s nižšími souřadnicemi; pokud je v nenačteném chunku, páska zmizí celá.
21. **`ShapingBlockItem.java:58-64` – Desync položení trailu:** Na klientovi se položený blok vykreslí jako tenká placka 0.1 bloku z hrubé hlíny, dokud nepřijde paket ze serveru.
22. **`McColumns.java:54` – Kolize klíče pro záporné Y:** Bitový posun ignoruje záporná čísla v deepslate vrstvě (Y < 0), což corruptuje fyziku terénu.
23. **`McColumns.java:94` – Pád skrz rampu pod převisem:** `if (!RampBlock.isRamp(s)) return false;` předčasně ukončí hledání povrchu, pokud je nad rampou jakýkoliv blok.

### D. GUI, Menu & Ergonomie
24. **`TrailMapScreen.java:19` – Pád na `IndexOutOfBoundsException`:** Neclamped index vybrané trasy shodí hru.
25. **`TrailMapFrames.java:24-26` – Zrcadlové převrácení mapy v rámečku:** Rotace o 180° otočí osu X a prohodí Východ a Západ.
26. **`MapTexture.java:17` – Desync dimenzí v rámečku:** Zobrazuje trať z Netheru i v Overworldu.
27. **`TrailMapScreen.java:16` – Dvojitý blur menu v 1.21:** Chybějící override `renderBackground` spouští vanillový shader blur přes vlastní ztmavení.
28. **`WorkshopScreen.java:383` – Ztráta úprav při odebrání kola ze stojanu:** Síťový race condition mezi `WorkshopApplyPayload` a `WorkshopTakePayload` zahodí neuložené úpravy.
29. **Audio Screen UX:** Zobrazuje pouze nejasné názvy oken bez ikony nebo názvu procesu (Spotify vs Chrome); chybí plynulé posuvníky a indikátor hrajícího zvuku.
30. **Názvy dílů:** Nudné technické názvy ("Carbon", "Red", "Blue") nahradit atraktivními MTB/moto názvy.

### E. Položky, NBT & Síťové exploity
31. **`MountainBikeEntity.java:311` – Float Overflow crash serveru:** Poslání extrémní rychlosti (`1e200`) způsobí `NaN` pozici a pád serveru na neplatný AABB.
32. **`RiderServer.java:48-73` – Absence serverové validace terénu:** Umožňuje cheaterům létat a procházet zdmi rychlostí 50 m/s.
33. **`RespawnPlanner.java:85` – Synchronní načítání chunků na Netty vlákně:** Může způsobit deadlock serveru při stisku respawnu.
34. **Absence `ctx.enqueueWork` v síťových paketech:** `BikeBells`, `BoomboxServer` a `TrailRecords` mutují herní stav přímo na Netty vláknech, což vede k `ConcurrentModificationException`.
35. **Duplikace kol v Creative i Survival:** Sneak + pravé kliknutí nekontroluje `isRemoved()`, což umožňuje zdvojení kola při interakci dvou hráčů najednou.

---

## 6. Kompletní Rework Trailové Mapy

Mapa tratí od ChatGPT byla nepřehledná, padala a měla převrácené osy.
* **Architektura:**
  - Vektorové vykreslování trasy: plynulé Bézierovy křivky namísto hrubých lomených čar.
  - Barevné kódování obtížnosti podle IMBA/bikepark standardu:
    - **Zelená (Easy/Flow):** Široká stopa, mírné klesání.
    - **Modrá (Intermediate):** Menší klopenky, vlny, skoky s plným stolem.
    - **Červená (Advanced/Technical):** Prudké pasáže, kameny, kořeny, gap skoky.
    - **Černá / Pro Line:** Masivní dropy, road gapy, extrémní sklon.
  - **Výškový profil (Elevation Profile):** Spodní panel s osou výšky (Y) a vzdálenosti (X), zobrazení celkového klesání a průměrného sklonu.
  - **Měření časů (Stopky & PB):** Tabule na začátku trailu odstartuje čas; projetí cílové brány změří čas a uloží osobní rekord (Personal Best) na mapu hráče.
  - **Nástěnná mapa:** Správně orientovaná mapa v Item Framu (opravený bug s převrácením Východ/Západ).

---

## 7. NOVÝ SPORT: DIRT MOTORKA (Motocross / Pitbike)

Dirt bike je prvním motorovým vozidlem v módu! Využívá robustní fyzikální základy kola, ale přináší zcela jiný pocit z jízdy.

### 7.1 Fyzika a Jízdní Dynamika
* **Hmotnost a setrvačnost:**
  - Kolo váží ~14 kg; motorka váží **~100–110 kg**.
  - Znatelně vyšší setrvačnost, stabilnější chování ve vyjetých kolejích, masivnější absorpce nerovností.
* **Pohon zadního kola (Throttle Torque):**
  - Držitel plynu (W) neudává cílovou rychlost jako u koně, ale aplikuje **točivý moment na zadní kolo**.
  - Při plném plynu z klidu se zadní kolo protočí a odhodí hlínu (roost).
* **Power Wheelie & Zvedání na zadní:**
  - Zatažení za řídítka (Space / S) pod plynem zvedne přední kolo do svíčky.
* **Odpružení:**
  - Obrácená teleskopická vidlice (USD – Upside Down) se zdvihem 300 mm.
  - Zadní centrální tlumič s progresivním přepákováním (monoshock).

### 7.2 Zvukový subsystém motoru
* Zvuková smyčka 4-taktního jednoválce 250cc:
  1. `moto_idle.ogg`: Nízké, klidné bublání na volnoběh (~1 500 RPM).
  2. `moto_low.ogg`: Náběh točivého momentu (~3 500 RPM).
  3. `moto_mid.ogg`: Agresivní tah v lineárním pásmu (~7 000 RPM).
  4. `moto_high.ogg`: Zaječení vytočeného motoru (~11 000 RPM).
  5. `moto_limiter.ogg`: Omezovač otáček (staccato štěkání).
  6. `moto_backfire.ogg`: Střelba do výfuku při prudkém ubrání plynu ve vysokých otáčkách.
* Pitch a gain se dynamicky mění v `MotoSoundController.java` podle otáček motoru a zatížení zadního kola.

### 7.3 3D Model a Vizuální Prvky
* **Rám & Motor:**
  - Dvojitý kolébkový rám z ocelových trubek.
  - Motorový blok s chladicími žebry, svodem výfuku a koncovkou.
  - Žádné kliky a pedály – robustní **zubaté ocelové stupačky (footpegs)**.
* **Kapotáže a Plasty:**
  - Plastová nádrž a boční kryty chladiče (shrouds), přední blatník a zadní blatník s bočními tabulkami na čísla.
  - Široká hrazdová řídítka s pěnovým chráničem (bar pad).
  - Dlouhé, rovné motokrosové sedlo umožňující posun těžiště jezdce dopředu i dozadu.
* **Pláště:**
  - Širší a agresivnější motokrosový špalíkový dezén vzadu i vpředu.

---

## 8. Doporučený postup implementace pro Claude

1. **Krok 1: Fyzika a Game Feel (Kamera & Smyky)**
   - Opravit raycast kamery v tunelu a adaptivní vertikální náklon.
   - Povolit uzamčení zadního kola při brzdění a propojit smyk se zvukem hrabání a roost částicemi.
2. **Krok 2: Audio Fixy & Sluchátka**
   - Opravit memory leaky v přímých bufferech a COM vláknech.
   - Implementovat bezpečné uchování hlasitosti Windows (odstranit Spotify blasting bug).
   - Vytvořit prostorový zvukový emitor pro spadlá sluchátka.
3. **Krok 3: Oprava sítě a bezpečnosti**
   - Obalit síťové handlery do `ctx.enqueueWork(...)`.
   - Přidat ochranu proti NaN float overflow při extrémních rychlostech.
4. **Krok 4: Trail Map Rework**
   - Opravit `IndexOutOfBoundsException` v `TrailMapScreen`.
   - Opravit zrcadlení Východ/Západ v rámečku na zdi.
5. **Krok 5: Dirt Motorka (Moto)**
   - Vytvořit entitu `DirtBikeEntity` a model `DirtBikeModel`.
   - Implementovat systém točivého momentu na zadní kolo a dynamický zvukový regulátor motoru.
6. **Krok 6: Finální textury ve stylu Create Modu**
   - Využít principy a palety ze srovnávací vitríny `tools/preview/texture_comparison/`.

Hodně štěstí! Kód je čistý, testy jsou zelené a geometrie rámů hlásí 0 chyb. Vše je připraveno pro velký skok v hratelnosti i obsahu.
