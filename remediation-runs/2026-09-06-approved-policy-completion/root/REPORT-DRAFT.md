# Parlor — Controlled Remediation Completion

**DRAFT — native-language verification and final evidence reconciliation are still in progress.**

This report covers the 16 independently confirmed defects authorized for repair, including the subsequently approved DS-C01 language policy and WD-C2 testing-content decisions. It is not a new exhaustive whole-repository audit, a GitHub closure count, or approval to publish.

## Scope, status and source identity

**Repository/release verdict: NOT READY.** The completion percentage below refers only to the 16 authorized repairs and their stated local verification, not Store readiness or the absence of other defects.

<!-- FINAL_SCOPE_COUNTS -->

The work remains **uncommitted**. No staging, branch/checkout changes, commits, merges or pushes occurred in the main repository. No GitHub mutations, private Store signing/credentials, Store operations, application-identity changes or publication enablement were performed. Disposable synthetic Git/signing test fixtures remain distinct from those prohibited operations. Original audits and failed-run receipts are preserved; this report supersedes their old completion statements, not their evidence.

```text
Repository: /Users/abdelrahman/Projects/parlor
Branch: main
Base HEAD: 3625d0663ba6eb51338cbd5f9dc45f859ec18846
Base Git tree: db7f3d2afe73a13628296daee2cce71165eebc8d
669-file source-manifest SHA-256:
9ac2be536979f2216b39c10a6d148125a12380987d4efd483bbcbaeb1733318e
Tracked-diff SHA-256:
148fb4583d8fa8341b3cbc48a7fc0bc7564bf4df5c27e9d80e32c6bfdc57b784
Complete 84-file patch SHA-256 (45 modified + 39 new files):
71b17c8e05ab9e5850c523ff0c25a29a576a6bc088636c4a265e0944efcfc000
```

HEAD/tree alone do not identify a dirty checkout. [source-identity.json](source-identity.json), [diff-identity.json](diff-identity.json), [issue-source-map.json](issue-source-map.json) and [remediation.patch](remediation.patch) bind the reviewed source, including untracked test/source additions. The patch was independently reconstructed in memory: all 161 hunks reproduce the current 84 files exactly. Audit/design material and protected exclusions are not silently counted as shipping source.

## Issue-by-issue results

| Finding | Correction and regression evidence | Independent review | Compatibility | Status |
|---|---|---|---|---|
| **ST-C1** | Verify iOS legacy-directory backup exclusion before listing, reading or migration; retain the last recoverable bytes on failure. **8 native regressions + 6 storage-safety tests**. | `native_fix_review`: approved. | No encryption/save/key change; a backup flag is not real backup/restore proof. | **FIXED AND VERIFIED** |
| **SN-C1** | Leave the receive deadline after a validated irreversible start commit; bound the best-effort ACK separately while preserving caller cancellation. **9 regressions**. | `root`, independent of `session_cont`: approved. | Protocol 4.2, command validation and host authority unchanged. | **FIXED AND VERIFIED** |
| **SN-C2** | Cancellation-safe kit/host/peer creation, registration, ownership handoff and cleanup. An interrupted opening is not explicit Leave. **29 factory/host/peer/lifecycle regressions**. | `factory_review`: approved. | Platform factories updated; no credential, membership or wire-format migration. | **FIXED AND VERIFIED** |
| **WD-C1** | Before auto-readiness, consult current own-player authoritative state and phase/session/seat/generation; remounts do not resubmit recorded readiness. **12 actual Compose-router regressions**. | `root`, independent of `whodunit_cont`: approved. | No speculative peer mutation, weakened revision check or retry. | **FIXED AND VERIFIED** |
| **IOS-B1** | Fail Xcode embedding on failed `cd`/Gradle; normalize only after success. **5 extracted-shell + 3 normalizer tests**, including stale output. | `session_cont`: approved. | Existing embedding/signing/identity policy retained. | **FIXED AND VERIFIED** |
| **ROOT-T3** | Correct 10 annotated method signatures to explicit `Unit`; enforce compiled signatures. **8 formerly missing enabled tests now execute**. | `session_cont`: approved. | Assertions preserved; two corrected physical signatures remain disabled, alongside the third physical skip. | **FIXED AND VERIFIED** |
| **MF-C1** | Settings-aware Doctor previous-protection/history consistency; shared Mafia-only UI target predicate. **29 Doctor regressions**, detailed below. | `whodunit_cont`, with `factory_review` follow-up: approved. | Existing default-OFF flag/schema retained; valid ON histories load; host-only history never goes to peers. | **FIXED AND VERIFIED** |
| **WD-C3** | Reject impossible active Elimination final-two Round/revote recovery; retain legitimate terminal/Classic/replay states. **9 recovery regressions**. | `root`, with `factory_review` follow-up: approved. | No normalization/migration or reopening of completed games. | **FIXED AND VERIFIED** |
| **M-C03** | Give final names and roles full-width stacked layout so long names do not squeeze out roles. **6 real Compose layout cases**, compact/landscape, EN/AR, font scales 1/2. | `whodunit_cont`: approved. | Only intentionally public final-role layout changes. | **FIXED AND VERIFIED** |
| **DS-C01** | Ownership-aware iOS language restoration, approved pre-release policy and stable UIKit containment. See the dedicated evidence section. | `native_fix_review` / `release_fix_review` / `factory_review`: exact roles in the issue ledger. | Preserve OS preferences; no ambiguous development-build migration or language-keyed session recreation. | **PENDING FINAL NATIVE REVIEW** |
| **DS-C02** | Atomic distinct toast-lifetime identities outside retryable queue updates; include state-owner identity in composition keys. **9 state/lifetime/concurrency regressions**. | `whodunit_cont`, `native_fix_review` follow-up: approved. | Queue bound 4, coalescing, stale dismissal and independent expiry preserved. | **FIXED AND VERIFIED** |
| **DS-C03** | Cover-specific ghost-action colors on black privacy/recovery surfaces; ordinary light-theme buttons unchanged. **3 contrast tests + recovery-focus regression**. | `whodunit_cont`: approved. | Text contrast approximately 10.25:1, formerly 2.70:1; no Back/privacy-action change. | **FIXED AND VERIFIED** |
| **WD-C2** | Four coherent story corrections, version bumps, strict identity checks and explicit-discard preservation. **27 focused passing descriptors**, including 48 deterministic complete-game combinations. | `release_fix_review`: original content/recovery; `factory_review`: root-authored preservation follow-up. Approved. | Four content revisions become 1.0.1; old/missing identities cannot resume; save/protocol formats unchanged. | **FIXED AND VERIFIED** |
| **RL-C1** | Strict portable file-size checks for both AAB and dependency report. **7 shell-preflight regressions**. | `mafia_cont` / `release_fix_review`: approved. | Existing 512 MiB/10 MiB bounds retained; Linux-shaped fixtures ran on macOS. | **FIXED AND VERIFIED** |
| **RL-C2** | Public-only temporary trust for approved self-signed upload leaf certificates, after certificate/algorithm/signer checks; retain mandatory strict `jarsigner`. **21 synthetic-signing regressions**. | `release_fix_review`: approved after adversarial review. | Fingerprints, integrity, unsigned-entry rejection and separate TSA trust remain strict; no real Store key used. | **FIXED AND VERIFIED** |
| **RL-C3** | Read/validate/mutate/commit against the same mutation-edit snapshot; retain rollout/digest checks, cleanup and no blind retry. **9 mocked-HTTP production-helper regressions**. | `mafia_cont` / `release_fix_review`: approved. | Candidate/receipt binding retained; no Play operation performed. | **FIXED AND VERIFIED** |

Full authorship, independent-review chains, current absolute source paths/hashes/one-based locations, regression XML, compatibility and limitations are in [issues.json](issues.json). The original finder/author does not approve their own fix. Compilation, mocked transport, synthetic signing and a passing subtest within a failed aggregate retain their narrower meanings.

## Doctor protection — both configurations verified

`doctorCanProtectSamePlayerConsecutively` already existed; it was **not introduced as a new switch**. The repair preserves its serialized name and default **OFF**, and cross-checks recovery against retained canonical history.

- **ON:** protect the same eligible living target for **four consecutive nights**, across player counts **5–16**. There is no arbitrary two-night limit.
- **OFF:** consecutive repetition is rejected; legal alternating targets succeed. An explicit resolved skip clears the previous effective protection under the existing semantics.
- Self-protection remains separately configurable. The first valid submission is final for that night; a setting does not permit multiple submissions, dead targets or resurrection.
- Organizer/host setup, atomic authoritative start, reducers/resolution, local and LAN target eligibility, own-private peer state and recovery carry the chosen value. Settings cannot change mid-game.
- The 29 Doctor regressions include first night, absent/dead Doctor, terminal private-field cleanup, valid ON/OFF saves, forged null/wrong previous targets, skips and **130-night traces spanning the 128-record history bound**. Host history is not transmitted to peers.
- In-memory host/peer/rejoin tests are not physical P2pKit or cold credential-rejoin proof. Native game compilation is not native execution of Desktop-only rule tests.

Arabic now uses **«ليالٍ متتالية»**; English already correctly described consecutive nights.

## WD-C2 — coherent testing stories, explicit incompatibility

Every line and affected killer variant of the four stories was read during this scoped correction; see the [full-story input ledger](../story/input-review.json) and [independent source review](../reviews/independent-wdc2-original-source-review-01.json). The changes are **15 scalar edits**, including four content-version bumps **1.0.0 → 1.0.1**. No reducer, game rule, role/character/killer ID, sampling order, player count, mode, schema, minimum app version or protocol change was made.

| Story | Selected factual chronology | Intentional deception retained |
|---|---|---|
| Last Dinner | James returns and lights his cigar at **9:15**. The unsupported 25-minute gap is removed. | The false **9:10** alibi, pantry detour and incriminating cufflink. |
| Layla Halabi | One outage at **9:05**, aligned across introduction, setting and Ghassan's account. | Existing guilty detours/cover stories; no second outage invented. |
| Jasmine Ring | Cellar entry **10:50**, hidden **11:00**, victim descends **11:10**, attack **11:12**; not a full hour of hiding. | False cigarette-shopping story and shopkeeper rebuttal. |
| Khan el-Khalili | Poisoning **9:40**; records **10:15–10:55**; **35-minute** interval. | The truthful later records alibi deliberately omits earlier poisoning. |

Canonical content digests include prose. Catalog versions derive from the envelopes; there is no separate pinned digest table to regenerate. Kotlin tests prove identity changes and strict version/digest save/LAN matching. Literal digest cross-checks in the editorial evidence were computed independently in Python, not misrepresented as printed Kotlin output.

Old internal saves have **no migration promise**. Incompatible or identity-less records cannot launch. Retired Solo/unsupported local-mode records are rejected **before decoding**, not converted. The UI explains incompatibility in English and Arabic; Retry, Back and remount preserve bytes. Only an explicit Discard deletes the selected save. There is no silent rewrite, inferred identity or automatic deletion.

Focused current coverage: **5 chronology + 4 compatibility + 1 complete-game-trace + 11 reconstruction + 6 actual recovery-UI tests = 27 passing descriptors**. The complete-game test covers **24 killer variants × 2 modes = 48 combinations**, each played twice deterministically, with strict snapshot/recovery round-trips and replay. The preservation red run had 3 expected failures; the same regression tests subsequently passed. Earlier loader-precheck failures are not falsely described as rendered-UI witnesses.

The owner approved these as **testing content only**. They remain bundled/reachable, not test-only assets. [The chosen chronologies](../../../docs/WHODUNIT_TEST_CONTENT.md) document reasoning and unresolved out-of-scope editorial observations. Final production editorial/content-rights review remains required.

## DS-C01 — supported baseline and actual verification

The [pre-release policy](../../../docs/PRE_RELEASE_COMPATIBILITY.md) supports clean installs and current ownership-aware language selections, System mode and restarts. Ambiguous unmarked old development overrides have **no supported migration path**. This is an intentional owner-approved policy, not an unresolved blocker.

The ownership record releases only Parlor's exact installed override, restoring the previous application-domain preference or absence. Unmarked/external OS preferences are preserved. Development reset means a fresh explicitly owned test profile—not deleting Apple preferences, resetting a personal device or silently clearing user data. UserDefaults remains asynchronous; normal-restart tests do not establish power-loss atomicity or indistinguishable same-value external-write provenance.

A native app-host test independently exposed sustained native RTL/Compose LTR disagreement after foregrounding. The correction adds a **stable plain UIKit parent**, giving the host its own outer view while Compose retains the same child and direction ownership. It is not a second navigation controller. Source review and the named UIKit/simulator checks verify the preservation of full bounds, existing Compose insets, privacy-cover code, appearance forwarding, system decorations and child orientation policies; this is not physical app-switcher/accessibility approval. `MainViewController.kt` is unchanged from HEAD. No language-keyed session/controller recreation was introduced.

Verification includes 11 actual Foundation ownership tests, 13 Desktop structural contracts, four actual host-composable continuity cases, four UIKit container tests and an uninstrumented Swift/Compose cold-launch smoke test. Host tests use real retained runtimes and gated start handshakes with controlled transport; they do not establish physical LAN behavior.

The instrumented native matrix distinguishes **actual Settings-screen interactions** from **copy-only invocations of the real injected SettingsStore**. Local-game entry and setup use the public UI. Once a game is active, continuity checks change language through the latter mechanism, because active game routes do not expose Settings; these are not Settings-screen taps. In-app Settings choices and actual iOS per-app language selection are separate scenarios whose outcomes require their own evidence.

<!-- FINAL_NATIVE_RESULT -->

The original native direction failure, missed catalog successor and later Mafia Start wait are retained as distinct failed outcomes. No source establishes the historical user's “first four launches crash” explanation. A test selector failure, a source-proved native-direction regression, an Xcode failure and an OS termination are not interchangeable diagnoses.

## Combined verification against the frozen updated source

Root alone owned the build lane. Commands used the checked-in Gradle **8.13**, JDK **21.0.11**, strict dependency verification, one worker and no parallel/configuration cache. Final relevant Gradle compilation/test tasks were forced without the build cache. Current pins remain Kotlin **2.4.10**, CMP **1.10.3**, AGP **8.13.2**, Lint **9.1.1**, R8 **9.1.41**, P2pKit **0.7.0-rc3** and exact protocol **4.2**.

| Gate / current evidence cycle | Executed result |
|---|---|
| `productionDesktopCheck staticAnalysis` / `combined-desktop-static-04` | **PASS** — 188 suites, **1,243 descriptors: 1,240 PASS + 3 physical-P2P skips**. |
| `productionCheck` / `combined-production-04` | **PASS** — 222 suites, **1,395 descriptors: 1,392 PASS + 3 skips**; **172 release Python tests PASS**; Detekt, lint/R8, unsigned AAB, shell dispatch and release validators. |
| `allTests productionIosSimulatorRuntimeTests` / `combined-native-alltests-02` | **PASS** — 398 suites, **2,481 descriptors: 2,478 PASS + 3 skips**. Desktop 1,243 including 3 skips; Android debug-unit 406 and release-unit 406; actual ARM64 iOS simulator 426. |
| `productionAppleCheck` / `combined-apple-02` | **PASS** — all three Release frameworks link; four actual iOS Detekt reports clean; 147 tasks/143 executed. **No runtime tests** or Swift-wrapper compile in this gate. |
| Resource parity / `resources-and-diff-02` | **PASS** — English/Arabic shell 139, design-system 16, Whodunit **324**, Mafia 269; keys/types/placeholders/emptiness checked. Not translation-quality or screen-reader approval. |
| Tracked whitespace / same cycle | `git diff --check` **PASS**. New files are separately hashed/reviewed. |
| Uninstrumented iOS wrapper / `ios-wrapper-smoke-01` | **PASS, 5/5 XCTest**: one unchanged actual cold-launch/Home/no-alert test plus four UIKit container fixtures. Not five app launches or a language/session matrix. |

Counts are **executions per cycle**; overlapping gates must not be added into a fictional unique-test total. Current Desktop module counts include Mafia **269** and Whodunit **327**. The three physical P2pKit tests remain ignored. Thirteen `iosX64Test` tasks are host-disabled. Mafia/shared-engine `iosSimulatorArm64Test` tasks are **SKIPPED** with **NO-SOURCE** test compile/link prerequisites; linkage is not native rule coverage.

All **32 accepted lint warnings remain visible**. Earlier production-01/02/03 failures against the accepted-warning inventory remain failed receipts; the independently reviewed current inventory passes without suppressing warnings. Historical witnesses, compile failures, selector failures and cleanup-attestation failures remain in [cycle-history.json](cycle-history.json).

The unsigned AAB was **8,803,044 bytes**, SHA-256 `f30780bf995741a372646dd9c6a27705cb4eb0368c1d5a4de7197386d3a61473`, with 354 archive entries. Framework/plist and AAB metadata receipts were retained before deleting large outputs. None is a signed/approved Store candidate.

The **Debug artifact inventories—both smoke and instrumented app-host—omit `Parlor.debug.dylib`**: they capture only the launcher executable and Compose framework. Copied-source manifests/diffs and compile/runtime receipts identify the source exercised, but are **not a complete cryptographic inventory of every executed Debug binary**. The separately inspected Release app belongs to a different build and does not fill this Debug provenance gap.

## Remaining work — not concealed by scoped completion

- **WD-C4:** owner decision still needed on whether Leave-confirmation time counts toward a Whodunit discussion. No timer-policy change was invented.
- **IOS-R1:** remains a **locally investigable app-host storage/Keychain evidence gap**, outside the 16 repairs—not a confirmed defect or a physical-device-only blocker. Use an explicitly owned app-host investigation to establish healthy Keychain-backed migration/recovery before attributing a new defect. Do not hide storage errors, return empty stores on failure or add entitlements blindly. Real backup/restore remains a separate physical-device gate.
- **Physical P2pKit fixture readiness:** the three ignored tests retain historical admission/approval assumptions. Independently align and review their fixtures against the current protocol before relying on real-device results; do not merely remove `@Ignore`. Then execute controlled two-/three-device host/peer, permission-denial/rejoin, radio and lifecycle checks. In-memory tests are not physical LAN evidence.
- Actual physical Android/iOS LAN, Local Network denial/recovery, app-switcher behavior, real backup/restore, native gestures/VoiceOver/TalkBack/large text and sustained lifecycle/performance validation remain separate gates.
- Installed **Xcode 26.5 / 17F42** is not Store-qualified **26.3 / 17C529**. Qualified signed archives, real certificate/Store verification and authorized external owner actions remain unavailable.
- Android managed-device runtime requires the applicable Linux/KVM/x86_64 environment; Desktop/unit tests on this Apple Silicon host do not replace it. Linux-shaped shell fixtures are not a Linux signed-release pipeline run.
- The known `com.parlor.app` Store-identity collision and disabled publication workflows remain intact. Do not publish, select replacement identities or re-enable workflows without owner authorization.
- All bundled testing stories still need production editorial, translation, content-rights/legal and Store declaration approval.
- Other documentation discrepancies, rejected candidates and evidence gaps from the original audit are not newly declared repaired. This scope is not a fresh full-file audit or a claim that no other defects exist.

## Preservation, cleanup and evidence

<!-- FINAL_PRESERVATION -->

The build/test lane's mandatory finalization preserves compact results, immediately invokes `./gradlew --stop`, then cleans only owned generated outputs and stops again if cleanup could start Gradle. App-host finalizers also handle their owned DerivedData, simulator and ownership-attested secondary FIFOs. Each actual completion/failure remains recorded; this workflow description is not a claim that every historical attestation succeeded. No global dependency caches, signing material, source/configuration, prior evidence or another task's processes are deleted/stopped.

The historical apphost-02 cleanup receipt remains **FAIL** because of a secondary UID-attestation error. A [separate independent check](../reviews/independent-dsc01-apphost02-cleanup-supplement-01.json) established that its exact attested paths/workers are absent; that later observation does not retroactively make the failed attestation pass. Modern current cycles retain explicit stop/cleanup/worker receipts. No “all historical cleanup was perfect” claim is made.

Evidence entry points: [issues.json](issues.json), [gates.json](gates.json), [verification-summary.json](verification-summary.json), [cycle-history.json](cycle-history.json), [review-and-research-index.json](review-and-research-index.json), [preservation.json](preservation.json) and [final-observation.json](final-observation.json). Original audit/campaign reports remain unchanged. Integration, GitHub closure, real signing and publication still require separate authorization.
