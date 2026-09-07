# Architektur und technische Entscheidungen

## A1 — Native Android-App

**Entscheidung:** Kotlin, Jetpack Compose und Material 3. ViewModels stellen unveränderlichen UI-Zustand über StateFlow bereit; Coroutines übernehmen asynchrone Arbeit. Room speichert Job-/Artefakt-Metadaten, DataStore nicht geheime Einstellungen. OkHttp und kotlinx.serialization für Provider- und Metadatenverkehr. Hilt für die überschaubare Android-Abhängigkeitsverdrahtung; keine zusätzliche eigene DI-Plattform.

Die App braucht Share Intents, persistente Hintergrundarbeit, Storage Access Framework, geschützte Schlüssel und native Prozesse. Deshalb kein Svelte/Capacitor-Frontend: Eine Web-Oberfläche würde diese nativen Anforderungen nicht beseitigen, sondern eine weitere Brücke hinzufügen. Diese Entscheidung ist projektspezifisch, kein allgemeines Urteil gegen Svelte. Ein späterer Web-Client oder Backend wäre ein eigener Architekturentscheid, keine vorsorgliche v1-Komponente. [R01–R03]

Zunächst drei Gradle-Module: `:app` für UI/Android-Integration/Persistenz/Scheduling, `:core` für JVM-testbare Modelle/Regeln/Parser und `:extractor` für die Android-/Native-Integrationsgrenze. Provideradapter als getrennte Packages; erst bei einem nachgewiesenen Nutzen in weitere Module aufteilen. Ein separates Modul stellt keine OS-Sandbox dar. Keine unnötigen Microservices, kein Kubernetes, kein Node-Prozess und kein Python-UI.

**Build-Vorschlag:** minSdk 29; compileSdk/targetSdk auf die bei Implementierung aktuelle stabile SDK-Version, nicht Preview. Den tatsächlich kompatiblen Satz aus JDK, Gradle, AGP, Kotlin und Compose BOM ermitteln, festhalten und prüfen. Aktuelle stabile Versionen bedeuten nicht, dass jede neueste Einzelversion beliebig kombinierbar ist. `arm64-v8a` für das reale Telefon und `x86_64` für einen typischen Desktop-Emulator vorsehen; tatsächliche ADB-ABIs und Speicherseitengröße messen. Keine pauschale Zusage allein aufgrund eines Emulatornamens. Native Bibliotheken zusätzlich auf 16-KB-Kompatibilität prüfen. [R04]

## A2 — Kleine, klare Grenzen

`SourceResolver`: Eingabe prüfen, kanonische Identität ermitteln.

`ExtractorEngine`: Metadaten, Tracks und Audiodatei zur exakten Source-ID beschaffen. Die übrige App sieht keine wrapper-spezifischen Typen oder freie Shellkommandos.

`AcquisitionPlanner`: aufgelöste Konfiguration in einen deterministischen Plan übersetzen. Nur diese Komponente entscheidet, ob Captions, Audio und/oder STT erforderlich sind.

`ProviderAdapter`: capabilities-abhängige Transkription mit typisierten direkten Ergebnissen beziehungsweise Remote-Handles.

`JobCoordinator`: langlebige Zustände, Claims, Checkpoints, Abbruch und Wiederaufnahme. Android-Ausführungsmechanismen sind austauschbare Treiber, nicht das Datenmodell.

`ArtifactStore` und `Exporter`: interne Ergebnissicherung strikt vom Schreiben in externe Document-URIs trennen.

`ExtractorUpdateManager`: vertrauensgeprüfte, kompatible Komponentenpakete und deren Aktivierung; kein Bestandteil des normalen Providers.

UI kennt Use-Cases und Zustände, nicht Request Bodies, API-Schlüssel, yt-dlp-Pfade oder FFmpeg-Kommandos.

## A3 — Datenmodell

| Entität | Zweck |
|---|---|
| `Source` | UUID, Typ, Original-/kanonische URL, Video-ID oder Datei-Hash; beobachtete Metadaten |
| `Job` | Nutzerauftrag, unveränderlicher Konfigurationssnapshot, gewünschte Zweige, Erstellungszeit |
| `Attempt` | Ausführungsversuch pro Zweig: Versionen, Optionen, Stufe, Request-Zustand, Checkpoints, Fehler |
| `TranscriptArtifact` | Unveränderliches intern persistiertes Ergebnis mit Provenienz, Umfang, Qualitätswarnungen |
| `ExportRecord` | Format, Ziel-URI, Inhaltsversion, Schreibstatus und Exportfehler eines Artefakts |
| `SubmissionRecord` | Provider, Account-Referenz, Region, Audio-/Konfigurationshash, Submission-Zustand und Remote-ID |
| `EngineInstallation` | Paketidentität, Versionen/Hashes, Vertrauensnachweis, Gesundheitszustand, Nutzung/Pinning |

Modellnamen, die angefordert wurden, und tatsächlich vom Provider gemeldete Modelle getrennt speichern. Ohne gemeldeten Snapshot keine angeblich exakte Modellversion erfinden. Rückfalllisten und tatsächlich gewählte Alternativen protokollieren. UTC für gespeicherte Zeitpunkte; Anzeige lokal, feste Zeitzone gegebenenfalls in Exportmetadaten. Konfigurationssnapshots enthalten keine Secrets; Schlüsselrotation arbeitet über eine Credential-Referenz.

Ein Source kann viele Jobs haben. Ein Job kann mehrere Attempts und Artefakte besitzen. BOTH ist daher kein Sonderfall mit zwei Dateipfaden in einer einzelnen Jobzeile. Ein erneuter Export erzeugt keine neue Transkription. Eine gewollte Neutranskription erzeugt einen neuen Attempt, keine Mutation eines fertigen Transkripts.

## A4 — Zustände ohne Bedeutungsverlust

Drei voneinander unabhängige Ebenen:

**Ausführung:** `QUEUED`, `RUNNING`, `WAITING_NETWORK`, `WAITING_RATE_LIMIT`, `WAITING_USER`, `WAITING_REMOTE`, `SUBMISSION_UNCERTAIN`, `FINISHED`, `CANCELLED`.

**Phase:** resolve, fetch_captions, download_audio, prepare_audio, upload, submit, retrieve, normalize, persist. Phasen beschreiben Arbeit, nicht Erfolg.

**Ergebnis:** `NONE`, `SUCCESS`, `SUCCESS_WITH_WARNINGS`, `PARTIAL_SUCCESS`, `FAILED`, `CANCELLED`. Exportstatus separat: `NOT_REQUESTED`, `PENDING`, `WRITING`, `EXPORTED`, `PERMISSION_REQUIRED`, `FAILED`.

`SUCCESS` setzt voraus, dass alle im Plan angeforderten Transkriptzweige vollständig im technisch nachweisbaren Umfang erhalten und intern sicher gespeichert wurden. Fehlende Audiochunks oder bekannte Abruflücken ergeben `PARTIAL_SUCCESS`, auch wenn eine lesbare Datei existiert; unbekannte inhaltliche Vollständigkeit bleibt ausdrücklich unbekannt. BOTH mit nur einem erfolgreichen Zweig ist `PARTIAL_SUCCESS`. Ein Strukturhinweis ist kein fehlender Zweig. Ein erfolgreiches internes Transkript mit fehlgeschlagenem Export wird als „Transkript vorhanden; Export fehlgeschlagen“ dargestellt, niemals schlicht als „Alles erledigt“. Ein Abbruch löscht nicht automatisch bereits entstandene Ergebnisse.

Alle Übergänge über nachvollziehbare, getestete Regeln; keine beliebigen Strings in mehreren Workern. Transaktionale Claims/Leases verhindern gleichzeitige Bearbeitung desselben Steps. Ein abgelaufener Lease erlaubt Zustandsabgleich, beweist aber nicht, dass eine externe kostenpflichtige Submission nie erfolgt ist.

## A5 — Ausführung unter Android

Keine einzige stundenlang schlafende Worker-Schleife. Kurze, persistierte Arbeitsschritte planen; Downloads/Uploads und CPU-Aufbereitung klar von passivem Warten auf Providerergebnisse trennen.

WorkManager für beständige, verschiebbare Arbeit, Recovery und begrenzte Poll-Abfragen verwenden. Für längere, unmittelbar vom Nutzer gestartete Transfers geeignete user-initiated data transfer jobs auf unterstützten Android-Versionen prüfen. Foreground Services nur mit passendem Typ, Startbedingungen, Notification und Timeout-Behandlung einsetzen. Kein Missbrauch von `mediaPlayback`, Alarmen oder endlosen Wake Locks. [R05–R07]

Ab Android 16 können auch lange Foreground-Worker Jobquoten belasten. Außerdem bestehen versions-/targetabhängige Laufzeitgrenzen für `dataSync` und `mediaProcessing`. Daher muss der P0-Nachweis die konkrete Scheduling-Strategie für die gewählte SDK-/Gerätekombination festlegen. Nicht „WorkManager garantiert unbegrenzten Hintergrundbetrieb“ in die Dokumentation schreiben. [R05–R06]

Remote-Polling: ID und nächsten Abfragezeitpunkt speichern, Ausführung freigeben, bei Fälligkeit eine begrenzte Abfrage durchführen. Keine PeriodicWorkRequest mit vermeintlicher Sekundengenauigkeit. Im sichtbaren UI darf kurzzeitig schneller beobachtet werden; Hintergrundzeitpunkte bleiben OS-gesteuert. Passive Cloudverarbeitung verbraucht keinen Audio-CPU-Slot und hält kein Netz unnötig wach.

OS-Prozessverlust, Wegwischen der App, Task-Manager-Stopp, ausdrücklicher Force-Stop, Reboot und Gerätesperre als unterschiedliche Fälle behandeln. Nach Force-Stop erst beim zulässigen erneuten Start rekonstruieren. [R25] Bei noch nicht zugänglichem Credential-/Dateispeicher warten. Bei Abbruch HTTP-Calls und eigene Native-Subprozesse gezielt terminieren; keine fremden Prozesse beenden. Remote-Cancel ist capabilities-abhängig und nicht gleichbedeutend mit Erstattung.

**Parallelität:** konfiguriertes Joblimit 1–4, separate Providerlimits, standardmäßig ein Audioaufbereitungsprozess. Steuerung über langlebige Jobdaten plus begrenzte Ausführungsressourcen, nicht nur ein flüchtiges globales Semaphore. Retry erhält Backoff mit Jitter und Grenzen. Fortschritt ist realer Byte-, Chunk- oder Step-Fortschritt; keine geschätzten Providerprozente.

## A6 — Sicher fortsetzen statt blind erneut senden

Vor kostenrelevanter Übermittlung Submission-Absicht mit Provider, Inputhash und Optionen persistieren. Remote-ID unmittelbar nach bestätigter Annahme sichern. Dokumentierte Idempotenzmechanismen verwenden, wenn vorhanden; nicht aus der Existenz von HTTP POST oder einem selbst erfundenen Header ableiten.

Bei Timeout nach möglicher Annahme `SUBMISSION_UNCERTAIN`. Zunächst nach dokumentierten Möglichkeiten abgleichen. Bei nicht wiederherstellbarer synchroner Transkription nach Prozessverlust kann eine erneute Transkription nötig werden; mögliche Doppelberechnung muss vor Wiederholung sichtbar sein. Keine allgemeine Exactly-once-Garantie für externe APIs behaupten.

Bereits erhaltene Providerantwort vor teuren/fehleranfälligen Folgeoperationen in app-internem, begrenztem Spool sichern, damit Parser-/Exportfehler keine weitere Submission erzwingen. Inhaltsdaten bleiben privat und folgen einer Löschrichtlinie. Upload-URLs können verfallen; langlebig Source-ID und kontrollierte lokale Dateien behalten, nicht die Gültigkeit signierter URLs voraussetzen.

## A7 — Ergebnisablage und externe Ordner

Kanonisches strukturiertes Transkript als versionierte JSON-Datei im app-internen Files-Bereich; Room enthält Verweis, Hash, Metadaten und ggf. einen wiederherstellbaren Such-/Segmentindex. Der Index ist nicht eine zweite konkurrierende Wahrheit. Rohe Captions/Providerdaten getrennt und nur gemäß gewählter Aufbewahrung speichern.

Lokale Schreibreihenfolge: eindeutige temporäre Datei, vollständig schreiben/schließen und erforderliche Synchronisierung, auf demselben Dateisystem atomar finalisieren, dann Datenbankverweis veröffentlichen. Reconciliation für Absturz zwischen Datei- und DB-Operation vorsehen. Niemals behaupten, Dateisystem und SQLite seien automatisch eine gemeinsame Transaktion.

SAF-Ziele sind `content://`-URIs; nicht in fiktive `/storage/...`-Pfade konvertieren. Persistierbare Berechtigung nur soweit angeboten übernehmen. Testbare Abstraktion für `ContentResolver`/DocumentsProvider. Externe Anbieter können Rename, Lesen oder atomare Operationen anders unterstützen; keine universelle Atomizität oder Cloud-Synchronisierung garantieren. Fertig erst nach erfolgreich geschlossenem Write; falls lesbar Hash/Byteumfang prüfen und die Prüfstufe speichern. Fehlgeschlagene Teildateien kontrolliert bereinigen, das interne Ergebnis behalten. [R08]

`FileProvider` mit zeitlich/umfangsmäßig begrenztem Leserecht für Teilen; keine `file://`-URIs oder global offenen Exporte. Große Dateien streamen; keine mehrstündigen Audios als vollständiges ByteArray laden. WorkManager erhält IDs, nicht Audio, Transkripttexte oder Schlüssel. Cache-Limits beachten; benötigte Wiederaufnahmedaten nicht in beliebig vom OS löschbaren Cache verschieben.

## A8 — Bewusst noch offene Entscheidungen

Der P0-Test entscheidet über Android-Wrapper, gebündelte Python-/JS-Runtime, FFmpeg-Distribution, kompatibles Hot-Update-Paket und konkrete Scheduling-Zuordnung. Das ist keine Einladung, dauerhaft bei Recherche zu bleiben: Jeder Punkt braucht Kandidat, tatsächlich ausgeführten Test, Resultat und Entscheidung. Verträge/Produktumfang bleiben stabil. Unsichere Upstream-Binaries oder unkontrollierte Fremdserver sind kein zulässiger Abkürzungsweg.

Architekturentscheidungen anschließend unter `docs/adr/` knapp protokollieren: Kontext, Alternativen, Entscheidung, Folgen, gemessene Versionen und Tests. Keine API-/Toolversionen aus dieser Spezifikation ungeprüft übernehmen.
