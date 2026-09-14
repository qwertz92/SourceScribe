# Bekannte Probleme und offene Punkte

**Stand:** 14. September 2026, nach zweiundzwanzig Runden adversarischer Reviews. Diese Datei ist für den
nächsten Agenten gedacht und listet, was **nicht** vollständig erledigt ist. Ein geschlossener Punkt
behält seine Nummer und einen kurzen Vermerk, damit Verweise aus anderen Dokumenten gültig bleiben. Was hier nicht steht, ist entweder erledigt oder in
[STATUS.md](STATUS.md) beschrieben.

Jeder Eintrag nennt Datei und Stelle, die Voraussetzung, das erwartete gegenüber dem tatsächlichen
Verhalten und was zum Schließen fehlt. Einträge ohne reproduzierbaren Ablauf sind als **unbestätigt**
markiert; sie sind Verdachtsfälle, keine belegten Defekte. Eine Stelle nennt Datei und Funktion oder zitiert den
Ausdruck, nie eine Zeilennummer: Zeilennummern wandern mit jeder Änderung darüber, und `tools/check-repository.py`
weist sie in `docs/` ab.

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
  (`SttStep.normalize`) entsteht aus `state != RESPONSE_SAVED` **oder** aus einem Fehlschlag von
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
- **Seit Runde 17** hat `ENGINE_NOT_AVAILABLE`, eines der Beispiele oben, einen eigenen Text, ohne Schrittangabe,
  weil kein Update-Schritt gescheitert ist, und `ENGINE_SLOTS_IN_USE` ist mit eigenem Text neu hinzugekommen. Neu
  gezählt ist die Untergrenze von 43 nicht.

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

- **Stelle:** `app/src/main/java/app/sourcescribe/data/SttStep.kt`, `prepareMissingRetry()`: der Vergleich
  `artifact.warningCount != partial.warnings.size` und der Vergleich der Warnungen behaltener Abschnitte mit
  `providerWarnings.distinct()`
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
- **Was fehlt:** `TranscriptDocument.normalizationVersion` in `core/.../Domain.kt` steht fest auf
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
  **Runde 13 hat dafür einen falschen Grund in den Code geschrieben** — „kein veröffentlichter Preis“.
  `gpt-4o-transcribe-diarize` ist bepreist, aber je Token: 2,50 und 10,00 Dollar je Million. Die
  „$0.006 / minute“ daneben stehen in einer Spalte, die die Seite selbst „Estimated cost“ überschreibt.
  Eine Dauer lässt sich nicht mit einer Tokenzahl multiplizieren, also gibt es keinen Stundensatz zu
  führen, und `null` ist die richtige Angabe aus einem anderen Grund als dem genannten. Am
  12. September 2026 aus dem Markup der Seite nachgelesen.
- **Warum es offen bleibt:** Die Zuschläge stehen nicht in `ProviderCapabilities`, sondern beim jeweiligen
  Adapter; sie hier einzurechnen hieße, sie in den Vertrag aufzunehmen. Das ist eine Vertragsänderung und
  keine Zeile. Als Vorprüfung bleibt die Stelle ungefährlich — was bindet, ist `SttStep.submit`, das mit
  den Zuschlägen und über den ganzen Abschnittsplan prüft. Seit Runde 13 sagt der Kommentar beides.
  Gefunden vom lesenden Reviewer in Runde 13 auf die Frage, welche Rechnung mehr als einmal im Baum steht.

### 28. Was `tools/check-repository.py` weiterhin nicht liest (niedrig)

- **Stelle:** `_read_text`, `_check_actions` und `_check_notice_versions` in `tools/check-repository.py`.
- **Voraussetzung:** Je nach Fall: eine UTF-16- oder UTF-32-Datei **ohne** Byte-Reihenfolge-Markierung,
  eine Textdatei über einem Mebibyte, oder eine noch nicht versionierte Datei unter `.github/workflows/`.
- **Erwartet gegen tatsächlich:** Drei Reste, nachdem Runde 13 UTF-16 und Runde 14 UTF-32 **mit**
  Markierung geschlossen haben. Ohne Markierung ist beides an den Bytes nicht von einer Binärdatei zu
  unterscheiden und fällt weiter durch. Eine Textdatei über `MAX_SCAN_BYTES` wird bei 1 048 576 Byte
  gekappt; ein Geheimnis dahinter wird nicht gefunden, und der Abschlusszeile ist die Kappung anzusehen
  (`… read only to 1048576 bytes`), dem Exitcode nicht. Und `_check_actions` durchsucht das Dateisystem
  statt `git ls-files`, prüft eine unversionierte Workflow-Datei also auf ungepinnte Actions, während der
  Geheimnisscan sie nie sieht.
- **Was Runde 14 hier geschlossen hat, und warum es schlimmer war als die Lücke davor:** Eine
  UTF-32LE-Markierung lautet `ff fe 00 00`, und ihre ersten zwei Bytes sind genau eine UTF-16LE-Markierung.
  Runde 13 prüfte zwei Bytes, also wurde eine UTF-32LE-Datei als UTF-16 dekodiert — Text mit einem Nullbyte
  zwischen jedem Zeichen, an dem kein Muster greift — und **als gelesen gezählt**. Vor Runde 13 hätte die
  Nullbyte-Probe dieselbe Datei ehrlich als binär gemeldet. Die vier Bytes werden jetzt zuerst geprüft.
- **Was Runde 15 dazu nachgetragen hat:** Das gilt nur für Little-Endian. Eine UTF-32BE-Datei beginnt mit
  `00 00 fe ff`, und die Zwei-Byte-Prüfung der Runde 13 hätte sie nie für UTF-16 gehalten. Den
  Big-Endian-Eintrag in `UTF32_BOMS` durchlief aber kein Test, weil `.encode("utf-32")` auf den Maschinen
  hier die Little-Endian-Markierung schreibt; der Selbsttest enthält jetzt eine UTF-32BE-Datei.
- **Warum der Rest offen bleibt:** Alle drei sind heute leer — keine Datei im Baum trägt eine UTF-16- oder
  UTF-32-Markierung, die einzige Datei über einem Mebibyte ist die gepackte Extraktor-Engine und echt
  binär, und `.github/workflows/` enthält nur Versioniertes. Die Reihenfolge ist Absicht: Die weitere
  Richtung — mehr prüfen, nicht weniger — ist bei der Actions-Prüfung die sichere. Gefunden vom lesenden
  Reviewer in Runde 13 und in Runde 14 erneut, beide Male durch Ausführen der Funktionen außerhalb des
  Repositorys gegen selbstgebaute Dateien.
- **Was Runde 22 dazu nachgetragen hat:** `_check_notice_versions` verbindet einen Namen aus dem Versionskatalog nur
  mit einer Version, die ihm in derselben Zeile folgt, seit Runde 22 mit oder ohne „v“ davor. Eine Version in einer
  eigenen Tabellenspalte und ein Name, den ein Lizenzhinweis anders schreibt als der Versionskatalog, etwa
  „Bouncy Castle PG“ statt `bcpg`, bleiben unerkannt. Heute steht in der Tabelle von `THIRD_PARTY_NOTICES.md` keine
  Version eines Namens aus dem Versionskatalog in einer eigenen Spalte; die Zeile zu WebP trennt Name und Version,
  und WebP steht nicht im Katalog. Gefunden vom Code-Reviewer der Runde 22, mit der Funktion gegen selbstgebaute
  Hinweise.

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

### 30. Die Mindestdauer eines Modells ist eine dritte Längenschranke, die die Anzeige nicht kennt (niedrig)

- **Stelle:** `AssemblyAiAdapter.MIN_DURATION_MS` (160 ms) und `GroqAdapter.MIN_DURATION_MS` (10 ms) gegen
  `MainViewModel.sourceTooLong` und `MainViewModel.estimatedCostMicrousd`.
- **Voraussetzung:** Eine Quelle unter 160 ms bei AssemblyAI beziehungsweise unter 10 ms bei Groq. Die
  Abschnittsplanung reicht sie als einen Abschnitt durch, weil `MIN_FINAL_CHUNK_DURATION_MS` nur bei mehr
  als einem Abschnitt eingreift.
- **Erwartet gegen tatsächlich:** Runde 13 hat die Kostenzeile daran gehindert, eine **zu lange** Quelle zu
  bepreisen. Am anderen Ende gilt dasselbe nicht: Eine Quelle unter der Mindestdauer bekommt einen
  winzigen Preis angezeigt, während die Übermittlung sie mit `INVALID_INPUT` ablehnen würde.
- **Warum es offen bleibt:** Eine Quelle unter einer Zehntelsekunde ist für ein Transkriptionswerkzeug
  praxisfremd, und die Behebung wäre eine dritte Bedingung in einer Regel, die „zu lang“ heißt — eine zu
  kurze Quelle braucht eine andere Aussage, nicht dieselbe. Bewusst als Punkt notiert statt beiläufig
  mitgefixt. Gefunden vom lesenden Reviewer in Runde 14.

### 31. Eine leere Fachbegriffsliste wird erst bei der Übermittlung abgelehnt — erledigt am 13. September 2026

Aufgenommen in Runde 14, geschlossen in Runde 15, und weiter als beschrieben: Der Punkt nannte Listen aus
lauter Leereinträgen, der Mechanismus traf aber jede Liste mit einem leeren Eintrag, auch eine gemischte wie
`["Kubernetes", ""]` — und für die rechnete die Kostenzeile trotz der Runde-14-Korrektur den Zuschlag noch
ein. Erreichbar blieb beides nur über einen gespeicherten oder übernommenen Auftrag, weil das Eingabefeld
Leerzeilen beim Tippen entfernt. Die Regel steht jetzt einmal, in
`core/src/main/kotlin/app/sourcescribe/core/ContextTerms.kt`, und jede Stelle, die über eine Liste
entscheidet, fragt sie: beide Anbieterpfade, `SttStep.validate`, die Kostenformel in `SttStep` sowie
`configError` und `estimatedCostMicrousd` in `MainViewModel`. Die Vorschau meldet `CONTEXT_TERM_BLANK` mit
eigenem Text und zeigt keinen Preis. Die Vertragsänderung, die dieser Punkt für den richtigen Zug hielt —
`configError` ruft die Adapterprüfung auf —, war nicht nötig: Eine gemeinsame Regel in `core` ersetzt die
Kopien, statt eine weitere hinzuzufügen. Belegt durch
`ViewRulesTest.aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced` und zwei Zusicherungen in
`SttStepTest.estimateCostCeilsMinimumAndAssemblyAddonsAndBlocksUnknownPrice`. Gefunden vom lesenden Reviewer
in Runde 14, verbreitert vom Code-Reviewer in Runde 15.

Runde 17 hat eine Lücke dahinter geschlossen: Eine gespeicherte Liste `[""]` zeigte sich als leeres Feld, und die
Vorschau meldete `CONTEXT_TERM_BLANK`, ohne dass sich auf dem Bildschirm etwas entfernen ließ. Start lässt leere
Einträge jetzt weg (`MainViewModel.configurationForStart` über `ContextTerms.withoutBlanks`), und Fehlerzeile wie
Kostenzeile urteilen über die Konfiguration, die Start anlegt. Ein vor Runde 17 so gespeicherter Auftrag bleibt bei
`SttStep.validate` mit `CONTEXT_TERM_BLANK` stehen, auch nach einer Wiederholung, die die gespeicherte Konfiguration
übernimmt; „Neu vorbereiten“ und ein Start danach legen einen Auftrag ohne leere Einträge an.

### 32. Ein grüner CI-Lauf zeigt nicht, wie viele Instrumentierungstests übersprungen wurden (niedrig)

- **Stelle:** `.github/workflows/android.yml`, der Schritt mit `:app:connectedDebugAndroidTest` und
  `:extractor:connectedDebugAndroidTest` und seine Funktion `cleanup()`.
- **Voraussetzung:** Ein CI-Lauf, der grün endet.
- **Erwartet gegen tatsächlich:** Ein Instrumentierungslauf endet grün, auch wenn Tests per Annahme
  übersprungen werden — ohne `sourcescribeEngineUpdate` sind es im Modul `extractor` achtzehn, siehe den
  Wartungshinweis zu Instrumentierungstests unten. Der Workflow setzt diese Flagge, aber sichtbar wird eine
  Überspringung dort nicht: `cleanup()` gibt die Testberichte nur aus, wenn der Lauf gescheitert ist, und
  einen `upload-artifact`-Schritt gibt es nicht. Kippt künftig ein größerer Teil der Suite unbemerkt ins
  Überspringen, etwa weil eine Flagge nicht mehr durchgereicht wird, bleibt CI grün, und die Zahl steht nur
  in Dateien, die mit dem Runner verworfen werden.
- **Warum es offen bleibt:** Die Änderung wäre klein — eine Zusammenfassung von `tests`, `failures`,
  `errors` und `skipped` je Bericht, unabhängig vom Ausgang —, aber nicht lokal ausführbar, und ein
  Workflowschritt, dessen Wirkung niemand gesehen hat, wäre genau die ungeprüfte Behauptung, die diese
  Schleife sonst abbaut. Gemeldet vom Invarianten-Reviewer in Runde 15. Seine Fassung, `cleanup()` lese
  „nie `<skipped>`“, stimmt nur für die Elemente: Im Fehlerfall gibt das Skript auch `root.attrib` aus, und
  ob darin eine Überspringzahl steht, ist an keinem echten Bericht geprüft.

### 33. `setBackoffCriteria` im Erfassungsauftrag greift nie (niedrig, informativ)

- **Stelle:** `app/src/main/java/app/sourcescribe/data/JobCoordinator.kt`,
  `.setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)`.
- **Voraussetzung:** Keine; die Zeile steht in jedem eingereihten Erfassungsauftrag.
- **Erwartet gegen tatsächlich:** WorkManager wendet eine Rückzugsregel nach `Result.retry()` an. Kein
  Worker gibt `Result.retry()` zurück — `git grep` findet es weder in `app/src/main` noch in
  `extractor/src/main` —, und die App plant Wiederholungen selbst: `nextAt` in der Datenbank und frisches
  Einreihen mit `setInitialDelay`. Die Zeile legt einen Mechanismus nahe, der hier nicht arbeitet.
- **Warum es offen bleibt:** Ob WorkManager die Regel auch auf einen vom System gestoppten und neu
  eingeplanten Worker anwendet, ist nicht am Gerät geprüft. Die Zeile zu entfernen könnte genau diesen Pfad
  verändern, und ein ungemessenes Verhalten zu ändern wäre schlimmer als eine irreführende Zeile. Gemeldet
  vom Invarianten-Reviewer in Runde 15.

### 34. Der Lexer der Zahlenprüfung kennt keine Zeichenkette innerhalb eines String-Templates (niedrig, heute folgenlos)

- **Stelle:** `codeOnly` in `core/src/test/kotlin/app/sourcescribe/core/StatedNumbersTest.kt`.
- **Voraussetzung:** Ein String-Template, dessen Ausdruck selbst eine Zeichenkette enthält, und in dieser
  inneren Zeichenkette ein Kommentar-Anfang, etwa `"${x ?: "/*"}"`.
- **Erwartet gegen tatsächlich:** Der Lexer beendet die äußere Zeichenkette am ersten inneren
  Anführungszeichen und liest den Inhalt der inneren als Code. Ein `/*` dort öffnet einen Kommentar, den es
  nicht gibt, bis zum nächsten `*/` irgendwo dahinter; ein `//` verwirft den Rest der Zeile. Beides kann eine
  echte Deklaration verschlucken, still, aus demselben Grund wie die zwei stillen Formen der Runde 15.
- **Warum es offen bleibt:** Eine Suche nach Zeilen mit `${` und einem späteren Anführungszeichen findet im
  Modul `core` 54, darunter Templates mit innerer Zeichenkette in `TranscriptExporter`, `ExtractorMetadata`
  und `SyncTranscriptParser`, und in keiner steht `/*` oder `//`. Über den Baum liest der Lexer dieselben 33
  Konstanten wie der Scanner davor, am Modell gemessen. Verschachtelte Templates zu modellieren hieße, einen
  Stapel von Lexerzuständen zu führen — mehr Code, der selbst geprüft werden müsste, für einen Fall, den es
  nicht gibt. Die Fassung der Runde 14 hatte denselben Fall schlechter: Ihr Blockkommentarmuster griff in
  jeder Zeichenkette, nicht nur in Templates. Selbst gefunden beim Nachfragen, was die Korrektur der
  Runde 15 nicht kann.
- **Nachtrag aus Runde 16, die zweite Richtung:** Derselbe Fehler kann eine Deklaration auch erfinden. Endet
  die äußere Zeichenkette am öffnenden Anführungszeichen der inneren, beginnt am schließenden eine neue, die
  erst am nächsten echten Anführungszeichen endet, und was zwischen beiden steht, liest der Lexer als Code.
  Stünde dort `; const val FAKE = 1`, fände `DECLARATION` eine Konstante, die es nicht gibt, ganz ohne `/*`
  oder `//`. Diese Richtung wäre laut, weil der Vergleich am Baum an einem Namen scheitert, der in der Liste
  fehlt. Der Code-Reviewer der Runde 16 hat den Ablauf am Code von Hand nachgerechnet, nicht ausgeführt.
  Nachgezählt am 13. September 2026: `grep -rnoE '\$\{[^}]*"[^}]*\}' core/src/main/kotlin` findet 16 Templates
  mit innerer Zeichenkette, vor und nach den Änderungen der Runde 16 gleich viele, und in keinem steht mehr
  als ein kurzes Wort wie `unknown`, `WORD` oder `;hls-vtt-assembled`.

### 35. Die Zeilengrenzen auf zwei Bildschirmen sind nur zum Teil am Gerät gemessen (niedrig)

- **Stelle:** Kostenzeile in `NewSourceScreen.kt`, seit `579f972` ein `ReservedText` mit der Höhe des höchsten
  ihrer Texte; Fehlerzeile der Vorschau in `NewSourceScreen.kt`, Wartezeit und Bytezähler in `HistoryScreen.kt`
  (`ReservedText`).
- **Gemessen:** Vor `579f972` schnitt die Kostenzeile bei 200 % Schrift ab, im Englischen das Tarifdatum zur Hälfte,
  im Deutschen ganz (`emulator-5556`, 1080 × 2424 px, 420 dpi).
  Nachgemessen am 14. September auf dem Stand von `39a1cdc`, jedes Mal ganz innerhalb des scrollenden Formulars: In
  beiden Sprachen ist die Zeile bei Schriftgröße 1.0 und 1.3 zweizeilig, 84 und 110 px hoch, bei 2.0 dreizeilig und
  252 px hoch, und auf allen sechs Bildschirmfotos steht ihr Text vollständig, das Tarifdatum eingeschlossen.
  Gemessen ist nur `estimated_cost`, nicht `cost_source_too_long` und nicht `price_unknown`.
- **Nicht gemessen:** ob die Platzhalter der Verlaufskarte (`LONGEST_ELAPSED`, `LONGEST_BYTE_SIZE`) die höchsten
  Fälle sind, und die Fehlerzeile der Vorschau bei 200 % Schrift.
- **Eine Messfalle:** uiautomator meldet von einem Knoten nur den sichtbaren Teil. Auf `8e41bf4` lag die Kostenzeile
  bei 130 % im Englischen und bei 200 % im Deutschen zum Teil unter der Navigationsleiste und maß weniger, als sie
  hoch ist. Eine Messung gilt erst, wenn die Zeile ganz innerhalb des scrollenden Formulars liegt.
- **Warum es offen bleibt:** Es fehlt ein Compose-UI-Test, der über `onTextLayout` Zeilenzahl und Abschneiden prüft;
  er wäre der bessere Weg, weil er bleibt. Vorschlag des Code-Reviewers in Runde 15.

### 36. Die Fehlerzeile der Vorschau erscheint und verschwindet (niedrig)

- **Stelle:** `PreviewCard` in `NewSourceScreen.kt`, der Zweig unter der Kostenzeile.
- **Voraussetzung:** Eine Vorschau, deren Einstellungen erst unvollständig und dann vollständig sind, oder
  eine Quelle, die länger ist als die Grenze des Auftrags.
- **Ablauf:** Anbieter, Modell und Schlüssel wählen. Solange etwas fehlt, steht die Fehlerzeile da; sobald
  nichts mehr fehlt, verschwindet sie, und der Startknopf unter der Karte rückt um ihre Höhe nach oben —
  der Knopf, den man als Nächstes antippt. Die Längenwarnung (`SOURCE_LONGER_THAN_LIMIT`) ersetzt die Zeile
  durch einen Satz mit eigenem Knopf und anderer Höhe.
- **Erwartet gegen tatsächlich:** Erwartet ist, dass sich unter der Karte nichts bewegt. Seit Runde 15
  bewegt sich nichts mehr, wenn ein Fehlertext einen anderen ablöst; beim Erscheinen und Verschwinden
  ändert die Karte ihre Höhe weiterhin.
- **Warum es offen bleibt:** Die Höhe auch ohne Fehler freizuhalten, ließe im häufigsten Zustand eine
  leere Fläche von der Höhe des längsten Fehlertextes stehen. Ein Vorschlag wäre, den Platz im gültigen
  Zustand mit einem kurzen Satz wie „Diese Quelle ist startklar“ zu füllen und die Längenwarnung in
  dieselbe reservierte Höhe zu legen. Das ist eine Gestaltungsentscheidung und keine Korrektur, deshalb
  nicht eigenmächtig getroffen. Gefunden in Runde 15 beim Umbau der Zeile.

### 37. Zurück zu einer älteren Engine warnt, sperrt aber nichts (niedrig)

- **Stelle:** `EngineUpdateManager.rollbackTarget` und `rollback(expectedId)` in
  `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`, die Bestätigung über
  `MainViewModel.prepareRollback` in `SettingsScreen.kt`. Vorgabe in `docs/SECURITY_UPDATES.md`, Abschnitt S7:
  „Rollback auf bekannte Sicherheitslücken warnend kennzeichnen und ggf. sperren.“
- **Voraussetzung:** Eine aktualisierte Engine ist aktiv, und eine frühere gesunde oder die gebündelte ist da.
- **Stand seit Runde 16:** Bis dahin stellte ein einzelner Tap um, ohne jeden Hinweis; gemeldet hat das der
  Invarianten-Reviewer der Runde 16 als mittleren Fund. Jetzt öffnet der Knopf eine Bestätigung, die nennt,
  welche Version wieder aktiv würde, und sagt, dass eine ältere Version Lücken enthalten kann, die eine
  spätere schon schließt, und dass SourceScribe das nicht prüft. Umgestellt wird nur auf die genannte
  Installation: Hat sich das Ziel seit dem Öffnen geändert, endet `rollback` mit `ROLLBACK_TARGET_CHANGED`
  (in der App `ENGINE_ROLLBACK_TARGET_CHANGED`) und stellt nichts um.
- **Was fehlt:** Das Wissen, welche Version eine bekannte Lücke hat. Im Baum gibt es dafür nichts, weder eine
  Mindestversion noch eine Liste betroffener Versionen; gesucht am 13. September 2026 nach `CVE-`,
  `vulnerab`, `blocklist`, `denylist`, `minVersion` und `Sicherheitslück`. Also gibt es nichts, woran sich
  eine Sperre halten könnte. Eine Liste im App-Code wäre mit der nächsten bekannt gewordenen Lücke veraltet;
  es bräuchte ein signiertes Feld im Engine-Paket, und das ist eine Entscheidung über das Updateformat, nicht
  über diesen Knopf.

### 38. Dass jede Änderung des Entwurfs über `withDraft` geht, ist eine Absprache, keine Schranke (niedrig, heute folgenlos)

- **Stelle:** `ScreenState.draft` und `withDraft` in `app/src/main/java/app/sourcescribe/MainViewModel.kt`,
  `DraftTextField` in `app/src/main/java/app/sourcescribe/ui/NewSourceScreen.kt`.
- **Voraussetzung:** Eine künftige Stelle im View-Model setzt den Entwurf mit `copy(draft = …)` statt über
  `withDraft`.
- **Erwartet gegen tatsächlich:** Ein Textfeld der Auftragseinstellungen zeigt seinen getippten Text nur,
  solange die Epoche seiner Einstellung dieselbe ist, und `withDraft` rückt die Epoche jeder Einstellung
  weiter, die eine Änderung verschiebt. Eine Änderung an `withDraft` vorbei rückt nichts weiter. Ein Feld, in
  das zuvor getippt wurde, zeigt dann weiter den alten Text über einem Entwurf, der ihn nicht mehr enthält —
  der Fehler, den die Epochen in Runde 16 geschlossen haben.
- **Warum es offen bleibt:** Heute schreiben acht Stellen den Entwurf, alle über `withDraft`: `inspect`,
  `selectTrack`, `startPreviews`, `clearPreview`, `changeDraft`, `importAudio`, `prepareAgain` und
  `deleteCredential`. Eine Suche nach `draft =` außerhalb von `withDraft` findet nichts. `ViewModelStateTest`
  prüft die Epochen beim Tippen, bei einer Voreinstellung, bei der Spurwahl, beim Schließen der Vorschau und
  bei „Neu vorbereiten“, nicht aber einzeln für `inspect`, `startPreviews`, `importAudio` und
  `deleteCredential`. Eine Schranke wäre eine Klasse für Entwurf und Epochen mit privatem Konstruktor, deren
  einzige Änderung beide zugleich fortschreibt. Das berührt jede Stelle, die `ScreenState(draft = …)` baut,
  darunter viele Tests, und ist deshalb nicht in derselben Runde gemacht worden wie die Epochen selbst.

### 39. Die Wartezeit im Verlauf reserviert Platz für höchstens dreistellige Stunden (niedrig)

- **Stelle:** `LONGEST_ELAPSED = "000:00:00"` in `app/src/main/java/app/sourcescribe/ui/HistoryScreen.kt`
  gegen `duration` in `app/src/main/java/app/sourcescribe/ui/Labels.kt`.
- **Voraussetzung:** Ein Auftrag steht 1000 Stunden oder länger, also gut 41 Tage, auf `WAITING_REMOTE`.
- **Erwartet gegen tatsächlich:** `duration` begrenzt die Stunden nicht, ab 1000 Stunden steht dort
  `1000:00:00`, ein Zeichen breiter als der Platzhalter. `ReservedText` schneidet nichts ab und misst den
  gezeigten Text mit. Höher wird die Karte also nur, wenn dieses eine Zeichen eine neue Zeile braucht, und
  dann einmal, beim Übergang. Der Code-Reviewer der Runde 16 hat den Fall berechnet und dabei „11 Zeichen“
  geschrieben; es sind 10 gegen 9.
- **Warum es offen bleibt:** Ein breiterer Platzhalter verschiebt die Grenze nur, bei vier Stellen auf gut
  416 Tage, und kann bei großer Schrift eine Zeile reservieren, die im Regelfall leer bleibt. Ob ein Anbieter
  einen Auftrag so lange offen hält, ist nicht geprüft.

### 40. Zwischen Prüfsumme und Start einer Engine liegt ein kurzes Fenster (niedrig, Restrisiko aus S1)

- **Stelle:** `EngineUpdateManager.file` in
  `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt` prüft bei jedem Aufruf über
  `validSlot` den SHA-256 des Slots. Gestartet wird die Datei danach in `NativeRuntime`.
- **Voraussetzung:** Ein Prozess, der mit der UID der App schreiben darf, ersetzt die Datei genau zwischen
  Prüfung und Start.
- **Warum es offen bleibt:** Wer mit derselben UID schreibt, braucht dieses Fenster nicht, er kann App-Daten
  und Slots ohnehin ändern. `docs/SECURITY_UPDATES.md` nennt genau das in S1 als akzeptiertes Restrisiko: Ein
  Prozess mit eigener PID, aber derselben Android-UID bleibt im selben Vertrauensbereich. Einen Weg darüber
  hinaus hat niemand gezeigt; der Invarianten-Reviewer der Runde 16 hat den Punkt selbst als spekulativ
  eingestuft.

### 41. `youtube-nocookie.com` wird nicht als YouTube-Quelle erkannt (niedrig, sichere Richtung)

- **Stelle:** `youtubeHosts` in `core/src/main/kotlin/app/sourcescribe/core/SourceResolver.kt`, geprüft in
  `SourceResolver.youtube`.
- **Voraussetzung:** Ein Link der Form `https://www.youtube-nocookie.com/embed/<id>`.
- **Erwartet gegen tatsächlich:** Die Einbettungsadresse wird mit `INVALID_HOST` abgelehnt, während
  `youtube.com/embed/<id>` angenommen wird. Nichts wird falsch verarbeitet, der Link wird nur nicht
  angenommen.
- **Warum es offen bleibt:** Nicht verlangt und nicht ohne einen Test für genau diese Form aufzunehmen, der
  auch zeigt, dass die kanonische Adresse dieselbe bleibt. Gefunden vom Invarianten-Reviewer der Runde 16.

### 42. Geteilte Exporte bleiben im Cache liegen (niedrig, Hygiene)

- **Stelle:** `shareArtifact` und `shareDiagnostics` in `app/src/main/java/app/sourcescribe/MainViewModel.kt`
  schreiben nach `cacheDir/shares/`.
- **Erwartet gegen tatsächlich:** Die Datei bleibt nach dem Teilen liegen, bis Android den Cache räumt, auch
  ein vollständiges Transkript. Der Ordner liegt im app-privaten Cache; ein neuer Abflussweg entsteht dadurch
  nicht.
- **Warum es offen bleibt:** Wann die empfangende App die Datei gelesen hat, erfährt SourceScribe nicht.
  Löschen beim nächsten Teilen oder beim Start der App kann einer App die Datei entziehen, die sie erst später
  liest; denkbar, nicht geprüft, etwa bei einem Mailentwurf. Eine Frist wäre möglich, jede Zahl dafür aber
  geraten. Gefunden vom Invarianten-Reviewer der Runde 16.

### 43. Scheitert ein Update nach dem Räumen, bleibt die entfernte Installation entfernt (niedrig)

- **Stelle:** `EngineUpdateManager.stage` in
  `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`: `materializeSlot` räumt, danach läuft
  `verifyRuntimeCompatibility`.
- **Ablauf:** Geräumt wird erst nach Download und Signaturprüfung, aber vor dem Selbsttest der Laufzeit. Besteht die
  neue Engine ihn nicht, ist der neue Slot fort und der geräumte ebenso.
- **Warum es offen bleibt:** Den Selbsttest vor dem Räumen laufen zu lassen hieße, eine Datei außerhalb ihres
  Hash-Slots auszuführen, und `file()` erlaubt das bewusst nicht. Geräumt wird ohnehin nur eine Installation, auf die
  nichts verweist ([ADR 0009](adr/0009-engine-slot-cleanup.md)). Aufgenommen in Runde 17.

### 44. `ENGINE_SLOTS_IN_USE` nennt auch dort einen Update-Schritt, wo niemand aktualisiert (niedrig)

- **Stelle:** `stepText` in `app/src/main/java/app/sourcescribe/ui/Labels.kt`, das jedem Code mit dem Präfix `ENGINE_`
  „Beim Aktualisieren der Extraktionskomponente“ voranstellt.
- **Ablauf:** Findet die gebündelte Engine nach einem App-Update keinen Platz, endet mit diesem Code jeder Aufruf des
  Managers, der zuerst `ensureBundledLocked` durchläuft, darunter Vorschau, Start und ein Auftrag, der seine Engine
  sucht, und die Anzeige nennt den Update-Schritt.
  Aktualisiert hat der Nutzer nichts; die App hat ihre mitgelieferte Engine eingerichtet.
- **Warum es offen bleibt:** Der Satz danach stimmt, nur der Schritt ist ungenau, und der Fall setzt voraus, dass
  unfertige Aufträge an mindestens drei Engines gebunden sind, die weder aktiv noch vorherig sind. Aufgenommen in
  Runde 17.

### 45. Der STT-Schreiber von `ArtifactRow.complete` hat keinen eigenen Test (niedrig, heute folgenlos)

- **Stelle:** `app/src/main/java/app/sourcescribe/data/SttStep.kt`, `complete = document.scope.confirmedComplete`.
- **Warum es offen bleibt:** Der Unterschied zu `technicallyComplete` zeigt sich nur an einem Umfang mit gesetztem
  Flag und fehlendem Abschnitt, und den erzeugt der STT-Zweig nicht; ein Test müsste ihn am Normalisieren vorbei in
  den Speicherschritt schieben. Die beiden Schreiber in `JobCoordinator` sind belegt
  (`AppPipelineTest.aResultWithAMissingChunkIsStoredAsPartialByBothArtifactWriters`), die Gegenprobe dazu steht in
  STATUS unter Runde 17. Aufgenommen in Runde 17.

### 46. Zwei Grenzfälle der Umstellung auf die neu gebündelte Engine (niedrig)

- **Stelle:** `EngineUpdateManager.ensureBundledLocked`, der Zweig für eine neu gebündelte Engine;
  [ADR 0010](adr/0010-bundled-engine-after-app-update.md).
- **(a) Eine schon geladene Version kommt gebündelt wieder.** Hatte jemand genau die Version, die ein App-Update
  mitbringt, vorher als Update geladen und war danach zur alten gebündelten zurückgegangen, stellt das App-Update
  trotzdem auf sie um, weil ihr Eintrag bis dahin nicht als gebündelt markiert war.
- **(b) Eine dritte Engine verliert ihren Platz als vorherige.** Jemand aktiviert eine geladene Engine D und geht zur
  gebündelten A zurück; D ist jetzt die vorherige. Bringt ein App-Update die gebündelte Engine C, wird C aktiv und A
  die vorherige. D ist danach über „Zur vorherigen Engine“ nicht mehr zu erreichen. Sie steht weiter in der Liste
  der Einstellungen, die aber nur anzeigt, und eine Aktualisierung bietet nur das neueste Release des Kanals an.
  Beim Räumen kann sie weichen, weil nichts sie mehr schützt. Gemeldet vom Code-Reviewer der Runde 18, der es als
  mittel einstufte.
- **Warum niedrig und offen:** Jede Aktivierung von Hand verdrängt die vorherige Engine auf dieselbe Weise; der Weg
  zurück reicht in diesem Modell genau einen Schritt, und das App-Update ist selbst eine ausdrückliche Handlung. Eine
  beliebige installierte Engine gezielt zu aktivieren wäre eine neue Funktion mit derselben Bestätigung wie der
  Rollback (S7), keine Korrektur der Umstellung, und ist deshalb eine Frage an den Nutzer. Für (b) fehlt ein Test;
  `anAppUpdateMakesItsBundledEngineActiveOnceAndKeepsTheOldOneAsTheWayBack` setzt nie eine vorherige Engine.

### 47. Eine aktivierte Engine wird nach einem App-Update nicht neu gegen die Laufzeit geprüft (niedrig, unbestätigt)

- **Stelle:** `EngineUpdateManager.active`; `verifyRuntimeCompatibility` läuft nur in `stage`, `activate` und
  `ensureBundledLocked`.
- **Voraussetzung:** Eine selbst aktivierte heruntergeladene Engine und ein App-Update, das Python oder die
  JavaScript-Laufzeit ändert.
- **Unbestätigt:** Ob eine yt-dlp-Version mit einer neueren gebündelten Laufzeit tatsächlich scheitert, ist nicht
  geprüft; yt-dlp unterstützt mehrere Python-Versionen. Scheitert sie, zeigen es die Extraktionsfehler, und
  „Zur vorherigen Engine“ oder ein Update hilft. Zum Schließen: `active()` prüft nach einem Versionswechsel der App
  einmal die Laufzeit, mit Test. Aufgenommen in Runde 17.

### 48. `stage` fragt die Referenzen vor dem Download und beim Räumen getrennt (niedrig)

- **Stelle:** `EngineUpdateManager.stage`: `ensureRoomLocked(update.sha256, remove = false)` vor dem Download,
  `materializeSlot` mit `ensureRoomLocked(installation.id, remove = true)` danach. `JobCoordinator.retry` gibt einem
  Versuch „Nur Fehlendes“ die Engine seines Vorgängers, ohne den Manager zu fragen.
- **Voraussetzung:** Fünf belegte Slots, von denen genau eine Installation entbehrlich ist, und ein Auftrag mit
  Teilergebnis, dessen letzter STT-Versuch an genau diese gebunden war.
- **Ablauf:** Der Trockenlauf findet Platz. Während des gedrosselten Downloads, 64 KiB je Sekunde, entsteht für diesen
  Auftrag ein Versuch „Nur Fehlendes“. Er ist unfertig und hält die Engine seines Vorgängers, das Räumen nach dem
  Download findet keinen Platz mehr, `stage` endet mit `SLOTS_IN_USE`, der geprüfte Download ist verworfen, und
  entfernt ist nichts.
- **Warum es heute kaum eintritt:** Update und Wiederholung laufen beide über `MainViewModel.action`, dessen Sperre
  eine Aktion zurzeit zulässt, und `stageAndActivate` hält sie über den ganzen Download. Die Sperre gehört aber zu
  einem View-Model. `MainActivity` hat den Startmodus `standard` und nimmt geteilte Texte an; eine zweite Instanz
  mit eigenem View-Model, etwa nach dem Teilen aus einer anderen App, ist deshalb nicht ausgeschlossen, am Gerät
  geprüft ist sie nicht. Jeder andere neue Versuch einer YouTube-Quelle fragt den Manager nach der aktiven Engine und
  wartet, bis `stage` ihn freigibt, oder übernimmt als Rückfall auf STT die Engine eines Untertitelversuchs, der sie
  in diesem Moment noch hält.
- **Warum es offen bleibt:** Der Ausgang ist sicher und hat einen eigenen Text; er kostet den Download. Die Referenzen
  unter der Sperre des Managers festzuhalten hieße, dass Aufträge für ihre Schreibzugriffe in Room auf den Manager
  warten. Gemeldet vom Code-Reviewer der Runde 18; [ADR 0009](adr/0009-engine-slot-cleanup.md) nennt den Fall.

### 49. Eine Vorschau hält ihre Engine nicht (niedrig)

- **Stelle:** `JobCoordinator.inspect` gibt `engines.file(engines.active())` an `extractor.resolve`, ohne einen Versuch
  anzulegen; `EngineReferences.inUse` kennt nur Versuche.
- **Was es bräuchte:** Während yt-dlp für die Vorschau läuft, müsste deren Engine weder aktiv noch vorherig werden und
  dann beim Räumen weichen, also zwei Aktivierungen und ein Räumen während eines einzigen Aufrufs.
- **Warum es heute kaum eintritt:** `inspect` und `prepareAgain` laufen unter derselben Sperre von
  `MainViewModel.action` wie `stageAndActivate` und `rollback`, und nur diese beiden aktivieren, gehen zurück oder
  räumen für ein Update. Umstellung und Räumen nach einem App-Update geschehen im ersten Aufruf des Managers im
  Prozess, und `inspect` fragt `engines.active()`, bevor es die Datei nimmt. Eine zweite Instanz der Aktivität mit
  eigenem View-Model hebt die Sperre auf wie in Punkt 48; die zwei Aktivierungen müssten dann dort während eines
  einzigen Aufrufs von yt-dlp geschehen.
- **Warum es trotzdem hier steht:** Die Sicherheit hängt an einer Sperre des View-Models, nicht am Manager. Ein
  künftiger Aufrufer von `stage`, `activate` oder `rollback` außerhalb dieser Sperre, etwa ein Update im Hintergrund,
  öffnet das Fenster. Gemeldet vom Code-Reviewer der Runde 18.

### 50. Instrumentierungstests reihen Arbeit in den WorkManager der App ein (niedrig, heute folgenlos)

- **Stelle:** `ensureWorkManager` in `AppPipelineTest`, `BatchCreationTest`, `EngineJobPinningTest`,
  `ParallelJobsTest`, `ProcessRecoveryTest` und `ViewModelStateTest` unter `app/src/androidTest/java/app/sourcescribe/`;
  `SourceScribeApplication` als `Configuration.Provider`.
- **Was geschieht:** Die Tests laufen im Prozess der App. `WorkManager.getInstance` liefert dort die Instanz der App
  mit ihrer Datenbank `androidx.work.workdb`, weil die Anwendung selbst die Konfiguration liefert; der Rückfall auf
  `WorkManagerTestInitHelper` im `catch` wird deshalb nie erreicht. Jede Testumgebung baut einen eigenen
  `JobCoordinator` mit eigener Room-Datenbank, reiht aber über denselben Code Arbeit unter `attempt:<id>` und
  `exports:<id>` in diese Datenbank ein. Eine Kopie vom 14. September, 01:47, enthielt 235 abgeschlossene Einträge,
  176 erfolgreich und 59 abgebrochen; wie viele davon aus Tests stammen, ist nicht gezählt.
- **Warum heute folgenlos:** Die Ids sind zufällige UUIDs, und die Tests brechen beim Aufräumen nur Arbeit mit den
  Tags ihrer eigenen Aufträge ab. Ein Worker, dessen Versuch in der Datenbank der App nicht existiert, findet ihn
  nicht und endet ohne Wirkung mit `Result.success()` (`AcquisitionWorker.doWork`, `SourceScribeDao.claim`). Die
  Eingabedaten tragen nur die Versuchs-Id. Stirbt ein Testlauf vor dem Aufräumen, kann solche Arbeit später im
  Prozess der App laufen, ebenso ohne Wirkung; beobachtet ist das nicht.
- **Warum es offen bleibt:** Getrennt wäre es mit einer eigenen WorkManager-Instanz für Tests, etwa über
  `WorkManagerTestInitHelper` mit einer eigenen Test-Anwendung, und das ändert, wie diese Klassen ihre Worker
  ausführen. Gefunden in Runde 18 beim Lesen der App-Daten auf `emulator-5556`; nachgelesen hat es ein Hilfsagent,
  die tragenden Stellen sind nachgeprüft.

### 51. Die Migrationstests legen ihre Datenbanken zwischen denen der App an (niedrig, heute folgenlos)

- **Stelle:** `MigrationTest` unter `app/src/androidTest/java/app/sourcescribe/data/`:
  `MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SourceScribeDatabase::class.java)` und die Namen
  `migration-<UUID>.db`.
- **Was geschieht:** Der Helfer legt die Testdatenbanken mit dem Kontext der App an, also in ihrem Verzeichnis
  `databases/` neben ihrer eigenen Datenbank. Getrennt sind sie nur durch den zufälligen Namen. Seit `39a1cdc` löscht
  eine äußere Regel nach jedem Test die Datenbank samt `-journal`, `-shm`, `-wal` und `.lck` und prüft, dass nichts
  mit ihrem Namen bleibt.
- **Warum heute folgenlos:** Die Namen treffen nicht den der App, und gelöscht wird nur, was die Tests selbst anlegen.
  Stirbt der Prozess mitten in einem Test, bleiben die Dateien liegen wie vor `39a1cdc`.
- **Warum es offen bleibt:** Ein eigenes Verzeichnis bräuchte absolute Pfade als Datenbanknamen oder einen Kontext mit
  eigenem Datenbankverzeichnis; ob `MigrationTestHelper` und die Sperrdatei `.lck` damit arbeiten, ist nicht geprüft.
  Gemeldet vom Invarianten-Reviewer der Runde 19; von derselben Art wie Punkt 50.

### 52. `SettingsStore` sucht seinen DataStore im Konstruktor und hält ihn für den ganzen Prozess (niedrig)

- **Stelle:** `settingsDataStore` und die Map `stores` in `app/src/main/java/app/sourcescribe/data/SettingsStore.kt`,
  seit `8fe0dd5`, aufgerufen im Konstruktor von `SettingsStore`.
- **Was geschieht:** Der Konstruktor ruft `preferencesDataStoreFile`, das `Context.getFilesDir()` fragt, und
  `File.canonicalPath`, das den Pfad im Dateisystem auflöst und `IOException` werfen darf. Hilt erzeugt den
  `@Singleton` beim ersten Bedarf, und unter den Empfängern ist `MainViewModel`, das auf dem Hauptthread entsteht.
  Der ersetzte Delegat `preferencesDataStore` fragte nach der Datei erst, wenn DataStore sie zum ersten Mal las.
  Außerdem legt jeder neue Pfad einen Eintrag in `stores` an, der bis zum Ende des Prozesses bleibt.
- **Warum niedrig:** In der App gibt es einen Pfad und damit einen Eintrag, wie beim alten Delegaten, und DataStore
  verbietet zwei Instanzen für dieselbe Datei. Weitere Einträge entstehen nur in Instrumentierungstests mit eigenen
  Kontexten. Ob der Zugriff im Konstruktor spürbar dauert oder je wirft, ist nicht gemessen.
- **Was zum Schließen fehlt:** den DataStore erst beim ersten Lesen oder Schreiben suchen, ohne dass zwei Stores für
  dieselbe Datei entstehen können. Gemeldet vom Code-Reviewer der Runde 19; dass der alte Delegat die Datei ebenso
  im Konstruktor fragte, wie er schrieb, trifft nicht zu.

### 53. Nach einer gescheiterten zweiten Umbenennung bleiben alte Metadaten im Slot (niedrig, heute folgenlos)

- **Stelle:** `replaceInSlot` in `extractor/src/main/java/app/sourcescribe/extractor/EngineUpdateManager.kt`, seit
  `a1a5af1`.
- **Was geschieht:** Beim Reparieren eines beschädigten Slots der gebündelten Engine benennt `replaceInSlot` erst die
  geprüfte Datei in den Slot und dann `metadata.json`. Scheitert nur die zweite Umbenennung, endet der Aufruf mit
  `STORAGE`, und im Slot liegt die geprüfte Datei neben den Metadaten, die vorher dort lagen. Der nächste Aufruf
  findet den Slot gültig, weil `validSlot` nur die Datei prüft, und schreibt die Metadaten nicht neu.
- **Warum folgenlos:** `metadata.json` wird nur geschrieben und verschoben, nirgends gelesen. Im Normalfall hat die
  alten Metadaten eine frühere Einrichtung desselben Hashes geschrieben.
- **Was zum Schließen fehlt:** Wer `metadata.json` künftig liest, muss mit Metadaten einer früheren Einrichtung
  rechnen oder sie beim Prüfen des Slots abgleichen. Der Ausgang steht in
  [ADR 0011](adr/0011-damaged-bundled-engine-slot.md), und
  `aSlotRepairWhoseMetadataCannotFollowFailsWithStorageAndLeavesTheEngineRepaired` hält ihn fest. Gemeldet vom
  Invarianten-Reviewer der Runde 20.

### 54. `ChoiceAccessibilityTest` scheitert in der CI, solange die App noch startet (mittel)

- **Stelle:** `appLanguageChoiceExposesButtonSemanticsAndOpensItsDialog` in
  `app/src/androidTest/java/app/sourcescribe/ChoiceAccessibilityTest.kt`, im Geräteschritt von
  `.github/workflows/android.yml`.
- **Was geschieht:** Der Test wartet 25 Sekunden vergeblich auf ein anklickbares, aktiviertes Element mit dem Label
  der App-Sprache und dem Wert „Deutsch“ oder „English“: am 10. September in Run 34544393441, am 14. September in den
  Runs 34838928729, 34841018134 und 34855355701. Bestanden hat er in der CI am 14. September in Run 34845673702. Der
  Geräteschritt ruft die Tests von `app` vor denen von `extractor` auf, und nach dem Fehlschlag laufen die von
  `extractor` in der CI nicht.
- **Was die Meldung zeigt:** Run 34855355701 lief mit der Meldung aus `20ca738`. Das Element stand auf der Seite,
  anklickbar, sichtbar und mit dem Wert „English“, aber mit `enabled=false`. `SettingsScreen` übergibt der Sprachwahl
  `enabled = !state.busy`, und `Choice` reicht das an seine `Surface` weiter. `busy` setzt `MainViewModel`, solange
  eine exklusive Aktion läuft, beim Start `coordinator.recover()`, das Laden der Zugangsdaten und `refreshEngines()`.
  Dieses ruft `EngineUpdateManager.installations()` auf, das die gebündelte Engine kopiert, verifiziert und gegen die
  Laufzeit prüft, solange der Manager sie nicht zwischengespeichert hat. Die App startete also noch. Welcher der
  Schritte in der CI so lange brauchte, zeigt das Log nicht.
- **Was nicht die Ursache ist:** die Anzeige allein. Auf `emulator-5556` besteht der Test mit 1080 × 2424 Pixeln bei
  420 dpi und mit den 320 × 640 Pixeln bei 160 dpi, die das Log des Emulators der CI nennt.
- **Was zum Schließen fehlt:** Der Test prüft die Bedienung der Sprachwahl, nicht, wie schnell die App startet. Er
  muss warten, bis die App mit dem Start fertig ist, mit einer eigenen Grenze und Meldung dafür, und so in der CI
  bestehen. Seit Runde 22 lässt die Meldung aus, was ein Feld hält, und kürzt jeden Text nach 60 Zeichen.

### 55. Bouncy Castle 1.86 lief auf keiner Android-Version vor API 37 (niedrig, unbestätigt)

- **Stelle:** `bcpg` und `bcprov` in `gradle/libs.versions.toml`, genutzt von `EngineVerifier` beim Prüfen jedes
  Engine-Updates. `minSdk` ist 29.
- **Was fehlt:** ein Lauf der Signaturprüfung auf API 29 bis 36. Lokal gibt es nur ein System-Image mit API 37, und
  die CI nutzt ebenfalls API 37.
- **Was dafür spricht, dass es hält:** Laut den Release Notes prüft der Build von 1.86 die Basisklassen jedes Moduls
  mit AnimalSniffer gegen API-Level 26. In den drei Dateien von 1.86 verweist keine Klasse auf eine der
  `…ValueExact`-Methoden von `java.math.BigInteger`, an denen 1.85 laut denselben Notes auf älteren Android-Versionen
  scheiterte (STATUS, Runde 21).
- **Was zum Schließen fehlt:** die Suite des Moduls `extractor` mit einem signierten Update auf einem Emulator mit
  API 29.

### 56. Scheitert nach einem Prüffehler auch das Löschen der Prüfansicht, geht die erste Meldung verloren (niedrig, unbestätigt)

- **Stelle:** der `finally`-Block von `inspectArchive` in `core/src/main/kotlin/app/sourcescribe/core/EngineVerifier.kt`.
- **Voraussetzung:** Die Prüfung eines Archivs scheitert, nachdem `inspectArchive` seine Prüfansicht
  `.engine-inspect-*.zip` neben dem Archiv angelegt hat, und das Löschen dieser Ansicht scheitert ebenfalls.
- **Erwartet gegen tatsächlich:** Erwartet ist die Meldung des ersten Fehlers, etwa zu einem zu großen Eintrag. Der
  `finally`-Block wirft beim gescheiterten Löschen eine eigene `EngineVerificationException`, und eine Ausnahme aus
  einem `finally` ersetzt die, die gerade durchläuft. Es bliebe „temporary inspection view could not be removed“.
  Abgelehnt wird das Update in beiden Fällen; verloren ginge nur die Diagnose.
- **Warum unbestätigt:** Ein Test müsste das Löschen genau zwischen Anlegen und Aufräumen scheitern lassen, und dafür
  hat `EngineVerifier` keine Stelle. Dass die Ansicht nach einem Fehlschlag verschwindet, hält seit Runde 22
  `aFailedInspectionRemovesItsTemporaryView` fest. Gemeldet vom Code-Reviewer der Runde 22.

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

### Aufklappen schiebt, was darunter steht

Der Invarianten-Reviewer hat in Runde 15 als Hinweis gemeldet, dass aufklappbare Elemente beim Öffnen und
Schließen ihre Nachbarn verschieben: die Auftragskarte im Verlauf, die Einträge der Hilfe, die
Herkunftsangaben in der Ergebnisansicht und in den Auftragseinstellungen über „Quelle prüfen“ der Hinweis
`no_provider_help`, der verschwindet, sobald ein Schlüssel gewählt ist. Bis Runde 16 stand hier als vierte
Stelle, dass in der Vorschau Modell- und Schlüsselauswahl erst mit einem Anbieter erscheinen. Das ist eine
andere Stelle als die genannte, und auch sie liegt in den Auftragseinstellungen, nicht in der Vorschau;
gefunden vom Konsistenz-Reviewer der Runde 16. Vollständig ist keine dieser Aufzählungen: Die
Expertenoptionen klappen auf dieselbe Weise auf, und weitere bedingt eingeblendete Blöcke sind nicht
gezählt. Das bleibt so, und die Abwägung steht hier, damit sie nicht jede Runde neu gemeldet
wird. Diese Datei liest die Regel, dass nichts springt, als Regel gegen Bewegung, die niemand ausgelöst hat:
eine Zeile, die wächst, während jemand liest oder etwas anderes bedient. Hier ändert sich der Platz dort, wo
gerade getippt wurde, und zeigt, worum gebeten wurde; Platz für eingeklappten Inhalt freizuhalten, hebt das
Einklappen auf. Wer das anders entscheidet, zählt die Stellen zuerst vollständig und ändert sie zugleich.

### Nach einem Prozesstod zeigen die Auftragsfelder den Entwurf, nicht den getippten Text

Der Code-Reviewer der Runde 16 hat gemeldet, dass ein über `rememberSaveable` geretteter Text nach dem Tod des
Prozesses sofort wieder verschwand. Seit den Epochen ist das gewollt. Der Entwurf lebt nur im View-Model und ist mit
dem Prozess fort; das neue View-Model hat eine neue Sitzungskennung, und ein Feld zeigt dann, was der Entwurf hält,
also die gespeicherten Vorgaben. Den geretteten Text zu zeigen hieße, einen Wert anzuzeigen, mit dem kein Auftrag
starten würde, denn der Start liest den Entwurf und nicht das Feld.
Am Gerät geprüft in Runde 18, auf dem Stand von `39a1cdc`: `0.5` ins Budgetfeld getippt, die App in den
Hintergrund geschickt, ihr Prozess beendet (`am kill`, danach lief keiner mehr) und die App gestartet, wie es der
Launcher tut. Danach war das Feld leer und zeigte nicht `0.5`.
Wer getippte Werte über einen Prozesstod retten will, rettet den Entwurf, etwa über `SavedStateHandle`, nicht den
Text eines Feldes.

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

**Eine gescheiterte Installation sieht aus wie ein Codefehler.** Am 13. September 2026 war `/data` auf
`emulator-5556` zu 91 % belegt, und `adb install -r` scheiterte für die App-APK und die Test-APK des Moduls
`extractor` mit `INSTALL_FAILED_INSUFFICIENT_STORAGE`, während die kleine Test-APK der App installiert wurde.
Die Instrumentierung lief also mit neuen Tests gegen die App einer früheren Runde und meldete zwei
Fehlschläge, die nach einem Defekt aussahen. Nach jeder Installation die Ausgabe auf `Success` prüfen, im
Zweifel `adb shell dumpsys package app.sourcescribe.debug | grep lastUpdateTime` lesen, und bei vollem
Speicher die alten Debug- und Testpakete zuerst deinstallieren. Am selben Abend scheiterte die Installation
ein zweites Mal, diesmal mit 523 MB frei, und der Prüflauf brach wie vorgesehen ab, bevor ein Test lief.
`adb shell pm trim-caches 4G` und das Deinstallieren von `app.sourcescribe.extractor.test` brachten `/data`
von 609 MB auf 933 MB frei. Für die Wiederholung des `extractor`-Laufs am selben Abend kam das Testpaket
zurück; danach waren 607 MB frei. Die zweite gescheiterte Installation hatte bei 523 MB stattgefunden.

Seit dem 12. September, als der Play Store auf `emulator-5556` vorinstallierte Apps aktualisiert hat, reicht der Platz
dort nicht mehr, um die Testpakete über ihre alten Fassungen zu installieren. Die Prüfskripte deinstallieren deshalb
vor jeder Installation beide Testpakete, `app.sourcescribe.extractor.test` und `app.sourcescribe.debug.test`, nie
die App selbst: Das löschte ihre Einstellungen und ihren Verlauf. In Runde 18 stieg der freie Platz damit von 565 MB
auf 886 MB und lag nach den drei Installationen bei 560 MB.

### Die Debug-App auf `emulator-5556` trägt Einstellungen aus Tests

Bis Runde 18 haben Instrumentierungstests die Einstellungen von `app.sourcescribe.debug` geschrieben, zuletzt am
14. September 2026 um 00:47. Die Datei `files/datastore/settings.preferences_pb` hält seitdem nur STT, Groq mit
`whisper-large-v3`, den Fachbegriff `mutated-default`, zwei parallele Aufträge, das Systemdesign, 2 GiB
Speichergrenze und keine Voreinstellung. Zurückgesetzt ist nichts, weil niemand weiß, was vorher darin stand, und die
App zu deinstallieren löschte auch den Verlauf. Wer an diesem Gerät etwas prüft, das von Einstellungen abhängt, liest
sie zuerst. Ob ein Lauf sie ändert, zeigt ihr SHA-256 davor und danach:

```bash
adb -s emulator-5556 exec-out run-as app.sourcescribe.debug cat files/datastore/settings.preferences_pb | sha256sum
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

Lokal kann der Befund fehlen, während die CI an ihm scheitert: `NewerVersionAvailable` liest die neuesten Versionen
aus `maven-metadata.xml` im Lint-Cache unter `build/intermediates/lint-cache` jedes Moduls. Dort lagen lokal Dateien
vom 7. und 8. September, als Bouncy Castle 1.86 am 11. September erschien, und nur die CI scheiterte, bis
`d994c23` die Version nachzog. Vor einem lokalen Lintlauf, der für die CI stehen soll, diese Verzeichnisse löschen.
