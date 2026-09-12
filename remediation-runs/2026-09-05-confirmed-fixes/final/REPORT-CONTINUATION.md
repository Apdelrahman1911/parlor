# Parlor — Controlled Remediation Report (Continuation)

**Date:** 6 September 2026 (Africa/Cairo).  
**Current report:** this additive continuation supersedes status statements in [the preserved prior milestone](REPORT.md).  
**Verdict: NOT READY.** Fourteen scoped fixes are implemented, independently reviewed, and locally verified. One item is partially verified and one remains blocked on content-author decisions.

## 1. Completion and scope

| Status | Count | Share of the 16 authorized defects |
|---|---:|---:|
| FIXED AND VERIFIED, within the evidence limits below | 14 | **87.5%** |
| PARTIALLY VERIFIED — DS-C01 | 1 | 6.25% |
| BLOCKED — WD-C2 | 1 | 6.25% |

The denominator is the **16 independently confirmed defects authorized for remediation**. It is not the entire project, an exhaustive-review percentage, a GitHub closure count, or Store readiness. DS-C01 is not counted complete merely because code was changed. WD-C2's four story manifestations count as one finding; MF-C1's M-C01 alias is not counted twice.

No commits, staging, checkout/branch changes, merges, pushes, GitHub issue changes, Store operations, real signing, identity changes, or publication enablement were performed. All changes remain uncommitted for the owner's review.

The earlier milestone completed combined native/Apple checks and resource validation. This continuation added a real iOS app-host language/restart matrix, independently reviewed cleanup controls, and final evidence reconciliation. It changed no application source and preserved every prior receipt, including the first failed selector test.

## 2. Issue-by-issue result

Reviewer names below are separate from each fix's author. Exact authors, original findings, independent conclusions, hashed review dossiers and fresh regression XML are indexed in [issues-continued.json](issues-continued.json). Current absolute source locations/hashes are in [issue-source-map.json](issue-source-map.json); all changed ranges are in [diff-identity.json](diff-identity.json).

| Finding | Implemented correction and regression evidence | Independent reviewer/conclusion | Compatibility | Status |
|---|---|---|---|---|
| **ST-C1** | Exclude and verify the iOS legacy directory before inventory/direct reads/migration. Exclusion failure is visible; failed legacy bytes remain recoverable. **8 new native tests**, plus 6 existing storage-safety tests. | `native_fix_review`: approved the bounded filesystem correction. | Encryption/save format and protected-record precedence unchanged. An OS backup flag is not real backup/restore evidence. | **FIXED AND VERIFIED** |
| **SN-C1** | A validated start commit exits the receive deadline before a separately bounded best-effort ACK. **9 tests** cover late/slow/lost/throwing ACKs, real caller cancellation, invalid commits and subsequent coordinator snapshots. | `root`, independent of `session_cont`: approved. | Exact protocol 4.2 and canonical authority unchanged. | **FIXED AND VERIFIED** |
| **SN-C2** | Ownership now spans kit creation, host/peer opening, lifecycle registration and handoff. Cancellation cleans unreturned rooms and collectors; an interrupted peer opening is not explicit Leave/revocation. **29 factory/host/peer/coordinator regressions**. | `factory_review`: approved after sibling-path and cancellation review. | All three platform factories use the correction. No credential, membership, snapshot or protocol migration. Physical sockets/radio behavior is not proved by fakes. | **FIXED AND VERIFIED** |
| **WD-C1** | Peer remount effects consult the current own-player authoritative flow and phase/session/seat/generation before automatic readiness. **12 real Compose-router tests**. | `root`, independent of `whodunit_cont`: approved; final cross-game review also completed. | No optimistic peer mutation or weakened command/revision validation. | **FIXED AND VERIFIED** |
| **IOS-B1** | Xcode's framework phase fails on unsuccessful `cd` or Gradle execution; stale output cannot mask failure through normalization. **8 shell/normalizer tests**, including stale-framework cases. | `session_cont`: approved. | Existing embedding task, identities and signing policy retained. This does not diagnose historical repeated launch failures. | **FIXED AND VERIFIED** |
| **ROOT-T3** | Explicit `Unit` fixes 10 invalid annotated method signatures. A compiled-signature test guards future discovery. **8 previously omitted enabled methods now execute**; physical tests remain ignored. | `session_cont`: approved. | Assertions preserved. Two corrected physical-test signatures remain disabled, alongside the existing third physical skip. | **FIXED AND VERIFIED** |
| **MF-C1 / M-C01** | Canonical nonterminal recovery cross-checks the Doctor's private previous effective protection against newest retained resolved-night history. Existing repeat setting retained; both Mafia UIs share the same Mafia-only eligibility predicate. **29 Doctor regressions**, detailed below. | `whodunit_cont`: approved; `factory_review` reviewed final cross-game interactions and test-analysis follow-up. | Default OFF, existing serialized flag, protocol and save schema retained. Valid ON histories load; inconsistent histories fail closed. No host history goes to peers. | **FIXED AND VERIFIED** |
| **WD-C3** | Reject impossible active Elimination final-two `Round`/`TiedRevote` recovery; retain valid terminal/replay and Classic states. **9 recovery tests**, with existing defensive-fallback coverage reconciled. | `root`, independent of `whodunit_cont`: approved; `factory_review` approved test-analysis follow-up and final cross-game pass. | No migration or snapshot normalization; completed games are not reopened. | **FIXED AND VERIFIED** |
| **M-C03** | Stack final name and role at full width instead of allowing long names to squeeze out roles. **6 actual-composable layout tests** cover compact/landscape, English/Arabic, font scales 1 and 2. | `whodunit_cont`: approved. | Only intentionally public final-role layout changes. Native screen-reader/layout verification remains separate. | **FIXED AND VERIFIED** |
| **DS-C01** | Record application-domain ownership of Parlor's iOS language override; restore only the exact owned value or prior absence. Preserve unmarked/external OS preferences and stable session composition. **11 native ownership tests**, 13 structural source-contract checks, and **one actual app-host XCTest with four distinct process launches**. | `native_fix_review`: source fix approved; `factory_review`: actual four-boot restart/System matrix approved. **Legacy-upgrade and broader lifecycle limits remain**. | No settings/session migration. Old unmarked app overrides cannot safely be distinguished from legitimate OS overrides; identical external writes and asynchronous UserDefaults durability remain limitations. | **PARTIALLY VERIFIED** |
| **DS-C02** | Allocate distinct toast lifetime IDs atomically outside retryable queue updates; include state-owner identity in composition keys. **4 state, 3 lifetime and 2 concurrency tests**. | `whodunit_cont`: approved; `native_fix_review` approved the test-dispatcher follow-up. | Queue bound of 4, adjacent-text coalescing, stale dismissal and independent expiry preserved. | **FIXED AND VERIFIED** |
| **DS-C03** | Cover-specific ghost-action colors on black privacy/recovery surfaces; ordinary light-theme Ghost buttons unchanged. **3 composable contrast tests** plus existing focus regression. Text contrast: approximately **10.25:1**, previously 2.70:1. | `whodunit_cont`: approved. | No privacy-cover, Back or action-callback behavior changes. | **FIXED AND VERIFIED** |
| **WD-C2** | **No story edits made.** Four objective chronology contradictions are confirmed, but source/history does not establish the author's intended facts. Exact questions are below. | `mafia_cont`: original defect independently confirmed; no fix approved. | All four resources remain byte-identical to HEAD at version 1.0.0. A prose edit changes canonical content identity and requires an explicit save/admission compatibility decision. | **BLOCKED** |
| **RL-C1** | Both AAB and dependency-report size checks use strict portable regular-file metadata instead of the broken BSD/GNU `stat` fallback. **7 shell-preflight tests**. | `mafia_cont` and `release_fix_review`: approved. | Existing 512 MiB/10 MiB bounds and security gates retained. Linux-shaped fixtures ran on macOS, not an actual Linux runner. | **FIXED AND VERIFIED** |
| **RL-C2** | Verify every payload signer/fingerprint; allow temporary public-only trust for an approved self-signed upload leaf only after certificate/algorithm checks. Mandatory strict `jarsigner` remains; TSA trust stays separate. **21 synthetic-signing tests**. | `release_fix_review`: approved after adversarial follow-up. | JDK 21 validation-only helper; no blanket exit-code waiver or weakening of integrity/identity checks. No real Store key or positive trusted RFC3161 end-to-end test claimed. | **FIXED AND VERIFIED** |
| **RL-C3** | Create the mutation edit first; read, validate, mutate and commit against that same edit snapshot. Preserve staged-rollout/digest checks, cleanup and no blind mutation retry. **9 new mocked-HTTP production-helper tests**. | `mafia_cont` and `release_fix_review`: approved. | Receipt and candidate binding retained; post-commit readback remains separate. No Play request was sent. | **FIXED AND VERIFIED** |

All release-tooling regressions also ran within the **172-test** release Python suite. Failed intermediate cycles and original-defect witnesses are preserved in [cycle-history.json](cycle-history.json); neither a successful negative witness nor an individually passing suite inside a failed aggregate is relabelled a repaired aggregate PASS.

## 3. Doctor setting: verified behavior, not a newly invented feature

The existing `doctorCanProtectSamePlayerConsecutively` switch remains **OFF by default**. Its core ON behavior already existed; the repair adds recovery consistency, removes UI predicate duplication within Mafia, improves Arabic wording, and verifies the complete applicable path.

- **ON:** the same eligible living target is protected over **four consecutive nights**, across engine player counts **5–16**. There is no arbitrary two-night limit.
- **OFF:** a repeated target is rejected; A/B/A alternating protection succeeds. A resolved explicit skip clears the effective previous protection, permitting the target again on a later eligible night.
- Self-protection remains an independent setting. One submission per night, living-target eligibility, and no resurrection remain enforced.
- Both settings run through **130-night traces**, including the 128-record history cap. First night, absent/dead Doctor, terminal private-state clearing, revotes, valid save/load and forged null/wrong previous-protection fields are tested.
- Real local session/recovery paths and host/peer bridges verify settings propagation, immutable post-start setup, own-private projections and bridge rejoin. Those bridge tests use **in-memory transport**, not physical LAN or cold transport-credential rejoin.
- Arabic now says **«ليالٍ متتالية»**. The English wording already describes consecutive nights correctly.

## 4. Combined verification of the same frozen source

Every cycle used one coordinated build lane, JDK **21.0.11**, the checked-in Gradle **8.13** wrapper, strict dependency verification, and task-owned temporary paths. Final build cycles used `--no-build-cache`, `--no-configuration-cache`, `--no-parallel`, and one worker. Signing variables/properties were removed or explicitly empty.

| Executed gate | Actual result | Independent evidence review |
|---|---|---|
| `productionCheck` — `combined-production-04` | **PASS:** 1,372 JVM/Android test executions, 3 physical-test skips; 172 release Python tests. 43 Detekt reports with zero findings. Lint/R8/unsigned AAB, shell dispatch and workflow validators pass. **32 accepted lint warnings remain**, unchanged from policy. | `release_fix_review/combined-production-evidence-04.{md,json}` |
| `allTests productionIosSimulatorRuntimeTests` — `native-alltests-01` | **PASS:** 2,458 test executions and 3 physical-LAN skips across 392 XML suites: **1,220 Desktop + 426 iOS simulator + 406 Android debug-unit + 406 Android release-unit** passes. | `native_fix_review/native-final-verification-01.{md,json}` |
| `productionAppleCheck` — `combined-apple-01` | **PASS:** three fresh Release framework links (`iosArm64`, `iosSimulatorArm64`, `iosX64`); four actual iOS-main Detekt reports with zero findings. 113 other Apple analysis tasks are **NO-SOURCE**. **No runtime tests** belong to this cycle. | `release_fix_review/combined-apple-evidence-01.{md,json}` |
| English/Arabic structural resource parity | **PASS:** 139 shell, 323 Whodunit, 269 Mafia and 16 design-system resources per locale; keys, types, empty values and format placeholders checked. | `evidence/final-resource-checks-01/` |
| `git diff --check` | **PASS** for tracked changes. Complete source/addition hashes are recorded separately. | Same lightweight cycle |

### Additional actual iOS runtime verification

`dsc01-apphost-02` **PASS**: one exact XCTest, zero failures/skips, four distinct app process launches and 14 ordered synthetic observations. Through the actual Settings UI, Arabic survives a restart then System restores prior absence/English fallback; English survives a restart then System restores the exact synthetic prior `[ar-EG, en]` preference. Ownership was already present **before original App initialization** on both restarts. Real localized controls and the original UIKit root's forced LTR/RTL direction are checked; exactly one root controller was created per process.

The test uses unchanged Kotlin App/Settings and an **isolated copied Swift wrapper** containing a bounded observer. It is not an unmodified shipping-wrapper smoke test, direct Compose-direction probe, active-game identity test, actual iOS Settings app interaction, physical-device or signed-release proof. `factory_review/dsc01-apphost-02-independent-result-review.{md,json}` independently verifies the raw results, copy/source binding and cleanup.

The first attempt, `dsc01-apphost-01`, genuinely executed **one failed XCTest**: its exact Settings label omitted the child text that pinned CMP 1.10.3 merges into the accessibility label. Home launch/observation succeeded; no language mutation occurred. This was not an inferred app crash. Its incorrect `NOT_RUN` receipt field remains untouched and is explicitly qualified by raw failure evidence. The versioned harness fixes the selector and status bookkeeping—not application code—and preserves every language/restart assertion. Both successful and failed cycles cleaned their outputs, owned simulators, workers and attested secondary FIFOs.

**36** current harness safety/receipt/shell/selector contracts and **17** final-observation contracts pass. These are evidence-infrastructure tests, not additional app-runtime cases. Hash-bound before/after controls and Gradle-stop receipts are retained; earlier overlapping executions are not summed as distinct tests.

Counts are **executions**, not distinct tests summed across overlapping cycles. Mafia has **267 Desktop tests**, Whodunit **309**. Mafia and shared engine have no native/Android test source in the applicable graph; their compilation is not native rule-test coverage. All **13 `iosX64Test` tasks are host-disabled**. The 426 native tests actually ran on a newly created, task-owned iOS 26.5 simulator across 11 modules.

Three `P2pKitRoomTransportLoopbackTest` methods remain deliberately skipped: peer-to-host round trip, peer admission/membership, and host broadcast. Their historical admission assumptions also need investigation before any attempt to enable them. Advertisement success and in-memory bridge tests are not physical P2pKit interoperability evidence.

Resource parity does not establish translation quality, native VoiceOver/TalkBack behavior or visual completeness. Existing R8/native-symbol and accepted lint advisories are not hidden by the PASS labels.

## 5. Exact source and artifact identity

```text
Repository: /Users/abdelrahman/Projects/parlor
Branch: main
Base HEAD: 3625d0663ba6eb51338cbd5f9dc45f859ec18846
Base Git tree: db7f3d2afe73a13628296daee2cce71165eebc8d
Verified 658-file source manifest SHA-256:
e59da533dbec2f02f6f2b460ce72e2a7337af1e44abfb0bc6d533304d4127ec7
Tracked diff SHA-256:
60b555c0dd323a086e959ffbc5aa3fd7c6629ae1d0ad016021572e7ae272e4dd
Complete 62-file patch SHA-256, INCLUDING 28 untracked source/test additions:
305b8f3db676a79ddc98faf72e08d4269596528cdc30cafbbf4c6811312f03f9
```

The base commit/tree **alone do not identify these changes**. Use [source-identity.json](source-identity.json), [diff-identity.json](diff-identity.json) and [remediation.patch](remediation.patch) together. The manifest excludes audit/design evidence and protected material; those are not silently counted as shipping source.

The unsigned AAB was 8,802,894 bytes, SHA-256 `d046d2a2ed9856d5e77888acc9a31a41dd46d1bd64faa3585a16008be8d3f01e`. Its receipt contains the 354-entry archive inventory. Apple receipts identify all three linked framework binaries and retain their plists. Large generated artifacts were inspected and then deleted; compact receipts remain. None is an approved signed release candidate.

Protocol 4.2, seeds, serialized settings/save formats, P2pKit 0.7.0-rc3 pins, dependency-verification metadata, application identities and publication-disablement safeguards are unchanged. Game rules remain in game modules; shared transport/session code contains no new Mafia/Whodunit dispatch. The host remains the sole multiplayer reducer authority.

## 6. What is still needed

### WD-C2 — four specific author decisions

1. **Last Dinner / James:** did he actually return/light the cigar at **9:00 or 9:15**? Preserve the deliberately false **9:10** alibi unless separately directed.
2. **Layla Halabi:** is the outage at **9:05 or 9:45**? Sami's factual 9:35 entry currently describes it as already having happened.
3. **Jasmine / Nadim:** retain **10:50 entry, 11:00 hiding, 11:10–11:12 descent** and shorten “a full hour,” or did he enter/hide earlier?
4. **Khan / Karim:** are poisoning/records-arrival **9:40/10:00**, or the final narrative's approximately **10:14/10:15**? Refaat corroborates the 10:15 arrival.

After the author chooses, decide content-version and existing-save compatibility explicitly. Canonical content hashing includes prose; simply retaining version 1.0.0 does not preserve the digest. Do not weaken admission/save matching, rewrite intentional lies or silently normalize old saves. See [the unchanged-content evidence](../whodunit/WD-C2-blocked.md).

### DS-C01 — remaining verification and upgrade ambiguity

The actual four-process Settings/restart matrix described above now passes. Remaining verification is **direct Compose direction, actual iOS Settings interaction and active-game/controller continuity**. Current in-game navigation deliberately hides Settings; changing the persistent domain behind the running store would not test its real live mutation path. No artificial navigation path or production observation hook was added.

An old unmarked `AppleLanguages` value may be an earlier Parlor override or a legitimate OS-managed preference. Current code safely preserves it. **An explicit legacy handling policy remains necessary**; the test does not manufacture missing provenance. Same-value external writes and asynchronous/nontransactional UserDefaults durability remain bounded guarantees. DS-C01 therefore stays **PARTIALLY VERIFIED**, despite the now-successful restart matrix.

The archived Apple runner remains untouched. New independently reviewed versioned harnesses address its secondary-worker cleanup limitation: live PID/start ancestry, UID/inode/birth-time and exact FIFO-pair ownership are attested; only those FIFOs and empty parents are removed after workers exit. **Both actual cycles verified this cleanup.** No broad `/var/folders` deletion, global-cache deletion or unrelated process termination occurred.

### Other unresolved or external gates

- **IOS-R1 remains TEST/EVIDENCE GAP**; no warning was hidden and no entitlement was added blindly.
- **WD-C4 Leave-confirmation timer policy remains unresolved**, outside these 16 repairs. No decision was invented about whether modal time counts.
- The copied unsigned Debug Swift/Compose app-host now ran successfully. The **unmodified shipping-wrapper smoke test and unsigned Swift Release wrapper** were not rerun; the observer matrix does not replace them.
- Installed **Xcode 26.5 / 17F42** differs from Store-qualified **26.3 / 17C529**. No qualified archive/signing evidence exists from this run.
- Android managed-device runtime needs its pinned Linux/KVM/x86_64 environment; the current Apple Silicon host is not that gate. Other Desktop host graphs were not rerun here.
- Physical Android/iOS LAN, Local Network denial/recovery, app-switcher/lifecycle behavior, native accessibility/RTL/large-text, real backup/restore, signing, Store, privacy declarations and legal/content-rights approvals remain separate requirements.
- The known `com.parlor.app` Store-identity collision and disabled publication workflows remain intact. Owner-approved identities and publication authorization are prerequisites, not a code check to bypass.
- The original register includes nine documentation discrepancies, six evidence gaps and three rejected candidates in addition to the 16 defects, plus unconfirmed WD-C4. This remediation does **not** declare every other audit item fixed or provide a new full-project line-by-line coverage claim.

## 7. Safety, cleanup and continuation

The baseline's **1,618 inventoried original files remain present**: 1,584 unchanged and 34 intentionally modified tracked files. **No pre-existing untracked file changed.** Twenty-eight source/test files were added. HEAD, branch refs, stash and empty staging index are preserved; `AGENTS.md`, existing audits and design work are untouched. See [preservation.json](preservation.json).

Completed build cycles recorded immediate `./gradlew --stop`, then exact task-owned `build/` and scratch removal after retaining required reports. The owned native simulator was shut down and deleted, with pre-existing simulators preserved. Completed modern cycles report no cleanup errors, remaining owned workers or retained build outputs. The original `ios-b1-red` receipt predates PID/start ownership logging; its empty scan is retained as a historical evidence limitation, not fabricated proof of historical worker absence. Global Gradle caches, private material and unrelated processes were not removed. The checkout is intentionally dirty with authorized, **uncommitted** changes—not falsely described as a clean release tree.

Evidence entry points:

- [Current issue ledger](issues-continued.json), [current gate ledger](gates-continued.json), [combined verification](verification-summary.json).
- [Cycle/failure history](cycle-history.json), [source/patch identity](diff-identity.json), [final observation](final-observation.json).
- [Original review/research index](review-and-research-index.json), plus [the additive continuation evidence](continuation-evidence.json). Original audit receipts are retained separately and never overwritten.

Next safe implementation step: obtain the four chronology decisions and an explicit content/save compatibility policy; implement only those approved changes with regression and independent review. Resolve the DS-C01 legacy-language policy and remaining broader lifecycle evidence; the bounded app-host restart matrix is now complete. Any source change invalidates the final source freeze and requires relevant focused and combined checks again. Integration and GitHub closure still require separate authorization.

The latest [final observation](final-observation.json) binds the finished post-materialization cycles, preservation checks, cleanup evaluation, observed workers and free disk. It deliberately records incomplete historical PID evidence rather than claiming perfect historical cleanup provenance.
