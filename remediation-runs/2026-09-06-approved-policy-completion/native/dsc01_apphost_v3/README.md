# DS-C01 v3 — Copy-only application evidence

**Status: authored, source-bound by root, NOT EXECUTED or independently approved.**
This supplements, and never overwrites, frozen v1/v2 evidence. The earlier v2
Settings/restart test passed on its separately recorded source; that is not a
result for this harness or the later source.

## Finite runtime scenarios

One ordered XCTest, one newly created task-owned iPhone 17 Pro / iOS 26.5
simulator, unsigned Debug only:

1. Actual App Settings: English/Arabic/System, four real process boots, original
   persistent ownership before App initialization, previous-preference fixture,
   exact localized labels, original root UIKit direction, and **direct
   `LocalLayoutDirection.current` inside the actual Settings composition**.
2. Actual local Whodunit UI journey: choose pass-and-play, bundled story, Classic,
   six synthetic player names, then stop at public intro. Capture the actual
   `PartyAwareSession` controller, canonical `StateFlow`, and current immutable
   committed state object. Compare reference and value identity across AR, EN,
   System plus three actual Home/background/activate cycles.
3. Actual local Mafia UI journey: pass-and-play, five synthetic names, existing
   default setup/start, then stop at the closed role-assignment handoff. The same
   actual-reference/direction/lifecycle checks run in a fresh process. The test
   never opens a private role, submits a night action, or replaces a reducer.
4. Open actual iOS Settings via public `UIApplication.openSettingsURLString`.
   If its exact Language/English controls are available, choose English there,
   return to actual App Settings, choose Arabic then System, and verify the
   OS-managed value survives both System and restart. If a control is not
   observed, preserve bounded exact-label diagnostics and report **BLOCKED**.
   Such absence is not proof of non-applicability or of an app defect: it may
   need simulator language setup or an independently reviewed selector update.

There is **no in-game Settings route**. Changes while either local game remains
mounted use visibly labeled, copy-only **synthetic invocations of the actual
production Koin `SettingsStore`**. They do not write backing defaults. Normal
game entry, name validation, configuration and reducer start remain actual UI.
Direct observation runs inside the real session-owning composables; retaining
one root controller or recreating an equal fake state does not pass.

## Observation and scope limits

- One private canonical checkpoint stays in process memory. Evidence exports
  only referential/value-equality booleans, public phase IDs, bounded counts,
  public synthetic preferences and directions. No identifiers, hashes of state,
  seeds, roles, assignments, credentials or snapshot payloads are exported.
- Captures cannot rebase; disposal or replacement fails. Completion releases
  the checkpoint and the invocation coroutine scope. Failure finalization stops
  the exact owned simulator/app/workers. Each game has a fresh process.
- This is not a complete-game, persistence/resume, navigation-gesture, memory-
  leak, physical LAN, retained multiplayer-host, real-device, signing or Store
  readiness test. The supplemental in-memory retained-host scenario is NOT RUN
  in this bounded iteration; it must not be inferred from local continuity.
- Copied name fields gain only public seat-index test tags. Their production
  callbacks, sanitizer, focus/IME policy, state and layout are not replaced.

## Source binding and execution — root only

`bind_source.py FROZEN_SOURCE_MANIFEST_SHA256 FROZEN_DIFF_SHA256` creates a new
`source-bindings.json` only after the root freezes the later reviewed tree. It
refuses an existing binding or a mismatched dirty/untracked source. It does not
use the superseded v2 source identity and does not silently refresh anything.

Every applicable bound tracked **and untracked** build input is copied into a
fresh owned directory. No production source is edited; no source-set override,
dependency graph replacement or verification-metadata change is introduced.
Only exact reviewed Kotlin and Swift copy transformations are allowed. The
runner records the original/copied hashes and complete copy-only diff before
Xcode. Original lifecycle forwarding and original App initialization remain.
After task workers stop, it records the same inputs' hashes and inventory again,
excluding generated build/hidden directories. Any modified, missing, symlinked,
or newly introduced applicable input fails source attestation before owned-copy
cleanup; source equality in the original repository alone cannot pass this gate.

After separate control review, root first runs the pure contract suites with
`/usr/bin/python3 -B -m unittest test_secondary_fifo test_harness_contract
test_copy_observation_contract`, retaining exit/report evidence. These are
synthetic control tests, never iOS runtime proof. Then root executes
`run_dsc01_apphost_cycle.py dsc01-apphost-NN APPROVED_CONTROL_SHA256` in the one
shared build lane. The complete bound control manifest must match approval.

XCTest has a 720-second default / 900-second maximum allowance; the whole Xcode
command is bounded at 2,100 seconds. No settings action or game command is
automatically retried. Observation polling and public setup scrolling are
bounded. A build failure, selector failure and application failure remain
separate evidence classifications.

Exit 0 requires all applicable runtime gates, source/control equality and cleanup
to pass. Exit 2 is **PARTIALLY_VERIFIED**: required Settings/local tests passed,
but actual OS per-app language selection is blocked. Exit 1 is failed/incomplete
evidence. Unexecuted/blocked gates cannot become PASS from compilation.

## Ownership and cleanup

V2 PID/start/ancestry controls and `SecondaryFifoLedger` are retained unchanged.
The source copy, every generated module/build-logic output, DerivedData, temporary
Gradle home and fresh simulator belong to this cycle. Its isolated Gradle daemon
registry is stopped immediately after the embedded task and after Xcode, even
on failure. Cleanup attests worker termination, removes only owned external FIFOs
and empty owned parents, unlinks reviewed cache symlinks, and removes the owned
copy/DerivedData/temp tree. Global dependency caches and user data are preserved.
Original-repository `build/` directories are **not** owned by a copy-only run and
are never deleted; unexpected originals are preserved and make verification fail.

## Authoritative references

Accessed 2026-09-06, compact excerpts and fetch hashes in `research/`:

- Apple `UIApplication.openSettingsURLString`, introduced iOS 8, documented to
  launch Settings at the app's custom settings when present. Applies to iOS16+
  deployment and the owned iOS26.5 runtime; no private URL is used.
- Apple WWDC19 **Creating Great Localized Experiences with Xcode 11** (session
  403): per-app language settings introduced for multilingual users; a language
  change can relaunch the application. Thus OS selection/restart is tested
  separately from in-process local-game retention. This does not establish the
  exact availability/selector behavior of iOS26.5; actual runtime evidence must.
- CMP UI/Foundation **1.10.3** source-based accessibility merge proof is retained
  in the previous run's `dsc01-accessibility-source-01/02` and independent factory
  dossier. Settings uses the proven exact merged tab labels. New public-setup
  prefixes and field tags are closed, bounded observation seams, not fallbacks
  that invoke app callbacks or disclose arbitrary accessible content.

No build, test, simulator, app or background worker has been launched by this
harness author. Root owns execution and cleanup receipts.
