# Übergabe nach persönlicher Preview 0.1.0-preview.1

Stand: 8. September 2026. Nach der Budgetpause beauftragte der Nutzer einen
**begrenzten Abschluss**: bekannte kleine Fehler schließen, persönliche Test-APK
signieren, dokumentieren, committen/pushen und eine Preview markieren. Danach
pausieren; keine automatische Fortsetzung der gesamten v1-Roadmap.

## Einstieg für einen anderen Agenten oder Rechner

1. `AGENTS.md`, [PRODUCT](PRODUCT.md), [ROADMAP](ROADMAP.md), [STATUS](STATUS.md),
   [Restarbeiten](NEXT_STEPS.md) und [Previewbericht](reports/2026-09-08-preview.md)
   lesen. Technische Dokumente gezielt für den nächsten Eingriff vertiefen.
2. Git-Zustand, letzte Commits, Toolchain und `adb devices -l` prüfen. Regulär auf
   `main` arbeiten; vorhandene Änderungen erhalten. App-Basis ist `b631bbe`,
   CI-Diagnose `f9d4f8b`; spätere Dokumentationscommits ändern diese APK-Inputs nicht.
3. Auf einem frischen Linux-/WSL-System [BUILD](BUILD.md) verwenden. SDK, Caches,
   APKs und Rohlogs sind absichtlich nicht in Git. Quellcode, Wrapper, Prüfsummen,
   Room-Schemas, kleine Fixtures und Berichte reisen mit.
4. Das vom Nutzer gewählte begrenzte Ziel bearbeiten. Bestandene Prüfungen nicht
   neu erfinden. UI-Feedback auf der tatsächlich installierten Version reproduzieren
   und den Screenshot selbst betrachten.

## Gesicherter Stand

- Alle P0–P5-Funktionsbereiche integriert; vollständige v1-Abnahme offen.
  Keine freigegebenen Live-STT-Zugänge, Testdateien oder Kostenbudgets. Eigene
  Schlüsseltests des Nutzers ermächtigen Entwicklungsagenten nicht zu Aufrufen.
- App-Build r80, Geräteprüfung r81: 180 App-Tests PASS, fünf opt-in-Skips.
  Letzte History-Korrektur tatsächlich im Screenshot geprüft; aktueller Release-
  Inhalt mit offiziellen SDK-Werkzeugen und Native-/DEX-Inventar geprüft.
- Persönliche Release-App `app.sourcescribe`, Version 0.1.0 / Code 1, dauerhaft
  signiert. Preview-Bezeichnung/Tag: `v0.1.0-preview.1`. APK:
  `app/build/outputs/apk/release/SourceScribe-0.1.0-preview.1.apk`.
  Hash, Signatur und Laufzeitnachweis stehen im Previewbericht.
- Debug-App `app.sourcescribe.debug` bleibt separat. Der hiesige Emulator enthält
  dort frühere echte Captionjobs und eine synthetische 10.000-Segment-Datei.
  Nicht löschen/überschreiben. App-Daten sind nicht im Git-Clone; API-Schlüssel
  müssen auf einem neuen Gerät neu eingerichtet werden.
- Die öffentliche GitHub-Preview enthält den Quellstand, **keinen APK-Download**:
  FFmpeg-Corresponding-Source-/Notices-Zuordnung bleibt unvollständig.
- CI Build/JVM/Lint/Emulatorstart PASS; Gerätetest in Run `34252821287` FAIL,
  Einzelursache unbekannt. Diagnose für den nächsten Lauf vorbereitet.

## Private Dateien und portable Nachweise

Der persönliche Key liegt außerhalb des Repositorys. Auf diesem Rechner benennt
`.local-tools/PRIVATE-RELEASE.md` die privaten Speicherorte und Sicherungsschritte;
keine Passwortwerte. Für Updates denselben Key und einen höheren Versionscode
verwenden. Ohne separate sichere Key-Übertragung kann ein anderer Rechner bauen,
aber kein kompatibles Update dieser persönlichen Installation signieren.

`.local-tools/build-reports/` enthält ignorierte Rohlogs, Hashlisten und Screenshots.
Die portable Zusammenfassung ist [preview-evidence.json](reports/2026-09-08-preview-evidence.json);
ausgewählte unbedenkliche Screenshots sind versioniert. Ein zusätzlicher Build in
einem frischen Clone bleibt NOT_RUN. Frühere Pausenstände stehen in Git und im
Integrationsbericht, nicht als konkurrierende Arbeitsaufträge in dieser Übergabe.

## Hiesige Umgebung und Arbeitsweise

Windows-ADB: `/mnt/c/Users/thoma/AppData/Local/Android/Sdk/platform-tools/adb.exe`,
`emulator-5554`, API 37/x86_64/16-KB, 1280 × 2856, Dichte 480.
Windows-ADB erwartet bei `install` Windows-Pfade (`wslpath -w`), Linux-ADB Linux-Pfade.
Portable Befehle und Package-IDs stehen in BUILD.
Java 17.0.20.1, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00.
SDK und Caches unter `.local-tools/`; keine globale Konfiguration nötig.

16 GiB WSL-Swap sind nachgewiesen aktiv. Ein Gradle-Prozess, ein Worker, 2-GiB-Heap;
SDK/Builddaten nicht in RAM-basiertes `/tmp` verlegen. Root führt den einzigen
ADB-Prüfstrom. Keine Buildinput-Änderungen während Gradle läuft. Astra verantwortet
Produktions-UI/Integration; größere klare Implementierungen an Sol, enge unabhängige
Reviews an Luna. Findings bestätigen; keine wiederholten Vollreviews als Selbstzweck.

TalkBack ist aus, Schriftfaktor 1, temporäre Accessibility-/Netz-/Displaywerte sind
restauriert. Hörbare Tests vorher ankündigen. Vor späterem Ausschalten eigene Worker
beenden und Änderungen sichern; gemeinsame Emulator-/ADB-/Codex-Prozesse nicht
anlasslos global beenden. [Lernprotokoll](LEARNINGS.md).
