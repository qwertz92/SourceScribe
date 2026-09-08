# ADR 0008 — Updatefehler an bestehenden I/O-Grenzen prüfen

Datum: 8. September 2026. Status: Implementiert, auf Android geprüft und unabhängig reviewed.

T25/T27 verlangen reproduzierbare Schreibfehler, Offline- und Rate-Limit-Antworten
im echten Updateablauf. Vorbereitete Zustandsdateien allein prüfen diese Pfade
nicht. Ein interner Konstruktor erhält deshalb eine OkHttp-Call.Factory und eine
Schreibfunktion für heruntergeladene Dateien. Es entstehen keine zusätzlichen
Transportinterfaces, konfigurierbaren Endpunkte oder persistenten Testschalter.

Der öffentliche Konstruktor bindet weiterhin ausschließlich den vorhandenen
Client mit begrenzten Timeouts und deaktivierten automatischen Redirects/Retry
sowie FileOutputStream mit flush/fsync. Allowlist, Release-URL-Bindung,
Signaturprüfung und Aktivierungsprobe bleiben im eigentlichen Manager und gelten
auch für injizierte Antworten. App-Code nutzt ausschließlich diesen öffentlichen
Konstruktor. Testantworten und fehlerhafte Schreibfunktionen liegen im Test-APK.

Die Instrumentation prüft echte Manager-Aufrufe gegen kontrollierte I/O-Antworten,
aktiven Slot, Bereinigung und Neustart. Ein injizierter Schreibfehler ist
TESTED_WITH_FIXTURES; er ist ausdrücklich kein vollständig gefülltes physisches
Dateisystem. r63 bestand alle 16 deterministischen Android-Managerprüfungen auf API 37,
x86_64 und 16-KB-Seiten. Der getrennte Live-Test bestand die signierte
Nightly-Aktivierung samt öffentlicher YouTube-Probe und Rollback. Der unabhängige
Luna-Abschlussreview bestätigte keinen weiteren Produktions- oder Sicherheitsdefekt;
der Hauptagent prüfte die tatsächlichen Runnerausgaben. Physisches ENOSPC und
ARM64 bleiben separate, nicht ausgeführte Nachweise.
