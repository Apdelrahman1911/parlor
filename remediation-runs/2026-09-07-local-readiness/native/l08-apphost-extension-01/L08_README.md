# L08 — Additive native storage and retained-host verification

**Draft, not executed.** These controls require an independent source/diff review,
successful control tests, a fresh full source binding, and actual root-coordinated
native execution before any result is claimed. Prior eight-boot health receipts
remain unchanged: they did not exercise full `GameSnapshot` recovery.

## Actual boundaries under test

The original Debug app, Koin factories, native controller, locale/theme provider,
game definitions, reducers, snapshot codecs, and storage implementations run in
the existing runner's newly owned simulator/source copy. Additional `.kt.in` and
`.swift.in` files are **copy-only instrumentation**, never shipping source or a
new production dependency. No private seed, role map, canonical payload, snapshot
hash, credential value, or arbitrary native path is an evidence field.

### Storage: 13 cold launches, 20 durable operations

| Boots | Purpose |
|---|---|
| 1–2 | Whodunit Classic: reducer-built Round 1 checkpoint; restart, real Home Resume, real recovery, continue to PostGame |
| 3–4 | Whodunit Elimination: same complete store/recovery boundary, with the selected mode preserved |
| 5–6 | Mafia Doctor ON: four successive effective protections of one living target, Night 5 save/recovery, terminal continuation |
| 7–8 | Mafia Doctor OFF: alternating targets, explicit skip, then the pre-skip target; adjacent illegal repetition must not consume the choice |
| 9–10 | Seed and recover healthy legacy Mafia envelope; isolate a malformed legacy neighbor and a recognized damaged protected/valid legacy pair |
| 11 | Malformed record: actual Home failure, Retry, failure again, explicit Discard |
| 12 | Dual-copy record: fail closed, retain excluded legacy with equal expected envelope; actual Home/Retry failures, Discard removes both copies |
| 13 | After restart, all seven exact synthetic IDs return NotFound and neither native location retains them |

Each healthy resume observes the **actual controller attached by the original
game flow**, not a replacement resumed session constructed by the harness.
Synthetic drivers submit legal real-controller actions; they do not simulate
every gameplay tap. Only the actual terminal `SerializedSnapshotWriter` may
delete a completed game's save. Native backup exclusion and complete file/
directory protection are inspected, not repaired by observers.

The dual-copy seed first passes a real encrypted store roundtrip. A second
matching valid plaintext envelope is written only to its exact token-owned
legacy filename; only the protected record's last MAC byte is flipped in place.
Magic/version/length and original metadata remain observed. Failed protected
authentication must not fall back to legacy or delete the user's recovery copy.
Legacy bytes are compared with a freshly reducer-built expected envelope on
boot 12; no legacy payload is emitted. Retry failure is observed through the
original recovery coordinator; explicit Discard is the only destructive UI
action on these damaged records.

### Started host: 3 cold launches, 42 durable operations

Whodunit Classic and Mafia Doctor ON/OFF mount the original host flows through
the actual process owner. A copied **ControlledStartRoom test transport**, not
P2pKit, withholds peer Ready and validates the production start offer/commit,
frozen roster, direct snapshot recipients, and bounded queue/drop counters.
Mafia uses the original setup switch and Start callback; default OFF is observed.

After one canonical capture, each variant changes the real SettingsStore to
English, Arabic, and System, backgrounds/foregrounds via XCTest/UIKit three
times, and remounts game presentation once. Runtime/controller/StateFlow/value
identity and private assignment equality must remain unchanged. The original
Koin `AppLifecycleCoordinator` drives the explicit transport adapter through
Suspended/Resuming; real authoritative own-seat submissions must be rejected in
both states. Adapter-directed Active restoration is **synthetic**, not a LAN
rejoin proof. A final valid own-seat ACK and actual owner finalLeave must release
the runtime, room, jobs, and fixture process scope.

The test-only App presentation seam bypasses normal host lobby/navigation while
preserving the actual native root and app-owned locale/theme. This is not proof
of the normal host-entry journey, retained navigation, or real P2pKit recovery.

## Evidence, controls, and execution

`l08_receipts.py` requires the exact ordered cold-process/action matrix, closed
schemas, real native event sequences, actual raw geometry/preferences,
full per-process framework SHA256/UUID binding, and corresponding executed
XCTest rows. Missing/unexecuted/failed steps cannot become PASS. Unknown fields
are rejected before durable evidence copying; closed failures are retained.
Mock parser fixtures are never native evidence. The old five-XCTest discovery
contract and existing eight-boot/Settings/local-game validators are unchanged.

Root execution sequence after reviewed promotion:

1. Run all `scripts/verification/ios-readiness/test_*.py` with
   `/usr/bin/python3 -B -m unittest discover -s scripts/verification/ios-readiness -p 'test_*.py' -v`.
2. Re-freeze the complete current dirty/untracked build inputs using the existing
   `bind_source.py`; obtain independent approval of that exact control hash.
3. Use the existing owned-copy runner and a fresh exclusive cycle. Its default
   remains signing-disabled; any existing separately reviewed simulator ad-hoc
   comparison is not Store signing authorization.
4. Inspect copied diff, actual Xcode/test receipts, both L08 subgates, framework
   binding, post-worker source comparison, and cleanup before drawing conclusions.

Existing nested/outer Gradle-stop, signal handling, ownership-attested worker/
FIFO cleanup, simulator deletion, and source-copy/DerivedData finalization are
not replaced. Do not reuse the archived runner with unaddressed worker-path
cleanup limitations. Required logs remain compact; no screenshots/private
hierarchy dumps or synthetic payloads should be retained.

## Explicit remaining limits

Native compilation/runtime and every new test are currently **NOT RUN**. Swift/
Kotlin interop signatures, real tag reachability, callback scheduling, and the
increased matrix's existing timeout budgets need execution, not assumptions.
Foundation metadata observations do not prove physical lock/backup behavior.
Neither suite proves physical LAN discovery, admission, encrypted peer traffic,
credential resume, device lock, power-loss durability, every engine edge case,
full UI playthroughs, accessibility, Store signing/review, or historical crash
causes. Keep those gaps and original evidence classifications explicit.
