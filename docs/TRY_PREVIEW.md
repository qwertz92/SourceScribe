# Persönliche Testversion 0.2.0-preview.1 ausprobieren

App-Version 0.2.0, Versionscode 2; zweite markierte Preview, keine vollständige v1.
Android ab API 29 (Android 10), ARM64 oder x86_64. Tatsächlich geprüft wurde bisher
Android 17/API 37/x86_64/16-KB im Emulator; ein physisches ARM64-Handy ist noch offen.

## Installieren und starten

Gebaut ist der Stand `1fe2dad`. Die Release-APK liegt unsigniert im Projekt unter
`.local-tools/releases/SourceScribe-0.2.0-preview.1-1fe2dad-unsigned.apk`; ihr SHA-256, die Gates und der
Gerätelauf stehen im [Prüfbericht](reports/2026-09-14-preview-0.2.md). Installierbar wird sie erst mit deiner
Signatur. Die Befehle stehen in [BUILD](BUILD.md) unter „Preview 0.2.0-preview.1 signieren“; die Passwörter tippst
nur du ein.

Signiere mit demselben Schlüssel wie 0.1.0. Dann nimmt Android die APK als Update einer installierten 0.1.0 an und
behält deren Daten; beim ersten Start migriert die App ihre Datenbank von Schema 3 auf 4. Diese Migration prüft ein
Test an einer Testdatenbank; das Update einer echten 0.1.0-Installation ist nicht ausprobiert. Mit einem anderen
Schlüssel lehnt Android das Update ab, und Deinstallieren löscht die Daten. Welcher Schlüssel es war, zeigt der
letzte Befehl der Anleitung: Die Zeile mit `SHA-256` muss auf
`19d1da9a8fe704082a531faed8a24d966c485aae581a7076dd4b4f66c11d3881` enden, das Zertifikat von 0.1.0.

Die Gerätetests liefen mit der Debug-APK desselben Stands. Sie liegt als
`.local-tools/releases/SourceScribe-0.2.0-preview.1-1fe2dad-debug.apk` daneben, braucht keine Signatur von dir und
installiert sich als eigene App `app.sourcescribe.debug` mit eigenem Verlauf neben der Release-App. Signiert ist sie
mit dem Debug-Schlüssel dieses Rechners; liegt auf dem Gerät schon eine Debug-App mit einem anderen Schlüssel, lehnt
Android auch dieses Update ab.

Auf dem Handy die APK persönlich übertragen, in der Dateien-App öffnen und deren
Installationsdialog bestätigen. Falls Android es verlangt, die Installation für
**diese Dateien-App** erlauben; nach der Installation kann diese Erlaubnis wieder
entzogen werden. Das Dokument-/Audiowellen-Icon mit dem Namen SourceScribe starten.
Nicht die Debug-App deinstallieren, wenn deren frühere Testdaten erhalten bleiben sollen.
GitHub enthält weiterhin nur den Preview-Quellstand: öffentliche APK-Verteilung ist
wegen fehlender vollständiger FFmpeg-Quell-/Lizenzbelege blockiert.

Bei englischem Start: **More → App language → Deutsch**. Unter **Mehr** lassen
sich außerdem **System**, **Hell** oder **Dunkel** einstellen. App-Sprache und
Transkriptionssprache sind unabhängig. Ohne Benachrichtigungserlaubnis zeigt der
Verlauf den Fortschritt; Androids Hintergrundregeln gelten weiterhin.

## Neu seit 0.1.0

Zwischen `v0.1.0-preview.1` und dem gebauten Stand `1fe2dad` liegen 108 Commits vom 10. bis 14. September 2026.
Sie setzen deine Rückmeldungen vom 10. September um und enthalten die Korrekturen aus 23 Runden adversarischer Reviews.
Was du beim Testen merken solltest:

- **Auswahl und Begriffe erklärt.** Audiospuren zeigen Codec, Datenrate, Kanäle und Größe statt Formatnummern,
  Fachbegriffe haben Erklärungen, und eine gültige maximale Audiodauer führt nicht mehr in die Meldung, man solle
  einen Wert zwischen 1 und 600 Minuten eingeben (`782aef5`).
- **Warnungen in Worten.** Die Ergebnisansicht sagt, was einem Ergebnis fehlt, statt Codes auszugeben (`029a53f`).
- **Kostenzeile.** Sie zeigt die Summe, gegen die das Budget geprüft wird, mit den Zuschlägen für Sprechertrennung
  und Fachbegriffe und abschnittsweise gerechnet; eine Quelle, die das Längenlimit ablehnt, bekommt keinen Preis; die
  Zeile verschiebt nichts und wird bei großer Schrift nicht abgeschnitten (`c12b7ee`, `f5cc8c5`, `7e0b625`,
  `579f972`; Zuschläge für Fachbegriffe `41d9eb7`, `d6a773c`, `710c64f`, `8833d07`).
- **Eingaben bleiben, wie getippt.** Trennzeichen in Listenfeldern bleiben stehen, schnell getippte Werte in den
  Auftragseinstellungen werden nicht mehr vertauscht, leere Fachbegriffe aus einer gespeicherten Liste blockieren
  keinen Start, und was nach einem Modellwechsel den Start blockiert, bleibt erreichbar (`ab9f36e`, `1b2dc45`,
  `85a2a50`, `0fdf8c0`, `67c2c44`).
- **Weniger Springen.** Die Fehlerzeile der Vorschau, die Wartezeit und der Bytezähler im Verlauf reservieren ihre
  Höhe; bei importiertem Audio fällt die leere Adresszeile weg (`e33ac54`, `5da4530`, `c5a6564`).
- **Teilergebnisse bleiben erkennbar.** SRT- und VTT-Exporte eines unvollständigen Ergebnisses beginnen mit einem
  Hinweis und markieren fehlende Abschnitte, ohne transkribierten Text zu überdecken; Ansicht und gespeicherter
  Eintrag folgen derselben Regel für „vollständig“ (`c3ae9e5`, `ab34dd1`, `4ddca61`).
- **Engine.** Ein Rollback nennt die Version, die er aktiviert; die mit einem App-Update gelieferte yt-dlp-Version
  wird aktiv; volle Engine-Plätze und ein beschädigter Engine-Slot legen die App nicht mehr still; der Plugin-Lader
  von yt-dlp ist in jedem Kindprozess aus (`23d8a0c`, `8e41bf4`, `23aab99`, `89eecad`, `a1a5af1`, `eaba2d4`).
- **Signaturprüfung der Engine-Updates.** Sie nutzt Bouncy Castle 1.86 (`d994c23`, Lizenzhinweise `c523a16`) und
  lehnt eine Signaturdatei ab, in der hinter der Signatur ein zweiter Block oder weiterer Text steht, auch wenn er
  ohne Zeilenumbruch an der Fußzeile hängt (`8097c08`, `20c0689`).

Von deinen 20 Rückmeldungen vom 10. September sind 17 umgesetzt. Offen: das Scrollen in der Ergebnisansicht, dessen
Ursache nie reproduziert wurde, der Platz der unteren Schaltfläche, bei dem unklar ist, was gemeint war, und die
Aussage zur AssemblyAI-Region im Glossar, die nicht gegen die Anbieterdokumentation geprüft ist. Einzelheiten stehen
in [DEFECTS.md](DEFECTS.md) unter „Rückmeldungen aus dem Test vom 10. September 2026“.

Beim Start prüft die App ihre Aufträge, Zugangsdaten und die mitgelieferte Engine. Solange dabei oben ein schmaler
Fortschrittsbalken läuft, sind unter anderem das Prüfen einer Quelle, der Import einer Audiodatei und der Start eines
Auftrags gesperrt, in den Einstellungen auch Sprachwahl, Anbieter und Schlüssel. In der CI dauerte das auf einem
frisch installierten Emulator länger als 25 Sekunden ([DEFECTS](DEFECTS.md), Punkt 57).

## Erster Test ohne STT-Anbieter

1. Unter **Neue Quelle → Beschaffung → Nur YouTube** wählen.
2. Einen öffentlichen YouTube-Einzelvideolink eingeben oder aus YouTube mit
   SourceScribe teilen. **Quelle prüfen** wählen und die Auflösung abwarten.
3. Titel/Quelle und **Untertitelspur** prüfen; dann **Bestätigen und starten**.
4. Unter **Verlauf → Ergebnis öffnen** Herkunft und Text ansehen. Fehlen Captions,
   meldet dieser Modus den Fehler, ohne einen STT-Anbieter aufzurufen.
5. Im Viewer durchsuchen; unter **Aktionen** kopieren/als Datei teilen oder in
   einen selbst gewählten Ordner exportieren. Ohne gewählten Exportordner bleibt das Transkript
   intern erhalten; Export kann später ohne erneute Transkription erfolgen.

## Was enthalten ist

| Bereich | Möglichkeiten |
|---|---|
| Quellen | Explizite YouTube-Einzelvideos per Teilen/Einfügen, mehrere Links mit gemeinsamer Bestätigung, lokale Audiodatei importieren. Ganze Videos; keine automatische Clip-Auswahl durch URL-Zeitmarken. |
| Vier Modi | **Nur YouTube**, **YouTube, sonst STT**, **Nur STT**, **Beides**. Beides erhält zwei getrennte Ergebnisse; ein fehlerhafter Zweig löscht den erfolgreichen nicht. |
| Anbieter | AssemblyAI (US/EU), OpenAI und Groq. Eigene Schlüssel geschützt speichern; Modell und modellabhängige Optionen auswählen. Live-Transkriptionen sind noch nicht durch Entwicklungsagenten geprüft. |
| Expertenoptionen | Caption-Sprachen/-Typen, Übersetzung erlauben, gesonderter Fallback bei Abruffehlern, STT-Sprache, unterstützte Zeit-/Sprecherangaben und Kontextbegriffe; explizite Audio-/Caption-Spuren. |
| Vorgaben | Globale Einstellungen, benannte Presets und Overrides vor Auftragsstart. Bereits laufende Jobs behalten ihren gespeicherten Konfigurationsstand. |
| Grenzen | Audiodauer und lokales Budget, Netzpolitik, 1–4 parallele Jobs, Speicherlimit und Audioaufbewahrung. Budgets sind lokale Schutzgrenzen, keine verbindliche Providerabrechnung. |
| Verlauf | Suche, Phase/Ergebnis/Export getrennt, Abbruch, neue Ausführung, fehlende Arbeit nachholen soweit sicher, anderen Anbieter vorbereiten, Eintrag löschen. AssemblyAI-Remote-Löschung bei vorhandener passender Remote-ID. |
| Ergebnisse | Herkunft, Quelle, Sprache, Modell-/Zeit-/Sprecherdaten soweit vorhanden, Warnungen, Suche auch in langen Transkripten, Kopieren und Android-Dateifreigabe. |
| Export | Markdown, TXT, JSON; SRT/VTT nur mit geeigneten Zeitdaten; ist ein Ergebnis nicht bestätigt vollständig, beginnt die Datei mit einem Hinweis-Cue, und nicht transkribierte Abschnitte stehen als eigene Cues darin. Roh-Captions soweit vorhanden. Fehlgeschlagener Export startet keine neue STT-Anfrage. |
| Wartung | Redigierte Diagnose, geprüfte signierte yt-dlp-/EJS-Updates und Rollback. Python/JS-Runtime/FFmpeg benötigen APK-Updates; laufende Jobs behalten ihre Engine. |

Für deinen eigenen STT-Test zuerst unter **Mehr → Zugangsdaten** Anbieter/Region
und API-Schlüssel einrichten. Danach eine kurze eigene Aufnahme über **Audiodatei
importieren** wählen, Modell und Optionen prüfen und bewusst starten. Das umgeht
YouTube als zusätzliche Fehlerquelle. Anschließend YouTube-Audio und Beides testen.
Ein ungewisser Übermittlungsstatus bedeutet: möglicherweise bereits beim Anbieter
angenommen/berechnet; nicht blind neu starten. Ein lokal abgebrochener Remotejob
kann beim Anbieter weiterlaufen.

## TalkBack und bekannte Grenzen

TalkBack ist Googles Android-Screenreader: Er liest Oberflächenelemente vor und
ermöglicht Bedienung ohne den Bildschirm anzusehen. SourceScribe installiert ihn
nicht. Die App stellt zugängliche Beschriftungen und Bedienrollen bereit. Die
frühere unerwartete Sprachausgabe kam von bewusst aktivierten Accessibility-Tests
im Emulator; der Dienst ist jetzt aus. Ohne Bedarf musst du ihn nicht einschalten.
[Offizielle Erklärung](https://support.google.com/accessibility/android/answer/6283677?hl=de).

Noch offen: echte Transkription bei allen drei Anbietern, physisches ARM64,
vollständige TalkBack-Bedienung, die Signaturprüfung der Engine-Updates auf Android vor API 37 ([DEFECTS](DEFECTS.md),
Punkt 55) sowie öffentliche APK-Quell-/Lizenzbelege. Der Caption-Kernpfad wurde für 0.1.0 real geprüft; für 0.2.0
liefen die Live-Tests nicht, Providerfehler sind weiterhin kontrolliert mit Fixtures geprüft.
Keine Zusammenfassungs-API, kein Web-Frontend, kein eigener Backend-Dienst und
keine automatische Ablage in einem bestimmten ChatGPT-Projekt.

## Nützliches Feedback

Bitte Gerät/Android-Version, App-Version, Modus/Anbieter/Modell, genaue Schritte
und erwartetes/tatsächliches Ergebnis nennen. Ein Screenshot zeigt Abstände und
Ausrichtung oft besser als eine Beschreibung. Keine API-Schlüssel mitsenden;
private Textstellen bei Bedarf schwärzen. Bei Fehlern helfen Zeitpunkt und der
angezeigte technische Status. Bekannte Restarbeiten stehen in [NEXT_STEPS](NEXT_STEPS.md), bekannte Fehler nach
Priorität in [BUGS](BUGS.md).

Das vorhandene adaptive Icon wird nativ aus Vektorpfaden gerendert; hier eine
256-Pixel-Vorschau mit abgerundeter Launcher-Maske, keine neue KI-Grafik:

![Dokument mit Audiowellenform auf dunkelgrünem Hintergrund](reports/screenshots/icon-preview.png)
