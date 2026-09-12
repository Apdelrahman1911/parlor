# Independent IOS-R1 app-host retry review

Reviewer: `/root/whodunit_cont`. Author/executor: `/root`. Source: `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`. Tracked status empty at review; untracked user/audit material preserved.

## Narrow approval

**SAFE TO ATTEMPT, not runtime PASS** for the exact 622-line `run_iosr1_apphost_cycle.py`, SHA-256 `7e6abf8cb71ea66fe73123323f6cbc907381af595a0a3127679627bd9fc1b5aa`. Immutable reviewed bytes: `evidence/iosr1-runner-independent-whodunit-cont/draft-7e6abf8cb71e.py`.

This is a delta approval from independently reviewed/executed `6922206db73a…` (609 lines). The full earlier source-reading lineage and ownership/finalizer analysis remain in `reviews/iosr1-runner-independent-whodunit-cont.md`; I reopened every changed hunk and surrounding environment, copied-wrapper, collection, validation, and cleanup paths. No fixture, init, Swift template, test method, ownership, or finalizer changed. Imported helpers remain `b34ad4662ac9…` and `a0500421159c…`.

## Changes independently checked

1. **New cycle identity:** `iosr1-apphost-02` avoids overwriting cycle01 receipts. Cycle01 exact executed runner and all other reviewed inputs were archived before retry edits.
2. **Unsigned context:** the whitelist now omits `EXPANDED_CODE_SIGN_IDENTITY` instead of setting it to an empty string. It still explicitly disables signing and leaves identity/team blank; the invocation init still refuses a nonblank identity, non-Debug build, or non-simulator SDK. No signing key, ad-hoc identity, dependency override, or application source changes were introduced.
3. **Source-backed reason:** independently reopened Kotlin Gradle Plugin 2.4.10 `XcodeEnvironment.kt:59–60,83–84`, `SetupEmbedAndSignAppleFrameworkTaskSideEffect.kt:14–20`, and `AppleXcodeTasks.kt:244–420`. The environment reader returns the string unchanged; the task adds `codesign --sign envSign` whenever `envSign != null`, including `""`. Absence removes this callback; `CODE_SIGNING_ALLOWED=NO` alone is not that plugin callback's guard. I independently recomputed the exact cached source JAR hash and all three source-entry hashes; they equal the retained immutable-public-source bytes in `research/iosr1-codesign-session_cont/`. Exact pin: `gradle/libs.versions.toml:8`. Public reference commit: `5687445832cd835b4509b9fbc264cdf1a8201093`; URLs/access times are in that research ledger. This supports a retry, not an assumption about Xcode's eventual child environment or success.
4. **Evidence before assertions:** `xcresult` extraction exit codes are recorded rather than immediately throwing. The exact owned simulator's app container is constrained beneath its UUID; the fixed synthetic result filename rejects a file symlink/unexpected resolved parent and anything over 8192 bytes. If available, the result and app hashes are collected before asserting XCTest success. Subsequent exact XCTest, copied nested-stop receipt, and strict probe-schema gates still determine runtime evidence; a copied result alone cannot PASS.
5. **Privacy of early collection:** I reopened both complete additive Kotlin producers and the complete Swift patch. They emit only fixed categories, counts, booleans, and optional numeric OSStatus; no exception text, record, key, metadata, or player state enters the JSON. The copied Swift writer is launch-flag-gated, one-shot, bounded, atomic, and refuses an existing file. The fresh task-owned simulator remains mandatory; old user containers are not admissible. Unknown/shared file holders still block cleanup and are never adopted merely by file access.
6. **Actual export compatibility:** cycle01's preserved compiled header, SHA-256 `47bf42c606417baf1e7fddac4138c01874448df0079f815a349b2b707163774e`, contains the expected singleton, `cancel()`, and a `void (^)(NSString *)` callback exported as `start(onJson:)`. The unchanged regex accepts that real declaration. This does not prove Swift linking or runtime completion in cycle02.

## Synthetic evidence

- Existing pure validator/patch/signal/schema/source-binding tests: **88 PASS** against the retry snapshot.
- Existing fake process-ownership tests: **9 PASS** against the retry snapshot.
- New AST-only collection/header/environment cases: **9 PASS**. Successful observations still require validation; failed XCTest or extraction retains a completed bounded probe without PASS; oversized/symlink/other-device input refuses copying; missing probe never passes; actual cycle01 export and unsigned whitelist are checked.
- These tests use fake commands and in-memory paths, not native tools, signals, builds, or an app. The collection tests use spies for validators; actual validators are exercised by the separate 88 tests.
- One test-author error is preserved separately: the extracted block has 12 statements, not the initially asserted 11. Execution stopped before the synthetic collection; fixing that count did not change the runner or application. See `retry-collection-synthetic-attempt-01.json` and the archived first script.

Receipts and scripts: `evidence/iosr1-runner-independent-whodunit-cont/{pure-synthetic-7e6abf8cb71e.json,ownership-synthetic-7e6abf8cb71e.json,retry-collection-synthetic-7e6abf8cb71e.json}`. Coverage and immutable-source binding: `coverage/reviews-iosr1-runner-retry-whodunit_cont.jsonl`.

## Boundaries and continuation

Root alone may run the approved retry in the shared build lane. This reviewer executed no Gradle, Xcode, simulator/device, signing, or app command. Pure tests created only compact audit evidence; no generated build output or owned process needs cleanup. Runtime completion, actual XCTest records, source-attributed storage outcomes, and actual post-run cleanup must be independently reconciled after root finishes. Physical-device behavior, real signed Keychain behavior, Store readiness, full UI/a11y, and multiplayer remain outside this narrow gate.
