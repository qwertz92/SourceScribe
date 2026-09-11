# Bekannte Probleme und offene Punkte

**Stand:** 11. September 2026, nach vier Runden adversarischer Reviews. Diese Datei ist für den
nächsten Agenten gedacht und listet, was **nicht** vollständig erledigt ist. Ein geschlossener Punkt
behält seine Nummer und einen kurzen Vermerk, damit Verweise aus anderen Dokumenten gültig bleiben. Was hier nicht steht, ist entweder erledigt oder in
[STATUS.md](STATUS.md) beschrieben.

Jeder Eintrag nennt Datei und Stelle, die Voraussetzung, das erwartete gegenüber dem tatsächlichen
Verhalten und was zum Schließen fehlt. Einträge ohne reproduzierbaren Ablauf sind als **unbestätigt**
markiert; sie sind Verdachtsfälle, keine belegten Defekte.

## Rückmeldungen aus dem Test vom 10. September 2026

Die Liste des Nutzers umfasste 20 Punkte. 17 davon sind umgesetzt und am Emulator oder durch Tests
belegt. Diese drei sind offen oder nur teilweise geschlossen:

### 1. Ergebnisansicht: Scrollen „rechts nicht, nur bis zur Hälfte“ — Ursache nie reproduziert

- **Stelle:** `app/src/main/java/app/sourcescribe/ui/TranscriptScreen.kt`
- **Was gemacht wurde:** Die Ansicht war ein `Dialog` mit fester Größe (94 % Breite, 92 % Höhe). Sie ist
  jetzt ein eigener Vollbildschirm mit `windowInsetsPadding(WindowInsets.safeDrawing)`, einer einzigen
  `LazyColumn` und einer festen Fußleiste. Damit sind feste Bruchteilhöhen und verdeckte Ränder als
  mögliche Ursachen weg.
- **Was inzwischen belegt ist:** Am 11. September 2026 auf `emulator-5556` mit einem echten Transkript
  von 286 Abschnitten geprüft: Wischen am rechten Rand scrollt, und die Liste läuft bis zum letzten
  Abschnitt bei 18:25 durch. Die Volltextsuche filtert dabei weiter korrekt, auch bei offener Tastatur.
- **Was fehlt:** Querformat und ein zweites Gerät. Der ursprünglich gemeldete Ablauf wurde damit nicht
  reproduziert; ob er auf dem Gerät des Nutzers noch auftritt, ist ungeprüft.

### 2. Untere Aktionsschaltfläche „könnte mehr Platz vertragen“

- **Stelle:** `NewSourceScreen.kt` (`Check source` / `Confirm and start`), `TranscriptScreen.kt` (Fußleiste)
- **Stand:** Alle Hauptschaltflächen sind jetzt mindestens 52 dp hoch und über die volle Breite. Ob das
  dem entspricht, was gemeint war, ist nicht rückgefragt worden — die Formulierung lässt offen, ob es um
  Höhe, Abstand zum Navigationsbalken oder um die Erreichbarkeit mit dem Daumen ging.
- **Was fehlt:** Rückfrage oder ein Screenshot-Vergleich vorher/nachher.

### 3. AssemblyAI EU/USA: Aussage im Glossar nicht gegen die aktuelle Anbieterdokumentation geprüft

- **Stelle:** `app/src/main/res/values/strings.xml`, `help_region_body`
- **Was belegt ist:** Der Adapter benutzt tatsächlich zwei verschiedene Basisadressen
  (`api.assemblyai.com` gegenüber `api.eu.assemblyai.com`, `core/.../providers/AssemblyAiAdapter.kt`),
  und OpenAI wie Groq kennen nur `Region.US`. Das steht so im Glossar und stimmt.
- **Was unbestätigt ist:** Die Aussage, dass ein AssemblyAI-Schlüssel an genau eine Region gebunden ist
  und ein Aufruf in der falschen Region als Authentifizierungsfehler endet. Das entspricht dem bekannten
  Produktzuschnitt, wurde in dieser Sitzung aber nicht gegen die aktuelle AssemblyAI-Dokumentation
  nachgeprüft. Zum Schließen: Primärquelle lesen, Satz bestätigen oder korrigieren, Datum im Text nennen.

## Offene Reviewfunde

### 4. Interne Integritätscodes ohne eigenen Text (mittel)

- **Stelle:** `app/src/main/java/app/sourcescribe/ui/Labels.kt`, `messageText`
- **Stand:** Die Familien, die ein Nutzer im Alltag trifft, haben eigene Texte: ungültige Links,
  Anbieterfehler, Extraktionsfehler, Engine-Updates, Audioimport, und seit dem zweiten Reviewdurchgang
  neun der zehn Codes der lokalen Audiovorbereitung (`AudioPreparationCode`). Der zehnte,
  `AUDIO_STORAGE_FAILED`, teilt weiter den Sammeltext `reason_storage` mit sechs anderen Präfixen;
  das ist tragbar, weil ein Speicherfehler in jedem dieser Fälle die Ursache benennt. Der
  `else`-Zweig zeigt
  weiterhin „Vorgang konnte nicht abgeschlossen werden“ plus den technischen Status.
- **Was offen ist:** Rund 45 interne Integritätscodes fallen weiter in diesen Zweig, etwa
  `SUBMISSION_BINDING_MISMATCH`, `PREPARED_AUDIO_CHANGED`, `ARTIFACT_BINDING_MISMATCH`,
  `RAW_HASH_MISMATCH`, `PATH_ESCAPE`. Sie bedeuten alle „ein interner Bindungs- oder Prüfschritt hat
  nicht gepasst, es wurde nichts stillschweigend akzeptiert“ und sind im normalen Betrieb sehr selten.
  Ein gemeinsamer erklärender Satz plus technischer Status wäre besser als der jetzige generische Text.

### 5. Vorabprüfung und tatsächliche Grenze messen zwei verschiedene Dauern (mittel, teilweise unbestätigt)

- **Stelle:** `core/.../JobLimits.exceeds` (Vorschau) gegenüber `app/.../data/SttStep.kt`
  (`preparation.probe`)
- **Voraussetzung:** Die Vorschau prüft die von yt-dlp gemeldete Videolänge; die eigentliche Grenze prüft
  die gemessene Länge der heruntergeladenen Tonspur. Beide vergleichen strikt mit `>` ohne Toleranz.
- **Folge:** Weichen die beiden Werte um Millisekunden ab, kann ein Auftrag die Vorschau bestehen und
  danach mit `AUDIO_LONGER_THAN_LIMIT` enden. Das kostet einen Download, aber kein Geld: der Abbruch
  liegt vor `SUBMIT`. Umgekehrt kann die Vorschau einen Auftrag blockieren, der durchgelaufen wäre.
- **Unbestätigt:** Wie oft und wie stark die beiden Werte in der Praxis auseinanderlaufen, wurde nicht
  gemessen. Zum Schließen: an mehreren echten Videos beide Werte protokollieren und daraus entscheiden,
  ob eine Toleranz (analog `PREPARED_DURATION_TOLERANCE_MS`) gerechtfertigt ist. Eine Toleranz weitet den
  Kostenrahmen minimal auf und darf nicht ohne diese Messung eingeführt werden.

### 6. Vier Bytes Streuwert im erzeugten Dateinamen (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptExporter.kt`, `shortId`
- **Stand:** Erzeugte Namen tragen vier Bytes SHA-256 des Artefakt- beziehungsweise Exportbezeichners.
  Bei rund 77 000 Exporten insgesamt liegt die Wahrscheinlichkeit irgendeiner Kollision bei etwa 50 %.
  Für den Einzelnutzerbetrieb ist das unkritisch, aber es ist eine bewusste Annahme, keine Garantie.
- **Was fehlt:** Entweder mehr Bytes oder ein Test, der die Annahme dokumentiert.

### 7. Gelöschtes Exportdokument bleibt als belegter Name gezählt (niedrig)

- **Stelle:** `app/src/main/java/app/sourcescribe/data/ExportStore.kt`, `reconcile` gegenüber der
  `repeated`-Prüfung in `export`
- **Voraussetzung:** Ein Export ist fehlgeschlagen oder sein Dokument wurde außerhalb der App gelöscht.
  `reconcile` behält `documentUri` auf der `FAILED`-Zeile.
- **Folge:** Die Wiederholungsprüfung zählt diese Zeile weiter als belegten Namen. Ein selbst vergebener
  Name bekommt dann beim nächsten Export einen Unterscheidungszusatz, obwohl die Datei nachweislich weg
  ist. Es entsteht keine Kollision und kein Datenverlust, nur ein unnötiger Zusatz im Dateinamen.
- **Was fehlt:** Entscheidung, ob `reconcile` `documentUri` bei nachgewiesen fehlender Datei löschen
  soll. Das berührt die Exportreparatur und braucht einen eigenen Test.

### 8. AssemblyAI meldet Sprache und Modell ohne Längen- oder Zeichenprüfung (niedrig, unbestätigt)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/providers/AssemblyAiAdapter.kt`,
  `optionalMetadataString` für `speech_model_used`
- **Stand:** Die gemeldete Sprache wird seit dem zweiten Reviewdurchgang auf eine Sprachkennung geprüft
  (`en`, `en_us`, `zh-CN`). Für `speech_model_used` gilt weiterhin nur „nicht leer“: der Adapter
  übernimmt jede Zeichenkette in beliebiger Länge nach `provenance.reportedModel`, wo sie angezeigt wird.
  Zum Vergleich prüft `SyncTranscriptParser` (OpenAI, Groq) die gemeldete Sprache auf genau zwei
  ASCII-Buchstaben.
- **Unbestätigt:** Ob AssemblyAI je etwas anderes als einen kurzen Modellnamen zurückgibt. Ein echter
  Anbieterlauf hat in diesem Projekt noch nicht stattgefunden.
- **Was fehlt:** Eine Längenobergrenze mit Warnung statt stiller Übernahme, plus ein Contract-Test mit
  einer überlangen Antwort.
- **Nicht mehr offen, damit es niemand zweimal baut:** Die Warnliste selbst ist seit dem dritten
  Reviewdurchgang gedeckelt (`core/.../providers/Warnings.kt`). Vorher erzeugte jeder fehlerhafte
  Eintrag einer Antwort eine eigene Warnzeile, sodass eine Antwort mit Millionen Einträgen die Liste
  mitwachsen ließ; das betraf auch `SyncTranscriptParser` und damit OpenAI und Groq.

### 9. Warncodes in der Ergebnisansicht — erledigt am 11. September 2026

Bleibt als Nummer stehen, damit Verweise aus anderen Dokumenten gelten. Aus über 50 Codefamilien
werden jetzt zwölf Sätze; die Zuordnung liegt in `core/.../TranscriptWarnings.kt` und ist ohne Gerät
testbar. Die rohen Codes stehen weiterhin unter den Details der Ergebnisansicht. Ein Code, für den es
keinen Satz gibt, wird weiterhin technisch angezeigt statt verschluckt, und ein Eintrag, der gar nicht
wie ein Code aussieht, wird unverändert durchgereicht.

### 10. Ein Fehlertext deckt zwei verschiedene Ursachen ab (niedrig, unbestätigt)

- **Stelle:** `app/src/main/java/app/sourcescribe/ui/Labels.kt`, Zweig
  `AUDIO_INVALID_INPUT` / `AUDIO_INPUT_NOT_FILE`
- **Stand:** Beide zeigen „Die Audiodatei dieses Auftrags war nicht lesbar.“ Für
  `AUDIO_INPUT_NOT_FILE` stimmt das. `AUDIO_INVALID_INPUT` entsteht dagegen in
  `AudioPreparation.validateChunkRequest` bei einem unmöglichen Parameter, also bei einem
  Programmierfehler, nicht bei einer kaputten Datei.
- **Unbestätigt:** Ein Auslöser in normaler Nutzung wurde nicht gefunden; die Prüfung schützt gegen
  internen Fehlgebrauch. Deshalb kein eigener Text, sondern hier notiert.
- **Was fehlt:** Entweder ein eigener Text in der Familie der internen Integritätscodes (Punkt 4) oder
  der Nachweis, dass der Code nie beim Nutzer ankommt.

### 11. Eine Änderung an der Warnungserzeugung sperrt die Wiederverwendung bezahlter Abschnitte (niedrig)

- **Stelle:** `app/src/main/java/app/sourcescribe/data/SttStep.kt`, `prepareMissingRetry()`,
  Zeile 236 (`artifact.warningCount != partial.warnings.size`) und Zeile 336-338
- **Voraussetzung:** Ein Auftrag steht auf `PARTIAL_SUCCESS`. Für einen bereits bezahlten Abschnitt
  hatte die Antwort mehr als 64 verschiedene Warnungen. Danach wird die App auf einen Stand mit der
  Warndeckelung aktualisiert.
- **Ablauf:** „Nur fehlende Abschnitte erneut versuchen“ parst die gespeicherten Rohdaten neu und
  vergleicht das Ergebnis mit dem, was beim ersten Lauf gespeichert wurde. Die neue Fassung liefert
  65 Einträge statt der alten Zahl, der Vergleich schlägt fehl, und der Weg wird mit
  `MISSING_RETRY_DATA` dauerhaft verweigert.
- **Was daran schon in Ordnung ist:** Der Ausfall ist sicher, nicht heimlich. `JobCoordinator.retry`
  fängt den Fehler vor der Transaktion ab, es entstehen keine Kosten und keine stille Wiederholung,
  und der Teilstand bleibt erhalten. `SttMissingRetryTest.retainedProviderWarningsMustMatchPartialArtifact`
  deckt genau diesen Mechanismus mit `assertEquals(0, requestAttempts.get())` ab.
- **Heute nicht erreichbar:** In diesem Projekt hat noch kein echter Anbieterlauf stattgefunden (siehe
  Punkt 8), es gibt also keinen gespeicherten Teilstand mit Anbieterwarnungen. Erreichbar wird es,
  sobald echte Läufe existieren und danach die Warnungserzeugung erneut verändert wird.
- **Was fehlt:** `TranscriptDocument.normalizationVersion` (`core/.../Domain.kt:163`) steht fest auf
  `"1"`, wird nirgends erhöht und nirgends geprüft — nur im Export angezeigt. Es ist offenbar genau
  für diesen Fall gedacht. Zum Schließen: einen kurzen Architekturentscheid schreiben, was ein
  Versionsunterschied bedeuten soll (Vergleich überspringen? Teilstand neu normalisieren?), dann
  umsetzen. Das berührt die Job-Semantik und darf nicht nebenbei in einem anderen Fix passieren.

### 12. `abr` verdrängt `tbr`, auch wenn sein Wert dann verworfen wird (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/ExtractorMetadata.kt`, `rate`
- **Voraussetzung:** Ein Format trägt in `abr` eine Zahl außerhalb von 1..10000 und in `tbr` eine
  brauchbare.
- **Erwartet gegen tatsächlich:** Erwartet wäre, dass die brauchbare Angabe gewinnt. Tatsächlich
  gewinnt `abr` bereits dadurch, dass überhaupt eine Zahl darin steht; scheitert sie dann an der
  Bereichsprüfung, bleibt `bitrateKbps` leer, obwohl `tbr` gereicht hätte. Folge: keine
  Größenschätzung für diese Spur.
- **Warum es so steht:** Aufgefallen beim Schließen der Herkunftsangabe in Runde 4. Welche Angabe
  eine Bitrate liefert, ist eine Datenänderung; sie gehört nicht in einen Fix für die
  Herkunftszeile und ist deshalb hier festgehalten statt nebenbei geändert.
- **Was fehlt:** Entscheidung, ob die Auswahl auf „erste brauchbare Angabe“ umgestellt wird, plus
  Test. Der neue Test `provenanceDoesNotNameANumberFieldWhoseValueWasRejectedAsImplausible` hält das
  heutige Verhalten fest und würde bei einer Umstellung bewusst angepasst.

## Bewusste Entscheidungen, die wie Fehler aussehen

### Ein gewöhnlicher Start-Tap nach „Neu vorbereiten“ genügt für die Freigabe

Ein Review der vierten Runde hat gemeldet, dass ein wieder vorbereiteter Auftrag mit einem einzigen
Antippen von „Start“ kostenpflichtig losläuft, weil `configurationForStart` die Freigabe aus Modus,
Modell und passendem gespeichertem Schlüssel neu berechnet und den bisherigen Wert nie liest. Der
Mechanismus stimmt, die Einordnung als Defekt nicht.

So ist es gewollt, und zwar app-weit, nicht nur beim erneuten Vorbereiten: Das Antippen von „Start“
**ist** die bewusste Freigabe, gebunden an genau diese Konfiguration. `ViewRulesTest.
deliberateStartBindsApprovalToModeCredentialProviderAndRegionWithoutChangingTheDraft` hält das seit
längerem fest. Genau darauf beruht die Behebung des blockierenden Nutzerfehlers vom 10. September:
Ein Auftrag, der an seiner eigenen Längengrenze hängen geblieben ist, soll mit einem geänderten Wert
wieder startbar sein, ohne Anbieter und Schlüssel erneut auszuwählen. Die Invariante verlangt keine
zusätzliche Hürde, sondern dass nichts **still** wiederholt wird — ein Tap ist nicht still.

Was an der Meldung berechtigt war: Der Test prüfte nur den Entwurf vor diesem Tor, und sein Kommentar
ließ sich so lesen, als brauchte es mehr als das gewöhnliche Antippen. Beides ist korrigiert; der Test
prüft jetzt mit einem wirklich registrierten Schlüssel, was am Tor passiert, und zusätzlich, dass ohne
passenden Schlüssel gar nichts freigegeben wird.

### Nicht erreichbare `PROVIDER_`- und `RESPONSE_`-Zweige in `messageText`

Ein Review hat gezeigt, dass die meisten `PROVIDER_*`- und `RESPONSE_*`-Zweige heute nicht erreichbar
sind, weil `SttStep` die Anbietfehler in den einzelnen Phasen selbst behandelt und den nackten Code
speichert. Die Zweige bleiben trotzdem stehen: Sie sind das Auffangnetz für einen `ProviderError`, der
einer Phasenbehandlung entkommt, und die Schrittangabe („Beim Absenden an den Anbieter“) wäre in genau
diesem Fall richtig. Anders lag der Fall bei den acht `AUDIO_*`-Zweigen, die nach `ExtractionFailure`
modelliert waren: dort gab es keinen denkbaren Erzeuger, und sie sind entfernt.

## Wartungshinweise, die keine Defekte sind

### Instrumentierungstests laufen nicht über Gradle aus WSL heraus

Der Gradle-Lauf findet in WSL statt, der Emulator läuft unter Windows. Der Windows-`adb`-Server hört nur
auf `127.0.0.1`, und WSL erreicht diese Adresse nicht (nachgemessen am 11. September 2026:
`ADB_SERVER_SOCKET=tcp:172.20.112.1:5037` läuft in `Connection timed out`). `connectedDebugAndroidTest`
ist deshalb lokal nicht ausführbar, ohne den Windows-`adb`-Server neu zu starten — was den Emulator des
Nutzers mit abhängen würde. Der gangbare Weg ohne Eingriff in fremde Geräte:

```bash
bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest
```

Danach unter Windows beide APKs installieren und die Instrumentierung direkt starten:

```powershell
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5556 shell am instrument -w -r app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
```

### Abhängigkeitsprüfung nach jedem Versionswechsel neu erzeugen

`gradle/verification-metadata.xml` enthält SHA-256-Prüfsummen für jedes aufgelöste Artefakt. Ein
Versionswechsel im Versionskatalog scheitert deshalb zunächst mit `dependency verification failed`. Der
Ablauf ist:

```bash
bash tools/build-local.sh --write-verification-metadata sha256 :core:test :app:compileDebugKotlin :app:lintDebug
```

Danach den Diff der Datei ansehen: Es dürfen nur Einträge für die neuen Versionen hinzukommen. Werden
bestehende Einträge entfernt, hat der Lauf zu wenige Konfigurationen aufgelöst; dann mit mehr Tasks
wiederholen, statt den Verlust zu übernehmen.

### Lint meldet neue Bibliotheksversionen als Fehler

`lint { warningsAsErrors = true }` macht aus `GradleDependency` einen Fehler. Sobald Google eine neue
Compose-BOM oder Room-Version veröffentlicht, schlägt der Lint-Lauf fehl, ohne dass sich am Code etwas
geändert hat. Das ist beabsichtigt: Die Regel erzwingt, dass Aktualisierungen tatsächlich gemacht werden.
Der zugehörige Ablauf ist der Abschnitt darüber.
