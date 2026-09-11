# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der neunten Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Zehnte Reviewrunde über die Korrekturen der neunten

Runden 3 bis 9 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt. Die beiden
Commits der neunten Runde einzeln benennen, nicht als Bereich — die Bereichsschreibweise lag in Runde 7
schon einmal daneben, weil `a..b` den Anfangscommit auslässt und ältere Fixes Vorfahren davon sind. Drei
Sonnet-5-Reviewer: core, Texte und Doku, und einer über die Korrekturen selbst. Drei Lehren gehören in den
Auftrag: ein Fund, den der Reviewer nicht ausführen konnte, gilt erst nach einer Gegenprobe mit wieder
eingebautem Fehler als lebender Fehler; jede Zahl in der Doku wird nachgezählt, weil in den Runden 7, 8 und
9 je eine falsche darin stand, alle von mir; und solange ein Reviewer läuft, wird an keiner Datei
gearbeitet, die er lesen könnte — in Runde 9 hat das einen Fund gegen einen Baum erzeugt, der sich unter
dem Reviewer bewegte. Die Jagdliste:

- Die neue Regel lautet: Prosa wird gekürzt, ein Bezeichner ganz behalten oder ganz verworfen. Ist die
  Zuordnung richtig getroffen? `title`, `channel` und `format_note` gelten als Prosa, `upload_date`,
  `language`, `acodec` und `ext` als Bezeichner. Gibt es ein Feld auf der falschen Seite — eines, dessen
  gekürzte Form eine andere Aussage macht statt einer kürzeren? Und: Ist ein langer Bezeichner jetzt still
  weg, wo vorher ein falscher Wert stand — fällt das irgendwo auf, oder sieht es wie „nicht angegeben“ aus?
- Genau das ist der Kern des Runde-9-Funds: Ein Fix der Vorrunde zog eine Grenze ein und hob dabei eine
  Zusicherung auf (`AudioTracks.automatic` wählte plötzlich selbst). Sucht dasselbe Muster in den neuen
  Änderungen: Welche Eigenschaft, die woanders im Code zugesichert ist, könnte das Verwerfen — nicht mehr
  das Kürzen — eines Wertes kippen? `AudioTracks`, `CaptionTracks` und alles, was über `originalLanguage`
  entscheidet, sind die Kandidaten.
- Der Grenzwerttest für die Bildadresse nennt die Zahl jetzt ausgeschrieben und sichert die Konstante
  getrennt zu. Gibt es weitere Tests, die ihre Eingabe aus derselben Konstante bauen, die sie prüfen, und
  deshalb mit einer versehentlich veränderten Grenze mitwandern? `MAX_FILENAME_PART_BYTES`, `LIMIT`,
  `KIND_LIMIT` und die Zeitgrenzen sind die Stellen, wo ich das vermute.
- Der Vollständigkeitstest der Familienliste vergleicht Mengen und hat seit Runde 9 zwei zusätzliche
  Zusicherungen gegen doppelte Einträge. Greifen sie wirklich? Baue einen doppelten Eintrag ein und sieh
  nach, welche Zeile fällt — und ob sie es aus dem beabsichtigten Grund tut, nicht wegen der zufällig
  gleichen Länge von Liste und Zuordnung.
- Die Doku dieser Runde korrigiert fünf Zahlen und begründet jede. Zähle sie alle nach: 59 Familiennamen,
  21 in keinem Test, 32 nicht durch die Zusammenfassung, dreizehn zusammengesetzte Namen, mindestens 43
  Codes im `else`-Zweig, 162 JVM-Tests, 224 als schlimmster Fall der Warnliste. Die Trennung von
  `MALFORMED_WORD` und `MALFORMED_WORDS` entscheidet die ersten drei; ohne sie kommt etwas anderes heraus.
- DEFECTS 4 und Abschnitt 3 dieses Dokuments tragen seit Runde 9 dieselbe Aussage. Prüfe, ob sie das
  wirklich tun, und ob eine dritte Stelle — STATUS, PRODUCT, der Code selbst — noch die widerlegte Annahme
  vertritt, alle Codes des Zweigs bedeuteten dasselbe.

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
