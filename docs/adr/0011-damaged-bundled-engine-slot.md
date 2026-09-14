# ADR 0011 — Ein beschädigter Slot der gebündelten Engine wird ersetzt

Datum: 14. September 2026. Status: Implementiert in Runde 18. Welche Prüfungen gelaufen sind, steht in
[STATUS.md](../STATUS.md).

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
Prüfsummen und Signatur geprüft hat. Die Kopie liegt vollständig und geprüft im temporären Verzeichnis, bevor der
alte Slot entfernt und sie an seine Stelle umbenannt wird. Eine symbolische Verknüpfung wird dabei selbst entfernt,
nie ihr Ziel. `stage` lehnt einen solchen Slot weiter ab.

`docs/SECURITY_UPDATES.md` verlangt in S7, keine Dateien unter einem laufenden Prozess auszutauschen. Das gilt einer
gültigen Engine. Bytes, die nicht zu ihrem Hash passen, sind keine, und wer sie ausführt, führt beschädigte Daten
aus. Die Ausnahme ist ein Lesefehler beim Hashen einer intakten Datei, den `validSlot` wie jede Ausnahme als
ungültig wertet. Dann kommen dieselben Bytes zurück, und ein Prozess, der genau zwischen Entfernen und Umbenennen aus
der Datei liest, scheitert sichtbar. yt-dlp läuft nur beim Auflösen einer Quelle und beim Herunterladen, also vor
jeder Anfrage an einen Anbieter; eine kostenrelevante Anfrage wiederholt ein solcher Fehler nicht.

## Verworfene Alternativen

- **Auch in `stage` ersetzen:** Ein Update ist freiwillig, und die Ablehnung blockiert dort nur dieses Update. Der
  Slot kann einer heruntergeladenen Engine gehören, an die ein laufender Auftrag gebunden ist. Bis Runde 19 stand
  hier außerdem, für den Weg bis zum Slot gebe es keinen deterministischen Test, weil eine gültige Signatur zu einem
  echten Release gehöre. Die gebündelte Engine ist aber selbst ein signiertes Release, und ein Test liefert sie
  `stage` über eine Attrappe des Netzes aus.
- **Beim Laden jeden ungültigen Slot löschen:** trifft auch Slots, an die Aufträge gebunden sind, und ersetzt nichts.
- **Den Fehler hinnehmen:** Die einzige Abhilfe war, die App-Daten zu löschen.

## Tests

`EngineUpdateManagerTest.aDamagedBundledSlotIsReplacedWithTheVerifiedEngineInsteadOfStoppingEveryCall` kürzt die
Datei, während der Manager läuft, der sie geprüft hat, entfernt sie vor einem Neustart und legt an ihre Stelle eine
symbolische Verknüpfung, deren Ziel unverändert bleiben muss.

`EngineUpdateManagerTest.stageRefusesADamagedSlotOfTheEngineItDownloadedInsteadOfReplacingIt` liefert `stage` die
gebündelte Engine über eine Attrappe des Netzes, beschädigt ihren Slot während des Downloads und verlangt
`VERIFICATION` und den Slot, wie er war; erst der nächste Aufruf, der die gebündelte Engine einrichtet, ersetzt ihn.
