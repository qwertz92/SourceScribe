# WSL out-of-memory error and recovery

Checked September 7, 2026, around 18:24 CEST. No global security settings, swap configuration, or running services
changed.

## Findings

- Previous boot's kernel log: at 18:14:17 and 18:16:50, `Out of memory: Killed process 26230 (python3)`,
  `anon-rss:7463876kB`.
- At the time, `Total swap = 4194304kB` and `Free swap = 0kB`: the old 4GiB limit was still active. This does not by
  itself identify the specific Python call.
- The last memory dump also showed `shmem:4425320kB`. `/tmp` is tmpfs; several GiB of SDK/Gradle files had been
  sitting there, raising memory pressure — not by itself proof of the Python usage.
- New kernel boot at 18:17:25, boot ID `f9343bc4-7b90-47ae-becd-7c673e4cf0f5`; previous boot ID
  `d7a9bf09d45642ca952c45d9b131d1ca`.
- `Adding 16777216k swap on /dev/sdc`; `/proc/swaps` and `free -h` confirm **16GiB of swap actually active**, unused
  at check time.
- About 15.4GiB total Linux RAM, 940MiB used, 14.4GiB available at the first measurement after recovery.
- `systemctl poweroff` shut down the distro at 18:18:35; its filesystem was remounted at 18:19:01. The VM stayed
  within the same kernel boot.

`VmmemWSL` accounts for the whole WSL VM under Windows; it is not evidence that any one Linux process or specific
distro is still running — several distros share kernel, RAM, and swap, and the Codex Windows desktop app uses tools
inside WSL here. A full `wsl --shutdown` ends the VM; a later access can restart it. Primary sources:
[Microsoft WSL architecture](https://learn.microsoft.com/en-us/windows/wsl/about),
[Shutdown and terminate](https://learn.microsoft.com/en-us/windows/wsl/basic-commands).

## Project change

- SDK, Gradle cache, and temporary build files now live under the already-ignored `.local-tools/` on the project
  drive, not in `/tmp`.
- Gradle: max 2GiB heap, 768MiB metaspace, one worker, no parallel projects, no persistent daemon.
- `tools/build-local.sh` serializes builds with `flock`, sets the local cache paths, and caps one run, including
  wait time, at 20 minutes.
- Source code and Room schemas survived. Running shell/Gradle calls and files in tmpfs were lost. Earlier test
  reports are not retroactively presented as new tests; current checks are rerun.

Diagnostic commands: `journalctl --list-boots`, `journalctl -k -b -1`, `dmesg -T`, `free -h`, `cat /proc/swaps`,
`cat /proc/sys/kernel/random/boot_id`, `findmnt -no TARGET,FSTYPE /tmp`.

**Status:** the out-of-memory error, restart, and swap activation are `LIVE_VERIFIED`. Attributing the Python
process was investigated from the surviving agent/host logs but remains unproven (`BLOCKED`: the specific process
call is missing). A full app test rerun was `NOT_RUN` at this diagnostic point.

During build r22 at 18:57 CEST: 3.1GiB RAM used, 12GiB available, 16GiB swap still entirely unused.
