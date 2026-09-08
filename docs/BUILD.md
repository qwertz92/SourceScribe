# Build und persönliche Auslieferung

SourceScribe baut mit Java 17, dem eingecheckten Gradle Wrapper 9.7.1, AGP 9.4.0 sowie compile/target SDK 37. Die Android-Module sind `:app` und `:extractor`; `:core` ist ein JVM-Modul. CI installiert die Build Tools 37.0.0 und 36.0.0, damit beide für Android-/Native-Prüfungen vorhanden sind.

## Lokaler Build

Voraussetzungen sind ein Android SDK mit `platforms;android-37.0`, Build Tools 37.0.0/36.0.0 sowie ein JDK 17. Der reproduzierbare lokale Einstieg verwendet einen einzelnen Build-Worker, einen repo-lokalen Gradle-Cache und einen Lock gegen parallele Builds:

```text
timeout 1500 tools/build-local.sh :core:test :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease :extractor:lintDebug :extractor:lintRelease
```

Das Skript legt seine Daten unter `.local-tools/` ab und schreibt keine globale Gradle-Konfiguration. Den Debug-Build erzeugt es mit der App-ID `app.sourcescribe.debug` und den Release-Build mit `app.sourcescribe`. Provider-Schlüssel, Live-Aufrufe, ADB-Installationen und Instrumentationstests sind in diesem Befehl nicht enthalten. Fixture- und Android-Integrationshilfen bleiben auf `androidTest` beschränkt.

Für einzelne Aufgaben darf derselbe Wrapper-Aufruf verwendet werden, zum Beispiel `tools/build-local.sh :app:lintDebug`. Die konkreten lokalen Ergebnisse und nicht ausgeführten Geräte-/Providerprüfungen gehören in einen datierten Bericht unter `docs/reports/`; dieser Text behauptet keinen Live-Nachweis.

## GitHub Actions

`.github/workflows/android.yml` läuft ohne Secrets und ohne APK-Veröffentlichung. Die Actions sind auf unveränderliche, am 7. September 2026 gegen die offiziellen Release-Refs geprüfte Commits gepinnt:

- `actions/checkout` v7.0.1 — [`3d3c42e5aac5ba805825da76410c181273ba90b1`](https://github.com/actions/checkout/releases/tag/v7.0.1)
- `actions/setup-java` v6.0.0 — [`dd06d9cba3e5552c54d9f8ea23572deb30010f7c`](https://github.com/actions/setup-java/releases/tag/v6.0.0)
- `android-actions/setup-android` v4.0.1 — [`40fd30fb8d7440372e1316f5d1809ec01dcd3699`](https://github.com/android-actions/setup-android/releases/tag/v4.0.1)
- `actions/cache` v6.1.0 — [`55cc8345863c7cc4c66a329aec7e433d2d1c52a9`](https://github.com/actions/cache/releases/tag/v6.1.0)

Der CI-Cache umfasst nur den Wrapper-Download und Gradle-Abhängigkeitsdateien unter `.ci-gradle/`; Build-Ausgaben und ein globaler Benutzer-Cache werden nicht gespeichert. Der Workflow enthält JVM-Tests, Debug-/Release-APKs, beide Android-Test-APKs sowie vollständiges Debug-/Release-Lint beider Android-Module mit `--no-daemon --max-workers=1`.

AVD-Erzeugung und Emulator verwenden dasselbe explizite `ANDROID_AVD_HOME` unter `.ci-android/avd`. Das Verzeichnis wird vor dem SDK-Setup angelegt; nach `avdmanager create` werden die INI-Datei und der registrierte AVD-Name geprüft. Damit hängt der Start nicht von unterschiedlichen Standardpfaden der beiden Android-Werkzeuge ab. Bei einem Fehler bleibt der Exitcode erhalten und der Workflow gibt den Emulator-Logauszug sowie die ADB-Geräteliste aus. Zusätzlich werden Fehlerknoten aus den JUnit-XML-Berichten und das Ende der UTP-Testlogs ausgegeben, damit Installations- oder Runnerfehler diagnostizierbar bleiben.

Anschließend startet er einen zeitlich begrenzten Android-37-Emulator mit dem offiziellen Image `system-images;android-37.0;google_apis_ps16k;x86_64`, 4 GiB RAM und zwei CPU-Kernen. Vor `:app:connectedDebugAndroidTest :extractor:connectedDebugAndroidTest` wird die tatsächliche Seitengröße 16384 geprüft. Die Emulator-Konfiguration folgt den offiziellen [Startoptionen](https://developer.android.com/studio/run/emulator-commandline) und der [Hardware-/Grafikbeschleunigung](https://developer.android.com/studio/run/emulator-acceleration); Linux-GitHub-Runner unterstützen [Android-Hardwarebeschleunigung](https://docs.github.com/en/actions/reference/runners/github-hosted-runners). Eine nötige KVM-Zugriffsfreigabe gilt nur dem Benutzer dieses kurzlebigen CI-Runners.

Die Tests nutzen synthetische Daten und lokale HTTPS-Server. Die Netzdiagnose darf öffentliche DNS-/TLS-Erreichbarkeit prüfen; reale STT-Anfragen bleiben ausgeschlossen. Update-Fixtures sind aktiviert, echte Update-Downloads, YouTube-Live-Quellen und die interaktiven UI-/Prozessneustart-Fixtures erfordern zusätzliche explizite Testargumente und laufen nicht automatisch. Dieser Workflow ist implementiert; tatsächlich erfolgreiche CI-Läufe werden erst mit ihrer Run-ID im Testbericht als Nachweis geführt.

## Persönlicher Release-Pfad

Der Release-Build wird zuerst als unsigned APK gebaut. `tools/sign-release.sh` richtet genau diese Datei mit den offiziellen Android Build Tools 37.0.0 aus, signiert sie mit `apksigner` und prüft die Signatur. Das Keystore bleibt außerhalb des Repositorys; das Skript lehnt einen fehlenden, relativen oder im Repository liegenden `KEYSTORE_PATH` ab. Es erzeugt oder verteilt keinen privaten Schlüssel.

Keystore-Passwort und Schlüsselpasswort werden ausschließlich über Umgebungsvariablen gelesen. Die Variablennamen werden an `apksigner` als `env:`-Passwörter übergeben, damit kein Passwort als Argument oder Ausgabe erscheint:

```text
export KEYSTORE_PATH=/absolute/path/outside/SourceScribe/sourcescribe-release.jks
export KEY_ALIAS=sourcescribe
export KEYSTORE_PASSWORD='(lokal setzen)'
export KEY_PASSWORD='(lokal setzen)'
tools/sign-release.sh app/build/outputs/apk/release/app-release-unsigned.apk /absolute/path/outside/SourceScribe/sourcescribe-release.apk
unset KEYSTORE_PATH KEY_ALIAS KEYSTORE_PASSWORD KEY_PASSWORD
```

Das Ziel muss neu sein; ein vorhandenes Ausgabefile wird vor jeder Verarbeitung abgewiesen. Der unsigned Input bleibt erhalten. Für persönliche Updates denselben extern verwahrten Release-Key und fortlaufende Versionscodes verwenden. Dieser Pfad veröffentlicht kein APK und installiert nichts auf einem Gerät.
