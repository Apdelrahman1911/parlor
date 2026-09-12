# Independent review: first Android attempt and listener-only retry

Reviewer: `/root/whodunit_cont`; 2026-09-05. No adb, emulator, keytool, or Gradle execution by this reviewer. Disassembly tools only read the installed SDK binary.

## Observed result and classification

Root-owned `android-managed-01` failed **before Gradle, synthetic signing, or emulator launch**. Its foreground adb PID36540 exited with SIGABRT (`-6`) after:

```text
could not install *smartsocket* listener: listening on specified hostname currently unsupported
```

The reviewed runner launched `adb -L tcp:127.0.0.1:52894 server nodaemon`. Its ADB readiness guard rejected the exited process before the checked-in signing/build harness could launch. No application test ran. Classification: **AUDIT HARNESS ARGUMENT FAILURE**, not a Parlor app defect, test failure, device result, or Store finding. Preserve this failed receipt rather than relabeling it PASS.

The recorded finalizer observed no remaining owned processes/unknown holders, unlinked both task-owned Gradle cache pointers, removed its temporary root, and left no build outputs. `cleanup_status=PASS`; `runtime_evidence_status=FAIL`. Root separately matched the generated macOS crash report by process/name/parent/time, retained compact metadata, and removed that exact owned diagnostic (`evidence/android-managed-01/owned-crash-report-cleanup.json`). No unrelated diagnostic report was required.

## Independent source and exact-binary proof

Public AOSP commit `1cf2f017d312f73b3dc53bda85ef2610e35a80e9` corroborates:

- `socket_spec.cpp:78–118`: `tcp:PORT` produces an empty hostname; explicit host/port is parsed separately.
- `155–158`: the helper recognizes only empty hostname or literal `localhost` as local.
- `339–367`: listening uses the all-interface branch only for empty hostname with `gListenAll`; empty/local hostname otherwise selects loopback; literal `::1` selects IPv6; another explicit hostname returns the observed error.
- `186–241`: connecting is a separate path and accepts explicit numeric IPv4 through `network_connect`.
- `client/commandline.cpp:1627–1677`: only `-a` selects all-interface listening; explicit `-L` overrides `ADB_SERVER_SOCKET` for the server invocation.

This public commit is **not claimed to be the exact SDK37 source**. Read-only ARM64 disassembly of the actual installed adb supplies the applicable-version proof:

- `0x1000a314c–0x1000a319c`: empty hostname or the exact bytes `localhost` selects the loopback branch.
- `0x1000a326c–0x1000a3280`: another hostname emits the exact observed error and returns `-1`.
- `0x1000a3394–0x1000a33a0`: passes the chosen port and `true` to `network_loopback_server`.
- `0x1000d5a8c–0x1000d5cc8`: loopback server tries IPv4 and limited IPv6 fallback; its address callbacks are selected before `bind`.
- `0x1000d5ee8–0x1000d5f10`: IPv4 callback writes family2 and address bytes `7f 00 00 01` (127.0.0.1); `0x1000d5eb8–0x1000d5ee4` references `in6addr_loopback`.

Exact command receipts, binary/source hashes, authoritative URLs/access dates, selected source, and disassembly are retained beside this note. Failed source-path lookups are recorded separately and are not evidence. Unneeded full downloads were not retained.

## Minimal correction and source-bound approval

Root changed only three audit-helper lines: cycle ID to `android-managed-02`, its unique temporary-prefix label, and **server-only** `-L` from `tcp:127.0.0.1:PORT` to `tcp:localhost:PORT`. Client `ADB_SERVER_SOCKET=tcp:127.0.0.1:PORT` and `ANDROID_ADB_SERVER_PORT` stay unchanged. Explicit localhost is preferable to relying on empty-host default behavior because it cannot choose the all-interface branch even if a future flag changes.

The exact three-line diff was independently read against preserved646line revision74cf. New646line SHA256: `b34ad4662ac906bff0a2f4e8ebe25c0757229098361e8e34c29b7c06681730c0`. All process ownership, listener PID attestation, discovery isolation, input/evidence capture, strict verification, and cleanup guards are unchanged.

Decision: **SAFE TO ATTEMPT** this exact retry revision. Runtime boot, registration, tests, and cleanup remain unverified until the new cycle completes. The previous approval/coverage remains bound to its exact preserved74cf snapshot; the new approval does not rewrite earlier evidence or claim a successful Android run.
