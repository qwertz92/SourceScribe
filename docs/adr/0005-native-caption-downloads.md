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
Bei der minimalen Downloadrezeptur bestätigt dieser Marker die ausgeführte Rezeptur,
nicht unabhängig die Identität des Serverinhalts. Die Zuordnung stammt aus den
zuvor über HTTPS aufgelösten Metadaten; opaque HLS-IDs bieten keine zusätzliche
Video-ID-Prüfung.

Nur der konkrete YouTube-HLS-Untertitelendpunkt erweitert die Metadaten-Allowlist.
Uploader- und Auto-Spuren werden durch getrennte Download-Flags gewählt. Ein
nicht mehr vorhandener Track darf nicht durch eine andere Sprache oder Generation
ersetzt werden. HLS-Zusammenfügung wird in der Herkunft ausdrücklich vermerkt.
Als optionale rohe Ausgabe gilt das erhaltene Extractorformat vor dem Parser und
der SourceScribe-Normalisierung; bei HLS ist dies die vom Extractor zusammengesetzte
VTT-Datei, keine Behauptung unveränderter einzelner Netzwerkfragmente.

Die Umsetzung und ihre Android-/Fixture-Nachweise werden im Testbericht festgehalten.

## Bindung ohne erneute Quellenauflösung

Der Download erhält über `--load-info-json` eine private, minimale Datei mit
Video-ID, konstantem Titel und genau der geprüften URL/Sprach-/Formatkombination.
Sie enthält absichtlich weder `webpage_url` noch Medienformate oder andere Tracks:
yt-dlp könnte bei vorhandenem `webpage_url` nach einem Downloadfehler selbständig
neu extrahieren. Der Abruf bekommt auch kein zweites URL-Argument. Die Kombination
`--ignore-no-formats-error --skip-download` erlaubt diese reine Untertitelrezeptur.
Direkte Tracks benutzen HTTPS; der erlaubte HLS-Endpunkt explizit `m3u8_native`.
Beide Ausgabevorlagen liegen in demselben begrenzten privaten UUID-Verzeichnis.
Ein vorhandenes `v`-Queryargument muss eindeutig zur bestätigten Video-ID passen.
Querynamen werden vor der Prüfung dekodiert; doppelte `v`/`lang`/`tlang`-Parameter
werden abgewiesen. Die effektive Sprache (`tlang`, sonst `lang`) muss zum
ausgewählten Track passen, einschließlich der Originalspur-Endung `-orig`.
Die eingelesene URL bleibt damit auch die abgerufene URL; abgelaufene URLs führen
zu einem sichtbaren Fehler, keiner stillen Spuränderung.
