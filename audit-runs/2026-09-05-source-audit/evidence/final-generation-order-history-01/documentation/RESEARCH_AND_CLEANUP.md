# Research, verification receipts and cleanup

## Evidence identity and research method

Research was used to resolve specific implementation/API uncertainties, not to
replace reading Parlor. [RESEARCH_INDEX.json](RESEARCH_INDEX.json) indexes dated
URL/fetch records and their original local evidence. The underlying dossiers
retain claims, applicable versions, access times, content hashes and limitations.
Index generation is not a new fetch or a new source-review attestation.

Research in this audit was accessed **2026-09-05**; exact UTC timestamps are in
individual records. Failed fetches and source-index-only inspections are not
treated as successful implementation verification. Current official web pages
are distinguished from immutable tagged/published source and local SDK headers.
Only public dependency/API information was queried; no signing material, player
data or confidential application excerpts were sent to external services.

| Question | Authoritative references and exact applicability | Retained evidence |
|---|---|---|
| Compose effect remount/lifetime | Published CMP runtime1.10.3 metadata and Google Maven AndroidX runtime1.10.5 `Effects.kt`; supports WD-C1's remove/remount schedule, not a measured device loop | `evidence/session_cont-research.json`, `evidence/androidx-runtime-1.10.5-LaunchedEffect-excerpt.txt` |
| Locale ownership | [Apple preference-domain/search order](https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/UserDefaults/AboutPreferenceDomains/AboutPreferenceDomains.html), [JetBrains resource environment](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-resource-environment.html), published CMP1.10.3 resource/locale source | `evidence/design-locale-api/research.json`; DS-C01 validation |
| Row/toast behavior | Exact dependency source in component dossiers plus unchanged production-composable tests; not general mobile layout certification | `evidence/mafia-layout-api/`, `evidence/design-toast-api/research.json` |
| Contrast | [W3C WCAG2.2 contrast minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html), sRGB formula and resolved source colors; 2.7003406786:1 | `research/DS-C03-w3c-whodunit_cont.json` |
| P2pKit ownership/send/identity | Published Maven Central P2pKit0.7.0-rc3 common/JVM/native source and matching metadata; does not establish physical LAN behavior | `evidence/session_cont-p2pkit-research.jsonl`, `evidence/research-p2p-send/`, `research/p2p-published-*.json` |
| Legacy backup and native exceptions | [Apple backup-exclusion API](https://developer.apple.com/documentation/foundation/urlresourcevalues/isexcludedfrombackup), Foundation read/close API/SDK headers, Kotlin2.4.10 `Foundation.def` and [exception interop](https://kotlinlang.org/docs/native-definition-file.html#handle-objective-c-exceptions); flag observation is not a backup transfer | `research/storage-residual-session_cont/research-ledger.json`, ST-C1 and residual validations |
| Unsigned iOS recovery warning | [Apple Security OSStatus documentation](https://developer.apple.com/documentation/security/errsecmissingentitlement), default access-group and Foundation APIs, installed simulator SDK26.5. Actual app-host rerun identifies the credential-pipeline failure; only the later equivalent same-app read establishes -34018. Neither original native status nor signed-device behavior is inferred | `research/IOS-R1-session_cont/research-ledger.json`; `validations/IOS-R1-apphost02-session_cont.md` |
| Xcode phase/UITest execution | Apple script documentation/open Swift-build source as corroboration, installed Xcode26.5 native shell witness, KGP2.4.10 task source and Xcode/xcresult manuals | `research/xcode-phase-session_cont/`, `research/ios-ui-test-session_cont/`, `research/kgp-simulator-session_cont/` |
| Empty versus absent KGP signing identity | Official Kotlin2.4.10 tag commit `5687445832cd835b4509b9fbc264cdf1a8201093`, `XcodeEnvironment.kt`, `AppleXcodeTasks.kt`, `SetupEmbedAndSignAppleFrameworkTaskSideEffect.kt`; all three fetched bytes match cached published sources. Empty non-null identity requests codesign; omission fixed the audit invocation, not application code | `research/iosr1-codesign-session_cont/fetch-and-cache-ledger.json`; `reviews/iosr1-codesign-session_cont.md` |
| GNU `stat` | [Coreutils9.4 `stat.c`](https://raw.githubusercontent.com/coreutils/coreutils/v9.4/src/stat.c) proves the mixed-output fallback; no GNU host execution was invented | `evidence/release-independent-session_cont/research.jsonl`, RL-C1 validation |
| Upload certificate trust | [Android app signing](https://developer.android.com/studio/publish/app-signing), [JDK21 jarsigner](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jarsigner.html), exact OpenJDK21.0.11 source; no owner's certificate inspected | Same release research ledger; RL-C2 validation |
| Google edit snapshots | [Android Publisher edits](https://developers.google.com/android-publisher/edits), [track update](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks/update), [staged rollouts](https://developers.google.com/android-publisher/tracks); corroborates synthetic TOCTOU model, not a live Store receipt | `research/google-edits-source-{1,2,3}.txt`, RL-C3 validation |
| Jupiter discovery | Resolved Jupiter5.10.1 test-method contract/source plus compiled Parlor method metadata; eight bodies subsequently ran via isolated Unit wrappers | `evidence/root-t3-independent-session_cont/research.jsonl` |
| Dependency verification/advisory applicability | [Gradle8.13 verification contract](https://docs.gradle.org/8.13/userguide/dependency_verification.html), schema1.3, published checksums, advisory/fix and Kotlin2.4.10 KAPT source; KAPT-specific candidate rejected because the affected path is not active | `research/metadata-official-refs-whodunit_cont.json`, `research/MT-C1-session_cont/`, MT-C1/MT-T1 validations |
| Ordered event cancellation | Published coroutines1.11.0 SharedFlow semantics: replay0/no subscribers drops without consumer backpressure; no current shipping GameEvent consumer consequence established | `research/ordered-event-session_cont/research-ledger.json` |
| Disposable Android instrumentation | Checked-in test harness/task wiring plus pinned AGP8.13.2/ddmlib, public Android environment contracts and exact installed SDK binary behavior; isolation must not read user ADB keys or use user devices | `reviews/android-runtime-closeout-session_cont.md`; managed-isolation research and actual cycle receipt where present |

The research register is not an assertion of complete upstream dependency
security review or of all future advisories. Version-specific counter-evidence,
blocked product decisions and unverified platforms remain explicit.

## Commands and artifacts

[VERIFICATION.md](VERIFICATION.md) / [verification-ledger.json](verification-ledger.json)
state the final result of each gate. [CLEANUP_LEDGER.json](CLEANUP_LEDGER.json)
indexes commands, original receipt hashes, timestamps, exits, preserved reports
and cleanup results. Original execution logs are under `evidence/<cycle>/`.

- Normal production/Desktop/Apple/combined-test cycles are separate from
  intentionally failing reproducers and unsuccessful audit harness attempts.
- `--dependency-verification=strict` was retained. No metadata/check/test gate
  was weakened. JDK21 and the checked-in wrapper were used.
- Generated unsigned AAB/framework/app/APK bytes were inspected or hashed where
  required, then removed. Their receipts are not signed-release certification.
  No release candidate was approved, published or retained.
- Test XML, selected manifests/plists, sanitized logs, screenshots and isolated
  fixtures are compact required evidence. Their nested copied paths sometimes
  contain `build/`; those are **not live build intermediates** and must not be
  indiscriminately deleted.
- Evidence limitations are retained: the first storage cycle's XML collection
  was incomplete; native test-binary hashing was not universal; 32 lint warnings
  are policy-accepted; omitted/ignored/host-disabled tests are not counted as run.
- Android managed02 ran all three declared Release instrumented methods on
  local Darwin/ARM64 API35, with disposable test-only signing. Managed01 failed
  before Gradle/emulator/test/key creation at the audit ADB-listener setup.
- Apphost01 compiled the fixture but ran zero tests because of the audit's
  empty signing-identity environment entry. Apphost02 compiled/embedded and ran
  one copied diagnostic XCTest. Its observed credential-source failure is not
  a storage-health pass, and only launcher/framework execution-time hashes were
  retained—not full Debug bundle or signed-artifact provenance.

## Mandatory hygiene as executed

The shared lane's Gradle runner checked that target output directories were
absent before building. After each task returned—even failure—it immediately
ran `./gradlew --stop`, collected compact results, and removed only task-created
module/root/build-logic outputs. Exact deletion was used instead of another
Gradle clean invocation, so cleanup did not start a replacement daemon.

Xcode/native companions separately recorded immediate/final Gradle stops,
task-owned temporary/DerivedData removal and owned simulator shutdown/deletion.
The managed Android harness used a newly owned AVD/home/ADB context, not user
state. Its successful run and final cleanup were independently reconciled against
raw test XML, input hashes and detached-worker ownership. The iOS diagnostic
likewise used a freshly created simulator, not a user's existing data profile.

**Recorded cleanup exception and correction:** the apphost02 runner's initial
PASS omitted an external `ibtoold-67921` directory. Independent review found it
after the main outputs and all attested workers were gone. Root verified exact
parent/child identity, FIFO paths, uid/inodes/birthtimes and no open holders,
then unlinked only its two zero-byte FIFOs and removed the two empty directories.
The additive `evidence/iosr1-apphost-02-secondary-cleanup/receipt.json` and
independent `evidence/iosr1-apphost-02-post-secondary-cleanup-independent-whodunit-cont.json`
record successful correction. The initial failed independent check and the raw
runner-only PASS are both preserved, not rewritten. The final checker now checks
explicit secondary roots and both macOS path aliases; that read-only extension
received independent positive/negative tests before use.

The original independent reconciliation plus supplements verify the actual
cycle-level results. No global Gradle cache, protected signing input, source or
pre-existing user work was removed. Pre-existing other-version Gradle/Kotlin
workers were not claimed as audit-owned or terminated. Earlier native cycles did
not capture ownership paths for every possible secondary Apple temporary file;
only their recorded roots/UUIDs and attested processes can be proven absent.
No global temporary-directory sweep or complete historical worker attribution
is claimed.

## Final preservation proof

`final_preservation_check.py` rechecks baseline hashes, current branch/SHA/tree,
refs/stash/untracked listing and `git diff --check`; checks known generated output
paths and exact task-owned native temporary/device roots; and inspects process
metadata without killing any unrelated process.

Its result is [evidence/final-preservation-and-hygiene.json](evidence/final-preservation-and-hygiene.json).
It does not fingerprint excluded private/prior/local-state contents or claim
ownership of every OS service. The verification ledger is separately schema-
validated using the readiness skill. A schema PASS means the evidence index is
consistent, **not that Parlor is READY**.
