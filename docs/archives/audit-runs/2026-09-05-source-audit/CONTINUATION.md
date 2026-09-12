# Exact continuation checkpoint

## Authority and source identity

This is still **audit-only**. Do not implement the recommended fixes, mutate
GitHub, commit, merge, sign a Store candidate or enable publication without
separate authorization. The Android managed-test harness's disposable synthetic
signature is not Store signing or permission to use private credentials.

Start at `/Users/abdelrahman/Projects/parlor`, `main`, commit
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`. Recheck these values and the full
working tree before reusing evidence. Preserve the baseline untracked roots and
all audit material. Do not checkout/reset/stash/clean Git to simplify validation.

## Reading checkpoint

**No unread ranges remain in the applicable 628-text-file inventory.** All
139,888 verbatim lines and six binary dispositions are in
`coverage/FINAL_COVERAGE.jsonl`, with hashes and actual reviewers. This does not
close behavioral uncertainties. Protected/local/prior-audit exclusions are
explicit, not covertly included in a “fully reviewed” percentage.

There are 16 independently confirmed defects, nine documentation mismatches,
six test/evidence gaps, three rejected numbered candidates, and one blocked
numbered candidate (WD-C4). IOS-R1 is now an evidence gap, not a confirmed defect.
Use `canonical-register.json`; historical indexes have earlier
counts intentionally preserved. These are not GitHub states.

## Outstanding behavior and verification

All paths below are relative to the repository root. Ranges are one-based
reinspection anchors, **not claims that those source lines were left unread**.
Each relevant full file/hash is already in the coverage ledger.
The [exact source-anchor index](evidence/continuation-source-anchors.json) expands
the abbreviated paths below into absolute paths, complete ranges and hashes.

### 1. Android managed Release completed; cross-host evidence outstanding

- Exact chain: `scripts/android/run_release_managed_device_smoke.sh:1–58` →
  `build.gradle.kts:53–57` → `composeApp/build.gradle.kts:289–302` →
  `pixel2Api35ReleaseAndroidTest`.
- `evidence/android-managed-02/` records the actual successful execution: all
  three instrumented methods passed, none failed/skipped. Both independent
  reviewers reconciled the original XML/input hashes/cleanup. The checked-in
  harness supplies a disposable two-day PKCS12 test key; Store credentials are
  not required for this local gate. Do not overwrite or recharacterize these
  receipts when obtaining cross-host evidence.
- `evidence/android-managed-01/` failed at the audit ADB server's listener syntax
  before Gradle, emulator, test or key creation. Its owned crash report was
  removed after PID/time attestation. It is not an application-test failure.
- Three source-declared methods live in
  `composeApp/src/androidInstrumentedTest/java/com/parlor/app/ReleaseRuntimeSmokeTest.java:18–60`
  and `.../kotlin/com/parlor/app/MainActivityColdStartTest.kt:21–138`. They check
  non-debuggable Activity startup, a multicast lock, and first draw while
  settings I/O is blocked—not real LAN delivery or full gameplay.
- The cold-start fixture deletes/replaces settings files. **Only a fresh owned
  emulator/data profile is safe**, never a connected user's phone/profile.
- Local Darwin/ARM64 API35 revision9 is not the policy's Linux/x86_64 runner.
  Obtain the pinned CI matrix separately. Thirteen Intel iOS tasks cannot be
  counted as executed on this ARM host; Desktop-only game tests need an explicit
  cross-platform coverage decision, not an invented native pass.

### 2. IOS-R1: diagnostic completed; healthy signed recovery remains unverified

Owner: platform verification agent. **TEST/EVIDENCE GAP; no defect severity.**
The actual app-host diagnostic ran in `evidence/iosr1-apphost-02/`: exactly one
copied XCTest passed. Original Home visibly warned before the trigger. The real
Koin-bound diagnostic rerun then returned local success-empty, multiplayer
`SecureStorageUnavailable`, and the correct unavailable projection. A subsequent
equivalent Keychain read in that same app returned `-34018`; it is **not** the
original Home or production-rerun numerical status. The warning is intended
fail-closed handling, not a demonstrated code defect or a fix.

The failed apphost01 attempt ran zero tests: an audit-only empty
`EXPANDED_CODE_SIGN_IDENTITY` requested signing under pinned KGP 2.4.10. Retry02
omitted that entry. Both attempts and the earlier standalone-native probes remain
historical evidence, not explanations of old physical-iPhone launch failures.

Reopen `App.kt:120–143`, `shell/home/HomeRecoveryAvailability.kt:88–145`,
`shell/home/HomeScreen.kt:253–279`,
`shared/transport-p2p/.../ResumableCredentialStore.kt:146–151,318–340`,
`P2pKitRoomTransport.kt:625–655`,
`composeApp/src/iosMain/.../storage/IosSecureKeyValueBacking.kt:74–112,145–179`
and `IosSnapshotFileSystem.kt:74–123,150–166,219–228,361–370`.

Do not repeat the completed probe and claim it can recover a discarded historical
status. Exact original-invocation attribution needs a separately reviewed,
invocation-specific observer; it remains locally investigable, not proven to
require a Store setup. Healthy empty/synthetic-record recovery, write/read/delete,
restart and lock/unlock still require qualified platform/device verification
with separately authorized signing where applicable. No player values, full
queries, credentials or raw snapshots may enter evidence. Do not suppress the
warning, equate errors with absence, or add entitlements blindly. The final
`validations/IOS-R1-apphost02-session_cont.md` supplement controls the earlier
blocked classification. Only launcher/framework hashes survived cleanup; full
bundle and signed-artifact provenance was not established.

### 3. Navigation, UI and accessibility matrix

Owner: UI/platform verification. **Partly locally executable, partly physical;
not complete.** No claim of tooling impossibility replaces missing execution.

Anchors: `composeApp/src/commonMain/.../{App.kt:1–338,AppNavigation.kt:1–172,
AppNavigationHost.kt:1–141,AppBackPolicy.kt:1–23,AppNavigationTransitions.kt:1–67}`;
platform `AppNavigationTransitions.ios.kt:1–71`, `MainViewController.kt:1–56`,
`MainActivity.kt:1–69`, Desktop `Main.kt:1–71`; Swift `ContentView.swift:1–62`;
`shared/design-system/src/iosMain/.../LocalAppLocale.ios.kt:1–91`.

Execute both games' setup → private handoff/readiness → complete game → post-game
→ replay, local and host/peer. Cover compact/landscape/tablet/large text,
keyboard/scroll/sticky controls/safe areas, all dialogs/errors/empty states,
theme/language switching without session recreation, rapid taps, stale callbacks,
loading cancellation, root/tab preservation, and explicit Save/Leave.

Verify Android system/predictive Back; iOS LTR/RTL full/short/fast/reversed/cancelled
swipes and runtime-language changes; Desktop Escape/key repeat/window closure.
Protected previous entries must not flash behind blocked Back or private handoffs.
Perform TalkBack/VoiceOver focus/order/labels/live announcements/reduced-motion
and actual frame/recomposition/memory measurements. No such measured performance
baseline is supplied by a source review or a single screenshot.

### 4. WD-C4: decide modal-clock policy before classifying

Owner: game/product owner. `game-modes/whodunit/.../ui/flow/multiplayer/
WhodunitHostSessionFlow.kt:249–258` and `WhodunitPhaseRouter.kt:827–938` show the
ticker cancellation while Leave confirmation is open without canonical pause.
Specify whether elapsed modal time should count, then add a deterministic
cancel/dismiss/background test. Peers currently show waiting UI, not a numeric
countdown. Do not invent timer rules; `validations/WD-C4-root.md` remains blocked.

### 5. Physical LAN, identity, privacy and rejoin

Owner: device QA. Use controlled same-LAN rooms and physical Android/iOS devices.
Full bundled Whodunit needs **six seats/devices**; Mafia supports **5–16**. Pairwise
discovery/advertisement cannot substitute for a complete game.

Trace current protocol4.2/start handshake, transport-attested identity, admission
approval/capacity/name reservations, readiness barriers, slow/flooding peers,
command sequences/deduplication/outcome queries, delayed ACK, revision/generation
binding, seat loss, elimination-audience disconnection, rejoin deadlines,
credential staging/rotation, process recreation, explicit Leave, host loss,
background/inactive/foreground and repeated transitions. Preserve the exact
private-slice boundary and never expose host seeds/maps as test diagnostics.

Anchors include `shared/session/.../multidevice/SessionStartHandshake.kt:210–287,356–369`,
`shared/transport-p2p/.../P2pKitRoomTransport.kt:259–337`,
`AppLifecycleRoomCoordinator.kt:44–54`, the respective host/peer flows and current
protocol/credential classes in the full coverage ledger. Ignored physical tests
also have stale fixtures (SN-T1); do not un-ignore them and call mocks physical proof.

### 6. Storage and app-switcher behavior

Owner: platform/privacy verification. Native ST-C1 proves backup-exclusion flags
on synthetic retained files, not actual iCloud transfer. Device lock/background,
backup/restore, app-switcher timing, permission/storage failure, partial writes,
and post-open NSFileHandle read/close faults still need evidence.

Reopen `IosSnapshotFileSystem.kt:74–208,347–382`, iOS keychain/secure backing,
`AndroidSnapshotFileSystem.kt:1–298`, Android backup XML/manifest and Swift privacy
cover. The rejected directory-open witness does not prove all native exception
paths safe. Android legacy retention is not the demonstrated iOS backup leak:
manifest and both backup-rule formats exclude that legacy filesDir path.

### 7. Release/owner-dependent gates

Owner: release/content/application owner. Required: qualified Xcode26.3/17C529,
actual owner identities/certificates/profiles and signed artifact verification,
live protection/environment governance, lawful content/font/icon rights,
truthful Store privacy/accessibility declarations, controlled Store API rehearsal
and Store review evidence. Current `com.parlor.app` approval/collision block and
disabled candidate/promotion workflows must remain intact.

RL-C1/RL-C2/RL-C3 are separately confirmed **code defects**, not external gates to
waive. Their remediation requires authorization. Do not choose new identifiers,
inspect private signing inputs, enable publishing or upload/promote during audit.

## Continuation execution rules

1. Reconcile new input hashes before reusing old results; source changes invalidate
   affected coverage/tests. A current commit alone is insufficient for dirty inputs.
2. Use one root-owned build lane, JDK21 and the checked-in strict wrapper.
3. Run only isolated reproducers against synthetic data; keep changes under this
   audit workspace. A new candidate requires a different source-reopening validator.
4. Preserve compact raw results; immediately stop Gradle, clean precise task-owned
   outputs/DerivedData/device state, and verify owned worker termination. Keep
   global caches, other tasks' processes, source and evidence untouched.
   The archived app-host runner missed a secondary Apple `ibtoold` FIFO root
   outside TMPDIR. Before reusing it, extend/review the finalizer to record and
   clean only secondary paths attested to its own workers, including macOS
   `/var` and `/private/var` aliases. The known cycle02 residue is already removed
   and independently rechecked; do not broadly sweep global temporary folders.
5. Update the gate, research, cleanup and final preservation receipts. Do not
   report missing tests as PASS or a successful witness as a corrected defect.
6. If further execution is deferred, state exactly what was not done. This
   checkpoint is unfinished verification, not a silently reduced audit scope.
