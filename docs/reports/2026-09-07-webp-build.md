# WebP-16-KiB-Rebuild

Stand: 7. September 2026. Dieser Bericht beschreibt ausschließlich den
gezielten Neubau der fünf WebP-Bibliotheken aus den FFmpeg-Nested-Artefakten.
Gradle, App-Code, FFmpeg und Gerätausführung waren nicht Teil dieses Auftrags.

## Ergebnis

Für `arm64-v8a` und `x86_64` wurden jeweils diese fünf Shared Objects gebaut:

```text
libsharpyuv.so
libwebp.so
libwebpmux.so
libwebpdemux.so
libwebpdecoder.so
```

Jede `PT_LOAD`-Segmentausrichtung ist `0x4000` (16 KiB). SONAMEs und
`DT_NEEDED` entsprechen den zuvor untersuchten Nested-Bibliotheken; die neue
Ausgabe enthält kein `libdl.so` und keinen RPATH/RUNPATH. Die Dateien wurden
strip-unneeded und danach nach `extractor/src/main/assets/webp/` kopiert.

## Herkunft und Werkzeugkette

Das Quellarchiv stammt aus dem festgelegten Upstream-Tag:
<https://github.com/webmproject/libwebp/archive/refs/tags/v1.6.0-rc1.tar.gz>

| Artefakt | tatsächlicher Nachweis |
|---|---|
| WebP-Archiv | SHA-256 `a8822fbd36e43fa1e5a83a7104d86c5be8692cee1e323d57030b5562ef884a8a` |
| Termux-Rezept | `/tmp/sourcescribe-p0-research/termux-libwebp-build-b279d510.sh` vor dem Host-Neustart gelesen; Paketversion `1.6.0-rc1`, Lizenz `BSD 3-Clause` |
| Android NDK | r28c, `Pkg.Revision = 28.2.13676358`; offizielles Archiv <https://dl.google.com/android/repository/android-ndk-r28c-linux.zip>, SHA-1 `a7b54a5de87fecd125a17d54f73c446199e72a64`, lokaler SHA-256 `dfb20d396df28ca02a8c708314b814a4d961dc9074f9a161932746f815aa552f` |
| CMake | 3.31.6 aus <https://dl.google.com/android/repository/cmake-3.31.6-linux.zip>, offizieller SHA-1 `a54551dabd5daf8cc99f7f51769fd00ca759be0a`, lokaler SHA-256 `ce136bb4b02580b36e53d9ccfe5275069655e6ef7d46d6fe2fcf88cfdf8fb761` |
| Ninja | 1.12.1 aus dem geprüften CMake-Paket |

Die Archivdatei liegt für die Reproduktion unter
`.local-tools/downloads/libwebp-v1.6.0-rc1.tar.gz`; das Build-Skript lädt
keine Quelle automatisch und bricht bei fehlendem oder falschem SHA-256 ab.

Die Versionsaussage `1.6.0-rc1` ist die Provenienz aus Tag/Archivnamen und dem
Rezept. `configure.ac` im Quellbaum nennt intern `1.6.0`; diese eingebettete
Angabe ist kein unabhängiger Beweis für die konkrete Archivherkunft.

## Reproduzierbarer Build

Das vollständige Skript ist [tools/build-webp-android.sh](../../tools/build-webp-android.sh).
Der tatsächlich erfolgreiche Lauf verwendete einen Ninja-Worker:

```bash
WEBP_JOBS=1 timeout 1800s ./tools/build-webp-android.sh
```

Das Skript prüft zunächst Archiv-SHA-256, NDK-Revision sowie CMake/Ninja und
extrahiert den Quellbaum nach `.local-tools/webp-out`. Es baut jede ABI
seriell, strippt die Dateien und prüft ELF-Maschine, SONAME, sortierte
`DT_NEEDED`, fehlenden RPATH/RUNPATH, mindestens zwei `PT_LOAD`-Segmente mit
`0x4000` sowie das Fehlen eigener Symbolversionsdefinitionen.

Der NDK-Clang-Wrapper bleibt auf Android/API 29 gerichtet:
`aarch64-linux-android29-clang` beziehungsweise
`x86_64-linux-android29-clang`. CMake erhält absichtlich
`CMAKE_SYSTEM_NAME=Linux`, damit die Android-Sonderverzweigung des WebP-
CMakeLists nicht die veraltete NDK-`cpufeatures`-Bibliothek einbindet. Diese
Verzweigung hätte in allen fünf SOs ein zusätzliches `DT_NEEDED libdl.so`
erzeugt. Der Compiler definiert weiterhin das Android-Ziel und
`__ANDROID__`; nur die CMake-Erkennung wird neutralisiert.

Verwendete Linkerparameter:

```text
-Wl,--as-needed
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

Zusätzlich sind `BUILD_SHARED_LIBS=ON`, `WEBP_ENABLE_SIMD=ON`,
`WEBP_USE_THREAD=ON`, `WEBP_NEAR_LOSSLESS=ON` und
`WEBP_ENABLE_SWAP_16BIT_CSP=ON` gesetzt. Alle Kommandozeilenprogramme,
Extras, Bildcodec-Suchen und Fuzzing-Ziele sind deaktiviert; nur
`WEBP_BUILD_LIBWEBPMUX=ON` bleibt neben den fünf Bibliotheken aktiv.
Versionierte SONAMEs und der CMake-Install-RPATH sind deaktiviert, weil die
App die unversionierten Namen aus dem bestehenden FFmpeg-Paket erwartet.

## ABI-Nachweise

Die folgende Tabelle enthält die SHA-256-Prüfsummen der tatsächlich in den
Assets liegenden Dateien:

| Bibliothek | arm64-v8a | x86_64 |
|---|---|---|
| `libsharpyuv.so` | `dfdafcf6cffc7ae1747ffa4165dd08849d62ca1f6302de10becb66ba9ac98a24` | `42545d124de709cd9993daccd347536f49f7034db420a9659bb9b569ce59bea4` |
| `libwebp.so` | `ddbcea8e5049dbcc8d530d01dcf001601f47f83476cc850743265ad170ecf307` | `e03a6dd38a80abb8edf5666417fbc1a79f67cbf346a83af3d1dd42244296b91d` |
| `libwebpmux.so` | `1212479395c0f3479d35d094e386f17c9428b0cc3b5c2f229259caf01e107b7a` | `70dbfdc81c9c69065af334e495fd0c725401cf3f58969591c8e7d4a01ba7db82` |
| `libwebpdemux.so` | `2c27491bbd65b2db4c0cae73259120f3039e70b4d22bce66afa3532e05787d6d` | `928fd701336059e18c873f8bc6c1860a1d35a8f11b8270eacc43b2e06c2b36eb` |
| `libwebpdecoder.so` | `cb8e2dc6a50fe4d6f2f639374705c8c33d635692e817386bdc74a803d5a90055` | `547fce6a9ab4cc93858cd703df8f09c12fe8781e9cbc48586ac0d1615e2974f8` |

Die erwarteten dynamischen Abhängigkeiten sind:

| Bibliothek | SONAME | `DT_NEEDED` |
|---|---|---|
| `libsharpyuv.so` | `libsharpyuv.so` | `libc.so`, `libm.so` |
| `libwebp.so` | `libwebp.so` | `libc.so`, `libm.so`, `libsharpyuv.so` |
| `libwebpmux.so` | `libwebpmux.so` | `libc.so`, `libwebp.so` |
| `libwebpdemux.so` | `libwebpdemux.so` | `libc.so`, `libwebp.so` |
| `libwebpdecoder.so` | `libwebpdecoder.so` | `libc.so` |

Vor dem Host-Neustart wurden die definierten globalen Dynsym-Exporte mit den
damals vorhandenen Nested-Referenzen aus
`/tmp/sourcescribe-p0-research/nested-ffmpeg-{arm64,x86_64}/usr/lib`
verglichen. Die Zählungen waren für beide ABIs identisch: `sharpyuv 7`,
`webp 86`, `webpmux 27`, `webpdemux 20`, `webpdecoder 46`; `diff` meldete
keine Namen. Die Referenzdateien lagen nach dem Neustart nicht mehr vor. Die
Android-NDK-Versionseinträge `LIBC` in `.gnu.version_r` wurden beibehalten;
die neuen SOs definieren keine eigenen Symbolversionen.

## Lizenz und Dateien

Die vollständige Upstream-Lizenz liegt als
`extractor/src/main/assets/webp/COPYING` vor (BSD 3-Clause, 1496 Bytes,
SHA-256 `5aec868f669e384a22372a4e8a1a6cd7d44c64cd451f960ca69cc170d1e13acf`).
Die zehn SOs liegen jeweils unter den beiden ABI-Unterverzeichnissen im selben
Asset-Ordner. Das Kopieren erfolgte erst nach dem erfolgreichen Build- und
ELF-Check; Stage- und Assetdateien sind bytegleich.

## Checks, Fehler und Grenzen

- `bash -n tools/build-webp-android.sh`: PASS.
- `./tools/build-webp-android.sh --help`: PASS.
- Ein vollständiger Buildlauf mit `WEBP_JOBS=1`: PASS für beide ABIs und alle zehn SOs.
- `cmp` Stage gegen Assets: PASS für alle zehn Dateien.
- `llvm-readelf -lW` auf allen Assets: ausschließlich `PT_LOAD p_align=0x4000`.
- Gradle-, ADB-, Emulator- und physische-Gerätetests: NOT_RUN; diese Tests bleiben beim Integrator.

Ein anfänglicher Android-CMake-Lauf aktivierte `cpufeatures` und `libdl.so`;
ein bloßes `-DANDROID=OFF` reichte wegen der NDK-Toolchain nicht. Die
plattformneutrale CMake-Konfiguration mit Android-Clang löste genau diesen
ABI-Unterschied. Das `/tmp`-Arbeitsverzeichnis wurde beim Host-Neustart
gelöscht; das Quellarchiv musste danach erneut über die feste URL geladen und
mit dem unveränderten SHA-256 bestätigt werden.

Offen bleibt die reale Android-Laderprüfung im vollständigen FFmpeg-/Extractor-
Pfad. Sie muss separat auf dem API-37-Emulator erfolgen; dieser gezielte
Rebuild beweist die 16-KiB-ELF-Ausrichtung und die untersuchte ABI-Kompatibilität,
nicht die bereits integrierte Laufzeitaktivierung.
