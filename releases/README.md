# Descent MTB – releases

Hotové buildy módu pro **Minecraft 1.21.1 + NeoForge 21.1.x** (Java 21). JAR stačí vložit do složky `mods`.

## 1.0.0-alpha-m25 (milník 25)

Soubor: `descentmtb-1.0.0-alpha-m25.jar`
SHA-256: `d1231bae76ef5ca75f77b53ac3751033dc813ded293d7324f2f150bea25cb089`

- Sjednocená pozadí devíti editorů, přehlednější audio s posuvníky a stránkováním, zalomené texty aktualizací.
- Adaptivní nabídka hůlky s klávesnicovou volbou; kompaktní nápověda nad hotbarem, podrobnosti při držení Shift.
- Kola, stavění tratí a vybavení mají vlastní kreativní kategorie. HUD a banner triku respektují místo časomíry.
- Aktuální návody a úklid starých podkladů do archivů.

Ověřeno pouze `assemble`; bez herního, vizuálního či poslechového ověření a bez nezávislé review. Fyzika, modely a textury nebyly v tomto milníku přepisované.

## 1.0.0-alpha-m24 (milník 24)

Soubor: `descentmtb-1.0.0-alpha-m24.jar`
SHA-256: `9a2fc24d7939bf1f3bd1f06b6f2caa95676226007035d53d2958234268ee423b`

- **Textury:** všechny bloky a předměty přepracované ve vanilla stylu (stínované palety, žádné ploché výplně); lopata má pravý Minecraft stick.
- **Cedule tratí:** masivnější jako skutečné trailové cedule – tlustá deska s rámem a šrouby, hranatý sloupek se stříškou, správně namapované textury.

Ověřeno jen sestavením; ve hře zatím neověřeno.

## 1.0.0-alpha-m23 (milník 23)

Soubor: `descentmtb-1.0.0-alpha-m23.jar`
SHA-256: `e91206f0495ac61621b73326e8f8c4d336cf0e25d4ce46036a0b4c8e6c6442dc`

- **Terén tratí:** svahy přes víc bloků nad sebou už nemají vodorovné „poličky“ a švy na hranicích bloků – plocha se na hranici bloku přesně ořízne.

Ověřeno jen sestavením; ve hře zatím neověřeno.

## 1.0.0-alpha-m22 (milník 22)

Soubor: `descentmtb-1.0.0-alpha-m22.jar`
SHA-256: `9f6043b9536cea9932b0462da713f8ab224172bc63dd06290d6b5b59a56cfa16`

- **Motorky:** v inventáři 3D model jako kola (obarvený podle úprav), výrazně mírnější pády, plynulá kamera z helmy při backflipu (i u kol).
- **Trail mapa:** víc rámečků vedle sebe = jedna velká mapa na zdi; v ruce ukazuje všechny traily, na zdi jen ty přidané kliknutím na cedule.
- **Zvuky:** pryč kovové cinkání kola při pádu/Heelclickeru.
- **Textury:** nová ušlapaná trailová hlína a trailová prkna ve vanilla stylu.

Ověřeno jen sestavením a unit testy; ve hře zatím neověřeno.

## 1.0.0-alpha-m21 (milník 21)

Soubor: `descentmtb-1.0.0-alpha-m21.jar`
SHA-256: `2ca62a7e54b526e1b7db56d4da32390315969758b919628c5c3e0c757d51fe37`

- **Aktualizace ze hry:** tlačítko „MTB aktualizace“ v hlavním menu a v pauze – zkontroluje GitHub Releases, stáhne novou verzi a po zavření hry ji sama nainstaluje.
- **Rámy:** oprava orientace příčky zadních vzpěr u hardtailu (geometrický checker 0 chyb).

Ověřeno jen sestavením; ve hře zatím neověřeno.

## 1.0.0-alpha-m20 (milník 20)

Soubor: `descentmtb-1.0.0-alpha-m20.jar`
SHA-256: `8516437eaa23adf1d078f5a21cdf0d3a1b0bb3504ada668ee3d1b95ac2354cfd`

- **Motorky:** motokrosová motorka 250F a pitbike 125 (motor s točivým momentem na zadním kole, řazení, omezovač, power wheelie, protáčení kola a roost, syntetizovaný zvuk motoru).
- **Stojan:** úpravy motorek – barvy dílů, zadní rozeta, závodní výfuk, tvrdost odpružení, startovní číslo.
- **Mapa tratí:** terén ze skutečného světa, zoom a posun, tooltipy tratí, šipka hráče, výškový profil s křížem, osobní rekordy.
- **Pocit z jízdy:** volný rozhled z helmy, sklon kamery podle terénu, otřesy při dopadu, smyky zadního kola, plynulé řetězení triků.
- **Audio:** spadlá sluchátka už nepouští hudbu z Windows do reproduktorů, ztlumení světa sluchátky, opravy úniků paměti a pádů u zachytávání zvuku.
- **Fyzika:** voda brzdí a nadnáší, přistání na couvačku, nové kolizní sondy, bezpečnější sesedání.
- **Textury:** nová udusaná trailová hlína a prkna s hřebíky jako výchozí materiály.

Testováno jen unit testy (436 zelených); ve hře zatím neověřeno.
