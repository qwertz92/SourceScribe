# Bekannte Fehler nach Priorität

**Stand:** 14. September 2026, Preview 0.2.0-preview.1 am Stand `1fe2dad`, nach 23 Runden adversarischer Reviews.
Hier steht jeder bekannte, nicht behobene Punkt mit seiner Auswirkung und einer Priorität. Nach Runde 23 hat der Nutzer
die Reviewschleife angehalten, um die Preview selbst zu testen und danach auszuwählen, was behoben wird. Ohne diese
Auswahl wird nur ein Fehler der Stufe P1 behoben. Stelle im Code, Ablauf und Belege stehen in [DEFECTS.md](DEFECTS.md)
unter derselben Nummer. Die Punkte 1 bis 3 sind die offenen Rückmeldungen aus dem Test vom 10. September. Geschlossen
und dort nur noch als Nummer geführt sind 6, 7, 8, 9, 19, 31 und 54.

## Skala

Für funktionale Fehler gibt es kein allgemein anerkanntes Maß wie CVSS für Sicherheitslücken. Üblich ist, zwei Fragen
zu trennen: wie schwer ein Fehler ist und wie dringend er behoben werden soll. DEFECTS führt die Schwere aus Sicht des
Reviews: kritisch, hoch, mittel oder niedrig. Die Priorität hier fragt, was der Nutzer davon merkt und wie oft, und
kann deshalb von der Schwere abweichen. Punkt 57 etwa ist in DEFECTS niedrig und hier P2, weil er jeden Start betrifft.

| Stufe | Bedeutung | Umgang |
|---|---|---|
| P1 | Die App oder eine Kernfunktion ist unbrauchbar: Untertitel holen, Audio transkribieren, Ergebnis speichern oder exportieren. Ebenso Datenverlust, Kosten ohne Freigabe oder eine ausnutzbare Sicherheitslücke. | Sofort beheben. |
| P2 | Ein normaler Ablauf liefert ein falsches oder irreführendes Ergebnis oder stört deutlich; es gibt einen Umweg. | Beheben, wenn der Nutzer es auswählt. |
| P3 | Stört wenig: ungenauer Text, Anzeige, Diagnose, Aufräumen, oder ein Fehler, der im normalen Gebrauch nur unter seltenen Voraussetzungen auftritt. | Nur auf Wunsch. |
| P4 | Merkt man nicht: Tests, Prüfskripte, Code, der heute richtig arbeitet, aber leicht falsch werden kann, oder ein Fall, den nur ein konstruierter Ablauf erreicht. | Nur festgehalten. |

**Unbestätigt** heißt: aus dem Code abgeleitet, aber nicht nachgestellt oder nicht gemessen. Eingestuft ist, was dabei
wahrscheinlich ist; hätte der schlimmere Fall eine höhere Stufe, steht er im Hinweis. Für die sicherheitsnahen Punkte
37, 40 und 42 ist keine ausnutzbare Lücke belegt, deshalb steht bei ihnen kein CVSS-Wert.

Offen sind 50 Punkte: 2 P2, 19 P3, 28 P4 und Punkt 55, der unter „Nicht geprüft“ steht.

## P1

Keiner bekannt. Was einen P1 verbergen kann, steht im nächsten Abschnitt.

## Nicht geprüft

Diese Lücken sind keine bekannten Fehler. Scheitert etwas davon, ist es ein P1.

- **Echte Transkription bei AssemblyAI, OpenAI und Groq.** In keinem Test dieses Projekts mit echtem Schlüssel
  gelaufen, nur gegen nachgebildete Antworten der Anbieter.
- **Ein Gerät mit ARM64, wie fast jedes aktuelle Handy.** Alle Gerätetests liefen auf x86_64-Emulatoren mit Android 17
  (API 37). Die ARM64-Fassung der App lief in keinem dieser Tests.
- **Punkt 55, Android vor Version 17.** Die App prüft die Signatur ihrer mitgelieferten Engine bei jedem Start mit
  Bouncy Castle 1.86. Installieren lässt sich die App ab Android 10 (API 29), gelaufen ist diese Prüfung aber nur auf
  API 37. Scheitert sie auf einer älteren Version, ist die Engine dort unbrauchbar, und ohne sie verarbeitet die App
  keine YouTube-Links. Dafür, dass sie hält, sprechen die Release Notes von Bouncy Castle.
- **Das Update einer installierten Preview 0.1.0.** Die Preview 0.2.0 stellt die Datenbank von Schema 3 auf Schema 4
  um. Geprüft ist das nur an Testdatenbanken in `MigrationTest`.

Nicht geprüft ist außerdem die vollständige Bedienung mit TalkBack.

## P2

| Punkt | Was passiert | Hinweis |
|---|---|---|
| 57 | Nach jedem Start sind unter anderem das Prüfen einer Quelle, der Import einer Audiodatei und der Start eines Auftrags gesperrt, in den Einstellungen auch Sprachwahl, Anbieter und Schlüssel. Solange läuft oben ein schmaler Fortschrittsbalken. Die App prüft in dieser Zeit ihre Aufträge, Zugangsdaten und die mitgelieferte Engine. | Unbestätigt, wie lange. In der CI dauerte es auf einem frisch installierten Emulator länger als 25 Sekunden. Ist es auf dem Gerät nach einem Augenblick vorbei, ist es P4. |
| 1 | Die Ergebnisansicht scrollte beim Test vom 10. September rechts nicht und nur bis zur Hälfte. Sie ist seither neu gebaut. Auf dem Emulator scrollt sie mit einem Transkript von 286 Abschnitten auch am rechten Rand bis zum letzten. | Unbestätigt, ob es auf dem Gerät des Nutzers noch auftritt. |

## P3

| Punkt | Was passiert | Hinweis |
|---|---|---|
| 2 | Die untere Schaltfläche „könnte mehr Platz vertragen“. Heute sind alle Hauptschaltflächen mindestens 52 dp hoch und voll breit. | Rückfrage unten. |
| 3 | Das Glossar sagt, ein AssemblyAI-Schlüssel gelte nur in einer Region, EU oder USA, und in der falschen ende der Aufruf mit einem Anmeldefehler. Gegen die aktuelle Doku des Anbieters ist das nicht geprüft. | Text. |
| 4 | Dutzende Fehlercodes haben keinen eigenen Text; am 11. September waren es mindestens 43. Die App zeigt dann „Vorgang konnte nicht abgeschlossen werden“ mit dem technischen Code, auch bei gewöhnlichen Ausgängen wie `REMOTE_TIMEOUT` oder `NO_TRANSCRIPT`. | Nur im Fehlerfall; in DEFECTS mittel. |
| 5 | Vorschau und Auftrag messen die Länge verschieden: die Vorschau mit der Angabe von yt-dlp, der Auftrag an der geladenen Tonspur. Liegt die Länge bis auf Millisekunden an der Grenze des Auftrags, kann er die Vorschau bestehen und nach dem Download ohne Kosten abbrechen, oder die Vorschau blockiert ihn zu Unrecht. | Kostet höchstens einen Download. |
| 11 | „Nur fehlende Abschnitte erneut versuchen“ wird für ein Teilergebnis dauerhaft verweigert, wenn eine neuere Fassung der App aus seiner gespeicherten Antwort andere Warnungen liest, etwa bei einem leeren Modellfeld. | Der Fehler selbst kostet nichts; ein neuer Auftrag bezahlt aber auch die schon bezahlten Abschnitte. |
| 12 | Für manche Tonspuren fehlt die Größenschätzung, nämlich wenn yt-dlp eine unplausible Audiobitrate und eine brauchbare Gesamtbitrate meldet. | Anzeige. |
| 13 | Lässt sich ein exportiertes Dokument gar nicht abfragen, meldet die App, die Datei sei nicht mehr da, obwohl nur die Prüfung scheiterte. | Irreführende Meldung. |
| 14 | Transkripte aus Untertiteln, die vor dem 11. September entstanden, melden unlesbare Abschnitte als fehlende Zeitmarken statt als fehlenden Text. | Nur ältere Ergebnisse. |
| 17 | Die deutschen Texte sagen mal „Tonspur“, mal „Audiospur“. | Wortwahl, siehe unten. |
| 18 | Ist jeder Abschnitt einer Anbieterantwort unlesbar, übernimmt die App den Volltext, warnt aber wie bei fehlendem Text, obwohl nur Gliederung und Zeitangaben fehlen. | Irreführende Warnung. |
| 24 | Neben der Kostenschätzung steht nur das Datum des Tarifs, nicht die Seite des Anbieters, gegen die er geprüft wurde. | Nachprüfbarkeit. |
| 25 | Die App erlaubt bei Groq 25 MB je Datei, auch wenn ein bezahlter Groq-Schlüssel 100 MB erlaubt. | Nur mit bezahltem Groq-Schlüssel; bewusst die sichere Seite. |
| 35 | Bei sehr großer Schrift könnten Texte auf dem Bildschirm für eine neue Quelle und im Verlauf abgeschnitten werden. Gemessen ist nur die Kostenzeile, und sie steht auch bei doppelter Schriftgröße vollständig da. | Unbestätigt. |
| 36 | Die Fehlerzeile der Vorschau erscheint und verschwindet, und der Startknopf darunter rückt um ihre Höhe. | Entscheidung unten. |
| 37 | Zurück zu einer älteren Engine warnt, sperrt aber nichts, auch nicht bei einer Version mit bekannter Lücke; welche Versionen Lücken haben, weiß die App nicht. | Bräuchte eine Angabe im signierten Engine-Paket. |
| 41 | Links der Form `youtube-nocookie.com/embed/…` lehnt die App ab. | Umweg: dieselbe Adresse mit `youtube.com`. |
| 42 | Geteilte Exporte, auch ganze Transkripte, bleiben im privaten Cache der App, bis Android ihn räumt. | Kein neuer Abflussweg. |
| 46 | Zwei Grenzfälle, wenn ein App-Update eine neue Engine mitbringt, nachdem Engines von Hand gewechselt wurden. Im zweiten ist eine vorher genutzte Engine nicht mehr über „Zur vorherigen Engine“ erreichbar. | Entscheidung unten. |
| 47 | Eine selbst aktivierte Engine wird nach einem App-Update nicht erneut gegen die mitgelieferte Laufzeit geprüft. | Unbestätigt, ob sie dann scheitert. Umweg: „Zur vorherigen Engine“. |

## P4

| Punkt | Was ist |
|---|---|
| 10 | Ein Fehlertext deckt auch einen internen Programmierfehler ab; ein Auslöser im normalen Gebrauch ist nicht bekannt. |
| 15 | Freier Text aus Großbuchstaben in der Warnungsliste würde wie ein Code angezeigt; heute legt niemand solchen Text dort ab. |
| 16 | Eine künftig falsch benannte Warnung `RESPONSE_…` würde als verlorener Abschnitt gemeldet; heute stimmt die Regel für jede. |
| 20 | Die Längengrenze gemeldeter Modellnamen steht in zwei Parsern unabhängig voneinander. |
| 21 | Bei einer grob falsch gestellten Geräteuhr fehlen Tag und Monat im Dateinamen. |
| 22 | Zwei Tonspuren mit überlanger Sprachangabe erscheinen beide als „Unbekannt“, ohne Hinweis; reale Sprachangaben sind viel kürzer. |
| 23 | Die Höchstdauer von 600 Minuten steht im Code und in zwei Texten als eigene Zahl. |
| 26 | Ob „25 MB“ bei Groq und OpenAI dezimal oder binär gemeint ist, ist offen; die App nimmt die kleinere Zahl. |
| 27 | Eine Vorprüfung der Kosten rechnet ohne Zuschläge; heute gibt es dort keine, und die bindende Prüfung rechnet sie ein. |
| 28 | Das Prüfskript des Repositorys übersieht einige Fälle: UTF-16 oder UTF-32 ohne Markierung, Text nach dem ersten Mebibyte, unversionierte Workflow-Dateien und manche Schreibweisen von Versionen in Lizenzhinweisen. Heute trifft keiner zu. |
| 29 | Die Preisseite von OpenAI nennt `whisper-1` nicht wörtlich; der Preis stimmt. |
| 30 | Eine Quelle unter 160 ms bekäme bei AssemblyAI einen winzigen Preis angezeigt und würde dann abgelehnt. |
| 32 | Ein grüner CI-Lauf zeigt nicht, wie viele Gerätetests übersprungen wurden. |
| 33 | Die Rückzugsregel im Erfassungsauftrag wird nie ausgelöst, weil kein Worker eine Wiederholung anfordert. |
| 34 | Ein Test, der Zahlen im Code prüft, versteht verschachtelte Zeichenketten in Templates nicht; heute steht in keiner etwas, das ihn stört. |
| 38 | Dass jede Änderung am Auftragsentwurf über `withDraft` läuft, ist eine Absprache im Code, keine Schranke. |
| 39 | Die Wartezeit im Verlauf reserviert Platz für bis zu 999 Stunden; darüber kann die Karte einmal eine Zeile höher werden. |
| 40 | Zwischen Prüfung und Start einer Engine liegt ein kurzes Fenster, das nur ein Prozess mit den Rechten der App nutzen könnte. |
| 43 | Scheitert eine neue Engine an ihrem Selbsttest, nachdem für sie eine ungenutzte Installation geräumt wurde, sind beide fort. |
| 44 | Eine Meldung zu belegten Engine-Plätzen nennt einen Update-Schritt, obwohl niemand aktualisiert; das setzt unfertige Aufträge an mindestens drei weiteren Engines voraus. |
| 45 | Ein Schreibweg des Vollständigkeitsflags hat keinen eigenen Test. |
| 48 | Ein Engine-Update kann an belegten Plätzen scheitern, wenn währenddessen ein Wiederholungsversuch entsteht; eine Sperre lässt das heute kaum zu. |
| 49 | Eine Vorschau hält ihre Engine nicht fest; dieselbe Sperre lässt den Fall heute kaum zu. |
| 50 | Gerätetests reihen Arbeit in den WorkManager der App ein, ohne Wirkung. |
| 51 | Migrationstests legen ihre Datenbanken neben die der App und löschen sie danach. |
| 52 | Die Einstellungen suchen ihre Datei schon beim Erzeugen, auf dem Hauptthread; ob das spürbar dauert oder je scheitert, ist nicht gemessen. |
| 53 | Nach einer gescheiterten Reparatur kann eine Metadatei im Engine-Slot veraltet sein; gelesen wird sie nirgends. |
| 56 | Scheitert beim Prüfen eines Engine-Pakets auch das Löschen der Prüfansicht, geht die erste Fehlermeldung verloren; abgelehnt wird das Paket so oder so. |

## Entscheidungen des Nutzers

Diese Punkte sind Gestaltungsfragen; ohne Rückfrage wird an ihnen nichts geändert.

- **Punkt 2, „könnte mehr Platz vertragen“:** Gemeint war die Höhe, der Abstand zur Navigationsleiste oder die
  Erreichbarkeit mit dem Daumen? Heute sind alle Hauptschaltflächen mindestens 52 dp hoch und voll breit.
- **Punkt 17, Wortwahl:** „Tonspur“ oder „Audiospur“ für alle deutschen Texte?
- **Punkt 36, Fehlerzeile der Vorschau:** Solange Anbieter, Modell oder Schlüssel fehlen, steht unter der Kostenzeile
  eine Fehlerzeile; ist alles gewählt, verschwindet sie, und der Startknopf rückt nach oben. Entweder bleibt das so,
  oder der Platz bleibt immer frei, im gültigen Zustand mit einem kurzen Satz wie „Diese Quelle ist startklar“, und in
  derselben Höhe steht auch die Längenwarnung.
- **„Aufklappen schiebt, was darunter steht“,** unter den bewussten Entscheidungen in DEFECTS: Aufklappbare Elemente
  verschieben beim Öffnen und Schließen, was darunter steht, etwa die Auftragskarte im Verlauf, die Einträge der Hilfe,
  die Herkunftsangaben in der Ergebnisansicht und die Expertenoptionen. Ebenso verschwindet in den
  Auftragseinstellungen über „Quelle prüfen“ der Hinweis „Für YouTube-Untertitel ist kein Anbieter nötig. …“, sobald
  ein Schlüssel gewählt ist. Bisher gilt: Bewegt sich etwas dort, wo gerade getippt wurde, ist das gewollt, und Platz
  für eingeklappten Inhalt freizuhalten, höbe das Einklappen auf. Soll das anders sein, werden zuerst alle Stellen
  gezählt und dann zusammen geändert.
- **Punkt 46 (b), Engines:** Wer eine heruntergeladene Engine aktiviert und zur mitgelieferten zurückgeht, erreicht die
  heruntergeladene nach einem App-Update mit neuer Engine nicht mehr über „Zur vorherigen Engine“. Entweder bleibt das
  so, und der Weg zurück reicht einen Schritt, oder es kommt eine neue Funktion: jede installierte Engine gezielt
  aktivieren, mit derselben Bestätigung wie beim Zurückgehen.
