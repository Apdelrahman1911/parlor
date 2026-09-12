# Final cross-game remediation review — MF-C1, WD-C1, WD-C3

Reviewer: `/root/factory_review`, independent of both fix authors. This is an
additional bounded regression review, **not a new exhaustive repository audit**.
No production/test/configuration edits, builds, credentials or Git mutations
were performed by this reviewer.

## Decision and reviewed identity

**APPROVED within the reviewed source and executed JVM/Compose scope for all
three fixes. No new scoped blocker established.** Physical-device and external
release requirements are not certified by this conclusion.

- Repository: `/Users/abdelrahman/Projects/parlor`, branch `main`.
- HEAD: `3625d0663ba6eb51338cbd5f9dc45f859ec18846`.
- Git tree: `db7f3d2afe73a13628296daee2cce71165eebc8d`.
- The reviewed artifact is the **dirty remediation checkout**, not HEAD alone.
- Frozen source manifest:
  `e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7`.
- Tracked diff SHA-256:
  `60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd`.

The accompanying `final-cross-game-review-01.json` records 66 file entries:
54 complete text reads and 12 explicitly bounded caller/resource subranges,
16,767 reviewed lines. It contains absolute paths, one-based ranges, hashes,
reviewer identity and raw test references. Every current reviewed file matched
both before/after source manifests of `combined-production-04`. Complete reads
span this review's uninterrupted task, including the same reviewer's turns
before context continuation. Unread caller ranges are not marked reviewed.

## MF-C1 — Doctor behavior and canonical recovery

The repeat setting was **already implemented**. Source inspection confirms:

- `doctorCanProtectSamePlayerConsecutively` remains default OFF, including
  every preset. The setup draft carries that same setting; local and LAN-host
  setup both submit one atomic `ConfigureAndStart`. Setup-only reducer guards
  prevent mid-game changes; peers cannot author settings.
- ON bypasses only the previous-target restriction. There is no streak
  counter or two-night cap. Living/active target, Doctor ownership, separate
  self-protection and first-valid-submission guards remain authoritative.
- OFF compares the chosen target with the last resolved **effective**
  protection. A resolved explicit skip sets that history to null, making the
  earlier target legal next night if still eligible. A skip still consumes
  the current night's submission.
- The two UI routers now share the same small Mafia-owned predicate after
  filtering living active seats. No game-specific rule moved into session
  or transport infrastructure. English already described consecutive nights;
  Arabic now says `ليالٍ متتالية`, not exactly two nights.

The actual fix at
`game-modes/mafia/src/commonMain/kotlin/com/parlor/games/mafia/snapshot/MafiaSnapshotRecovery.kt:354–365`
binds the assigned Doctor's private previous target to the newest canonical
night record outside PostGame. This matches the unchanged reducer:
`MafiaReducer.kt:445–475` writes both values from the same
`resolution.effectiveDoctorTarget`.

Important counter-cases were traced, not inferred from the new condition:

1. Observable settings, exact role counts and private-role agreement run
   before `singleOrNull`; at most one Doctor is possible. No Doctor, pre-night
   and skipped-night null histories remain valid.
2. A Doctor dying at night retains that night's effective target. A Doctor
   eliminated by day retains the latest night target. The next night without
   an active Doctor resolves both histories to null. A previous target's later
   death does not erase its historical selection.
3. `hasExpectedRetainedDays` requires the full bounded suffix. The newest
   record survives the 128-record cap; an unavailable predecessor before the
   retained suffix is not fabricated. Existing settings-aware historical
   validation rejects OFF repeated targets but accepts arbitrary ON streaks.
4. PostGame intentionally clears private action fields while preserving host
   history. The new comparison correctly exempts that phase; the existing
   terminal guard still rejects a forged non-null private previous target.
5. Strict encode/decode and `loadMafiaResumedSession` reach this check before
   installing a restored local controller. No malformed snapshot is repaired
   into a plausible alternative state.

Privacy remains structural: host serialization takes one canonical state and
sends a redacted public projection plus only the recipient's private slice.
The full role map, seed, night log and vote log remain absent from peer bytes.
Peer validation deliberately does not require host history. Rejoin republishes
the same host-owned setting/private restriction; local snapshots are not a host
migration mechanism.

The original defect's prerequisite remains accurately narrow: a malformed but
successfully authenticated canonical snapshot or synthetic trusted producer,
not an established ordinary-game corruption path or authentication bypass.
No snapshot, game or protocol version migration was introduced.

## WD-C1 — Readiness remount and receipt ordering

The production peer tree at `WhodunitGameFlow.kt:1340–1385` removes the router
while a command is pending/resolved or the host is paused. Previously every
return to Intro/Briefing sent another readiness command. The host correctly
made repeated readiness a no-op and returned InvalidAction; it was the UI's
fresh command generation, not deficient protocol deduplication.

The correction at `WhodunitPhaseRouter.kt:341–361` keys the effect by controller,
phase, seat and assignment generation, then reads the retained **atomic
own-player StateFlow directly**. It checks that phase/generation are still
current and sends only when that seat is not already in the corresponding
host-authoritative readiness set. It does not cache optimistic success.

This direct read matters: the passive bridge installs the validated public
and own-private data before the coordinator records its revision and permits
the command cover to disappear (`AuthoritativeSessionCoordinator.kt:
1589–1642`). A Compose collector can still render the previous projection
briefly; consulting only the rendered parameter would allow another command.
The new guard sees the installed authoritative readiness instead.

The whole surrounding path was checked: retained peer owner -> multiplayer
PartyAwareSession passthrough -> passive Shadow controller -> sequenced command
coordinator -> transport-attested actor -> game action authority -> sole host
reducer -> result/snapshot installation -> UI acknowledgement. Exact revision,
sequence, command-ID, actor and protocol checks are unchanged. Pending actions
are queried rather than blindly replayed.

Replay increments assignment generation and clears readiness; a fresh ceremony
can acknowledge normally. Stale phase/generation rendering does not acknowledge
before catching up. A definitively rejected command with no recorded readiness
may be retried on remount without pretending it succeeded. Replacement owners
re-check current state. The sibling host auto-ACK is a local unchanged receipt,
not this peer-cover/error-loop path; pass-and-play remains locally orchestrated.

## WD-C3 — Final-two reachability

The new `WhodunitStateValidator.kt:626–633` guard requires more than two
survivors in active Elimination Round/TiedRevote states. Earlier roster,
elimination-membership and connection-shape guards make the arithmetic valid:
active gameplay cannot subtract arbitrary unknown/eliminated/dropped seats.
Both canonical and peer validation call the same phase-shape check.

This matches the existing reducer, not an invented rule:
`WhodunitReducer.kt:788–821` enters Reveal immediately when a non-killer
elimination leaves two survivors. Killing the killer takes its separate player
victory branch. Classic is unaffected. FinalVote is already Classic-only.
Reveal, PostGame, early ending and fresh replay are not rejected by this active
phase guard.

The old test deliberately manufactured an impossible active final-two state.
Its defensive all-abstain reducer fallback remains covered, but the test now
correctly rejects that state at recovery instead of using the fallback as
justification to reopen a completed game. The new tests also exercise a
last-round killer elimination leaving two innocent survivors, valid terminal
save/load and postgame/replay. Current and bare-legacy malformed snapshots are
rejected rather than normalized; serialization versions remain unchanged.

## Independently inspected execution evidence

Root-owned `combined-production-04` executed `productionCheck` using the checked-in
wrapper, JDK21, strict dependency verification, one worker, no parallel execution
and **no build cache**. It completed at `2026-09-05T22:30:19.979017Z`, exit 0.
Raw logs show the game/session/network test tasks executed, not FROM-CACHE or
UP-TO-DATE. Type-aware static analysis completed in the same successful gate.
This reviewer parsed preserved XML independently instead of trusting a test count.

| Relevant runtime group | Executed | Fail / error / skip |
| --- | ---: | --- |
| Mafia Doctor rules | 8 | 0 / 0 / 0 |
| Mafia Doctor recovery/history | 15 | 0 / 0 / 0 |
| Mafia in-memory host/peer path | 2 | 0 / 0 / 0 |
| Mafia Doctor target predicate | 2 | 0 / 0 / 0 |
| Mafia English/Arabic setup control | 2 | 0 / 0 / 0 |
| Whodunit readiness remount | 12 | 0 / 0 / 0 |
| Whodunit final-two recovery | 9 | 0 / 0 / 0 |
| Whodunit existing rules-invariant suite | 9 | 0 / 0 / 0 |

All Mafia tests: **267 passed**. All Whodunit tests: **309 passed**. Session:
145 passed; networking: 34 passed. Transport: 249 passed, **3 physical-LAN
checks skipped**, not 252 executed successfully. These XML totals do not imply
that this bounded review reread every existing unrelated test assertion.

The ON rules test protects the same target for **four consecutive nights at
every supported 5–16-player count**. Recovery covers legitimate three-night ON
histories, OFF alternating targets, forged null/wrong values across active
phases, skips, self-protection, no/dead Doctor, terminal cleanup and 130-night
bounded history. The resumed local controller tests both persisted settings.

Test limitations are explicit. The Mafia LAN fixture bypasses admission/start
and credential plumbing; it first checks safe public placeholders, then issues
real reconciliation to obtain own-private snapshots. Its same-seat synthetic
reconnect is not cold credential-backed or physical P2pKit proof. The Whodunit
readiness tests use the real Compose router/passive controller and controlled
subtree removal with explicit host reductions, not a physical room. Current
production coordinator ordering was separately source-traced above.

The final two-property Mafia test typing change and Whodunit real-suspend-loader
reference were also separately reviewed in this folder's `*-detekt-review-01/`
receipts; no assertion or fake suspension was substituted. The exact final test
hashes now match the fresh combined run.

## Cleanup, preservation and limits

The completed combined cycle's retained raw `stop.log` says no Gradle daemons
are running; stop exit 0 at `22:30:20.307635Z`. Its receipt records precise
cleanup of 15 owned root/module/build-logic output directories, no retained or
remaining outputs, no owned workers and no cleanup errors, completed at
`22:30:22.205283Z`. Before/after source manifests are identical. No global cache
or source deletion was used.

This reviewer started no build/app/server worker and created no generated build
outputs; only these two compact evidence files were added. The root's separate
Apple lane was not stopped or cleaned by this reviewer. Its result is **not
inferred here** from the desktop verification.

Physical Android/iOS LAN, app-switcher/lifecycle behavior, cold credential rejoin,
real large-text accessibility and Store/signing/legal evidence remain external
or separately owned gates. WD-C4's modal-timer product policy remains unresolved
and was not silently chosen. No claim is made about WD-C2 authorial decisions,
other issue closure, an entirely clean repository, or production readiness.
