# Mafia remediation — author review checkpoint

Reviewer/author: `/root/mafia_cont`. Baseline: `main`,
`3625d0663ba6eb51338cbd5f9dc45f859ec18846` (full source hashes in
`baseline.json`; changed-file hashes in `source-review-01.json`).

**Status: implementation authored, source self-review complete; automated
execution and independent fix approval are pending root's build lane.** No
compiler, Gradle, Xcode, device, simulator, server, or native process was run
by this agent. No commit, branch, integration, Store, credential, or audit
artifact operation was performed.

## M-C01 / MF-C1 — canonical Doctor history

The original confirmed defect was an authenticated **local snapshot semantic
validation gap**, not an authentication bypass or a known normal producer.
Changing only the Doctor's `previousDoctorProtect` to null/a different valid
seat could disagree with retained canonical resolution history and alter the
next night's OFF-setting restriction after resume.

`MafiaSnapshotRecovery.hasValidResolutionHistory` now requires exact equality
between the Doctor's private previous protection and the newest retained
night's effective protection in nonterminal phases. This applies with repeat
ON and OFF. Null/skipped protection is meaningful. The latest record remains
available after the 128-entry history cap. Dead Doctors retain the effective
choice from their death night or preceding night until the next resolution
sets both canonical fields to null. Post-game remains the intentional
exception: the reducer clears private transient history while preserving the
host audit suffix. Existing terminal-private-clearing checks still reject a
non-null terminal previous target.

No schema/version change, old-record normalization, history erasure, new
protocol field, or peer-history requirement was introduced. The full history
and role seed/map remain host-only. Existing settings-aware historical repeat
validation remains unchanged; legal repeat-ON histories still pass.

## Existing Doctor setting — verified contract and focused coverage

The existing default-OFF `doctorCanProtectSamePlayerConsecutively` already
controls submission and resolution without a two-night cap. The reducer was
not changed. Both local/LAN target filters now call one small **Mafia UI-only**
predicate, preserving their living/active-seat filtering and independent
self-protection rule. This makes the shared behavior directly testable; no
game-specific logic moved to shared session/networking code.

English already said “on consecutive nights.” Arabic now uses
“في ليالٍ متتالية” rather than “في ليلتين متتاليتين.” Both refer to consecutive
nights, without an exactly-two limit. The setup comment now describes the
actual atomic `ConfigureAndStart` action used by both production routers.

| Requirement | Added deterministic coverage |
| --- | --- |
| Default OFF; ON has no two-night cap | `MafiaDoctorRulesTest`: all supported counts 5–16; four consecutive saves of one living target |
| OFF repeat rejection and alternation | First/second/first protection trace; rejection leaves submission unconsumed |
| Skips/effective history | Skip consumes that night, resets effective history to null, permits earlier target following night |
| Self-protection independent; first-valid submission | Both booleans' Cartesian product; repeated, replacement and null resubmission rejected |
| Living target/Doctor identity | Dead target and non-Doctor submissions rejected; dead Doctor cannot submit |
| Atomic setup/locked settings/determinism | Draft and action-codec round-trip; role map unchanged by repeat flag; post-start setting actions no-op |
| Snapshot/recovery | Strict encode/decode/load rejection of null/wrong private history; legal states round-trip with both settings |
| Phases and corner cases | Setup, role assignment, first night, pending night, Mafia coordination revote, dawn, discussion, day vote/revote, outcome, next night, no Doctor, dead Doctor/previous target, early end/normal win |
| Capped history | 130 legal nights retain 128 entries, valid suffix loads, latest mismatch still rejects |
| Local resume | Real `loadMafiaResumedSession` → `PassAndPlaySessionController` retains selected rule and enforces it |
| LAN authority/rejoin | Production host/peer bridges on bounded authenticated in-memory rooms: host-only setup, no speculative peer mutation, repeat acceptance/rejection, same-seat rejoin before/after submission, first-submission retention |
| Peer privacy | Public/own-only snapshots have no host seed/role map/logs; no other private slice; peer validator accepts history-redacted projections |
| Setup UI/localization | Actual EN/AR screen toggle defaults OFF and commits ON/OFF through callback without changing unrelated fields |

The LAN fixture deliberately does **not** exercise P2pKit, radio discovery,
credential storage/admission, OS lifecycle, or physical device rejoin. It uses
the existing test-only no-start-handshake seam; existing protocol/start tests
remain required. No physical evidence is implied.

## M-C03 — final-role visibility

`PostGameScreen` changes only each final name/role pair from `Row` to `FlowRow`.
Names retain full text and accessibility scaling; the role reflows onto the
next line when the pair cannot fit. Existing side-by-side space-between
layout is retained when it does fit. Direction-aware end padding separates
same-line labels. No name-policy restriction, forced font-scale reduction,
fixed-height clipping, new layout engine, or reveal-policy change occurs.

`PostGameScreenLayoutTest` renders the actual production composable with a
legal maximum-length name in EN/LTR and AR/RTL at 320dp, normal/2x text, and
640×320dp landscape/2x. It checks every final role has positive width, remains
within viewport horizontally, is scroll-reachable/displayed, and has no text
layout overflow. The long name must not overflow either. Dependencies are
Desktop-test-only and reuse the already-pinned UI-test/desktop artifacts.
Physical TalkBack/VoiceOver, native rendering and Store review remain external
gates; host UI tests cannot certify them.

## Root-lane verification requested

1. `./gradlew :game-modes:mafia:desktopTest --dependency-verification=strict`
   (new 35 tests plus the complete existing Mafia suite, not a selector-only claim).
2. Relevant Detekt/static analysis and Android/iOS compilation as scheduled by
   root, plus combined Whodunit/Mafia/session/transport checks.
3. Preserve evidence; immediately stop Gradle, precisely clean task-created
   build outputs, verify owned process shutdown. Root owns all executions.
4. Separate-agent full diff/original-finding review before any completion claim.

`git diff --check` passed and XML parsed with 269 matching EN/AR keys; these
are source checks only, not test/build success. No pre-existing Mafia test was
deleted, weakened, ignored, or rewritten to satisfy the new invariant.

## Follow-up 03 — failed run preserved; layout and fixture corrected

The earlier FlowRow proposal above is historical, **not the final layout and
not a passing result**. Root's `games-session-desktop-02` executed 267 Mafia
tests: 260 passed, six layout cases and the initial LAN private-projection
assertion failed. The receipt records stop exit 0, no remaining task-owned
outputs/workers, and no cleanup errors. Full failure XML remains preserved.

The layout failures reached `hasVisualOverflow` after displayed, nonempty and
horizontal-viewport assertions passed. Those XMLs do not establish the exact
overflow dimension. The follow-up uses a simple full-width name-over-role
Column, with both Text nodes owning the available width. It removes the
failed FlowRow proposal rather than shrinking or truncating content. All
visibility assertions remain, with paragraph, constraints, bounds and
width/height-overflow diagnostics added for any further failure.

The LAN failure occurred **before reconnect**, because the no-start-handshake
fixture never requested the initial own-private snapshot. A null accepted
start id bypasses initial recovery; its authoritative flag is already true
and therefore did not prove installation. Source inspection and independent
review by `/root/whodunit_cont` agree. The test now asserts initial placeholder
privacy, attaches collectors, emits a synthetic HostRestored event and runs
the real SnapshotRequest → host serializer → peer validator/install path.
Every original own-only/host-secret assertion is retained. This does not
change shipping networking or claim a real start/rejoin credential test.

Hashes and detailed source reasoning are recorded in
`source-review-03-failure-followup.json`. **Rerun and independent approval are
still pending; this checkpoint must not be described as completed.**

Exact-version source counter-evidence subsequently located by the independent
reviewer is recorded in `MC03-test-semantics-followup.json`: CMP's String Text
semantics recreates its paragraph at the parent's maximum width but reports
the original intrinsic text size. Thus `hasVisualOverflow` alone can be a
false positive for an intrinsic-width Text. The six FlowRow failures must not
be reported as proven actual clips. The original confirmed Row defect is
unaffected: its independent audit repro measured an actual **zero-width role**.
The current full-width Column resolves that sibling-width starvation and
avoids the intrinsic-width measurement ambiguity without removing assertions.

## Follow-up 04 — Mafia suite passed and independent fix review approved

Root cycle `games-session-desktop-03` executed **267 Mafia tests across 31
suites, zero failures/errors/skips**: all 35 new tests and 232 existing tests.
This includes all six final-role layout cases and both in-memory LAN cases.
The retained XML was reopened and counted; all 15 source hashes still match
the frozen manifest. Root's receipt records source stability, stop exit 0,
no remaining owned workers/outputs and no cleanup errors.

`/root/whodunit_cont` independently approved MF-C1/M-C01 and final Column
M-C03 at these exact tested hashes. See
`../whodunit/MF-C1-M-C03-independent-review.md` and its evidence ledger.
`source-review-04-mafia-suite-pass.json` binds the suite XML, author freeze,
independent approval and cleanup receipt.

**The overall multi-module cycle still failed** at Whodunit test compilation
after Mafia completed; it is not a combined repository PASS. Static analysis,
applicable Android/Apple builds and real-device/release gates remain root-owned
or external. No integration, commit, issue closure or Store operation occurred.
Mafia source remains frozen pending the remaining combined checks.
