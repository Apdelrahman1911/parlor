# Parlor — implementation and verification continuation

## 1. Read this first

This is the **2026-09-08 handoff checkpoint**, not a final READY verdict. The
user requested that all current project work and continuation evidence be
committed/pushed so another agent can finish without repeating or skipping work.
This transfer does not perform another native run or final CI dispatch.

The 16 approved repairs are already in history. The remaining work is four
verification workstreams, with one precise iOS interaction-fixture correction
still to implement. A successful harness test is not application-runtime proof.
Do not turn an incomplete/failed run into PASS by relabeling it.

### Repository and identity

- Repository: `https://github.com/Apdelrahman1911/parlor.git`.
- Continue branch: `fix/local-readiness-2026-09-07`, **not `main`**.
- Original local root: `/Users/abdelrahman/Projects/parlor`.
- Before this handoff: commit `8b9e3b0cdfdab82e7cff3ad8135d0eae9a0b8ca3`,
  tree `c1e92b5e00ab64a293c4d7f9c9b5e2b85887be88`.
- The delivery adds commits after that baseline. Obtain the exact delivered
  tip with `git rev-parse HEAD` and compare it to `git ls-remote origin
  refs/heads/fix/local-readiness-2026-09-07`; do not pretend the old SHA is current.
- The handoff manifest records the transferred bytes and explicit exclusions.
  Old source/control bindings remain immutable historical evidence. **Create
  fresh bindings after the handoff commits before any new native execution.**

For a new clone, select the branch explicitly. In an existing checkout, inspect
status first; do not switch/reset/stash over user work merely to match a report.

```sh
git clone --branch fix/local-readiness-2026-09-07 \
  https://github.com/Apdelrahman1911/parlor.git
cd parlor
git status --short
git rev-parse HEAD HEAD^{tree}
```

### Authority and safety

Read the existing `AGENTS.md` (preserved byte-for-byte), then
`docs/PRODUCTION_ARCHITECTURE.md`, `docs/RELEASE_GATES.md`,
`docs/HOW_TO_ADD_A_GAME.md`, and accepted ADRs. Source and actual task wiring win.
`docs/PARLOR_PROJECT_HANDOFF.md`, `project-code-audit/`, early plans and reports
are useful historical context, **not current execution proof**.

The user authorized scoped repairs, tests, independent review, commits and normal
pushes to the current branch. This does **not** authorize merging, force-pushing,
changing GitHub issues, choosing replacement application identities, private
Store credentials/signing, publishing/promoting, or enabling disabled workflows.
Physical-device testing and Store operations remain excluded. Preserve the
local stash `codex-preserve-pre-ui-user-changes-2026-09-03`; it is not part of this
branch delivery and must not be applied or pushed implicitly.

Use one coordinated local Gradle/Xcode lane. Review agents may read in parallel;
an independent agent must approve each correction and its actual evidence.
Never let another agent edit frozen control/source files during a native run.

## 2. Navigation map and architecture constraints

Parlor is a Kotlin/Compose Multiplatform party-game container. Android/iOS ship;
Desktop is development/testing, not a promised Store platform. There is no cloud
backend, account system, public-internet matchmaking, spectator mode, raw-IP
join, host migration, or supported timed Mafia rounds.

| Area | Actual ownership to preserve |
|---|---|
| `composeApp` | Composition root, game catalog/bindings, existing Navigation 3 shell, DI, platform entries, platform storage. Do not start another navigation migration. |
| `shared/core`, `shared/engine` | Pure generic IDs/contracts/reducers/projections, no game/UI/transport dependencies. |
| `shared/session` | Local and multiplayer session orchestration; UI uses `SessionController`. Host alone executes multiplayer reducers. |
| `shared/networking` | Transport-independent protocol; compatibility is **exact 4.2**. |
| `shared/transport-p2p` | Only P2pKit importer; same-LAN transport, lifecycle/admission integration. |
| `shared/storage` | Authenticated snapshots and distinct rejoin credential storage; platform adapters in app. |
| `shared/content` | Validated offline bundled content. Production remote source is disabled/offline. |
| `shared/design-system` | Reusable components, theme, EN/AR locale ownership and direction. |
| `game-modes/whodunit`, `game-modes/mafia` | Each game's reducer, rules, projections, codecs, snapshots, local/LAN UI and resources. |
| `shared/engine-testing`, `shared/networking-testing` | Non-shipping fixtures; never add them to a shipping source set. |
| `iosApp` | Swift/UIKit/Compose integration and Xcode wrapper; no CocoaPods. |

Pass-and-play keeps canonical state locally and privately hands the device
between seats. LAN keeps canonical state/reducers on the host; peers submit
validated commands without speculative canonical mutation. Peers receive only
public data plus their own private slice, never host secrets, seeds, role maps,
another player's data, or resolved host-only histories. Preserve ordering,
deduplication, seat ownership, cancellation, bounded queues and recovery invariants.

## 3. Completed application repair scope — do not redo it

Primary original repair commit:
`85ded00435b4f8327ce32d3b7f5062c4438ae174`. This was one original 16-repair commit,
not sixteen separate commits. Subsequent focused corrections are explicitly mapped.

Use the complete, independently reconciled map and executed test descriptors:

```text
remediation-runs/2026-09-07-local-readiness/reviews/
  original16-timer-commit-evidence-map-01.json
  original16-timer-regression-descriptors-01.json
```

They contain per-ID source paths/ranges/hashes, follow-up commits, authors,
independent validators, compatibility and raw regression references. Reopen
affected current source before modifying it; these records are not substitutes
for source review or a new final-SHA run.

| ID | Existing correction |
|---|---|
| ST-C1 | Backup-exclude legacy iOS saves before access/migration; preserve last recoverable copy on failure. Later recognized-current-read failure retention is included. |
| SN-C1 | Accepted irreversible start commit survives best-effort ACK timeout; genuine caller cancellation remains preserved. |
| SN-C2 | Cancellation-safe opening, lifecycle registration, ownership transfer and cleanup, including sibling host/peer paths. |
| WD-C1 | Do not automatically resubmit recorded peer readiness on presentation remount. |
| IOS-B1 | Xcode phase propagates directory/build failures; only normalizes successfully embedded output; stale-output controls retained. |
| ROOT-T3 | Correct Jupiter signatures/discovery and executable protection; physical tests remain intentionally disabled. |
| MF-C1 | Settings-aware Doctor previous-protection/resolved-history recovery consistency. `M-C01` is an alias, not another defect. |
| WD-C3 | Reject impossible active final-two Elimination recovery without rejecting valid terminal saves. |
| M-C03 | Keep final Mafia roles visible with long names/compact layouts and accessible large text. |
| DS-C01 | Ownership-aware iOS language override/System selection; do not erase OS-owned preferences. |
| DS-C02 | Distinct toast lifetime identity with bounded queue/coalescing/concurrency/expiry. |
| DS-C03 | Cover-appropriate recovery actions on black privacy surfaces, preserving ordinary light-theme buttons. |
| WD-C2 | Coherent testing-story chronology, explicit content versions/digests and strict save/LAN matching. |
| RL-C1 | Portable strict Linux/macOS artifact and dependency-report size checks. |
| RL-C2 | Approved self-signed Android upload certificates supported without weakening integrity/fingerprint checks. |
| RL-C3 | Play promotion validates/mutates/commits one edit snapshot, preserving rollout/digest/no-blind-retry guards. |

### Settled product decisions

- **Doctor:** reuse the existing serialized
  `doctorCanProtectSamePlayerConsecutively`, default OFF. ON permits the same
  eligible living target for three or more nights (actual rules coverage uses
  four consecutive nights across counts 5–16); OFF rejects consecutive targets,
  permits alternation and retains existing effective-protection/skip semantics.
  Self-protection is independent. One submission per night; no resurrection,
  duplicate flag, protocol change or host-history disclosure. Local/UI/LAN and
  recovery tests are mapped above; physical LAN is not claimed.
- **Whodunit Leave Confirmation:** its time does **not** count toward Discussion.
  This was already supported by presentation-owned ticker behavior and is now
  explicit and regression-tested for organizer/host, without granting peer clock
  authority or clearing other pauses. See `docs/WHODUNIT_DISCUSSION_TIMING.md` and
  commit `b4f0ece464aa44819044dd78d2f2fd137d88c91d` plus mapped test follow-ups.
  Six local and four host timer tests actually ran. WD-C4 is no longer an open
  product-policy question; do not count it as a seventeenth original defect.
- **Language:** no migration support for ambiguous unmarked preferences from old
  unpublished internal builds. Clean installs/current-build selection/System/
  restart are supported. Use fresh owned profiles or explicit development reset,
  never silent deletion of legitimate OS preferences.
- **Content:** old internal test saves need not migrate. Reject incompatible
  content clearly and offer explicit discard/restart; do not silently rewrite or
  delete saves. Testing-story editorial corrections are authorized, not final
  production editorial approval. Current versions: Last Dinner/Layla/Khan/
  Iskenderia `1.0.2`, Jasmine `1.0.3`, Saidi/Zamalek `1.0.1`; catalog, JSON and
  current source remain authoritative. Bundled stories currently require six
  players, even where the engine supports a wider range.

## 4. Exact runtime checkpoint

Throughout the rest of this document:

```text
C = remediation-runs/2026-09-07-local-readiness
R = C/reviews
E = C/evidence
```

These are explanatory aliases; expand them or assign shell variables explicitly.

### Latest completed native run: `ios-readiness-16` — FAIL, cleaned

Run finished `2026-09-08T07:38:55Z`. Do **not** wait on/resume old tool session
63942: it exited. Evidence is in `E/ios-readiness-16/`.

- Xcode exit 65: **five XCTest methods, four UIKit PASS, one main method FAIL**.
- Functional storage: **15 operation receipts, 14 PASS and one FAIL**; ten boots
  observed, nine completed boot markers. Boots 11–13 and the later retained-host
  scenarios did not execute. A partial matrix is not complete.
- First failed receipt: `parlor-l08-storage-functional-10-continue.json`.
  Stage: `continue-home-counters`. The probe checks
  `resumeTaps == 1 && discardTaps == 0` at
  `C/l08-storage-functional-companion-02/L08StorageFunctionalProbe.kt.in:250`.
- Complete reachable proof narrows this to the expected Home resume counter
  remaining **zero**. Legacy-load itself passed. Controller attachment and the
  Mafia reducer continuation were **not reached**. Do not invent a Mafia recovery
  defect from this failure.
- Public geometry showed target motion after the bounded list swipe; the XCTest
  had neither stable-geometry gating nor a callback acknowledgement around its
  single tap. Motion is a useful lead, **not a proven sole cause**. The final
  recorded target was below the overlay; overlay interception is not established.
- Foundation's exact executable/main-image selection now binds successfully:
  `COLLECTION_VALIDATED_NOT_L08_PASS`. Native16's paired native observations are
  valid within their stated scope. They are not protection-enforcement evidence.
- **All 24 retained strict Complete comparisons still fail.** Source/control/
  copied-input identities were unchanged; immediate/final Gradle stops returned
  zero; owned simulator, secondary FIFOs, temporary copy and DerivedData removed;
  no owned outputs/workers remain in the receipts.

Independent final reconciliation:
`R/ios-readiness-16-independent-postrun-review-01.json`
SHA-256 `f54fa06059d5711f640232ef486d067bb51ce8b6dd5d1788c6b6a9e569948d0f`.
It includes exact source ranges, counters, public geometry/timestamps, completed
subtraces, binding proof, missing scenarios and cleanup.

**The gesture correction has NOT been started/adopted/tested.** There is no hidden
ready-to-apply gesture draft. See workstream A below.

### Latest normal-source launch run: `ios-readiness-15`

`E/ios-readiness-15/receipt.json`: runtime PASS, package/notices PASS, provenance
FAIL, cleanup PASS, unchanged source/control bytes.

Eight genuine normal-source XCTest cold launches and 48 observations passed.
The separate ninth public-tool image observation failed because `vmmap` returned
255 obtaining DYLD info. Its diagnostic says “Assuming … minimal corpse”; this
is **not proof of app crash, OOM, memory pressure, permissions or entitlements**.
Do not guess the historical four-failed-launch root cause from that string.

Research and exact public API/SDK applicability:
`R/normal-native15-vmmap-libproc-review-01/review.md`.
Apple XNU reference tag `xnu-12377.61.12`, commit
`4d495c6e23c53686cf65f45067f79024cf5dcee8`; not a claim of installed-kernel byte
identity. Do not blindly retry flags, run privileged tools or change entitlements.

## 5. Newly transferred provenance controls — source tested, native pending

Canonical runner: `C/native/normal-ios-launch-proposal-01/`.
The exact six-file libproc proposal was adopted from
`R/libproc-image-observer-01/draft/` after independent review. Earlier sample-star/
failed-command controls are also present. Frozen readme statements about pending
execution describe their creation time; the newer receipts below supersede those
statements without rewriting their original bytes.

- Default vmmap path is preserved. New **explicit** `--image-observer=libproc`
  mode has no automatic fallback and injects no application code.
- Host-only C helper compiles against the actual SDK and calls
  `proc_pidinfo(PROC_PIDREGIONPATHINFO)` for **every** sample-selected image.
- Enforce exact start (API may advance over holes), contained zero-offset RX
  non-writable region, full native struct return, canonical kernel vnode path,
  device/inode/file-size identity and before/after process/artifact/helper checks.
- This corroborates selected executable regions/backing files. It is **not** a
  whole-map enumeration, independent in-memory Mach-O UUID/page hash, or separate
  provenance observation for each of the eight earlier UI repetitions.

### Actual completed checks

| Evidence | Actual result |
|---|---|
| `E/normal-libproc-controls-01/` | **187/187** unique declared/discovered/executed unittest IDs, no failures/errors/skips; source/control stable; stop/cleanup PASS. Includes original 177 plus 10 new tests. |
| `E/normal-libproc-validation-01/` | Isolated AST mutation removing only final-query postflight correctly causes the one selected regression to fail with `not raised`; method restored. This is an **expected mutation witness**, not an app PASS. |
| Same validation cycle | Actual C compilation with `-Wall -Wextra -Werror` succeeds. Five malformed-request smokes are rejected before any native process-region query. Helper deleted; stop/cleanup PASS. |
| Actual Parlor libproc query | **NOT RUN.** No final Debug provenance claim yet. |

Source approval: `R/libproc-image-observer-01/independent-review-03.json`
SHA-256 `a13eb3f7ca9d77af2bc5ff28a5283b1d3abaa71eca0a225065da29438fd84ece`.
Actual control/compiler/cleanup reconciliation:
`R/normal-libproc-control-reconciliation-02.json`
SHA-256 `64472e0b52d8d7819ca0f5d640b606cf7cee3817f263c53657438fcc0c76997e`.
This supersedes the earlier review's transposed native15/native16 sentence;
the original receipt and its correction both remain available.
Freeze03 SHA-256
`da6246f2076f40cc1ae5f3a128064730383b1b6dc322007ea1057a6beb83cc33`.
The earlier review found and corrected missing postflight helper integrity; its
first regression mutated too early and was corrected to mutate only on the last
query. Keep those rejected versions as evidence, not alternate implementations.

## 6. Remaining local work — execute in this order

**Freeze order:** after implementing and testing the fixture correction below,
commit the reviewed source/control changes, regenerate the mechanical inventory,
and make its inventory-only commit **before final native A/B execution**. Use that
same frozen tree for native evidence, the dependency chain and final CI. If an
exploratory native failure requires another correction, preserve that run and
freeze/bind again. Later source or inventory changes require explicit independent
requalification of affected evidence; never relabel an old run as a new-SHA run.

### A. Finish L08 public interaction/recovery and protection investigation

1. Reopen native16's independent report and exact failing probe/UI seam/callers.
2. Make the smallest **copy-only fixture** correction in
   `C/l08-storage-functional-companion-02/L08StorageFunctionalUITests.swift.in`:
   bounded stable/visible geometry for the uniquely identified card and the
   existing `l08-ui-observe` zero-before/one-after callback acknowledgement
   around **exactly one real `target.tap()`**. Confirm API behavior from official
   XCTest docs/current helpers. Do not retry activation, call navigation/reducers
   directly, weaken counters, change snapshots, or assume geometry alone proves
   delivery. Retain bounded closed diagnostics if activation still fails.
3. Add focused fixture guards/mutations; separate agent reviews the full delta,
   guards, test validity and compatibility. Run focused controls under the lane.
4. The existing composition driver embeds exact hash pins of the companion and
   literal transforms. If approved companion bytes change, update/review **only
   affected pins/freezes**; do not bypass a mismatch. Old freezes remain intact.
5. Create a new source binding/control manifest and obtain independent execution
   approval. Run a fresh owned simulator through the existing composition runner.
   Do not reuse source15/control521a… as current after handoff commits.
6. Finish all 13 functional-storage boots and three host fixtures, actual
   Settings/System/OS-owned preference/lifecycle/session paths, plus image and
   cleanup checks. A complete functional companion can still return
   `PARTIALLY_VERIFIED`/exit2 because strict protection remains unsatisfied.
7. Strict L08: correct Complete flags and atomic encrypted writes are already
   present. Foundation and app-container comparison also fail metadata readback.
   Do not hide the warning, change entitlements blindly, swallow Keychain errors,
   change the protection policy, delete saves, or turn this into an external-only
   gate. Investigate a **new discriminating hypothesis** if one exists; document
   any genuine local limit and preserve the mismatch instead of endlessly rerunning
   identical probes. Functional success alone does not prove Complete enforcement.

Useful controls already executed: `E/l08-main-selector-controls-01/` **51/51**;
`E/l08-continuation-stage-controls-02/` **28/28**. The first stage-control run
was **27 PASS/1 FAIL** due to an overbroad test restriction; its receipt remains.
Native14 is also still FAIL; do not resurrect its old whole-run status.

### B. Complete normal Debug provenance and current launch evidence

1. Reuse the exact reviewed libproc controls and successful 187-test/compile/
   mutation evidence unless relevant bytes change. Do not repeat research just
   because the worker changed.
2. After changes settle, bind the new source and runner controls independently.
3. Run the canonical normal-source runner with explicit libproc mode. It owns
   eight normal-source XCTest repetitions, a separate ninth image observation,
   exact built/installed artifacts/signatures/notices and full cleanup.
4. Require real native struct sizes/results; synthetic `1328` fixture values are
   not an ABI measurement. A C compile/invalid-request smoke is not a native query.
5. Independently reconcile actual run and limitations. If it fails, diagnose the
   specific failing guard; never silently select a looser observer. Preserve all
   failed receipts. If successful, report current reproducible launch evidence and
   explicitly state the historical crash cause remains unknown absent original
   diagnostic evidence.

### C. Fresh dependency export → render → candidate consumer

After the final source freeze, follow **packet B** in
`R/final-current-sha-verification-preparation-02.md`.

- Export with `scripts/verification/resolved_dependencies.init.gradle` and
  `writeResolvedDependencyInventory`; strict verification stays enabled.
- Render with production `scripts/verification/dependency_inventory.py`.
- Require four complete graph manifests, exact artifacts/POMs/variants/digests
  and no unresolved metadata. Inspect differences; do not hardcode old counts.
- New independently reviewed final-source binding, then execute
  `R/dependency-candidate-consumer-03/candidate_consumer.py` via the approved thin
  adapter `R/run_candidate_input_01.py` in the standard lane.
- Reuse existing owned ephemeral schema checkers (`jsonschema 4.25.1`,
  `referencing 0.36.2`, hash-pinned optional parsers); no global installation or
  silently waived schema format. Consumer's **16 synthetic controls already
  passed**, but the final candidate execution has **not** run.
- Require all three coupled receipts: consumer scoped PASS, prerequisites scoped
  PASS/exit0/no error/ephemeral cleanup, and outer lane PASS/source equality/
  stop0/cleanup. An inner success file alone is insufficient.

Approved adapter hash:
`2a306819fb41b1883139fb42355d493f02e1d93a687f54d06f43ffbb758afa60`;
consumer hash:
`eb6e2baaac22739b4652bc63c6b57a24374ee57fb81d4711b981b872e8ac1c7f`.
Independent review: `R/candidate-input-adapter-independent-01.json`.

### D. Final current-SHA combined qualification, independent review and push

1. Finish/review/adopt any remaining source/control corrections. Commit source
   changes first. Regenerate the mechanical review inventory **after** source
   commits, then commit only that inventory and run `--check` on the final SHA.
   This handoff refreshes its own inventory; future changes require a new refresh.
2. Push the exact final branch normally and verify remote SHA. Dispatch only
   `production-verification.yml`, never a disabled publishing workflow.
3. Collect **all five jobs** at the same SHA/attempt: Linux x64 + Android release,
   Linux arm64, Windows x64, macOS x64, qualified Apple. The workflow already
   covers aggregates/static analysis/lint/R8/unsigned packaging, `allTests`,
   managed Android runtime, native iOS runtime and qualified Release linkage/
   Swift wrapper. Inspect actual task/descriptors/skips/cache outcomes.
4. Keep five main/five cleanup artifacts, compact raw logs/test descriptors,
   source/run/artifact/digest/signature/notice bindings and cleanup records.
   Do not duplicate all local heavyweight builds when valid same-SHA CI supplies
   the exact gate. Cached test output is not a freshly executed runtime test.
5. Reconcile original16/timer regressions, dependency chain, native evidence,
   privacy/compatibility, qualified platform and release tooling. Independently
   review final implementation/evidence and report every residual limit honestly.
6. Commit/push completed follow-up work without leaving verified source changes
   behind. Preserve failed evidence, local user work and global caches.

Historical CI `34152138368`, attempt1, really passed all five jobs at
`89dbe8aeaf4e2c83f491521629982be09706cf67`; **not the new handoff/final SHA**.
Its qualified Xcode26.3, three Release links, Android R8 runtime and alternate-host
evidence are real. Do not call those platforms generally unavailable. Raw
framework hashes for nonpackaged device/x64 links were not retained, but the
source-defined linkage gate does not mandate an extra huge rebuild for them.

## 7. Commands, toolchain and cleanup

JDK21; checked-in Gradle8.13 wrapper; Kotlin2.4; AGP8.13.2; Lint9.1.1; R8 9.1.41;
P2pKit0.7.0-rc3 Maven Central only, matching core/LAN versions. Use strict
verification; never regenerate verification metadata to silence a failure.
Version source remains `config/parlor-version.xcconfig`.

Original local environment: Xcode26.5/17F42/iOS26.5 Simulator, `/usr/bin/python3`
3.9.6 (Homebrew Python XML was broken), approximately 11GiB free after cleanup.
Store-qualified Xcode26.3/17C529 is available through the existing CI job.
Runners deliberately reject unreviewed toolchain/environment substitutions.

```sh
C=remediation-runs/2026-09-07-local-readiness
R="$C/reviews"

# Read-only fresh source observation, then explicit new binding; no runtime approval.
/usr/bin/python3 -B scripts/verification/ios-readiness/bind_source.py --describe
/usr/bin/python3 -B scripts/verification/ios-readiness/bind_source.py \
  "$C/ios-readiness-source-NEW.json" "$SOURCE_MANIFEST_SHA" "$DIFF_SHA"

# Read/review the resulting control manifest before any new native attempt.
/usr/bin/python3 -B "$C/native/normal-ios-launch-proposal-01/run_normal_ios_launch.py" \
  --control-manifest "$BINDING"

# Only with new independently approved binding/hash, free lane and unused NN:
/usr/bin/python3 -B "$C/native/normal-ios-launch-proposal-01/run_normal_ios_launch.py" \
  ios-readiness-NN "$BINDING" "$APPROVED_CONTROL_SHA" \
  --simulator-signing=adhoc --image-observer=libproc

# L08 has its own exact composed control manifest and independent approval:
/usr/bin/python3 -B "$C/native/l08-app-foundation-composition-01/compose_runner.py" \
  --control-manifest "$BINDING"
/usr/bin/python3 -B "$C/native/l08-app-foundation-composition-01/compose_runner.py" \
  ios-readiness-NN "$BINDING" "$APPROVED_COMPOSED_SHA" --simulator-signing=adhoc

# Root-only ordinary focused lane; select an unused descriptive cycle name.
/usr/bin/python3 -B "$C/run_gradle_cycle.py" NEW-CYCLE \
  :game-modes:whodunit:desktopTest --tests '*RelevantTest*'

# After fresh frozen export/render and independent candidate-binding approval:
/usr/bin/python3 -B "$C/run_gradle_cycle.py" candidate-input-next \
  --command /usr/bin/python3 -B "$R/run_candidate_input_01.py" \
  "$CANDIDATE_BINDING" "$REVIEWED_BINDING_SHA"

# After reviewed source commits, then inventory-only commit and exact-SHA check:
/usr/bin/python3 -B scripts/generate_review_inventory.py
/usr/bin/python3 -B scripts/generate_review_inventory.py --check

# Final verification only, after the final exact commit is on the remote:
gh workflow run production-verification.yml --repo Apdelrahman1911/parlor \
  --ref fix/local-readiness-2026-09-07
```

Placeholders are not runnable values. Binding filenames must be lowercase;
replace `NEW` with a fresh lower-case suffix. Native cycle format is exactly
`ios-readiness-NN`; inspect existing directories before choosing it. Read all
runner contracts first. Native runners own their lock/finalizer; **do not wrap
them inside the ordinary lane** or relocate their external TMPDIR into the repo.
The ordinary lane hash is
`5c0e546c819c28762cbc6b355ec2717737fc53f96712af591a27add878579d7a`.

After **every** build/test/check cycle, including failure/interruption:

1. Preserve compact needed evidence/exit status and only artifacts needed next.
2. Immediately `./gradlew --stop` in the correct lane/environment.
3. Clean exact task-owned build/DerivedData/simulator/scratch outputs; use
   `./gradlew clean --no-daemon` only when safe. Stop again if cleanup starts Gradle.
4. Verify task-owned workers/output absence; record any failure explicitly.

Never kill unrelated processes, delete global caches, reset the working tree or
remove source/signing/configuration/verification metadata. Archived `build/`
segments **inside evidence paths contain required reports**, not live module
outputs. Do not delete those by recursively removing every directory named build.
The archived secondary-worker FIFO cleanup issue was repaired with inode/device/
UID/PID lifetime attestation; do not revert to broad `/tmp` cleanup.

## 8. Evidence reuse, exclusions and readiness verdict

Use `R/final-readiness-residual-reconciliation-02.json`
SHA-256 `b4be11e7263b2f183690896d93502e9f2f59033b5d9ff5828e455e7c23868e9c`.
It reopens current source, actual Keychain OSStatus observations, OS-created Arabic
preferences/System/restart and retained local game identities, qualified Apple/
alternate-host linkage/runtime, all 26 shipped notice resources and declarations.
No additional mandatory local build/probe was established beyond A–D in that
bounded reconciliation. This is not exhaustive-review completion or proof that
no other defect exists.

### Ledger checkpoints, not a completion percentage

- Original16: all mapped to committed corrections/independent review and executed
  scoped regressions; final same-SHA interaction qualification remains required.
- Local gate families L01–L13 overlap the four workstreams; they are **not 13
  independent build batches**. L07 normal provenance and L08 final functional/
  strict protection evidence are unfinished. L11 fresh candidate input chain and
  L01/final exact-source qualification remain outstanding. Historical passes in
  L02–L06/L09/L10/L12/L13 are reusable only within exact source/method scope.
- Current final verdict: **NOT READY / local verification unfinished**. Do not
  report a percentage without a defined, evidence-backed denominator.
- Mechanical inventory is **not** line-by-line review evidence. Copied sources,
  POMs, research and draft fixtures under audit/remediation roots are nonshipping
  evidence even when a path-based CSV heuristic resembles a production source set.
- Every historical receipt keeps its original status, source/hash/path references
  and limitations; later success does not rewrite earlier failure.

### Deferred physical-device and Store-operation categories

- Physical Android/iOS same-LAN admission, identity/rejoin/disconnect/lifecycle,
  app-switcher/privacy and real screen-reader/gesture/visual behavior.
- Physical locked/unlocked Keychain/file-protection/keybag behavior, real backup/
  restore exclusion and power-loss durability. This does **not** waive the
  separately observable local strict metadata mismatch.
- Actual private signing, Store candidate/upload/promotion/submission/review and
  publication operations. None was performed or is authorized by this handoff.

### Separate owner decisions/declarations before production

- `com.parlor.app` has a known Store identity collision. Debug suffix separation
  and disabled publishing workflows are intentional safeguards. Do not choose
  replacement identities or enable publishing without explicit authority.
- Owner legal/license/content-rights certification, final production story
  editorial approval, real privacy URLs/questionnaires and Store declarations
  remain requirements. Static technical notice/configuration checks are not legal
  certification. These are not mislabeled as code fixes or physical-device tests.

### What this transfer includes and deliberately omits

Includes application history/current controls, all audit/remediation reports and
reproducer/draft source, design prototype, original handoff, references, failed
receipts and needed raw logs/XML/compact native/CI evidence. Execution claims
remain separately classified. Necessary ignored evidence is explicitly staged;
no broad ignore-rule removal or global cache publication is needed.

Excluded: secrets/private Store/signing material, global/developer caches,
machine-local configuration, OS metadata, synchronization lock files, unrelated
stash/work, and disposable generated APKs. The APK producer receipts/hashes and
reproducible tooling are included; these old-source synthetic artifacts are not
current release evidence. Local originals are preserved rather than reset or
silently deleted. See the transfer manifest for exact exclusions.
The APKs used a disposed synthetic signing key, so rebuilding them does not
reproduce identical signed bytes. See `R/synthetic-apk-delivery-review-01.json`.
Audit roots use `-text` Git attributes to preserve hash-bound CRLF/upstream/raw
evidence on checkout; this does not disable application checks or change source
set membership.

Read `handoffs/2026-09-08-agent-transfer/` for preservation, safety scan,
independent handoff review and cleanup. Git history identifies the final delivered
tip; no document can embed its own commit SHA without a self-reference problem.

## 9. First actions for the next agent

1. Verify branch/remote/HEAD/tree/status, read AGENTS and this handoff; check the
   transfer manifest. Do not apply the unrelated stash.
2. Read native16 postrun, libproc source/execution reviews, residual reconciliation
   and final-current-SHA preparation. Use current source hashes, not old labels.
3. Confirm the lane is free and toolchains/disk are suitable. There is no active
   inherited native session to resume.
4. Implement/review the bounded single-tap fixture correction (not yet started),
   test it, commit source/controls then inventory, and finish A and B at that
   frozen source with fresh bindings. Reuse unchanged successful controls.
5. Finish C and D on the same frozen source, independently
   review evidence, push and give an honest final report. Leave exact checkpoints
   rather than claiming an unexecuted gate passed.
