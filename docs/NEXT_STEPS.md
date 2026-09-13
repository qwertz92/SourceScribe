# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der fünfzehnten Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Sechzehnte Reviewrunde über die Korrekturen der fünfzehnten

Runden 3 bis 15 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt. In Runde 15
war es die Zahlenprüfung, die Runde 14 permissiver gemacht hatte: Von ihren vier Lücken waren zwei still.
Commits der fünfzehnten Runde sind `8833d07`, `b2286d5`, `089b184`, `1f378a9`, `e33ac54` und `ab9f36e` sowie
der Doku-Commit direkt darüber; einzeln benennen, nicht als Bereich, weil `a..b` den Anfangscommit auslässt.

Rein lesende Reviewer zuerst, gleichzeitig; ein verändernder danach allein. Zwölf Lehren gehören in den
Auftrag:

- Ein Fund, den der Reviewer nicht ausführen konnte, gilt erst nach einer Gegenprobe als lebender Fehler.
- **Eine Gegenprobe muss jede Stelle abschalten, die eine Regel durchsetzt, nicht die erstbeste.** Runde 11
  fand so eine dritte, unbekannte Durchsetzungsstelle; Runde 12 eine vierte.
- **Eine grüne Gegenprobe über einer falschen Zahl sieht aus wie eine über einer richtigen.** Sie prüft die
  Kopplung zwischen Test und Code, nie die Aussage über die Welt.
- **Eine Anbieterangabe wird aus dem Markup der Seite gelesen, nicht aus einer Zusammenfassung.** Der
  Befehl und das Vorgehen stehen als Wartungshinweis in [DEFECTS.md](DEFECTS.md).
- **Eine Randbedingung, die als „heute irrelevant“ abgehakt wird, braucht dieselbe Quellenprüfung wie die
  Zahl, um die es geht.** Runde 13 schrieb „kein veröffentlichter Preis“ über ein Modell, dessen Preiszeile
  in derselben Ausgabe stand, die sie gerade prüfte.
- **Frage bei jeder Korrektur: Was konnte die Prüfung vorher, das sie danach nicht mehr kann?** Nicht nur,
  was sie jetzt mehr kann. Und: Trennt sie „geprüft und sauber“ noch von „nicht geprüft“? Eine
  Erweiterung, die einen Fall still in die erste Kategorie schiebt, ist schlimmer als die Lücke davor.
- **Zähle jede Zahl über eine Korrektur am Modell nach, nicht am eigenen Text.** Runde 14 hat ihre eigenen
  Formen an drei Stellen verschieden gezählt, und die ersten Kommentare der Runde 15 haben „still“ und
  „laut“ noch einmal falsch verteilt. Beide Male hat erst das Ausführen beider Fassungen über alle Fälle
  gezeigt, was stimmt.
- **Wird eine Regel an einer Stelle zusammengeführt, gehört jeder Leser auf die Liste.** Runde 15 hat
  `ContextTerms` eingeführt und `SttStep.validate` erst beim Schreiben der Doku umgestellt, obwohl der
  Code-Reviewer die Stelle in seiner Liste aller Leser genannt hatte.
- **Ein Fund außerhalb des zugewiesenen Diffs ist ein Fund.** Der wichtigste Fund der Runden 12, 13 und 14
  kam jedes Mal so zustande, und in Runde 15 lagen sieben Funde außerhalb.
- **Eine Commit-Nachricht ist eine Prüfung, und eine Korrektur braucht ihre eigene Gegenprobe am Gerät.**
  Beim Nachprüfen jeder Behauptung der Commit-Nachrichten fanden sich in Runde 15 zwei Fehler, die kein
  Reviewer gemeldet hatte, einer davon in einer Korrektur derselben Runde. Die erste Korrektur des zweiten
  verlor bei schnellem Tippen Zeichen, bei grüner App-Suite; gezeigt hat das erst das Tippen am Gerät.
- Jede Zahl wird nachgezählt und nennt den Stand, für den sie gilt. Eine laufende Summe, die sich nicht
  aus dem Dokument heraus nachrechnen lässt, gehört gestrichen statt korrigiert — deshalb steht die
  heutige Testzahl seit Runde 15 nur im Kopf von [STATUS.md](STATUS.md).
- Ein Reviewer, der für Gegenproben Dateien verändert, läuft nicht neben einem, der liest. Jeder
  Symbolname im Auftrag wird vorher gegen den Baum geprüft. Und nach jeder Installation auf dem Emulator
  wird die Ausgabe auf `Success` geprüft: Ein voller Speicher ließ in Runde 15 neue Tests gegen alten
  App-Code laufen (Wartungshinweis in [DEFECTS.md](DEFECTS.md)).

Die Jagdliste:

- **`codeOnly` in `StatedNumbersTest` ist ein kleiner Lexer, und ein Lexer hat Zustände, die er nicht
  kennt.** Eine Zeichenkette innerhalb eines String-Templates steht als [Punkt 34](DEFECTS.md). Welche
  Kotlin-Lexik fehlt noch? Jede Lücke mit einem Beispiel am Python-Modell nachstellen, bevor jemand sie
  schließt, und die Gegenfrage stellen: Liest er über den Baum weiterhin genau die 33 Konstanten?
- **`ContextTerms` wird an sechs Stellen gefragt.** Gibt es eine siebte, die über eine Liste urteilt, ohne
  zu fragen? Und ist eine gespeicherte Liste mit leerem Eintrag auf jedem Modell behebbar, auch auf einem,
  das kein Fachbegriffsfeld zeigt? `CONTEXT_TERM_BLANK` steht in `configError` vor der Frage, ob das
  Modell überhaupt Fachbegriffe kann.
- **`ReservedText` misst bei jeder Textänderung neu**, bei der Wartezeit also jede Sekunde, in einer Karte
  eines scrollenden Verlaufs. Kostet das spürbar? Und sind `000:00:00` und `0000.0 GB` breit genug, auch
  in einer Schrift, deren Ziffern nicht gleich breit sind?
- **`ListField` merkt sich jede weitergegebene Liste, bis sie zurückkommt.** Kann eine Änderung von
  außen verloren gehen, die zufällig einer noch nicht zurückgekommenen Liste gleicht? Kommt eine
  weitergegebene Liste je verändert zurück, sodass sie nie gleich ankommt? Und `LimitFields` hat noch die
  Bauart der verworfenen ersten Fassung, einen `LaunchedEffect`, der jedem abweichenden Wert folgt: Am
  Gerät mit `120` und `0.25` schnell getippt nicht reproduziert, ausgeschlossen ist der Fehler damit nicht.
  Geprüft ist nur Tippen über `adb shell input`, keine echte Bildschirmtastatur.
- **`PREVIEW_ERRORS_SHOWN_AS_TEXT` ist eine zweite Liste neben `previewError`.** Das Raster in
  `ViewRulesTest` erreicht neun der zwölf Codes nachweislich. Erreicht es die übrigen drei, und welcher
  künftige Zweig läge außerhalb des Rasters?
- **Die Zeilengrenzen auf zwei Bildschirmen sind nicht gemessen** ([Punkt 35](DEFECTS.md)): am Gerät oder
  mit einem Compose-UI-Test, in beiden Sprachen, bei 200 % Schrift und im Querformat — die offene Frage aus
  Runde 14, jetzt für vier Zeilen statt einer.
- **`RAW_DATA_WITHOUT_EXTENSION` ist ein interner Integritätscode ohne eigenen Text**, wie seine Geschwister
  in `ArtifactFilesException`. Gehört er unter [Punkt 4](DEFECTS.md)?
- **Die historischen Punktverweise der Runden 3 und 4 in STATUS** hat in Runde 15 niemand vollständig gegen
  die Nummerierung in DEFECTS geprüft, nur stichprobenhaft.
- **Aufklappen schiebt, was darunter steht**, und steht jetzt als bewusste Entscheidung in
  [DEFECTS.md](DEFECTS.md). Trägt die Abwägung, oder gibt es eine der vier Stellen, an der sich etwas bewegt,
  das niemand angetippt hat?
- **Die Fehlerzeile der Vorschau erscheint und verschwindet** ([Punkt 36](DEFECTS.md)). Eine
  Gestaltungsfrage mit Vorschlag; nicht ohne Rückfrage entscheiden.
- **DEFECTS 30 bleibt offen:** die Mindestdauer eines Modells als dritte Längenschranke, die die Anzeige
  nicht kennt. Seit 31 geschlossen ist, lässt sich fragen, ob dieselbe Bauart — eine Regel in `core`, jeder
  Leser fragt sie — auch dort trägt.
- **Die Module `app` und `extractor` haben keine Zahlenliste.** Seit Runde 12 offen. Taugt die Bauart aus
  `core` für ein Androidmodul, dessen Tests auf dem Gerät laufen und den Quelltext dort nicht sehen?
- **`Source.originalLanguage` hat kein Gegenstück zu `AudioTrack.languageRefused`.** Seit Runde 12 offen,
  von niemandem geprüft: Ein verworfener Wert sieht dort wie ein nie genannter aus.
- **`ProviderCapabilities.pricingSource` erreicht niemanden** (DEFECTS 24), und DEFECTS 29 hängt daran: Die
  Preisseite von OpenAI nennt `whisper-1` gar nicht.
- **Nicht mehr offen, damit es niemand ein zweites Mal aufmacht:** Alle acht Preiszahlen sind am
  12. September 2026 aus dem Markup der drei Anbieterseiten nachgelesen worden, in Runde 13 und in Runde 14
  unabhängig voneinander — AssemblyAI 0,21/0,15 je Stunde, Sprechertrennung 0,02 in beiden Spalten,
  Fachbegriffe 0,05 nur in der Spalte Universal-3.5 Pro; Groq 0,111 und 0,04 je Stunde, Mindestabrechnung
  zehn Sekunden, 25 MB gegen 100 MB; OpenAI 0,0045 und 0,006 je Minute. Alle acht stimmen mit dem Code
  überein. Ebenfalls geprüft und leer: Es gibt keinen weiteren AssemblyAI-Zusatz, den die App mitschickt und
  nicht berechnet — der gesendete Rumpf enthält nur `audio_url`, `speech_models`, `punctuate`,
  `speaker_labels`, Sprachfelder und `keyterms_prompt`. In Runde 15 hat der Code-Reviewer die OpenAI-Seite
  noch einmal selbst geladen und die Tokenpreise für `gpt-4o-transcribe-diarize` samt der Spaltenüberschrift
  „Estimated cost“ bestätigt.
- Und die Doku: Jede Zahl der Runde-15-Passage nachzählen, mit dem Stand, für den sie gilt, und jedes
  „heute“ neben einer Zahl in allen Dokumenten.

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

**Einer dieser vier braucht drei Flaggen, nicht eine.**
`EngineUpdateManagerTest.realReleaseStageActivateAndRollbackSurvivesManagerRestart` verlangt zuerst
`-e sourcescribeEngineLiveUpdate true`, dann `-e engineProbeSource <URL>`, und erst danach läuft es in
`withIsolatedManager`, das `-e sourcescribeEngineUpdate true` verlangt. Die ersten beiden Namen sind bis
Runde 13 in keinem lebenden Dokument vorgekommen — nur in einem datierten Bericht vom 7. September — und
`sourcescribeEngineLiveUpdate` unterscheidet sich von `sourcescribeEngineUpdate` um ein Wort. Wer nur die
letzte setzt, sieht den Test übersprungen und keinen Hinweis darauf, warum.

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
sondern ein Saatgenerator, der synthetische Daten in die echte App-Datenbank des Geräts schreibt — seine
Flagge heißt `sourcescribeUiFixture`, und sie stand bis Runde 14 in keinem Dokument überhaupt; `BUILD.md`
schließt die Klasse stattdessen mit `-e notClass` aus, was denselben Zweck erfüllt und den Namen nie
nötig machte. Und `EngineJobPinningTest` braucht **zwei** Flaggen: `-e sourcescribeEngineJobPinning true`
schaltet ihn überhaupt erst frei, und `-e engineProbeSource <URL>` gibt ihm das echte Release. Die erste
wird im Test zuerst geprüft, also bleibt er ohne sie übersprungen, gleichgültig welche URL dabeisteht.

**Zwei der neun Flaggen sind keine Schranke.** `engineUpdateChannel` wählt in `EngineJobPinningTest` und in
`EngineUpdateManagerTest` den Kanal eines echten Releases und fällt ohne Angabe auf `NIGHTLY` zurück.
Sie überspringt nichts und fehlt deshalb zu Recht in der Liste der Schranken — aber sie stand bis
Runde 14 nur in zwei datierten Berichten vom 7. September, also nirgends, wo jemand nachsieht. Die zweite
ist `sourcescribeProcessStage`: `ProcessRecoveryTest` prüft zuerst die Schranke `sourcescribeProcessFixture`
und liest die Stufe danach mit `assertEquals`, sodass eine fehlende oder falsche Stufe die Tests rot werden
lässt, statt sie zu überspringen. Bis Runde 15 stand hier „die neunte Flagge“, als gäbe es nur eine.

Wie viele Tests es heute sind und wie viele davon laufen, steht im Kopf von [STATUS.md](STATUS.md) und nur
dort; die Tests, die nicht laufen, stehen als `BLOCKED/NOT_RUN` in [DEFECTS.md](DEFECTS.md). Bis Runde 15
stand hier eine eigene Summe, die jede Runde an zwei Orten nachgezogen werden musste.

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
| 2 — sechs übersprungene Tests, BLOCKED/NOT_RUN | **Fünf** davon brauchen eine echte Videoquelle (`ExtractionChainTest` zwei, `NativeRuntimeTest` einer über `publicSourceUrl`) oder ein echtes Engine-Release: `EngineJobPinningTest` über `sourcescribeEngineJobPinning` **und** `engineProbeSource`, und `EngineUpdateManagerTest.realReleaseStageActivateAndRollbackSurvivesManagerRestart` über **drei** Flaggen — `sourcescribeEngineLiveUpdate`, `engineProbeSource` und `sourcescribeEngineUpdate`, letztere über `withIsolatedManager`. `UiFixtureTest` bleibt mit Absicht aus: Es ist ein Saatgenerator, der synthetische Daten in die App-Datenbank des Geräts schreibt, kein Test. | Je ein dokumentierter Lauf mit echter Quelle beziehungsweise echtem Release. Gefunden in Runde 11, als auffiel, dass `am instrument` auch dann `OK` schreibt, wenn eine Annahme übersprungen wurde. |
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
