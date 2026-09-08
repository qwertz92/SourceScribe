# SourceScribe

**Stand:** 8. September 2026 · Arbeit auf Nutzerwunsch pausiert · **Status:** Persönliche Preview; vollständige v1-Abnahme blockiert

SourceScribe ist eine persönlich genutzte Android-App für nachvollziehbare
Transkripte. Eine ausdrücklich eingegebene YouTube-Quelle lässt sich teilen oder
einfügen; vorhandene Untertitel werden gesichert und Audio kann über den gewählten
Provider transkribiert werden. Ergebnisse bleiben zunächst intern und können als
Datei über das Android-Share-Sheet weitergegeben werden. Deutsche Zusammenfassung
und Faktencheck bleiben außerhalb der App.

## Aktueller Stand

Das öffentliche Repository ist [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe).
Debug-App, Instrumentation-APK und unsignierte Release-APK sind gebaut. Bestanden
sind 103 JVM-Tests und zuletzt 180 Android-App-Prüfungen; fünf opt-in-Fälle wurden
im Gesamtlauf übersprungen und separat ausgeführt. Echte Android-Caption-/
Audioextraktion und der Caption-App-Pfad bis SAF-Export sind auf dem
API-37-/x86_64-/16-KB-Emulator nachgewiesen. Deutsch/Englisch, System/Hell/Dunkel,
überarbeitete Auswahlfelder und die Freigabe durch bewussten Auftragsstart sind
integriert. Die letzten kleinen History-Anpassungen nach dem r77-Gerätelauf
sind noch nicht erneut auf dem Gerät geprüft. Ein signiertes Engineupdate während zweier laufender Fixturejobs ist
bestanden. [Preview-Prüfbericht](docs/reports/2026-09-08-preview.md) und
[STATUS](docs/STATUS.md) trennen Implementierung, Fixtures und reale Nachweise.

Echte Transkriptionen mit AssemblyAI, OpenAI und Groq bleiben mangels freigegebener
Zugangsdaten, Testinhalte und Kostenrahmen blockiert. Ein physisches ARM64-Gerät
fehlt; vollständige TalkBack-Bedienung ist mit der verfügbaren Eingabeautomation
nicht nachgewiesen. Der Release-Build ist unsigniert. Öffentliche APK-Verteilung
bleibt wegen fehlender vollständiger FFmpeg-Lizenz-/Quellbelege gesperrt.

Die CI startet inzwischen den Emulator, scheitert aber noch im Gerätetest mit
unbekannter Einzelursache. Der nächste gezielte Diagnoseweg ist in
[HANDOFF](docs/HANDOFF.md) gesichert; kein weiterer Lauf während der Pause.

Die maßgeblichen Nachweise werden fortgeschrieben:

- [Tatsächlicher Projektstatus](docs/STATUS.md)
- [Build und persönliche Auslieferung](docs/BUILD.md)
- [Build-/P0-Prüfbericht](docs/reports/2026-09-07-build-and-p0.md)
- [Lizenz- und Provenienzprüfung](docs/reports/2026-09-07-licenses.md)

## Einstieg

Der [Dokumentationsindex](docs/INDEX.md) führt zu Produktumfang, Architektur,
Sicherheitsgrenzen, Integrationsverträgen, Roadmap und Testplan. Vor Änderungen
bitte [AGENTS.md](AGENTS.md) lesen.

Der lokale Build-Einstieg steht in [docs/BUILD.md](docs/BUILD.md). Er erzeugt
keine Provideraufrufe und installiert nichts auf einem Gerät. ADB-, Provider- und
weitere Live-Nachweise werden in den Berichten mit ihrer tatsächlichen Ebene als
`PASS`, `BLOCKED` oder `NOT_RUN` geführt.

## Technische Leitlinien

SourceScribe ist eine native Kotlin-/Jetpack-Compose-App ohne eigenen Server und
ohne zusätzliche LLM-API für Zusammenfassungen. Quellen, Konfigurationen,
Verarbeitungszustände und Exporte haben getrennte Verträge. API-Schlüssel,
temporäre Audiodaten und vollständige Transkripte gehören weder in Git noch in
Logs, Diagnoseexporte oder Testberichte.
