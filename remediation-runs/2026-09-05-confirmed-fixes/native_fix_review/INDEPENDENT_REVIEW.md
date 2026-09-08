# ST-C1 / DS-C01 — independent remediation review

Reviewer: `/root/native_fix_review` (author of neither fix). Reviewed on
2026-09-06 Africa/Cairo. Base: `main`, commit
`3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree
`db7f3d2afe73a13628296daee2cce71165eebc8d`, with the current remediation edits.
This approves only the scoped changes below, not the whole checkout or release.

## Decisions

| Finding | Independent decision |
| --- | --- |
| ST-C1 | **APPROVED — FIXED AND VERIFIED**, scoped to the failed-legacy-migration backup-exclusion defect. |
| DS-C01 | **APPROVED AS PARTIAL REMEDIATION — PARTIALLY VERIFIED.** New owned overrides reconcile correctly; older unmarked overrides remain unresolved. |

No additional blocking source regression was established in this review.
Full file/range hashes, reasoning, test names and cleanup references are in
`independent-review.json`. Its six directly affected source/test files have
manifest SHA-256 `f0037021f229a8fb0e32b618e4423dbc83322bbe6e77029874e8a07e4d2db352`.
All six match the before/after source manifests of `native-storage-language-02`.

## ST-C1: root cause and failure paths

I reopened the complete 500-line `IosSnapshotFileSystem.kt`, complete new
222-line `IosLegacySnapshotBackupTest.kt`, all 195 lines of the existing
`IosStorageSafetyTest.kt`, and the original candidate, independent dossier,
115-line audit reproducer and raw failing XML. I also traced production DI,
the common snapshot store, Home inventory, explicit Retry/Discard, Keychain
read/create handling and write-before-delete sequencing.

The new boundary excludes `Documents/snapshots` before initial inventory and
again before each direct read/current-record selection. Neither original
failure path—unrecognized legacy framing or damaged current framing with a
retained old copy—can now bypass that initial protection. The setter is
followed by a fresh `NSURL` Boolean-`NSNumber` readback. Failure of the initial
exclusion is outside per-record corruption swallowing and reaches the
non-sensitive storage error surface, rather than being reported as an empty
store. Ordinary failed records remain discoverable and unchanged.

There is no new legacy fallback, corrupt-state repair, peer serialization,
key/format change, or automatic data destruction. The pre-existing
recognized-magic/current-record precedence cleanup is unchanged and its two
existing regression tests still execute. Explicit discard remains possible
when the legacy-exclusion callback fails; repeated reads reapply protection.

The original audit executed two defect witnesses plus one expected failed
safety assertion. That evidence proves the original defect; it is **not** the
repair evidence. The new native run executes eight Foundation-bound tests
covering both manifestations, direct read, oversize, exclusion failure,
discard, retry, nonexistent paths and recreated directories, plus all six
existing storage safety tests: **14 passed, zero failures/errors/skips**.

### Storage limitations

- No real iCloud/device backup or restore was performed. Fresh Apple guidance
  explicitly says `isExcludedFromBackup` is guidance, not a guarantee that an
  item can never appear in a backup or on a restored device. Directory-level
  exclusion is the documented mechanism for a related group of files.
- These focused tests do not execute a healthy Keychain-backed migration in a
  signed app host, every mixed inventory/interrupted-write scenario, or
  real-device lock behavior. The unchanged cryptographic and ordering paths
  were reviewed, not misreported as newly runtime-tested.
- Some tests reuse the suite-level legacy directory. Thus not every assertion
  starts from its own independently false backup flag. Coverage relies on the
  cold-inventory case, explicit injected call/failure checks and complete
  reachable source proof together; each case is not represented as an isolated
  end-to-end backup experiment.
- IOS-R1 and the separate native-exception hypothesis remain outside this
  repair's verified scope.

## DS-C01: ownership and lifecycle

I reopened all 61 lines of `IosLanguageOverrideOwner.kt`, the complete
83-line iOS locale adapter, all 142 lines of the native tests and both original
finding dossiers. I traced Settings actions through the serialized persistent
store into `App`, the common locale boundary, the native controller/SwiftUI
wrapper and pinned CMP 1.10.3 resource/locale source.

Prior values now come from the application **persistent domain**, not the
resolved global/registration search result. Release validates marker version,
installed-string type, previous-array type and **whole-array equality** before
restoring or removing the language key. Externally changed arrays survive;
identical unmarked values are not claimed. Invalid previous metadata cannot be
reinstalled. A newly constructed owner reconciles an earlier marked lifetime
before either System or another explicit language is applied.

The locale adapter retains one remembered owner and the existing unkeyed
content boundary. It adds no session/navigation recreation, replacement native
controller or competing navigation mechanism. The existing conditional UIKit
direction refresh is unchanged.

All **11 actual Kotlin/Native owner tests passed** against UUID-named Foundation
suites; the full design-system native suite reports **26 passed**. The earlier
native01 run failed two fixture expectations: Foundation global preferences
precede registered defaults. Native02 captures the real pre-operation fallback
and still checks app-domain absence and exact restoration. Neither production
locale file changed between those runs; this corrects the fixture rather than
weakening its ownership assertions.

### Language limitations — do not close the whole issue

- Old unmarked Parlor `AppleLanguages` and legitimate OS-managed per-app values
  have no distinguishing provenance. The patch deliberately preserves them;
  the original upgrade manifestation can remain. This is **not a universally
  repaired migration**.
- Value comparison cannot detect another actor writing the exact same array.
- `NSUserDefaults` accepts mutations synchronously but persists asynchronously;
  two preference writes are not a durable cross-process transaction.
- Constructing a new owner in the tests is not killing/relaunching an iOS app.
  These tests do not prove real process persistence, displayed Compose strings,
  live UIKit/gesture direction or session continuity in an actual app. The
  source-level retained-composition conclusion is narrower than UI evidence.

## Executed evidence and cleanup

The root-owned run selected design-system `iosSimulatorArm64Test` and the two
storage classes from composeApp's `iosSimulatorArm64Test`, with strict
dependency verification, one worker and the owned simulator binding.
Raw XML: **40 test cases total, zero failures/errors/skips**. The actual task
log shows execution of both native test tasks, not just compilation.
Execution source manifest:
`fbea29c6e4f53108c632b49ff4e8b9da644f6caeb9e48876b70948d6a90e430f`.

`native-storage-language-02/receipt.json` records task exit 0, Gradle-stop exit 0
at `2026-09-05T20:44:16.946167Z`, precise cleanup complete at
`20:44:21.103803Z`, no remaining generated outputs and no owned workers.
`simulator/receipt.json` records successful shutdown/deletion of the fresh
owned iOS 26.5 simulator, absence of its UUID/processes and preservation of all
22 pre-existing simulator identities. These are independently inspected
receipts; this reviewer did not manipulate devices.

The installed Xcode 26.5/17F42 and iOS 26.5 simulator are not the qualified
Xcode 26.3/17C529 release environment, a physical device, or Store evidence.

## Research and reviewer safety

Fresh official Apple source and SHA-256 receipts are stored in
`authoritative-research*.json` with compact extracts. In particular:

- [Backup resource key](https://developer.apple.com/documentation/foundation/urlresourcekey/isexcludedfrombackupkey): Boolean value and reset/reapply behavior.
- [Optimizing iCloud backup](https://developer.apple.com/documentation/foundation/optimizing-your-app-s-data-for-icloud-backup): directory exclusion and explicit non-guarantee caveat.
- [Preference domains](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/UserDefaults/AboutPreferenceDomains/AboutPreferenceDomains.html): persistent application-domain writes and global-before-registration ordering.

The initial backup-QA URL redirected to a JavaScript shell; that shell was not
used as proof. The current official documentation JSON supplied the substantive
directory guidance. Exact-version CMP source was independently reopened from
the original Maven-source evidence; no newer-version behavior was substituted.

I changed only evidence under this review directory. I started no Gradle/Xcode
build, app/test worker, simulator or server; performed no device/preferences or
private-data operations; and made no Git-history changes. Root-owned cleanup
was verified without stopping another task's build lane.
