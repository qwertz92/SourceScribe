# SourceScribe — Codex-Übergabepaket v3

**Stand:** 5. September 2026. **Status:** konsolidierte Spezifikation; noch keine Android-Implementierung, kein APK und keine ausgeführten App-Tests.

## Ziel

Eine persönlich genutzte Android-App: YouTube-Link teilen oder einfügen, vorhandene Untertitel sichern und/oder Audio über AssemblyAI, OpenAI oder Groq transkribieren. Ergebnisse mit nachvollziehbarer Herkunft lokal aufbewahren und als Datei an ChatGPT weitergeben. Deutsche Zusammenfassung und Faktencheck bleiben außerhalb der App.

Dieses Paket ersetzt die früheren SourceScribe-Prompts einschließlich ihrer Override-Blöcke. Die frühere Unterhaltung erklärt die Motivation, ist aber keine zusätzliche konkurrierende Spezifikation. Spätere ausdrückliche Nutzerentscheidungen bleiben maßgeblich.

## So beginnt die Implementierung

Im Projektordner zuerst [AGENTS.md](AGENTS.md), Produkt und Roadmap lesen. Der
[gemeinsame Dokumentationsindex](docs/INDEX.md) führt zu den technischen Verträgen
und tatsächlichen Nachweisen. Ein vorhandenes Repository zuerst prüfen und erhalten.

Für ADB-Tests muss Codex den bereitgestellten Emulator tatsächlich über `adb devices -l` erreichen. Ein Cloud-Workspace hat nicht automatisch Zugriff auf einen Emulator des eigenen Rechners. Unter Windows/WSL den bereits funktionierenden ADB-Zugriffsweg verwenden, statt einen ungeschützten ADB-Server ins Netzwerk zu öffnen.

API-Schlüssel ausschließlich über lokale, nicht versionierte Konfiguration beziehungsweise die App bereitstellen. Kein Schlüssel gehört in einen Chat, eine Spezifikation, einen Screenshot oder ein Git-Commit.

## Dokumente und Zuständigkeit

| Datei | Verbindlicher Inhalt |
|---|---|
| [docs/INDEX.md](docs/INDEX.md) | Gemeinsamer Einstieg für alle Agenten |
| [AGENTS.md](AGENTS.md) | Dauerhafte Arbeits-, Review- und Nachweisregeln |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Funktionsumfang, Bedienung, Vorgaben und Anforderungs-IDs |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Technologie, Datenmodell, Zustände, Hintergrundausführung, Speicherung |
| [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md) | Extraktion, Provider, Audioaufbereitung und Provenienz |
| [docs/SECURITY_UPDATES.md](docs/SECURITY_UPDATES.md) | Sicherheitsgrenzen, Schlüssel, Updates und Rollback |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Implementierungsreihenfolge und Freigabestufen |
| [docs/TEST_PLAN.md](docs/TEST_PLAN.md) | Abnahmefälle und beweisbare Fertigstellung |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Geprüfte Primärquellen und offene Integrationsfragen |
| [docs/STATUS.md](docs/STATUS.md) | Tatsächlicher Projektfortschritt; anfänglich alles unimplementiert |
| [extras/SUMMARIZE_GUARDRAIL.md](extras/SUMMARIZE_GUARDRAIL.md) | Separater Zusatz für das ChatGPT-Projekt „Summarize“ |

Anforderungen werden in PRODUCT definiert; technische Dokumente konkretisieren sie, statt eigene konkurrierende Produktmodi einzuführen. Bei einem echten Widerspruch die sichere, nicht kostenverursachende Variante wählen, ihn dokumentieren und vor einer irreversiblen Entscheidung klären. Normale technische Detailentscheidungen selbst treffen und begründen.

## Wesentliche Entscheidungen

Native Android-App mit Kotlin und Jetpack Compose/Material 3. Kein Svelte-Frontend, kein eigener Server und keine zusätzliche LLM-API für Zusammenfassungen. Vier eindeutige Beschaffungsmodi statt mehrerer überlappender „Auto“-Varianten. Getrennte Zustände für Verarbeitung, Transkriptartefakte und externe Exporte. Kleine, klar abgegrenzte Codebereiche statt einer unnötig großen Modul-Landschaft.

Besonders risikoreich sind Android-taugliche yt-dlp-/Python-/JavaScript-Komponenten, sichere Komponentenupdates, Lebenszyklusverhalten und mehrdeutige Provider-Timeouts. Diese Punkte werden früh praktisch geprüft, nicht erst nach der UI-Implementierung.

## Was dieses Paket nicht behauptet

Es garantiert weder fehlerfreie Software noch jederzeitige YouTube-Erreichbarkeit. Es bestätigt keine accountabhängigen Kontingente und keine Audioqualität ohne Referenztranskript. Es ist keine bereits getestete App. Die konkrete Android-Extractor-Integration wird erst durch den vorgeschriebenen Techniknachweis ausgewählt.
