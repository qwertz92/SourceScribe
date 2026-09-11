# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der zehnten Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Elfte Reviewrunde über die Korrekturen der zehnten

Runden 3 bis 10 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt. Runde 10 am
deutlichsten: Die Korrektur der Vorrunde hatte ihren Fehler nicht behoben, sondern verschoben und seinen
Auslöser verbreitert. Die Commits der zehnten Runde sind `db8a6d2` (Code) und `c795ab0` (Doku); die der
elften stehen darunter, sobald sie geschrieben sind. Einzeln benennen, nicht als Bereich — die
Bereichsschreibweise lag in Runde 7 schon einmal daneben, weil `a..b` den Anfangscommit auslässt und
ältere Fixes Vorfahren davon sind. Drei Sonnet-5-Reviewer: core, Doku, und einer quer durchs Repository.
Vier Lehren gehören in den Auftrag:

- Ein Fund, den der Reviewer nicht ausführen konnte, gilt erst nach einer Gegenprobe mit wieder
  eingebautem Fehler als lebender Fehler.
- Jede Zahl in der Doku wird nachgezählt, und **jede Zahl nennt den Stand, für den sie gilt** — in Runde
  10 stand eine richtige Zahl ohne ihren Commit da und wäre beim Nachzählen gegen den heutigen Baum als
  falsch gemeldet worden.
- Jeder Symbolname in einem Auftrag wird vorher gegen den Baum geprüft. Die Liste der Vorrunde schickte
  einen Reviewer nach `CaptionTracks`, das es nicht gibt.
- Ein Reviewer, der für Gegenproben Dateien verändert, und einer, der liest, laufen **nicht** gleichzeitig.
  In Runde 10 haben beide lesenden Reviewer den Baum unter sich wandern sehen, weil ich dem dritten das
  Verändern erlaubt habe.

Die Jagdliste:

- `AudioTrack.languageRefused` ist neu und sagt, dass die Quelle eine Sprache genannt hat, die der
  Datensatz nicht tragen konnte. Wird die Unterscheidung überall gezogen, wo sie zählt — oder nur in
  `AudioTracks.automatic`? Und die unbeantwortete Frage dahinter: Die App fragt jetzt zurück, zeigt dem
  Nutzer aber zwei Spuren ohne jede Sprachangabe. Ist das eine lesbare Frage oder eine rätselhafte?
- Dieselbe Verwechslung eine Ebene höher: Wo sonst bedeutet `null` zwei verschiedene Dinge — „nicht
  angegeben“ und „angegeben, aber verworfen“? `Source.originalLanguage`, `publishedDate`, `codec`,
  `container`, `reportedModel`. Ein Reviewer der zehnten Runde hat für diese fünf gesagt, dass keine davon
  eine Entscheidung trägt; prüfe das selbst nach, statt es zu übernehmen. Eine Stelle habe ich selbst
  geprüft und nicht als Fehler gewertet: `TrackSelection.captions` ordnet nach `originalLanguage`, und ein
  verworfener Wert lässt `preferOriginalLanguage` still wirkungslos werden. Entschieden wird dabei aber
  nichts — die Funktion sortiert nur, die Liste wird nicht kürzer, und ab zwei Einträgen fragt
  `JobCoordinator` ohnehin. Wer das anders sieht, soll es an einem Ablauf zeigen.
- Die Regel „Tragen ist begrenzt, Fragen nicht“ gilt jetzt in `ExtractorMetadata`. Wird anderswo eine Frage
  an eine gekürzte Kopie gestellt? `CaptionParser`, `SttStep`, `Diagnostics`, `Labels` sind die Stellen mit
  `contains(`, `startsWith(` oder `endsWith(` auf einem Wert, der vorher durch `take(` gelaufen ist.
- **Die fünf neu ausgeschriebenen Grenzen: Ist die festgenagelte Zahl die richtige?** Eine Grenze
  festzunageln hält sie fest, auch wenn sie von Anfang an falsch war. `MAX_CANONICAL_BYTES` (32 MiB),
  `MAX_AUDIO_SECONDS` (36 000), `Warnings.LIMIT` (64), `Warnings.KIND_LIMIT` (160), `MAX_DURATION_MS`
  (36 000 000). Zwei davon sind in Runde 11 gegen eine zweite Quelle bestätigt worden: `MAX_AUDIO_SECONDS`
  gegen die 600 Minuten, die der Text `invalid_duration` in beiden Sprachen nennt, und `MAX_DURATION_MS`
  gegen AssemblyAIs eigene Dokumentation. Für die übrigen drei gibt es keine zweite Quelle im Repository —
  das ist eine Lücke im Beweis, kein Fehler, und wer sie schließen kann, soll es sagen.
- Die Sichtbarkeit war die Ursache: Ein Test greift nach einer Konstante, wenn er sie sehen kann. Welche
  `internal` oder `public` Konstanten gibt es sonst noch, und hängt an einer davon ein Test, der mit ihr
  mitwandert? Die Suche geht über alle drei Module, nicht nur über `core`.
- Und die Doku: Jede Zahl der Runde-10-Passage nachzählen, mit dem Stand, für den sie gilt.

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
- Die drei Live-Providerläufe, ARM64 und TalkBack bleiben blockiert wie in der Tabelle unten.

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
