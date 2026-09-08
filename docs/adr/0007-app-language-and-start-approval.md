# ADR 0007 — App-Sprache und Freigabe durch Auftragsstart

Datum: 8. September 2026. Status: implementiert und funktional geprüft; TalkBack-Geräteabnahme separat blockiert.

Der Nutzer wünscht eine explizite App-Sprachwahl Deutsch/Englisch und keinen
separaten allgemeinen Audioübermittlungs-Schalter. Die bestätigte Providerwahl
und der Start des konkreten Auftrags bilden gemeinsam die Freigabe. Modi,
Provider-/Credentialbindung und Budget bleiben unverändert verpflichtend.

`startPreviews` erstellt dafür erst beim Start unveränderliche Konfigurationen
mit einer an Provider, Region und vorhandene Credentialreferenz gebundenen
Uploadfreigabe. Die Vorschau prüft dieselbe daraus resultierende Konfiguration,
mutiert aber weder Draft noch gespeicherte Aufträge. Ohne passenden Schlüssel
bleibt Caption-first möglich, ein STT-Zweig wird dadurch nicht still freigegeben.
Der Coordinator und Provideradapter behalten ihre unabhängigen Sicherheitsprüfungen.
Globale Vorgaben, Presets, fremde Quellen und frühere Aufträge erben keine Freigabe.

Die App-Sprache nutzt AndroidX `AppCompatDelegate.setApplicationLocales` mit
`AppCompatActivity`, Standard-Android-Ressourcen und `autoStoreLocales` für
Android 12 und älter. Keine zweite Einstellungskopie in Room/DataStore.
AppCompat 1.8.0 wurde gegen aktuelle Release Notes und Google-Maven-Metadaten
verifiziert. Auf Android 13+ ist dieselbe Auswahl in den Systemeinstellungen
sichtbar. Spracheinstellungen sind unabhängig von ASR-Sprache und Jobkonfiguration.
Nicht-Activity-Texte nutzen den lokalisierten ContextCompat-Kontext.

Quellen: [Android App-Sprachen](https://developer.android.com/guide/topics/resources/app-languages?hl=en),
[AppCompat 1.8.0](https://developer.android.com/jetpack/androidx/releases/appcompat?hl=en).
AndroidX übernimmt bei alten Android-Versionen eine kleine synchrone Locale-Datei;
diese dokumentierte Plattformlösung ersetzt eine eigene Startup-Persistenzlogik.

Abnahme: de/en einschließlich Prozessneustart, stabile Vorschau/Jobdaten beim
Sprachwechsel, keine ungefragte Submission, Startfreigabe nur für gewählte
Konfiguration, Theme-Auswahl weiterhin unabhängig, Screenshots in beiden Sprachen
und bei 200-%-Schrift. Noch kein Test-PASS durch diesen Entscheid allein.
