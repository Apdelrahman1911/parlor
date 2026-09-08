# Mafia fixes — independent second review

Reviewer: `/root/whodunit_cont`; author/finder: `/root/mafia_cont`.
Repository: `/Users/abdelrahman/Projects/parlor`; baseline `main`, commit
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. This is the **modified working
tree**, not an assertion that HEAD contains these fixes. Exact reviewed file
hashes/ranges and executed-source binding accompany this report in
`MF-C1-M-C03-independent-evidence.json`.

## Decision

- **MF-C1 / M-C01: bounded fix approved.** The previously confirmed Low local
  snapshot semantic-validation defect is addressed without changing game
  rules, snapshots' schema, protocol 4.2, or peer privacy boundaries.
- **M-C03: bounded fix approved.** Final-role width starvation is addressed by
  the final full-width Column implementation, not the superseded FlowRow.
  Both production routers reach the same corrected results composable.
- Cycle `games-session-desktop-03` executed all **267 Mafia tests in 31
  suites, zero failures/errors/skips**, including all 35 added tests. Actual
  retained XML was independently parsed, not just the author's summary.
- This is **not** approval of the complete multi-module command: it failed
  during Whodunit test compilation. No Whodunit tests ran in that cycle.
  Native builds, physical-device operation and Store readiness are not
  certified by this review. No commit, integration or issue closure occurred.

## MF-C1: root cause and correction

Original evidence reopened: `audit-runs/2026-09-05-source-audit/candidates/`
`M-C01-doctor-recovery-history.md` and `validations/MF-C1-whodunit_cont.md`.
The defect requires a malformed but successfully authenticated **local**
canonical snapshot; no normal reducer producer, remote exploit, or disk
authentication bypass was established. A mismatched Doctor private
`previousDoctorProtect` could otherwise change the following night's
default-OFF target restriction after Resume.

The current `MafiaSnapshotRecovery.kt:354–365` compares this field with the
latest retained `nightLog.doctorProtect` in every assigned nonterminal
snapshot. Settings/observable validation, exact role counts and private-role
agreement run before this comparison (`68–142`); at most one Doctor is
legal. No Doctor therefore means null rather than ambiguous ownership.

The executable expectation is the unchanged reducer, not prose:
`MafiaReducer.kt:445–475` writes both fields from the **same**
`resolution.effectiveDoctorTarget`. Pending submissions, Mafia-only revotes,
acknowledgements, day voting and next-night entry preserve the previous
effective value. A skip resets both to null. A Doctor killed that night
retains that night's effective target; a subsequently inactive Doctor's
next resolution sets both to null. The target dying later does not erase the
historical choice. The newest record survives the 128-entry suffix cap.

PostGame is the necessary exception: `finishGame` (`997–1016`) deliberately
clears the private field, while retaining host history. The pre-existing
terminal-private check (`Recovery:144–155`) still rejects a non-null terminal
value. Setup/unassigned early termination retains its existing separate
guard. No malformed state is normalized into a plausible different game.

The changed canonical check is reached by strict snapshot encode/decode and
`loadMafiaResumedSession:35–64`; local flow installs only a successful decoded
state. Peer codecs/validators never require or receive the host night log.
Public projections still exclude private slices; player projections retain
only that recipient's private state and remove seed, full role map and logs.

### Associated setting/helper changes

`DoctorTargetEligibility.kt:1–14` is an exact extraction of the existing
self-protection and repeat-target predicates, used **after** each Mafia
router's living/active filter. It stays in Mafia UI, not shared networking or
session infrastructure. Reducer authority and deterministic role assignment
are unchanged. The setting was already default OFF and already allowed more
than two consecutive nights when ON; this is not a new rule implementation.
Arabic now says consecutive nights without implying exactly two. The setup
comment now matches the actual atomic `ConfigureAndStart` path.

## M-C03: original failure, counter-evidence and final layout

Original dossier and root's independent validation were reread:
`candidates/M-C03-mafia-row-layout.md`, `validations/M-C03-root.md`.
The original audit's actual production-composable test measured **zero role
width** at 320dp with a policy-valid 32-W name. Unweighted Row siblings
allowed the name to exhaust the role's available width. Final roles are
intentionally public; this is presentation loss, not hidden-state leakage.
Unproven setup/tally sibling allegations remain outside this fix.

Final `PostGameScreen.kt:63–82` uses a name-over-role Column; **both Text nodes
own full available width**. Existing vertical scrolling remains. No forced
font scaling, truncated names, height cap, new navigation architecture,
changed reveal condition or game-state mutation was introduced. Normal
short-name pairs also stack; this is the small visible trade-off.

The failed intermediate FlowRow must **not** be described as proven actual
clipping. In cycle02 all six tests passed nonzero-bounds, displayed and
horizontal-viewport checks, then failed `hasVisualOverflow`; their XML lacks
dimensions. Independent exact-version source research shows that CMP
1.10.3 String Text semantics can recreate a MultiParagraph at the parent's
maximum width while retaining the original intrinsic layout size. Semantic
overflow can consequently disagree with actual fitted-Paragraph drawing.
This is recorded with official Maven Central source-jar URLs/hashes in
`MC03-overflow-research*.json` and excerpts. It is counter-evidence, **not a
new confirmed app defect** or a proven exact explanation of all six failures.

The final tests preserve every assertion and add dimensions/constraints on
failure. They render the real composable in EN/LTR and AR/RTL at 320×640dp,
font scale 1 and 2, and 640×320dp landscape at scale 2. Every role must be
scroll-reachable/displayed, have positive bounds, fit horizontally and have
no semantic text overflow. The maximum-length name must also not overflow.
All six now pass; these deterministic Desktop results are not mobile-native
rendering, real text-setting, TalkBack or VoiceOver evidence.

## Failed-cycle investigation and test quality

- Cycle01 stopped at a test-only pinned-API compile mismatch (`DpRect`
  width/height). No tests ran. Coordinate comparisons retained the exact
  positive-size assertion.
- Cycle02: 267 Mafia tests, seven failures. Six were the intermediate layout
  assertions discussed above. The seventh occurred **before reconnect** in
  the LAN fixture's first private-projection assertion, not a production
  reconnect privacy failure.
- Independent source tracing found the fixture bypassed the start handshake:
  null accepted start ID makes `hasAuthoritativeSnapshot` start true but
  launches no initial snapshot request (`AuthoritativeSessionCoordinator.kt:
  1353–1359,1433–1438`). Mafia seeds a safe public-only placeholder. The state
  flow is direct, **not** lazily subscribed.
- The final fixture first asserts every placeholder has no private slice,
  attaches collectors, then emits synthetic HostRestored. Real peer request,
  host serialization, peer validation and install run before privacy
  assertions. It does not inject another player's private state into a peer
  or modify production networking. All previous first-valid-submission,
  own-only, seed/map/log redaction and rejoin assertions remain.
- This authenticated in-memory room fixture bypasses lobby admission/start,
  credential recovery, radio discovery and OS lifecycle. Its same-seat
  disconnect/reconnect is not evidence for physical P2pKit or cold rejoin.

## Executed evidence and cleanup

Root-owned cycle03 began `2026-09-05T19:41:01.693890Z`, ended
`19:42:03.940194Z`, exit 1 (Whodunit compile error). Strict dependency
verification, JDK21, checked-in wrapper, no parallel builds and one worker
were used. Full command and before/after source manifests are preserved in
`evidence/games-session-desktop-03/receipt.json`.

| Mafia regression group | Executed tests | Fail/error/skip |
| --- | ---: | --- |
| Doctor rules | 8 | 0/0/0 |
| Doctor canonical recovery/history | 15 | 0/0/0 |
| Doctor in-memory host/peer path | 2 | 0/0/0 |
| Doctor UI target predicate | 2 | 0/0/0 |
| Doctor EN/AR setup control | 2 | 0/0/0 |
| Final-role layout | 6 | 0/0/0 |
| Existing Mafia suite | 232 | 0/0/0 |

All 15 changed/new Mafia file hashes match the author's frozen manifest and
both cycle03 source manifests. The only source change after that run at
comparison time was the separately authorized Whodunit **test** type
annotation correction. No Mafia source changed under the test process.

Stop exit 0 at `19:42:04.204530Z`; precise generated-output cleanup completed
`19:42:04.581043Z`. Receipt records no remaining owned workers/outputs or
cleanup errors. This validator also checked every recorded removed output
path was absent before the next build. Root/build-logic and relevant module
outputs were included; no global cache/source/user files were removed.

## Limits / remaining gates

The accompanying coverage ledger distinguishes complete reads from caller
subranges; it is not a claim that this reviewer reread the entire application
or every old Mafia test in this remediation pass. Existing tests executing
successfully is different from complete review of every assertion. Combined
Whodunit regression execution, static analysis and applicable Android/Apple
builds remain root-owned gates. Physical LAN, native accessibility/rendering,
real lifecycle, signing and Store work remain unverified here. No source
outside this bounded change set is independently approved by this report.
