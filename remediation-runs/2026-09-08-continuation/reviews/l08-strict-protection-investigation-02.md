# L08 strict protection: bounded continuation investigation

Author: `/root/l08_fixture`, 2026-09-08 UTC. Independent reviewer requested:
`/root/fixture_review`. This is research/evidence reconciliation, not a runtime
execution or an approval of the separately authored portability correction.

**Verdict:** strict L08 remains unsatisfied. Its observed metadata failures are
not relabelled physical-only. There is an actionable hosted verification path,
but neither the newest failed CI runs nor public upstream source establish
successful application-runtime protection.

Paths below use `C = remediation-runs/2026-09-07-local-readiness` and
`N = remediation-runs/2026-09-08-continuation`.

## Actual retained observations, reopened

* All 15 `C/evidence/ios-readiness-16/parlor-l08-storage-functional-*.json`
  records were read, including the failed boot-10 continuation. Their 24
  actual Complete comparisons are **24 FAIL / 0 PASS**: 12 directory and
  12 regular-file readings. Every reading returned a dictionary, omitted the
  protection key, and reported no NSError. This is not a getter exception or
  an observed Complete value with a formatting discrepancy.
* Native16 has 14 successful functional operation records and one failed
  `continue-home-counters` record. It did not complete the required 13-boot,
  20-operation matrix or the following three-host matrix. The final two of
  the required 26 protection comparisons belong to boot-12 `dual-preserved`
  and were not observed. Successful functional records do not override any
  failed protection comparison.
* `C/evidence/ios-readiness-16/parlor-l08-app-foundation.json`, SHA-256
  `e656f929fd9c96a58ecf7430bd3f74b5d33be2cf3c8dcb4f15d446aeb6b42d35`,
  has `OBSERVATIONS_COMPLETE`, runtime `[26,5,0]`, and Foundation UUID
  `2cc9fce0-08f9-3a0d-8a2f-6db229462de5`. It is observation collection,
  independently bound as `COLLECTION_VALIDATED_NOT_L08_PASS`, not L08 PASS.
  Its eight native operations returned success without NSError/exception.
  All eight FileManager readings omitted the key; all five regular-file URL
  readings returned `until-first-authentication`; all eight volume readings
  returned `supported`. Backup exclusion visibly changed `included` to
  `excluded`. Fixture cleanup was `REMOVED`, with hardware verification and
  requirements waiver both false.

The direct native sequence included fresh directory creation with Complete,
before/after backup exclusion, atomic Complete write, non-atomic Complete
write, explicit directory setter, and default file write followed by explicit
file setter. Consequently these observations do not establish Kotlin-only
bridging, app-container-only behavior, backup exclusion, atomic writing alone,
or omission of an explicit setter as the cause. Changing those application
paths speculatively is not justified.

The current application still requests `NSFileProtectionComplete` when
creating the snapshot directory and `NSDataWritingAtomic |
NSDataWritingFileProtectionComplete` when writing files:
`composeApp/src/iosMain/kotlin/com/parlor/app/storage/IosSnapshotFileSystem.kt`
lines 76–108 and 362–374. Intent is not runtime enforcement proof.
`C/l08-storage-functional-companion-02/L08StorageFunctionalProbe.kt.in`
lines 500–562 performs both actual reads and exact equality, retaining absence
as `missing`/`FAIL`. The native observer uses fresh NSURL queries and confines
URL protection observations to regular files (`L08AppFoundation.m.in`
lines 98–119, 175–181 and 221–273).

## Distinct hosted failures and latent profile defect

The inspected source snapshot is branch `fix/local-readiness-2026-09-07`,
HEAD `25b1c5551aee7630ac22d4b34668dd4b06c71b17`, tree
`629a5d2b699cddb62d61849de3bd185fd4c01378`. Other agents' concurrent
corrections are outside this report's source-approval scope.

The raw receipts and Xcode logs from Actions run `34216788568`, native17/18,
were reopened, not inferred solely from another agent's summary:

* Both receipts end `FAIL` with `TimeoutExpired` for
  `ps -axo pid=,ppid=,pgid=,lstart=,command=` after 15 seconds.
* Native17's Xcode log reaches build preparation/tool discovery and ends
  `BUILD INTERRUPTED`; Foundation collection is `NOT_RUN`.
* Native18's retained compressed Xcode log expands to zero bytes and normal
  provenance is `NOT_RUN`. No application protection observation is present.
* Both final cleanup receipts say `PASS`, with owned device absent, temporary
  directory removed, no remaining owned workers/unknown holders, and source,
  controls and copied inputs unchanged. Cleanup success is not runtime success.

These **actual ownership-observation failures precede app runtime**. They
cannot be attributed to the following separate, newly identified source
defect, nor do they add another observed protection mismatch on iOS26.2:

`scripts/verification/ios-readiness/toolchain_profiles.py` deliberately selects
Xcode26.3/17C529 with simulator SDK/runtime26.2 for the qualified hosted
profile. However, at the inspected snapshot:

1. `C/native/l08-app-foundation-draft-01/L08AppFoundation.m.in` lines 491–492
   requires exactly runtime26.5.0, before container setup and all observations.
2. `app_foundation_receipts.py` lines 116–118 also requires `[26,5,0]` for a
   complete receipt.
3. `app_foundation_copy.py:render_native` supplies no selected runtime, and
   the composition's preservation/parsing/collection-binding paths use those
   default controls.

Thus an otherwise successful hosted26.2 launch would fail the native runtime
guard, and a complete26.2 receipt would fail the current parser. This is a
latent portability control defect, not newly executed native failure evidence.
`/root/native_ci` owns its minimal correction; `/root/portability_review`
owns independent correction review. The exact selected profile must bind
native generation, preservation, parsing and final collection validation.
Do not accept arbitrary runtime triples, silently fall back, or widen the
guard without binding the actual selected profile and fresh controls.

A scoped survey found no additional active runtime26.5 literal in the
Foundation copy/Swift/bridge path. The pinned historical
`C/reviews/l08-native-foundation-control-03/run_control.py` also contains
26.5/17F42/23F77 CLI controls, but the app observer imports only its bounded
JSON/row validators (`decode_json`, `ROW_KINDS`, `validate_error`,
`validate_query`), not its full runtime validator or CLI main. Do not modify
that historical control for this correction. Exact CoreSimulator device-set
ownership, platform7, arm64, `com.parlor.app.debug` and Parlor image identities
remain deliberate guards, not portability waivers.

## New targeted public-source comparison

New retained research is in `N/reviews/l08-strict-protection-investigation-02/`.
Its `source-identities.json` records URLs, retrieval times, response sizes and
hashes; the full downloaded source and official documentation JSON are retained.

Official Xcode26.3 release notes explicitly identify **Swift6.2.3 and the
iOS26.2 SDK**. The read primary content of those notes and the iOS/iPadOS26.2
notes supplies no File Protection/Simulator guarantee or fix. This absence
does not prove support or non-support.

To avoid repeating the earlier broad research, one targeted source-family
comparison was made against the previously identified public repository:

* `swiftlang/swift-foundation`, tag `swift-6.2.3-RELEASE`, resolves to commit
  `7242fa9cb8993aaf9a0a72522206b96a45da9c28`, tree
  `74cee4137d7cf047a39dfcd2c6874fc6dd37ef45`.
* Its `Sources/FoundationEssentials/FileManager/FileManager+Files.swift`
  is 42,075 bytes; SHA-256
  `70a0ff4e281a35cbcc672cff7f2a6f7518fc28d2f55422a6c248825a9289e8d4`.
* Getter lines 656–665 and setter lines 990–1000 are guarded with
  `#if !targetEnvironment(simulator) && FOUNDATION_FRAMEWORK` and are
  unchanged from the retained Swift6.3 source at immutable commit
  `60bd7a1d8a730ebdeab2cb037b0519ef3b010ed3`. The complete file differs
  in only two unrelated lines (`_extendedAttributes` access and an extended-
  attribute list bound), not those protection blocks.

This supports the already identified public-implementation omission hypothesis
across these two source families. **Neither compiler version nor a public
release tag attests which implementation was loaded in either Simulator.**
It does not explain private NSData/Foundation helper behavior conclusively,
prove universal Simulator incapability, or make volume support/setter success
equivalent to Complete. No new entitlement/signing change is justified.

## Acceptance and next discriminating observation

After the independently reviewed ownership-runner and exact-profile control
corrections are frozen, inventory committed and fresh bindings created, use
the **already required coordinator-owned composed runtime cycle** on the
qualified26.2 profile. Retain the same eight native operations, Foundation
image UUID, actual runtime and actual application comparison rows. An extra
standalone expensive protection probe is not required for this distinction.

This compares environment/runtime families, not an isolated SDK variable:
host OS, runtime and potentially the loaded Foundation implementation differ.

* The same native/app mismatches would strengthen evidence across two
  environments, but leave strict L08 unsatisfied and not physical-only.
* Native Complete with app mismatch would justify targeted same-process
  API/path/bridge investigation, not an automatic application patch.
* Actual Complete from both would require complete, freshly bound application
  evidence and independent comparison review. It would not retroactively
  pass the original strict L08 gate or establish device enforcement.
* Failure before native collection adds no protection observation; preserve
  its actual failure boundary and cleanup rather than count an attempted run.

Functional acceptance remains the existing exact 13 cold boots, 20 operation
records, 26 metadata comparisons and the separate three-host matrix, including
all Retry/Discard/retained-dual boundaries. Complete comparisons remain
separate from functional acceptance. `l08_functional_receipts.py` lines
185–201 requires all26; `l08_functional_runner.py:classify_final_companion`
never returns overall PASS, even if all26 match. At best it returns
`PARTIALLY_VERIFIED`; the original strict gate was not executed by that
companion. Do not change that classification merely to make CI green.

Physical locked/unlocked keybag, backup/restore and power-loss tests remain
separate device obligations, outside this task's permissions. Store
signing/publication and separate owner/legal obligations are also not proved.

## Work and cleanup boundary

No application/control source was edited, no repository tests or builds were
run, and no native tools, Simulator, CI dispatch, commit or push were executed
by this investigation. Only read-only source/evidence inspection, bounded
public-document retrieval, and these new research/report files were created.
No build workers or disposable outputs were created, so no Gradle stop or
cleanup of another agent's coordinated build lane was attempted. Existing
failed receipts and all concurrent user/agent changes were preserved.
