# Recherche, Quellen und Unsicherheiten

**Prüfstand: 5. September 2026.** Die verlinkten Primärquellen wurden für diese Überarbeitung recherchiert. Es wurden hier weder Providerkonten ausgelesen noch Android-Binaries gebaut oder auf einem Emulator getestet. Marketingseiten, README, Release Notes und API-Schemas können unterschiedlich aktuell sein; implementationskritische Aussagen gegen die ausgewählte Version und den echten Vertrag prüfen.

Die Architekturvorgaben sind Entwurfsentscheidungen. Die folgenden Einträge begründen relevante technische Randbedingungen. Preise und Kontingente werden nicht als ewige Konstanten in Produktregeln übernommen.

## R01 — Native Android-UI

[Android: Jetpack Compose](https://developer.android.com/compose)

Compose ist das offiziell empfohlene moderne Toolkit für native Android-Oberflächen. Daraus folgt nicht automatisch die Architektur jeder App; bei SourceScribe sprechen die Android-spezifischen Integrationen zusätzlich für Kotlin/Compose.

## R02 — Svelte

[Svelte: Overview](https://svelte.dev/docs/svelte/overview)

Svelte ist ein Web-UI-Framework. Für SourceScribe wird keine zusätzliche Weboberfläche benötigt; die Entscheidung gegen Svelte betrifft diesen Android-zentrierten Umfang, nicht seine allgemeine Qualität.

## R03 — Capacitor

[Capacitor: Documentation](https://capacitorjs.com/docs)

Capacitor verbindet Web-Anwendungen mit nativen Plattformfunktionen. Es wäre eine mögliche Alternative, beseitigt aber nicht die Anforderungen an Android-Dienste, native Extraktion und Speicherzugriff. Der zusätzliche Brückenaufwand wird hier vermieden.

## R04 — Native Bibliotheken und 16-KB-Seiten

[Android: Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes)

Abhängigkeiten mit nativem Code erfordern passende Build-/Ausrichtungs- und Laufzeittests. Deshalb zählen Python-, JS- und FFmpeg-Binaries, nicht nur der Kotlin-Code. Das bereitgestellte Testgerät hinsichtlich ABI und `PAGE_SIZE` tatsächlich vermessen; keine konkrete Play-Deadline als unveränderliche Produktanforderung übernehmen.

## R05 — Lange WorkManager-Worker

[Android: Support for long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)

Android dokumentiert, dass lange Worker ab Android 16 auch mit Foreground Service Jobquoten ausschöpfen können, und nennt Alternativen für passende Anwendungsfälle. WorkManager ist kein Freibrief für unbegrenzte Hintergrundausführung.

## R06 — Foreground-Service-Grenzen

[Android: Behavior changes for apps targeting Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)

Für bestimmte Service-Typen gelten Laufzeit-/Startbedingungen und Timeoutbehandlung. Die sechs Stunden für dataSync sind ein versions-/targetabhängiges Ausführungsbudget, keine Begrenzung der Länge einer extern transkribierten Audiodatei. Cloudwartezeit und aktive Gerätearbeit deshalb trennen.

## R07 — Nutzerinitiierte Transfers

[Android: User-initiated data transfer](https://developer.android.com/develop/background-work/background-tasks/uidt)

Eine mögliche passende API für längere direkt vom Nutzer angestoßene Datenübertragungen. Verfügbarkeit und Einsatzbedingungen gegen die gewählte Android-Version prüfen; nicht als universellen Jobersatz verwenden.

## R08 — Storage Access Framework

[Android: Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)

Dokumenten-/Baum-URIs und Berechtigungen ermöglichen kontrollierte Zielordner. Ein Document-URI ist kein gewöhnlicher lokaler Pfad; Zugriff kann verloren gehen und einzelne Provider haben unterschiedliche Operationen. Interne Ergebnissicherung von Export trennen.

## R09 — Android-Wrapper für yt-dlp

[youtubedl-android: README](https://github.com/yausername/youtubedl-android/blob/master/README.md)

Dokumentiert `updateYoutubeDL` mit Stable-/Nightly-Optionen. Gleichzeitig steht dort eine ältere Python-Version. Der konkrete veröffentlichte AAR-Inhalt wurde für dieses Paket nicht inspiziert. Deshalb keine Behauptung, eine bestimmte neueste Binary sei bereits kompatibel oder sicher. Wrapper-/Fork-Lizenz und Updatebezugsquelle prüfen.

## R10 — yt-dlp-Abhängigkeiten und Channels

[yt-dlp: README](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)

Dokumentiert unterstützte Python-Versionen, Abhängigkeiten, Untertiteloptionen und Releasekanäle. Gelesener Stand: CPython 3.10+ und PyPy 3.11+ als unterstützte Versionen. Diese Angaben sind von Empfehlungen in Release Notes zu unterscheiden.

## R11 — Python-Empfehlung in Release Notes

[yt-dlp: Releases](https://github.com/yt-dlp/yt-dlp/releases)

Die geöffneten Release Notes nennen eine auf Python 3.11 angehobene empfohlene Mindestversion und einen anstehenden Supportwechsel. „Empfohlen“ und „bereits technisch zwingend“ nicht gleichsetzen. Native Runtimeversion im Android-Artefakt tatsächlich messen.

## R12 — EJS und JavaScript

[yt-dlp: EJS setup guide](https://github.com/yt-dlp/yt-dlp/wiki/EJS)

[yt-dlp-ejs: Repository](https://github.com/yt-dlp/ejs)

Die YouTube-Extraktionskette umfasst JavaScript-Challenge-Skripte und unterstützte Runtimes. EJS muss zur yt-dlp-Version passen. Ein einfaches Update ausschließlich der yt-dlp-Datei ist daher kein vollständiges Kompatibilitätskonzept. Die Android-Eignung einer konkreten Runtime bleibt Gegenstand von P0.

## R13 — Grenzen aktueller YouTube-Extraktion

[yt-dlp: PO Token Guide](https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide)

Upstream dokumentiert weitere client-/zugriffsabhängige Anforderungen. Ein neuer Release beseitigt nicht automatisch jeden Downloadfehler. Daraus wird keine Umgehungsfunktion spezifiziert, sondern eine differenzierte Fehlerbehandlung und eine ehrliche Zugriffsgrenze.

## R14 — Groq Speech-to-Text

[Groq: Speech to Text](https://console.groq.com/docs/speech-to-text)

Dokumentiert `whisper-large-v3` und `whisper-large-v3-turbo`, Ausgabe-/Zeitformate, Uploadunterschiede und Chunking. Gelesene Richtpreise: 0,111 USD beziehungsweise 0,04 USD pro Audiostunde. Die beschriebenen Datei-/Attachment-/URL-Grenzen und Tarife müssen getrennt behandelt werden. Für v1 direkte Uploads, kein öffentlicher Audiohost.

## R15 — AssemblyAI

[AssemblyAI: Submit a transcript](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/submit)

[AssemblyAI: Models](https://www.assemblyai.com/docs/getting-started/models)

[AssemblyAI: Homepage](https://www.assemblyai.com/)

Async-Transkription mit Remote-ID ist für das Produkt geeignet. Die abgerufenen Seiten/Beispiele hatten teilweise unterschiedliche Modellstände: Homepage bewirbt Universal 3.5 Pro, Beispielcode nennt teils Universal 3 Pro. Aktuelle Schemawerte und tatsächliche Annahme prüfen; Modellnamen nicht anhand des Marketingtextes erraten. Angefordertes und gemeldetes Modell getrennt erhalten.

## R16 — OpenAI-Dateitranskription

[OpenAI: File transcription](https://developers.openai.com/api/docs/guides/speech-to-text)

Die geöffnete Anleitung verwendet `gpt-transcribe`, dokumentiert ein 25-MB-Dateilimit und modellabhängige Optionen. `languages` und `language` nicht beliebig austauschen. `timestamp_granularities[]` wird dort für `whisper-1` dokumentiert, nicht als universelle Option. Anbieter-/Modellprofile brauchen echte Contract-Tests.

## R17 — OpenAI-Modelle

[OpenAI: GPT-Transcribe](https://developers.openai.com/api/docs/models/gpt-transcribe)

[OpenAI: GPT-4o Transcribe Diarize](https://developers.openai.com/api/docs/models/gpt-4o-transcribe-diarize)

Das allgemeine Transkriptionsmodell und das diarization-fähige Modell sind separat dokumentiert. Gelesener GPT-Transcribe-Richtpreis: 0,0045 USD pro Minute. Das belegt weder Accountzugriff noch identische Sprecher-/Timestamp-Fähigkeiten. Keine ChatGPT-Abogebühr als API-Guthaben behandeln.

## R18 — Groq-Rate-Limits

[Groq: Rate Limits](https://console.groq.com/docs/rate-limits)

Die Tabelle nennt für beide Whisper-Modelle im Free Plan 7.200 Audiosekunden pro Stunde und 28.800 pro Tag, also zwei beziehungsweise acht Stunden, sowie Requestlimits. Limits gelten laut Dokumentation organisationsweit. Nicht als garantiert unabhängige Tagesbudgets je Modell/Gerät oder als verbindlichen Abrechnungszähler interpretieren. Accountbedingungen und aktuelle Headers bleiben maßgeblich.

## R19 — FFmpegKit-Status

[FFmpegKit: aktuelle README](https://github.com/arthenica/ffmpeg-kit/blob/main/README.md)

Die README enthält einen Updatehinweis aus Juli 2026: ursprünglicher FFmpegKit-Strang eingestellt, source-only Fortführung FFmpegKitNext. Für die konkrete App weder historische Binärkoordinaten noch einen beliebigen Fork blind wählen. Build-/ABI-/Lizenz-/Vertrauensprüfung bleibt offen bis P0.

## R20 — Dynamisches Nachladen

[Android: Dynamic Code Loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)

Android beschreibt Integritäts-, Manipulations- und Plattformrisiken. Die persönliche Sideload-Anwendung macht einen geprüften Komponentenupdateweg plausibel, aber nicht risikofrei oder automatisch Play-kompatibel.

## R21 — Rechte nachgeladenen Codes

[Android: Security checklist](https://developer.android.com/privacy-and-security/security-tips)

Nachgeladener Code läuft mit den Sicherheitsrechten der App. Deshalb sind ein separater Prozess mit gleicher UID und verschlüsselte Schlüssel kein vollständiger Schutz vor einem bösartigen Update. Signatur-/Herausgeberprüfung und begrenzte Quellen bleiben zentral.

## R22 — Android Keystore

[Android: Keystore system](https://developer.android.com/privacy-and-security/keystore)

Nicht exportierbare Schlüssel schützen die gespeicherte Verschlüsselungsgrundlage. Verfügbarkeit, Gerätebindung und Schlüsselinvalidierung müssen zum Hintergrundworkflow passen. Kein Schutzversprechen für API-Key-Klartext in einer bereits kompromittierten App ableiten.

## R23 — Codex-Projektanweisungen

[OpenAI: Custom instructions with AGENTS.md](https://developers.openai.com/codex/guides/agents-md)

Dokumentiert das Einlesen von AGENTS.md als projektbezogene Anleitung. Deshalb kurzer dauerhafter Regelteil im Repository plus separate Produkt-/Architekturdokumente, nicht nur ein langer Chatprompt. Die tatsächliche Arbeitsumgebung muss auf die Dateien zugreifen können.

## R24 — Codex-Subagenten

[OpenAI: Subagents](https://developers.openai.com/codex/multi-agent)

Dokumentiert delegierte, abgegrenzte Arbeit und weist auf Koordinationsrisiken paralleler Schreibzugriffe hin. Daher unabhängige Reviews und klar zugeordnete Codebereiche; keine starre Anzahl an „Superagenten“ oder erfundene Delegation.

## R25 — Force-Stop ist ein eigener Zustand

[Android: Behavior changes, all apps, Android 15](https://developer.android.com/about/versions/15/behavior-changes-all)

Der Stopped-State soll bis zur entsprechenden Nutzerinteraktion erhalten bleiben. Die App soll nach erneutem Start rekonstruieren, nicht behaupten, jeden absichtlichen Stop heimlich zu überleben.

## R26 — Frühere WER-Angabe einordnen, nicht zur Produktregel machen

[Originalauswertung eines Transkriptionsdienstes: YouTube Auto-Captions](https://youtube-transcript.ai/blog/youtube-auto-caption-accuracy-study)

Die früher erwähnte Zahl ist auffindbar: Auswertung vom 19. August 2026 mit 264 englischen Videos und 9,9 % medianer WER. Sie stammt vom Anbieter eines Transkriptionswerkzeugs und verwendet bereitgestellte Creator-Captions als Referenz. Sie ist kein kontrollierter Head-to-head-Vergleich gegen Groq auf demselben Material und belegt keine universelle Qualitätsreihenfolge. Auch `1 − WER` ist nicht ohne Weiteres eine allgemeine Genauigkeitsgarantie. Deshalb keine derartige Rangfolge oder Qualitätsprozentanzeige in SourceScribe.

## Noch praktisch zu verifizieren

Android-Wrapper-/Runtimekombination einschließlich EJS; tatsächliche Hot-Update-Artefakte und Signaturprüfung; Jobtreiber pro SDK; Modell-/Accountfähigkeiten; Remote-Cancel/Idempotenz/Löschung je Provider; SAF-Verhalten des gewählten Dokumentenanbieters; ARM64-/Emulator-/16-KB-Laufzeit. Diese offenen Punkte sind P0-/Integrationsaufträge, keine bereits positiv getesteten Eigenschaften.
