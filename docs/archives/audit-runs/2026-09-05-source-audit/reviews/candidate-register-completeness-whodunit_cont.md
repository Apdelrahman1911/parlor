# Candidate-register completeness review

Reviewer: `/root/whodunit_cont`. Administrative consolidation only; **no new application-source approval**. Baseline `/Users/abdelrahman/Projects/parlor`, branch `main`, commit `3625d0663ba6eb51338cbd5f9dc45f859ec18846`, tree `db7f3d2afe73a13628296daee2cce71165eebc8d`; tracked tree unchanged at assembly.

## Scope and result

Read the complete current candidate, validation and reviewer-note Markdown plus audit README: **88 input reports / 2,968 lines**. Input hashes/ranges are in `evidence/candidate-register-completeness-inputs-whodunit_cont.json`. This is report-review coverage only, never additional source-review credit. Prior complete-reading records were hash-checked; new administrative mirrors, authorized final banners/correction and the new binary-assets report were read. Historical candidate bodies remained byte-identical beneath new banners. Execution receipts and the32line XCTest source were inspected to qualify evidence, without additional application-coverage credit.

`canonical-register.json` indexes **29 canonical IDs** with category, severity, finder, separate validator, dossier/validation links, absolute primary source locations, current source hashes and qualified execution references. All 29 now have a candidate dossier and a different named independent validator. No missing independent adjudication remains within those29final IDs. A separately tracked30th candidate, IOS-R1, is pending independent investigation and excluded from the positive counts; unnumbered leads and external limits below are not silently declared resolved.

| Category | Count |
|---|---:|
| Application/content defects | 10 |
| iOS build defect | 1 |
| Latent release defects | 3 |
| Test-registration defect | 1 |
| Documentation mismatches | 6 |
| Test/evidence gaps | 5 |
| Rejected candidates | 3 |

Thus **15 CONFIRMED DEFECT**, **6 DOCUMENTATION MISMATCH**, **5 TEST/EVIDENCE GAP**, **3 FALSE POSITIVE**. The 26 positive adjudications comprise **8 Medium / 18 Low**. These are audit dispositions, not GitHub open/closed issue counts, implemented fixes, readiness approval or proof no other defect exists.

## Per-ID filing and independent-review index

`W` = `/root/whodunit_cont`, `S` = `/root/session_cont`, `M` = `/root/mafia_cont`, `R` = `/root`. The arrow is finder → independent validator. Dossier links may preserve pre-validation hypotheses; the paired final independent report is the disposition authority.

| ID | Category | Severity | Finder → validator | Dossier / independent evidence |
|---|---|---|---|---|
| DOC-C1 | Documentation | Low | W → M | [Dossier](../candidates/DOC-C1-local-name-retention.md) / [validation](../validations/DOC-C1-mafia_cont.md) |
| DOC-C2 | Documentation | Low | W → R | [Dossier](../candidates/DOC-C2-game-vs-room-terminal.md) / [validation](../validations/DOC-C2-root.md) |
| DOC-C3 | Documentation | Low | M → S | [Dossier](../candidates/DOC-C3-xcode-team-change-scope.md) / [validation](../validations/DOC-C3-session_cont.md) |
| DOC-C4 | Documentation | Low | W → S | [Dossier](../candidates/DOC-C4-release-required-job-count.md) / [validation](../validations/DOC-C4-session_cont.md) |
| DS-C01 | App/content | Low | M → R | [Dossier](../candidates/DS-C01-ios-follow-system-locale.md) / [validation](../validations/DS-C01-root.md) |
| DS-C02 | App/content | Low | M → R | [Dossier](../candidates/DS-C02-toast-identity-reuse.md) / [validation](../validations/DS-C02-root.md) |
| DS-C03 | App/content | Low | M → W | [Dossier](../candidates/DS-C03-light-cover-ghost-contrast.md) / [validation](../validations/DS-C03-whodunit_cont.md) |
| INV-C01 | Evidence gap | Low | M → W | [Dossier](../candidates/INV-C01-generated-review-status.md) / [validation](../validations/INV-C01-whodunit_cont.md) |
| INV-C02 | Documentation | Low | M → W | [Dossier](../candidates/INV-C02-inventory-test-classification.md) / [validation](../validations/INV-C02-whodunit_cont.md) |
| IOS-B1 | iOS build | Medium | R → S | [Dossier](../candidates/IOS-B1-xcode-swallowed-framework-failure.md) / [validation](../validations/IOS-B1-session_cont.md) |
| M-C01 | App/content | Low | M → W | [Dossier](../candidates/M-C01-doctor-recovery-history.md) / [validation](../validations/MF-C1-whodunit_cont.md) |
| M-C02 | Rejected | — | M → S | [Dossier](../candidates/M-C02-connected-retirement-rejected.md) / [validation](../validations/M-C02-session_cont.md) |
| M-C03 | App/content | Low | M → R | [Dossier](../candidates/M-C03-mafia-row-layout.md) / [validation](../validations/M-C03-root.md) |
| MT-C1 | Rejected | — | W → S | [Dossier](../candidates/MT-C1-kotlin-kapt-advisory-applicability.md) / [validation](../validations/MT-C1-session_cont.md) |
| MT-T1 | Evidence gap | Low | W → S | [Dossier](../candidates/MT-T1-artifact-hash-assertion-scope.md) / [validation](../validations/MT-T1-session_cont.md) |
| RL-C1 | Latent release | Medium | W → S | [Dossier](../candidates/RL-C1-whodunit_cont.md) / [validation](../validations/RL-C1-session_cont.md) |
| RL-C2 | Latent release | Medium | W → S | [Dossier](../candidates/RL-C2-whodunit_cont.md) / [validation](../validations/RL-C2-session_cont.md) |
| RL-C3 | Latent release | Medium | W → S | [Dossier](../candidates/RL-C3-whodunit_cont.md) / [validation](../validations/RL-C3-session_cont.md) |
| ROOT-C1 | Rejected | — | R → M | [Dossier](../candidates/ROOT-C1-ios-directory-crash-rejected.md) / [validation](../reviews/independent-ROOT-C1-mafia-cont.md) |
| ROOT-T3 | Test registration | Medium | R → S | [Dossier](../candidates/ROOT-T3-nonvoid-test-registration.md) / [validation](../validations/ROOT-T3-session_cont.md) |
| SN-C1 | App/content | Medium | S → R | [Dossier](../candidates/SN-C1-start-ack-deadline.md) / [validation](../validations/SN-C1-root.md) |
| SN-C2 | App/content | Medium | S → R | [Dossier](../candidates/SN-C2-host-registration-cancellation.md) / [validation](../validations/SN-C2-root.md) |
| SN-D1 | Documentation | Low | S → R | [Dossier](../candidates/SN-D1-physical-matrix-player-counts.md) / [validation](../validations/SN-D1-root.md) |
| SN-T1 | Evidence gap | Low | S → R | [Dossier](../candidates/SN-T1-ignored-loopback-fixtures.md) / [validation](../validations/SN-T1-root.md) |
| WD-C1 | App/content | Medium | W → S | [Dossier](../candidates/WD-C1-whodunit_cont.md) / [validation](../candidates/WD-C1-independent-session_cont.md) |
| WD-C2 | App/content | Low | W → M | [Dossier](../candidates/WD-C2-whodunit_cont.md) / [validation](../validations/WD-C2-mafia_cont.md) |
| WD-C3 | App/content | Low | W → R | [Dossier](../candidates/WD-C3-whodunit_cont.md) / [validation](../validations/WD-C3-root.md) |
| WD-T1 | Evidence gap | Low | W → R | [Dossier](../candidates/WD-T1-whodunit_cont.md) / [validation](../validations/WD-T1-root.md) |
| WD-T2 | Evidence gap | Low | W → R | [Dossier](../candidates/WD-T2-whodunit_cont.md) / [validation](../validations/WD-T2-root.md) |

## Separate pending candidate — excluded from positive counts

**IOS-R1**: [fresh unsigned simulator recovery-unavailable observation](../candidates/IOS-R1-simulator-recovery-unavailable.md). Finder `/root`; independent validator `/root/session_cont` assigned, **not yet approved**. No severity or causal source location is asserted. Both retained `xcode-ui-01` screenshots visibly show the localized recovery-unavailable card. Whether that is an application defect, unsigned/native-protection environment limitation, or correct failure handling remains under source-first investigation. This is not a data-loss, crash or physical-device claim.

The checked-in XCTest is **one English-only test** and passed (`evidence/xcode-ui-01/xcresult-summary.json`). English and Arabic screenshots came from **separate supplemental simctl launches**, not two XCTest cases. Actual test source `iosApp/iosAppUITests/IOSAppLaunchUITests.swift:1–32` was read to correct this audit-note claim. Screenshot observation does not identify a root cause. Machine register field `unconfirmed_candidates` keeps IOS-R1 separate: **30allocated /29adjudicated /26positive /3rejected /1pending**.

## Normalization and evidence qualifications

- **MF-C1 aliases M-C01**. Preserve `validations/MF-C1-whodunit_cont.md`; do not count twice.
- WD-C1 independent evidence is intentionally in `candidates/WD-C1-independent-session_cont.md`; ROOT-C1 evidence is in `reviews/independent-ROOT-C1-mafia-cont.md`. Neither folder placement is missing validation.
- ROOT-C1, ROOT-T3 and M-C02 now have administrative candidate mirrors. Mirrors reproduce existing classifications, not new approvals or findings. ROOT-C1 rejects only the concrete corrupt-directory opener/crash claim; M-C02 rejects only the nonexistent connected-state retirement guard.
- ROOT-T3 is **one cause / ten omitted descriptors** (eight enabled plus two intentionally ignored). Its eight enabled bodies subsequently passed isolated Unit-return wrappers. SN-T1 concerns a different stale admission-fixture cause. Never count the two physical tests as executed.
- WD-C2 groups **four validated chronology manifestations** only. M-C03 confirms **final-role zero-width layout only**, not setup/tally siblings. INV-C01 documentation wording is part of its evidence gap, not a second documentation finding.
- **ROOT-T1/ROOT-T2 are not allocated findings**; no source in this register defines them. Historical P2P/PHY/HOT/CVE identifiers are not automatically current defects.
- M-C01 and WD-C3 require malformed but authenticated current snapshots. No normal-game producer or authentication bypass was demonstrated; retain Low defense-in-depth severity and this prerequisite.
- RL-C1 and RL-C2 have authoritative source proofs, not executed GNU/AAB/signing evidence. RL-C3 has a two-test mocked-HTTP execution (one expected failure, one counter-evidence pass), not Store proof. All are latent while the production workflows remain intentionally disabled.
- DS-C01 uses isolated native Foundation preferences, not the iOS application. IOS-B1 uses an extracted synthetic Xcode26.5 build phase, not a Store-qualified26.3 app archive or an explanation of prior launch crashes. DS-C02 and M-C03 use production Desktop composables, not physical-device UI proof.
- Dedicated MT-T1 runtime-counterexample execution was not located; its classification rests on independent deterministic source proof. Passing ordinary contract suites would not prove the assertion-boundary weakness absent.

## Administrative inconsistencies to preserve or clarify

At root request, explicit final-disposition banners were added to **DS-C02, SN-C2, WD-C2, WD-T1, WD-T2**, preserving every byte of their historical candidate bodies. These are administrative imports from existing independent validations, not new source approvals. DOC-C2, DS-C01, SN-D1 and SN-T1 already have equivalent superseding banners. The incorrect English/Arabic-XCTest statement in `reviews/root-pending-validation-notes.md:18` was corrected against the actual32line test and separate supplemental launch receipts.

`reviews/whodunit-cont-notes.md:77` retains an older WD-C3-pending summary; the dossier and independent validation supersede it. Other append-only notes include similarly historical pending paragraphs followed by final results. Do not infer current status from isolated regex hits.

`candidates/WD-C1-independent-session_cont.md:27` proposes a legal four-player reproduction without distinguishing bundled content. The shipping procedure needs six bundled seats (or explicitly synthetic four-seat content). This does not undermine its count-independent overlay/remount source proof.

## Unnumbered and sibling uncertainty, not extra confirmed counts

Root should link any subsequent closure or carry these limitations into the final assessment:

| Lead | Existing note / limitation |
|---|---|
| UI-owned Whodunit ticker while presentation is absent | `reviews/whodunit-cont-notes.md:10,58,70`; navigation/lifecycle cross-path follow-up, no new finding approval. |
| Non-scrolling loading/connecting UI, TimerRibbon width, duplicate-name feedback | Same note:27–28; no concrete measurement or clarified requirement establishes a current defect. |
| iOS localized Debug display-name precedence | `reviews/root-platform-shell-notes.md:20`; platform result, not assumed from filenames. |
| Host-form story wording in Mafia | `reviews/root-ui-tests-notes.md:5`; no new defect count from review shorthand. |
| iOS post-open Objective-C I/O failure and legacy-delete asymmetry | `reviews/root-storage-shell-notes.md:11,29`; distinct from narrow rejected ROOT-C1. |
| Cancelled-middle ordered GameEvent emission | `reviews/session-cont-notes.md:8,59`; no shipping consumer established. |
| Content scalar coercion/supplementary-control and toolchain prose caveats | `reviews/product-docs-mafia-cont.md:20–23`; harmful shipping consequence not established. |
| Content ages/Zamalek timing and Mafia setup/tally layout siblings | WD-C2 and M-C03 dossiers; explicitly not confirmed manifestations. |

Already rejected unnumbered directions include the incoming-host Reconnecting/ordinary-leave misuse hypotheses (`reviews/session-cont-notes.md:32–33,59`), a chained forged-timer test claim (`reviews/whodunit-cont-notes.md:68`), and hypothetical release retry/provenance directions (`reviews/release-whodunit_cont-notes.md:23–25,44`). No issue count is fabricated from these notes.

## Safety and completion

Only task-specific audit evidence was written. No application source, test registration, dependency metadata, signing input, design asset or pre-existing user work changed. No Git branch/commit, GitHub/Store action, application launch, build/test execution or process termination occurred in this administrative subtask. Root owns the shared build lane and associated immediate cleanup; this reviewer did not stop or delete root-owned work. The read `xcode-ui-01` receipt independently records its stops, temporary simulator deletion, task-owned output cleanup and no remaining outputs; no extra cleanup operation was necessary for this report-only subtask.

Programmatic validation checks29unique adjudicated IDs plus distinct pending IOS-R1; category/severity totals; separate finder/validator identity for adjudicated findings; every dossier, validation, execution and primary source path; source-range bounds/hashes; and unchanged HEAD/tree/tracked status. Pending IOS-R1 deliberately has no completed validation or causal source location yet. Receipt: `evidence/candidate-register-completeness-validation-whodunit_cont.json`. This validates filing consistency, not the correctness of source behavior by itself.
