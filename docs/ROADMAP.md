# Roadmap und Freigabestufen

## Prinzip

Eine vollständige persönliche v1 ist das Ziel. Die Reihenfolge minimiert Integrationsrisiken; sie streicht keine der Kernfunktionen. Erst einen echten Durchstich bauen, dann erweitern. Keine vorausberechneten Zeitversprechen. Jede Phase schließt mit integriertem Code, tatsächlichen Tests, kurzem Entscheidungsprotokoll und aktualisiertem STATUS.

| Phase | Inhalt | Nachweis für Abschluss |
|---|---|---|
| P0 — Machbarkeit | Workspace/Toolchain/ADB, Android-Extractor einschließlich Runtime/EJS/FFmpeg, ABI/16 KB, Updatevertrauen, Scheduling-Entscheid | Tatsächliche Metadaten-, Caption-, Audio-, Native- und Update-/Rollback-Prüfungen; offene Punkte sichtbar |
| P1 — Echter Durchstich | Share/Paste → Nur YouTube → korrekte Caption-Herkunft → interne Speicherung → MD-Export → Viewer/Verlauf | APK auf ADB-Gerät; echte Quelle plus deterministische Fixtures; falsche Source-ID abgewiesen |
| P2 — Erste STT-Pipeline | Groq als erster leicht prüfbarer synchroner Adapter; lokale Datei + YouTube-Audio; Größenlimits/Chunking; Credentials; Rückfallmodus | Echter Providerlauf nur mit Testfreigabe; alle Fehler-/Submissionfälle zunächst über Fixtures |
| P3 — Vollständige Beschaffung | AssemblyAI-Async und OpenAI; BOTH, vier Modi, Trackauswahl, Sprache/Optionen, Presets | Contract-Tests für alle Adapter; Teilfehler sauber; Live-Nachweise pro freigegebenem Provider |
| P4 — Zuverlässigkeit | Queue/Parallelität, Prozess-Recovery, Quoten, Exportreparatur, Kostenwarnungen, sicheres Engineupdate + Rollback | Lebenszyklus-, Speicher-, Update- und Doppelabrechnungs-Gegenproben bestanden |
| P5 — Bedienung und Auslieferung | Viewer/Suche, weitere Formate, Einstellungen, Diagnose, Accessibility, Release-Signing/CI/Docs | Deutsche UI auf realem ADB-System geprüft; alle geforderten Formate und Share-Flows nachgewiesen |
| P6 — Abnahme | Integrierte Regression, unabhängige adversariale Reviews, Releaseprüfung | TEST_PLAN erfüllt; keine offenen Abnahmeverletzungen; klare Restlimits und Prüfevidenz |

Der zuerst implementierte Provider ist keine permanente Produktvoreinstellung. Fehlt ein Groq-Testschlüssel, mit Fixtures fortfahren und einen anderen ausdrücklich freigegebenen Provider für den ersten Live-Nachweis verwenden. Alle drei Adapter bleiben im vollständigen Umfang.

## P0-Arbeitsauftrag

Maximal eine kleine, aussagekräftige Auswahl gepflegter Android-Integrationskandidaten gegeneinander prüfen; keine endlose Bibliothekssuche. Mindestens den naheliegenden yt-dlp-Android-Wrapper untersuchen und nur bei technischen Gründen eine Alternative/eigene Runtimeintegration verfolgen. Dokumentiere Artefaktinhalt statt nur README-Versprechen.

Bewerte den separaten Komponentenupdateweg früh genug, dass nicht erst nach fertiger UI ein unauflösbarer Vertrauens-/Runtimekonflikt auftaucht. Scheitert ein Kandidat, kontrollierte Alternative prüfen. Bleibt Hot-Update blockiert, den Grund und den gebündelten APK-Pfad liefern, aber vollständige v1-Abnahme nicht als erfüllt markieren. Kein eigener Server als stiller Architekturwechsel.

## Freigabebezeichnungen

**Entwicklungsstand:** Gerüst oder nicht vollständig geprüfte Implementierung. Keine Alltagstauglichkeitszusage.

**Persönliche Preview:** Kernpfad auf dem bereitgestellten ADB-System nachgewiesen; fehlende Provider-/Geräte-/Update-Nachweise explizit aufgelistet. Kein Synonym für fertige v1.

**Vollständige v1:** definierte Pflichtfunktionen und Abnahmen erfüllt. Live-Providerverifikation erfolgt mit freigegebenen Credentials. Ohne diese kann der Code vollständig sein, aber nicht vollständig live-verifiziert.

**Gerätefreigabe:** Das konkrete getestete ABI/API-/Seitengrößenprofil angeben. Ein x86_64-Emulatortest ist kein Nachweis für jedes ARM64-Gerät. Wenn kein physisches Gerät verfügbar war, dies klar sagen; ARM64-Build und statische Nativeprüfungen trotzdem ausführen.

## Danach, nicht als Voraussetzung aufblasen

Priorität nach v1: aussagekräftiger Transcript-Diff, zusätzliche Anzeige-/Filteroptionen und persönliche Presetverbesserungen. Später bei konkretem Bedarf RSS/weitere Quellen, lokale ASR-Provider, anderer Client oder optionaler persönlicher Backend-Dienst. Kein numerischer „Accuracy Score“ ohne Referenzdaten, kein automatisches Mischen zweier Transkripte.

## Agentenaufteilung

Ein Integrator verantwortet Produktverträge, Datenmodell und Scheduler. Unabhängige Pakete für Caption-Parser/Exporter, einzelne Provideradapter und UI können nach stabilen Interfaces parallel entstehen. Read-only-Reviews für Sicherheit und Lebenszyklus unabhängig vom Autor einplanen. Bei gemeinsamem Emulator Geräte-/Testslots serialisieren: mehrere Agenten dürfen sich nicht gegenseitig die App stoppen, Dateien löschen oder denselben API-Budgettopf unkoordiniert verbrauchen.

## Restarbeit nach Preview 0.1.0-preview.1

Die konkrete priorisierte Liste mit Einstieg und Abschlussnachweis steht in
[NEXT_STEPS](NEXT_STEPS.md). Persönlicher Signing-Key und r80/r81-Regression sind
erledigt. Live-Provider, physisches ARM64, TalkBack, CI-Gerätefehler und öffentliche
APK-Quell-/Lizenzbelege bleiben ausdrücklich offen. Keine neue Implementierungs-
oder Vollreview-Schleife ohne nächsten Nutzerauftrag.
