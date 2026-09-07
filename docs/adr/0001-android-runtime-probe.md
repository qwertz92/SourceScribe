# ADR 0001: Android-Runtime zuerst praktisch prüfen

Datum: 7. September 2026. Status: P0 in Prüfung, keine Freigabe.

Der untersuchte Kandidat ist `io.github.junkfood02.youtubedl-android` 0.18.1
(`library` und `ffmpeg` von Maven Central). Quell-JAR und AAR liegen der Prüfung
zugrunde. Der Wrapper liefert Python, QuickJS und FFmpeg als Android-Bestandteile;
die tatsächlichen Versionen und Ausführung müssen die Instrumentation belegen.

Wir verwenden zunächst seine gebündelte Initialisierung für den P0-Nachweis.
Die eigene Ausführungsgrenze bekommt ausschließlich kontrollierte Argumentlisten,
Ausgabe- und Zeitlimits. Der Wrapper-Quellcode enthält unbeschränkte StringBuffer
für Prozessausgabe und eine Shell-Pipeline zur Prozessbaumbeendigung. Diese
Ausführungs- und Updatefunktionen werden nicht ungeprüft zur Produktgrenze.

Der Wrapper-Updater ist bis zum nachgewiesenen Vertrauensweg gesperrt. Hot-Updates
benötigen verifizierte Paketidentität/Kompatibilität und getrennte Slots; das
Maven-Artefakt allein beweist keinen sicheren dynamischen Updateweg.

Buildkandidat: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.4.20, Compose BOM 2026.08.00,
compile/target SDK 37, min SDK 29, JDK 17. Die konkreten Maven-Metadaten und
[AGP-Kompatibilität](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
wurden abgerufen. Der gemeinsame Build prüft erst ihre Kombination.

P0-Gerät: API 37, x86_64, 16-KB-Seiten. Zusätzlich ARM64-Binaries statisch prüfen.
Keine Behauptung einer physischen ARM64-Gerätefreigabe.
