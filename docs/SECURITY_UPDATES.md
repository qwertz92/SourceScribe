# Sicherheit, Updates und persönliche Auslieferung

## S1 — Bedrohungsmodell und Vertrauensgrenzen

Zu schützen sind API-Schlüssel, Audio-/Transkriptinhalte, persönliche Quellenhistorie, lokale Dateien, Providerbudgets und die Integrität des ausführbaren Codes. Untrusted sind eingefügte Links, Share-Inhalte, Titel/Untertitel, Medien, API-Antworten und heruntergeladene Updateartefakte. HTTPS alleine macht fremden Code nicht vertrauenswürdig.

Die App führt Drittanbietercode für Extraktion und Audioverarbeitung aus. Ein Prozess mit eigener PID, aber derselben Android-UID bleibt im selben App-Vertrauensbereich. Keine falsche Aussage, ein separat gestartetes Python oder verschlüsselte API-Schlüssel verhinderten den Zugriff eines kompromittierten Extractors auf App-Daten. Android weist ausdrücklich darauf hin, dass nachgeladener Code mit App-Berechtigungen läuft. [R20–R21]

Für die persönliche v1 wird dieses verbleibende Risiko transparent akzeptierbar gemacht durch eingeschränkte Bezugsquellen, verifizierte Pakete, minimale Optionen, aktuelle geprüfte Runtimes und getrennte Datenflüsse. Keystore schützt gespeicherte Schlüssel, nicht jeden späteren Klartextgebrauch in einer kompromittierten App. Echte Isolation mit eigener UID/isolated service und kontrolliertem I/O wäre ein gesonderter, praktisch zu validierender Entwurf, kein kostenloser Effekt eines zusätzlichen Prozesses.

## S2 — Geheimnisse und Daten

API-Schlüssel mit einem nicht exportierbaren Android-Keystore-Schlüssel verschlüsseln, z. B. AES-GCM mit frischem Nonce je Verschlüsselung. Nur Ciphertext und Metadaten in privatem App-Speicher; kein Klartext in Room, DataStore, BuildConfig, Ressourcen, WorkManager Data oder Prozessargumenten. Credential-ID und Providerregion als zusätzliche Bindung berücksichtigen. Aktuell gepflegte Android-/Kryptobibliotheken verwenden; keine selbst entworfene Kryptografie. [R22]

Für unbeaufsichtigte Jobs muss klar sein, ob ein Key ohne erneute biometrische Freigabe verfügbar sein darf. Default darf nach normaler Geräteentsperrung Hintergrundjobs ermöglichen. Keystore-Invalidierung, Gerätewechsel/Restore und fehlende Entsperrung verständlich behandeln. Schlüssel niemals bei gescheiterter Entschlüsselung auf Klartextspeicherung zurückfallen lassen.

Key-Eingabe maskiert, sensible Screenshots/Task-Previews soweit sinnvoll begrenzen. Kein Secret in Testberichten oder ADB-Befehlszeilen. Debug- und Release-Logging redigieren Authorization-Header, signierte Medien-/Upload-URLs, Kontextbegriffe und vollständige Providerantworten. Diagnoseexport enthält standardmäßig Fehlerkategorien, Versionen und pseudonymisierte IDs, nicht Audio, Transkript, URL-Tokens oder Schlüssel. Export vor Freigabe anzeigen lassen.

Automatisches Cloudbackup für Secrets und sensible App-Daten standardmäßig deaktivieren oder explizit ausschließen; die tatsächlichen Regeln für Backup/Device-Transfer prüfen. Ein Restore eines alten verschlüsselten Blobs ohne seinen gerätegebundenen Key ist kein funktionierendes Credential-Backup. Presetexport ohne Secrets, komplette Daten-/Schlüsselbackups nicht in v1.

Intern gespeicherte Transkripte sind app-privat, aber ohne zusätzlich implementierten Tresor nicht als separat anwendungsverschlüsselt bezeichnen. Externe TXT-/MD-/JSON-Exporte sind lesbare Dokumente; bei gewähltem Cloud-DocumentsProvider können sie dessen Synchronisierung unterliegen. Keine pauschale Aussage „alle Daten bleiben offline“: STT übermittelt Audio an den gewählten Provider.

## S3 — Netz, Eingaben und Rechte

Nur Internet und funktional nötige Android-Berechtigungen. Kein `MANAGE_EXTERNAL_STORAGE`, keine Accessibility-Service-Berechtigung, kein Mikrofonzugriff ohne neue Aufnahmefunktion. SAF statt Vollzugriff. Notifications zur passenden Zeit anfordern; Ablehnung darf nicht zu geheimem oder defektem Verhalten führen. Die App arbeitet nicht als generischer Proxy für beliebige URLs.

Strenge YouTube-Host-/URL-Prüfung, einschließlich Userinfo, Unicode-Lookalikes, verschachtelten Redirectparametern und Mehrfach-URLs. Keine shellfähigen freiformigen Parameter. Downloader nur mit kontrolliertem Konfigurationssatz; Dateinamen/-archive gegen Pfadtraversal und unerlaubte absolute Pfade prüfen. Provider-API-Hosts aus geprüften Profilen, keine frei eingegebenen OpenAI-kompatiblen Endpoints in v1.

Secrets nie bei Redirects an fremde Hosts weiterreichen. API-, Medien- und Update-HTTP-Clients getrennt konfigurieren. HTTPS und normale Zertifikatsprüfung verwenden; keine globalen Trust-all-Zertifikate. Test-HTTP-Ausnahmen ausschließlich in klar getrennten Testvarianten. Parsergrößen, Dekompression, Ausgabevolumen und Prozesslaufzeiten begrenzen. Medien-/Metadatendateien niemals als Anweisungen ausführen.

## S4 — Zwei verschiedene Updateklassen

**Komponentenupdate:** austauschbares yt-dlp-/EJS-Paket innerhalb einer nachgewiesen kompatiblen eingebetteten Runtime. Kann ohne APK-Neuinstallation erfolgen.

**App-/Runtimeupdate:** Änderungen an Kotlin-Code, nativen Python-/JS-/FFmpeg-Binaries, ABI, SDK oder inkompatiblen Schnittstellen. Dafür einen signierten APK-Updatepfad verwenden. Kein Versprechen, alle zukünftigen Änderungen mit dem yt-dlp-Button beheben zu können.

Initial eine tatsächlich geprüfte Kombination mit der App ausliefern. Die App darf nicht nur auf einen ersten Internetdownload angewiesen sein. Versionen und Herkunft aller Komponenten in Einstellungen und Diagnosen anzeigen. Ein Runtimebedarf ist `REQUIRES_APP_UPDATE`, nicht „Update erfolgreich“.

## S5 — Updatepolitik für die persönliche Sideload-App

Kanäle: Stable und Nightly. Nightly darf voreingestellt sein, wenn die ausgelieferte Kombination aus diesem Kanal geprüft wurde; Kanalwahl bedeutet nicht automatische Installation. Default: manuelle Installation, optional höchstens tägliche Metadatenprüfung und Benachrichtigung. Bei plausibler Extractor-Störung „Update prüfen / Update und erneut versuchen“ anbieten. Kein Main-/Master-Checkout und keine beliebigen Repository-URLs in v1.

Optional „nach passendem Extraktionsfehler einmal aktualisieren“ nur nach ausdrücklichem Opt-in. Nie bei Offlinezustand, HTTP 429, privatem Video oder gesperrter Quelle reflexartig updaten. Vor einem Retry Video-ID und bisherige kostenrelevante Steps beachten. Eine Release-Notiz beweist nicht, dass genau der beobachtete Fehler behoben ist; UI formuliert „neue Version verfügbar“, nicht „garantierte Reparatur“.

Updatechecks sind OS-gesteuert, nicht sekundengenaue Benachrichtigungen. Prüfanfragen cachen, ETag/Backoff soweit verfügbar nutzen und Notifications deduplizieren. Nach einmal gescheitertem Update/Retry keine Endlosschleife.

## S6 — Integrität und Authentizität

Ein SHA-256-Hash, der ungesichert aus derselben kompromittierten Quelle stammt wie die Datei, belegt nur Übereinstimmung, keine unabhängige Herausgeberauthentizität. Die Mindestanforderung ist eine **authentifizierte Zuordnung** von vertrauenswürdigem Herausgeber, Paketinhalt, Version und Kompatibilität.

Bevorzugt offizielle, signierte Release-/Checksum-Metadaten mit bereits vertrauenswürdig gebundenem öffentlichen Schlüssel prüfen. Signaturalgorithmus, tatsächlich veröffentlichte Artefakte und Key-Verifikation im P0-Test nachweisen. Einen öffentlichen Schlüssel nicht bei jedem Update unkontrolliert neben der Signatur neu laden. Schlüsselwechsel braucht einen nachvollziehbaren Vertrauenspfad oder ein App-Update.

Falls ein Android-Wrapper eigene umgepackte/lazy Artefakte benötigt: deren Beziehung zu Upstream, Buildpfad und Herausgebervertrauen ausdrücklich prüfen. Alternativ in kontrollierter Projekt-CI ein Paket aus fixierten Upstream-Releases bauen und mit einem eigenen Release-Schlüssel signieren. Dann liegt Vertrauen zusätzlich beim eigenen Build-/Signierprozess; dies muss dokumentiert werden. Kein erfundener „offizieller“ Status für Forks. Private Signierschlüssel nicht ins Repository oder auf das Telefon legen.

Eine kryptografische Prüfung ist Pflicht, nicht ein optionales kosmetisches Feature. Fehlt ein belastbarer Vertrauensweg für das gewählte Hot-Update-Artefakt, keine unsichere Implementierung erzwingen. Komponentenupdate als blockiert melden, geprüfte gebündelte Version verwenden und signierte APK-Updates ermöglichen. Das erfüllt dann noch nicht die vollständige Hot-Update-Abnahme.

## S7 — Kompatibilitätspaket und Aktivierung

Ein installierbares Engine-Paket enthält oder referenziert verifizierbar: Paketformatversion, Herausgeber, Kanal, Release-/Commit-ID, yt-dlp- und EJS-Versionen, unterstützte App-/Python-/JS-Versionen, Artefakthashes/-größen und erforderliche Signatur. Die tatsächliche Upstream-Kopplung von EJS und yt-dlp beachten; nicht jedes Teil isoliert auf „latest“ setzen. [R12]

Updateablauf: Metadaten verifizieren → Kompatibilität prüfen → begrenzt in privaten Stagingbereich laden → Signatur/Hashes prüfen → Archive sicher entpacken → lokale Initialisierung prüfen → kontrollierten Funktionstest ausführen → Kandidat aktivieren. Keine unlimitierte Dekompression, Zip-Slip-Pfade oder ausführbaren Dateien auf öffentlich beschreibbarem Speicher.

Aktuelle Jobs pinnen ihre tatsächlich genutzte Engine-Version. Keine Dateien unter einem laufenden Prozess austauschen. Neue Jobs verwenden erst freigegebene Versionen. Aktivierung und Rollback über kleine atomar wechselnde Metadaten/Verzeichnisslots mit Crash-Recovery. Bei In-process-Python beachten, dass geladene Module nicht durch Dateiaustausch sauber neu geladen werden; Prozessneustart oder bewiesene Reloadstrategie ist Pflicht.

Lokale Initialisierungsfehler führen zum Rollback. Ein fehlgeschlagener Live-Smoke-Test wegen Offline/YouTube-Ausfall ist dagegen zunächst unklar, kein sicherer Beweis für eine kaputte neue Version. Vor Aktivierung muss der definierte Nachweis erfüllt sein; unklare Kandidaten bleiben gestaged. Aktive, vorherige gesunde und gebündelte Version erhalten; alte Versionen erst ohne aktive Referenzen bereinigen. Rollback auf bekannte Sicherheitslücken warnend kennzeichnen und ggf. sperren. Kein „previous = sicher“, nur weil es älter ist.

## S8 — APK-Auslieferung ohne ständige Handarbeit

APK-Builds für Debug und persönliche Release-Nutzung klar trennen. Für aufeinanderfolgende persönliche Updates denselben kontrollierten Release-Signing-Key verwenden und Versionscodes erhöhen. Den Key sicher außerhalb von Git sichern; CI-Signing nur bei bewusst bereitgestelltem Secret. Erstinstallation/Update nutzt den normalen Android-Installationsdialog, keine stille Installation behaupten.

CI kann APK-Artefakte erzeugen. Eine spätere Release-Veröffentlichung beziehungsweise automatische Updatequelle braucht einen ausdrücklichen Auftrag; Codex soll nicht unbemerkt ein öffentliches Repository oder Release erzeugen. Optional APK-Updatehinweise in der App, aber keine zweite komplexe Appstore-Plattform bauen. Dieser Pfad bleibt auch dann nötig, wenn Komponentenupdates funktionieren.

Vor Veröffentlichung Abhängigkeitslizenzen, konkrete FFmpeg-Konfiguration, Notice-/Quellcodepflichten und App-Lizenz prüfen. Nicht das gesamte Projekt reflexartig MIT lizenzieren, wenn eingebundene Komponenten andere Bedingungen mitbringen. Die persönliche Sideload-Architektur ist nicht automatisch eine geprüfte Google-Play-Architektur. [R09, R19–R21]
