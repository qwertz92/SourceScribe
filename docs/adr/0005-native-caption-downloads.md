# ADR 0005: Untertitel über den verifizierten Extractor beschaffen

7. September 2026. Der echte Android-Metadatenlauf für `jNQXAC9IVRw`
lieferte sowohl direkte `api/timedtext`-URLs als auch HLS-Untertitel über
`manifest.googlevideo.com/api/manifest/hls_timedtext_playlist/`. Der bisherige
Parser wies deshalb selbst die Audio-Auflösung ab.

Der vorhandene yt-dlp-Downloader übernimmt direkte und segmentierte Untertitel.
SourceScribe benötigt dadurch keinen zweiten HLS-Parser. Feste Argumente sperren
Audio-Downloads, Plugins, Remote-Komponenten, automatische Updates und Shell-Hooks.
Quelle, ausgewählte Sprache, Generation und Format werden gebunden; private
Ausgabepfade, Dateianzahl, Bytes und Laufzeit bleiben begrenzt. Der zurückgemeldete
Video-Identifier muss vor Verarbeitung der Untertitel zur bestätigten Quelle passen.

Nur der konkrete YouTube-HLS-Untertitelendpunkt erweitert die Metadaten-Allowlist.
Uploader- und Auto-Spuren werden durch getrennte Download-Flags gewählt. Ein
nicht mehr vorhandener Track darf nicht durch eine andere Sprache oder Generation
ersetzt werden. HLS-Zusammenfügung wird in der Herkunft ausdrücklich vermerkt.
Als optionale rohe Ausgabe gilt das erhaltene Extractorformat vor dem Parser und
der SourceScribe-Normalisierung; bei HLS ist dies die vom Extractor zusammengesetzte
VTT-Datei, keine Behauptung unveränderter einzelner Netzwerkfragmente.

Die Umsetzung und ihre Android-/Fixture-Nachweise werden im Testbericht festgehalten.
