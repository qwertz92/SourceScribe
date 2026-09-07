# ADR 0002: Gemeinsame unveränderliche Verträge

Datum: 7. September 2026. Umsetzung beginnt im JVM-Modul `core`.

Source, JobConfig, Provenance und TranscriptDocument sind serialisierbare Werte.
YouTube-Identität ist die validierte Video-ID; lokale Imports erhalten eine eigene
ID und einen separat ermittelten SHA-256-Inhaltshash. Metadaten bleiben nullable.
UTC-Zeitpunkte werden als Unix-Millisekunden gespeichert. Generation, Übersetzung,
Provider, angefordertes und gemeldetes Modell sind orthogonale Felder.

Der Planner entscheidet ausschließlich anhand des gespeicherten JobConfig und
beobachteter Caption-Verfügbarkeit. Abruffehler unterscheiden sich von nachgewiesenem
Fehlen. Eine geplante STT-Aktion ersetzt weder Uploadfreigabe noch Capability-/Budgetprüfung.
CAPTIONS_THEN_STT fordert ein erfülltes alternatives Ergebnis, BOTH beide Ergebnisse.

Room speichert Sources, Jobs, Attempts, Artefaktverweise, Submission- und Exportrecords
getrennt; Snapshot und Artefaktinhalt bleiben unveränderlich. Jede Antwort wird vor
Parser/Export intern gesichert. Kostenrelevante Absichten werden vor Übermittlung
gesichert; unklarer Ausgang sperrt automatisches Wiederholen. Keine Exactly-once-Zusage.
