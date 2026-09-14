# Tatsächlicher Projektstatus

**Stand:** 14. September 2026. **Freigabe:** Persönliche Preview; vollständige v1 weiterhin blockiert.
Der Preview-Abschluss vom 8. September steht unten; seither ist die Nutzerrückmeldung vom
10. September eingearbeitet, siehe den nächsten Abschnitt. **Die Testzahlen weiter unten in diesem
Abschnitt sind der Stand vom 8. September und nicht der heutige.** Heute, nach Runde 20, sind es 182
JVM-Tests im Modul `core`, 207 Instrumentierungstests im Modul `app` und 50 im Modul `extractor`,
zusammen 439, davon 433 ausgeführt, `core` zuletzt im Build für Bouncy Castle 1.86, `app` im Gate der Runde 18; die
Runde-11-Passage sagt, warum die Tests im Modul `extractor` zehn Runden lang in keiner Gate-Meldung vorkamen.
Diese Zahlen standen bis Runde 14 unter dem Wort „Heute“ auf dem Stand der
zwölften Runde — die Gate-Zahlen einer Runde stehen in ihrer eigenen Passage, und dieser Satz oben muss
mitwandern. Eine Testzahl, die als heutige gelten soll, gehört nur hierher. App-Quellstand ist die Spitze
von `main`, CI-Diagnose `f9d4f8b`, lokales `main` und öffentliches
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

## Nutzerrückmeldung vom 10. September 2026 und zwölf Reviewdurchgänge

**Stand:** 11. September 2026. Der Nutzer hat die Preview am Gerät getestet und 20 Punkte gemeldet.
17 davon sind umgesetzt und am Emulator oder durch Tests belegt; die drei offenen stehen mit Stelle und
fehlendem Nachweis in [Bekannte Probleme](DEFECTS.md).

Dazu kamen elf vollständige Runden adversarischer Reviews mit Sonnet-5-Agenten, jede Runde über die
Korrekturen der vorherigen. Die ersten sechs stehen in diesem Abschnitt, die Runden 7 bis 11 in eigenen
Abschnitten darunter: Runde 1 über `782aef5`, Runde 2 über `5a6bfef`, Runde 3 über `42f723e`,
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
Antwortgröße statt mit der Zahl echter Probleme (gedeckelt in `core/.../Warnings.kt`, damals noch unter
`providers/`, betraf
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
Codefamilien aus vier Quellen wurden zunächst zwölf Sätze, seit Runde 5 dreizehn, geordnet nach
dem, was sie für das Transkript bedeuten — erst was fehlt, dann was unsicher ist, dann was der Anbieter über sich selbst gemeldet hat.
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
191 Instrumentierungstests auf `emulator-5556` — 185 bestanden, 6 per Annahme übersprungen, 0 Fehler.
Der 191. ist der Test zu Punkt 19: Er führt einen echten RAW-Export aus und danach einen Textexport
desselben Artefakts, weil die Regel für RAW-Geschwister bisher nur im Namensbauer geprüft war.

### Runde 7

Drei Reviewer. Der für die übrige Codeänderung bekam `2296fe5`, `7c913ca` und `08947bc` einzeln benannt,
der für Texte und Doku den Bereich `029a53f..f0f6884`, der für die Kernmechanik die Korrekturen der sechsten
Runde. Vier Funde, drei bestätigt, einer beim Nachrechnen entkräftet.

Diese Passage nannte zunächst pauschal „`2296fe5..d6af9fd`, also alle Commits der Runden 5 und 6“. Das war
als Zusammenfassung falsch: Diese Schreibweise lässt `2296fe5` selbst aus, und die drei Fix-Commits der
fünften Runde sind Vorfahren davon, liegen also gar nicht darin. Abgedeckt waren sie trotzdem, weil die
Aufträge sie einzeln beziehungsweise über den zweiten Bereich benannten — die Zusammenfassung stimmte nicht,
die Abdeckung schon.

**Der wichtigste lag wieder in der Vorrunde, und wieder in ihrem nützlichsten Teil.** Runde 6 hatte die
Deckelung der Warnliste so erweitert, dass die erste Warnung einer noch nicht gemeldeten Art auch jenseits
der Grenze erhalten bleibt. Die Begründung stand im Klassenkommentar: Welche Arten es gibt, entscheidet
dieses Programm und nicht die Antwort, ihre Zahl ist also klein. Das stimmte für die drei Aufrufstellen und
für nichts in der Klasse selbst — sie deckelte die Zahl der Arten nicht. Fünftausend verschiedene
Freitexteinträge, oder fünftausend Großbuchstabennamen ohne angehängte Zahl, blieben alle in der Liste, und
die Kürzungsmarke wurde nicht einmal gesetzt: genau das Wachstum mit der Antwortgröße, gegen das die Klasse
existiert.

Die Korrektur ist eine harte Obergrenze auf die Zahl der Arten und damit auf die Liste: höchstens `LIMIT`
plus `KIND_LIMIT` Einträge, wie ein Aufrufer seine Codes auch benennt. Damit die Grenze eine Decke bleibt
und nicht zur Arbeitsgrenze wird, ist die Zuordnung von Codefamilien zu Gruppen von einem `when` in Daten
überführt — `TranscriptWarnings.GROUPED_FAMILIES`. Die Namen sind dadurch zählbar, ein Test hält die Zahl
der selbst benannten Arten mit doppeltem Abstand unter der Decke, und derselbe Umbau schließt eine zweite
Lücke aus Runde 5: von 59 Familiennamen kamen 21 in keinem einzigen Test vor, und 32 wurden nicht durch
die Zusammenfassung geführt, die dieser Test prüft. Jeder wird jetzt in fünf Schreibweisen durch sie
geführt.

Diese beiden Zahlen standen hier in zwei Anläufen falsch, und der Weg dahin gehört dazu. Zuerst als 46,
gerechnet als 59 minus die dreizehn Namen der Ein-Beispiel-pro-Gruppe-Liste — zu hoch, weil andere Tests
weitere Namen nebenbei nennen. Dann als „34 in überhaupt keinem Test“, was die Zahl der engen Frage mit den
Worten der weiten verband: 34 galt nur für die beiden Dateien, die die Zusammenfassung ausführen, nicht für
die Suite. Nachgezählt über den Stand `d6af9fd`, jeden Namen als Namen gelesen und nicht als Anfang eines
längeren: 38 der 59 kommen irgendwo in `core/src/test` vor, 21 nirgends; in den beiden Dateien mit der
Zusammenfassung sind es 27 und 32. Wer die Zahl nachrechnet, braucht die Grenze zwischen `MALFORMED_WORD`
und `MALFORMED_WORDS` — ohne sie kommt etwas anderes heraus, und genau daran sind meine ersten beiden
Anläufe gescheitert.

Ausgeliefert wurde in derselben Runde noch eine Begrenzung, die keiner der Funde verlangt hat: Die
gemeldete Originalsprache bekam eine Grenze von 100 Zeichen, weil sie als einziges Wurzelfeld gar keine
trug — was, wie Runde 8 gezeigt hat, so nicht stimmte, und was Runde 9 vom Kürzen auf Verwerfen umgestellt
hat.

Weiter bestätigt: `CaptionTrack.name` folgte der Leerstringregel nicht, die Runde 6 für die übrigen
Quellfelder eingezogen hatte — ein als `""` gemeldeter Spurname erschien in der Herkunftszeile als `name=`,
wo ein fehlender Name `unknown` ergibt. Dieselbe halb geschlossene Begründung wie zweimal vorher.
Und `SyncTranscriptParser` nannte für einen leeren, aber überlangen Modellwert den falschen Grund: zu lang,
obwohl der Wert nichts benannte und kein Kürzen ihn brauchbar gemacht hätte. Ein kurzer leerer Wert war dort
gar keine Meldung, während der AssemblyAI-Adapter ihn immer meldet. Beide antworten jetzt gleich. Die Folge
für Punkt 11 der bekannten Probleme ist dort vermerkt, weil dies der zweite Fall dieser Art ist.

**Ein Fund hat das Nachrechnen nicht überlebt.** Gemeldet als hoch: `compose` berechnet das Budget für den
Titel als Bytegrenze minus der *Zeichenzahl* des Teils, der überleben muss; Sprache und Quell-ID reichen je
40 Byte, bei Dreibytezeichen also 13 Zeichen, das Budget fällt um bis zu 52 Byte zu groß aus, und der
abschließende Schnitt nimmt das Ende, wo der Streuwert sitzt. Die Einheitenverwechslung ist echt. Auslösbar
ist sie nicht: `generatedStem` schickt den Titel vorher durch `safePart` mit dessen Standardwert von 40 Byte,
und der Reviewer hat diese Schranke auf Sprache und Quell-ID angewendet, aber nicht auf den Titel. Sein
Beispiel mit 300 Zeichen Titel erreicht `compose` nie. Korrigiert habe ich die Arithmetik trotzdem, weil die
Grenzen sonst über zwei Schranken halten, die nichts voneinander wissen, und weil die Titelschranke
anzuheben die naheliegendste nächste Änderung an dieser Datei ist. Der Test ist entsprechend keine
Regressionsprobe, sondern eine Schrankenprobe über 648 Namenskombinationen; Code und
[Bekannte Probleme](DEFECTS.md) sagen genau das.

**Die Gegenprobe hat auch einen Fehler in meinem eigenen Test gefunden.** Ich habe alle Korrekturen dieser
Runde vorübergehend zurückgenommen und die Tests laufen lassen, um zu sehen, welcher neue Test wirklich
greift. Sechs fielen, einer nicht. Dieser eine, damals noch als Regressionsprobe für den gemeldeten Fund
geschrieben, setzte die Quelle mit `document().source.copy(...)` neu und ersetzte damit den langen Titel
durch den kurzen Standardwert. Nach der Korrektur dieses Aufbaufehlers fiel er immer noch nicht — und das
war der Beleg, der den Fund entkräftet hat, weil damit die Titelschranke als Ursache übrig blieb. Der Test
ist daraufhin durch die Schrankenprobe ersetzt worden, die jetzt im Baum steht; im heutigen Code ist der
Aufbaufehler also nicht mehr zu finden.

Ohne den Durchlauf wäre der erste Test als grüner Test durchgegangen, der nichts prüft, und der Fund wäre
als behoben gemeldet worden, obwohl nichts ihn belegt hätte. Die Gegenprobe gehört ab jetzt zur Runde.

Aus der Doku-Prüfung: „zwölf Sätze“ stand an zwei Stellen, obwohl `SECTION_ALIGNMENT` schon in Runde 5 die
dreizehnte Gruppe war; der Pfad `providers/Warnings.kt` in der Runde-3-Passage zeigte seit Runde 5 auf
nichts. Beides korrigiert. Die vom selben Reviewer gemeldete Angabe 190/184 Instrumentierungstests war zum
Zeitpunkt seines Lesens richtig und mit `d6af9fd` bereits auf 191/185 nachgeführt — ein Zeitversatz, kein
Fund.

Gates nach Runde 7: 160 JVM-Tests im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund,
191 Instrumentierungstests auf `emulator-5556` — 185 bestanden, 6 per Annahme übersprungen, 0 Fehler.
Die neun neuen JVM-Tests sind die aus dieser Runde.

### Runde 8

Drei Reviewer über die vier Commits der siebten Runde, einzeln benannt statt als Bereich, nachdem die
Bereichsschreibweise in der Vorrunde schon einmal daneben lag. Neun Funde, alle bestätigt, keiner in der
Produktionslogik der Vorrunde selbst — dafür zwei in dem, was ich über sie geschrieben habe.

**Der wichtigste betrifft einen Test aus Runde 7 und ist wieder dasselbe Muster.** Der neue Test, der jeden
Familiennamen durch die Zusammenfassung führt, verspricht in seinem Kommentar Schutz gegen „einen Namen, der
gegenüber seinem Erzeuger falsch geschrieben ist“. Das kann er nicht halten: Er nimmt Eingabe und Erwartung
aus derselben Zuordnung, also stimmen die beiden miteinander überein, was immer dort steht. Eine Zusage, die
weiter trägt als das, was der Code prüft — diesmal im Kommentar eines Tests statt in dem einer Klasse.

Geschlossen ist das mit einer zweiten Quelle statt mit einer Korrektur des Kommentars allein:
`everyGroupIsReachableFromACodeSomeParserActuallyWrites` führt jetzt nicht mehr ein Beispiel pro Gruppe,
sondern einen Eintrag pro Familie, abgeschrieben aus den vier Dateien, die Warnungen aufzeichnen, in der
Form, die die aufzeichnende Zeile wirklich erzeugt. Eine Umbenennung nur auf einer der beiden Seiten fällt
damit auf. Der Grund, warum es diese zweite Quelle von Hand braucht: Dreizehn der 59 Namen kommen in den
Erzeugern als Zeichenkette überhaupt nicht vor, weil die Zeile sie aus Teilen zusammensetzt
(`"MISSING_${"$"}{if (word) "WORD" else "SEGMENT"}_TEXT_${"$"}index"` und ähnlich). Weder eine Suche noch ein Leser
findet sie dort.

**Der zweite große Fund widerlegt eine Behauptung aus meiner eigenen Commit-Nachricht.** Sie sagte, die
Originalsprache sei „als einziges Wurzelfeld“ ohne Längengrenze gewesen. Sie war es nicht: Das Datum hatte
ebenfalls keine, die Adresse des Vorschaubilds auch nicht, und die Sprache eines Audioformats steht zwischen
einer Notiz und einem Container, die beide begrenzt sind. Genau die halb geschlossene Lücke, die diese
Schleife inzwischen viermal gefunden hat, diesmal in meiner Begründung statt in meinem Code. Datum und
Formatsprache bekommen eine Grenze von 100 Zeichen; die Adresse wird an derselben Grenze wie die
Untertiteladresse abgelehnt statt gekürzt, weil eine halbe Adresse keine kürzere ist, sondern eine falsche.
Runde 9 hat dieses „abgelehnt statt gekürzt“ dann auf Datum und Sprache mit ausgedehnt — siehe dort.

Dazu ein Fehler, den ein Reviewer beim Durchspielen einer Testlücke gefunden hat und der älter ist als diese
Runden: Eine Dateiendung aus lauter Zeichen, die ein Name nicht tragen kann, fällt nicht auf den Ersatzwert
zurück. Die Zeichen werden einzeln durch Unterstriche ersetzt und die Folge dann zu einem einzigen
zusammengefasst, das Ergebnis ist also nie leer, und der Test auf Leere griff nicht. Eine Endung `???` wird
damit zu `_`, und die Datei nennt gar kein Format mehr.

Dass das je passiert wäre, behauptet dieser Absatz nicht mehr: Alle drei Stellen, die heute eine Endung
liefern, geben nur Werte aus festen Listen her. Erreichbar ist der Fall über den selbst gewählten Namen, wo
er jetzt sichtbar abgelehnt wird statt eine Datei namens `_` zu erzeugen. Die Härtung selbst gilt einer
gemeinsam genutzten Funktion und damit auch dem nächsten Aufrufer ohne solche Liste.

Aus der Doku-Prüfung, alle drei nachgezählt und bestätigt: die Zahl 46 war zu hoch (richtig sind 34, siehe
oben); die Bereichsangabe für die Reviewer der Vorrunde stimmte nicht mit der Git-Semantik überein; und zwei
Standzeilen waren bei „sechs Runden“ stehengeblieben, obwohl derselbe Commit die siebte beschrieb. Dazu ein
Pfad in Punkt 18, dem seit Runde 5 das Segment `providers/` fehlt.

**Auch in dieser Runde hat die Gegenprobe gearbeitet.** Vier Tests fallen mit wieder eingebauten Fehlern:
die drei neu geschriebenen und der umgebaute Familientest, also auch der Tippfehler-Fall, für den die zweite
Quelle gebaut wurde. Was diese Runde sonst an Zusicherungen ergänzt hat, hält bestehendes Verhalten fest und
soll nicht fallen. Zwei neue Testmethoden kommen dazu, von 160 auf 162; die dritte hat eine bestehende
ersetzt.

Gates nach Runde 8: 162 JVM-Tests im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund,
191 Instrumentierungstests auf `emulator-5556` — 185 bestanden, 6 per Annahme übersprungen, 0 Fehler.

### Runde 9

Drei Reviewer über die vier Commits der achten Runde. Der für die Kernmechanik hat alle 59 Einträge der
neuen Familienliste einzeln gegen die Zeile geprüft, die sie schreibt, und keinen Fehler gefunden — die
Liste stimmt. Die Funde liegen anderswo, und der wichtigste wieder in der Korrektur der Vorrunde.

**Die Begrenzung aus Runde 8 hat eine zugesicherte Eigenschaft gebrochen.** `AudioTracks.automatic`
weigert sich, zwischen zwei Tonspuren zu wählen, deren Sprachen sich unterscheiden; welche Sprache
gesprochen wird, ist eine Entscheidung des Lesers und bleibt bei ihm. Das Kürzen auf 100 Zeichen konnte
zwei verschiedene Sprachangaben, die in ihren ersten hundert Zeichen übereinstimmen, zu einer machen — die
Weigerung hörte still auf zu greifen, und die App entschied selbst. Ein Fix, der eine Grenze einzog und
dabei eine Zusicherung aufhob.

Die Regel steht jetzt ausgesprochen da statt unterstellt: Prosa überlebt das Kürzen, ein Bezeichner nicht.
Datum, Sprache, Codec und Container werden ganz behalten oder ganz verworfen, wie es die Adresse des
Vorschaubilds seit Runde 8 schon wird; Titel, Kanal und Notiz bleiben Prosa und werden weiter gekürzt.

**Der schwerste Doku-Fund steht in einem Commit gegen sich selbst.** Derselbe Commit, der in DEFECTS 4 die
Annahme „diese Codes bedeuten alle dasselbe“ ausdrücklich verworfen hat, ließ dieselbe Annahme samt der
alten Zahl in Abschnitt 3 von NEXT_STEPS stehen — und genau dieses Dokument soll die Arbeit ohne
Wiedereinlesen fortsetzen lassen. Ein Agent, der nur dort liest, hätte die widerlegte Diagnose bekommen.
Beide Stellen tragen jetzt dieselbe Aussage, und die Zusammenfassung verweist ausdrücklich auf den Punkt.

Weiter aus der Doku-Prüfung, alle nachgezählt und bestätigt: „Elf“ nur zusammengesetzte Namen sind
dreizehn. Die Zahl 34 galt für die beiden Dateien, die die Zusammenfassung ausführen, stand aber unter den
Worten „in überhaupt keinem Test“ — quer über die Suite sind es 21. Der entscheidende Testausdruck in
Punkt 21 hatte eine Ziffer zu wenig, an der Zeichenzahl ohne jede Ausführung erkennbar. Die Auszählung
hinter „43 Codes“ nennt ihre Quellen unvollständig, weil `JobCoordinator` auch die Codes dreier weiterer
Dateien durchreicht — 43 ist eine Untergrenze. Zwei Codes standen zu Unrecht in der Liste der gewöhnlichen
Betriebsausgänge. Und sieben Zitate schlossen mit einem geraden Anführungszeichen statt dem deutschen.

**Einen Fund habe ich zurückgewiesen.** Der Kernreviewer schrieb, kein Test führe `SyncTranscriptParser`
aus, alle 26 seiner Familien seien nur über die Zuordnung geprüft. `SyncProviderTest` instanziiert die
OpenAI- und Groq-Adapter elfmal und nagelt zehn dieser Codes über echte Durchläufe fest, darunter
`MALFORMED_SEGMENT`, `MISSING_WORD_TEXT` und beide Modellcodes. Selbst nachgezählt, bevor ich etwas daran
geändert habe.

**Eine Lehre über das Verfahren, nicht über den Code.** Ein Fund des Doku-Reviewers — die Doku nenne das
Verwerfen „Kürzen“ — betraf eine Änderung, die noch gar nicht committet war: Ich hatte `ExtractorMetadata`
bearbeitet, während er lief, und er liest zum Prüfen auch Code. Recht hatte er trotzdem, nur eben über
einen Stand, den es beim Commit noch nicht gab. Solange ein Reviewer läuft, wird an keiner Datei mehr
gearbeitet, die er lesen könnte — auch nicht an einer außerhalb seines Auftrags.

Gates nach Runde 9: 162 JVM-Tests im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund,
191 Instrumentierungstests auf `emulator-5556` — 185 bestanden, 6 per Annahme übersprungen, 0 Fehler.
Die Gegenprobe mit wieder eingebautem Kürzen und gesenkter Adressgrenze lässt beide betroffenen Tests
fallen.

### Runde 10

Drei Reviewer über die beiden Commits der neunten Runde. Einer für den Code, einer für die Doku, und
einer nicht für einen Commit, sondern quer durchs Repository auf die zwei Muster, die diese Schleife schon
nachweislich erwischt hat. Der wichtigste Fund liegt wieder in der Vorrunde, und diesmal haben ihn zwei
Reviewer unabhängig voneinander gefunden und jeder mit einem eigenen, ausgeführten Test belegt.

**Die Korrektur der neunten Runde hat den Fehler nicht beseitigt, sondern verschoben — und dabei
verbreitert.** Runde 8 kürzte Sprachangaben auf 100 Zeichen; zwei verschiedene Tags mit gleichen ersten
hundert Zeichen wurden dadurch zu einem, und `AudioTracks.automatic` hörte auf, die Wahl zwischen
verschiedenen gesprochenen Sprachen zu verweigern. Runde 9 ersetzte das Kürzen durch Verwerfen — und ein
verworfener Wert ist `null`, genau wie ein nie angegebener. Zwei Tonspuren mit überlangen, schon im ersten
Zeichen verschiedenen Tags lesen sich seither beide als `null`; die Weigerung greift nicht. Der Auslöser
wurde dabei weiter, nicht enger: vorher brauchte es zwei Tags mit gemeinsamem Anfang, jetzt genügen zwei
überlange, und auch ein überlanger neben einer Spur, die gar keine Sprache nennt.

Die Unterscheidung steht jetzt im Datensatz selbst: `AudioTrack.languageRefused` sagt, dass die Quelle eine
Sprache genannt hat, die der Datensatz nicht tragen konnte. Verworfen ist nicht dasselbe wie nie gesagt —
das Erste heißt, dass die Quelle zwei Spuren auseinandergehalten hat.

**Beim Nachverfolgen kam dieselbe Verwechslung noch dreimal vor, in derselben Datei.** Ob ein Sprach-Tag auf
`-desc` endet, ob eine Notiz `(original)` enthält, ob sie `drc` enthält — alle drei Fragen wurden an die
begrenzte Kopie gestellt statt an das, was angekommen ist. Alle drei entscheiden, welche Spur ohne Rückfrage
gewählt wird: die erste, ob eine Bildbeschreibung als Dialog durchgeht; die zweite, welche Spur als Original
gilt; die dritte die Reihenfolge. Die Regel ist jetzt vollständig ausgesprochen: **Tragen ist begrenzt,
Fragen nicht.** Beobachtet wurde keiner der drei Fälle: Reale Sprach-Tags sind unter zwanzig Zeichen
lang — das hat ein Reviewer gegen die Anbieterdokumentation geprüft — und Formatnotizen sind ein
paar Worte, wofür es allerdings nur die Fixtures dieses Repositorys als Beleg gibt.

**Vier Grenzen wurden nur aus sich selbst gelesen.** Ein Test, der seine Eingabe aus derselben Konstante
baut, die er prüft, wandert mit ihr mit; genau das hat Runde 9 an der Bildadresse repariert. Dieselbe Form
steckte noch in `ArtifactFiles.MAX_CANONICAL_BYTES` (32 MiB), `JobLimits.MAX_AUDIO_SECONDS` (zehn Stunden,
eine Zusage des Produkts), `Warnings.LIMIT` (64) und `AssemblyAiAdapter.MAX_DURATION_MS` (zehn Stunden, die
Grenze des Anbieters). Drei davon haben die Reviewer durch Absenken der Konstante und einen grün bleibenden
Lauf belegt. Ausgeschrieben stehen jetzt fünf: dieselbe Testzeile nagelt neben `Warnings.LIMIT` auch
`Warnings.KIND_LIMIT` (160) fest. Die fünfte ist die schwächste der Funde, weil sie als einzige schon eine
echte Untergrenze hatte — seit Runde 7 prüft ein Test, dass sie mindestens das Doppelte aller Namen fasst,
die das Programm selbst erzeugen kann, also 142. Zwischen 143 und 159 hielt sie nichts.

Warum diese fünf so lange durchgekommen sind, ist keine Zufallsfrage, sondern eine Frage der Sichtbarkeit —
aber nicht der zwischen `public` und `internal`. Die Tests, die eine dieser Grenzen prüfen, liegen in
`core/src/test`, also im selben Modul; von dort ist `internal` genauso sichtbar wie `public`, und
`Warnings.LIMIT` steht in einer `internal class`, ist also gar nicht öffentlich. Entscheidend ist eine
andere Linie: `MAX_FILENAME_PART_BYTES` und die Grenzen in `RetryDelay` sind `private`. Ihre Tests kamen
auch aus demselben Modul nicht an sie heran und mussten die Zahl ausschreiben, weshalb sie dieses Muster
nie hatten. Nicht Sorgfalt hat sie geschieden, sondern Erreichbarkeit vom Test aus — was auch sagt, wo als
Nächstes zu suchen ist.

**Eine eigene Behauptung aus Runde 9 war zu großzügig.** Die Commitnachricht sagte, zwei neue Zusicherungen
fingen einen doppelten Eintrag in der Familienliste. Ausgeführt fängt ihn eine; die zweite ist aus den
beiden anderen rechnerisch bereits ableitbar und kann bei dieser Reihenfolge nie selbst die fallende Zeile
sein. Falsch ist sie nicht, zusätzlich fängt sie nichts.

Aus der Doku-Prüfung: Ein Kommentar im Familientest trug noch die alte „Elf“, die Runde 9 im Fließtext schon
zu dreizehn korrigiert hatte. Die Jagdliste für diese Runde nannte `CaptionTracks` als Kandidaten — dieses
Symbol gibt es nicht, es gibt nur `CaptionTrack`, eine Datenklasse ohne Auswahllogik. Sie schrieb „korrigiert
fünf Zahlen“ und zählte sieben auf. Und sie forderte, „21 in keinem Test“ nachzuzählen, ohne zu sagen, dass
diese Zahl zum Stand `d6af9fd` gehört: Seit Runde 8 stehen alle 59 Namen von Hand im Familientest, heute
wären es 0. Ein Agent, der der Liste wörtlich folgt, hätte eine korrekte Zahl für falsch gehalten.

**Und eine Lehre über das Verfahren, die ich mir selbst geschrieben und selbst gebrochen habe.** Runde 9
endete mit dem Satz, während ein Reviewer läuft, werde an keiner Datei gearbeitet, die er lesen könnte.
Ich habe mich daran gehalten — und gleichzeitig einem der drei Reviewer ausdrücklich erlaubt, für
Gegenproben Dateien zu verändern, während die beiden anderen lasen. Beide haben den Baum unter sich
wandern sehen und es gemeldet. Die Regel gilt nicht nur für mich: Ein Reviewer, der verändert, und einer,
der liest, laufen nicht gleichzeitig.

Gates nach Runde 10: 168 JVM-Tests im Modul `core` ohne Fehler (162 vorher, sechs neue Methoden), alle vier
Lintberichte ohne Befund, 191 Instrumentierungstests auf `emulator-5556` — 185 bestanden, 6 per Annahme
übersprungen, 0 Fehler. Die Gegenprobe, die jede Korrektur dieser Runde zurücknimmt, lässt genau sieben Tests
fallen: die sechs neuen Methoden und die eine bestehende, die eine neue Zusicherung bekommen hat. Kein
weiterer Test fällt, keine der Korrekturen ändert also bestehendes Verhalten.

### Runde 11

Diesmal in zwei Phasen: erst zwei rein lesende Reviewer, danach der verändernde allein auf dem Baum. Das
war die Lehre aus Runde 10, und sie hat funktioniert — kein Reviewer hat diesmal einen Baum geprüft, der
sich unter ihm bewegte.

**Der schwerste Fund betrifft nicht den Code, sondern die Beweisführung dieser Schleife selbst.** Seit
Runde 6 stand in jeder Gate-Meldung „191 Instrumentierungstests“. Das Modul `extractor` hat eigene 39, und
die liefen in keiner der zehn Runden. Schlimmer ist die zweite Hälfte: Ohne das Argument
`-e sourcescribeEngineUpdate true` überspringt diese Suite vierzehn Tests per Annahme und schreibt trotzdem
`OK (39 tests)`. Ohne Flagge 21 bestanden und 18 übersprungen, mit ihr 35 und 4 — beide Läufe sehen grün
aus, und nur der zweite prüft Prüfsumme, Slotwechsel und Rückrollung der signierten Engine-Updates.

Der Grund ist der dokumentierte Notbehelf selbst: Weil Gradle aus WSL den Emulator nicht erreicht, wird das
rohe Textprotokoll von `am instrument` gelesen, und genau das schreibt `OK`, sobald kein harter Fehlschlag
auftrat — eine übersprungene Annahme zählt dort nicht. Ein Reviewer hat beide Läufe nachgestellt und die
Zahlen bestätigt, und dabei alle neun Instrumentierungsargumente des Projekts aufgelistet: sieben davon
sind echte Schranken, hinter denen ohne Flaggen zusammen 24 Tests still liegen bleiben. Der Ablauf in
NEXT_STEPS und der Wartungshinweis in DEFECTS nennen jetzt beide Suiten, beide Aufrufe und beide Zahlen.

**Zwei Sicherheitsgrenzen hatten überhaupt keinen Test.** `EngineVerifier.MAX_ARCHIVE_ENTRIES` und
`MAX_TOTAL_UNPACKED_BYTES` sind die Abwehr gegen ein Archiv, das klein ankommt und groß wird — die Stelle,
an der ein signiertes Update ausgepackt wird. Dass der Mechanismus greift, war nie bezweifelt und nie
gezeigt worden; das sind verschiedene Dinge. Beide haben jetzt einen Test mit den echten Produktionswerten.

**Und die Gegenprobe hat an einem davon sofort gearbeitet.** Der Test für die Eintragszahl blieb grün,
nachdem ich zwei der drei Stellen abgeschaltet hatte, die diese Grenze durchsetzen. Der Grund ist gutartig
und war mir unbekannt: Die Zahl wird schon aus dem Endverzeichnis des Archivs gelesen und dort verworfen,
bevor ein einziger Eintrag geöffnet wird. Der Testname behauptete genau das — jetzt prüft er es auch,
über die Meldung, die dabei entsteht.

**Das Muster „ein Test, der aus seiner eigenen Konstante baut“ hat sechs weitere Instanzen.** Alle vom
zweiten Reviewer durch Absenken der Konstante und einen grün bleibenden Lauf belegt, eine davon zum ersten
Mal auf dem Gerät statt in der JVM.

Hier stand daraufhin eine laufende Summe seit Runde 9. Runde 12 hat sie gestrichen, weil drei
Nachzählungen drei verschiedene Ergebnisse hatten — meine eigene, die des lesenden Reviewers und die der
Commit-Nachricht von `d99f950` — und keine davon aus dem Dokument heraus nachprüfbar war. Dass sich eine
Zahl nicht reproduzieren lässt, ist derselbe Befund, den sie belegen sollte: Einzeln suchen war die falsche
Form. Was zählbar bleibt, steht jetzt in `StatedNumbersTest` und ist dort pro Zeile nachlesbar.

**Die strukturelle Antwort ist eine einzige Datei.** `StatedNumbersTest` schreibt jede Zahl aus, die dieses
Modul behauptet: die acht Preise hinter jedem Kostenvoranschlag samt Stichtag und Quelle, die Grenzen der
drei Anbieter, die eine Grenze, die dem Nutzer versprochen wird, und die Budgets, für die es keine zweite
Quelle gibt — die letzten ausdrücklich als solche benannt, weil „keine zweite Quelle“ eine ehrliche
Antwort ist und eine unausgesprochene Annahme nicht. Die verstreuten Festlegungen aus den Runden 10 und 11
sind dorthin gewandert, damit keine Zahl an zwei Stellen behauptet wird.

**Dabei fiel etwas auf, das kein Reviewer gesucht hatte.** Fünf der acht Preisangaben, aus denen der
Kostenvoranschlag gebaut wird, standen in keinem Test: beide AssemblyAI-Modellpreise, beide Aufschläge und
der Whisper-Preis von OpenAI. Ebenso wenig die zehn Sekunden, die Groq mindestens berechnet, zwei der drei
Stichtage und jede der drei Quellseiten. Ein Tippfehler um den Faktor zehn hätte den Betrag verschoben, den
jemand vor dem Bezahlen zu sehen bekommt, und jeder Test wäre grün geblieben. Die drei übrigen Preise waren
in `SyncProviderTest` ausgeschrieben — meine erste Fassung dieses Satzes behauptete, keiner sei es, und war
damit selbst eine Zahl ohne Nachzählen.

Weiter aus den beiden lesenden Phasen: Der Herkunftsnachweis schrieb `language=unknown`, wo die Quelle sehr
wohl eine Sprache genannt hatte, die der Datensatz nicht tragen konnte; er schreibt jetzt
`language=stated-but-unusable`. Mein eigener Kommentar aus Runde 10 nannte den Rohdaten-Grenzwert „einen
anderen Fall“ — er ist keiner, `ByteArray(n + 1)` ist für jedes `n` größer als `n`. Die Behauptung „alle
vier sind öffentlich“ stimmte nicht, weil `Warnings` eine `internal class` ist; die Erklärung ist dadurch
besser geworden, denn die entscheidende Linie ist nicht `public` gegen `internal`, sondern `private` gegen
vom Test aus erreichbar. Festgenagelt waren fünf Grenzen, nicht vier. Und NEXT_STEPS verlangte, die Commits
der Runde einzeln zu benennen, und nannte keinen.

**Auch die App-Suite hatte solche Schranken, und vier ihrer sechs Auslassungen waren ausführbar.**
`ProcessRecoveryTest` stellt den Prozesstod über einen Neustart hinweg nach und verlangt pro Lauf genau
eine Stufe — vier einzelne Läufe, alle vier bestanden, zum ersten Mal in dieser Schleife. Die zwei
übrigen bleiben mit Absicht liegen: `UiFixtureTest` ist kein Test, sondern ein Saatgenerator, der
synthetische Daten in die App-Datenbank schreibt, und `EngineJobPinningTest` braucht ein echtes Release.

Gates nach Runde 11, vollständig gezählt: **173 JVM-Tests** im Modul `core` ohne Fehler (169 vorher —
168 am Ende von Runde 10, plus den einen Test, den `40f5894` früher in dieser Runde zu
`TranscriptExporterTest` hinzugefügt hat: vier
neue Methoden für die Zahlen, zwei für die Archivgrenzen, zwei durch die Zusammenlegung entfallen), alle
vier Lintberichte ohne Befund, **191 Instrumentierungstests** im Modul `app` — 185 im gemeinsamen Lauf, vier
weitere einzeln über ihre Stufen, 0 Fehler — und **erstmals 39** im Modul `extractor` — 35 bestanden,
0 Fehler. Von 403 Tests sind damit 397 ausgeführt. Die sechs übrigen stehen als eigene Zeile in der
Blockadetabelle von NEXT_STEPS, statt als Zahl in einem Satz zu verschwinden. Die Gegenprobe, die jede
Korrektur dieser Runde zurücknimmt, lässt fünf Tests fallen.

### Runde 12

Commits: `41d9eb7` (Zuschlag und Zahlenliste), `c12b7ee` (Kostenschau), `b239673` (Archivwache),
`f9be165` (Repositoryprüfung) und `bd4d2f5` (Dokumentation). Einzeln benannt, weil eine
Runde ohne ihre Hashes nicht nachprüfbar ist — Runde 11 hatte dieselbe Auslassung, und Runde 12 hat sie
zuerst wiederholt und dann in Runde 13 korrigiert.

**Der als teuerster Fund dieser Runde gemeldete Punkt war falsch; Runde 13 hat ihn zurückgenommen.** Der
Reviewer hatte den Auftrag, die acht Preise gegen die Seiten der Anbieter nachzulesen, und meldete keine
falsche Zahl, sondern eine falsch angewandte: Der Zuschlag von fünf Cent je Stunde für eine
Fachbegriffsliste werde nur berechnet, wenn das Modell `universal-3-5-pro` heißt, AssemblyAI verlange ihn
aber für beide. Ich habe das übernommen und die Bedingung entfernt. Die Zusatztabelle der Anbieterseite hat
eine Spalte je Modell, und die Zeile für Fachbegriffe liest sich „$0.05 /hr“ unter Universal-3.5 Pro und
**„Included“** unter Universal-2 — der Code war vorher richtig, und die Korrektur hat ihn falsch gemacht.
Der Rest dieses Absatzes bleibt als Protokoll stehen; was tatsächlich gilt und wie der Fehler zustande kam,
steht in der Runde-13-Passage.

**Beim Nachziehen fand ich zwei weitere Stellen.** Die erste trug denselben zurückgenommenen Fehler:
`SttStep` im Modul `app` rechnet dieselbe Summe ein zweites Mal, und das ist die Stelle, die beim Lauf
tatsächlich gegen das Budget prüft. Die zweite ist unabhängig davon und bleibt ein echter Fund: Die Zahl,
die der Nutzer vor dem Start sieht, kam aus einer dritten Rechnung in `NewSourceScreen` — Länge mal
Stundenpreis, ohne jeden Zuschlag, ohne Groqs Mindestabrechnung und ohne die Abschnittsteilung, nach der
abgerechnet wird. Wer sein Budget nach der angezeigten Zahl wählte, konnte es von einer Prüfung abgelehnt
bekommen, die anders gerechnet hatte.

Alle drei sind jetzt eine Rechnung. `MainViewModel.estimatedCostMicrousd` summiert über denselben
Abschnittsplan, den der Lauf verwendet, und die Anzeige liest sie; die Hilfe sagt jetzt, was drinsteht, und
heißt nicht mehr „Basispreis“. Überschreitet eine Quelle die Höchstdauer, steht dort kein Preis mehr und
auch nicht „Preis unbekannt“ — das wäre eine unwahre Aussage über den Tarif gewesen, während die Zeile
darunter schon erklärt, dass die Quelle gar nicht verarbeitet werden kann.

**Die Zahlenliste aus Runde 11 hatte eine Lücke und zwei falsch einsortierte Einträge.** Es fehlte
`TranscriptExporter.SHORT_ID_BYTES` — die sechs Bytes, mit denen ein Exportname eindeutig wird und deren
Begründung direkt darüber steht: vier Bytes hätten eine Kollision schon bei 77 000 Namen wahrscheinlich
gemacht, sechs erst bei zwanzig Millionen. Drei Tests lasen die Länge aus genau der Konstante ab, die sie
prüfen sollten; die Gegenprobe hat das bestätigt: Auf vier Bytes abgesenkt blieben alle drei grün.

Falsch einsortiert waren Groqs Uploadgrenze und das Prompt-Budget. Beide standen unter „was die Anbieter
erlauben“, und beides stimmt — aber keine Anbieterseite sagt sie so. Groq dokumentiert 25 MB für die
kostenlose und 100 MB für die bezahlte Stufe, und weil die App die Stufe eines mitgebrachten Schlüssels
nicht kennt, hält sie alle an die kleinere: eine Entscheidung dieses Programms. Und OpenAIs Grenze sind
224 **Tokens**, nicht Bytes; der Code zählt Bytes, was sicher ist, weil ein Byte-Tokenizer nie mehr Tokens
als Bytes erzeugt, aber eben nicht dasselbe. Beide stehen jetzt bei den selbstgewählten Budgets, wo
dransteht, dass keine zweite Quelle sie bestätigt.

**Und die Quelle für OpenAIs Preise zeigte auf eine Seite ohne Preise.** Die beiden Zahlen stimmen, die
verlinkte Anleitung nennt sie nur nirgends; wer das Datum daneben prüfen wollte, fand nichts zu prüfen.
Dabei fiel ein eigener Punkt auf: Diese Adresse erreicht ohnehin niemanden. Alle drei Adapter führen sie
mit, gelesen wird sie von nichts — weder Anzeige noch Export noch Diagnose. Das steht als
[Punkt 24](DEFECTS.md) offen, weil die Hilfe statischer Text pro Thema ist und die Adresse ein zweites Mal
zu behaupten genau das wäre, wogegen `StatedNumbersTest` angelegt wurde.

**Die offene Frage aus Runde 11 ist beantwortet.** Die Liste schützte gegen eine geänderte Zahl, aber nichts
hinderte jemanden daran, eine neue Konstante anzulegen und nicht einzutragen — sie hängte an Sorgfalt.
`everyNumberThisModuleStatesHasALineInThisFile` liest jetzt den Quelltext des Moduls und sammelt jede
nicht-`private` Konstante, deren Wert eine Zahl ist. Steht ein Name im Baum und nicht in der Liste oder
umgekehrt, fällt der Test und nennt beide Seiten. Die Gegenprobe hat eine erfundene neue Konstante sofort
zu Fall gebracht.

Der erste Ertrag kam sofort: Beim Eintragen fiel auf, dass `SyncProviderSupport.MAX_UPLOAD_BYTES` von
nichts gelesen wird. 25 MB standen an vier Stellen, drei davon in Gebrauch — jeder Adapter reicht seine
eigene an `capabilities` weiter, und von dort liest die Prüfung. Die vierte war tot und ist gelöscht. Der
Compiler sagt dazu nichts, auch mit `allWarningsAsErrors` nicht: Eine nicht-private Konstante gehört zur
Modulschnittstelle, und ob die jemand benutzt, weiß er nicht.

**Das Gegenstück dazu fand ich in `tools/check-repository.py`**, das in der CI läuft und `PASS repository
checks` schreibt. Es entschied an einer Liste von neunzehn Dateiendungen, welche Datei es überhaupt
öffnet. Alles ohne Endung fiel durch: `gradlew`, `LICENSE`, `.gitignore`, `.gitattributes`, dazu jede
`.pem`, `.env` oder `.asc`, die jemand angelegt hätte. 34 verfolgte Dateien liefen so an der
Geheimnisprüfung vorbei, während der Lauf wie eine Prüfung des Repositorys aussah; fünfzehn davon sind
Text und hätten gelesen gehört, der Rest ist binär. Dieselbe Bauart wie die Zahlenliste vorher: eine
Aufzählung, die jemand hätte pflegen müssen. Jetzt entscheidet ein Nullbyte am Dateianfang über Text oder
Binärdatei, und die Schlusszeile nennt, was wirklich gelesen wurde: **202 Dateien gelesen, 19 als binär
übersprungen**, statt vorher 187 nach Endung ausgewählter. Neue Funde gab es dabei keine — die fünfzehn
Dateien sind sauber, sie waren nur nie angesehen worden.

**Die beiden Archivtests aus Runde 11 prüften nicht, was ihr Name sagte.** Der zweite hieß, ein Archiv
werde gestoppt, wenn es beim Auspacken über die Gesamtgrenze geht, und sein Kommentar nannte die
komprimierten Nullbytes den Kern des Angriffs. Ausgelöst hat ihn aber die Prüfung der *deklarierten*
Größen, die vor dem ersten gelesenen Byte greift und komprimierungsblind ist. Die Wache, die zählt, was
wirklich aus dem Entpacker kommt, war weiter ungetestet — und sie ist die einzige, die ein lügendes Archiv
je erreicht.

Der neue Test baut deshalb zwei: eines mit einem Eintrag, der ein Kibibyte ansagt und neun Mebibyte
mitbringt, und eines, das ehrlich bis 63 Mebibyte füllt und dann lügt. Dabei kam eine vierte
Durchsetzungsstelle zum Vorschein, die ich nicht kannte: Nach jedem Eintrag wird verglichen, ob die
gelesene Länge der angesagten entspricht. Sie fängt jede Lüge, die einen Eintrag zu Ende bringt —
weswegen die beiden Testarchive so gebaut sein müssen, dass sie vorher über eine Grenze laufen. Die erste
Fassung des Tests fiel genau daran, und das war die nützlichste Minute dieser Runde.

**Gegenprobe:** Jede Korrektur zurückgenommen — der Zuschlag wieder an ein Modell gebunden, die
Preisquelle zurück auf die Seite ohne Preise, die Exportkennung auf vier Bytes, eine nicht eingetragene
neue Konstante angelegt und **alle vier** Stellen abgeschaltet, die ein Archiv beim Entpacken begrenzen —
ließ 7 Tests fallen. Die drei Exporttests, die `SHORT_ID_BYTES` lesen, blieben dabei grün: genau der
Beleg dafür, dass sie über diese Zahl nie etwas gesagt haben.

Diese Gegenprobe lief im Modul `core`. Die beiden Stellen im Modul `app` sind **nicht** einzeln
zurückgenommen worden — das hätte zwei vollständige APK-Bauten gekostet. Was dafür belegt war: Der
angepasste `SttStepTest` hat auf dem Gerät ausgeführt und bestanden, und er nagelte für `universal-2` mit
Fachbegriffen 200 000 fest.

**Und genau daran ist zu sehen, was eine Gegenprobe leistet und was nicht.** Sie belegt, dass ein Test die
Zahl im Code festhält — nicht, dass die Zahl stimmt. 200 000 war falsch; der Anbieter verlangt auf diesem
Modell 150 000, und Runde 13 hat die Zeile auf 150 000 zurückgesetzt. Eine grüne Gegenprobe über einer
falschen Anbieterangabe sieht genauso aus wie eine über einer richtigen.

**Dokumentation:** In der Runde-11-Passage stand eine laufende Summe „zwölf seit Runde 9“, die sich aus
dem Dokument heraus nicht nachrechnen ließ; drei Nachzählungen kamen auf drei Ergebnisse. Sie ist
gestrichen statt korrigiert, mit der Begründung daneben. Der Sprung von 168 auf 169 Tests zwischen Runde 10
und 11 ist jetzt erklärt. `EngineJobPinningTest` braucht zwei Flaggen, nicht eine — die Anleitung nannte
nur die zweite, und ohne die erste bleibt der Test übersprungen, gleich welche URL dabeisteht. Der
PowerShell-Block in DEFECTS benutzte fünfmal ein `$adb`, das nirgends gesetzt wurde. Und in `AGENTS.md`
stand seit dem Gründungstag, das Übergabepaket enthalte noch keinen App-Code und keinen App-Testnachweis.

Gates nach Runde 12: **176 JVM-Tests** im Modul `core` ohne Fehler (173 vorher: einer für den
Zuschlag, einer für die Vollständigkeit der Zahlenliste, einer für die zweite Archivwache), alle vier
Lintberichte ohne Befund, der unsignierte Release-Build gebaut, `tools/check-repository.py` mit
Selbsttest bestanden (202 Dateien gelesen, 19 als binär übersprungen), **191
Instrumentierungstests** im Modul `app` — 185 im gemeinsamen Lauf, 4 weitere einzeln über ihre
Stufen, 0 Fehler — und **39** im Modul `extractor` — 35 bestanden, 0 Fehler. Von 406 Tests sind damit
400 ausgeführt. Von den sechs übrigen brauchen fünf eine echte Quelle oder ein echtes Release; die
sechste, `UiFixtureTest`, bleibt mit Absicht aus, weil sie kein Test ist, sondern ein Saatgenerator.

### Runde 13

Commits: `d6a773c` (Zuschlag), `dd371f5` (Zahlenprüfung), `0c30bde` (UTF-16), `f5cc8c5`
(Kostenzeile) und der Dokumentationscommit, der diesen Absatz trägt. Einzeln benannt, nicht als
Bereich, weil `a..b` den Anfangscommit auslässt.

**Der teuerste Fund der zwölften Runde war ein Fehler, und diese Runde hat ihn gefunden.** Der lesende
Code-Reviewer sollte die vier Commits der Vorrunde prüfen und hat dabei die AssemblyAI-Preisseite noch
einmal geholt. Deren Zusatztabelle hat eine Spalte je Modell. Die Zeile „Keyterms Prompting“ liest sich
„$0.05 /hr“ unter Universal-3.5 Pro und **„Included“** unter Universal-2: Auf dem günstigeren Modell ist die
Fachbegriffsliste im Stundenpreis enthalten. Genau das stand vor Runde 12 im Code, und Runde 12 hat es
herausgenommen.

Ich habe das nicht der Zusammenfassung des Reviewers geglaubt, sondern die Seite selbst geholt und ihre
Tabellen ausgelesen. Die Kopfzeile lautet `Add-on features | Universal-3.5 Pro | Universal-2`, die Zeile
darunter `Keyterms Prompting | $0.05 /hr | Included`. Die Zeile „Speaker Diarization“ daneben sagt für beide
Spalten `$0.02 /hr` — dort stimmt die Annahme „gleicher Aufschlag für beide“ tatsächlich, und dass die
beiden Zeilen sich unterscheiden, ist der ganze Punkt.

**Wie es dazu kam, ist der eigentliche Ertrag dieser Runde.** Ich habe die Seite heute zusätzlich über
denselben zusammenfassenden Abruf geholt, den ich am 11. September benutzt habe: ein Werkzeug, das eine
Seite an ein kleines Modell gibt und dessen Antwort zurückreicht. Auf die Frage nach genau diesen beiden
Spalten antwortet es „**+$0.05/hr** für beide Modelle“. Das Wort „Included“ in der Tabellenzelle ist auf dem
Weg zu einem Preis geworden. Der Fehler lag also nicht im Nachlesen, sondern darin, eine Zusammenfassung
als Quelle zu nehmen, wo der Auszug aus dem Markup nötig gewesen wäre. Für kostenrelevante Zahlen steht das
jetzt als Regel im Projektgedächtnis.

Die Folge war keine Überziehung, sondern das Gegenteil, und beides ist ein Defekt: Eine
Universal-2-Anfrage mit Fachbegriffen wurde um ein Drittel zu teuer geschätzt — 83 335 statt 62 500
Mikro-Dollar für eine 25-Minuten-Quelle — und konnte an einem Budget scheitern, das der Anbieter
eingehalten hätte. Zurückgenommen ist die Bedingung an zwei rechnenden Stellen (`AssemblyAiAdapter`,
`SttStep`) und an drei behauptenden: dem Test, den Runde 12 dafür angelegt hat, der Zeile in `SttStepTest`,
die 200 000 festnagelte, und dem Kommentar in `StatedNumbersTest`. Die dritte war nur zu finden, weil sie
im Instrumentierungslauf liegt — `:core:test` erreicht sie nicht. Der neue Test nagelt **beide** Spalten
fest, damit die Asymmetrie nicht ein zweites Mal wie ein Versehen aussieht.

**Die Vollständigkeitsprüfung aus Runde 12 ließ sich mit gewöhnlichem Kotlin umgehen.** Sie las den
Quelltext zeilenweise und verlangte, dass eine Zeile mit `const val` beginnt. Damit entgingen ihr drei
Formen, von denen keine ungewöhnlich ist: eine Deklaration, deren Wert auf der nächsten Zeile steht, weil
die erste zu lang wurde; eine mit einer Annotation davor — `@Suppress("MagicNumber") const val …` ist genau
die Gestalt einer unterdrückten Lint-Warnung; und zwei gleichnamige Konstanten in einer Datei, die im
`Set` zu einem Eintrag verschmolzen. Die Prüfung liest jetzt die Datei als Ganzes, nimmt Annotationen und
Modifizierer vom Zeilenanfang mit, folgt dem `=` über den Zeilenumbruch und meldet Namenskollisionen
getrennt. Am heutigen Baum ändert das nichts — dieselben 33 Konstanten —, und genau deswegen prüft ein
eigener Test die Prüfung an Textbeispielen: Ein Test, der nur den Baum liest, wäre vorher wie nachher grün
gewesen.

**`tools/check-repository.py` überspringt UTF-16-Dateien vollständig.** Die Text-oder-Binär-Entscheidung aus
Runde 12 sucht ein Nullbyte am Dateianfang. In UTF-16 wird jedes ASCII-Zeichen als zwei Bytes abgelegt, von
denen eines Null ist — der erste Buchstabe einer solchen Datei löst die Heuristik aus, und die ganze Datei
bleibt ungelesen. Auf einer Windows-Maschine ist das keine Exotik: Notepads „Unicode“-Option und ältere
PowerShell-Umleitungen schreiben es. Im Baum lag zum Stand dieser Runde keine solche Datei: Von den 221
versionierten tragen genau 19 ein Nullbyte in den ersten 8192 Bytes — fünf PNG-Screenshots, zehn
`.so`-Bibliotheken, die gepackte Extraktor-Engine, zwei Signaturdateien und das Wrapper-JAR —, und eine
Byte-Reihenfolge-Markierung trägt keine einzige. Ein Schlüssel in einer UTF-16-Datei wäre trotzdem an
der Prüfung vorbeigelaufen, die `PASS repository checks` schreibt. Jetzt wird
eine Byte-Reihenfolge-Markierung vor der Nullbyte-Probe gelesen; ohne Markierung bleibt UTF-16 von
Binärdaten ununterscheidbar, und das steht als Grenze dieser Prüfung im Code. Der Selbsttest enthält die
Datei jetzt, und seine Zählerzusicherung ist von „mindestens fünf“ auf eine genaue Zahl umgestellt — eine
Untergrenze ist auch dann erfüllt, wenn eine Datei ungelesen bleibt, also gerade in dem Fall, den der
Zähler sichtbar machen soll.

**Die Kostenzeile nannte einen Preis für eine Quelle, die derselbe Bildschirm eine Zeile tiefer ablehnt.**
Runde 12 hat richtig erkannt, dass über der Höchstdauer kein Preis stehen darf — aber gegen die absolute
Obergrenze der App geprüft, 36 000 Sekunden, während die Warnung darunter gegen das *pro Auftrag* gesetzte
Längenlimit prüft. Bei einem Limit von einer Stunde und einer zweistündigen Quelle stand dort „Geschätzte
Kosten: 0.3000 USD“ und direkt darunter, dass die Transkription abbrechen würde, bevor etwas an den
Anbieter geht. Geprüft wird jetzt gegen beide Grenzen, und der Text sagt „länger, als dieser Auftrag
zulässt“ statt „überschreitet die Höchstdauer“, weil die Zeile darunter ohnehin sagt, welche der beiden
Grenzen es ist und was dagegen zu tun ist. Kein Geldrisiko — `SttStep.prepare()` weist dieselbe Quelle
unabhängig ab —, aber eine Zahl, der man nicht glauben kann.

**Dabei ist mir ein eigener Fund aufgefallen, den der Reviewer nicht hatte:** Diese Zeile wechselt zwischen
einem kurzen Preis und einem ganzen Satz, ohne Höhe zu reservieren. Wer den Knopf „Limit anheben“ drückt,
hätte den Rest des Bildschirms unter dem Finger nach oben rutschen sehen. Sie steht jetzt auf zwei Zeilen,
wie es die beiden anderen wechselnden Textzeilen dieses Bildschirms schon tun.

**Die Summe, die der Nutzer sieht, hatte keinen Test.** Runde 12 hat die Anzeige auf denselben
Abschnittsplan und dieselbe Funktion gelegt, die der Lauf verwendet — und dann nichts davon geprüft.
Getestet war die Einzelrechnung für einen Abschnitt, nicht das Aufsummieren über den Plan, und genau in
diesem Aufsummieren lagen die Funde der Runden 3 bis 12. Der neue Test nagelt vier Fälle fest, darunter
den, der die Bauart erklärt: Mit Sprechertrennung ergibt die abschnittsweise Rechnung 70 835 Mikro-Dollar
und eine ungeteilte 70 834 — ein Mikro-Dollar Unterschied, weil jeder Abschnitt einzeln aufgerundet und
einzeln abgerechnet wird.

**Zwei kleinere Punkte.** Die Preisseite von OpenAI, auf die Runde 12 umgestellt hat, nennt `whisper-1`
nirgends: Der Name kommt auf der ganzen Seite nicht vor, die Zeile mit den 0,006 Dollar je Minute heißt
„Whisper“ und steht hinter dem Aufklappen der Tabelle. Die Zahl stimmt, den letzten Schritt muss der Leser
selbst machen — das steht jetzt im Kommentar daneben statt als stille Annahme. Und
`SyncProviderSupport.validateOptions` ist eine vierte Stelle, die eine Dauer in Geld umrechnet, als einzige
ohne Zuschläge. Das ist für die zwei Anbieter, die sie heute erreichen, richtig — Groq bietet keine
Sprechertrennung an, und OpenAIs diarisierendes Modell hat keinen veröffentlichten Preis und wird eine
Zeile weiter abgelehnt —, wäre aber am Tag falsch, an dem einer von beiden einen bepreisten Zusatz bekommt.
Der Kommentar sagt das jetzt; als Vorprüfung bleibt sie ungefährlich, weil `SttStep.submit` mit den
Zuschlägen und über den ganzen Plan prüft, bevor etwas hinausgeht. Die Begründung für OpenAI in diesem
Absatz ist falsch und bleibt als Protokoll stehen: `gpt-4o-transcribe-diarize` hat einen veröffentlichten
Preis, je Million Token; einen Stundensatz, mit dem die App rechnen könnte, gibt es nicht. Was tatsächlich
gilt, steht in der Runde-14-Passage.

**Gegenprobe.** Jede Korrektur zurückgenommen, in zwei Läufen, weil die beiden Module getrennt gebaut
werden. Im Modul `core` und im Prüfwerkzeug fielen drei Prüfungen: der neue Adaptertest, sobald der
Zuschlag wieder auf beiden Modellen liegt; der neue Scannertest, sobald die Zahlenprüfung wieder
zeilenweise liest; und der Selbsttest von `check-repository.py`, sobald die Markierungsprüfung fehlt.

**Eine Prüfung fiel dabei ausdrücklich nicht, und das ist der Beleg, auf den es ankommt.**
`everyNumberThisModuleStatesHasALineInThisFile` blieb mit dem zurückgenommenen Scanner grün — weil im
heutigen Baum keine der drei umgehbaren Formen vorkommt. Ein Test, der nur den Baum liest, hätte die
Lücke weder vorher noch nachher angezeigt. Genau deswegen prüft der neue Test die Prüfung an
Textbeispielen.

**Und die Gegenprobe im Modul `app`, die Runde 12 ausgelassen hat, ist nachgeholt.** Sie kostet einen
eigenen APK-Bau, weshalb sie beim letzten Mal unterblieb — und genau dort lag diesmal eine der drei
falschen Behauptungen. Drei Tests fielen: der 150-000-Assert in `SttStepTest`, sobald der Zuschlag wieder auf
beiden Modellen liegt; der neue Summentest, sobald die Anzeige wieder in einem Stück rechnet statt
abschnittsweise; und `aSourceLongerThanThisJobAllowsIsNotPriced`, sobald die Längenregel wieder nur die
Obergrenze der App kennt. Zusammen mit den drei im Modul `core` fielen sechs Prüfungen.

Gates nach Runde 13: **177 JVM-Tests** im Modul `core` ohne Fehler (176 vorher: einer für die
Prüfung der Zahlenprüfung), alle vier Lintberichte ohne Befund, der unsignierte Release-Build gebaut,
`tools/check-repository.py` mit Selbsttest bestanden (202 Dateien gelesen, 19 als binär übersprungen),
**193 Instrumentierungstests** im Modul `app` — 187 im gemeinsamen Lauf, 4 weitere einzeln über ihre
Stufen, 0 Fehler — und **39** im Modul `extractor` — 35 bestanden, 4 per Annahme übersprungen, 0 Fehler.
Von 409 Tests sind damit 403 ausgeführt, drei mehr als nach Runde 12 in beiden Zahlen. Von den sechs
übrigen brauchen fünf eine echte Quelle oder ein echtes Release; die sechste, `UiFixtureTest`, bleibt mit
Absicht aus, weil sie kein Test ist, sondern ein Saatgenerator.

### Runde 14

Commits: `9021bf5` (UTF-32), `8c16c0a` (Zahlenprüfung), `1b63860` (Preisgrund), `7e0b625` (Kostenzeile),
`710c64f` (leere Begriffsliste), `538dfec` (Groqs Mindestabrechnung) und der Dokumentationscommit, der
diesen Absatz trägt.

**Zwei der drei wichtigsten Funde dieser Runde sind Fehler, die Runde 13 gemacht hat, während sie Fehler
der Runde 12 korrigierte.** Das ist keine Wiederholung des Vorrundenmusters, sondern seine Verschärfung:
Nicht der Fund war falsch, sondern die Korrektur.

**Der UTF-16-Fix hat UTF-32 schlechter gemacht als vorher.** Eine UTF-32LE-Byte-Reihenfolge-Markierung
lautet `ff fe 00 00`. Ihre ersten zwei Bytes sind genau eine UTF-16LE-Markierung. Runde 13 prüfte zwei
Bytes, also wurde eine UTF-32LE-Datei als UTF-16 dekodiert: Text mit einem Nullbyte zwischen jedem Zeichen,
an dem kein Geheimnismuster greift — **und sie wurde als gelesen gezählt**. Vor Runde 13 hätte die
Nullbyte-Probe dieselbe Datei ehrlich als binär gemeldet und die Abschlusszeile es gesagt. Damit hat eine
Korrektur, die Abdeckung hinzufügen sollte, an einer Stelle Abdeckung vorgetäuscht. Die vier Bytes werden
jetzt zuerst geprüft, UTF-32 wird gelesen, und der Selbsttest enthält so eine Datei.

**Und in derselben Runde habe ich eine Anbieterbehauptung aufgeschrieben, die das widerlegende Markup schon
auf dem Schirm hatte.** Der Kommentar zu DEFECTS 27 sagte, OpenAIs diarisierendes Modell habe „keinen
veröffentlichten Preis“. `gpt-4o-transcribe-diarize` ist bepreist, je Token: 2,50 und 10,00 Dollar je
Million. Ich hatte diese Zeile beim Prüfen von `whisper-1` in derselben Ausgabe stehen und nicht gelesen.
Der Wert im Code bleibt richtig — `priceMicrousdPerHour = null` —, aber aus einem anderen Grund: Die
„$0.006 / minute“ daneben stehen in einer Spalte, die die Seite selbst „Estimated cost“ überschreibt, und
eine Dauer lässt sich nicht mit einer Tokenzahl multiplizieren. Es gibt keinen Stundensatz zu führen, und
das Verweigern einer Schätzung ist die ehrliche Antwort, nicht eine Lücke. Dass die Lehre der Vorrunde
— Markup statt Zusammenfassung — den Fehler nicht verhindert hat, liegt daran, dass ich das Markup hatte
und die falsche Zeile daraus gelesen habe. Die Lehre lautet jetzt schärfer: Eine Randbedingung, die als
„heute irrelevant“ abgehakt wird, braucht dieselbe Quellenprüfung wie die Zahl, um die es geht.

**Die Zahlenprüfung ließ sich weiterhin umgehen, in vier Formen, und fand eine, die es nicht gibt.**
Runde 13 hatte das Präfix vor einer Deklaration ausgeschrieben: Name, optionale Klammer, Leerraum. Eine
Annotation kann tiefer klammern — `@Deprecated("x", ReplaceWith("y()"))` ist drei Ebenen — und kein
regulärer Ausdruck kann Klammern zählen. Dazu: ein Umbruch **vor** dem `=` statt danach, eine Deklaration
hinter einem Semikolon, und ein Blockkommentar hinter dem Wert, dessen Anführungszeichen den Wert als
Zeichenkette aussehen ließen. Die fünfte Form ist die Gegenrichtung und wäre laut geworden: Ein
`const val` **innerhalb** eines Blockkommentars wurde als echt gezählt und hätte den Test für eine
Konstante scheitern lassen, die es nicht gibt.

Das Präfix wird jetzt gar nicht mehr gedeutet. Es wird nur auf das Wort `private` gelesen, also muss es
nicht verstanden werden — „alles Übrige auf dieser Zeile“ genügt, und Blockkommentare werden vorher
entfernt. Alle siebzehn Fälle standen erst als Python-Modell, bevor eine Zeile Kotlin geschrieben wurde,
und alle siebzehn sind Zusicherungen im Test, zehn davon neu. (Berichtigt in Runde 15: In diesen zwei
Absätzen stand „fünf Formen“, „die sechste Form“ und „elf davon“. Wie es dazu kam, steht in der
Runde-15-Passage.)

**Die Kostenzeile reservierte ihre Höhe nur nach unten.** Runde 13 setzte `minLines = 2` und schrieb
daneben „two lines whatever it says“. `minLines` verhindert eine erste, nicht eine dritte Zeile. Die
Schwesterzeile desselben Bildschirms setzt seit langem beide Grenzen und nimmt einen Auslassungspunkt in
Kauf; die Kostenzeile tut das jetzt auch, und der längste der drei Texte ist gekürzt, damit der Fall
seltener wird. Ob einer von ihnen bei größter Schrift wirklich drei Zeilen braucht, ist **nicht gemessen**
— mit der Obergrenze ist es ein abgeschnittenes Wort statt eines springenden Bildschirms, und das ist die
Abwägung, die der Rest dieses Bildschirms schon trifft.

**Zwei kleinere Funde, beide dieselbe Gestalt wie der Längenfehler der Vorrunde.** Eine Fachbegriffsliste,
die nur leere Einträge enthält, bekam den Zuschlag angerechnet, obwohl der Adapter so einen Auftrag
vollständig ablehnt; die Bedingung liest jetzt `isNotBlank` statt `isNotEmpty`. Und die Mindestdauer eines
Modells — 160 ms bei AssemblyAI, 10 ms bei Groq — ist eine dritte Längenschranke, die die Anzeige nicht
kennt. Die bleibt als [Punkt 30](DEFECTS.md) offen: Eine zu kurze Quelle braucht eine andere Aussage als
„zu lang“, und eine dritte Bedingung in eine Regel dieses Namens zu schreiben wäre der falsche Zug.

**Gegenprobe.** Jede Korrektur mit einem Test zurückgenommen, in zwei Läufen, weil die Module getrennt
gebaut werden. Drei Prüfungen fielen: der Selbsttest von `check-repository.py`, sobald die
Markierungsprüfung wieder zwei Bytes liest; der Scannertest, sobald das Präfix wieder ausgeschrieben und
das Blockkommentarmuster stillgelegt ist; und `estimateCostCeilsMinimumAndAssemblyAddonsAndBlocksUnknownPrice`
im Modul `app`, sobald eine blanke Begriffsliste wieder als Prompt zählt.

**Drei Korrekturen dieser Runde haben keine Gegenprobe, und zwar aus drei verschiedenen Gründen.** Der
richtiggestellte Grund für `priceMicrousdPerHour = null` ist Prosa und ändert kein Verhalten. Die
Obergrenze der Kostenzeile hat keinen UI-Test, und dass sie wirkt, ist am Code zu sehen, nicht an einem
Lauf — gemessen ist sie nicht. Und Groqs Mindestabrechnung über mehrere Abschnitte ist eine
Testergänzung ohne begleitende Codeänderung: Es gibt nichts zurückzunehmen, weil die Logik schon richtig
war und nur ungeprüft. Alle drei stehen hier, statt unter „Gegenprobe: sechs“ mitgezählt zu werden.

**Und wieder fiel `everyNumberThisModuleStatesHasALineInThisFile` nicht**, obwohl der Scanner
zurückgenommen war — dieselbe Beobachtung wie in Runde 13, aus demselben Grund: Keine der fünf Formen
kommt im Baum vor. Der Test am Baum kann diese Lücke nicht zeigen, der Test an Textbeispielen schon.

Gates nach Runde 14: **177 JVM-Tests** im Modul `core` ohne Fehler (unverändert zu Runde 13, weil diese
Runde Zusicherungen zu bestehenden Tests hinzugefügt hat und keine neuen Testmethoden), alle vier
Lintberichte ohne Befund, der unsignierte Release-Build gebaut, `tools/check-repository.py` mit Selbsttest
bestanden (202 Dateien gelesen, 19 als binär übersprungen), **193 Instrumentierungstests** im Modul `app`
— 187 im gemeinsamen Lauf, 4 weitere einzeln über ihre Stufen, 0 Fehler — und **39** im Modul `extractor`
— 35 bestanden, 4 per Annahme übersprungen, 0 Fehler. Von 409 Tests sind damit 403 ausgeführt, wie nach
Runde 13; die sechs übrigen sind dieselben sechs.

### Runde 15

Commits: `8833d07` (eine Regel für Fachbegriffslisten), `b2286d5` (typisierter Fehler für Rohdaten ohne
Endung), `089b184` (Lexer der Zahlenprüfung), `1f378a9` (UTF-32BE im Selbsttest), `e33ac54` (reservierte
Höhen dreier Statuszeilen) und `ab9f36e` (Listenfelder behalten, was getippt wird), dazu der Doku-Commit,
der diese Passage schreibt.

**Die Korrektur der Zahlenprüfung aus Runde 14 hatte selbst zwei stille Lücken.** Runde 14 hatte das
Präfix vor einer Deklaration nicht mehr gedeutet und Blockkommentare mit einem eigenen Muster entfernt.
Vier Formen liefen daran vorbei. Alle vier hat der Code-Reviewer gefunden, die rohe Zeichenkette im Text
seines Berichts und die übrigen drei in seiner Tabelle; die rohe Zeichenkette stand zudem als erste Frage im
Auftrag, und alle vier waren am Python-Modell nachgestellt, bevor etwas geändert wurde. Zwei davon wären
laut geworden: Ein `const val` in einer rohen Zeichenkette und einer im Rest eines verschachtelten
Blockkommentars wurden als echt gezählt, und der Vergleich am Baum wäre an einer Konstante gescheitert,
die es nicht gibt. Die anderen zwei wären still geblieben: Ein `/*` in einer gewöhnlichen Zeichenkette
öffnete einen Kommentar bis zum nächsten `*/` in einer späteren Zeichenkette und verschluckte eine echte
Deklaration dazwischen, und von zwei Deklarationen auf einer Zeile fand das gierige Wertmuster nur die
erste. Eine Deklaration, die der Scanner nicht sieht, fehlt auf beiden Seiten des Vergleichs, sobald sie
niemand eingetragen hat — der Test fällt nicht, und niemand wird aufgefordert, die Zahl zu belegen.

Statt eines weiteren Musters liest die Prüfung den Quelltext jetzt mit einem kleinen Lexer, `codeOnly`, der
Kommentare samt Verschachtelungstiefe, rohe und gewöhnliche Zeichenketten und Zeichenliterale kennt. Das
Modell lief über 24 Fälle: die siebzehn bestehenden, die vier neuen und drei, über die der Lexer selbst
hinweglesen muss — ein Anführungszeichen und ein Semikolon als Zeichenliteral, beide so im Modul vorhanden,
und ein maskiertes Anführungszeichen in einer Zeichenkette. Der bisherige Scanner lag bei 4 der 24 falsch,
der Lexer bei keinem, und über das echte Modul lesen beide dieselben 33 Konstanten. Der Test hat jetzt 24
Zusicherungen. Die letzten drei belegen nicht, dass der Lexer Zeichenliterale braucht: Ein Nachbau ohne
diesen Zweig liest über das Modul dieselben 33 Konstanten, weil eine gewöhnliche Zeichenkette am Zeilenende
endet und auf den Zeilen mit diesen Zeichenliteralen keine Deklaration steht. Sie halten fest, dass eine
Deklaration in der Zeile danach gefunden wird, wie auch immer sich der Lexer später ändert.

Was der Lexer nicht modelliert, steht als [Punkt 34](DEFECTS.md): eine Zeichenkette innerhalb eines
String-Templates, in der ein Kommentar-Anfang stünde. Heute folgenlos, und das ist gezählt: Von den 54
Zeilen des Moduls, die ein Template und danach ein Anführungszeichen enthalten, enthält keine `/*` oder `//`.

**Die Zählung der Runde 14 war falsch, und an drei Stellen verschieden.** In dieser Datei stand „fünf
Formen“ und „die sechste Form“; die Commit-Nachricht von `8c16c0a` nannte eine Form und „Vier weitere
Formen“ und schrieb dann „keine der sechs Formen“; und im Testkommentar stand „Round 14 closed these five“
über einer Liste, die `TWO` enthielt. Der Konsistenzreviewer hat die Abweichung gefunden und `TWO` dabei als
eigene Form mitgezählt. Entschieden hat es erst das Modell, das beide Scanner über alle siebzehn Fälle
laufen ließ: Runde 13 lag bei fünf Fällen falsch — vier übersehene und einer, den sie fälschlich zählte —,
und `TWO`, zwei Annotationen hintereinander, las schon Runde 13 richtig, weil ihr Präfixmuster sich
wiederholen durfte. „Elf davon sind jetzt Zusicherungen“ passte zu keiner Aufteilung: Es waren alle
siebzehn, zehn davon neu, wie Code- und Konsistenzreviewer unabhängig nachgezählt haben. Die
Runde-14-Passage ist berichtigt und nennt, was vorher dort stand, und der Testkommentar führt `TWO` jetzt
als Absicherung. Die Commit-Nachricht bleibt, wie sie ist.

**Die Regel für Fachbegriffslisten war nur zur Hälfte geschlossen.** Runde 14 hatte die Bedingung für den
Zuschlag auf `any { it.isNotBlank() }` gestellt. Das erfasst eine Liste aus lauter Leereinträgen, nicht
aber eine gemischte wie `["Kubernetes", ""]`: Dafür ist die Bedingung wahr, die Kostenzeile rechnete den
Zuschlag ein, und beide Anbieterpfade lehnen den ganzen Auftrag wegen des einen leeren Eintrags ab. Über das
Eingabefeld entsteht so eine Liste nicht, weil es leere Zeilen verwirft. `SettingsStore.validateConfig`
prüft gespeicherte Einstellungen aber nur auf Anzahl, Länge und Steuerzeichen, ein leerer Eintrag käme dort
durch; welcher heutige Weg ihn schreiben würde, ist nicht belegt. Gemeldet hat es der Code-Reviewer.

Die Regel steht jetzt einmal, in `ContextTerms`, und alle sechs Stellen, die über eine Liste entscheiden,
fragen sie: beide Anbieterpfade, `SttStep.validate` vor jedem Versand, die Kostenformel in `SttStep` sowie
Fehlerzeile und Preis der Vorschau in `MainViewModel`. `SttStep.validate` hatte der Code-Reviewer in seiner
Liste aller Leser genannt; umgestellt wurde die Stelle trotzdem erst beim Schreiben dieses Absatzes. Das
Verhalten hat es nicht geändert — sie prüfte schon dasselbe —, aber „jede Stelle fragt die eine Regel“
wäre sonst falsch gewesen. Die Vorschau nennt den Fehler jetzt selbst, als `CONTEXT_TERM_BLANK` mit eigenem
Text in beiden Sprachen, und zeigt für so einen Auftrag keinen Preis. Die Kostenformel in `SttStep` bleibt
dabei eine reine Preisfunktion und liefert für so eine Liste den Preis ohne Zuschlag, keine unbekannte
Schätzung: Die Budgetprüfung ruft dieselbe Formel vor jedem Abschnitt auf, der hinausgeht, und dort soll
`null` weiter nur „kein geprüfter Tarif“ heißen. Dass so ein Auftrag nicht startet, sagen `SttStep.validate`
vor dem Versand und `configError` in der Vorschau.
`AssemblyAiAdapter.estimatedCostMicrousd` fragt weiter `isNotEmpty`; das genügt, weil es auf
`ValidatedRequest` rechnet, einer Liste, die `validateConfig` schon geprüft hat. [Punkt 31](DEFECTS.md) ist
damit geschlossen, und weiter, als er beschrieben war.

**Außerhalb des zugewiesenen Diffs lagen sieben Funde; fünf sind behoben, zwei stehen als Punkte offen.**

- Die Fehlerzeile der Vorschau in `NewSourceScreen` hatte keine reservierte Höhe, direkt unter der
  Kostenzeile, die Runde 14 gerade begrenzt hatte. Ihr Text wechselt, während darüber Anbieter, Modell,
  Schlüssel, Budget und Optionen eingestellt werden. Gemeldet vom Code-Reviewer. Die erste Korrektur
  übernahm die feste Zweizeilengrenze der Kostenzeile und hätte jeden Fehlertext abgeschnitten, der mehr
  als zwei Zeilen braucht: Die drei Kostentexte sind für diese Grenze kurz gehalten, die Fehlertexte nicht,
  der längste hat 111 Zeichen im Deutschen und 108 im Englischen. Wie viele Zeilen sie am Gerät brauchen,
  ist nicht gemessen. Aufgefallen ist das vor dem Commit, beim Nachprüfen der Commit-Nachricht. Jetzt
  reserviert die Zeile mit `ReservedText` die Höhe des höchsten Textes, den sie zeigen kann, bei echter
  Breite und Schriftgröße, und schneidet keinen ab. Welche Codes das sind, steht als
  `PREVIEW_ERRORS_SHOWN_AS_TEXT` neben `previewError`; `ViewRulesTest` läuft `previewError` über alle Modi,
  Anbieter, Modelle und Regionen, sechs Quellformen und neun Optionsvarianten, mit und ohne Schlüssel, und
  verlangt jeden erreichten Code in der Liste. Dass die Zeile beim Verschwinden den Startknopf verschiebt,
  steht als [Punkt 36](DEFECTS.md).
- Die Wartezeit in der Verlaufskarte (`HistoryScreen`) ändert sich jede Sekunde und wächst von `9:59` über
  `10:00` bis `1:00:00`, ohne reservierte Höhe, und der Bytezähler darunter zeigte rohe Bytes, ebenfalls
  ohne. Beide reservieren jetzt mindestens die Höhe, die ein Platzhalter für ihren breitesten Fall bei der
  echten Breite und Schriftgröße braucht — `ReservedText`, das Messverfahren aus `Choice` als eigene
  Komponente —, und der Bytezähler zeigt `byteSize`. Gemeldet vom Invarianten-Reviewer, als zwei Funde.
- `ArtifactFiles.validateRawArguments` prüfte „Endung ohne Daten“, nicht aber „Daten ohne Endung“, und
  warf dafür über `rawExtension!!` eine rohe `NullPointerException` statt eines Fehlercodes. Kein heutiger
  Aufrufer ruft die Funktion so auf; jetzt gibt es `RAW_DATA_WITHOUT_EXTENSION`, mit Test. Gemeldet vom
  Invarianten-Reviewer.
- `docs/ARCHITECTURE.md` nannte acht Komponenten- und Entitätsnamen aus seiner ersten Fassung, die es im
  Code nicht gibt, darunter `ExtractorUpdateManager` für `EngineUpdateManager`. Der Konsistenzreviewer fand
  sechs; `Job` und `Attempt` fehlten in seiner Liste und heißen im Code `JobRow` und `AttemptRow`.
- Offen als [Punkt 32](DEFECTS.md): Ein grüner CI-Lauf zeigt nicht, wie viele Instrumentierungstests
  übersprungen wurden. Gemeldet vom Invarianten-Reviewer.
- Offen als [Punkt 33](DEFECTS.md): `setBackoffCriteria` im Erfassungsauftrag greift nie, weil kein Worker
  `Result.retry()` zurückgibt. Gemeldet vom Invarianten-Reviewer.

**Zwei weitere Fehler fanden sich erst beim Nachprüfen der Commit-Nachrichten, beide selbst gefunden.** Der
eine ist die Zweizeilengrenze oben. Der andere ist älter als diese Runde: Die Felder für Fachbegriffe und für
bevorzugte Untertitelsprachen waren direkt an die Liste gebunden, die sie bearbeiten. Ein getipptes
Trennzeichen erzeugte einen leeren Eintrag, der sofort verworfen wurde, und das Feld zeigte die wieder
zusammengefügte Liste ohne das Trennzeichen. Am Gerät (`emulator-5556`, App-Stand vor der Korrektur): Nach
„Kubernetes“, Enter, „Docker“ zeigte das Fachbegriffsfeld `KubernetesDocker`, nach `de,en` das Sprachenfeld
nur `de`. Einen zweiten Begriff konnte man also nur einfügen oder durch Teilen des ersten erzeugen.
`ListField` hält jetzt den getippten Text und folgt der Liste nur, wenn sie sich von außen ändert. Die erste
Fassung davon verlor bei schnellem Tippen Zeichen: „Kubernetes“, Enter, „Docker“ kam in zwei Läufen als
`Kuberes`/`Dokern` und als `Kuberns`/`Docke` an, langsam getippt richtig. Eine weitergegebene Liste kommt
erst Frames später zurück, und die Fassung hielt so eine verspätete Liste für eine Änderung von außen und
setzte den Text mitten im Wort zurück. Jetzt merkt sich das Feld jede weitergegebene Liste, bis sie
zurückkommt, und nur eine nie weitergegebene setzt den Text zurück. Am Gerät zeigen danach Fachbegriffs-,
Sprachen-, Minuten- und Budgetfeld schnell wie langsam getippt genau das Getippte. Gefunden hat den Fehler
der ersten Fassung erst die Gegenprobe am Gerät; die App-Suite war mit ihr grün.

**Innerhalb des Diffs, kleiner.** Der Selbsttest von `check-repository.py` durchlief den Big-Endian-Eintrag
der UTF-32-Markierungen nie, weil `.encode("utf-32")` die Markierung in der Bytereihenfolge der Maschine
schreibt, hier Little-Endian; er enthält jetzt eine UTF-32BE-Datei. Und die Erzählung der Runde 14 gilt nur
für Little-Endian: Eine UTF-32BE-Datei beginnt mit `00 00`, und die Zwei-Byte-Prüfung der Runde 13 hätte sie
nie für UTF-16 gehalten. Beides vom Code-Reviewer; die Commit-Nachricht von `9021bf5` bleibt, wie sie ist.

**Dokumentation.** Die Runde-13-Passage behauptete noch unmarkiert, OpenAIs diarisierendes Modell habe keinen
veröffentlichten Preis — die Behauptung, die Runde 14 selbst zurückgenommen hatte. Sie trägt jetzt eine
Protokollmarkierung wie die Runde-12-Passage. NEXT_STEPS nannte sich im Kopf „zuletzt nach der dreizehnten
Reviewrunde nachgeführt“, wenige Zeilen über dem Auftrag für die fünfzehnte, und nannte
`engineUpdateChannel` „die neunte Flagge“, die keine Schranke sei — es sind zwei: `sourcescribeProcessStage`
wird mit `assertEquals` gelesen und lässt Tests rot werden, statt sie zu überspringen. Diese drei Funde
stammen vom Konsistenzreviewer. Im Abschnitt „Artefakte und Grenzen“ dieser Datei stand außerdem noch
„heute sind es 191“, ein zweites „heute“ mit veralteter Zahl, das keiner der drei Reviewer gemeldet hat.
Die heutige Testzahl steht jetzt nur noch im Kopf dieser Datei, und NEXT_STEPS führt keine eigene Summe mehr.

**Zwei Reviewerbehauptungen waren falsch, eine dritte zur Hälfte.** Der Konsistenzreviewer zählte hinter
den sieben Schranken-Flaggen 27 statt der dokumentierten 24 Tests und markierte das selbst als spekulativ.
Nachgezählt: Die 18 Übersprungenen des Moduls `extractor` enthalten die drei `publicSourceUrl`-Tests schon,
und er hatte sie ein zweites Mal addiert; 18 und 6 sind 24. Dass `TWO` eine eigene Form sei, hat das Modell
widerlegt (oben). Und der Invarianten-Reviewer schrieb, `cleanup()` im Workflow lese „nie `<skipped>`“: Das
stimmt für die Elemente, aber das Skript gibt im Fehlerfall auch `root.attrib` aus, die Attribute des
Wurzelelements, und ob darin eine Überspringzahl steht, ist an keinem echten Bericht geprüft. Am Befund
ändert das nichts, weil ein grüner Lauf gar nichts ausgibt.

**Geprüft und ohne Änderung gelassen, jeweils mit Grund:**

- Eine auf genau drei Bytes gekürzte UTF-32LE-Markierung wird als UTF-16 gelesen (Code-Reviewer, von ihm
  selbst als sehr unwahrscheinlich eingestuft). Drei Bytes können kein Geheimnismuster enthalten; es ändert
  kein Ergebnis.
- Kanal, Dauer, Datum und Adresse in der Vorschaukarte haben keine Zeilengrenze (Code-Reviewer, als
  spekulativ markiert). Sie stammen aus der aufgelösten Quelle und ändern sich nicht, während jemand die
  Einstellungen darunter bedient.
- Aufklappbare Elemente schieben beim Öffnen, was darunter steht (Invarianten-Reviewer, als Hinweis). Das
  steht jetzt mit seiner Abwägung unter den bewussten Entscheidungen in [DEFECTS.md](DEFECTS.md).
- Ob die historischen Punktverweise der Runden 3 und 4 zur heutigen Nummerierung passen, hat der
  Konsistenzreviewer nur stichprobenhaft geprüft. Die Frage steht im Auftrag für Runde 16.

**Gegenprobe.** Jede Korrektur mit einem Test zurückgenommen, in sieben Läufen auf dem Stand von `ab9f36e`,
jede Datei vor dem nächsten Lauf aus `HEAD` wiederhergestellt:

- Modul `core`, Lauf 1: der Lexer ohne den Zweig für rohe Zeichenketten und `ArtifactFiles` ohne die neue
  Prüfung. Es fielen genau `theScannerSeesTheDeclarationsThatUsedToSlipPastIt` und
  `ArtifactFilesTest.sizeBoundsAndRetentionBoundaryAreEnforced`.
- Lauf 2, der Lexer zählt verschachtelte Kommentare nicht mehr, und Lauf 3, das Wertmuster reicht wieder bis
  zum Zeilenende: Beide Male fiel genau der Scannertest.
- Lauf 4, der Lexer ohne Zeichenliterale: alles grün, wie das Modell vorhergesagt hatte. Die Zusicherungen
  für Zeichenliterale zeigen also nicht, dass dieser Zweig gebraucht wird; der Kommentar über `codeOnly` sagt
  das selbst.
- Der Selbsttest von `check-repository.py` ohne den Big-Endian-Eintrag in `UTF32_BOMS`: gefallen.
- Modul `app`, Lauf 1: der Zuschlag wieder an `any { it.isNotBlank() }`, `configError` ohne
  `CONTEXT_TERM_BLANK` und `PREVIEW_ERRORS_SHOWN_AS_TEXT` ohne `CHOOSE_AUDIO_TRACK`. Es fielen genau
  `estimateCostCeilsMinimumAndAssemblyAddonsAndBlocksUnknownPrice`,
  `aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced` und
  `everyErrorThePreviewCanShowHasItsHeightReserved`.
- Modul `app`, Lauf 2: `estimatedCostMicrousd` ohne die Ablehnung einer Liste mit leerem Eintrag. Es fiel
  genau `aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced`.

Danach wurde der committete Stand neu gebaut und installiert; `core` lief vollständig grün, beide
App-Testklassen ebenfalls, und der Baum war sauber. `everyNumberThisModuleStatesHasALineInThisFile` fiel in
keinem der vier `core`-Läufe: Der Test am Baum kann diese Lücken nicht zeigen, der an Textbeispielen schon.

**Für die Listenfelder ist der Gerätelauf die Gegenprobe.** Der App-Stand vor der Korrektur zeigte
`KubernetesDocker` und `de`, die erste Fassung schnell getippt zweimal verstümmelte Begriffe, der Stand von
`ab9f36e` alle acht Fälle wie getippt. Die Einzelwerte stehen weiter oben in dieser Passage.

**Ohne Gegenprobe bleiben die reservierten Höhen.** Sie haben keinen UI-Test; dass sie wirken, ist am Code
zu sehen, gemessen ist es nicht ([Punkt 35](DEFECTS.md)). Die berichtigten Kommentare in `StatedNumbersTest`
und die Namen in ARCHITECTURE sind Prosa.

Gates nach Runde 15: **177 JVM-Tests** im Modul `core` ohne Fehler (unverändert zu Runde 14, weil die neuen
Zusicherungen in bestehenden Testmethoden stehen), alle vier Lintberichte ohne Befund, der unsignierte
Release-Build gebaut, `tools/check-repository.py` mit Selbsttest bestanden (203 Dateien gelesen, 19 als
binär übersprungen), **195 Instrumentierungstests** im Modul `app` — 189 im gemeinsamen Lauf, 4 weitere
einzeln über ihre Stufen, 0 Fehler — und **39** im Modul `extractor` — 35 bestanden, 4 per Annahme
übersprungen, 0 Fehler. Der erste Lauf um 18:37 nutzte eine Test-APK von 18:16, älter als die letzten
Commits, und `extractor` wird mit `core` gebaut. Deshalb lief er nach der Gegenprobe noch einmal, um 20:26
gegen den Stand von `ab9f36e`, mit demselben Ergebnis und denselben vier Übersprungenen. Gradle meldete dabei
`:core:compileKotlin`, beide Kotlin-Übersetzungen von `extractor` und `:extractor:packageDebugAndroidTest`
als `UP-TO-DATE`; die Test-APK von 18:16 war also schon die des committeten Stands. Von 411 Tests sind 405
ausgeführt. Per Annahme übersprungen und nirgends einzeln nachgeholt sind im Modul `app` `EngineJobPinningTest#runningCoordinatorJobsFinishWithTheirPinnedEngineAcrossRealActivation`
und `UiFixtureTest#seedSyntheticUiFixtureForAdbViewer`, im Modul `extractor`
`EngineUpdateManagerTest#realReleaseStageActivateAndRollbackSurvivesManagerRestart`,
`ExtractionChainTest#actualAudioIsDownloadedProbedAndPreparedForExactSource`,
`ExtractionChainTest#actualCaptionIsFetchedAndParsedForExactSource` und
`NativeRuntimeTest#publicSourceProbeUsesOnlyConfiguredSourceIdAndCounts`.

**Die Schleife ist nicht konvergiert.** Fünfzehn Runden, keine davon leer. Solange eine Runde noch etwas
findet, ist die nächste fällig — gerade weil die Funde der Runden 3 bis 15 jeweils in den Korrekturen der
Vorrunde lagen. Runde 10 war der deutlichste Beleg dafür, dass eine Korrektur einen Fehler verschieben
statt beheben kann; Runde 11 dafür, dass auch die Messung selbst geprüft gehört; Runde 12 dafür, dass ein
Reviewer, der über den zugewiesenen Diff hinaussieht, den teuersten Fund macht — und dass zwei Listen, die
von Sorgfalt abhingen, beide dieselbe Lücke hatten. Runde 13 ist der bislang deutlichste Fall: Der teuerste
Fund einer Runde kann selbst der Fehler sein. Eine Korrektur, die eine Anbieteraussage ändert, ist erst
belegt, wenn die Anbieterseite im Original gelesen wurde und nicht in einer Zusammenfassung. Und Runde 14
sagt, warum das nicht reicht: Zwei ihrer drei Hauptfunde waren Fehler **in** den Korrekturen der Runde 13,
einer davon schlimmer als die Lücke, die er schließen sollte. Eine Korrektur ist neuer, ungeprüfter Code,
auch wenn sie eine Prüfung erweitert. Runde 15 fügt hinzu, dass das auch für die Beschreibung einer
Korrektur gilt: Ihre eigenen Zahlen — wie viele Formen, wie viele Zusicherungen, welche davon still — waren
in Runde 14 an drei Stellen verschieden falsch, und der erste Entwurf der Kommentare in Runde 15 hat „still“
und „laut“ noch einmal falsch verteilt, bevor das Nachzählen am Modell es fand.

### Runde 16

Commits: `1b2dc45` (Epochen für getippte Einstellungen), `eaba2d4` (Plugin-Lader in jedem Kindprozess aus),
`23d8a0c` (Rollback nennt sein Ziel und stellt nur darauf um), `5da4530` (Platz nur für Vorschaufehler, die die
Vorschau zeigen kann), `0fdf8c0` (was einen Auftrag blockiert, bleibt nach einem Modellwechsel erreichbar),
`579f972` (Kostenzeile reserviert statt gekappt), `c5a6564` (keine leere Adresszeile) und `c3ae9e5`
(Teilergebnis in SRT und VTT). Einen eigenen Doku-Commit hat Runde 16 nicht: Diese Passage ist mit denen der
Runden 17 und 18 in einem Commit entstanden. Gates und Gegenprobe liefen für die Runden 16 und 17 zusammen auf dem
Stand von `8e41bf4` und stehen in der Passage der Runde 17; ein Teil der Messungen am Gerät lief erst in Runde 18.
Wo eine spätere Runde einen hier beschriebenen Mechanismus geändert hat, steht das dabei.

Drei Reviewer, alle nur lesend und gleichzeitig: Code, Konsistenz, Invarianten. Modell und Weg aus
`meta.json` gelesen, nicht aus dem Auftrag: `"agentType":"general-purpose"`, `"model":"sonnet"`,
`"requestShape":"background"`.

**Die Listenfelder der Runde 15 haben das Budgetfeld nicht erreicht, und dessen Bauart vertauschte Ziffern.**
Der Code-Reviewer hat zwei Dinge gezeigt. Erstens verschluckte `ListField` eine Änderung von außen, wenn sie
zufällig einer weitergegebenen Liste glich, die noch nicht zurückgekommen war. Zweitens hatte `LimitFields`
genau die Bauart der Zwischenfassung, die Runde 15 verworfen hatte. Runde 15 hatte `120` und `0.25` schnell
ins Budgetfeld getippt und nichts gefunden. Am Stand von `ab9f36e` auf `emulator-5556` wurde aus schnell
getipptem `0.123456` zuerst `0.134562`. In drei Durchgängen von je acht Fällen wich es danach zweimal ab:
`0.123456` wurde zu `023456.1`, `12.345678` zu `1245678.3`. Ein kurzer Wert belegt nichts über einen langen.
Der dritte Durchgang blieb am Fachbegriffsfeld stehen, weil das Prüfskript ein mehrzeiliges Feld nur bis
zum Ende der Zeile leerte. Das war ein Fehler des Skripts, nicht der App, und ist dort behoben.

Der Mechanismus erklärt, warum kein Vergleich das lösen kann. `collectAsStateWithLifecycle` liefert den
Entwurf ein bis zwei Frames nach dem Tastendruck. Ein Feld, das den zurückkommenden Wert mit seinem Text
vergleicht, kann eine Änderung von außen nicht von einem verspäteten eigenen Wert unterscheiden. Seit
`1b2dc45` vergleicht es deshalb nicht mehr. Jede Schreibstelle des Entwurfs läuft über `withDraft`, das die
Epoche jeder getippten Einstellung weiterrückt, die sich ändert, außer der gerade getippten. Das Feld zeigt
seinen Text genau so lange, wie seine Epoche gilt. Dass alle Schreibstellen das tun, ist eine Absprache und
keine Schranke; das steht als [Punkt 38](DEFECTS.md). Seit `67c2c44` übernimmt das Feld getippten Text
außerdem nur, wenn der Entwurf ihn genommen hat. Am Gerät nach beiden Korrekturen, auf `8e41bf4`: alle acht Fälle
in drei Durchgängen wie getippt, 24 von 24, darunter `0.123456`, `12.345678` und `7.654321` im Budgetfeld,
`de-AT,en-GB,fr-CA,es-419` bei den Untertitelsprachen und drei Fachbegriffe in drei Zeilen. Die Änderung von außen
ließ sich am Gerät nicht prüfen: Den Knopf, der die Längengrenze anhebt, zeigt die Vorschau erst mit einem
gespeicherten Schlüssel, und auf diesem Gerät wird keiner eingegeben. Sie ist dort `NOT_RUN` und nur durch die
Epochentests in `ViewRulesTest` und `ViewModelStateTest` belegt, nicht am Feld selbst.
Die Drehung ist in Runde 18 am Gerät geprüft, auf dem Stand von `39a1cdc`: `Kubernetes` mit einem Zeilenumbruch
dahinter stand nach einer Drehung ins Querformat und zurück unverändert im Fachbegriffsfeld, der Umbruch eingeschlossen.

**Nach einem Prozesstod zeigen die Felder den Entwurf.** Der Code-Reviewer hatte gemeldet, dass ein über
`rememberSaveable` geretteter Text sofort wieder verschwand. Das ist jetzt Absicht und steht als bewusste
Entscheidung in [DEFECTS.md](DEFECTS.md): Der Entwurf ist mit dem Prozess fort, und ein Feld, das den alten
Text zeigte, zeigte einen Wert, mit dem kein Auftrag starten würde. Runde 18 hat das am Gerät geprüft, das Ergebnis
steht ebenfalls dort.

**Ein Teilergebnis sah als SRT oder VTT aus wie ein ganzes.** Das war der mittlere Fund des
Invarianten-Reviewers. Markdown, Text und JSON tragen die Einschränkungen als Metadaten; ein Player zeigt
nur Cues. Seit `c3ae9e5` beginnt ein nicht bestätigt vollständiges Ergebnis mit einem Hinweis-Cue, jeder
Abschnitt, den der Umfang als nicht transkribiert führt, bekommt einen eigenen Cue über genau dieses
Intervall, und VTT trägt die Einschränkungen zusätzlich als `NOTE`. Die Regel steht einmal in `core`,
`TranscriptScope.confirmedComplete`, und der Ausgang eines Versuchs fragt sie an drei Stellen. Für Dokumente
der App ändert sich am Ausgang nichts, weil `SttStep` `technicallyComplete` nur ohne fehlenden Chunk setzt;
nachgelesen an `allComplete`. Ein ganzes Ergebnis ohne Warnung wird Byte für Byte wie vorher exportiert.
Runde 17 hat zwei Stellen nachgezogen: Ergebnisansicht und gespeichertes `complete` eines Artefakts fragen
seit `4ddca61` dieselbe Regel, und der Cue über einem nicht transkribierten Abschnitt liegt seit `ab34dd1` nur
dort, wo kein transkribierter Text läuft.

**Zurück zu einer älteren Engine war ein Tap ohne jeden Hinweis.** Der zweite mittlere Fund desselben
Reviewers, gegen die eigene Vorgabe in `docs/SECURITY_UPDATES.md` (S7). Seit `23d8a0c` öffnet der Knopf eine
Bestätigung, die die Version nennt, und umgestellt wird nur auf die genannte Installation. Gesperrt wird
nichts, weil nichts im Baum sagt, welche Version eine bekannte Lücke hat ([Punkt 37](DEFECTS.md)). Seit
`67c2c44` schließt die Bestätigung erst, wenn die Umstellung läuft.

**Der Plugin-Lader von yt-dlp ist jetzt selbst aus.** Bisher lud yt-dlp kein Plugin, aber nur über zwei
Bedingungen einer Version. Seit `eaba2d4` setzt die Laufzeit `YTDLP_NO_PLUGINS=1`. Der Reviewer hatte die
Variable nur im README geprüft; nachgelesen ist sie im gebündelten 2026.08.19: `load_plugins` in
`yt_dlp/plugins.py` kehrt bei gesetzter Variable zurück, bevor es eine Suchliste liest, und der Weg, den
`YoutubeDL` für die API selbst nimmt, endet an derselben Prüfung.

**Was sonst gefunden und korrigiert ist:**

- Ein Schalter, der für das vorige Modell an war, lag nach einem Modellwechsel grau und unbedienbar da, und
  das Fachbegriffsfeld war verborgen, während seine Einträge den Auftrag als `UNSUPPORTED_OPTION` oder
  `CONTEXT_TERM_BLANK` blockierten (Code-Reviewer). `0fdf8c0`.
- Die Kostenzeile schnitt bei Schriftgröße 2.0 ab, im Englischen das Tarifdatum zur Hälfte, im Deutschen ganz;
  gemessen vor jeder Änderung auf `emulator-5556`. `579f972` reserviert die Höhe des höchsten Texts.
  Nachgemessen am 14. September auf dem Stand von `39a1cdc`, jedes Mal ganz innerhalb des scrollenden Formulars: In
  beiden Sprachen ist die Zeile bei Schriftgröße 1.0 und 1.3 zweizeilig, 84 und 110 px hoch, bei 2.0 dreizeilig und
  252 px hoch, und auf allen sechs Bildschirmfotos steht ihr Text vollständig, das Tarifdatum eingeschlossen.
- Eine importierte Datei hat keine Adresse, und die Karte zeichnete trotzdem eine leere Zeile. `c5a6564`.
- `PREVIEW_ERRORS_SHOWN_AS_TEXT` reservierte Platz für `UPLOAD_APPROVAL_REQUIRED`, das keine Vorschau liefern
  kann, und der Test prüfte nur neun von zwölf Codes auf Erreichbarkeit. `5da4530`.
- Die vierte Stelle unter „Aufklappen schiebt“ war falsch zugeschrieben und falsch verortet
  (Konsistenz-Reviewer), und „alle vier Stellen“ war keine vollständige Zählung. In
  [DEFECTS.md](DEFECTS.md) berichtigt.

**Zwei Aussagen von Reviewern stimmten nicht und stehen berichtigt.** Der Code-Reviewer hielt alle zwölf
Vorschaufehler für erreichbar, auch `UPLOAD_APPROVAL_REQUIRED`. `previewError` fragt `configError` aber nur
mit einem passenden Schlüssel und einem Modell, und genau unter dieser Bedingung hat `configurationForStart`
die Freigabe schon gesetzt. Der Test verlangt jetzt alle elf gelisteten Codes und fällt, wenn der zwölfte
wieder in der Liste steht (Gegenprobe in der Passage der Runde 17). Und zur Wartezeit schrieb er,
`1000:00:00` habe 11 Zeichen; es sind 10 gegen 9 des Platzhalters ([Punkt 39](DEFECTS.md)).

**Offen und eingetragen:** [Punkt 34](DEFECTS.md) um die zweite Richtung des Lexerfehlers ergänzt, dazu neu
die Punkte 37 bis 42: Rollback ohne Sperre, `withDraft` als Absprache, die Wartezeit ab 1000 Stunden, das
Fenster zwischen Prüfsumme und Start einer Engine, `youtube-nocookie.com` und geteilte Exporte im Cache.

**Der Weg zum Gate fand einen Fehler, den das Review nicht gefunden hatte.** Der erste Gate-Build scheiterte
an `:app:mergeDexRelease` mit dem Dexing-Fehler, dessen Pfad sich nur in der Groß- und Kleinschreibung
unterscheidet ([LEARNINGS.md](LEARNINGS.md)). Nach dem Leeren von `extractor/build` aus WSL heraus und mit
`--no-watch-fs` kam der Build bis zu den Lintläufen. Der erste davon endete mit „Internal error: Unexpected
lint invalid arguments“; die Ursache war Speichermangel in WSL beim Lesen eines Verzeichnisses über 9p, nicht
der Code. Der Lauf danach fand einen echten Fehler: `1b2dc45` war ohne Lintlauf committet und verletzte
`ModifierParameter` in `DraftTextField`. Das korrigiert `610c4fa`, der erste Commit der Runde 17.

**Die Schleife ist nicht konvergiert.** Sechzehn Runden, keine davon leer. Runde 16 fügt hinzu, dass ein
Gerätelauf nur die Werte belegt, die er tippt: `0.25` blieb heil, `0.123456` nicht, und die erste Korrektur
hätte ohne den längeren Wert als belegt gegolten.

### Runde 17

Commits: `610c4fa` (Lint: `DraftTextField` ohne optionalen Modifier), `67c2c44` (ein abgewiesener Tastendruck
und die Rollback-Bestätigung bleiben, wie sie waren), `85a2a50` (Start lässt leere Fachbegriffe weg), `4ddca61`
(Ergebnisansicht und Artefaktzeile fragen dieselbe Regel), `ab34dd1` (Hinweis-Cue nur, wo kein Text läuft),
`23aab99` (Platz für eine neue Engine, [ADR 0009](adr/0009-engine-slot-cleanup.md)) und `8e41bf4` (neu
gebündelte Engine wird aktiv, [ADR 0010](adr/0010-bundled-engine-after-app-update.md)). Den Doku-Commit teilt sie
mit den Runden 16 und 18.

Zwei Reviewer, nur lesend und gleichzeitig, auf dem Stand von `c3ae9e5` mit den Punkten 37 bis 42 im
Arbeitsbaum: Code und Invarianten. Modell und Weg aus `meta.json` gelesen: `"agentType":"general-purpose"`,
`"model":"sonnet"`, `"requestShape":"background"`. Der Konsistenz-Reviewer sollte über den Doku-Commit der
Runde 16 laufen, den es nicht gab; er läuft in Runde 19 über den Doku-Commit, der die Passagen der Runden 16 bis
18 schreibt.

**Eine App-Aktualisierung mit neuer gebündelter Engine konnte die App dauerhaft lahmlegen.** Der
Invarianten-Reviewer fand es am Rand seines Auftrags und stufte es niedriger ein, als es ist. `EngineUpdateManager`
hält höchstens fünf Slots und entfernte nie eine gesunde Installation. Bei fünf belegten Slots fand die gebündelte
Engine einer neuen App-Version keinen Platz, und `ensureBundledLocked` warf `STORAGE`. Weil `bundled()`,
`active()`, `installations()`, `stage()`, `activate()` und beide Rollback-Aufrufe zuerst dort vorbeikommen, ließ
sich danach keine Quelle mehr prüfen und kein Auftrag starten. Die Vorgabe, die das verhindert hätte, steht seit
dem ersten Commit in `docs/SECURITY_UPDATES.md` (S7): alte Versionen erst ohne aktive Referenzen bereinigen.
Seit `23aab99` räumt der Manager, wenn ein neuer Slot entstehen soll und keiner frei ist. Nie entfernt werden die
aktive, die vorherige und die gebündelte Installation und jede, an die ein unfertiger Versuch gebunden ist. Eine
Engine, mit der ein Teilergebnis fortgesetzt würde, wich zunächst nur als letzter Ausweg und nur für die gebündelte
Engine; diese Ausnahme beruhte auf einer falschen Annahme und ist in Runde 18 entfallen. Was Aufträge brauchen,
fragt der Manager über Room, und nur, wenn etwas weg muss. Reicht es nicht, heißt der Fehler jetzt `SLOTS_IN_USE`
statt `STORAGE`, mit eigenem Text, und nichts ist entfernt. Entscheidung und Restrisiken:
[ADR 0009](adr/0009-engine-slot-cleanup.md) und [Punkte 43 und 44](DEFECTS.md).

**Nach einem App-Update blieb die alte gebündelte Engine aktiv.** Kein Reviewerfund; gefunden beim Lesen von
`ensureBundledLocked` für ADR 0009. Die aktive Engine wurde nur gesetzt, wenn keine gesetzt war. Wer nie selbst
eine andere aktiviert hatte, extrahierte deshalb nach jedem App-Update weiter mit der Engine, die beim ersten
Start aktiv geworden war. Seit `8e41bf4` wird die neu gebündelte beim ersten Start aktiv, wenn die bisher aktive
Engine selbst eine gebündelte war; die alte bleibt als vorherige der Weg zurück, eine selbst aktivierte
heruntergeladene Engine bleibt aktiv, und ein späteres bewusstes Zurückgehen hält.
[ADR 0010](adr/0010-bundled-engine-after-app-update.md), Grenzfälle in [Punkt 46](DEFECTS.md); einen davon hatte
ADR 0010 falsch beschrieben, berichtigt in Runde 18.

**Ein während eines Starts abgewiesener Tastendruck blieb im Feld stehen.** Code-Reviewer. `DraftTextField`
übernahm den getippten Text, bevor feststand, ob der Entwurf ihn nimmt, und `changeDraft` kehrte während eines
Starts ohne Rückmeldung zurück. Ein Tastendruck, der das Feld in den ein bis zwei Frames erreichte, bevor es für
den Start gesperrt wurde, ließ es danach einen Wert zeigen, mit dem weder dieser noch ein späterer Start lief.
Seit `67c2c44` meldet `typeIntoDraft`, ob der Entwurf den Wert genommen hat, und nur dann wird er der Text des
Feldes.

**Die Rollback-Bestätigung schloss sich auch, wenn der Tap abgewiesen wurde.** Code-Reviewer. `rollback` leerte
das Ziel vor `action`; hielt eine andere Aktion die Sperre, kam `ACTION_BUSY`, und die Bestätigung mit der
genannten Version war fort. Seit `67c2c44` schließt sie erst, wenn die Umstellung läuft.

**Eine gespeicherte Fachbegriffsliste mit leerem Eintrag ließ sich auf dem Bildschirm nicht beheben.**
Code-Reviewer. `[""]` zeigt sich als leeres Feld, und jeder Anbieter lehnt die ganze Liste ab; die Vorschau
meldete `CONTEXT_TERM_BLANK`, ohne dass auf dem Bildschirm etwas zu entfernen war. Erreichbar ist das nur über
eine Liste, die gespeichert wurde, bevor das Feld Leerzeilen entfernte, in einem neu vorbereiteten Auftrag oder
einer Voreinstellung. Seit `85a2a50` lässt Start leere Einträge weg, und Fehlerzeile wie Kostenzeile urteilen über
genau die Konfiguration, die Start anlegt. Die Vorschau kann `CONTEXT_TERM_BLANK` damit nicht mehr zeigen, und der
Code steht nicht mehr in `PREVIEW_ERRORS_SHOWN_AS_TEXT`. Ein Auftrag, der vor Runde 17 so gespeichert wurde, bleibt
bei `SttStep.validate` mit diesem Code stehen ([Punkt 31](DEFECTS.md)).

**Ergebnisansicht und Artefaktzeile fragten nicht die Regel der Runde 16.** Beide Reviewer. Die Ergebnisansicht
prüfte `technicallyComplete != true`, und `ArtifactRow.complete` wurde an drei Stellen aus dem rohen Flag
geschrieben, das der Ausgang des Auftrags, die Wiederholung und die Aufbewahrung des Audios lesen. Heute
gleichwertig, weil kein Schreiber das Flag bei einem fehlenden Chunk setzt; eine künftige Änderung an einer der
beiden Formeln hätte aber ein Teilergebnis als ganzes gezeigt und gespeichert. Seit `4ddca61` fragen alle
`confirmedComplete`. Belegt für die Ansicht und die beiden Schreiber in `JobCoordinator`; der Schreiber in
`SttStep` hat keinen eigenen Test ([Punkt 45](DEFECTS.md)).

**Der Hinweis-Cue über einem fehlenden Abschnitt lag auch über transkribiertem Text.** Code-Reviewer. Der Umfang
zählt Chunkfenster, und `SyncTranscriptParser` verschiebt die Anbieterzeiten um den Chunkbeginn, ohne sie am
Chunkende zu kappen; ein Cue kann also über die Fenstergrenze in die Lücke reichen. Seit `ab34dd1` liegt der
Hinweis nur über dem Teil der Lücke, den kein transkribierter Cue abdeckt. Belegt am konstruierten Fall, nicht an
echten Anbieterdaten.

**Der Untertitelzweig suchte seine Engine mit `single`.** Kein Reviewerfund; gefunden beim Lesen für ADR 0009.
Fehlte die gebundene Engine, warf das eine Ausnahme ohne Grund. Jetzt wartet der Auftrag mit
`ENGINE_NOT_AVAILABLE` wie der STT-Zweig, und der Code hat einen eigenen Text ([Punkt 4](DEFECTS.md)). Teil von
`23aab99`.

**Offen geblieben:** Die Testlücke, die der Invarianten-Reviewer meldete — ein Rollback, während ein Auftrag mit
gebundener Engine läuft —, deckt nur der Live-Test `EngineJobPinningTest` ab, und der nur für das Aktivieren; im
Gate-Lauf überspringt er sich per Annahme. Sie stand in der Jagdliste der Runde 18 und steht in der der Runde 19.
Neu in DEFECTS aus Runde 17: 43 bis 47.

**Gegenprobe der Runden 16 und 17.** Jede Korrektur mit Test zurückgenommen, auf dem Stand von `8e41bf4`: sieben
Builds für die Module `app` und `extractor`, drei Läufe für `core`, danach der committete Stand neu gebaut. Jeder
Lauf im Modul `app` hatte einen Kontrolltest, der bestehen musste, und jeder im Modul `extractor` dieselben acht
Tests aus `EngineUpdateManagerTest` und `NativeRuntimeTest`, von denen nur die betroffenen fallen durften.

- App, Satz 1, Epochen und Liste der Vorschaufehler aus Runde 16 zusammen mit Ergebnisregel, Untertitel-Schreibern,
  `single` und Engine-Referenzen aus Runde 17: Es fielen genau die acht erwarteten Tests. Fall A von
  `r16_outside.py` ließ sich auf diesem Build so wenig ausführen wie auf dem committeten Stand, aus demselben Grund.
- App, Satz 2, ein Tastendruck rückt die Epoche seines eigenen Feldes: genau
  `aKeystrokeLeavesItsFieldTheTextAndEveryOtherChangeMovesTheEpochOfWhatItChanges`. `r16_typing.py` zeigte auf
  diesem Build einen von 24 Fällen abweichend, `7.654321` als `7654321`, und endete trotzdem mit 0; seitdem endet
  das Skript mit 1, wenn ein Fall abweicht.
- App, Satz 3, ein abgewiesener Tastendruck gilt als angenommen, die Rollback-Bestätigung schließt vor der Sperre,
  Start behält leere Fachbegriffe: genau die vier erwarteten.
- Extractor, Satz 1, Rollback ohne Ziel, Plugin-Lader an, keine Umstellung nach einem App-Update: genau drei.
  Satz 3, die vorherige Engine ungeschützt und ein Trockenlauf, der entfernt: genau drei. Satz 4, unfertige
  Versuche ungeschützt: genau drei.
- Extractor, Satz 2, Reihenfolge verkehrt und ein verschluckter Fehler der Referenzabfrage: im ersten Lauf vier
  statt drei. Der vierte, `anAppUpdateMakesItsBundledEngineActiveOnceAndKeepsTheOldOneAsTheWayBack`, endete mit
  `PROBE_FAILED` aus `native_runtime:TIMED_OUT`, dem Selbsttest der Laufzeit mit 30 Sekunden, während daneben ein
  Gradle-Build anlief. Mit derselben Test-APK ohne Build fielen genau die drei, und er bestand. Dass die Last die
  Ursache war, legt das nahe; bewiesen ist es nicht.
- `core`, der Zeitexport hält jedes Ergebnis für ganz: die zwei erwarteten Tests und dazu
  `aStretchNotTranscribedIsMarkedOnlyWhereNoTranscribedTextRuns`, der seit Runde 17 ebenfalls die Hinweis-Cues
  eines Teilergebnisses verlangt. Die Regel übersieht fehlende Abschnitte: genau die zwei erwarteten. Der Hinweis
  liegt wieder über der ganzen Lücke: genau der eine.

**Am Gerät, auf `8e41bf4`.** Getippt: 24 von 24 wie getippt, die Werte stehen in der Passage der Runde 16. Die
Kostenzeile hat `r16_cost_font.py` auf diesem Stand gemessen, aber bei 130 % Schrift im Englischen und bei 200 % im
Deutschen lag sie zum Teil unter der Navigationsleiste, und uiautomator meldet nur den sichtbaren Teil; das Skript
nahm diese Höhen trotzdem. Die Messung ist in Runde 18 wiederholt, mit einem Skript, das die Zeile erst ganz über
die Leiste schiebt. Drehen und Prozesstod liefen ebenfalls erst in Runde 18.

Gates nach Runde 17, auf dem Stand von `8e41bf4`: **182 JVM-Tests** im Modul `core` ohne Fehler, alle vier
Lintberichte ohne Befund, der unsignierte Release-Build gebaut, `tools/check-repository.py` mit Selbsttest
bestanden (207 Dateien gelesen, 19 als binär übersprungen), **205 Instrumentierungstests** im Modul `app` — 199 im
gemeinsamen Lauf, 4 weitere einzeln über ihre Stufen, 0 Fehler — und **47** im Modul `extractor` — 43 bestanden,
4 per Annahme übersprungen, 0 Fehler. Von 434 Tests sind 428 ausgeführt; übersprungen und nirgends nachgeholt
sind dieselben sechs wie nach Runde 15. Runde 16 hat in `core`, `app` und `extractor` 4, 4 und 2 Tests
hinzugefügt, Runde 17 1, 6 und 6. Gebaut und gelintet wurde der Arbeitsbaum vor den Commits; die sechs
Code-Commits der Runde 17 enthalten ihn Datei für Datei, und nach dem letzten war nur `docs/DEFECTS.md` noch
geändert. Die erste Installation, ein Vorabtest der neuen Tests, scheiterte mit
`INSTALL_FAILED_INSUFFICIENT_STORAGE`: Der Play Store hatte auf `emulator-5556` am 12. September vorinstallierte
Apps aktualisiert. Seitdem werden beide Testpakete vor jeder Installation deinstalliert (Wartungshinweis in
[DEFECTS.md](DEFECTS.md)).

**Die Schleife ist nicht konvergiert.** Siebzehn Runden, keine davon leer. Runde 17 fügt zwei Dinge hinzu. Ihr
schwerster Fund lag nicht in einer Korrektur der Vorrunde, sondern in einer Vorgabe, die seit dem ersten Commit im
Sicherheitsdokument stand und nie umgesetzt war; ein Review, das nur den Diff liest, findet so etwas nicht. Und
Lint gehört vor den Commit: `1b2dc45` ging mit einem Lintfehler in den Baum, weil Lint erst im Gate danach lief.

### Runde 18

Commits: `cb4afb9` (die Engine eines Teilergebnisses macht Platz wie jede andere), `89eecad` (ein
beschädigter Slot der gebündelten Engine wird ersetzt, [ADR 0011](adr/0011-damaged-bundled-engine-slot.md)),
`8fe0dd5` (jeder Einstellungsspeicher liest die Datei seines eigenen Kontexts) und `39a1cdc` (die
Migrationstests löschen ihre Datenbanken), dazu der Doku-Commit, der diese Passage und die der Runden 16 und 17
schreibt.

Zwei Reviewer, nur lesend und gleichzeitig, auf einem Export (`git archive`) von `8e41bf4` über die Commits
`1b2dc45` bis `8e41bf4`: Code und Invarianten. Modell und Weg aus `meta.json` gelesen:
`"agentType":"general-purpose"`, `"model":"sonnet"`, `"requestShape":"background"`. Ein Konsistenz-Reviewer lief
nicht: Der Doku-Commit, den er lesen sollte, entsteht erst mit dieser Passage. Er läuft in Runde 19.

**Die Engine eines Teilergebnisses hielt ein Update auf, ohne gebraucht zu werden.** Invarianten-Reviewer, mittel.
Runde 17 hatte die Engine des jüngsten STT-Versuchs eines Auftrags mit Teilergebnis vor dem Räumen geschützt, weil
„Nur Fehlendes“ mit ihr weiterlaufe. Das stimmte nicht: `SttStep.prepareMissingRetry` legt den neuen Versuch in
`Phase.SUBMIT` an, mit den Audioabschnitten, die sein Vorgänger vorbereitet hat, und nur `resolve` und `download`
fragen die gebundene Engine. Waren fünf Slots belegt und nur eine solche Engine entbehrlich, lehnte `stage` ein
freiwilliges Update deshalb mit `SLOTS_IN_USE` ab, und der Text verlangte, das Teilergebnis abzuschließen oder zu
löschen. Seit `cb4afb9` hält nur ein unfertiger Versuch seine Engine; `EngineReferences` kennt nur noch
`inUse`, und der Text von `ENGINE_SLOTS_IN_USE` nennt, was tatsächlich hilft.
`SttMissingRetryTest.aMissingChunkRetryOfAVideoCompletesWithoutTheEngineItIsBoundTo` lässt „Nur Fehlendes“ für ein
Video bis zum Ergebnis laufen, während die Engine, an die es gebunden ist, nicht installiert ist.
[ADR 0009](adr/0009-engine-slot-cleanup.md) ist berichtigt.

**Ein beschädigter Slot der gebündelten Engine hätte Vorschau und Start dauerhaft verhindert.** Kein Reviewerfund;
gefunden beim Nachprüfen des ersten Grenzfalls in ADR 0010. `materializeSlot` lehnt seit `06b996a` einen
vorhandenen Slot ab, dessen Datei nicht die Bytes seines Namens enthält, auch eine symbolische Verknüpfung an
seiner Stelle. Alle Aufrufe des Managers außer `check()`, `discardUnhealthyCandidate()` und `file()` durchlaufen
zuerst `ensureBundledLocked`. Bei einem solchen Slot der gebündelten Engine endete deshalb jeder davon mit
`VERIFICATION`, darunter jede Vorschau und jeder Start eines Auftrags, und das nach jedem Start der App, bis jemand
die App-Daten und mit ihnen den Verlauf löschte. Beobachtet ist das nicht. Seit `89eecad` ersetzt
`ensureBundledLocked` den Slot durch die Bytes, die es gerade aus dem APK kopiert und geprüft hat; eine symbolische
Verknüpfung wird selbst entfernt, nie ihr Ziel, und `stage` lehnt weiter ab. Test:
`aDamagedBundledSlotIsReplacedWithTheVerifiedEngineInsteadOfStoppingEveryCall`. Der Grenzfall in ADR 0010 war damit
falsch beschrieben: Ein ungültiger Slot stellte nicht erneut um, sondern hielt Vorschau und Start an, und nach einem
Absturz zwischen den zwei Speicherungen kann niemand zurückgehen, bevor die Umstellung nachgeholt ist. ADR 0010 ist
berichtigt.

**Instrumentierungstests haben die Einstellungen der App auf dem Gerät geändert.** Kein Reviewerfund; gefunden beim
Lesen der Einstellungsdatei von `app.sourcescribe.debug` auf `emulator-5556`. Darin standen Werte aus Tests, etwa
der Fachbegriff `mutated-default` aus `ParallelJobsTest`, zuletzt geschrieben am 14. September um 00:47, während der
Gates der Runde 17. Der Delegat `preferencesDataStore` hält einen DataStore für den ganzen Prozess, auf den Dateien
des Kontexts, der ihn zuerst benutzt; ein `SettingsStore` für einen Testkontext mit eigenen Dateien las und schrieb
deshalb die der App. Seit `8fe0dd5` gibt es einen DataStore je Datei. Die App hat nur einen Kontext und behält
damit ihre Datei. `SettingsStoreTest` prüft, dass zwei Kontexte zwei Dateien benutzen und die Datei der App
unberührt bleibt. Die Werte auf dem Gerät bleiben stehen, weil niemand weiß, was vorher darin stand
(Wartungshinweis in [DEFECTS.md](DEFECTS.md)).

**Die Migrationstests ließen ihre Datenbanken liegen.** Kein Reviewerfund, gefunden beim selben Blick in die
App-Daten. `MigrationTest` legt je Test eine Datenbank zwischen den Datenbanken der App an, und jede Ausführung ließ
zwei zurück, jede mit Journal und Sperrdatei: 24 Dateien aus vier Läufen am 13. und 14. September. Seit
`39a1cdc` löscht eine Regel, die die Regel des Helfers umschließt, beide nach dem Test, samt der Sperrdatei,
die nicht zu den Dateien gehört, die `Context.deleteDatabase` entfernt, und verlangt, dass keine Datei mit ihrem
Namen bleibt.
Nach den beiden Gate-Läufen der Runde 18 lag auf `emulator-5556` keine neue Datei der Migrationstests. Die 24 alten
und die zwei Sperrdateien aus der Gegenprobe sind danach gelöscht, nachdem sie aufgelistet waren; in `databases/`
liegen nur noch die Datenbank der App mit `-shm` und `-wal`.

**Die Gates fanden einen Fehler in einer Korrektur dieser Runde.** Der erste Gate-Lauf meldete sieben Fehlschläge in
`AudioImportTest`, alle mit `DEVICE_STORAGE_LOW`, bei 560 MB freiem Speicher. Die Ursache war die Korrektur der
Einstellungen: Damit `AudioImportTest` die Einstellungen seines eigenen Kontexts liest, bekam dessen Attrappe ein
eigenes `filesDir`, legte das Verzeichnis aber nicht an, wie es `Context.getFilesDir` tut. Die Speicherprüfung misst
den freien Platz jedes ihrer Verzeichnisse mit `File.usableSpace`, und das ist für einen Pfad, der nicht existiert,
0. Die Attrappe legt das Verzeichnis jetzt an; der zweite Lauf steht unten. Vor dem Gate war kein Test über die
geänderte Klasse gelaufen.

**Offen und eingetragen:** die übrigen Funde des Code-Reviewers. Ein App-Update verdrängt eine dritte Engine aus der
Rolle der vorherigen; er stufte das als mittel ein, hier steht es als niedrig, mit dem Grund, in
[Punkt 46](DEFECTS.md). `stage` fragt die Referenzen vor dem Download und beim Räumen getrennt
([Punkt 48](DEFECTS.md)), und eine Vorschau hält ihre Engine nicht ([Punkt 49](DEFECTS.md)). Beides hielt er für
offen; heute verhindert es die Sperre von `MainViewModel.action`, die aber je View-Model gilt. Dazu
[Punkt 50](DEFECTS.md): Instrumentierungstests reihen Arbeit in den WorkManager der App ein. Zwei Testlücken, die er
nannte, bleiben offen und stehen in der Jagdliste: eine IME-Komposition während eines abgewiesenen Tastendrucks und
der Lückenhinweis im VTT-Export.

**Gegenprobe der Runde 18.**
Jede Korrektur mit Test zurückgenommen, auf dem Stand von `39a1cdc`: fünf Builds, zwei für das Modul `app` und drei
für das Modul `extractor`, danach der committete Stand neu gebaut und installiert; kein Build lief neben einem
Gerätelauf. Jeder Lauf im Modul `app` hatte einen Kontrolltest, jeder im Modul `extractor` dieselben neun Tests aus
`EngineUpdateManagerTest` und `NativeRuntimeTest`, von denen nur die betroffenen fallen durften. So kam es in allen
fünf Sätzen.

- App, Satz 1, fertige Versuche halten ihre Engine wieder, und der Schritt `SUBMIT` fragt die gebundene Engine:
  genau `EngineReferencesTest.unfinishedAttemptsKeepTheirEnginesAndAPartialResultKeepsNone` und
  `SttMissingRetryTest.aMissingChunkRetryOfAVideoCompletesWithoutTheEngineItIsBoundTo`, der zweite mit `SUBMIT`
  statt `NORMALIZE`.
- App, Satz 2, ein Einstellungsspeicher für den ganzen Prozess, und die Migrationstests löschen ihre Sperrdateien
  nicht: genau `SettingsStoreTest.aStoreReadsAndWritesTheSettingsInTheFilesOfItsOwnContext` und beide
  Migrationstests, diese mit je einer übrig gebliebenen `.lck`-Datei, obwohl `Context.deleteDatabase` weiter lief.
  `AudioImportTest` bestand, und die Einstellungsdatei der App blieb unverändert.
- Extractor, Satz 1, geräumt wird zuerst die neueste Installation, und die gebündelte Engine lehnt einen
  beschädigten Slot wieder ab: genau drei, der Test des beschädigten Slots mit `VERIFICATION` aus
  `materializeSlot`.
- Extractor, Satz 2, die vorherige Installation ist ungeschützt, und das Ersetzen eines Slots löscht, worauf seine
  Dateien zeigen: genau drei; der Test des beschädigten Slots fand das Ziel der symbolischen Verknüpfung nicht mehr.
- Extractor, Satz 3, Installationen unfertiger Versuche sind ungeschützt: genau die fünf erwarteten.

**Am Gerät, auf dem Stand von `39a1cdc`.**
Die Kostenzeile ist in beiden Sprachen bei drei Schriftgrößen nachgemessen, jede Messung mit der Zeile ganz
innerhalb des scrollenden Formulars; die Zahlen stehen in der Passage der Runde 16. `r16_outside.py` fand im ersten
Lauf in der Dateiauswahl des Systems binnen 17 Sekunden weder die Datei noch die Seitenleiste und brach ab, bevor
es zu Drehung und Prozesstod kam. Seitdem wartet es bis zu 30 Sekunden, bis die Auswahl offen ist, sichert sonst
Bildschirm und Hierarchie und prüft Drehung und Prozesstod auch ohne Import. Der zweite Lauf, nach einem Neustart
des Rechners durch ein Windows-Update, kam durch die Auswahl: Die Änderung von außen blieb `NOT_RUN`, weil ohne
gespeicherten Schlüssel kein Knopf die Längengrenze anhebt, die Drehung bestand, und nach dem Prozesstod war das
Budgetfeld leer (Passage der Runde 16 und [DEFECTS.md](DEFECTS.md)). Vor und nach beiden Läufen hatte die
Einstellungsdatei der App denselben SHA-256.

Gates nach Runde 18: **182 JVM-Tests** im Modul `core` ohne Fehler, alle vier Lintberichte ohne Befund, der
unsignierte Release-Build gebaut, `tools/check-repository.py` mit Selbsttest bestanden (209 Dateien gelesen, 19 als
binär übersprungen), **207 Instrumentierungstests** im Modul `app` — 201 im gemeinsamen Lauf, 4 weitere einzeln über
ihre Stufen, 0 Fehler — und **48** im Modul `extractor` — 44 bestanden, 4 per Annahme übersprungen, 0 Fehler. Von
437 Tests sind 431 ausgeführt; übersprungen und nirgends nachgeholt sind dieselben sechs wie nach Runde 15. Runde 18
hat im Modul `app` 2 Tests hinzugefügt und im Modul `extractor` 1. Der erste Lauf der App-Suite um 02:32 endete mit
den sieben Fehlschlägen oben, der zweite um 02:46, nach dem Neubau der Test-APK, ohne Fehler. Vor und nach beiden
Läufen hatte die Einstellungsdatei der App denselben SHA-256, und es lagen 24 Migrationsdateien in den App-Daten.
Gebaut, gelintet und geprüft wurde der Arbeitsbaum vor den Commits. Der Code der vier Commits ist dieser
Arbeitsbaum, nachgeprüft über einen Fingerabdruck des Diffs unmittelbar vor dem ersten Commit; nach den Gates
geändert wurde nur der Absatz zu den Restrisiken in ADR 0009.

**Die Schleife ist nicht konvergiert.** Achtzehn Runden, keine davon leer. Runde 18 fügt zwei Dinge hinzu. Die
Begründung einer Architekturentscheidung ist selbst eine Behauptung über den Code: ADR 0009 schützte eine Engine für
einen Weg, der sie nie ausführt, und ADR 0010 beschrieb einen Grenzfall, den der Code nie erreichen ließ; beides
zeigte sich erst beim Lesen der Stellen, die die ADRs nennen. Und eine Testisolierung wirkt nur so weit, wie der Code
den Kontext fragt, den der Test ihm gibt, und eine Attrappe muss die Verträge des Originals halten: Der Delegat der
Einstellungen kannte nur den ersten Kontext, und die Korrektur brach sieben Nachbartests, weil ihre Attrappe ein
Verzeichnis nannte, das es nicht gab.

### Runde 19

Gelesen haben die vier Commits der Runde 18 ein Code- und ein Invarianten-Reviewer auf einem Export von
`39a1cdc` und den Doku-Commit `51329fb` ein Konsistenz-Reviewer auf einem Export dieses Commits. Alle drei
liefen als `claude-sonnet-5`, abgelesen an den Antworten in ihren Transkripten. Eine Unterbrechung der Sitzung
durch ein Windows-Update traf die ersten beiden; beide liefen aus ihren Transkripten weiter.

**ADR 0010 sagte zu viel über `ensureBundledLocked`.** Code-Reviewer, niedrig. ADR 0010 begründete, warum nach einem
Absturz zwischen den zwei Speicherungen niemand zur alten Engine zurückgehen kann, damit, dass jeder Aufruf des
Managers zuerst `ensureBundledLocked` durchlaufe. `check()` und `discardUnhealthyCandidate()` tun das nicht; beim
Nachprüfen kam `file()` hinzu, und derselbe Satz stand in einem Kommentar in `ensureBundledLocked`. ADR 0011 zählte die
sieben Aufrufe, die es durchlaufen, richtig auf, nannte sie aber „jeden Aufruf des Managers“. Die Folgerung hält,
weil `rollback()` und `activate()` es durchlaufen. Berichtigt in beiden ADRs und im Kommentar (`3fc97aa`).

**Das Ersetzen eines beschädigten Slots ließ den Pfad der Engine kurz fehlen.** Code-Reviewer, mittel. `validSlot`
wertet jede Ausnahme als ungültig, auch einen Lesefehler beim Hashen einer intakten Datei, und seit `89eecad`
ersetzt `ensureBundledLocked` einen ungültigen Slot, indem es ihn entfernt und die geprüfte Kopie an seine Stelle
umbenennt. Ein Auftrag, der yt-dlp genau dazwischen über seinen Pfad startet, scheitert, und bis zu vier Aufträge
dürfen gleichzeitig laufen. Jetzt nimmt die geprüfte Datei mit einer einzigen Umbenennung den Platz der alten im
Verzeichnis des Slots ein, `Files.move` mit `ATOMIC_MOVE` und `REPLACE_EXISTING`, die Metadaten mit einer zweiten;
wer den Pfad öffnet, findet die alte oder die neue Datei (`a1a5af1`,
[ADR 0011](adr/0011-damaged-bundled-engine-slot.md)). Entfernt und neu angelegt wird der Slot nur noch, wo an seiner
Stelle kein Verzeichnis steht, ein Teil seines Pfads eine symbolische Verknüpfung ist oder statt der Datei ein
Verzeichnis darin liegt, und einen solchen Slot lehnt `file()` ab; außerdem, wenn die Umbenennung über eine intakte
Datei scheitert. Den letzten Fall ließ die erste Fassung des ADR aus; aufgefallen ist das beim Schreiben dieser
Passage, vor dem Commit. Der Test des beschädigten Slots legt jetzt eine Datei neben die Engine, die jede Reparatur
überstehen muss, und ersetzt zuletzt den ganzen Slot durch eine symbolische Verknüpfung auf ein Verzeichnis mit einer
falschen `yt-dlp`: Die Verknüpfung weicht, das Verzeichnis bleibt, wie es war.

**`stage` lehnte einen beschädigten Slot ab, ohne dass ein Test es zeigte.** Invarianten-Reviewer, Testlücke.
ADR 0011 sagte, für diesen Weg gebe es keinen deterministischen Test, weil eine gültige Signatur zu einem echten
Release gehöre. Die gebündelte Engine ist selbst eines:
`stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt` liefert sie `stage` über eine Attrappe des
Netzes, kürzt die Datei im Slot, sobald `stage` die Engine anfragt, und verlangt `VERIFICATION`, die drei Anfragen in
ihrer Reihenfolge, den Slot weiter beschädigt und keine temporären Verzeichnisse; erst der nächste Aufruf, der die
gebündelte Engine einrichtet, repariert ihn (`fbd83ce`).

**STATUS stand drei Runden zurück.** Invarianten-Reviewer, mittel. ADR 0009 und ADR 0011 verweisen für ihre
Prüfungen hierher, und dieses Dokument endete bei Runde 15. Geschlossen mit `51329fb`.

**Nicht bestätigt hat sich, dass die Aufräumregel in `MigrationTest` einen Testfehler verdeckt.** Code-Reviewer, als
mittel gemeldet. Er beschrieb `ExternalResource` mit `after()` in einem `finally`, dessen Ausnahme die des Tests
ersetzt; so war es in JUnit 4.12. In 4.13.2, das das Projekt nutzt (`gradle/libs.versions.toml`), sammelt die Regel
beide Ausnahmen und wirft sie zusammen als `MultipleFailureException`.

**Offen und eingetragen:** [Punkt 51](DEFECTS.md), die Migrationstests legen ihre Datenbanken im Datenbankverzeichnis
der App an (Invarianten-Reviewer, niedrig), und [Punkt 52](DEFECTS.md), `SettingsStore` sucht seinen DataStore im
Konstruktor und hält je Datei einen für den ganzen Prozess (Code-Reviewer, zwei niedrige Funde). Ein Detail des
zweiten stimmte nicht: Der ersetzte Delegat fragte `Context.getFilesDir()` nicht im Konstruktor, sondern erst, wenn
DataStore die Datei zum ersten Mal brauchte. Ohne Test bleiben die drei Lücken, die der Code-Reviewer nannte: eine
symbolische Verknüpfung auf eine Verknüpfung an der Stelle des Slots, ein Lesefehler beim Hashen einer intakten Datei
und zwei gleichzeitige Aufrufer des Managers, die das Fenster treffen. Sie stehen in der Jagdliste.

**Zeilenangaben in DEFECTS zeigten auf anderen Code.** Konsistenz-Reviewer, mittel und niedrig. Punkt 40 nannte für
`file()` und `validSlot` zwei Zeilen, die in `rollback` und `ensureRoomLocked` lagen, Punkt 42 für `shareArtifact`
und `shareDiagnostics` zwei, auf denen `retryExport` und `showLicenses` standen. Von den dreizehn Zeilenangaben der
Datei zeigten beim Nachprüfen acht auf anderen Code, auch in Punkt 4 und Punkt 11. Die Punkte nennen jetzt Funktionen
oder zitieren den Ausdruck (`1ef195d`), und `tools/check-repository.py` weist Zeilennummern in Markdown unter
`docs/` ab, in den drei Formen, die DEFECTS benutzte (`0f6db67`). Sonst fand der Konsistenz-Reviewer nichts;
nachgezählt hat er unter anderem die Testzuwächse jeder Runde je Modul, die Summen im Kopf und zwanzig zitierte
Commits.

**Gegenprobe der Runde 19.**
Zurückgenommen, was die neuen Tests der Runde 19 festhalten, auf dem Stand von `0f6db67`: zwei Builds für das
Modul `extractor`, danach der committete Stand neu gebaut und installiert; kein Build lief neben einem Gerätelauf.
Jeder Lauf hatte dieselben neun Tests aus `EngineUpdateManagerTest` und `NativeRuntimeTest`, von denen nur die
betroffenen fallen durften. So kam es in beiden Sätzen.

- Satz 1, `stage` ersetzt einen beschädigten Slot der geladenen Engine, wie es `ensureBundledLocked` tut, und das
  Entfernen einer symbolischen Verknüpfung löscht auch die Datei `yt-dlp` in ihrem Ziel: genau
  `stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt`, dem die erwartete `EngineUpdateException`
  fehlte, und der Test des beschädigten Slots, der die falsche `yt-dlp` im Ziel der Verknüpfung nicht mehr fand.
- Satz 2, ein beschädigter Slot wird immer entfernt und die Kopie an seine Stelle umbenannt, wie in Runde 18: genau
  der Test des beschädigten Slots, dem die Datei neben der Engine fehlte.

Die Regel gegen Zeilennummern weist in der Fassung von `docs/DEFECTS.md` vor `1ef195d` zwölf Zeilen ab, die
zusammen dreizehn Zeilenangaben tragen, und sonst nichts. Ohne die Regel scheitert ihr Selbsttest, weil er in seiner
Testdatei keine der drei Zeilen abweist. Vor und nach den Geräteläufen hatte die Einstellungsdatei der App denselben
SHA-256.

**Gates der Runde 19.**
Runde 19 ändert das Modul `extractor`, seine Tests, `tools/check-repository.py` und die Doku. Gelaufen sind Build und
beide Lintberichte des Moduls `extractor`, beide ohne Befund, `tools/check-repository.py` mit Selbsttest, auch nach
der neuen Regel, und die ganze Suite des Moduls auf `emulator-5556`: **49 Tests**, 45 bestanden, 4 per Annahme
übersprungen, dieselben vier wie nach Runde 18, 0 Fehler. Runde 19 hat im Modul `extractor` einen Test hinzugefügt.
Die Suite lief auf dem Stand vor den Commits, nachgeprüft über einen Fingerabdruck des Diffs. Die letzte Änderung
davor betraf nur einen Kommentar: Der Build danach übersetzte das Modul neu, fand die Klassen unverändert und baute
die Test-APK nicht neu. Die Module `core` und `app` sind in Runde 19 unverändert und liefen zuletzt im Gate der
Runde 18; `app` hängt vom Modul `extractor` ab. Vor und nach dem Lauf hatte die Einstellungsdatei der App denselben
SHA-256.

**Die Schleife ist nicht konvergiert.** Neunzehn Runden, keine davon leer. Runde 19 fügt zwei Dinge hinzu. Ein Fund
über das Verhalten einer Bibliothek gilt nur für die Version, die er beschreibt: Der Reviewer beschrieb JUnit 4.12,
das Projekt nutzt 4.13.2. Und eine Korrektur, die ein Fenster schließt, zählt auf, wo es offen bleibt; die erste
Fassung des ADR für das Ersetzen im Slot ließ einen Fall aus.

### Runde 20

Gelesen haben die fünf Commits der Runde 19 ein Code- und ein Invarianten-Reviewer auf einem Export von
`0f6db67`, beide als `claude-sonnet-5`, abgelesen an den Antworten in ihren Transkripten. Keiner meldete einen
kritischen oder hohen Fund, der Code-Reviewer zwei mittlere und drei niedrige, der Invarianten-Reviewer vier niedrige.
Zwei Befunde fanden beide: das verlorene Ausführungsbit und den Ausgang, wenn beim Reparieren eines Slots nur die
zweite Umbenennung scheitert.

**Ein Aufruf meldete `STORAGE`, obwohl er den Slot schon repariert hatte.** Code-Reviewer, mittel, und
Invarianten-Reviewer, niedrig. `replaceInSlot` benennt erst die geprüfte Datei in den beschädigten Slot und dann seine
Metadaten. Scheitert nur die zweite Umbenennung, endet der Aufruf mit `STORAGE`, obwohl im Slot schon die geprüften
Bytes liegen; der nächste Aufruf findet den Slot gültig und benutzt ihn, und die alten Metadaten bleiben liegen. So
bleibt es: Der Speicher hat versagt, und der Aufrufer erfährt es. Der Code-Reviewer schlug vor, den Aufruf gelingen zu
lassen; dann bliebe der Fehler unbemerkt. Hier ist der Fund als niedrig eingestuft, weil er einen Aufruf kostet und
`metadata.json` nirgends gelesen wird. Ein neuer Test legt an die Stelle der Metadaten ein Verzeichnis, über das keine
Umbenennung einer Datei geht, und hält den Ausgang fest; ADR 0011, das bisher nur zwei Ausgänge kannte
(Code-Reviewer, niedrig), und die KDoc von `materializeSlot` beschreiben ihn (`dc9e6dd`). Die alten
Metadaten stehen als [Punkt 53](DEFECTS.md) in DEFECTS.

**Die Regel gegen Zeilennummern kannte die Formen nicht, die als Nächstes kommen.** Code-Reviewer, mittel. Ein
Linkanker auf eine Zeile, ein großes L vor der Zahl hinter einem Dateinamen, die Abkürzung „Z.“ vor einer Zahl und
ein groß geschriebenes „ZEILE“ kamen durch. Die Regel weist sie jetzt ab; der Selbsttest hat eine Zeile je Form und
Beinahe-Treffer wie „z. B.“, `L10n` und „Pipeline 2“, die durchgehen müssen (`203114f`). Dass die Regel auch
Text trifft, der nur so aussieht, etwa einen Port allein in Backticks oder eine ganz zitierte Zeile eines
Stacktraces, meldeten der Code-Reviewer als niedrig und der Invarianten-Reviewer als spekulativ. Das gilt und bleibt
so: Solcher Text wird umformuliert, und der Kommentar über den Mustern sagt das. In `docs/` trifft es heute nichts.

**`tools/check-repository.py` hatte sein Ausführungsbit verloren.** Beide Reviewer, niedrig. Es ging mit
`0f6db67` verloren, weil das Hilfsskript, das die Commits der Runde 19 in den Index schrieb, jede Datei als
100644 eintrug; das der Runde 20 übernimmt den Modus aus HEAD. `tools/build-webp-android.sh` hatte das Bit nie,
obwohl sein Bericht es direkt über seinen Pfad startet. Beide sind wieder 100755, und `tools/check-repository.py`
meldet jetzt ein versioniertes Skript mit Shebang außerhalb eines Verzeichnisses `src`, das git nicht als ausführbar
führt. Die Schlusszeile nennt, wie viele Skripte es prüfte, oder dass kein Git-Index da war (`3bca289`).

**Der Testhelfer übersah das Arbeitsverzeichnis der gebündelten Engine.** Invarianten-Reviewer, niedrig.
`assertNoUpdateTemporaryDirectories` suchte nach Verzeichnissen, die mit `.staging-` oder `.slot-` beginnen, nicht
nach `.bundled-`, in dem `ensureBundledLocked` arbeitet, und jeder der sieben Tests, die den Helfer heute rufen,
richtet zuerst die gebündelte Engine ein. Jetzt schlägt er bei jedem Eintrag in `engines/` fehl, dessen Name mit
einem Punkt beginnt, und nennt ihn (`8b3ffb8`). In der Suite ließ kein Aufruf ein solches Verzeichnis zurück.

**Nicht bestätigt hat sich eine Testlücke, die der Code-Reviewer nannte:** Kein Test halte fest, dass `stage` einen
beschädigten Slot nie ersetzt. Das tut `stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt` aus
Runde 19; die Gegenprobe der Runde 19 ließ `stage` den Slot ersetzen, und genau dieser Test fiel.

**Konsistenz der Doku der Runde 19.** Ein dritter Reviewer, ebenfalls `claude-sonnet-5`, prüfte jede
Tatsachenbehauptung von `b204a47` gegen Code, Git und die Rohbelege der Runde 19 und fand nichts. Fünf Aussagen
konnte er mit den Belegen, die er hatte, nicht prüfen; vier davon sind seither nachgeprüft. Die vier übersprungenen
Tests im Modul `extractor` sind dieselben wie im Gate der Runde 18. JUnit 4.12 ruft `after()` von `ExternalResource`
in einem `finally`, so steht es in der Quelle beim Tag `r4.12`. Die drei Reviewer der Runde 19 liefen laut ihren
Transkripten als `claude-sonnet-5`, und in den Transkripten der ersten beiden liegt eine Pause von siebeneinhalb
Stunden, die zur Unterbrechung durch das Windows-Update passt. Dass die Reviewer der Runde 20 beim Schreiben noch
nichts gemeldet hatten, stimmte, als der Text entstand; die Datei des Invarianten-Berichts ist allerdings zwölf
Sekunden älter als der Commit. Offen bleibt, ob beim Build der Runde 19 wirklich nur noch ein Kommentar ausstand: Die
Zeitstempel zeigen es nicht und widerlegen es nicht.

**Gegenprobe der Runde 20.**
Zurückgenommen, was die Korrekturen der Runde 20 festhalten, auf dem Stand von `dc9e6dd`: zwei Builds für
das Modul `extractor`, danach die Test-APK des committeten Stands neu gebaut und installiert; kein Build lief neben
einem Gerätelauf. Jeder Lauf hatte dieselben zehn Tests aus `EngineUpdateManagerTest` und `NativeRuntimeTest`, von
denen nur die betroffenen fallen durften. So kam es in beiden Sätzen.

- Satz 1, `ensureBundledLocked` lässt sein Verzeichnis `.bundled-` liegen: genau die sieben Tests, die den Helfer
  rufen, jeder mit dem Namen des liegen gebliebenen Verzeichnisses in der Meldung.
- Satz 2, der Aufruf gelingt, wenn die Metadaten der Datei nicht folgen: genau der neue Test, dem die erwartete
  `EngineUpdateException` fehlte.

Die Prüfung der Dateimodi meldete über das Repository, solange git beide Skripte noch als 100644 führte, genau diese
zwei und sonst nichts. Meldet sie nichts, scheitert ihr Selbsttest an dem Teil mit dem eigenen Git-Index; mit den
Mustern der Runde 19 scheitert er an der Zusicherung über die Zeilen, weil nur die ersten drei abgewiesen werden. Vor
und nach den Geräteläufen hatte die Einstellungsdatei der App denselben SHA-256.

**Gates der Runde 20.**
Runde 20 ändert Tests und einen Kommentar im Modul `extractor`, `tools/check-repository.py`, zwei Dateimodi und
die Doku. Gelaufen sind Build und beide Lintberichte des Moduls `extractor`, beide ohne Befund,
`tools/check-repository.py` mit Selbsttest und die ganze Suite des Moduls auf `emulator-5556`: **50 Tests**, 46
bestanden, 4 per Annahme übersprungen, dieselben vier wie nach Runde 19, 0 Fehler. Runde 20 hat im Modul `extractor`
einen Test hinzugefügt. Build und Suite liefen auf dem Stand vor den Commits; der Fingerabdruck des Diffs war am
Anfang und am Ende des Builds und vor der Suite derselbe. Die Fassung von `tools/check-repository.py` in jedem der
zwei Commits, die sie ändern, bestand ihren Selbsttest, bevor der Commit entstand, und nach dem letzten Commit bestand
der Lauf über das Repository mit fünf geprüften Skripten. Die Module `core` und `app` sind in Runde 20 unverändert und
liefen zuletzt im Gate der Runde 18. Vor und nach der Suite hatte die Einstellungsdatei der App denselben SHA-256.
Danach liefen die 182 JVM-Tests von `core` noch einmal, im Build für Bouncy Castle 1.86 (`d994c23`), und bestanden
alle.

**Die Schleife ist nicht konvergiert.** Zwanzig Runden, keine davon leer. Runde 20 fügt zwei Dinge hinzu. Ein
Werkzeug, das Commits aus Textersetzungen baut, schreibt mehr als Text: Das der Runde 19 legte den Dateimodus fest,
und keine Prüfung sah Dateimodi an. Und eine neue Regel braucht Gegenbeispiele in beide Richtungen, bevor sie gilt:
Die gegen Zeilennummern kannte genau die Formen, die das Dokument schon benutzt hatte.

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
sind in r81 mit den damaligen 180 App-Tests (Stand 8. September; die heutige Zahl steht im Kopf
dieser Datei), betrachtetem Screenshot und aktuellem statischen
Releaseaudit nachgeprüft. Offizielle Signatur-/Ausrichtungsprüfung ebenfalls PASS.

Nach dem belegten WSL-Speicherfehler sind 16 GiB Swap aktiv. Builds verwenden einen
Worker, 2 GiB Java-Heap und projektlokale Caches. Keine neue globale Konfiguration.
[Diagnose und Speicherbegrenzung](reports/2026-09-07-wsl-recovery.md).

Die aktuelle Übergabe steht in [HANDOFF](HANDOFF.md), konkrete Restarbeiten in
[NEXT_STEPS](NEXT_STEPS.md), verifizierte Fallstricke in [LEARNINGS](LEARNINGS.md).
Keine automatische Fortsetzung nach diesem begrenzten Preview-Abschluss.
Bereits erledigte Implementierung und Fixtureprüfungen bleiben erhalten.
