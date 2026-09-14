# ADR 0011 — Ein beschädigter Slot der gebündelten Engine wird ersetzt

Datum: 14. September 2026. Status: Implementiert in Runde 18, in Runde 19 auf ein Ersetzen im Slot umgestellt, in
Runde 20 um den Ausgang ergänzt, wenn nur die zweite Umbenennung scheitert.
Welche Prüfungen gelaufen sind, steht in [STATUS.md](../STATUS.md).

## Kontext

`EngineUpdateManager.materializeSlot` lehnt seit `06b996a` einen Slot ab, dessen Verzeichnis existiert, dessen
Datei aber nicht die Bytes enthält, deren SHA-256 der Name des Slots ist, auch eine symbolische Verknüpfung an
ihrer Stelle: `VERIFICATION`, ohne etwas zu ändern. Für ein Update ist das richtig. Für die gebündelte Engine war
es eine Sackgasse. `bundled()`, `active()`, `installations()`, `rollbackTarget()`, `rollback()`, `stage()` und
`activate()` gehen zuerst durch `ensureBundledLocked`, alle Aufrufe des Managers außer `check()`,
`discardUnhealthyCandidate()` und `file()`. Also endete jeder davon mit `VERIFICATION`, nach jedem Start der App:
Keine YouTube-Quelle ließ sich mehr prüfen und kein Auftrag mehr starten, der eine Engine braucht, bis jemand die
App-Daten löschte und damit den Verlauf.

Wie ein Slot so werden kann: ein Fehler des Speichers; eine Entfernung, die zwischen dem Löschen der Datei und dem
des Verzeichnisses abbrach, während `state.json` nicht lesbar war, sodass die nächste Ladung nichts aufräumt; ein
Prozess mit derselben UID, der ohnehin keine Grenze ist. Beobachtet ist keiner dieser Fälle. Die Engines liegen in
`noBackupFilesDir`, und Backups sind abgeschaltet; eine Wiederherstellung scheidet als Ursache aus. Gefunden in
Runde 18 beim Prüfen von ADR 0010; kein Reviewerfund.

## Entscheidung

`ensureBundledLocked` ersetzt einen solchen Slot durch die Bytes, die es gerade aus dem APK kopiert und gegen
Prüfsummen und Signatur geprüft hat. Die Kopie liegt vollständig und geprüft im temporären Verzeichnis, bevor sie
etwas ersetzt. Ist der Slot ein Verzeichnis, nimmt sie mit einer einzigen Umbenennung den Platz der Datei darin ein,
die Metadaten mit einer zweiten, und das Verzeichnis bleibt. Steht an seiner Stelle etwas anderes, eine symbolische
Verknüpfung etwa, oder lässt sich die Datei so nicht ersetzen, wird der Slot entfernt und das temporäre Verzeichnis an
seine Stelle umbenannt. Eine symbolische Verknüpfung wird dabei selbst ersetzt oder entfernt, nie ihr Ziel. `stage`
lehnt einen solchen Slot weiter ab.

`docs/SECURITY_UPDATES.md` verlangt in S7, keine Dateien unter einem laufenden Prozess auszutauschen. Das gilt einer
gültigen Engine. Bytes, die nicht zu ihrem Hash passen, sind keine, und wer sie ausführt, führt beschädigte Daten
aus. Die Ausnahme ist ein Lesefehler beim Hashen einer intakten Datei, den `validSlot` wie jede Ausnahme als
ungültig wertet. Dann kommen dieselben Bytes zurück. Ein Prozess, der die Datei offen hat, liest weiter die alte, und
einer, der sie über ihren Pfad öffnet, findet die alte oder die neue, weil die Umbenennung den Eintrag in einem
Schritt austauscht. Das gilt für jeden der bis zu vier Aufträge, die gleichzeitig laufen dürfen. Nur wo der Slot
entfernt und neu angelegt wird, fehlt der Pfad dazwischen. Das geschieht, wo an seiner Stelle kein Verzeichnis steht,
ein Teil seines Pfads eine symbolische Verknüpfung ist oder in ihm statt der Datei ein Verzeichnis liegt; einen solchen
Slot lehnt `file()` ab, und kein Auftrag startet von ihm. Es geschieht aber auch, wenn die Umbenennung über eine
intakte Datei scheitert, etwa an einem Fehler des Speichers; dann fehlt der Pfad dazwischen wie in Runde 18 bei jedem
Ersetzen. yt-dlp läuft nur beim Auflösen einer Quelle und beim Herunterladen, also vor jeder Anfrage an einen
Anbieter; eine kostenrelevante Anfrage wiederholt ein Fehler dort nicht.

Scheitert nur die zweite Umbenennung, steht die geprüfte Datei schon im Slot, neben den alten Metadaten, und der
Aufruf endet trotzdem mit `STORAGE`: Ein Fehler beim Schreiben in den Speicher wird dem Aufrufer nicht verschwiegen.
Der nächste Aufruf findet den Slot gültig, weil `validSlot` nur die Datei prüft, und benutzt sie. Die alten Metadaten
bleiben liegen; `metadata.json` wird nur geschrieben und verschoben, gelesen wird es nirgends.

## Verworfene Alternativen

- **Auch in `stage` ersetzen:** Ein Update ist freiwillig, und die Ablehnung blockiert dort nur dieses Update. Der
  Slot kann einer heruntergeladenen Engine gehören, an die ein laufender Auftrag gebunden ist. Bis Runde 19 stand
  hier außerdem, für den Weg bis zum Slot gebe es keinen deterministischen Test, weil eine gültige Signatur zu einem
  echten Release gehöre. Die gebündelte Engine ist aber selbst ein signiertes Release, und ein Test liefert sie
  `stage` über eine Attrappe des Netzes aus.
- **Den Slot immer entfernen und das temporäre Verzeichnis an seine Stelle umbenennen, wie in Runde 18:** Dazwischen
  fehlt der Pfad, und ein Auftrag, der die Engine genau dann startet, scheitert, auch wenn nur ein Lesefehler den Slot
  ungültig erscheinen ließ. Gemeldet vom Code-Reviewer der Runde 19.
- **Den Aufruf gelingen lassen, sobald die Datei im Slot steht, auch wenn die Metadaten nicht folgen:** Die Engine
  wäre einen Aufruf früher nutzbar, aber der Fehler des Speichers bliebe unbemerkt. Vorgeschlagen vom Code-Reviewer
  der Runde 20.
- **Beim Laden jeden ungültigen Slot löschen:** trifft auch Slots, an die Aufträge gebunden sind, und ersetzt nichts.
- **Den Fehler hinnehmen:** Die einzige Abhilfe war, die App-Daten zu löschen.

## Tests

`EngineUpdateManagerTest.aDamagedBundledSlotIsReplacedWithTheVerifiedEngineInsteadOfStoppingEveryCall` kürzt die
Datei, während der Manager läuft, der sie geprüft hat, entfernt sie vor einem Neustart und legt an ihre Stelle eine
symbolische Verknüpfung, deren Ziel unverändert bleiben muss. Eine Datei, die neben der Engine im Slot liegt, bleibt
dabei jedes Mal liegen, weil das Verzeichnis nicht ersetzt wird, und zuletzt weicht eine symbolische Verknüpfung an
der Stelle des ganzen Slots, während das Verzeichnis, auf das sie zeigt, bleibt, wie es war.

`EngineUpdateManagerTest.stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt` liefert `stage` die
gebündelte Engine über eine Attrappe des Netzes, beschädigt ihren Slot während des Downloads und verlangt
`VERIFICATION` und den Slot, wie er war; erst der nächste Aufruf, der die gebündelte Engine einrichtet, ersetzt ihn.

`EngineUpdateManagerTest.aSlotRepairWhoseMetadataCannotFollowFailsWithStorageAndLeavesTheEngineRepaired` legt an die
Stelle der Metadaten ein Verzeichnis, das keine Umbenennung einer Datei ersetzen kann, und kürzt die Engine. Der
Aufruf endet mit `STORAGE`, im Slot liegen danach die geprüften Bytes, und der nächste Aufruf benutzt sie.
