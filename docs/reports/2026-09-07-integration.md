# Integrationsprüfungen nach dem ersten Android-Durchstich

Dieser Bericht ist chronologisch: ältere FAIL/NOT_RUN-Angaben bleiben zur
Nachvollziehbarkeit erhalten. Der aktuelle zusammengefasste Stand steht im
[Preview-Prüfbericht vom 8. September](2026-09-08-preview.md).

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


## r58/r59 — Wiederaufnahme, Instrumentation und UI-Feedback (8. September)

Ausgangspunkt: gepushter Pausenstand a4862a0, installierte r57-App und Test-APK.
ADB weiterhin emulator-5554, Android 17/API 37, x86_64, 16-KB-Seiten. Keine
realen Provider-Credentials verwendet, keine absichtliche Audioausgabe aktiviert.

- `am instrument -w -e class app.sourcescribe.data.AudioImportTest,app.sourcescribe.data.ExportStoreTest app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner`: 22 Tests, 24 Fehler einschließlich Teardown. Die neun Audioimporttests bestanden. Export-Fixture verweigert schon den Kontrollaufruf mit `MANAGE_DOCUMENTS`/`ACTION_OPEN_DOCUMENT`; das beweist noch keinen Produkt-Exportfehler. Rohlog: `.local-tools/build-reports/android-import-export-r58.txt`.
- `am instrument -w -e class app.sourcescribe.data.AppPipelineTest,app.sourcescribe.data.SttMissingRetryTest,app.sourcescribe.data.SttRecoveryTest,app.sourcescribe.data.ParallelJobsTest,app.sourcescribe.data.MigrationTest app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner`: 91 Tests, ein Fehler, 28,621 s. Betroffen: `retryMissingOnlyUsesLatestFallbackSttAttemptInsteadOfOlderSuccess`, Runtimeprobe `MISSING_BINARY`. 90 Tests bestanden, einschließlich 14 Missing-Retry-, 34 Recovery-, Parallelitäts- und Migrationstests. Rohlog: `.local-tools/build-reports/android-recovery-r58.txt`.
- Die zusätzlich im Review gefundene falsche Audio-Fixture-Projektion ist ein eigener Testinfrastrukturdefekt; sie war kein fehlgeschlagener Audioimporttest in r58.
- AppCompat 1.8.0 gegen offizielle AndroidX-Releasepage und Google-Maven-Metadaten geprüft; de/en über Android-App-Sprachen, kein zweiter Sprachspeicher. Entschluss: ADR 0007. Build/Sprachwechsel/Screenshots nach dieser Änderung noch NOT_RUN, bis unten tatsächliche Resultate ergänzt werden.
- Gelesen: Chris-Banes-Skill `compose-ui-testing-patterns` als gezieltes Prüfverfahren, keine globale Skill-/Konfigurationsinstallation. Das breitere Google-Android-Testing-Setup würde unnötig mehrere Frameworks ergänzen und wurde nicht übernommen.

### r59 — echte Prozessgrenze, kontrollierter Provider

Zwei getrennte Instrumentation-Prozesse, dazwischen `adb shell am force-stop
app.sourcescribe.debug`. Opt-in: `sourcescribeProcessFixture=true`,
`sourcescribeProcessStage=1` bzw. `2`; Testmethoden
`ProcessRecoveryTest#stage1PersistsAcceptedAssemblyRemote` und
`#stage2ReopensAndFinalizesWithoutResubmission`.

Beide bestanden (1,557 s / 1,610 s). PID 21609 → 21705. Stage 1: zwei lokale
TLS-Fixture-Requests (Upload + genau eine Submission), eine dauerhaft gespeicherte
Remote-ID. Stage 2: genau ein GET, null neue kostenrelevante POSTs, eine unveränderte
Submission und dauerhaftes Ergebnis. Das ist ein echter Android-Prozessnachweis
mit TESTED_WITH_FIXTURES für den Provider, kein AssemblyAI-Live-Test.
Rohdaten: `android-process-stage1-r59.txt`, `android-process-stage2-r59.txt`,
`android-process-evidence-r59.txt` unter `.local-tools/build-reports/`.

Weiterer App-Lauf: `SttHardeningTest,SttStepTest,BatchCreationTest,ViewModelStateTest,
SourceFilesTest,StorageBudgetTest,CredentialStoreTest,DiagnosticsTest,DatabaseTest,
HistoryDatabaseTest` über denselben Runner: 51 Tests, fünf Fehler, alle in
`ViewModelStateTest` bei `ENGINE_PROBE_FAILED` vor der eigentlichen Testhandlung.
46 Tests bestanden. Rohlog: `android-app-rest-r59.txt`.

Die Runtime-Ursache wurde auf den prozessweiten youtubedl-android-Singleton und
abweichende isolierte Fixture-Verzeichnisse eingegrenzt. Testfixtures initialisieren
nun im echten Target-Kontext und verlinken nur diesen Runtime-Baum; vor rekursiver
Fixture-Bereinigung wird der Link explizit entfernt. Keine Assertions abgeschwächt.
Geräteregression dieses Fixes ausstehend.

Modelrouting aktuell aus tatsächlichen `turn_context`-Einträgen bestätigt:
Provider-/Runtime-Fixturearbeit GPT-5.6 Sol/high, unabhängiger Startfreigabe-Review
GPT-5.6 Luna/max. Root/Astra implementiert UI und integriert.

### r59/r60 — UI gebaut, Regression und bestätigter Exportfehler

r59: Debug-App und AndroidTest-APK BUILD SUCCESSFUL (17m55s, 100 Tasks).
Dependency-Enrollment für AppCompat getrennt geprüft: alle vorherigen SHA-256-Werte
unverändert; alle 17 neu hinzugekommenen Artefakte unabhängig aus Google Maven
bezogen und bytegenau abgeglichen (`dependency-delta-r59.json`). Sechs AppCompat-
Dateien einschließlich POMs zusätzlich in `appcompat-official-r59.json`.

App und Test-APK installiert. Tatsächlich bedient: Settings → App language →
Deutsch; Android LocaleManager meldet `[de]`. Darstellung → Dunkel funktioniert.
Nach Force-Stop und erneutem Öffnen bleiben de und Dunkel erhalten.
Screenshots betrachtet: `ui-before-r59.png`, `ui-new-r59.png`,
`ui-settings-en-r59.png`, `ui-language-dialog-r59.png`, `ui-settings-de-dark-r59.png`.
Die alte leere zweite Feldzeile und sich berührende Hauptbuttons sind behoben.
In der anschließenden Politur werden Navigation mit Symbolen statt Schrittnummern,
passende Farben für Text auf Teal-Flächen und kompakte Bestätigungsdialoge ergänzt;
diese Nachkorrektur benötigt noch ihren Gerätescreenshot.

Geräteregression r59: 37 Tests, zehn Fehler ausschließlich ExportStoreTest.
AudioImportTest 10, ViewRulesTest 5, ViewModelStateTest 6 und die zuvor defekte
AppPipeline-Retry-Methode bestanden. Native-Fixture-Ursache somit tatsächlich
nachgetestet. Die Exportfixture benötigte außerdem `isChildDocument`; ein während
Teilkompilierung geänderter Java-Rückgabetyp verursachte in einzelnen Fällen einen
ABI-Fehler. Der Folge-Gesamtbuild muss deshalb einen eingefrorenen Quellstand prüfen.

Separat bestätigter Produktdefekt: `DocumentFile.isDirectory()` verschluckt
SecurityException (AndroidX `DocumentsContractApi19.queryForString`, catch(Exception)
und Defaultwert). Das führt beim entzogenen Zugriff zu FAILED statt
PERMISSION_REQUIRED. ExportStore verwendet jetzt direkte DocumentsContract-/
Resolver-Operationen, damit der tatsächliche Fehler erhalten bleibt. Geschriebene
Dateien mit nicht möglichem Readback werden als UNVERIFIED gekennzeichnet.
Die unabhängigen bisherigen Regressionen bleiben unverändert; neue Gegenprobe
für verweigertes Readback und tatsächlich entzogenes Tree-Recht ergänzt.

r60: Gesamtbuild nach 7m01s bei :app:lintAnalyzeDebug intern abgebrochen:
`AsyncExecutionService.getService must not return null`, PSI-Neuaufbau von
ExportStore.kt. Diese Datei war während der Analyse geändert worden; kein Lint-PASS
und keine Unterdrückung. Wiederholung erfolgt mit eingefrorenem Build-Quellstand.

Luna/max-Review der Startfreigabe: kein bestätigter Defekt im begrenzten Diff-/
Aufrufkettenreview. Grenzen: statisch, kein Provider-/Gerätetest durch Reviewer.
ViewRulesTest ergänzt dazu die tatsächlich bestandene Android-Regression für alle
vier Modi und exakt passende Credential-/Provider-/Regionsbindung.

### r61 — Emulator-Neustart und eingefrorener Integrationslauf

Die bereits gebaute r59-App bestand zusätzlich die zwei ProcessRecovery-Stufen
über einen echten `adb reboot`: Stage 1 1,112 s, Stage 2 8,153 s, jeweils ein Test.
Nach dem Boot meldete Android `sys.boot_completed=1`, Uptime 63,31 s und Boot-ID
`11624e3a-4b9d-49a8-bf53-767f43787f0b`. Stage 2 bestätigt einen anderen Prozess,
genau einen lokalen TLS-GET, null neue Submission-POSTs, dieselbe Submission und
ein dauerhaftes Artefakt. Provider weiterhin TESTED_WITH_FIXTURES. Rohdaten:
`android-reboot-stage1-r61.txt`, `android-reboot-stage2-r61.txt`,
`android-reboot-r61.json`, `android-reboot-process-evidence-r61.txt`.

Der Gesamtlauf r61 verwendet einen eingefrorenen Satz von 144 Quell-/Builddateien
(`source-before-r61.json`). Die erzeugten Debug-APKs werden bereits parallel zur
anschließenden Release-/Lint-Prüfung auf dem Emulator geprüft; kein Gesamt-PASS
vor Abschluss aller Gates. Der Viewer-Seed schlug noch mit der r59-Test-APK vor
der Ausführung fehl: Kotlin leitete aus dem abschließenden `Log.i` einen Int-
Rückgabewert ab, JUnit verlangt void. Dieser Test wird separat korrigiert und
erneut ausgeführt; der übrige App-Lauf allein ersetzt diesen offenen Nachweis nicht.

GitHub Actions für `22df8a7` (Run 34229703342) scheiterte an vier fehlenden
Dependency-Metadaten-Hashes im kalten Cache. Die betreffenden Maven-Central-Dateien
sind unabhängig mit veröffentlichten SHA-256-Werten oder bytegleichen Antworten
beider offiziellen Endpunkte abgeglichen (`ci-metadata-candidates-r61.json`).
Die Ergänzung erfolgt erst nach Ende des eingefrorenen Builds, ohne Abschwächung
der Dependency-Verifikation.

### r61/r62 — Export geschlossen, UI und echte Updatebindung

r61 endete am internen 1200-s-Timeout während `:app:lintAnalyzeDebug`. Der vor/nach
dem Lauf erstellte SHA-256-Satz aller 144 Build-/Quelldateien ist identisch. Keine
Quelle wurde währenddessen verändert, nach Ende war kein Build-Java-Prozess mehr
aktiv. Debug-App und Test-APK waren vor dem Lint-Timeout fertig gebaut/installiert.
Instrumentation: Runner 177 Fälle, davon 174 ausgeführt, 172 bestanden, zwei
Exportfixturefehler. EngineJobPinning (1) und ProcessRecovery (2) waren in diesem
Lauf opt-in/nicht ausgeführt. UiFixture war wegen bekannter ungültiger Signatur
separat ausgenommen; dies war kein voller App-PASS.

Die zwei Schreibfixtures wurden korrigiert: Eine kleine Ausgabe passte vollständig
in die bisherige Pipe, bevor ihr Reader schloss; deshalb entstand erst MISMATCH.
ProxyFileDescriptor liefert nun zuerst einen akzeptierten Byte-Write und dann EIO.
Für ENOSPC fehlte zuvor ein gültiges `onGetSize`: dessen AOSP-Default wirft EBADF
und kann bereits FUSE-LOOKUP/GETATTR vor dem Write verhindern. Beide Callbacks
implementieren Größe/fsync und behalten ihren HandlerThread bis onRelease.
Die Assertions verlangen ausdrücklich ausgeführte Write-Callbacks; die bisherigen
Fehler-/Cleanup-/Ergebnisassertions wurden nicht abgeschwächt.

r62 `:app:assembleDebug :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL, 3m59s,
100 Tasks (14 ausgeführt). Wieder 144 identische Quellhashes vor/nach Build.
Warnung: mehrere Kotlin-Daemon-Sessions nach zuvor abgebrochenen Läufen; kein
parallel laufender Java-Build festgestellt. Keine Compilerregel deaktiviert.
`ExportStoreTest`: **17/17 bestanden**, 3,261 s auf demselben API-37-/16-KB-Gerät.
EIO/ENOSPC entstehen tatsächlich in Proxy-Write-Callbacks; das ist eine
TESTED_WITH_FIXTURES-SAF-Gegenprobe, kein physisch gefüllter Gerätespeicher.
Unabhängiger Luna/max-Abschlussreview: kein belegter False-Green-, Berechtigungs-
oder Cleanup-Datenverlustfund im abgegrenzten Exportdiff (statisch, keine eigenen
Geräteaufrufe). Rohlog: `android-export-r62.txt`.

`EngineJobPinningTest`, opt-in mit `sourcescribeEngineJobPinning=true`,
`engineProbeSource=https://www.youtube.com/watch?v=jNQXAC9IVRw`,
`engineUpdateChannel=NIGHTLY`: **1/1 bestanden**, 72,267 s. Zwei beanspruchte
Aufträge behalten Engine 2026.08.19, SHA-256
`1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`.
Die echte signierte Aktivierung verwendet 2026.08.30.232658, SHA-256
`3f1b267b4488f3aed3731a9e84a44011ca5569901868532e10ee11fd07d69707`;
EJS jeweils 0.8.0. Die alte Engine wurde anschließend tatsächlich für die
Versionsabfrage ausgeführt. Neue Aufträge erhalten die neue Engine. Grenzen:
`job.execution=NOT_RUN`; kein Nachweis zweier Downloads während des Updates,
null Providerrequests. Rohlog: `android-engine-pinning-r62.txt` (installierte r61-APK).

ADB/UI r61: echte Vorschau der expliziten Quelle jNQXAC9IVRw in Nur-YouTube-Modus,
danach de→en sowie Dunkel→Hell: Eingabe, Modus, Titel und Caption-Vorschau erhalten.
Betrachtete Screenshots: `ui-new-de-dark-r61.png`, `ui-settings-en-light-r61.png`,
`ui-settings-200-r61.png`, `ui-language-200-r61.png`,
`ui-language-landscape-200-r61.png`. Keine kostenpflichtige Submission.
Bei 200-%-Schrift war die Navigation zunächst seitlich abgeschnitten. r62 erlaubt
zweizeilige Labels; dessen Screenshot zeigte noch versetzte Symbolhöhen. Gleiche
reservierte Labelhöhe ist die anschließende, noch nachzutestende Korrektur.

TalkBack 17.0.0.889642762 wurde vorher angekündigt, kurz aktiviert und die Werte
`enabled_accessibility_services=null`, `accessibility_enabled=0` anschließend
nachweislich wiederhergestellt (`talkback-baseline-r62.json`,
`talkback-restored-r62.json`). Ein Fokusrahmen ist sichtbar. Die eingespritzten
Taps öffneten jedoch eine andere Auswahl als den fokussierten Eintrag; zuverlässige
TalkBack-Traversierung/Aktivierung bleibt **NOT_RUN/NOT_PROVEN**, kein PASS.
Die Bedienfolgen folgen [Googles Gestenbeschreibung](https://support.google.com/accessibility/android/answer/6151827?hl=en).

UiFixture r62: Signatur korrigiert, danach Fehler bei der Annahme, eine neue
Source-Zeile müsse SQLite-Row-ID 1 erhalten. Das gilt nur in leerer DB und wurde
auf den tatsächlichen Room-Insert-Vertrag (nicht -1) korrigiert. Bestehende
Nutzerdaten bleiben erhalten. Seed-/Viewer-Geräteregression noch offen.
Der CI-Metadatenfix ist separat als `0883b9e` committet und gepusht.

T15, ADB mit installierter r62-App: zweimal derselbe echte `ACTION_SEND`-Intent
(`text/plain`, EXTRA_TEXT mit jNQXAC9IVRw, SingleTop). UI zeigt exakt diese Eingabe.
Vorher/nachher in gestopptem App-Prozess konsistent kopierte Room-DB samt WAL:
jeweils eine Source, ein Job, ein Attempt, ein Artefakt, null Submissions.
**PASS: keine automatische zusätzliche Ausführung durch doppelte Share-Intents.**
Rohbeleg: `android-double-share-r62.json`, `ui-double-share-r62.txt`; temporäre
DB-Kopien anschließend entfernt. Kein Provider-Live-Test und kein Requestzähler
für einen kostenpflichtigen Anbieter durch diesen einzelnen UI-Nachweis.

Exportfix nach Abschlussreview als `812c4dc` committet und gepusht. Der nächste
kalte CI-Lauf erreichte danach die App-Konfiguration und meldete genau einen
weiteren fehlenden BOM-Metadatenhash (`kotlinx-serialization-bom:1.7.3`). Dessen
offizielle POM stimmt mit der veröffentlichten SHA-256 überein; Ergänzung erst
nach Ende des eingefrorenen Builds r63.


## r63/r64 — Gesamtregression, Updategrenzen und Lint

Quellstände wurden vor/nach jedem Build über SHA-256 verglichen: jeweils 144
Build-Input-Dateien unverändert. r63: `:app:assembleDebug
:app:assembleDebugAndroidTest :extractor:assembleDebugAndroidTest`, BUILD SUCCESSFUL
nach 7m06s, 132 Tasks (52 ausgeführt, 80 aktuell). APKs tatsächlich installiert.
Keine parallelen Gradle-Prozesse; kein erneuter Kotlin-Daemon-Hinweis in diesem Lauf.

- App-Runner: 181 gemeldete Fälle, davon 177 ausgeführt und bestanden, vier
  opt-in-Prüfungen nicht ausgeführt. 75,206 s. Enthalten sind alle 43 Pipeline-
  und 17 Exporttests. T05 ergänzt die tatsächliche Coordinator-Kette: vier
  429-Zyklen bis WAITING_USER, kein unbeabsichtigter STT-Fallback bei defekten
  Captions und exakt ein Fallbackversuch bei ausdrücklich aktivierter Option.
- EngineUpdateManager: 17 Runnerfälle = 16 bestandene Fixtures plus ein
  übersprungener opt-in-Livetest; 83,710 s. Neue Fälle prüfen Offline-I/O,
  HTTP 429 mit über Manager-Neustart gespeichertem Backoff, injizierten
  Speicherfehler mit Bereinigung/erhaltenem aktiven Slot sowie private/offline
  Aktivierungsproben. TESTED_WITH_FIXTURES, kein physisch gefülltes Dateisystem.
- Separater echter Update-Test: 1 PASS, 70,939 s. Signiertes Nightly
  2026.08.30.232658 / EJS 0.8.0, SHA-256
  `3f1b267b` (vollständiger Hash in lokalem Runnerprotokoll), echte öffentliche
  Aktivierungsprobe und Rollback auf gebündeltes 2026.08.19. LIVE_VERIFIED auf
  x86_64/API 37/16 KB; kein ARM64-Nachweis. Unabhängiger Updategrenzen-Review:
  kein bestätigter weiterer Produktions-/Sicherheitsdefekt.
- UiFixtureTest: 1 PASS, 0,985 s. SQLite-Insert-Ergebnis korrekt auf `!= -1L`
  statt auf eine erfundene feste Row-ID geprüft. Ein explizit synthetisches
  10.000-Segment-Artefakt in der App-DB angelegt; bestehende echte Caption erhalten.
  Tatsächliche UI-Suche nach SUCHMARKE findet Segment 9999 und blendet Segment 0
  aus. Keine Provider-/Genauigkeitsaussage aus diesem UI-Seed.
- r64-Lint: FAILED nach 4m26s mit vier konkreten Befunden. Drei veraltete
  Configuration-Breiten-/Höhenabfragen durch LocalWindowInfo/LocalDensity ersetzt.
  `localeConfig` gilt bewusst erst ab API 33; der bestehende AppCompat-
  autoStoreLocales-Pfad deckt API 29–32 ab. Dokumentiertes `tools:targetApi="33"`
  am Manifest, keine globale Regelabsenkung. Wiederholungsprüfung r65 läuft.

Lokale Rohprotokolle: `android-app-r63.txt`, `android-update-r63.txt`,
`android-live-update-r63.txt`, `android-ui-seed-r63.txt`, `build-r63.log`,
`build-r64.log`, `source-before/after-r63.json`, `source-before/after-r64.json`
im ignorierten Ordner `.local-tools/build-reports/`. Screenshots:
`ui-viewer-r63.png` und `ui-viewer-search-r63.png`.


## r65/r66 — Importgrenzen, kleine Darstellung und letzte Lintkorrektur

r65 stoppte nach 1m39s beim Compiler: Der für die Locale-Abfrage weiter benötigte
LocalConfiguration-Import war beim Wechsel der Größenberechnung entfernt worden.
Import wiederhergestellt; kein PASS. r66 baute Debug-App und AndroidTest-APK,
beide installiert. Gesamtauftrag nach 8m43s durch einen Lintbefund im neuen Test
abgebrochen: `UseKtx` an `Uri.parse`. Korrigiert auf das vorhandene `String.toUri`.
Die drei UI-Größenbefunde und localeConfig aus r64 erschienen nicht erneut.
Source-Freeze vor/nach r65 und r66: jeweils 144 identische Build-Input-Hashes.

T33 auf API37/x86_64/16KB tatsächlich ausgeführt:

- `ProcessRecoveryTest#stage1PersistsRevokedLocalAudioImport`, Argumente
  `sourcescribeProcessFixture=true`, `sourcescribeProcessStage=import-1`:
  1 PASS, 1,780 s; PID15513. Echter grantgeschützter DocumentsProvider-Content-URI,
  NativeRuntime-Audioprobe, persistierte Room-Zeile und interne WAV-Kopie geprüft.
  Danach Android-URI-Grant ausdrücklich widerrufen und externe Fixture synchron
  gelöscht. Der Provider prüft jede Löschung und wirft bei Fehler; Evidence wird
  erst nach erfolgreichem Reset dauerhaft geschrieben.
- `am force-stop app.sourcescribe.debug`, danach
  `ProcessRecoveryTest#stage2ReopensPrivateAudioAfterGrantAndSourceLoss`, Stage
  `import-2`: 1 PASS, 0,263 s, neue PID15631. Grant weiterhin DENIED; Room-Zeile,
  private Datei, SHA-256, Bytes und Metadaten unverändert. Stage2 nutzt nur Room,
  Datei und lokale OS-Grantprüfung, keinen ContentResolver-Open und keinen STT-
  Adapter. Das ist echter Prozess-/Grantnachweis mit synthetischen WAV-Bytes,
  kein Providertranskriptionsnachweis. Erfolgreich geprüfter Testroot gelöscht.
- Unabhängiger Luna-Review: zunächst behaupteter Lösch-Nachweisfehler nach
  Prüfung von ExportFixtureProvider:96–102/634–646 ausdrücklich zurückgenommen.
  Kein bestätigter Defekt. Interner Evidence-Name `grantAfterImport` anschließend
  zur Klarheit in `grantAfterRevocation` umbenannt, kein Verhaltenswechsel.

T26 r66: 1 FAIL nach65,685 s, `native_runtime:PROBE_FAILED:exit=2` beim
zusätzlichen yt-dlp-Versionsprobeaufruf des Testwrappers. Der Fehlertext enthält
keine stderr-Diagnose; eine genaue argv0-Ursache ist damit nicht bewiesen.
Der neue Wrapper deklariert die zuvor real ermittelte Version wie die bestehenden
Scriptfixtures. Beide echten alten Runtime-/Hash-Proben vor/nach Aktivierung
bleiben erhalten. Wrapper-yt-dlp UND -EJS werden im Ergebnis ausdrücklich als
`DECLARED_FROM_REAL_OLD_PROBE` markiert. Zwei laufende Caption-Jobs bleiben
TESTED_WITH_FIXTURES; Signatur/Aktivierungsprobe und echte Slots getrennte Belege.
Neue Ausführung in r67 erforderlich. Ein unabhängiger Review fand keine bestätigte
Pinning-/Laufzeitverletzung. Generisches Probe-Label von `PUBLIC_YOUTUBE` auf
`YOUTUBE_SOURCE_ARGUMENT` präzisiert: auch eine unlisted URL kann zugänglich sein.

UI-Geräteprüfungen:

- `System` folgt dem Android-Nachtmodus tatsächlich: Screenshots
  `ui-system-dark-r63.png` und `ui-system-light-r63.png`; Nachtmodus auf `no`
  zurückgestellt. Sprache Deutsch und Theme SYSTEM erhalten.
- Großes synthetisches Artefakt: Kopierauswahl bietet 11 Abschnitte; Formatauswahl
  exakt `.txt`, `.md`, `.json`, kein SRT/VTT/RAW ohne Zeit-/Rohdaten. Ein lokaler
  Prüfschritt suchte zunächst fälschlich englische Großschreibung `JSON` im
  deutschen Label; korrigierter Check auf tatsächliche Dateiendungen PASS.
- 320×600dp (WM960×1800,Dichte480), Schrift200%: echtes Clipping von
  Beschaffungsmodus/Modell und ungünstiger Navigation-Umbruch dokumentiert in
  `ui-small-fields-200-r66.png`. Root/Astra nutzt nun Native TextMeasurer mit
  tatsächlicher Breite/Schrift; Höhe der längsten Option wird reserviert, um
  vollständige Auswahlwerte ohne Sprung beim Optionswechsel darzustellen.
  Navigation erhält etwas mehr Beschriftungsbreite. Wiederholung in r67 offen.
- Copy-Hinweis in de/en gekürzt; Aktionen-Dialog besitzt eine Maximalhöhe mit
  internem Scroll statt pauschaler 80 %-Leerfläche. WM-Größe/font_scale nach Probe
  wiederhergestellt.
- TalkBack r66 vorab angekündigt. Bound service und touchExplorationEnabled=true
  aus dumpsys belegt; ADB-Keycombination gelangte teilweise zu Android-
  Systemshortcuts, daher KEIN TalkBack-Bedien-PASS. Service wieder AUS; ursprüngliche
  Accessibility-Werte und TalkBack-POST_NOTIFICATIONS einschließlich Flags exakt
  wiederhergestellt. Neuer ausschließlich opt-in AndroidTest nutzt tatsächliche
  Touch-Injektion mit FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES; noch NOT_RUN.

CI a10b105 stoppte an guava-parent33.6.0-jre.pom. Ein begrenzter Scan der
projektlokalen Cache-BOM-/Parent-POMs fand zusätzlich nur coroutines-bom1.8.1 ohne
Verifikationseintrag. Beide Bytes gegen Maven Central und dessen veröffentlichte
SHA-256-Datei geprüft; Metadaten ergänzt. Keine Binärhashes entfernt oder
Verification gelockert. Rohbeleg `ci-metadata-candidates-r66.json`.


T21/T22 zusätzlich r66: tatsächlicher 10.000-Segment-Share erstellt eine
912.857-Byte-UTF-8-Markdowndatei; SHA-256
`57535b2781782b4b66ba13e70d4f3ab8c753d5c870f769a6f4b09831f55b484a`.
Erstes/letztes Segment und SUCHMARKE im Dateiinhalt geprüft. Android-Chooser
zeigt die Datei, kein Empfänger ausgewählt/kein Versand. `ui-share-large-r66.json`,
`ui-share-large-r66.png` und synthetische `.md` lokal; kein OOM/ANR im Ablauf.
Repository-Gate samt Selbsttest während r67 ebenfalls PASS.

## r68/r69 — Vollständiger lokaler Build und Datenschutz-/Importnachweise

r67 scheiterte nach 3 min 55 s am nicht öffentlichen SDK-Aufruf `UiAutomation.destroy`
im neuen Testentwurf. Der Aufruf wurde entfernt; Instrumentation verwaltet ihren
UiAutomation-Lebenszyklus selbst. Kein Produktionsfehler und kein Test-PASS.
r68: **BUILD SUCCESSFUL, 16 min 43 s, 257 Tasks (115 ausgeführt,142 aktuell)**.
Debug-App, AndroidTest-APK und unsignierte Release-APK gebaut; App und App-Test
installiert. `:core:test` aktuell; App/Extractor jeweils Debug-/Release-Lint:
**vier XML-Berichte, jeweils 0 Issues**. Alle 145 Quell-/Build-Dateihashes vor/nach
Lauf identisch. Bekannte Hostwarnung bleibt: AGP-Metrikinitialisierung kann
`/root/.android/analytics.settings` im Sandboxkontext nicht schreiben. Keine
Lintregel, Dependency-Verifikation oder Sicherheitsprüfung abgeschwächt.

T23: Androidlauf mit AppPipeline-WorkData, Diagnostics und CredentialStore:
**14/14 PASS, 6,435 s**. WorkManager2.11.2 veröffentlicht `WorkInfo.inputData` nicht;
das ausschließlich in androidTest dokumentiert verwendete interne WorkSpec-DAO
liest die tatsächlich gespeicherten Acquisition-/Export-Inputs. Beide bestehen
exakt aus `attemptId`. Während des Laufs gesondert erfasster Logcat-Ausschnitt der
App-/Test-UIDs10234/10237:199 Zeilen,14 Canarystrings,null Treffer. Das belegt
diesen Testausschnitt, keinen universellen Leak-Ausschluss. Unabhängiger Luna-Review
bestätigt die konkreten Assertions, ohne weiteren reproduzierbaren Defekt.
Rohbelege: `android-sensitive-data-r68.txt`, `android-canary-logcat-r68.json/.txt`.

T33 auf aktueller r68-APK erneut bestanden: Import mit entzogenem Grant und
extern gelöschter Testdatei (1,811s), expliziter Force-Stop, Wiederöffnen der
internen Kopie im neuen Prozess (0,229s). Zwei separate Runneraufrufe mit
`sourcescribeProcessStage=import-1/import-2`. TESTED_WITH_FIXTURES für die
synthetische Audiodatei; LIVE_VERIFIED für Android-Grant-/Prozessgrenze.
Keine Provider-Submission. Rohbelege `android-import-1-r68.txt`,
`android-import-2-r68.txt`.

T26 blieb offen: r68 erreichte den Versionsprobe-Schritt, scheiterte nach 96,205 s
am 30-s-Warten auf zwei Caption-Barrierankünfte. Der bisherige Test protokollierte
keine Attempt-Endzustände; eine Ursache ist damit nicht bewiesen. Persistierte
App-Parallelität am Gerät tatsächlich 2. r69 ergänzt ausschließlich synthetische
State-/Phase-/Fehlerdiagnostik am Timeout, keine Timeouterhöhung oder Lockerung.

UI: 320×600dp und200-%-Schrift zeigt vollständige, umgebrochene Auswahlwerte nach
der Textmessung; bisherige Ellipsen entfernt. `Optionen` brach dennoch unschön
vor dem letzten Zeichen um. Die Navigation verwendet deshalb künftig die kurzen
Labels `Mehr`/`More`. Android-UI-Dump bestätigt den unabhängigen Reviewbefund:
Auswahlfeld-App-Sprache ist klickbar, meldet aber `android.view.View`. Korrektur
in r69: explizite `Role.Button` und Android-Semantikregression für Label/Wert,
Schaltflächenklasse und tatsächliche Dialogaktivierung.

Der bisherige TalkBack-Gestenentwurf mit `UiAutomation.injectInputEvent` wird
nicht übernommen: AOSP zeigt, dass Injection die InputFilter-/TouchExplorer-Kette
umgeht. [InputDispatcher](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android16-release/services/inputflinger/dispatcher/InputDispatcher.cpp)
und [InputManagerService](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/input/InputManagerService.java)
wurden unabhängig nachgelesen. TalkBack wurde vor Aktivierung angekündigt; im
zweiten Versuch ausdrücklich auf `touchExplorationEnabled=true` gewartet.
Emulator-Console-Tastaturevents veränderten den grünen Fokus nicht zuverlässig:
kein Bedien-PASS. Accessibilitywerte wiederhergestellt. Der diesmal tatsächlich
vorher vorhandene Notificationgrant (`true`,USER_SET|USER_SENSITIVE_WHEN_GRANTED)
wurde nach zunächst falschem Revoke wieder exakt auf den gemessenen Zustand
zurückgesetzt. Alte r66-Baseline gilt nicht als aktuelle Nutzereinstellung.
Computer-Use-Fallback konnte nicht initialisieren: `sandboxCwd is not a local
file URI: file:///mnt/c/users/thoma/mystuff/personal/projects/sourcescribe`.
Keine Windows-Sicherheits-/Konfigurationsänderung als Umgehung.

CI: Die zusätzlich tatsächlich angeforderten Parent-/BOM-POMs guava-parent33.6.0-jre
und kotlinx-coroutines-bom1.8.1 wurden gegen Maven-Central-Datei, veröffentlichte
SHA-256 und lokale Cachebytes abgeglichen und als `f31fa6f` separat gepusht.
Keine Prüfsummen entfernt. Der neue CI-Lauf ist noch kein bestätigter Gesamt-PASS.

### r69/r70 — Nachgewiesene Testursachen statt Timeouterhöhung

r69: BUILD SUCCESSFUL in 9 min 50 s, 216 Tasks (54 ausgeführt,162 aktuell),145 unveränderte
Quell-/Buildhashes. Aktuelle App installiert; Debug- und Release-Lint jeweils 0
Issues. r70 ändert ausschließlich die Engine-Testdatei: AndroidTest-APK und
Debug-Lint bestanden nach 3 min 33 s (111 Tasks,10 ausgeführt), Hashsatz unverändert.

Der r69-Android-Gesamtlauf enthält 184 Runnerfälle:178 PASS,1 FAIL,5 opt-in-Skips;
96,284s. Der neue Choice-Test prüfte die Aktivierung vor Ende der Initialisierung.
Das Feld wird danach auf dem Gerät tatsächlich aktiv. Zusätzlich war seine
Erwartung an die Android-Klassenhierarchie falsch: Compose exponiert bei
semantischen Textkindern die Buttonrolle als zusätzliches Kind desselben
klickbaren Controls. Genau diese Struktur ist auch an einer unveränderten
Material3-Schaltfläche sichtbar. Vor der Korrektur der Produktionsrolle fehlte
das Button-Rollenkind am Choice-Feld; danach ist es vorhanden. Die Regression
prüft deshalb Aktivierung nach Ready, Label/Wert, Buttonrolle innerhalb des
Controls und echte Dialogöffnung. Keine Produktionsänderung nur zur Erfüllung
einer falschen Frameworkannahme. `ui-settings-role-r69.xml` hält beide Strukturen
fest. Wiederholung dieser Regression in r71 erforderlich.

T26 r69: beide Attempts erreichten FETCH_CAPTIONS, pausierten danach mit NATIVE;
keine Barrierankunft. Androids JSONObject.quote escaped `/` als `\/`, und der
Test setzte den resultierenden JSON-String direkt in Python-Code. Python behält
diesen Backslash; der Dateipfad ist dadurch falsch/relativ. Der tatsächliche
Android-Dalvik-Probelauf bestätigt genau diesen JSON-Output; JSON-Dekodierung
ergibt wieder den richtigen absoluten Pfad. `android-json-python-quote-r69.json`.
Die SyntaxWarning beim Interpretieren des absichtlich fehlerhaften alten Werts
ist Teil dieser Reproduktion, keine ignorierte Produktionswarnung.
Der Test dekodiert den vorhandenen JSON-String nun ausdrücklich mit `json.loads`.
Die Produktionsruntime verwendete bereits einen eigenen passenden Python-
Stringencoder und war von diesem Testfehler nicht betroffen.

Der verbesserte Share-Dateiname wurde auf der tatsächlichen r69-Oberfläche
(installierte Produktions-App r68 mit derselben Sharefunktion) geprüft:
Source-ID, Artefakt-ID, `model-unknown`, Sprache de und `.md` vorhanden.
912.857 Bytes, SHA-256 unverändert
`57535b2781782b4b66ba13e70d4f3ab8c753d5c870f769a6f4b09831f55b484a`.
Android-Chooser geöffnet und ohne Empfängerauswahl geschlossen.
`ui-share-filename-r69.json`, `ui-share-filename-r69.png`.

Die Emulator-Console bestätigt Tastatursendungen mit OK, aber eine gleichzeitig
laufende zeitlich begrenzte Kernel-getevent-Aufzeichnung sieht keine zugehörigen
Events (`console-hardware-events-r69.txt`). Zusammen mit dem nicht startenden
Windows-Computer-Use-Tool bleibt die vollständige TalkBack-Traversierung/-
Aktivierung in dieser Umgebung **BLOCKED**, kein erfundener Accessibility-PASS.
Der getrennte Android-Semantiktest kann diese Grenze nicht ersetzen.


## r70/r71 — geschlossene Regression und aktueller APK-Nachweis

T26 r70: **1 PASS, 81,72 s**. Nach Korrektur des tatsächlich reproduzierten
Android-JSON-/Python-Testpfades erreichen zwei unterschiedliche native Prozesse
ihre Barrierpunkte innerhalb laufender Coordinatorjobs. Während beide Jobs ihre
Leases/alte Installation halten, wird der echte signierte Nightly aktiviert.
Beide synthetischen Captionjobs enden mit dem vorher gepinnten Enginebezug;
ein neuer Job bindet die neue Installation. Reale alte Runtime/Dateihashes
bleiben vor/nach Aktivierung gleich. Genau getrennte Rohlabels:
`job.execution=TESTED_WITH_FIXTURES`, `job.finished=2`,
`real.updateVerification=SIGNED_HASH_RUNTIME`.
Unabhängiger abschließender Luna-Review von Testcode und Rohlog: kein neuer
reproduzierbarer Befund. Die Wrappermetadaten sind deklariert, keine zwei echten
YouTube-Captiondownloads behauptet. Rohlog `android-engine-job-pinning-r70.txt`.

r71: **BUILD SUCCESSFUL, 4 min 23 s, 111 Tasks (10 ausgeführt)**.
AndroidTest-APK und App-Debug-Lint; 145 Quell-/Buildhashes vor/nach Lauf identisch.
Der vollständige App-Lauf ohne erneute UI-Fixture-Erzeugung endet nach 82,854 s:
**179 PASS, 5 opt-in-Skips, kein Fehler**. Runner „OK (184 tests)“ umfasst die
Skips. Alle neuen UI-, T05-, Export- und Datenschutzregressionen sind enthalten.
Die letzte Produktions-App r69 bleibt unverändert; nur Testcode wurde korrigiert.

Aktuelle Releaseprüfung r71: 18 Backup-/Transferausschlüsse, Backup/Klartext-HTTP/
Debuggable/TestOnly jeweils aus; 22 AndroidTest-Deskriptoren gegen alle DEX-Dateien
geprüft, keine enthalten. 528 innere/äußere ELF-Dateien geprüft; alle zehn bekannten
WebP-Ersatzdateien mit passender 16-KB-Ausrichtung vorhanden; keine ungelöste
ABI-/Alignmentabweichung. Physisches ARM64 bleibt dadurch nicht bewiesen.
Die aktuelle optimierte Backup-Resource heißt im APK `res/4j.xml`; der Pfad wurde
aus der tatsächlich paketierten Resourcentabelle ermittelt.

Letzte Viewergegenprobe: 200-%-Schrift im Querformat zeigt zunächst Titel/Suche.
Der erste visuelle Verdacht, der Inhalt sei dadurch unerreichbar, wurde durch
reales Scrollen widerlegt: Segment 0 vollständig sichtbar, Actions/Back zugänglich.
Keine Produktionsänderung für einen nicht reproduzierten Fehler.
`ui-viewer-landscape-200-segments-r71.png/.txt`; frische Font-/Rotationsbaseline
jeweils exakt wiederhergestellt. Das echte Antippen von „Copy section 1 of 11“
schließt den Abschnittspicker und kehrt zum Viewer zurück. Kein separater
systemweiter Clipboard-Inhaltsnachweis daraus abgeleitet.

Aktuelle APK-Bytes/Hashes, vollständige Build-/Testbefehle, Versionen und Grenzen
sind im [Preview-Prüfbericht](2026-09-08-preview.md) zusammengeführt. Die alten
fehlgeschlagenen Läufe wurden nicht gelöscht oder nachträglich als PASS umetikettiert.


## r72–r78 — echte OS-Gegenproben und gezielte Verlaufskorrektur

r73 bestand die zusätzliche reale Netz-/Displayprüfung: Caption-only-Job offline
angelegt (Flightmode/WLAN/mobile Daten wirklich aus, kein Defaultnetz), Home und
Display aus, Netz wiederhergestellt. Derselbe WorkManager-Job endete mit einem
neuen internen Captionartefakt, null STT-Submissions; beim Abschluss war Android
Asleep. Vorher/nachher identische Netzwerte. Der erste r72-Prüfversuch hatte
fälschlich ausschließlich Asleep statt zunächst Dozing erwartet und wurde als
FAIL behalten; das war kein Produktionsfehler. Details im Previewbericht.

Die reale Verlaufsansicht zeigte bei diesem reinen Caption-Job dennoch
„Groq · whisper-large-v3“ aus der unbenutzten Konfiguration. Root korrigierte
zunächst CAPTIONS_ONLY. Build r74: PASS, 10 min 21 s, 216 Tasks (31 ausgeführt),
145 unveränderte Quell-/Buildhashes, App-Lint Debug/Release bestanden. Dieser
Zwischenstand wurde nicht als vollständige Korrektur freigegeben: unabhängiger
Luna-Review reproduzierte dieselbe unbeschriftete Vorgabe bei einem erfolgreichen
CAPTIONS_THEN_STT-Job und die sichtbare AssemblyAI-Löschaktion ohne Remote-Handle.

Der vollständige Fix kennzeichnet STT-fähige Konfigurationen ausdrücklich als
„STT-Vorgabe“ / „STT configuration“. Reine Caption-Jobs zeigen keinen Provider.
Die Remote-Löschaktion erhält ausschließlich berechtigte Job-IDs aus einer
Room-Abfrage (AssemblyAI, Handle vorhanden, nicht REMOTE_DELETED) und verlangt
weiterhin einen terminalen Jobzustand. Remote-IDs und Schlüssel gelangen nicht
in diesen UI-State; der Backend-Recheck bleibt unverändert. Keine DB-Migration,
Provideranfrage oder zusätzliche Zustimmung durch diese Darstellung.
Sol ergänzte einen echten Room-Regressionstest für fehlende/falsche/mehrfache/
gelöschte Handles. Abschließender Luna-Review des Diffs: kein weiterer konkreter
Befund. r77 bestand nach 12 min 3 s alle 216 Buildtasks (55 ausgeführt),
App-Lint Debug/Release und identische 145 Quellhashes vor/nach dem Build. Die
installierte APK bestand 180 Android-App-Tests in 95,977 s; fünf opt-in-Skips
bleiben getrennt. Der neue Room-Regressionstest ist ausgeführt und bestanden.

CI a12e2e9 liefert nun den eigentlichen Startfehler statt nur Timeout:
Emulator 37.1.11 findet `sourcescribe-ci` nicht in seinem AVD-Verzeichnis.
Run 34248225818 hat Build/JVM/APK/Lint bestanden; keine Android-Instrumentation
begonnen. `ci-a12-failed.txt` enthält den tatsächlichen Emulator-Output.
Ein vermeintlicher Unbound-Variable-Fehler im neuen EXIT-Trap wurde nach
Kontrollflussprüfung widerlegt: der Trap wird erst nach PID-Zuweisung installiert.
Keine Timeouts oder Testgates abgeschwächt.


## Abschlusskorrekturen nach realem Screenshot

`ui-history-r77.png/.txt` zeigt den reparierten Nur-YouTube-Verlauf ohne fremde
Providerangabe. Bei derselben Prüfung fiel auf, dass der Exportstatus und seine
gleichlautende Fehlermeldung doppelt erschienen. Außerdem wurde bei SUCCESS die
Aktion „Fehlenden Zweig neu ausführen“ angeboten, obwohl kein Zweig fehlt.
Die minimale UI-Korrektur lässt nur unterschiedliche Zusatzfehler und blendet
diese einzelne Aktion bei vollständigem Erfolg aus. Neue Ausführung und anderer
Provider bleiben verfügbar. Root/Astra verantwortet diese Produktions-UI;
r79-Build/Lint bestand nach 12 min 31 s (216 Tasks, 31 ausgeführt), mit identischen
145 Quellhashes. Der unabhängige Review fand zusätzlich SUCCESS_WITH_WARNINGS:
Auch dieses Ergebnis ist vollständig. Root bestätigte dies gegen
AcquisitionPlanner und die Aggregation neuester Attempts und ergänzte denselben
Guard um SUCCESS_WITH_WARNINGS. Abschließender statischer Luna-Recheck: PASS;
Neue Ausführung, anderer Provider und unvollständige Outcomes bleiben erhalten.
Das Gerät prüft anschließend den finalen r80-Stand. Die ursprüngliche r77-
Screenshotgegenprobe bleibt als Nachweis der doppelten Meldung erhalten.

Die separate CI-Korrektur `558527f` wurde gepusht. `ANDROID_USER_HOME` und
`ANDROID_AVD_HOME` gelten sowohl beim Erzeugen als auch beim Starten des AVD;
Verzeichnis vor SDK-Setup angelegt, Registrierung vor dem Start geprüft. Bash-
Syntax und Repository-Gates PASS. Neuer tatsächlicher CI-Lauf: 34252821287.
Kein Android-CI-PASS vor dessen erfolgreichem Abschluss behauptet.


## Erneute Nutzerpause / r80

Auf ausdrücklichen Wunsch wegen Weekly-Budget keine neuen Tests oder Aufgaben.
r80 regulär abgeschlossen: BUILD SUCCESSFUL nach 10 min 19 s, 216 Tasks
(29 ausgeführt), App-Lint Debug/Release PASS. 145 Quell-/Buildhashes vor/nach
identisch. APK-Hashes in `apks-r80.json`, noch nicht installiert. Letzte
vollständige App-Geräteprüfung bleibt r77 mit 180 PASS plus fünf Opt-in-Skips.
Nachher-Screenshot, Releaseaudit des finalen Hashes und erneuter CI-Lauf NOT_RUN.
Alle Subagenten sind abgeschlossen. Aktive Übergabe oben in HANDOFF.
