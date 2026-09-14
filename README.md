# SourceScribe

**Stand:** 14. September 2026 · Persönliche Preview 0.2.0-preview.1; Reviewschleife angehalten, bekannte Fehler in [BUGS](docs/BUGS.md) · **Status:** Persönliche Preview; vollständige v1-Abnahme blockiert

SourceScribe ist eine persönlich genutzte Android-App für nachvollziehbare
Transkripte. Eine ausdrücklich eingegebene YouTube-Quelle lässt sich teilen oder
einfügen; vorhandene Untertitel werden gesichert und Audio kann über den gewählten
Provider transkribiert werden. Ergebnisse bleiben zunächst intern und können als
Datei über das Android-Share-Sheet weitergegeben werden. Deutsche Zusammenfassung
und Faktencheck bleiben außerhalb der App.

## Aktueller Stand

Das öffentliche Repository ist [qwertz92/SourceScribe](https://github.com/qwertz92/SourceScribe).
Für die Preview 0.2.0-preview.1 sind am Stand `1fe2dad` Debug-App, beide Test-APKs und die unsignierte
Release-APK gebaut; signiert wird sie vom Nutzer mit dem Schlüssel von 0.1.0.
[Einstieg zum Testen](docs/TRY_PREVIEW.md) · [Preview-Quellstand](https://github.com/qwertz92/SourceScribe/releases/tag/v0.2.0-preview.1) · [Prüfbericht](docs/reports/2026-09-14-preview-0.2.md).
An diesem Stand bestanden 187 JVM-Tests, im Modul `app` 202 von 208 Instrumentierungstests und im Modul
`extractor` 46 von 50, auf dem API-37-/x86_64-/16-KB-Emulator `emulator-5556`. Die übrigen zehn sind opt-in: Vier
Prozessstufen liefen einzeln und bestanden; sechs Tests brauchen eine echte Quelle oder einen echten Engine-Download
oder legen nur Testdaten an und liefen nicht. Derselbe Stand ist in der CI grün, die Gerätetests beider Module
eingeschlossen. Echte Android-Caption-/Audioextraktion und der Caption-App-Pfad bis SAF-Export wurden für 0.1.0
nachgewiesen und für 0.2.0 nicht wiederholt. Deutsch/Englisch, System/Hell/Dunkel, überarbeitete Auswahlfelder und
die Freigabe durch bewussten Auftragsstart sind integriert. [STATUS](docs/STATUS.md) trennt Implementierung,
Fixtures und reale Nachweise.

Echte Transkriptionen mit AssemblyAI, OpenAI und Groq bleiben mangels freigegebener
Zugangsdaten, Testinhalte und Kostenrahmen blockiert. Ein physisches ARM64-Gerät
fehlt; vollständige TalkBack-Bedienung ist mit der verfügbaren Eingabeautomation
nicht nachgewiesen. Die GitHub-Preview enthält nur den Quellstand. Öffentliche APK-Verteilung
bleibt wegen fehlender vollständiger FFmpeg-Lizenz-/Quellbelege gesperrt.

Die CI baut, testet und lintet und führt die Gerätetests beider Module auf einem Emulator aus. Ihr Geräteschritt
scheiterte in vier Läufen vom 10. und 14. September an `ChoiceAccessibilityTest`. Der letzte davon zeigte den Grund:
Die Sprachwahl blieb gesperrt, weil die App noch startete, länger als die 25 Sekunden, die der Test wartete. Seit
`1fe2dad` wartet er zuerst auf das Ende des Starts, und der Lauf am Stand `1fe2dad` ist grün
([DEFECTS](docs/DEFECTS.md), Punkt 54).

Die maßgeblichen Nachweise werden fortgeschrieben:

- [Tatsächlicher Projektstatus](docs/STATUS.md)
- [Restarbeiten und nächste Prüfziele](docs/NEXT_STEPS.md)
- [Bekannte Fehler nach Priorität](docs/BUGS.md)
- [Bekannte Probleme im Detail](docs/DEFECTS.md)
- [Lernprotokoll für andere Agenten](docs/LEARNINGS.md)
- [Build und persönliche Auslieferung](docs/BUILD.md)
- [Prüfbericht der Preview 0.2.0](docs/reports/2026-09-14-preview-0.2.md)
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
