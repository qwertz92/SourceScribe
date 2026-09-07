# ADR 0003: Kurze, dauerhaft gesicherte Arbeitsschritte

Datum: 7. September 2026. Architekturentscheid; Gerätenachweis folgt integriert.

WorkManager übernimmt Recovery, Metadaten/Captions und einzelne kurze Schritte.
Ein Durchlauf führt höchstens einen fälligen Schritt aus und sichert den nächsten
Zustand. Passive AssemblyAI-Verarbeitung hat keinen laufenden Worker: Remote-ID
und nächster Poll-Zeitpunkt liegen in Room, danach wird ein neuer begrenzter
OneTimeWorkRequest fällig. OS-Zeitpunkte sind keine exakten Timer.

Downloads, Audioaufbereitung und einzelne Uploads werden begrenzt und erhalten
eigene Checkpoints. Eine harte Grenze von acht Minuten je HTTP-Aufruf bzw.
Arbeitsschritt verhindert einen stundenlang laufenden Worker. Längere vom Nutzer
gestartete Transfers können auf API 34+ über einen UIDT-Job laufen, sofern die
Startbedingungen tatsächlich erfüllt sind; Android 29–33 verwendet begrenzte
WorkManager-Schritte. Ein UIDT-Job hält keine passive Providerwartezeit offen.
Vor Implementierung/Nachweis dieses Treibers besteht keine UIDT-Freigabe.

Zentrale Claims/Leases und Ressourcenlimits liegen in Room, nicht ausschließlich
in Prozess-Semaphoren. Ein neuer Prozess rekonstruiert abgebrochene Schritte;
eine gespeicherte kostenrelevante Submission-Absicht ohne sichere Antwort wird
SUBMISSION_UNCERTAIN. Ein abgelaufener Lease erlaubt keine blinde Neusubmission.
Force-Stop wird erst beim zulässigen nächsten App-Start abgeglichen.

Primärquellen, am 7. September geprüft:
[UIDT und Eignung von WorkManager für kurze unterbrechbare Transfers](https://developer.android.com/develop/background-work/background-tasks/uidt)
und [Quoten langer Worker ab Android 16](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running).

## Wiederherstellung nach Prozessverlust

Beim ersten Start werden alle geerbten Versuchs- und Ressourcenleases gelöscht,
auch wenn der letzte Checkpoint bereits WAITING oder QUEUED war. Persistierte
Poll-Zeitpunkte bleiben erhalten. Die Wiederherstellung erfolgt vor neuen Claims;
später scheiterndes WorkManager-Enqueue wiederholt diesen Abgleich nicht während
bereits laufender Arbeit. Eine gültige atomare Artefaktdatei kann ihre fehlende
Room-Zeile nur mit eindeutigem, passendem PERSIST-Checkpoint reparieren. Quelle,
Konfigurationssnapshot, Zweig, Provider und gegebenenfalls bestehende Row-Hashes
müssen übereinstimmen. Unzuordenbare Dateien werden nicht erfundenen Jobs zugeordnet.

Temporäre Versuchsdateien werden erst nach finalem Checkpoint und gesichertem
Artefakt gelöscht. Wiederholbares Startup-Cleanup schließt das anschließende
Absturzfenster. Verwaiste Import-Teildateien sowie aufgegebene Importvorschauen
werden unter denselben Lebenszyklussperren wie Start/Löschung bereinigt.
Exporte bekommen einen unabhängigen Abgleich ihrer externen Dokumentreferenz;
ein unterbrochener Schreibvorgang bleibt sichtbar und wird ausschließlich nach
expliziter Nutzeraktion in eine neue Datei wiederholt. Das fordert keine neue STT an.
