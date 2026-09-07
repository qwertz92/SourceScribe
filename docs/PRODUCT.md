# Produktumfang und Bedienung

## Ziel und Grenzen

SourceScribe beschafft nachvollziehbare Transkripte für die anschließende Analyse in ChatGPT. Primärnutzer ist technisch versiert, verwendet hauptsächlich Android und möchte trotzdem einen schnellen Share-Sheet-Workflow. Gute Standardeinstellungen, ausführliche Expertenoptionen und verständliche Fehler sind gleichrangig.

v1 enthält YouTube-Einzelvideos und den Import lokaler Audiodateien. Letzterer nutzt dieselbe STT-Pipeline und bleibt auch bei einer YouTube-Störung verwendbar. Keine Playlists, laufenden Livestreams, DRM-Umgehung, Cookie-Extraktion aus anderen Apps, automatische Websuche nach Ersatzquellen, Substack-Scraper, lokale ML-Modelle oder Zusammenfassungs-/Faktencheck-API in v1. Website-Niederschriften werden im separaten ChatGPT-Workflow behandelt. Keine versteckte Übersetzung oder Textverbesserung.

## Anforderungen

### SS-01 — Identische Quelle

URLs über Android Share, Einfügen oder Texteingabe annehmen; mehrere explizit eingegebene URLs als getrennte Jobs mit gemeinsamer Bestätigung erlauben. YouTube-Hosts streng prüfen, Video-ID kanonisieren und mit den aufgelösten Metadaten abgleichen. Tracking entfernen; Zeitmarken nicht still als Ausschnitt behandeln: v1 verarbeitet das ganze Video und zeigt dies an. Bei Video- plus Playlist-ID ausschließlich das explizite Video; reine Playlist ablehnen. Shorts und abgeschlossene Livestreams als Einzelvideos behandeln, sofern technisch zugänglich. Laufende Streams und Premieren vor Beginn nicht unbegrenzt aufnehmen.

Titel, Kanal, Dauer, Veröffentlichungsdatum und Thumbnail nur anzeigen, soweit tatsächlich ermittelt. Fehlende Metadaten als unbekannt markieren. Bei neuer Auflösung der Audioquelle die Video-ID erneut prüfen. Lokale Dateien erhalten eine eigene Source-ID, dokumentierte Dateimetadaten und nach Import einen Inhaltshash, aber keine erfundene YouTube-Zuordnung.

### SS-02 — Vier verbindliche Beschaffungsmodi

| Modus | Verhalten | Kein akzeptabler YouTube-Track |
|---|---|---|
| `CAPTIONS_ONLY` — Nur YouTube | Ausgewählte Caption-Spur; kein Audio-/STT-Download | Verständlicher Fehlschlag, niemals Provider |
| `CAPTIONS_THEN_STT` — YouTube, sonst STT | Akzeptable Caption-Spur; sonst ausgewählter Provider | Audio beschaffen und STT starten, soweit vorab erlaubt |
| `STT_ONLY` — Nur STT | Gewählte Audiospur transkribieren, keine Captions archivieren | Für diesen Modus irrelevant |
| `BOTH` — Beides | Caption- und STT-Zweig unabhängig ausführen | STT erhalten; fehlenden Caption-Zweig offen als Teilergebnis führen |

„Voreinstellung“ ist lediglich die Übernahme der globalen Konfiguration, kein fünfter Verarbeitungsmodus. „Quality First“ und „Auto“ existieren nicht als zusätzliche konkurrierende Algorithmen. Wer im STT-Modus auch Captions archivieren möchte, wählt BOTH.

Globale Startvorgabe: `CAPTIONS_THEN_STT`, Originalsprache bevorzugt, vom Kanal bereitgestellte Tracks vor automatisch erzeugten, automatische Übersetzungen aus. Ein Provider wird im Onboarding ausdrücklich ausgewählt. Ohne konfigurierten Provider sind Nur-YouTube-Jobs weiter möglich; ein nicht ausführbarer Fallback wird vor dem Start angezeigt.

Ein bestätigtes Fehlen akzeptabler Captions ist nicht dasselbe wie HTTP 429, Offline-Zustand oder ein Parserfehler. Bei solchen Abruffehlern zunächst begrenzt wiederholen, danach standardmäßig nachfragen/pausieren. Optional darf der Nutzer global oder pro Job auch dafür STT-Fallback erlauben. Die Entscheidung wird mit dem Job gespeichert. Niemals alleine wegen eines Health-Hinweises automatisch neu transkribieren.

### SS-03 — Globale Vorgaben, Presets und Job-Overrides

Einstellungen umfassen Modus, zugelassene Caption-Typen, Sprachpräferenzen, Übersetzungszulassung, Provider/Modell/Region, unterstützte STT-Optionen, Exportformate, Zielordner, Audioaufbewahrung, Netzpolitik und Parallelität. Auswahl vor Start darf globale Vorgaben übersteuern, verändert diese aber nicht.

Aufgelöste Konfiguration beim Start als unveränderlichen Snapshot speichern; laufende Jobs verändern sich nicht durch spätere Settings-Änderungen. Zugangsdaten nur referenzieren, nicht hineinkopieren. Gespeicherte benannte Presets verwenden denselben Konfigurationstyp. Sinnvolle Beispiele: „YouTube zuerst“, „Neue STT“, „Beides archivieren“. Alle Presets verwenden den festgelegten Modus und dieselben validierten Optionen, keine zusätzliche Regel-/Skriptsprache. Keine Preset-Namen wie „100 % akkurat“ oder „garantiert kostenlos“.

Quick Controls: Modus, Provider/Modell und Preset. Erweiterte Optionen einklappbar. Bei Nur YouTube kein verwirrend aktiver Provider-Schalter. Expliziter Audio-Track- und Caption-Track-Picker, sobald mehrere geeignete Spuren existieren. Originalsprache und ausgewählte Audioversion getrennt behandeln; automatisch synchronisierte oder übersetzte Tonspuren nicht still zur Originalquelle erklären.

### SS-04 — Drei STT-Anbieter

AssemblyAI, OpenAI und Groq vollständig integrieren, aber Optionen nach tatsächlichen Modellfähigkeiten darstellen. Deutsch/Englisch und automatische Sprachwahl sind Kernanwendungen. Sprechertrennung, Wort-/Segmentzeiten und Kontextbegriffe nur anbieten, soweit unterstützt. Inkompatible Kombinationen vor dem Upload erklären. Nutzer konfiguriert den eigenen Account; die App enthält keine gemeinsamen API-Schlüssel.

AssemblyAI-Guthaben kann genutzt werden; Groq ist eine wählbare günstige Alternative. Keine feste universelle Genauigkeitsrangfolge. Keine automatische Flucht zu einem anderen Provider: Audioübermittlung und mögliche Kosten brauchen eine zuvor ausdrücklich gewählte Richtlinie. Modell-/Accountzugriff muss verifiziert werden; ein erfolgreicher Modelllistenabruf allein beweist keine erfolgreiche Transkription.

### SS-05 — Verlauf und mehrere Jobs

Dauerhafter Verlauf mit Suche, Quelle, Datum, Provider/Modell, Phase, Ergebnis-/Exportstatus und verständlicher Fehleransicht. Wiederholen, nur fehlenden Zweig wiederholen, abbrechen, mit anderem Provider neu ausführen und löschen. Neue Ausführung als neue Attempt erhalten; abgeschlossene Ergebnisse nicht überschreiben.

Konfigurierbar 1–4 gleichzeitig aktive Jobs, Standard 2. CPU-intensive Audioaufbereitung standardmäßig einmal gleichzeitig. Providerlimits separat berücksichtigen. Doppeltes Teilen desselben Links darf nicht unbemerkt einen weiteren kostenpflichtigen Auftrag auslösen: vorhandenen Job zeigen, neue Ausführung explizit anbieten. Verschiedene Konfigurationen und bewusst gewünschte Vergleiche bleiben möglich.

### SS-06 — Hintergrundausführung und ehrliche Zustände

Navigation, Display-Aus und normale Prozessneuerstellung dürfen den Verlauf nicht verlieren. Arbeit wird soweit vom OS zugelassen fortgesetzt oder aus sicheren Checkpoints wieder aufgenommen. Nach einem Benutzer-Force-Stop keine geheime Selbstreaktivierung versprechen. Beim nächsten Öffnen Zustand abgleichen und verständlich fortsetzen.

Phase und reale Byte-/Chunk-Fortschritte zeigen. Ohne Provider-Prozentwert „Provider verarbeitet“ mit Laufzeit, nicht einen erfundenen Prozentbalken. Wartezustände für Netz, Nutzerentscheidung, Kontingent, Geräteentsperrung und Exportberechtigung unterscheiden. In BOTH erfolgreiche Artefakte sofort öffnen können, während der andere Zweig noch läuft.

### SS-07 — Provenienz und Ergebnisqualität

YouTube-Auto-Captions, vom Kanal bereitgestellte Captions, automatische Übersetzung, externes STT und lokale Datei als getrennte Herkunftsinformationen dokumentieren. Uploader-bereitgestellt heißt nicht nachgewiesen manuell erstellt. Unklare Informationen als unbekannt lassen. Provider und Modell nicht in dasselbe Feld wie Caption-Herkunft zwingen.

Originaldaten auf Wunsch speichern; abgeleitete bereinigte Texte getrennt halten. Keine LLM-Korrektur, keine scheinbar beste Wortauswahl aus zwei Transkripten. Weder Lesbarkeit noch Textübereinstimmung beweisen Genauigkeit. Strukturwarnungen dürfen Schleifen, fehlende Chunks, kaputte Zeiten oder leeres Ergebnis anzeigen, aber keine falsche WER-/Accuracy-Bewertung liefern. Echte Sprechwiederholungen dürfen nicht global gelöscht werden.

### SS-08 — Speicherung und Exporte

Intern eine kanonische, dauerhaft gespeicherte Fassung behalten. Externe Zielordner über Android Storage Access Framework auswählen, nicht als ungeprüften Dateisystempfad behandeln. Markdown ist Standard; TXT, strukturiertes JSON sowie SRT/VTT bei vorhandenen geeigneten Zeitdaten. Rohe Captions sind eine zusätzliche Ausgabe. Tatsächliches Rohformat erhalten; Konvertierungen kennzeichnen.

Bei Verlust der Ordnerberechtigung bleibt das Transkript intern verfügbar und erhält `EXPORT_PENDING/FAILED`, keine erneute STT-Anfrage. Export wiederholbar, Kollisionen vermeiden. Dateinamen enthalten Video-/Source-ID, Quelle/Modell und Sprachangabe; Titel allein ist nicht eindeutig. Exportmetadaten bleiben auch nach Umbenennung aussagekräftig.

Audioaufbewahrung: nur technisch notwendige temporäre Dateien, bis erfolgreicher Transkriptpersistierung (Standard), oder dauerhaft ausdrücklich behalten. Speicherobergrenze und sichere Bereinigung; aktive Dateien und erfolgreiche Transkripte niemals als Cache löschen. „Nie behalten“ bedeutet nicht „niemals während der Verarbeitung auf Datenträger schreiben“.

### SS-09 — Viewer und Weitergabe

Lange Transkripte ohne UI-Blockierung anzeigen, durchsuchen, kopieren und über Android Share als Datei teilen. Herkunft, Quelle, Sprache, Zeit-/Sprecherangaben und Warnungen sichtbar. BOTH zeigt getrennte Artefakte, keine automatische Verschmelzung. Zunächst umschaltbare Ansicht; auf großen Displays optional nebeneinander. Ein vollständiger algorithmischer Diff ist nachgeordnet.

Kein Versprechen, dass Android eine Datei automatisch im ChatGPT-Projekt „Summarize“ ablegt. Standard-Share-Sheet und gespeicherte Datei sind die zuverlässige Schnittstelle. Kein eigener OpenAI-LLM-Schlüssel nur für Zusammenfassungen erforderlich.

### SS-10 — Sicherheit und Komponentenupdates

API-Schlüssel geschützt speichern; Diagnoseexport redigieren; keine Telemetrie oder Drittanbieteranalyse. Herkunft von Update-Code prüfen, kompatible yt-dlp-/EJS-Pakete unabhängig vom APK aktualisieren und zurückrollen, soweit technisch nachgewiesen. Native Runtimes und unverträgliche Änderungen benötigen gegebenenfalls weiterhin ein APK-Update. Genaue Freigabe- und Fehlerregeln stehen in SECURITY_UPDATES.

### SS-11 — Kosten, Limits und Transparenz

Vor kostenrelevanten Jobs geschätzte Audiodauer und verfügbare Preisinformation mit Datums-/Tarifangabe zeigen. Bei unbekanntem Preis „unbekannt“, nicht null. Persönliche lokale Budgets begrenzen neue Submissions konservativ, ersetzen keine Providerabrechnung. Overlap, Retries und Anbieter-Rundung können Zusatzverbrauch verursachen. Ein entfernter Job kann nach lokalem Abbruch weiterlaufen und berechnet werden.

Lokale Usage-Zählung ist nur eine Teilansicht dieses Geräts, keine verbindliche organisationsweite Kontingentanzeige. Warteschlange bei Rate-Limit statt Endlosschleife; `Retry-After` berücksichtigen. Keine Paid-Upgrades oder Budgetänderungen automatisch vornehmen.

### SS-12 — Bedienqualität und Nachweise

Deutsche UI mit systematischer Ressourcenlokalisierung, System/hell/dunkel, lesbarer Typografie, ausreichenden Touch-Flächen, TalkBack-Semantik, Schriftvergrößerung und klaren Leer-/Fehlerzuständen. Aufwendige Grafiken dürfen Funktion und Performance nicht verdrängen. Clipboard nur nach Nutzeraktion lesen, nicht dauernd überwachen. Laufende Jobs über passende Notifications anzeigen; verweigerte Berechtigungen verständlich behandeln.

Vollständiges Repository, nachvollziehbare Build-Anleitung, CI, Testberichte, debug APK und ein für persönliche Updates reproduzierbar signierbarer Release-Pfad. Die vollständige v1-Abnahme folgt TEST_PLAN, nicht dem bloßen Vorhandensein von Screenshots.
