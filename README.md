# HugoSniper

Client-seitige Fabric-Mod für **Minecraft 1.21.11** (Java Edition): Dashboard (`/ahs`), HUD und Filter für das
HugoSMP-Auction-House. Der automatische Kauf ist **standardmäßig AUS**, hat eine **harte Grenze von 10.000.000** und
ist ungetestet gegen den echten Server (siehe `docs/TESTANLEITUNG.md`).

## Versionen

| Komponente | Version |
|---|---|
| Minecraft | 1.21.11 |
| Java | 21 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.141.3+1.21.11 |
| Fabric Loom | 1.16.3 |
| Gradle | 9.4.1 |
| Mappings | Mojang (official) |

## JAR bauen

1. **JDK 21** installieren (z. B. Temurin 21) und prüfen: `java -version`.
2. ZIP entpacken.
3. Terminal im Projektordner öffnen.
4. Windows: `gradlew.bat build` — Linux/macOS: `./gradlew build`
   (Beim ersten Start lädt das Skript `gradle-wrapper.jar` nach; Internet nötig. Alternativ mit installiertem Gradle:
   `gradle wrapper --gradle-version 9.4.1`.)
5. Die fertige JAR liegt in `build/libs/` (`hugo_sniper-1.0.0.jar`, **nicht** die `-sources.jar`).
6. JAR in den Ordner `.minecraft/mods/` kopieren.
7. Mit Fabric Loader 0.18.x und Fabric API für 1.21.11 starten.
8. Im Spiel `/ahs` eingeben.
9. Filter anlegen und **zuerst ohne automatischen Kauf** testen.

Nur Tests: `./gradlew test`

## Befehle

`/ahs` Dashboard · `/ahs start` · `/ahs pause` · `/ahs scan` · `/ahs hud` · `/ahs status`.
Der Server-Befehl `/ah` bleibt unberührt.

## So arbeitet die Mod

* **Passiv:** HugoSniper liest nur das Fenster, das du mit `/ah` selbst geöffnet hast. Dashboard und AH-Fenster sind
  getrennte Bildschirme: Dashboard → Start → schließen → `/ah` öffnen. Das HUD zeigt den Live-Status.
* **Identität:** Entscheidungen basieren auf der echten Registry-ID des ItemStacks, nie auf dem Anzeigenamen.
* **Preis:** nur Lore-Zeilen mit Preis-Hinweiswort (`price`, `preis`, `$` …), genau eine eindeutige ganze Zahl.
  Unklar → übersprungen. Stückpreis wird aufgerundet.
  Kurzformen (`100k`, `100m`, `1.5M`, `2.25b`) werden unterstützt. Der Server rundet sie, deshalb rechnet die Mod
  mit der **oberen Grenze** (`100k` → bis 100.500, `10M` → bis 10.500.000). Das ist absichtlich konservativ: Ein `10M`-Angebot
  gilt als über der 10-Mio-Grenze und wird nie automatisch gekauft. Angezeigte Preise im Dashboard sind dann diese Obergrenze.
* **Kauf-Ablauf:** Zustandsmaschine; pro Vorgang höchstens ein Buy-Klick; Erfolg nur bei Inventar-Nachweis.
* **Über 10.000.000:** Kein Klick; das Server-Menü bleibt offen, die Mod pausiert.
* **Refresh:** Standard passiv. Optional „Server-Refresh-Klick“ nur bei genau einem passenden Button.

## Wichtige Einstellungen (config/hugo_sniper.json)

`auctionTitleHints`, `buyButtonHints`, `confirmHints`, `priceLineHints`, `refreshHints` enthalten Hinweiswörter.
Da das echte HugoSMP-GUI nicht bekannt ist, müssen sie ggf. angepasst werden (Dashboard → Einstellungen;
„Fenstertitel als AH übernehmen“ übernimmt den zuletzt gesehenen Titel).

## Bekannte Unsicherheiten beim Kompilieren

Der Minecraft-nahe Code (`gui/`, `hud/`, `command/`, `auction/ContainerReader`, `auction/SniperEngine`) konnte bei der
Erstellung **nicht gegen die echte 1.21.11-API kompiliert werden** (kein Netzwerk). Falls der Build meckert, sind das
die wahrscheinlichsten Stellen:

* `MultiPlayerGameMode.handleInventoryMouseClick(...)` in `SniperEngine.click`
* `Screen.renderBackground(...)` und `mouseScrolled(...)` Signaturen in `ThemedScreen`/`DashboardScreen`
* `HudRenderCallback` (veraltet; Alternative: `HudElementRegistry`)
* `Holder.getRegisteredName()` in `ContainerReader.addEnchants`
* `AbstractWidget.setAlpha`/`isHovered` in `ThemedScreen`
