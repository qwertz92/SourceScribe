# SourceScribe

**Stand:** 8. September 2026 · **Status:** Entwicklung, auf Nutzerwunsch pausiert

SourceScribe ist eine persönlich genutzte Android-App für nachvollziehbare
Transkripte. Eine ausdrücklich eingegebene YouTube-Quelle lässt sich teilen oder
einfügen; vorhandene Untertitel werden gesichert und Audio kann über den gewählten
Provider transkribiert werden. Ergebnisse bleiben zunächst intern und können als
Datei über das Android-Share-Sheet weitergegeben werden. Deutsche Zusammenfassung
und Faktencheck bleiben außerhalb der App.

## Aktueller Stand

Das öffentliche Repository ist [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe).
Debug-App, aktuelles Instrumentation-APK und unsigned Release sind gebaut.
103 JVM-Tests bestanden zuletzt; der aktuelle Build r57 besteht einschließlich
vollständigem App-Lint Debug/Release und Extractor-Lint Release. Echte Android-
Caption-/Audioextraktion sowie der Caption-App-Pfad bis SAF-Export wurden geprüft.
Die erweiterten App-Gerätetests, UI-Überarbeitung und vollständige v1-Abnahme
bleiben offen. Der genaue [Wiederaufnahmestand](docs/HANDOFF.md) ist gesichert.

Live-Provideraufrufe haben keine freigegebenen Zugangsdaten. Ein physisches
ARM64-Gerät ist derzeit nicht verfügbar. Eine öffentliche APK-Freigabe bleibt
wegen der dokumentierten offenen Native-Lizenz- und Corresponding-Source-Belege
gesperrt; eigene Quelltexte und geprüfte Begleitdateien sind davon getrennt.

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
