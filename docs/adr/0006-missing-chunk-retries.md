# ADR 0006: Bestätigte Wiederholung fehlender Audioabschnitte

Datum: 7. September 2026. Implementierungsentscheid; Nachweise im Testbericht.

„Nur Fehlendes“ betrachtet den jüngsten Versuch jedes tatsächlich angelegten
Zweigs, einschließlich des STT-Fallbacks. Ein früherer Erfolg verdeckt keinen
neueren Teilerfolg. Bei einem STT-Teilergebnis werden ausschließlich dessen
ausgewiesene fehlende Chunks neu angefragt. Ein vollständiger neuer Versuch
bleibt eine getrennte, ausdrücklich bestätigte Aktion.

Der neue Versuch übernimmt den unveränderten Konfigurationssnapshot, die
ursprüngliche Engine-Zuordnung und den geprüften Audio-Chunkplan. Vorbereitete
Audiodateien und erfolgreiche Originalantworten werden unter Quotenreservierung
in sein privates Verzeichnis kopiert und erneut an Quelle, Konfiguration und
Hashes gebunden. Fehlende oder veränderte Dateien verhindern diese Wiederholung;
es gibt keinen stillen Ersatz durch kostenpflichtige Neuberechnung erfolgreicher
Abschnitte. Erfolgreiche Antworten werden erneut normalisiert, nicht erfunden.

Room-Version 3 ergänzt `submissions.reusedFromId` und `rejectionCode`. Übernommene
Antworten verweisen auf ihre ursprüngliche Submission, reservieren selbst keine
weiteren Kosten und verändern deren ursprünglichen Kosteneintrag nicht. Das neue
Artefakt nennt sein Vorgängerartefakt in der Provenienz. Alte Artefakte bleiben
unverändert. Ein explizites Fortsetzen nach korrigierter Anmeldung setzt nur
eindeutig mit AUTHENTICATION/ACCESS_DENIED abgelehnte Submissions zurück.
SENDING und UNCERTAIN werden dadurch niemals erneut gesendet.

Technische Antwortspools und vorbereitete Audiodateien eines Teilergebnisses
bleiben bis zum vollständigen jüngsten Ergebnis oder zur ausdrücklichen Löschung
verfügbar. Diese begrenzte Wiederherstellungsaufbewahrung gilt auch ohne dauerhaften
Raw-Export; sie unterliegt weiterhin dem Speicherlimit.

Ein bereits in Room angelegter Job bleibt bei WorkManager-Enqueue-Fehlern ein
angelegter Job. Ein noch ungeclaimter Versuch wechselt sichtbar zu WAITING_USER
mit SCHEDULING_FAILED; ein verspätet gestarteter Worker kann diesen nicht claimen.
Ein bereits laufender Worker wird dabei nicht überschrieben. Dadurch täuscht
ein Schedulerfehler keinen fehlgeschlagenen Job-Start vor und erzeugt beim
erneuten Klick keine kostenrelevanten Duplikate.

Eine bestätigte Mehrfachauswahl wird als Ganzes in einer Room-Transaktion
angelegt, bevor der erste Worker eingeplant wird. Ein Prozessverlust während
der Transaktion hinterlässt keinen Teil-Batch; nach dem Commit findet die
Startup-Recovery alle noch nicht eingeplanten Versuche. Die UI entfernt die
Vorschauen erst nach dieser dauerhaften Übernahme. Jeder Quellenwechsel und
jede Änderung der ausgewählten Audiospur hebt eine frühere Upload-Freigabe auf.

Reviewergänzung vom 8. September: Eine im Teilergebnis fehlende Ausgabe kann
bereits eine bezahlte, gespeicherte Providerantwort besitzen, etwa wenn die
Summe der Antworten eine lokale Größenbegrenzung überschreitet. „Nur Fehlendes“
darf daraus keinen neuen Upload ableiten. Solche Antworten sowie ungewisse oder
noch laufende Submissions sperren diese Wiederholung vor jeder Dateikopie.

Retry-Dateien entstehen vor dem Room-Commit. Die Startbereinigung entfernt
deshalb ausschließlich kanonische UUID-Verzeichnisse im privaten `attempts`-
Ordner, für die unter dem gemeinsamen Lebenszyklus-Lock kein Versuch in Room
existiert. Verzeichnisse bestehender Versuche, fremde Namen und Symlinks bleiben
erhalten. So bleiben nach einem Prozessabbruch während der Kopie keine dauerhaft
quotenwirksamen, unreferenzierten Audio- und Antwortkopien zurück.
