# Implementierungs- und Nachweischeckliste

Stand: 8. September 2026. **PAUSIERT auf Nutzerwunsch.** Endzustände je Arbeitspaket: `complete`, `no_change`,
`blocked`, `conflict`. Bis zum Nachweis bleibt ein Paket offen. Die Prüflabels
IMPLEMENTED / TESTED_WITH_FIXTURES / LIVE_VERIFIED sind keine austauschbaren Begriffe.

| Paket | Abnahme/Evidenz | Integrationsverantwortung | Zustand |
|---|---|---|---|
| Wiki | Gemeinsamer Index statt Anforderungsduplikat; Claude-Brücke | Hauptagent | complete |
| Umgebung/Git | Toolchain/Netz/ADB gemessen, Daten erhalten, öffentliches main/Commits | Hauptagent | complete |
| P0 Runtime x86_64 | Echte Android-Python-/JS-/EJS-/FFmpeg-/TLS-/YouTube-Prüfung r48, 16 KB | Hauptagent | complete |
| P0 ARM64 statisch | Beide ABIs gebaut; aktuelles APK einschließlich 528 innerer ELF-Dateien geprüft | Hauptagent | complete |
| P0 ARM64 physisch | Keine angeschlossene physische ARM64-Hardware | Nutzergerät fehlt | blocked |
| P0 Updates | Signiertes Nightly aktiviert und zurückgerollt; Schad-/Fehlerfixtures; r70 zwei laufende Fixturejobs gepinnt | Hauptagent | complete |
| P1 | Echter Share → Caption → Herkunft → Room → MD/SAF → Viewer/Teilen | Hauptagent | complete |
| P2 Implementierung/Fixtures | Groq, Import, Vorbereitung/Chunks, Credentials, Submissiongrenzen | Hauptagent | complete |
| P3 Implementierung/Fixtures | AssemblyAI/OpenAI, vier Modi, Tracks/Optionen/Presets, Teilfehler | Hauptagent | complete |
| P4 Implementierung/Fixtures | Queue/Limits, Recovery/Unsicherheit, Exportreparatur, Updategrenzen; Gesamtlauf r77 | Hauptagent | complete |
| P5 UI-Feedback | Feld-/Buttonabstände, de/en, System/Hell/Dunkel, Startfreigabe ohne allgemeinen Uploadschalter, tatsächliche Screenshotkritik | Hauptagent/Astra | complete |
| P5 Viewer/Export | 10.000 Segmente, Suche/Share/Kopierauswahl, keine erfundenen Zeitformate; SAF-Fehlerregressionen | Hauptagent | complete |
| P5 TalkBack | Semantik und Dialogaktivierung geprüft; vollständige TalkBack-Traversierung mit verfügbarer Eingabeautomation nicht möglich | Toolinggrenze | blocked |
| P5 Signing | Signierskript mit temporärem Testkey geprüft; kein dauerhafter persönlicher Key vorhanden | Persönlicher Schlüssel fehlt | blocked |
| P5 CI | Run 34252821287: Build/JVM/Lint und Emulatorstart PASS; connectedDebugAndroidTest FAIL, Einzelursache noch unbekannt. Diagnoseausgabe vorbereitet, neuer Lauf pausiert | Hauptagent | offen |
| Letzte History-Anzeige r80 | Build/Lint PASS; neuer Geräte-/Screenshotlauf und finaler Releaseaudit wegen Pause NOT_RUN | Hauptagent | offen |
| P6 lokale Regression/Reviews | 103 JVM + 180 ausgeführte Android-App-Tests; unabhängige Reviews und dokumentierte Regressionen | Hauptagent + Reviewer | complete |
| P6 vollständige Abnahme | Pflichtnachweise wegen untenstehender externer Grenzen unvollständig | Hauptagent | blocked |
| Live-Provider | Kein freigegebener Testzugang/Inhalt/Kostenrahmen für AssemblyAI/OpenAI/Groq | Nutzerfreigabe fehlt | blocked |
| Öffentliche APK | Vollständige FFmpeg-Lizenz-/Corresponding-Source-Zuordnung fehlt | Native-Provenienzgrenze | blocked |

`complete` bei einem Fixturepaket ist keine Live-Providerfreigabe. Die Phasen mit
blockierten Pflichtteilen sind als Gesamtphase nicht vollständig abgenommen.
Aktuelle Rohbelegnamen, APK-Hashes und Prüfebenen im
[Preview-Prüfbericht](reports/2026-09-08-preview.md).

## Ausgangsmessung

- Nur Übergabedokumente, kein vorhandener App-Code; lokales Git auf `main` initialisiert.
- GitHub-Zugang als qwertz92 verfügbar; `qwertz92/SourceScribe` anfangs nicht vorhanden.
- ADB über vorhandene Windows-SDK-Installation: `emulator-5554`, Android 17/API 37,
  `sdk_gphone16k_x86_64`, ABI-Liste `x86_64,arm64-v8a`, `PAGE_SIZE=16384`.
  ARM64 in der Liste belegt keine native physische ARM64-Ausführung.
- WSL: OpenJDK 17.0.20.1; Gradle 9.6.0 erfolgreich gestartet. Lokaler isolierter
  SDK-/Gradle-Cache zunächst unter `/tmp`; nach dem belegten Speicherfehler
  nach `.local-tools/` auf das Projektlaufwerk verlegt. Keine globalen Sicherheitseinstellungen geändert.
