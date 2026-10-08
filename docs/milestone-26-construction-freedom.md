# Milník 26 — volná stavba a méně zařízení

## 1.0.0-alpha-m26 (milník 26)

Soubor: `descentmtb-1.0.0-alpha-m26.jar`
SHA-256: `c11894186facfab27d274c7debdde204cae110fc73e04cad78f77d126aadd93b`

- Generátor skoků: odstraněné původní stropy 12 m délky, 7 m šířky, 4 m výšky, 8 m plošiny a 12 m dopadu. Rozměry lze přímo napsat; odraz podporuje úhel 1–89°. Zůstávají pouze technické rozměry Minecraft světa.
- Propojení ramp bez stropu 32 m délky a 8 m převýšení. Skoky a propojené rampy neblokuje `trails.maxBlocks`; funguje ochrana staveb, načtené chunky, survival materiál i undo.
- Náhled má počet vzorků podle obrazovky, ne podle délky stavby. Strmé profily zachovávají původní rohové výšky ve všech vrstvách, včetně ukládání.
- Jedna společná kreativní záložka **Descent MTB** pro všechno vybavení a stavění.
- Odstraněné boomboxy, sluchátka, zachytávání audio aplikací, související sítě, recepty, nastavení, mixin a klávesy.
- Odstraněné trhací fólie a špinění výhledu. Bláto na samotném kole a jeho mytí zůstávají.

Stará zařízení a fólie již nejsou registrované a ze starých světů se nepřenášejí; kola, jejich úpravy a GPS zůstávají. Síťový protokol je 11, server a klient musí používat stejnou verzi.

Ověřeno pouze `assemble` a obsah výsledného JARu. Bez hry, screenshotů, unit testů, poslechu, multiplayer ověření a bez AgentBridge či nezávislé agentové review.
