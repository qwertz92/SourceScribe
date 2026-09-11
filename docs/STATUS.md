# Tatsächlicher Projektstatus

**Stand:** 11. September 2026. **Freigabe:** Persönliche Preview; vollständige v1 weiterhin blockiert.
Der Preview-Abschluss vom 8. September steht unten; seither ist die Nutzerrückmeldung vom
10. September eingearbeitet, siehe den nächsten Abschnitt. App-Quellstand ist die Spitze von `main`,
CI-Diagnose `f9d4f8b`, lokales `main` und öffentliches
[GitHub-Repository](https://github.com/qwertz92/SourceScribe).

Native Android-App mit Compose/Material 3, allen vier Beschaffungsmodi,
AssemblyAI/OpenAI/Groq, unveränderlichen Jobkonfigurationen, paralleler Queue,
Prozess-Recovery, geschützten Credentials, Herkunft/Verlauf, SAF-Export und
signierten Engineupdates implementiert. Keine Zusammenfassungs-API, kein
Web-Frontend und kein eigener Backend-Dienst.

r80-Build/Lint regulär beendet, kein Build-/Testworker mehr aktiv.
103 JVM-Tests und zuletzt **180 tatsächlich ausgeführte Android-App-Tests**
bestanden. Der Runner führt zusätzlich fünf opt-in-Skips; deren separate
Nachweise stehen im [Preview-Prüfbericht](reports/2026-09-08-preview.md).
Debug-/AndroidTest-/unsigned und persönlich signierte Release-APKs gebaut; alle vier aktuellen Lintberichte
haben null Issues. Echte Android-Extraktion und Caption-App-Durchstich auf dem
API-37-/x86_64-/16-KB-Emulator nachgewiesen. Keine echte STT-API aufgerufen.

| Phase | Implementierung und Fixtures | Reale Nachweise / offene Freigabe |
|---|---|---|
| P0 | IMPLEMENTED / TESTED_WITH_FIXTURES: Runtime, Verifier, Manager; WebP für beide ABIs mit 16-KB-Ausrichtung neu gebaut | LIVE_VERIFIED: Python/TLS, JS/EJS, FFmpeg, exakte YouTube-Metadaten/Caption/Audio, Update/Rollback. Physisches ARM64 BLOCKED. |
| P1 | IMPLEMENTED / TESTED_WITH_FIXTURES: URL/Planner, Caption/Provenienz, Room, Export/Viewer | LIVE_VERIFIED: Android Share → echte Caption → intern → SAF-Markdown mit Inhaltsvergleich → Teilen-Dialog. |
| P2 | IMPLEMENTED / TESTED_WITH_FIXTURES: Groq, lokaler Import, Audiovorbereitung/Chunks, Credentials, Submissiongrenzen | Echte Groq-Transkription BLOCKED: keine freigegebenen Testzugänge/Inhalte/Kosten. |
| P3 | IMPLEMENTED / TESTED_WITH_FIXTURES: AssemblyAI, OpenAI, alle Modi, BOTH-Teilfehler, Sprache/Tracks/Optionen/Presets | Echte AssemblyAI-/OpenAI-Transkription BLOCKED; kein Mock als Provider-PASS. |
| P4 | IMPLEMENTED / TESTED_WITH_FIXTURES: Queue/Limits, Recovery, unsichere Submissions, Exportreparatur, Updatefehler | LIVE_VERIFIED: Android-Prozess-/Grantgrenzen, Reboot-Recovery mit Providerfixture, echtes signiertes Update/Rollback. T26 mit zwei laufenden Fixturejobs PASS r70. |
| P5 | IMPLEMENTED: de/en-App-Sprache, System/Hell/Dunkel, überarbeitete Auswahlfelder/Navigation, Viewer/Suche/Kopieren/Share, Formate/Diagnose/Signierpfad | ADB-/Screenshotprüfungen einschließlich 200-%-Schrift und Querformat bestanden. Vollständige TalkBack-Bedienung BLOCKED; dauerhafte persönliche Release-Signatur und Installation PASS r81. |
| P6 | Integrierte Regression und unabhängige Reviews ausgeführt; bestätigte Defekte samt Regression behoben | Vollständige Abnahme BLOCKED: Provider, physisches ARM64, TalkBack und öffentliche APK-Lizenz-/Quellbelege fehlen. |

## Nutzerrückmeldung vom 10. September 2026 und vier Reviewdurchgänge

**Stand:** 11. September 2026. Der Nutzer hat die Preview am Gerät getestet und 20 Punkte gemeldet.
17 davon sind umgesetzt und am Emulator oder durch Tests belegt; die drei offenen stehen mit Stelle und
fehlendem Nachweis in [Bekannte Probleme](DEFECTS.md).

Dazu kamen sechs vollständige Runden adversarischer Reviews mit Sonnet-5-Agenten, jede Runde über die
Korrekturen der vorherigen: Runde 1 über `782aef5`, Runde 2 über `5a6bfef`, Runde 3 über `42f723e`,
Runde 4 über `7da4cdf`, Runde 5 über `477dc5b` und `029a53f`, Runde 6 über `e882b25`, `94ca585`
und `4edffa7`.
Aus Runde 1 stammen unter anderem die Untertitelspur-Regression und die Vereinheitlichung der
Längengrenze, aus Runde 2 ein Instrumentierungstest, der auf einen umbenannten Statuscode wartete, die
fehlenden Fehlertexte der lokalen Audiovorbereitung, die falsch zugeordnete Meldung bei beschädigtem
Zwischenstand und drei Glossarzitate, die nicht der Beschriftung auf dem Bildschirm entsprachen.

**Runde 3 hat belegt, warum die Schleife nötig ist:** Ihr wichtigster Fund war eine Regression, die die
Korrekturen der zweiten Runde selbst eingebaut hatten. Die neue Zeile mit der Trefferzahl hatte keine
reservierte Höhe und konnte beim ersten Tastendruck umbrechen, also genau der Layout-Shift, den derselbe
Auftrag beseitigen sollte. Weiter aus Runde 3: die Warnliste beider Antwortparser wuchs mit der
Antwortgröße statt mit der Zahl echter Probleme (gedeckelt in `core/.../providers/Warnings.kt`, betraf
auch OpenAI und Groq), die Herkunftszeile eines Audioformats nannte Felder, deren Inhalt der Leser
verwirft, die Schrittangabe der Audiovorbereitung trug noch das Verb des Herunterladens, und zwei neue
Textbausteine wichen vom Wortschatz des übrigen Programms ab. Zwei Funde derselben Runde sind nicht
geschlossen, sondern als Punkt 9 und 10 in [Bekannte Probleme](DEFECTS.md) aufgenommen.

**Runde 4 hat die Begründung wiederholt**, diesmal an zwei Stellen zugleich. Erstens schloss die
Korrektur aus Runde 3 an der Herkunftszeile nur die halbe Lücke: Ein Feld, das statt einer Zahl ein Wort
trug, wurde nicht mehr genannt, ein Feld mit einer Zahl außerhalb ihres Wertebereichs dagegen weiter —
während Kommentar und Commit-Nachricht behaupteten, jedes Feld werde mit seinem tatsächlichen Leser
geprüft. Der Beleg lag in einer Testfixtur, die seit längerem `"filesize":0` enthält und die
Herkunftszeile nie geprüft hat. Zwei der vier Reviewer fanden das unabhängig voneinander. Zweitens war
die Zeile „Keine Treffer“ in der Ergebnisansicht nicht mitgezogen worden: Sie las noch die laufende
Eingabe, während die Liste unter ihr bereits dem mitgetragenen Ergebnis folgte, sodass beim Löschen
einer ergebnislosen Suche für einen Moment weder Hinweis noch Liste dastand. Beide Male dieselbe
Fehlerklasse, die dieselbe Korrektur gerade beseitigt hatte, an der jeweils benachbarten Stelle.

Die Herkunftszeile wird deshalb nicht mehr aus einer zweiten Prüfmenge neben den Werten gebildet,
sondern aus den Werten selbst: Es gibt pro Feld nur noch eine Stelle, die auseinanderlaufen könnte.
Ein dritter Fund der Runde war eine richtige Beobachtung mit falscher Einordnung und ist als bewusste
Entscheidung in [Bekannte Probleme](DEFECTS.md) festgehalten, damit sie nicht erneut gemeldet wird.
Zwei weitere Funde stehen dort als Punkt 11 und 12.

Zusätzlich umgesetzt, weil am Gerät sichtbar geworden: Die Audiospurliste ist nach Lesereihenfolge sortiert
(Empfehlung, Originalsprache, weitere Sprachen, Audiodeskription zuletzt), die Ergebnisansicht zeigt bei
aktiver Suche die Trefferzahl statt der Gesamtzahl, und die Auswahlfelder reservieren die erklärende Zeile
auch dann, wenn noch nichts gewählt ist.

Gerätebelege vom 11. September 2026 auf `emulator-5556` (Pixel 10, API 37) mit
`https://youtu.be/aircAruvnKk` (18:40): formatiertes Veröffentlichungsdatum, Untertitelspurauswahl in
`CAPTIONS_THEN_STT`, Audiospur als „English · Original audio · 6.8 MB“ mit technischer Zeile, fertiger
Untertitelauftrag mit 286 Abschnitten, Zustandschip mit Ergebnis darunter, Teilen direkt aus dem Verlauf,
Scrollen am rechten Rand bis zum letzten Abschnitt bei 18:25, Volltextsuche bei offener Tastatur und
Zurücknavigation ohne Appende.

Gates nach Runde 4: 133 JVM-Tests im Modul `core` ohne Fehler (124 vor Runde 3, dann fünf und vier neue
für die Funde der beiden Runden), alle vier Lintberichte (`:app` und `:extractor`, debug und release)
ohne Befund; Lint ist auf `abortOnError` und `warningsAsErrors` gestellt, ein Befund hätte den Build
abgebrochen. Die
Instrumentierungstests sind aus WSL heraus nicht über Gradle startbar; der Grund und der gangbare Weg
über `am instrument` stehen in [Bekannte Probleme](DEFECTS.md). Über diesen Weg zuletzt am
11. September 2026 nach Runde 4 auf `emulator-5556` gelaufen: 188 Instrumentierungstests, 182
bestanden, 6 per Annahme übersprungen (die ausdrücklich opt-in gestellten Inszenierungstests, nach
`AGENTS.md` als `NOT_RUN` zu führen), 0 Fehlschläge. Damit ist auch der einzige Fehlschlag des Stands
vor Runde 3 geschlossen: Er lag nicht am Code, sondern an einem Test, der noch den Vertrag vor
`782aef5` verlangte. Der Plan für die Fortsetzung steht in [Restarbeiten](NEXT_STEPS.md).

Nach Runde 4 umgesetzt: Die Ergebnisansicht zeigt keine rohen Warncodes mehr. Aus über 50
Codefamilien aus vier Quellen werden zwölf Sätze, geordnet nach dem, was sie für das Transkript
bedeuten — erst was fehlt, dann was unsicher ist, dann was der Anbieter über sich selbst gemeldet hat.
Die Zuordnung liegt in `core/.../TranscriptWarnings.kt` und ist damit ohne Gerät testbar; die rohen
Codes bleiben unter den Details erreichbar, und ein unbekannter Code wird weiterhin technisch angezeigt
statt verschluckt.

Gerätebeleg dazu vom 11. September 2026 auf `emulator-5556`, mit dem synthetischen UI-Prüfdatensatz
(10 000 Abschnitte): Die beiden freien Sätze des Datensatzes stehen unverändert da, ohne technischen
Rahmen und ohne am Doppelpunkt abgeschnitten zu werden — der Fehler, den die Formprüfung für
Nicht-Codes verhindert. Die Sprungfreiheit wurde über drei Suchzustände nachgemessen: ohne Suche,
mit 1111 Treffern und ohne Treffer steht die Zählzeile jeweils bei y=696..738, der Detailknopf bei
y=976..1029 und der Hinweis zum langen Text bei y=1076..1118 — identische Werte in allen drei
Zuständen, obwohl die Zählzeile dabei von einer auf zwei Zeilen wechselt.

**Runde 5 hat es zum dritten Mal in Folge bestätigt.** Der wichtigste Fund lag wieder in der
Vorrunde — diesmal in der Zuordnung selbst, die Runde 4 als erledigt gemeldet hatte. Zwei Reviewer fanden
unabhängig dasselbe: `MALFORMED_SEGMENT` ließ den ganzen Eintrag fallen, der Satz sprach aber von
fehlenden Zeitmarken. Der Text fehlte also, und die Anzeige behauptete einen kleineren Schaden als den
eingetretenen. Dazu, aus derselben Runde:

- Die Regel, die Chunk und Position aus einem Code entfernt, riet, wo der Familienname endet. Java-Regex
  nimmt den am weitesten links beginnenden Treffer, also fraß die Regel das letzte Wort jeder Familie, die
  selbst auf dieses Wort endet: `MISSING_SPEAKER_7` wurde zu `MISSING`, `INVALID_CHUNK_OFFSET_5` zu
  `INVALID_CHUNK`. Beide Einträge in der Zuordnung waren damit toter Code, und angezeigt wurde das
  verstümmelte Fragment — schlechter als der Zustand davor. Jetzt wird nur noch eine Zahl am Ende
  gestrichen, und ein Wort davor gehört zum Familiennamen.
- Untertitelparser und Anbieterparser schrieben beide `MALFORMED_SEGMENTS` mit verschiedener Bedeutung. Der
  Untertitelfall heißt jetzt anders, weil dort der Text der Zeile fehlt und dort die Zeitangaben.
- Fünf der zwölf Gruppen wurden von keinem Test je erzeugt. Ein Test prüft jetzt, dass jede Gruppe von
  einem Code erreichbar ist, den ein Parser wirklich schreibt, und schlägt fehl, sobald eine Gruppe ohne
  solchen Code dazukommt.

Zwei Funde habe ich beim Nachrechnen selbst ergänzt, beide aus derselben Frage „trifft der Satz zu, was
der Code tut“: `INVALID_CHUNK_OFFSET` lag bei der Abdeckung, obwohl der Text erhalten bleibt und nur die
Zeit fehlt; und `AUDIO_INTERVAL_GAP_OR_OVERLAP` deckt laut eigenem Namen Lücke und Überlappung ab, sodass
„nicht die ganze Tonspur steckt im Ergebnis“ im Überlappungsfall falsch war. Dafür gibt es eine eigene
dreizehnte Gruppe mit einem Satz, der nur behauptet, was feststeht.

Runde 5 hatte auch einen Fehler in meiner eigenen Aufteilung: Der Bereich für die beiden ersten Reviewer
deckte nur die Warnanzeige ab, die Korrekturen der vierten Runde wären ungeprüft geblieben. Der
nachgeschobene dritte Auftrag hat dann zwei weitere Dinge gefunden: eine belegte Testlücke — kein Test
deckte den Fall ab, dass beide Schlüssel eines Feldpaares einen brauchbaren Wert tragen — und einen
Kommentar, der behauptete, eine leere Zeichenkette trage nichts bei, während sie als `""` bis in die
Herkunftszeile durchkam. Ein dritter gemeldeter Widerspruch in der Dokumentation war zum Zeitpunkt des
geprüften Commits echt und beim nächsten Commit bereits behoben; live war nur eine veraltete Jagdliste.

Gates nach Runde 5: 146 JVM-Tests im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund.

**Runde 6 hat es zum vierten Mal in Folge bestätigt.** Der wichtigste Fund lag wieder in der Korrektur der
Vorrunde, und diesmal in deren nützlichstem Teil: Die Deckelung der Warnliste zählte Einträge, und ein
einziger kaputter Abschnitt kann sie allein füllen. Danach fiel jeder weitere Code ganz weg, auch der erste
einer noch nicht gemeldeten Art. Vierundsechzig fehlerhafte Zeitangaben löschten damit eine spätere Warnung
über fehlenden Text, und der Leser erfuhr nur, dass es mehr Hinweise gab, nie welche. Durch die
Vereinheitlichung der Grenze von 256 auf 64 im selben Commit war das deutlich leichter zu erreichen als
vorher. Jenseits der Grenze wird die erste Warnung einer Art jetzt behalten; welche Arten es gibt,
entscheidet dieses Programm und nicht die Antwort, ihre Zahl ist also klein und beschränkt.

Ein Test hatte das alte Verhalten als Soll festgeschrieben, mit der Begründung, die Kürzungsmarke halte die
Liste ehrlich. Die Begründung hält nicht: Die Marke sagt, dass etwas fehlt, nie was. Die Zusicherung ist
umgedreht.

Weiter aus Runde 6: `SECTION_ALIGNMENT` stand vor den sicheren Verlusten, obwohl der eigene Klassenkommentar
Sicheres vor Unsicherem stellt. Ein Code war nur auf Familienebene geprüft, nie durch die Zusammenfassung.
Und die Behauptung, auf der die ganze Gruppierung beruht — ein unlesbarer Eintrag wird ganz fallengelassen —
war auf der Anbieterseite durch keinen Test gegen den echten Parser abgesichert; auf der Untertitelseite war
sie es. Zwei weitere Behauptungen standen nur in Commit-Nachrichten: dass ein Format mit leerem Codec
verworfen wird, und — vom Reviewer gefunden — dass leere Felder außerhalb des Formatblocks derselben Regel
folgen. Das erste stimmte und ist jetzt geprüft, das zweite stimmte nicht und ist jetzt so.

`family()` und `looksLikeCode()` arbeiten seit dieser Runde ohne Regex. Der Fund der fünften Runde entstand
daraus, dass Java-Regex den am weitesten links beginnenden Treffer nimmt; Indexarithmetik hat diese Falle
nicht und ist nachrechenbar. Nebeneffekt: Die Familie ist billig genug, um sie bei jeder einzelnen Warnung
zu bestimmen — der Test mit einer Million Einträgen läuft in 95 ms.

Außerdem in dieser Runde geschlossen: Punkt 8 der bekannten Probleme (AssemblyAI übernahm die Modellangabe
in beliebiger Länge) und Punkt 6 (Streuwert im Dateinamen).

Gates nach Runde 6: 151 JVM-Tests im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund,
190 Instrumentierungstests auf `emulator-5556` — 184 bestanden, 6 per Annahme übersprungen, 0 Fehler.

**Die Schleife ist nicht konvergiert.** Sechs Runden, keine davon leer. Solange eine Runde noch etwas findet,
ist die nächste fällig — gerade weil die Funde der Runden 3 bis 6 jeweils in den Korrekturen der Vorrunde
lagen.

## UI-Feedback umgesetzt

Auswahlfelder haben abgestimmte Label-/Wertabstände und reservieren auch bei großer
Schrift genug Platz für die längste Option. Untere Navigation mit „Mehr“/„More“
bleibt lesbar. App-Sprache Deutsch/Englisch ist unabhängig von ASR und Theme.
Der separate allgemeine Audioübermittlungs-Schalter ist entfernt; bewusste
Providerwahl plus Auftragsstart erzeugen die intern an Quelle/Konfiguration
gebundene Freigabe. Dies erlaubt keine Live-Tests durch Entwicklungsagenten.

## Artefakte und Grenzen

r81 installiert und erneut gerätegeprüft: Debug-APK r80: `app/build/outputs/apk/debug/app-debug.apk` (159.065.550 Bytes).
SHA-256: `ad94d85da0b1fc3cf3bbc885b3f3fa2857806448d4e20524a3c5971e3a887b32`.
Persönlich signierte Release-APK:
`app/build/outputs/apk/release/SourceScribe-0.1.0-preview.1.apk` (147.376.546 Bytes).
SHA-256: `410b654fd08bdb2b7f8142dd88f440b7f49ffe123e363dd3607582b9e855c20a`.
Signierte App zusätzlich real mit englischer YouTube-Caption bis zum erneut
geöffneten gespeicherten Viewer geprüft; deren SAF-Export separat NOT_RUN.
Version 0.1.0 / Code 1; Preview-Tag `v0.1.0-preview.1`. [Testleitfaden](TRY_PREVIEW.md).
Genaue Build-/Testbefehle, APK-Hashes, Versionen und Rohbelegnamen im
[Preview-Prüfbericht](reports/2026-09-08-preview.md), chronologische Fehlversuche
und Reviewkorrekturen im [Integrationsbericht](reports/2026-09-07-integration.md).

Öffentliche APK-Verteilung bleibt wegen fehlender vollständiger
FFmpeg-Corresponding-Source-/Lizenzzuordnung gesperrt. GitHub-Preview enthält nur den Quellstand; die signierte APK bleibt lokal.
Die GitHub-CI hat Build/JVM/Lint bestanden und den Emulator erfolgreich gestartet.
Run 34252821287 scheiterte anschließend im Gerätetest; der konkrete Einzelfehler
ist noch unbekannt. Eine gezielte Berichtsausgabe ist vorbereitet, aber wegen
Pause noch nicht in CI ausgeführt. Die abschließenden kleinen UI-Änderungen r80
sind in r81 mit 180 App-Tests, betrachtetem Screenshot und aktuellem statischen
Releaseaudit nachgeprüft. Offizielle Signatur-/Ausrichtungsprüfung ebenfalls PASS.

Nach dem belegten WSL-Speicherfehler sind 16 GiB Swap aktiv. Builds verwenden einen
Worker, 2 GiB Java-Heap und projektlokale Caches. Keine neue globale Konfiguration.
[Diagnose und Speicherbegrenzung](reports/2026-09-07-wsl-recovery.md).

Die aktuelle Übergabe steht in [HANDOFF](HANDOFF.md), konkrete Restarbeiten in
[NEXT_STEPS](NEXT_STEPS.md), verifizierte Fallstricke in [LEARNINGS](LEARNINGS.md).
Keine automatische Fortsetzung nach diesem begrenzten Preview-Abschluss.
Bereits erledigte Implementierung und Fixtureprüfungen bleiben erhalten.
