# Third-party notices

Stand: 7. September 2026. Dieses Dokument ist ein gezieltes Inventar der im
aktuellen SourceScribe-Baum sichtbaren direkten Abhängigkeiten, der gebündelten
Extractor-Artefakte und der nachgewiesenen WebP-Dateien. Es ist noch kein
vollständiges, veröffentlichungsfertiges Lizenz- oder Corresponding-Source-
Angebot.

## Eigenes Projekt

Die eigenen SourceScribe-Anteile stehen nach ausdrücklicher Projektentscheidung
unter GPL-3.0-only; der vollständige Text liegt in [`LICENSE`](LICENSE). Diese
Projektwahl ist keine Rechtsbehauptung, dass jede UI-Zeile unabhängig von den
konkreten Abhängigkeiten zwingend unter GPL stehen müsse. Die endgültige
Vertriebs- und Kombinationsprüfung bleibt offen.

## Gebündelte Extractor-Komponenten

| Komponente | Version/Nachweis | Lizenz-/Quellhinweis |
|---|---|---|
| `youtubedl-android` AARs (`library`, `common`, `ffmpeg`) | 0.18.1; lokale AARs aus Gradle-Cache, FFmpeg-AAR SHA-256 `0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49` | Die POMs nennen GPL-3.0; [Upstream-Repository](https://github.com/yausername/youtubedl-android), [POM-Lizenzreferenz](https://www.gnu.org/licenses/gpl-3.0.en.html) |
| FFmpeg im FFmpeg-AAR | 7.1.1; Artefaktmetadaten und Buildflags `--enable-gpl --enable-version3` | [FFmpeg legal](https://ffmpeg.org/legal.html), [offizielles Release-Archiv](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz), erwartete Quelle SHA-256 `733984395e0dbbe5c046abda2dc49a5544e7e0e1e2366bba849222ae9e3a03b1`; bitidentische AAR-Provenienz ist nicht bewiesen |
| x264, VMAF, SVT-AV1 und weitere FFmpeg-Codecs | Im FFmpeg-Buildpfad berührt; einzelne Corresponding-Source-/Lizenzzuordnung noch offen | x264-Quellhash ungelöst; VMAF-/SVT-Lizenzinventar unvollständig; keine Veröffentlichung daraus ableiten |
| Python-Runtime | 3.12.11 laut untersuchtem AAR-Artefakt | [Python license](https://docs.python.org/3/license.html), PSF-2.0; gebündelte AAR-Noticeprüfung noch nicht vollständig |
| QuickJS-Runtime | 2025-04-26 laut untersuchtem AAR-Artefakt | [QuickJS license/source](https://bellard.org/quickjs/); gebündelte Noticeprüfung noch nicht vollständig |
| OpenSSL | Version im aktuellen AAR-Review nicht separat festgestellt | [OpenSSL license](https://www.openssl.org/source/license.html); keine Versions- oder Corresponding-Source-Zuordnung behauptet |
| yt-dlp | Zipapp 2026.08.19, SHA-256 `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6` | [yt-dlp LICENSE](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE); Unlicense-Angabe aus Upstream muss mit dem konkreten Zipapp-Inhalt abgeglichen werden |
| yt-dlp-ejs | EJS 0.8.0 im geprüften Zipapp | [EJS LICENSE](https://github.com/yt-dlp/ejs/blob/master/LICENSE); konkrete gebündelte Notice noch nicht separat archiviert |
| Bouncy Castle | `bcpg` 1.85, `bcprov` 1.85.2 | [Bouncy-Castle-Lizenz](https://www.bouncycastle.org/licence.html); JVM-Kryptobibliothek, keine native AAR-Komponente |
| WebP | 1.6.0-rc1, zehn neu gebaute SOs für `arm64-v8a`/`x86_64`; Quellarchiv SHA-256 `a8822fbd36e43fa1e5a83a7104d86c5be8692cee1e323d57030b5562ef884a8a` | BSD 3-Clause in [`extractor/src/main/assets/webp/COPYING`](extractor/src/main/assets/webp/COPYING), ergänzend [`AUTHORS`](extractor/src/main/assets/webp/AUTHORS) und [`PATENTS`](extractor/src/main/assets/webp/PATENTS); [Upstream-Tag](https://github.com/webmproject/libwebp/tree/v1.6.0-rc1) |

Der FFmpeg-AAR enthält keine Dateien mit `LICENSE`, `NOTICE`, `COPYING` oder
`README`; dessen POM nennt lediglich GPL-3.0. Die WebP-Begleitdateien sind
deshalb bewusst separat im Asset-Verzeichnis versioniert.

## Direkte und transitive JVM-/Android-Abhängigkeiten

Die Versionen stehen in [`gradle/libs.versions.toml`](gradle/libs.versions.toml)
und wurden aus dem lokalen Gradle-Cache gegen die aufgelösten POMs betrachtet.
Die AndroidX-, Kotlin-/kotlinx-, OkHttp-, Hilt-, Room-, WorkManager-, DataStore-
und DocumentFile-Komponenten werden über ihre jeweiligen Apache-2.0-
Upstreamhinweise geführt. Bouncy Castle (`bcpg` 1.85, `bcprov` 1.85.2) folgt der
[Bouncy-Castle-Lizenz](https://www.bouncycastle.org/licence.html). JUnit 4.13.2
ist test-only und folgt der [EPL 1.0](https://www.eclipse.org/legal/epl-v10.html).

Die lokale Auflösung enthielt 73 paketierte transitive Artefakte und 8 von den
Metadaten aus erreichbare externe Lizenz-/Quellverweise. Diese Zahlen sind ein
Prüfumfang für den aktuellen Stand, keine Zusicherung, dass damit jedes
enthaltene FFmpeg-Codec- oder Generatorartefakt vollständig erfasst ist.

## Veröffentlichungsgrenze

Vor einer öffentlichen APK-Freigabe müssen Corresponding Source, Notices,
Codec- und Lizenzzuordnung für den konkreten FFmpeg-AAR geschlossen werden.
Insbesondere sind der x264-Quellhash sowie VMAF und SVT-AV1 noch nicht
ausreichend belegt. Bis dahin dürfen die eigenen Source-Dateien und die
geprüften WebP-Dateien öffentlich liegen; ein öffentliches APK-Release ist
`BLOCKED`.
