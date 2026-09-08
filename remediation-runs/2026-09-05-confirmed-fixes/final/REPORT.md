# Parlor — Controlled Remediation Report

**Date:** 6 September 2026 (Africa/Cairo).  
**Verdict: NOT READY.** Fourteen scoped fixes are implemented, independently reviewed, and locally verified. One item is partially verified and one remains blocked on content-author decisions.

## 1. Completion and scope

| Status | Count | Share of the 16 authorized defects |
|---|---:|---:|
| FIXED AND VERIFIED, within the evidence limits below | 14 | **87.5%** |
| PARTIALLY VERIFIED — DS-C01 | 1 | 6.25% |
| BLOCKED — WD-C2 | 1 | 6.25% |

The denominator is the **16 independently confirmed defects authorized for remediation**. It is not the entire project, an exhaustive-review percentage, a GitHub closure count, or Store readiness. DS-C01 is not counted complete merely because code was changed. WD-C2's four story manifestations count as one finding; MF-C1's M-C01 alias is not counted twice.

No commits, staging, checkout/branch changes, merges, pushes, GitHub issue changes, Store operations, real signing, identity changes, or publication enablement were performed. All changes remain uncommitted for the owner's review.

The final continuation completed the remaining combined native and Apple checks, structural English/Arabic resource validation, independent evidence review, and the preservation/diff record. It did not introduce further application behavior changes.

## 2. Issue-by-issue result

Reviewer names below are separate from each fix's author. Exact authors, original findings, independent conclusions, hashed review dossiers and fresh regression XML are indexed in [issues.json](issues.json). Current absolute source locations/hashes are in [issue-source-map.json](issue-source-map.json); all changed ranges are in [diff-identity.json](diff-identity.json).

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
| **DS-C01** | Record application-domain ownership of Parlor's iOS language override; restore only the exact owned value or prior absence. Preserve unmarked/external OS preferences and stable session composition. **11 native ownership tests**, plus 13 structural source-contract checks. | `native_fix_review`: approves the ownership correction, **not complete end-to-end or legacy-upgrade coverage**. | No settings/session migration. Old unmarked app overrides cannot safely be distinguished from legitimate OS overrides; identical external writes and asynchronous UserDefaults durability remain limitations. | **PARTIALLY VERIFIED** |
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

Native tests exercise real Foundation preferences in unique synthetic suites, but creating a new owner object is **not killing and relaunching the app**. Still needed: fresh owned-simulator app tests for persisted English/Arabic/System selection across real process termination, native/Compose direction, OS per-app changes, and active-session continuity.

An old unmarked `AppleLanguages` value may be either an earlier Parlor override or a legitimate OS-managed choice. It cannot safely be deleted based only on its value. A deliberate legacy recovery policy is still required; no claim of universal upgrade repair is made. Same-value external writes and UserDefaults' asynchronous/nontransactional persistence also remain explicitly bounded guarantees.

Before reusing `audit-runs/2026-09-05-source-audit/run_iosr1_apphost_cycle.py`, repair and independently test its known secondary Apple-worker temporary-path cleanup limitation using **ownership-attested paths only**. That archived runner was **not reused** in this phase.

### Other unresolved or external gates

- **IOS-R1 remains TEST/EVIDENCE GAP**; no warning was hidden and no entitlement was added blindly.
- **WD-C4 Leave-confirmation timer policy remains unresolved**, outside these 16 repairs. No decision was invented about whether modal time counts.
- The actual Swift/Compose app-launch test and unsigned Swift Release wrapper were **not rerun in this remediation**. Kotlin-native tests and framework linkage are not substitutes.
- Installed **Xcode 26.5 / 17F42** differs from Store-qualified **26.3 / 17C529**. No qualified archive/signing evidence exists from this run.
- Android managed-device runtime needs its pinned Linux/KVM/x86_64 environment; the current Apple Silicon host is not that gate. Other Desktop host graphs were not rerun here.
- Physical Android/iOS LAN, Local Network denial/recovery, app-switcher/lifecycle behavior, native accessibility/RTL/large-text, real backup/restore, signing, Store, privacy declarations and legal/content-rights approvals remain separate requirements.
- The known `com.parlor.app` Store-identity collision and disabled publication workflows remain intact. Owner-approved identities and publication authorization are prerequisites, not a code check to bypass.
- The original register includes nine documentation discrepancies, six evidence gaps and three rejected candidates in addition to the 16 defects, plus unconfirmed WD-C4. This remediation does **not** declare every other audit item fixed or provide a new full-project line-by-line coverage claim.

## 7. Safety, cleanup and continuation

The baseline's **1,618 inventoried original files remain present**: 1,584 unchanged and 34 intentionally modified tracked files. **No pre-existing untracked file changed.** Twenty-eight source/test files were added. HEAD, branch refs, stash and empty staging index are preserved; `AGENTS.md`, existing audits and design work are untouched. See [preservation.json](preservation.json).

Completed build cycles recorded immediate `./gradlew --stop`, then exact task-owned `build/` and scratch removal after retaining required reports. The owned native simulator was shut down and deleted, with pre-existing simulators preserved. Final cycles report no cleanup errors, remaining owned workers or retained build outputs. Global Gradle caches, private material and unrelated processes were not removed. The checkout is intentionally dirty with authorized, **uncommitted** changes—not falsely described as a clean release tree.

Evidence entry points:

- [Issue ledger](issues.json), [gate ledger](gates.json), [combined verification](verification-summary.json).
- [Cycle/failure history](cycle-history.json), [source/patch identity](diff-identity.json), [final observation](final-observation.json).
- [Independent review and research index](review-and-research-index.json). Original audit receipts are retained separately and never overwritten.

Next safe implementation step: obtain the four chronology decisions and an explicit content/save compatibility policy; implement only those approved changes with regression and independent review. In parallel, complete the DS-C01 app-host verification prerequisite and lifecycle matrix. Any source change invalidates the final source freeze and requires relevant focused and combined checks again. Integration and GitHub closure still require separate authorization.
