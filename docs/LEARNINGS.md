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
  `free -m` und `dmesg` lesen und die Gradle-Aufrufe teilen. In Runde 21 zeigte sich dieselbe Meldung von `dmesg`, eine
  gescheiterte Seitenanforderung der Ordnung 4 in `p9pdu_readf`, als `Could not read directory path` in
  `mergeDebugAndroidTestResources`; der nächste Build derselben Aufgabe lief durch.
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
- **Wer einen Commit aus Textersetzungen baut, trägt auch den Dateimodus ein.** `git update-index --cacheinfo`
  verlangt ihn, und das Stage-Skript der Runde 19 setzte fest 100644 ein. So verlor `tools/check-repository.py` sein
  Ausführungsbit, ohne dass sich eine Zeile änderte; im Diff steht das nur als `old mode 100755` und
  `new mode 100644`. Den Modus aus HEAD übernehmen; `tools/check-repository.py` prüft seit Runde 20 Skripte mit
  Shebang.
- **Eine Datei lässt sich nicht über ein Verzeichnis umbenennen.** `rename(2)` lehnt das ab, laut Handbuch mit
  `EISDIR`, und `Files.move` mit `ATOMIC_MOVE` meldet eine `IOException`. Auf dem Emulator mit API 37 zeigt das
  `aSlotRepairWhoseMetadataCannotFollowFailsWithStorageAndLeavesTheEngineRepaired`, der mit `STORAGE` endet. Ein Test
  kann so die zweite von zwei Umbenennungen gezielt scheitern lassen: Er legt ein Verzeichnis an ihr Ziel.
- **`grep -l` zählt Dateien, in denen eine Zeichenkette steht, keine Aufrufe.** `d994c23` nannte 55 Klassen von
  `bcprov` 1.85.2, die `BigInteger.intValueExact` aufrufen, und der Release-Reviewer der Runde 21 bestätigte die Zahl
  mit `grep -lr intValueExact`. Die Zeichenkette steht auch in Klassen, die gleichnamige Methoden von Bouncy Castle
  selbst aufrufen, und keine Klasse ruft die von `BigInteger` auf. Aufrufe stehen als Methodenverweise im
  Konstantenpool einer Klasse; wer sie zählt, liest diese, mit `javap -c` oder einem kleinen Leser, und prüft den
  Leser an einem Verweis, der sicher vorkommt.
- **Lint vergleicht Versionen mit einem Cache.** `NewerVersionAvailable` liest die neuesten Versionen aus
  `maven-metadata.xml` unter `build/intermediates/lint-cache` jedes Moduls. Lokal stammten sie vom 7. und
  8. September, und so scheiterte vom 11. bis 14. September nur die CI an Bouncy Castle 1.86. Vor einem lokalen
  Lintlauf, der für die CI stehen soll, diesen Cache löschen.
- **Ein Beleg nennt den Code, der lief, nicht nur den HEAD.** Die Gates der Runde 20 trugen `HEAD=b204a47` und liefen
  mit Code, der erst danach als `dc9e6dd` committet wurde; an diesen Code band den Lauf nur der Fingerabdruck
  daneben. Die Gates der Runde 21 prüfen, dass der Baum HEAD plus genau die benannten Korrekturen ist, und schreiben
  das in ihre Ausgabe.
- **Compose gibt vom Inhalt eines Passwortfelds nur Punkte an die Accessibility, in der Fassung, die die App
  bündelt.** Auf `emulator-5556` mit API 37 und `compose-bom` 2026.09.00 erschien Text, den `adb shell input text` in
  das Schlüsselfeld der Einstellungen tippte, im Dump von `uiautomator` als Punkte, mit `password=true`, und nirgends
  im Klartext. `uiautomator` liest denselben Baum wie `UiAutomation.rootInActiveWindow`. Ein Reviewer der Runde 22
  hatte aus dem Quelltext von Compose auf GitHub das Gegenteil geschlossen, ohne eine Fassung zu nennen. Ein Feld ohne
  Maskierung gibt seinen Inhalt dagegen im Klartext weiter; das zeigt der Test aus Runde 22, bevor er die Meldung
  prüft.
- **ASCII-Armor endet für Bouncy Castle an seiner Fußzeile, und die Fußzeile an ihrem Zeilenumbruch.**
  `PGPUtil.getDecoderStream` liest Armor über `ArmoredInputStream`, und in 1.86 liefert dieser Strom nach
  `-----END PGP SIGNATURE-----` nichts mehr, auch wenn dahinter ein zweiter Block steht;
  `PGPObjectFactory.nextObject()` gibt dann `null` zurück. Die Fußzeile selbst liest er bis zu ihrem Zeilenumbruch.
  Fehlt dieser, verschwindet angehängter Text mit ihr, und auch hinter dem Parser bleibt nichts übrig. Wer genau eine
  Signatur verlangt, prüft deshalb die rohen Bytes hinter der Fußzeile, nicht nur, was der Parser nicht gelesen hat.
- **Ein grüner CI-Lauf beendet keinen zeitweisen Fehler.** `ChoiceAccessibilityTest` scheiterte in drei CI-Läufen,
  bestand am Stand `7d9ce41`, ohne dass sich seine Bedingung oder der Code der App geändert hatte, und scheiterte am
  Stand `17bc156` wieder. Einen Punkt in DEFECTS schließt erst die gefundene Ursache. Gezeigt hat sie eine Meldung,
  die den Zustand des gesuchten Elements nennt: Es war da, aber gesperrt, weil die App noch startete.
- **Leitet Git Bash die Ausgabe von `wsl.exe` in eine Datei, überschreibt stderr den Anfang von stdout.** Am
  14. September auf HomeBase gezeigt: Nach `wsl.exe -e bash -lc '…' > datei 2>&1` stand in der Datei die Zeile von
  stderr an der Stelle der ersten Zeile von stdout, und von dieser blieb nur ihr Rest. `wsl.exe` schreibt beide
  Ströme von getrennten Positionen aus, die beim Start des Aufrufs am selben Punkt stehen; was vorher in der Datei
  stand, bleibt. Mit `2>&1 | cat > datei` oder einer Umleitung innerhalb von WSL bleibt alles in der Reihenfolge,
  in der es geschrieben wurde.
- **Ein Test, der eine Bedienung prüft, wartet zuerst, bis die App sie freigibt.** `MainViewModel` führt beim Start
  eine exklusive Aktion aus. Solange sie läuft, sperrt `state.busy` unter anderem die Sprachwahl, und `MainActivity`
  zeigt einen Fortschrittsbalken; in der CI dauerte das länger als 25 Sekunden. In der Accessibility erscheint der
  Balken als `android.widget.ProgressBar`: Auf `emulator-5556` fand ihn `ChoiceAccessibilityTest` noch nach 150 Sekunden, als die Aktion beim Start 600 Sekunden länger dauerte, und nicht mehr, sobald sie beendet war. Seit Runde 23 wartet der Test, bis keiner mehr zu sehen ist.
