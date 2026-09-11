# Tatsächlicher Projektstatus

**Stand:** 11. September 2026. **Freigabe:** Persönliche Preview; vollständige v1 weiterhin blockiert.
Der Preview-Abschluss vom 8. September steht unten; seither ist die Nutzerrückmeldung vom
10. September eingearbeitet, siehe den nächsten Abschnitt. **Die Testzahlen weiter unten in diesem
Abschnitt sind der Stand vom 8. September und nicht der heutige.** Heute sind es 173 JVM-Tests im Modul
`core`, 191 Instrumentierungstests im Modul `app` und 39 im Modul `extractor`, zusammen 403, davon 397
ausgeführt; die Runde-11-Passage sagt, warum die letzten 39 zehn Runden lang in keiner Gate-Meldung
vorkamen. App-Quellstand ist die Spitze von `main`,
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

## Nutzerrückmeldung vom 10. September 2026 und elf Reviewdurchgänge

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
Mal auf dem Gerät statt in der JVM. Damit sind es zwölf seit Runde 9, einzeln gefunden — und zwölf einzeln
gefundene Instanzen sind der Hinweis, dass das Suchen einzeln die falsche Form hat.

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

Gates nach Runde 11, vollständig gezählt: **173 JVM-Tests** im Modul `core` ohne Fehler (169 vorher: vier
neue Methoden für die Zahlen, zwei für die Archivgrenzen, zwei durch die Zusammenlegung entfallen), alle
vier Lintberichte ohne Befund, **191 Instrumentierungstests** im Modul `app` — 185 im gemeinsamen Lauf, vier
weitere einzeln über ihre Stufen, 0 Fehler — und **erstmals 39** im Modul `extractor` — 35 bestanden,
0 Fehler. Von 403 Tests sind damit 397 ausgeführt. Die sechs übrigen stehen als eigene Zeile in der
Blockadetabelle von NEXT_STEPS, statt als Zahl in einem Satz zu verschwinden. Die Gegenprobe, die jede
Korrektur dieser Runde zurücknimmt, lässt fünf Tests fallen.

**Die Schleife ist nicht konvergiert.** Elf Runden, keine davon leer. Solange eine Runde noch etwas findet,
ist die nächste fällig — gerade weil die Funde der Runden 3 bis 11 jeweils in den Korrekturen der Vorrunde
lagen. Runde 10 war der deutlichste Beleg dafür, dass eine Korrektur einen Fehler verschieben statt beheben
kann; Runde 11 der deutlichste dafür, dass auch die Messung selbst geprüft gehört — zehn Runden lang war
eine ganze Testsuite nicht in der Zahl enthalten, die ich als Beleg genannt habe.

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
