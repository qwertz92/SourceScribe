# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026 am Ende der dritten Reviewrunde, auf Bitte des Nutzers, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die drei Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Vierte Reviewrunde über die Korrekturen der dritten

Die dritte Runde hat in `42f723e` eine Regression gefunden, die die zweite Runde selbst eingebaut
hatte: eine Textzeile ohne reservierte Höhe, also genau der Layout-Shift, den derselbe Auftrag
beseitigen sollte. Deshalb ist die Runde nach den Korrekturen nicht optional. Der Commitbereich für
die Reviewer ist `42f723e..HEAD`. Vier Sonnet-5-Agenten, dieselbe Aufteilung wie bisher: core,
App-Logik, UI, Ressourcen und Doku. Die Auftragstexte der dritten Runde lassen sich
übernehmen; nur die Jagdliste wechselt auf die neuen Stellen:

- `core/.../providers/Warnings.kt`: in Runde 4 geprüft. Eine Allowlist-Ergänzung für
  `WARNINGS_TRUNCATED` ist nicht nötig, weil Transkriptwarnungen gar nicht in den Diagnosebericht
  gehen. Der Contract-Test am echten Adapter fehlte und ist nachgetragen
  (`AssemblyAiAdapterTest.anAnswerFullOfBrokenEntriesCannotGrowTheWarningListWithIt`). Offen bleibt
  Punkt 11 in [DEFECTS.md](DEFECTS.md): die Deckelung kann die Wiederverwendung bezahlter Abschnitte
  nach einem App-Update sperren.
- `ExtractorMetadata.carried` prüft Zahlenfelder jetzt mit dem Zahlenleser. Gibt es ein Feld, das
  dadurch aus `evidence` verschwindet, obwohl es weiterhin ausgewertet wird?
- `TranscriptScreen`: Reserviert die gemessene Höhe wirklich beide Fassungen, auch bei 200 %
  Schriftgröße und im Querformat? Springt nichts anderes, wenn `Filtered` nachläuft?
- `AudioTracks.readingOrder` hat einen Schlüssel mehr. Ist der Comparator weiter total?

### 2. Warncodes lesbar machen (DEFECTS 9, mittel)

Der größte verbleibende sichtbare Mangel und derselbe Mangel, den der Nutzer am 10. September an
anderen Stellen gemeldet hat: In der Ergebnisansicht stehen rohe Codes wie
`WORD_TIMESTAMPS_MALFORMED_17`. Umfang sind rund 25 Codes in `AssemblyAiAdapter` und
`SyncTranscriptParser`. Vorgehen: Indexanhang abschneiden, Codefamilie auf einen Satz abbilden
(analog `messageText` in `Labels.kt`), gleiche Familien zusammenfassen statt sie zu wiederholen, und
die technische Fassung in der Diagnose unverändert lassen.

### 3. Interne Integritätscodes (DEFECTS 4, mittel)

Rund 45 Codes fallen in den `else`-Zweig von `messageText`. Sie bedeuten alle dasselbe: ein interner
Bindungs- oder Prüfschritt hat nicht gepasst, und es wurde nichts stillschweigend akzeptiert. Ein
gemeinsamer erklärender Satz plus technischer Status ist besser als der jetzige generische Text. Am
besten zusammen mit Punkt 2, weil es dieselbe Datei und dieselbe Denkweise ist.

### 4. Kleine offene Punkte, in dieser Reihenfolge

- DEFECTS 8: Längenobergrenze für `speech_model_used` plus Contract-Test mit überlanger Antwort.
- DEFECTS 7: Entscheiden, ob `reconcile` `documentUri` bei nachgewiesen fehlender Datei löscht.
- DEFECTS 6: Mehr Bytes im Streuwert des Dateinamens oder ein Test, der die Annahme festhält.
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
