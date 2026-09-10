# Bekannte Probleme und offene Punkte

**Stand:** 11. September 2026. Diese Datei ist für den nächsten Agenten gedacht und listet ausschließlich,
was **nicht** vollständig erledigt ist. Was hier nicht steht, ist entweder erledigt oder in
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
- **Was fehlt:** Der ursprünglich gemeldete Ablauf wurde nie nachgestellt, weil kein langes Transkript
  auf dem Gerät vorlag. Der Umbau ist also eine begründete Änderung an der wahrscheinlichsten Ursache,
  kein nachgewiesener Fix. Zum Schließen: ein Transkript mit mehreren hundert Abschnitten öffnen, am
  linken und am rechten Rand wischen, bis zum Ende scrollen, in Quer- und Hochformat.

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

### 4. Fehlercodes ohne eigenen Text (mittel)

- **Stelle:** `app/src/main/java/app/sourcescribe/ui/Labels.kt`, `messageText`
- **Stand:** Die Familien, die ein Nutzer im Alltag trifft, haben jetzt eigene Texte: ungültige Links,
  Anbieterfehler (Schlüssel, Kontingent, Netz, Serverfehler), Extraktionsfehler, Engine-Updates,
  Audioimport. Der `else`-Zweig zeigt weiterhin „Vorgang konnte nicht abgeschlossen werden“ plus den
  technischen Status.
- **Was offen ist:** Rund 45 interne Integritätscodes fallen weiter in diesen Zweig, etwa
  `SUBMISSION_BINDING_MISMATCH`, `PREPARED_AUDIO_CHANGED`, `ARTIFACT_BINDING_MISMATCH`,
  `RAW_HASH_MISMATCH`, `PATH_ESCAPE`. Sie bedeuten alle „ein interner Bindungs- oder Prüfschritt hat
  nicht gepasst, es wurde nichts stillschweigend akzeptiert“ und sind im normalen Betrieb sehr selten.
  Ein gemeinsamer erklärender Satz plus technischer Status wäre besser als der jetzige generische Text.

### 5. Vorabprüfung und tatsächliche Grenze messen zwei verschiedene Dauern (mittel, teilweise unbestätigt)

- **Stelle:** `MainViewModel.exceedsLengthLimit` gegenüber `data/SttStep.kt` (`preparation.probe`)
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

### 7. Englisches Glossar setzt Bedienelementnamen ohne Anführungszeichen (niedrig)

- **Stelle:** `app/src/main/res/values-en/strings.xml`, die `help_*_body`-Texte
- **Stand:** Der deutsche Text schreibt „Stabil“, „Nightly“, „Neu vorbereiten“ in Anführungszeichen; der
  englische schreibt dieselben Namen als normalen Fließtext. Damit fehlt im Englischen der Hinweis, dass
  ein Wort den Namen einer Schaltfläche meint und keine Beschreibung ist.
- **Was fehlt:** Die betroffenen englischen Texte durchgehen und dieselben Namen in `"…"` setzen.

### 8. Verlauf: `FINISHED` ohne Ergebnis passt zu keinem Filter außer „Alle“ (unbestätigt)

- **Stelle:** `app/src/main/java/app/sourcescribe/ui/HistoryScreen.kt`, `HistoryFilter.matches`
- **Voraussetzung:** Ein Auftrag mit `ExecutionState.FINISHED` und `Outcome.NONE`.
- **Unbestätigt:** Ob diese Kombination überhaupt entstehen kann, hängt von `JobCoordinator` ab und wurde
  nicht nachverfolgt. Falls ja, verschwindet ein solcher Auftrag aus allen vier Sachfiltern.

## Wartungshinweise, die keine Defekte sind

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
