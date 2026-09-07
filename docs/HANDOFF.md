# Gesicherte Arbeitspause — 8. September 2026

Der Nutzer hat ausdrücklich eine Pause bis zum nächsten Weiterarbeiten angeordnet: keine neuen Aufgaben, kontrolliertes Ende laufender Prozesse und Subagenten, danach darf der Rechner ausgeschaltet werden. Nicht automatisch weiterarbeiten. Beim nächsten Auftrag dieses Dokument, STATUS und den Integrationsbericht lesen; vorhandenen Code und Fixtures fortsetzen, nicht neu erzeugen.

## Aktueller Integrationsstand

- `main`, öffentliches Repository `qwertz92/SourceScribe`; reguläre Commits/Pushes erlaubt. Keine Tags, Releases oder APK-Veröffentlichung. Die Pausensicherung ist ein Entwicklungsstand und keine v1-Freigabe.
- P0: echte Android-Extraktion (Python, QuickJS/EJS, FFmpeg, Caption/Audio) auf API 37/x86_64/16-KB-Emulator nachgewiesen, zuletzt r48. Echte signierte Update-Aktivierung und Rollback r40. Physisches ARM64 weiterhin BLOCKED, statische ABI-/16-KB-Prüfungen vorhanden.
- Core: zuletzt 103 JVM-Tests in r52 bestanden. Neue HTTPS-Fixtures für tatsächlichen bezahlten Read-Timeout, Redirect-Isolation und Sprecherlabels über Chunkgrenzen bestanden.
- App: Room-Schema 3, drei STT-Adapter, immutable Jobkonfigurationen, atomare Batch-Anlage, Claims/Quoten, Recovery, sichere Credentials, Import/Export und Compose-UI implementiert. Aktuelle breite Android-Regression noch NOT_RUN.
- r57: in 11m38s vollständig erfolgreich beendet (219 Tasks,71 ausgeführt). Debug-App und aktuelles Instrumentation-APK kompiliert und beide erfolgreich auf `emulator-5554` installiert; unsigned Release gebaut; vollständiges App-Lint Debug/Release und Extractor-Lint Release jeweils null Befunde. Neue Android-Fixtures wegen Pause nicht mehr gestartet.
- APKs: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, `app/build/outputs/apk/release/app-release-unsigned.apk`. Datum/Hash vor Verwendung prüfen. APK-Dateien bleiben lokal ignoriert.
- Letzte tatsächlich ausgeführte App-Instrumentation war r45: nur Teilgruppen erfolgreich; AudioImport-Fixtures fehlgeschlagen und damaliger DocumentsProvider fehlte mit MANAGE_DOCUMENTS-Schutz. Testmanifest danach korrigiert, aber frische APK noch nicht auf diese Fehler getestet. Ein erfolgreicher Test-APK-Build ist kein Instrumentation-PASS.
- Alle realen STT-Aufrufe weiterhin BLOCKED: keine ausdrücklich freigegebenen Credentials, Inhalte oder Kostenbudgets. Nur synthetische Schlüssel und lokale TLS-Fixtures verwenden.

## Verbindliches neues UI-Feedback

Der Nutzer sah eng aneinanderliegende untere Schaltflächen und unnötig große Felder mit Leerraum für Beschaffung/Anbieter/Modell. Beim Weiterarbeiten durch Astra korrigieren: klarer Abstand, kompakte Standardhöhe, stabile Dialog-/Scroll-Anker, Mindest-Touchflächen und 200-%-Schrift erhalten. App-Sprachauswahl Deutsch/Englisch hinzufügen, alle sichtbaren Texte konsistent lokalisieren; dies ist unabhängig von der ASR-Sprache. SS-12 ist entsprechend aktualisiert. System/Hell/Dunkel gibt es bereits in Einstellungen; keinen zweiten Theme-Mechanismus bauen.

Nachtrag des Nutzers: Die Felder müssen nicht besonders klein werden; gemeint sind unausgewogene Abstände zwischen Label, Wert, Rahmen und benachbarten Feldern. Der separate Schalter „Ich erlaube die Übermittlung …“ soll entfallen, einschließlich seines derzeit schlecht ausgerichteten Layouts. Bewusste Wahl des Cloud-Providers und Start des konkreten Auftrags gelten als Uploadfreigabe; dies muss intern weiterhin an Quelle, Anbieter, Modus und Kostenrahmen gebunden sein. Kein stiller Providerwechsel oder zusätzlicher Fallback daraus ableiten. Diese Produktänderung erteilt dem Entwicklungsagenten keine Freigabe für echte kostenpflichtige Provider-Tests. UI-Abnahme künftig anhand tatsächlich betrachteter Screenshots aller wesentlichen Ansichten, auch typografischer/Abstands-/Alignment-Kritik. Einen geeigneten aktuellen UI-/UX-Skill erst morgen recherchieren und auf Eignung prüfen.

TalkBack erzeugt hörbare Sprachausgabe auf dem Windows-Rechner. Diese Tests künftig ausdrücklich vorher ankündigen. Der Dienst wurde zum Test absichtlich aktiviert; vor Pause sind sämtliche zuvor gespeicherten Schrift-/Rotations-/Accessibility-Werte und die TalkBack-Notificationberechtigung wiederhergestellt. Der letzte `dumpsys accessibility` bestätigt `touchExplorationEnabled=false`, keine aktiven/gebundenen Services. Rohbelege: `ui-r53-final-settings-restored.json`, `pause-accessibility.txt` unter `.local-tools/build-reports/`.

TalkBack 17.0.0.889642762 war tatsächlich gebunden; grüne Fokusrahmen an Dialogtitel und Navigation wurden gesehen. Vollständige Fokusnavigation/Aktivierung und gehörte Labelqualität nicht als PASS deklarieren. UI-Automator-Dumps können die Accessibility-Services vorübergehend unterdrücken; nicht zugleich mit TalkBack einsetzen. ADB-Gesten/Keyboardinjektion erwiesen sich für vollständige Navigation noch nicht als verlässlich. Offizielle aktuelle Tastaturbelegung: https://support.google.com/accessibility/android/answer/6110948?hl=en (ab TalkBack 16.2 standardmäßig Enhanced-Keymap).

## Nächste konkrete Prüfungen

1. Git/Versionen/ADB und letzten r57-Ausgang lesen. Erst danach UI-Korrektur/Sprachwahl oder ein eingegrenzter Testblock. Root allein betreibt Gradle/ADB. Größere abgegrenzte Implementierungen mit frischem Kontext an GPT-5.6 Sol; Luna max für unabhängige Reviews. Keine parallelen zentralen Dateiänderungen.
2. Frische AudioImportTest/ExportStoreTest zuerst ausführen. Vermutete, noch nicht bestätigte Restgefahr: Instrumentation.context ändert nicht zwangsläufig die tatsächliche Binder-UID bei URI-Grants des DocumentsProvider. Falls reproduziert, Grant testexklusiv vom Provider selbst vergeben; Produktionsschutz nicht lockern. Anschließend gesamte App-Fixtures ausführen und tatsächliche Fehler beheben.
3. Neue `ParallelJobsTest` ausführen: zwei lokale TLS-Provider gleichzeitig, dritter Claim gesperrt, Settings-Snapshot, Abbruch A beeinträchtigt B nicht. `async(Dispatchers.IO)` ist bereits gegen Test-Latch-Deadlock korrigiert.
4. `ProcessRecoveryTest` in zwei separaten Instrumentation-Aufrufen mit Force-Stop dazwischen: `stage1PersistsAcceptedAssemblyRemote`, dann `stage2ReopensAndFinalizesWithoutResubmission`; Argumente `sourcescribeProcessFixture=true`, `sourcescribeProcessStage=1` bzw. `2`. Stage 2 verwendet tatsächliches `JobCoordinator.recover()`, prüft andere PID, ein GET und null neue POSTs. Nicht versehentlich als normale vollständige Suite starten; Opt-in-Test.
5. `UiFixtureTest` mit `sourcescribeUiFixture=true` erzeugt 10000 synthetische zeitlose Segmente mit langem Unicode-Titel und SUCHMARKE. Danach ADB-Suche/Viewer/Clipboard-Teile/Share, SRT-VTT-Sperre, Rotation, 200-%-Schrift, kleine Anzeige und TalkBack prüfen. Reale r38-Historie erhalten.
6. Abnahme-Inventar T01–T34 abgleichen. Noch gezielte Nachweise u. a. echter Coordinator-Caption-429/Parser-Retry/Fallback, doppelte Share-Intents, echte SAF-Grant-Entziehung, Prozessabbrüche an Export-/Datei-/Room-Grenzen, Update während zweier Jobs und Disk-full-Szenarien. Vorhandene Fixtures zuerst nutzen; keine Testgerüste duplizieren.
7. Neueste Release-APK erneut prüfen. r50-Testsignierung mit inzwischen vernichtetem temporärem Testkey, DEX-/Manifest-/16-KB-Audit war erfolgreich, ersetzt aber nicht den endgültigen Kandidaten. Öffentliche APK-Verteilung zusätzlich wegen fehlender exakt passender FFmpeg-Corresponding-Source-Nachweise BLOCKED; siehe Lizenzbericht.
8. CI-Workflow vorhanden, noch NOT_RUN. Pausenpush soll keine neue CI-Arbeit starten. Beim Wiederaufnehmen Workflow ausdrücklich auslösen, tatsächlichen Run prüfen; keine Provider-Secrets, keine APK-Veröffentlichung.

## Aktuelle Review-Korrekturen und Prüfgrenzen

- Missing-only Retry validiert sämtliche alten Submissions. Bereits RESPONSE_SAVED trotz aggregatbedingtem Missing wird mit MISSING_RETRY_RESPONSE_SAVED abgewiesen, damit keine zweite kostenpflichtige Anfrage entsteht. Unsichere Zustände gesperrt; Video-/Audio-/Hashbindung und Warnungssemantik geprüft. 14 neue Android-Fixtures geschrieben, noch nicht ausgeführt.
- Abgebrochene Retry-Vorbereitung wird erfasst, bevor Dateien kopiert werden; Startup bereinigt nur eindeutig verwaiste UUID-Attempt-Verzeichnisse. Unbekannte Namen/Symlinks bleiben geschützt.
- Recovery isoliert beschädigte Artefakte und ungültige Jobkonfigurationen, erhält unsichere bezahlte Responses und Preview-Pins. Keine Default-Konfiguration für kaputte gespeicherte Jobs. Fehlende finale Dateien mit vorhandener Room-Zeile führen zu ARTIFACT_FILE_MISSING.
- Eindeutig eigene finale Dateien ohne Room-Artefaktzeile können bei explizitem Löschen entfernt werden; fremde/mehrdeutige Eigentümer bleiben unangetastet. Caption-Provenienz bindet die immutable Engineinstallation. Cancellation aus suspendierter Prüfung wird weitergereicht.
- Abschließender enger Luna-Recovery-Review: keine neuen Findings bei diesen vier zuletzt reparierten Fällen. Das war statisch, nicht Android-Ausführung. Der vorher vermutete globale UNCERTAIN-Job-Lock und angeblich fehlender Preview-Lock wurden nach Kontrollflussprüfung verworfen; nicht erneut blind implementieren.
- Neue Testcode-Kompilierungsfehler r55/r56 (Companion-Typ, suspendierte DAO-Referenzen, Nullbarkeit) sind in r57 tatsächlich überwunden. Keine Warnungen oder Testregeln abgeschwächt.
- Letzter Delivery-Review vor der Pause: `AudioImportFixtureProvider.query` ignoriert die angefragte Projektion, während `AudioImport` bei der SIZE-only-Abfrage Spalte 0 liest. Codebefund bestätigt, plausibler Ursprung der alten AudioImport-Fixturefehler; tatsächliche neue Android-Ausführung und Regression noch offen. Behaupteter Export-Cursor-Overflow ist noch NICHT bestätigt: der Provider verwendet `RowBuilder.add(columnName, value)`, nicht sequenzielles `add(value)`; deshalb vor einer Änderung gegen Androids tatsächliche API prüfen. OUTPUT_FAILURE-Pipe-Race ebenfalls nur Hypothese. Signierskript statisch ohne konkreten Befund; CI noch nicht ausgeführt. Keine Reparatur dieser neu eingegangenen Befunde während der angeordneten Pause beginnen.

## Befehle und Umgebung

```text
timeout 1500 bash tools/build-local.sh :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease :extractor:lintRelease
timeout 45 python3 tools/check-repository.py --self-test
timeout 45 python3 tools/check-repository.py
```

ADB ist Windows-seitig unter `/mnt/c/Users/thoma/AppData/Local/Android/Sdk/platform-tools/adb.exe`. Instrumentation: `timeout 600 <adb> -s emulator-5554 shell am instrument -w -e class <Testklasse> app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner`. Vollständige Tests ohne `-e class`; Opt-in-Fixtures bleiben dabei übersprungen. Windows-Installationspfade beginnen `C:\Users\thoma\mystuff\personal\Projects\SourceScribe\`.

Java 17.0.20.1, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, SDK 37, min29. Emulator API37/Android17/x86_64/16KB, 1280×2856, density480. Build max1 Worker, Java2GiB, projektlokale SDK-/Gradle-Caches unter `.local-tools/`, kein tmpfs-Massenbuild. 16GiB WSL-Swap aktiv; letzte Messung 5,6GiB RAM belegt, knapp10GiB verfügbar,212KiB Swap. Externe AGP-Analytics-Warnung über schreibgeschütztes `/root/.android/analytics.settings` bekannt; keine globale Änderung dafür.


## Abschluss der Pausensicherung

Der laufende r57-Build wurde regulär erfolgreich beendet. Alle Subagenten haben beendet oder waren bereits nach einem früheren Rate-Limit gestoppt; keine neue Delegation offen. SourceScribe-Debug und beide Testpakete sind per Force-Stop beendet, anschließendes `pidof` liefert keine dieser PIDs. Außerhalb der Sandbox-Prozessansicht wurden keine Java-/Native-Buildprozesse mit Arbeitsverzeichnis im Projekt mehr gefunden. Der bereits vorher vorhandene Emulator, ADB-Server und Codex selbst wurden nicht global beendet; das normale Herunterfahren des Rechners ist jetzt möglich.

Quellcode ist in logisch getrennten Commits gesichert: `a801be3` App-Integration, `791cfd5` Geräte-Fixtures, `246b2d9` Build/CI/Signing; danach Dokumentations-Pausencommit. Der letzte Commit verwendet `[skip ci]`, damit der reine Sicherungspush keine neue CI-Aufgabe startet. Repository-Checks und `git diff --check` bestanden; Android-Laufzeitlücken bleiben ausdrücklich offen. Lokale Rohlogs/APKs liegen weiterhin auf dem Projektlaufwerk unter ignorierten Build-/Toolverzeichnissen und werden beim normalen Neustart erhalten.

Für morgen genügt im selben Codex-Task: „Bitte vom gesicherten Stand in docs/HANDOFF.md fortfahren, einschließlich meines letzten UI-Feedbacks.“ Erst dann Arbeit/Subagenten/Tests wieder starten.
