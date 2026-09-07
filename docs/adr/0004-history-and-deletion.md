# ADR 0004: Wiederaufnahme, neue Versuche und Löschung

7. September 2026. Eine sichere Wiederaufnahme verwendet denselben Versuch und
seine Submission-Belege. Eine ausdrücklich bestätigte neue Ausführung erhält
eine neue Attempt-ID und ein neues Artefakt; alte Ergebnisse bleiben erhalten.
Beim gezielten Wiederholen werden nur Zweige ohne vollständiges Ergebnis neu
angelegt. Ein anderer Provider wird vor dem Start als neuer Konfigurationssnapshot
in der Quellenvorschau bestätigt. Ungewisse oder entfernte Aufträge können weiter
Kosten verursachen; Wiederholung ist deshalb keine automatische Fehlerbehandlung.

Löschen wird zuerst dauerhaft als Absicht in Room gespeichert. Neue Claims sind
dann gesperrt. Erst nach Ende lokaler Worker werden ausschließlich Dateien dieses
Jobs entfernt und seine DB-Bezüge gelöscht. Ein Absturz setzt beim nächsten Start
diese bestätigte Löschung fort. Externe Exporte werden nicht gelöscht. Von anderen
Jobs verwendete Importdateien bleiben erhalten. Schema 2 ergänzt die Löschabsicht
mit einer nichtdestruktiven Migration von Schema 1.

Globale Parallelität ist die gemeinsame Scheduler-Kapazität (1–4); Provider,
Kostenrahmen, Quellenwahl und alle Beschaffungsoptionen bleiben im Job-Snapshot.

Ein automatischer Rückfall verwendet eine deterministische UUID aus der ID seines
Caption-Versuchs. So entsteht pro Versuch höchstens ein STT-Zweig; zurückgestellte
Systemuhren oder gleiche Millisekunden unterdrücken keinen neuen ausdrücklichen
Versuch. Die DB-Transaktion prüft zusätzlich Besitzer und Ablauf der Arbeitslease.

Reviewergänzung vom 8. September: Die Recovery prüft auch die Gegenrichtung
„Room-Artefakt ohne finalisierte Datei“. Der betroffene Versuch erhält einen
sichtbaren Integritätsfehler; Metadaten und andere Ergebnisse bleiben erhalten.
Ungültige gespeicherte Job-Konfigurationen sperren nur den betroffenen Auftrag.
Es wird kein Ersatzsnapshot mit Standardwerten erzeugt. Verlauf und Einstellungen
zeigen diesen Zustand, ohne die defekte Konfiguration erneut als gültig zu laden.
Caption-Provenienz wird bei normaler Persistierung und Recovery zusätzlich an
die im Versuch gepinnte, unveränderliche Engine-Installation gebunden.
