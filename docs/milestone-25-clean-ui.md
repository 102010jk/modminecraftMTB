# Milník 25 — čistší rozhraní a projekt

## Změny

Devět MTB obrazovek sdílí jednoduché tmavé pozadí místo několika různých variant; hotový obsah se nerozmazává. Společný `UiTheme` poskytuje karty, barvy, zkrácení textu s výpustkou a omezené zalamování. Existující editační akce a ochrana neuložených změn workshopu zůstávají zachované.

Nabídka trailové lopaty přizpůsobuje šířku kategorií oknu. Popisky režimů nepřetékají přes karty a delší popisy jsou pod prstencem. Tab přepíná kategorii, šipky vybírají režim a Enter potvrzuje. Krátké stisknutí klávesy již nezavírá klikací nabídku při následném uvolnění. Podržení a uvolnění funguje i pro klávesu navázanou na tlačítko myši.

Audio nastavení používá posuvníky hlasitosti 0–200 % a dosahu 10–32 m. Dosah a desky se zobrazují jen pro reproduktor; sluchátka je nepotřebují. Volby se použijí při výběru zdroje, což vysvětlují tooltipy. Aplikace mají zkrácené názvy s celým názvem v tooltipu. Předchozí/další stránka je omezena na skutečné stránky. Pozdě dokončený starší refresh nesmí přepsat novější seznam. Chyby a prázdný seznam mají omezený zalomený text.

Aktualizační obrazovka má jednotnou kartu, zalomené hlášky/poznámky a průběh stahování; její síťová a instalační logika se nemění. Nadpisy servisních obrazovek respektují šířku stavu uložení.

HUD nástrojů je nad hotbarem, s omezenou šířkou. Výchozí kompaktní varianta zobrazí režim, jeho nastavení a klávesu nabídky; Shift dočasně rozbalí popis a nápovědu. `hud.showSpeed`, `showAirTime`, `showTrailHints` a `compactTrailHints` v client configu umožňují další zjednodušení. Časomíra omezuje dlouhé názvy a banner triku respektuje její místo. Tyto HUD prvky se nekreslí při otevřeném menu.

Kreativní předměty jsou ve třech kartách: kola/stojan, stavění tratí, vybavení. Každý dříve vystavený předmět je zachovaný a je zařazen jednou; ID předmětů, bloků ani jejich ukládání se nemění.

Aktuální README nahrazuje historické popisy skutečnými funkcemi a klávesami. `PLAN.md`, `PLAN_SONNET.md` a staré předání jsou v `docs/archive`; tři volné kontrolní PNG jsou v `tools/preview/archive`. Historie i milníkové JARy zůstávají zachované. Addon lanovek nebyl změněn.

## Ověření a omezení

Sestavení Java 21 `assemble` úspěšné. Byly zkontrolované rozdíly a obsah JARu. Nebyla spuštěna hra, screenshoty, vizuální náhledy, automatické testy ani nezávislá review. Vzhled, ovládání a rozložení ve skutečném Minecraft klientu tedy nejsou ověřené. Fyzika, modely a textury nebyly tímto milníkem přepisované.
