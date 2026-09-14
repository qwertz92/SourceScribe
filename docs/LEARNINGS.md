# Lernprotokoll für die Wiederaufnahme

Stand: 14. September 2026. Fehlversuche und Korrekturen im
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
- **Dexing-Fehler mit kleingeschriebenem Projektpfad ist veralteter Gradle-Zustand.**
  `DexingNoClasspathTransform` meldete `The given file '/mnt/c/users/.../projects/sourcescribe/...'
  is located outside the root directory '/mnt/c/Users/.../Projects/SourceScribe/...'`. Der Unterschied
  ist ausschliesslich die Gross-/Kleinschreibung des Pfads, nicht die genannte Klasse: zwei Laeufe
  nannten zwei verschiedene, teils unveraenderte Klassen. Behoben durch `rm -rf core/build` aus WSL
  heraus und `--no-watch-fs`. Nicht nach der genannten Klasse suchen, sondern den Buildordner leeren.
- **Nicht waehrend eines laufenden Builds im Repository editieren.** Eine Zeichenkette, die nach der
  R-Generierung eingefuegt wurde, liess `compileDebugKotlin` an einer `Unresolved reference` scheitern,
  die es zu diesem Zeitpunkt gar nicht mehr gab. Aenderungen sammeln und zwischen zwei Laeufen anwenden.
- **„Internal error: Unexpected lint invalid arguments“ kann Speichermangel sein.** In Runde 16 endete ein Lintlauf
  so; im Kernelprotokoll von WSL stand eine fehlgeschlagene Seitenanforderung beim Lesen eines Verzeichnisses über
  9p. Ein zweiter Lauf, nachdem der Speicher wieder frei war, lief durch. Nicht den Code verdächtigen, sondern
  `free -m` und `dmesg` lesen und die Gradle-Aufrufe teilen.
- **Lint vor jedem Commit einer UI-Änderung.** `1b2dc45` ging mit einem Lintfehler (`ModifierParameter`) in den
  Baum, weil Lint erst im Gate danach lief.
- **K2 zieht Smart-Casts durch lokale Boolean-`val`s.** Stammt `existingHealthy` aus `existing?.healthy == true`,
  gilt `existing` hinter `existingHealthy && …` als nicht null, und ein `existing?.bundled` dort ist die Warnung
  „Unnecessary safe call“, mit `allWarningsAsErrors` ein Buildfehler. Die Bedingung so ordnen, dass der sichere
  Aufruf vor der Prüfung steht.
- **Keine Instrumentierung neben einem Gradle-Build.** In Runde 17 scheiterte ein Test der Gegenprobe an
  `native_runtime:TIMED_OUT`, dem Selbsttest der Laufzeit mit 30 Sekunden, während daneben ein Build in WSL anlief;
  mit derselben Test-APK ohne Build bestand er. Bewiesen ist die Ursache nicht, aber eine Gegenprobe, die an Last
  scheitert, belegt nichts.
- **`preferencesDataStore` bindet einmal je Prozess.** Der Delegat legt einen DataStore an, auf den Dateien des
  Kontexts, mit dem er zuerst benutzt wird, und jeder spätere Kontext bekommt denselben. Wer einen Store je Kontext
  braucht, baut ihn mit `PreferenceDataStoreFactory` und hält je Datei genau einen; DataStore verbietet zwei.
- **Eine Kontextattrappe hält die Verträge des Originals.** `Context.getFilesDir` legt sein Verzeichnis an. Eine
  Attrappe, die das nicht tat, ließ `File.usableSpace` 0 melden, und die Speicherprüfung sah eine volle Platte, in
  sieben Tests zugleich.
- **`Context.deleteDatabase` entfernt keine `.lck`-Datei.** Laut AOSP-Quelltext von
  `SQLiteDatabase.deleteDatabase` löscht es die Datenbank, `-journal`, `-shm`, `-wal`, eine Prüfdatei des
  Frameworks und `-mj`-Dateien, nicht aber die Sperrdatei `<name>.lck`, die neben den Datenbanken der
  Migrationstests lag. In der Gegenprobe der Runde 18 blieben genau diese zwei Dateien zurück, als nur ihr eigenes
  Löschen fehlte. Wer Testdatenbanken aufräumt, löscht sie selbst und prüft danach, dass nichts mit ihrem Namen
  bleibt.
- **Ein Prüfskript trägt sein Urteil im Exitcode.** `r16_typing.py` meldete in Runde 17 einen abweichenden Fall und
  endete mit 0; wer nur den Exitcode liest, hätte den Lauf als bestanden gezählt.
- **Ein Reviewerfund über das Verhalten einer Bibliothek nennt ihre Version.** `ExternalResource` rief in JUnit 4.12
  `after()` in einem `finally`, dessen Ausnahme die des Tests ersetzte. In 4.13.2, das dieses Projekt nutzt, sammelt
  die Regel beide und wirft sie zusammen als `MultipleFailureException`. In Runde 19 beschrieb ein Reviewer die alte
  Fassung.
- **Eine Datei, die andere über ihren Pfad öffnen, wird mit einer Umbenennung ersetzt, nicht mit Entfernen und
  Umbenennen.** `Files.move` mit `ATOMIC_MOVE` und `REPLACE_EXISTING` ruft auf demselben Dateisystem `rename(2)`: Der
  Eintrag nennt davor die alte und danach die neue Datei, nie keine, und eine symbolische Verknüpfung an seiner Stelle
  wird selbst ersetzt, nicht ihr Ziel. Ein Verzeichnis an seiner Stelle lässt sich so nicht ersetzen.
- **Eine Zeilennummer in einem Dokument veraltet mit der nächsten Änderung darüber.** In Runde 19 zeigten acht von
  dreizehn Zeilenangaben in `docs/DEFECTS.md` auf anderen Code, eine davon auf `rollback` statt auf die Prüfung eines
  Hashes. Eine Stelle nennt die Funktion oder zitiert den Ausdruck; `tools/check-repository.py` weist Zeilennummern
  in `docs/` ab.
