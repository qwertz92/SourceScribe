# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der elften Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Zwölfte Reviewrunde über die Korrekturen der elften

Runden 3 bis 11 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt. Runde 11 hat
das Feld erweitert: Ihr schwerster Fund lag nicht im Code, sondern in der Messung — zehn Runden lang war
eine ganze Testsuite nicht in der Zahl enthalten, die als Beleg genannt wurde. Die Commits der elften Runde
sind `40f5894` und `90c3f94` sowie die beiden dieser Nachbereitung; einzeln benennen, nicht als Bereich,
weil `a..b` den Anfangscommit auslässt.

Zwei rein lesende Reviewer zuerst, der verändernde danach allein — das hat in Runde 11 funktioniert und
bleibt so. Fünf Lehren gehören in den Auftrag:

- Ein Fund, den der Reviewer nicht ausführen konnte, gilt erst nach einer Gegenprobe als lebender Fehler.
- **Eine Gegenprobe muss jede Stelle abschalten, die eine Regel durchsetzt, nicht die erstbeste.** In Runde
  11 blieb ein neuer Test grün, nachdem zwei von drei Durchsetzungsstellen deaktiviert waren — und genau
  das hat die dritte, vorher unbekannte, sichtbar gemacht.
- Jede Zahl wird nachgezählt und nennt den Stand, für den sie gilt.
- Jeder Symbolname in einem Auftrag wird vorher gegen den Baum geprüft.
- Ein Reviewer, der für Gegenproben Dateien verändert, läuft nicht neben einem, der liest.

Die Jagdliste:

- **`StatedNumbersTest` ist neu und behauptet, jede Zahl dieses Moduls zu nennen. Stimmt das?** Welche
  nicht-`private` Konstante in `core` fehlt in der Liste? Und die schärfere Frage dahinter: Der Test
  schützt gegen eine *geänderte* Zahl, aber nichts hält jemanden davon ab, eine *neue* Konstante
  hinzuzufügen und nicht einzutragen. Gibt es dafür eine Schranke, die nicht von Sorgfalt abhängt?
- **Ist eine der dort festgenagelten Zahlen die falsche?** Festnageln hält eine Zahl fest, auch wenn sie von
  Anfang an falsch war. Die acht Preise und die Anbietergrenzen sind gegen die Seiten der drei Anbieter
  prüfbar; `MAX_UPLOAD_BYTES` bei Groq und OpenAI, `MAX_PROMPT_BYTES` und die Stichtage vom 7. September
  sind seither von niemandem nachgelesen worden.
- **Die Module `app` und `extractor` haben keine solche Liste.** Welche Zahlen behaupten sie, und hängt an
  einer davon ein Test, der mit ihr mitwandert? Runde 11 hat nur `core` durchsucht.
- **Weiter beim Messen selbst.** Neun Instrumentierungsargumente sind bekannt, sieben davon echte
  Schranken. Gibt es eine Prüfung, die aus einem anderen Grund nicht läuft — eine Gradle-Aufgabe, die in
  keinem Ablauf steht, eine Lint-Regel, die es gar nicht bis zum Bericht schafft, ein Testverzeichnis ohne
  Runner? Und: Sagt `tools/check-repository.py` etwas, das es nicht wirklich prüft?
- **Die zwei neuen Archivtests.** Sie bauen ihre Eingabe aus den Produktionswerten, was hier richtig ist.
  Aber prüfen sie, was ihr Name sagt? Schalte jede Durchsetzungsstelle einzeln ab und sieh nach, welche
  greift — bei der Eintragszahl waren es drei, und die wirksame war nicht die offensichtliche.
- **`Source.originalLanguage` hat kein Gegenstück zu `AudioTrack.languageRefused`.** Ein verworfener Wert
  sieht dort wie ein nie genannter aus. Geprüft wurde, dass `TrackSelection.captions` dadurch nur sortiert
  und nichts still entscheidet; prüfe es nach und such, ob sonst jemand dieses Feld liest.
- Und die Doku: Jede Zahl der Runde-11-Passage nachzählen, mit dem Stand, für den sie gilt.

### 2. Warncodes lesbar machen (DEFECTS 9) — erledigt

Umgesetzt am 11. September 2026. Dreizehn Sätze statt 59 Codefamilien, Zuordnung in
`core/.../TranscriptWarnings.kt`, rohe Codes unter den Details. Am Gerät angesehen und die
Sprungfreiheit der Kopfzeilen über drei Suchzustände nachgemessen.

### 3. Codes ohne eigenen Text (DEFECTS 4, mittel)

Mindestens 43 Codes fallen in den `else`-Zweig von `messageText`. **Sie bedeuten nicht alle dasselbe** —
diese Annahme stand bis Runde 8 hier und in DEFECTS 4 und ist dort widerlegt: Ein guter Teil sind
gewöhnliche Betriebsausgänge wie `INTERRUPTED`, `REMOTE_TIMEOUT` oder `NO_TRANSCRIPT`, für die ein Satz
über einen fehlgeschlagenen internen Prüfschritt schlicht falsch wäre. Ein gemeinsamer Satz für den ganzen
Zweig ist deshalb keine Lösung, sondern derselbe Fehler, den diese Schleife sonst jagt.

Was es stattdessen braucht, steht ausführlich in DEFECTS 4: eine ausdrücklich aufgezählte Liste für die
Integritätscodes statt eines Namensmusters, eigene Texte für die Betriebsausgänge, und vorher eine neue
Auszählung — die vorhandene ist nachweislich unvollständig, weil `JobCoordinator` auch die Codes aus
`CaptionParser`, `ArtifactFiles` und `StorageBudget` durchreicht. Lies den Punkt dort, bevor du hier
anfängst; diese Zusammenfassung ersetzt ihn nicht.

### 4. Kleine offene Punkte, in dieser Reihenfolge

- DEFECTS 13: Eigener Fehlercode dafür, dass ein Exportdokument nicht geprüft werden konnte, statt
  denselben Grund wie für ein nachweislich gelöschtes zu melden.
- DEFECTS 10: Eigener Text für `AUDIO_INVALID_INPUT` oder Nachweis, dass er nie beim Nutzer ankommt.

### 5. Was ohne den Nutzer nicht weitergeht

- DEFECTS 2: Nachfragen, was bei der unteren Schaltfläche mit mehr Platz gemeint war — Höhe, Abstand
  zum Navigationsbalken oder Daumenreichweite. Ohne das ist jede weitere Änderung geraten.
- DEFECTS 1: Querformat und ein zweites Gerät für den gemeldeten Scrollfehler. Am Emulator ist er
  nicht reproduzierbar; ob er auf dem Gerät des Nutzers noch auftritt, ist ungeprüft.
- DEFECTS 3: Die Aussage zu AssemblyAI-Regionen gegen die aktuelle Anbieterdokumentation prüfen.
- Die drei Live-Providerläufe, ARM64 und TalkBack bleiben blockiert wie in der Tabelle unten, und mit
  ihnen die sechs Tests, die eine echte Quelle oder ein echtes Release brauchen.

### Ablauf für die Gates, damit nichts gesucht werden muss

```bash
wsl.exe -e bash -lc "cd /mnt/c/Users/thoma/mystuff/personal/Projects/SourceScribe && bash tools/build-local.sh :core:test :app:lintDebug :app:lintRelease :extractor:lintDebug :extractor:lintRelease"
```

Instrumentierung läuft **nicht** über Gradle aus WSL heraus; der Grund und der gangbare Weg stehen in
[DEFECTS.md](DEFECTS.md) unter den Wartungshinweisen. Kurzfassung: APKs in WSL bauen, unter Windows
installieren, `am instrument` direkt starten. Das eigene Gerät ist `emulator-5556`;
`emulator-5554` gehört dem Nutzer und wird nicht angefasst.

**Es sind zwei Instrumentierungssuiten, nicht eine.** Die Runden 6 bis 10 haben nur die erste ausgeführt
und ihre Zahl als das Gate berichtet; die zweite lief in keiner Runde. Beide gehören dazu:

```bash
wsl.exe -e bash -lc "cd /mnt/c/Users/thoma/mystuff/personal/Projects/SourceScribe && bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest :extractor:assembleDebugAndroidTest"
```

```bash
adb -s emulator-5556 shell am instrument -w -r app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
```

```bash
adb -s emulator-5556 shell am instrument -w -r -e sourcescribeEngineUpdate true app.sourcescribe.extractor.test/androidx.test.runner.AndroidJUnitRunner
```

Das `-e sourcescribeEngineUpdate true` ist nicht optional, auch wenn der Lauf ohne es grün aussieht: Es
schaltet vierzehn Tests von `EngineUpdateManagerTest` frei, die sonst per Annahme übersprungen werden —
Prüfsumme, Slotwechsel, Rückrollung, beschädigter aktiver Slot. Ohne den Schalter meldet die Suite
21 bestanden und 18 übersprungen, mit ihm 35 bestanden und 4 übersprungen. Die verbleibenden vier
brauchen eine echte Quelle beziehungsweise ein echtes Release und bleiben `BLOCKED/NOT_RUN`.

**Die App-Suite hat dieselbe Art Schranke, und vier ihrer sechs Übersprungenen sind ausführbar.**
`ProcessRecoveryTest` stellt den Prozesstod über einen Neustart hinweg nach und verlangt pro Lauf genau
eine Stufe; mit mehreren Stufen gleichzeitig fallen die übrigen drei, sie werden also nicht übersprungen,
sondern rot. Vier einzelne Läufe:

```bash
adb -s emulator-5556 shell am instrument -w -r -e class app.sourcescribe.data.ProcessRecoveryTest#stage1PersistsAcceptedAssemblyRemote -e sourcescribeProcessFixture true -e sourcescribeProcessStage 1 app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
```

und ebenso für `stage2ReopensAndFinalizesWithoutResubmission` mit Stufe `2`,
`stage1PersistsRevokedLocalAudioImport` mit `import-1` und
`stage2ReopensPrivateAudioAfterGrantAndSourceLoss` mit `import-2`.

Die zwei übrigen Auslassungen der App-Suite bleiben mit Absicht liegen: `UiFixtureTest` ist kein Test,
sondern ein Saatgenerator, der synthetische Daten in die echte App-Datenbank des Geräts schreibt, und
`EngineJobPinningTest` braucht `engineProbeSource`, also ein echtes Release. Zusammengerechnet sind damit
von 403 Tests 397 ausgeführt; die sechs übrigen stehen als `BLOCKED/NOT_RUN` in
[DEFECTS.md](DEFECTS.md).

## Restarbeiten nach der ersten persönlichen Preview

Stand: 8. September 2026. Diese Liste konkretisiert die unveränderte Roadmap.
Keine automatische Arbeitsfreigabe nach Preview-Abschluss; Nutzerfeedback abwarten.

| Priorität / Status | Nächster konkreter Schritt | Abschlussnachweis |
|---|---|---|
| 1 — CI, NOT_RUN | Workflow `android.yml` auf `main` einmal gezielt starten, vorbereitete JUnit-/UTP-Ausgabe auswerten. Run 34252821287 scheiterte nach erfolgreichem Boot im Gerätetest; Einzelursache unbekannt. | Ursache reproduziert, enge Korrektur und betroffene Prüfung erfolgreich. Keine Tests/Severities abschwächen. |
| 1 — ARM64, BLOCKED | Physisches Gerät per ADB bereitstellen; API/ABI/Seitengröße messen. P0 Python/TLS/JS/EJS/FFmpeg/Caption/Audio und Update/Rollback prüfen. | Tatsächliche physische ARM64-Laufzeitbelege; Emulation ersetzt diese nicht. |
| 1 — drei Live-Provider, BLOCKED | Nutzer testet selbst oder erteilt je Anbieter Zugang, freigegebene Datei und Kostenrahmen. Mit kurzer eigener Aufnahme starten; Modellzugang, Sprache/Zeiten/Sprecher, Provenienz und Export prüfen. | Je AssemblyAI, OpenAI und Groq ein echter dokumentierter Lauf. Keine Schlüssel/Transkripte veröffentlichen; Modelllisten/Fixtures genügen nicht. |
| 1 — öffentliche APK, BLOCKED | FFmpeg-/x264-/VMAF-/SVT-AV1-Quellen und Notices der enthaltenen Versionen zuordnen. Falls nicht belastbar möglich, gezielten reproduzierbaren Native-Neubau erwägen. | Passende Source-/Lizenzbelege nach [Lizenzbericht](reports/2026-09-07-licenses.md), finaler APK-/ABI-Audit. Erst danach APK als Release-Asset. |
| 2 — TalkBack, BLOCKED | Fokusnavigation/Aktivierung mit geeigneter echter Eingabe oder menschlichem Tester. Sprachausgabe ankündigen, Einstellungen restaurieren. | Hauptflächen einschließlich Trackdialog, Viewer, Export und Fehleransicht bedienbar; Labels/Reihenfolge tatsächlich geprüft. Semantiktests bereits PASS. |
| 2 — sechs übersprungene Tests, BLOCKED/NOT_RUN | Vier davon brauchen eine echte Videoquelle (`ExtractionChainTest` zwei, `NativeRuntimeTest` einer über `publicSourceUrl`) oder ein echtes Engine-Release (`EngineUpdateManagerTest.realReleaseStageActivateAndRollbackSurvivesManagerRestart`, `EngineJobPinningTest` über `engineProbeSource`). `UiFixtureTest` bleibt mit Absicht aus: Es ist ein Saatgenerator, der synthetische Daten in die App-Datenbank des Geräts schreibt, kein Test. | Je ein dokumentierter Lauf mit echter Quelle beziehungsweise echtem Release. Gefunden in Runde 11, als auffiel, dass `am instrument` auch dann `OK` schreibt, wenn eine Annahme übersprungen wurde. |
| 2 — persönliche Updates, teilweise NOT_RUN | Privaten Signing-Key separat sichern/übertragen. Bei nächster App-Änderung VersionCode erhöhen, bestehende persönliche Installation mit demselben Key aktualisieren. | Verlauf bleibt nach signiertem App-Update erhalten. Signing/Erstinstallation PASS, Upgrade dieser Installation noch NOT_RUN. |
| 2 — Nutzerfeedback, ausstehend | Gerät/Version, Schritte, erwartetes/tatsächliches Verhalten und Screenshot erfassen. Reproduzierbare Defekte eng fixen. | Regression und unabhängige Gegenprüfung im betroffenen Bedienpfad; keine pauschale Kosmetikschleife. |
| 3 — frischer Clone, NOT_RUN | BUILD auf zweitem Rechner oder isoliertem Linux-System ohne vorhandene Projektcaches ausführen. | Wrapper-/Dependency-Verifikation, Build und passende Tests erfolgreich. Privater Key/SDK/Appdaten separat. |

CI-Nachverfolgung, nur bei neuem Auftrag:

```bash
timeout 30 gh workflow run android.yml --ref main
timeout 30 gh run list --workflow android.yml --limit 3
# Konkrete Run-ID aus obiger Ausgabe verwenden:
timeout 60 gh run view RUN_ID --log-failed
```

Nach v1: algorithmischer Transcript-Diff, zusätzliche Filter und Presetgestaltung.
Andere Quellen, lokale ASR und Backend bleiben außerhalb dieser Preview/v1.
Hersteller-Akkuregeln, physisches ENOSPC und sämtliche Android-Versionen sind nicht
pauschal abgenommen. Kontrollierte I/O-/Providerfehler wurden bereits getestet.

Die frühere 70-%-Zahl war keine belastbare Aufwandsschätzung. Funktionalität ist
breit implementiert; verbleibende reale Integration, Geräte-/Providerabnahme und
Auslieferungsbelege sind nicht proportional zur Zahl erledigter Roadmapzeilen.
Daraus keine neue Prozentzahl oder Stundenprognose ableiten.
