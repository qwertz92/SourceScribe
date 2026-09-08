# Tatsächlicher Projektstatus

**Stand:** 8. September 2026. **Arbeit PAUSIERT auf Nutzerwunsch.** **Freigabe:** Persönliche Preview; vollständige v1
weiterhin blockiert. App-Quellstand `b631bbe`, CI-Diagnose `f9d4f8b`, lokales `main` und öffentliches
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
Debug-/AndroidTest-/unsigned Release-APKs gebaut; alle vier aktuellen Lintberichte
haben null Issues. Echte Android-Extraktion und Caption-App-Durchstich auf dem
API-37-/x86_64-/16-KB-Emulator nachgewiesen. Keine echte STT-API aufgerufen.

| Phase | Implementierung und Fixtures | Reale Nachweise / offene Freigabe |
|---|---|---|
| P0 | IMPLEMENTED / TESTED_WITH_FIXTURES: Runtime, Verifier, Manager; WebP für beide ABIs mit 16-KB-Ausrichtung neu gebaut | LIVE_VERIFIED: Python/TLS, JS/EJS, FFmpeg, exakte YouTube-Metadaten/Caption/Audio, Update/Rollback. Physisches ARM64 BLOCKED. |
| P1 | IMPLEMENTED / TESTED_WITH_FIXTURES: URL/Planner, Caption/Provenienz, Room, Export/Viewer | LIVE_VERIFIED: Android Share → echte Caption → intern → SAF-Markdown mit Inhaltsvergleich → Teilen-Dialog. |
| P2 | IMPLEMENTED / TESTED_WITH_FIXTURES: Groq, lokaler Import, Audiovorbereitung/Chunks, Credentials, Submissiongrenzen | Echte Groq-Transkription BLOCKED: keine freigegebenen Testzugänge/Inhalte/Kosten. |
| P3 | IMPLEMENTED / TESTED_WITH_FIXTURES: AssemblyAI, OpenAI, alle Modi, BOTH-Teilfehler, Sprache/Tracks/Optionen/Presets | Echte AssemblyAI-/OpenAI-Transkription BLOCKED; kein Mock als Provider-PASS. |
| P4 | IMPLEMENTED / TESTED_WITH_FIXTURES: Queue/Limits, Recovery, unsichere Submissions, Exportreparatur, Updatefehler | LIVE_VERIFIED: Android-Prozess-/Grantgrenzen, Reboot-Recovery mit Providerfixture, echtes signiertes Update/Rollback. T26 mit zwei laufenden Fixturejobs PASS r70. |
| P5 | IMPLEMENTED: de/en-App-Sprache, System/Hell/Dunkel, überarbeitete Auswahlfelder/Navigation, Viewer/Suche/Kopieren/Share, Formate/Diagnose/Signierpfad | ADB-/Screenshotprüfungen einschließlich 200-%-Schrift und Querformat bestanden. Vollständige TalkBack-Bedienung BLOCKED; dauerhafte persönliche Release-Signatur NOT_RUN. |
| P6 | Integrierte Regression und unabhängige Reviews ausgeführt; bestätigte Defekte samt Regression behoben | Vollständige Abnahme BLOCKED: Provider, physisches ARM64, TalkBack und öffentliche APK-Lizenz-/Quellbelege fehlen. |

## UI-Feedback umgesetzt

Auswahlfelder haben abgestimmte Label-/Wertabstände und reservieren auch bei großer
Schrift genug Platz für die längste Option. Untere Navigation mit „Mehr“/„More“
bleibt lesbar. App-Sprache Deutsch/Englisch ist unabhängig von ASR und Theme.
Der separate allgemeine Audioübermittlungs-Schalter ist entfernt; bewusste
Providerwahl plus Auftragsstart erzeugen die intern an Quelle/Konfiguration
gebundene Freigabe. Dies erlaubt keine Live-Tests durch Entwicklungsagenten.

## Artefakte und Grenzen

Neu gebaut, noch nicht installiert/erneut gerätegeprüft: Debug-APK r80: `app/build/outputs/apk/debug/app-debug.apk` (159.065.550 Bytes).
SHA-256: `ad94d85da0b1fc3cf3bbc885b3f3fa2857806448d4e20524a3c5971e3a887b32`.
Release-APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, unsigniert.
Genaue Build-/Testbefehle, APK-Hashes, Versionen und Rohbelegnamen im
[Preview-Prüfbericht](reports/2026-09-08-preview.md), chronologische Fehlversuche
und Reviewkorrekturen im [Integrationsbericht](reports/2026-09-07-integration.md).

Öffentliche APK-Verteilung bleibt wegen fehlender vollständiger
FFmpeg-Corresponding-Source-/Lizenzzuordnung gesperrt. Kein Release veröffentlicht.
Die GitHub-CI hat Build/JVM/Lint bestanden und den Emulator erfolgreich gestartet.
Run 34252821287 scheiterte anschließend im Gerätetest; der konkrete Einzelfehler
ist noch unbekannt. Eine gezielte Berichtsausgabe ist vorbereitet, aber wegen
Pause noch nicht in CI ausgeführt. Die abschließenden kleinen UI-Änderungen r80
erhalten erst bei Fortsetzung einen neuen Geräte-/Screenshot-/Releaseaudit.

Nach dem belegten WSL-Speicherfehler sind 16 GiB Swap aktiv. Builds verwenden einen
Worker, 2 GiB Java-Heap und projektlokale Caches. Keine neue globale Konfiguration.
[Diagnose und Speicherbegrenzung](reports/2026-09-07-wsl-recovery.md).

Die aktuelle Pause und der konkrete nächste Prüfschritt stehen ganz oben in
[HANDOFF](HANDOFF.md). Keine automatische Fortsetzung oder weitere Tests.
Bereits erledigte Implementierung und Fixtureprüfungen bleiben erhalten.
