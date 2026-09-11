# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der achten Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Neunte Reviewrunde über die Korrekturen der achten

Runden 3 bis 8 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt. Die Commits der
achten Runde einzeln benennen, nicht als Bereich — die Bereichsschreibweise lag in Runde 7 schon einmal
daneben, weil `a..b` den Anfangscommit auslässt und ältere Fixes Vorfahren davon sind. Drei Sonnet-5-Reviewer:
core, Texte und Doku, und einer über die Korrekturen selbst. Zwei Lehren, die in den Auftrag gehören: ein
Fund, den der Reviewer nicht ausführen konnte, gilt erst nach einer Gegenprobe mit wieder eingebautem Fehler
als lebender Fehler; und jede Zahl in der Doku wird nachgezählt, weil in den Runden 7 und 8 je eine falsche
darin stand, beide von mir. Die Jagdliste:

- Der Familientest hat jetzt eine zweite, von Hand aus den Erzeugern abgeschriebene Liste. Stimmt jeder der
  59 Einträge mit der Zeile überein, die ihn wirklich schreibt — besonders die elf, die dort nur aus Teilen
  zusammengesetzt entstehen? Ein falsch abgeschriebener Eintrag verlangt einen Namen, den nie jemand
  erzeugt, und niemand merkt es, solange die Zuordnung denselben Fehler trägt.
- Beide Listen stehen jetzt nebeneinander, und die eine prüft die andere. Kann jemand einen Namen ändern,
  ohne dass irgendein Test fällt? Wenn ja, wo?
- `ExtractorMetadata` begrenzt jetzt Datum, Originalsprache und Formatsprache auf 100 Zeichen und lehnt eine
  zu lange Bildadresse ab. Gibt es ein weiteres Feld ohne Grenze — auch in `CaptionTrack` und `AudioTrack`,
  nicht nur in `Source`? Ändert eine der Grenzen die Bedeutung eines Feldes, statt es nur zu beschneiden?
  Und ist die Ablehnung der Adresse wirklich eine Ablehnung, oder verschwindet dabei still ein Vorschaubild,
  das vorher da war?
- `safePart` fällt jetzt auch dann auf den Ersatzwert zurück, wenn nur Unterstriche übrig bleiben. Gibt es
  einen Wert, der dadurch seinen Ersatzwert bekommt, obwohl er etwas benannt hat? Was ist mit einem Namen,
  der absichtlich aus Unterstrichen besteht?
- Die Gegenprobe selbst: Sie stellt Fehler wieder her und erwartet fallende Tests. Prüfe die
  Wiederherstellungsskripte im Ablageordner nicht — prüfe stattdessen, ob die Behauptung in STATUS, welcher
  Test wobei fällt, mit dem Baum übereinstimmt.
- Und die Doku: Jede Zahl der Runde-8-Passage nachzählen. In Runde 7 war es „46" statt 34, in Runde 8 hätte
  „164" statt 162 dringestanden, wenn ich nicht vor dem Commit nachgezählt hätte.

### 2. Warncodes lesbar machen (DEFECTS 9) — erledigt

Umgesetzt am 11. September 2026. Dreizehn Sätze statt 59 Codefamilien, Zuordnung in
`core/.../TranscriptWarnings.kt`, rohe Codes unter den Details. Am Gerät angesehen und die
Sprungfreiheit der Kopfzeilen über drei Suchzustände nachgemessen.

### 3. Interne Integritätscodes (DEFECTS 4, mittel)

Rund 45 Codes fallen in den `else`-Zweig von `messageText`. Sie bedeuten alle dasselbe: ein interner
Bindungs- oder Prüfschritt hat nicht gepasst, und es wurde nichts stillschweigend akzeptiert. Ein
gemeinsamer erklärender Satz plus technischer Status ist besser als der jetzige generische Text. Am
besten zusammen mit Punkt 2, weil es dieselbe Datei und dieselbe Denkweise ist.

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
