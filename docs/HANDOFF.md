# Übergabe nach persönlicher Preview 0.2.0-preview.1

Stand: 14. September 2026. Nach der Preview 0.1.0 vom 8. September testete der Nutzer die App und schickte am
10. September 20 Rückmeldungen. 17 davon sind umgesetzt, und 23 Runden adversarischer Reviews prüften Code und Doku.
Nach Runde 23 hat der Nutzer die Schleife angehalten, um die Preview 0.2.0-preview.1 selbst zu testen und danach anhand
von [BUGS](BUGS.md) zu entscheiden, welche bekannten Punkte behoben werden. Ohne diese Auswahl wird nur ein Fehler der
Stufe P1 behoben. Keine automatische Fortsetzung der gesamten v1-Roadmap.

## Einstieg für einen anderen Agenten oder Rechner

1. `AGENTS.md`, [PRODUCT](PRODUCT.md), [ROADMAP](ROADMAP.md), [STATUS](STATUS.md),
   [Bekannte Fehler nach Priorität](BUGS.md), [Restarbeiten](NEXT_STEPS.md), [Bekannte Probleme](DEFECTS.md) und [Prüfbericht 0.2.0](reports/2026-09-14-preview-0.2.md)
   lesen. Technische Dokumente gezielt für den nächsten Eingriff vertiefen.
2. Git-Zustand, letzte Commits, Toolchain und `adb devices -l` prüfen. Regulär auf
   `main` arbeiten; vorhandene Änderungen erhalten. Die APKs der Preview 0.2.0 sind am Stand `1fe2dad`
   gebaut; spätere Commits, die nur Dokumentation ändern, ändern diese APK-Inputs nicht.
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
- Preview 0.2.0 am Stand `1fe2dad`: JVM 187 PASS; auf `emulator-5556` App-Suite 202 PASS bei sechs
  opt-in-Skips, die vier Prozessstufen einzeln PASS, Extractor-Suite 46 PASS bei vier opt-in-Skips; vier
  Lintberichte ohne Befund; statische Releaseprüfung der unsignierten APK PASS; CI-Lauf `34865638431` grün.
  Einzelheiten im [Prüfbericht](reports/2026-09-14-preview-0.2.md).
- Release-App `app.sourcescribe`, Version 0.2.0 / Code 2, Tag `v0.2.0-preview.1`. Die unsignierte APK liegt unter
  `.local-tools/releases/SourceScribe-0.2.0-preview.1-1fe2dad-unsigned.apk`; signiert wird sie vom Nutzer mit dem
  Schlüssel von 0.1.0, die Befehle stehen in [BUILD](BUILD.md). Die signierte APK von 0.1.0 liegt nicht mehr unter
  `app/build/outputs/`; Hash und Zertifikat stehen im [Previewbericht 0.1.0](reports/2026-09-08-preview.md).
- Debug-App `app.sourcescribe.debug` bleibt separat. Auf `emulator-5554` enthielt sie am 8. September
  frühere echte Captionjobs und eine synthetische 10.000-Segment-Datei.
  Nicht löschen/überschreiben. App-Daten sind nicht im Git-Clone; API-Schlüssel
  müssen auf einem neuen Gerät neu eingerichtet werden.
- Die öffentliche GitHub-Preview enthält den Quellstand, **keinen APK-Download**:
  FFmpeg-Corresponding-Source-/Notices-Zuordnung bleibt unvollständig.
- Der Geräteschritt der CI scheiterte in vier Läufen vom 10. und 14. September an `ChoiceAccessibilityTest`; der
  letzte zeigte, dass die App noch startete. Seit `1fe2dad` wartet der Test auf das Ende des Starts, und der
  Lauf am Stand `1fe2dad` ist grün ([DEFECTS](DEFECTS.md), Punkt 54).

## Private Dateien und portable Nachweise

Der persönliche Key liegt außerhalb des Repositorys. Auf diesem Rechner benennt
`.local-tools/PRIVATE-RELEASE.md` die privaten Speicherorte und Sicherungsschritte;
keine Passwortwerte. Für Updates denselben Key und einen höheren Versionscode
verwenden. Ohne separate sichere Key-Übertragung kann ein anderer Rechner bauen,
aber kein kompatibles Update dieser persönlichen Installation signieren.

`.local-tools/build-reports/` enthält ignorierte Rohlogs, Hashlisten und Screenshots, die der Preview 0.2.0
unter `preview-0.2.0-1fe2dad/`. Die portable Zusammenfassung zu 0.1.0 ist [preview-evidence.json](reports/2026-09-08-preview-evidence.json);
ausgewählte unbedenkliche Screenshots sind versioniert. Ein zusätzlicher Build in
einem frischen Clone bleibt NOT_RUN. Frühere Pausenstände stehen in Git und im
Integrationsbericht, nicht als konkurrierende Arbeitsaufträge in dieser Übergabe.

## Hiesige Umgebung und Arbeitsweise

Windows-ADB: `/mnt/c/Users/thoma/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
`emulator-5554` (API 37/x86_64/16-KB, 1280 × 2856, Dichte 480) gehört dem Nutzer: Agenten bedienen und lesen ihn
nicht. Agenten arbeiten auf `emulator-5556`, API 37/x86_64/16-KB, 1080 × 2424, Dichte 420.
Windows-ADB erwartet bei `install` Windows-Pfade (`wslpath -w`), Linux-ADB Linux-Pfade.
Portable Befehle und Package-IDs stehen in BUILD.
Java 17.0.20.1, Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00.
SDK und Caches unter `.local-tools/`; keine globale Konfiguration nötig.

16 GiB WSL-Swap sind nachgewiesen aktiv. Ein Gradle-Prozess, ein Worker, 2-GiB-Heap;
SDK/Builddaten nicht in RAM-basiertes `/tmp` verlegen. Der Hauptagent führt den einzigen
ADB-Prüfstrom. Keine Buildinput-Änderungen während Gradle läuft. Reviewer lesen einen Export
(`git archive`), solange daneben gebaut oder am Gerät geprüft wird. Findings vor dem Handeln bestätigen.

Stand 8. September auf `emulator-5554`: TalkBack aus, Schriftfaktor 1, temporäre
Accessibility-/Netz-/Displaywerte restauriert. Hörbare Tests vorher ankündigen. Vor späterem Ausschalten eigene Worker
beenden und Änderungen sichern; gemeinsame Emulator-/ADB-/Codex-Prozesse nicht
anlasslos global beenden. [Lernprotokoll](LEARNINGS.md).
