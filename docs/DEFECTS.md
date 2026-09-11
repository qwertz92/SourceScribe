# Bekannte Probleme und offene Punkte

**Stand:** 12. September 2026, nach dreizehn Runden adversarischer Reviews. Diese Datei ist für den
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
- **Was offen ist:** Mindestens 43 Codes fallen weiter in diesen Zweig. Ausgezählt am 11. September 2026
  aus den Stellen, die die Felder schreiben, die `messageText` liest — `SttStep`, `JobCoordinator`,
  `ExportStore`, `MainViewModel` —, gegen die Codes, die das `when` beantwortet.
- **Diese Auszählung ist nachweislich unvollständig,** gefunden in Runde 9. `JobCoordinator` bildet in
  seinem `catch` die Ausnahmen auf den Fehlercode ab und kopiert dabei `CaptionParseException.reason`,
  `ArtifactFilesException.reason` und `StorageBudgetException.reason` unverändert hinein. Die Codes dieser
  drei Klassen stehen in `core/.../CaptionParser.kt`, `core/.../ArtifactFiles.kt` und
  `app/.../data/StorageBudget.kt` — keine davon war in der Liste oben. `PATH_ESCAPE` und `RAW_HASH_MISMATCH`,
  die zwei Beispiele, die dieser Punkt von Anfang an nannte, kommen genau von dort. Wer den Punkt schließt,
  fängt also bei diesen drei Dateien an und zählt neu; 43 ist eine Untergrenze, keine Zahl.
- **Die Annahme dieses Punktes hält der Auszählung nicht stand:** Sie bedeuten *nicht* alle „ein interner
  Bindungs- oder Prüfschritt hat nicht gepasst“. Ein guter Teil sind gewöhnliche Betriebsausgänge —
  `INTERRUPTED`, `REMOTE_TIMEOUT`, `NO_TRANSCRIPT`, `ENGINE_NOT_AVAILABLE`, `AUDIO_TRACK_MISSING` —, für
  die dieser Satz schlicht falsch wäre. Ein gemeinsamer Satz für den ganzen Zweig würde damit genau den
  Fehler machen, den diese Reviewschleife sonst jagt: eine Ursache behaupten, die nicht die eingetretene ist.
- **Zwei Codes standen in dieser Aufzählung zu Unrecht, gefunden in Runde 9.** `RESPONSE_NOT_READY`
  (`SttStep.kt:985`) entsteht aus `state != RESPONSE_SAVED` **oder** aus einem Fehlschlag von
  `bindingMatches` — der zweite Fall ist wörtlich eine Bindungsprüfung, dieser Code gehört also in beide
  Lager und braucht einen Satz, der beide trägt. Bei `SOURCE_NOT_FOUND` ist offen, ob er im Normalbetrieb
  überhaupt erreichbar ist: Beide beteiligten Fremdschlüssel stehen auf `RESTRICT`, und `JobCoordinator`
  wartet vor dem Löschen auf den Abbruch der Arbeit. Das spricht eher für ein internes Prüfproblem als für
  einen gewöhnlichen Ausgang; nachgewiesen ist keins von beidem.
- **Was das für die Lösung heißt:** Die Integritätscodes brauchen eine ausdrücklich aufgezählte Liste — kein
  Namensmuster, denn ein Muster beansprucht jeden künftigen Code, der zufällig auf dasselbe Wort endet, was
  Punkt 16 für `RESPONSE_` bereits als Risiko führt. Die Betriebsausgänge brauchen eigene Texte oder bleiben
  bewusst beim generischen. Elf weitere Namen in der Auszählung sind Exportzustände und Prüfwerte, von denen
  erst zu belegen ist, dass sie `messageText` überhaupt erreichen.

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

### 6. Vier Bytes Streuwert im erzeugten Dateinamen — erledigt am 11. September 2026

Bleibt als Nummer stehen, damit Verweise gelten. Der Streuwert ist jetzt sechs Bytes breit
(`TranscriptExporter.SHORT_ID_BYTES`), womit dieselbe Wahrscheinlichkeit erst jenseits von sechzehn
Millionen Namen liegt. Der frühere Eintrag übertrieb: Um denselben Namen konkurrieren nur Artefakte, die
schon in Tag, Sprache und Quellbezeichner übereinstimmen. Genau dieses Argument war aber der Grund, warum
die Breite eine Annahme statt einer Schranke war; mit sechs Bytes braucht man es nicht mehr.
`theIdentityInAGeneratedNameIsADigestOfAStatedWidth` hält die Breite fest und dazu, dass es ein Streuwert
und kein Präfix ist.

### 7. Gelöschtes Exportdokument bleibt als belegter Name gezählt — erledigt am 11. September 2026

Bleibt als Nummer stehen, damit Verweise gelten. `reconcile` löscht `documentUri` jetzt genau dann, wenn
der Anbieter gefragt wurde und geantwortet hat, dass das Dokument weg ist. Der naheliegende Fix — das Feld
bei jedem Fehlschlag leeren — wäre selbst ein Defekt gewesen: Eine beim Schreiben abgebrochene Zeile trägt
eine wirklich vorhandene Datei, weil die URI erst nach erfolgreichem `createDocument` gespeichert wird. Der
Prüfprovider der Tests lehnt einen kollidierenden Namen ab, statt automatisch umzubenennen; ein solcher Fix
hätte den zweiten Export also nicht nur kosmetisch, sondern ganz scheitern lassen. Zwei Tests halten beide
Fälle auseinander: `aChosenNameIsFreeAgainOnceItsDocumentIsProvenGone` und
`anInterruptedExportKeepsHoldingItsNameBecauseItsFileIsThere`. Offen bleibt Punkt 13.

### 8. AssemblyAI meldet Modell ohne Längenprüfung — erledigt am 11. September 2026

Bleibt als Nummer stehen, damit Verweise gelten. `speech_model_used` wird jetzt auf dieselbe Obergrenze von
128 Zeichen geprüft, die der Parser für OpenAI und Groq schon anwendete, und bei Überlänge mit
`REPORTED_MODEL_TOO_LONG` abgelehnt statt angezeigt. Nicht gekürzt: ein abgeschnittener Modellname wäre ein
Wert, den niemand gemeldet hat. `aReportedModelIsRefusedRatherThanShownAtAnyLength` prüft beide Seiten der
Grenze. Die gemeldete Sprache war schon vorher auf eine Sprachkennung geprüft.

Weiter unbestätigt bleibt, ob AssemblyAI je etwas anderes als einen kurzen Modellnamen liefert: ein echter
Anbieterlauf hat in diesem Projekt nicht stattgefunden. Die Prüfung hängt nicht davon ab — sie verhindert,
dass eine unbekannte Antwort ungeprüft als Modellangabe erscheint.

### 9. Warncodes in der Ergebnisansicht — erledigt am 11. September 2026

Bleibt als Nummer stehen, damit Verweise aus anderen Dokumenten gelten. Aus über 50 Codefamilien
werden jetzt dreizehn Sätze; die Zuordnung liegt in `core/.../TranscriptWarnings.kt` und ist ohne Gerät
testbar. Die Zahl war hier und in [Restarbeiten](NEXT_STEPS.md) bis Runde 7 mit zwölf angegeben, obwohl
`SECTION_ALIGNMENT` schon in Runde 5 als dreizehnte Gruppe dazugekommen war. Seit Runde 7 ist die
Zuordnung Daten statt eines `when`, und jeder einzelne Familienname wird in fünf Schreibweisen durch die
Zusammenfassung geführt — vorher kamen 21 der 59 Namen in keinem Test vor und 32 nicht durch die
Zusammenfassung. Seit Runde 8 steht
neben der Zuordnung eine zweite, aus den Erzeugern abgeschriebene Liste, damit ein gegenüber dem Erzeuger
falsch geschriebener Name auffällt statt beiden Seiten gleichzeitig zu entgehen. Die rohen Codes stehen weiterhin unter den Details der Ergebnisansicht. Ein Code, für den es
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
- **Am 11. September 2026 ist genau so eine Änderung passiert, zweiter Fall dieser Art:**
  `SyncTranscriptParser` meldet für ein vorhandenes, aber unbrauchbares `model`-Feld jetzt
  `REPORTED_MODEL_MALFORMED`, wo es vorher schwieg. Eine gespeicherte Antwort mit `"model":""` liefert
  beim Neuparsen also eine Warnung mehr als die gezählte, und der Weg „nur fehlende Abschnitte“ wird
  für diesen Teilstand dauerhaft verweigert. Der Ausfall bleibt der sichere aus dem Absatz darüber:
  kein Kostenrisiko, keine stille Wiederholung, Teilstand erhalten. Die Änderung ist trotzdem richtig,
  weil sie eine Meldepflicht erfüllt, und sie zeigt, dass dieser Punkt keine Einzelfallfrage ist: jede
  künftige Korrektur an der Warnungserzeugung trifft ihn wieder, solange `normalizationVersion` nicht
  entschieden ist.
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

### 13. Ein ungeprüftes Exportdokument wird gemeldet, als wäre es nachweislich weg (niedrig)

- **Stelle:** `app/src/main/java/app/sourcescribe/data/ExportStore.kt`, `reconcile`, der
  `IllegalArgumentException`-Zweig
- **Voraussetzung:** Die gespeicherte Dokument-URI lässt sich beim Anbieter gar nicht abfragen, etwa weil
  sie keine brauchbare Dokument-URI mehr ist.
- **Erwartet gegen tatsächlich:** Erwartet wäre eine Aussage über das, was festgestellt wurde. Tatsächlich
  trägt die Zeile denselben Grund `EXTERNAL_DOCUMENT_MISSING` wie ein wirklich gelöschtes Dokument, und der
  Nutzer liest „Die exportierte Datei ist nicht mehr da“, obwohl nur die Prüfung fehlgeschlagen ist. Das
  ist derselbe Fehler wie ein ungewisser Ausgang, der als sicherer dargestellt wird.
- **Warum es so steht:** Beim Schließen von Punkt 7 aufgefallen. Der Namensteil ist gelöst, weil der
  ungeprüfte Fall seine URI behält und der Name damit vorsichtshalber als belegt gilt. Der Text braucht
  aber einen eigenen Fehlercode und je einen Satz in beiden Sprachen; das ist eine eigene Änderung.
- **Was fehlt:** Zweiter Fehlercode, zwei Texte, ein Test mit einer nicht abfragbaren URI.

### 14. Alte Artefakte tragen den alten Untertitelcode und bekommen weiter den Zeitmarkensatz (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/CaptionParser.kt` gegen bereits gespeicherte
  `TranscriptDocument.warnings`
- **Voraussetzung:** Ein Transkript, das vor dem 11. September 2026 aus einer Untertitelspur entstand und
  dabei `MALFORMED_SEGMENTS_<n>` aufgezeichnet hat.
- **Erwartet gegen tatsächlich:** Der Untertitelfall heißt jetzt `MALFORMED_CAPTION_SEGMENTS_<n>`, weil der
  Anbieterparser denselben Namen mit anderer Bedeutung benutzte. Neue Aufträge bekommen den richtigen Satz;
  ein alter Eintrag fällt weiter in die Zeitmarkengruppe und sagt damit „Zeitmarken fehlen“, obwohl der Text
  dieser Stelle fehlt.
- **Warum es so steht:** Der Index, der beide Quellen unterscheiden würde, wird beim Zusammenfassen
  absichtlich entfernt. Eine Umschrift gespeicherter Warnlisten berührt `normalizationVersion` und hängt an
  derselben Entscheidung wie Punkt 11.
- **Was fehlt:** Entscheidung zusammen mit Punkt 11, ob gespeicherte Warnlisten je migriert werden.

### 15. Eine Notiz aus reinen Großbuchstaben würde wie ein Code behandelt (niedrig, unbestätigt)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptWarnings.kt`, `looksLikeCode`
- **Voraussetzung:** Jemand legt später freien Text in `TranscriptDocument.warnings`, der nur aus
  Großbuchstaben, Ziffern und Unterstrichen besteht.
- **Erwartet gegen tatsächlich:** So ein Text würde am ersten Doppelpunkt abgeschnitten und als technischer
  Status gerahmt. Heute erreichbar ist das nicht: Die einzige Stelle, die freien Text in diese Liste legt,
  ist der UI-Prüfdatensatz, und dessen zwei Sätze enthalten Leerzeichen und Kleinbuchstaben.
- **Was fehlt:** Nichts, solange die Liste Codes trägt. Wird sie je für Text vorgesehen, braucht sie ein
  eigenes Feld statt einer Formprüfung.

### 16. Der `RESPONSE_`-Zweig ist nicht gegen einen künftig falsch benannten Code geschützt (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptWarnings.kt`, der `else`-Zweig von
  `group`
- **Voraussetzung:** Jemand nennt eine neue Warnung `RESPONSE_...`, die keinen verlorenen Abschnitt bedeutet.
- **Erwartet gegen tatsächlich:** Sie würde stillschweigend als verlorener Abschnitt gemeldet, also als
  größerer Schaden, als entstanden ist. Heute stimmt die Regel: Jeder real erzeugte `RESPONSE_`-Code
  entsteht in `SttStep` an einer Stelle, die im selben Schritt `missing += chunk.index` setzt — nachgeprüft
  für alle zwölf Werte von `ProviderErrorCode` durch
  `everyRefusalTheProviderGivesForOneSectionCountsAsALostSection`.
- **Was fehlt:** Entweder eine geschlossene Liste statt der Präfixregel oder ein Test, der die Kopplung an
  `missing` am Erzeugungsort festhält.
- **Seit Runde 7:** Die Zahl dieser Familien ist als `TranscriptWarnings.PREFIXED_FAMILY_COUNT` zählbar und
  geht in die Obergrenze von `Warnings` ein. Das ändert nichts an der Zuordnungsfrage hier.

### 17. „Tonspur“ gegen „Audiospur“: Englisch ist einheitlich, Deutsch nicht (niedrig)

- **Stelle:** `app/src/main/res/values/strings.xml`, `step_prepare_audio` und die vier
  `reason_audio_*`-Texte gegen den Rest der Datei
- **Erwartet gegen tatsächlich:** Im Englischen heißt jede der zehn entsprechenden Stellen „audio track“.
  Im Deutschen sagen die Verarbeitungstexte „Tonspur“ und die Auswahltexte „Audiospur“. Eine Lesart dafür
  gibt es — wählbare Spur gegen verarbeiteten Inhalt —, aber `help_audio_track_body` durchbricht sie selbst.
- **Was fehlt:** Entscheidung für ein Wort und eine Durchsicht aller Vorkommen in einem Zug.

### 18. Fällt die ganze Segmentliste aus, behauptet der Satz fehlenden Text (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/providers/SyncTranscriptParser.kt`, `parse`, gegen
  `TranscriptWarnings.group` für `MALFORMED_SEGMENT`
- **Voraussetzung:** Jeder Eintrag der Segmentliste einer Anbieterantwort ist unlesbar.
- **Erwartet gegen tatsächlich:** Ist am Ende kein einziges Segment übrig, ersetzt der Parser die Liste durch
  das vollständige Textfeld der Antwort. Dann fehlt kein Text, sondern nur die Gliederung samt Zeitangaben.
  Die Warnungen sehen aber genauso aus wie bei einem Teilverlust, und der Satz sagt, Stellen fehlten im
  Ergebnis. Für den häufigen Fall — einzelne unlesbare Einträge — ist der Satz richtig, und nur dieser Fall
  ist gefährlich, weil ein Teilverlust leise ist.
- **Warum es so steht:** Die Zuordnung sieht nur die Codeliste und kann nicht erkennen, ob der Ersatz gegriffen
  hat. Sichtbar würde das erst durch eine eigene Warnung am Erzeugungsort.
- **Was fehlt:** Eine Warnung, die den Ersatz der Segmentliste durch das Textfeld festhält, plus eigener Satz.
  Das ist zugleich unabhängig nützlich: Heute ist ein Ergebnis ohne jede Gliederung von einem gegliederten
  nicht zu unterscheiden.

### 19. Die RAW-Regel der Wiederholungsprüfung — erledigt am 11. September 2026

Am selben Tag aufgenommen und geschlossen. Eine Geschwisterzeile im Format `RAW` liefert `null` als
Endung, weil die Endung aus der aufbewahrten Anbieterdatei kommt und nicht in der Exportzeile steht; sie
zählt deshalb vorsichtshalber als belegter Name. Belegt war das nur durch den Einzeltest zum Namensbauer.
`aRawSiblingCountsAsATakenNameBecauseTheRowDoesNotRecordItsExtension` führt jetzt einen echten RAW-Export
aus und danach einen Textexport desselben Artefakts mit selbst vergebenem Namen. Die Zeile bekommt einen
Unterscheidungszusatz, obwohl `.json3` und `.txt` gar nicht kollidieren könnten — das ist die bewusst
vorsichtige Seite, und der Test hält sie fest statt sie zu behaupten. Der Zusatz wäre vermeidbar, wenn die
Exportzeile die geschriebene Endung führte; das ist eine Spalte mehr samt Migration für einen kosmetischen
Gewinn und deshalb nicht gemacht.

### 20. Die zwei Modelllängengrenzen sind unabhängig hartkodiert (niedrig)

- **Stelle:** `core/.../providers/AssemblyAiAdapter.kt` und `core/.../providers/SyncTranscriptParser.kt`,
  je ein privates `MAX_REPORTED_MODEL_LENGTH = 128`; in den Tests je ein eigenes `129`
- **Voraussetzung:** Jemand ändert die Grenze an einer der beiden Stellen.
- **Erwartet gegen tatsächlich:** Erwartet wäre, dass beide Parser dieselbe Antwort auf denselben Wert
  geben — das ist die Zusage, die Runde 7 mit der Reihenfolge der Ablehnungsgründe hergestellt hat.
  Tatsächlich hält nichts die beiden Zahlen zusammen: kein gemeinsamer Wert, kein Test, der sie
  vergleicht. Die Abweichung fiele erst auf, wenn jemand beide Parser mit demselben Wert prüft.
- **Was fehlt:** Ein gemeinsamer Ort für die Grenze. Er gehört nicht in einen der beiden Adapter, und ob
  `ProviderContract` der richtige Platz für eine Parsergrenze ist, ist eine Entscheidung und kein Handgriff
  — deshalb hier notiert statt nebenbei gemacht.
- **Seit Runde 8 gilt dasselbe für die Warnnamen:** `SyncTranscriptParser` schreibt
  `"REPORTED_MODEL_MALFORMED"` und `"REPORTED_MODEL_TOO_LONG"` als rohe Zeichenketten, der AssemblyAI-Adapter
  führt sie als benannte Konstanten mit denselben Werten. Benennt jemand eine Seite um, meldeten die beiden
  Parser dieselbe Lage unter verschiedenen Codes, und die Zusammenfassung ordnete den neuen Namen keiner
  Gruppe mehr zu. Der Test aus Runde 8, der jede Familie gegen eine zweite Liste hält, fängt den zweiten
  Teil davon — nicht aber, dass die zwei Parser auseinanderlaufen.

### 21. Ein Zeitstempel weit außerhalb des Üblichen ergibt kein Datum im Dateinamen (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/TranscriptExporter.kt`, `identitySuffix`,
  `createdAtUtc(document.createdAt).take(10)`
- **Voraussetzung:** `createdAt` liegt außerhalb der Jahre 0000–9999.
- **Erwartet gegen tatsächlich:** Erwartet wird ein Ausschnitt der Form `YYYY-MM-DD`. Tatsächlich schreibt
  `Instant.toString()` solche Jahre mit Vorzeichen und variabler Stellenzahl, sodass die ersten zehn Zeichen
  `+292278994` lauten — Tag und Monat fehlen, und zwei Läufe desselben Tages könnten sich im Namen nicht
  mehr über das Datum unterscheiden.
- **Warum nicht gefixt:** `createdAt` kommt aus der Uhr des Geräts, nicht aus einer Antwort; der Fall setzt
  eine grob falsch gestellte Uhr voraus. Die Bytegrenzen des Namens bleiben eingehalten, weil diese
  Darstellung reines ASCII ist. Hergeleitet aus der dokumentierten Form von `Instant.toString()`, nicht
  ausgeführt — entscheiden würde
  `assertEquals("+292278994", Instant.ofEpochMilli(Long.MAX_VALUE).toString().take(10))`.
- **Der Wert stand hier zuerst mit einer Ziffer zu wenig.** `Long.MAX_VALUE` Millisekunden liegen im Jahr
  292 278 994, also neun Ziffern plus Vorzeichen — genau die zehn Zeichen, die `take(10)` nimmt. Schon an
  der Zeichenzahl war die alte Angabe als falsch erkennbar, ohne irgendetwas auszuführen.

### 22. Die Rückfrage nach der Tonspur zeigt nicht, warum sie gestellt wird (Anzeige niedrig, Herkunftsnachweis war hoch — behoben)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/AudioTracks.kt`, `describe`, zusammen mit der
  Spurauswahl in der Vorbereitungsansicht.
- **Voraussetzung:** Zwei Tonspuren, deren Sprachangaben die Quelle genannt hat, die aber länger als hundert
  Zeichen waren und deshalb nicht in den Datensatz übernommen wurden (`AudioTrack.languageRefused`).
- **Erwartet gegen tatsächlich:** Seit Runde 10 verweigert `automatic` hier die stille Wahl, was richtig
  ist — die Quelle hat die beiden auseinandergehalten. Der Nutzer bekommt dann aber eine Liste, in der
  beide Spuren gar keine Sprache nennen, und keinen Hinweis darauf, dass eine genannt wurde. Die Frage ist
  damit richtig gestellt, aber schwer zu beantworten.
- **Der Herkunftsnachweis sagt es seit Runde 11.** Ein Reviewer hat die zweite Hälfte dieses Punktes
  gefunden, die schwerer wiegt als die erste: `TranscriptExporter` schrieb `language=unknown`, wenn die
  Quelle sehr wohl eine Sprache genannt hatte. Der Export ist der Herkunftsnachweis, und die Offenlegung
  von Sprache und Unsicherheit ist eine Invariante dieses Projekts — dort stand also eine unwahre Aussage.
  Er schreibt jetzt `language=stated-but-unusable`.
- **Warum die Anzeige nicht gefixt ist:** Der Fall ist nicht beobachtet worden und mit echten yt-dlp-Daten
  praktisch nicht erreichbar — reale Sprach-Tags sind unter zwanzig Zeichen lang. Ein eigener Text dafür
  bräuchte zwei neue übersetzte Zeichenketten für einen Zustand, den niemand je sehen wird; die Alternative
  wäre, die Angabe gekürzt und als gekürzt gekennzeichnet mitzuführen, was der Regel „ganz oder gar nicht“
  widerspräche. Die falsche stille Entscheidung ist behoben, die schlechte Frage bleibt.
- **Und warum die Sortierung so bleibt:** `AudioTracks.readingOrder` legt eine Spur mit verworfener Sprache
  in dieselbe Gruppe wie eine ohne jede Sprache, ebenfalls in Runde 11 gefunden. Das bleibt absichtlich so,
  solange die Anzeige beide als „Unbekannt“ führt: Würde nach einem Unterschied sortiert, den die Liste
  nicht zeigt, wäre ihre Reihenfolge für den Leser nicht mehr erklärbar. Wer den Text ergänzt, ändert
  beides zusammen.

### 23. Die Höchstdauer steht an drei Stellen als eigene Zahl (niedrig)

- **Stelle:** `core/src/main/kotlin/app/sourcescribe/core/JobLimits.kt` (`MAX_AUDIO_SECONDS = 36_000`) und
  `invalid_duration` in `app/src/main/res/values/strings.xml` sowie `values-en/strings.xml`, beide mit
  „600 Minuten“ im Text.
- **Voraussetzung:** Jede Änderung an der Höchstdauer.
- **Erwartet gegen tatsächlich:** Erwartet wäre eine Zahl, aus der die anderen folgen. Tatsächlich stehen
  drei unabhängige Zahlen da; die beiden Texte lesen nichts aus der Konstante. Eine geänderte Konstante
  hätte die App etwas anderes durchsetzen lassen, als beide Texte versprechen.
- **Was seit Runde 10 gilt:** `JobLimitsTest` nagelt die Konstante auf 36 000 Sekunden und 600 Minuten fest
  und nennt den Textschlüssel im Kommentar, sodass eine Änderung dort einen Test fällt und auf die Texte
  zeigt. Das ist eine Brücke, keine Behebung: Wer die Texte ändert und die Konstante nicht, fällt weiter
  durch kein Netz. Eine Formatzeichenkette mit `%d` aus `MAX_AUDIO_MINUTES` wäre die Behebung.

### 24. Die Quellseite eines Preises wird mitgeführt und nirgends gezeigt (niedrig)

- **Stelle:** `ProviderCapabilities.pricingSource` in
  `core/src/main/kotlin/app/sourcescribe/core/ProviderContract.kt`, gesetzt von allen drei Adaptern.
- **Voraussetzung:** Keine. Der Wert existiert immer.
- **Erwartet gegen tatsächlich:** Neben der Kostenschätzung steht der Tarifstand — ein Datum. Das Datum
  ist genau dann etwas wert, wenn man nachsehen kann, wogegen es geprüft wurde; die Seite dafür liegt im
  Datensatz und erreicht weder Anzeige noch Export noch Diagnose. Eine Suche nach `pricingSource` findet
  drei Zuweisungen und keinen einzigen Leser.
- **Warum es offen bleibt:** Die Hilfe ist ein statischer Text pro Thema und kennt den gewählten Anbieter
  nicht; ein dynamischer Absatz dort wäre eine Änderung am Hilfemodell, keine Zeile. Die Alternative,
  die drei Adressen als Text in `help_cost_body` zu schreiben, würde jede Adresse ein zweites Mal
  behaupten — genau das, wogegen `StatedNumbersTest` angelegt wurde. Gefunden in Runde 12 beim Prüfen
  der Behauptung, Stichtag und Quelle stünden beide beim Betrag.

### 25. Die Uploadgrenze für Groq ist die kleinere von zwei Stufen (niedrig)

- **Stelle:** `GroqAdapter.MAX_UPLOAD_BYTES` in
  `core/src/main/kotlin/app/sourcescribe/core/providers/GroqAdapter.kt`.
- **Voraussetzung:** Ein bezahlter Groq-Schlüssel („dev tier“) und eine Datei zwischen 25 und 100 MB.
- **Erwartet gegen tatsächlich:** Groq dokumentiert (nachgelesen am 11. September 2026) 25 MB für die
  kostenlose und 100 MB für die bezahlte Stufe. Die App kennt die Stufe eines selbst mitgebrachten
  Schlüssels nicht und hält deshalb alle an die kleinere; ein zahlender Nutzer bekommt eine lokale
  Ablehnung für eine Datei, die der Anbieter angenommen hätte.
- **Warum es so bleibt:** Die Gegenrichtung ist schlechter. Wer die Grenze anhebt, verwandelt eine lokale
  Ablehnung in einen Fehlschlag beim Anbieter, und der ist bei einem kostenpflichtigen Dienst die teurere
  der beiden Auskünfte. Die Behebung wäre eine Angabe der Stufe in den Zugangsdaten, nicht eine größere
  Zahl. Seit Runde 12 sagen Kommentar und `StatedNumbersTest`, dass dies eine Entscheidung dieses
  Programms ist und keine Zahl des Anbieters.

### 26. „25 MB“ ist bei zwei Anbietern nicht als dezimal oder binär bestimmt (niedrig)

- **Stelle:** `GroqAdapter.MAX_UPLOAD_BYTES` und `OpenAiAdapter.MAX_UPLOAD_BYTES`, beide `25_000_000`.
- **Voraussetzung:** Eine Datei zwischen 25 000 000 und 26 214 400 Byte.
- **Erwartet gegen tatsächlich:** Beide Anbieter schreiben „25 MB“ ohne zu sagen, ob sie dezimal oder
  binär rechnen. Der Code nimmt dezimal, also die kleinere Auslegung, und liegt damit auf der sicheren
  Seite — aber ob der Server bei 25 000 000 oder bei 26 214 400 Byte abschneidet, ist nicht belegt.
- **Warum es offen bleibt:** Das ließe sich nur mit einem echten Request an der Grenze klären, also mit
  einem kostenpflichtigen Aufruf. Als Vermutung gekennzeichnet, nicht als Fund. Aufgeworfen vom lesenden
  Reviewer in Runde 12 und von ihm selbst ausdrücklich als nicht ausgeführt markiert.

### 27. Eine vierte Kostenrechnung, als einzige ohne Zuschläge (niedrig, heute folgenlos)

- **Stelle:** `SyncProviderSupport.validateOptions` in
  `core/src/main/kotlin/app/sourcescribe/core/providers/SyncTranscriptParser.kt`, verwendet von
  `GroqAdapter` und `OpenAiAdapter`.
- **Voraussetzung:** Ein Groq- oder OpenAI-Modell, das einen bepreisten Zusatz bekommt —
  Sprechertrennung oder eine Fachbegriffsliste mit eigenem Stundensatz. Heute gibt es keins.
- **Erwartet gegen tatsächlich:** Vier Stellen dieses Programms rechnen eine Dauer in Geld um. Drei
  addieren die Zuschläge, diese nicht. Dass das heute nichts ändert, ist geprüft und nicht vermutet: Groq
  meldet `diarization = false` für jedes Modell, und OpenAIs einziges diarisierendes Modell hat
  `priceMicrousdPerHour = null` und wird eine Zeile weiter abgelehnt, statt geschätzt zu werden.
- **Warum es offen bleibt:** Die Zuschläge stehen nicht in `ProviderCapabilities`, sondern beim jeweiligen
  Adapter; sie hier einzurechnen hieße, sie in den Vertrag aufzunehmen. Das ist eine Vertragsänderung und
  keine Zeile. Als Vorprüfung bleibt die Stelle ungefährlich — was bindet, ist `SttStep.submit`, das mit
  den Zuschlägen und über den ganzen Abschnittsplan prüft. Seit Runde 13 sagt der Kommentar beides.
  Gefunden vom lesenden Reviewer in Runde 13 auf die Frage, welche Rechnung mehr als einmal im Baum steht.

### 28. Was `tools/check-repository.py` weiterhin nicht liest (niedrig)

- **Stelle:** `_read_text` und `_check_actions` in `tools/check-repository.py`.
- **Voraussetzung:** Je nach Fall: eine UTF-16-Datei ohne Byte-Reihenfolge-Markierung, eine Textdatei über
  einem Mebibyte, oder eine noch nicht versionierte Datei unter `.github/workflows/`.
- **Erwartet gegen tatsächlich:** Drei Reste, nachdem Runde 13 UTF-16 **mit** Markierung geschlossen hat.
  Ohne Markierung ist UTF-16 an den Bytes nicht von einer Binärdatei zu unterscheiden und fällt weiter
  durch. Eine Textdatei über `MAX_SCAN_BYTES` wird bei 1 048 576 Byte gekappt; ein Geheimnis dahinter wird
  nicht gefunden, und der Abschlusszeile ist die Kappung anzusehen (`… read only to 1048576 bytes`), dem
  Exitcode nicht. Und `_check_actions` durchsucht das Dateisystem statt `git ls-files`, prüft eine
  unversionierte Workflow-Datei also auf ungepinnte Actions, während der Geheimnisscan sie nie sieht.
- **Warum es offen bleibt:** Alle drei sind heute leer — keine UTF-16-Datei im Baum, die einzige Datei
  über einem Mebibyte ist die gepackte Extraktor-Engine und echt binär, und `.github/workflows/` enthält
  nur Versioniertes. Die Reihenfolge ist Absicht: Die weitere Richtung — mehr prüfen, nicht weniger — ist
  bei der Actions-Prüfung die sichere. Gefunden vom lesenden Reviewer in Runde 13, der die Funktionen
  außerhalb des Repositorys gegen gebaute Dateien laufen ließ.

### 29. Die Preisseite von OpenAI nennt `whisper-1` nicht (niedrig)

- **Stelle:** `OpenAiAdapter.PRICING_SOURCE` und `PRICE_WHISPER_MICRO_USD_PER_HOUR` in
  `core/src/main/kotlin/app/sourcescribe/core/providers/OpenAiAdapter.kt`.
- **Voraussetzung:** Jemand folgt der Adresse, um die Zahl neben dem Stichtag nachzuprüfen.
- **Erwartet gegen tatsächlich:** Die Zahl stimmt — 0,006 Dollar je Minute sind genau 360 000 Mikro-Dollar
  je Stunde. Die Zeichenfolge `whisper-1` kommt auf der Seite aber überhaupt nicht vor (am 12. September
  2026 im Markup nachgezählt: null Treffer); die Zeile mit diesem Preis heißt „Whisper“ und steht in der
  eingeklappten Hälfte der Tabelle. Wer `whisper-1` sucht, findet nur `gpt-realtime-whisper`, ein anderes
  Modell zu einem anderen Preis. Für `gpt-transcribe` steht die Zeile offen in der Tabelle.
- **Warum es offen bleibt:** Die Seite gehört dem Anbieter. Was dieses Projekt tun kann, ist den letzten
  Schritt auszusprechen statt ihn anzunehmen — das steht seit Runde 13 im Kommentar neben der Zahl.
  Hängt an Punkt 24 weiter oben in dieser Datei:
  Solange die Adresse niemanden erreicht, erreicht auch diese Einschränkung niemanden.

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

### `MALFORMED_SEGMENT` und `MALFORMED_WORD` liegen absichtlich in verschiedenen Gruppen

Beide entstehen in derselben Zeile von `parseEntries`, und beide lassen den Eintrag fallen. Sie sagen dem
Leser trotzdem nicht dasselbe: Die Segmentliste ist der angezeigte Lesetext, ein fehlender Eintrag ist dort
eine fehlende Passage. Die Wortliste trägt nur die Zeiten je Wort und erscheint im Export als Wortzahl; ein
fehlender Eintrag ist dort eine fehlende Zeitangabe. Deshalb gehören `MALFORMED_SEGMENT` und
`MISSING_SEGMENT_TEXT` zu den fehlenden Texten, `MALFORMED_WORD` und `MISSING_WORD_TEXT` zu den Wortzeiten —
obwohl beide Paare aus derselben Zeile derselben Funktion stammen.

### Die 40-Byte-Schranke des Titels trägt die Namenslänge mit

Ein Reviewer hat in Runde 7 als hohen Fund gemeldet, dass `TranscriptExporter.compose` das Budget für den
Titel als Bytegrenze minus der *Zeichenzahl* des Teils berechnet, der überleben muss. Sprache und Quell-ID
reichen je 40 Byte, bei Dreibytezeichen also 13 Zeichen, das Budget fällt um bis zu 52 Byte zu groß aus, der
Name überläuft seine Grenze, und der abschließende Schnitt nimmt das Ende — wo der Streuwert sitzt.

Die Einheitenverwechslung war echt und ist korrigiert. Auslösbar war sie nicht, und das ist der Teil, der
hier stehen muss, weil er sonst in jeder Runde neu als hoher Fund gemeldet wird: `generatedStem` schickt den
Titel vorher durch `safePart` mit dessen Standardwert von 40 Byte. Der breiteste Name, den der Namensbauer
zusammensetzen kann, bleibt damit unter der Grenze von 180 Byte — nachgewiesen mit wieder eingebautem Fehler,
unter dem der zugehörige Test grün durchläuft, während sechs andere fallen.

Daraus folgt umgekehrt: Die Schranke von 40 Byte auf dem Titel ist tragend, nicht kosmetisch. Wer den Anteil
des Titels am Namen erhöht — die naheliegendste nächste Änderung an dieser Datei —, nimmt der Grenze ihren
zweiten Halt. Der Test `noPartOfANameMeasuredInCharactersPushesTheIdentityOutOfIt` ist deshalb keine
Regressionsprobe, sondern eine Schrankenprobe über 648 Namenskombinationen.

### Nicht erreichbare `PROVIDER_`- und `RESPONSE_`-Zweige in `messageText`

Ein Review hat gezeigt, dass die meisten `PROVIDER_*`- und `RESPONSE_*`-Zweige heute nicht erreichbar
sind, weil `SttStep` die Anbietfehler in den einzelnen Phasen selbst behandelt und den nackten Code
speichert. Die Zweige bleiben trotzdem stehen: Sie sind das Auffangnetz für einen `ProviderError`, der
einer Phasenbehandlung entkommt, und die Schrittangabe („Beim Absenden an den Anbieter“) wäre in genau
diesem Fall richtig. Anders lag der Fall bei den acht `AUDIO_*`-Zweigen, die nach `ExtractionFailure`
modelliert waren: dort gab es keinen denkbaren Erzeuger, und sie sind entfernt.

## Wartungshinweise, die keine Defekte sind

### Eine Anbieterzahl wird aus dem Markup gelesen, nicht aus einer Zusammenfassung

Runde 12 hat eine richtige Preisbedingung entfernt, weil die Preisseite über ein zusammenfassendes
Abrufwerkzeug gelesen wurde: Es gibt die Seite an ein kleines Modell und reicht dessen Antwort zurück. Die
Zusatztabelle von AssemblyAI hat eine Spalte je Modell, und in der Zeile „Keyterms Prompting“ steht unter
Universal-2 das Wort **„Included“**. Die Zusammenfassung machte daraus „+$0.05/hr für beide Modelle“ — am
12. September 2026 mit demselben Werkzeug reproduziert, um auszuschließen, dass sich die Seite geändert
hatte.

Wer eine Zahl ändert, die Geld betrifft, holt die Seite selbst und liest ihre Tabelle aus:

```bash
curl -sL --max-time 60 -A "Mozilla/5.0" https://www.assemblyai.com/pricing -o page.html
```

Danach die `<table>`-Blöcke mit Kopfzeile und Zellen ausgeben, statt nach der Zahl allein zu suchen —
welche Spalte eine Zelle trägt, ist hier die ganze Frage. Ein Wort wie „Included“ steht an derselben
Stelle, an der sonst ein Preis steht, und geht in jeder Zusammenfassung als Preis durch.

### Instrumentierungstests laufen nicht über Gradle aus WSL heraus

Der Gradle-Lauf findet in WSL statt, der Emulator läuft unter Windows. Der Windows-`adb`-Server hört nur
auf `127.0.0.1`, und WSL erreicht diese Adresse nicht (nachgemessen am 11. September 2026:
`ADB_SERVER_SOCKET=tcp:172.20.112.1:5037` läuft in `Connection timed out`). `connectedDebugAndroidTest`
ist deshalb lokal nicht ausführbar, ohne den Windows-`adb`-Server neu zu starten — was den Emulator des
Nutzers mit abhängen würde. Der gangbare Weg ohne Eingriff in fremde Geräte:

```bash
bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest :extractor:assembleDebugAndroidTest
```

Danach unter Windows die APKs installieren und beide Instrumentierungssuiten starten:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5556 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5556 install -r -t extractor/build/outputs/apk/androidTest/debug/extractor-debug-androidTest.apk
& $adb -s emulator-5556 shell am instrument -w -r app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5556 shell am instrument -w -r -e sourcescribeEngineUpdate true app.sourcescribe.extractor.test/androidx.test.runner.AndroidJUnitRunner
```

Die erste Zeile gehört dazu: `$adb` stand hier seit dem 7. September in fünf Aufrufen und wurde nirgends
gesetzt, sodass der Block beim Einfügen an der ersten Zeile scheiterte.

**Die zweite Suite gehört dazu, und die Flagge auch.** Das Modul `extractor` hat eigene 39
Instrumentierungstests; die Reviewrunden 6 bis 10 haben nur die 191 des Moduls `app` ausgeführt und deren
Zahl als das Gate berichtet. Ohne `-e sourcescribeEngineUpdate true` überspringt die zweite Suite vierzehn
Tests von `EngineUpdateManagerTest` per Annahme und sieht mit 21 bestanden trotzdem grün aus; mit der
Flagge sind es 35 bestanden, 4 übersprungen, 0 Fehler. Die vier brauchen eine echte Quelle beziehungsweise
ein echtes Release und bleiben `BLOCKED/NOT_RUN`. Einer von ihnen,
`EngineUpdateManagerTest.realReleaseStageActivateAndRollbackSurvivesManagerRestart`, braucht dazu noch
`-e sourcescribeEngineLiveUpdate true` und `-e engineProbeSource <URL>`; der Ablauf in
[NEXT_STEPS.md](NEXT_STEPS.md) nennt alle drei.

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
