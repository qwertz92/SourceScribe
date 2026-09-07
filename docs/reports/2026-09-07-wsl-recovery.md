# WSL-Speicherfehler und Wiederaufnahme

Tatsächlich geprüft am 7. September 2026 gegen 18:24 CEST. Keine globalen
Sicherheitseinstellungen, Swap-Konfigurationen oder laufenden Dienste geändert.

## Befund

- Kerneljournal des vorherigen Boots: am 7. September um 18:14:17 und 18:16:50
  `Out of memory: Killed process 26230 (python3)`, `anon-rss:7463876kB`.
- Dabei `Total swap = 4194304kB` und `Free swap = 0kB`: die alte 4-GiB-Grenze
  war noch aktiv. Der konkrete Python-Aufruf ist dadurch noch nicht identifiziert.
- Im letzten Speicherdump zusätzlich `shmem:4425320kB`. `/tmp` ist tatsächlich
  tmpfs; dort lagen zuvor mehrere GiB SDK-/Gradle-Dateien. Diese Einrichtung
  erhöhte den Speicherdruck. Sie beweist nicht alleine den Python-Verbrauch.
- Neuer Kernelboot um 18:17:25, Boot-ID
  `f9343bc4-7b90-47ae-becd-7c673e4cf0f5`. Vorherige Boot-ID
  `d7a9bf09d45642ca952c45d9b131d1ca`.
- `Adding 16777216k swap on /dev/sdc`; `/proc/swaps` und `free -h` bestätigen
  **16 GiB tatsächlich aktiven Swap**, zum Prüfzeitpunkt unbenutzt.
- Rund 15,4 GiB Linux-RAM gesamt, 940 MiB belegt und 14,4 GiB verfügbar bei der
  ersten Messung nach Wiederaufnahme.
- `systemctl poweroff` beendete um 18:18:35 die Distribution; um 18:19:01 wurde
  deren Dateisystem neu eingehängt. Die VM blieb innerhalb desselben Kernelboots.

`VmmemWSL` bilanziert die WSL-VM unter Windows. Der Prozess ist kein Beleg,
dass ein einzelner Linux-Prozess oder eine bestimmte Distribution noch läuft.
Mehrere Distributionen teilen Kernel, RAM und Swap; die Windows-Desktop-Oberfläche
von Codex verwendet hier Werkzeuge in WSL. Vollständiges `wsl --shutdown` beendet
auch die VM; ein erneuter Zugriff kann sie wieder starten.
Primärquellen: [Microsoft WSL-Architektur](https://learn.microsoft.com/en-us/windows/wsl/about),
[Shutdown und Terminate](https://learn.microsoft.com/en-us/windows/wsl/basic-commands).

## Änderung im Projekt

- SDK, Gradle-Cache und temporäre Build-Dateien liegen künftig unter dem bereits
  ignorierten `.local-tools/` auf dem Projektlaufwerk, nicht in `/tmp`.
- Gradle: maximal 2 GiB Heap, 768 MiB Metaspace, ein Worker, keine parallelen
  Projekte und kein dauerhaft laufender Daemon.
- `tools/build-local.sh` serialisiert Builds mit `flock`, setzt die lokalen
  Cachepfade und begrenzt einen Lauf einschließlich Wartezeit auf 20 Minuten.
- Quellcode und Room-Schemas blieben erhalten. Laufende Shell-/Gradle-Aufrufe und
  Dateien im tmpfs gingen verloren. Frühere Testberichte werden nicht rückwirkend
  als neue Tests ausgegeben; aktuelle Prüfungen werden erneut ausgeführt.

Diagnosebefehle: `journalctl --list-boots`, `journalctl -k -b -1`, `dmesg -T`,
`free -h`, `cat /proc/swaps`, `cat /proc/sys/kernel/random/boot_id`,
`findmnt -no TARGET,FSTYPE /tmp`.

Status: Speicherfehler, Neustart und Swap-Aktivierung `LIVE_VERIFIED`.
Die Zuordnung des Python-Prozesses wurde anhand der erhaltenen Agenten-/Hostprotokolle
untersucht, bleibt aber unbelegt (`BLOCKED`: fehlender Prozessaufruf). Erneute
App-Gesamttests waren zu diesem Diagnosezeitpunkt `NOT_RUN`.

Bei laufendem Build r22 um 18:57 CEST: 3,1 GiB RAM belegt, 12 GiB verfügbar,
16 GiB Swap weiterhin vollständig unbenutzt.
