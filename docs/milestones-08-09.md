# Milníky 08 a 09 — 2026-10-06

## 08: kolo bez jezdce nekřičí

Commit `51f2da9`, JAR `dist/descentmtb-milestone-08-riderless-sound.jar`.

Zvukový snímek výslovně rozlišuje přítomnost ovládajícího jezdce. Prázdné kolo nespouští predikci hlasového křiku ani hlas po dopadu. Při ztrátě jezdce se aktivní křik zastaví a resetuje se rozpracovaná predikce. Zvuky náboje, pneumatik a mechanického nárazu zůstávají. Skutečný přechod do pádu může přehrát jednorázový hlas právě vyhozeného jezdce, pokud na kole seděl v předchozím snímku; prázdné kolo samo nový hlas nevytváří.

`assemble` úspěšný, log `build/milestone-08.log`. Herní ověření neproběhlo.

## 09: propojení modelových dílů

Commit `e134460`, JAR `dist/descentmtb-milestone-09-frame-joints.jar`; `descentmtb-latest.jar` odpovídá 09.

- Enduro sedlovka prodloužena směrem dovnitř sedlové trubky, horní konec a sedlo zůstávají na svém místě. UV přesunuto do volného místa, aby prodloužený díl nepřekryl zámek sedla.
- DJ sedlovka má skutečnou zasunutou část a zámek na horním konci. Sedlo zůstává nízko na krátké vyčnívající části; nedošlo ke změně kotev jezdce. Vzpěry se sbíhají k sedlové trubce pod napojením horní trubky. Příčka je mezi vzpěrami.
- Párové značkové úchyty doplněny průchozími šrouby oka tlumiče. Volné konce dosavadních Commencal/Specialized detailů napojeny na existující konstrukci, odsazení YT výztuhy zmenšeno, aby se dotýkala sedlové trubky.
- Generátor značkových dílů i obě modelové textury aktualizovány. Vygenerovány všechny varianty povrchu a kotvy nálepek.

Požadovaný `python tools/check_frames.py`: **40 → 0 chyb**, všech 9 enduro a 3 DJ tvarů. Tolerance ani seznam kontrol nebyly změněny. `gen_enduro_texture.py` a `gen_hardtail_texture.py`: žádné UV překryvy/díry. `assemble` úspěšný, `build/milestone-09.log`. Bez hry, autopilota, herních screenshotů nebo unit suite.

Checker kontroluje spoje při klidové póze, geometrii sedla/sedlovky a polohu úchytů. Neověřuje skutečnou mechaniku konkrétní značky ani vůle při stlačení pérování. **Dosavadní společná zadní stavba značek stále není věrná jejich skutečné kinematice.** Milník 09 opravuje geometrické mezery, nikoli tuto celou konstrukční otázku.

## Navazující práce na věrných značkových konstrukcích

Prohlédnuta oficiální boční fotografie [Nomadu 6](https://www.santacruzbicycles.com/cdn/shop/files/nomad-6-hero-1.jpg?v=1732142350&width=1440). [Servisní stránka stejné generace MY23](https://www.santacruzbicycles.com/pages/product-support/nomad-6-my23) poskytuje rozkres horního/spodního VPP linku a jejich os. Podporuje požadavek na spodním linkem poháněný tlumič v dolní části předního trojúhelníku; společný vysoký pivot současného modelu tuto konstrukci nenahrazuje.

Pro YT nalezen oficiální [technický výkres Capra MK3 CF](https://www.yt-industries.com/media/1c/a7/6f/1729759076/CAPRA-MK3-CF-29-MX-TECHNICAL_INFORMATION_CUSTOMER_210518.pdf?ts=1729759076). Nebyl z něj implementován nový model. Zbytek referencí je v `docs/frame-catalog.md`; výběr správné generace zůstává nezbytný.

Další krok má řešit celý per-model pohyblivý mechanismus, nikoli jen přesun oka tlumiče: samostatné hlavní čepy/linky, propojení zadního trojúhelníku, oba úchyty, průchod řetězu a délky dílů při stlačení. Zachovat kompatibilitu ordinalů tvarů, modelové materiály, kotvy nálepek a kontakt jezdce. Zelený checker nelze prezentovat jako schválení věrnosti rámu.

Boombox/sluchátka (`wip/m07c-audio-wip/`), GPS mapa a zbývající immersion efekty nebyly v těchto dvou milnících měněny. Cizí preview a crop/zoom PNG zůstaly mimo commity.
