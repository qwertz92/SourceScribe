# Integrationsprüfungen nach dem ersten Android-Durchstich

Stand: 8. September 2026. Fortlaufender Bericht; kein vollständiger v1-Nachweis.
Rohprotokolle liegen lokal unter `.local-tools/build-reports/` und werden wegen
Laufzeitdaten nicht pauschal versioniert. Vorherige P0-/P1-Belege stehen im
[Build- und P0-Bericht](2026-09-07-build-and-p0.md).

## Umgebung

Java 17.0.20.1, AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00,
compile/target SDK 37, min SDK 29. Gradle wurde nach dem Versionsbefund aus
Lint von 9.6.0 auf 9.7.1 aktualisiert. Wrapper und Distribution sind fest
gepinnt und über die offiziellen Gradle-Prüfsummen kontrolliert. Die zusätzlich
benötigten `kotlin-reflect:2.4.0`-JAR/POM wurden gegen Maven Centrals veröffentlichte
SHA-1-Dateien verglichen und mit lokal berechneten SHA-256-Werten in die strikte
Gradle-Abhängigkeitsprüfung aufgenommen; die Prüfung wurde nicht abgeschaltet.

Gerät: `emulator-5554`, Android 17/API 37, `sdk_gphone16k_x86_64`, 16-KB-Seiten.
Fingerprint: `google/sdk_gphone16k_x86_64/emu64xa16k:17/CP31.260618.005/15731206:user/dev-keys`.
Dies ist kein physischer ARM64-Nachweis. Ein Build- oder Testprozess läuft jeweils
allein; Gradle nutzt einen Worker, 2 GiB Heap und den Projekt-Datenträger.

## Tatsächlich ausgeführte Läufe

| Lauf | Befehl/Auswahl | Ergebnis |
|---|---|---|
| r45 Build | `tools/build-local.sh :core:test :extractor:assembleDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest :extractor:lintDebug :app:lintDebug` | 93 JVM-Tests bestanden; drei APKs gebaut; Extractor-Lint bestanden. App-Lint mit 30 Befunden fehlgeschlagen. |
| r45 Android-Extraktion | Instrumentation: `ExtractionBoundaryTest`, `ExtractionChainTest`; explizite Quelle `jNQXAC9IVRw` | 5 Tests bestanden, 56,618 s. Echte Caption-, Audio- und Aufbereitungskette enthalten. |
| r45 App-Gesamtlauf | Instrumentation des App-Test-APKs | Abgebrochen: testexklusiver DocumentsProvider ohne erforderlichen MANAGE_DOCUMENTS-Schutz verursachte Prozessabsturz und Importfehler. Kein Gesamt-PASS. |
| r45 STT/Storage-Untermenge | `HistoryDatabaseTest,MigrationTest,StorageBudgetTest,SttStepTest,SttRecoveryTest,SttHardeningTest` | 46 Tests, 45 bestanden, 1 Fehler in der Upload-Disconnect-Fixture. Keine echte Provider-API. |
| r46 JVM | `tools/build-local.sh :core:test` mit Gradle 9.7.1 | Strikte Dependency-Verifikation verweigerte die noch nicht erfassten Kotlin-Reflect-Artefakte. Nach Quellen-/Checksum-Prüfung ergänzt. |
| r47 JVM | `tools/build-local.sh :core:test` | 97 Tests, 96 bestanden. Die neue TLS-Fehlermatrix ließ bei DISCONNECT_AT_START eine synthetische Request-Zeile in der Queue zurück. |
| r48 JVM | `tools/build-local.sh :core:test :extractor:assembleDebugAndroidTest :extractor:lintDebug` | 97 JVM-Tests bestanden; Extractor-Test-APK und Lint bestanden, Build 10 min 5 s. |
| r48 Android-Extraktion | Dieselbe explizite Quelle und beide Testklassen wie r45 | 6 Tests bestanden in 34,024 s: vier Fixture-Gegenproben und zwei echte Extraktionsprüfungen. |
| r49 | `tools/build-local.sh :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease :extractor:lintRelease` | 98 JVM-Tests bestanden. App-Kompilierung nach 4 min 56 s abgebrochen: unnötiger Safe-Call auf bereits als nicht-null erkannter CaptionTrack. Korrigiert; übrige Gates dieses Laufs nicht ausgeführt. |
| r50 | `tools/build-local.sh :app:assembleDebug :app:assembleRelease` | Debug- und unsigniertes Release-APK gebaut; Release-Lint-Vital bestanden. Build 15 min 46 s. Vollständiges Lint und aktuelle Instrumentation noch offen. |
| r51 | Gemeinsamer Build wie r49 | 103 JVM-Tests, davon drei fehlgeschlagen: die neue Timeout-Fixture verwendete eine von OkHttp 5 verbotene Timeout-Anpassung im Network-Interceptor. App-Gates nicht erreicht. |
| r52 | `tools/build-local.sh :core:test` | Nach Korrektur ausschließlich der Fixture: 103 Tests bestanden, keine Fehler, keine übersprungenen Tests; 1 min 24 s. TLS-Timeouts, Redirect-/Authorization-Isolation und Cross-Chunk-Sprecher enthalten. |

### Geräteprüfung des r50-APKs am 8. September

Installation des Debug-APKs (157.172.786 Bytes) erfolgreich. Bestehender
r38-Verlauf und Caption-Artefakt waren nach Start weiter lesbar. Bei
`font_scale=2.0` und Querformat scrollten Viewer und Aktionsdialog; die
Viewer-Fußleiste blieb bei zwei unterschiedlichen Scrollständen an denselben
Koordinaten. Der Formatwähler zeigte nach Scrollen weitere Formate und behielt
seine Abbrechen-Schaltfläche. Dies ist ein echter ADB-Nachweis für diese
konkreten Layoutpfade, noch kein TalkBack-/Großtranskript-Gesamtabschluss.

Rohbelege: `.local-tools/build-reports/ui-r50-200-landscape*.txt`,
`ui-r50-200-actions-scrolled.txt` und `ui-r50-200-format-picker*.txt`.
Ausgangswerte für Schrift, Rotation und Accessibility wurden danach aus der
zuvor gespeicherten JSON-Datei wiederhergestellt und vollständig verglichen.
Weitere Änderungen an Schlüsselbearbeitung und UI-Zustand nach r50 sind durch
diesen älteren APK-Lauf noch nicht geprüft.

### Release-Prüfung von r50

Unsigned APK: 147.238.008 Bytes, SHA-256
`e25f93202244ff3f79859128e3df3f61029481c0f8126bc37f2602a978fb0aab`.
`tools/sign-release.sh` mit einem eigens erzeugten, anschließend entfernten
Test-Keystore ausgeführt: Signierung und `apksigner verify --verbose` bestanden,
ebenso `zipalign -c -P 16 4`. Erneuter Aufruf auf dasselbe Ziel wurde korrekt
abgewiesen. Das ist ein Werkzeugtest, keine persönliche Release-Signatur oder
Veröffentlichung.

Im Release-Manifest fehlen `debuggable`, `testOnly` und die Fixture-Provider;
Backup und Klartextverkehr sind deaktiviert. Der DEX-Scan fand weder
AudioImport-/ExportFixtureProvider noch UiFixtureTest oder MockWebServer.
Die zwölf direkten ELF-Dateien und 512 ELF-Einträge in Runtime-ZIPs bzw.
WebP-Assets wurden statisch geprüft. Die zehn ursprünglichen WebP-Einträge in
den FFmpeg-ZIPs benötigen die gebündelten 16-KB-Ersatzbibliotheken; der
Native-Runner stellt deren geprüftes Verzeichnis an den Anfang von
`LD_LIBRARY_PATH`. Alle übrigen geprüften Einträge besitzen mindestens
16-KB-PT_LOAD-Ausrichtung. Die bereits ausgeführten 16-KB-FFmpeg-Gerätetests
bleiben der Laufzeitnachweis; ELF-Header allein ersetzen ihn nicht.

Rohbelege: `.local-tools/build-reports/release-r50-signing.txt`,
`release-r50-manifest.txt`, `release-r50-audit.json` und
`release-r50-inner-native.txt`. Öffentliche APK-Distribution bleibt wegen des
ungeklärten zugehörigen FFmpeg-Build-/Quellpakets blockiert.

### Native-Warnung in r50

AGP konnte `libdatastore_shared_counter.so` nicht strippen und übernahm die
Dateien unverändert. Die anschließende tatsächliche Prüfung beider APK-Dateien
mit ELF-Headerparser und NDK-r28c-`llvm-readelf -S` zeigte ausschließlich
16-KB-PT_LOAD-Ausrichtung und keine `.debug`-Sektionen. ARM64: 10.360 Bytes,
SHA-256 `deed4546c8dafad0e68ea2c25e4c0a62ca97343614ae386b7ed2af6abb7fa999`;
x86_64: 9.424 Bytes,
`c3973140a0e6144a83e7dd7ee3c4e161f42181591feda254e12c8395d3bbacd0`.
Manuelles `llvm-strip --strip-unneeded` auf Kopien funktionierte und reduzierte
sie auf 7.784 bzw. 7.336 Bytes; die Originale enthalten also kleine Symboltabellen.
Für genau diese Bibliothek ist nun die unveränderte Übernahme konfiguriert,
wie für die übrigen auditierten Native-Artefakte. Die wenigen zusätzlichen KiB
sind bewusst akzeptiert; es wurde keine allgemeine Strip-/Lint-Prüfung abgeschaltet.

## Bestätigte Defekte und Korrekturen

- Caption-URLs: dekodierte Querynamen und doppelte `v`/`lang`/`tlang` prüfen;
  effektive Sprache und angegebene Video-ID an den ausgewählten Track binden.
- Download ohne erneute Quellenauflösung: minimale `--load-info-json`-Rezeptur
  ohne `webpage_url` und ohne zweites URL-Argument. Der lokale ID-Marker beweist
  die Rezeptausführung, nicht unabhängig den Serverinhalt einer opaque HLS-URL.
- Verwaiste Leases, Artefaktfinalisierung vor Room-Insert, Quoten und
  Importvorschauen: Recovery und Löschung teilen die Lebenszykluskoordination.
- Bereits gespeicherte Starts und Exporte: Schedulerfehler werden dauerhaft
  getrennt erfasst und täuschen keinen nicht angelegten Job vor.
- Ungewisse Submission und AssemblyAI-Receipt: erhaltene Antworten/Handles bleiben
  gebunden; Abbruch oder fehlender Receipt erlaubt keine blinde Neusubmission.
- UI: 200-%-Querformat-Viewer überarbeitet; Startkonfiguration während der
  Übernahme gesperrt; Abbrechen unabhängig vom allgemeinen Aktions-Gate;
  anstehender Share-Text über Rotation erhalten. Aktuelle Gerätegegenprüfung offen.
- Test-DocumentsProvider mit MANAGE_DOCUMENTS geschützt; die anschließende
  URI-Grant-Prüfung bleibt bis zum aktuellen Android-Lauf `NOT_RUN`.
- Disconnect-Fixtures trennen nach dem tatsächlich eingelesenen Request und
  prüfen weiterhin jeden Pfad sowie die genaue Request-Anzahl.

„Nur fehlende Chunks wiederholen“ und Schema 3 werden gemäß
[ADR 0006](../adr/0006-missing-chunk-retries.md) integriert. Neue Codepfade sind
bis zu ihren bestandenen Tests ausschließlich `IMPLEMENTED`.

## Offene Nachweise

- `BLOCKED`: Live-STT je Provider ohne freigegebene Credentials, Audiodateien
  und Kostenrahmen; physisches ARM64-Gerät nicht vorhanden.
- `BLOCKED`: öffentliche APK-Freigabe ohne ausreichenden Corresponding-Source-
  Nachweis für das konkrete FFmpeg-Artefakt; siehe Lizenzbericht.
- `NOT_RUN`: vollständiger aktueller App-Testlauf, neue Schema-3-/Chunk-Retry-
  Regressionen, vollständige UI-/TalkBack-/Lebenszyklusmatrix und Releaseprüfung.

## Wiederaufnahme und Arbeitsteilung

Das Nutzungslimit unterbrach auch vier Agenten. Ihre Dateiedits sind erhalten;
angefangene STT-Implementierung und Pipeline-Regressionen wurden anschließend
mit frischem Kontext an zwei GPT-5.6-Sol-Agenten übergeben. Die Laufzeitmodelle
wurden in deren `turn_context.model` als `gpt-5.6-sol` überprüft. Unabhängige
Reviews nutzen Luna mit Max-Reasoning; Architektur, UI und Integration bleiben
beim Hauptagenten. Dies ist eine Arbeitsaufteilung, kein gemessener Kostenvergleich.


### Wiederaufnahme r53–r57 (8. September, lokale Zeit)

Die neue App wurde in r53 und r55 tatsächlich als Debug-APK gebaut; die vollständige Abnahme blieb jeweils offen. r53 scheiterte im vollständigen App-Lint an drei verbleibenden Befunden: überflüssiger leerer `super.onCleared()`-Aufruf, ein für `revokeUriPermission` unzulässiges Prefix-Flag in der Testbereinigung und ein wegen minSdk 29 überflüssiger `-v26`-Ressourcenqualifier. Alle drei wurden gezielt korrigiert. r54 scheiterte danach am inkrementellen AAPT-Zustand nach dem Ressourcenverzeichniswechsel; nur die betroffenen generierten Ressourcenverzeichnisse unter `app/build/intermediates` wurden gelöscht. r55 verarbeitete dieselben Quellressourcen erfolgreich.

r55 erreichte erstmals die Kompilierung der erweiterten App-Instrumentation und fand einen falsch qualifizierten verschachtelten Fixture-Typ sowie zwei ungeeignete suspendierte DAO-Funktionsreferenzen. Die Korrektur verwendet den tatsächlichen Companion-Typ und direkte suspendierte Aufrufe in Schleifen. r56 fand anschließend drei Nullbarkeitswarnungen in den Testhilfen; explizite `requireNotNull`-Prüfungen machen deren Voraussetzungen überprüfbar. Die strikten Compiler-/Lint-Regeln bleiben unverändert. Rohprotokolle: `.local-tools/build-reports/build-r53.log` bis `build-r57.log`; r57 ist beim Eintrag noch laufend, kein behaupteter Test-PASS.

Der abschließende unabhängige Luna-Review des eingegrenzten Recovery-Bereichs meldete keine neuen Befunde. Verifiziert wurden statisch: beschädigte Jobkonfigurationen werden ohne Default-Konfiguration isoliert; eindeutig eigene finalisierte Artefakte lassen sich auch ohne Room-Artefaktzeile löschen; Cancellation bei suspendierter Engine-Bindungsprüfung wird weitergereicht; Room-Artefakte mit fehlenden finalisierten Dateien führen zu `ARTIFACT_FILE_MISSING`. Die neuen Android-Regressionsprüfungen müssen noch ausgeführt werden. Ein unlesbarer Checkpoint bleibt bei der Dateibereinigung bewusst unangetastet, wenn dadurch kein Eigentümer sicher feststellbar ist.


### Sicherer Haltepunkt r57 — 8. September 2026, etwa 01:56 CEST

**BUILD/LINT PASS**, Befehl:

```text
timeout 1500 bash tools/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:lintRelease :extractor:lintRelease
```

r57 endete erfolgreich nach 11m38s, 219 Tasks (71 ausgeführt,148 aktuell). Die drei vollständigen Lint-XML-Berichte für App Debug/Release und Extractor Release enthalten jeweils **0 Issues**. Kotlin-/Java-Testkompilierung bestanden. Bekannte externe AGP-Analytics-Meldung wegen schreibgeschütztem Benutzerpfad bleibt separat dokumentiert; kein globaler Schreibzugriff eingerichtet. Keine neue Native-Strip-Warnung im r57-Protokoll.

| APK | Bytes | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` |157943686|`bb8cfabbcafd38a14fd56c8885796b9c0ebae7ad0da6bf9f6d78b2b5bc53d70f`|
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |2301475|`6113217f7c76fd8d6fde7bcec9626dbab5f549d1da8c0bdf6a1e92e03fa7078b`|
| `app/build/outputs/apk/release/app-release-unsigned.apk` |147294844|`e0b05e8b8a6368484ecb7349be6c9b5bc7c6a5351a2b21529b5c02cdab0846cc`|

App-Debug und aktuelle App-Test-APK wurden jeweils mit `adb -s emulator-5554 install -r` erfolgreich installiert (`install-app-r57.txt`, `install-app-test-r57.txt`). Die neue Android-Gesamtregression ist **NOT_RUN**: Der Nutzer hat vor deren Start die Arbeitspause angeordnet. Der zuletzt abgeschlossene Core-Lauf bleibt r52 mit103 Tests. CI bleibt **NOT_RUN**; die abschließende Pausensicherung verwendet `[skip ci]`, um ausdrücklich keine neue Arbeit nach der Pause zu starten. Dies ist keine bestandene oder ersetzte CI-Prüfung.

**UI-Nachweise begrenzt:** System/Hell/Dunkel ist implementiert; Dark Mode wurde in r53 visuell geprüft und auf System zurückgestellt. TalkBack17.0.0.889642762 war tatsächlich gebunden und sprach hörbar (auch vom Nutzer bestätigt). Grüne Fokusrahmen an Dialogtitel und Navigation gesehen; vollständige Traversierung/Aktivierung und Sprachqualität weiterhin **NOT_RUN**, keine vollständige Accessibility-Abnahme. ADB-Injektionen und UI-Automator-Dumps waren hierfür noch kein verlässlicher durchgängiger Ablauf. Die aktuellen [offiziellen Tastaturangaben](https://support.google.com/accessibility/android/answer/6110948?hl=en) unterscheiden Default- und Enhanced-Keymap.

Vor Pause wiederhergestellt und ausgelesen: font_scale1.0,user_rotation0,accelerometer_rotation1,enabled_accessibility_servicesnull,accessibility_enabled0; TalkBack-POST_NOTIFICATIONS wiedergranted=false und ausschließlich ursprüngliches FlagUSER_SENSITIVE_WHEN_GRANTED. `dumpsys accessibility` bestätigt `touchExplorationEnabled=false`, `Bound services:{}`, `Enabled services:{}`. SourceScribe-Debug und beide Testpakete kontrolliert per Force-Stop beendet. Keine Provider-Aufträge gestartet. Belege lokal unter `.local-tools/build-reports/pause-r57-evidence.json`, `ui-r53-final-settings-restored.json`, `pause-accessibility.txt`.

**Neueste Review-Hinweise offen:** AudioImport-Fixture ignoriert SIZE-only-Projektion; Importer liest daher falsche Spalte0 (statisch bestätigt, neue Android-Gegenprobe offen). Behaupteter Export-RowBuilder-Overflow bisher nicht bestätigt, da named-column-Overload verwendet wird; vor Änderung verifizieren. OUTPUT_FAILURE-Pipe-Race lediglich Hypothese. Diese eingegangenen Befunde und das neue Nutzerfeedback (Feld-/Buttonabstände, de/en-Appsprache, separaten Übermittlungs-Schalter entfernen) sind für die Wiederaufnahme in HANDOFF gespeichert, nicht während der Pause still implementiert.
