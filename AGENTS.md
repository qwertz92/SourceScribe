# Arbeitsregeln für SourceScribe

## Orientierung und Verantwortung

Dies ist ein persönliches Android-Projekt, kein SaaS. `docs/PRODUCT.md` definiert den Umfang; `docs/ROADMAP.md` die Reihenfolge. Architektur, Integrationsverträge und Sicherheit stehen in den gleichnamigen Dokumenten. Lies sie gezielt, bevor du ihren Bereich veränderst. Arbeitsberichte und Dokumentation auf Deutsch; Codebezeichner und stabile Zustandswerte auf Englisch.

`docs/INDEX.md` ist der gemeinsame Wissenseinstieg. Anforderungen nicht in ein zweites Wiki kopieren; Entscheidungen, Quellen und echte Tests in den verlinkten Dokumenten pflegen. `CLAUDE.md` bleibt ausschließlich die Brücke `@AGENTS.md`.

Bestehende Nutzerdateien und uncommittete Änderungen erhalten. Keine eigenmächtigen Remote-Pushes, Veröffentlichungen, Änderungen an Abrechnungen, Absenkungen des Sicherheitsniveaus oder Löschungen fremder Daten. Ein echter Blocker rechtfertigt einen klaren Zwischenstand, keine erfundene Fertigstellung.

## Unverhandelbare Invarianten

- Genau die angegebene Quelle verarbeiten. Keine Ersatzvideos, Suchtreffer oder erfundenen Transkripte.
- Der gewählte Beschaffungsmodus und Kostenrahmen gelten auch bei Fehlern. Keine stillen Providerwechsel, Uploads oder kostenpflichtigen Wiederholungen.
- Ungewisser Ausgang eines kostenrelevanten Requests ist nicht gleichbedeutend mit „nicht ausgeführt“.
- Erst intern dauerhaft sichern, dann exportieren. Ein Exportfehler löst keine erneute STT-Anfrage aus.
- Herkunft, Sprache, tatsächliche Modellangaben und Unsicherheiten offenlegen. Keine erfundenen Zeitstempel, Sprecheridentitäten, Prozentfortschritte oder Genauigkeitswerte.
- Ein Teilergebnis darf nicht als vollständiger Erfolg erscheinen. Erfolgreiche Geschwisterartefakte bleiben erhalten.
- API-Schlüssel, temporäre Audioquellen und vollständige Transkripte nicht in Logs, Git, WorkManager-Input-Daten oder Diagnoseexporte schreiben.
- Kein ungeprüfter Update-Code, keine Shell-Interpolation untrusted Eingaben, keine frei konfigurierbaren yt-dlp-Plugins oder `--exec`-Hooks.
- Ein eigener Prozess mit derselben Android-UID ist keine Sicherheitsisolation gegenüber App-Daten.

## Implementierungsstil

Kotlin, Compose, Coroutines/Flow; UI frei von Netzwerk-/Dateiverarbeitungslogik. Domainlogik möglichst JVM-testbar. Kleine Änderungen mit klaren Verträgen, typisierten Fehlern, Cancellation und nachvollziehbaren Zuständen. Keine Framework-Abstraktionen ohne konkreten Nutzen. Bibliotheks-/Toolversionen im Versionskatalog und Build festhalten, keine dynamischen `+`-Versionen.

Vor Änderungen an Datenmodell, Job-Semantik, Update-Vertrauen oder Netzwerkgrenzen einen kurzen Architekturentscheid dokumentieren. Die technischen Aussagen in RESEARCH sind datierte Recherche, keine ewigen Versionsvorgaben. Prüfe integrationskritische Details gegen aktuelle Primärquellen und den tatsächlich ausgeführten Code.

## Subagenten und Review

Delegiere sinnvoll, sofern die Umgebung echte Subagenten unterstützt. Geeignete unabhängige Bereiche sind Provideradapter samt Contract-Tests, Caption-Parser/Exporter, UI und gezielte Sicherheits-/Lebenszyklusreviews. Gemeinsame Interfaces, DB-Migrationen und Scheduler haben einen verantwortlichen Integrator. Scope, erlaubte Dateien, erwartete Tests und Ergebnisformat je Auftrag festlegen; isolierte Worktrees bei parallelen Codeänderungen bevorzugen.

Ein Reviewer soll nicht nur seinen eigenen Code abnehmen. Findings brauchen Datei/Stelle, Voraussetzung, reproduzierbaren Ablauf, erwartetes/tatsächliches Verhalten und Schweregrad. Der Hauptagent prüft die Evidenz. Bei fehlender Delegationsfunktion getrennte Review-Rollen sequenziell ausführen; keine fiktiven Agenten behaupten.

## Nachweise und Fertigstellung

Reale Befehle und Ergebnisse aufzeichnen. Tests dürfen nicht abgeschwächt, gelöscht oder übersprungen werden, nur um grün zu werden. Netzwerk-/Providerfehler durch kontrollierte Fixtures reproduzieren; Live-Tests separat dokumentieren. Fehlende Credentials oder Geräte als `BLOCKED/NOT_RUN`, nicht als `PASS` führen.

Nach einem Reviewfund einen passenden Regressionstest ergänzen. Vor Freigabe alle betroffenen Tests erneut ausführen und einen abschließenden unabhängigen Review-Pass durchführen. Keine offenen kritischen/hohen Defekte; keine offenen Abnahmeverletzungen. Niedrige verbleibende Risiken sichtbar dokumentieren. Keine endlose „bis garantiert fehlerfrei“-Schleife; eine nicht geschlossene Abnahmeanforderung verhindert die Freigabe, nicht die ehrliche Übergabe des erreichten Stands.

## Repositoryhygiene

Versioniere Quellcode, Tests, freigegebene kleine Fixtures, Lock-/Versionsdateien, Room-Schemas und Dokumentation. Ignoriere Schlüssel, lokale SDK-Pfade, Signing-Dateien, private Testdaten, Audio-Downloads und Laufzeitlogs. CI ohne echte Provider-Schlüssel betreiben. Testhilfen, Klartext-HTTP für lokale Fixtures und Demo-Provider dürfen nicht im persönlichen Release-Build verfügbar sein. Keine persistenten Test-Hintertüren.

Halte `docs/STATUS.md` aktuell. Der Abschlussbericht unterscheidet implementiert, fixture-getestet, live-verifiziert und blockiert. Der letzte Satz dieses Abschnitts sagte bis zum 11. September, das Übergabepaket enthalte noch keinen App-Code und keinen App-Testnachweis; das galt am Gründungstag und ist seit dem ersten Modul falsch. Was tatsächlich fehlt, steht in `docs/STATUS.md` und in der Blockadetabelle von `docs/NEXT_STEPS.md` — dort und nirgends sonst.
