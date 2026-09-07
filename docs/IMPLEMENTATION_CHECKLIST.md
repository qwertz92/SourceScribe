# Implementierungs- und Nachweischeckliste

Stand: 7. September 2026. Endzustände je Arbeitspaket: `complete`, `no_change`,
`blocked`, `conflict`. Bis zum Nachweis bleibt ein Paket offen. Die Prüflabels
IMPLEMENTED / TESTED_WITH_FIXTURES / LIVE_VERIFIED sind keine austauschbaren Begriffe.

| Paket | Abnahme/Evidenz | Integrationsverantwortung | Zustand |
|---|---|---|---|
| Wiki | Geprüftes Konzept; gemeinsamer Index; Claude-Brücke | Hauptagent | complete |
| Umgebung/Git | Dateien erhalten, Toolchain/Netz/ADB gemessen, Public-Repo und Commits | Hauptagent | complete |
| P0 Runtime | Android Python/JS/EJS/FFmpeg, TLS, Metadaten/Caption/Audio, ABI/16 KB | Hauptagent | offen |
| P0 Updates | Android-Nightly 2026.08.30.232658 aktiviert und auf 2026.08.19 zurückgesetzt; Schadfixtures und Lint bestanden | Hauptagent | complete |
| P1 | Share/Paste, Caption, Herkunft, intern sichern, MD/SAF, Viewer/Verlauf | Hauptagent | offen |
| P2 | Groq, lokaler Import, Vorbereitung/Chunks, Credential-Schutz | Hauptagent | offen |
| P3 | AssemblyAI/OpenAI, alle Modi, Optionen/Tracks/Presets | Hauptagent | offen |
| P4 | Queue, Limits, Recovery, Submission-Unsicherheit, Exportreparatur, Updates | Hauptagent | offen |
| P5 | Deutsche Compose-UI, Suche/Share/Formate, Settings, Diagnose, Signing/CI | Hauptagent (Astra für UI) | offen |
| P6 | T01–T34, ADB-Fälle, unabhängige Reviews, Regression, Abschlussbericht | Hauptagent + unabhängige Reviewer | offen |
| Live-Provider | Freigegebene Credentials, Dateien und Kostenrahmen je Provider | Nutzerfreigabe fehlt | blocked |
| Physisches ARM64 | Gerätelauf zusätzlich zum statischen ABI-Nachweis | Gerät nicht angeschlossen | blocked |

## Ausgangsmessung

- Nur Übergabedokumente, kein vorhandener App-Code; lokales Git auf `main` initialisiert.
- GitHub-Zugang als qwertz92 verfügbar; `qwertz92/SourceScribe` anfangs nicht vorhanden.
- ADB über vorhandene Windows-SDK-Installation: `emulator-5554`, Android 17/API 37,
  `sdk_gphone16k_x86_64`, ABI-Liste `x86_64,arm64-v8a`, `PAGE_SIZE=16384`.
  ARM64 in der Liste belegt keine native physische ARM64-Ausführung.
- WSL: OpenJDK 17.0.20.1; Gradle 9.6.0 erfolgreich gestartet. Lokaler isolierter
  SDK-/Gradle-Cache zunächst unter `/tmp`; nach dem belegten Speicherfehler
  nach `.local-tools/` auf das Projektlaufwerk verlegt. Keine globalen Sicherheitseinstellungen geändert.
