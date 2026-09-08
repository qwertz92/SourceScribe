# Lernprotokoll für die Wiederaufnahme

Stand: 8. September 2026. Fehlversuche und Korrekturen im
[Integrationsbericht](reports/2026-09-07-integration.md) und
[Previewbericht](reports/2026-09-08-preview.md); hier wiederverwendbare Folgerungen.

- **Build ist kein Laufzeitnachweis.** Gerät, APK-Hash und tatsächlichen Pfad nennen.
  Fixture, echte Quelle und Provideraufruf unterscheiden. 185 Runnerfälle mit fünf
  Skips bedeuten 180 bestandene Tests.
- **UI am Bild beurteilen.** Label-/Wertabstände, Buttonabstände, Proportionen,
  stabile Dialoge und 200-%-Schrift betrachten. Zusammengehörige Korrekturen und
  statischen Review bündeln, einmal bauen, betroffene Pfade prüfen. Scrollen
  widerlegte einen vermeintlich abgeschnittenen Viewer; nicht jeden Verdacht umbauen.
- **TalkBack ist hörbar.** Semantiktest und gebundener Dienst beweisen keine
  vollständige Bedienung. ADB/UIAutomation kann die InputFilter-Kette umgehen.
  Eingabeereignis ist kein nachgewiesener Fokuswechsel. Tests ankündigen und
  Ausgangswerte anschließend exakt restaurieren.
- **Asynchrone UI abwarten.** Trackwahl/Start erst nach beendeter Quellprüfung.
  Der r75-Abbruch war verfrühte Testautomation, kein App-Bug.
- **Prozessabbruch beweisen.** `am kill` beendete den gebundenen Prozess nicht.
  Nur eigene identifizierte PID kontrolliert beenden und Verschwinden prüfen.
  r78 wurde nach dem 100-Sekunden-Fenster erfolgreich wieder aufgenommen; früher
  Timeout bleibt FAIL, späterer Beleg gilt nur für tatsächlich erfasste Daten.
  Dozing ist nicht zwingend ein Fehler beim Display-Aus-Test.
- **Offizielle SDK-Werkzeuge verwenden.** `aapt2` löst das Backup-Resource-Mapping
  auf, `apksigner` prüft die Signatur. Nicht im PATH bedeutet nicht nicht vorhanden.
  APK-Hash vor/nach Audit vergleichen. Native ZIP-Payloads und tatsächlich verwendete
  Ersatzassets mitprüfen; ein großer selbstgeschriebener Parser ist kein alleiniger Gate.
- **JSON nicht als andere Sprache einsetzen.** Androids `JSONObject.quote` lieferte
  maskierte Slashes; direkt als Python-Literal eingesetzt entstanden falsche
  Testpfade. Daten mit JSON lesen statt Sprachen verschachteln.
- **CI-Pfade und Fehlerausgaben explizit halten.** AVD-Erzeugung und Emulator
  brauchen dasselbe Verzeichnis. API-37-Emulator erhöhte 2 auf 4 GiB RAM; nun
  ausdrücklich gesetzt. Nach erfolgreichem Boot JUnit/UTP lesen, nicht jede
  nachfolgende Störung als Boot-Timeout behandeln.
- **Last begrenzen.** WSL-OOM war belegt, 16-GiB-Swap ist inzwischen aktiv.
  Ein Buildworker, 2-GiB-Heap, große Caches auf dem Projektlaufwerk. Mehr Swap
  ersetzt keine Begrenzung paralleler Last.
- **Reviews eng und unabhängig halten.** Luna fand echte Grenzfälle, aber auch
  widerlegte Lock-/Cursor-/Layoutverdachte. Datei, Voraussetzung und reproduzierbare
  Abweichung verlangen und verifizieren. Vage Audits produzierten zu große
  Hilfswerkzeuge. Kleine Dinge direkt erledigen, Sol für klare größere Pakete,
  Astra für UI/Integration.
- **Handoff aktuell halten.** Keine widersprüchlichen Pausen aneinanderhängen;
  Historie gehört in Git/Berichte. SDK, Rohlogs, private Keys und Appdaten reisen
  nicht automatisch mit. Restabnahme ist kein rein kosmetischer Aufwand.
