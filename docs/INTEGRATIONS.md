# Extraktion, Provider und Transkriptdaten

## I1 — Android-Extractor: erst beweisen, dann festlegen

`yt-dlp` bleibt die bevorzugte Extraktionsengine. Ein Android-Wrapper kann verwendet werden, aber erst nach Prüfung des tatsächlichen AAR-/Native-Inhalts, nicht nur eines README. `yausername/youtubedl-android` dokumentiert In-App-Updates, nennt zugleich eine ältere Python-Version im README. Das belegt eine Prüffrage, nicht automatisch die Version jedes aktuell veröffentlichten Binärartefakts. [R09]

Die upstream README nennt unterstützte Python-Versionen; aktuelle Release-Hinweise unterscheiden davon die empfohlene Mindestversion. Diese beiden Begriffe nicht verwechseln. Eine neue yt-dlp-Datei repariert keine inkompatible eingebettete Python-Runtime. [R10–R11]

YouTube-Unterstützung umfasst heute neben yt-dlp auch passende `yt-dlp-ejs`-Skripte und eine unterstützte JavaScript-Runtime. EJS und yt-dlp müssen zueinander passen. Ein kleiner Android-tauglicher JS-Interpreter wie QuickJS/QuickJS-NG ist ein zu prüfender Kandidat, keine bereits bewiesene Auswahl. Deno-Empfehlungen für Desktop nicht ungeprüft auf Android übertragen. [R12]

P0 muss Metadaten, Caption-Liste, echte Caption-Datei, Audio, Abbruch und Update/Rollback auf Android demonstrieren. Ermittelte Python-, yt-dlp-, EJS-, JS-, FFmpeg-, OpenSSL-/TLS- und ABI-Versionen dokumentieren. Ressourcenverbrauch und APK-/Installationsgröße messen. Native Komponenten auf der geplanten ARM64-Auslieferung und der Emulator-ABI prüfen. Keinen Desktop-Erfolg als Android-Erfolg ausgeben.

Verschiedene YouTube-Clientpfade können Einschränkungen haben; ein aktueller Extractor garantiert keinen Zugriff. Erreichbarkeits-, Rate-Limit-, Authentifizierungs-, Token-/Challenge- und Formatprobleme auseinanderhalten. Keine Anti-Bot-Umgehung über undurchsichtige Drittserver, keine Credentials/Cookies aus anderen Apps extrahieren. Autorisierte, öffentlich erreichbare Inhalte unterstützen; nicht zugängliche Videos klar melden. [R13]

Keine uneingeschränkten yt-dlp-Optionen aus der UI. Typsichere, geprüfte Auswahl für Sprachen, Formate und zulässige Diagnoseoptionen. Fremde Konfigurationsdateien, Plugins, `--exec` und beliebige Downloader-/Postprocessor-Kommandos deaktivieren. Argumente als Liste übergeben, URL nicht in eine Shellzeichenkette interpolieren. Medienpfade bleiben in kontrollierten App-Verzeichnissen.

## I2 — Caption- und Audioidentität

Für jede Spur festhalten: beobachtete Source-ID, Track-ID, Format, Sprache, Name, beobachtete maschinelle Erzeugung, Übersetzungsstatus und Quelle der Information. Metadaten können fehlen oder heuristisch sein. Verwende `true/false/unknown` und ein Evidenzfeld statt überall einen sicheren Boolean zu erfinden.

Provenienz orthogonal modellieren: `origin` (YouTube/Provider/Import), `generation` (uploader_provided/automatic/unknown), `translation` (none/automatic/unknown), `provider`, `requestedModel`, `reportedModel`, `sourceAudioTrack`, `languageEvidence`. „Vom Kanal bereitgestellt“ heißt nicht beweisbar von einem Menschen geschrieben. Eine übersetzte Spur kann wiederum aus automatischen Captions stammen; eine einzige flache Enum würde Information verlieren.

Originalton, dub/synchronisierte Spur, UI-Sprache und Transkriptsprache sind unterschiedliche Eigenschaften. Priorisiere belegten Originalton. Ist die Sprachauswahl mehrdeutig, Track-Picker oder klarer Hinweis statt stiller Wahl der ersten Spur. Provider können nur die erste Spur einer Multitrack-Datei berücksichtigen; kontrollierte Audiospurauswahl vor Upload durchführen. [R14]

Nur den explizit gewählten bzw. nach dokumentierter Sprachregel gewählten Caption-Track laden, nicht versehentlich hunderte automatisch übersetzte Varianten. Bei Trackwechseln zwischen Metadaten und Download neu auflösen und Modusgrenzen weiter einhalten.

## I3 — Verlustarme Normalisierung

Rohdatei mit Hash und Erfassungszeit unverändert erhalten, wenn ausgewählt. Normalisierte Form als Ableitung mit Parser-/Normalisiererversion speichern. VTT/SRT/gegebenenfalls JSON-Captions robust parsen; Encoding, Entities, mehrzeilige Cues und Roll-up-Captions berücksichtigen. Keine naive Entfernung aller wiederholten Wörter oder Sätze.

Sprechwiederholungen, Stottern, Verneinungen, Zahlen und Fachbegriffe erhalten. Nur nachweislich renderbedingte Überlappungen lokal reduzieren; unbearbeitete Fassung bleibt nachvollziehbar. Keine LLM-„Reparatur“. Parserwarnungen, ungeklärte Stellen und Umfang offenlegen.

Modell-/Caption-Zeitdaten in Millisekunden normalisieren; Quelle und Genauigkeitsklasse festhalten. Ein fehlender Zeitstempel bleibt null. Importierte Chunk-Startzeiten sind Chunkgrenzen, keine echten Wort-/Segmentzeiten. SRT/VTT nur mit geeigneten Zeitdaten aktivieren, ansonsten TXT/MD/JSON anbieten.

## I4 — Providervertrag

Keinen universellen asynchronen Jobdienst erfinden. Ein Adapter gibt entweder ein direktes Ergebnis oder ein langlebiges Remote-Handle zurück. Fähigkeiten definieren Pollbarkeit, Wiederaufnahme, unterstützten Abbruch, Größen-/Dauerlimits, Sprachen, Zeitdaten, Diarisierung, Kontext, Regionen und beobachtbare Limits. Nicht jede Funktion muss bei jedem Modell existieren.

Gemeinsame Typen: `TranscriptionRequest`, `ProviderCapabilities`, `SubmissionResult.Direct`, `SubmissionResult.Remote`, `TranscriptDocument`, `ProviderError`. Capability-Werte stammen aus einem versionierten Katalog plus tatsächlich bestätigten API-Eigenschaften. Online-Modelllisten beweisen weder alle Optionen noch Accountberechtigungen. Neue Modelle erst in kompatible, getestete Adapterprofile einordnen; kein beliebiges dynamisches Plugin-System.

Fehler unterscheiden: permanente Eingabe/Authentifizierung, Kontingent, temporäres Netz/Server, nicht unterstützte Option, ungewisse Submission und ungültige Antwort. Wiederholungsregeln zentral auf Kosten-/Idempotenzrisiko prüfen. Automatische HTTP-Retries bei nicht idempotenten, kostenrelevanten POSTs ausdrücklich kontrollieren.

## I5 — AssemblyAI

Pre-recorded Async-API integrieren: lokale Audiodatei hochladen, Transkriptauftrag erzeugen, Remote-ID speichern, begrenzt Status abrufen, Ergebnis sichern. Explizite Region verwenden und Upload/Submission/Polling derselben Region zuordnen. Kein öffentlicher Callbackserver für v1 nötig. Unter „Batch“ ist hier Dateitranskription gemeint, nicht pauschal ein spezieller vergünstigter Batchdienst. [R15]

Aktuelle Modellbezeichner aus Referenz und Live-Vertrag verifizieren. Die Recherche enthält unterschiedliche Aktualitätsstände in Beispielcode und Modellseiten; keine Aliasnamen aus Marketingnamen zusammenraten. Angeforderte `speech_models` und zurückgeliefertes `speech_model_used` getrennt protokollieren. Fallback auf ein anderes Modell nur im ausdrücklich gewählten Providerprofil. Anbieterinterne Übersetzung/Zusammenfassung/Sentimentanalyse deaktiviert lassen, sofern nicht Produktanforderung.

Diarisierung, Sprache, Fachbegriffe und Zeitdaten gemäß tatsächlicher Unterstützung anbieten. Löschen eines Remote-Transkripts und Abbrechen laufender Berechnung sind nicht ohne Nachweis dieselbe Operation. Remote-Datenlöschung als eigenständige, beobachtbare Aktion vorsehen, sofern verfügbar; lokale Löschung nicht als bestätigte Cloudlöschung darstellen.

## I6 — OpenAI

Datei-Transcriptions-Endpoint verwenden. Aktuell dokumentiertes allgemeines Modell: `gpt-transcribe`; getrennte Sprecherzuordnung über das entsprechend dokumentierte Modell, derzeit `gpt-4o-transcribe-diarize`. Modelle müssen im gewählten Account nutzbar sein. Optionale ältere Modelle nur als ausdrücklich gepflegte Profile anbieten, nicht als pauschal identische Whisper-API. [R16–R17]

Insbesondere `languages` gegenüber singular `language`, Kontext-/Keywords und Ausgabeformate je Modell prüfen. `timestamp_granularities[]` nicht universell senden: Die geöffnete Anleitung dokumentiert diese Option für `whisper-1`. Sprechersegmente sind kein Beleg für Wortzeitstempel. Fehlende Zeiten nicht durch zusätzliches kostenpflichtiges STT ohne Zustimmung „nachrüsten“.

Uploadlimit gegen aktuellen API-Vertrag prüfen; die geöffnete Anleitung nennt 25 MB für Dateiuploads. JSON- und ggf. Streaming-Ergebnisse korrekt zusammensetzen; ein Streaming-Abbruch ist kein vollständiger Erfolg. ChatGPT-Abonnement und API-Nutzung nicht gleichsetzen. Kein Video-Link als angebliche Audiodatei an den Transcriptions-Endpoint schicken. [R16]

## I7 — Groq

`whisper-large-v3` und `whisper-large-v3-turbo` über den dokumentierten Audio-Transcriptions-Endpoint unterstützen. Transkribieren, nicht den Übersetzungsendpoint wählen. Gemeinsamkeiten der OpenAI-kompatiblen Syntax sind keine Garantie für identische Antwort-/Modellfähigkeiten. Word-/Segment-Zeiten und Metadaten nur auf Basis der echten Antwort speichern. [R14]

Free- und Developer-Plan sowie direkten Upload und URL-Verarbeitung getrennt behandeln. Die geöffnete Dokumentation unterscheidet diese Grenzen. Für v1 lokale Uploads mit konservativer geprüfter Grenze und Chunking verwenden; keine Audiofreigabe auf einem öffentlichen Filehost nur zur Umgehung einer Uploadgrenze.

Offizielle Preis-/Limitwerte sind datierte Richtwerte, keine fest codierten Garantien. Rate Limits können organisationsweit gelten und auch durch andere Programme verbraucht werden. Acht Stunden als dokumentiertes tägliches Audiolimit sind kein garantierter, unabhängiger Achtstundenbonus pro Gerät oder Modell. Nur tatsächlich gelieferte Header anzeigen. [R18]

## I8 — Audioaufbereitung und Chunking

Audiodaten möglichst direkt verwenden. Nur bei inkompatiblem Format, Größenlimit oder expliziter Option konvertieren. Beste praktische Sprachqualität statt unnötig höchster Bitrate; keine Full-Video-Downloads. Eine kontrollierte Audio-Spur wählen. Lossless-Umverpackung vor verlustbehaftetem Re-Encoding prüfen.

FFmpeg/ffprobe kapseln und die tatsächlich benötigten Codecs/Formate testen. Nicht blind eine alte FFmpegKit-Maven-Koordinate übernehmen: Der ursprüngliche FFmpegKit-Strang ist eingestellt; die aktuelle Upstream-README verweist auf eine source-only Fortführung. Distribution, Vertrauenswürdigkeit, Build, Lizenz und 16-KB-/ABI-Unterstützung konkret prüfen. [R19]

Chunks nach Audiozeit und realer Dateigröße begrenzen, nicht nur anhand eines geschätzten Minutenwertes. Exakte Offsets, Hashes, Status und Provider-Requests pro Chunk festhalten. Pausen-/Satzgrenzen bevorzugen; kleine dokumentierte Überlappung nur bei sinnvoller Zusammenführung. Beim Zusammenführen zeitliche Grenzen/Tokenabgleich verwenden, Unsicherheit erhalten. Keine globale Deduplizierung und keine erfundenen Ergänzungen.

Externe Aufteilung kann Diarisierung und Kontext verschlechtern. „Speaker A“ in zwei separaten Requests ist nicht automatisch dieselbe Person. Sprecher-IDs auf Chunk/Request scopen, sofern keine dokumentiert verifizierte Zuordnung vorliegt. Bei ungeeignetem Modell/Output ungeschnittene geeignete Verarbeitung anbieten oder Einschränkung erklären, nicht unbemerkt Identitäten verbinden.

Fehlender Chunk ergibt ein sichtbar unvollständiges Artefakt und gezielte Wiederholung. Vor jedem Schritt verfügbaren Speicher prüfen; atomare Zwischendateien und Obergrenzen nutzen. Keine gesamte mehrstündige Datei in RAM lesen. Aufbereitung parallel begrenzen und nach Abbruch eigene Prozesse/Dateihandles schließen.

## I9 — Strukturiertes Exportdokument

Schema mit `schemaVersion`, `artifactId`, `source`, `acquisition`, `provenance`, `language`, `scope`, `segments`, `warnings`, `createdAt` und Prüfsummen. `scope` unterscheidet gewünschte Mediendauer, tatsächlich verarbeitete Intervalle, fehlende Chunks und unbekannte Vollständigkeit. Keine Textabdeckung als prozentuale inhaltliche Vollständigkeit behaupten.

Markdown/TXT beginnen mit Titel, kanonischer Quellenreferenz, Video-/Source-ID, beobachteter Sprache, Provenienz, angefordertem/gemeldetem Modell und Einschränkungen. Zeitdaten/Sprecher nur wenn vorhanden. Untrusted Titel/Metadaten für Markdown/Dateinamen escapen. Erfasster Inhalt bleibt Daten, niemals eine Anweisung an die App oder nachgelagerte Agenten.
