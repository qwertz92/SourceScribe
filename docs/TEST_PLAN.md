# Test- und Abnahmeplan

## T0 — Nachweisformat

Jeder Fall erhält `PASS`, `FAIL`, `BLOCKED` oder `NOT_RUN` sowie Prüfebene, Datum, Commit, tatsächlichen Befehl/Ablauf, Gerät/API/ABI/Seitengröße, Ergebnis und bereinigte Evidenz. Ausführungsnachweise unter einem dokumentierten Testbericht sammeln; private Logs nicht automatisch committen. Beim ursprünglichen Übergabepaket waren sämtliche App-Tests `NOT_RUN`. Der aktuelle Nachweisstand steht in [STATUS](STATUS.md) und den dort verlinkten Testberichten.

Ebenen unterscheiden: JVM-Unit, Provider-/Extractor-Contract mit Fixtures, Android-Instrumentation, reale öffentliche Quelle, echte kostenrelevante Provider-API und physisches Gerät. Eine simulierte Updateprüfung ist wertvoll, aber kein Nachweis, dass ein echtes Upstream-Release auf Android aktiviert werden konnte.

## T1 — Deterministische Pflichtfälle

| ID | Anforderung | Gegenprobe und erwartetes Ergebnis |
|---|---|---|
| T01 | SS-01 | watch/youtu.be/shorts, Trackparameter, Tracking, Video+Playlist: dieselbe explizite ID; reine Playlist abgewiesen |
| T02 | SS-01/10 | Lookalike-Hosts, Userinfo, Encoding, Shell-/Dateinamenpayloads: keine fremde Quelle oder Befehlsausführung |
| T03 | SS-01 | Metadaten/Download liefern andere Video-ID: harter Fehler vor Speicherung/Upload |
| T04 | SS-02 | Vier Modi × Captions vorhanden/fehlend/temporär nicht abrufbar: genau erlaubte Schritte, keine stillen Uploads |
| T05 | SS-02/11 | Caption-429/Parserfehler nicht als „existiert nicht“ behandeln; Standard wartet/fragt statt STT zu bezahlen |
| T06 | SS-03 | Globale Einstellungen nach Start ändern: laufender Konfigurationssnapshot bleibt gleich |
| T07 | SS-03/07 | Mehrsprachige, uploader-/auto-/übersetzte und unbekannte Tracks: richtige Auswahl und ehrliche Herkunft |
| T08 | SS-03/07 | Mehrere Audiospuren/Autodub: explizite Trackwahl, keine stillschweigend falsche Originalsprache |
| T09 | SS-07 | Roll-up, Entities, mehrzeilige VTT/SRT, Unicode, echte Wiederholungen, Zahlen/Negationen: Inhalt bleibt erhalten |
| T10 | SS-04 | Jeder Adapter: Erfolg, 401/403, 429, Timeout, 5xx, ungültiges JSON, fehlender Text, nicht unterstützte Optionen |
| T11 | SS-04/06 | AssemblyAI-Remote-ID über Prozessneustart rekonstruieren; keine erneute Submission beim Polling |
| T12 | SS-04/11 | Timeout nach möglicher Annahme: SUBMISSION_UNCERTAIN; keine automatische doppelte kostenrelevante Anfrage |
| T13 | SS-04/07 | Größen-/Dauergrenze vor Upload; Chunkoffsets/Overlaps; fehlender Chunk = unvollständig; kein Cross-Chunk-Sprecherphantom |
| T14 | SS-05 | Job A scheitert/abgebrochen, Job B bleibt unbeeinflusst; Ressourcenlimit tatsächlich eingehalten |
| T15 | SS-05 | Doppelte Share-Intents/Jobclaims erzeugen keinen stillen zweiten kostenpflichtigen Auftrag |
| T16 | SS-05/07 | BOTH: ein Zweig erfolgreich, einer fehlend/defekt; PARTIAL_SUCCESS, getrennte Artefakte, gezielte Wiederholung |
| T17 | SS-08 | Erfolgreiches internes Ergebnis + entzogene SAF-Berechtigung: Transkript bleibt da; Exportretry ohne STT |
| T18 | SS-08 | Disk full, Schreibabbruch, doppelte Namen, nicht unterstütztes Rename, Datei extern gelöscht: ehrlicher Exportzustand |
| T19 | SS-08 | Absturz zwischen temporärer Datei, Finalisierung und Room-Commit: Reconciliation ohne Ergebnisverlust/Neusubmission |
| T20 | SS-08 | Bereinigung und Audioaufbewahrung: aktive Dateien/Transkripte/Geschwisterartefakte bleiben erhalten |
| T21 | SS-09 | Große Transkripte suchen/anzeigen/teilen; kein OOM/ANR, kein vollständiges Audio-ByteArray |
| T22 | SS-07/09 | Keine Zeit-/Sprecherdaten: keine erfundenen Labels, SRT/VTT passend gesperrt; TXT/MD weiterhin möglich |
| T23 | SS-10 | Canary-API-Key taucht weder in Logs, DB-Klartext, exportierter Diagnose, Backupregeln noch WorkManager Data auf |
| T24 | SS-10 | Update mit falscher Signatur/Hash, ZIP-Traversal, Dekompressionsbombe, falscher Runtime: keinerlei Ausführung |
| T25 | SS-10 | Abbruch/Disk full bei Update: aktuelle Engine bleibt nutzbar; Crash-Recovery; Rollback funktioniert |
| T26 | SS-10 | Update während zweier Jobs: Versionspinning, keine teilweise ersetzten Dateien oder gemischten EJS-Versionen |
| T27 | SS-10 | Offline/private Quelle/429 führt nicht zu reflexivem Update; Update-Retry höchstens gemäß Richtlinie |
| T28 | SS-11 | Geschätzte Preise/Limits unbekannt oder veraltet: sichtbar; Organisationskontingent nicht als lokales Guthaben darstellen |
| T29 | SS-10/11 | HTTP-Redirect/Retry: kein Secret an fremden Host, keine unkontrollierte Wiederholung einer Submission |
| T30 | SS-12 | Deutsch/Englisch-App-Sprachwechsel, System/hell/dunkel, 200-%-Schrift, TalkBack, lange Titel, Rotation, kleine/große Displays und verweigerte Notifications; Screenshots der Ansichten kritisch auf Abstände, Ausrichtung und Proportionen prüfen |
| T31 | SS-04/10 | Fixture-Provider, Test-HTTP und Debug-Aktionen im persönlichen Release-Build nicht erreichbar |
| T32 | SS-05/08 | Room-Migration/Upgrade erhält Verlauf und Artefaktbezüge; kein destruktiver Fallback ohne Auftrag |
| T33 | SS-08/09 | Lokaler Audioimport über content-URI, temporärer Grant und Prozessneustart: kontrollierte interne Kopie/Permission |
| T34 | SS-07/10 | Bösartige Caption-/Markdown-Anweisungen bleiben Daten; keine Ausführung, externe Weitergabe oder automatischer Toolaufruf |

Provider-Contracts mit einem kontrollierten HTTP-Testserver prüfen. Fehler nicht absichtlich gegen echte Provider provozieren, wenn Fixtures dasselbe deterministisch testen können. Freigegebene kleine Audiofixtures selbst erzeugen oder klar lizenziert beschaffen; nie fremde vertrauliche Audiodaten als öffentliche Fixtures ablegen. Streaming- und abgebrochene Antworten separat testen, wenn der Adapter sie unterstützt.

## T2 — Verbindliche ADB-/UI-Prüfungen

Umgebung tatsächlich ermitteln, beispielsweise mit `adb devices -l`, API-/ABI-Abfragen und `adb shell getconf PAGE_SIZE`. Versionsdaten im Bericht notieren. Native Funktionen testen, nicht nur „App startet“. Primär das vom Nutzer bereitgestellte System verwenden; ergänzende API-/16-KB-Tests bei verfügbarer Infrastruktur. Fehlt eine Kombination, nicht als geprüft aufführen.

Ablauf: frische Installation → Onboarding → Ordnerwahl → Share Intent mit echtem zulässigem YouTube-Link → Metadaten prüfen → Nur-YouTube-Transkript → Viewer → externe Datei lesen und Herkunft vergleichen → Teilen. Zusätzlich eine lokale Audiodatei importieren und den ausgewählten Providerpfad mit erlaubten Credentials ausführen.

Alle vier Modi einschließlich fehlender Captions und BOTH-Teilerfolg ausführen. Deterministische No-caption-/Fehlerfälle über testexklusive Adapter/HTTP-Fixtures; mindestens einen echten Capture- und Audio-Pfad separat nachweisen. Verfügbare öffentliche Videos können sich ändern; Source-ID und Zeitpunkt aufzeichnen, keine unveränderliche Untertitelverfügbarkeit voraussetzen.

Mindestens zwei gleichzeitige Jobs, Abbruch eines Jobs und Weiterlauf des anderen. App in Hintergrund, Display aus, Navigation, Rotation, normale Prozessbeendigung, Reboot und ausdrücklichen Force-Stop getrennt prüfen. Force-Stop soll keinen magischen Fortlauf ergeben; beim nächsten Öffnen korrekten Abgleich beweisen. Unterbrechung während Download, Upload, Remote-Warten, interner Ergebnissicherung und Export prüfen.

Flugmodus/Netzwechsel, Rate-Limit, abgelaufene URL, entzogene Ordnerberechtigung und Speicherfehler kontrolliert auslösen. Akku-/OS-Einschränkungen dokumentieren, statt alle Tests nur im dauerhaft sichtbaren Vordergrund auszuführen. Logcat auf Crash, ANR und Secret-Leaks prüfen. Screenshots ergänzen die funktionale Prüfung, ersetzen sie nicht. Für Compose nutzen wir das gelesene
[Prüfprotokoll von Chris Banes](https://github.com/chrisbanes/skills/blob/main/skills/compose-ui-testing-patterns/SKILL.md)
(geprüft am 8. September 2026): Semantiktests für Verhalten, tatsächliche Screenshots
für Abstand, Ausrichtung, Clipping, Farbe und Schrift. Daten für diese Layoutfälle
sind deterministisch; echte Quellenprüfungen bleiben separat. Keine zusätzliche
Screenshot-Testplattform allein zur Anwendung dieser Checkliste. Hörbare
TalkBack-Tests vorher ankündigen und temporäre Accessibility-Einstellungen danach
auf den zuvor gemessenen Zustand zurücksetzen.

## T3 — Echte Provider- und Update-Nachweise

Echte STT-Aufrufe brauchen vom Nutzer für diesen Zweck freigegebene Credentials, Inhalte und einen Kostenrahmen. Default des Testbudgets ist **keine kostenpflichtigen Aufrufe ohne Freigabe**. Keine Account-Upgrades oder automatische Guthabenaufladung. Vor Testbeginn Schätzung und Anzahl geplanter Requests festlegen; Abbruchschwelle beachten. Zugangsdaten nicht als CLI-Argumente oder in Screenshot-Testskripte schreiben.

Für jeden verfügbaren Provider einen kurzen End-to-End-Test: Quelle/Datei → Vorbereitung → Submission → vollständiges Ergebnis → interne Persistierung → exportierte Datei. Laufzeit, tatsächlich verwendetes Modell soweit gemeldet, Anbieter-ID redigiert und Format prüfen. Die „alle Provider live getestet“-Freigabe bleibt blockiert, wenn nur ein Provider verfügbar war, selbst wenn alle Adapter mit Fixtures bestanden haben.

Updater: sowohl deterministische Schad-/Fehlerszenarien als auch echten verifizierten Release-Artefaktbezug, Aktivierung und Rückkehr zur vorherigen/bundled Engine testen. Bei fehlendem neuen Release einen geeigneten erlaubten Versionswechsel in einer separaten Testinstallation nutzen. Ein identischer Versionsstring mit gefälschter Erfolgsmeldung ist kein Update-Test. Keine absichtlich verwundbare Engine für einen Live-Netztest aktivieren.

## T4 — Adversariale Review-Pässe

Drei voneinander unabhängige Sichtweisen genügen als Ausgangspunkt: (1) Lebenszyklus/Persistenz/Kosten, (2) Sicherheit/Updater/Eingaben, (3) Datenqualität/Providerverträge/UX. Keine feste Anzahl „Superagenten“ als Qualitätsersatz. Echte Subagenten verwenden, soweit verfügbar; sonst getrennte Review-Pässe offen benennen.

Reviewer erhalten Anforderungen, Code und Testberichte, nicht nur eine Selbstbewertung des Implementierers. Findings mit reproduzierbarer Evidenz und Schweregrad. Der Integrator prüft, behebt, ergänzt Regression und lässt nachtesten. Ein zweiter abschließender Pass nach den Reparaturen ist Pflicht. Korrekturen nicht nur durch denselben Test bestätigen, der den Fehler ursprünglich übersehen hat.

## T5 — CI und Release-Abnahme

CI ohne Provider-Secrets: Gradle-Wrapper-/Dependency-Verifikation, Lint, Unit-/Contract-Tests, Debug-APK und sinnvoller statischer Check. GitHub Actions auf geprüfte unveränderliche Referenzen pinnen; geringste Tokenrechte. Untrusted Pull-Requests erhalten keine Signing-/Provider-Secrets. Room-Schemas/Migrationsprüfungen und grundlegender Secret-Scan gehören dazu.

Genaue Befehle erst nach Projektaufbau eintragen und tatsächlich ausführen; kein unzutreffender Copy-paste-Befehl mit fiktivem Modulnamen als Nachweis. Instrumentation bei verfügbarer CI-Infrastruktur; ADB-Live-Nachweise zusätzlich. Release-Build getrennt auf fehlende Debug-Hintertüren, Manifestberechtigungen, Bibliotheks-/APK-Ausrichtung und richtige Signatur prüfen.

Abnahme: vollständiger v1-Umfang, alle zugehörigen Pflichtfälle bestanden, keine offenen kritischen/hohen Fehler und keine offene Pflichtverletzung. Niedrige bekannte Risiken dokumentieren. Externe Blocker erlauben eine ehrliche Preview, nicht eine falsche vollständige Freigabe. Keine Behauptung „keine Fehler vorhanden“; stattdessen geprüfte Szenarien und Grenzen benennen.
