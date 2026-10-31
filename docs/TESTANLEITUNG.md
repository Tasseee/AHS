# Manuelle Testanleitung (HugoSMP)

**Status: Live-Test auf HugoSMP NICHT durchgeführt.** Alles unten ist ungetestet und muss von dir geprüft werden.
Nutze zuerst eine lokale Testwelt, dann ein billiges Item. Keine teuren Angebote.

## 0. Vorab
* Regeln von HugoSMP lesen: Automatisierung/Makros können verboten sein und zu Strafen führen. Das ist dein Risiko.
* Auto-Kauf bleibt AUS bis Schritt 5.

## 1. Start
1. `/ahs` → Dashboard erscheint, Status „Pausiert“.
2. Dashboard → Item-Filter → „Filter hinzufügen“. Billiges Item wählen (z. B. „Aus Hand übernehmen“), Max-Preis setzen.

## 2. AH-Erkennung
1. Start drücken, Dashboard schließen, `/ah` öffnen.
2. HUD-Zeile zeigt „Suche aktiv …“. Steht dort „nicht als Auction House erkannt“: Dashboard → Einstellungen →
   „Fenstertitel als AH übernehmen“.

## 3. Lesen (Auto-Kauf AUS)
1. Ein Angebot deines Items sollte als „Gefunden/Prüfen“ erscheinen, mit Menge, Gesamt- und Stückpreis.
2. Stimmen die Zahlen nicht mit dem Tooltip überein → **stopp**, Protokoll prüfen, `priceLineHints` anpassen.
3. Preis nicht lesbar → muss „Fehler/Preis unklar“ zeigen und nie kaufen.

## 4. Kaufmenü beobachten (immer noch Auto-Kauf AUS)
Klicke selbst ein Angebot an und notiere Titel, Buy-Button-Name/Lore und ob der Preis im Menü nochmals steht.
Trage passende `buyButtonHints`/`confirmHints` ein. Steht kein Preis im Menü, bleibt „Preis im Kaufmenü verlangen“ AN
(dann kauft die Mod nicht automatisch).

## 5. Erster Auto-Kauf
1. Einstellungen: Auto-Kauf AN; im Filter Auto-Kauf AN; Preis-Limit sehr niedrig.
2. Ein einziges günstiges Angebot. Protokoll beobachten: „Kaufaktion ausgelöst“ ist **kein** Erfolg;
   erst „Kauf nachweislich abgeschlossen“ (Inventar +N) zählt.

## 6. Sicherheitsprüfungen
* Angebot > 10.000.000: Mod klickt nicht, pausiert, Server-Menü bleibt offen.
* Zwei Buy-Buttons erkannt: Abbruch.
* Während des Ablaufs Fenster schließen: Abbruch, kein erneuter Klick.
* Pause während Vorgang: nichts wird gesendet.
