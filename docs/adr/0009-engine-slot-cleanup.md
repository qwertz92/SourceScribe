# ADR 0009 — Alte Engine-Slots räumen, wenn eine neue Engine keinen Platz findet

Datum: 13. September 2026. Status: Implementiert in Runde 17, berichtigt in Runde 18 (letzter Abschnitt). Welche
Prüfungen gelaufen sind, steht in [STATUS.md](../STATUS.md).

## Kontext

`EngineUpdateManager` hält höchstens fünf Engine-Slots (`MAX_INSTALLATIONS`). Entfernt wurde bis Runde 17 nie
eine gesunde Installation: `discardUnhealthyCandidate` löscht nur ungesunde Kandidaten, `cleanupCrashOrphans`
nur Slots, die kein Eintrag in `state.json` nennt. Waren alle fünf Slots belegt, etwa durch die gebündelte
Engine und vier geladene Updates, endete jedes weitere `stage` mit `STORAGE`. Schwerer wog ein App-Update mit
einer anderen gebündelten yt-dlp-Version: `ensureBundledLocked` fand keinen Slot und warf ebenfalls `STORAGE`.
Weil `active()`, `installations()`, `rollbackTarget()`, `stage()` und `activate()` diese Funktion zuerst
aufrufen, konnte die App danach keine YouTube-Quelle mehr prüfen und keinen Auftrag mehr starten oder
fortsetzen, dauerhaft und ohne eine Handlung, die das behob. Gemeldet hat das der Invarianten-Reviewer der
Runde 17.

Vorgabe aus `docs/SECURITY_UPDATES.md`, Abschnitt S7: „Aktive, vorherige gesunde und gebündelte Version
erhalten; alte Versionen erst ohne aktive Referenzen bereinigen.“

## Entscheidung

- **Wann geräumt wird:** nur wenn ein neuer Slot entstehen soll und keiner frei ist, in `materializeSlot`. Beim
  Laden eines Updates ist das der Moment nach Download und Signaturprüfung. `stage` fragt vor dem Download nur,
  ob sich Platz schaffen ließe, und entfernt dabei nichts: Für einen Download, der noch scheitern kann, geht
  keine Installation verloren.
- **Nie entfernt:** die aktive und die vorherige Installation, die Engine, die diese App-Version bündelt, die
  ankommende Installation selbst und jede Installation, an die ein nicht abgeschlossener Versuch gebunden ist,
  gleich ob er läuft oder auf Netz, Anbieter oder den Nutzer wartet.
- **Reihenfolge:** zuerst Slot-Verzeichnisse, die kein Eintrag nennt, dann die ältesten Installationen, auf die
  nichts verweist. `state.json` hält die Einträge in der Reihenfolge ihrer ersten Aufnahme. Ein abgeschlossener
  Versuch verweist auf keine Engine, auch nicht mit einem Teilergebnis (Berichtigung in Runde 18).
- **Woher der Manager weiß, was Aufträge brauchen:** Das Modul `extractor` kennt keine Aufträge. Die App
  übergibt im Konstruktor eine Funktion `references` (`AppModule`), die `engineReferences` über Room beantwortet.
  Gefragt wird nur, wenn tatsächlich etwas weg muss. Scheitert die Abfrage, geht ihr Fehler unverändert weiter,
  und nichts wird entfernt: Ohne dieses Wissen ist kein Slot sicher zu löschen, und ein Grund, den niemand
  geprüft hat, wird nicht behauptet.
- **Wie entfernt wird:** erst der Eintrag in `state.json` (über `AtomicFile`), dann das Verzeichnis. Ein Absturz
  dazwischen hinterlässt einen Slot ohne Eintrag, den die nächste Ladung eines lesbaren Zustands entfernt.
  Scheitert das Schreiben, behält auch der Zustand im Speicher den Eintrag.
- **Eigener Code:** Reicht das Entfernbare nicht, endet der Aufruf mit `SLOTS_IN_USE`, bevor irgendetwas entfernt
  ist. `STORAGE` bleibt für Fälle, in denen das Dateisystem versagt, auch für ein Verzeichnis, das sich nicht
  löschen ließ. Beide Codes haben in der App eigene Texte.
- **Nebenbei vereinheitlicht:** Der Untertitelzweig in `JobCoordinator.captions` sucht die gebundene Engine jetzt
  wie `SttStep.pinnedEngine` und wartet mit `ENGINE_NOT_AVAILABLE`, statt über `single` eine namenlose Ausnahme
  zu werfen. `ENGINE_NOT_AVAILABLE` hat einen eigenen Text.

## Verworfene Alternativen

- **Nur geladene Updates entfernen, gebündelte Engines nie:** Alte gebündelte Engines sammeln sich über
  App-Updates hinweg an; das eigentliche Problem bliebe.
- **Eine Schaltfläche „Engines löschen“:** Der Nutzer sieht nicht, welche Engine ein unfertiger Auftrag noch
  braucht, und `ensureBundledLocked` läuft auch im Hintergrund ohne jemanden, der tippen könnte.
- **Einen Fehler von `ensureBundledLocked` hinnehmen, solange die aktive Engine gesund ist:** Das ändert die
  Zusage an sechs Aufrufstellen, dass die gebündelte Engine als Rückfall immer bereitsteht.
- **Referenzen zusätzlich in `state.json` führen:** eine zweite Wahrheit neben Room, die bei Abbruch und Löschung
  auseinanderlaufen kann.

## Folgen und Restrisiken

- Ein unfertiger Versuch hält seine Engine auch in Phasen, in denen er sie nicht mehr ausführt, etwa ein Versuch
  „Nur Fehlendes“. Das schützt mehr als nötig, nie weniger.
- `stage` fragt die Referenzen vor dem Download und beim Räumen danach getrennt. Entstünde dazwischen ein Versuch
  „Nur Fehlendes“, der die Engine seines Vorgängers übernimmt, ohne den Manager zu fragen, und war genau diese zum
  Räumen vorgesehen, endete das Update nach dem Download mit `SLOTS_IN_USE`, und entfernt wäre nichts. Heute
  verhindert das nur die Sperre von `MainViewModel.action`, über die Update und Wiederholung beide laufen, und sie
  gilt je View-Model. Jeder andere neue Versuch einer YouTube-Quelle fragt den Manager nach der aktiven Engine und
  wartet, bis `stage` ihn freigibt, oder übernimmt als Rückfall auf STT die Engine eines Untertitelversuchs, der sie
  in diesem Moment noch hält.
- Scheitert `stage` nach dem Räumen, etwa am Selbsttest, bleibt die entfernte Installation entfernt.
- Ist `state.json` nicht lesbar, nennt der Zustand keinen Slot, und alle vorhandenen Verzeichnisse gelten als
  zuerst entfernbar, soweit kein Auftrag an sie gebunden ist. Das nimmt vorweg, was ohnehin geschieht:
  `ensureBundledLocked` schreibt den Zustand dabei neu, und die nächste Ladung räumt Slots ohne Eintrag.

## Tests

`EngineUpdateManagerTest` prüft Reihenfolge, Schutz, die Ablehnung ohne Entfernen, den Fehler der Abfrage und das
Laden eines Updates, das vor dem Download abgelehnt wird oder am Download scheitert. `EngineReferencesTest` prüft die
Room-Abfrage, `AppPipelineTest.aCaptionAttemptWhoseEngineIsGoneWaitsWithThatReason` den Untertitelzweig und
`SttMissingRetryTest.aMissingChunkRetryOfAVideoCompletesWithoutTheEngineItIsBoundTo`, dass „Nur Fehlendes“ ohne
seine Engine auskommt. Den Weg bis zum erfolgreich geladenen Update prüft nur der getrennte Live-Test, weil eine
gültige Signatur zu einem echten Release gehört.

## Berichtigung in Runde 18

Bis Runde 18 hielt der Manager zusätzlich die Engine des jüngsten STT-Versuchs eines Auftrags, dessen Ergebnis nicht
bestätigt vollständig war, und ließ sie nur als letzten Ausweg für die gebündelte Engine weichen, nie für ein Update.
Die Begründung, „Nur Fehlendes“ laufe mit dieser Engine weiter, stimmte nicht: `SttStep.prepareMissingRetry` legt den
neuen Versuch in `Phase.SUBMIT` an, mit den Audioabschnitten, die sein Vorgänger vorbereitet hat, und nur `resolve`
und `download` fragen die gebundene Engine. Der neue Versuch trägt ihre Id weiter, als Angabe, woher das Audio
stammt (ADR 0006), nicht als Datei, die er ausführt. Der Schutz ließ dagegen ein freiwilliges Update mit
`SLOTS_IN_USE` scheitern, und dessen Text verlangte, das Teilergebnis abzuschließen oder zu löschen. Gefunden hat das
der Invarianten-Reviewer der Runde 18. Seitdem hält nur ein unfertiger Versuch seine Engine.
