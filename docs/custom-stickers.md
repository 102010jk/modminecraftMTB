# Vlastní PNG a textové samolepky

Milník 05 hledá PNG automaticky při načtení nebo obnovení resource packů. Není potřeba přidávat položku do Java enumu. Podporuje oba adresáře `textures/sticker/` a `textures/stickers/`, libovolný namespace a podadresáře. Doporučené jsou průhledné obrázky 32×32 nebo 64×64; maximum je 1024×1024.

Pro práci ve zdrojích vlož PNG do:

`C:/MTBMod/src/main/resources/assets/descentmtb/textures/stickers/moje_logo.png`

Změna zdrojového PNG nezmění už sestavený JAR. Sestav nový JAR, nebo použij resource pack v instanci Minecraftu:

```text
resourcepacks/moje-samolepky/
  pack.mcmeta
  assets/descentmtb/textures/stickers/moje_logo.png
```

Obsah `pack.mcmeta` pro Minecraft 1.21.1:

```json
{"pack":{"pack_format":34,"description":"Moje MTB samolepky"}}
```

Pack zapni v nabídce resource packů. Po změně obrázku použij F3+T. PNG se objeví v paletě dílny; vyber ho a klikni na trubku v bočním náhledu. Načtení probíhá z aktivních packů, takže nahrazení stejného resource ID funguje obvyklou prioritou resource packů.

Sestava a knihovna návrhů ukládají resource ID, nikoli samotná obrazová data. Ostatní hráči potřebují stejný PNG v aktivním resource packu (lze distribuovat serverovým resource packem). Mod soubory PNG neposílá automaticky. Pokud PNG chybí, zůstane uložené ID a vykreslí se růžová kontrolní textura; dílna ukáže upozornění. Po vrácení packu se obrázek obnoví.

Volba **Obtočit kolem trubky** mění rovinnou nálepku na povrch přes čtyři plochy hranaté modelové trubky. Pozice, velikost, otočení, barva a zrcadlení zůstávají editovatelné. Na dělené horní a spodní trubce používají sousední segmenty společnou délkovou souřadnici. Posun napříč posouvá obvodový spoj. Volba strany u vzpěr a vidlice vybírá větev; u jediné rámové trubky wrap obepíná všechny plochy. Nálepka se ořízne na koncích trubky.

Textové nálepky se vytvářejí zvláštním tlačítkem. Textové pole patří pouze k nim; vymazání textu nepřevádí nálepku na obrázek. Text používá tutéž plochu a měřítko jako PNG. Písmo rasterizuje klient, takže jeho drobný vzhled může záviset na systému. Text a nastavení se synchronizují jako součást kola.

Ověření této etapy: pouze kompilace a sestavení. Vykreslení ve hře nebylo automaticky testováno.
