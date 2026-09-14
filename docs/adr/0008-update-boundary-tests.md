# ADR 0008 — Test Update Failures at Existing I/O Boundaries

Date: September 8, 2026. Status: implemented, verified on Android, and independently reviewed.

T25/T27 require reproducible write failures, offline responses, and
rate-limit responses within the real update flow. Prepared state files alone
don't exercise these paths. An internal constructor therefore takes an
OkHttp `Call.Factory` and a write function for downloaded files. This adds
no extra transport interfaces, configurable endpoints, or persistent test
switches.

The public constructor still wires up only the existing client, with
bounded timeouts, automatic redirects/retry disabled, and a
`FileOutputStream` with flush/fsync. The allowlist, release-URL binding,
signature verification, and activation probe stay in the actual manager and
apply to injected responses just the same. App code uses only this public
constructor. Test responses and faulty write functions live in the test
APK.

Instrumentation verifies real manager calls against controlled I/O
responses, the active slot, cleanup, and restart. An injected write failure
is `TESTED_WITH_FIXTURES`; it explicitly does not stand in for a genuinely
full physical file system. Revision r63 passed all 16 deterministic Android
manager checks on API 37, x86_64, with 16 KB pages. The separate live test
passed signed Nightly activation, including a public YouTube probe, and
rollback. The independent final Luna review confirmed no further production
or security defect; the lead agent verified the actual runner output.
Physical ENOSPC and ARM64 remain separate, not-yet-executed evidence.
