# Restarbeiten

## Wiederaufnahme: hier weitermachen

Geschrieben am 11. September 2026, zuletzt nach der zweiundzwanzigsten Reviewrunde nachgeführt, damit die
Arbeit ohne Wiedereinlesen der ganzen Sitzung weitergehen kann. Reihenfolge ist Absicht.

**Wo der Stand steht:** Die Reviewrunden und was sie gefunden haben, stehen in
[STATUS.md](STATUS.md); was offen ist, mit Stelle und fehlendem Nachweis, in
[DEFECTS.md](DEFECTS.md). Die Punktnummern unten sind die Nummern dort.

### 1. Dreiundzwanzigste Reviewrunde über die Korrekturen der zweiundzwanzigsten

Runden 3 bis 16 haben ihren wichtigsten Fund jeweils in den Korrekturen der Vorrunde gehabt, Runde 17 in einer nie
umgesetzten Vorgabe aus S7 in [SECURITY_UPDATES.md](SECURITY_UPDATES.md), Runde 18 in der Begründung eines ADR,
Runde 19 und Runde 20 wieder in Korrekturen der Vorrunde, dem Ersetzen eines beschädigten Slots und der Regel gegen
Zeilennummern, Runde 21 in den Lizenzhinweisen nach dem Wechsel auf Bouncy Castle 1.86, Runde 22 in einer Korrektur
der Vorrunde: Hinter der Signatur in Armor, deren Test Runde 21 schrieb, blieb ein zweiter Block ungelesen. Commits der
zweiundzwanzigsten Runde sind `8097c08`, `3863da4`, `391ca8b`, `1c7d629`, `d99ff67` und `17bc156`, dazu ihr Doku-Commit; einzeln benennen, nicht als Bereich, weil `a..b` den
Anfangscommit auslässt. Die Doku der Preview 0.2.0 entsteht erst nach ihrem Build.

**Stand am 14. September 2026, nach Runde 22:** Die Korrekturen der Runde 22 sind committet und gepusht, ihre Funde,
Gegenproben und Gates stehen in [STATUS.md](STATUS.md). Offen aus Runde 22 bleiben eine gescheiterte Löschung der
Prüfansicht, deren Meldung die erste ersetzt ([Punkt 56](DEFECTS.md)), und zwei Schreibweisen, die die
Versionsprüfung der Lizenzhinweise nicht erkennt ([Punkt 28](DEFECTS.md)). Die lokalen Release-Gates auf `17bc156`
bestanden, der CI-Lauf auf demselben Commit scheiterte an `ChoiceAccessibilityTest`, weil die App noch startete
([Punkt 54](DEFECTS.md)). Runde 23 hat die sechs Commits der Runde 22 mit einem Code- und einem Invarianten-Reviewer
gelesen. Als Nächstes: deren Korrekturen und die des Tests, danach Build und Gates der Preview 0.2.0 auf dem letzten
dieser Commits; ein Code- und ein Invarianten-Reviewer über die Korrekturen der Runde 23 und ein Konsistenzreviewer
über die Doku-Commits der Runden 22 und 23 und den der Preview. Meldet keiner einen hohen oder mittleren Fund, kommt
das Tag `v0.2.0-preview.1` auf den letzten Doku-Commit.

Rein lesende Reviewer zuerst, gleichzeitig; ein verändernder danach allein. Diese Lehren gehören in den
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
- Ein Reviewer, der für Gegenproben Dateien verändert, läuft nicht neben einem, der liest, es sei denn, der
  lesende liest einen Export (`git archive`) statt des Arbeitsbaums; so liefen seit Runde 18 die Gegenproben neben
  den Reviewern. Jeder Symbolname im Auftrag wird vorher gegen den Baum geprüft. Und nach jeder Installation auf dem
  Emulator wird die Ausgabe auf `Success` geprüft: Ein voller Speicher ließ in Runde 15 neue Tests gegen alten
  App-Code laufen (Wartungshinweis in [DEFECTS.md](DEFECTS.md)).
- **Ein Gerätelauf belegt nur die Werte, die er tippt.** `0.25` blieb in Runde 15 heil, `0.123456` in Runde 16
  nicht. Lange Werte, mehrzeilige Felder und jedes Feld einzeln.
- **Ein Reviewer, der eine Erreichbarkeit „durchgerechnet“ hat, kann einen Vorschritt übersehen.** Runde 16:
  `configurationForStart` setzt die Freigabe, bevor `configError` gefragt wird. Runde 18: Zwei Fenster, die der
  Code-Reviewer für offen hielt, schließt heute innerhalb eines View-Models die Sperre von `MainViewModel.action`.
- **Lint vor dem Commit, nicht erst im Gate.** Runde 17: `1b2dc45`.
- **Die Vorgaben der Dokumente gegen den Code lesen, nicht nur den Diff.** Runde 17: S7 verlangte seit dem ersten
  Commit, alte Engines zu bereinigen, und nichts tat es.
- **Die Begründung eines ADR ist eine Behauptung über den Code.** Runde 18: ADR 0009 schützte eine Engine für einen
  Weg, der sie nie ausführt, und ADR 0010 beschrieb einen Grenzfall, den der Code nicht erreichen ließ. Jede
  genannte Stelle lesen.
- **Eine Gegenprobe braucht Tests, die die Schutzregeln einzeln unterscheiden.** Die ersten Slot-Tests der
  Runde 17 hätten nicht gezeigt, welche von zwei Regeln fehlt, die Reihenfolge oder der Schutz der vorherigen
  Engine; sie wurden umgebaut, bevor sie liefen.
- **Ein Messskript kann eine Zahl liefern, die nach Befund aussieht, und ein Prüfskript trägt sein Urteil im
  Exitcode.** Runde 17 maß die Kostenzeile bei 130 % Schrift mit 41 px, weniger als bei 100 %: Die
  Navigationsleiste verdeckte sie, und uiautomator meldet nur den sichtbaren Teil. Und `r16_typing.py` meldete einen
  abweichenden Fall und endete mit 0.
- **Keine Instrumentierung neben einem Gradle-Build.** Runde 17: Ein Selbsttest der Laufzeit lief in seine
  30 Sekunden, während ein Build anlief.
- **Nach Tests auf dem Gerät die Daten der App lesen, nicht nur die Testberichte, und eine geänderte Testklasse vor
  dem Gate laufen lassen.** Runde 18: Einstellungen und 24 Datenbankdateien aus Tests lagen seit Tagen in der
  Debug-App, und die Korrektur der Einstellungen brach sieben Tests einer Nachbarklasse, die erst das Gate lief.
- **Ein Fund über das Verhalten einer Bibliothek gilt für eine Version.** Runde 19: Der Code-Reviewer beschrieb
  `ExternalResource` wie in JUnit 4.12; das Projekt nutzt 4.13.2, und dort tritt der gemeldete Fehler nicht auf. Die
  Version aus dem Build lesen, bevor ein solcher Fund zählt. Runde 22 wieder: Laut dem Quelltext, den der
  Invarianten-Reviewer las, gibt Compose den Inhalt eines Passwortfelds an die Accessibility weiter; mit der Fassung
  der App erschienen auf dem Emulator nur Punkte.
- **Eine Korrektur, die ein Fenster schließt, zählt auf, wo es offen bleibt.** Runde 19: Die erste Fassung von
  ADR 0011 zum Ersetzen im Slot ließ aus, dass eine gescheiterte Umbenennung über eine intakte Datei den Slot weiter
  entfernt und neu anlegt.
- **Ein Werkzeug, das Commits aus Textersetzungen baut, schreibt mehr als Text.** Runde 20: Das Stage-Skript der
  Runde 19 trug jede Datei als 100644 in den Index ein und nahm `tools/check-repository.py` das Ausführungsbit;
  beide Reviewer fanden es an den Modi in Git. Den Modus aus HEAD übernehmen und `git show --summary` lesen.
- **Eine neue Regel braucht Gegenbeispiele in beide Richtungen, bevor sie gilt.** Runde 20: Die Regel gegen
  Zeilennummern kannte die drei Formen aus DEFECTS, nicht den Linkanker auf eine Zeile.
- **Eine Zahl aus einer Textsuche zählt Zeichenketten, keine Aufrufe.** Runde 21: `d994c23` nannte 55 Klassen, die
  eine Methode aufrufen, die ältere Android-Versionen nicht haben, und der Release-Reviewer bestätigte die Zahl mit
  derselben Suche. Keine Klasse ruft sie auf.
- **Ein Versionswechsel trifft jede Datei, die die Version nennt.** Runde 21: Nach Bouncy Castle 1.86 nannten beide
  Lizenzhinweise 1.85, und die Statusdokumente kannten die Version 0.2.0 nicht.
- **Eine Zusicherung über die Form einer Eingabe gilt für die Schicht, die sie liest.** Runde 22: „genau eine
  Signatur“ prüfte, was der Parser lieferte, und hinter der Fußzeile von ASCII-Armor liefert er nichts, auch keinen
  zweiten Block.
- **Eine Gegenprobe braucht einen Fall, den nur die Korrektur abfängt.** Runde 22: Ohne Filter erscheint der Inhalt
  des Schlüsselfelds schon als Punkte. Der Test setzt seinen Text deshalb in ein Feld ohne Maskierung und prüft
  zuerst, dass der Text im Baum steht.
- **Ein grüner CI-Lauf schließt keinen zeitweisen Fehler.** Runde 22: `ChoiceAccessibilityTest` bestand am Stand
  `7d9ce41` nach drei Fehlschlägen, ohne Änderung an seiner Bedingung oder am Code der App, und scheiterte am Stand
  `17bc156` wieder.

Die Jagdliste:

- **Der Rest hinter der Signatur** (`8097c08`): Liest `PGPUtil.getDecoderStream` oder der Parser einen
  Teil des Rests in einen eigenen Puffer, sodass `onlyWhitespaceRemains` ihn nicht mehr sieht, etwa bei einer binären
  Signatur, auf die Bytes folgen, die kein Paket sind?
- **Die unlesbaren Pfade** (`1c7d629`): Die Prüfung meldet jetzt jeden Pfad im Git-Index, den der Baum
  nicht liefert, nicht nur Skripte. Stört das in einem Arbeitsbaum mit `sparse-checkout` oder mitten in einer
  Änderung, die eine Datei gelöscht, aber noch nicht aus dem Index genommen hat?
- **Das „v“ vor einer Version** (`d99ff67`): Hält das Muster jetzt etwas für die Version eines Namens aus
  dem Versionskatalog, das keine ist?
- **Die Meldung von `ChoiceAccessibilityTest`** (`17bc156`): Zeigt ein Knoten den Inhalt eines Feldes, ohne
  selbst bearbeitbar zu sein, etwa eine Vorschau oder ein Zähler? Und reichen 60 Zeichen je Text, um die Ursache von
  [Punkt 54](DEFECTS.md) zu erkennen?
- **Die Testlücken des Code-Reviewers der Runde 21:** Kein Selbsttest hält die Schlusszeile ohne Git-Index fest, und
  keiner zeigt, dass die neuen Formen gegen Zeilennummern außerhalb von `docs/` durchgehen.
- **Der Test der gescheiterten Umbenennung der Metadaten** (`dc9e6dd`) setzt voraus, dass `rename(2)` eine
  Datei nicht über ein Verzeichnis benennt. Gilt das auf jedem Dateisystem, auf dem App-Daten liegen können?
- **Die Testlücken des Code-Reviewers der Runde 19:** eine symbolische Verknüpfung auf eine Verknüpfung an der Stelle
  des Slots, ein Lesefehler beim Hashen einer intakten Datei und zwei gleichzeitige Aufrufer des Managers, von denen
  einer den Slot ersetzt, während der andere die Engine startet. Keine davon hat einen Test.
- **[Punkt 52](DEFECTS.md):** Lässt sich der DataStore erst beim ersten Zugriff suchen, ohne zwei Stores für dieselbe
  Datei zu erlauben?
- **Die Sperre von `MainViewModel.action`** trägt die Punkte [48](DEFECTS.md) und [49](DEFECTS.md). Entsteht eine
  zweite Instanz der Aktivität mit eigenem View-Model tatsächlich, etwa nach dem Teilen aus einer anderen App, und
  gibt es einen Aufrufer von `stage`, `activate`, `rollback` oder `retry` außerhalb der Sperre?
- **[Punkt 46](DEFECTS.md) (b):** Soll eine dritte Engine nach einem App-Update erreichbar bleiben? Eine
  Gestaltungsfrage; nicht ohne Rückfrage entscheiden.
- **Der Selbsttest der Laufzeit in `ensureBundledLocked`** hat 30 Sekunden. Was geschieht auf einem langsamen Gerät
  beim ersten Start nach einem App-Update, wenn er sie überschreitet, und kommt die App dann wieder heraus?
- **`engineReferences`** lädt jetzt nur noch Versuche, aber alle. Tragbar bei vielen Aufträgen?
- **[Punkt 47](DEFECTS.md)** und ein Rollback, während ein Auftrag mit gebundener Engine läuft: kein
  deterministischer Test, nur der Live-Test für das Aktivieren.
- **[Punkt 50](DEFECTS.md):** Sollen die Tests eine eigene WorkManager-Instanz bekommen, und läuft dann noch, was
  sechs Testklassen heute über echte Worker prüfen?
- **`typeIntoDraft` und eine IME-Komposition** während eines Starts: geprüft ist nur `adb shell input`. Und der
  Lückenhinweis im VTT-Export hat keinen eigenen Test; dass die Lückenberechnung das Format nicht kennt, hat nur der
  Code-Reviewer der Runde 18 nachgelesen.
- **Die Epochen** (`DraftEdits`, [Punkt 38](DEFECTS.md)), **die reservierten Höhen** ([Punkte 35](DEFECTS.md) und
  [39](DEFECTS.md)) und ob `ReservedText` in einer scrollenden Liste mit sekündlich wechselnder Wartezeit spürbar
  kostet.
- **Weiter offen:** DEFECTS 30 (die Mindestdauer eines Modells als dritte Längenschranke), die fehlende
  Zahlenliste der Module `app` und `extractor`, `Source.originalLanguage` ohne Gegenstück zu
  `AudioTrack.languageRefused`, `ProviderCapabilities.pricingSource` mit DEFECTS 24 und 29, die historischen
  Punktverweise der Runden 3 und 4 in STATUS, ob `RAW_DATA_WITHOUT_EXTENSION` unter [Punkt 4](DEFECTS.md) gehört,
  der Lexer der Zahlenprüfung ([Punkt 34](DEFECTS.md)) und, als Gestaltungsfragen, die nicht ohne Rückfrage
  entschieden werden, DEFECTS 36 und „Aufklappen schiebt“.
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
- Und die Doku: Jede Zahl der Passagen der Runden 16 bis 18 nachzählen, mit dem Stand, für den sie gilt, und jedes
  „heute“ neben einer Zahl in allen Dokumenten.

### 2. Warncodes lesbar machen (DEFECTS 9) — erledigt

Umgesetzt am 11. September 2026. Dreizehn Sätze statt 59 Codefamilien, Zuordnung in
`core/.../TranscriptWarnings.kt`, rohe Codes unter den Details. Am Gerät angesehen und die
Sprungfreiheit der Kopfzeilen über drei Suchzustände nachgemessen.

### 3. Codes ohne eigenen Text (DEFECTS 4, mittel)

Mindestens 43 Codes fielen am 11. September in den `else`-Zweig von `messageText`; `ENGINE_NOT_AVAILABLE`, eines
der Beispiele in DEFECTS 4, hat seit Runde 17 einen eigenen Text. **Sie bedeuten nicht alle dasselbe** —
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
- DEFECTS 36, „Aufklappen schiebt“ unter den bewussten Entscheidungen in DEFECTS und DEFECTS 46 (b):
  Gestaltungsfragen, die nicht ohne den Nutzer entschieden werden; am 14. September zur Entscheidung vorgelegt.
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
