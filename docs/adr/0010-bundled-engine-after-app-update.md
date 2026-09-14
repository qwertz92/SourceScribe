# ADR 0010 — Nach einem App-Update wird die neu gebündelte Engine aktiv

Datum: 13. September 2026. Status: Implementiert in Runde 17, Grenzfälle berichtigt in Runde 18, eine Begründung in
Runde 19. Welche Prüfungen gelaufen sind, steht in [STATUS.md](../STATUS.md).

## Kontext

`EngineUpdateManager.ensureBundledLocked` setzte die aktive Installation nur, wenn noch keine gesetzt war. Wer nie
selbst ein Engine-Update aktiviert hatte, behielt nach einem App-Update mit einer anderen gebündelten
yt-dlp-Version die gebündelte Engine der **alten** App-Version als aktive: `active()` gibt die aktive zurück,
solange sie gesund ist. Das App-Update kam bei der Extraktion also nie an. Die neue Engine lag nur als
Rückfallziel da, und „Zur vorherigen Engine“ bot sie mit dem Hinweis an, eine ältere Version könne Lücken
enthalten. Gefunden in Runde 17 beim Lesen des Codes für ADR 0009; kein Reviewerfund.

## Entscheidung

Beim ersten Start, an dem die Engine dieser App-Version noch keinen gesunden Eintrag mit `bundled = true` hat,
wird sie aktiv, **wenn die bisher aktive Installation selbst eine gebündelte ist**, also die einer früheren
App-Version. Die bisherige wird die vorherige und bleibt damit der Weg zurück. Hat der Nutzer eine
heruntergeladene Engine aktiviert (`bundled = false`), bleibt sie aktiv; das war eine Wahl, das Beibehalten der
alten gebündelten nicht.

Danach geschieht das nicht wieder: Geht jemand später bewusst zur alten gebündelten Engine zurück, bleibt sie
aktiv, weil die neue dann einen gesunden gebündelten Eintrag hat. Laufende und wartende Aufträge behalten ihre
Engine wie bisher (S7).

## Grenzfälle

- Hatte jemand genau diese Version schon als Update geladen und war danach zur alten gebündelten zurückgegangen,
  stellt das App-Update trotzdem auf sie um: Ihr Eintrag war bis dahin nicht als gebündelt markiert.
- Stand als vorherige eine dritte Engine, von der jemand bewusst zur alten gebündelten zurückgegangen war, ist sie
  danach nicht mehr über „Zur vorherigen Engine“ erreichbar, weil die alte gebündelte die vorherige wird. So endet
  auch jede Aktivierung von Hand: Der Weg zurück reicht genau einen Schritt. Die Installation bleibt in der Liste,
  bis sie beim Räumen weicht (ADR 0009).
- Ein Absturz zwischen den zwei Speicherungen in `ensureBundledLocked` verschiebt die Umstellung nur auf den nächsten
  Start. Dazwischen kann niemand zur alten Engine zurückgehen, weil `rollback()` und `activate()` zuerst
  `ensureBundledLocked` durchlaufen und die Umstellung damit nachholen.

Die ersten beiden Fälle sind selten, und ein App-Update ist selbst eine ausdrückliche Handlung.

Bis Runde 18 stand hier als erster Grenzfall, nach einem solchen Absturz oder mit einem Slot, der die Prüfung nicht
mehr besteht, werde erneut umgestellt, auch nach einem bewussten Zurückgehen. Beides stimmte nicht: Der Absturz
lässt kein Zurückgehen dazwischen zu, und ein ungültiger Slot endete in `materializeSlot` mit `VERIFICATION`, ohne
umzustellen, bei jedem Aufruf des Managers, der zuerst `ensureBundledLocked` durchläuft. Seit
[ADR 0011](0011-damaged-bundled-engine-slot.md) wird er ersetzt; der gesunde Eintrag bleibt, und umgestellt wird
nichts. Den zweiten Fall hat der Code-Reviewer der Runde 18 gemeldet. Dass die Begründung des dritten zu weit griff,
hat der Code-Reviewer der Runde 19 gezeigt: `check()`, `discardUnhealthyCandidate()` und `file()` gehen nicht durch
`ensureBundledLocked`, stellen aber auch nichts um.

## Verworfene Alternativen

- **Nie umstellen:** Das App-Update wirkt dann bei der Extraktion nicht, und die Runtime der neuen App muss mit
  einer yt-dlp-Version arbeiten, die nie mit ihr geprüft wurde.
- **Immer auf die neueste Version umstellen:** Versionsangaben sind über Stable- und Nightly-Kanal hinweg nicht
  verlässlich vergleichbar, und eine bewusst aktivierte Engine würde überschrieben.
- **Beim Start fragen:** ein Dialog vor jeder Nutzung, den Hintergrundaufträge nicht beantworten können.

## Tests

`EngineUpdateManagerTest` prüft das Umstellen beim ersten Start, das Beibehalten beim zweiten und nach einem
bewussten Zurückgehen und dass eine aktivierte heruntergeladene Engine aktiv bleibt.
